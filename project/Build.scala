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
      scalaVersion := "2.10.1-RC2",
      scalacOptions ++= Seq("-Xlint", "-deprecation", "-unchecked", "-feature"),
      fork in Test := true,
      libraryDependencies ++= Seq(
        "joda-time" % "joda-time" % "2.1",
        "org.joda" % "joda-convert" % "1.3",
        "org.slf4j" % "slf4j-api" % "1.7.2",
        "org.slf4j" % "slf4j-log4j12" % "1.7.2",
        "net.liftweb" %% "lift-json" % "2.5-M4",
        "com.ning" % "async-http-client" % "1.7.10",
        "junit" % "junit" % "4.11" % "test",
        "org.specs2" %% "specs2" % "1.14" % "test",
        "org.scalacheck" %% "scalacheck" % "1.10.0" % "test",
        "org.mockito" % "mockito-all" % "1.9.5" % "test")))
}
