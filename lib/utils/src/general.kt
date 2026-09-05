package dev.petuska.spond.sync.utils

import kotlin.random.Random

/** Generates a random color in hex format (e.g., #RRGGBB). */
fun randomColourHex(): String {
  val red = Random.nextInt(256).toString(16).padStart(2, '0')
  val green = Random.nextInt(256).toString(16).padStart(2, '0')
  val blue = Random.nextInt(256).toString(16).padStart(2, '0')
  return "#${red}${green}${blue}"
}
