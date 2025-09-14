package testing

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlin.uuid.Uuid

object TestLogWriter : LogWriter() {
  private val observers = mutableMapOf<Uuid, Observer>()

  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    for (observer in observers.values) {
      if (observer.tags.isNotEmpty() && tag !in observer.tags) continue
      observer.onLog(severity, message, tag, throwable)
    }
  }

  fun observe(
    vararg tags: String,
    observer: (severity: Severity, message: String, tag: String, throwable: Throwable?) -> Unit,
  ): AutoCloseable {
    val id = Uuid.random()
    observers[id] = Observer(tags.toSet(), observer)
    return AutoCloseable { observers.remove(id) }
  }

  private class Observer(
    val tags: Set<String>,
    val onLog: (severity: Severity, message: String, tag: String, throwable: Throwable?) -> Unit,
  )
}
