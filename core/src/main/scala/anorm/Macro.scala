package anorm

object Macro {
  import scala.language.experimental.macros
  import scala.reflect.macros.Context

  def namedParserImpl[T: c.WeakTypeTag](c: Context): c.Expr[Any] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($n)" }
  }

  def namedParserImpl_[T: c.WeakTypeTag](c: Context)(names: c.Expr[String]*): c.Expr[Any] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe
    val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
    val params = ctor.paramLists.flatten

    if (names.size < params.size) {
      c.abort(c.enclosingPosition,
        "no column name for parameters: ${show(names)} < $params")

    } else {
      parserImpl[T](c) { (t, _, i) =>
        names.lift(i) match {
          case Some(n) => q"anorm.SqlParser.get[$t]($n)"
          case _ => c.abort(c.enclosingPosition,
            s"missing column name for parameter $i")
        }
      }
    }
  }

  def offsetParserImpl[T: c.WeakTypeTag](c: Context)(offset: c.Expr[Int]): c.Expr[Any] = {
    import c.universe._

    parserImpl[T](c) { (t, _, i) =>
      q"anorm.SqlParser.get[$t]($offset + ${i + 1})"
    }
  }

  def indexedParserImpl[T: c.WeakTypeTag](c: Context): c.Expr[Any] = {
    import c.universe._

    offsetParserImpl[T](c)(reify(0))
  }

  private def parserImpl[T: c.WeakTypeTag](c: Context)(genGet: (c.universe.Type, String, Int) => c.universe.Tree): c.Expr[Any] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe
    @inline def abort(m: String) = c.abort(c.enclosingPosition, m)

    if (!tpe.typeSymbol.isClass || !tpe.typeSymbol.asClass.isCaseClass) {
      abort(s"case class expected: ${tpe}")
    }

    val colTpe = c.weakTypeTag[Column[_]].tpe
    val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod

    if (ctor.paramLists.isEmpty) {
      abort(s"parsed data cannot be passed as parameter: $ctor")
    }

    val TypeRef(_, _, tpeArgs) = tpe

    val companion = tpe.typeSymbol.companion.typeSignature
    val apply = companion.decl(TermName("apply")).asMethod

    val boundTypes = apply.typeParams.zip(tpeArgs).map {
      case (sym, ty) => sym.fullName -> ty
    }.toMap

    val optTpe = c.weakTypeTag[Option[_]].tpe

    // ---

    val (x, m, body, _) = ctor.paramLists.
      foldLeft[(Tree, Tree, Tree, Int)]((EmptyTree, EmptyTree, EmptyTree, 0)) {
        case ((xa, ma, bs, ia), pss) =>
          val (xb, mb, vs, ib) =
            pss.foldLeft((xa, ma, List.empty[Tree], ia)) {
              case ((xtr, mp, ps, pi), term: TermSymbol) =>
                val tn = term.name.toString
                val tt = {
                  val t = term.typeSignature
                  boundTypes.lift(t.typeSymbol.fullName).getOrElse(t)
                  // TODO: term.isParamWithDefault
                }
                val ctype = appliedType(colTpe, List(tt))

                c.inferImplicitValue(ctype) match {
                  case EmptyTree =>
                    abort(s"cannot find $ctype for ${term.name} in $ctor")

                  case _ => {
                    val get = genGet(tt, tn, pi)

                    pq"${term.name}" match {
                      case b @ Bind(bn, _) =>
                        val bt = q"${bn.toTermName}"

                        xtr match {
                          case EmptyTree => (get, b, List[Tree](bt), pi + 1)
                          case _ => (q"$xtr ~ $get",
                            pq"anorm.~($mp, $b)", bt :: ps, pi + 1)

                        }
                    }
                  }
                }
            }

          val by = bs match {
            case EmptyTree => q"new $tpe(..${vs.reverse})"
            case xs => q"$xs(..${vs.reverse})"
          }

          (xb, mb, by, ib)
      }

    val caseDef = cq"$m => { $body }"
    val parser = q"$x map[$tpe] { _ match { case $caseDef } }"

    if (debugEnabled) {
      val generated = show(parser).replaceAll("anorm.", "").
        replaceAll("\\)\\)\\.\\$tilde\\(", ") ~ ").
        replaceAll("\\)\\.\\$tilde\\(", " ~ ").replaceAll("\\$tilde", "~")

      c.echo(c.enclosingPosition, s"row parser generated for $tpe: $generated")
    }

    c.Expr(c.typecheck(parser))
  }

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T the type of case class
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.namedParser[YourCaseClass]
   * }}}
   */
  def namedParser[T] = macro namedParserImpl[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values using given `names`.
   *
   * @tparam T the type of case class
   * @param names the names of columns corresponding to the case class values
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.namedParser[YourCaseClass]
   * }}}
   */
  def parser[T](names: String*) = macro namedParserImpl_[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position.
   *
   * @tparam T the type of case class
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.indexedParser[YourCaseClass]
   * }}}
   */
  def indexedParser[T] = macro indexedParserImpl[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position, with an offset.
   *
   * @tparam T the type of case class
   * @param offset the offset of column to be considered by the parser
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.offsetParser[YourCaseClass](2)
   * }}}
   */
  def offsetParser[T](offset: Int) = macro offsetParserImpl[T]

  private lazy val debugEnabled =
    Option(System.getProperty("anorm.macro.debug")).
      filterNot(_.isEmpty).map(_.toLowerCase).map { v =>
        "true".equals(v) || v.substring(0, 1) == "y"
      }.getOrElse(false)
}
