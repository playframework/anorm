package anorm

import java.lang.{ Boolean => JBool, Long => JLong }

import java.sql.Connection

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.RowLists
import acolyte.jdbc.Implicits._

import Macro.ColumnNaming

class MacroSpec extends org.specs2.mutable.Specification {
  "Macro" title

  val barRow = RowLists.rowList1(classOf[Int] -> "v")

  val fooRow1 = RowLists.rowList5(
    classOf[Float] -> "r", classOf[String] -> "bar",
    classOf[Int] -> "loremIpsum",
    classOf[JLong] -> "opt", classOf[JBool] -> "x") // java types to avoid conv

  val fooRow2 = RowLists.rowList5(
    classOf[Float] -> "r", classOf[String] -> "bar",
    classOf[Int] -> "lorem_ipsum",
    classOf[JLong] -> "opt", classOf[JBool] -> "x") // java types to avoid conv

  "Column naming" should {
    import ColumnNaming._

    "be snake case with 'loremIpsum' transformed to 'lorem_ipsum'" in {
      SnakeCase("loremIpsum") must_== "lorem_ipsum"
    }

    "be using a custom transformation" in {
      ColumnNaming(_.toUpperCase).apply("foo") must_== "FOO"
    }
  }

  "Generated named parser" should {
    // No Column[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.namedParser[Foo[Bar]]")

    // Not enough column names for class parameters
    shapeless.test.illTyped(
      """anorm.Macro.parser[Foo[Int]]("Foo", "Bar")""")

    "be successful for Bar" in withQueryResult(barRow :+ 1 :+ 3) { implicit c =>
      val parser1 = Macro.namedParser[Bar]
      val parser2 = Macro.parser[Bar]("v")

      SQL"TEST".as(parser1.*) must_== List(Bar(1), Bar(3)) and (
        SQL"TEST".as(parser2.*) must_== List(Bar(1), Bar(3)))
    }

    "be successful for Foo[Int]" >> {
      def spec(parser1: RowParser[Foo[Int]], parser2: RowParser[Foo[Int]])(implicit c: Connection) = {
        val expected = List(Foo(1.2F, "str1")(1, Some(2L))(Some(true)), Foo(2.3F, "str2")(4, None)(None), Foo(3.4F, "str3")(5, Some(3L))(None), Foo(5.6F, "str4")(6, None)(Some(false)))

        SQL"TEST".as(parser1.*) must_== expected and (
          SQL("TEST").as(parser2.*) must_== expected)
      }

      "using the default column naming" in withQueryResult(
        fooRow1 :+ (1.2F, "str1", 1, 2L, true) :+ (2.3F, "str2", 4,
          nullLong, nullBoolean) :+ (3.4F, "str3",
            5, 3L, nullBoolean) :+ (5.6F, "str4", 6,
              nullLong, false)) { implicit con =>

          spec(Macro.namedParser[Foo[Int]],
            Macro.parser[Foo[Int]]("r", "bar", "loremIpsum", "opt", "x"))

        }

      "using the snake case naming" in withQueryResult(
        fooRow2 :+ (1.2F, "str1", 1, 2L, true) :+ (2.3F, "str2", 4,
          nullLong, nullBoolean) :+ (3.4F, "str3",
            5, 3L, nullBoolean) :+ (5.6F, "str4", 6,
              nullLong, false)) { implicit con =>

          spec(Macro.namedParser[Foo[Int]](ColumnNaming.SnakeCase),
            Macro.parser[Foo[Int]](ColumnNaming.SnakeCase,
              "r", "bar", "loremIpsum", "opt", "x"))

        }
    }

    "use a sub-parser from the implicit scope" in {
      implicit val barParser: RowParser[Bar] = Macro.namedParser[Bar]
      val fooBar = Macro.namedParser[Foo[Bar]]

      val row = RowLists.rowList6(
        classOf[Float] -> "r", classOf[String] -> "bar",
        classOf[Int] -> "loremIpsum", classOf[Long] -> "opt",
        classOf[Boolean] -> "x", classOf[Int] -> "v")

      withQueryResult(row :+ (1.2F, "str1", 1, 2L, true, 6)) { implicit c =>
        SQL"TEST".as(fooBar.singleOpt) must beSome(
          Foo(1.2F, "str1")(Bar(6), Some(2))(Some(true)))
      }
    }
  }

  "Generated indexed parser" should {
    // No Column[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.indexedParser[Foo[Bar]]")

    "be successful for Bar" in withQueryResult(
      RowLists.intList :+ 1 :+ 3) { implicit c =>
        SQL"TEST".as(Macro.indexedParser[Bar].*) must_== List(Bar(1), Bar(3))
      }

    "be successful for Foo[Int]" in withQueryResult(
      fooRow1 :+ (1.2F, "str1", 1, 2L, true) :+ (
        2.3F, "str2", 4, nullLong,
        nullBoolean) :+ (3.4F, "str3", 5, 3L,
          nullBoolean) :+ (5.6F, "str4", 6,
            nullLong, false)) { implicit con =>
        val parser: RowParser[Foo[Int]] = Macro.indexedParser[Foo[Int]]

        SQL"TEST".as(parser.*) must_== List(Foo(1.2F, "str1")(1, Some(2L))(Some(true)), Foo(2.3F, "str2")(4, None)(None), Foo(3.4F, "str3")(5, Some(3L))(None), Foo(5.6F, "str4")(6, None)(Some(false)))
      }
  }

  "Generated indexed parser (with an offset)" should {
    // No Column[Bar] so compilation error is expected
    shapeless.test.illTyped("anorm.Macro.offsetParser[Foo[Bar]]")

    "be successful for Bar" in withQueryResult(
      RowLists.intList :+ 1 :+ 3) { implicit c =>
        SQL"TEST".as(Macro.offsetParser[Bar](0).*) must_== List(Bar(1), Bar(3))
      }

    "be successful for Foo[Int]" in withQueryResult(
      fooRow1 :+ (1.2F, "str1", 1, 2L, true) :+ (
        2.3F, "str2", 4, nullLong,
        nullBoolean) :+ (3.4F, "str3", 5, 3L,
          nullBoolean) :+ (5.6F, "str4", 6,
            nullLong, false)) { implicit con =>
        val parser: RowParser[Foo[Int]] = Macro.offsetParser[Foo[Int]](0)

        SQL"TEST".as(parser.*) must_== List(Foo(1.2F, "str1")(1, Some(2L))(Some(true)), Foo(2.3F, "str2")(4, None)(None), Foo(3.4F, "str3")(5, Some(3L))(None), Foo(5.6F, "str4")(6, None)(Some(false)))
      }

    "be successful for Goo[T] with offset = 2" in withQueryResult(
      fooRow1 :+ (1.2F, "str1", 1, 2L, true) :+ (
        2.3F, "str2", 4, nullLong, nullBoolean) :+ (
          3.4F, "str3", 5, 3L, nullBoolean) :+ (
            5.6F, "str4", 6, nullLong, false)) { implicit con =>
        val parser: RowParser[Goo[Int]] = Macro.offsetParser[Goo[Int]](2)

        SQL"TEST".as(parser.*) must_== List(Goo(1, Some(2L), Some(true)), Goo(4, None, None), Goo(5, Some(3L), None), Goo(6, None, Some(false)))
      }
  }

  // Avoid implicit conversions
  lazy val nullBoolean = null.asInstanceOf[JBool]
  lazy val nullLong = null.asInstanceOf[JLong]

  case class Bar(v: Int)
  case class Foo[T](r: Float, bar: String = "Default")(
      loremIpsum: T, opt: Option[Long] = None)(x: Option[Boolean]) {
    override lazy val toString = s"Foo($r, $bar)($loremIpsum, $opt)($x)"
  }
  case class Goo[T](loremIpsum: T, opt: Option[Long], x: Option[Boolean]) {
    override lazy val toString = s"Goo($loremIpsum, $opt, $x)"
  }
}
