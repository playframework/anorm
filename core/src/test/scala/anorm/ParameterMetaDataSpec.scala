/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

import java.util.{ Date => JDate, UUID => JUUID }

import java.math.{ BigDecimal => JBigDec, BigInteger }

import java.net.{ URI, URL }

import java.lang.{
  Boolean => JBool,
  Byte => JByte,
  Character,
  Double => JDouble,
  Float => JFloat,
  Integer,
  Long => JLong,
  Short => JShort
}
import java.sql.Timestamp

import org.specs2.matcher.TypecheckMatchers._

final class ParameterMetaDataSpec extends org.specs2.mutable.Specification {
  "Parameter metadata".title

  import TestUtils.typecheck

  "Metadata" should {
    "not be resolved" in {
      typecheck("implicitly[ParameterMetaData[Any]]") must failWith(".*not\\ found.*")
    }

    "be provided for parameter" >> {
      "of type Array of Byte" in {
        Option(implicitly[ParameterMetaData[Array[Byte]]].sqlType).aka("SQL type") must beSome
      }

      "of type Blob" in {
        Option(implicitly[ParameterMetaData[java.sql.Blob]].sqlType).aka("SQL type") must beSome
      }

      "of type Boolean" in {
        Option(implicitly[ParameterMetaData[Boolean]].sqlType).aka("SQL type") must beSome
      }

      "of type Clob" in {
        Option(implicitly[ParameterMetaData[java.sql.Clob]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Boolean" in {
        Option(implicitly[ParameterMetaData[JBool]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Date" in {
        Option(implicitly[ParameterMetaData[JDate]].sqlType).aka("SQL type") must beSome
      }

      "of type Double" in {
        Option(implicitly[ParameterMetaData[Double]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Double" in {
        Option(implicitly[ParameterMetaData[JDouble]].sqlType).aka("SQL type") must beSome
      }

      "of type Float" in {
        Option(implicitly[ParameterMetaData[Float]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Float" in {
        Option(implicitly[ParameterMetaData[JFloat]].sqlType).aka("SQL type") must beSome
      }

      "of type Int" in {
        Option(implicitly[ParameterMetaData[Int]].sqlType).aka("SQL type") must beSome
      }

      "of type Byte" in {
        Option(implicitly[ParameterMetaData[Byte]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Byte" in {
        Option(implicitly[ParameterMetaData[JByte]].sqlType).aka("SQL type") must beSome
      }

      "of type InputStream" in {
        Option(implicitly[ParameterMetaData[java.io.InputStream]].sqlType).aka("SQL type") must beSome
      }

      "of type Integer" in {
        Option(implicitly[ParameterMetaData[Integer]].sqlType).aka("SQL type") must beSome
      }

      "of type Short" in {
        Option(implicitly[ParameterMetaData[Short]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Short" in {
        Option(implicitly[ParameterMetaData[JShort]].sqlType).aka("SQL type") must beSome
      }

      "of type BigInt" in {
        Option(implicitly[ParameterMetaData[BigInt]].sqlType).aka("SQL type") must beSome
      }

      "of type BigInteger" in {
        Option(implicitly[ParameterMetaData[BigInteger]].sqlType).aka("SQL type") must beSome
      }

      "of type Long" in {
        Option(implicitly[ParameterMetaData[Long]].sqlType).aka("SQL type") must beSome
      }

      "of type Java Long" in {
        Option(implicitly[ParameterMetaData[JLong]].sqlType).aka("SQL type") must beSome
      }

      "of type BigDecimal" in {
        Option(implicitly[ParameterMetaData[BigDecimal]].sqlType).aka("SQL type") must beSome
      }

      "of type Java BigDecimal" in {
        Option(implicitly[ParameterMetaData[JBigDec]].sqlType).aka("SQL type") must beSome
      }

      "of type Timestamp" in {
        Option(implicitly[ParameterMetaData[Timestamp]].sqlType).aka("SQL type") must beSome
      }

      "of type TimestampWrapper1" in {
        Option(implicitly[ParameterMetaData[TimestampWrapper1]].sqlType).aka("SQL type") must beSome
      }

      "of type Reader" in {
        Option(implicitly[ParameterMetaData[java.io.Reader]].sqlType).aka("SQL type") must beSome
      }

      "of type String" in {
        Option(implicitly[ParameterMetaData[String]].sqlType).aka("SQL type") must beSome
      }

      "of type UUID" in {
        Option(implicitly[ParameterMetaData[JUUID]].sqlType).aka("SQL type") must beSome
      }

      "of type URI" in {
        Option(implicitly[ParameterMetaData[URI]].sqlType).aka("SQL type") must beSome
      }

      "of type URL" in {
        Option(implicitly[ParameterMetaData[URL]].sqlType).aka("SQL type") must beSome
      }

      "of type Char" in {
        Option(implicitly[ParameterMetaData[Char]].sqlType).aka("SQL type") must beSome
      }

      "of type Character" in {
        Option(implicitly[ParameterMetaData[Character]].sqlType).aka("SQL type") must beSome
      }
    }
  }
}
