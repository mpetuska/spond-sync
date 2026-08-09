package dev.petuska.spond.sync.config

import dev.petuska.spond.sync.spond.sink.SpondSinkConfig
import dev.petuska.spond.sync.volleyzone.source.VolleyZoneSourceConfig
import kotlinx.serialization.Serializable

@Serializable data class Config(val volleyzone: VolleyZoneSourceConfig, val spond: SpondSinkConfig)
