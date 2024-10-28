package cli.config

import co.touchlab.kermit.Message
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag

class GHAFormatter(
  private val colourLogFormatter: ColourLogFormatter = ColourLogFormatter()
) : MessageStringFormatter by colourLogFormatter {

  override fun formatSeverity(severity: Severity): String {
    val stackTrace = Throwable(
      "STACK"
    ).stackTrace.firstOrNull { !it.className.startsWith("co.touchlab") && !it.className.startsWith("cli.config") }
    val params = stackTrace?.let { " file=${stackTrace.fileName},line=${stackTrace.lineNumber}" } ?: ""
    val level = when (severity) {
      Severity.Verbose -> "debug"
      Severity.Debug -> "debug"
      Severity.Info -> "notice"
      Severity.Warn -> "warning"
      Severity.Error -> "error"
      Severity.Assert -> "error"
    }
    return "::$level$params::${colourLogFormatter.formatSeverity(severity)}"
  }

  override fun formatMessage(severity: Severity?, tag: Tag?, message: Message): String {
    return super.formatMessage(severity, tag, message).replace("\n", "%0A")
  }
}
