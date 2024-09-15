package worker.util

import co.touchlab.kermit.Logger
import kotlin.system.exitProcess

/**
 * Log [message] as an error and exit process.
 */
internal fun Logger.exit(message: String): Nothing {
  a(message, IllegalStateException(message))
  exitProcess(1)
}
