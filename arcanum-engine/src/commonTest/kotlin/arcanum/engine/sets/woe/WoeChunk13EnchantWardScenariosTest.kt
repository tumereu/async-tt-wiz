package arcanum.engine.sets.woe

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * WOE chunk 13: the invented names carry the oracle text of Bestial Bloodline, Bitter Chill, Cooped Up, Horned Loch-Whale, Sleep-Cursed
 * Faerie, Twining Twins, Rootrider Faun, Stormkeld Vanguard, Chancellor of Tales, Mocking Sprite, Rowdy Research, Disdainful Stroke,
 * Scalding Viper and Unruly Catapult.
 */
class WoeChunk13EnchantWardScenariosTest {
    private val lineage = Cards.invent("Feral Lineage", "{1}{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
        {4}{G}: Return this card from your graveyard to your hand.
    """)
    private val frostbite = Cards.invent("Bleak Frostbite", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
        When this Aura is put into a graveyard from the battlefield, you may pay {1}. If you do, scry 1, then draw a card.
    """)
    private val hutch = Cards.invent("Hutched Away", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block.
        {2}{W}: Exile enchanted creature.
    """)
    private val whale = Cards.inventFaces("Adventure",
        face("Antlered Tarn-Whale", "{4}{U}{U}", "Creature — Whale", """
            Flash
            Ward {2}
            This creature enters tapped unless it's your turn.
        """, "6/6", null, null),
        face("Tarn Plunge", "{1}{U}", "Instant — Adventure",
            "The owner of target attacking creature you don't control puts it on their choice of the top or bottom of their library.", null, null, null))
    private val sprite = Cards.invent("Drowsy Sprite", "{U}", "Creature — Faerie Wizard", """
        Flying, ward {2}
        This creature enters tapped with three stun counters on it. (If it would become untapped, remove a stun counter from it instead.)
        {1}{U}: Untap this creature.
    """, "3/3")
    private val twosome = Cards.inventFaces("Adventure",
        face("Mirrored Twosome", "{2}{U}{U}", "Creature — Faerie Wizard", "Flying, vigilance, ward {1}", "4/4", null, null),
        face("Brief Spiral", "{1}{W}", "Instant — Adventure",
            "Exile target nontoken creature. Return it to the battlefield under its owner's control at the beginning of the next end step.", null, null, null))
    private val satyr = Cards.invent("Thicketrider Satyr", "{1}{G}", "Creature — Satyr Scout", """
        {T}: Add {G}.
        {1}, {T}: Add one mana of any color.
    """, "1/3")
    private val vanguard = Cards.inventFaces("Adventure",
        face("Stormcrag Vanguard", "{4}{G}{G}", "Creature — Giant Warrior", "This creature can't be blocked by creatures with power 2 or less.", "6/7", null, null),
        face("Bear Under", "{1}{G}", "Sorcery — Adventure",
            "Destroy target artifact or enchantment. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val reeve = Cards.invent("Reeve of Yarns", "{3}{U}", "Creature — Faerie Advisor", """
        Flying
        Whenever you cast an Adventure spell, you may copy it. You may choose new targets for the copy.
    """, "2/3")
    private val pixie = Cards.invent("Jeering Pixie", "{2}{U}", "Creature — Faerie Rogue", """
        Flying
        Instant and sorcery spells you cast cost {1} less to cast.
    """, "2/1")
    private val inquiry = Cards.invent("Unruly Inquiry", "{6}{U}", "Instant", """
        This spell costs {1} less to cast for each creature that attacked this turn.
        Draw three cards.
    """)
    private val stroke = Cards.invent("Scornful Rebuff", "{1}{U}", "Instant", "Counter target spell with mana value 4 or greater.")
    private val adder = Cards.inventFaces("Adventure",
        face("Searing Adder", "{1}{R}", "Creature — Elemental Snake",
            "Whenever an opponent casts a spell with mana value 3 or less, this creature deals 1 damage to that player.", "2/1", null, null),
        face("Steam Rinse", "{1}{U}", "Sorcery — Adventure",
            "Return target nonland permanent to its owner's hand. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val trebuchet = Cards.invent("Wayward Trebuchet", "{2}{R}", "Artifact Creature — Construct", """
        Defender
        {T}: This creature deals 1 damage to each opponent.
        Whenever you cast an instant or sorcery spell, untap this creature.
    """, "0/4")

    private val adventureMouse = Cards.inventFaces("Adventure",
        face("Burrow Scamper", "{W}", "Creature — Mouse", "", "2/1", null, null),
        face("Hop Along", "{W}", "Sorcery — Adventure", "Target creature you control gets +1/+1 until end of turn.", null, null, null))
    private val bear = Cards.invent("Fenwood Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val boar = Cards.invent("Fenwood Boar", "{2}{G}", "Creature — Boar", "", "3/3")
    private val ox = Cards.invent("Brindle Ox", "{2}{W}", "Creature — Ox", "", "2/4")
    private val husk = Cards.invent("Marsh Husk", "{3}{R}", "Creature — Zombie", "", "4/4")
    private val spark = Cards.invent("Tinder Spark", "{R}", "Instant", "Tinder Spark deals 3 damage to target creature.")
    private val volley = Cards.invent("Pebble Volley", "{1}{R}", "Instant", "Pebble Volley deals 2 damage to target creature.")
    private val musing = Cards.invent("Quiet Musing", "{U}", "Instant", "Draw a card.")
    private val nudge = Cards.invent("Tidal Nudge", "{U}", "Instant", "Tap target creature.")
    private val idol = Cards.invent("Spindle Idol", "{2}", "Artifact", "")

    // CR 303.4, 613.4c (+2/+2), 113.6m: the graveyard-return ability works from the graveyard and the Aura goes to its owner's hand
    @Test
    fun lineageGivesPlusTwoPlusTwo() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(lineage)
        alice.casts(lineage, target = bear)
        resolveAll()
        expect { bear.power shouldBe 4; bear.toughness shouldBe 4 }
    }

    @Test
    fun lineageReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(lineage)
        alice.activates(lineage)
        resolveAll()
        expect { alice.hand has lineage }
    }

    // CR 303.4, 502.3, 603.6c: tapped on entering and stays tapped; dying sends the Aura to the graveyard: pay {1}, scry 1, draw
    @Test
    fun frostbiteTapsAndPaysToDrawWhenItGoesToTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(frostbite, spark)
        alice.library("Forest")
        bob.battlefield(bear)
        alice.casts(frostbite, target = bear)
        resolveAll()
        expect { bear.tapped shouldBe true }
        alice.casts(spark, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard; frostbite.zone shouldBe Zone.Graveyard }
        answer(Answer.YesNo(true))
        paySuggested()
        answer(Answer.Objects(emptyList()))
        resolveAll()
        expect { alice.hand has "Forest" }
    }

    @Test
    fun frostbiteDeclinedPaymentDrawsNothing() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(frostbite, spark)
        alice.library("Forest")
        bob.battlefield(bear)
        alice.casts(frostbite, target = bear)
        resolveAll()
        alice.casts(spark, target = bear)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect { alice.library has "Forest" }
    }

    // CR 508.1a, 509.1a: can't attack; CR 406.2 and 701.13 (exile): the activated ability exiles the enchanted creature, the Aura then dies
    @Test
    fun hutchedAwayStopsAttackingAndExiles() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(hutch)
        alice.casts(hutch, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.attacks(bear) }
        alice.activates(hutch)
        resolveAll()
        expect { bear.zone shouldBe Zone.Exile; hutch.zone shouldBe Zone.Graveyard }
    }

    // CR 614.1c: enters tapped unless it's your turn; CR 702.36a (flash) lets Alice cast it in Bob's turn
    @Test
    fun whaleEntersUntappedOnYourTurn() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(whale)
        alice.casts(whale)
        resolveAll()
        expect { card("Antlered Tarn-Whale").tapped shouldBe false }
    }

    @Test
    fun whaleEntersTappedOnAnOpponentsTurn() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(whale)
        turn(number = 2, active = bob)
        bob.passes()
        alice.casts(whale)
        resolveAll()
        expect { card("Antlered Tarn-Whale").tapped shouldBe true }
    }

    // CR 702.21a: ward {2}: the opponent's targeting spell is countered unless they pay {2} (Bob has one land)
    @Test
    fun wardCountersAnOpponentsSpellThatCantPay() = scenario {
        alice.battlefield(sprite)
        bob.battlefield("Island")
        bob.hand(nudge)
        turn(priority = bob)
        bob.casts(nudge, target = sprite)
        resolveAll()
        expect { sprite.tapped shouldBe false }
    }

    // CR 715.2, 700.2: the Adventure face targets an attacking creature of an opponent; its owner chooses top or bottom (CR 400.3)
    @Test
    fun tarnPlungeLetsTheOwnerChoose() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(whale)
        bob.battlefield(bear, ox)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(bear)
        bob.passes()
        alice.casts("Tarn Plunge", target = bear, way = CastWay(face = 1))
        resolveAll()
        bob.answers(Answer.Option("bottom"))
        expect { bear.zone shouldBe Zone.Library }
    }

    // CR 122.1g, 614.1c, 701.21 (untap): three stun counters; each untap removes one counter and the Sprite stays tapped until none are left
    @Test
    fun spriteEntersStunnedAndEachUntapRemovesACounter() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(sprite)
        alice.casts(sprite)
        resolveAll()
        expect { sprite.tapped shouldBe true; sprite.counters(CounterKind.Stun) shouldBe 3 }
        repeat(3) {
            alice.activates(sprite)
            resolveAll()
            expect { sprite.tapped shouldBe true }
        }
        expect { sprite.counters(CounterKind.Stun) shouldBe 0 }
        alice.activates(sprite)
        resolveAll()
        expect { sprite.tapped shouldBe false }
    }

    // CR 715.2, 603.7, 400.7: exile a nontoken creature; it comes back at the beginning of the next end step under its owner's control
    @Test
    fun briefSpiralReturnsTheCreatureAtEndOfTurn() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(twosome)
        bob.battlefield(bear)
        alice.casts("Brief Spiral", target = bear, way = CastWay(face = 1))
        resolveAll()
        expect { bear.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    // CR 602.5, 605: the Satyr's two mana abilities both need its tap: after one, the other can't be activated
    @Test
    fun satyrsAbilitiesShareTheTap() = scenario {
        alice.battlefield(satyr, "Forest")
        alice.activates(satyr, ability = 0)
        assertFailsWith<IllegalArgumentException> { alice.activates(satyr, ability = 1) }
    }

    @Test
    fun satyrMakesOneManaOfAnyColorForOne() = scenario {
        alice.battlefield(satyr, "Forest")
        alice.hand(musing)
        alice.activates(satyr, ability = 1)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        if (pending is Decision.ManaPayment) paySuggested()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        alice.casts(musing)
        resolveAll()
        expect { satyr.tapped shouldBe true; musing.zone shouldBe Zone.Graveyard }
    }

    // CR 509.1b: blockers with power 2 or less can't block it; a power-3 creature can
    @Test
    fun vanguardCantBeBlockedByPowerTwoOrLess() = scenario {
        alice.battlefield(vanguard)
        bob.battlefield(bear, boar)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vanguard)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bear to vanguard) }
        bob.blocks(boar to vanguard)
    }

    // CR 715.2, 701.8: Bear Under destroys an artifact
    @Test
    fun bearUnderDestroysAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(vanguard)
        bob.battlefield(idol)
        alice.casts("Bear Under", target = idol, way = CastWay(face = 1))
        resolveAll()
        expect { idol.zone shouldBe Zone.Graveyard }
    }

    // CR 707.10, 707.10c, 715.4: casting an Adventure spell lets Alice copy it with a new target; a creature face cast from hand isn't one
    @Test
    fun reeveCopiesAnAdventureSpellWithNewTargets() = scenario {
        alice.battlefield(reeve, bear, ox, "Plains")
        alice.hand(adventureMouse)
        alice.casts("Hop Along", target = bear, way = CastWay(face = 1))
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target(ox))))
        resolveAll()
        expect { bear.power shouldBe 3; ox.power shouldBe 3 }
    }

    @Test
    fun reeveIgnoresACreatureSpell() = scenario {
        alice.battlefield(reeve, "Plains")
        alice.hand(adventureMouse)
        alice.casts("Burrow Scamper")
        resolveAll()
        expect { alice.battlefield has adventureMouse }
    }

    // CR 601.2f: instants cost {1} less, creatures don't: {1}{R} with one Mountain works, a {1}{G} creature with one Forest doesn't
    @Test
    fun pixieReducesAnInstantButNotACreature() = scenario {
        alice.battlefield(pixie, "Forest")
        alice.hand(volley, bear)
        bob.battlefield(ox)
        alice.casts(bear)
        assertEquals(true, decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    @Test
    fun pixieLetsAnInstantBeCastForOneLess() = scenario {
        alice.battlefield(pixie, "Mountain")
        alice.hand(volley)
        bob.battlefield(bear)
        alice.casts(volley, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
    }

    // CR 601.2f, 508.1a (three players): each creature that attacked this turn, whoever controls it, makes it cost {1} less
    @Test
    fun inquiryCostsLessForEachCreatureThatAttackedThisTurn() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(inquiry)
        alice.library("Forest", "Forest", "Forest")
        bob.battlefield(bear, boar)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(bear, boar)
        bob.passes()
        carol.passes()
        alice.casts(inquiry)
        resolveAll()
        expect { alice.hand has "Forest" }
    }

    @Test
    fun inquiryCostsFullWithoutAnAttack() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(inquiry)
        alice.casts(inquiry)
        assertEquals(true, decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 701.6, 202.3: counters a mana value 4 spell; a mana value 3 spell is no legal target
    @Test
    fun rebuffCountersAFourDropButNotAThreeDrop() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(stroke)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(husk)
        turn(number = 2, active = bob)
        bob.casts(husk)
        bob.passes()
        alice.casts(stroke, target = husk)
        resolveAll()
        expect { husk.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun rebuffHasNoTargetForAThreeDrop() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(stroke)
        bob.battlefield("Forest", "Forest", "Forest")
        bob.hand(boar)
        turn(number = 2, active = bob)
        bob.casts(boar)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(stroke, target = boar) }
    }

    // CR 603.2, 120.3 (three players): damage goes to the opponent who cast the cheap spell only; an expensive spell does nothing
    @Test
    fun adderDamagesOnlyTheOpponentWhoCastACheapSpell() = scenario(players = 3) {
        alice.battlefield(adder)
        carol.battlefield("Forest", "Forest", "Forest", "Mountain", "Mountain", "Mountain", "Mountain")
        carol.hand(bear, husk)
        turn(number = 3, active = carol)
        carol.casts(bear)
        resolveAll()
        expect { carol.life shouldBe 19; bob.life shouldBe 20; alice.life shouldBe 20 }
        carol.casts(husk)
        resolveAll()
        expect { carol.life shouldBe 19 }
    }

    // CR 715.2: Steam Rinse returns a nonland permanent to its owner's hand
    @Test
    fun steamRinseBouncesAPermanent() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(adder)
        bob.battlefield(boar)
        alice.casts("Steam Rinse", target = boar, way = CastWay(face = 1))
        resolveAll()
        expect { bob.hand has boar }
    }

    // CR 603.2, 120.3 (three players): damages each opponent; casting an instant untaps it for a second activation
    @Test
    fun trebuchetDamagesEachOpponentAndUntapsOnAnInstant() = scenario(players = 3) {
        alice.battlefield(trebuchet, "Island")
        alice.hand(musing)
        alice.library("Forest")
        alice.activates(trebuchet)
        resolveAll()
        expect { bob.life shouldBe 19; carol.life shouldBe 19; trebuchet.tapped shouldBe true }
        assertFailsWith<IllegalArgumentException> { alice.activates(trebuchet) }
        alice.casts(musing)
        resolveAll()
        expect { trebuchet.tapped shouldBe false }
        alice.activates(trebuchet)
        resolveAll()
        expect { bob.life shouldBe 18; carol.life shouldBe 18 }
    }
}
