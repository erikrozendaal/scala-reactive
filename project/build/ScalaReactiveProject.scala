import sbt._

class ScalaReactiveProject(info: ProjectInfo)
extends DefaultProject(info)
{
  val jodaTime = "joda-time" % "joda-time" % "1.6.2"
  val slf4jApi = "org.slf4j" % "slf4j-api" % "1.6.1"
  val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % "1.6.1"
  val junit = "junit" % "junit" % "4.8.1" % "test"
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.6" % "test"
  val mockito = "org.mockito" % "mockito-all" % "1.8.5" % "test"
  val scalacheck = "org.scala-tools.testing" %% "scalacheck" % "1.7" % "test"
}
