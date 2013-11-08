package laika.tree.helper

import laika.io.Input
import scala.collection.mutable.ListBuffer
import laika.tree.Documents._
import scala.io.Codec
import laika.io.InputProvider
import laika.io.InputProvider.ProviderBuilder

trait InputBuilder {

  
  def contents: String => String
  
  private def parseTree (dirStructure: List[String], path: Path = Root, indent: Int = 0): (TestProviderBuilder, List[String]) = {
    val prefix = indent * 2
    val lines = dirStructure.takeWhile(_.startsWith(" " * prefix))
    
    def parseLines (lines: List[String]): (List[TestProviderBuilder], List[(String,String)], List[String]) = lines match {
      case Nil | "" :: _ => (Nil, Nil, Nil)
      case line :: rest => line.drop(prefix).take(2) match {
        case "+ " => {
          val name = line.drop(prefix).drop(2)
          val (dir, rest2) = parseTree(rest, path / name, indent + 1)
          val (dirs, files, rest3) = parseLines(rest2)
          (dir :: dirs, files, rest3)
        }
        case "- " => {
          val (dirs, files, rest2) = parseLines(rest)
          val n = line.drop(prefix).drop(2).split(":")
          (dirs, (n(0),n(1)) :: files, rest2)
        }
      }
    }
    
    val (dirs, files, _) = parseLines(lines)
    (new TestProviderBuilder(dirs, files, path), dirStructure.dropWhile(_.startsWith(" " * prefix)))
  }
  
  def parseTreeStructure (source: String): ProviderBuilder = parseTree(source.split("\n").toList)._1
  
  
  def createProvider (dirs: List[ProviderBuilder], files: List[(String,String)], path: Path, docTypeMatcher: Path => DocumentType): InputProvider = {
    
    def getInput (inputName: String, contentId: String, path: Path) = {
      val content = contents(contentId).replace("@name", inputName)
      Input.fromString(content, path / inputName)
    }
    
    def docType (name: String) = docTypeMatcher(path / name)

    val fileMap = files map (f => (docType(f._1), getInput(f._1, f._2, path))) groupBy (_._1)
    
    def documents (docType: DocumentType) = fileMap.get(docType).map(_.map(_._2)).getOrElse(Nil)
    
    val subtrees = dirs map (_.build(docTypeMatcher,null)) filter (d => docType(d.path.name) != Ignored)
    
    TestInputProvider(path, documents(Config), documents(Markup), documents(Dynamic), documents(Static), documents(Template), subtrees)
    
  }
  
  case class TestInputProvider (path: Path,
    configDocuments: Seq[Input],
    markupDocuments: Seq[Input],
    dynamicDocuments: Seq[Input],
    staticDocuments: Seq[Input],
    templates: Seq[Input],
    subtrees: Seq[InputProvider]
  ) extends InputProvider
  
  private[InputBuilder] class TestProviderBuilder (dirs: List[TestProviderBuilder], files: List[(String,String)], val path: Path) extends ProviderBuilder {
    def build (docTypeMatcher: Path => DocumentType, codec: Codec) = 
      createProvider(dirs, files, path, docTypeMatcher)
  }
  
  
}