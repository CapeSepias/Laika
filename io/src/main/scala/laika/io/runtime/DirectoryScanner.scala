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

package laika.io.runtime

import java.nio.file.{DirectoryStream, Files, Path => JPath}

import cats.effect.{Async, Resource}
import cats.implicits._
import laika.ast.DocumentType.Static
import laika.ast.Path.Root
import laika.ast.{Path, TextDocumentType}
import laika.io.model._

import scala.collection.{AbstractIterator, Iterator}

/** Scans a directory in the file system and transforms it into a generic InputCollection
  * that can serve as input for parallel parsers or transformers.
  * 
  * @author Jens Halm
  */
object DirectoryScanner {

  /** Scans the specified directory and transforms it into a generic InputCollection.
    */
  def scanDirectories[F[_]: Async] (input: DirectoryInput): F[TreeInput[F]] = {
    val sourcePaths: Seq[String] = input.directories map (_.getAbsolutePath)
    join(input.directories.map(d => scanDirectory(Root, d.toPath, input))).map(_.copy(sourcePaths = sourcePaths))
  }
  
  private def scanDirectory[F[_]: Async] (vPath: Path, filePath: JPath, input: DirectoryInput): F[TreeInput[F]] =
    Resource
      .fromAutoCloseable(Async[F].delay(Files.newDirectoryStream(filePath)))
      .use(asInputCollection(vPath, input)(_))
  
  private def join[F[_]: Async] (collections: Seq[F[TreeInput[F]]]): F[TreeInput[F]] = collections
    .toVector
    .sequence
    .map(_.reduceLeftOption(_ ++ _).getOrElse(TreeInput.empty))

  private def asInputCollection[F[_]: Async] (path: Path, input: DirectoryInput)(directoryStream: DirectoryStream[JPath]): F[TreeInput[F]] = {

    def toCollection (filePath: JPath): F[TreeInput[F]] = {
      
      val childPath = path / filePath.getFileName.toString
      
      def binaryInput = InputRuntime.binaryFileResource(filePath.toFile)

      if (input.fileFilter(filePath.toFile)) TreeInput.empty[F].pure[F]
      else if (Files.isDirectory(filePath)) scanDirectory(childPath, filePath, input)
      else input.docTypeMatcher(childPath) match {
        case docType: TextDocumentType => TreeInput[F](Seq(TextInput.fromFile(childPath, docType, filePath.toFile, input.codec)), Nil).pure[F]
        case Static                    => TreeInput[F](Nil, Seq(BinaryInput(childPath, binaryInput, Some(filePath.toFile)))).pure[F]
        case _                         => TreeInput.empty[F].pure[F]
      }
    }

    val collections = for {
      path <- JIteratorWrapper(directoryStream.iterator).toSeq
    } yield toCollection(path)

    join(collections)
  }

  // copied from SDK source to avoid having either a dependency to scala-compat or a warning with 2.13
  // this is literally the one place in the Laika source where we need to deal with a Java collection
  case class JIteratorWrapper[A](underlying: java.util.Iterator[A]) extends AbstractIterator[A] with Iterator[A] {
    def hasNext: Boolean = underlying.hasNext
    def next(): A = underlying.next
  }
  
}
