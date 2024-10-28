package worker.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.firstOrNull
import spond.Spond
import spond.data.location.Location
import javax.inject.Inject

class LocationService @Inject constructor(
  private val spond: Spond,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("LocationService")

  suspend fun resolveSpondLocation(address: String): Location? {
    val autocomplete = spond.autocompleteLocation(address)
      .firstOrNull()
    if (autocomplete != null) {
      log.v("Successfully autocompleted locaton for address $address to ${autocomplete.identity}")
    } else {
      log.w("Could not autocomplete location for address $address")
      return null
    }
    val location = spond.getLocation(autocomplete.id)
    log.d("Resolved autocomplete location ${autocomplete.identity} to location ${location.identity}")
    return location
  }
}
