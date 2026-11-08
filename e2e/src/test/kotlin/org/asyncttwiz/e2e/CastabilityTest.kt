package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.TableMarks
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Castability, costs and payment (owner, report KX8GYS, castability spec §6), on two phones: which cards count as castable, what they
 * show, what can be picked given what is picked, and that the player can always finish what is offered. The other direction of each
 * search (nothing castable left out) is the engine's NoFalseDenialFuzzTest.
 */
class CastabilityTest : E2e() {
    private fun deck(vararg cards: Pair<SubmittedDefinition, Int>) = TestDeck("Castability", cards.toList())

    /** A game with Alice holding at least these cards (by name, how many), and Bob at least [bobHolds]; both have kept. */
    private fun start(aliceDeck: TestDeck, aliceHolds: Map<String, Int>, bobDeck: TestDeck = TestDeck.glimmers(), bobHolds: Map<String, Int> = emptyMap(),
        bobStarts: Boolean = false, aliceSettings: PlaySettings? = null, bobSettings: PlaySettings? = null) {
        startGame(aliceDeck, bobDeck, bobStarts = bobStarts, aliceSettings = aliceSettings, bobSettings = bobSettings, deal = { a, b ->
            aliceHolds.all { (name, n) -> hand(a).count { it == name } >= n } && bobHolds.all { (name, n) -> hand(b).count { it == name } >= n }
        })
        bothOpen()
        if (bobStarts) {
            alice.tap("Keep")
            bob.tap("Keep")
            waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        } else bothKeep(bobAway = false)
    }

    /** [player] ends their turn, whatever is left of it, the other player's device doing as it does. */
    private fun endsTurn(player: E2ePlayer) = waitUntil("${player.name}'s turn over") {
        if (player.state.turn.active == player.me) player.playAlong()
        player.state.turn.active != player.me
    }

    /** [player] plays each of [cards] from their hand, one by one, on the server before the next. */
    private fun plays(player: E2ePlayer, vararg cards: String) {
        val other = if (player === alice) bob else alice
        for (card in cards) {
            val before = player.field(card).size
            player.playFromHand(card, this)
            waitUntil("$card on ${player.name}'s battlefield", along = listOf(other)) {
                player.field(card).size == before + 1 && player.inOwnMain() && player.engine { device.session.pending.isEmpty() }
            }
        }
    }

    private fun E2ePlayer.marks(name: String, owner: E2ePlayer = this): Set<String> =
        runCatching { battlefieldCard(name, owner).fetchSemanticsNode().config.getOrElse(TableMarks) { emptySet() } }.getOrDefault(emptySet())

    private fun E2ePlayer.tappedCount(name: String) = field(name).count { state.obj(it)?.tapped == true }

    private fun E2ePlayer.chipShown(text: String) = shows(hasContentDescription("Way $text"))

    /** The Done (or None) button of the target bar, when it can be pressed. */
    private fun E2ePlayer.doneEnabled(label: String = "Done") =
        shows(hasClickAction() and isEnabled() and (hasText(label) or hasAnyDescendant(hasText(label))))

    private fun E2ePlayer.tapDone(label: String = "Done") {
        val done = nodes(hasClickAction() and hasAnyDescendant(hasText(label))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }
        node(SemanticsMatcher("$label button") { it.id == done.id }).performClick()
    }

    private fun E2ePlayer.choosingTargets() = shows(hasContentDescription("Choose targets"))

    private fun E2ePlayer.payment(): String? = nodes(hasContentDescription("Payment: ", substring = true)).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }?.joinToString()

    private fun E2ePlayer.tapCast(label: String = "Cast") {
        val cast = nodes(hasClickAction() and hasAnyDescendant(hasText(label))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }
        node(SemanticsMatcher("$label button") { it.id == cast.id }).performClick()
    }

    /** A tap on a source on the table, under the panel hidden for it. */
    private fun E2ePlayer.onTable(name: String, index: Int = 0) {
        tap("Hide cast panel")
        waitUntil("the panel hidden") { showsControl("Show cast panel") }
        battlefieldCard(name, index = index).performClick()
        tap("Show cast panel")
        waitUntil("the panel back") { showsControl("Hide cast panel") }
    }

    private val rebukeDeck = deck(TestCards.shard to 14, TestCards.rebuke to 6, TestCards.pup to 10, TestCards.drowsy to 10)

    /** Alice has [shards] mana sources out, a Pup and a tapped Drowsy Pup, and raises the Rebuke. */
    private fun rebukeBoard(shards: Int) {
        start(rebukeDeck, mapOf("Ember Shard" to shards, "Ember Rebuke" to 1, "Ash Pup" to 1, "Drowsy Pup" to 1))
        plays(alice, *List(shards) { "Ember Shard" }.toTypedArray(), "Ash Pup", "Drowsy Pup")
        alice.handCard("Ember Rebuke").performClick()
    }

    // ZON-080, ZON-079, TBL-117, CR 601.2f (spec §6.1): enough for both, a tapped and an untapped creature: the chip shows the smallest +
    // {?}, the outline is for the minimum; both are offered; the cheap one taps two sources, the others stay
    @Test fun enoughForBothTheCheapOneTapsOnlyItsSources() {
        rebukeBoard(4)
        waitUntil("the chip with the smallest cost: ${alice.screen()}") { alice.chipShown("Cast {1}{R} + {?}") }
        waitUntil("two sources outlined for the minimum") { alice.nodes(SemanticsMatcher("source") { "source" in it.config.getOrElse(TableMarks) { emptySet() } }).fetchSemanticsNodes().size == 2 }
        alice.node(hasContentDescription("Way Cast {1}{R} + {?}")).performClick()
        waitUntil("the target bar") { alice.choosingTargets() }
        waitUntil("both creatures glow") { "target" in alice.marks("Ash Pup") && "target" in alice.marks("Drowsy Pup") }
        alice.battlefieldCard("Drowsy Pup").performClick()
        waitUntil("the Drowsy Pup exiled, on the server", along = listOf(bob)) {
            alice.field("Drowsy Pup").isEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(2, alice.tappedCount("Ember Shard"), "the right sources tap, the others stay")
    }

    // ZON-080, TBL-117, CR 601.2f (spec §6.1): the same start, the dear one: every source taps
    @Test fun enoughForBothTheDearOneTapsAll() {
        rebukeBoard(4)
        waitUntil("the chip") { alice.chipShown("Cast {1}{R} + {?}") }
        alice.node(hasContentDescription("Way Cast {1}{R} + {?}")).performClick()
        waitUntil("the target bar") { alice.choosingTargets() && "target" in alice.marks("Ash Pup") }
        alice.battlefieldCard("Ash Pup").performClick()
        waitUntil("the Ash Pup exiled, on the server", along = listOf(bob)) {
            alice.field("Ash Pup").isEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(4, alice.tappedCount("Ember Shard"))
    }

    // TBL-118, STK-047, CR 601.2f (spec §6.2, report KX8GYS): exact mana for the cheap one: only it is offered, the other shakes
    @Test fun exactManaForTheCheapOneOnlyItIsOffered() {
        rebukeBoard(2)
        waitUntil("the chip") { alice.chipShown("Cast {1}{R} + {?}") }
        alice.node(hasContentDescription("Way Cast {1}{R} + {?}")).performClick()
        waitUntil("the target bar, only the tapped creature glowing") { alice.choosingTargets() && "target" in alice.marks("Drowsy Pup") }
        assertFalse("target" in alice.marks("Ash Pup"), "the dear one doesn't glow")
        alice.battlefieldCard("Ash Pup").performClick()
        run(skip = java.time.Duration.ofMillis(300))
        assertTrue(alice.choosingTargets(), "still choosing")
        assertEquals(1, alice.field("Ash Pup").size)
        alice.battlefieldCard("Drowsy Pup").performClick()
        waitUntil("the Drowsy Pup exiled", along = listOf(bob)) { alice.field("Drowsy Pup").isEmpty() && alice.inOwnMain() }
        assertEquals(2, alice.tappedCount("Ember Shard"))
    }

    // STK-047, CR 601.2c, 601.2f (spec §6.3, the owner's example): {R}{R} for each creature type among the targets, 6 mana: a 4-type creature
    // can't be picked; with a 2-type one picked another 2-type one with other types can't, 1-type ones can, and one sharing its types can;
    // unselecting opens them again; Done only on a payable selection
    @Test fun theOwnersMultiTargetExampleNarrowsAsTheyArePicked() {
        val bobs = deck(TestCards.quadBeast to 8, TestCards.twoTypes to 8, TestCards.otherTwoTypes to 8, TestCards.sameTwoTypes to 8, TestCards.oneType to 8)
        start(deck(TestCards.geode to 20, TestCards.typeToll to 20), mapOf("Ember Geode" to 3, "Ember Toll" to 1), bobDeck = bobs,
            bobHolds = mapOf("Quad Beast" to 1, "Elf Bear" to 1, "Ox Human" to 1, "Bear Elf" to 1, "Lone Goat" to 1), bobStarts = true)
        plays(bob, "Quad Beast", "Elf Bear", "Ox Human", "Bear Elf", "Lone Goat")
        waitUntil("Alice's turn", along = listOf(bob)) { alice.inOwnMain() }
        plays(alice, "Ember Geode", "Ember Geode", "Ember Geode")
        alice.handCard("Ember Toll").performClick()
        waitUntil("the Toll's chip: ${alice.screen()}") { alice.shows(hasContentDescription("Way Cast", substring = true)) }
        alice.node(hasContentDescription("Way Cast", substring = true)).performClick()
        waitUntil("the target bar") { alice.choosingTargets() && "target" in alice.marks("Elf Bear", bob) }
        assertFalse("target" in alice.marks("Quad Beast", bob), "8 mana")
        alice.battlefieldCard("Quad Beast", bob).performClick()
        run(skip = java.time.Duration.ofMillis(300))
        assertFalse("picked" in alice.marks("Quad Beast", bob), "it shakes")
        alice.battlefieldCard("Elf Bear", bob).performClick()
        waitUntil("the picks worked out again") { "picked" in alice.marks("Elf Bear", bob) && alice.doneEnabled() }
        waitUntil("another two types can't be picked") { "target" !in alice.marks("Ox Human", bob) }
        assertTrue("target" in alice.marks("Lone Goat", bob), "one type more can")
        assertTrue("target" in alice.marks("Bear Elf", bob), "the same two types count once")
        alice.battlefieldCard("Elf Bear", bob).performClick()
        waitUntil("unselected: they open again") { "target" in alice.marks("Ox Human", bob) }
        alice.battlefieldCard("Elf Bear", bob).performClick()
        waitUntil("picked again") { "picked" in alice.marks("Elf Bear", bob) && alice.doneEnabled() }
        alice.battlefieldCard("Lone Goat", bob).performClick()
        waitUntil("three types, still payable") { "picked" in alice.marks("Lone Goat", bob) && alice.doneEnabled() }
        alice.tapDone()
        waitUntil("the Toll resolved", along = listOf(bob)) {
            alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } && alice.state.obj(alice.field("Lone Goat", bob.me).single())?.tapped == true
        }
        assertEquals(3, alice.tappedCount("Ember Geode"), "6 mana")
    }

    // STK-047, CR 601.2f (spec §6.4): {5}, {1} less for each target, three sources: one target doesn't pay: Done greyed until the second
    @Test fun aCostFallingWithMoreTargetsKeepsDoneGreyUntilItPays() {
        start(deck(TestCards.shard to 16, TestCards.hush to 8, TestCards.pup to 8, TestCards.ashHound to 8), mapOf("Ember Shard" to 3, "Ember Hush" to 1, "Ash Pup" to 1, "Ash Hound" to 1))
        plays(alice, "Ember Shard", "Ember Shard", "Ember Shard", "Ash Pup", "Ash Hound")
        alice.handCard("Ember Hush").performClick()
        waitUntil("the Hush's chip, the smallest it can pay: ${alice.screen()}") { alice.chipShown("Cast {3} + {?}") }
        alice.node(hasContentDescription("Way Cast {3} + {?}")).performClick()
        waitUntil("the target bar, both offered") { alice.choosingTargets() && "target" in alice.marks("Ash Pup") && "target" in alice.marks("Ash Hound") }
        assertFalse(alice.doneEnabled("None"), "no targets cost {5}")
        alice.battlefieldCard("Ash Pup").performClick()
        waitUntil("picked") { "picked" in alice.marks("Ash Pup") }
        run(skip = java.time.Duration.ofMillis(200))
        assertFalse(alice.doneEnabled(), "one target costs {4}: ${alice.screen()}")
        alice.battlefieldCard("Ash Hound").performClick()
        waitUntil("two targets cost {3}") { "picked" in alice.marks("Ash Hound") && alice.doneEnabled() }
        alice.tapDone()
        waitUntil("the Hush resolved", along = listOf(bob)) {
            alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } && alice.field("Ash Pup").all { alice.state.obj(it)?.tapped == true }
        }
        assertEquals(3, alice.tappedCount("Ember Shard"))
    }

    // STK-047, CR 601.2f (spec §6.5): a rising cost per target (strive's template): with 3 mana a second target can be picked, a third can't
    @Test fun aRisingCostPerTargetStopsWhatCanBePicked() {
        start(deck(TestCards.shard to 12, TestCards.striving to 8, TestCards.pup to 7, TestCards.ashHound to 7, TestCards.drowsy to 6),
            mapOf("Ember Shard" to 3, "Ember Striving" to 1, "Ash Pup" to 1, "Ash Hound" to 1, "Drowsy Pup" to 1))
        plays(alice, "Ember Shard", "Ember Shard", "Ember Shard", "Ash Pup", "Ash Hound", "Drowsy Pup")
        alice.handCard("Ember Striving").performClick()
        waitUntil("the chip: ${alice.screen()}") { alice.chipShown("Cast {R} + {?}") }
        alice.node(hasContentDescription("Way Cast {R} + {?}")).performClick()
        waitUntil("the target bar") { alice.choosingTargets() && "target" in alice.marks("Ash Pup") }
        alice.battlefieldCard("Ash Pup").performClick()
        waitUntil("one picked") { "picked" in alice.marks("Ash Pup") && alice.doneEnabled() }
        alice.battlefieldCard("Ash Hound").performClick()
        waitUntil("two picked, the third out") { "picked" in alice.marks("Ash Hound") && alice.doneEnabled() && "target" !in alice.marks("Drowsy Pup") }
        alice.tapDone()
        waitUntil("the Striving resolved", along = listOf(bob)) { alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } && alice.tappedCount("Ember Shard") == 3 }
    }

    // STK-047, CR 601.2f (spec §6.5): "spells that target this cost more" on the other side: with one source it isn't offered
    @Test fun aCreatureThatTaxesTargetingIsNotOfferedWithoutTheMana() {
        start(deck(TestCards.shard to 20, TestCards.spark to 20), mapOf("Ember Shard" to 1, "Storm Spark" to 1),
            bobDeck = deck(TestCards.wardedPup to 20, TestCards.pup to 20), bobHolds = mapOf("Warded Pup" to 1, "Ash Pup" to 1), bobStarts = true)
        plays(bob, "Warded Pup", "Ash Pup")
        waitUntil("Alice's turn", along = listOf(bob)) { alice.inOwnMain() }
        plays(alice, "Ember Shard")
        alice.playFromHand("Storm Spark", this)
        waitUntil("the Spark's target bar, the other creature glowing") { alice.choosingTargets() && "target" in alice.marks("Ash Pup", bob) }
        assertFalse("target" in alice.marks("Warded Pup", bob), "{2} more can't be paid")
        alice.battlefieldCard("Ash Pup", bob).performClick()
        waitUntil("the Pup dead", along = listOf(bob)) { alice.field("Ash Pup", bob.me).isEmpty() && alice.inOwnMain() }
    }

    // STK-023, CR 601.2b, 601.2c, 601.2f (spec §6.6): X and a reduction read from the target: X's most assumes the cheapest target (3 sources,
    // {X}{R} on a tapped creature: X = 2), and that X then leaves only the cheap target
    @Test fun xComesBeforeTargetsAndIsNeverADeadEnd() {
        start(deck(TestCards.shard to 16, TestCards.cleave to 8, TestCards.pup to 8, TestCards.drowsy to 8), mapOf("Ember Shard" to 3, "Ember Cleave" to 1, "Ash Pup" to 1, "Drowsy Pup" to 1))
        plays(alice, "Ember Shard", "Ember Shard", "Ember Shard", "Ash Pup", "Drowsy Pup")
        alice.handCard("Ember Cleave").performClick()
        waitUntil("the chip keeps {X}: ${alice.screen()}") { alice.chipShown("Cast {X}{R} + {?}") }
        alice.node(hasContentDescription("Way Cast {X}{R} + {?}")).performClick()
        waitUntil("X asked: ${alice.screen()}") { alice.shows(hasContentDescription("Value 0")) }
        alice.tap("One more")
        waitUntil("X = 1") { alice.shows(hasContentDescription("Value 1")) }
        alice.tap("One more")
        waitUntil("X = 2") { alice.shows(hasContentDescription("Value 2")) }
        assertTrue(alice.shows(hasContentDescription("One more") and isNotEnabled()), "2 is the most: ${alice.screen()}")
        alice.tapCast()
        waitUntil("the target bar, only the tapped creature") { alice.choosingTargets() && "target" in alice.marks("Drowsy Pup") }
        assertFalse("target" in alice.marks("Ash Pup"), "X = 2 on it costs {5}{R}")
        alice.battlefieldCard("Drowsy Pup").performClick()
        waitUntil("the Drowsy Pup dead", along = listOf(bob)) { alice.field("Drowsy Pup").isEmpty() && alice.inOwnMain() }
        assertEquals(3, alice.tappedCount("Ember Shard"))
    }

    // TBL-117, TBL-122, STK-024 (spec §1.2, §6.7): floated mana counts and is spent first; the check doesn't look for "tap this first":
    // tapping your Sprite makes it the tapped target and the card then glows and can be cast
    @Test fun tappingYourCreatureFirstMakesItTheTappedTargetAndTheCardThenCasts() {
        start(deck(TestCards.shard to 14, TestCards.rebuke to 13, TestCards.sprite to 13), mapOf("Ember Shard" to 1, "Ember Rebuke" to 1, "Ember Sprite" to 1))
        plays(alice, "Ember Shard", "Ember Sprite")
        alice.handCard("Ember Rebuke").performClick()
        run(skip = java.time.Duration.ofMillis(300))
        assertEquals(null, alice.raisedInHand(), "two mana and no tapped creature: it isn't raised: ${alice.screen()}")
        alice.battlefieldCard("Ember Sprite").performClick()
        waitUntil("the Sprite tapped, its mana floating") { alice.state.player(alice.me).manaPool.size == 1 && alice.tappedCount("Ember Sprite") == 1 }
        alice.handCard("Ember Rebuke").performClick()
        waitUntil("raised, with its chip: ${alice.screen()}") { alice.chipShown("Cast {1}{R}") }
        alice.node(hasContentDescription("Way Cast {1}{R}")).performClick()
        waitUntil("the target bar") { alice.choosingTargets() && "target" in alice.marks("Ember Sprite") }
        alice.battlefieldCard("Ember Sprite").performClick()
        waitUntil("the Sprite exiled", along = listOf(bob)) { alice.field("Ember Sprite").isEmpty() && alice.inOwnMain() }
        assertEquals(1, alice.tappedCount("Ember Shard"), "pool first, then the Shard")
        assertTrue(alice.state.player(alice.me).manaPool.isEmpty())
    }

    /** Activates the Engine ({1}: you gain 1 life) and sees life rise; [pay] picks sources with Autotap off, null leaves it to Autotap. */
    private fun activatesEngine(pay: (() -> Unit)?, check: () -> Boolean) {
        val life = alice.state.player(alice.me).life
        alice.battlefieldCard("Ember Engine").performClick()
        if (pay != null) {
            waitUntil("the Engine's panel: ${alice.screen()}") { alice.showsControl("Hide cast panel") }
            pay()
            waitUntil("paid: ${alice.screen()}") { alice.payment()?.startsWith("Payment: Paid with") == true }
            alice.tapCast("Activate")
        }
        waitUntil("the Engine's ability on the Stack and resolved: ${alice.screen()}", along = listOf(bob)) {
            alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } && alice.state.stack.isEmpty() && check() &&
                alice.state.player(alice.me).life >= 0 && alice.state.player(alice.me).life != life - 100
        }
    }

    // STK-059, CR 605.3a (spec §6.8): with Autotap off each mana kind is picked by hand at a payment: an Ancient Tomb style source, one removing a
    // counter, and a sacrifice altar with the creature it takes (a chip in the panel); mana left over floats
    @Test fun everyManaKindPaysByHandAtAPayment() {
        start(deck(TestCards.engineRock to 7, TestCards.tomb to 7, TestCards.chargeStone to 7, TestCards.bloodAltar to 7, TestCards.pup to 12),
            mapOf("Ember Engine" to 1, "Tomb Stone" to 1, "Charge Stone" to 1, "Blood Altar" to 1, "Ash Pup" to 1),
            aliceSettings = PlaySettings(autotap = false))
        plays(alice, "Ember Engine", "Tomb Stone", "Charge Stone", "Blood Altar", "Ash Pup")
        val start = alice.state.player(alice.me).life
        activatesEngine({ alice.onTable("Tomb Stone") }) { alice.tappedCount("Tomb Stone") == 1 && alice.state.player(alice.me).manaPool.size == 1 }
        activatesEngine({ alice.onTable("Charge Stone") }) { alice.tappedCount("Charge Stone") == 1 }
        activatesEngine({ alice.node(hasContentDescription("Mana source Blood Altar: Ash Pup", substring = true)).performClick() }) { alice.field("Ash Pup").isEmpty() }
        assertEquals(start - 2 + 3, alice.state.player(alice.me).life, "the Tomb's damage, the life gained")
    }

    // STK-059, CR 605.3a, 118.3 (spec §6.8): a pain source's coloured ability picked by hand: its damage comes with the mana
    @Test fun aPainSourcesColouredAbilityPaysByHand() {
        start(deck(TestCards.painStone to 20, TestCards.redFlash to 20), mapOf("Pain Stone" to 1, "Red Flash" to 1), aliceSettings = PlaySettings(autotap = false))
        plays(alice, "Pain Stone")
        alice.playFromHand("Red Flash", this)
        waitUntil("the panel: ${alice.screen()}") { alice.shows(hasContentDescription("Not paid: Tap sources on the table to pay {R}.")) }
        alice.onTable("Pain Stone")
        waitUntil("paid") { alice.payment() == "Payment: Paid with Pain Stone" }
        alice.tapCast()
        waitUntil("the Flash resolved", along = listOf(bob)) { alice.state.stack.isEmpty() && alice.inOwnMain() && alice.state.player(alice.me).life == 19 }
    }

    // STK-059, CR 605.1b, CR 106.1 (spec §6.8): a Signet fed by a Shard, picked in either order; a two-colour filter; a colour choice; triggered mana
    @Test fun aSignetChainAndTheColourKindsPayByAutotapAndByHand() {
        start(deck(TestCards.signet to 8, TestCards.shard to 8, TestCards.filter to 8, TestCards.prism to 8, TestCards.blueNote to 8),
            mapOf("Ember Signet" to 1, "Ember Shard" to 2, "Filter Stone" to 1, "Ember Prism" to 1, "Blue Note" to 2))
        plays(alice, "Ember Signet", "Ember Shard")
        alice.playFromHand("Blue Note", this)
        waitUntil("the Note cast through the chain by Autotap", along = listOf(bob)) { alice.tappedCount("Ember Signet") == 1 && alice.tappedCount("Ember Shard") == 1 && alice.inOwnMain() && alice.state.stack.isEmpty() }
        assertEquals(1, alice.state.player(alice.me).manaPool.size, "the Signet's {R} left over")
        plays(alice, "Ember Shard", "Filter Stone")
        alice.playFromHand("Blue Note", this)
        waitUntil("the Note cast through the filter by Autotap", along = listOf(bob)) { alice.tappedCount("Filter Stone") == 1 && alice.inOwnMain() && alice.state.stack.isEmpty() }
    }

    @Test fun triggeredManaPays() {
        start(deck(TestCards.mountain to 14, TestCards.boost to 13, TestCards.twoPence to 13), mapOf("Mountain" to 1, "Mountain Hymn" to 1, "Two Pence" to 1))
        plays(alice, "Mountain", "Mountain Hymn")
        alice.playFromHand("Two Pence", this)
        waitUntil("{2} from one Mountain", along = listOf(bob)) { alice.tappedCount("Mountain") == 1 && alice.inOwnMain() && alice.state.stack.isEmpty() }
        assertTrue(alice.state.player(alice.me).manaPool.isEmpty())
    }

    // STK-059, STK-049, CR 605.3a, 118.12 (spec §4, §6.8): a payment during resolution takes every mana ability: Autotap's source taken out, the panel
    // fills in an Ancient Tomb style one, whose extra mana floats; then the pool pays; then a Signet picked is fed by a Shard
    @Test fun aPaymentDuringResolutionTakesEveryManaAbility() {
        start(deck(TestCards.tollkeeper to 14, TestCards.tomb to 8, TestCards.signet to 8, TestCards.shard to 10),
            mapOf("Ember Tollkeeper" to 3, "Tomb Stone" to 1, "Ember Signet" to 1, "Ember Shard" to 1))
        plays(alice, "Ember Shard", "Tomb Stone", "Ember Signet")
        fun tollAsked() = waitUntil("Alice asked to pay {1}: ${alice.screen()}", along = listOf(bob)) {
            alice.state.pending?.decision is arcanum.engine.decision.Decision.May && alice.shows(hasText("Pay {1}"))
        }
        alice.playFromHand("Ember Tollkeeper", this)
        tollAsked()
        assertTrue(alice.shows(hasContentDescription("Mana source Ember Shard", substring = true) and hasContentDescription(", tapped", substring = true)), "Autotap's Shard: ${alice.screen()}")
        alice.node(hasContentDescription("Mana source Ember Shard", substring = true)).performClick()
        waitUntil("the Shard out, the Tomb filled in: ${alice.screen()}") {
            alice.shows(hasContentDescription("Mana source Tomb Stone", substring = true) and hasContentDescription(", tapped", substring = true))
        }
        alice.text("Pay").performClick()
        waitUntil("the Tomb paid, its other mana floats", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.tappedCount("Tomb Stone") == 1 && alice.state.player(alice.me).manaPool.size == 1
        }
        assertEquals(18, alice.state.player(alice.me).life)
        alice.playFromHand("Ember Tollkeeper", this)
        tollAsked()
        alice.text("Pay").performClick()
        waitUntil("the pool paid", along = listOf(bob)) { alice.state.stack.isEmpty() && alice.state.pending == null && alice.state.player(alice.me).manaPool.isEmpty() }
        assertEquals(0, alice.tappedCount("Ember Shard"), "the pool first")
        alice.playFromHand("Ember Tollkeeper", this)
        tollAsked()
        alice.node(hasContentDescription("Mana source Ember Signet", substring = true)).performClick()
        waitUntil("the Signet picked, the Shard feeding it: ${alice.screen()}") {
            alice.shows(hasContentDescription("Mana source Ember Signet", substring = true) and hasContentDescription(", tapped", substring = true)) &&
                alice.shows(hasContentDescription("Mana source Ember Shard", substring = true) and hasContentDescription(", tapped", substring = true))
        }
        alice.text("Pay").performClick()
        waitUntil("the chain paid, one mana left over", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.tappedCount("Ember Signet") == 1 && alice.state.player(alice.me).manaPool.size == 1
        }
    }

    // STK-024 (spec §4, §6.9): partial override: a picked source stays and Autotap fills in the rest; one taken out isn't used again; picking
    // everything overrides completely
    @Test fun aPickedSourceStaysAndAutotapFillsInTheRest() {
        start(deck(TestCards.brood to 8, TestCards.shard to 11, TestCards.emberStone to 11, TestCards.sprite to 10), mapOf("Ember Brood" to 1, "Ember Shard" to 1, "Ember Stone" to 1, "Ember Sprite" to 1))
        plays(alice, "Ember Shard", "Ember Stone", "Ember Sprite")
        alice.playFromHand("Ember Brood", this)
        waitUntil("the Brood's panel") { alice.shows(hasContentDescription("Extra Offspring {2}")) }
        alice.node(hasContentDescription("Extra Offspring {2}")).performClick()
        waitUntil("Autotap's two sources chosen: ${alice.payment()}") { alice.payment()?.startsWith("Payment: Paid with ") == true }
        val first = alice.payment()!!.removePrefix("Payment: Paid with ").split(", ")
        assertEquals(setOf("Ember Shard", "Ember Stone"), first.toSet(), "lands and artifacts before creatures")
        alice.onTable("Ember Sprite")
        waitUntil("the Sprite picked, one other filled in: ${alice.payment()}") { alice.payment()?.contains("Ember Sprite") == true && alice.payment()!!.split(", ").size == 2 }
        val taken = if ("Ember Shard" in alice.payment()!!) "Ember Shard" else "Ember Stone"
        val out = if (taken == "Ember Shard") "Ember Stone" else "Ember Shard"
        alice.onTable(taken)
        waitUntil("the other fills in: ${alice.payment()}") { alice.payment()?.contains(out) == true && alice.payment()?.contains("Ember Sprite") == true }
        // Taking the rest out: greyed, says what is missing
        alice.onTable(out)
        waitUntil("Cast greyed with what is missing: ${alice.screen()}") { alice.shows(hasContentDescription("Not paid: Not enough to pay {2}", substring = true)) }
        // A complete override: picking everything
        alice.onTable(out)
        waitUntil("paid by the two picks: ${alice.payment()}") { alice.payment()?.contains(out) == true && alice.payment()?.contains("Ember Sprite") == true && !alice.shows(hasContentDescription("Not paid: ", substring = true)) }
        alice.tapCast()
        waitUntil("the Brood and its copy", along = listOf(bob)) { alice.field("Ember Brood").size == 2 && alice.inOwnMain() }
        assertEquals(1, alice.tappedCount(out))
        assertEquals(1, alice.tappedCount("Ember Sprite"))
    }

    // STK-024 (spec §4, §6.9): when the cost changes the picks stay and Autotap's are worked out again (kicker turned on in the panel)
    @Test fun whenTheCostChangesPicksStayAndAutotapsAreWorkedOutAgain() {
        start(deck(TestCards.clan to 10, TestCards.shard to 10, TestCards.emberStone to 10, TestCards.sprite to 10), mapOf("Ember Clan" to 1, "Ember Shard" to 1, "Ember Stone" to 1, "Ember Sprite" to 1))
        plays(alice, "Ember Shard", "Ember Stone", "Ember Sprite")
        alice.playFromHand("Ember Clan", this)
        waitUntil("the Clan's panel: ${alice.screen()}") { alice.shows(hasContentDescription("Extra Kicker {1}")) }
        waitUntil("one source: ${alice.payment()}") { alice.payment()?.startsWith("Payment: Paid with ") == true }
        alice.onTable("Ember Sprite")
        waitUntil("the Sprite picked: ${alice.payment()}") { alice.payment() == "Payment: Paid with Ember Sprite" }
        alice.node(hasContentDescription("Extra Kicker {1}")).performClick()
        waitUntil("{2}: the pick stays, Autotap adds one: ${alice.payment()}") { alice.payment()?.contains("Ember Sprite") == true && alice.payment()!!.split(", ").size == 2 }
    }

    // STK-024 (spec §6.9): with Autotap off the panel opens after the targets, and only what you pick pays
    // TBL-117, STK-024 (spec §4, §6.14): a cast asking only targets goes out at once after its last target: floated mana pays it, not the
    // sources Autotap would take; with Autotap off the panel opens after the targets
    @Test fun aCastAskingOnlyTargetsGoesOutAtOnceAndFloatedManaPaysIt() {
        start(deck(TestCards.spark to 13, TestCards.shard to 13, TestCards.mountain to 14), mapOf("Storm Spark" to 1, "Ember Shard" to 1, "Mountain" to 1))
        plays(alice, "Mountain", "Ember Shard")
        alice.battlefieldCard("Ember Shard").performClick()
        waitUntil("the Shard's mana floats") { alice.state.player(alice.me).manaPool.size == 1 }
        alice.playFromHand("Storm Spark", this)
        waitUntil("the target bar") { alice.choosingTargets() }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Spark paid and resolved", along = listOf(bob)) { alice.state.stack.isEmpty() && alice.inOwnMain() && alice.state.player(bob.me).life == 17 }
        assertEquals(0, alice.tappedCount("Mountain"), "floated mana paid it instead of the Mountain")
    }

    @Test fun withAutotapOffThePanelOpensAfterTheTargets() {
        start(deck(TestCards.spark to 20, TestCards.mountain to 20), mapOf("Storm Spark" to 1, "Mountain" to 1), aliceSettings = PlaySettings(autotap = false))
        plays(alice, "Mountain")
        alice.playFromHand("Storm Spark", this)
        waitUntil("the target bar: ${alice.screen()}") { alice.choosingTargets() }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the panel after the targets: ${alice.screen()}") { alice.shows(hasContentDescription("Not paid: Tap sources on the table to pay {R}.")) }
        assertTrue(alice.state.stack.isEmpty(), "nothing sent")
        alice.onTable("Mountain")
        waitUntil("paid") { alice.payment() == "Payment: Paid with Mountain" }
        alice.tapCast()
        waitUntil("resolved", along = listOf(bob)) { alice.state.player(bob.me).life == 17 && alice.inOwnMain() }
    }

    // STK-024 (spec §6.10): Autotap prefers damage-free sources; a pain source only when needed
    @Test fun autotapPrefersDamageFreeSourcesAndAPainSourceOnlyWhenNeeded() {
        start(deck(TestCards.painStone to 14, TestCards.shard to 13, TestCards.engineRock to 13), mapOf("Pain Stone" to 1, "Ember Shard" to 1, "Ember Engine" to 1))
        plays(alice, "Pain Stone", "Ember Shard", "Ember Engine")
        val life = alice.state.player(alice.me).life
        activatesEngine(null) { alice.tappedCount("Ember Shard") == 1 && alice.tappedCount("Pain Stone") == 0 }
        assertEquals(life + 1, alice.state.player(alice.me).life, "no damage")
        activatesEngine(null) { alice.tappedCount("Pain Stone") == 1 }
    }

    // STK-024, STK-059, TRN-065, HIS-084, CR 605.3a, 704.3, 704.5a (spec §5, §6.10): a lethal source only when it's the only way: the cast finishes, the
    // game ends when priority would next come, and Undo reaches back from there
    @Test fun aLethalSourceOnlyWhenItIsTheOnlyWayThenUndoReachesBack() {
        start(deck(TestCards.doom to 20, TestCards.spark to 20), mapOf("Doom Stone" to 1, "Storm Spark" to 1))
        plays(alice, "Doom Stone")
        alice.playFromHand("Storm Spark", this)
        waitUntil("the target bar") { alice.choosingTargets() }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the game over on Alice's table: ${alice.screen()}", along = listOf(bob)) { alice.showsText("You lost") }
        assertTrue(alice.state.player(alice.me).life <= 0)
        alice.tap("Undo")
        waitUntil("Undo back from the ending: ${alice.screen()}", along = listOf(bob)) {
            !alice.showsText("You lost") && alice.state.player(alice.me).life == 20
        }
    }

    // STK-024, TBL-117 (spec §1.3, §6.11): a way exists but Autotap finds none: the panel opens and you pay by hand
    @Test fun aWayAutotapDoesNotFindOpensThePanel() {
        start(deck(TestCards.shard to 14, TestCards.xStone to 13, TestCards.blueNote to 13), mapOf("Ember Shard" to 1, "Measure Stone" to 1, "Blue Note" to 1))
        plays(alice, "Ember Shard", "Measure Stone")
        alice.playFromHand("Blue Note", this)
        waitUntil("the panel: ${alice.screen()}") { alice.shows(hasContentDescription("Not paid: ", substring = true)) }
        alice.onTable("Measure Stone")
        waitUntil("the Stone picked") { alice.payment()?.contains("Measure Stone") == true }
        alice.onTable("Ember Shard")
        waitUntil("paid by hand: ${alice.screen()}") { !alice.shows(hasContentDescription("Not paid: ", substring = true)) }
        alice.tapCast()
        waitUntil("the Note resolved", along = listOf(bob)) { alice.tappedCount("Measure Stone") == 1 && alice.tappedCount("Ember Shard") == 1 && alice.inOwnMain() && alice.state.stack.isEmpty() }
    }

    // STK-059, CR 601.2g, 601.2h (spec §1, §6.12): a land tapped for mana is then sacrificed as the cost; a creature the cost taps doesn't also tap for mana
    @Test fun manaChoicesInteractWithTheOtherCosts() {
        start(deck(TestCards.mountain to 12, TestCards.landEater to 10, TestCards.sprite to 8, TestCards.tapPact to 10), mapOf("Mountain" to 1, "Land Eater" to 1, "Ember Sprite" to 2, "Tap Pact" to 1))
        plays(alice, "Mountain")
        alice.playFromHand("Land Eater", this)
        waitUntil("the Land Eater's choice or out: ${alice.screen()}", along = listOf(bob)) {
            alice.field("Mountain").isEmpty() && alice.state.stack.isEmpty() && alice.inOwnMain()
        }
    }

    // TBL-122, STK-025, CR 605.3a (spec §6.12): during a cast non-mana abilities are refused: they shake, nothing happens
    @Test fun nonManaAbilitiesAreRefusedDuringACast() {
        start(deck(TestCards.engineRock to 10, TestCards.brood to 10, TestCards.shard to 10, TestCards.emberStone to 10), mapOf("Ember Engine" to 1, "Ember Brood" to 1, "Ember Shard" to 1, "Ember Stone" to 1))
        plays(alice, "Ember Shard", "Ember Stone", "Ember Engine")
        alice.playFromHand("Ember Brood", this)
        waitUntil("the Brood's panel") { alice.shows(hasContentDescription("Extra Offspring {2}")) }
        val life = alice.state.player(alice.me).life
        alice.tap("Hide cast panel")
        waitUntil("the panel hidden") { alice.showsControl("Show cast panel") }
        alice.battlefieldCard("Ember Engine").performClick()
        run(skip = java.time.Duration.ofMillis(300))
        // A row of its menu, if it shows one, does nothing either
        if (alice.showsText("{1}: You gain 1 life.", substring = true)) alice.text("{1}: You gain 1 life.", substring = true).performClick()
        run(skip = java.time.Duration.ofMillis(300))
        assertEquals(life, alice.state.player(alice.me).life, "nothing activated")
        assertTrue(alice.state.stack.isEmpty(), "nothing on the Stack")
        assertTrue(alice.engine { device.session.pending.isEmpty() }, "nothing sent")
        assertTrue(alice.showsControl("Show cast panel"), "the cast stays open: ${alice.screen()}")
    }

    // TRN-070, TBL-117 (spec §6.13): Quick mode doesn't pass your window while a card is castable only through a pain source
    @Test fun quickModeStopsForACardCastableOnlyThroughAPainSource() {
        start(deck(TestCards.painStone to 20, TestCards.redFlash to 20), mapOf("Pain Stone" to 1, "Red Flash" to 1), bobDeck = deck(TestCards.mountain to 40))
        plays(alice, "Pain Stone")
        endsTurn(alice)
        waitUntil("Bob's turn: Alice stops at a window: ${alice.screen()}", along = listOf(bob)) {
            alice.state.turn.active == bob.me && alice.state.priority.holder == alice.me && alice.current() && alice.settled(this)
        }
        val step = alice.state.turn.step
        repeat(3) { run(skip = java.time.Duration.ofMillis(500)) }
        assertEquals(step, alice.state.turn.step, "her device didn't pass it")
    }

    /** Bob's sorcery on the Stack in his Main 1, Alice holding an instant castable only after she floats: a tapped creature is what makes it cheap
     * enough, and tapping her Sprite is what would. Her device passes or stops by [mode]. */
    private fun bobCastsWhileAliceCouldOnlyAfterFloating(mode: ResponseMode) {
        startGame(deck(TestCards.sprite to 20, TestCards.tappedFlash to 20), deck(TestCards.mountain to 20, TestCards.study to 20), aliceSettings = PlaySettings(mode = mode),
            deal = { a, b -> "Ember Sprite" in hand(a) && "Tapped Flash" in hand(a) && "Ember Study" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        // In Control her own upkeep and draw are hers to pass
        waitUntil("Alice's Main 1", along = listOf(bob)) { if (!alice.inOwnMain() && mode == ResponseMode.FullControl) alice.playAlong(); alice.inOwnMain() }
        alice.playFromHand("Ember Sprite", this)
        waitUntil("the Sprite on her battlefield", along = listOf(bob)) {
            if (mode == ResponseMode.FullControl && alice.state.stack.isNotEmpty()) alice.playAlong()
            alice.field("Ember Sprite").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        endsTurn(alice)
        if (mode == ResponseMode.FullControl) return
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Ember Study", this)
    }

    // Spec §9 (owner's decision): a spell castable only after floating first doesn't stop Quick mode: the spell resolves without Alice
    @Test fun quickPassesAWindowWhereASpellIsCastableOnlyAfterFloating() {
        bobCastsWhileAliceCouldOnlyAfterFloating(ResponseMode.AutoSkip)
        waitUntil("Bob's Study resolved, Alice's device passing") { bob.inOwnMain() && bob.state.stack.isEmpty() && bob.state.player(bob.me).library.size < 32 }
    }

    // Spec §9: Normal stops at the other player's spell whatever she can cast then (reported to the owner: what the spec asked to check)
    @Test fun normalStopsThereAndTheFlashCanBeCastAfterFloating() {
        bobCastsWhileAliceCouldOnlyAfterFloating(ResponseMode.Normal)
        waitUntil("Alice stops at Bob's spell: ${alice.screen()}") { alice.state.stack.isNotEmpty() && alice.state.priority.holder == alice.me && alice.current() }
        alice.battlefieldCard("Ember Sprite").performClick()
        waitUntil("her Sprite tapped, the mana floating: ${alice.screen()}") { alice.tappedCount("Ember Sprite") == 1 && alice.state.player(alice.me).manaPool.size == 1 }
        alice.playFromHand("Tapped Flash", this)
        waitUntil("the Flash's target bar") { alice.choosingTargets() }
    }

    // Spec §9: Control stops at every window of Bob's turn, so she can float there
    @Test fun controlStopsThere() {
        bobCastsWhileAliceCouldOnlyAfterFloating(ResponseMode.FullControl)
        waitUntil("Alice stops in Bob's turn: ${alice.screen()}") { alice.state.turn.active == bob.me && alice.state.priority.holder == alice.me && alice.current() }
        val step = alice.state.turn.step
        repeat(3) { run(skip = java.time.Duration.ofMillis(500)) }
        assertEquals(step, alice.state.turn.step, "she passes herself")
    }
}
