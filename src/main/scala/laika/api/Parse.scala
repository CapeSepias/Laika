/*
 * Copyright 2013 the original author or authors.
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

package laika.api

import java.io.File
import java.io.InputStream
import java.io.Reader
import scala.io.Codec
import laika.factory.ParserFactory
import laika.io.IO
import laika.io.Input
import laika.io.InputProvider
import laika.io.InputProvider._
import laika.tree.Documents._
import laika.tree.Templates.TemplateDocument
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import laika.template.ParseTemplate
  
/** API for performing a parse operation from various types of input to obtain
 *  a document tree without a subsequent render operation. 
 *  
 *  In cases where a render operation should follow immediately, it is more 
 *  convenient to use the [[laika.api.Transform]] API instead which 
 *  combines a parse and a render operation directly.
 *  
 *  Example for parsing Markdown from a file:
 *  
 *  {{{
 *  val doc = Parse as Markdown fromFile "hello.md"
 *  }}}
 * 
 *  @author Jens Halm
 */
class Parse private (factory: ParserFactory, rewrite: Boolean) {
  
  private lazy val parse = factory.newParser

  /** Returns a new Parse instance that produces raw document trees without applying
   *  the default rewrite rules. These rules resolve link and image references and 
   *  rearrange the tree into a hierarchy of sections based on the (flat) sequence
   *  of header instances found in the document.
   */
  def asRawDocument = new Parse(factory, false)
  
  /** Returns a document tree obtained from parsing the specified string.
   *  Any kind of input is valid, including an empty string. 
   */
  def fromString (str: String) = fromInput(Input.fromString(str))
  
  /** Returns a document tree obtained from parsing the input from the specified reader.
   */
  def fromReader (reader: Reader) = fromInput(Input.fromReader(reader))

  /** Returns a document tree obtained from parsing the file with the specified name.
   *  Any kind of character input is valid, including empty files.
   * 
   *  @param name the name of the file to parse
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (name: String)(implicit codec: Codec) = fromInput(Input.fromFile(name)(codec))
  
  /** Returns a document tree obtained from parsing the specified file.
   *  Any kind of character input is valid, including empty files.
   * 
   *  @param file the file to use as input
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (file: File)(implicit codec: Codec) = fromInput(Input.fromFile(file)(codec))
  
  /** Returns a document tree obtained from parsing the input from the specified stream.
   * 
   *  @param stream the stream to use as input for the parser
   *  @param codec the character encoding of the stream, if not specified the platform default will be used.
   */
  def fromStream (stream: InputStream)(implicit codec: Codec) = fromInput(Input.fromStream(stream)(codec))
  
  def fromInput (input: Input) = {
    
    val doc = IO(input)(parse)

    if (rewrite) doc.rewrite else doc
  }
  
  def fromDirectory (name: String)(implicit codec: Codec) = fromTree(Directory(name)(codec))

  def fromDirectory (dir: File)(implicit codec: Codec) = fromTree(Directory(dir)(codec))
  
  def fromDefaultDirectory (implicit codec: Codec) = fromTree(DefaultDirectory(codec))
  
  def fromTree (builder: InputConfigBuilder): DocumentTree = fromTree(builder.build(factory)) 
  
  def fromTree (config: InputConfig): DocumentTree = {
    
    abstract class ConfigInput (input: Input) {
      val config = ConfigFactory.parseReader(input.asReader, ConfigParseOptions.defaults().setOriginDescription("path:"+input.path))
      val path = input.path
    }
    
    class TreeConfig (input: Input) extends ConfigInput(input)
    class RootConfig (input: Input) extends ConfigInput(input)
    
    type Operation[T] = () => (DocumentType, T)

    def parseMarkup (input: Input): Operation[Document] = () => (Markup, IO(input)(parse))
    
    def parseTemplate (docType: DocumentType)(input: Input): Operation[TemplateDocument] = () => (docType, IO(input)(config.templateParser.fromInput(_)))
    
    def parseTreeConfig (input: Input): Operation[TreeConfig] = () => (Config, new TreeConfig(input)) 
    def parseRootConfig (input: Input): Operation[RootConfig] = () => (Config, new RootConfig(input)) 
    
    def collectOperations[T] (provider: InputProvider, f: InputProvider => Seq[Operation[T]]): Seq[Operation[T]] =
      f(provider) ++ (config.provider.subtrees map (collectOperations(_,f))).flatten
    
    val operations = collectOperations(config.provider, _.markupDocuments.map(parseMarkup)) ++
                     collectOperations(config.provider, _.templates.map(parseTemplate(Template))) ++
                     collectOperations(config.provider, _.dynamicDocuments.map(parseTemplate(Dynamic))) ++
                     config.config.map(parseRootConfig) ++
                     collectOperations(config.provider, _.configDocuments.find(_.path.name == "default.conf").toList.map(parseTreeConfig)) // TODO - filename could be configurable
    
    val results = if (config.parallel) operations.par map (_()) seq else operations map (_())
    
    val docMap = (results collect {
      case (Markup, doc: Document) => (doc.path, doc)
    }) toMap
    
    val templateMap = (results collect {
      case (docType, doc: TemplateDocument) => ((docType, doc.path), doc)
    }) toMap
    
    val treeConfigMap = (results collect {
      case (Config, config: TreeConfig) => (config.path, config)
    }) toMap
    
    val rootConfigSeq = (results collect {
      case (Config, config: RootConfig) => config.config
    })
    
    def collectDocuments (provider: InputProvider, root: Boolean = false): DocumentTree = {
      val docs = provider.markupDocuments map (i => docMap(i.path))
      val templates = provider.templates map (i => templateMap((Template,i.path)))
      val dynamic = provider.dynamicDocuments map (i => templateMap((Dynamic,i.path)))
      val treeConfig = provider.configDocuments.find(_.path.name == "default.conf").map(i => treeConfigMap(i.path).config)
      val rootConfig = if (root) rootConfigSeq else Nil
      val static = provider.staticDocuments
      val trees = provider.subtrees map (collectDocuments(_))
      val config = (treeConfig.toList ++ rootConfig) reduceLeftOption (_ withFallback _) 
      new DocumentTree(provider.path, docs, templates, dynamic, Nil, static, trees, config)
    }
    
    val tree = collectDocuments(config.provider, true)
    if (rewrite) tree.rewrite else tree
  }
  
  
}

/** Serves as an entry point to the Parse API.
 * 
 *  @author Jens Halm
 */
object Parse {
  
  /** Returns a new Parse instance for the specified parser factory.
   *  This factory is usually an object provided by the library
   *  or a plugin that is capable of parsing a specific markup
   *  format like Markdown or reStructuredText. 
   * 
   *  @param factory the parser factory to use for all subsequent operations
   */
  def as (factory: ParserFactory) = new Parse(factory, true) 
  
}