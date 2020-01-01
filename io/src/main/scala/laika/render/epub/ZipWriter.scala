/*
 * Copyright 2012-2020 the original author or authors.
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

package laika.render.epub

import java.io.InputStream
import java.util.zip.{CRC32, ZipEntry, ZipOutputStream}

import cats.effect.Async
import cats.implicits._
import laika.ast.Path
import laika.io.model.{BinaryInput, BinaryOutput}
import laika.io.runtime.{CopyRuntime, Runtime}

/**
  * @author Jens Halm
  */
object ZipWriter {

  /** Writes an EPUB Zip file to the specified output.
    * The virtual path of the given inputs will also become the path within
    * the Zip container.
    * The implementation follows the EPUB specification in that the first
    * file (called `mimeType`) is written uncompressed. Hence this is not
    * a generic zip utility as the method name suggests.
    */
  def zipEPUB[F[_]: Async: Runtime] (inputFs: Seq[BinaryInput[F]], output: BinaryOutput[F]): F[Unit] = {

    def copy (inputs: Vector[(InputStream, Path)], zipOut: ZipOutputStream): F[Unit] = {
    
      def writeEntry (input: (InputStream, Path), prepareEntry: ZipEntry => F[Unit] = _ => Async[F].unit): F[Unit] = for {
        entry <- Async[F].delay(new ZipEntry(input._2.relativeTo(Path.Root).toString))
        _     <- prepareEntry(entry)
        _     <- Async[F].delay(zipOut.putNextEntry(entry))
        _     <- CopyRuntime.copy(input._1, zipOut)
        _     <- Async[F].delay(zipOut.closeEntry())
      } yield ()
      
      
      def prepareUncompressedEntry (entry: ZipEntry): F[Unit] = Async[F].delay {
        val content = StaticContent.mimeType
        val crc32 = new CRC32
        entry.setMethod(ZipOutputStream.STORED)
        entry.setSize(content.length)
        crc32.update(content.getBytes("UTF-8"))
        entry.setCrc(crc32.getValue)
      }

      writeEntry(inputs.head, prepareUncompressedEntry) >> 
        inputs.tail.map(writeEntry(_)).sequence >> 
          Async[F].delay(zipOut.close())
    }
    
    val in = inputFs.toVector.map { doc =>
      doc.input.map((_, doc.path))
    }.sequence

    val out = output.resource.map(new ZipOutputStream(_))
    
    Runtime[F].runBlocking {
      (in, out).tupled.use {
        case (ins, zip) => copy(ins, zip)
      }
    }
  }
  
}
