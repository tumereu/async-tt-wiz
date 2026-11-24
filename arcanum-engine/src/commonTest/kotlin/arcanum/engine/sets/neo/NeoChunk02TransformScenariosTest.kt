package arcanum.engine.sets.neo

import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chunk 2 of NEO: transforming Sagas (CR 714) whose chapters and back faces give the engine something new. */
class NeoChunk02TransformScenariosTest {
    private val sagaReminder = "(As this Saga enters and after your draw step, add a lore counter.)"
    private val chapterThree = "III — Exile this Saga, then return it to the battlefield transformed under your control."
    private fun saga(name: String, cost: String, chapters: String, back: String, backType: String, backText: String, pt: String) =
        Cards.inventFaces("Transform",
            face(name, cost, "Enchantment — Saga", "$sagaReminder\n$chapters\n$chapterThree", null, null, null),
            face(back, null, backType, backText, pt, null, "B"))

    private val bear = Cards.invent("Neo Saga Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Neo Saga Cub", "{G}", "Creature — Bear", "", "1/1")
    private val ogre = Cards.invent("Neo Saga Ogre", "{4}{R}", "Creature — Ogre", "", "5/5")
    private val quickTwo = Cards.invent("Neo Twin Quip", "{1}{U}", "Instant", "Draw a card.")
    private val quickOne = Cards.invent("Neo Lone Quip", "{U}", "Instant", "Draw a card.")
    private val surge = Cards.invent("Neo Saga Surge", "{X}{R}", "Sorcery", "Neo Saga Surge deals X damage to any target.")
    private val zap = Cards.invent("Neo Saga Zap", "{R}", "Instant", "Neo Saga Zap deals 1 damage to any target.")
    private val doom = Cards.invent("Neo Saga Doom", "{1}{B}", "Instant", "Destroy target creature.")

    private val fable = saga("Fable of the Broken Mirror", "{2}{R}", """
        I — Create a 2/2 red Goblin Shaman creature token with "Whenever this token attacks, create a Treasure token."
        II — You may discard up to two cards. If you do, draw that many cards.
    """.trimIndent(), "Reflection of the Mirror", "Enchantment Creature — Goblin Shaman", "Haste", "2/2")
    private val vessel = Cards.invent("Neo Vessel of Hunger", "{1}{B}{R}", "Enchantment Creature — Ogre Shaman", """
        Trample
        Whenever this creature deals damage, put a +1/+1 counter on it.
        Whenever this creature deals damage to a player, if it has dealt 10 or more damage to that player this turn, they lose the game.
    """.trimIndent(), "3/3")
    private val moonfolk = Cards.invent("Neo Breakthrough Moonfolk", "{3}{U}", "Enchantment Creature — Moonfolk", """
        Flying
        Whenever you cast a spell, your opponents can't cast spells with the same mana value as that spell until your next turn.
    """.trimIndent(), "3/3")
    private val etching = Cards.invent("Neo Etching Shaman", "{R}", "Enchantment Creature — Human Shaman", """
        Haste
        If a creature dealt damage this turn by a source you controlled would die, exile it instead.
    """.trimIndent(), "2/2")
    private val toshiro = saga("Life of the Ronin Umezawa", "{1}{B}", """
        I, II — Choose one —
        • Target creature gets +2/+2 until end of turn.
        • Target creature gets -1/-1 until end of turn.
        • You gain 2 life.
    """.trimIndent(), "Memory of the Ronin", "Enchantment Creature — Human Samurai", "", "2/3")
    private val konda = saga("The Fall of Lord Kondai", "{2}{W}", """
        I — Exile target creature an opponent controls with mana value 4 or greater.
        II — Each player gains control of all permanents they own.
    """.trimIndent(), "Fragment of Kondai", "Enchantment Creature — Human Noble", "Defender", "1/3")
    private val manifest = Cards.invent("Neo Manifest Dragon", "{4}{G}{G}", "Enchantment Creature — Dragon Spirit", """
        Flying, trample
        Whenever this creature attacks, defending player chooses a nonland card in your graveyard. Return that card to your hand. This creature gets +X/+0 until end of turn, where X is the mana value of that card.
    """.trimIndent(), "6/6")
    private val longReach = saga("The Long Arm of Night", "{3}{B}", """
        I, II — Each opponent sacrifices a creature of their choice unless they discard a card.
    """.trimIndent(), "Animus of the Long Arm", "Enchantment Creature — Spirit", """
        Menace
        Whenever this creature attacks, it gets +X/+0 until end of turn, where X is the number of creature cards in defending player's graveyard.
    """.trimIndent(), "0/4")
    private val tribute = saga("Tribute to Horoba", "{1}{B}", """
        I, II — Each opponent creates a 1/1 black Rat Rogue creature token.
    """.trimIndent(), "Echo of Horoba", "Enchantment Creature — Spirit", "Flying, haste", "3/3")
    private val ratTaker = Cards.invent("Neo Rat Warden", "{2}{B}", "Creature — Spirit", "When this creature enters, gain control of all Rat tokens.", "3/3")

    /** Alice attacks [defender] with [attacker], nobody blocks, and combat damage is dealt (triggers resolved). */
    private fun Scenario.attackUnblocked(attacker: String, defender: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(attacker, defender = defender)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
    }

    // CR 608.2c: "that many" is the number of cards discarded; CR 701.9a
    @Test
    fun fableChapterTwoDrawsAsManyAsWereDiscarded() = scenario {
        alice.hand(cub, bear, ogre)
        loreChapter(fable, 2, "Island", "Island", "Island")
        resolveAll()
        alice.chooses(cub, ogre)
        resolveAll()
        expect {
            alice.graveyard has cub
            alice.graveyard has ogre
            alice.hand has bear
            alice.hand.size shouldBe 3 // the bear and the two drawn
        }
    }

    // CR 603.4 (an intervening "if" checked as it triggers and resolves), 104.3e; only this creature's damage to that player counts (CR 120.1)
    @Test
    fun vesselMakesThePlayerItDealtTenDamageThisTurnLose() = scenario(players = 3) {
        alice.battlefield(vessel, counters = listOf(arcanum.api.CounterKind.PowerToughness(1, 1) to 7))
        attackUnblocked(vessel, "bob")
        expect {
            bob.life shouldBe 10
            state.player(bob.id).lost shouldBe true
            state.player(carol.id).lost shouldBe false
            vessel.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 8
        }
    }

    // CR 603.4: nine damage isn't ten
    @Test
    fun vesselDoesNothingBelowTenDamage() = scenario(players = 3) {
        alice.battlefield(vessel, counters = listOf(arcanum.api.CounterKind.PowerToughness(1, 1) to 6))
        attackUnblocked(vessel, "bob")
        expect {
            bob.life shouldBe 11
            state.player(bob.id).lost shouldBe false
        }
    }

    // CR 101.2, 601.2e; CR 608.2h: the mana value of the spell as it was cast (X counted, CR 202.3e) is kept after it resolves
    @Test
    fun moonfolkStopsOpponentsCastingSpellsOfThatManaValue() = scenario(players = 3) {
        alice.battlefield(moonfolk, "Mountain", "Mountain")
        alice.hand(surge)
        bob.battlefield("Island", "Island")
        bob.hand(quickTwo, quickOne)
        carol.battlefield("Island", "Island")
        carol.hand(quickTwo)
        alice.casts(surge, target = "carol", x = 1)
        resolveAll()
        allPass()
        expect { alice.graveyard has surge }
        alice.passes()
        val bobCasts = Engine.options(state, bob.id).filterIsInstance<Input.Cast>().map { it.card }
        assertEquals(listOf(card(quickOne)), bobCasts.distinct())
    }

    // CR 614.1a, 700.4: a creature a source of yours damaged this turn is exiled instead of dying; one it didn't damage dies
    @Test
    fun etchingExilesCreaturesItsControllersSourcesDamaged() = scenario {
        alice.battlefield(etching, "Mountain", "Swamp", "Swamp")
        alice.hand(zap, doom)
        bob.battlefield(cub, bear)
        alice.casts(zap, target = cub)
        resolveAll()
        alice.casts(doom, target = bear)
        resolveAll()
        expect {
            bob.exile has cub
            bob.graveyard has bear
        }
    }

    // CR 700.2, 714.2b: each chapter ability asks for its mode as it is put on the Stack
    @Test
    fun toshiroChapterIsModal() = scenario {
        alice.battlefield(bear)
        loreChapter(toshiro, 1)
        decision<Decision.Modes>()
        answer(Answer.Modes(listOf(0)))
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    // CR 714.2b: "I, II": chapter II asks again; the lifegain mode
    @Test
    fun toshiroChapterTwoGainsLife() = scenario {
        loreChapter(toshiro, 2)
        decision<Decision.Modes>()
        answer(Answer.Modes(listOf(2)))
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 613.1b, 108.3: each player gets back every permanent they own, whoever controlled it
    @Test
    fun kondaChapterTwoReturnsControlToOwners() = scenario(players = 3) {
        alice.battlefield(bear)
        bob.battlefield(ogre)
        carol.battlefield(cub)
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf(bear), duration = Duration.Indefinitely)
        effect(carol, Mechanic.ControlledBy(Players.You), on = listOf(ogre), duration = Duration.Indefinitely)
        loreChapter(konda, 2)
        resolveAll()
        expect {
            bear.controller shouldBe alice.id
            ogre.controller shouldBe bob.id
            cub.controller shouldBe carol.id
        }
    }

    // CR 506.2, 608.2d: the player attacked chooses the card; X is its mana value
    @Test
    fun manifestDefendingPlayerChoosesTheCard() = scenario(players = 3) {
        alice.battlefield(manifest)
        alice.graveyard(ogre, cub, "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(manifest, defender = "carol")
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(carol.id, choice.player)
        assertEquals(setOf(card(ogre), card(cub)), choice.options.toSet())
        carol.chooses(cub)
        resolveAll()
        expect {
            alice.hand has cub
            alice.graveyard has ogre
            manifest.power shouldBe 7
        }
    }

    // CR 118.12a, 101.4: each opponent in turn order discards or sacrifices a creature they choose
    @Test
    fun longReachEachOpponentDiscardsOrSacrifices() = scenario(players = 3) {
        bob.battlefield(bear, ogre)
        bob.hand(quickOne)
        carol.battlefield(cub)
        carol.hand(quickTwo)
        loreChapter(longReach, 1)
        resolveAll()
        // Bob discards; Carol declines and sacrifices her only creature
        decision<Decision.May>().player shouldBeSame bob.id
        answer(Answer.YesNo(true))
        resolveAll()
        decision<Decision.May>().player shouldBeSame carol.id
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.graveyard has quickOne
            bob.battlefield has bear
            bob.battlefield has ogre
            carol.graveyard has cub
            carol.hand has quickTwo
        }
    }

    // CR 111.2: each opponent creates a token they control; CR 613.1b: "gain control of all Rat tokens" takes each
    @Test
    fun tributeGivesOpponentsRatsAWardenTakes() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(ratTaker)
        loreChapter(tribute, 1)
        resolveAll()
        val rats = state.battlefield.filter { state.obj(it)?.definition?.faces?.first()?.name == "Rat Rogue Token" }
        assertEquals(setOf(bob.id, carol.id), rats.map { state.obj(it)!!.controller }.toSet())
        alice.casts(ratTaker)
        resolveAll()
        resolveAll()
        assertEquals(setOf(alice.id), rats.map { view()[it]!!.controller }.toSet())
    }

    private infix fun <T> T.shouldBeSame(expected: T) = assertEquals(expected, this)
}
