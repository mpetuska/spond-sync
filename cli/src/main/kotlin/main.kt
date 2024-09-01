package cli

import cli.config.DaggerCliComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import worker.SyncWorker
import worker.WorkerConfig
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
suspend fun main() {
  val config: WorkerConfig = File("cli.config.json").inputStream().use(Json::decodeFromStream)
  val component = DaggerCliComponent.builder()
    .team(config.team)
    .spond(config.spond)
    .sportpress(config.sportpress)
    .build()

  val worker = component.worker()
  worker.syncGroup(config.team, false)
}
