package dev.petuska.spond.sync.runtime

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.runtime.config.Config
import dev.petuska.spond.sync.runtime.config.sink.SpondSinkConfig
import dev.petuska.spond.sync.runtime.config.source.SourceConfig
import dev.petuska.spond.sync.runtime.model.Match
import dev.petuska.spond.sync.runtime.model.Time
import dev.petuska.spond.sync.runtime.sink.spond.MatchesSpondSink
import dev.petuska.spond.sync.runtime.sink.spond.TrianglesSpondSink
import dev.petuska.spond.sync.runtime.source.volleyzone.BvaDataSource
import dev.petuska.spond.sync.runtime.source.volleyzone.NvlDataSource
import dev.petuska.spond.sync.runtime.util.TimeSource
import dev.petuska.spond.sync.spond.Spond
import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Inject
class SpondSyncRunner(
  private val config: Config,
  private val spond: Spond,
  private val timeSource: TimeSource,
  private val bvaSource: BvaDataSource,
  private val nvlSource: NvlDataSource,
  private val matchesSink: MatchesSpondSink.Factory,
  private val trianglesSink: TrianglesSpondSink.Factory,
) {
  private val log = Logger.withTag("SpondSyncRunner")

  suspend fun syncGroup(
    from: Time = timeSource.fromRuntime(determineSeasonStart().toInstant(TimeZone.UTC)),
    until: Time = from + 365.days,
  ) {
    for ((subGroupName, subGroupConfig) in config.spond.subGroups) {
      val sources = subGroupConfig.sources.associateWith { config.sources.getValue(it) }
      log.i("Fetching matches for $subGroupName from ${sources.size} sources.")
      val matches =
        sources
          .flatMap { (sourceId, source) ->
            when (source) {
              is SourceConfig.BVA -> bvaSource.listMatches(sourceId, source, from, until)
              is SourceConfig.NVL -> nvlSource.listMatches(sourceId, source, from, until)
            }
          }
          .distinctBy(Match::identity)
      log.i("Found ${matches.size} matches for $subGroupName from ${sources.size} sources.")

      when (subGroupConfig.events.mode) {
        SpondSinkConfig.Events.Mode.Matches ->
          matchesSink.create(subGroupName, subGroupConfig, from, until).syncMatches(matches)
        SpondSinkConfig.Events.Mode.Triangles ->
          trianglesSink.create(subGroupName, subGroupConfig, from, until).syncMatches(matches)
      }
    }
  }

  suspend fun cleanGroup(
    from: Time = timeSource.fromSink(determineSeasonStart().toInstant(TimeZone.UTC)),
    until: Time = from + 365.days,
  ) {
    for ((subGroupName, subGroupConfig) in config.spond.subGroups) {
      log.w("[$subGroupName] Cancelling all spond events.")
      when (subGroupConfig.events.mode) {
        SpondSinkConfig.Events.Mode.Matches ->
          matchesSink.create(subGroupName, subGroupConfig, from, until).cancelAllMatches()
        SpondSinkConfig.Events.Mode.Triangles ->
          trianglesSink.create(subGroupName, subGroupConfig, from, until).cancelAllTriangles()
      }
      log.i("[$subGroupName] Cancelled all spond events.")
    }
  }

  private fun determineSeasonStart(): LocalDateTime {
    val now = timeSource.now().atSink.toLocalDateTime(TimeZone.UTC)
    val year =
      if (now.month >= Month.AUGUST) {
        now.year
      } else {
        now.year - 1
      }
    val month = "${Month.SEPTEMBER.ordinal}".padStart(2, '0')
    val start = LocalDateTime.parse("$year-$month-01T00:00:00")
    log.i("Assuming season start at $start")
    return start
  }
}
