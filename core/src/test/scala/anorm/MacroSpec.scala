package anorm

import java.lang.{ Boolean => JBool, Long => JLong }
import java.sql.Connection

import acolyte.jdbc.{ DefinedParameter => DParam, ParameterMetaData => ParamMeta, RowLists, UpdateExecution }
import acolyte.jdbc.AcolyteDSL.{ connection, handleStatement, withQueryResult }
import acolyte.jdbc.Implicits._

import org.specs2.specification.core.Fragments

import com.github.ghik.silencer.silent

import Macro.ColumnNaming
import SqlParser.scalar

final class MacroSpec extends org.specs2.mutable.Specification {
  "Macro" title

  val barRow1 = RowLists.rowList1(classOf[Int] -> "v")

  val fooRow1 = RowLists.rowList5(
    classOf[Float]  -> "r",
    classOf[String] -> "bar",
    classOf[Int]    -> "loremIpsum",
    classOf[JLong]  -> "opt",
    classOf[JBool]  -> "x"
  ) // java types to avoid conv

  val fooRow2 = RowLists.rowList5(
    classOf[Float]  -> "r",
    classOf[String] -> "bar",
    classOf[Int]    -> "lorem_ipsum",
    classOf[JLong]  -> "opt",
    classOf[JBool]  -> "x"
  ) // java types to avoid conv

  "Column naming" should {
    import ColumnNaming._

    "be snake case with 'loremIpsum' transformed to 'lorem_ipsum'" in {
      SnakeCase("loremIpsum") must_=== "lorem_ipsum"
    }

    "be using a custom transformation" in {
      ColumnNaming(_.toUpperCase).apply("foo") must_=== "FOO"
    }
  }

  "Generated named parser" should {
    // No Column[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.namedParser[Foo[Bar]]")

    // Not enough column names for class parameters
    shapeless.test.illTyped("""anorm.Macro.parser[Foo[Int]]("Foo", "Bar")""")

    "be successful for Bar" in withQueryResult(barRow1 :+ 1 :+ 3) { implicit c =>
      val parser1 = Macro.namedParser[Bar]
      val parser2 = Macro.parser[Bar]("v")

      (SQL"TEST".as(parser1.*) must_=== List(Bar(1), Bar(3))).and(SQL"TEST".as(parser2.*) must_=== List(Bar(1), Bar(3)))
    }

    "be successful for Foo[Int]" >> {
      def spec(parser1: RowParser[Foo[Int]], parser2: RowParser[Foo[Int]])(implicit c: Connection) = {
        val expected = List(
          Foo(1.2f, "str1")(1, Some(2L))(Some(true)),
          Foo(2.3f, "str2")(4, None)(None),
          Foo(3.4f, "str3")(5, Some(3L))(None),
          Foo(5.6f, "str4")(6, None)(Some(false))
        )

        (SQL"TEST".as(parser1.*) must_=== expected).and(SQL("TEST").as(parser2.*) must_=== expected)
      }

      "using the default column naming" in withQueryResult(
        fooRow1 :+ (1.2f, "str1", 1, 2L, true) :+ (2.3f, "str2", 4,
        nullLong, nullBoolean) :+ (3.4f, "str3",
        5, 3L, nullBoolean) :+ (5.6f, "str4", 6,
        nullLong, false)
      ) { implicit con =>

        spec(Macro.namedParser[Foo[Int]], Macro.parser[Foo[Int]]("r", "bar", "loremIpsum", "opt", "x"))

      }

      "using the snake case naming" in withQueryResult(
        fooRow2 :+ (1.2f, "str1", 1, 2L, true) :+ (2.3f, "str2", 4,
        nullLong, nullBoolean) :+ (3.4f, "str3",
        5, 3L, nullBoolean) :+ (5.6f, "str4", 6,
        nullLong, false)
      ) { implicit con =>

        spec(
          Macro.namedParser[Foo[Int]](ColumnNaming.SnakeCase),
          Macro.parser[Foo[Int]](ColumnNaming.SnakeCase, "r", "bar", "loremIpsum", "opt", "x")
        )

      }
    }

    "use a sub-parser from the implicit scope" in {
      implicit val barParser: RowParser[Bar] = Macro.namedParser[Bar]
      val fooBar                             = Macro.namedParser[Foo[Bar]]

      val row = RowLists.rowList6(
        classOf[Float]   -> "r",
        classOf[String]  -> "bar",
        classOf[Int]     -> "loremIpsum",
        classOf[Long]    -> "opt",
        classOf[Boolean] -> "x",
        classOf[Int]     -> "v"
      )

      withQueryResult(row :+ (1.2f, "str1", 1, 2L, true, 6)) { implicit c =>
        SQL"TEST".as(fooBar.singleOpt) must beSome(Foo(1.2f, "str1")(Bar(6), Some(2))(Some(true)))
      }
    }

    "support self reference" in {
      val _ = Macro.namedParser[Self] // check compile is ok

      ok // TODO: Supports aliasing to make it really usable (see #124)
    }
  }

  "Generated indexed parser" should {
    // No Column[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.indexedParser[Foo[Bar]]")

    "be successful for Bar" in withQueryResult(RowLists.intList :+ 1 :+ 3) { implicit c =>
      SQL"TEST".as(Macro.indexedParser[Bar].*) must_=== List(Bar(1), Bar(3))
    }

    "be successful for Foo[Int]" in withQueryResult(
      fooRow1 :+ (1.2f, "str1", 1, 2L, true) :+ (2.3f, "str2", 4,
      nullLong,
      nullBoolean) :+ (3.4f, "str3", 5, 3L,
      nullBoolean) :+ (5.6f, "str4", 6,
      nullLong, false)
    ) { implicit con =>
      val parser: RowParser[Foo[Int]] = Macro.indexedParser[Foo[Int]]

      SQL"TEST".as(parser.*) must_=== List(
        Foo(1.2f, "str1")(1, Some(2L))(Some(true)),
        Foo(2.3f, "str2")(4, None)(None),
        Foo(3.4f, "str3")(5, Some(3L))(None),
        Foo(5.6f, "str4")(6, None)(Some(false))
      )
    }
  }

  "Generated indexed parser (with an offset)" should {
    // No Column[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.offsetParser[Foo[Bar]]")

    "be successful for Bar" in withQueryResult(RowLists.intList :+ 1 :+ 3) { implicit c =>
      SQL"TEST".as(Macro.offsetParser[Bar](0).*) must_=== List(Bar(1), Bar(3))
    }

    "be successful for Foo[Int]" in withQueryResult(
      fooRow1 :+ (1.2f, "str1", 1, 2L, true) :+ (2.3f, "str2", 4,
      nullLong,
      nullBoolean) :+ (3.4f, "str3", 5, 3L,
      nullBoolean) :+ (5.6f, "str4", 6,
      nullLong, false)
    ) { implicit con =>
      val parser: RowParser[Foo[Int]] = Macro.offsetParser[Foo[Int]](0)

      SQL"TEST".as(parser.*) must_=== List(
        Foo(1.2f, "str1")(1, Some(2L))(Some(true)),
        Foo(2.3f, "str2")(4, None)(None),
        Foo(3.4f, "str3")(5, Some(3L))(None),
        Foo(5.6f, "str4")(6, None)(Some(false))
      )
    }

    "be successful for Goo[T] with offset = 2" in withQueryResult(
      fooRow1 :+ (1.2f, "str1", 1, 2L, true) :+ (2.3f, "str2", 4, nullLong, nullBoolean) :+ (3.4f, "str3", 5, 3L, nullBoolean) :+ (5.6f, "str4", 6, nullLong, false)
    ) { implicit con =>
      val parser: RowParser[Goo[Int]] = Macro.offsetParser[Goo[Int]](2)

      SQL"TEST".as(parser.*) must_=== List(
        Goo(1, Some(2L), Some(true)),
        Goo(4, None, None),
        Goo(5, Some(3L), None),
        Goo(6, None, Some(false))
      )
    }
  }

  "Discriminator naming" should {
    import Macro.DiscriminatorNaming

    "be 'classname' by default" in {
      DiscriminatorNaming.Default("foo") must_=== "classname"
    }

    "be customized" in {
      val naming = DiscriminatorNaming { _ => "foo" }
      naming("bar") must_=== "foo"
    }
  }

  "Discriminate function" should {
    import Macro.Discriminate

    "be identity by default" in {
      Discriminate.Identity("x.y.z.Type") must_=== "x.y.z.Type"
    }

    "be customized" in {
      val discriminate = Discriminate(_.split("\\.").last)
      discriminate("x.y.z.Type") must_=== "Type"
    }
  }

  "Sealed parser" should {
    // No subclass
    shapeless.test.illTyped("anorm.Macro.sealedParser[NoSubclass]")

    // Cannot find the RowParser instances for the subclasses,
    // from the implicit scope
    shapeless.test.illTyped("Macro.sealedParser[Family]")

    // No subclass
    shapeless.test.illTyped("Macro.sealedParser[EmptyFamily]")

    "be successful for the Family trait" >> {
      "with the default discrimination" in {
        val barRow2 = RowLists.rowList2(classOf[String] -> "classname", classOf[Int] -> "v")

        withQueryResult(barRow2 :+ ("anorm.MacroSpec.Bar", 1) :+ ("anorm.MacroSpec.CaseObj", -1)) { implicit c =>
          implicit val caseObjParser =
            RowParser[CaseObj.type] { _ => Success(CaseObj) }

          implicit val barParser = Macro.namedParser[Bar]

          // cannot handle object anorm.MacroSpec.NotCase: no case accessor
          @silent def familyParser = Macro.sealedParser[Family]

          SQL"TEST".as(familyParser.*) must_=== List(Bar(1), CaseObj)
        }
      }

      "with a customized discrimination" in {
        val barRow2 = RowLists.rowList2(classOf[String] -> "foo", classOf[Int] -> "v")

        withQueryResult(barRow2 :+ ("Bar", 1) :+ ("CaseObj", -1)) { implicit c =>
          implicit val caseObjParser =
            RowParser[CaseObj.type] { _ => Success(CaseObj) }

          implicit val barParser = Macro.namedParser[Bar]

          // cannot handle object anorm.MacroSpec.NotCase: no case accessor
          @silent def familyParser =
            Macro.sealedParser[Family](Macro.DiscriminatorNaming(_ => "foo"), Macro.Discriminate(_.split("\\.").last))

          SQL"TEST".as(familyParser.*) must_=== List(Bar(1), CaseObj)
        }
      }
    }
  }

  "Generated parameter encoders" should {
    import Macro.{ ParameterProjection => proj }
    import NamedParameter.{ namedWithString => named }

    // No ToParameterList[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.toParameters[Goo[Bar]]")

    "be successful for Bar" >> {
      val fixture = Bar(1)

      Fragments.foreach(
        Seq[(ToParameterList[Bar], List[NamedParameter])](
          Macro.toParameters[Bar]()               -> List(named("v" -> 1)),
          Macro.toParameters[Bar](proj("v", "w")) -> List(named("w" -> 1))
        ).zipWithIndex
      ) { case ((encoder, params), index) =>
        s"using encoder #${index}" in {
          encoder(fixture) must_=== params
        }
      }
    }

    "be successful for Goo[Int]" >> {
      val fixture = Goo(1, Some(2L), None)

      Fragments.foreach(
        Seq[(ToParameterList[Goo[Int]], List[NamedParameter])](
          Macro.toParameters[Goo[Int]]() -> List(
            named("loremIpsum" -> 1),
            named("opt"        -> Some(2L)),
            named("x"          -> Option.empty[Boolean])
          ),
          Macro.toParameters[Goo[Int]](proj("loremIpsum", "value")) -> List(named("value" -> 1))
        ).zipWithIndex
      ) { case ((encoder, params), index) =>
        s"using encoder #${index}" in {
          encoder(fixture) must_=== params
        }
      }
    }

    "be successful for sealed family" >> {
      // cannot handle object anorm.MacroSpec.NotCase: no case accessor
      @silent implicit def familyParams: ToParameterList[Family] = {
        implicit val barToParams: ToParameterList[Bar] = Macro.toParameters[Bar]
        implicit val caseObjParam                      = ToParameterList.empty[CaseObj.type]

        Macro.toParameters[Family]
      }

      Fragments.foreach(
        Seq[(Family, List[NamedParameter])](Bar(1) -> List(named("v" -> 1)), CaseObj -> List.empty[NamedParameter])
      ) { case (i, params) =>
        s"for $i" in {
          ToParameterList.from(i) must_=== params
        }
      }
    }

    "be successful for Goo[Bar]" >> {
      implicit def barToParams: ToParameterList[Bar] =
        Macro.toParameters[Bar]()

      val fixture = Goo(Bar(1), None, Some(false))

      Fragments.foreach(
        Seq[(ToParameterList[Goo[Bar]], List[NamedParameter])](
          Macro.toParameters[Goo[Bar]](proj("loremIpsum", "foo"), proj("opt", "bar")) -> List(
            named("foo_v" -> 1 /* Bar.v as Goo.{lorem=>foo} */ ),
            named("bar"   -> Option.empty[Long])
          ),
          Macro.toParameters[Goo[Bar]]("#") -> List(
            named("loremIpsum#v" -> 1),
            named("opt"          -> Option.empty[Long]),
            named("x"            -> Some(false))
          )
        ).zipWithIndex
      ) { case ((encoder, params), index) =>
        s"using encoder #${index}" in {
          encoder(fixture) must_=== params
        }
      }
    }
  }

  "Generated column" should {
    shapeless.test.illTyped("anorm.Macro.valueColumn[Bar]") // case class

    shapeless.test.illTyped("anorm.Macro.valueColumn[InvalidValueClass]")

    "be generated for a supported ValueClass" in {
      implicit val generated: Column[ValidValueClass] =
        Macro.valueColumn[ValidValueClass]

      withQueryResult(RowLists.doubleList :+ 1.2d) { implicit con =>
        SQL("SELECT d").as(scalar[ValidValueClass].single).aka("parsed column") must_=== new ValidValueClass(1.2d)
      }
    }
  }

  "ToStatement" should {
    shapeless.test.illTyped("anorm.Macro.valueToStatement[Bar]") // case class

    val SqlDouble3s = ParamMeta.Double(23.456d)

    def withConnection[A](f: java.sql.Connection => A): A = f(connection(handleStatement.withUpdateHandler {
      case UpdateExecution("set-double ?", DParam(23.456d, SqlDouble3s) :: Nil) => 1 /* case ok */

      case x => sys.error(s"Unexpected: $x")
    }))

    "be generated for a ValueClass" in {
      implicit val generated: ToStatement[ValidValueClass] =
        Macro.valueToStatement[ValidValueClass]

      withConnection { implicit c =>
        (SQL("set-double {p}").on("p" -> new ValidValueClass(23.456d)).execute() must beFalse)
      }
    }
  }

  // ---

  // Avoid implicit conversions
  lazy val nullBoolean = null.asInstanceOf[JBool]
  lazy val nullLong    = null.asInstanceOf[JLong]

  sealed trait NoSubclass
  object NotFamilly

  // Sealed family
  sealed trait Family
  case class Bar(v: Int) extends Family
  case object CaseObj    extends Family
  object NotCase         extends Family

  sealed trait EmptyFamily

  case class Foo[T](r: Float, bar: String = "Default")(loremIpsum: T, opt: Option[Long] = None)(x: Option[Boolean])
      extends Family {
    override lazy val toString = s"Foo($r, $bar)($loremIpsum, $opt)($x)"
  }

  case class Goo[T](loremIpsum: T, opt: Option[Long], x: Option[Boolean]) {
    override lazy val toString = s"Goo($loremIpsum, $opt, $x)"
  }

  // TODO: Supports aliasing to make it really usable (see #124)
  case class Self(id: String, next: Self)
}

final class ValidValueClass(val foo: Double) extends AnyVal

final class InvalidValueClass(val foo: MacroSpec) extends AnyVal {
  // No support as `foo` is not itself a ValueClass
}
