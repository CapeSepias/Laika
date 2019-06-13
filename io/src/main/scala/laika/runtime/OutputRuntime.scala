package laika.runtime

import java.io._

import cats.effect.{Async, Resource}
import cats.implicits._
import laika.io._

import scala.io.Codec

/**
  * @author Jens Halm
  */
object OutputRuntime {
  
  def write[F[_]: Async] (result: String, output: TextOutput): F[Unit] = {
    output match {
      case StringOutput(_) => Async[F].unit
      case TextFileOutput(file, _, codec) => fileWriter(file, codec).use { writer =>
        Async[F].delay(writer.write(result))
      }  
    }
  }
  
  def createDirectory[F[_]: Async] (file: File): F[Unit] = 
    Async[F].delay(file.exists || file.mkdirs()).flatMap(if (_) Async[F].unit 
    else Async[F].raiseError(new IOException(s"Unable to create directory ${file.getAbsolutePath}")))
 
  def fileWriter[F[_]: Async] (file: File, codec: Codec): Resource[F, Writer] = Resource.fromAutoCloseable(Async[F].delay {
    new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), codec.charSet))
  })

  def asStream[F[_]: Async] (output: BinaryOutput): Resource[F, OutputStream] = output match {
    case BinaryFileOutput(file, _) => 
      Resource.fromAutoCloseable(Async[F].delay(new BufferedOutputStream(new FileOutputStream(file))))
    case ByteOutput(out, _) => Resource.pure(out) // TODO - 0.12 - verify need + use cases for this type
  }

}
