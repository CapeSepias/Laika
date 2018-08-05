import laika.ast.Emphasized
import laika.ast.Strong

name := "site-rewriteRules"

version := "0.1"

scalaVersion := "2.12.6"

enablePlugins(LaikaPlugin)

laikaExtensions += laikaRewriteRule {
  case Emphasized(content,_) => Some(Strong(content))
}
