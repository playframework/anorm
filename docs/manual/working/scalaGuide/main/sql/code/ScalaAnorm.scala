package scalaGuide.main.sql
//#playdb
import javax.inject.Inject
import anorm._
import play.api.db.Database

class ScalaAnorm @Inject()(db: Database) {
  db.withConnection { implicit connection =>
    SQL("Select 1").execute()
  }
}
//#playdb
