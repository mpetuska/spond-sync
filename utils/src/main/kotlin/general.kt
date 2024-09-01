package utils

import kotlin.random.Random

fun randomColourHex(): String {
  val red = Random.nextInt(256)
  val green = Random.nextInt(256)
  val blue = Random.nextInt(256)
  return "#${red.toString(16)}${green.toString(16)}${blue.toString(16)}"
}
