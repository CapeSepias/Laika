/*
 * Copyright 2013 the original author or authors.
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

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Stack
import laika.tree.Elements._
import laika.parse.rst.Elements._
import scala.annotation.tailrec
import scala.collection.immutable.TreeSet

/** 
 *  The default rewrite rules that get applied to the raw document tree after parsing
 *  reStructuredTextMarkup. The rules are responsible for resolving internal references
 *  to link targets, footnotes, citations, substitution definitions and text roles.
 * 
 *  These rules are specific to `reStructuredText`, but some of them might get promoted
 *  to the general rules implemented in [[laika.tree.RewriteRules]] in a later release.
 *  
 *  @author Jens Halm
 */
object RewriteRules {

  
  private def invalidSpan (message: String, fallback: String) =
      InvalidSpan(SystemMessage(laika.tree.Elements.Error, message), Text(fallback))
      
  private def selectSubstitutions (document: Document) = document.select { 
      case _: SubstitutionDefinition => true 
      case _ => false 
    } map { 
      case SubstitutionDefinition(id,content,_) => (id,content) 
    } toMap
    
  private def selectTextRoles (document: Document) = document.select { 
      case _: CustomizedTextRole => true
      case _ => false 
    } map { 
      case CustomizedTextRole(id,f,_) => (id,f)                                   
    } toMap  
    
  private def selectCitations (document: Document) = document.select { 
      case _: Citation => true 
      case _ => false 
    } map { 
      case Citation(label,content,_) => label 
    } toSet 
   
    
  private class FootnoteResolver (unresolvedFootnotes: List[Element], unresolvedReferences: List[Element]) {
     
    case class Key(key: AnyRef) {
      val hashed = key.hashCode
      override def equals(any: Any) = any match {
        case Key(other) => key eq other
        case _          => false
      }
      override def hashCode = hashed
    }

    class SymbolProvider {
      private val symbols = List('*','\u2020','\u2021','\u00a7','\u00b6','#','\u2660','\u2665','\u2666','\u2663')
      private val stream = Stream.iterate((symbols,1)){ case (sym,num) => if (sym.isEmpty) (symbols,num+1) else (sym.tail,num) }.iterator
      def next = {
        val (sym,num) = stream.next
        sym.head.toString * num
      }
    }  
    
    object NumberProvider {
      private val explicitNumbers = unresolvedFootnotes.collect { case n @ FootnoteDefinition(NumericLabel(num),_,_) => num } toSet 
      private val numberIt = Stream.from(1).iterator
      private val usedNums = new scala.collection.mutable.HashSet[Int]
      private lazy val usedIt = TreeSet(usedNums.toList:_*).iterator
      @tailrec final def next: Int = {
        val candidate = numberIt.next
        if (!explicitNumbers.contains(candidate)) { usedNums += candidate; candidate }
        else next
      }
      def remove (num: Int) = usedNums -= num
      def nextUsed = if (usedIt.hasNext) Some(usedIt.next.toString) else None
    }
    
    val symbols = new SymbolProvider
    
    private val resolvedFootnotes = (unresolvedFootnotes map { 
      case f @ FootnoteDefinition(label,content,_) => 
        val (id, display) = label match {
          case Autonumber => 
            val num = NumberProvider.next.toString
            (num,num)
          case NumericLabel(num) => 
            val label = num.toString
            (label,label)
          case AutonumberLabel(label) => 
            (label,NumberProvider.next.toString)
          case Autosymbol =>
            val sym = symbols.next
            (sym,sym)
        }
        (Key(f), Footnote(id, display, content))
    })
    
    private val resolvedByIdentity = resolvedFootnotes toMap
    
    private val resolvedByLabel = (resolvedFootnotes map {
      case (_, fn @ Footnote(id, label, _, _)) => List((id -> fn),(label -> fn))
    }).flatten.toMap 
    
    val refSymbols = new SymbolProvider
    def toLink (fn: Footnote) = FootnoteLink(fn.id, fn.label)
    def numberedLabelRef (fn: Footnote) = fn match {
      case Footnote(id, label, _, _) =>
        NumberProvider.remove(label.toInt)
        FootnoteLink(id,label)
    }
    
    private val (autonumberedLabels, otherRefs) = unresolvedReferences partition {
      case FootnoteReference(AutonumberLabel(_),_,_) => true; case _ => false
    }
    
    def resolve (id: String, f: Footnote => FootnoteLink, source: String, msg: String) = {
      resolvedByLabel.get(id).map(f).getOrElse(invalidSpan(msg, source))
    }
    
    private val resolvedReferences = (autonumberedLabels map {
      case r @ FootnoteReference(AutonumberLabel(label), source, _) =>
        (Key(r), resolve(label, numberedLabelRef, source, "unresolved footnote reference: " + label))
    }).toMap ++ (otherRefs map {
      case r @ FootnoteReference(Autonumber, source, _) => 
        val num = NumberProvider.nextUsed
        (Key(r), (num map {n => resolve(n, toLink, source, "too many autonumer references")})
          .getOrElse(invalidSpan("too many autonumer references", source)))
        
      case r @ FootnoteReference(NumericLabel(num), source, _) => 
        (Key(r), resolve(num.toString, toLink, source, "unresolved footnote reference: " + num))
        
      case r @ FootnoteReference(Autosymbol, source, _) =>
        val sym = refSymbols.next
        (Key(r), resolve(sym, toLink, source, "too many autosymbol references"))
    }).toMap
    
    def resolved (footnote: FootnoteDefinition) = resolvedByIdentity(Key(footnote))
    
    def resolved (ref: FootnoteReference) = resolvedReferences(Key(ref))
  }
  
  private def selectFootnotes (document: Document) = {
    new FootnoteResolver(document.select { 
      case _: FootnoteDefinition => true 
      case _ => false 
    }, document.select { 
      case _: FootnoteReference => true 
      case _ => false 
    })
  }
  
  private case class InvalidLinkTarget (id: String, message: SystemMessage) extends LinkTarget
  
  private def selectLinkTargets (document: Document) = {
    val linkIds = Stream.from(1).iterator
    
    def getId (id: String) = if (id.isEmpty) linkIds.next else id
    
    val unresolvedLinkTargets = document.select { 
      case _: ExternalLinkDefinition => true // TODO - improve after collect method has been addded
      case _: InternalLinkTarget => true
      case _: IndirectLinkDefinition => true
      case _ => false 
    } map { 
      case lt: ExternalLinkDefinition => (getId(lt.id),lt) 
      case lt: InternalLinkTarget => (getId(lt.id),lt) 
      case lt: IndirectLinkDefinition => (getId(lt.id),lt)
    } toMap
    
    def resolveIndirectTarget (target: IndirectLinkDefinition, visited: Set[Any]): Any = {
      if (visited.contains(target.id)) 
        InvalidLinkTarget(target.id, SystemMessage(laika.tree.Elements.Error, "circular link reference: " + target.id))
      else
        unresolvedLinkTargets.get(target.ref.id) map {
          case it @ IndirectLinkDefinition(id, _,_) => resolveIndirectTarget(it, visited + target.id)
          case other => other
        } getOrElse InvalidLinkTarget(target.id, SystemMessage(laika.tree.Elements.Error, "unresolved link reference: " + target.ref.id))
    }            
                                   
    unresolvedLinkTargets map { 
      case (id, it: IndirectLinkDefinition) => (id, resolveIndirectTarget(it, Set()))
      case other => other 
    }    
  }
  
  
  /** Function providing the default rewrite rules when passed a document instance.
   */
  val defaults: Document => PartialFunction[Element, Option[Element]] = { document =>
    
    val substitutions = selectSubstitutions(document)
    
    val textRoles = selectTextRoles(document)
                                 
    val citations = selectCitations(document)             
                               
    val footnotes = selectFootnotes(document)
    
    val linkTargets = selectLinkTargets(document)
    val linkRefIds = Stream.from(1).iterator
    def getRefId (id: String) = if (id.isEmpty) linkRefIds.next else id
    
    // TODO - handle duplicate link target ids
    
    val levelMap = scala.collection.mutable.Map.empty[(Char,Boolean),Int]
    val levelIt = Stream.from(1).iterator
    
    val pf: PartialFunction[Element, Option[Element]] = {
      case SectionHeader(char, overline, content, _) => 
        Some(Header(levelMap.getOrElseUpdate((char,overline), levelIt.next), content))
        
      case SubstitutionReference(id,_) =>
        substitutions.get(id).orElse(Some(invalidSpan("unknown substitution id: " + id, "|"+id+"|")))
        
      case InterpretedText(role,text,_,_) =>
        textRoles.get(role).orElse(Some({s: String => invalidSpan("unknown text role: " + role, "`"+s+"`")})).map(_(text))
        
      case c @ CitationReference(label,source,_) =>
        Some(if (citations.contains(label)) CitationLink(label) else invalidSpan("unresolved citation reference: " + label, source))
        
      case f: Footnote           => Some(f) // TODO - should not be required  
      case f: FootnoteDefinition => Some(footnotes.resolved(f))  
      case r: FootnoteReference  => Some(footnotes.resolved(r))  
        
      case ref: LinkReference   => Some(linkTargets.get(getRefId(ref.id)) match {
        case Some(ExternalLinkDefinition(id, url, title, _)) => ExternalLink(ref.content, url, title)
        case Some(InternalLinkTarget(id,_))                  => InternalLink(ref.content, "#"+id)
        case Some(InvalidLinkTarget(id, msg))                => InvalidSpan(msg, Text(ref.source))
        case None =>
          val msg = if (ref.id.isEmpty) "too many anonymous link references" else "unresolved link reference: " + ref.id
          InvalidSpan(SystemMessage(laika.tree.Elements.Error, msg), Text(ref.source))
      })
      
      case _: Temporary => None // TODO - replace Reference with InvalidBlock/Span?
    }
    pf
  }

  /** Applies the default rewrite rules to the specified document tree,
   *  returning a new rewritten tree instance.
   */
  def applyDefaults (doc: Document) = doc.rewrite(defaults(doc)) 
  
   
  
}