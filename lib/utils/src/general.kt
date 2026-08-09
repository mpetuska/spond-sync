package dev.petuska.spond.sync.utils

import dev.zacsweers.metro.Qualifier
import kotlin.random.Random

fun randomColourHex(): String {
  val red = Random.nextInt(256)
  val green = Random.nextInt(256)
  val blue = Random.nextInt(256)
  return "#${red.toString(16)}${green.toString(16)}${blue.toString(16)}"
}

@Qualifier
@Target(
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.TYPE,
)
annotation class Named(val value: String)
