package cli.config

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import dagger.*
import worker.SyncWorker
import worker.WorkerConfig
import worker.WorkerModule

@Module(includes = [WorkerModule::class])
interface CliModule {
  companion object {
    @Provides
    @Reusable
    fun logger(severity: Severity): Logger = Logger(
      config = loggerConfigInit(
        platformLogWriter(
          if (System.getenv("CI") != null && System.getenv("GITHUB_RUN_ATTEMPT") != null) {
            println("Using GHAFormatter")
            GHAFormatter()
          } else {
            ColourLogFormatter()
          }
        ),
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
