lazy val commonSettings = Seq(
  organization := "dev.whaling",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.12"
)

lazy val core = (project in file("core"))
                .settings(commonSettings:_*)
                .enablePlugins(ScalaNativePlugin)

lazy val pipe = (project in file("pipe"))
                     .settings(commonSettings:_*)
                     .enablePlugins(ScalaNativePlugin)
                     .dependsOn(core)

lazy val client = (project in file("client"))
                 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core)

lazy val server = (project in file("server"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core)

lazy val serverExample = (project in file("examples/server"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core,server,client)

lazy val pipeExample = (project in file("examples/pipe"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core,pipe,client)

lazy val curlExample = (project in file("examples/curl"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core,client)

lazy val timerExample = (project in file("examples/timer"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core)
