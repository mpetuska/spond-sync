package cli

import cli.config.ColourLogFormatter
import cli.config.GHAFormatter
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
import kotlinx.coroutines.runBlocking
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import worker.WorkerComponent
import worker.WorkerConfig
import worker.build

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
  private val sync by
    option(help = "Should event sync be performed.")
      .flag("--nosync", default = true, defaultForHelp = "enabled")

  private val config by
    argument(help = "Worker config json file").convert {
      val path = Path(it)
      if (fileSystem.exists(path)) path else fail("Specified config file $it does not exist!")
    }

  override fun run() = runBlocking {
    val logSeverity = if (actionsStepDebug) Severity.Debug else logLevel
    val workerConfig: WorkerConfig =
      fileSystem.source(config).buffered().use(Source::readString).let(Json::decodeFromString)
    val logFormatter = if (ci && githubRunAttempt != null) GHAFormatter() else ColourLogFormatter()
    val worker = WorkerComponent::class.build(workerConfig, logSeverity, logFormatter).worker
    if (clean) worker.cleanGroup()
    if (sync) worker.syncGroup()
  }
}
