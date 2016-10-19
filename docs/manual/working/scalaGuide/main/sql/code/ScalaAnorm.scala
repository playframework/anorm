package scalaGuide.main.sql
//#playdb
import play.api.db.Database
import anorm._
import com.google.inject.Inject

class ScalaAnorm  @Inject () (db: Database) {

  db.withConnection { implicit connection =>
    val result: Boolean = SQL("Select 1").execute()

  }
}
//#playdb
