package anorm.macros

import scala.quoted.{ Expr, Quotes, Type }

import anorm.{ Compat, ToParameterList, ToParameterValue, ToSql, ToStatement, NamedParameter }
import anorm.Macro.{ debugEnabled, ParameterProjection }

private[anorm] object ToParameterListImpl {
  def sealedTrait[T](using Quotes, Type[T]): Expr[ToParameterList[T]] = {
    /* TODO
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
     */

    '{ ??? }
  }

  def caseClass[A](
      forwardExpr: Expr[ToParameterList[A]],
      projection: Expr[Seq[ParameterProjection]],
      separator: Expr[String]
  )(using q: Quotes, tpe: Type[A], tsTpe: Type[ToParameterList]): Expr[A => List[NamedParameter]] = {
    import q.reflect.*

    val (aTpr, aTArgs) = TypeRepr.of[A](using tpe) match {
      case tpr @ AppliedType(_, args) =>
        tpr -> args.collect {
          case repr: TypeRepr =>
            repr
        }

      case tpr =>
        tpr -> List.empty[TypeRepr]
    }

    val tpeSym = aTpr.typeSymbol

    @inline def abort(msg: String) = report.errorAndAbort(msg)

    if (!tpeSym.isClassDef || !tpeSym.flags.is(Flags.Case)) {
      abort(s"Case class expected: $tpe")
    }

    val ctor = tpeSym.primaryConstructor

    if (ctor.paramSymss.isEmpty) {
      abort("parsed data cannot be passed as constructor parameters")
    }

    val resolv = ImplicitResolver[A](q).resolver(forwardExpr, Map.empty, report.info(_))(tsTpe)

    // ---

    val (boundTypes, properties) = ctor.paramSymss match {
      case targs :: params :: Nil if targs.forall(_.isType) => {
        val boundTps = targs.zip(aTArgs).toMap

        boundTps -> params
      }

      case params :: Nil =>
        Map.empty[Symbol, TypeRepr] -> params

      case params :: _ => {
        report.info(
          s"${aTpr.show} constructor has multiple list of parameters. As for unapply, only for the first one will be considered"
        )

        Map.empty[Symbol, TypeRepr] -> params
      }

      case _ =>
        report.errorAndAbort(s"${aTpr.show} constructor has no parameter")
    }

    val compiledProjection: Seq[ParameterProjection] = {
      import _root_.anorm.Macro.parameterProjectionFromExpr

      projection.valueOrAbort
    }
    lazy val projectionMap = compiledProjection.collect {
      case ParameterProjection(propertyName, Some(parameterName)) =>
        propertyName -> parameterName
    }.toMap

    // Among the properties, according the specified projection
    val selectedProperties: Seq[String] = {
      val propertyNames = properties.map(_.name)

      if (compiledProjection.isEmpty) {
        propertyNames
      } else {
        compiledProjection.collect {
          case proj if propertyNames.contains(proj.propertyName) =>
            proj.propertyName
        }
      }
    }

    if (selectedProperties.isEmpty) {
      report.errorAndAbort(
        s"No property selected to be converted as SQL parameter for ${aTpr.show}: ${properties.mkString(", ")}"
      )
    }

    // ---

    type Builder = scala.collection.mutable.Builder[NamedParameter, List[NamedParameter]]

    type Append[T] = Function2[Expr[T], Expr[Builder], Expr[Builder]]

    val namedAppends = Map.newBuilder[String, (TypeRepr, Append[Any])]

    properties.zipWithIndex.foreach {
      case (sym, i) =>
        if (!selectedProperties.contains(sym.name)) {
          report.info(s"${sym.name} is filtered: ${selectedProperties}")
        } else {
          val pname = Expr(projectionMap.getOrElse(sym.name, sym.name))

          val tt: TypeRepr = sym.tree match {
            case vd: ValDef => {
              val vtpe = vd.tpt.tpe

              boundTypes.getOrElse(vtpe.typeSymbol, vtpe)
            }

            case _ =>
              report.errorAndAbort(s"Value definition expected for ${aTpr.show} constructor parameter: $sym")
          }

          tt.asType match {
            case pt @ '[t] => {
              val toSql = Expr.summon[ToSql[t]] match {
                case Some(resolved) =>
                  resolved

                case _ =>
                  '{ null: ToSql[t] }
              }

              resolv(tt) match {
                case None if tt <:< aTpr => {
                  val append: Function2[Expr[A], Expr[Builder], Expr[Builder]] = { (v, buf) =>
                    '{
                      val prefix: String = $pname + $separator

                      $buf ++= ${ forwardExpr }($v).map { p =>
                        p.copy(name = prefix + p.name)
                      }
                    }
                  }

                  namedAppends += sym.name -> (tt -> append.asInstanceOf[Append[Any]])
                }

                case None =>
                  Expr.summon[ToStatement[t]] match {
                    case None =>
                      abort(s"cannot find either anorm.ToParameterList or anorm.ToStatement for ${sym.name}:${tt.show}")

                    case Some(toStmt) => { // use ToSql+ToStatement
                      val append: Function2[Expr[t], Expr[Builder], Expr[Builder]] = { (v, buf) =>
                        '{
                          $buf += NamedParameter
                            .namedWithString($pname -> $v)(ToParameterValue($toSql, $toStmt))
                        }
                      }

                      namedAppends += sym.name -> (tt -> append.asInstanceOf[Append[Any]])
                    }
                  }

                case Some((toParams, _)) => { // use ToParameterList
                  val append: Function2[Expr[t], Expr[Builder], Expr[Builder]] = { (v, buf) =>
                    '{
                      val prefix: String = $pname + $separator

                      $buf ++= ${ toParams.asExprOf[ToParameterList[t]] }($v).map { p =>
                        p.copy(name = prefix + p.name)
                      }
                    }
                  }

                  namedAppends += sym.name -> (tt -> append.asInstanceOf[Append[Any]])
                }
              }
            }
          }
        }
    }

    val appendParameters: Map[String, (TypeRepr, Append[Any])] =
      namedAppends.result()

    inline def appendField(
        inline a: Term,
        buf: Expr[Builder],
        fieldName: String,
        paramName: String
    ): Expr[Builder] = appendParameters.get(fieldName) match {
      case Some((pTpr, appendFn)) => {
        val fieldValue = a.select(a.symbol.fieldMember(fieldName))

        appendFn(fieldValue.asExpr, buf)
      }

      case _ =>
        report.errorAndAbort(s"Missing append function for ${fieldName}: ${aTpr.show}")
    }

    inline def withBuilder(inline a: Term, buf: Expr[Builder]): Expr[Unit] = {
      val pj: List[(String, String)] = {
        if (compiledProjection.nonEmpty) {
          compiledProjection.toList.flatMap {
            case ParameterProjection(nme, param) =>
              param.map(nme -> _)
          }
        } else {
          selectedProperties.map { n => n -> n }.toList
        }
      }

      Expr.block(
        pj.map {
          case (fieldName, paramName) =>
            appendField(a, buf, fieldName, paramName)
        },
        '{ () }
      )
    }

    inline def appendBlock(inline a: Expr[A]): Expr[List[NamedParameter]] = {
      val term = asTerm(a)

      '{
        val buf: Builder = List.newBuilder[NamedParameter]

        ${ withBuilder(term, 'buf) }

        buf.result()
      }
    }

    val block = '{ (a: A) => ${ appendBlock('a) } }

    if (debugEnabled) {
      report.info(s"ToParameterList generated for ${aTpr.show}: ${block.show}")
    }

    block
  }
}
