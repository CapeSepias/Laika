
name := "laika"

organization := "org.planet42"

version := "0.1.0"

description := "Library for transforming lightweight text markup into various types of output formats, written in Scala"

scalaVersion := "2.9.2"

scalacOptions ++= Opts.compile.encoding("UTF-8") :+ Opts.compile.deprecation :+ Opts.compile.unchecked

libraryDependencies <+= scalaVersion {
  case v if v startsWith "2.10" => "org.scalatest" %% "scalatest" % "1.9.1" % "test"
  case _                        => "org.scalatest" %% "scalatest" % "1.8"   % "test"
}

crossVersion := CrossVersion.binary

crossScalaVersions := Seq("2.9.2", "2.10.0")


// Publishing to Sonatype OSS Repository

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>https://github.com/planet42/Laika</url>
  <licenses>
    <license>
      <name>Apache 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/planet42/Laika.git</url>
    <connection>scm:git:https://github.com/planet42/Laika.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jenshalm</id>
      <name>Jens Halm</name>
      <url>http://www.planet42.org</url>
    </developer>
  </developers>)

