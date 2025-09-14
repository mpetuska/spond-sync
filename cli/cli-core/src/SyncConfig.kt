package cli

import kotlinx.serialization.Serializable
import spond.sink.SpondSinkConfig
import volleyzone.source.VolleyZoneSourceConfig

@Serializable
data class SyncConfig(val volleyzone: VolleyZoneSourceConfig, val spond: SpondSinkConfig)
