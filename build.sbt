val scala3 = "3.3.3"
val scala213 = "2.13.14"
val scala212 = "2.12.13"
val scala211 = "2.11.12"

inThisBuild(
  Seq(
    organization := "com.github.lolgab",
    version := "0.2.1",
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala3, scala213, scala212, scala211),
  )
)

val publishSettings = Seq(
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  homepage := Some(url("https://github.com/scala-native/scala-native-loop")),
  licenses := Seq(
    "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
  ),
  publishTo := sonatypePublishToBundle.value,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scala-native/scala-native-loop"),
      "scm:git:git@github.com:scala-native/scala-native-loop.git"
    )
  )
)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "utf8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    // "-Wunused:imports"
  ),
  libraryDependencies += "com.lihaoyi" %%% "utest" % "0.8.3" % Test,
  testFrameworks += new TestFramework("utest.runner.Framework"),
)

lazy val core = project
  .in(file("core"))
  .settings(name := "native-loop-core")
  .settings(commonSettings)
  .settings(publishSettings)
  .enablePlugins(ScalaNativePlugin)

lazy val scalaJsCompat = project
  .in(file("scalajs-compat"))
  .settings(name := "native-loop-js-compat")
  .settings(commonSettings)
  .settings(publishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)
