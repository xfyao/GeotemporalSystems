name := "GeotemporalSystems"

version := "1.0"

scalaVersion := "2.11.7"

val akkaVersion = "2.3.6"
val sprayVersion = "1.3.1"

resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"

/* dependencies */
libraryDependencies ++= Seq (

  // -- Akka --
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  // -- Json --
  "org.json4s" %% "json4s-native" % "3.2.11",
  // -- Unit Test --
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  // -- GeoHash --
  "ch.hsr" % "geohash" % "1.1.0",
  // -- Redis client - a nonblocking Scala implementation --
  "com.etaty.rediscala" %% "rediscala" % "1.5.0",
  // -- Pickling --
  "org.scala-lang.modules" %% "scala-pickling" % "0.10.1",
  // -- net.lift4j --
  "net.liftweb" % "lift-json_2.11" % "2.6-M4",
  // -- Spray --
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-can" % sprayVersion,
  "io.spray" %% "spray-httpx" % sprayVersion,
  "io.spray" %% "spray-testkit" % sprayVersion % "test",
  // -- logback --
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  // -- Redis client - a high performance Java implementation --
  "redis.clients" % "jedis" % "2.7.2"
)