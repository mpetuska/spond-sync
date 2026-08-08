package volleyzone.source

import io.ktor.http.Url
import kotlinx.serialization.Serializable

/**
 * @property leagues a map of named urls to VolleyZone leagues to scrape for events.
 * @property addresses an optional mapping between VolleyZone event venue address and full address.
 */
@Serializable
data class VolleyZoneSourceConfig(
  val leagues: Map<String, Url>,
  val addresses: Map<String, String> = mapOf(),
)
