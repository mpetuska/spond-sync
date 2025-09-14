package worker.util

actual fun exitProcess(status: Int): Nothing = kotlin.system.exitProcess(status)
