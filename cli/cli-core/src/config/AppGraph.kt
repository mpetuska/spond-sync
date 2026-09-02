package dev.petuska.spond.sync.cli.config

import dev.petuska.spond.sync.runtime.SpondSyncRunner
import dev.petuska.spond.sync.runtime.config.Config
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import kotlin.time.Duration
import kotlinx.serialization.json.Json

@DependencyGraph(AppScope::class)
interface AppGraph {
  val syncRunner: SpondSyncRunner

  @DependencyGraph.Factory
  interface Factory {
    fun create(
      @Provides config: Config,
      @Provides @Named("source") sourceOffset: Duration = Duration.ZERO,
      @Provides @Named("sink") sinkOffset: Duration = Duration.ZERO,
      @Provides json: Json = Json,
      @Provides @Named("dry") dry: Boolean = false,
    ): AppGraph
  }
}
