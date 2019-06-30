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
    organization := "me.scf37.hottie",
    scalaVersion := "2.13.0",
    crossScalaVersions := Seq("2.13.0", "2.12.4"),
    resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/",
    scalacOptions ++= compilerOptions,
    libraryDependencies += "org.javassist" % "javassist" % "3.23.1-GA",
    libraryDependencies += "me.scf37.filewatch" %% "filewatch" % "1.0.9",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    libraryDependencies += "org.objenesis" % "objenesis" % "2.6",

    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",

    sbtClasspath := {
      val cp = (fullClasspath in Test).value.map(x => x.data.getAbsolutePath).mkString(":")
      System.setProperty("sbt-classpath", cp)
    },
    test := Def.sequential(sbtClasspath, test in Test).value,
    parallelExecution in Test := false,

    releaseTagComment := s"[ci skip]Releasing ${(version in ThisBuild).value}",
    releaseCommitMessage := s"[ci skip]Setting version to ${(version in ThisBuild).value}",
    resourceGenerators in Compile += buildProperties,
    bintrayOmitLicense := true,
    bintrayVcsUrl := Some("git@github.com:scf37/hottie.git")

  )