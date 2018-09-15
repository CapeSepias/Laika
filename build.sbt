lazy val basicSettings = Seq(
  version               := "0.9.0",
  homepage              := Some(new URL("http://planet42.github.io/Laika/")),
  organization          := "org.planet42",
  organizationHomepage  := Some(new URL("http://planet42.org")),
  description           := "Text Markup Transformer for sbt and Scala applications",
  startYear             := Some(2012),
  licenses              := Seq("Apache 2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalaVersion          := "2.12.6",
  scalacOptions         := Opts.compile.encoding("UTF-8") :+ 
                           Opts.compile.deprecation :+ 
                           Opts.compile.unchecked :+ 
                           "-feature" :+ 
                           "-language:implicitConversions" :+ 
                           "-language:postfixOps" :+ 
                           "-language:higherKinds"
)

lazy val moduleSettings = basicSettings ++ Seq(
  crossVersion       := CrossVersion.binary,
  crossScalaVersions := Seq("2.12.6", "2.11.12")
)

lazy val publishSettings = Seq(
  publishMavenStyle       := true,
  publishArtifact in Test := false,
  pomIncludeRepository    := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <scm>
      <url>https://github.com/planet42/Laika.git</url>
      <connection>scm:git:https://github.com/planet42/Laika.git</connection>
    </scm>
    <developers>
      <developer>
        <id>jenshalm</id>
        <name>Jens Halm</name>
        <url>http://planet42.org</url>
      </developer>
    </developers>)
)

lazy val noPublishSettings = Seq(
  publish := (()),
  publishLocal := (()),
  publishTo := None
)

val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"  % "test"
val jTidy     = "net.sf.jtidy"  % "jtidy"      % "r938" % "test"
val config    = "com.typesafe"  % "config"     % "1.2.1"
val fop       = "org.apache.xmlgraphics" % "fop" % "2.1" 

lazy val root = project.in(file("."))
  .aggregate(core, pdf, plugin)
  .disablePlugins(ScriptedPlugin)
  .settings(basicSettings)
  .settings(noPublishSettings)
  .enablePlugins(ScalaUnidocPlugin)
  .settings(unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(plugin))

lazy val core = project.in(file("core"))
  .disablePlugins(ScriptedPlugin)
  .settings(moduleSettings)
  .settings(publishSettings)
  .settings(
    name := "laika-core",
    libraryDependencies ++= Seq(config, scalatest, jTidy)
  )
  
lazy val pdf = project.in(file("pdf"))
  .dependsOn(core)
  .disablePlugins(ScriptedPlugin)
  .settings(moduleSettings)
  .settings(publishSettings)
  .settings(
    name := "laika-pdf",
    libraryDependencies ++= Seq(fop, scalatest)
  )
  
lazy val plugin = project.in(file("sbt"))
  .dependsOn(core, pdf)
  .settings(basicSettings)
  .settings(
    name := "laika-sbt",
    sbtPlugin := true,
    crossScalaVersions := Seq("2.12.6"),
    publishMavenStyle := false,
    bintrayRepository := "sbt-plugins",
    bintrayOrganization := None,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false
  )
