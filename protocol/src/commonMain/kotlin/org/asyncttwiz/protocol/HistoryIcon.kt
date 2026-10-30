package org.asyncttwiz.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Every icon a history event can carry. [wire] is the stored/transmitted name, the Material icon name the
 * icon had while it was a plain string, so retained groupings and snapshots decode unchanged. A name this
 * build does not know (a newer server) decodes as [UNKNOWN], drawn as a neutral dot, never a failure.
 * Clients draw these through exhaustive `when`s: a new entry does not compile until every table draws it. */
@Serializable(with = HistoryIconSerializer::class)
enum class HistoryIcon(val wire: String) {
    FLAG("flag"), CLOSE("close"), OUTLINED_FLAG("outlined_flag"), ROTATE_90_DEGREES_CW("rotate_90_degrees_cw"),
    FAVORITE("favorite"), EXPOSURE_PLUS_1("exposure_plus_1"), CASINO("casino"), MONETIZATION_ON("monetization_on"),
    SHUFFLE("shuffle"), VISIBILITY("visibility"), PAGEVIEW("pageview"), OPEN_WITH("open_with"), DOWNLOAD("download"),
    CHAT_BUBBLE("chat_bubble"), INBOX("inbox"), ADS_CLICK("ads_click"), SETTINGS_BACKUP_RESTORE("settings_backup_restore"),
    STICKY_NOTE_2("sticky_note_2"), SWITCH_ACCOUNT("switch_account"), FLIP_CAMERA_ANDROID("flip_camera_android"),
    FLIP("flip"), FLIP_TO_FRONT("flip_to_front"), ADD_BOX("add_box"), CONTROL_POINT_DUPLICATE("control_point_duplicate"),
    DELETE("delete"), LINK("link"), LINK_OFF("link_off"), SWORDS("swords"), LAYERS("layers"), LAYERS_CLEAR("layers_clear"),
    REMOVE_CIRCLE_OUTLINE("remove_circle_outline"), FORMAT_INK_HIGHLIGHTER("format_ink_highlighter"),
    SWAP_HORIZ("swap_horiz"),
    /** Older clients' result glyph; no current event records it, retained data may. */
    EMOJI_EVENTS("emoji_events"),
    /** Decoded from a name this build does not know. */
    UNKNOWN("unknown");

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun fromWire(name: String): HistoryIcon = byWire[name] ?: UNKNOWN
    }
}

internal object HistoryIconSerializer : KSerializer<HistoryIcon> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("org.asyncttwiz.protocol.HistoryIcon", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: HistoryIcon) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): HistoryIcon = HistoryIcon.fromWire(decoder.decodeString())
}
