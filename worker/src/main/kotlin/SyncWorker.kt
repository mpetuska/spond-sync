package worker

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.*
import spond.Spond
import spond.data.group.Group
import spond.data.group.GroupId
import worker.service.SpondService
import worker.service.SportpressService
import javax.inject.Inject


class SyncWorker @Inject constructor(
  private val spondService: SpondService,
  private val sportpressService: SportpressService,
  baseLogger: Logger,
  private val config: WorkerConfig,
) {
  private val log = baseLogger.withTag("SyncWorker")

  fun debug() {
    println("ALL GOOD: $config")
  }

  suspend fun cleanGroup(name: String) {
    val group = spondService.findGroup(name)
    if (group != null) {
      log.i("Found spond group ${group.identity}")
      log.d { "Cancelling all spond events for ${group.identity}" }
      spondService.cancelAllEvents(group)
      log.i { "Cancelled all spond events for ${group.identity}" }
    } else {
      exit("Unable to find spond Group(name=$name)")
    }
  }

  /**
   * Syncs sportpress events to spond.
   * 1. List spond sub-groups.
   * 2. Determine sportpress season start date.
   * 3. Find sportpress season.
   * 4. Find all sportpress teams.
   * 5. Find all sportpress events for each team.
   */
  suspend fun syncGroup(name: String, clean: Boolean = false) {
    val group = spondService.findGroup(name)
    if (group != null) {
      log.i("Found spond group ${group.identity}")
    } else {
      exit("Unable to find spond Group(name=$name)")
    }

    if (clean) {
      log.d { "Cancelling all spond events for ${group.identity}" }
      spondService.cancelAllEvents(group)
      log.i { "Cancelled all spond events for ${group.identity}" }
    }

    val seasonStartDate = determineSeasonStart(yearOffset = -1)
    log.i("Assuming season start at $seasonStartDate")

    val season = sportpressService.findActiveSeason(seasonStartDate)
    if (season != null) {
      log.i("Selected sportpress ${season.identity}")
    } else {
      exit("No active sportpress season found")
    }

    val teams = sportpressService.findTeams(season, config.team)
    if (teams.isNotEmpty()) {
      log.i("Found ${teams.size} sporkpress teams")
    } else {
      exit("No sporkpress teams found")
    }

    val events = sportpressService.findEvents(season, teams)
    if (events.isNotEmpty()) {
      log.i("Found sporkpress events for ${events.size} teams")
    } else {
      exit("No sporkpress events found")
    }
  }

  private fun exit(message: String): Nothing {
    throw IllegalStateException(message)
  }

  private fun determineSeasonStart(yearOffset: Int = 0): LocalDateTime {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    val year = if (now.month >= Month.AUGUST) {
      now.year
    } else {
      now.year - 1
    } + yearOffset
    val month = "${Month.SEPTEMBER.ordinal}".padStart(2, '0')
    val start = LocalDateTime.parse("$year-$month-01T00:00:00")
    return start
  }
}
