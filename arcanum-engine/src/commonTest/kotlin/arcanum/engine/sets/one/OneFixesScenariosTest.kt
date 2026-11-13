package arcanum.engine.sets.one

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.rtr.may
import kotlin.test.Test
import kotlin.test.assertTrue

/** ONE fix chunk. Invented names carry the oracle text of the real cards. */
class OneFixesScenariosTest {
    private val oil = CounterKind.Named("oil")

    private val vat = Cards.invent("Roiling Oil Vat", "{R}", "Artifact", """
        At the beginning of your upkeep, put an oil counter on another target nontoken artifact or creature you control.
        {2}, {T}: Create a 1/1 red Phyrexian Goblin creature token. Activate only if an oil counter was removed from a permanent you controlled this turn or a permanent with an oil counter on it was put into a graveyard this turn.
    """)
    private val siphon = Cards.invent("Oil Siphon", "{1}", "Artifact", "{T}, Remove an oil counter from this artifact: You gain 1 life.")
    private val recall = Cards.invent("Rustback Recall", "{U}", "Instant", "Return target artifact to its owner's hand.")
    private val bequest = Cards.invent("Oil Bequest", "{U}", "Sorcery", "Target opponent gains control of target permanent you control.")

    private val dusk = Cards.invent("Crimson Star's Dusk", "{X}{R}{R}", "Sorcery",
        "Destroy up to X target artifacts. If X is 5 or more, for each artifact destroyed this way, create a token that's a copy of it. Those tokens gain haste. Exile them at the beginning of the next end step.")
    private val seraphine = Cards.invent("Seraphine, Veiled Reaper", "{3}{W}{W}{B}{B}", "Legendary Planeswalker — Seraphine", """
        Hexproof
        [+2]: Each opponent loses 3 life and you gain 3 life.
        [0]: You draw two cards. Then each opponent may scry 1.
        [−3]: Exile target creature or enchantment. If it wasn't an Aura, create a token that's a copy of it, except it's a 1/1 white Spirit creature with flying in addition to its other types.
    """, loyalty = "6")
    private val kinzar = Cards.invent("Kinzar Bleak Patron", "{4}{B}", "Legendary Creature — Phyrexian Vampire", """
        Flying
        Whenever another nontoken creature you control dies, you may pay 2 life and exile it. If you do, create a token that's a copy of that creature, except it's 1/1 and has toxic 1. (Players dealt combat damage by it also get a poison counter.)
    """, "5/4")
    private val relic = Cards.invent("Lifespring Relic", "{2}", "Artifact", "{T}: You gain 1 life.")
    private val ingot = Cards.invent("Mimic Ingot", "{0}", "Artifact", "You may have this artifact enter as a copy of any artifact on the battlefield.")
    private val ooze = Cards.invent("Mimic Ooze", "{0}", "Creature — Ooze", "You may have this creature enter as a copy of any creature on the battlefield.", "0/0")
    private val boar = Cards.invent("Thornback Boar", "{2}{G}", "Creature — Boar", "Trample", "2/2")
    private val jolt = Cards.invent("Spiteful Jolt", "{R}", "Instant", "Spiteful Jolt deals 2 damage to target creature.", null)

    private val blade = Cards.invent("Blade of Mingled Souls", "{2}{U}", "Artifact — Equipment", """
        For Mirrodin! (When this Equipment enters, create a 2/2 red Rebel creature token, then attach this to it.)
        Whenever this Equipment becomes attached to a creature, for as long as this Equipment remains attached to it, you may have that creature become a copy of another target creature you control.
        Equip {2}
    """.trimIndent())
    private val valet = Cards.invent("Brasswork Valet", "{3}", "Artifact Creature — Golem", "{T}: Attach target Equipment you control to target creature you control.", "1/3")
    private val cub = Cards.invent("Thornpath Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val colossus = Cards.invent("Thornpath Colossus", "{4}{G}", "Creature — Giant", "", "5/5")

    /** Alice casts [clone] (in her hand) copying [copied]. */
    private fun Scenario.castsCopy(clone: String, copied: String) {
        alice.casts(clone)
        playOut(copied)
    }

    private fun Scenario.hasActivated(id: arcanum.engine.state.ObjectId) =
        view()[id]?.characteristics?.abilities.orEmpty().any { it.ability is Ability.Activated }

    // CR 608.2h, 603.10a: the permanent the oil counter was removed from is judged as it was then, though it has left the battlefield since
    @Test
    fun vatCountsARemovalFromAPermanentThatLeftSince() = scenario {
        alice.battlefield(vat, "Mountain", "Mountain", "Island")
        alice.battlefield(siphon, counters = listOf(oil to 1))
        alice.hand(recall)
        alice.activates(siphon)
        resolveAll()
        alice.casts(recall, target = siphon)
        resolveAll()
        alice.activates(vat, ability = 0)
        resolveAll()
        expect { alice.battlefield has "Phyrexian Goblin Token" }
    }

    // CR 608.2h: "a permanent you controlled" is the controller as the counter was removed; Bob controlling it now doesn't take it away
    @Test
    fun vatCountsARemovalFromAPermanentYouNoLongerControl() = scenario(players = 3) {
        alice.battlefield(vat, "Mountain", "Mountain", "Island")
        alice.battlefield(siphon, counters = listOf(oil to 1))
        alice.hand(bequest)
        alice.activates(siphon)
        resolveAll()
        alice.casts(bequest, targets = listOf("bob", siphon))
        resolveAll()
        expect { bob.battlefield has siphon }
        alice.activates(vat, ability = 0)
        resolveAll()
        expect { alice.battlefield has "Phyrexian Goblin Token" }
    }

    // CR 707.2, 608.2h: the token copies the destroyed artifact as it last existed on the battlefield, a copy of Lifespring Relic, not the
    // Mimic Ingot card in the graveyard
    @Test
    fun duskCopiesADestroyedCopyAsItLastExisted() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(relic)
        alice.hand(ingot, dusk)
        castsCopy(ingot, relic)
        alice.casts(dusk, target = ingot, x = 5)
        resolveAll()
        val token = tokens(relic).single()
        expect { alice.graveyard has ingot }
        assertTrue(hasActivated(token))
    }

    // CR 707.2, 608.2h: the exiled creature is copied as it last existed on the battlefield (a copy of the Boar), with the exceptions (CR 707.9b)
    @Test
    fun seraphineCopiesAnExiledCopyAsItLastExisted() = scenario {
        alice.battlefield(seraphine, counters = listOf(CounterKind.Loyalty to 6))
        alice.battlefield(boar)
        alice.hand(ooze)
        castsCopy(ooze, boar)
        alice.activates(seraphine, ability = 2, target = ooze)
        resolveAll()
        val token = CardRef(tokens(boar).single(), "token")
        expect {
            alice.exile has ooze
            token.power shouldBe 1
            token.subtypes.toSet() shouldBe setOf(Subtype("Boar"), Subtype("Spirit"))
            token hasKeyword Keyword.Trample.kind
            token hasKeyword Keyword.Flying.kind
        }
    }

    // CR 707.2, 608.2h: "that creature" died a copy of the Boar; the token copies it as it last existed, trample included
    @Test
    fun kinzarCopiesADeadCopyAsItLastExisted() = scenario {
        alice.battlefield(kinzar, boar, "Mountain")
        alice.hand(ooze, jolt)
        castsCopy(ooze, boar)
        alice.casts(jolt, target = ooze)
        playOut()
        val token = CardRef(tokens(boar).single(), "token")
        expect {
            alice.exile has ooze
            token.power shouldBe 1
            token hasKeyword Keyword.Trample.kind
            token hasKeyword Keyword.Toxic.Kind
        }
    }

    // CR 608.2h, 611.2b: the Blade became attached to the Cub; moved on to the Valet before that trigger resolves (the Valet's own trigger
    // declined), the "for as long as" duration has already ended, so neither the Cub nor the Valet becomes a copy of the Colossus
    @Test
    fun bladeMovedOnBeforeItsTriggerResolvesCopiesNothing() = scenario {
        alice.battlefield("Island", "Island", blade, valet, cub, colossus)
        alice.activates(blade, target = cub)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(colossus))))
        alice.activates(valet, targets = listOf(blade, valet))
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(colossus))))
        resolveAll()
        may(false)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect {
            valet.power shouldBe 1
            cub.power shouldBe 2
        }
    }

    private val drove = Cards.invent("Inquisitive Drove", "{3}{G}", "Instant",
        "Choose target opponent. You create X 3/3 green Beast creature tokens, where X is the number of artifacts that player controls.")

    // CR 608.2c: "that player" is the chosen opponent: X counts Carol's two artifacts, not Bob's or Alice's
    @Test
    fun droveCountsTheChosenOpponentsArtifacts() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", relic)
        bob.battlefield(siphon)
        carol.battlefield(ingot, vat)
        alice.hand(drove)
        alice.casts(drove, targets = listOf("carol"))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Beast Token" } shouldBe 2 }
    }
}
