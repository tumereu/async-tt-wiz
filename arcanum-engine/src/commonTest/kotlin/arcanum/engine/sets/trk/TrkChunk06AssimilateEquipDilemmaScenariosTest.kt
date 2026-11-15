package arcanum.engine.sets.trk

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.chooseNone
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Star Trek chunk 06 in play: facing a dilemma (CR 700.17: choosing one or more modes for a spell or ability; a copy's modes are copied,
 * not chosen), "faced a dilemma this turn", assimilate (CR 701.72b), "can't be beamed up" (CR 702.196b), a granted attack trigger that
 * looks for another Communicator, "instead" after a surveil, and an opponent who may have you draw. */
class TrkChunk06AssimilateEquipDilemmaScenariosTest {
    private val reminder = "(You face a dilemma as you choose one or more modes for a spell or ability.)"
    private val analyst = Cards.invent("Unit Drone Analyst", "{2}{U}{B}", "Legendary Artifact Creature — Human Scientist",
        "Whenever you face a dilemma, draw a card. $reminder", "2/4")
    private val clothier = Cards.invent("Orvan Tesk, Plain Clothier", "{2}{B}", "Legendary Creature — Cardassian Rogue", """
        Menace
        Whenever you face a dilemma, each opponent loses 2 life and you gain 2 life. $reminder
    """, "3/3")
    private val charm = Cards.invent("Trk6 Quandary Charm", "{U}", "Instant", """
        Choose one —
        • You gain 2 life.
        • Each opponent loses 1 life.
    """)
    private val plainSpell = Cards.invent("Trk6 Plain Insight", "{U}", "Instant", "You gain 1 life.")
    private val envoy = Cards.invent("Trk6 Envoy of Choices", "{1}{W}", "Creature — Human Advisor", """
        When this creature enters, choose one —
        • You gain 1 life.
        • Each opponent loses 1 life.
    """, "2/2")
    private val echo = Cards.invent("Trk6 Echo Relay", "{U}{U}", "Instant", "Copy target instant or sorcery spell you control.")
    private val arrayKeeper = Cards.invent("The Array Keeper", "{5}{U}", "Legendary Creature — Elemental", """
        Vigilance, ward {2}
        At the beginning of each end step, if you faced a dilemma this turn, target opponent exiles cards from the top of their library until they exile a nonland card. You may cast that card without paying its mana cost. $reminder
    """, "5/6")
    private val bear = Cards.invent("Trk6 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk6 Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val wall = Cards.invent("Trk6 Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    private val scout = Cards.invent("Trk6 Grove Scout", "{1}{G}", "Enchantment Creature — Elf Scout", "", "2/2")
    private val drone = Cards.invent("Trk6 Drone", "{2}", "Artifact Creature — Drone", "", "1/1")
    private val resistance = Cards.invent("Resistance Is Pointless", "{6}{B}{B}", "Enchantment", """
        Artifact creatures you control have indestructible.
        At the beginning of your end step, assimilate target creature card from a graveyard. (Put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)
    """)
    private val cube = Cards.invent("Collective Hexahedron", "{5}{W}{B}", "Artifact — Spacecraft", """
        Affinity for artifact creatures
        Whenever this Spacecraft enters or attacks, assimilate a creature card exiled with it. If you can't, exile up to one target creature. (To assimilate a card, put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)
        Station
        12+ | Artifact creatures you control have flying.
    """, "6/6")
    private val stranded = Cards.invent("Stranded Afar", "{U}", "Enchantment — Aura", """
        Enchant artifact or creature
        When this Aura enters, you may pay {3}. If you do, tap enchanted permanent.
        Enchanted permanent doesn't untap during its controller's untap step and can't be beamed up.
    """)
    private val recallPing = Cards.invent("Trk6 Recall Ping", "{U}", "Sorcery", """
        Draw a card.
        Beam me up {2}{U} (You may cast this card from your graveyard for {2}{U} if you also return a creature you control to its owner's hand. Then exile this spell.)
    """)
    private val pin = Cards.invent("Hailing Pin", "{3}", "Artifact — Communicator Equipment", """
        When you cast this spell, copy it. (The copy becomes a token.)
        Equipped creature has "Whenever this creature attacks while you control another creature with a Communicator attached to it, this creature can't be blocked this turn."
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val spareBadge = Cards.invent("Trk6 Spare Badge", "{1}", "Artifact — Communicator Equipment", "Equip {1}")
    private val mira = Cards.invent("Mira Volk, Fixer", "{1}{U}", "Legendary Creature — Human Artificer", """
        Whenever Mira Volk becomes tapped, surveil 1. If her power is 4 or greater, instead draw a card.
        Whenever you face a dilemma, put a +1/+1 counter on Mira Volk. $reminder
    """, "1/2")
    private val vision = Cards.invent("Describe the Vision", "{1}{B}", "Enchantment", """
        When this enchantment enters, target opponent may have you draw a card. If they don't, they lose 4 life and you surveil 1.
        Whenever you face a dilemma, you may return this enchantment to its owner's hand. $reminder
    """)
    private val regress = Cards.invent("Regress Strand", "{1}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -4/-0, can't block, and is a Salamander. (It loses all other creature types.)
        Threshold — At the beginning of each upkeep, if there are seven or more cards in your graveyard, exile enchanted creature.
    """)

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // ---- Face a dilemma

    // CR 700.17: choosing a mode for a spell as it is cast is facing a dilemma; the trigger resolves first (CR 603.3)
    @Test
    fun choosingAModeForASpellFacesADilemma() = scenario(players = 3) {
        alice.battlefield(analyst, "Island")
        alice.hand(charm)
        alice.library("Forest", "Plains")
        alice.casts(charm, modes = listOf(0))
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.library has "Plains"
            alice.life shouldBe 22
        }
    }

    // CR 700.17: a spell without modes is no dilemma, nor is another player's choice of modes
    @Test
    fun noModesOrAnotherPlayersModesAreNoDilemma() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(analyst)
        bob.battlefield("Island", "Island")
        bob.hand(charm, plainSpell)
        alice.library("Forest")
        bob.casts(plainSpell, paying = listOf("Island"))
        resolveAll()
        bob.casts(charm, modes = listOf(1), paying = listOf("Island#1"))
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.life shouldBe 19
            bob.life shouldBe 21
        }
    }

    // "An opponent chooses one — • You gain 2 life. • Each opponent loses 1 life." written in the model (the parser doesn't read "An opponent
    // chooses one —" yet)
    private val imposedQuandary = Cards.define("Trk Fix Imposed Quandary", Cards.definition(Cards.invent("Trk Fix Imposed Quandary Base", "{0}", "Instant", """
        Choose one —
        • You gain 2 life.
        • Each opponent loses 1 life.
    """)).let { d ->
        d.copy(faces = d.faces.map { f -> f.copy(name = "Trk Fix Imposed Quandary", abilities = f.abilities.map { a ->
            if (a is arcanum.api.Ability.Spell) a.copy(instructions = a.instructions.copy(modes = a.instructions.modes!!.copy(
                chooser = arcanum.api.Players.Each(arcanum.api.PlayerFilter.Opponent)))) else a
        }) })
    })

    // CR 700.2e, 700.17: when another player chooses the modes, the controller first picks which opponent does, and that player faces the
    // dilemma, not the controller
    @Test
    fun theOpponentWhoChoosesTheModesFacesTheDilemma() = scenario(players = 3) {
        alice.battlefield(analyst)
        carol.battlefield(analyst)
        alice.hand(imposedQuandary)
        alice.library("Forest")
        bob.library("Island")
        carol.library("Plains")
        alice.casts(imposedQuandary)
        expect { (pending as Decision.ChoosePlayer).player shouldBe alice.id }
        answer(Answer.Player(carol.id))
        expect { (pending as Decision.Modes).player shouldBe carol.id }
        answer(Answer.Modes(listOf(0)))
        resolveAll()
        expect {
            carol.hand has "Plains"
            alice.hand.size shouldBe 0
            alice.life shouldBe 22
        }
    }

    // CR 700.17, 603.3c: the modes of a triggered ability are chosen as it is put on the Stack, a dilemma too; each opponent loses 2 life
    @Test
    fun choosingAModeForATriggeredAbilityFacesADilemma() = scenario(players = 3) {
        alice.battlefield(clothier, "Plains", "Plains")
        alice.hand(envoy)
        alice.casts(envoy)
        resolveAll()
        modes(0)
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 23
        }
    }

    // CR 707.10, 700.17: a copy's modes are copied, not chosen: copying a modal spell is no dilemma
    @Test
    fun aCopyOfAModalSpellIsNoDilemma() = scenario(players = 3) {
        alice.battlefield(analyst, "Island", "Island", "Island")
        alice.hand(charm, echo)
        alice.library("Forest", "Plains")
        alice.casts(charm, modes = listOf(1), paying = listOf("Island"))
        alice.casts(echo, target = charm)
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.library has "Plains"
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // CR 700.17 ("faced a dilemma this turn"), 603.4; the opponent exiles until a nonland card, which you may cast free (CR 608.2g)
    @Test
    fun afterADilemmaTheOpponentExilesUntilANonlandCardYouCast() = scenario(players = 3) {
        alice.battlefield(arrayKeeper, "Island")
        alice.hand(charm)
        bob.library("Plains", bear, "Forest")
        alice.casts(charm, modes = listOf(0))
        resolveAll()
        advanceTo(TurnStep.End)
        targets("bob")
        resolveAll()
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.controller shouldBe alice.id
            bob.exile has "Plains"
            bob.library has "Forest"
        }
    }

    // CR 603.4: without a dilemma this turn the ability doesn't trigger
    @Test
    fun withoutADilemmaNothingIsExiled() = scenario(players = 3) {
        alice.battlefield(arrayKeeper, "Island")
        alice.hand(plainSpell)
        bob.library("Plains", bear)
        alice.casts(plainSpell)
        resolveAll()
        advanceTo(TurnStep.End)
        assertNull(pending)
        expect {
            bob.library has bear
            bob.exile.isEmpty()
        }
    }

    // ---- Assimilate

    // CR 701.72b, 205.1b: the card enters under your control with a +1/+1 counter as a Borg artifact creature: its other creature types go,
    // its other card types stay; then it is an artifact creature you control (indestructible)
    @Test
    fun assimilateAnOpponentsGraveyardCreature() = scenario(players = 3) {
        alice.battlefield(resistance)
        bob.graveyard(scout)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            scout.zone shouldBe Zone.Battlefield
            scout.controller shouldBe alice.id
            scout.types.toSet() shouldBe setOf(CardType.Enchantment, CardType.Artifact, CardType.Creature)
            scout.subtypes shouldBe listOf(Subtype("Borg"))
            scout.counters(p1p1) shouldBe 1
            scout.power shouldBe 3
            scout hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 702.41a: affinity for artifact creatures; CR 609.3: nothing exiled with it, so it can't assimilate and exiles up to one target creature
    @Test
    fun enteringWithNothingExiledExilesACreature() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp", drone, "Trk6 Drone")
        alice.hand(cube)
        bob.battlefield(bear)
        alice.casts(cube)
        resolveAll()
        targets(bear)
        resolveAll()
        expect {
            alice.battlefield has cube
            bob.exile has bear
        }
    }

    // CR 701.72b, 607.2a: attacking, it exiles a creature; attacking again, it assimilates the card exiled with it, so it exiles nothing
    @Test
    fun attackingAgainAssimilatesTheCardExiledWithIt() = scenario {
        alice.battlefield(cube, counters = listOf(arcanum.api.KeywordExpansions.CHARGE to 12))
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cube)
        targets(bear)
        resolveAll()
        expect { bob.exile has bear }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(cube)
        if (pending is Decision.Targets) answer(Answer.Targets(emptyList()))
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.controller shouldBe alice.id
            bear.subtypes shouldBe listOf(Subtype("Borg"))
            bear.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
            bear.counters(p1p1) shouldBe 1
            alice.battlefield has cube
        }
    }

    // ---- Auras

    private fun Scenario.castable(name: String) =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().any { it.card == card(name) }

    // CR 702.196b: a creature that can't be beamed up can't be returned to pay a beam me up cost; another creature can
    @Test
    fun anEnchantedCreatureCantBeBeamedUp() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", bear, stranded)
        attach(stranded, bear)
        alice.graveyard(recallPing)
        alice.library("Forest")
        assertFalse(castable(recallPing))
    }

    // CR 702.196b: only the enchanted creature is excluded
    @Test
    fun anotherCreatureStillPaysTheBeamMeUpCost() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", bear, ox, stranded)
        attach(stranded, bear)
        alice.graveyard(recallPing)
        alice.library("Forest")
        assertTrue(castable(recallPing))
        alice.casts(recallPing, choosing = listOf(ox))
        resolveAll()
        expect {
            alice.hand has ox
            alice.hand has "Forest"
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 205.1a: "is a Salamander" replaces its creature types; CR 603.4: threshold, at the beginning of each upkeep, here an opponent's
    @Test
    fun theEnchantedCreatureIsASalamanderAndIsExiledWithThreshold() = scenario {
        bob.battlefield(ox)
        alice.battlefield(regress)
        attach(regress, ox)
        alice.graveyard("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.library("Island", "Island")
        expect {
            ox.subtypes shouldBe listOf(Subtype("Salamander"))
            ox.power shouldBe -1
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        while (pending == null && state.stack.isNotEmpty()) resolve()
        resolveAll()
        expect { bob.exile has ox }
    }

    // ---- Equipment

    // CR 707.10f: the copy of a permanent spell becomes a token as it resolves
    @Test
    fun castingItMakesATokenCopy() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(pin)
        alice.casts(pin)
        resolveAll()
        expect { alice.battlefield.names.count { it == pin } shouldBe 2 }
    }

    // CR 603.2, 509.1b: the granted trigger looks for another creature with a Communicator as it attacks; then it can't be blocked
    @Test
    fun cantBeBlockedWhileAnotherCreatureHasACommunicator() = scenario {
        alice.battlefield(bear, ox, pin, spareBadge)
        attach(pin, bear)
        attach(spareBadge, ox)
        bob.battlefield(wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(wall to bear) }
    }

    // CR 603.2: without another Communicator on a creature it doesn't trigger, and it may be blocked
    @Test
    fun blockableWithoutAnotherCommunicator() = scenario {
        alice.battlefield(bear, ox, pin, spareBadge)
        attach(pin, bear)
        bob.battlefield(wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to bear)
    }

    // ---- "instead"

    // CR 701.25a: power 1: it surveils (the card is looked at and may go to the graveyard)
    @Test
    fun becomingTappedSurveils() = scenario {
        alice.battlefield(mira)
        alice.library("Island", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mira)
        resolveAll()
        decision<Decision.ChooseObjects>()
        chooseNone()
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.library has "Island"
        }
    }

    // CR 608.2c: power 4 or greater: she draws a card instead of surveilling
    @Test
    fun withPowerFourSheDrawsInstead() = scenario {
        alice.battlefield(mira, counters = listOf(p1p1 to 3))
        alice.library("Island", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mira)
        resolveAll()
        assertNull(pending)
        expect { alice.hand has "Island" }
    }

    // ---- Target opponent may have you draw

    // CR 608.2d: the target opponent chooses; declining, they lose 4 life and you surveil 1
    @Test
    fun theOpponentDeclinesAndLosesLife() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(vision)
        alice.library("Island")
        alice.casts(vision)
        resolveAll()
        targets("carol")
        resolveAll()
        assertEquals(carol.id, pending!!.player)
        answer(Answer.YesNo(false))
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            carol.life shouldBe 16
            bob.life shouldBe 20
            alice.hand.size shouldBe 0
            alice.library has "Island"
        }
    }

    // CR 608.2d: accepting, you draw a card and they lose nothing
    @Test
    fun theOpponentLetsYouDraw() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(vision)
        alice.library("Island")
        alice.casts(vision)
        resolveAll()
        targets("carol")
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            carol.life shouldBe 20
            alice.hand has "Island"
        }
    }
}
