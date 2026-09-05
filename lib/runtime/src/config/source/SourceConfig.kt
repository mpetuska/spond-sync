package dev.petuska.spond.sync.runtime.config.source

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SourceConfig {
  /**
   * URL to hero png to use for events created from this source.
   */
  val hero: Url?

  @Serializable
  @SerialName("BVA")
  data class BVA(val url: Url, override val hero: Url? = null) : SourceConfig

  @Serializable
  @SerialName("NVL")
  data class NVL(val url: Url, override val hero: Url? = null) : SourceConfig
}
