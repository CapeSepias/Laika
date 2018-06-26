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

package laika.io

import java.io.File

import laika.tree.Paths.{Path, Root}

import scala.io.Codec

/** Represents a tree structure of Inputs, abstracting over various types of IO resources. 
 *  
 *  While the default implementations wrap the structure of directories in the file system,
 *  other implementations may build an entirely virtual input tree structure. 
 * 
 *  @author Jens Halm
 */
trait InputProvider {

  
  /** The local name of the tree represented by this provider.
   */
  lazy val name: String = path.name

  /** The full path of the tree represented by this provider.
   *  This path is always an absolute path
   *  from the root of the (virtual) input tree,
   *  therefore does not represent the filesystem
   *  path in case of file I/O.
   */
  def path: Path
  
  /** All inputs for configuration files
   *  on this level of the input hierarchy.
   */
  def configDocuments: Seq[Input]

  /** All inputs for markup documents
   *  that need to be parsed
   *  on this level of the input hierarchy.
   */
  def markupDocuments: Seq[Input]

  /** All inputs for dynamic files
   *  that need to be processed
   *  on this level of the input hierarchy.
   */
  def dynamicDocuments: Seq[Input]
  
  /** All inputs for style sheets
   *  that need to be processed
   *  on this level of the input hierarchy,
   *  mapped to their format.
   */
  def styleSheets: Map[String,Seq[Input]]

  /** All inputs for static files
   *  that need to be copied
   *  from this level of the input hierarchy.
   */
  def staticDocuments: Seq[Input]

  /** All inputs for template files
   *  on this level of the input hierarchy.
   */
  def templates: Seq[Input]
  
  /** All subtrees of this provider.
   */
  def subtrees: Seq[InputProvider]
  
  /** The paths this provider has been created from 
   *  or an empty list if this input provider does 
   *  not originate from the file system.
   */
  def sourcePaths: Seq[String]
  
}


/** Factory methods for creating `InputProvider` and `InputConfigBuilder`
 *  instances. The latter offers fine grained control like setting
 *  custom document type matchers or custom template engines, before
 *  creating the actual `InputProvider`.
 */
object InputProvider {

  type FileFilter = File => Boolean
  
  
  private class DirectoryInputProvider (dirs: Seq[File], val path: Path, exclude: FileFilter, docTypeMatcher: PartialFunction[Path, DocumentType], codec: Codec) extends InputProvider {
    
    import DocumentType._
    
    private def docType (f: File) = docTypeMatcher.lift(path / f.getName).getOrElse(Ignored)

    private def toInput (pair: (DocumentType,File)) = Input.fromFile(pair._2, path)(codec)

    private lazy val files = {
      def filesInDir (dir: File) = dir.listFiles filter (f => f.isFile && !exclude(f))
      dirs flatMap filesInDir map (f => (docType(f), f)) groupBy (_._1) withDefaultValue Nil
    }
    
    private def documents (docType: DocumentType) = files(docType).map(toInput)
    
    lazy val configDocuments: Seq[Input] = documents(Config)
    
    lazy val markupDocuments: Seq[Input] = documents(Markup)
    
    lazy val dynamicDocuments: Seq[Input] = documents(Dynamic)

    lazy val styleSheets: Map[String, Seq[Input]] = files collect { case p@(StyleSheet(format), pairs) => (format, pairs map toInput) }
    
    lazy val staticDocuments: Seq[Input] = documents(Static)
    
    lazy val templates: Seq[Input] =  documents(Template)
    
    lazy val sourcePaths: Seq[String] = dirs map (_.getAbsolutePath)
    
    lazy val subtrees: Seq[InputProvider] = {
      def subDirs (dir: File) = dir.listFiles filter (f => f.isDirectory && !exclude(f) && docType(f) != Ignored)
      val byName = (dirs flatMap subDirs groupBy (_.getName)).values
      byName map (subs => new DirectoryInputProvider(subs, path / subs.head.getName, exclude, docTypeMatcher, codec)) toList
    }
    
  }

  def forWorkingDirectory (docTypeMatcher: PartialFunction[Path, DocumentType], exclude: FileFilter)(implicit codec: Codec): InputProvider =
    forRootDirectories(Seq(new File(System.getProperty("user.dir"))), docTypeMatcher, exclude)
  
  /** Creates an InputProvider based on the specified directories, including
   *  all subdirectories. The directories will be merged into a tree with a single
   *  root. If any of the specified root directories contain sub-directories with
   *  the same name, these sub-directories will be merged, too.
   * 
   *  @param roots the root directories of the input tree
   *  @param docTypeMatcher a function determining the document type based on the path of the input
   *  @param exclude the files to exclude from processing
   *  @param codec the character encoding of the files, if not specified the platform default will be used
   */
  def forRootDirectories (roots: Seq[File], docTypeMatcher: PartialFunction[Path, DocumentType], exclude: FileFilter)(implicit codec: Codec): InputProvider = {
    require(roots.nonEmpty, "The specified roots sequence must contain at least one directory")
    for (root <- roots) {
      require(root.exists, s"Directory ${root.getAbsolutePath} does not exist")
      require(root.isDirectory, s"File ${root.getAbsolutePath} is not a directory")
    }
      
    new DirectoryInputProvider(roots, Root, exclude, docTypeMatcher, codec)
  }
  
  /** Responsible for building new InputProviders based
   *  on the specified document type matcher and codec.
   */
  trait ProviderBuilder {
    def build (docTypeMatcher: PartialFunction[Path, DocumentType]): InputProvider
  }
  
  /** A filter that selects files that are hidden according to `java.io.File.isHidden`.
   */
  val hiddenFileFilter: FileFilter = file => file.isHidden && file.getName != "."

}
