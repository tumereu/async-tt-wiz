package org.asyncttwiz.android

/** TRN-063 (owner playtest 2026-11-09): a quick second tap on the main button is ignored. */
object MainButtonGuard {
    /** A press this soon (ms) after the last one taken is the second tap of a double tap. */
    const val MILLIS = 300L
}
