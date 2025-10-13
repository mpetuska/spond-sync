package spond.sink.service

import co.touchlab.kermit.Logger
import core.di.ClubScope
import kotlinx.coroutines.flow.firstOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.Spond
import spond.data.location.Location

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
