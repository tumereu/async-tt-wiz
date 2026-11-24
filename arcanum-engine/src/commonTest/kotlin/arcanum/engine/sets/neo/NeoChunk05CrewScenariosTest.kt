package arcanum.engine.sets.neo

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chunk 5 of NEO: "becomes crewed" triggers (CR 702.122e), a Vehicle that becomes a copy of what crewed it, an exile cost whose cards
 * come back when the token leaves, and Pilot tokens that crew as though stronger. */
class NeoChunk05CrewScenariosTest {
    private fun crewLine(n: Int) =
        "Crew $n (Tap any number of creatures you control with total power $n or more: This Vehicle becomes an artifact creature until end of turn.)"

    private val ogre = Cards.invent("Neo Crew Ogre", "{2}{R}", "Creature — Ogre", "Trample", "3/3")
    private val daimyo = Cards.invent("Neo Crew Daimyo", "{1}{W}", "Legendary Creature — Human Noble", "", "2/2")
    private val cart = Cards.invent("Neo Crew Cart", "{2}", "Artifact — Vehicle", crewLine(5), "4/4")
    private val wagon = Cards.invent("Neo Crew Wagon", "{2}", "Artifact — Vehicle", crewLine(5), "2/5")
    private val golem = Cards.invent("Neo Crew Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val myr = Cards.invent("Neo Crew Myr", "{2}", "Artifact Creature — Myr", "", "1/1")
    private val banish = Cards.invent("Neo Crew Banishment", "{0}", "Instant", "Exile target creature.")

    private val rallyingRig = Cards.invent("Rallying Rig", "{1}{U}", "Artifact — Vehicle", "Flying\n" +
        "Whenever this Vehicle becomes crewed, up to one other target Vehicle you control becomes an artifact creature until end of turn.\n" +
        crewLine(3), "3/4")
    private val mirrorlinkRig = Cards.invent("Mirrorlink Rig", "{2}{U}", "Artifact — Vehicle", "Flying\n" +
        "Whenever this Vehicle becomes crewed for the first time each turn, until end of turn, this Vehicle becomes a copy of target nonlegendary " +
        "creature that crewed it this turn, except it's 4/3, it's a Vehicle artifact in addition to its other types, and it has flying.\n" +
        crewLine(1), "4/3")
    private val nexus = Cards.invent("Titanframe Nexus", "{2}", "Artifact — Vehicle",
        "{5}, Exile this Vehicle and four other artifact creatures and/or Vehicles you control: Create Titanframe, a legendary 10/10 Construct " +
            "artifact creature token with flying, vigilance, trample, lifelink, and haste that's all colors. When that token leaves the battlefield, " +
            "return all cards exiled with this Vehicle except this card to the battlefield tapped under their owners' control.\n" +
            "Crew 2", "2/4")
    private val hauler = Cards.invent("Vaultcracker Hauler", "{2}", "Artifact — Vehicle",
        "This Vehicle enters with three charge counters on it.\n" +
            "{2}, {T}, Remove a charge counter from this Vehicle: Draw a card. Then if there are no charge counters on this Vehicle, create a " +
            "Treasure token and a 1/1 colorless Pilot creature token with \"This token crews Vehicles as though its power were 2 greater.\"\n" +
            "Crew 3", "4/4")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 702.122e: its crew ability resolving triggers it; CR 115.3: "other" leaves this Vehicle out; CR 205.1b: an artifact creature
    @Test
    fun rallyingRigCrewedAnimatesAnotherVehicle() = scenario {
        alice.battlefield(rallyingRig, cart, wagon, ogre)
        alice.activates(rallyingRig, ability = 0, choosing = listOf(ogre))
        resolve()
        val targets = pending as Decision.Targets
        assertEquals(setOf(target(cart), target(wagon)), targets.options.toSet())
        answer(Answer.Targets(listOf(target(cart))))
        resolveAll()
        expect {
            rallyingRig.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            cart.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            cart.power shouldBe 4
            wagon.types shouldBe listOf(CardType.Artifact)
        }
    }

    // CR 707.2, 707.9b: a copy of the creature that crewed it, except 4/3, a Vehicle artifact and flying; CR 611.2a: until end of turn
    @Test
    fun mirrorlinkRigCopiesTheCreatureThatCrewedIt() = scenario {
        alice.battlefield(mirrorlinkRig, ogre, daimyo)
        alice.activates(mirrorlinkRig, ability = 0, choosing = listOf(ogre))
        resolveAll()
        val copy = card(mirrorlinkRig)
        expect {
            val view = arcanum.engine.scenario.CardRef(copy, "copy").view.characteristics!!
            view.name shouldBe ogre
            view.power shouldBe 4
            view.toughness shouldBe 3
            view.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
            view.subtypes.toSet() shouldBe setOf(Subtype("Ogre"), Subtype("Vehicle"))
            arcanum.engine.scenario.CardRef(copy, "copy") hasKeyword Keyword.Trample.kind
            arcanum.engine.scenario.CardRef(copy, "copy") hasKeyword Keyword.Flying.kind
        }
        advanceTo(arcanum.api.TurnStep.Upkeep, turn = 2)
        expect {
            val view = arcanum.engine.scenario.CardRef(copy, "copy").view.characteristics!!
            view.name shouldBe mirrorlinkRig
        }
    }

    // CR 603.2, 702.122e: only the first crewing each turn triggers it; a legendary creature isn't a legal target, so that trigger is removed
    // (CR 603.3d), and the second crewing doesn't trigger
    @Test
    fun mirrorlinkRigTriggersOnlyTheFirstTimeEachTurn() = scenario {
        alice.battlefield(mirrorlinkRig, ogre, daimyo)
        alice.activates(mirrorlinkRig, ability = 0, choosing = listOf(daimyo))
        resolveAll()
        expect { stack.size shouldBe 0 }
        alice.activates(mirrorlinkRig, ability = 0, choosing = listOf(ogre))
        resolve()
        expect {
            stack.size shouldBe 0
            mirrorlinkRig.power shouldBe 4
            mirrorlinkRig hasKeyword Keyword.Crew.Kind
        }
    }

    // CR 118.3, 601.2h: the cost exiles it and four others (a stolen one included); CR 111.4, 105.2c: the token; CR 603.7a, 603.7c: when the
    // token leaves, the other exiled cards return tapped, each under its owner's control (CR 110.2), and this card stays exiled (CR 400.7)
    @Test
    fun titanframeNexusReturnsTheOthersWhenTheTokenLeaves() = scenario(players = 3) {
        alice.battlefield(nexus, cart, golem, myr, "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(wagon)
        effect(alice, Mechanic.ControlledBy(Players.You), on = listOf(wagon), duration = Duration.Indefinitely)
        alice.hand(banish)
        alice.activates(nexus, ability = 0, choosing = listOf(cart, golem, myr, wagon))
        expect {
            alice.exile.names.toSet() shouldBe setOf(nexus, cart, golem, myr)
            bob.exile.names shouldBe listOf(wagon)
        }
        resolveAll()
        expect {
            alice.battlefield has "Titanframe"
            "Titanframe".power shouldBe 10
            "Titanframe".toughness shouldBe 10
            "Titanframe".colors.toSet() shouldBe setOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green)
            "Titanframe".types shouldBe listOf(CardType.Artifact, CardType.Creature)
            "Titanframe".view.characteristics!!.supertypes shouldBe listOf(arcanum.api.Supertype.Legendary)
            "Titanframe" hasKeyword Keyword.Lifelink.kind
            "Titanframe" hasKeyword Keyword.Haste.kind
        }
        alice.casts(banish, target = "Titanframe")
        resolveAll()
        expect {
            alice.battlefield.names.toSet() shouldBe setOf(cart, golem, myr, "Plains")
            cart.tapped shouldBe true
            myr.tapped shouldBe true
            wagon.zone shouldBe Zone.Battlefield
            wagon.tapped shouldBe true
            wagon.controller shouldBe bob.id
            nexus.zone shouldBe Zone.Exile
        }
    }

    // CR 608.2c: "Then if" is checked after the draw; CR 702.122a: the Pilot crews as though its power were 3
    @Test
    fun vaultcrackerHaulerMakesTokensWithItsLastCounter() = scenario {
        alice.battlefield(hauler, counters = listOf(CounterKind.Named("charge") to 1))
        alice.battlefield("Plains", "Plains")
        alice.library("Island", "Island")
        alice.activates(hauler, ability = 0)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.battlefield has "Treasure Token"
            alice.battlefield has "Pilot Token"
        }
        alice.activates(hauler, ability = 1, choosing = listOf("Pilot Token"))
        resolveAll()
        expect { hauler.types shouldBe listOf(CardType.Artifact, CardType.Creature) }
    }

    // CR 608.2c: with a charge counter left, no tokens
    @Test
    fun vaultcrackerHaulerKeepsItsTokensWhileCountersRemain() = scenario {
        alice.battlefield(hauler, counters = listOf(CounterKind.Named("charge") to 2))
        alice.battlefield("Plains", "Plains")
        alice.library("Island", "Island")
        alice.activates(hauler, ability = 0)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf("Island")
            hauler.counters(CounterKind.Named("charge")) shouldBe 1
            alice.battlefield lacks "Treasure Token"
            alice.battlefield lacks "Pilot Token"
        }
    }
}
