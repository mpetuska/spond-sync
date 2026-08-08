package dev.petuska.spond.sync.config

import dev.zacsweers.metro.Inject
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.io.decodeFromSource

@Inject
class ConfigLoader(private val json: Json) {
  /**
   * Loads and merges configuration data from the specified collection of file paths. Reads JSON
   * objects from each file, merges them hierarchically, and decodes them into a [Config] object.
   *
   * @param files a collection of file paths pointing to configuration files to load and merge.
   * @return the resulting [Config] object created by merging the JSON data from the given files.
   */
  fun load(files: Collection<Path>): Config {
    val mergedJson =
      files
        .asSequence()
        .map { SystemFileSystem.source(it).buffered() }
        .map { source -> source.use { json.decodeFromSource<JsonObject>(it) } }
        .reduce { acc, current -> acc.mergeWith(current) }
    return json.decodeFromJsonElement<Config>(mergedJson)
  }

  private fun JsonObject.mergeWith(overrideJson: JsonObject): JsonObject {
    val mergedMap = toMutableMap()

    for ((key, overrideValue) in overrideJson) {
      when (val baseValue = mergedMap[key]) {
        is JsonObject if overrideValue is JsonObject ->
          mergedMap[key] = baseValue.mergeWith(overrideValue)
        is JsonArray if overrideValue is JsonArray ->
          mergedMap[key] = JsonArray(baseValue + overrideValue)
        else -> mergedMap[key] = overrideValue
      }
    }

    return JsonObject(mergedMap)
  }
}
