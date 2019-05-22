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

package laika.io

import java.io.File

import laika.api.builder.OperationConfig
import laika.ast.{Path, TextDocumentType}

import scala.io.Codec

/** API for producing a result from processing various types of input.
  *
  * This is essentially a collection of shortcuts that allow any class
  * merging in this trait to define all input related operations in terms of the only
  * abstract method `fromInput`. Calling `fromFile("foo.md")` for example
  * is only a convenient shortcut for calling `fromInput(Input.fromFile("foo.md")`.
  *
  * @author Jens Halm
  */
trait InputOps {

  /** The type of the result returned by all operations of this trait.
    */
  type InputResult

  /** The type of text document created by this instance.
    */
  def docType: TextDocumentType

  /**  Returns the result from parsing the specified string.
    *  Any kind of input is valid, including an empty string.
    */
  def fromString (str: String): InputResult = fromInput(StringInput(str, docType))

  /** Returns the result from parsing the file with the specified name.
    *  Any kind of character input is valid, including empty files.
    *
    *  @param name the name of the file to parse
    *  @param codec the character encoding of the file, if not specified the platform default will be used.
    */
  def fromFile (name: String)(implicit codec: Codec): InputResult = fromFile(new File(name))

  /** Returns the result from parsing the specified file.
    *  Any kind of character input is valid, including empty files.
    *
    *  @param file the file to use as input
    *  @param codec the character encoding of the file, if not specified the platform default will be used.
    */
  def fromFile (file: File)(implicit codec: Codec): InputResult = fromInput(TextFileInput(file, docType, Path(file.getName), codec))

  /** Returns the result from parsing the specified input.
    *
    *  This is a generic method based on Laika's IO abstraction layer that concrete
    *  methods delegate to. Usually not used directly in application code, but
    *  might come in handy for very special requirements.
    *
    *  @param input the input for the parser
    */
  def fromInput (input: TextInput): InputResult

}

/** API for producing a result from processing various types of input trees.
  *
  * This is essentially a collection of shortcuts that allow any class
  * merging in this trait to define all input related operations in terms of the only
  * abstract method `fromInputTree`. Calling `fromDirectory("src")` for example
  * is only a convenient shortcut for calling `fromInputTree(InputTree.fromDirectory("src")`.
  *
  * @author Jens Halm
  */
trait InputTreeOps {

  type FileFilter = File => Boolean

  /** The type of the result returned by all operations of this trait.
    */
  type InputTreeResult

  /** The configuration to use for all input operations.
    */
  def config: OperationConfig

  /**  Returns the result obtained by parsing files from the
    *  specified directory and its subdirectories.
    *
    *  @param name the name of the directory to traverse
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromDirectory (name: String)(implicit codec: Codec): InputTreeResult =
    fromDirectory(new File(name), DirectoryInput.hiddenFileFilter)(codec)

  /**  Returns the result obtained by parsing files from the
    *  specified directory and its subdirectories.
    *
    *  @param name the name of the directory to traverse
    *  @param exclude the files to exclude from processing
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromDirectory (name: String, exclude: FileFilter)(implicit codec: Codec): InputTreeResult =
    fromDirectory(new File(name), exclude)(codec)

  /**  Returns the result obtained by parsing files from the
    *  specified directory and its subdirectories.
    *
    *  @param dir the root directory to traverse
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromDirectory (dir: File)(implicit codec: Codec): InputTreeResult =
    fromDirectory(dir, DirectoryInput.hiddenFileFilter)(codec)

  /**  Returns the result obtained by parsing files from the
    *  specified directory and its subdirectories.
    *
    *  @param dir the root directory to traverse
    *  @param exclude the files to exclude from processing
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromDirectory (dir: File, exclude: FileFilter)(implicit codec: Codec): InputTreeResult =
    fromDirectories(Seq(dir), exclude)(codec)

  /**  Returns the result obtained by parsing files from the
    *  specified directories and its subdirectories, merging them into
    *  a tree with a single root.
    *
    *  @param roots the root directories to traverse
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromDirectories (roots: Seq[File])(implicit codec: Codec): InputTreeResult =
    fromDirectories(roots, DirectoryInput.hiddenFileFilter)(codec)

  /**  Returns the result obtained by parsing files from the
    *  specified directories and its subdirectories, merging them into
    *  a tree with a single root.
    *
    *  @param roots the root directories to traverse
    *  @param exclude the files to exclude from processing
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromDirectories (roots: Seq[File], exclude: FileFilter)(implicit codec: Codec): InputTreeResult =
    fromTreeInput(DirectoryInput(roots, codec, config.docTypeMatcher, exclude))

  /**  Returns the result obtained by parsing files from the
    *  current working directory.
    *
    *  @param exclude the files to exclude from processing
    *  @param codec the character encoding of the files, if not specified the platform default will be used.
    */
  def fromWorkingDirectory (exclude: FileFilter = DirectoryInput.hiddenFileFilter)(implicit codec: Codec): InputTreeResult =
    fromDirectories(Seq(new File(System.getProperty("user.dir"))), exclude)

  /** Returns the result obtained by parsing files from the
    *  specified input tree.
    *
    *  @param inputTree the input tree to process
    */
  def fromTreeInput(input: TreeInput): InputTreeResult

}
