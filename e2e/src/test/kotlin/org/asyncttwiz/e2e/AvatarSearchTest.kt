package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * Owner, 2026-11-14 (LOB-149): avatar choices name their cards, and what they are is said above their framed area; the
 * flow of choosing a searched card's art as your avatar in Edit profile.
 */
class AvatarSearchTest : E2e() {
    // LOB-149, LOB-013, LOB-010: Alice searches "Ember Twin" in Edit profile: the choice shows the card's name under its art
    // and the line above says what matched; she taps it and saves, and her account has that card's art as its avatar
    @Test fun aSearchedAvatarShowsItsCardsNameAndIsSaved() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-SETTINGS")) }
        alice.tagged("menu-SETTINGS").performClick()
        waitUntil("Alice's settings") { alice.shows(androidx.compose.ui.test.hasContentDescription("Edit profile")) }
        alice.described("Edit profile").performClick()
        waitUntil("the Edit profile dialog's random choices") {
            alice.showsText("Random legendary creatures and planeswalkers") && alice.shows(hasTestTag("avatar-choices"))
        }
        alice.tagged("avatar-search").performTextInput(TestCards.twin.name)
        val choice = hasTestTag("avatar-choice") and hasAnyDescendant(hasText(TestCards.twin.name))
        waitUntil("Ember Twin named under its art") { alice.showsText("Cards matching “Ember Twin”: 1") && alice.shows(choice) }
        alice.node(choice).performClick()
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Save"))).performClick()
        waitUntil("Alice's avatar saved") { alice.auth.state.account?.avatar?.scryfallId == TestCards.twin.scryfallId }
    }
}
