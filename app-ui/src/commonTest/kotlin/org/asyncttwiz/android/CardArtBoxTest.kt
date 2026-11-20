package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals

class CardArtBoxTest {
    // LOB-144: on an upright picture the warning stamp is a square as big as the art box's smaller side, centred in the art box
    @Test fun anUprightPicturesStampSitsOnItsArtBox() {
        val stamp = CardArtBox.stamp(200f, 280f)
        val art = CardArtBox.of(200f, 280f)
        assertEquals(art.height, stamp.width, .01f)
        assertEquals(stamp.width, stamp.height, .01f)
        assertEquals(art.center.x, stamp.center.x, .01f)
        assertEquals(art.center.y, stamp.center.y, .01f)
    }

    // LOB-144 (owner, 2026-11-19, review item ac8328aef7-c): on a picture shown sideways (Inspect's split cards and battles) the
    // stamp is centred on the picture, as big as on the same card upright
    @Test fun aSidewaysPicturesStampIsCentred() {
        val stamp = CardArtBox.stamp(280f, 200f)
        assertEquals(CardArtBox.stamp(200f, 280f).width, stamp.width, .01f)
        assertEquals(140f, stamp.center.x, .01f)
        assertEquals(100f, stamp.center.y, .01f)
    }
}
