name := "akka-twitter-scoring-stream"

resolvers += Resolver.sonatypeRepo("releases")

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.danielasfregola" %% "twitter4s" % "7.0",
  "com.github.tototoshi" %% "scala-csv" % "1.3.6"
)

