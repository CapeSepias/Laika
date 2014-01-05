/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package laika.sbt

import sbt._
import Keys._
import laika.api._
import laika.io.InputProvider.Directories
import laika.io.InputProvider.InputConfigBuilder
import laika.io.OutputProvider.Directory
import laika.io.OutputProvider.OutputConfigBuilder
import laika.template.ParseTemplate
import laika.template.DefaultTemplate
import laika.tree.Documents._
import laika.tree.Elements._
import laika.directive.Directives._
import laika.parse.rst.ReStructuredText
import laika.parse.rst.{Directives=>rst}
import laika.parse.rst.TextRoles.TextRole
import laika.parse.markdown.Markdown
import laika.render.HTML
import laika.render.HTMLWriter
import laika.io.InputProvider
import laika.io.Input.LazyFileInput
import laika.io.Input
import laika.tree.ElementTraversal
import laika.parse.markdown.html.VerbatimHTML
import laika.parse.rst.ExtendedHTML

object LaikaSbtPlugin extends Plugin {

  
  object LaikaKeys {
    
    val Laika               = config("laika")
    
    val site                = taskKey[File]("Generates a static website")
    
    val docTypeMatcher      = settingKey[Option[Path => DocumentType]]("Matches a path to a Laika document type")
    
    val encoding            = settingKey[String]("The character encoding")

    val strict              = settingKey[Boolean]("Indicates whether all features not part of the original Markdown or reStructuredText syntax should be switched off")
    
    val renderMessageLevel  = settingKey[Option[MessageLevel]]("The minimum level for system messages to be rendered to HTML")

    val logMessageLevel     = settingKey[Option[MessageLevel]]("The minimum level for system messages to be logged")
    
    val markdown            = settingKey[Markdown]("The parser for Markdown files")
    
    val reStructuredText    = settingKey[ReStructuredText]("The parser for reStructuredText files")

    val markupParser        = settingKey[Parse]("The parser for all text markup files")
    
    val templateParser      = settingKey[ParseTemplate]("The parser for template files")
    
    val rawContent          = settingKey[Boolean]("Indicates whether embedding of raw content (like verbatim HTML) is supported in markup")
    
    val inputTree           = taskKey[InputConfigBuilder]("The configured input tree for the parser")

    val outputTree          = taskKey[OutputConfigBuilder]("The configured output tree for the renderer")
    
    val rewriteRules        = settingKey[Seq[DocumentContext => RewriteRule]]("Custom rewrite rules to add to the standard rules")
    
    val siteRenderers       = settingKey[Seq[HTMLWriter => RenderFunction]]("Custom renderers overriding the defaults per node type")
    
    val parallel            = settingKey[Boolean]("Indicates whether parsers and renderers should run in parallel")
    
    val spanDirectives      = settingKey[Seq[Spans.Directive]]("Directives for inline markup")

    val blockDirectives     = settingKey[Seq[Blocks.Directive]]("Directives for block-level markup")

    val templateDirectives  = settingKey[Seq[Templates.Directive]]("Directives for templates")
    
    val rstSpanDirectives   = settingKey[Seq[rst.Directive[Span]]]("Inline directives for reStructuredText")
    
    val rstBlockDirectives  = settingKey[Seq[rst.Directive[Block]]]("Block directives for reStructuredText")
    
    val rstTextRoles        = settingKey[Seq[TextRole]]("Custom text roles for reStructuredText")

    val includeAPI          = settingKey[Boolean]("Indicates whether API documentation should be copied to the site")
    
    val copyAPI             = taskKey[File]("Copies the API documentation to the site")

    val packageSite         = taskKey[File]("Create a zip file of the site")
    
    
    // helping the type inferrer:
    
    def siteRenderer (f: HTMLWriter => RenderFunction) = f
    def rewriteRule (rule: RewriteRule): DocumentContext => RewriteRule = _ => rule
    def rewriteRuleFactory (factory: DocumentContext => RewriteRule) = factory
    
  }
  
  
  object LaikaPlugin {
    import LaikaKeys._
    import Tasks._
    
    val defaults: Seq[Setting[_]] = inConfig(Laika)(Seq(
        
      sourceDirectories   := Seq(sourceDirectory.value / "docs"),
      
      target              := target.value / "docs",
      
      target in site      := target.value / "site",

      target in copyAPI   := (target in site).value / "api",
      
      excludeFilter       := HiddenFileFilter,
      
      encoding            := "UTF-8",
      
      strict              := false,
      
      renderMessageLevel  := None,
      
      logMessageLevel     := Some(Warning),

      docTypeMatcher      := None,
      
      rawContent          := false,
      
      markdown            := {
                            val md = (Markdown withBlockDirectives (blockDirectives.value: _*) withSpanDirectives (spanDirectives.value: _*))
                            val md2 = if (rawContent.value) md.withVerbatimHTML else md
                            if (strict.value) md2.strict else md2
                          },

      reStructuredText    := { 
                            val rst = (ReStructuredText withLaikaBlockDirectives (blockDirectives.value: _*) withLaikaSpanDirectives 
                            (spanDirectives.value: _*) withBlockDirectives (rstBlockDirectives.value: _*) withSpanDirectives 
                            (rstSpanDirectives.value: _*) withTextRoles (rstTextRoles.value: _*))
                            val rst2 = if (rawContent.value) rst.withRawContent else rst
                            if (strict.value) rst2.strict else rst2
                          },
      
      markupParser        := (Parse as markdown.value or reStructuredText.value withoutRewrite),
      
      templateParser      := (ParseTemplate as DefaultTemplate.withDirectives(templateDirectives.value: _*)),
      
      rewriteRules        := Nil,
      
      siteRenderers       := Nil,
      
      parallel            := true,
      
      spanDirectives      := Nil,
      blockDirectives     := Nil,
      templateDirectives  := Nil,
      
      rstSpanDirectives   := Nil,
      rstBlockDirectives  := Nil,
      rstTextRoles        := Nil,
      
      includeAPI          := false,
      
      inputTree           := inputTreeTask.value,
      outputTree          := outputTreeTask.value,
      site                := siteTask.value,
      copyAPI             := copyAPITask.value,
      packageSite         := packageSiteTask.value,
      clean               := cleanTask.value,
      
      mappings in site    := sbt.Path.allSubpaths(site.value).toSeq,
      
      artifact in packageSite     := Artifact(moduleName.value, Artifact.DocType, "zip", "site"),
      artifactPath in packageSite := {
                                    val art = (artifact in packageSite).value
                                    val classifier = art.classifier map ("-"+_) getOrElse ""
                                    target.value / (art.name + "-" + projectID.value.revision + classifier + "." + art.extension)
                                  }
      
    ))
    
  }
  
  
  object Tasks {
    import LaikaKeys._
    import Def._
    
    val inputTreeTask = task {
      val builder = Directories(sourceDirectories.value, excludeFilter.value.accept)(encoding.value)
        .withTemplates(templateParser.value)
      val builder2 = if (parallel.value) builder.inParallel else builder
      docTypeMatcher.value map (builder2 withDocTypeMatcher _) getOrElse builder2
    }
    
    val outputTreeTask = task {
      val builder = Directory((target in site).value)(encoding.value)
      if (parallel.value) builder.inParallel else builder
    }
    
    val siteTask = task {
      val apiDir = copyAPI.value
      val targetDir = (target in site).value
      val cacheDir = streams.value.cacheDirectory / "laika" / "site"
      
      val inputs = inputTree.value.build(markupParser.value.fileSuffixes)
      
      val cached = FileFunction.cached(cacheDir, FilesInfo.lastModified) { in =>
        
        IO.delete(((targetDir ***) --- targetDir --- (apiDir ***) --- collectParents(apiDir)).get)
        if (!targetDir.exists) targetDir.mkdirs()
        
        streams.value.log.info("Reading files from " + sourceDirectories.value.mkString(", "))
        streams.value.log.info(Log.inputs(inputs.provider))
        
        val rawTree = markupParser.value fromTree inputs
        val tree = rawTree rewrite (rewriteRules.value, AutonumberContext.defaults)

        logMessageLevel.value foreach { Log.systemMessages(streams.value.log, tree, _) }
        streams.value.log.info(Log.outputs(tree))
        
        val html = renderMessageLevel.value map (HTML withMessageLevel _) getOrElse HTML
        val renderers = siteRenderers.value :+ VerbatimHTML :+ ExtendedHTML // always install Markdown and rst extensions
        val render = ((Render as html) /: renderers) { case (render, renderer) => render using renderer }
        render from tree toTree outputTree.value
        
        streams.value.log.info("Generated site in " + targetDir)
        
        (targetDir ***).get.toSet
      }
      cached(collectInputFiles(inputs.provider))
      
      targetDir
    }
    
    val copyAPITask = taskDyn {
      val targetDir = (target in copyAPI).value
      if (includeAPI.value) task { 
        
        val cacheDir = streams.value.cacheDirectory / "laika" / "api"
        val apiMappings = (mappings in packageDoc in Compile).value
        val targetMappings = apiMappings map { case (file, target) => (file, targetDir / target) }
        
        Sync(cacheDir)(targetMappings)
        
        streams.value.log.info("Copied API documentation to " + targetDir)
        targetDir 
      }
      else task { 
        IO.delete(targetDir)
        targetDir 
      }
    }
    
    val packageSiteTask = task { 
      val zipFile = (artifactPath in packageSite).value
      streams.value.log.info(s"Packaging $zipFile ...")
      
      IO.zip((mappings in site).value, zipFile)

      streams.value.log.info("Done packaging.")
      zipFile
    }
    
    val cleanTask = task { 
      IO.delete((target in site).value)
    }

    def collectInputFiles (provider: InputProvider): Set[File] = {
      def allFiles (inputs: Seq[Input]) = (inputs collect {
        case f: LazyFileInput => f.file
      }).toSet
      
      allFiles(provider.markupDocuments) ++
      allFiles(provider.dynamicDocuments) ++
      allFiles(provider.templates) ++
      allFiles(provider.configDocuments) ++
      allFiles(provider.staticDocuments) ++
      (provider.subtrees flatMap collectInputFiles)
    }
    
    def collectParents (file: File) = {
      def collect (file: File, acc: Set[File]): Set[File] = {
        file.getParentFile match {
          case null => acc
          case p => collect(p, acc + p)
        }
      }
      collect(file, Set())
    }
    
  }
  
  object Log {
    
    def s (num: Int) = if (num == 1) "" else "s"
      
    def inputs (provider: InputProvider): String = {
      
      def count (provider: InputProvider): (Int, Int, Int) = {
        val docs = provider.markupDocuments.length
        val tmpl = provider.dynamicDocuments.length + provider.templates.length
        val conf = provider.configDocuments.length
        val all = (provider.subtrees map count) :+ (docs, tmpl, conf)
        (((0,0,0) /: (all)) { 
          case ((d1, t1, c1), (d2, t2, c2)) => (d1+d2, t1+t2, c1+c2)
        })
      }
      
      val (docs, tmpl, conf) = count(provider)
      
      s"Parsing $docs markup document${s(docs)}, $tmpl template${s(tmpl)}, $conf configuration${s(conf)} ..."
    }
    
    def outputs (tree: DocumentTree): String = {
      
      def count (tree: DocumentTree): (Int, Int) = {
        val render = tree.documents.length + tree.dynamicDocuments.length
        val copy = tree.staticDocuments.length
        val all = (tree.subtrees map count) :+ (render, copy)
        (((0,0) /: (all)) { 
          case ((r1, c1), (r2, c2)) => (r1+r2, c1+c2)
        })
      }
      
      val (render, copy) = count(tree)
      
      s"Rendering $render HTML document${s(render)}, copying $copy static file${s(copy)} ..."
    }
    
    def systemMessages (logger: Logger, tree: DocumentTree, level: MessageLevel) = {
      
      import laika.tree.Elements.{Info=>InfoLevel}
      
      def logMessage (inv: Invalid[_], path: Path) = {
        val source = inv.fallback match {
          case Text(text,_) => text
          case Literal(text,_) => text
          case LiteralBlock(text,_) => text
          case other => other.toString
        }
        val text = s"$path: ${inv.message.content}\nsource: $source"
        inv.message.level match {
          // we do not log above warn level as the build will still succeed with invalid nodes
          case Debug => logger.debug(text)
          case InfoLevel => logger.info(text)
          case Warning | Error | Fatal => logger.warn(text)
        }
      }
      
      def log (tree: DocumentTree): Unit = {
        
        def logRoot (e: ElementTraversal[_], path: Path) = {
          val nodes = e collect { 
            case i: Invalid[_] if i.message.level >= level => i 
          }
          nodes foreach { logMessage(_, path) }
        }
        
        tree.documents foreach { doc => logRoot(doc.content, doc.path) }
        tree.dynamicDocuments foreach { doc => logRoot(doc.content, doc.path) }
        tree.subtrees foreach log
      }
      
      log(tree)
      
    }
    
  } 
  
}
