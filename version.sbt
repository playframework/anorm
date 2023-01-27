// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

ThisBuild / dynverVTagPrefix := false

(ThisBuild / version) := {
  val Stable = """([0-9]+)\.([0-9]+)\.([0-9]+)""".r

  (ThisBuild / dynverGitDescribeOutput).value match {
    case Some(descr) => {
      if ((ThisBuild / isSnapshot).value) {
        (ThisBuild / previousStableVersion).value match {
          case Some(previousVer) => {
            val current = (for {
              Seq(maj, min, patch) <- Stable.unapplySeq(previousVer)
              nextPatch            <- scala.util.Try(patch.toInt).map(_ + 1).toOption
            } yield {
              val suffix = descr.commitSuffix.sha
              s"${maj}.${min}.${nextPatch}-${suffix}-SNAPSHOT"
            }).getOrElse {
              sys.error("Fails to determine qualified snapshot version")
            }

            current
          }

          case _ =>
            sys.error("Fails to determine previous stable version")
        }
      } else {
        descr.ref.value // without 'v' prefix
      }
    }

    case _ =>
      sys.error("Fails to resolve Git information")
  }
}
