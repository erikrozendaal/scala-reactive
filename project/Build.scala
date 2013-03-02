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
      libraryDependencies ++= Seq(
        "joda-time" % "joda-time" % "2.1",
        "org.joda" % "joda-convert" % "1.2",
        "org.slf4j" % "slf4j-api" % "1.6.6",
        "org.slf4j" % "slf4j-log4j12" % "1.6.6",
        "net.liftweb" %% "lift-json" % "2.5-M4",
        "com.ning" % "async-http-client" % "1.7.6",
        "junit" % "junit" % "4.11" % "test",
        "org.scala-tools.testing" %% "specs" % "1.6.9" % "test",
        "org.scalacheck" %% "scalacheck" % "1.10.0" % "test",
        "org.mockito" % "mockito-all" % "1.9.0" % "test")))
}
