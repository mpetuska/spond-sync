package utils.http

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> HttpClient.paginate(
  json: Json,
  crossinline setPage: HttpRequestBuilder.(lastValue: T?, page: UInt) -> Unit,
  crossinline isLastPage: (page: UInt, response: HttpResponse, pageValueCount: UInt) -> Boolean,
  crossinline builder: HttpRequestBuilder.() -> Unit,
): Flow<T> =
  flow {
      var page = 0u
      var lastValue: T? = null
      while (true) {
        val last =
          prepareGet {
              builder()
              setPage(lastValue, ++page)
            }
            .execute { response ->
              var emittedValues = 0u
              json.decodeFromString<List<T>>(response.bodyAsText()).forEach { value ->
                lastValue = value
                emittedValues++
                emit(value)
              }
              isLastPage(page, response, emittedValues)
            }
        if (last || lastValue == null) break
      }
    }
    .flowOn(Dispatchers.IO)

fun HttpRequestBuilder.parameters(name: String, values: Iterable<Any>?) {
  values?.joinToString(separator = ",")?.let { parameter(name, it) }
}
