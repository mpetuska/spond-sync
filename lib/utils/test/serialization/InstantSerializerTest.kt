package dev.petuska.spond.sync.utils.serialization

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/** @see InstantSerializer */
val InstantSerializerTest by testSuite {
  val format = Json { ignoreUnknownKeys = true }

  test("encodes and decodes") {
    val expected = Clock.System.now()
    val json = format.encodeToString(expected)
    val actual = format.decodeFromString<Instant>(json)

    assertEquals(expected, actual)
  }
}
