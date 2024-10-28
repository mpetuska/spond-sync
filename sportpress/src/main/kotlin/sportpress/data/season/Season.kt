package sportpress.data.season

import kotlinx.serialization.Serializable
import utils.Identifiable

typealias SeasonId = UInt

@Serializable
data class Season(
  val id: SeasonId,
  val name: String,
) : Identifiable {
  override val identity: String
    get() = "Season(id=$id, name=$name)"
}
