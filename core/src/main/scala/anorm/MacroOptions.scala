package anorm

private[anorm] trait MacroOptions {

  /**
   * Naming strategy, to map each class property to the corresponding column.
   */
  trait ColumnNaming extends (String => String) {

    /**
     * Returns the column name for the class property.
     *
     * @param property the name of the case class property
     */
    def apply(property: String): String
  }

  /** Naming companion */
  object ColumnNaming {

    /** Keep the original property name. */
    object Identity extends ColumnNaming {
      def apply(property: String) = property
    }

    /**
     * For each class property, use the snake case equivalent
     * to name its column (e.g. fooBar -> foo_bar).
     */
    object SnakeCase extends ColumnNaming {
      private val re = "[A-Z]+".r

      def apply(property: String): String =
        re.replaceAllIn(property, { m => s"_${m.matched.toLowerCase}" })
    }

    /** Naming using a custom transformation function. */
    def apply(transformation: String => String): ColumnNaming =
      new ColumnNaming {
        def apply(property: String): String = transformation(property)
      }
  }

  trait Discriminate extends (String => String) {

    /**
     * Returns the value representing the specified type,
     * to be used as a discriminator within a sealed family.
     *
     * @param tname the name of type (class or object) to be discriminated
     */
    def apply(tname: String): String
  }

  object Discriminate {
    sealed class Function(f: String => String) extends Discriminate {
      def apply(tname: String) = f(tname)
    }

    /** Uses the type name as-is as value for the discriminator */
    object Identity extends Function(identity[String])

    /** Returns a `Discriminate` function from any `String => String`. */
    def apply(discriminate: String => String): Discriminate =
      new Function(discriminate)
  }

  trait DiscriminatorNaming extends (String => String) {

    /**
     * Returns the name for the discriminator column.
     * @param familyType the name of the famility type (sealed trait)
     */
    def apply(familyType: String): String
  }

  object DiscriminatorNaming {
    sealed class Function(f: String => String) extends DiscriminatorNaming {
      def apply(familyType: String) = f(familyType)
    }

    /** Always use "classname" as name for the discriminator column. */
    object Default extends Function(_ => "classname")

    /** Returns a naming according from any `String => String`. */
    def apply(naming: String => String): DiscriminatorNaming =
      new Function(naming)
  }

  /**
   * @param propertyName the name of the class property
   * @param parameterName the name of for the parameter,
   * if different from the property one, otherwise `None`
   */
  case class ParameterProjection(propertyName: String, parameterName: Option[String] = None)

  object ParameterProjection {
    def apply(propertyName: String, parameterName: String): ParameterProjection =
      ParameterProjection(propertyName, Option(parameterName))
  }
}
