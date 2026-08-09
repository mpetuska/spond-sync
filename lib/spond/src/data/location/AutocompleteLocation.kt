package dev.petuska.spond.sync.spond.data.location

import dev.petuska.spond.sync.spond.data.WithId
import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AutocompleteLocation(
  @SerialName("placeId") override val id: LocationId,
  @SerialName("featureName") val name: String,
  val addressLine: String,
) : Identifiable, WithId {
  override val identity: String
    get() = "AutocompleteLocation(id=$id, name=$name)"
}
