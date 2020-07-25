homepage := Some(url("https://github.com/scala-native/scala-native-loop"))
licenses := Seq(
  "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
)

Global / onChangedBuildSource := ReloadOnSourceChanges

publishMavenStyle := true
Test / publishArtifact := false
pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
scmInfo := Some(
  ScmInfo(
    url("https://github.com/scala-native/scala-native-loop"),
    "scm:git:git@github.com:scala-native/scala-native-loop.git"
  )
)
developers := List(
  Developer(
    "rwhaling",
    "Richard Whaling",
    "richard@whaling.dev",
    url("http://whaling.dev")
  )
)

lazy val commonSettings = Seq(
  organization := "dev.whaling",
  version := "0.1.1-SNAPSHOT",
  scalaVersion := "2.11.12",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "utf8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Ywarn-unused-import"
  ),
  Compile / doc / scalacOptions -= "-Xfatal-warnings",
  libraryDependencies += "com.lihaoyi" %%% "utest" % "0.7.4" % Test,
  testFrameworks += new TestFramework("utest.runner.Framework"),
  Test / nativeLinkStubs := true,
  publish / skip := true,
  publishLocal / skip := true
)

lazy val core = project
  .in(file("core"))
  .settings(name := "native-loop-core")
  .settings(commonSettings)
  .settings(publish / skip := false)
  .settings(publishLocal / skip := false)
  .enablePlugins(ScalaNativePlugin)

lazy val pipe = project
  .in(file("pipe"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val client = project
  .in(file("client"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val server = project
  .in(file("server"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val scalaJsCompat = project
  .in(file("scalajs-compat"))
  .settings(name := "native-loop-js-compat")
  .settings(commonSettings)
  .settings(publish / skip := false)
  .settings(publishLocal / skip := false)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val serverExample = project
  .in(file("examples/server"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core, server, client)

lazy val pipeExample = project
  .in(file("examples/pipe"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core, pipe, client)

lazy val curlExample = project
  .in(file("examples/curl"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core, client)

lazy val timerExample = project
  .in(file("examples/timer"))
  .settings(commonSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)
