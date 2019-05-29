package laika.factory

import cats.effect.Async
import laika.io.{BinaryOutput, RenderedTreeRoot}

/** Post processor for the result output of a renderer.
 *  Useful for scenarios where interim formats will be generated
 *  (e.g. XSL-FO for a PDF target or XHTML for an EPUB target).
 *  
 *  @author Jens Halm
 */
trait BinaryPostProcessor {

  /** Processes the interim render result and writes it to the specified final output.
   */
  def process[F[_]: Async] (result: RenderedTreeRoot, output: BinaryOutput): F[Unit]
  
}
