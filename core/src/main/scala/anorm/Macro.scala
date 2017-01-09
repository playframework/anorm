package anorm

/**
 * @define caseTParam the type of case class
 * @define namingParam the column naming, to resolve the column name for each case class property
 * @define namesParam the names of the columns corresponding to the case class properties
 */
object Macro {
  import scala.language.experimental.macros
  import scala.reflect.macros.whitebox

  /**
   * Naming strategy, to map each class property to the corresponding column.
   */
  trait ColumnNaming extends (String => String) {
    /**
     * Returns the column name for the class property.
     *
     * @param property the name of the case class property
     */
    def apply(property: String): String
  }

  /** Naming companion */
  object ColumnNaming {
    /**
     * For each class property, use the snake case equivalent
     * to name its column (e.g. fooBar -> foo_bar).
     */
    object SnakeCase extends ColumnNaming {
      private val re = "[A-Z]+".r

      def apply(property: String): String =
        re.replaceAllIn(property, { m => s"_${m.matched.toLowerCase}" })
    }

    /** Naming using a custom transformation function. */
    def apply(transformation: String => String): ColumnNaming =
      new ColumnNaming {
        def apply(property: String): String = transformation(property)
      }
  }

  def namedParserImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[T] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($n)" }
  }

  def namedParserImpl1[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[ColumnNaming]): c.Expr[T] = {
    import c.universe._

    parserImpl[T](c) { (t, n, _) => q"anorm.SqlParser.get[$t]($naming($n))" }
  }

  @deprecated("Use [[namedParserImpl2]]", "2.5.2")
  def namedParserImpl_[T: c.WeakTypeTag](c: whitebox.Context)(names: c.Expr[String]*): c.Expr[T] = namedParserImpl2[T](c)(names: _*)

  def namedParserImpl2[T: c.WeakTypeTag](c: whitebox.Context)(names: c.Expr[String]*): c.Expr[T] = {
    import c.universe._

    namedParserImpl4[T](c)(names) { n => q"$n" }
  }

  def namedParserImpl3[T: c.WeakTypeTag](c: whitebox.Context)(naming: c.Expr[ColumnNaming], names: c.Expr[String]*): c.Expr[T] = {
    import c.universe._

    namedParserImpl4[T](c)(names) { n => q"$naming($n)" }
  }

  private def namedParserImpl4[T: c.WeakTypeTag](c: whitebox.Context)(names: Seq[c.Expr[String]])(naming: c.Expr[String] => c.universe.Tree): c.Expr[T] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe
    val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
    val params = ctor.paramLists.flatten

    if (names.size < params.size) {
      c.abort(c.enclosingPosition,
        s"no column name for parameters: ${show(names)} < $params")

    } else {
      parserImpl[T](c) { (t, _, i) =>
        names.lift(i) match {
          case Some(n) => {
            val cn = naming(n)
            q"anorm.SqlParser.get[$t]($cn)"
          }

          case _ => c.abort(c.enclosingPosition,
            s"missing column name for parameter $i")
        }
      }
    }
  }

  def offsetParserImpl[T: c.WeakTypeTag](c: whitebox.Context)(offset: c.Expr[Int]): c.Expr[T] = {
    import c.universe._

    parserImpl[T](c) { (t, _, i) =>
      q"anorm.SqlParser.get[$t]($offset + ${i + 1})"
    }
  }

  def indexedParserImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[T] = {
    import c.universe._

    offsetParserImpl[T](c)(reify(0))
  }

  private def parserImpl[T: c.WeakTypeTag](c: whitebox.Context)(genGet: (c.universe.Type, String, Int) => c.universe.Tree): c.Expr[T] = {
    import c.universe._

    val tpe = c.weakTypeTag[T].tpe
    @inline def abort(m: String) = c.abort(c.enclosingPosition, m)

    if (!tpe.typeSymbol.isClass || !tpe.typeSymbol.asClass.isCaseClass) {
      abort(s"case class expected: $tpe")
    }

    val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod

    if (ctor.paramLists.isEmpty) {
      abort(s"parsed data cannot be passed as parameter: $ctor")
    }

    val TypeRef(_, _, tpeArgs) = tpe

    val boundTypes: Map[String, Type] = if (tpeArgs.isEmpty) Map.empty else {
      // Need apply rather than ctor to resolve parameter symbols

      val companion = tpe.typeSymbol.companion.typeSignature
      val apply = companion.decl(TermName("apply")).asMethod

      if (apply.paramLists.isEmpty) Map.empty
      else apply.typeParams.zip(tpeArgs).map {
        case (sym, ty) => sym.fullName -> ty
      }.toMap
    }

    val colTpe = c.weakTypeTag[Column[_]].tpe
    val parserTpe = c.weakTypeTag[RowParser[_]].tpe

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
                  case EmptyTree => {
                    val ptype = appliedType(parserTpe, List(tt))

                    c.inferImplicitValue(ptype) match {
                      case EmptyTree =>
                        abort(s"cannot find $ctype nor $ptype for ${term.name} in $ctor")
                      case pr => {
                        if (debugEnabled) {
                          c.echo(c.enclosingPosition, s"instance of RowParser[$tt] resolved for ${tpe.typeSymbol.fullName}.$tn: $pr")
                        }

                        // Use an existing `RowParser[T]` as part
                        pq"${term.name}" match {
                          case b @ Bind(bn, _) =>
                            val bt = q"${bn.toTermName}"

                            xtr match {
                              case EmptyTree => (pr, b, List[Tree](bt), pi + 1)
                              case _ => (q"$xtr ~ $pr",
                                pq"anorm.~($mp, $b)", bt :: ps, pi + 1)

                            }
                        }
                      }
                    }
                  }

                  case ic => {
                    if (debugEnabled) {
                      c.echo(c.enclosingPosition, s"instance of Column[$tt] resolved for ${tpe.typeSymbol.fullName}.$tn: $ic")
                    }

                    // Generate a `get` for the `Column[T]`
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
        replaceAll("\\.\\$tilde", " ~ ").
        replaceAll("\\(SqlParser([^(]+)\\(([^)]+)\\)\\)", "SqlParser$1($2)").
        replaceAll("\\.\\$plus\\(([0-9]+)\\)", " + $1").
        replaceAll("\\(([^ ]+) @ _\\)", "($1)").
        replaceAll("\\$tilde", "~")

      c.echo(c.enclosingPosition, s"row parser generated for $tpe: $generated")
    }

    c.Expr(c.typecheck(parser))
  }

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.namedParser[YourCaseClass]
   * }}}
   */
  def namedParser[T]: RowParser[T] = macro namedParserImpl[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by name.
   *
   * @tparam T $caseTParam
   * @param naming $namingParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.namedParser[YourCaseClass]
   * }}}
   */
  def namedParser[T](naming: Macro.ColumnNaming): RowParser[T] = macro namedParserImpl1[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values according the property `names`.
   *
   * @tparam T $caseTParam
   * @param names $namesParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] =
   *   Macros.parser[YourCaseClass]("foo", "bar")
   * }}}
   */
  def parser[T](names: String*): RowParser[T] = macro namedParserImpl2[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values according the property `names`.
   *
   * @tparam T $caseTParam
   *
   * @param naming $namingParam
   * @param names $namesParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] =
   *   Macros.parser[YourCaseClass]("foo", "loremIpsum")
   * }}}
   */
  def parser[T](naming: Macro.ColumnNaming, names: String*): RowParser[T] = macro namedParserImpl3[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position.
   *
   * @tparam T $caseTParam
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.indexedParser[YourCaseClass]
   * }}}
   */
  def indexedParser[T]: RowParser[T] = macro indexedParserImpl[T]

  /**
   * Returns a row parser generated for a case class `T`,
   * getting column values by position, with an offset.
   *
   * @tparam T $caseTParam
   * @param offset the offset of column to be considered by the parser
   *
   * {{{
   * import anorm.{ Macros, RowParser }
   *
   * val p: RowParser[YourCaseClass] = Macros.offsetParser[YourCaseClass](2)
   * }}}
   */
  def offsetParser[T](offset: Int): RowParser[T] = macro offsetParserImpl[T]

  private lazy val debugEnabled =
    Option(System.getProperty("anorm.macro.debug")).
      filterNot(_.isEmpty).map(_.toLowerCase).map { v =>
        "true".equals(v) || v.substring(0, 1) == "y"
      }.getOrElse(false)

}
