package cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import config.ClubGraph
import core.di.Sink
import core.di.Source
import core.util.Duration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlinx.serialization.json.Json
import utils.Named
import volleyzone.source.VolleyZoneSourceConfig

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
