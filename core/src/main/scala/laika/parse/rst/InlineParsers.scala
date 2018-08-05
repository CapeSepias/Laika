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

package laika.parse.rst

import laika.api.ext.{SpanParser, SpanParserBuilder}
import laika.parse.core._
import laika.parse.core.markup.RecursiveSpanParsers
import laika.parse.core.text.TextParsers._
import laika.parse.core.text.{DelimitedText, DelimiterOptions}
import laika.parse.rst.BaseParsers._
import laika.parse.rst.Elements.{InterpretedText, ReferenceName, SubstitutionReference}
import laika.parse.util.URIParsers
import laika.ast._
import laika.util.~


/** Provides all inline parsers for reStructuredText.
 *  
 *  Inline parsers deal with markup within a block of text, such as a
 *  link or emphasized text. They are used in the second phase of parsing,
 *  after the block parsers have cut the document into a (potentially nested)
 *  block structure.
 * 
 *  @author Jens Halm
 */
object InlineParsers {


  /** Parses an escaped character. For most characters it produces the character
    *  itself as the result with the only exception being an escaped space character
    *  which is removed from the output in reStructuredText.
    *
    *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#escaping-mechanism]].
    */
  val escapedChar: Parser[String] = (" " ^^^ "") | (any take 1)


  private val pairs: Map[Char, Set[Char]] = List(/* Ps/Pe pairs */
                  '('->')', '['->']', '{'->'}', '<'->'>', '"'->'"', '\''->'\'', 
                  '\u0f3a'->'\u0f3b', '\u0f3c'->'\u0f3d', '\u169b'->'\u169c', '\u2045'->'\u2046',
                  '\u207d'->'\u207e', '\u208d'->'\u208e', '\u2329'->'\u232a', '\u2768'->'\u2769', '\u276a'->'\u276b',
                  '\u276c'->'\u276d', '\u276e'->'\u276f', '\u2770'->'\u2771', '\u2772'->'\u2773', '\u2774'->'\u2775',
                  '\u27c5'->'\u27c6', '\u27e6'->'\u27e7', '\u27e8'->'\u27e9', '\u27ea'->'\u27eb', '\u27ec'->'\u27ed',
                  '\u27ee'->'\u27ef', '\u2983'->'\u2984', '\u2985'->'\u2986', '\u2987'->'\u2988', '\u2989'->'\u298a',
                  '\u298b'->'\u298c', '\u298d'->'\u298e', '\u298f'->'\u2990', '\u2991'->'\u2992', '\u2993'->'\u2994',
                  '\u2995'->'\u2996', '\u2997'->'\u2998', '\u29d8'->'\u29d9', '\u29da'->'\u29db', '\u29fc'->'\u29fd',
                  '\u2e22'->'\u2e23', '\u2e24'->'\u2e25', '\u2e26'->'\u2e27', '\u2e28'->'\u2e29', '\u3008'->'\u3009',
                  '\u300a'->'\u300b', '\u300c'->'\u300d', '\u300e'->'\u300f', '\u3010'->'\u3011', '\u3014'->'\u3015',
                  '\u3016'->'\u3017', '\u3018'->'\u3019', '\u301a'->'\u301b', '\u301d'->'\u301e', '\ufd3e'->'\ufd3f',
                  '\ufe17'->'\ufe18', '\ufe35'->'\ufe36', '\ufe37'->'\ufe38', '\ufe39'->'\ufe3a', '\ufe3b'->'\ufe3c',
                  '\ufe3d'->'\ufe3e', '\ufe3f'->'\ufe40', '\ufe41'->'\ufe42', '\ufe43'->'\ufe44', '\ufe47'->'\ufe48',
                  '\ufe59'->'\ufe5a', '\ufe5b'->'\ufe5c', '\ufe5d'->'\ufe5e', '\uff08'->'\uff09', '\uff3b'->'\uff3d',
                  '\uff5b'->'\uff5d', '\uff5f'->'\uff60', '\uff62'->'\uff63', 
                  /* Pi/Pf pairs */
                  '\u00ab'->'\u00bb', '\u2018'->'\u2019', '\u201c'->'\u201d', '\u2039'->'\u203a', '\u2e02'->'\u2e03',
                  '\u2e04'->'\u2e05', '\u2e09'->'\u2e0a', '\u2e0c'->'\u2e0d', '\u2e1c'->'\u2e1d', '\u2e20'->'\u2e21',
                  /* Pi/Pf pairs reverse */
                  '\u00bb'->'\u00ab', '\u2019'->'\u2018', '\u201d'->'\u201c', '\u203a'->'\u2039', '\u2e03'->'\u2e02',
                  '\u2e05'->'\u2e04', '\u2e0a'->'\u2e09', '\u2e0d'->'\u2e0c', '\u2e1d'->'\u2e1c', '\u2e21'->'\u2e20',
                  /* pairs added explicitly in the reStructuredText ref impl */
                  '\u301d'->'\u301f', '\u201a'->'\u201b', '\u201e'->'\u201f', '\u201b'->'\u201a', '\u201f'->'\u201e',
                  /* additional pairing of open/close quotes for different typographic conventions in different languages */
                  '\u00bb'->'\u00bb', '\u2018'->'\u201a', '\u2019'->'\u2019', '\u201a'->'\u2018', '\u201a'->'\u2019',
                  '\u201c'->'\u201e', '\u201e'->'\u201c', '\u201e'->'\u201d', '\u201d'->'\u201d', '\u203a'->'\u203a')
                  .groupBy(_._1).mapValues(_.map(_._2).toSet)

                  
  private val startChars = anyOf(' ','-',':','/','\'','"','<','(','[','{','\n') take 1
  
  private val startCategories = Set[Int](Character.DASH_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.START_PUNCTUATION,
                            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION)
   
  private val endChars = anyOf(' ','-','.',',',':',';','!','?','\\','/','\'','"','>',')',']','}','\u201a','\u201e') take 1
  
  private val endCategories = Set[Int](Character.DASH_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.END_PUNCTUATION,
                            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION)                          

  
  /** Parses the markup at the start of an inline element according to reStructuredText markup recognition rules.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#inline-markup-recognition-rules]].
   * 
   *  @param start the parser that recognizes the markup at the start of an inline element
   *  @param end the parser that recognizes the markup at the end of an inline element, needed to verify
   *  the start sequence is not immediately followed by an end sequence as empty elements are not allowed.
   *  @return a parser without a useful result, as it is only needed to verify it succeeds
   */                          
  def markupStart (start: Parser[Any], end: Parser[String]): Parser[Any] = {
    ((lookBehind(2, beforeStartMarkup) | lookBehind(1, atStart ^^^ ' ')) >> afterStartMarkup(start)) ~ not(end) // not(end) == rule 6
  }
  
  /** Parses the start of an inline element without specific start markup 
   *  according to reStructuredText markup recognition rules.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#inline-markup-recognition-rules]].
   * 
   *  @param end the parser that recognizes the markup at the end of an inline element, needed to verify
   *  the start sequence is not immediately followed by an end sequence as empty elements are not allowed.
   *  @return a parser without a useful result, as it is only needed to verify it succeeds
   */ 
  def markupStart (end: Parser[String]): Parser[Any] = markupStart(success(()), end)
  
  /** Parses the end of an inline element according to reStructuredText markup recognition rules.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#inline-markup-recognition-rules]].
   * 
   *  @param end the parser that recognizes the markup at the end of an inline element
   *  @return a parser that produces the same result as the parser passed as an argument
   */
  def markupEnd (end: Parser[String]): Parser[String] = {
    end >> { markup => (lookBehind(markup.length + 1, beforeEndMarkup) ~ lookAhead(eol | afterEndMarkup)) ^^^ markup }
  }

  def delimitedByMarkupEnd (end: String): DelimitedText[String] with DelimiterOptions = {
    val postCondition = lookBehind(end.length + 1, beforeEndMarkup) ~ lookAhead(eol | afterEndMarkup)
    delimitedBy(end, postCondition)
  }

  def delimitedByMarkupEnd (end: String, postCondition: Parser[Any]): DelimitedText[String] with DelimiterOptions = {
    val combinedPostCondition = lookBehind(end.length + 1, beforeEndMarkup) ~ lookAhead(eol | afterEndMarkup) ~ postCondition
    delimitedBy(end, combinedPostCondition)
  }

  /** Parses the end of an inline element according to reStructuredText markup recognition rules.
    *
    *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#inline-markup-recognition-rules]].
    *
    *  @param delimLength the length of the end delimiter that has already been consumed from the input
    *  @return a parser that succeeds without consuming any input when the rst markup end conditions are met
    */
  def markupEnd (delimLength: Int): Parser[Any] = {
    lookBehind(delimLength + 1, beforeEndMarkup) ~ lookAhead(eol | afterEndMarkup)
  }
  
  /** Inline markup recognition rules 2 and 5
   */
  private def afterStartMarkup (start: Parser[Any])(before: Char): Parser[Any ~ String] = {
    val matching = pairs.getOrElse(before, Set()) 
    val excluded = (matching + ' ' + '\n').toList
    start ~ lookAhead(anyBut(excluded:_*) take 1)
  }
  
  /** Inline markup recognition rules 3
   */
  private val beforeEndMarkup: Parser[String] = anyBut(' ','\n') take 1
  
  /** Inline markup recognition rule 1
   */
  private val beforeStartMarkup: Parser[Char] = (startChars | anyWhile(char => startCategories(Character.getType(char))).take(1)) ^^ {_.charAt(0)}
  
  /** Inline markup recognition rule 4
   */
  private val afterEndMarkup: Parser[Any] = endChars | anyWhile(char => endCategories(Character.getType(char))).take(1)
  
  /** Parses a span of emphasized text.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#emphasis]]
   */
  lazy val em: SpanParserBuilder = SpanParser.forStartChar('*').recursive { implicit recParsers =>
    span(not(lookBehind(2, '*')), "*", not('*')) ^^ (Emphasized(_))
  }.withLowPrecedence
  
  /** Parses a span of text with strong emphasis.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#strong-emphasis]]
   */
  lazy val strong: SpanParserBuilder = SpanParser.forStartChar('*').recursive { implicit recParsers =>
    span('*',"**") ^^ (Strong(_))
  }


  private def span (start: Parser[Any], end: String)(implicit recParsers: RecursiveSpanParsers): Parser[List[Span]]
    = markupStart(start, end) ~> recParsers.escapedText(delimitedByMarkupEnd(end)) ^^ { text => List(Text(text)) }

  private def span (start: Parser[Any], end: String, postCondition: Parser[Any])(implicit recParsers: RecursiveSpanParsers): Parser[List[Span]]
    = markupStart(start, end) ~> recParsers.escapedText(delimitedByMarkupEnd(end, postCondition)) ^^ { text => List(Text(text)) }

  /** Parses an inline literal element.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#inline-literals]].
   */
  lazy val inlineLiteral: SpanParserBuilder = SpanParser.forStartChar('`').standalone {
    markupStart('`', "``") ~> delimitedByMarkupEnd("``") ^^ (Literal(_))
  }
  
  private def toSource (label: FootnoteLabel): String = label match {
    case Autonumber => "[#]_"
    case Autosymbol => "[*]_"
    case AutonumberLabel(label) => s"[#$label]_"
    case NumericLabel(label) => s"[$label]_"
  }
  
  /** Parses a footnote reference.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#footnote-references]].
   */
  lazy val footnoteRef: SpanParserBuilder = SpanParser.forStartChar('[').standalone {
    markupStart("]_") ~> footnoteLabel <~ markupEnd("]_") ^^ { label => FootnoteReference(label, toSource(label)) }
  }
  
  /** Parses a citation reference.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#citation-references]].
   */
  lazy val citationRef: SpanParserBuilder = SpanParser.forStartChar('[').standalone {
    markupStart("]_") ~> simpleRefName <~ markupEnd("]_") ^^ { label => CitationReference(label, s"[$label]_") }
  }
  
  /** Parses a substitution reference.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#substitution-references]].
   */
  lazy val substitutionRef: SpanParserBuilder = SpanParser.forStartChar('|').standalone {
    markupStart("|") ~> simpleRefName >> { ref =>
      markupEnd("|__") ^^ { _ => LinkReference(List(SubstitutionReference(ref)), "", s"|$ref|__") } |
      markupEnd("|_")  ^^ { _ => LinkReference(List(SubstitutionReference(ref)), ref, s"|$ref|_") } |
      markupEnd("|")   ^^ { _ => SubstitutionReference(ref) }
    }
  }
  
  /** Parses an inline internal link target.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#inline-internal-targets]]
   */
  lazy val internalTarget: SpanParserBuilder = SpanParser.forStartChar('_').recursive { recParsers =>
    markupStart('`', "`") ~>
    (recParsers.escapedText(delimitedBy('`').nonEmpty) ^^ ReferenceName) <~
    markupEnd(1) ^^ (id => Text(id.original, Id(id.normalized) + Styles("target")))
  }
  
  /** Parses an interpreted text element with the role name as a prefix.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#interpreted-text]]
   */  
  lazy val interpretedTextWithRolePrefix: SpanParserBuilder = SpanParser.forStartChar(':').recursive { recParsers =>
    (markupStart(":") ~> simpleRefName) ~ (":`" ~> recParsers.escapedText(delimitedBy('`').nonEmpty) <~ markupEnd(1)) ^^
      { case role ~ text => InterpretedText(role,text,s":$role:`$text`") }
  }
  
  /** Parses an interpreted text element with the role name as a suffix.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#interpreted-text]]
   */  
  def interpretedTextWithRoleSuffix (defaultTextRole: String): SpanParserBuilder =
    SpanParser.forStartChar('`').recursive { recParsers =>
      (markupStart("`") ~> recParsers.escapedText(delimitedBy('`').nonEmpty) <~ markupEnd(1)) ~ opt(":" ~> simpleRefName <~ markupEnd(":")) ^^
      { case text ~ role => InterpretedText(role.getOrElse(defaultTextRole), text, s"`$text`" + role.map(":"+_+":").getOrElse("")) }
    }.withLowPrecedence
  
  /** Parses a phrase link reference (enclosed in back ticks).
   *  
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#hyperlink-references]]
   */
  lazy val phraseLinkRef: SpanParserBuilder = SpanParser.forStartChar('`').recursive { recParsers =>
    def ref (refName: String, url: String) = if (refName.isEmpty) url else refName
    val url = '<' ~> delimitedBy('>') ^^ { _.replaceAll("[ \n]+", "") }
    val refName = recParsers.escapedText(delimitedBy('`','<').keepDelimiter) ^^ ReferenceName
    markupStart("`") ~> refName ~ opt(url) ~ (markupEnd("`__") ^^^ false | markupEnd("`_") ^^^ true) ^^ {
      case refName ~ Some(url) ~ true   => 
        SpanSequence(List(ExternalLink(List(Text(ref(refName.original, url))), url), ExternalLinkDefinition(ref(refName.normalized, url), url)))
      case refName ~ Some(url) ~ false  => ExternalLink(List(Text(ref(refName.original, url))), url)
      case refName ~ None ~ true        => LinkReference(List(Text(refName.original)), refName.normalized, s"`${refName.original}`_") 
      case refName ~ None ~ false       => LinkReference(List(Text(refName.original)), "", s"`${refName.original}`__") 
    }
  }
  
  /** Parses a simple link reference.
   *  
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#hyperlink-references]]
   */
  lazy val simpleLinkRef: SpanParserBuilder = SpanParser.forStartChar('_').standalone {
    markupEnd('_' ^^^ "__" | success("_")) >> {
      markup => reverse(markup.length, simpleRefName <~ reverseMarkupStart) ^^ { refName =>
        markup match {
          case "_"  => Reverse(refName.length, LinkReference(List(Text(refName)), ReferenceName(refName).normalized, s"${refName}_"), Text("_")) 
          case "__" => Reverse(refName.length, LinkReference(List(Text(refName)), "", s"${refName}__"), Text("__")) 
        }
      }
    } 
  }.withLowPrecedence
  
  private def reverse (offset: Int, p: => Parser[String]): Parser[String] = Parser { in =>
    p.parse(in.reverse.consume(offset)) match {
      case Success(result, _) => Success(result.reverse, in)
      case Failure(msg, _) => Failure(msg, in)
    }
  }
  
  private lazy val reverseMarkupStart: Parser[Any] = lookAhead(eof | beforeStartMarkup)


  private def trim (p: Parser[(String,String,String)]): Parser[Span] = p >> { res => Parser { in =>
    val startChar = Set('-',':','/','\'','(','{')
    val endChar   = Set('-',':','/','\'',')','}','.',',',';','!','?') 
    res match {
      case (start, sep, end) => 
        val startTrimmed = start.dropWhile(startChar)
        val endTrimmed = end.reverse.dropWhile(endChar).reverse
        val uri = startTrimmed + sep + endTrimmed
        val uriWithScheme = if (sep == "@" && !uri.startsWith("mailto:")) "mailto:"+uri else uri 
        val nextIn = in.consume(endTrimmed.length - end.length)
        Success(Reverse(startTrimmed.length, ExternalLink(List(Text(uri)), uriWithScheme), Text(sep+endTrimmed)), nextIn)
    }
  }}

  private val uriParsers = new URIParsers
  
  /** Parses a standalone HTTP or HTTPS hyperlink (with no surrounding markup).
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#standalone-hyperlinks]]
   */
  lazy val uri: SpanParserBuilder = SpanParser.forStartChar(':').standalone {
    trim(reverse(1, ("ptth" | "sptth") <~ reverseMarkupStart) ~
      uriParsers.httpUriNoScheme <~ lookAhead(eol | afterEndMarkup) ^^ {
      case scheme ~ rest => (scheme, ":", rest)
    })
  }.withLowPrecedence
  
  /** Parses a standalone email address (with no surrounding markup).
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#standalone-hyperlinks]]
   */
  lazy val email: SpanParserBuilder = SpanParser.forStartChar('@').standalone {
    trim(reverse(1, uriParsers.localPart <~ reverseMarkupStart) ~
      uriParsers.domain <~ lookAhead(eol | afterEndMarkup) ^? {
      case local ~ domain if local.nonEmpty && domain.nonEmpty => (local, "@", domain)
    })
  }.withLowPrecedence


}
