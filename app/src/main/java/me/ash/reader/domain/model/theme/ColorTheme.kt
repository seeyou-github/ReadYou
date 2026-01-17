package me.ash.reader.domain.model.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

@Serializable
data class ColorTheme(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    @Serializable(with = ColorSerializer::class)
    val textColor: Color,
    @Serializable(with = ColorSerializer::class)
    val backgroundColor: Color,
    @Serializable(with = ColorSerializer::class)
    val primaryColor: Color,
    val isDefault: Boolean = false,
    val isDarkTheme: Boolean = false
)

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeString(value.value.toString())
    }

    override fun deserialize(decoder: Decoder): Color {
        return Color(decoder.decodeString().toULong())
    }
}
