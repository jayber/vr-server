name := """vr-server"""

version := "1.0-SNAPSHOT"

val copyVRClient = taskKey[Unit]("copies VR client files")

lazy val root = (project in file(".")).enablePlugins(PlayScala)
  .settings(
    copyVRClient := {
      IO.copyDirectory(baseDirectory.value / "../vr", baseDirectory.value / "/public")
    }
  )

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "com.amazonaws" % "aws-java-sdk-ses" % "1.11.125",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)

