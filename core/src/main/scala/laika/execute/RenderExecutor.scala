/*
 * Copyright 2012-2019 the original author or authors.
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

package laika.execute

import java.io.File

import laika.api.Render
import laika.api.Render.Done
import laika.ast.Path.Root
import laika.ast._
import laika.factory.RenderContext2
import laika.io._
import laika.rewrite.TemplateRewriter

import scala.collection.mutable

/**
  *  @author Jens Halm
  */
object RenderExecutor {

  def execute[FMT] (op: Render.Op2[FMT], styles: Option[StyleDeclarationSet]): String = {

    val theme = op.config.themeFor(op.format)

    val effectiveStyles = styles.getOrElse(theme.defaultStyles)

    val renderFunction: (FMT, Element) => String = (fmt, element) => 
      theme.customRenderer.applyOrElse[(FMT,Element),String]((fmt, element), { case (f, e) => op.format.defaultRenderer(f, e) })
    
    val renderContext = RenderContext2(renderFunction, op.element, effectiveStyles, op.output.path, op.config)

    val fmt = op.format.formatterFactory(renderContext)
    
    val result = renderFunction(fmt, op.element)

    op.output match {
      case StringOutput(builder, _) => result
      case _ => result
    }
    
    // result // TODO - 0.12 - deal with different types of Output
  }
  
  def execute[Writer] (op: Render.MergeOp2[Writer]): Done = {
    val template = op.config.themeFor(op.processor.format).defaultTemplateOrFallback // TODO - 0.12 - look for templates in root tree
    val preparedTree = op.processor.prepareTree(op.tree)
    val renderedTree  = execute(Render.TreeOp2(op.processor.format, op.config, preparedTree, StringTreeOutput))
    op.processor.process(renderedTree, op.output)
    Done
  }

  def execute[FMT] (op: Render.TreeOp2[FMT]): RenderResult2 = {

    type Operation = () => RenderContent

    val theme = op.config.themeFor(op.format)
    
    def outputPath (path: Path): Path = path.parent / (path.basename +"."+ op.format.fileSuffix)
    
    def textOutputFor (path: Path): TextOutput = op.output match {
      case StringTreeOutput => StringOutput(new mutable.StringBuilder, outputPath(path)) // TODO - 0.12 - temporary solution
      case DirectoryOutput(dir, codec) => TextFileOutput(new File(dir, outputPath(path).toString.drop(1)), outputPath(path), codec)
    }
    def binaryOutputFor (path: Path): Seq[BinaryOutput] = op.output match {
      case StringTreeOutput => Nil
      case DirectoryOutput(dir, codec) => Seq(BinaryFileOutput(new File(dir, path.toString.drop(1)), path))
    }

    def renderDocument (document: Document, styles: StyleDeclarationSet): Operation = {
      val textOp = Render.Op2(op.format, op.config, document.content, textOutputFor(document.path))
      () => RenderedDocument(outputPath(document.path), document.title, document.sections, execute(textOp, Some(styles)))
    }

    def renderTemplate (document: DynamicDocument, styles: StyleDeclarationSet): Operation = {
      val textOp = Render.Op2(op.format, op.config, document.content, textOutputFor(document.path))
      () => RenderedTemplate(outputPath(document.path), execute(textOp, Some(styles)))
    }

    def copy (document: StaticDocument): Seq[Operation] = binaryOutputFor(document.path).map { out =>
      () => {
        IO.copy(document.input, out)
        CopiedDocument(document.input)
      }
    }

    def collectOperations (parentStyles: StyleDeclarationSet, docTree: DocumentTree): Seq[Operation] = {

      def isOutputRoot (source: DocumentTree) = (source.sourcePaths.headOption, op.output) match {
        case (Some(inPath), out: DirectoryOutput) => inPath == out.directory.getAbsolutePath
        case _ => false
      }

      val styles = parentStyles ++ docTree.styles(op.format.fileSuffix)

      (docTree.content flatMap {
        case doc: Document => Seq(renderDocument(doc, styles))
        case tree: DocumentTree if !isOutputRoot(tree) => collectOperations(styles, tree)
        case _ => Seq()
      }) ++
        (docTree.additionalContent flatMap {
          case doc: DynamicDocument => Seq(renderTemplate(doc, styles))
          case static: StaticDocument => copy(static)
          case _ => Seq()
        })
    }

    val templateName = "default.template." + op.format.fileSuffix // TODO - 0.12 - add to API: getDefaultTemplate(format) + withDefaultTemplate(format)
    val (treeWithTpl, template) = op.tree.selectTemplate(Path.Current / templateName).fold(
      (op.tree.copy(templates = op.tree.templates :+ TemplateDocument(Path.Root / templateName,
        theme.defaultTemplateOrFallback)), theme.defaultTemplateOrFallback)
    )(tpl => (op.tree, tpl.content))
    val treeWithTplApplied = TemplateRewriter.applyTemplates(treeWithTpl, op.format.fileSuffix)
    
    val finalTree = theme.staticDocuments.merge(treeWithTplApplied)
    val operations = collectOperations(theme.defaultStyles, finalTree)

    val results = BatchExecutor.execute(operations, op.config.parallelConfig.parallelism, op.config.parallelConfig.threshold)
    
    def buildNode (path: Path, content: Seq[RenderContent], subTrees: Seq[RenderedTree]): RenderedTree = 
      RenderedTree(path, finalTree.selectSubtree(path.relativeTo(Root)).fold(Seq.empty[Span])(_.title), content ++ subTrees)
    
    val resultRoot = TreeBuilder.build(results, buildNode)

    RenderResult2(None, resultRoot, template, finalTree.config) // TODO - 0.12 - handle cover document
  }

}
