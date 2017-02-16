package scalaGuide.sql.anorm

import play.api.Application
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test._
import play.api.test.Helpers._

class ScalaAnorm extends org.specs2.mutable.Specification {
  "Code samples" title

  def createApp(additionalConfiguration: Map[String, _]): Application = {
    new GuiceApplicationBuilder().configure(additionalConfiguration).build()
  }

  "Anorm" should {
    "be usable in play" in new WithApplication(createApp(additionalConfiguration = inMemoryDatabase())) {
      val database = app.injector.instanceOf[Database]
      //#playdb
      import anorm._

      database.withConnection { implicit c =>
        val result: Boolean = SQL("Select 1").execute()
      }
      //#playdb
      ok
    }
  }
}

object MacroFixtures {
  //#macroSealedParser
  import anorm._

  sealed trait Family
  case class Bar(v: Int) extends Family
  case object Lorem extends Family

  // First, RowParser instances for all the subtypes must be provided,
  // either by macros or by custom parsers
  implicit val barParser = Macro.namedParser[Bar]
  implicit val loremParser = RowParser[Lorem.type] {
    anyRowDiscriminatedAsLorem => Success(Lorem)
  }

  val familyParser = Macro.sealedParser[Family]

  // Generate a parser as following...
  val generated: RowParser[Family] =
    SqlParser.str("classname").flatMap { discriminator: String =>
      discriminator match {
        case "scalaguide.sql.MacroFixtures.Bar" =>
          implicitly[RowParser[Bar]]

        case "scalaguide.sql.MacroFixtures.Lorem" =>
          implicitly[RowParser[Lorem.type]]

        case (d) => RowParser.failed[Family](Error(SqlMappingError(
          "unexpected row type \'%s\'; expected: %s".format(d, "scalaguide.sql.MacroFixtures.Bar, scalaguide.sql.MacroFixtures.Lorem")
        )))
      }
    }
  //#macroSealedParser
}
