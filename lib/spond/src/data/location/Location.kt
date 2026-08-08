package spond.data.location

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import utils.Identifiable
import utils.serialization.PreservingJsonSerializer

typealias LocationId = String

@OptIn(ExperimentalSerializationApi::class)
@Serializable(Location.Serializer::class)
@KeepGeneratedSerializer
@Suppress("ConstructorParameterNaming")
data class Location(
  @SerialName("featureName") private var _featureName: String? = null,
  @SerialName("feature") private var _feature: String? = _featureName,
  @SerialName("addressLine") private var _addressLine: String? = null,
  @SerialName("address") private var _address: String? = _addressLine,
  @SerialName("#json") private val json: JsonObject,
) : Identifiable {

  init {
    _featureName = _featureName ?: _feature
    _feature = _feature ?: _featureName
    _addressLine = _addressLine ?: _address
    _address = _address ?: _addressLine ?: _feature
  }

  override val identity: String
    get() = "Location(feature=$feature, address=$address)"

  val feature
    get() = checkNotNull(_feature)

  val address
    get() = checkNotNull(_address)

  internal object Serializer : PreservingJsonSerializer<Location>(generatedSerializer())
}
