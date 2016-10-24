package scalaGuide.main.sql
//#playdb
import play.api.db.Database
import anorm._
import javax.inject.{Inject, Singleton}

@Singleton
class ScalaAnorm  @Inject () (db: Database) {
  def test(): Boolean = {
    db.withConnection { implicit connection =>
      SQL("Select 1").execute()
    }
  }
}
//#playdb
