package cli

import co.touchlab.kermit.Logger
import core.SyncService
import core.di.ClubScope
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import spond.Spond
import spond.data.group.Group
import spond.sink.SpondSinkConfig
import utils.Named
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Inject
@SingleIn(ClubScope::class)
class SyncWorker(
  private val syncService: SyncService,
  private val spond: Spond,
  private val config: SpondSinkConfig,
  @Named("dry") private val dry: Boolean,
  logger: Logger,
) {
  val log = logger.withTag("SyncWorker")

  suspend fun syncGroup() {
    log.d("Determining season start date")
    val seasonStartDate = determineSeasonStart()
    log.i("Assuming season start at $seasonStartDate")
    val from = seasonStartDate.toInstant(TimeZone.UTC)
    syncService.syncMatches(from = from, until = from + 365.days)
  }

  suspend fun cleanGroup(implicitYes: Boolean = false) {
    val group = spond.listGroups().firstOrNull { it.name == config.group }
    if (group != null) {
      log.i("[${group.identity}] Found spond group.")
      if (!implicitYes && !dry) {
        log.a("[${group.identity}] Are you sure you want to clear spond group? [y/n]: ")
        val yes = readln()
        if (yes != "y") {
          log.a("[${group.identity}] User declined - aborting...")
          return
        }
      }
      log.v("[${group.identity}] Cancelling all spond events.")
      cancelAllEvents(group)
      log.i("[${group.identity}] Cancelled all spond events.")
    } else {
      log.a("[${config.group}] Unable to find spond Group.")
      return
    }
  }

  /** Cancels all group events. */
  private suspend fun cancelAllEvents(group: Group) {
    log.d("Determining season start date")
    val seasonStartDate = determineSeasonStart()
    log.i("Assuming season start at $seasonStartDate")
    val from = seasonStartDate.toInstant(TimeZone.UTC)
    spond
      .listEvents(
        groupId = group.id,
        includeScheduled = true,
        includeHidden = false,
        includeRepeating = false,
        minStart = from,
        maxEnd = from + 365.days,
        limit = 500u,
      )
      .filter { event ->
        val description = event.description
        event.matchInfo != null && description?.contains(config.events.descriptionByline) == true
      }
      .collect {
        log.v("[${it.identity}] Canceling event.")
        try {
          if (dry) {
            log.i("[DRY] Cancelling spond event $it")
          } else {
            spond.cancelEvent(it.id, quiet = true)
          }
        } catch (e: ClientRequestException) {
          log.w("[${it.identity}] Failed to delete event.", e)
        }
        log.i("[${it.identity}] Cancelled event.")
      }
  }

  private fun determineSeasonStart(): LocalDateTime {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    val year =
      if (now.month >= Month.AUGUST) {
        now.year
      } else {
        now.year - 1
      }
    val month = "${Month.SEPTEMBER.ordinal}".padStart(2, '0')
    val start = LocalDateTime.parse("$year-$month-01T00:00:00")
    return start
  }
}
