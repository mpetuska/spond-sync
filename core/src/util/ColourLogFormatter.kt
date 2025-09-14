package core.util

import co.touchlab.kermit.Message
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * ```
 * SEVERITY:
 * ```
 */
@SingleIn(AppScope::class)
@Inject
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

  companion object {
    private fun ansiColour(severity: Severity?) =
      when (severity) {
        Severity.Verbose -> ANSI_WHITE
        Severity.Debug -> ANSI_RESET
        Severity.Info -> ANSI_CYAN
        Severity.Warn -> ANSI_YELLOW
        Severity.Error -> ANSI_RED
        Severity.Assert -> ANSI_PURPLE
        null -> ANSI_RESET
      }

    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_CYAN = "\u001B[36m"
    private const val ANSI_WHITE = "\u001B[37m"
    private const val ANSI_PURPLE = "\u001B[35m"
  }
}
