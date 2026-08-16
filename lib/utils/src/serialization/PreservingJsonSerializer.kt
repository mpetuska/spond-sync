package dev.petuska.spond.sync.utils.serialization

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject

/**
 * Deserializes json and preserves all original data including uknown keys in `@SerialName("#json")`
 * property.
 */
abstract class PreservingJsonSerializer<T : Any>(serializer: KSerializer<T>) :
  JsonTransformingSerializer<T>(serializer) {
  private val log = Logger.withTag("PreservingJsonSerializer(${serializer.descriptor.serialName})")

  override fun transformDeserialize(element: JsonElement): JsonElement {
    require(element is JsonObject) { "Expected element to be a JsonObject, but was $element" }
    val transformed = buildJsonObject {
      for ((key, value) in element.entries) {
        put(key, value)
      }
      put(KEY, element)
    }
    log.v { "Deserialising $element" }
    return super.transformDeserialize(transformed)
  }

  override fun transformSerialize(element: JsonElement): JsonElement {
    require(element is JsonObject) { "Expected element to be a JsonObject, but was $element" }
    val raw = element[KEY]
    require(raw is JsonObject) { "Expected $KEY to be a JsonObject, but was $raw" }
    val transformed = buildJsonObject {
      for ((key, value) in raw) {
        put(key, value)
      }
      for ((key, value) in element.entries) {
        if (key != KEY) {
          put(key, value)
        }
      }
    }
    log.v { "Serializing $element" }
    return super.transformSerialize(transformed)
  }

  companion object {
    const val KEY = "#json"
  }
}
