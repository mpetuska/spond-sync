package utils.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PreservingJsonSerializerTest {
  private val format = Json {
    ignoreUnknownKeys = true
  }

  @Test
  fun test() {
    val jsonString = """
      {
      "id": 69,
      "unknown": "Some unknown key"
      }
    """.trimIndent()
    val json = format.decodeFromString<JsonObject>(jsonString)
    val data = format.decodeFromString<TestObject>(jsonString)

    assertEquals(format.encodeToString(data), format.encodeToString(json))
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable(TestObject.Serializer::class)
  @KeepGeneratedSerializer
  data class TestObject(
    val id: Int,
    @SerialName("#json")
    val json: JsonObject
  ) {
    object Serializer : PreservingJsonSerializer<TestObject>(generatedSerializer())
  }
}
