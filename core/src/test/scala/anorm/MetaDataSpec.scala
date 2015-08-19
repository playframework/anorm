package anorm

object MetaDataSpec extends org.specs2.mutable.Specification {
  "Meta-data" title

  "Meta-data" should {
    val item1 = MetaDataItem(ColumnName(
      "TEST1.FOO", Some("ALI")), true, "java.lang.String")

    val item2 = MetaDataItem(ColumnName(
      "TEST1.FOO", Some("FOO")), true, "java.lang.String")

    val item3 = MetaDataItem(ColumnName(
      "TEST1.FOO", Some("IAS")), true, "java.lang.String")

    val meta = MetaData(List(item1, item2, item3))

    "be support colum aliases" in {
      meta.get("ALI") aka "ALI" must beSome(item1) and (
        meta.get("FOO") aka "FOO" must beSome(item2)) and (
          meta.get("IAS") aka "IAS" must beSome(item3))
    }
  }
}
