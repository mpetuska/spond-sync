package volleyzone.source.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import core.di.Source
import core.util.ColourLogFormatter
import core.util.Instant
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.reflect.KClass
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@MergeComponent.CreateComponent
expect fun KClass<TestApp>.build(
  seasonStart: Instant = Instant.parse("2024-09-01T00:00:00Z"),
  severity: Severity = Severity.Warn,
  logFormatter: MessageStringFormatter = ColourLogFormatter(),
  httpClientEngine: HttpClientEngine = MockEngine { respondError(HttpStatusCode.NotFound) },
): TestApp

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class TestApp(
  @get:Provides @get:Source val seasonStartAtSource: Instant,
  @get:Provides val severity: Severity,
  @get:Provides val logFormatter: MessageStringFormatter,
  @get:Provides val httpClientEngine: HttpClientEngine,
) : ClubComponent.Factory {
  abstract val logger: Logger
}
