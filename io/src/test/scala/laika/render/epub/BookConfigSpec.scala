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

import laika.ast.DocumentMetadata
import laika.ast.Path.Root
import laika.config.Config.ConfigResult
import laika.config.{Config, ConfigBuilder, ConfigDecoder, ConfigParser, Key}
import laika.format.EPUB.BookConfig
import laika.time.PlatformDateFormat
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
  * @author Jens Halm
  */
class BookConfigSpec extends AnyWordSpec with Matchers {

  private val testKey = Key("test")
  
  def decode[T: ConfigDecoder] (input: String): ConfigResult[T] =
    ConfigParser.parse(input).resolve().flatMap(_.get[T](Key.root))

  def decode[T: ConfigDecoder] (config: Config): ConfigResult[T] = config.get[T](testKey)
  
  "The codec for EPUB book configuration " should {

    "decode defaults with an empty config" in {
      BookConfig.decodeWithDefaults(Config.empty) shouldBe Right(BookConfig())
    }
    
    "decode an instance with fallbacks" in {
      val input =
        """{
          |  metadata {
          |    identifier = XX-33-FF-01
          |    author = "Helen North"
          |    language = en
          |    date = "2002-10-10T12:00:00"
          |  }
          |  navigationDepth = 3
          |  coverImage = cover.jpg
          |  epub {
          |    metadata {
          |      identifier = XX-33-FF-02
          |      author = "Maria South"
          |    }
          |    navigationDepth = 4
          |  }
          |}
        """.stripMargin
      ConfigParser.parse(input).resolve().flatMap(BookConfig.decodeWithDefaults) shouldBe Right(BookConfig(
        DocumentMetadata(
          Some("XX-33-FF-02"),
          Seq("Maria South", "Helen North"),
          Some("en"),
          Some(PlatformDateFormat.parse("2002-10-10T12:00:00").toOption.get)
        ),
        Some(4),
        Some(Root / "cover.jpg")
      ))
    }

    "round-trip encode and decode" in {
      val input = BookConfig(DocumentMetadata(Some("XX-33-FF-01")), Some(3), Some(Root / "cover.jpg"))
      val encoded = ConfigBuilder.empty.withValue(testKey, input).build
      decode[BookConfig](encoded) shouldBe Right(BookConfig(
        DocumentMetadata(
          Some("XX-33-FF-01")
        ),
        Some(3),
        Some(Root / "cover.jpg")
      ))
    }
    
  }
  
}
