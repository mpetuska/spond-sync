package dev.petuska.spond.sync.runtime.config.source

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SourceConfig {
  @Serializable @SerialName("BVA") data class BVA(val url: Url) : SourceConfig

  @Serializable  @SerialName("NVL")  data class NVL(val url: Url) : SourceConfig
}
