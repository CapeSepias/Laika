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

package laika.io
 
import java.io.BufferedReader
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import scala.collection.immutable.PagedSeq
import scala.io.Codec
import scala.util.parsing.input.CharSequenceReader
import scala.util.parsing.input.PagedSeqReader
import scala.util.parsing.input.Reader
import java.io.File
import laika.tree.Documents.Path
import laika.tree.Documents.Root

/** Represents the input for a parser, abstracting over various types of IO resources. 
 *  
 *  For parsers that use the parser combinators from the Scala SDK, 
 *  this trait offers a convenient `asParserInput` method. Other types of parsers
 *  may use the `java.io.Reader` provided by the `asReader` method. 
 * 
 *  @author Jens Halm
 */
trait Input {

  /** The input as a `java.io.Reader`.
   */
  def asReader: java.io.Reader

  /** The input as a Reader instance for the parser combinators of the Scala SDK.
   */
  def asParserInput: Reader[Char]
  
  def path: Path
  
  lazy val name = path.name
  
}

/** Factory methods for creating Input instances from different types of IO resources.
 * 
 *  @author Jens Halm
 */
object Input {
  
  trait Binary {
    def asBinaryInput: BinaryInput
  }
  
  /** A sub-trait for binary input which can only be created
   *  from a stream or a file, not from a `java.io.Reader` instance
   *  or from a `String`.
   */
  trait BinaryInput {
  
    def asStream: InputStream
    
  }
  
  private class StringInput (source: String, val path: Path) extends Input {
    
    def asReader = new StringReader(source)
  
    def asParserInput = new CharSequenceReader(source)
    
  }
  
  private class ReaderInput (val asReader: java.io.Reader, val path: Path) extends Input {
   
    def asParserInput = new PagedSeqReader(PagedSeq.fromReader(asReader))

  }
  
  private class StreamInput (stream: InputStream, val path: Path, codec: Codec) extends Input with Binary {
   
    def asBinaryInput: BinaryInput = new BinaryInput {
      val asStream = stream
    }
    
    def asParserInput = new PagedSeqReader(PagedSeq.fromReader(asReader))
    
    lazy val asReader = new BufferedReader(new InputStreamReader(stream, codec.decoder))
    
  }
  
  private class AutocloseStreamInput (stream: InputStream, p: Path, codec: Codec) extends StreamInput(stream,p,codec) with Closeable {
    
    override def asBinaryInput: BinaryInput = new BinaryInput with Closeable {
      val asStream = stream
      def close = stream close
    }
    
    def close = asReader.close
    
  }
  
  /** Creates a new Input instance from the specified source string.
   */
  def fromString (source: String, path: Path = Root): Input = new StringInput(source, path)

  /** Creates a new Input instance for the file with the specified name.
   *  
   *  @param name the name of the file
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (name: String)(implicit codec: Codec): Input with Binary with Closeable 
    = new AutocloseStreamInput(new FileInputStream(name), Path(stripSuffix(name)), codec)
  
  /** Creates a new Input instance for the specified file.
   *  
   *  @param file the file to use as input
   *  @param codec the character encoding of the file, if not specified the platform default will be used.
   */
  def fromFile (file: File)(implicit codec: Codec): Input with Binary with Closeable 
    = new AutocloseStreamInput(new FileInputStream(file), Path(stripSuffix(file.getName)), codec)
  
  def fromFile (file: File, virtualPath: Path)(implicit codec: Codec): Input with Binary with Closeable 
    = new AutocloseStreamInput(new FileInputStream(file), virtualPath / stripSuffix(file.getName), codec)
  
  private def stripSuffix (filename: String) = filename.lastIndexOf(".") match {
    case -1    => filename
    case index => filename.substring(0, index)
  }  
  
  /** Creates a new Input instance for the specified InputStream.
   *  
   *  @param stream the stream to read character data from
   *  @param name the name of the input source
   *  @param codec the character encoding used by the text input, if not specified the platform default will be used.
   */
  def fromStream (stream: InputStream, path: Path = Root)(implicit codec: Codec): Input with Binary = new StreamInput(stream, path, codec)
  
  /** Creates a new Input instance for the specified Reader.
   */
  def fromReader (reader: java.io.Reader, path: Path = Root): Input = new ReaderInput(reader, path)
  
  
}