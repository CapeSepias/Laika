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

package laika.helium

import laika.parse.text.CharGroup

/**
  * @author Jens Halm
  */
sealed abstract class Color (val displayValue: String) {
  def validate: Option[String]
}

object Color {
  
  def rgb (red: Int, green: Int, blue: Int): Color = rgba(red, green, blue, 1)
  def rgba (red: Int, green: Int, blue: Int, alpha: Float): Color = {
    val display = if (alpha == 1) s"rgb($red,$green,$blue)" else  s"rgba($red,$green,$blue,$alpha)"
    new Color (display) {
      def validate: Option[String] = {
        def unsignedByte (value: Int): Boolean = value >= 0 && value <= 255
        if (!unsignedByte(red) || !unsignedByte(green) || !unsignedByte(blue) || alpha < 0 || alpha > 1)
          Some(s"red, green and blue value must be between 0 and 255, alpha must be between 0.0 and 1.0")
        else None
      } 
    }
  }
  def hex (hexValue: String): Color = new Color(s"#$hexValue") {
    def validate: Option[String] = 
      if ((hexValue.length != 3 && hexValue.length != 6) || hexValue.forall(CharGroup.hexDigit.contains))
        Some("value must be 3 or 6 hexadecimal digits")
      else None
  }
  
}

case class ColorSet (primary: Color, 
                     primaryDark: Color, 
                     primaryLight: Color,  
                     secondary: Color,
                     messages: MessageColors,
                     syntaxHighlighting: SyntaxColors)

case class MessageColors (info: Color, 
                          infoLight: Color, 
                          warning: Color, 
                          warningLight: Color, 
                          error: Color, 
                          errorLight: Color)

case class SyntaxColors (base: ColorQuintet, wheel: ColorQuintet)

case class ColorQuintet (c1: Color, c2: Color, c3: Color, c4: Color, c5: Color)
