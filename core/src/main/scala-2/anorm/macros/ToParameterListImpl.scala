package anorm.macros

import scala.reflect.macros.whitebox

import anorm.{ Compat, ToParameterList, ToSql, ToStatement }
import anorm.Macro.{ debugEnabled, ParameterProjection }
import anorm.macros.Inspect.pretty

private[anorm] object ToParameterListImpl {
  def sealedTrait[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToParameterList[T]] = {
    val tpe                        = c.weakTypeTag[T].tpe
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val subclasses = Inspect.directKnownSubclasses(c)(tpe)

    if (subclasses.isEmpty) {
      abort(s"cannot find any subclass: $tpe")
    }

    // ---

    import c.universe._

    val cases = subclasses.map { subcls =>
      cq"v: ${subcls} => implicitly[_root_.anorm.ToParameterList[${subcls}]].apply(v)"
    }
    val arg = TermName(c.freshName("arg"))
    val mat = Match(q"${arg}", cases)

    val block = q"_root_.anorm.ToParameterList[${tpe}] { $arg: ${tpe} => $mat }"

    if (debugEnabled) {
      c.echo(c.enclosingPosition, s"ToParameterList generated for $tpe: ${pretty(c)(block)}")
    }

    c.Expr[ToParameterList[T]](c.typecheck(block))
  }

  def caseClass[T: c.WeakTypeTag](
      c: whitebox.Context
  )(projection: Seq[c.Expr[ParameterProjection]], separator: c.Expr[String]): c.Expr[ToParameterList[T]] = {

    val tpe                        = c.weakTypeTag[T].tpe
    val tpeSym                     = tpe.typeSymbol
    @inline def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    if (!tpeSym.isClass || !tpeSym.asClass.isCaseClass) {
      abort(s"Case class expected: $tpe")
    }

    val ctor = tpe.decl(c.universe.termNames.CONSTRUCTOR).asMethod

    if (ctor.paramLists.isEmpty) {
      abort(s"parsed data cannot be passed as parameter: $ctor")
    }

    // Typeclass types
    val toPListTpe     = c.weakTypeTag[ToParameterList[_]].tpe
    lazy val toSqlTpe  = c.weakTypeTag[ToSql[_]].tpe
    lazy val toStmtTpe = c.weakTypeTag[ToStatement[_]].tpe

    import c.universe._

    val boundTypes: Map[String, Type] = Inspect.boundTypes(c)(tpe)
    val forwardName                   = TermName(c.freshName("forward"))

    val resolveImplicit: (Name, Type, Type) => Implicit[Type, Name, Tree] =
      ImplicitResolver(c)(tpe, boundTypes, forwardName)

    // ---

    val debug: String => Unit = {
      if (debugEnabled) c.echo(c.enclosingPosition, _: String)
      else { (_: String) => () }
    }

    val compiledProj: Seq[ParameterProjection] = projection.map { expr =>
      @inline def eval() =
        c.eval(c.Expr[ParameterProjection](c.untypecheck(expr.tree)))

      try {
        eval()
      } catch {
        case scala.util.control.NonFatal(_) =>
          // workaround for Eval issue in 2.11
          eval()
      }
    }

    // All supported class properties
    val properties = ctor.paramLists.take(1).flatten.collect {
      case term: TermSymbol =>
        term
    }

    // Among the properties, according the specified projection
    val selectedProperties: Seq[String] = {
      val propertyNames = properties.map(_.name.toString)

      if (compiledProj.nonEmpty) {
        compiledProj.collect {
          case proj if propertyNames.contains(proj.propertyName) =>
            proj.propertyName
        }
      } else propertyNames
    }

    if (selectedProperties.isEmpty) {
      c.abort(
        c.enclosingPosition,
        s"No property selected to be converted as SQL parameter for ${tpe}: ${properties.mkString(", ")}"
      )
    }

    // ---

    // Types
    val pkg              = q"_root_.anorm"
    val NamedParameter   = q"${pkg}.NamedParameter"
    val ToParameterValue = q"${pkg}.ToParameterValue"
    lazy val ImuList     = q"_root_.scala.collection.immutable.List"

    val instanceName = TermName(c.freshName("instance"))
    val bufName      = TermName(c.freshName("buf"))
    val namedAppends = Map.newBuilder[String, (TermName, Tree)]

    if (ctor.paramLists.tail.nonEmpty) {
      c.echo(
        c.enclosingPosition,
        s"${tpe} constructor has multiple list of parameters. As for unapply, only for the first one will be considered: ${ctor.paramLists.headOption.mkString}"
      )
    }

    properties.foreach { term =>
      if (!selectedProperties.contains(term.name.toString)) {
        debug(s"${term} is filtered: ${selectedProperties}")
      } else {
        val tt = {
          val t = term.typeSignature

          boundTypes.getOrElse(t.typeSymbol.fullName, t)
        }
        val resolv = resolveImplicit(term.name, tt, _: Type)

        lazy val toSql = resolv(toSqlTpe) match {
          case unresolved @ Implicit.Unresolved() => {
            val applied = appliedType(toSqlTpe, tt)
            unresolved.copy(neededImplicit = q"null.asInstanceOf[${applied}]")
          }

          case resolved => resolved
        }

        resolv(toPListTpe) match {
          case unresolved @ Implicit.Unresolved() if unresolved.selfRef => {
            val pc = ParameterContext(c)(term)

            val defDef =
              q"def ${pc.defName}(${pc.parameterName}: String) = ${bufName} ++= ${forwardName}(${instanceName}.${term.name}).map { p => p.copy(name = ${pc.parameterName} + ${separator} + p.name) }"

            namedAppends += pc.propertyName -> (pc.defName -> defDef)
          }

          case Implicit.Unresolved() =>
            resolv(toStmtTpe) match {
              case Implicit.Unresolved() =>
                abort(s"cannot find either $toPListTpe or $toStmtTpe for ${term.name}:$tt")

              case toStmt => { // use ToSql+ToStatement
                val pc = ParameterContext(c)(term)

                val defDef =
                  q"def ${pc.defName}(${pc.parameterName}: String) = ${bufName} += ${NamedParameter}.namedWithString(${pc.parameterName} -> ${instanceName}.${term.name})(${ToParameterValue}(${toSql.neededImplicit}, ${toStmt.neededImplicit}))"

                namedAppends += pc.propertyName -> (pc.defName -> defDef)
              }
            }

          case toParams => { // use ToParameterList
            val pc = ParameterContext(c)(term)

            val defDef =
              q"def ${pc.defName}(${pc.parameterName}: String) = ${bufName} ++= ${toParams.neededImplicit}(${instanceName}.${term.name}).map { p => p.copy(name = ${pc.parameterName} + ${separator} + p.name) }"

            namedAppends += pc.propertyName -> (pc.defName -> defDef)
          }
        }
      }
    }

    val appendParameters = namedAppends.result()

    // val for local list buffer
    val NamedParamTpe = c.typeOf[_root_.anorm.NamedParameter]
    val bufVal        = q"val ${bufName} = ${ImuList}.newBuilder[${NamedParamTpe}]"

    // def of functions to append named parameters to a local list buffer
    val appendDefs = Compat.mapValues(appendParameters)(_._2).values.toSeq

    // applies/call of the functions according the projection
    val effectiveProj: Map[String, String] = {
      if (compiledProj.nonEmpty) {
        Compat.toMap(compiledProj) {
          case ParameterProjection(propName, Some(paramName)) =>
            propName -> paramName

          case ParameterProjection(propName, _) =>
            propName -> propName
        }
      } else
        Compat.collectToMap(properties) {
          case term: TermSymbol => {
            val propName = term.name.toString
            propName -> propName
          }
        }
    }

    val appendCalls = effectiveProj.flatMap {
      case (propName, paramName) =>
        appendParameters.get(propName).map {
          case (append, _) =>
            // Find the previously generated append function for the property,
            // and applies it with the parameter name
            q"${append}(${paramName})"
        }
    }

    val resultCall = q"${bufName}.result()"

    val innerBlock = (bufVal +: appendDefs) ++ appendCalls :+ resultCall

    val innerFn =
      q"def ${forwardName}(${instanceName}: ${tpe}): _root_.scala.collection.immutable.List[_root_.anorm.NamedParameter] = { ..${innerBlock} }"

    val block = q"{ $innerFn; _root_.anorm.ToParameterList[${tpe}](${forwardName}) }"

    if (debugEnabled) {
      c.echo(c.enclosingPosition, s"ToParameterList generated for $tpe: ${pretty(c)(block)}")
    }

    c.Expr[ToParameterList[T]](c.typecheck(block))
  }

  // ---

  import scala.reflect.api.Universe

  private case class ParameterContext[TermName <: Universe#TermNameApi, Tree <: Universe#TreeApi](
      propertyName: String,
      parameterName: TermName,
      defName: TermName
  )

  private object ParameterContext {

    /** Factory */
    def apply(c: whitebox.Context)(term: c.universe.TermSymbol): ParameterContext[c.TermName, c.Tree] = {
      import c.universe._

      val n = term.name.toString

      ParameterContext(
        propertyName = n,
        parameterName = TermName(c.freshName("parameter")),
        defName = TermName(c.freshName(n))
      )
    }
  }
}
