package utils.http

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> HttpClient.paginate(
  json: Json,
  crossinline setPage: HttpRequestBuilder.(lastValue: T?, page: UInt) -> Unit,
  crossinline isLastPage: (page: UInt, response: HttpResponse, pageValueCount: UInt) -> Boolean,
  crossinline builder: HttpRequestBuilder.() -> Unit,
): Flow<T> = flow<T> {
  var page = 0u
  var lastValue: T? = null
  while (true) {
    val last = prepareGet {
      setPage(lastValue, ++page)
      builder()
    }.execute {
      var emittedValues = 0u
      it.bodyAsChannel().toInputStream().use { stream ->
        json.decodeToSequence<T>(
          stream = stream,
        ).forEach { value ->
          lastValue = value
          emittedValues++
          emit(value)
        }
      }
      isLastPage(page, it, emittedValues)
    }
    if (last || lastValue == null) break
  }
}.flowOn(Dispatchers.IO)

fun HttpRequestBuilder.parameters(name: String, values: Iterable<Any>?) {
  values?.joinToString(separator = ",")?.let { parameter(name, it) }
}
