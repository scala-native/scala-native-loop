lazy val commonSettings = Seq(
  organization := "dev.whaling",
  version := "preview",
  scalaVersion := "2.11.12",
  libraryDependencies += "io.argonaut" % "argonaut_native0.3_2.11" % "6.2.3"
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

lazy val serverExample = (project in file("server-example"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core,server,client)

lazy val pipeExample = (project in file("pipe-example"))
             	 	 .settings(commonSettings:_*)
                 .enablePlugins(ScalaNativePlugin)
                 .dependsOn(core,pipe,client)
