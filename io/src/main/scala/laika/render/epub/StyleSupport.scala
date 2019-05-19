package laika.render.epub

import java.nio.charset.Charset

import laika.ast.{DocumentTreeRoot, Path, RawContent, TemplateDocument, TemplateElement}
import laika.directive.Templates
import laika.io.{BinaryInput, ByteInput, RenderedTreeRoot}
import laika.parse.directive.TemplateParsers
import laika.parse.markup.DocumentParser.ParserInput
import laika.parse.text.TextParsers.unsafeParserFunction

/** Processes CSS inputs for EPUB containers.
  *
  * @author Jens Halm
  */
object StyleSupport {

  private val fallbackStyles = ByteInput(StaticContent.fallbackStyles.getBytes(Charset.forName("UTF-8")), Path.Root / "styles" / "fallback.css")

  /** Collects all CSS inputs (recursively) in the provided document tree.
    * CSS inputs are recognized by file suffix).
    */
  def collectStyles (root: RenderedTreeRoot): Seq[BinaryInput] = root.staticDocuments.filter(_.path.suffix == "css")

  def collectStyles (root: DocumentTreeRoot): Seq[BinaryInput] = root.staticDocuments.filter(_.path.suffix == "css")

  /** Verifies that the specified document tree contains at least one CSS file
    * (determined by file suffix). If this is the case the tree is returned unchanged,
    * otherwise a new tree with a minimal fallback CSS inserted into the root is returned instead.
    */
  def ensureContainsStyles (root: DocumentTreeRoot): DocumentTreeRoot = {

    val allStyles = collectStyles(root)

    if (allStyles.isEmpty) root.copy(staticDocuments = root.staticDocuments :+ fallbackStyles)
    else root
  }

  /** Template directive that inserts links to all CSS inputs found in the document tree, using a path
    * relative to the currently processed document.
    */
  lazy val styleLinksDirective: Templates.Directive = Templates.create("styleLinks") {
    import Templates.dsl._

    cursor.map { docCursor =>
      val refPath = docCursor.parent.target.path
      val allLinks = collectStyles(/*docCursor.root.target*/null: DocumentTreeRoot).map { input => // TODO - 0.12 - resurrect directive
        val path = input.path.relativeTo(refPath).toString
        s"""<link rel="stylesheet" type="text/css" href="$path" />"""
      }
      TemplateElement(RawContent(Seq("html","xhtml"), allLinks.mkString("\n    ")))
    }
  }

  /** Parser for the EPUB-XHTML default template that supports the `styleLinks` directive.
    */
  object XHTMLTemplateParser extends TemplateParsers(Map(styleLinksDirective.name -> styleLinksDirective)) {
    def parse (input: ParserInput): TemplateDocument = {
      val root = unsafeParserFunction(templateRoot)(input.context)
      TemplateDocument(input.path, root)
    }
  }

}
