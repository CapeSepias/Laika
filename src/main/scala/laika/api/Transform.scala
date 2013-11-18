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

import java.io._
import scala.io.Codec
import laika.api.Transform.Rules
import laika.io._
import laika.tree.Documents._
import laika.tree.Elements.Element
import laika.tree.RewriteRules
import laika.factory.ParserFactory
import laika.factory.RendererFactory
import laika.io.InputProvider._
import laika.io.OutputProvider._
import laika.template.ParseTemplate
import Transform._
  
/** API for performing a transformation operation from and to various types of input and output,
 *  combining a parse and render operation. 
 *  
 *  In cases where a parse or render operation should
 *  be performed separately, for example for manually processing the document tree model
 *  between these operations, the [[laika.api.Parse]] and [[laika.api.Render]] APIs 
 *  should be used instead.
 *  
 *  Example for transforming from Markdown to HTML using files for both input and output:
 *  
 *  {{{
 *  Transform from Markdown to HTML fromFile "hello.md" toFile "hello.html"
 *  }}}
 *  
 *  Or for transforming a document fragment from a string to the PrettyPrint format
 *  for debugging purposes:
 *  
 *  {{{
 *  val input = "some *emphasized* text"
 *  
 *  Transform from Markdown to PrettyPrint fromString input toString
 *  
 *  res0: java.lang.String = 
 *  Document - Blocks: 1
 *  . Paragraph - Spans: 3
 *  . . Text - 'some ' 
 *  . . Emphasized - Spans: 1
 *  . . . Text - 'emphasized'
 *  . . Text - ' text'
 *  }}}
 *  
 *  Apart from specifying input and output, the Transform API also allows to customize the operation
 *  in various ways. The `usingRule` and `creatingRule` methods allow to rewrite the document tree
 *  between the parse and render operations and the `rendering` method allows to customize the
 *  way certain types of elements are rendered.
 *  
 *  @tparam W the writer API to use which varies depending on the renderer
 * 
 *  @author Jens Halm
 */
class Transform [W] private[Transform] (parser: ParserFactory, render: Render[W], rules: Rules, targetSuffix: String) {
  
  private val parse = Parse as parser asRawDocument
  
  /** Represents a single transformation operation for a specific
   *  input that has already been parsed. Various types of output can be
   *  specified to trigger the actual rendering.
   */
  class Operation private[Transform] (raw: Document) { 

    private val document = raw rewriteWith rules.forContext(DocumentContext(raw))
    private val op = render from document
    
    /** Renders to the file with the specified name.
     * 
     *  @param name the name of the file to parse
     *  @param codec the character encoding of the file, if not specified the platform default will be used.
     */
    def toFile (name: String)(implicit codec: Codec) = op.toFile(name)(codec)
    
    /** Renders to the specified file.
     * 
     *  @param file the file to write to
     *  @param codec the character encoding of the file, if not specified the platform default will be used.
     */
    def toFile (file: File)(implicit codec: Codec) = op.toFile(file)(codec)


    /** Renders to the specified output stream.
     * 
     *  @param stream the stream to render to
     *  @param codec the character encoding of the stream, if not specified the platform default will be used.
     */
    def toStream (stream: OutputStream)(implicit codec: Codec) = op.toStream(stream)(codec)
    
    /** Renders directly to the console.
      */
    def toConsole = op toConsole

    /** Renders to the specified writer.
      */
    def toWriter (writer: Writer) = op toWriter writer
    
    /** Renders to the specified `StringBuilder`.
     */
    def toBuilder (builder: StringBuilder) = op toBuilder builder 
    
    def toOutput (output: Output) = op toOutput output

    /** Renders to a String and returns it.
     */
    override def toString = op toString
    
  }

  /** Specifies a rewrite rule to be applied to the document tree model between the
   *  parse and render operations. This is identical to calling `Document.rewrite`
   *  directly, but if there is no need to otherwise access the document instance
   *  and just chain parse and render operations this hook is more convenient.
   *  
   *  The rule is a partial function that takes an `Element` and returns an `Option[Element]`.
   *  
   *  If the function is not defined for a specific element the old element remains
   *  in the tree unchanged. If it returns `None` then the node gets removed from the tree, 
   *  if it returns an element it will replace the old one. Of course the function may
   *  also return the old element.
   *  
   *  The rewriting is performed in a way that only branches of the tree that contain
   *  new or removed elements will be replaced. It is processed bottom-up, therefore
   *  any element container passed to the rule only contains children which have already
   *  been processed. 
   *  
   *  In case multiple rewrite rules need to be applied it may be more efficient to
   *  first combine them with `orElse`.
   */
  def usingRule (newRule: PartialFunction[Element, Option[Element]]) = creatingRule(_ => newRule)
  
  /** Specifies a rewrite rule to be applied to the document tree model between the
   *  parse and render operations. This is identical to calling `Document.rewrite`
   *  directly, but if there is no need to otherwise access the document instance
   *  and just chain parse and render operations this hook is more convenient.
   *  
   *  The difference of this method to the `usingRule` method is that it expects a function
   *  that expects a Document instance and returns the rewrite rule. This way the full document
   *  can be queried before any rule is applied. This is necessary in cases where the rule
   *  (which gets applied node-by-node) depends on information from other nodes. An example
   *  from the built-in rewrite rules is the rule that resolves link references. To replace
   *  all link reference elements with actual link elements, the rewrite rule needs to know
   *  all LinkDefinitions the document tree contains.
   *  
   *  The rule itself is a partial function that takes an `Element` and returns an `Option[Element]`.
   *  
   *  If the function is not defined for a specific element the old element remains
   *  in the tree unchanged. If it returns `None` then the node gets removed from the tree, 
   *  if it returns an element it will replace the old one. Of course the function may
   *  also return the old element.
   *  
   *  The rewriting is performed in a way that only branches of the tree that contain
   *  new or removed elements will be replaced. It is processed bottom-up, therefore
   *  any element container passed to the rule only contains children which have already
   *  been processed. 
   *  
   *  In case multiple rewrite rules need to be applied it may be more efficient to
   *  first combine them with `orElse`.
   */
  def creatingRule (newRule: DocumentContext => PartialFunction[Element, Option[Element]]) 
      = new Transform(parser, render, rules + newRule, targetSuffix) 
  
  /** Specifies a custom render function that overrides one or more of the default
   *  renderers for the output format this instance uses.
   *  
   *  This method expects a function that returns a partial function as the parameter.
   *  The outer function allows to capture the writer instance to write to and will
   *  only be invoked once. The partial function will then be invoked for each
   *  elememnt it is defined at. 
   * 
   *  Simple example for customizing the HTML output for emphasized text, adding a specific
   *  style class:
   *  
   *  {{{
   *  Transform from Markdown to HTML rendering { out => 
   *    { case Emphasized(content) => out << """&lt;em class="big">""" << content << "&lt;/em>" } 
   *  } fromFile "hello.md" toFile "hello.html"
   *  }}}
   */
  def rendering (customRenderer: W => PartialFunction[Element, Unit]) 
      = new Transform(parser, render using customRenderer, rules, targetSuffix)
  
  
  /** Parses the specified string and returns a new Operation instance which allows to specify the output.
   *  Any kind of input is valid, including an empty string. 
   */
  def fromString (str: String) = new Operation(parse.fromString(str))
  
  /** Parses the input from the specified reader
   *  and returns a new Operation instance which allows to specify the output.
   */
  def fromReader (reader: Reader) = new Operation(parse.fromReader(reader))
  
  /** Parses the file with the specified name
   *  and returns a new Operation instance which allows to specify the output.
   *  Any kind of character input is valid, including empty files.
   * 
   *  @param name the name of the file to parse
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (name: String)(implicit codec: Codec) = new Operation(parse.fromFile(name)(codec))
  
  /** Parses the specified file
   *  and returns a new Operation instance which allows to specify the output.
   *  Any kind of character input is valid, including empty files.
   * 
   *  @param file the file to read from
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (file: File)(implicit codec: Codec) = new Operation(parse.fromFile(file)(codec))
  
  /** Parses the input from the specified stream
   *  and returns a new Operation instance which allows to specify the output.
   * 
   *  @param stream the stream to use as input for the parser
   *  @param codec the character encoding of the stream, if not specified the platform default will be used.
   */
  def fromStream (stream: InputStream)(implicit codec: Codec) = new Operation(parse.fromStream(stream)(codec))
  
  
  class DirectoryConfigBuilder private[Transform] (inputBuilder: InputConfigBuilder, isParallel: Boolean = false) {
    
    def withTemplates (parse: ParseTemplate) = 
      new DirectoryConfigBuilder(inputBuilder.withTemplates(parse), isParallel)
    
    def withDocTypeMatcher (matcher: Path => DocumentType) =
      new DirectoryConfigBuilder(inputBuilder.withDocTypeMatcher(matcher), isParallel)

    def withConfigFile (file: File) = 
      new DirectoryConfigBuilder(inputBuilder.withConfigFile(file), isParallel)
    def withConfigFile (name: String) =
      new DirectoryConfigBuilder(inputBuilder.withConfigFile(name), isParallel)
    def withConfigString (source: String) =
      new DirectoryConfigBuilder(inputBuilder.withConfigString(source), isParallel)
    
    def parallel = new DirectoryConfigBuilder(inputBuilder.parallel, true)
    
    def toDirectory (name: String)(implicit codec: Codec) =
      execute(OutputProvider.Directory(name)(codec))
      
    def toDirectory (dir: File)(implicit codec: Codec) =
      execute(OutputProvider.Directory(dir)(codec))
    
    private def execute (outputBuilder: OutputConfigBuilder) = {
      withConfig(BatchConfig(inputBuilder.build(parser), if (isParallel) outputBuilder.parallel.build else outputBuilder.build))
    }
  }
  
  
  def fromDirectory (name: String)(implicit codec: Codec) = new DirectoryConfigBuilder(InputProvider.Directory(name)(codec))

  def fromDirectory (dir: File)(implicit codec: Codec) = new DirectoryConfigBuilder(InputProvider.Directory(dir)(codec))
  
  def withDefaultDirectories (implicit codec: Codec) = withConfig(DefaultDirectories(codec).build(parser))
  
  def withRootDirectory (name: String)(implicit codec: Codec): Unit = withRootDirectory(new File(name))(codec)
  
  def withRootDirectory (dir: File)(implicit codec: Codec): Unit = withConfig(RootDirectory(dir)(codec).build(parser))
  
  def withConfig (builder: BatchConfigBuilder): Unit = withConfig(builder.build(parser)) 

  def withConfig (config: BatchConfig): Unit = {

    val tree = parse.fromTree(config.input)

    val rewritten = tree.rewrite(rules.all, AutonumberContext.defaults)
    
    render from rewritten toTree config.output
  }
  

    
} 

/** Serves as an entry point to the Transform API.
 * 
 *  @author Jens Halm
 */
object Transform {
   
  private[laika] class Rules (rules: List[DocumentContext => PartialFunction[Element, Option[Element]]]){
    
    def all = rules.reverse
    
    def forContext (context: DocumentContext) = (rules map { _(context) }).reverse      
    
    def + (newRule: DocumentContext => PartialFunction[Element, Option[Element]]) = new Rules(newRule :: rules)
    
  }

  /** Step in the setup for a transform operation where the
   *  renderer must be specified.
   */
  class Builder private[Transform] (parser: ParserFactory) {

    /** Creates and returns a new Transform instance for the specified renderer and the
     *  previously specified parser. The returned instance is stateless and reusable for
     *  multiple transformations.
     * 
     *  @param factory the renderer factory to use for the transformation
     *  @return a new Transform instance
     */
    def to [W] (factory: RendererFactory[W]): Transform[W] = 
      new Transform(parser, Render as factory, new Rules(Nil), factory.fileSuffix) 
    
  }
  
  /** Returns a new Builder instance for the specified parser factory.
   *  This factory is usually an object provided by the library
   *  or a plugin that is capable of parsing a specific markup
   *  format like Markdown or reStructuredText. The returned builder
   *  can then be used to specifiy the renderer to create the actual
   *  Transform instance.
   * 
   *  @param factory the parser factory to use
   *  @return a new Builder instance for specifying the renderer
   */
  def from (factory: ParserFactory): Builder = new Builder(factory)
  
  
  case class BatchConfig (input: InputConfig, output: OutputConfig)
  
  class BatchConfigBuilder private[Transform] (inputBuilder: InputConfigBuilder, outputBuilder: OutputConfigBuilder) {
    
    def withTemplates (parser: ParseTemplate) = 
      new BatchConfigBuilder(inputBuilder.withTemplates(parser), outputBuilder)
    
    def withDocTypeMatcher (matcher: Path => DocumentType) =
      new BatchConfigBuilder(inputBuilder.withDocTypeMatcher(matcher), outputBuilder)

    def withConfigFile (file: File) = 
      new BatchConfigBuilder(inputBuilder.withConfigFile(file), outputBuilder)
    def withConfigFile (name: String) =
      new BatchConfigBuilder(inputBuilder.withConfigFile(name), outputBuilder)
    def withConfigString (source: String) =
      new BatchConfigBuilder(inputBuilder.withConfigString(source), outputBuilder)
    
    def parallel = new BatchConfigBuilder(inputBuilder.parallel, outputBuilder.parallel)
    
    def build (parser: ParserFactory) = BatchConfig(inputBuilder.build(parser), outputBuilder.build)
  }
  
  object BatchConfigBuilder {
    
    def apply (root: File, codec: Codec) = {
      require(root.exists, "Directory "+root.getAbsolutePath()+" does not exist")
      require(root.isDirectory, "File "+root.getAbsolutePath()+" is not a directoy")
      
      val sourceDir = new File(root, "source")
      val targetDir = new File(root, "target")
      
      new BatchConfigBuilder(InputProvider.Directory(sourceDir)(codec), OutputProvider.Directory(targetDir)(codec))
    }
    
    def apply (inputBuilder: InputConfigBuilder, outputBuilder: OutputConfigBuilder) = 
      new BatchConfigBuilder(inputBuilder, outputBuilder)
    
  }
  
  object RootDirectory {
    def apply (name: String)(implicit codec: Codec) = BatchConfigBuilder(new File(name), codec)
    def apply (file: File)(implicit codec: Codec) = BatchConfigBuilder(file, codec)
  }
  
  object DefaultDirectories {
    def apply (implicit codec: Codec) = BatchConfigBuilder(new File(System.getProperty("user.dir")), codec)
  }
  
  
  
}