package cli

import cli.config.DaggerCliComponent
import co.touchlab.kermit.Severity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import worker.WorkerConfig
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
suspend fun main(vararg args: String) {
  val configFile = requireNotNull(args.getOrNull(0))
  val config: WorkerConfig = File(configFile).inputStream().use(Json::decodeFromStream)
  val logSeverity = when {
    !System.getenv("LOG_LEVEL").isNullOrBlank() -> System.getenv("LOG_LEVEL").let { level ->
      Severity.entries.first { it.name.startsWith(level, ignoreCase = true) }
    }

    System.getenv("ACTIONS_STEP_DEBUG") == "true" -> Severity.Debug
    else -> Severity.Warn
  }
  val component = DaggerCliComponent.builder()
    .config(config)
    .logSeverity(logSeverity)
    .build()

  val worker = component.worker()
  if ("--clean" in args) {
    worker.cleanGroup()
  } else if ("--nosync" in args) {
    worker.syncGroup()
  }
}
