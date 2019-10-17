/*
 * Copyright 2013-2018 the original author or authors.
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

package laika.parse.markup

import laika.ast._
import laika.bundle.{ConfigProvider, MarkupExtensions, UnresolvedConfig}
import laika.factory.MarkupFormat
import laika.parse.combinator.Parsers
import laika.parse.{Parser, ParserContext}

/** Responsible for creating the top level parsers for text markup and template documents,
  * by combining the parser for the root element with a parser for an (optional) configuration
  * header.
  *
  * @author Jens Halm
  */
object DocumentParser {
  
  case class ParserInput (path: Path, context: ParserContext)
  
  case class ParserError (message: String, path: Path) extends 
    RuntimeException(s"Error parsing document '$path': $message")

  private def create [D, R <: ElementContainer[_]] (rootParser: Parser[R], configProvider: ConfigProvider)
    (docFactory: (Path, UnresolvedConfig, R) => D): ParserInput => Either[ParserError, D] = {

    forParser { path =>
      (configProvider.configHeader | Parsers.success(UnresolvedConfig.empty)) ~ rootParser ^^ { case configHeader ~ root =>
        docFactory(path, configHeader, root)
      }
    }
  }

  /** Combines the specified markup parsers and extensions and the parser for (optional) configuration
    * headers to create a parser function for an entire text markup document.
    */
  def forMarkup (markupParser: MarkupFormat,
                 markupExtensions: MarkupExtensions,
                 configProvider: ConfigProvider): ParserInput => Either[ParserError, UnresolvedDocument] = {

    val rootParser = new RootParser(markupParser, markupExtensions).rootElement

    markupExtensions.parserHooks.preProcessInput andThen
      forMarkup(rootParser, configProvider) andThen
      { _.map(markupExtensions.parserHooks.postProcessDocument) }
  }

  /** Combines the specified parsers for the root element and for (optional) configuration
    * headers to create a parser function for an entire text markup document.
    */
  def forMarkup (rootParser: Parser[RootElement], configProvider: ConfigProvider): ParserInput => Either[ParserError, UnresolvedDocument] =
    create(rootParser, configProvider) { (path, config, root) =>
      val fragments = root.collect { case f: DocumentFragment => (f.name, f.root) }.toMap
      UnresolvedDocument(Document(path, root, fragments), config)
   }

  /** Combines the specified parsers for the root element and for (optional) configuration
    * headers to create a parser function for an entire template document.
    */
  def forTemplate (rootParser: Parser[TemplateRoot], configProvider: ConfigProvider): ParserInput => Either[ParserError, TemplateDocument] = 
    create(rootParser, configProvider) { (path, config, root) =>
      TemplateDocument(path, root, config)
    }

  /** Builds a document parser for CSS documents based on the specified parser for style declarations.
    */
  def forStyleSheets (parser: Parser[Set[StyleDeclaration]]): ParserInput => Either[ParserError, StyleDeclarationSet] = 
    forParser { path => parser.map(res => StyleDeclarationSet.forPath(path, res)) }

  /** A document parser function for the specified parser that is expected to consume
    * all input.
    *
    * The specified function is invoked for each parsed document, so that a parser
    * dependent on the input path can be created.
    */
  def forParser[T] (p: Path => Parser[T]): ParserInput => Either[ParserError, T] = { in =>
    Parsers
      .consumeAll(p(in.path))
      .parse(in.context)
      .toEither
      .left.map(ParserError(_, in.path))
  }

}
