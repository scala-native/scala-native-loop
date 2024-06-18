val scala3   = "3.3.3"
val scala213 = "2.13.14"
val scala212 = "2.12.19"

inThisBuild(
  Seq(
    organization := "com.github.lolgab",
    version := "0.3.0",
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala3, scala213, scala212),
    versionScheme := Some("early-semver")
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
  ),
  developers := List(
    Developer(
      id = "lolgab",
      name = "Lorenzo Gabriele",
      email = "lorenzolespaul@gmail.com",
      url = url("https://github.com/lolgab")
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
    "-Xfatal-warnings"
    // "-Wunused:imports"
  ),
  libraryDependencies += "com.lihaoyi" %%% "utest" % "0.8.3" % Test,
  testFrameworks += new TestFramework("utest.runner.Framework")
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

lazy val root = project
  .in(file("."))
  .aggregate(core, scalaJsCompat)
  .settings(
    publish / skip := true
  )
