package cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import config.ClubComponent
import core.di.Sink
import core.di.Source
import core.util.Duration
import kotlin.reflect.KClass
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import utils.Named
import volleyzone.source.VolleyZoneSourceConfig

@MergeComponent.CreateComponent
expect fun KClass<DiApp>.build(
  volleyZoneConfig: VolleyZoneSourceConfig,
  sourceOffset: Duration = Duration.ZERO,
  sinkOffset: Duration = Duration.ZERO,
  severity: Severity = Severity.Warn,
  gitHubCi: Boolean = false,
  json: Json = Json,
  dry: Boolean = false,
): DiApp

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class DiApp(
  @get:Provides @get:SingleIn(AppScope::class) val volleyZoneConfig: VolleyZoneSourceConfig,
  @get:Provides @Source val sourceOffset: Duration,
  @get:Provides @Sink val sinkOffset: Duration,
  @get:Provides val severity: Severity,
  @get:Provides @get:Named("gitHubCi") val gitHubCi: Boolean,
  @get:Provides @get:SingleIn(AppScope::class) val json: Json,
  @get:Provides @get:Named("dry") val dry: Boolean,
) : ClubComponent.Factory {
  abstract val logger: Logger
}
