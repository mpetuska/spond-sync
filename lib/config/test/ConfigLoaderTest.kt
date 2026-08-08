package dev.petuska.spond.sync.config

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json

val ConfigLoaderTest by testSuite {
  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
    allowTrailingComma = true
    allowComments = true
  }
  val loader = ConfigLoader(json)

  test("Full config loads", TestConfig) {
    val config = loader.load(listOf(loadResource("full.json5")))

    assertEquals("Test Group", config.spond.group)
    assertEquals(6, config.volleyzone.leagues.size)
  }

  test("Partial configs merge", TestConfig) {
    val config = loader.load(listOf(loadResource("spond.json5"), loadResource("volleyzone.json5")))

    assertEquals("Test Group", config.spond.group)
    assertEquals(6, config.volleyzone.leagues.size)
  }
}

private fun loadResource(resourcePath: String) = Path("testResources/", resourcePath)
