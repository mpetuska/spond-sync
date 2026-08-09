package dev.petuska.spond.sync.spond.sink.service

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.spond.Spond
import dev.petuska.spond.sync.spond.data.location.Location
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.firstOrNull

@Inject
@SingleIn(ClubScope::class)
class LocationService(private val spond: Spond, baseLogger: Logger) {
  private val log = baseLogger.withTag("LocationService")

  suspend fun resolveSpondLocation(address: String): Location? {
    val autocomplete = spond.autocompleteLocation(address).firstOrNull()
    if (autocomplete != null) {
      log.d("[$address] Successfully autocompleted location to ${autocomplete.identity}.")
    } else {
      log.w("[$address] Could not autocomplete location.")
      return null
    }
    val location = spond.getLocation(autocomplete.id)
    log.d(
      "[$address] Resolved autocomplete location ${autocomplete.identity} to location ${location.identity}."
    )
    return location
  }
}
