import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info) { 
  val joda       = "joda-time" % "joda-time" % "1.6.2" 
  val slf4j_api  = "org.slf4j" % "slf4j-api" % "1.6.1"
  val slf4j      = "org.slf4j" % "slf4j-log4j12" % "1.6.1"
  val json       = "net.liftweb" %% "lift-json" % "2.2-RC5"
  val http       = "com.ning" % "async-http-client" % "1.4.0"
  val junit      = "junit" % "junit" % "4.5" % "test"
  val specs      = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"
  val scalacheck = "org.scala-tools.testing" % "scalacheck_2.8.0" % "1.7" % "test"
  val mock       = "org.mockito" % "mockito-all" % "1.8.5" % "test"
}
