package utils.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class PreservingJsonSerializerTest {
  private val format = Json { ignoreUnknownKeys = true }

  @Test
  fun test() {
    // language=json
    val jsonString =
      """
        {
        "id": 69,
        "unknown": "Some unknown key"
        }
      """
        .trimIndent()
    val json = format.decodeFromString<JsonObject>(jsonString)
    val data = format.decodeFromString<TestObject>(jsonString)

    assertEquals(format.encodeToString(data), format.encodeToString(json))
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable(TestObject.Serializer::class)
  @KeepGeneratedSerializer
  data class TestObject(val id: Int, @SerialName("#json") val json: JsonObject) {
    object Serializer : PreservingJsonSerializer<TestObject>(generatedSerializer())
  }
}
