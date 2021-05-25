lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xlint"
)

lazy val sbtClasspath = taskKey[Unit]("sbtClasspath")

lazy val hottie = project.in(file("."))
  .settings(
    name := "hottie",
    organization := "me.scf37",
    scalaVersion := "3.0.0",
    crossScalaVersions := Seq("2.13.0", "2.12.4", "3.0.0"),
    resolvers += Resolver.sonatypeRepo("public"),
    scalacOptions ++= compilerOptions,
    libraryDependencies += "org.javassist" % "javassist" % "3.23.1-GA",
    libraryDependencies += "me.scf37" %% "filewatch" % "1.0.0",
    libraryDependencies += (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "org.scala-lang" % "scala-compiler" % scalaVersion.value
      case Some((3, _)) => "org.scala-lang" %% "scala3-compiler" % scalaVersion.value
      case _ => throw new RuntimeException("No Scala4 support yet, sorry!")
    }),
    libraryDependencies += "org.objenesis" % "objenesis" % "2.6",

    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % "test",

    sbtClasspath := {
      val cp = (Test / fullClasspath).value.map(x => x.data.getAbsolutePath).mkString(":")
      System.setProperty("sbt-classpath", cp)
    },
    test := Def.sequential(sbtClasspath, Test / test).value,
    Test / parallelExecution := false,

  )

lazy val publishSettings = Seq(
      organization := "me.scf37",
      publishMavenStyle := true,
      description := "Watch, recompile and reload scala files on the fly",
      Compile / doc / sources := Seq.empty,
      scmInfo := Some(
            ScmInfo(
                  url("https://github.com/scf37/hottie"),
                  "git@github.com:scf37/hottie.git"
            )
      ),
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("https://github.com/scf37/hottie")),
      developers := List(
            Developer("scf37", "Sergey Alaev", "scf370@gmail.com", url("https://github.com/scf37")),
      )
)
