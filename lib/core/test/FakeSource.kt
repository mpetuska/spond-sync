package dev.petuska.spond.sync.core

import co.touchlab.kermit.Logger
import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.Time
import dev.petuska.spond.sync.core.model.contains
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
