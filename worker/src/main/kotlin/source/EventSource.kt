package worker.source

import kotlinx.datetime.Instant
import worker.data.SourceEvent

interface EventSource {
  val name: String
  suspend fun provideEvents(
    club: String,
    teams: Collection<String>,
    start: Instant,
    end: Instant? = null
  ): Map<String, List<SourceEvent>>
}
