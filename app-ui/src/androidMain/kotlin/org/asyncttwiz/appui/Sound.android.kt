package org.asyncttwiz.appui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** A static AudioTrack holding the whole sound, released a little after it has played. */
actual fun playPcm(pcm: ShortArray, sampleRate: Int) {
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
    try {
        track.write(pcm, 0, pcm.size)
        track.play()
    } catch (e: RuntimeException) {
        track.release()
        throw e
    }
    Handler(Looper.getMainLooper()).postDelayed({ runCatching { track.release() } }, pcm.size * 1000L / sampleRate + 300)
}
