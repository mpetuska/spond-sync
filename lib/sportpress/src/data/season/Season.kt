package dev.petuska.spond.sync.sportpress.data.season

import dev.petuska.spond.sync.utils.Identifiable
import kotlinx.serialization.Serializable

typealias SeasonId = UInt

@Serializable
data class Season(val id: SeasonId, val name: String) : Identifiable {
  override val identity: String
    get() = "Season(id=$id, name=$name)"
}
