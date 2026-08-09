package dev.petuska.spond.sync.core

import dev.petuska.spond.sync.core.model.Match
import dev.petuska.spond.sync.core.model.Time
import kotlinx.coroutines.flow.Flow

fun interface DataSource {
  fun listMatches(from: Time, until: Time): Flow<Match>
}
