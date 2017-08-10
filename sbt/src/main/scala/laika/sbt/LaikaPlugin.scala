/*
 * Copyright 2014-2016 the original author or authors.
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

import laika.api._
import laika.directive.Directives._
import laika.io.Input.LazyFileInput
import laika.io.{DocumentType, Input, InputProvider}
import laika.io.InputProvider.{Directories, InputConfigBuilder}
import laika.io.OutputProvider.{Directory, OutputConfigBuilder}
import laika.parse.markdown.Markdown
import laika.parse.markdown.html.VerbatimHTML
import laika.parse.rst.TextRoles.TextRole
import laika.parse.rst.{ExtendedHTML, ReStructuredText, Directives => rst}
import laika.render._
import laika.rewrite.{DocumentCursor, RewriteRules}
import laika.template.{DefaultTemplate, ParseTemplate}
import laika.tree.Documents._
import laika.tree.ElementTraversal
import laika.tree.Elements._
import laika.tree.Paths.Path
import org.apache.fop.apps.FopFactory
import sbt.Keys._
import sbt._

object LaikaPlugin extends AutoPlugin {

  val requirements = plugins.JvmPlugin
  override val trigger = noTrigger

  object autoImport {

    val Laika               = config("laika")

    val site                = taskKey[File]("Generates a static website")

    val generate            = inputKey[Set[File]]("Generates the specified output formats")

    val html                = taskKey[Set[File]]("Generates HTML output")

    val prettyPrint         = taskKey[Set[File]]("Generates Pretty Print output (document tree visualization)")

    val xslfo               = taskKey[Set[File]]("Generates XSL-FO output")

    val pdf                 = taskKey[File]("Generates PDF output")

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

    val rewriteRules        = settingKey[Seq[DocumentCursor => RewriteRule]]("Custom rewrite rules to add to the standard rules")

    val siteRenderers       = settingKey[Seq[HTMLWriter => RenderFunction]]("Custom HTML renderers overriding the defaults per node type")

    val foRenderers         = settingKey[Seq[FOWriter => RenderFunction]]("Custom XSL-FO renderers overriding the defaults per node type")

    val prettyPrintRenderers= settingKey[Seq[TextWriter => RenderFunction]]("Custom PrettyPrint renderers overriding the defaults per node type")

    val fopFactory          = settingKey[FopFactory]("The FopFactory for the PDF renderer")

    val fopConfig           = settingKey[Option[File]]("The Apache FOP configuration file for the PDF renderer")

    val parallel            = settingKey[Boolean]("Indicates whether parsers and renderers should run in parallel")

    val spanDirectives      = settingKey[Seq[Spans.Directive]]("Directives for inline markup")

    val blockDirectives     = settingKey[Seq[Blocks.Directive]]("Directives for block-level markup")

    val templateDirectives  = settingKey[Seq[Templates.Directive]]("Directives for templates")

    val rstSpanDirectives   = settingKey[Seq[rst.Directive[Span]]]("Inline directives for reStructuredText")

    val rstBlockDirectives  = settingKey[Seq[rst.Directive[Block]]]("Block directives for reStructuredText")

    val rstTextRoles        = settingKey[Seq[TextRole]]("Custom text roles for reStructuredText")

    val includeAPI          = settingKey[Boolean]("Indicates whether API documentation should be copied to the site")

    val includePDF          = settingKey[Boolean]("Indicates whether PDF output should be copied to the site")

    val copyAPI             = taskKey[File]("Copies the API documentation to the site")

    val copyPDF             = taskKey[File]("Copies the PDF output to the site")

    val packageSite         = taskKey[File]("Create a zip file of the site")


    // helping the type inferrer:

    def siteRenderer (f: HTMLWriter => RenderFunction) = f
    def foRenderer (f: FOWriter => RenderFunction) = f
    def textRenderer (f: TextWriter => RenderFunction) = f
    def rewriteRule (rule: RewriteRule): DocumentCursor => RewriteRule = _ => rule
    def rewriteRuleFactory (factory: DocumentCursor => RewriteRule) = factory

  }

  import autoImport._
  import Tasks._

  override def projectSettings: Seq[Setting[_]] = inConfig(Laika)(Seq(
    sourceDirectories   := Seq(sourceDirectory.value / "docs"),

    target              := target.value / "docs",

    target in site      := target.value / "site",

    target in xslfo     := target.value / "fo",

    target in prettyPrint := target.value / "prettyPrint",

    target in copyAPI   := (target in site).value / "api",

    excludeFilter       := HiddenFileFilter,

    encoding            := "UTF-8",

    strict              := false,

    renderMessageLevel  := None,

    logMessageLevel     := Some(Warning),

    docTypeMatcher      := None,

    rawContent          := false,

    markdown            := {
                          val md = Markdown withBlockDirectives (blockDirectives.value: _*) withSpanDirectives (spanDirectives.value: _*)
                          val md2 = if (rawContent.value) md.withVerbatimHTML else md
                          if (strict.value) md2.strict else md2
                        },

    reStructuredText    := {
                          val rst = ReStructuredText withLaikaBlockDirectives (blockDirectives.value: _*) withLaikaSpanDirectives
                            (spanDirectives.value: _*) withBlockDirectives (rstBlockDirectives.value: _*) withSpanDirectives
                            (rstSpanDirectives.value: _*) withTextRoles (rstTextRoles.value: _*)
                          val rst2 = if (rawContent.value) rst.withRawContent else rst
                          if (strict.value) rst2.strict else rst2
                        },

    markupParser        := (Parse as markdown.value or reStructuredText.value withoutRewrite),

    templateParser      := (ParseTemplate as DefaultTemplate.withDirectives(templateDirectives.value: _*)),

    rewriteRules        := Nil,

    siteRenderers       := Nil,
    prettyPrintRenderers:= Nil,
    foRenderers         := Nil,

    parallel            := true,

    spanDirectives      := Nil,
    blockDirectives     := Nil,
    templateDirectives  := Nil,

    rstSpanDirectives   := Nil,
    rstBlockDirectives  := Nil,
    rstTextRoles        := Nil,

    includeAPI          := false,
    includePDF          := false,

    inputTree           := inputTreeTask.value,
    outputTree in site  := outputTreeTask(site).value,
    outputTree in xslfo := outputTreeTask(xslfo).value,
    outputTree in prettyPrint  := outputTreeTask(prettyPrint).value,

    fopConfig           := None,
    fopFactory          := fopFactorySetting.value,

    site                := Def.sequential(siteGenTask, copyTask).value,
    generate            := generateTask.evaluated,
    html                := generateTask.toTask(" html").value,
    xslfo               := generateTask.toTask(" xslfo").value,
    pdf                 := generateTask.toTask(" pdf").value.headOption.getOrElse((artifactPath in pdf).value),
    prettyPrint         := generateTask.toTask(" prettyPrint").value,
    copyAPI             := copyAPITask.value,
    copyPDF             := copyPDFTask.value,
    packageSite         := packageSiteTask.value,
    clean               := cleanTask.value,

    mappings in site    := sbt.Path.allSubpaths(site.value).toSeq,

    artifact in packageSite     := Artifact(moduleName.value, Artifact.DocType, "zip", "site"),
    artifact in pdf             := Artifact(moduleName.value, Artifact.DocType, "pdf"),
    artifactPath in packageSite := artifactPathSetting(packageSite).value,
    artifactPath in pdf         := artifactPathSetting(pdf).value

  )) :+ (cleanFiles += (target in Laika).value)


  object Tasks {
    import Def._

    val fopFactorySetting: Initialize[FopFactory] = setting {
      fopConfig.value map {
        FopFactory.newInstance
      } getOrElse PDF.defaultFopFactory
    }

    def artifactPathSetting (key: Scoped): Initialize[File] = setting {
      val art = (artifact in key).value
      val classifier = art.classifier map ("-"+_) getOrElse ""
      target.value / (art.name + "-" + projectID.value.revision + classifier + "." + art.extension)
    }

    val inputTreeTask: Initialize[Task[InputConfigBuilder]] = task {
      val builder = Directories(sourceDirectories.value, excludeFilter.value.accept)(encoding.value)
        .withTemplateParser(templateParser.value)
      val builder2 = if (parallel.value) builder.inParallel else builder
      docTypeMatcher.value map (builder2 withDocTypeMatcher) getOrElse builder2
    }

    def outputTreeTask (key: Scoped): Initialize[Task[OutputConfigBuilder]] = task {
      val builder = Directory((target in key).value)(encoding.value)
      if (parallel.value) builder.inParallel else builder
    }

    def prepareTargetDirectory (key: Scoped): Initialize[TargetDirectory] = setting {
      val targetDir = (target in key).value
      val apiInSite = (target in copyAPI).value
      val pdfInSite = (artifactPath in pdf).value

      val filesToDelete = ((targetDir ***) --- targetDir --- pdfInSite --- (apiInSite ***) --- collectParents(apiInSite)).get

      new TargetDirectory(targetDir, filesToDelete)
    }

    def prepareRenderer [Writer, R <: Render[Writer] { type ThisType = R }] (
        render: R,
        custom: Seq[Writer => RenderFunction]): R = {
      (render /: custom) { case (render, renderer) => render using renderer }
    }

    private val allTargets = setting {
      Set((target in site).value, (artifactPath in pdf).value, (target in xslfo).value, (target in prettyPrint).value)
    }

    val generateTask: Initialize[InputTask[Set[File]]] = inputTask {

      val formats = spaceDelimited("<format>").parsed.map(OutputFormats.OutputFormat.fromString)
      if (formats.isEmpty) throw new IllegalArgumentException("At least one format must be specified")

      val inputs = inputTree.value.build(markupParser.value.fileSuffixes)

      val cacheDir = streams.value.cacheDirectory / "laika"

      lazy val tree = {

        streams.value.log.info("Reading files from " + sourceDirectories.value.mkString(", "))
        streams.value.log.info(Log.inputs(inputs.provider))

        val rawTree = markupParser.value fromTree inputs
        val tree = rawTree rewrite RewriteRules.chainFactories(rewriteRules.value :+ RewriteRules.defaultsFor(Markdown, ReStructuredText))

        logMessageLevel.value foreach { Log.systemMessages(streams.value.log, tree, _) }

        tree
      }

      val inputFiles = collectInputFiles(inputs.provider)

      val results = formats map { format =>

        val fun = FileFunction.cached(cacheDir / format.toString.toLowerCase, FilesInfo.lastModified, FilesInfo.exists) { _ =>

          format match {

            case OutputFormats.HTML =>

              val targetDir = prepareTargetDirectory(site).value.prepare

              val html = renderMessageLevel.value map (HTML withMessageLevel) getOrElse HTML
              val renderers = siteRenderers.value :+ VerbatimHTML :+ ExtendedHTML // always install Markdown and rst extensions
              val render = prepareRenderer(Render as html, renderers)
              render from tree toTree (outputTree in site).value

              streams.value.log.info(Log.outputs(tree))
              streams.value.log.info("Generated html in " + targetDir)

              (targetDir ***).get.toSet.filter(_.isFile)

            case OutputFormats.PrettyPrint =>

              val targetDir = prepareTargetDirectory(prettyPrint).value.prepare

              val render = prepareRenderer(Render as PrettyPrint, prettyPrintRenderers.value)
              render from tree toTree (outputTree in prettyPrint).value

              streams.value.log.info("Generated Pretty Print in " + targetDir)

              (targetDir ***).get.toSet.filter(_.isFile)

            case OutputFormats.XSLFO =>

              val targetDir = prepareTargetDirectory(xslfo).value.prepare

              val fo = renderMessageLevel.value map (XSLFO withMessageLevel) getOrElse XSLFO // TODO - ExtendedFO for rst
              val render = prepareRenderer(Render as fo, foRenderers.value)
              render from tree toTree (outputTree in xslfo).value

              streams.value.log.info("Generated XSL-FO in " + targetDir)

              (targetDir ***).get.toSet.filter(_.isFile)

            case OutputFormats.PDF =>

              val targetFile = (artifactPath in pdf).value
              targetFile.getParentFile.mkdirs()

              val pdfRenderer = renderMessageLevel.value map (PDF withMessageLevel) getOrElse PDF
              val render = prepareRenderer(Render as pdfRenderer.withFopFactory(fopFactory.value), foRenderers.value)
              render from tree toFile targetFile

              streams.value.log.info("Generated PDF in " + targetFile)

              Set(targetFile)

          }

        }

        fun(inputFiles)

      }

      val outputFiles = results reduce (_ ++ _)

      outputFiles intersect allTargets.value
    }

    val siteGenTask: Initialize[Task[Set[File]]] = taskDyn {
      if (includePDF.value) generateTask.toTask(" html pdf")
      else generateTask.toTask(" html")
    }

    val copyTask: Initialize[Task[File]] = task {
      val api = copyAPI.value
      val pdf = copyPDF.value

      (target in site).value
    }

    val copyAPITask: Initialize[Task[File]] = taskDyn {
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

    val copyPDFTask: Initialize[Task[File]] = taskDyn {
      val targetDir = (target in site).value
      val pdfSource = (artifactPath in pdf).value
      val pdfTarget = targetDir / pdfSource.getName

      if (includePDF.value) task {
        val cacheDir = streams.value.cacheDirectory / "laika" / "site-pdf"
        Sync(cacheDir)(Seq((pdfSource, pdfTarget)))

        streams.value.log.info("Copied PDF output to " + targetDir)
        targetDir
      }
      else task {
        IO.delete(pdfTarget)
        targetDir
      }
    }

    val packageSiteTask: Initialize[Task[File]] = task {
      val zipFile = (artifactPath in packageSite).value
      streams.value.log.info(s"Packaging $zipFile ...")

      IO.zip((mappings in site).value, zipFile)

      streams.value.log.info("Done packaging.")
      zipFile
    }

    val cleanTask: Initialize[Task[Unit]] = task {
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

    def collectParents (file: File): Set[File] = {
      def collect (file: File, acc: Set[File]): Set[File] = {
        file.getParentFile match {
          case null => acc
          case p => collect(p, acc + p)
        }
      }
      collect(file, Set())
    }

  }

  class TargetDirectory(dir: File, toBeDeleted: Seq[File]) {

    def prepare: File = {
      IO.delete(toBeDeleted)

      if (!dir.exists) dir.mkdirs()

      dir
    }

  }

  object OutputFormats {

    object OutputFormat {

      def fromString (name: String): OutputFormat = name.toLowerCase match {
        case "html" => HTML
        case "pdf" => PDF
        case "fo" | "xslfo" | "xsl-fo" => XSLFO
        case "prettyprint" | "pretty-print" => PrettyPrint
        case _ => throw new IllegalArgumentException(s"Unsupported format: $name")
      }

    }

    sealed abstract class OutputFormat

    case object HTML extends OutputFormat

    case object PDF extends OutputFormat

    case object XSLFO extends OutputFormat

    case object PrettyPrint extends OutputFormat

  }


  object Log {

    def s (num: Int): String = if (num == 1) "" else "s"

    def inputs (provider: InputProvider): String = {

      def count (provider: InputProvider): (Int, Int, Int) = {
        val docs = provider.markupDocuments.length
        val tmpl = provider.dynamicDocuments.length + provider.templates.length
        val conf = provider.configDocuments.length
        val all = (provider.subtrees map count) :+ (docs, tmpl, conf)
        ((0, 0, 0) /: all) {
          case ((d1, t1, c1), (d2, t2, c2)) => (d1 + d2, t1 + t2, c1 + c2)
        }
      }

      val (docs, tmpl, conf) = count(provider)

      s"Parsing $docs markup document${s(docs)}, $tmpl template${s(tmpl)}, $conf configuration${s(conf)} ..."
    }

    def outputs (tree: DocumentTree): String = {

      def count (tree: DocumentTree): (Int, Int) = {

        val (render, copy) = tree.content.foldLeft((0,0)) {
          case ((render, copy), _: Document) => (render + 1, copy)
          case ((render, copy), tree: DocumentTree) =>
            val (childRender, childCopy) = count(tree)
            (render + childRender, copy + childCopy)
        }

        tree.additionalContent.foldLeft((render, copy)) {
          case ((render, copy), _: DynamicDocument) => (render + 1, copy)
          case ((render, copy), _: StaticDocument) => (render, copy + 1)
          case _ => (render, copy)
        }

      }

      val (render, copy) = count(tree)

      s"Rendering $render HTML document${s(render)}, copying $copy static file${s(copy)} ..."
    }

    def systemMessages (logger: Logger, tree: DocumentTree, level: MessageLevel): Unit = {

      import laika.tree.Elements.{Info => InfoLevel}

      def logMessage (inv: Invalid[_], path: Path): Unit = {
        val source = inv.fallback match {
          case tc: TextContainer => tc.content
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

        tree.content foreach {
          case doc: Document => logRoot(doc.content, doc.path)
          case tree: DocumentTree => log(tree)
        }
        tree.additionalContent foreach {
          case doc: DynamicDocument => logRoot(doc.content, doc.path)
          case _ => ()
        }
      }

      log(tree)

    }

  }

}
