package worker.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.firstOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.Spond
import spond.data.location.Location

@Inject
@SingleIn(AppScope::class)
class LocationService(private val spond: Spond, baseLogger: Logger) {
  private val log = baseLogger.withTag("LocationService")

  suspend fun resolveSpondLocation(address: String): Location? {
    val autocomplete = spond.autocompleteLocation(address).firstOrNull()
    if (autocomplete != null) {
      log.v("Successfully autocompleted locaton for address $address to ${autocomplete.identity}")
    } else {
      log.w("Could not autocomplete location for address $address")
      return null
    }
    val location = spond.getLocation(autocomplete.id)
    log.d(
      "Resolved autocomplete location ${autocomplete.identity} to location ${location.identity}"
    )
    return location
  }
}
