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

package laika.sbt

import java.util.concurrent.Executors

import cats.effect.{Blocker, ContextShift, IO}
import laika.api.builder.{OperationConfig, ParserBuilder}
import laika.api.{MarkupParser, Transformer}
import laika.config.{ConfigBuilder, LaikaKeys}
import laika.factory.MarkupFormat
import laika.format.{HTML, Markdown, ReStructuredText}
import laika.io.api.TreeParser
import laika.io.implicits._
import laika.io.model.{InputTree, InputTreeBuilder}
import laika.sbt.LaikaPlugin.ArtifactDescriptor
import laika.sbt.LaikaPlugin.autoImport._
import sbt.Keys._
import sbt._

import scala.concurrent.ExecutionContext

/** Implementations for Laika's sbt settings.
  *
  * @author Jens Halm
  */
object Settings {

  import Def._

  implicit lazy val processingContext: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  lazy val blocker: Blocker = Blocker.liftExecutionContext(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))


  val defaultInputs: Initialize[InputTreeBuilder[IO]] = setting {
    InputTree
      .apply[IO]((Laika / excludeFilter).value.accept _)
      .addDirectories((Laika / sourceDirectories).value)(laikaConfig.value.encoding)
  }
  
  val describe: Initialize[String] = setting {

    val userConfig = laikaConfig.value

    def mergedConfig (config: OperationConfig): OperationConfig = {
      config.copy(
        bundleFilter = userConfig.bundleFilter,
        renderMessages = userConfig.renderMessages,
        failOnMessages = userConfig.failOnMessages
      )
    }

    def createParser (format: MarkupFormat): ParserBuilder = {
      val parser = MarkupParser.of(format)
      parser.withConfig(mergedConfig(parser.config)).using(laikaExtensions.value: _*)
    }

    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .withConfig(mergedConfig(createParser(Markdown).config))
      .using(laikaExtensions.value: _*)
      .io(blocker)
      .parallel[IO]
      .withAlternativeParser(createParser(ReStructuredText))
      .build

    val inputs = InputTree
      .apply[IO]((Laika / excludeFilter).value.accept _)
      .addDirectories((Laika / sourceDirectories).value)(laikaConfig.value.encoding)

    transformer
      .fromInput(inputs)
      .toDirectory((laikaSite / target).value)
      .describe
      .unsafeRunSync()
      .copy(renderer = "Depending on task")
      .formatted
  }
  
  val parser: Initialize[TreeParser[IO]] = setting {
    val fallback = ConfigBuilder.empty
      .withValue(LaikaKeys.metadata.child("title"), name.value)
      .withValue(LaikaKeys.metadata.child("description"), description.value)
      .withValue(LaikaKeys.metadata.child("version"), version.value)
      .build
    val userConfig = laikaConfig.value

    def createParser (format: MarkupFormat): ParserBuilder = {
      val parser = MarkupParser.of(format)
      val mergedConfig = parser.config.copy(
        bundleFilter = userConfig.bundleFilter,
        failOnMessages = userConfig.failOnMessages,
        renderMessages = userConfig.renderMessages,
        configBuilder = userConfig.configBuilder.withFallback(fallback)
      )
      parser.withConfig(mergedConfig).using(laikaExtensions.value: _*)
    }

    createParser(Markdown)
      .io(blocker)
      .parallel[IO]
      .withAlternativeParser(createParser(ReStructuredText))
      .build
  }


  /** Builds the artifact name based on its descriptor.
    * 
    * The default implementation provides a name in the form of `<project-name>-<version>-<classifier>.<suffix>`.
    * Classifier may be empty in which case the preceding dash will also be omitted.
    * The version will by default only contain major and minor digits,
    * assuming that it is rare to publish separate documentation for bugfix releases.
    */
  def createArtifactName(desc: ArtifactDescriptor): String = {
    val classifier = desc.classifier.fold("")("-"+_)
    val version = desc.version.split('.').take(2).mkString(".")
    desc.name + "-" + version + classifier + "." + desc.suffix
  }

  /** The set of targets for the transformation tasks of all supported output formats.
    */
  val allTargets = setting {
    Set(
      (laikaSite / target).value, 
      (laikaXSLFO / target).value, 
      (laikaAST / target).value
    )
  }

}
