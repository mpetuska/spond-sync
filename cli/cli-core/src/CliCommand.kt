package dev.petuska.spond.sync.cli

import co.touchlab.kermit.Severity
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.terminal.warning
import dev.petuska.spond.sync.cli.config.AppGraph
import dev.petuska.spond.sync.config.ConfigLoader
import dev.zacsweers.metro.createGraphFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.encodeToSink

class CliCommand(private val fileSystem: FileSystem = SystemFileSystem) :
  SuspendingCliktCommand("spond-sync") {
  init {
    context {
      helpFormatter = {
        MordantHelpFormatter(
          it,
          requiredOptionMarker = "*",
          showDefaultValues = true,
          showRequiredTag = true,
        )
      }
    }
  }

  override val printHelpOnEmptyArgs = true
  private val ci by option(hidden = true, envvar = "CI").flag("--noci")
  private val githubRunAttempt by option(hidden = true, envvar = "GITHUB_RUN_ATTEMPT").int()
  private val actionsStepDebug by option(hidden = true, envvar = "ACTIONS_STEP_DEBUG").flag()
  private val logLevel by
    option(
        names = arrayOf("--log-level", "-l"),
        envvar = "LOG_LEVEL",
        help = "Console log level [ Verbose, Debug, Info, Warn, Error, Assert ]",
      )
      .convert { level ->
        Severity.entries.firstOrNull { it.name.startsWith(level, ignoreCase = true) }
          ?: fail("log-level=$level is invalid!")
      }
      .default(Severity.Warn)
  private val clean by
    option(
        names = arrayOf("--clean", "-c"),
        help = "Should the group be cleaned of old managed events before updating",
      )
      .flag("--noclean", defaultForHelp = "disabled")

  private val yes by
    option(names = arrayOf("--yes", "-y"), help = "Should all confirmations be assumed yes.")
      .flag("--no", defaultForHelp = "disabled")

  private val sync by
    option(names = arrayOf("--sync", "-s"), help = "Should event sync be performed.")
      .flag("--nosync", default = true, defaultForHelp = "enabled")

  private val sourceOffset by
    option(
        names = arrayOf("--source-offset"),
        help = "Time offset for Source (VolleyZone) dates in days. Can be negative.",
      )
      .int()
      .convert { it.days }
      .default(Duration.ZERO)

  private val sinkOffset by
    option(
        names = arrayOf("--sink-offset"),
        help = "Time offset for Sink (Spond) dates in days. Can be negative.",
      )
      .int()
      .convert { it.days }
      .default(Duration.ZERO)

  private val writeConfig by
    option(
        names = arrayOf("--write-config"),
        help =
          "Should config file be written at the end of the run with merged configs and expanded default values.",
      )
      .convert { Path(it) }

  private val dry by
    option(
        names = arrayOf("--dry"),
        help = "Should spond changes should only be logged and not pushed.",
      )
      .flag("--nodry")

  private val configs by
    argument(help = "Sync config json files. The files are merged in order specified.")
      .convert { Path(it) }
      .multiple()

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
    allowTrailingComma = true
    allowComments = true
  }

  override suspend fun run() {
    val configs = configs.filter {
      if (fileSystem.exists(it)) {
        true
      } else {
        terminal.warning("Specified config file $it does not exist!")
        false
      }
    }
    check(configs.isNotEmpty()) { "None of the supplied config files exist! ${this.configs}" }
    val logSeverity =
      when {
        actionsStepDebug -> Severity.Verbose
        (githubRunAttempt ?: 0) > 1 -> {
          Severity.Debug
        }
        dry -> minOf(logLevel, Severity.Info)
        else -> logLevel
      }
    val syncConfig = ConfigLoader(json).load(configs)
    val app =
      createGraphFactory<AppGraph.Factory>()
        .create(
          volleyZoneConfig = syncConfig.volleyzone,
          sourceOffset = sourceOffset,
          sinkOffset = sinkOffset,
          severity = logSeverity,
          gitHubCi = ci && githubRunAttempt != null,
          json = json,
          dry = dry,
        )
    app.logger.d { "Config: $syncConfig" }
    val club = app.club(syncConfig.spond)
    val worker = club.syncWorker
    if (clean) worker.cleanGroup(yes)
    if (sync) worker.syncGroup()
    val writeConfig = writeConfig
    if (writeConfig != null)
      fileSystem.sink(writeConfig).buffered().use { json.encodeToSink(syncConfig, it) }
  }
}
