package dev.petuska.spond.sync.config

import kotlinx.serialization.Serializable
import spond.sink.SpondSinkConfig
import volleyzone.source.VolleyZoneSourceConfig

@Serializable data class Config(val volleyzone: VolleyZoneSourceConfig, val spond: SpondSinkConfig)
