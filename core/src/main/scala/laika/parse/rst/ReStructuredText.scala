/*
 * Copyright 2013-2016 the original author or authors.
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

package laika.parse.rst

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import laika.api.ext.{ExtensionBundle, ParserDefinitionBuilders, Theme}
import laika.factory.{ParserFactory, RendererFactory}
import laika.io.Input
import laika.parse.core.combinator.Parsers
import laika.parse.core.markup.DocumentParser
import laika.parse.rst.Elements.FieldList
import laika.parse.rst.ext._
import laika.parse.util.WhitespacePreprocessor
import laika.render.{HTML, HTMLWriter}
import laika.rewrite.{DocumentCursor, TreeUtil}
import laika.tree.Documents.Document
import laika.tree.Elements._
import laika.tree.Paths.Path
  
/** A parser for text written in reStructuredText markup. Instances of this class may be passed directly
 *  to the `Parse` or `Transform` APIs:
 *  
 *  {{{
 *  val document = Parse as ReStructuredText fromFile "hello.rst"
 *  
 *  Transform from ReStructuredText to HTML fromFile "hello.rst" toFile "hello.html"
 *  }}}
 * 
 *  reStructuredText has several types of extension points that are fully supported by Laika.
 *  In contrast to the original Python implementation, the API has been redesigned to be a more
 *  idiomatic, concise and type-safe Scala DSL.
 * 
 *  The following extension types are available:
 * 
 *  - Block Directives - an extension hook for adding new block level elements to
 *    reStructuredText markup. Use the `withBlockDirectives` method of this class to
 *    add directive implementations to the parser. Specification entry: 
 *    [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#directives]]
 * 
 *  - Substitution Definitions - an extension hook for adding new span level elements to
 *    reStructuredText markup that can be used by substitution references (like `|subst|`). 
 *    Use the `withSpanDirectives` method of this class to
 *    add directive implementations to the parser that can be used as substitution definitions. 
 *    Specification entry: 
 *    [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#substitution-definitions]]
 * 
 *  - Interpreted Text Roles - an extension hook for adding new dynamic span level elements to
 *    reStructuredText markup. In contrast to substitution definitions the implementation of a text
 *    role uses the text from the occurrences in the markup referring to the role as input.
 *    Use the `withTextRoles` method of this class to
 *    add custom text role implementations to the parser that can be referred to by interpreted text. 
 *    Specification entry: 
 *    [[http://docutils.sourceforge.net/docs/ref/rst/directives.html#custom-interpreted-text-roles]]
 *    
 *  In addition to the standard reStructuredText directives, the API also supports a custom directive
 *  type unique to Laika. They represent a library-wide extension mechanism and allow you to implement
 *  tags which can be used in any of the supported markup formats or in templates. If you need this
 *  level of flexibility, it is recommended to use the Laika directives, if you want to stay compatible
 *  with the reStructuredText reference parser, you should pick the standard directives.
 *  
 *  Laika directives can be registered with the `DirectiveSupport` extension bundle.
 *  The DSLs for creating directives are similar, but still different,
 *  due to differences in the feature set of the two variants. The Laika directives try to avoid some
 *  of the unnecessary complexities of reStructuredText directives.
 * 
 *  @author Jens Halm
 */
class ReStructuredText private (rawContent: Boolean = false) extends ParserFactory { self =>


  val fileSuffixes: Set[String] = Set("rest","rst")

  val extensions = Seq(
    new ExtensionBundle {
      override val useInStrictMode: Boolean = true

      override def themeFor[Writer](rendererFactory: RendererFactory[Writer]): Theme[Writer] = rendererFactory match {
        case _: HTML => Theme[HTMLWriter](customRenderers = Seq(ExtendedHTML))
        case _ => Theme[Writer]() // TODO - refactor to return Option instead
      }
    },
    RstExtensionSupport,
    StandardExtensions
  ) ++ (if (rawContent) Seq(RawContentExtensions) else Nil) // TODO - move

  /** Adds the `raw` directive and text roles to the parser.
   *  These are disabled by default as they present a potential security risk.
   */
  def withRawContent: ReStructuredText = new ReStructuredText(true)

  /** The actual parser function, fully parsing the specified input and
   *  returning a document tree.
   */
  def newParser (parserExtensions: ParserDefinitionBuilders): Input => Document = input => {
    // TODO - extract this logic once ParserFactory API gets finalized (preProcessInput)
    val raw = input.asParserInput.input
    val preprocessed = (new WhitespacePreprocessor)(raw.toString)

    // TODO - extract this logic into DocumentParser and/or OperationConfig and/or ParserFactory
    val rootParser = new RootParser(parserExtensions.blockParsers, parserExtensions.spanParsers)
    val configHeaderParsers = parserExtensions.configHeaderParsers :+ { _:Path => Parsers.success(Right(ConfigFactory.empty)) }
    val configHeaderParser = { path: Path => configHeaderParsers.map(_(path)).reduce(_ | _) }
    val doc = DocumentParser.forMarkup(rootParser.rootElement, configHeaderParser)(Input.fromString(preprocessed, input.path))

    // TODO - extract this logic once ParserFactory API gets finalized (postProcessDocument)
    def extractDocInfo (config: Config, root: RootElement): Config = {
      import scala.collection.JavaConverters._
      val docStart = root.content dropWhile { case c: Comment => true; case h: DecoratedHeader => true; case _ => false } headOption
      val docInfo = docStart collect { case FieldList(fields,_) => fields map (field => (TreeUtil.extractText(field.name),
        field.content collect { case p: Paragraph => TreeUtil.extractText(p.content) } mkString)) toMap }
      docInfo map (i => config.withValue("docInfo", ConfigValueFactory.fromMap(i.asJava))) getOrElse config
    }
    doc.copy(config = extractDocInfo(doc.config, doc.content))
  }
  
}

/** The default reStructuredText parser configuration, without any directives or text roles installed.
 * 
 *  @author Jens Halm
 */
object ReStructuredText extends ReStructuredText(false)
