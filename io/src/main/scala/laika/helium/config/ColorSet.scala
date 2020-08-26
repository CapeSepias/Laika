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

package laika.helium.config

import laika.theme.Color

private[helium] case class ColorSet (primary: Color,
                                     primaryDark: Color,
                                     primaryLight: Color,
                                     secondary: Color,
                                     messages: MessageColors,
                                     syntaxHighlighting: SyntaxColors)

private[helium] case class MessageColors (info: Color,
                                          infoLight: Color,
                                          warning: Color,
                                          warningLight: Color,
                                          error: Color,
                                          errorLight: Color)

private[helium] case class SyntaxColors (base: ColorQuintet, wheel: ColorQuintet)

case class ColorQuintet (c1: Color, c2: Color, c3: Color, c4: Color, c5: Color)
