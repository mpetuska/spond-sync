package spond.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import spond.data.WithId
import utils.Identifiable

@Serializable
data class AutocompleteLocation(
  @SerialName("placeId")
  override val id: LocationId,
  @SerialName("featureName")
  val name: String,
  val addressLine: String,
) : Identifiable, WithId {
  override val identity: String
    get() = "AutocompleteLocation(id=$id, name=$name)"
}
