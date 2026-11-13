package arcanum.engine.sets.dmu

import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The DMU cards of chunk 05 that give the engine something new, played: the invented names carry the oracle text of Ertai's Scorn, Silver
 * Scrutiny, Volshe Tideturner, Braids, Arisen Nightmare, Rulik Mons, Warren Chief, Queen Allenal of Ruadach, Dragon Whelp, Rith, Liberated
 * Primeval, Anointed Peacekeeper, Ratadrabik of Urborg and Defiler of Faith.
 */
class DmuChunk05WardAndConditionsScenariosTest {
    private val trinket = Cards.invent("Chunk Five Test Trinket", "{0}", "Instant", "You gain 1 life.")
    private val charm = Cards.invent("Chunk Five Test Charm", "{0}", "Instant", "You gain 1 life.")
    private val idol = Cards.invent("Chunk Five Test Idol", "{2}", "Artifact", "")
    private val relic = Cards.invent("Chunk Five Test Relic", "{2}", "Artifact", "")
    private val bear = Cards.invent("Chunk Five Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Chunk Five Test Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val kickedSprite = Cards.invent("Chunk Five Test Sprite", "{U}", "Creature — Faerie", "Kicker {1}", "1/1")
    private val blast = Cards.invent("Chunk Five Test Blast", "{R}", "Instant", "Chunk Five Test Blast deals 5 damage to target creature.")
    private val jolt = Cards.invent("Chunk Five Test Jolt", "{R}", "Instant", "Chunk Five Test Jolt deals 2 damage to target creature.")
    private val treasureSpell = Cards.invent("Chunk Five Test Hoard", "{0}", "Sorcery", "Create a Treasure token.")
    private val squireSpell = Cards.invent("Chunk Five Test Muster", "{0}", "Sorcery", "Create a 1/1 white Human creature token.")
    private val hero = Cards.invent("Chunk Five Test Paladin", "{1}{W}", "Legendary Creature — Human Knight", "Flying", "3/3")
    private val knight = Cards.invent("Chunk Five Test Knight", "{1}{W}{W}", "Creature — Human Knight", "", "2/2")
    private val squire = Cards.invent("Chunk Five Test Squire", "{1}{W}", "Creature — Human Soldier", "", "2/1")
    private val elf = Cards.invent("Chunk Five Test Mystic", "{G}", "Creature — Elf Druid", """
        {T}: Add {G}.
        {1}: This creature gets +1/+1 until end of turn.
    """, "1/1")

    private val scorn = Cards.invent("Ertoro's Disdain", "{1}{U}{U}", "Instant", """
        This spell costs {U} less to cast if an opponent cast two or more spells this turn.
        Counter target spell.
    """)
    private val scrutiny = Cards.invent("Argent Inquiry", "{X}{U}{U}", "Sorcery", """
        You may cast this spell as though it had flash if X is 3 or less.
        Draw X cards.
    """)
    private val tideturner = Cards.invent("Volmar Tidebender", "{1}{U}", "Creature — Merfolk Wizard",
        "{T}: Add {U}. Spend this mana only to cast an instant or sorcery spell or a kicked spell.", "1/3")
    private val braids = Cards.invent("Plaits, Waking Nightmare", "{1}{B}{B}", "Legendary Creature — Nightmare",
        "At the beginning of your end step, you may sacrifice an artifact, creature, enchantment, land, or planeswalker. If you do, each opponent may sacrifice a permanent of their choice that shares a card type with it. For each opponent who doesn't, that player loses 2 life and you draw a card.",
        "3/3")
    private val rulik = Cards.invent("Ruvik Mons, Burrow Boss", "{1}{R}{G}{G}", "Legendary Creature — Goblin", """
        Menace
        Whenever Ruvik Mons attacks, look at the top card of your library. If it's a land card, you may put it onto the battlefield tapped. If you didn't put a card onto the battlefield this way, create a 1/1 red Goblin creature token.
    """, "3/3")
    private val queen = Cards.invent("Queen Ellanor of Ruamach", "{G}{W}{W}", "Legendary Creature — Elf Noble", """
        Queen Ellanor of Ruamach's power and toughness are each equal to the number of creatures you control.
        If one or more creature tokens would be created under your control, those tokens plus a 1/1 white Soldier creature token are created instead.
    """, "*/*")
    private val whelp = Cards.invent("Cinder Whelpling", "{2}{R}{R}", "Creature — Dragon", """
        Flying
        {R}: This creature gets +1/+0 until end of turn. If this ability has been activated four or more times this turn, sacrifice this creature at the beginning of the next end step.
    """, "2/3")
    private val rith = Cards.invent("Ryth, Unbound Primordial", "{2}{R}{G}{W}", "Legendary Creature — Dragon", """
        Flying, ward {2}
        Other Dragons you control have ward {2}.
        At the beginning of your end step, if a creature or planeswalker an opponent controlled was dealt excess damage this turn, create a 4/4 red Dragon creature token with flying.
    """, "5/5")
    private val peacekeeper = Cards.invent("Consecrated Warden", "{2}{W}", "Creature — Human Cleric", """
        Vigilance
        As this creature enters, look at an opponent's hand, then choose any card name.
        Spells your opponents cast with the chosen name cost {2} more to cast.
        Activated abilities of sources with the chosen name cost {2} more to activate unless they're mana abilities.
    """, "3/3")
    private val defiler = Cards.invent("Despoiler of Faith", "{3}{W}{W}", "Creature — Phyrexian Human", """
        Vigilance
        As an additional cost to cast white permanent spells, you may pay 2 life. Those spells cost {W} less to cast if you paid life this way. This effect reduces only the amount of white mana you pay.
        Whenever you cast a white permanent spell, create a 1/1 white Soldier creature token.
    """, "5/5")
    private val ratadrabik = Cards.invent("Vexadrabik of Morrow", "{2}{W}{B}", "Legendary Creature — Zombie Wizard", """
        Vigilance, ward {2}
        Other Zombies you control have vigilance.
        Whenever another legendary creature you control dies, create a token that's a copy of that creature, except it's not legendary and it's a 2/2 black Zombie in addition to its other colors and types.
    """, "3/3")

    // ---- Ertai's Scorn

    // CR 601.2f, 118.7c: one opponent cast two spells this turn: {1}{U}, which two Islands pay
    @Test
    fun scornCostsLessAfterOneOpponentCastTwoSpells() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(scorn)
        bob.hand(trinket, charm)
        turn(number = 2, active = bob)
        bob.casts(trinket)
        allPass()
        bob.casts(charm)
        bob.passes()
        carol.passes()
        alice.casts(scorn, target = charm)
        resolveAll()
        expect {
            bob.graveyard has charm
            bob.life shouldBe 21
            alice.graveyard has scorn
        }
    }

    // Two opponents with one spell each aren't one opponent with two: {1}{U}{U}, which two Islands can't pay
    @Test
    fun scornCostsFullWhenEachOpponentCastOneSpell() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(scorn)
        bob.hand(trinket)
        carol.hand(charm)
        turn(number = 2, active = bob)
        bob.casts(trinket)
        bob.passes()
        carol.casts(charm)
        carol.passes()
        alice.casts(scorn, target = charm)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // ---- Silver Scrutiny

    // CR 702.8a, 601.2b, 601.2e, 601.3a: in Bob's turn it can be begun, but an X over 3 makes it illegal
    @Test
    fun scrutinyAtInstantSpeedRefusesXFour() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(scrutiny)
        alice.library("Plains", "Plains", "Plains", "Plains", "Plains")
        turn(number = 2, active = bob, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.casts(scrutiny, x = 4) }
    }

    // CR 702.8a: X of 3 at instant speed
    @Test
    fun scrutinyAtInstantSpeedWithXThree() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(scrutiny)
        alice.library("Plains", "Plains", "Plains", "Plains", "Plains")
        turn(number = 2, active = bob, priority = alice)
        alice.casts(scrutiny, x = 3)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.graveyard has scrutiny
        }
    }

    // CR 307.1: cast as a sorcery, any X
    @Test
    fun scrutinyAtSorcerySpeedWithAnyX() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(scrutiny)
        alice.library("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.casts(scrutiny, x = 4)
        resolveAll()
        expect { alice.hand.size shouldBe 4 }
    }

    // ---- Volshe Tideturner

    // CR 106.6: the mana can't pay for a creature spell that wasn't kicked
    @Test
    fun tideturnerManaDoesntPayForAnUnkickedCreature() = scenario {
        alice.battlefield(tideturner)
        alice.hand(kickedSprite)
        alice.casts(kickedSprite, kicked = 0)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 106.6, 702.33d: it pays for a kicked spell (the kicker paid before the mana, CR 601.2b, 601.2h)
    @Test
    fun tideturnerManaPaysForAKickedCreature() = scenario {
        alice.battlefield(tideturner, "Swamp")
        alice.hand(kickedSprite)
        alice.casts(kickedSprite, kicked = 1)
        (pending as? Decision.ManaPayment)?.let { d ->
            assertTrue(d.suggestion.isNotEmpty(), "Volmar's mana and the Swamp pay {1}{U}")
            answer(Answer.ManaPayment(d.suggestion))
        }
        resolveAll()
        expect {
            alice.battlefield has kickedSprite
            tideturner.tapped shouldBe true
        }
    }

    // ---- Braids, Arisen Nightmare

    // CR 101.4: Bob sacrifices an artifact like Alice's; Carol has none, so she loses 2 life and Alice draws a card
    @Test
    fun braidsEachOpponentSacrificesOrPays() = scenario(players = 3) {
        alice.battlefield(braids, idol, "Swamp")
        alice.library("Plains", "Plains")
        bob.battlefield(relic, "Forest")
        carol.battlefield(bear, "Mountain")
        advanceTo(TurnStep.End)
        resolveAll()
        answer(Answer.YesNo(true))
        alice.chooses(idol)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has idol
            bob.graveyard has relic
            bob.life shouldBe 20
            carol.battlefield has bear
            carol.life shouldBe 18
            alice.hand.size shouldBe 1
        }
    }

    // ---- Rulik Mons, Warren Chief

    // CR 110.5: the land enters tapped, and no token
    @Test
    fun rulikPutsALandAndMakesNoToken() = scenario {
        alice.battlefield(rulik)
        alice.library("Forest", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rulik)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has "Forest"
            "Forest".tapped shouldBe true
            alice.battlefield lacks "Goblin Token"
        }
    }

    // A land not put onto the battlefield (declined) makes a Goblin
    @Test
    fun rulikMakesAGoblinWhenTheLandIsLeft() = scenario {
        alice.battlefield(rulik)
        alice.library("Forest", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rulik)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.battlefield lacks "Forest"
            alice.battlefield has "Goblin Token"
        }
    }

    // ---- Queen Allenal of Ruadach

    // CR 614.1a: a creature token brings a Soldier; a Treasure doesn't
    @Test
    fun queenAddsASoldierOnlyToCreatureTokens() = scenario {
        alice.battlefield(queen)
        alice.hand(treasureSpell, squireSpell)
        alice.casts(treasureSpell)
        resolveAll()
        expect {
            alice.battlefield has "Treasure Token"
            alice.battlefield lacks "Soldier Token"
        }
        alice.casts(squireSpell)
        resolveAll()
        expect {
            alice.battlefield has "Human Token"
            alice.battlefield has "Soldier Token"
            queen.power shouldBe 3
        }
    }

    // ---- Dragon Whelp

    // CR 602.1, 603.7: activated four times, each resolution sees four activations: sacrificed at the end step
    @Test
    fun whelpActivatedFourTimesIsSacrificed() = scenario {
        alice.battlefield(whelp, "Mountain", "Mountain", "Mountain", "Mountain")
        repeat(4) { alice.activates(whelp) }
        resolveAll()
        expect { whelp.power shouldBe 6 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has whelp }
    }

    // Three activations: it stays
    @Test
    fun whelpActivatedThreeTimesStays() = scenario {
        alice.battlefield(whelp, "Mountain", "Mountain", "Mountain")
        repeat(3) { alice.activates(whelp) }
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has whelp }
    }

    // ---- Rith, Liberated Primeval

    // CR 120.4a, 603.4: 5 damage to Bob's 2/2 is excess; the creature died, but an opponent controlled it then
    @Test
    fun rithMakesADragonAfterExcessDamage() = scenario {
        alice.battlefield(rith, "Mountain")
        alice.hand(blast)
        bob.battlefield(bear)
        alice.casts(blast, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.battlefield has "Dragon Token"
        }
    }

    // Exactly lethal damage isn't excess
    @Test
    fun rithMakesNoDragonWithoutExcess() = scenario {
        alice.battlefield(rith, "Mountain")
        alice.hand(jolt)
        bob.battlefield(bear)
        alice.casts(jolt, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.battlefield lacks "Dragon Token"
        }
    }

    // ---- Anointed Peacekeeper

    // CR 605.1a: the named creature's mana ability costs nothing more; its other ability costs {2} more, which Bob can't pay
    @Test
    fun peacekeeperTaxesAbilitiesButNotManaAbilities() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(peacekeeper)
        bob.battlefield(elf, "Forest")
        bob.hand("Island")
        alice.casts(peacekeeper)
        resolve()
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        answer(Answer.CardName(elf))
        resolveAll()
        alice.passes()
        bob.activates(elf, ability = 0)
        expect { bob.pool.size shouldBe 1 }
        bob.activates(elf, ability = 1)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 601.2f: Bob's spell with the chosen name costs {2} more
    @Test
    fun peacekeeperTaxesOpponentsSpellsWithTheName() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(peacekeeper)
        bob.battlefield("Forest", "Forest")
        bob.hand(elf)
        bob.library("Island")
        alice.casts(peacekeeper)
        resolve()
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        answer(Answer.CardName(elf))
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(elf)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // ---- Ratadrabik of Urborg

    // CR 707.9b, 603.10a: the dead legend's copy isn't legendary and is a 2/2 white and black Human Knight Zombie with flying
    @Test
    fun ratadrabikCopiesADeadLegendAsAZombie() = scenario {
        alice.battlefield(ratadrabik, hero, "Mountain")
        alice.hand(blast)
        alice.casts(blast, target = hero)
        resolveAll()
        expect {
            alice.graveyard has hero
            alice.battlefield has hero
            hero.power shouldBe 2
            hero.toughness shouldBe 2
            hero.colors.toSet() shouldBe setOf(Color.White, Color.Black)
            hero.subtypes.toSet() shouldBe setOf(Subtype("Human"), Subtype("Knight"), Subtype("Zombie"))
            assertTrue(Supertype.Legendary !in hero.view.characteristics!!.supertypes, "not legendary")
            hero hasKeyword Keyword.Flying.kind
            hero hasKeyword Keyword.Vigilance.kind
        }
    }

    // ---- Defiler of Faith

    // CR 601.2b, 601.2f: 2 life paid, {1}{W}{W} costs {1}{W}, which two Plains pay
    @Test
    fun defilerLifeReducesWhiteMana() = scenario {
        alice.battlefield(defiler, "Plains", "Plains")
        alice.hand(knight)
        alice.casts(knight)
        answer(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { d ->
            assertEquals(ManaCost.parse("{1}{W}"), d.cost)
            answer(Answer.ManaPayment(d.suggestion))
        }
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.battlefield has knight
            alice.battlefield has "Soldier Token"
        }
    }

    // Two Defilers, life paid twice: {1}{W} loses its {W}; the second {W} reduces no generic mana, so {1} is left, which nothing pays
    @Test
    fun defilerReducesOnlyWhiteMana() = scenario {
        alice.battlefield(defiler, defiler)
        alice.hand(squire)
        alice.casts(squire)
        answer(Answer.YesNo(true))
        answer(Answer.YesNo(true))
        assertEquals(ManaCost.parse("{1}"), decision<Decision.ManaPayment>().cost)
    }
}
