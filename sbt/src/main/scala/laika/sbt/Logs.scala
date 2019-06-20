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

package laika.sbt

import laika.ast.DocumentType.{Config, Markup, StyleSheet, Template}
import laika.ast._
import laika.io.model.TreeInput
import sbt.Logger

/** Provides detailed logs for the tasks of the sbt plugin.
  *
  * @author Jens Halm
  */
object Logs {

  def s (num: Int): String = if (num == 1) "" else "s"

  /** Create a string containing detailed information about
    * the number and types of documents found in the specified input
    * tree.
    */
  def inputs (tree: TreeInput): String = {

    // TODO - 0.12 - resurrect based on new describeF function
//    val docTypes = tree.textInputs.map(_.docType)
//    val docs = docTypes.count(_ == Markup)
//    val tmpl = docTypes.count(_ == Template) + docTypes.count(_ == Dynamic)
//    val styles = docTypes.count(_.isInstanceOf[StyleSheet])
//    val conf = docTypes.count(_ == Config)
//
//    s"Parsing $docs markup document${s(docs)}, $tmpl template${s(tmpl)}, $conf configuration${s(conf)} ..."
    ""
  }

  /** Create a string containing detailed information about
    * the number and types of documents processed for the specified
    * output tree.
    */
  def outputs (root: DocumentTreeRoot, format: String): String = {

    val render = root.allDocuments.size
    val copy = root.staticDocuments.size

    s"Rendering $render $format document${s(render)}, copying $copy static file${s(copy)} ..."
  }

  /** Logs all system messages found in the specified document tree that
    * have the given minimum log level.
    *
    * @param logger the logger to write to
    * @param tree the document tree to extract system messages from
    * @param level the minimum log level for a system message to be included in the log
    */
  def systemMessages (logger: Logger, tree: DocumentTreeRoot, level: MessageLevel): Unit = {

    def logMessage (inv: Invalid[_], path: Path): Unit = {
      val source = inv.fallback match {
        case tc: TextContainer => tc.content
        case other => other.toString
      }
      val text = s"$path: ${inv.message.content}\nsource: $source"
      inv.message.level match {
        // we do not log above warn level as the build will still succeed with invalid nodes
        case MessageLevel.Debug => logger.debug(text)
        case MessageLevel.Info => logger.info(text)
        case MessageLevel.Warning | MessageLevel.Error | MessageLevel.Fatal => logger.warn(text)
      }
    }

    def logRoot (e: ElementTraversal, path: Path): Unit = {
      val nodes = e collect {
        case i: Invalid[_] if i.message.level >= level => i
      }
      nodes foreach { logMessage(_, path) }
    }

    tree.allDocuments foreach { doc => logRoot(doc.content, doc.path) }

  }

}
