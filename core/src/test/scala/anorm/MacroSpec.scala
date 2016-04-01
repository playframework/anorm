package anorm

import acolyte.jdbc.AcolyteDSL.withQueryResult
import acolyte.jdbc.RowLists
import acolyte.jdbc.Implicits._

object MacroSpec extends org.specs2.mutable.Specification {
  "Macro" title

  val barRow = RowLists.rowList1(classOf[Int] -> "v")

  val bazRow = RowLists.rowList4(classOf[String] -> "s", classOf[Boolean] -> "b", classOf[Float] -> "f", classOf[Int] -> "i")

  val fooRow = RowLists.rowList5(
    classOf[Float] -> "r", classOf[String] -> "bar",
    classOf[Int] -> "lorem", classOf[Long] -> "opt",
    classOf[Boolean] -> "x")

  "Generated named parser" should {

    // Not enough column names for class parameters
    shapeless.test.illTyped(
      """anorm.Macro.parser[Foo[Int]]("Foo", "Bar")""")

    "be successful for Bar" in withQueryResult(barRow :+ 1 :+ 3) { implicit c =>
      val parser1 = Macro.namedParser[Bar]
      val parser2 = Macro.parser[Bar]("v")

      SQL"TEST".as(parser1.*) must_== List(Bar(1), Bar(3)) and (
        SQL"TEST".as(parser2.*) must_== List(Bar(1), Bar(3)))
    }

    "be successful for Baz" in withQueryResult(bazRow :+ ("test", false, 1.23F, 1)) { implicit c =>
      val parser1 = Macro.namedParser[Baz]
      val parser2 = Macro.parser[Baz]("s", "f", "i", "b")

      SQL"TEST".as(parser1.*) must_== List(Baz("test", Qux(1.23F, 1), false)) and (
        SQL"TEST".as(parser2.*) must_== List(Baz("test", Qux(1.23F, 1), false)))
    }

    "be successful for Foo[Int]" in withQueryResult(
      fooRow :+ (1.2F, "str1", 1, 2L, true) :+ (2.3F, "str2", 4,
        null.asInstanceOf[Long], null.asInstanceOf[Boolean]) :+ (3.4F, "str3",
          5, 3L, null.asInstanceOf[Boolean]) :+ (5.6F, "str4", 6,
            null.asInstanceOf[Long], false)) { implicit con =>

        val parser1: RowParser[Foo[Int]] = Macro.namedParser[Foo[Int]]
        val parser2: RowParser[Foo[Int]] =
          Macro.parser[Foo[Int]]("r", "bar", "lorem", "opt", "x")

        val expected = List(Foo(1.2F, "str1")(1, Some(2L))(Some(true)), Foo(2.3F, "str2")(4, None)(None), Foo(3.4F, "str3")(5, Some(3L))(None), Foo(5.6F, "str4")(6, None)(Some(false)))

        SQL"TEST".as(parser1.*) must_== expected and (
          SQL("TEST").as(parser2.*) must_== expected)
      }
  }

  "Generated indexed parser" should {

    "be successful for Bar" in withQueryResult(
      RowLists.intList :+ 1 :+ 3) { implicit c =>
        SQL"TEST".as(Macro.indexedParser[Bar].*) must_== List(Bar(1), Bar(3))
      }

    "be successful for Foo[Int]" in withQueryResult(
      fooRow :+ (1.2F, "str1", 1, 2L, true) :+ (
        2.3F, "str2", 4, null.asInstanceOf[Long],
        null.asInstanceOf[Boolean]) :+ (3.4F, "str3", 5, 3L,
          null.asInstanceOf[Boolean]) :+ (5.6F, "str4", 6,
            null.asInstanceOf[Long], false)) { implicit con =>
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
      fooRow :+ (1.2F, "str1", 1, 2L, true) :+ (
        2.3F, "str2", 4, null.asInstanceOf[Long],
        null.asInstanceOf[Boolean]) :+ (3.4F, "str3", 5, 3L,
          null.asInstanceOf[Boolean]) :+ (5.6F, "str4", 6,
            null.asInstanceOf[Long], false)) { implicit con =>
        val parser: RowParser[Foo[Int]] = Macro.offsetParser[Foo[Int]](0)

        SQL"TEST".as(parser.*) must_== List(Foo(1.2F, "str1")(1, Some(2L))(Some(true)), Foo(2.3F, "str2")(4, None)(None), Foo(3.4F, "str3")(5, Some(3L))(None), Foo(5.6F, "str4")(6, None)(Some(false)))
      }

    "be successful for Goo[T] with offset = 2" in withQueryResult(
      fooRow :+ (1.2F, "str1", 1, 2L, true) :+ (
        2.3F, "str2", 4, null.asInstanceOf[Long],
        null.asInstanceOf[Boolean]) :+ (3.4F, "str3", 5, 3L,
          null.asInstanceOf[Boolean]) :+ (5.6F, "str4", 6,
            null.asInstanceOf[Long], false)) { implicit con =>
        val parser: RowParser[Goo[Int]] = Macro.offsetParser[Goo[Int]](2)

        SQL"TEST".as(parser.*) must_== List(Goo(1, Some(2L), Some(true)), Goo(4, None, None), Goo(5, Some(3L), None), Goo(6, None, Some(false)))
      }
  }

  case class Baz(s: String, bar: Qux, b: Boolean)
  case class Qux(f: Float, i: Int)
  case class Bar(v: Int)
  case class Foo[T](r: Float, bar: String = "Default")(
      lorem: T, opt: Option[Long] = None)(x: Option[Boolean]) {
    override lazy val toString = s"Foo($r, $bar)($lorem, $opt)($x)"
  }
  case class Goo[T](lorem: T, opt: Option[Long], x: Option[Boolean]) {
    override lazy val toString = s"Goo($lorem, $opt, $x)"
  }
}
