package worker

import co.touchlab.kermit.Logger
import kotlinx.datetime.*
import worker.service.SpondService
import worker.service.SyncService
import worker.service.TimeService
import worker.util.exit
import javax.inject.Inject


class SyncWorker @Inject constructor(
  private val spondService: SpondService,
  private val syncService: SyncService,
  private val timeService: TimeService,
  baseLogger: Logger,
  config: WorkerConfig,
) {
  private val log = baseLogger.withTag("SyncWorker")
  private val groupName = config.spond.group
  private val teamsSourceToSpond = config.teams

  suspend fun cleanGroup() {
    val group = spondService.findGroup(groupName)
    if (group != null) {
      log.a("Are you sure you want to clear spond group ${group.identity}? [y/n]: ")
      val yes = readln()
      if (yes != "y") {
        log.exit("User declined - aborting...")
      }
      log.i("Found spond group ${group.identity}")
      log.d { "Cancelling all spond events for ${group.identity}" }
      spondService.cancelAllEvents(group)
      log.i { "Cancelled all spond events for ${group.identity}" }
    } else {
      log.exit("Unable to find spond Group(name=$groupName)")
    }
  }

  /**
   * Syncs sportpress events to spond.
   */
  suspend fun syncGroup() {
    log.d("Looking for spond group by name $groupName")
    val group = spondService.findGroup(groupName)
    if (group != null) {
      log.i("Found spond group ${group.identity}")
    } else {
      log.exit("Unable to find spond group Group(name=$groupName)")
    }

    log.d("Determining season start date")
    val seasonStartDate = determineSeasonStart()
    log.i("Assuming season start at $seasonStartDate")

    val seasonStartInstant = seasonStartDate.toInstant(TimeZone.UTC)
    val teams = teamsSourceToSpond.mapNotNull { (team, spondTeam) ->
      val subGroup = group.subGroups.firstOrNull { subGroup -> subGroup.name == spondTeam }
      if (subGroup == null) {
        log.w("Unable to find spond subGroup $spondTeam linked to source team $team")
        null
      } else {
        team to subGroup
      }
    }.toMap()
    syncService.sync(
      seasonStartInstant,
      group,
      teams,
    )
  }

  private fun determineSeasonStart(): LocalDateTime {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    val year = if (now.month >= Month.AUGUST) {
      now.year
    } else {
      now.year - 1
    }
    val month = "${Month.SEPTEMBER.ordinal}".padStart(2, '0')
    val start = LocalDateTime.parse("$year-$month-01T00:00:00")
    return timeService.offset(start)
  }
}
