package dev.petuska.spond.sync.runtime.config

import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig
import dev.petuska.spond.sync.runtime.config.source.SourceConfig
import dev.petuska.spond.sync.runtime.model.SourceId
import kotlinx.serialization.Serializable

@Serializable
data class Config(
  val spond: SpondSinkConfig,
  val addresses: Map<String, String> = emptyMap(),
  val sources: Map<SourceId, SourceConfig> = emptyMap(),
)
