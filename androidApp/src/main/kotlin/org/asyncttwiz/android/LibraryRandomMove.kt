package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone

/** STUB until the shared "random order to Library top/bottom" rule lands (replaced at merge): the cards
 * onto their owners' Library top or bottom, one ordinary move each in a random order, as ONE invocation. */
internal fun TableToolkitController.toLibraryRandom(ids: List<String>, bottom: Boolean): Boolean =
    moveEachToOwnerZone(ids.distinct().shuffled(java.security.SecureRandom()), PlayZone.LIBRARY, bottom)
