package cli

import cli.config.DiApp
import cli.config.build
import co.touchlab.kermit.Severity
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.runBlocking
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class CliCommand(private val fileSystem: FileSystem = SystemFileSystem) :
  CliktCommand("spond-sync") {
  init {
    context {
      helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true, showRequiredTag = true) }
    }
  }

  override val printHelpOnEmptyArgs = true
  private val ci by option(hidden = true, envvar = "CI").flag("--noci")
  private val githubRunAttempt by option(hidden = true, envvar = "GITHUB_RUN_ATTEMPT").int()
  private val actionsStepDebug by option(hidden = true, envvar = "ACTIONS_STEP_DEBUG").flag()
  private val logLevel by
    option(
        envvar = "LOG_LEVEL",
        help = "Console log level [ Verbose, Debug, Info, Warn, Error, Assert ]",
      )
      .convert { level ->
        Severity.entries.firstOrNull { it.name.startsWith(level, ignoreCase = true) }
          ?: fail("log-level=$level is invalid!")
      }
      .default(Severity.Warn)
  private val clean by
    option(help = "Should the group be cleaned of old managed events before updating")
      .flag("--noclean", defaultForHelp = "disabled")

  private val yes by
    option(names = arrayOf("--yes", "-y"), help = "Should all confirmations be assumed yes.")
      .flag("--no", defaultForHelp = "disabled")

  private val sync by
    option(help = "Should event sync be performed.")
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

  private val updateConfig by
    option(
        names = arrayOf("--update-config"),
        help = "Should config file be updated with expanded default values.",
      )
      .flag("--noupdate-config")

  private val dry by
    option(
        names = arrayOf("--dry"),
        help = "Should spond changes should only be logged and not pushed.",
      )
      .flag("--nodry")

  private val config by
    argument(help = "Sync config json file").convert {
      val path = Path(it)
      if (fileSystem.exists(path)) path else fail("Specified config file $it does not exist!")
    }

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
    allowTrailingComma = true
    allowComments = true
  }

  override fun run() = runBlocking {
    val logSeverity =
      when {
        actionsStepDebug -> Severity.Verbose
        (githubRunAttempt ?: 0) > 1 -> {
          Severity.Debug
        }
        dry -> minOf(logLevel, Severity.Info)
        else -> logLevel
      }
    val syncConfig: SyncConfig =
      fileSystem.source(config).buffered().use(Source::readString).let(json::decodeFromString)
    val app =
      DiApp::class.build(
        volleyZoneConfig = syncConfig.volleyzone,
        sourceOffset = core.util.Duration(sourceOffset),
        sinkOffset = core.util.Duration(sinkOffset),
        severity = logSeverity,
        gitHubCi = ci && githubRunAttempt != null,
        json = json,
        dry = dry,
      )
    val club = app.club(syncConfig.spond)
    val worker = club.syncWorker
    if (clean) worker.cleanGroup(yes)
    if (sync) worker.syncGroup()
    if (updateConfig)
      fileSystem.sink(config).buffered().use { it.writeText(json.encodeToString(syncConfig)) }
  }
}
