@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.appui

// The "Your move" sound in the browser (WEB-008, LOB-146, TRN-075): Web Audio. Browsers start a page's audio only after
// the player has touched it, so the shell unlocks it on the first tap or key ([unlockSound]); once running it keeps
// playing in a background tab.

/** Plays [pcm] through the page's audio context; throws when the browser has none. */
actual fun playPcm(pcm: ShortArray, sampleRate: Int) {
    val samples = jsFloatArray(pcm.size)
    pcm.forEachIndexed { index, value -> jsSetFloat(samples, index, value / 32768f) }
    jsPlaySamples(samples, sampleRate)
}

/** Starts the page's audio context on the player's first tap or key, so later sounds (a background tab's too) play. */
fun unlockSound() = jsUnlockAudio()

private fun jsFloatArray(size: Int): JsAny = js("new Float32Array(size)")
private fun jsSetFloat(array: JsAny, index: Int, value: Float): Unit = js("{ array[index] = value; }")

private fun jsPlaySamples(samples: JsAny, rate: Int): Unit = js("""{
    const Context = globalThis.AudioContext || globalThis.webkitAudioContext;
    if (!Context) throw new Error('This browser has no Web Audio');
    const context = globalThis.__atwAudio || (globalThis.__atwAudio = new Context());
    if (context.state === 'suspended') context.resume();
    const buffer = context.createBuffer(1, samples.length, rate);
    buffer.copyToChannel(samples, 0);
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.connect(context.destination);
    source.start();
}""")

private fun jsUnlockAudio(): Unit = js("""{
    if (typeof window === 'undefined') return;
    const unlock = () => {
        const Context = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!Context) return;
        const context = globalThis.__atwAudio || (globalThis.__atwAudio = new Context());
        if (context.state === 'suspended') context.resume();
        if (context.state === 'running') {
            window.removeEventListener('pointerdown', unlock, true);
            window.removeEventListener('keydown', unlock, true);
        }
    };
    window.addEventListener('pointerdown', unlock, true);
    window.addEventListener('keydown', unlock, true);
}""")
