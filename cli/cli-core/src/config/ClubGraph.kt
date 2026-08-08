package config

import cli.SyncWorker
import core.di.ClubScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import spond.sink.SpondSinkConfig

@GraphExtension(ClubScope::class)
interface ClubGraph {
  val syncWorker: SyncWorker

  @ContributesTo(AppScope::class)
  @GraphExtension.Factory
  interface Factory {
    fun club(@Provides config: SpondSinkConfig): ClubGraph
  }
}
