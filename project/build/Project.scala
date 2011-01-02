import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info) { 
  val joda       = "joda-time" % "joda-time" % "1.6.2" 
  val slf4j_api  = "org.slf4j" % "slf4j-api" % "1.6.1"
  val slf4j      = "org.slf4j" % "slf4j-log4j12" % "1.6.1"
  val junit      = "junit" % "junit" % "4.5"
  val specs      = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"
  val scalacheck = "org.scala-tools.testing" % "scalacheck_2.8.0" % "1.7"
  val mock       = "org.mockito" % "mockito-all" % "1.8.5" % "test"
}
