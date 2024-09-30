package cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import worker.SyncWorker
import worker.WorkerConfig
import worker.WorkerModule

@Module(includes = [WorkerModule::class])
interface CliModule {
  companion object {
    @Provides
    fun logger(severity: Severity): Logger = Logger(
      config = loggerConfigInit(
        platformLogWriter(ColourLogFormatter()),
        minSeverity = severity,
      ),
      tag = "Global",
    )
  }
}

@Component(modules = [CliModule::class])
interface CliComponent {
  fun worker(): SyncWorker

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun config(config: WorkerConfig): Builder

    @BindsInstance
    fun logSeverity(severity: Severity): Builder

    fun build(): CliComponent
  }
}
