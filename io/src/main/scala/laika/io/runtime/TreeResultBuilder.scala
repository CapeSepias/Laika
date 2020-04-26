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

package laika.io.runtime

import java.io.File

import laika.config.{Config, ConfigError, ConfigParser, Origin}
import laika.ast.Path.Root
import laika.ast.{Document, DocumentTree, DocumentTreeRoot, Navigatable, Path, StyleDeclarationSet, TemplateDocument, TreeBuilder, TreeContent, UnresolvedDocument}
import cats.implicits._
import laika.config.Config.IncludeMap
import laika.config.Origin.{DocumentScope, TreeScope}

/**
  * @author Jens Halm
  */
object TreeResultBuilder {

  import laika.collection.TransitionalCollectionOps._

  sealed trait ParserResult extends Navigatable {
    def sourceFile: Option[File]
  }

  case class MarkupResult (doc: UnresolvedDocument, sourceFile: Option[File]) extends ParserResult {
    val path: Path = doc.document.path
  }
  case class TemplateResult (doc: TemplateDocument, sourceFile: Option[File])  extends ParserResult {
    val path: Path = doc.path
  }
  case class StyleResult (doc: StyleDeclarationSet, format: String, sourceFile: Option[File]) extends ParserResult {
    val path: Path = doc.paths.head
  }
  case class ConfigResult (path: Path, config: ConfigParser, sourceFile: Option[File]) extends ParserResult

  type UnresolvedContent = Either[UnresolvedDocument, TreeResult]
  
  case class TreeResult (path: Path, 
                         content: Seq[Either[UnresolvedDocument, TreeResult]], 
                         titleDoc: Option[UnresolvedDocument],
                         templates: Seq[TemplateDocument],
                         configs: Seq[ConfigResult]) extends ParserResult {
    val sourceFile: Option[File] = None
  }

  def buildNode (path: Path, content: Seq[ParserResult]): TreeResult = {
    
    val subTrees = content.collect { case tree: TreeResult => Right(tree) }.sortBy(_.toOption.get.path.name)
    val treeContent = content.collect { case MarkupResult(doc,_) => Left(doc) } ++ subTrees
    val templates = content.collect { case TemplateResult(doc,_) => doc }

    val treeConfig = content.collect { case c: ConfigResult => c }

    TreeResult(path, treeContent, None, templates, treeConfig)
  }

  def resolveConfig (doc: UnresolvedDocument, baseConfig: Config, includes: IncludeMap): Either[ConfigError, Document] =
    doc.config.resolve(Origin(DocumentScope, doc.document.path), baseConfig, includes).map(config => doc.document.copy(config = config))
  
  def resolveConfig (result: TreeResult, baseConfig: Config, includes: IncludeMap, titleDocName: Option[String] = None): Either[ConfigError, DocumentTree] = {

    val resolvedConfig = result.configs.foldLeft[Either[ConfigError, Config]](Right(baseConfig)) {
      case (acc, unresolved) => acc.flatMap(base => unresolved.config.resolve(Origin(TreeScope, unresolved.path), base, includes))
    }
    
    resolvedConfig.flatMap { treeConfig =>
      val titleName = titleDocName.getOrElse(treeConfig.getOpt[String]("titleDocuments.inputName").toOption.flatten.getOrElse("title"))
      def isTitleDoc (doc: TreeContent): Boolean = doc.path.basename == titleName
      val resolvedContent = result.content.toVector.traverse(
        _.fold(resolveConfig(_, treeConfig, includes), resolveConfig(_, treeConfig, includes, Some(titleName)))
      )
      
      for {
        content <- resolvedContent
        title   = content.collectFirst { case d: Document if isTitleDoc(d) => d }
      } yield DocumentTree(result.path, content.filterNot(isTitleDoc), title, result.templates, treeConfig)
    }
  }

  def buildTree (results: Seq[ParserResult], baseConfig: Config, includes: IncludeMap): Either[ConfigError, DocumentTreeRoot] = {
    
    val coverDoc = results.collectFirst {
      case MarkupResult(doc,_) if doc.document.path.parent == Root && doc.document.path.basename == "cover" => doc
    }
    val tree = TreeBuilder.build(results.filterNot(res => coverDoc.exists(_.document.path == res.path)), buildNode)

    val styles = results
      .collect { case StyleResult(styleSet, format, _) => (format, styleSet) }
      .groupBy(_._1)
      .mapValuesStrict(_.map(_._2).reduce(_ ++ _))
      .withDefaultValue(StyleDeclarationSet.empty)
    
    for {
      resolvedTree  <- resolveConfig(tree, baseConfig, includes)
      resolvedCover <- coverDoc.map(resolveConfig(_, resolvedTree.config, includes)).sequence: Either[ConfigError, Option[Document]]
    } yield
      DocumentTreeRoot(resolvedTree, resolvedCover, styles, includes = includes)
  }
  
}
