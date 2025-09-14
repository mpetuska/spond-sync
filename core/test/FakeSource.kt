package core

import co.touchlab.kermit.Logger
import core.model.Match
import core.model.Time
import core.model.contains
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSource(private val matches: Collection<Match>, logger: Logger = Logger.Companion) :
  DataSource {
  private val log = logger.withTag("FakeSource")

  override fun listMatches(from: Time, until: Time): Flow<Match> = flow {
    for (match in matches) {
      if (match in from..<until) {
        log.v("[${match.identity}] Emitting match.")
        emit(match)
      } else {
        log.d(
          "[${match.identity}] Not emitting the match since it is outside of filter range $from..<$until, matchDate=${match.start}..<${match.end}"
        )
      }
    }
  }
}
