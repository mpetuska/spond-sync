package utils.serialization

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class InstantSerializer : KSerializer<Instant> {
  private val delegate = String.serializer()
  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun serialize(encoder: Encoder, value: Instant) {
    delegate.serialize(encoder, value.toString())
  }

  override fun deserialize(decoder: Decoder): Instant = Instant.parse(delegate.deserialize(decoder))
}
