package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.playPcm
import org.asyncttwiz.protocol.MoveSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * TRN-075, LOB-146 (owner, 2026-11-11): the "Your move" sounds, the app's own: each made here in code when first played, a
 * short soft tone (a fraction of full volume, a quick rise and a smooth decay), no recorded or copied sound.
 */
object MoveSounds {
    const val SAMPLE_RATE = 22_050

    /** How loud the loudest sample is, of full scale: soft. */
    private const val PEAK = .3

    private val made = mutableMapOf<MoveSound, ShortArray>()

    /** [sound]'s samples (16-bit mono at [SAMPLE_RATE]); null for [MoveSound.Off]. */
    fun pcm(sound: MoveSound): ShortArray? = when (sound) {
        MoveSound.Off -> null
        else -> made.getOrPut(sound) { synthesize(sound) }
    }

    /** Plays [sound] ([why]: the move passing to you, or Settings' play button), with a line in the app log; never throws. */
    fun play(sound: MoveSound, why: String) {
        val pcm = pcm(sound) ?: return
        AppLog.info("game", "Your move sound: ${sound.id} ($why)")
        try { playPcm(pcm, SAMPLE_RATE) } catch (e: Throwable) {
            AppLog.warn("game", "Your move sound ${sound.id} couldn't play", e)
        }
    }

    private fun synthesize(sound: MoveSound): ShortArray = when (sound) {
        // A quick upward blip: 700 Hz rising to 1400 Hz within 40 ms, gone in about 120 ms
        MoveSound.Plip -> tone(.12, attack = .004, decay = .035) { t -> listOf(glide(700.0, 1400.0, .04, t) to 1.0) }
        // A falling water drop: 1500 Hz down to 500 Hz
        MoveSound.Drop -> tone(.16, attack = .003, decay = .045) { t -> listOf(glide(1500.0, 500.0, .09, t) to 1.0) }
        // A small bell: E6 with a fifth and an octave above, ringing out
        MoveSound.Chime -> tone(.5, attack = .005, decay = .15) { listOf(1318.5 to 1.0, 1975.5 to .45, 2637.0 to .2) }
        // A soft wooden knock
        MoveSound.Tock -> tone(.09, attack = .002, decay = .02) { listOf(420.0 to 1.0, 1130.0 to .3) }
        MoveSound.Off -> ShortArray(0)
    }

    /** The frequency at [t] s of a glide from [from] to [to] Hz taking [over] s (exponential), then holding. */
    private fun glide(from: Double, to: Double, over: Double, t: Double): Double = from * exp(kotlin.math.ln(to / from) * min(t, over) / over)

    /**
     * [seconds] of the partials [partials] gives at each moment (frequency in Hz to relative amplitude), each with its own
     * phase, under one envelope: a linear rise over [attack] s and an exponential decay of time constant [decay] s, faded to
     * nothing over the last 10 ms.
     */
    private fun tone(seconds: Double, attack: Double, decay: Double, partials: (Double) -> List<Pair<Double, Double>>): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        val phases = DoubleArray(partials(0.0).size)
        val total = partials(0.0).sumOf { it.second }
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var value = 0.0
            partials(t).forEachIndexed { k, (frequency, amplitude) ->
                phases[k] += 2 * PI * frequency / SAMPLE_RATE
                value += amplitude * sin(phases[k])
            }
            val envelope = min(1.0, t / attack) * exp(-t / decay) * min(1.0, (seconds - t) / .01)
            (value / total * envelope * PEAK * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

/** The account's "Your move" sound (LOB-146) for a table: the device's copy of the account settings at once, then the
 * server's, read once per app run. */
@Composable
fun rememberMoveSound(accountId: String, token: () -> String?, origin: String): MoveSound {
    val platform = AppPlatform.current
    var sound by remember(accountId) { mutableStateOf(MoveSound.DEFAULT) }
    LaunchedEffect(platform, accountId, origin) {
        val store = AccountSettingsStore(platform, accountId, token, origin)
        sound = store.cached().moveSound
        if (MoveSoundRefresh.read.add(accountId)) runCatching { store.refresh() }
            .onSuccess { sound = it.moveSound; AppLog.info("game", "Your move sound of ${accountId.take(8)} from the server: ${sound.id}") }
            .onFailure { AppLog.info("game", "Your move sound of ${accountId.take(8)}: the server not read (${(it as? AccountFailure)?.code ?: it::class.simpleName}), " +
                "this device's ${sound.id}"); MoveSoundRefresh.read.remove(accountId) }
    }
    return sound
}

/** The accounts whose settings a table has read from the server in this app run ([rememberMoveSound]). */
object MoveSoundRefresh {
    internal val read = mutableSetOf<String>()
    /** Forgets them, as a new app run would (tests: one process runs many apps). */
    fun reset() = read.clear()
}
