package cli.config

import co.touchlab.kermit.Message
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag

/**
 * ```
 * SEVERITY:
 * ```
 */
class ColourLogFormatter : MessageStringFormatter {
  private val severityLength = 1
  private val tagLength = 20

  override fun formatSeverity(severity: Severity): String {
    val capped = severity.name.take(severityLength).padEnd(severityLength)
    return "${capped.uppercase()}:"
  }

  override fun formatTag(tag: Tag): String {
    val cappedTag = tag.tag.take(tagLength).padEnd(tagLength)
    return cappedTag
  }

  override fun formatMessage(severity: Severity?, tag: Tag?, message: Message): String {
    return buildString {
      append(ansiColour(severity))
      severity?.let(::formatSeverity)?.also { append("$it ") }
      tag?.let(::formatTag)?.also { append("$it ") }
      append(message.message)
      append(ANSI_RESET)
    }
  }

  private fun ansiColour(severity: Severity?) = when (severity) {
    Severity.Verbose -> ANSI_WHITE
    Severity.Debug -> ANSI_RESET
    Severity.Info -> ANSI_CYAN
    Severity.Warn -> ANSI_YELLOW
    Severity.Error -> ANSI_RED
    Severity.Assert -> ANSI_PURPLE
    null -> ANSI_RESET
  }

  private companion object {
    const val ANSI_RESET = "\u001B[0m"
    const val ANSI_RED = "\u001B[31m"
    const val ANSI_YELLOW = "\u001B[33m"
    const val ANSI_CYAN = "\u001B[36m"
    const val ANSI_WHITE = "\u001B[37m"
    const val ANSI_PURPLE = "\u001B[35m"
  }
}
