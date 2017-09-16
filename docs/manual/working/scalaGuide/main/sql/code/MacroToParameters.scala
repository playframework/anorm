package scalaGuide.sql.anorm

object MacroToParameters {
  //#caseClassToParameters1
  import anorm.{ Macro, SQL, ToParameterList }
  import anorm.NamedParameter

  case class Bar(v: Int)

  val bar1 = Bar(1)

  // Convert all supported properties as parameters
  val toParams1: ToParameterList[Bar] = Macro.toParameters[Bar]

  val params1: List[NamedParameter] = toParams1(bar1)
  // --> List(NamedParameter(v,ParameterValue(1)))

  val names1: List[String] = params1.map(_.name)
  // --> List(v)

  val placeholders = names1.map { n => s"{$n}" } mkString ", "
  // --> "{v}"

  val generatedStmt = s"""INSERT INTO bar(${names1 mkString ", "}) VALUES ($placeholders)"""
  val generatedSql1 = SQL(generatedStmt).on(params1: _*)
  //#caseClassToParameters1

  //#caseClassToParameters2
  // Convert only `v` property as `w`
  implicit val toParams2: ToParameterList[Bar] = Macro.toParameters(
    Macro.ParameterProjection("v", "w"))

  toParams2(bar1)
  // --> List(NamedParameter(w,ParameterValue(1)))
  
  val insert1 = SQL("INSERT INTO table(col_w) VALUES ({w})").
    bind(bar1) // bind bar1 as params implicit toParams2
  //#caseClassToParameters2

  //#caseClassToParameters3
  case class Foo(n: Int, bar: Bar)

  val foo1 = Foo(2, bar1)

  // For Nested case class
  val toParams3: ToParameterList[Foo] =
    Macro.toParameters() // uses `toParams2` from implicit scope for `bar` property

  toParams3(foo1)
  // --> List(NamedParameter(n,ParameterValue(2)), NamedParameter(bar_w,ParameterValue(1)))
  // * bar_w = Bar.{v=>w} with Bar instance itself as `bar` property of Foo
  //#caseClassToParameters3

  //#caseClassToParameters4
  // With parameter projection (aliases) and custom separator # instead of the default _
  val toParams4: ToParameterList[Foo] =
    Macro.toParameters("#", Macro.ParameterProjection("bar", "lorem"))

  toParams4(foo1)
  // --> List(NamedParameter(lorem#w,ParameterValue(1)))
  //#caseClassToParameters4

  //#sealedFamily1
  sealed trait Family
  case class Sub1(v: Int) extends Family
  case object Sub2 extends Family

  val sealedToParams: ToParameterList[Family] = {
    // the instances for the subclasses need to be in the implicit scope first
    implicit val sub1ToParams = Macro.toParameters[Sub1]
    implicit val sub2ToParams = ToParameterList.empty[Sub2.type]

    Macro.toParameters[Family]
  }
  //#sealedFamily1
}
