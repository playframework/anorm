package scalaguide.sql.anorm

import org.specs2.mutable.Specification
import play.api.test._
import play.api.test.Helpers._

object ScalaAnorm extends Specification {
  "Code samples" title

  "Anorm" should {
    "be usable in play" in new WithApplication(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
      //#playdb
      import anorm._
      import play.api.db.DB

      DB.withConnection { implicit c =>
        val result: Boolean = SQL("Select 1").execute()
      }
      //#playdb
      ok
    }
  }
}
