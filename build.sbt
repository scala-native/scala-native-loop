Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  Seq(
    organization := "com.github.lolgab",
    version := "0.1.1"
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
      url("https://github.com/lolgab/scala-native-loop"),
      "scm:git:git@github.com:lolgab/scala-native-loop.git"
    )
  ),
  developers := List(
    Developer(
      "rwhaling",
      "Richard Whaling",
      "richard@whaling.dev",
      url("http://whaling.dev")
    )
  )
)

val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  skip in publish := true
)


lazy val commonSettings = Seq(
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
  Compile / doc / sources := Seq.empty,
  libraryDependencies += "com.lihaoyi" %%% "utest" % "0.7.5" % Test,
  testFrameworks += new TestFramework("utest.runner.Framework"),
  Test / nativeLinkStubs := true,
)

lazy val examplesSettings = Seq(
  test := {}
)

lazy val core = project
  .in(file("core"))
  .settings(name := "native-loop-core")
  .settings(commonSettings)
  .settings(publishSettings)
  .enablePlugins(ScalaNativePlugin)

lazy val pipe = project
  .in(file("pipe"))
  .settings(commonSettings)
  .settings(test := {})
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val client = project
  .in(file("client"))
  .settings(commonSettings)
  .settings(test := {})
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val server = project
  .in(file("server"))
  .settings(commonSettings)
  .settings(test := {})
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val scalaJsCompat = project
  .in(file("scalajs-compat"))
  .settings(name := "native-loop-js-compat")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(test := {})
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)

lazy val serverExample = project
  .in(file("examples/server"))
  .settings(
    commonSettings,
    examplesSettings
  )
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core, server, client)

lazy val pipeExample = project
  .in(file("examples/pipe"))
  .settings(
    commonSettings,
    examplesSettings
  )
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core, pipe, client)

lazy val curlExample = project
  .in(file("examples/curl"))
  .settings(
    commonSettings,
    examplesSettings
  )
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core, client)

lazy val timerExample = project
  .in(file("examples/timer"))
  .settings(
    commonSettings,
    examplesSettings
  )
  .settings(noPublishSettings)
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(core)
