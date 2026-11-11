package org.asyncttwiz.appui

/**
 * Plays [pcm] (16-bit signed mono samples at [sampleRate] Hz) once, at once, over whatever else the device plays; returns
 * without waiting for it to end (TRN-075, LOB-146: the "Your move" sound; Android: an AudioTrack of the game's sounds). May
 * throw when the platform can't play it.
 */
expect fun playPcm(pcm: ShortArray, sampleRate: Int)
