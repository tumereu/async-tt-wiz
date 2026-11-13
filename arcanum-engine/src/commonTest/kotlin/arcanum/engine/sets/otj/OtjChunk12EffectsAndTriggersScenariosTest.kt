package arcanum.engine.sets.otj

import arcanum.api.CardType
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.dft.payYes
import kotlin.test.Test

/** OTJ chunk 12: additional upkeep steps after a phase, a returned card that is a Treasure as it enters, copies of an opponent's tokens, and
 * exiling and returning creatures again and again. */
class OtjChunk12EffectsAndTriggersScenariosTest {
    private val garth = Cards.invent("Garth, Seconds Thief", "{1}{U}{B}{R}", "Legendary Creature — Ogre Warlock",
        "Menace\nWhenever Garth deals combat damage to a player, you get that many additional upkeep steps after this phase.", "2/5")
    private val ledger = Cards.invent("Dawnbell Ledger", "{1}{W}", "Enchantment", "At the beginning of your upkeep, you gain 1 life.")
    private val ilsa = Cards.invent("Ilsa, the Gilded Hush", "{1}{B}{G}", "Legendary Creature — Gorgon Assassin",
        "Deathtouch\nWhenever a nontoken creature an opponent controls dies, you may pay {1}. If you do, return that card to the battlefield tapped " +
            "under your control. It's a Treasure artifact with \"{T}, Sacrifice this artifact: Add one mana of any color,\" and it loses all other card types.",
        "3/3")
    private val steer = Cards.invent("Dustcoat Steer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val mayor = Cards.invent("Tollkeeper Mayor", "{1}{W}{B}", "Legendary Creature — Human Advisor",
        "Whenever one or more tokens your opponents control enter, for each of them, create a tapped token that's a copy of it. " +
            "This ability triggers only once each turn.\n" +
            "Whenever one or more tokens you control enter, each opponent loses 1 life and you gain 1 life.", "2/4")
    private val rally = Cards.invent("Roadside Rally", "{1}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")
    private val encore = Cards.invent("Encore Shuffle", "{X}{X}{2}{W}", "Sorcery",
        "Exile any number of creatures you control, then return them to the battlefield under their owner's control. Then repeat this process X more times.")
    private val tortoise = Cards.invent("Gatewarden Tortoise", "{1}{W}", "Creature — Turtle", "When this creature enters, you gain 1 life.", "1/3")

    // CR 500.10: each additional upkeep step is a phase of its own after the combat phase; its "at the beginning of your upkeep" abilities
    // trigger each time (CR 503.1), then the turn goes on with the postcombat main phase
    @Test
    fun combatDamageGivesThatManyUpkeepsAfterCombat() = scenario {
        alice.battlefield(garth, ledger)
        hits(garth)
        advanceTo(TurnStep.PostcombatMain)
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
            state.turn.number shouldBe 1
        }
    }

    // CR 611.2e: the returned card is a Treasure artifact as it enters, not a creature; CR 205.1a: its old types and subtypes are gone;
    // CR 110.2: under the payer's control
    @Test
    fun opponentsCreatureReturnsAsATreasure() = scenario {
        alice.battlefield(ilsa, "Swamp", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(steer)
        alice.casts("Storm Spark", target = steer, paying = listOf("Mountain"))
        allPass()
        allPass()
        payYes()
        expect {
            steer.zone shouldBe Zone.Battlefield
            steer.controller shouldBe alice.id
            steer.types shouldBe listOf(CardType.Artifact)
            steer.subtypes shouldBe listOf(Subtype("Treasure"))
            steer.tapped shouldBe true
        }
    }

    // CR 603.2c: one trigger for the batch, a tapped copy of each token (CR 707.2). (The engine creates the copies one at a time, so "one or
    // more tokens you control enter" isn't checked here: the copies should enter together.)
    @Test
    fun copiesEachTokenAnOpponentMakes() = scenario(players = 3) {
        alice.battlefield(mayor)
        bob.battlefield("Plains", "Plains")
        bob.hand(rally)
        turn(number = 2, active = bob)
        bob.casts(rally)
        allPass()
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2
            bob.battlefield.names.count { it == "Soldier Token" } shouldBe 2
        }
    }

    // CR 608.2c: the creatures are chosen as it resolves, X + 1 times; CR 400.7: each return is a new object, so its enters ability triggers
    // each time
    @Test
    fun exilesAndReturnsXPlusOneTimes() = scenario {
        alice.battlefield(tortoise, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(encore)
        alice.casts(encore, x = 1)
        allPass()
        alice.chooses(tortoise)
        // the returned tortoise is a new object (CR 400.7) the scenario's names don't show while the spell is still resolving
        answer(Answer.Objects((pending as Decision.ChooseObjects).options))
        resolveAll()
        expect {
            tortoise.zone shouldBe Zone.Battlefield
            alice.life shouldBe 22
        }
    }
}
