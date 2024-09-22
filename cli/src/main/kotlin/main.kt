package cli

import cli.config.DaggerCliComponent
import co.touchlab.kermit.Severity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import worker.SyncWorker
import worker.WorkerConfig
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
suspend fun main(vararg args: String) {
  val configFile = requireNotNull(args[0])
  val config: WorkerConfig = File(configFile ).inputStream().use(Json::decodeFromStream)
  val component = DaggerCliComponent.builder()
    .config(config)
    .logSeverity(Severity.Debug)
    .build()

  val worker = component.worker()
//  worker.cleanGroup()
  worker.syncGroup()
}
