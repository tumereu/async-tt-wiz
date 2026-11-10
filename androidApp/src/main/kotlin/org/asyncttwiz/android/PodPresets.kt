package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

/** What a preset stores from the New pod form (or pod setup): everything except the pod name and invitees. */
internal data class PodPresetForm(val settings: DraftSettings, val cubeId: String? = null,
    val setCode: String? = null, val setName: String? = null,
    /** Display only: the chosen cube's or set's name. */
    val sourceName: String? = null)

/** The preset list's open sub-dialog. */
internal sealed interface PodPresetPrompt {
    /** Asks a name for a new preset, or a new name for [renaming]. */
    data class Name(val renaming: PodPreset? = null) : PodPresetPrompt
    /** [name] is what to save as; [existing] the spelling of the preset it would replace. */
    data class Overwrite(val name: String, val existing: String = name) : PodPresetPrompt
    data class Delete(val preset: PodPreset) : PodPresetPrompt
}

internal data class PodPresetState(
    val open: Boolean = false, val presets: List<PodPreset> = emptyList(), val loaded: Boolean = false,
    val query: String = "", val busy: String? = null, val error: String? = null, val prompt: PodPresetPrompt? = null,
) {
    /** Built-ins first, then the account's presets (DRF-031). */
    val filtered: List<PodPreset> get() = podPresetFilter(PodBuiltInPresets + presets, query)
    /** Few presets fit on screen without searching. */
    val searchable: Boolean get() = presets.size > PodPresetSearchThreshold || query.isNotEmpty()
}
internal const val PodPresetSearchThreshold = 6

/** Every word must appear in the name, the summary or the set code, ignoring case. */
internal fun podPresetFilter(presets: List<PodPreset>, query: String): List<PodPreset> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return presets
    return presets.filter { preset ->
        val text = "${preset.name} ${podPresetSummary(preset)} ${preset.setCode.orEmpty()}"
        words.all { text.contains(it, ignoreCase = true) }
    }
}

/**
 * Built-in presets (DRF-031): listed before the account's own, settings only (choosing one keeps the current source),
 * never renamed, replaced or deleted. Only on the device; the server never stores them.
 */
internal val PodBuiltInPresets = listOf(
    PodPreset("builtin-glimpse", "Glimpse", DraftSettings.glimpse(), updatedAt = ""),
)
internal val PodPreset.builtIn: Boolean get() = PodBuiltInPresets.any { it.id == id }
/** A saved preset may not take a built-in's name (DRF-033): built-ins can't be replaced. */
internal fun podBuiltInName(name: String) = PodBuiltInPresets.any { PodPresetValidation.key(it.name) == PodPresetValidation.key(name) }

/** "Booster · My cube · 3×15", "Grid · Dominaria United · 18 grids"; built-ins "Glimpse · Built-in · 9×15". */
internal fun podPresetSummary(preset: PodPreset): String {
    if (preset.builtIn) return listOf(preset.name, "Built-in", "${preset.settings.packsPerPlayer}×${preset.settings.cardsPerPack}").joinToString(" · ")
    val source = when {
        preset.cubeMissing -> "Cube missing"
        preset.cubeName != null -> preset.cubeName!!
        preset.setCode != null -> preset.setName ?: preset.setCode!!
        else -> "No source"
    }
    return podPresetSummary(preset.settings, source, set = preset.setCode != null)
}
internal fun podPresetSummary(settings: DraftSettings, source: String, set: Boolean): String {
    val size = when {
        settings.mode == DraftMode.GRID -> "${settings.grids} grids"
        settings.mode == DraftMode.ROTISSERIE || settings.mode == DraftMode.SEALED && !set -> "${settings.cardsPerPlayer} cards each"
        settings.mode == DraftMode.SEALED -> "${settings.packsPerPlayer} ${if (settings.packsPerPlayer == 1) "booster" else "boosters"}"
        set -> podPackCount(settings.packsPerPlayer)
        else -> "${settings.packsPerPlayer}×${settings.cardsPerPack}"
    }
    return listOf(podModeName(settings.mode), source, size).joinToString(" · ")
}

/**
 * The account's presets, kept on the server. Each change is sent on its own and then the list is read again;
 * nothing is predicted. [form] snapshots the form being edited (null when there is nothing to save); [apply] loads a preset.
 */
internal class PodPresetController(private val api: Pods, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val form: () -> PodPresetForm?, private val apply: (PodPreset) -> Unit) {
    var state by mutableStateOf(PodPresetState())
        private set
    private var generation = 0L

    fun open() {
        if (state.open) return
        generation++
        state = state.copy(open = true, query = "", busy = null, error = null, prompt = null)
        perform("Loading presets…")
    }
    fun dismiss() { generation++; state = state.copy(open = false, query = "", busy = null, error = null, prompt = null) }
    fun clear() { dismiss(); state = PodPresetState() }
    fun reload() { if (state.open && state.busy == null) perform("Loading presets…") }
    fun query(value: String) { state = state.copy(query = value) }
    fun dismissError() { state = state.copy(error = null) }
    val canSave: Boolean get() = form() != null
    /** What "Save" would store, in the list's row format. */
    val formSummary: String? get() = form()?.let {
        podPresetSummary(it.settings, it.sourceName ?: if (it.cubeId == null && it.setCode == null) "No source" else "Cube", it.setCode != null)
    }

    fun choose(preset: PodPreset) {
        if (!state.open || state.busy != null) return
        dismiss(); apply(preset)
    }
    fun startSave() { if (state.busy == null && canSave) state = state.copy(prompt = PodPresetPrompt.Name(), error = null) }
    fun startRename(preset: PodPreset) { if (state.busy == null && !preset.builtIn) state = state.copy(prompt = PodPresetPrompt.Name(preset), error = null) }
    fun startDelete(preset: PodPreset) { if (state.busy == null && !preset.builtIn) state = state.copy(prompt = PodPresetPrompt.Delete(preset), error = null) }
    fun cancelPrompt() { if (state.busy == null) state = state.copy(prompt = null, error = null) }

    /** Saves under [text], asking first when another preset already has that name; or renames. */
    fun submitName(text: String) {
        val prompt = state.prompt as? PodPresetPrompt.Name ?: return
        if (state.busy != null) return
        val name = text.trim()
        if (!PodPresetValidation.name(name)) { state = state.copy(error = "Enter a name of up to ${PodPresetValidation.MAX_NAME} characters."); return }
        val key = PodPresetValidation.key(name)
        val renaming = prompt.renaming
        val taken = state.presets.firstOrNull { PodPresetValidation.key(it.name) == key && it.id != renaming?.id }
        when {
            podBuiltInName(name) -> state = state.copy(error = "“$name” is a built-in preset. Choose another name.")
            renaming == null && taken != null -> state = state.copy(prompt = PodPresetPrompt.Overwrite(name, taken.name), error = null)
            renaming == null -> save(name, false)
            taken != null -> state = state.copy(error = "Another preset is already called “${taken.name}”.")
            name == renaming.name -> state = state.copy(prompt = null, error = null)
            else -> perform("Renaming…", renaming = true) { api.renamePreset(it, renaming.id, RenamePodPreset(name)) }
        }
    }
    fun confirmOverwrite() { (state.prompt as? PodPresetPrompt.Overwrite)?.let { if (state.busy == null) save(it.name, true) } }
    fun confirmDelete() {
        val preset = (state.prompt as? PodPresetPrompt.Delete)?.preset ?: return
        if (state.busy == null) perform("Deleting…") { api.deletePreset(it, preset.id) }
    }

    private fun save(name: String, overwrite: Boolean) {
        val current = form() ?: return
        val input = SavePodPreset(name, current.settings, current.cubeId,
            current.setCode, current.setName?.takeIf { current.setCode != null }, overwrite)
        perform("Saving…", saving = name) { api.savePreset(it, input) }
    }

    /** Runs [change] (if any) and then reads the list again. */
    /** [done] is shown once the list is read (why it was read again). */
    private fun perform(message: String, saving: String? = null, renaming: Boolean = false, done: String? = null,
        change: (suspend (String) -> Any)? = null) {
        val token = credential() ?: run { state = state.copy(error = "Sign in to use presets."); return }
        val current = generation
        state = state.copy(busy = message, error = null)
        worker.execute {
            val result = runCatching {
                runBlocking {
                    change?.invoke(token)
                    api.presets(token).presets
                }
            }
            post {
                if (current != generation) return@post
                result.onSuccess { presets ->
                    state = state.copy(busy = null, presets = presets, loaded = true, prompt = null, error = done)
                }.onFailure { failure ->
                    val code = (failure as? PodFailure)?.code
                    state = state.copy(busy = null)
                    when {
                        // Someone else (another device) took the name meanwhile: ask about replacing it instead.
                        code == "preset_exists" && saving != null -> state = state.copy(prompt = PodPresetPrompt.Overwrite(saving))
                        code == "not_found" -> { state = state.copy(prompt = null); perform("Loading presets…", done = "That preset no longer exists.") }
                        else -> state = state.copy(error = podPresetError(code, renaming))
                    }
                }
            }
        }
    }
}

internal fun podPresetError(code: String?, renaming: Boolean = false): String = when (code) {
    "preset_exists" -> if (renaming) "Another preset already has that name." else "A preset with that name already exists."
    "too_many_presets" -> "You can keep up to ${PodPresetValidation.MAX_PRESETS} presets. Delete one first."
    "cube_not_found" -> "The chosen cube no longer exists. Choose another source first."
    "automatum_unsupported" -> "Automatum can't play every card of this cube yet. Choose another source."
    "invalid_settings" -> "These settings are not valid. Fix them before saving."
    "invalid_name" -> "Enter a name of up to ${PodPresetValidation.MAX_NAME} characters."
    "invalid_source" -> "This source cannot be saved in a preset."
    null, "network", "unavailable", "try_later" -> "Could not reach the server. Try again."
    else -> "Could not complete the preset request ($code)."
}
