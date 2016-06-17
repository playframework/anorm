package anorm

import java.sql.{ DriverManager, Connection }
import scala.util.Random

trait H2Database {
  def withH2Database[R](block: Connection => R): R = {
    val url = "jdbc:h2:mem:test" + Random.alphanumeric.take(6).mkString("")
    val connection = DriverManager.getConnection(url, "sa", "")

    try {
      block(connection)
    } finally {
      connection.close()
    }
  }

  case class TestTable(id: Long, foo: String, bar: Int)

  /** Create a simple 'test1' table for testing with. */
  def createTest1Table()(implicit conn: Connection): Unit = createTable("test1", "id bigint", "foo varchar", "bar int")

  /** Create a simple 'test2' table for testing with. */
  def createTest2Table()(implicit conn: Connection): Unit = createTable("test2", "id bigint", "foo varchar")

  protected def createTable(name: String, columns: String*)(implicit conn: Connection): Unit = conn.createStatement().execute(s"""create table $name (${columns mkString ", "});""")

}
