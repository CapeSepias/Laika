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

package laika.api

import java.io.{File, InputStream, Reader}

import laika.api.Render.{RenderGatheredOutput, RenderMappedOutput}
import laika.api.ext.ExtensionBundle
import laika.directive.Directives.Templates
import laika.factory.{ParserFactory, RenderResultProcessor, RendererFactory}
import laika.io.InputProvider._
import laika.io.Output.Binary
import laika.io.OutputProvider._
import laika.io._
import laika.rewrite.DocumentCursor
import laika.template.ParseTemplate
import laika.tree.Documents._
import laika.tree.Elements._

import scala.io.Codec
  
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
 *  Example for transforming an entire directory and its subdirectories to HTML in a target directory:
 *  
 *  {{{
 *  Transform from Markdown to HTML fromDirectory "source" toDirectory "target"
 *  }}}
 *  
 *  Example for transforming an entire directory and its subdirectories to a single PDF file:
 *  
 *  {{{
 *  Transform from Markdown to PDF fromDirectory "source" toFile "hello.pdf"
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
 *  @tparam Writer the writer API to use which varies depending on the renderer
 * 
 *  @author Jens Halm
 */
abstract class Transform [Writer] private[Transform] (parse: Parse) {
  
  /** The type of the transformation target for a single input document.
   */
  type DocTarget
  
  /** The type of the transformation target for an entire tree of input documents. 
   */
  type TreeTarget
  
  /** The concrete implementation of the abstract Transform type.
   */
  type ThisType <: Transform[Writer]
  
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
  def usingRule (newRule: RewriteRule): ThisType = creatingRule(_ => newRule)
  
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
  def creatingRule (newRule: DocumentCursor => RewriteRule): ThisType = using(new ExtensionBundle {
    override def rewriteRules: Seq[DocumentCursor => RewriteRule] = Seq(newRule)
  })
  
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
  def rendering (customRenderer: Writer => RenderFunction): ThisType

  /** Returns a new Transform instance with the specified extension bundles installed.
    *
    * Bundles are usually provided by libraries (by Laika itself or a 3rd-party extension library)
    * or as re-usable building blocks by application code.
    */
  def using (bundles: ExtensionBundle*): ThisType
  
  
  /** Parses the specified string and returns a new Operation instance which allows to specify the output.
   *  Any kind of input is valid, including an empty string. 
   */
  def fromString (str: String): DocTarget = fromDocument(parse.fromString(str))
  
  /** Parses the input from the specified reader
   *  and returns a new Operation instance which allows to specify the output.
   */
  def fromReader (reader: Reader): DocTarget = fromDocument(parse.fromReader(reader))
  
  /** Parses the file with the specified name
   *  and returns a new Operation instance which allows to specify the output.
   *  Any kind of character input is valid, including empty files.
   * 
   *  @param name the name of the file to parse
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (name: String)(implicit codec: Codec): DocTarget = fromDocument(parse.fromFile(name)(codec))
  
  /** Parses the specified file
   *  and returns a new Operation instance which allows to specify the output.
   *  Any kind of character input is valid, including empty files.
   * 
   *  @param file the file to read from
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (file: File)(implicit codec: Codec): DocTarget = fromDocument(parse.fromFile(file)(codec))
  
  /** Parses the input from the specified stream
   *  and returns a new Operation instance which allows to specify the output.
   * 
   *  @param stream the stream to use as input for the parser
   *  @param codec the character encoding of the stream, if not specified the platform default will be used.
   */
  def fromStream (stream: InputStream)(implicit codec: Codec): DocTarget = fromDocument(parse.fromStream(stream)(codec))
  
  /** Parses files from the specified directory and its subdirectories
   *  and returns a new target instance which allows to specify the output and 
   *  other configuration options.
   * 
   *  @param name the name of the directory to traverse
   *  @param codec the character encoding of the files, if not specified the platform default will be used.
   *  @return a builder which allows to specify the output and other configuration options
   */
  def fromDirectory (name: String)(implicit codec: Codec): TreeTarget = fromDirectory(name, hiddenFileFilter)(codec)
  
  /** Parses files from the specified directory and its subdirectories
   *  and returns a new target instance which allows to specify the output and 
   *  other configuration options.
   * 
   *  @param name the name of the directory to traverse
   *  @param exclude the files to exclude from processing
   *  @param codec the character encoding of the files, if not specified the platform default will be used.
   *  @return a builder which allows to specify the output and other configuration options
   */
  def fromDirectory (name: String, exclude: FileFilter)(implicit codec: Codec): TreeTarget = fromTree(InputProvider.Directory(name, exclude)(codec))

  /** Parses files from the specified directory and its subdirectories
   *  and returns a new target instance which allows to specify the output and 
   *  other configuration options.
   * 
   *  @param dir the directory to traverse
   *  @param codec the character encoding of the files, if not specified the platform default will be used.
   *  @return a builder which allows to specify the output and other configuration options
   */
  def fromDirectory (dir: File)(implicit codec: Codec): TreeTarget = fromDirectory(dir, hiddenFileFilter)(codec)
  
  /** Parses files from the specified directory and its subdirectories
   *  and returns a new target instance which allows to specify the output and 
   *  other configuration options.
   * 
   *  @param dir the directory to traverse
   *  @param exclude the files to exclude from processing
   *  @param codec the character encoding of the files, if not specified the platform default will be used.
   *  @return a builder which allows to specify the output and other configuration options
   */
  def fromDirectory (dir: File, exclude: FileFilter)(implicit codec: Codec): TreeTarget = fromTree(InputProvider.Directory(dir, exclude)(codec))
  
  /** Parses files from the specified directories and its subdirectories, 
   *  merging them into a tree with a single root
   *  and returns a new target instance which allows to specify the output and 
   *  other configuration options.
   * 
   *  @param roots the root directories to traverse
   *  @param codec the character encoding of the files, if not specified the platform default will be used.
   */
  def fromDirectories (roots: Seq[File])(implicit codec: Codec): TreeTarget = fromDirectories(roots, hiddenFileFilter)(codec)
  
  /** Parses files from the specified directories and its subdirectories, 
   *  merging them into a tree with a single root
   *  and returns a new target instance which allows to specify the output and 
   *  other configuration options.
   * 
   *  @param roots the root directories to traverse
   *  @param exclude the files to exclude from processing
   *  @param codec the character encoding of the files, if not specified the platform default will be used.
   */
  def fromDirectories (roots: Seq[File], exclude: FileFilter)(implicit codec: Codec): TreeTarget = fromTree(InputProvider.Directories(roots, exclude)(codec))
  
  /** Parses from the specified input and returns a new target instance 
   *  which allows to specify the output and other configuration options.
   * 
   *  @param inputBuilder the input to transform
   */
  def fromTree (inputBuilder: InputConfigBuilder): TreeTarget
  
  /** Renders the specified document and returns a new target instance 
   *  which allows to specify the output and other configuration options.
   * 
   *  @param doc the document to transform
   */
  protected[this] def fromDocument (doc: Document): DocTarget
  
}

/** Serves as an entry point to the Transform API.
 * 
 *  @author Jens Halm
 */
object Transform {
   
  
  /** A transform operation that maps each input document of a
   *  given input tree to a corresponding output document
   *  in the destination tree.
   *  
   *  @param parse the parser to use for parsing the input
   *  @param render the renderer to use for producing the output
   */
  class TransformMappedOutput[Writer] (parse: Parse, render: RenderMappedOutput[Writer]) extends Transform[Writer](parse) {
    
    type DocTarget = Render.SingleTarget
  
    type TreeTarget = MappedTreeTarget
  
    type ThisType = TransformMappedOutput[Writer]
    
    def rendering (customRenderer: Writer => RenderFunction): ThisType = new TransformMappedOutput(parse, render using customRenderer)

    def using (bundles: ExtensionBundle*): ThisType = new TransformMappedOutput(parse.using(bundles:_*), render.using(bundles:_*))
    
    def fromDocument (doc: Document): Render.SingleTarget = new Render.SingleTarget {
      protected def renderTo (out: Output) = render.from(doc).toOutput(out)
    }
    
    def fromTree (input: InputConfigBuilder): MappedTreeTarget = new MappedTreeTarget(input)
    
    /** Represents a tree of output destinations for recursive render operations,
     *  where each input document will be mapped to a corresponding output document. 
     *  Various types of output can be specified to trigger the actual rendering.
     *  
     *  @param inputBuilder the builder used to configure the input tree
     *  @param isParallel indicates whether the transform operations for the individual targets should run in parallel
     */
    class MappedTreeTarget (inputBuilder: InputConfigBuilder, 
                            isParallel:Boolean = false) extends TreeConfigBuilder[MappedTreeTarget] 
                                                        with Render.MappedTreeTarget { 
      
      protected def withInputBuilder (f: InputConfigBuilder => InputConfigBuilder): MappedTreeTarget = new MappedTreeTarget(f(inputBuilder), isParallel)
      
      protected def withParallelExecution: MappedTreeTarget = new MappedTreeTarget(inputBuilder.inParallel, true)
      
      protected def renderTo (out: OutputConfigBuilder): Unit = {
        val tree = parse.fromTree(inputBuilder)
        render.from(tree).toTree(if (isParallel) out.inParallel else out)
      }
    }
    
  }
  
  /** A transform operation that gathers input from one or more
   *  input documents in an input tree structure to be rendered 
   *  to a single output destination.
   *  
   *  @param parse the parser to use for parsing the input
   *  @param render the renderer to use for producing the output
   */
  class TransformGatheredOutput[Writer] (parse: Parse, render: RenderGatheredOutput[Writer]) extends Transform[Writer](parse) {
    
    type DocTarget = Render.BinaryTarget
  
    type TreeTarget = GatherTarget
  
    type ThisType = TransformGatheredOutput[Writer]
    
    def rendering (customRenderer: Writer => RenderFunction): ThisType = new TransformGatheredOutput(parse, render using customRenderer)

    def using (bundles: ExtensionBundle*): ThisType = new TransformGatheredOutput(parse.using(bundles:_*), render.using(bundles:_*))
    
    def fromDocument (doc: Document): Render.BinaryTarget = new Render.BinaryTarget {
      protected def renderBinary (out: Output with Binary) = render.from(doc).toBinaryOutput(out)
    }
    
    def fromTree (input: InputConfigBuilder): GatherTarget = new GatherTarget(input)
    
    /** A target for a render operation that accepts only binary output, the final
     *  result of the gathering operation that merges the individual render results
     *  obtained from transforming the input tree.
     *  
     *  @param inputBuilder the builder used to configure the input tree
     *  @param isParallel indicates whether the transform operations for the individual targets should run in parallel
   	 */
    class GatherTarget (inputBuilder: InputConfigBuilder, 
                        isParallel:Boolean = false) extends TreeConfigBuilder[GatherTarget] 
                                                    with Render.BinaryTarget { 
      
      protected def withInputBuilder (f: InputConfigBuilder => InputConfigBuilder): GatherTarget = new GatherTarget(f(inputBuilder), isParallel)
      
      protected def withParallelExecution: GatherTarget = new GatherTarget(inputBuilder.inParallel, true)
      
      protected def renderBinary (out: Output with Binary): Unit = {
        val tree = parse.fromTree(inputBuilder)
        render.from(tree).toBinaryOutput(out) // TODO - parallel output?
      }
    }

    
  }

  /** API for configuring a batch operation for an input tree.
   *  Gives access to all relevant aspects of traversing, parsing and rendering
   *  a tree of inputs.
   */
  trait TreeConfigBuilder[ThisType] {
    
    protected def withInputBuilder (f: InputConfigBuilder => InputConfigBuilder): ThisType
    
    protected def withParallelExecution: ThisType
    
    /** Specifies the template engine to use for
     *  parsing all template inputs found in the tree.
     */
    def withTemplateParser (parse: ParseTemplate): ThisType = withInputBuilder(_.withTemplateParser(parse))
    
    /** Specifies custom template directives to use with
     *  the default template engine.
     */
    def withTemplateDirectives (directives: Templates.Directive*): ThisType = withInputBuilder(_.withTemplateDirectives(directives:_*))
    
    /** Instructs both the parser and renderer to process all inputs and outputs in parallel.
     *  The recursive structure of document trees will be flattened before parsing and rendering
     *  and then get reassembled afterwards, therefore the parallel processing
     *  includes all subtrees of the document tree.
     *  
     *  The actual transformation is a three phase process, the first (parsing) and
     *  third (rendering) can run in parallel. The second phase in the middle cannot,
     *  as this is the document tree model rewrite step where things like cross references or
     *  table of contents get processed that need access to more than just the current
     *  document. 
     */
    def inParallel: ThisType = withParallelExecution
    
  }
  
  /** Step in the setup for a transform operation where the
   *  renderer must be specified.
   */
  class Builder private[Transform] (parse: Parse) {

    /** Returns a new Builder instance adding the specified parser factory.
     *  This factory is usually an object provided by the library
     *  or a plugin that is capable of parsing a specific markup
     *  format like Markdown or reStructuredText.
     *  
     *  This method is useful if you want to combine different markup
     *  formats within a single document tree. 
     * 
     *  @param factory the parser factory to add to the previously specified parsers
     *  @return a new Builder instance
     */
    def or (factory: ParserFactory): Builder = new Builder(parse or factory)
    
    /** Creates and returns a new Transform instance for the specified renderer and the
     *  previously specified parser. The returned instance is stateless and reusable for
     *  multiple transformations.
     * 
     *  @param factory the renderer factory to use for the transformation
     *  @return a new Transform instance
     */
    def to [Writer] (factory: RendererFactory[Writer]): TransformMappedOutput[Writer] = 
      new TransformMappedOutput(parse, Render as factory)
    
    /** Creates and returns a new Transform instance for the specified renderer and the
     *  previously specified parser. The returned instance is stateless and reusable for
     *  multiple transformations.
     * 
     *  @param processor the processor to use for the transformation
     *  @return a new Transform instance
     */
    def to [Writer] (processor: RenderResultProcessor[Writer]): TransformGatheredOutput[Writer] = 
      new TransformGatheredOutput(parse, Render as processor)
    
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
  def from (factory: ParserFactory): Builder = new Builder(Parse as factory)
  
  
}
