package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext


/** One file for the device, intentionally independent of account IDs and logout cleanup. */
internal class CardAppearancePreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("card-appearance", Context.MODE_PRIVATE)
    fun read() = CardAppearance(
        cardScale(store.getFloat("size", 1f)), store.getBoolean("custom-areas", false),
        CardArea.entries.associateWith { cardScale(store.getFloat("area/${it.name}", store.getFloat("size", 1f))) },
        cardScale(store.getFloat("text", 1f)), store.getBoolean("custom-text", false),
        CardTextScalePart.entries.associateWith { cardScale(store.getFloat("text/${it.name}", store.getFloat("text", 1f))) })

    fun setSize(value: Float, area: CardArea? = null) {
        store.edit().putFloat(area?.let { "area/${it.name}" } ?: "size", cardScale(value)).apply()
    }
    fun setText(value: Float, part: CardTextScalePart? = null) {
        store.edit().putFloat(part?.let { "text/${it.name}" } ?: "text", cardScale(value)).apply()
    }
    fun customizeAreas(enabled: Boolean) {
        val edit = store.edit().putBoolean("custom-areas", enabled)
        if (enabled && !read().customizeAreas) for (area in CardArea.entries) edit.putFloat("area/${area.name}", read().cardSize)
        edit.apply()
    }
    fun customizeText(enabled: Boolean) {
        val edit = store.edit().putBoolean("custom-text", enabled)
        if (enabled && !read().customizeText) for (part in CardTextScalePart.entries) edit.putFloat("text/${part.name}", read().textSize)
        edit.apply()
    }
    fun observe(change: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> change() }
        store.registerOnSharedPreferenceChangeListener(listener)
        return { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

@Composable
internal fun rememberCardAppearancePreferences(): CardAppearancePreferences {
    val context = LocalContext.current
    return remember(context) { CardAppearancePreferences(context) }
}

@Composable
internal fun CardAppearancePreferences.state(): State<CardAppearance> {
    val state = remember(this) { mutableStateOf(read()) }
    DisposableEffect(this) {
        val stop = observe { state.value = read() }
        state.value = read()
        onDispose { stop() }
    }
    return state
}

@Composable
internal fun rememberCardAppearance(): CardAppearance = rememberCardAppearancePreferences().state().value
