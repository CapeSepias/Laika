package laika.parse.rst

import laika.parse.InlineParsers
import laika.tree.Elements._

trait ExplicitBlockParsers extends BlockBaseParsers { self: InlineParsers => // TODO - probably needs to be rst.InlineParsers 

  
  // TODO - might be needed in some base trait
  def simpleReferenceName = {
    val alphanum = anyIn('0' to '9', 'a' to 'z', 'A' to 'Z') min 1 // TODO - check whether non-ascii characters are allowed
    val symbol = anyOf('-', '_', '.', ':', '+') take 1
    
    alphanum ~ ((symbol ~ alphanum)*) ^^ { 
      case start ~ rest => start + (rest map { case a~b => a+b }).mkString
    } // TODO - normalize ws - lowercase
  }
  
  def refName = simpleReferenceName | phraseRef
  
  def phraseRef = {
    '`' ~> (any min 1) <~ '`' // TODO - implement
  }
  
  
  /** TODO - move to base trait - duplicated from ListParsers
   *  nestedBlock parser in BlockParsers not used very often
   */
  def nestedBlocks (pos: BlockPosition) = 
    if (pos.nestLevel < maxNestLevel) (standardRstBlock(pos) | paragraph) *
    else (nonRecursiveRstBlock | paragraph) *
  
  
  def explicitStart = (".." ~ (ws min 1)) ^^ { case _ ~ ws => ws.length + 2 }
  
  
  def explicitBlockItems (pos: BlockPosition) = explicitStart >> { len => // TODO - length not needed when tab processing gets changed
    footnote(pos, len) | citation(pos, len) | linkDefinition(pos, len) // TODO - there is a linkDef alternative not requiring the .. prefix
  }
  
  
  def footnote (pos: BlockPosition, prefixLength: Int) = {
    val decimal = (anyIn('0' to '9') min 1) ^^ { n => NumericLabel(n.toInt) }
    val autonumber = '#' ^^^ Autonumber 
    val autosymbol = '*' ^^^ Autosymbol
    val autonumberLabel = '#' ~> simpleReferenceName ^^ AutonumberLabel 
    
    val label = decimal | autonumberLabel | autonumber | autosymbol
    
    val prefix = '[' ~> label <~ ']'
    
    def labelLength (label: FootnoteLabel) = { // TODO - not needed when tab processing gets changed
      val len = label match {
        case Autonumber | Autosymbol => 3
        case AutonumberLabel(label) => label.length + 2
        case NumericLabel(number: Int) => number.toString.length + 2
      }
      len + prefixLength
    }
    
    guard(prefix) >> { label => // TODO - parsing prefix twice is inefficient, indentedBlock parser should return result
      indentedBlock(prefix ^^ labelLength, pos) ^^ {
        case (lines,pos) => Footnote(label, parseMarkup(nestedBlocks(pos), lines mkString "\n"))
      }
    }
  }
  
  def citation (pos: BlockPosition, prefixLength: Int) = {
    val prefix = '[' ~> simpleReferenceName <~ ']'
    
    guard(prefix) >> { label => // TODO - parsing prefix twice is inefficient, indentedBlock parser should return result
      indentedBlock(prefix ^^ { _.length + prefixLength }, pos) ^^ {
        case (lines,pos) => Citation(label, parseMarkup(nestedBlocks(pos), lines mkString "\n"))
      }
    }
  }
  
  def linkDefinition (pos: BlockPosition, prefixLength: Int) = {
    val named = '_' ~> refName <~ ':' // TODO - backticks are optional here in most cases (unless ref name contains colons)
    val anonymous = "__:"
      
    val internal = named ^^ InternalLinkTarget // TODO - might need special logic for cases where it points to other targets (e.g. external)
    
    val externalName = (named | anonymous)
    
    val external = guard(externalName) >> { name =>
      indentedBlock(externalName ^^ { _.length + prefixLength }, pos) ^^ { // TODO - indentedBlock parser still requires ws after prefix
        case (lines,pos) => LinkDefinition(name, lines map (_.trim) filter (_.isEmpty) mkString)
      }
    }
    
    external | internal
    
    // TODO - add indirect targets once inline parsers are completed
  }
  
  
}