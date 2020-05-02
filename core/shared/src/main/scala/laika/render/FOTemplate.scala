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

package laika.render

import laika.ast._
import laika.rewrite.ReferenceResolver.CursorKeys

/** The default template for HTML renderers.
  *
  * @author Jens Halm
  */
object FOTemplate {

  private val templateText = """<?xml version="1.0" encoding="utf-8"?>
                               |
                               |<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:fox="http://xmlgraphics.apache.org/fop/extensions">
                               |
                               |  <fo:layout-master-set>
                               |  
                               |    <fo:simple-page-master 
                               |        master-name="default"
                               |        page-height="29.7cm"
                               |        page-width="21cm"
                               |        margin-top="1cm"
                               |        margin-bottom="1cm"
                               |        margin-left="2.5cm"
                               |        margin-right="2.5cm">
                               |      <fo:region-body margin-top="2cm" margin-bottom="2cm"/>
                               |      <fo:region-before extent="3cm"/>
                               |      <fo:region-after extent="1cm"/>
                               |    </fo:simple-page-master>
                               |    
                               |  </fo:layout-master-set>
                               |
                               |  #
                               |
                               |  #
                               |
                               |  <fo:page-sequence master-reference="default">
                               |
                               |    <fo:static-content flow-name="xsl-region-before">
                               |      <fo:block border-bottom-width="1pt" border-bottom-style="solid" 
                               |          font-weight="bold" font-size="9pt" text-align="center">
                               |        <fo:retrieve-marker 
                               |            retrieve-class-name="chapter"
                               |            retrieve-position="first-including-carryover"
                               |        />
                               |      </fo:block>
                               |    </fo:static-content>
                               |    
                               |    <fo:static-content flow-name="xsl-region-after">
                               |      <fo:block height="100%" font-weight="bold" font-size="10pt" text-align="center">
                               |        <fo:page-number/>
                               |      </fo:block>
                               |    </fo:static-content>
                               |      
                               |    <fo:flow flow-name="xsl-region-body">
                               |
                               |      #
                               |
                               |    </fo:flow>
                               |    
                               |  </fo:page-sequence>
                               |  
                               |</fo:root>
                               |""".stripMargin

  case object CoverImage extends SpanResolver with TemplateSpan {

    type Self = this.type
    def withOptions (options: Options): this.type = this
    val options = NoOpt
    
    private val coverImagePath = "laika.pdf.coverImage"

    def resolve (cursor: DocumentCursor): Span = {
      cursor.config
        .get[String](coverImagePath)
        .toOption
        .fold[TemplateSpan](TemplateSpanSequence.empty) { coverPath =>
          val fo = s"""    <fox:external-document src="$coverPath"
                    |      width="21cm" height="29.7cm" content-width="21cm"/>""".stripMargin
          TemplateString(fo)
      }
    }
  }
  
  /** The default template for PDF and XSL-FO renderers.
    *
    * It can be overridden by placing a custom template document
    * with the name `default.template.fo` into the root directory
    * of the input files. Alternatively the default can also be overridden
    * for individual sub-directories with a corresponding file with the same name.
    */
  val default: TemplateRoot = {
    val templateSpans = templateText.split("#").map(TemplateString(_))
    TemplateRoot(
      templateSpans(0),
      TemplateContextReference(CursorKeys.fragment("bookmarks"), required = false),
      templateSpans(1),
      CoverImage,
      templateSpans(2),
      TemplateContextReference(CursorKeys.documentContent, required = true),
      templateSpans(3)
    )
  }
  
}
