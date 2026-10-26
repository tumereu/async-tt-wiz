package org.asyncttwiz.android

import org.asyncttwiz.protocol.DefinitionFace

/** Current-face metadata also covers tokens and retained projection snapshots. No type/rules inference. */
internal fun changedPowerToughness(displayed: String, face: DefinitionFace?): Boolean =
    face != null && face.power.isNotEmpty() && face.toughness.isNotEmpty() &&
        displayed != "${face.power}/${face.toughness}"

internal const val ChangedPowerToughnessColor = 0xffa0522d.toInt()
