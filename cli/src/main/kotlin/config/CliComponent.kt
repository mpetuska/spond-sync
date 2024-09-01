package cli.config

import dagger.BindsInstance
import dagger.Component
import worker.SyncWorker
import worker.WorkerConfig
import worker.WorkerModule
import javax.inject.Named

@Component(modules = [WorkerModule::class])
interface CliComponent {
  fun worker(): SyncWorker

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun spond(config: WorkerConfig.Spond): Builder

    @BindsInstance
    fun sportpress(config: WorkerConfig.Sportpress): Builder

    @BindsInstance
    fun team(@Named("team") team: String): Builder

    fun build(): CliComponent
  }
}
