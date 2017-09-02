package anorm.macros

import scala.reflect.api.Universe

final case class Implicit[Type <: Universe#TypeApi, Name <: Universe#NameApi, Tree <: Universe#TreeApi](
  paramName: Name,
  paramType: Type,
  neededImplicit: Tree,
  tpe: Type,
  selfRef: Boolean)

object Implicit {
  object Unresolved {
    def unapply[Type <: Universe#TypeApi, Name <: Universe#NameApi, Tree <: Universe#TreeApi](impl: Implicit[Type, Name, Tree]): Boolean = impl.neededImplicit.isEmpty
  }
}
