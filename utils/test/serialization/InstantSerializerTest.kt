package utils.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/** @see InstantSerializer */
class InstantSerializerTest {
  private val format = Json { ignoreUnknownKeys = true }

  @Test
  fun test() {
    val expected = Clock.System.now()
    val json = format.encodeToString(expected)
    val actual = format.decodeFromString<Instant>(json)

    assertEquals(expected, actual)
  }
}
