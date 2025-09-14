package worker.util

import co.touchlab.kermit.Logger

/** Log [message] as an error and exit process. */
internal fun Logger.exit(message: String): Nothing {
  a(message, IllegalStateException(message))
  exitProcess(1)
}

expect fun exitProcess(status:Int): Nothing
