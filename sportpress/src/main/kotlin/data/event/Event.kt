package sportpress.data.event

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import sportpress.data.RenderedSerializer
import sportpress.data.team.TeamId
import utils.Identifiable

typealias EventId = UInt

@Serializable(Event.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Suppress("ConstructorParameterNaming")
data class Event(
  val id: EventId,
  @SerialName("title")
  @Serializable(RenderedSerializer::class)
  val name: String,
  val teams: List<TeamId>,
  val day: String,
  val link: String,
  val date: LocalDateTime,
  @SerialName("date_gmt")
  val dateGmt: LocalDateTime,
  val modified: LocalDateTime,
  @SerialName("modified_gmt")
  val modifiedGmt: LocalDateTime,
  @SerialName("results")
  val results: Map<UInt, Result>
) : Identifiable {
  override val identity: String
    get() = "Event(id=$id, name=$name)"

  @Serializable
  data class Result(
    @SerialName("one")
    private val _one: String,
    @SerialName("two")
    private val _two: String,
    @SerialName("three")
    private val _three: String? = null,
  ) {
    val one: UInt get() = _one.toUInt()
    val two: UInt get() = _two.toUInt()
    val three: UInt? get() = _three?.toUIntOrNull()
  }

  internal object Serializer : JsonTransformingSerializer<Event>(generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
      require(element is JsonObject)
      val new = buildJsonObject {
        element.forEach { (k, v) -> put(k, v) }
        put("results", JsonObject(mapOf()))
        val oldResults = element["results"]
        if (oldResults is JsonObject) {
          var valid = true
          val newResult = buildJsonObject {
            for ((id, result) in oldResults) {
              if (result !is JsonObject) {
                valid = false
                break
              }
              if (id != "0") {
                put(id, result)
              }
            }
          }
          if (valid) {
            put("results", newResult)
          }
        }
      }

      return super.transformDeserialize(new)
    }
  }
}
