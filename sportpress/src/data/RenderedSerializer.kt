package sportpress.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RenderedSerializer : KSerializer<String> {
  private val delegate = Rendered.serializer()
  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun deserialize(decoder: Decoder): String {
    return delegate.deserialize(decoder).rendered
  }

  override fun serialize(encoder: Encoder, value: String) {
    delegate.serialize(encoder, Rendered(value))
  }

  @Serializable private class Rendered(val rendered: String)
}
