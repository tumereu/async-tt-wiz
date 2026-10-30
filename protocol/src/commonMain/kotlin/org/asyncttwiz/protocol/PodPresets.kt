package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/**
 * A saved New pod form, minus the pod name and invitees, stored per account on the server.
 * The source is a cube reference or a set code (the set snapshot is rebuilt from the client's catalog on load).
 * [cubeMissing]: the referenced cube no longer exists (deleted); the player must choose another source.
 */
@Serializable data class PodPreset(
    val id: String, val name: String, val settings: DraftSettings,
    val cubeId: String? = null, val cubeName: String? = null, val cubeMissing: Boolean = false,
    val setCode: String? = null, val setName: String? = null,
    val updatedAt: String,
)
/** GET /v1/pod-presets: all of the caller's presets, by name. */
@Serializable data class PodPresetList(val presets: List<PodPreset>)
/**
 * POST /v1/pod-presets. Names are unique per account ignoring case: an existing name is refused (409 preset_exists)
 * unless [overwrite], which replaces that preset's contents and name. At most one of cubeId/setCode.
 */
@Serializable data class SavePodPreset(
    val name: String, val settings: DraftSettings,
    val cubeId: String? = null, val setCode: String? = null, val setName: String? = null,
    val overwrite: Boolean = false,
)
/** PUT /v1/pod-presets/{id}. A name used by another preset is refused (409 preset_exists). */
@Serializable data class RenamePodPreset(val name: String)
/** DELETE /v1/pod-presets/{id} (no body). */
@Serializable data class PodPresetDeleted(val id: String)

object PodPresetValidation {
    const val MAX_NAME = 60
    const val MAX_PRESETS = 200
    fun name(name: String) = name.isNotBlank() && name == name.trim() && name.length <= MAX_NAME &&
        DeckValidation.text(name) && name.none { it.isISOControl() }
    fun setCode(code: String) = code.length in 1..32 && code.all { it.isLetterOrDigit() }
    fun setName(name: String) = name.isNotBlank() && name.length <= 256 && DeckValidation.text(name) && name.none { it.isISOControl() }
    /** Same key on client and server: names that differ only by case are the same preset. */
    fun key(name: String) = name.lowercase()
}
