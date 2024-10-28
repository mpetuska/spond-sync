package utils.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject

/**
 * Deserializes json and preserves all original data including uknown keys in `@SerialName("#json")` property.
 */
abstract class PreservingJsonSerializer<T : Any>(
  serializer: KSerializer<T>,
) : JsonTransformingSerializer<T>(serializer) {
  override fun transformDeserialize(element: JsonElement): JsonElement {
    require(element is JsonObject)
    val transformed = buildJsonObject {
      for ((key, value) in element.entries) {
        put(key, value)
      }
      put(KEY, element)
    }
    return super.transformDeserialize(transformed)
  }

  override fun transformSerialize(element: JsonElement): JsonElement {
    require(element is JsonObject)
    val raw = element.getValue(KEY)
    require(raw is JsonObject)
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
    return super.transformSerialize(transformed)
  }

  private companion object {
    const val KEY = "#json"
  }
}
