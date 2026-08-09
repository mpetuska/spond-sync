package dev.petuska.spond.sync.config

import dev.petuska.spond.sync.cli.SyncWorker
import dev.petuska.spond.sync.core.di.ClubScope
import dev.petuska.spond.sync.spond.sink.SpondSinkConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@GraphExtension(ClubScope::class)
interface ClubGraph {
  val syncWorker: SyncWorker

  @ContributesTo(AppScope::class)
  @GraphExtension.Factory
  interface Factory {
    fun club(@Provides config: SpondSinkConfig): ClubGraph
  }
}
