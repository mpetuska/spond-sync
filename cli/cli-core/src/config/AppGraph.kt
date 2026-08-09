package dev.petuska.spond.sync.cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import dev.petuska.spond.sync.config.ClubGraph
import dev.petuska.spond.sync.core.di.Sink
import dev.petuska.spond.sync.core.di.Source
import dev.petuska.spond.sync.utils.Named
import dev.petuska.spond.sync.volleyzone.source.VolleyZoneSourceConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlin.time.Duration
import kotlinx.serialization.json.Json

@DependencyGraph(AppScope::class)
expect interface AppGraph : ClubGraph.Factory {
  val logger: Logger

  @DependencyGraph.Factory
  interface Factory {
    fun create(
      @Provides volleyZoneConfig: VolleyZoneSourceConfig,
      @Provides @Source sourceOffset: Duration = Duration.ZERO,
      @Provides @Sink sinkOffset: Duration = Duration.ZERO,
      @Provides severity: Severity = Severity.Warn,
      @Provides @Named("gitHubCi") gitHubCi: Boolean = false,
      @Provides json: Json = Json,
      @Provides @Named("dry") dry: Boolean = false,
    ): AppGraph
  }
}
