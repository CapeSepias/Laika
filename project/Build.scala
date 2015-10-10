import sbt._
import Keys._

object Build extends Build {


  object Settings {
    
    lazy val basic = Seq(
      version               := "0.6.0-SNAPSHOT",
      homepage              := Some(new URL("http://planet42.github.io/Laika/")),
      organization          := "org.planet42",
      organizationHomepage  := Some(new URL("http://www.planet42.org")),
      description           := "Text Markup Transformer for sbt and Scala applications",
      startYear             := Some(2012),
      licenses              := Seq("Apache 2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
      scalaVersion          := "2.10.5",
      scalacOptions         := Opts.compile.encoding("UTF-8") :+ 
                               Opts.compile.deprecation :+ 
                               Opts.compile.unchecked :+ 
                               "-feature" :+ 
                               "-language:implicitConversions" :+ 
                               "-language:postfixOps" :+ 
                               "-language:higherKinds"
    )
    
    lazy val module = basic ++ Seq(
      crossVersion       := CrossVersion.binary,
      crossScalaVersions := Seq("2.10.5", "2.11.7")
    )
    
  }
  
  object Dependencies {
    
    val parser    = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
    
    val scalatest = "org.scalatest" %% "scalatest" % "2.2.4"  % "test"
        
    val jTidy     = "net.sf.jtidy"  % "jtidy"      % "r938" % "test"
        
    val config    = "com.typesafe"  % "config"     % "1.0.2"
    
    val fop       = Seq(
                      "avalon-framework" % "avalon-framework-api" % "4.2.0",
                      "avalon-framework" % "avalon-framework-impl" % "4.2.0",
                      "org.apache.xmlgraphics" % "fop" % "1.1" 
                        exclude("org.apache.avalon.framework", "avalon-framework-api") 
                        exclude("org.apache.avalon.framework", "avalon-framework-impl")
                    )
    
  }
  
  object Plugins {
    
    import sbt.ScriptedPlugin._
    
    val scripted = scriptedSettings ++ Seq(

      scriptedLaunchOpts ++=
        Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value),

      scriptedBufferLog := false
    
    )
    
  }
  
  object Publishing {
    
    lazy val mavenCentral = Seq(
      
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
            <url>http://www.planet42.org</url>
          </developer>
        </developers>)
    )
    
    lazy val sbtPlugins = Seq(
      
      publishMavenStyle := false,

      publishTo := {
        val repo = if (version.value.endsWith("-SNAPSHOT")) "snapshots" else "releases"
        val baseUrl = "http://repo.scala-sbt.org/scalasbt/sbt-plugin-"
        Some(Resolver.url("scalasbt " + repo, url(baseUrl + repo))(Resolver.ivyStylePatterns))
      }
      
    )
    
    lazy val none = Seq(
      publish := (),
      publishLocal := (),
      publishTo := None
    )
    
  }
  
  
  lazy val root = Project("root", file("."))
    .aggregate(core, pdf, plugin)
    .settings(Settings.basic: _*)
    .settings(Publishing.none: _*)
 
  lazy val core = Project("laika-core", file("core"))
    .settings(Settings.module: _*)
    .settings(Publishing.mavenCentral: _*)
    .settings(libraryDependencies ++= { Seq(
      Dependencies.config,
      Dependencies.scalatest, 
      Dependencies.jTidy
    ) ++ { CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq(Dependencies.parser)
      case _ => Seq()
    }}})
    
  lazy val pdf = Project("laika-pdf", file("pdf"))
    .dependsOn(core)
    .settings(Settings.module: _*)
    .settings(Publishing.mavenCentral: _*)
    .settings(libraryDependencies ++= Dependencies.fop)
    
  lazy val plugin = Project("laika-sbt", file("sbt"))
    .dependsOn(core)
    .dependsOn(pdf)
    .settings(
      sbtPlugin := true, 
      crossScalaVersions := Seq("2.10.5")
     )
    .settings(Settings.basic: _*)
    .settings(Publishing.sbtPlugins: _*)
    .settings(Plugins.scripted: _*)
  
    
}
