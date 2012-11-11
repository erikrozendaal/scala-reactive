import sbt._
import sbt.Keys._

object ProjectBuild extends Build {

  lazy val root = Project(
    id = "root",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "scala-reactive",
      organization := "org.deler",
      version := "0.3-SNAPSHOT",
      scalaVersion := "2.9.2",
      scalacOptions ++= Seq("-deprecation", "-unchecked"),
      libraryDependencies ++= Seq(
        "joda-time" % "joda-time" % "1.6.2",
        "org.slf4j" % "slf4j-api" % "1.6.1",
        "org.slf4j" % "slf4j-log4j12" % "1.6.1",
        "net.liftweb" % "lift-json_2.9.1" % "2.4",
        "com.ning" % "async-http-client" % "1.4.0",
        "junit" % "junit" % "4.10" % "test",
        "org.scala-tools.testing" % "specs_2.9.1" % "1.6.9" % "test",
        "org.scalacheck" %% "scalacheck" % "1.10.0" % "test",
        "org.mockito" % "mockito-all" % "1.8.5" % "test",
        "junit" % "junit" % "4.10" % "test")))
}
