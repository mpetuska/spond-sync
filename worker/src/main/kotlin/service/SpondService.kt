package worker.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import spond.Spond
import spond.data.group.Group
import spond.data.group.GroupId
import javax.inject.Inject

class SpondService @Inject constructor(
  private val client: Spond,
  baseLogger: Logger,
) {
  private val log = baseLogger.withTag("SpondService")

  /**
   * Finds a group by name.
   */
  suspend fun findGroup(name: String): Group? {
    val group = client.listGroups()
      .onEach { log.d { "Found spond group ${it.identity}" } }
      .firstOrNull { it.name == name }
    return group
  }

  /**
   * Cancels all group events.
   */
  suspend fun cancelAllEvents(group: Group) {
    client.listEvents(groupId = group.id, includeScheduled = true).collect {
      log.i { "Canceling spond event ${it.identity}" }
      client.cancelEvent(it.id, quiet = true)
    }
  }
}
