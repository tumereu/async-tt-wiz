package arcanum.engine.view

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keywords that used to be done by hand (D61), now carried out by the engine from their CR definitions. */
class AutomatedKeywordsTest {
    private val bear = Cards.invent("Auto Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Auto Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")

    private val raider = Cards.invent("Auto Dashing Raider", "{3}{R}", "Creature — Human Warrior", """
        Dash {1}{R} (You may cast this spell for its dash cost. If you do, it gains haste, and it's returned from the battlefield to its owner's hand at the beginning of the next end step.)
    """, "3/2")
    private val blitzer = Cards.invent("Auto Blitzing Brute", "{3}{B}", "Creature — Devil", """
        Blitz {1}{B} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
    """, "3/3")
    private val echoer = Cards.invent("Auto Echoing Ape", "{1}{G}", "Creature — Ape", "Echo {1}{G} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)", "3/3")
    private val glacier = Cards.invent("Auto Aging Glacier", "{1}{U}", "Creature — Elemental", "Cumulative upkeep {1} (At the beginning of your upkeep, put an age counter on this permanent, then sacrifice it unless you pay its upkeep cost for each age counter on it.)", "4/4")
    private val mummy = Cards.invent("Auto Wrapped Priest", "{1}{W}", "Creature — Human Cleric", "Embalm {W} ({W}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a white Zombie Human Cleric with no mana cost. Embalm only as a sorcery.)", "2/1")
    private val eternal = Cards.invent("Auto Undying Champion", "{2}{R}", "Creature — Human Warrior", "Eternalize {2}{R} ({2}{R}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Human Warrior with no mana cost. Eternalize only as a sorcery.)", "2/2")
    private val encorer = Cards.invent("Auto Returning Pirate", "{2}{R}", "Creature — Human Pirate", "Encore {1}{R} ({1}{R}, Exile this card from your graveyard: For each opponent, create a token copy that attacks that opponent this turn if able. They gain haste. Sacrifice them at the beginning of the next end step. Activate only as a sorcery.)", "2/2")
    private val mobilizer = Cards.invent("Auto Horde Caller", "{1}{R}", "Creature — Goblin", "Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)", "2/2")
    private val brawler = Cards.invent("Auto Melee Brawler", "{1}{R}", "Creature — Human Warrior", "Melee (Whenever this creature attacks, it gets +1/+1 until end of turn for each opponent you attacked this combat.)", "2/2")
    private val usurper = Cards.invent("Auto Throne Usurper", "{1}{B}", "Creature — Human Rogue", "Dethrone (Whenever this creature attacks the player with the most life or tied for most life, put a +1/+1 counter on it.)", "2/2")
    private val taunter = Cards.invent("Auto Taunting Elf", "{1}{G}", "Creature — Elf", "Provoke (Whenever this creature attacks, you may have target creature defending player controls untap and block it if able.)", "3/3")
    private val rampager = Cards.invent("Auto Rampaging Beast", "{3}{G}", "Creature — Beast", "Rampage 2 (Whenever this creature becomes blocked, it gets +2/+2 until end of turn for each creature blocking it beyond the first.)", "3/3")
    private val shifter = Cards.invent("Auto Shifting Kami", "{2}{B}", "Creature — Spirit", "Soulshift 3 (When this creature dies, you may return target Spirit card with mana value 3 or less from your graveyard to your hand.)", "2/2")
    private val wisp = Cards.invent("Auto Little Wisp", "{1}{W}", "Creature — Spirit", "", "1/1")
    private val thirsty = Cards.invent("Auto Thirsting Ghoul", "{1}{R}", "Creature — Ogre", "Bloodthirst 2 (If an opponent was dealt damage this turn, this creature enters with two +1/+1 counters on it.)", "2/2")
    private val ingester = Cards.invent("Auto Hungry Drone", "{1}{U}", "Creature — Eldrazi Drone", "Ingest (Whenever this creature deals combat damage to a player, that player exiles the top card of their library.)", "2/2")
    private val undaunted = Cards.invent("Auto Brave Stand", "{5}{W}", "Sorcery", "Undaunted (This spell costs {1} less to cast for each opponent.)\nYou gain 5 life.")
    private val ascender = Cards.invent("Auto Rising Spire", "{1}{W}", "Creature — Vampire", "Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the game.)", "2/2")
    private val graveStorm = Cards.invent("Auto Grave Squall", "{B}", "Instant", "Gravestorm (When you cast this spell, copy it for each permanent put into a graveyard this turn. You may choose new targets for the copies.)\nTarget player loses 1 life.")
    private val replicator = Cards.invent("Auto Replicating Bolt", "{R}", "Instant", "Replicate {1} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)\nAuto Replicating Bolt deals 1 damage to any target.")
    private val squad = Cards.invent("Auto Squad Leader", "{1}{W}", "Creature — Human Soldier", "Squad {1} (As an additional cost to cast this spell, you may pay {1} any number of times. When this creature enters, create that many tokens that are copies of it.)", "2/2")
    private val demonstrator = Cards.invent("Auto Shared Lesson", "{1}{U}", "Sorcery", "Demonstrate (When you cast this spell, you may copy it. If you do, choose an opponent to also copy it.)\nDraw a card.")
    private val spectacle = Cards.invent("Auto Showy Strike", "{3}{R}", "Sorcery", "Spectacle {R} (You may cast this spell for its spectacle cost rather than its mana cost if an opponent lost life this turn.)\nAuto Showy Strike deals 2 damage to any target.")
    private val surge = Cards.invent("Auto Surging Insight", "{4}{U}", "Sorcery", "Surge {U} (You may cast this spell for its surge cost if you or a teammate has cast another spell this turn.)\nDraw a card.")
    private val warper = Cards.invent("Auto Warping Scout", "{3}{G}", "Creature — Elf Scout", "Warp {G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)", "3/3")
    private val mayhem = Cards.invent("Auto Mayhem Imp", "{2}{B}", "Creature — Imp", "Mayhem {B} (You may cast this card from your graveyard for {B} if you discarded it this turn. Timing rules still apply.)", "2/2")
    private val recover = Cards.invent("Auto Recovering Lesson", "{1}{B}", "Sorcery", "Draw a card.\nRecover {1}{B} (When a creature is put into your graveyard from the battlefield, you may pay {1}{B}. If you do, return this card from your graveyard to your hand. Otherwise, exile this card.)")
    private val transmuter = Cards.invent("Auto Transmuting Scroll", "{1}{U}{U}", "Sorcery", "Draw a card.\nTransmute {1}{U}{U} ({1}{U}{U}, Discard this card: Search your library for a card with the same mana value as this card, reveal it, put it into your hand, then shuffle. Transmute only as a sorcery.)")
    private val rioter = Cards.invent("Auto Rioting Goblin", "{1}{R}", "Creature — Goblin", "Riot (This creature enters with your choice of a +1/+1 counter or haste.)", "2/2")
    private val ravenous = Cards.invent("Auto Ravenous Maw", "{X}{G}", "Creature — Tyranid", "Ravenous (This creature enters with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters.)", "0/0")
    private val devourer = Cards.invent("Auto Devouring Wurm", "{2}{G}", "Creature — Wurm", "Devour 2 (As this enters, you may sacrifice any number of creatures. This creature enters with twice that many +1/+1 counters on it.)", "2/2")
    private val grafter = Cards.invent("Auto Grafting Ooze", "{2}{G}", "Creature — Ooze", "Graft 2 (This creature enters with two +1/+1 counters on it. Whenever another creature enters, you may move a +1/+1 counter from this creature onto it.)", "0/0")
    private val station = Cards.invent("Auto Orbital Station", "{2}", "Artifact — Spacecraft", "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery.)")

    private val rummager = Cards.invent("Auto Rummaging Imp", "{B}", "Creature — Imp", "{T}, Discard a card: Draw a card.", "1/1")
    private val hydra = Cards.invent("Auto Myriad Hydra", "{4}{G}", "Creature — Hydra", "Myriad (Whenever this creature attacks, for each opponent other than defending player, you may create a token copy that's tapped and attacking that player or a planeswalker they control. Exile the tokens at end of combat.)", "2/2")
    private val amplifier = Cards.invent("Auto Amplified Bear", "{2}{G}", "Creature — Bear", "Amplify 2 (As this creature enters, put two +1/+1 counters on it for each Bear card you reveal in your hand.)", "2/2")
    private val bargainer = Cards.invent("Auto Bargained Insight", "{1}{U}", "Sorcery", "Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)\nDraw a card.")
    private val slinger = Cards.invent("Auto Web Swinger", "{3}{U}", "Creature — Spider Human Hero", "Web-slinging {U} (You may cast this spell for {U} if you also return a tapped creature you control to its owner's hand.)", "2/3")
    private val rogue = Cards.invent("Auto Sneaky Rogue", "{B}", "Creature — Faerie Rogue", "", "1/1")
    private val prowler = Cards.invent("Auto Prowling Cutpurse", "{3}{B}", "Creature — Faerie Rogue", "Prowl {B} (You may cast this for its prowl cost if you dealt combat damage to a player this turn with a Faerie or Rogue.)", "2/2")
    private val haunted = Cards.inventFaces("Transform",
        arcanum.engine.cards.face("Auto Haunted Villager", "{1}{W}", "Creature — Human Peasant",
            "Disturb {1}{W} (You may cast this card transformed from your graveyard for its disturb cost.)", "2/2", null, null),
        arcanum.engine.cards.face("Auto Lingering Shade", null, "Creature — Spirit", "Flying", "1/1", null, null))
    private val partnerA = "Bonded Seeker"
    private val partnerB = "Bonded Finder"

    private fun Scenario.alternativeWay(name: String): CastWay =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(name) && it.way.alternative != null }.way

    private fun Scenario.libraries() = players.forEach { it.library("Forest", "Forest", "Forest", "Forest") }

    // CR 702.109a: cast for its dash cost it has haste and returns to its owner's hand at the beginning of the next end step
    @Test
    fun dash() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(raider)
        libraries()
        alice.casts(raider, way = alternativeWay(raider))
        allPass()
        expect {
            alice.battlefield has raider
            raider hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.hand has raider }
    }

    // CR 702.109a: cast for its mana cost, nothing of that
    @Test
    fun notDashed() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(raider)
        libraries()
        alice.casts(raider)
        allPass()
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.battlefield has raider }
    }

    // CR 702.152a: blitzed, it has haste and "when it dies, draw a card", and is sacrificed at the next end step
    @Test
    fun blitz() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(blitzer)
        libraries()
        alice.casts(blitzer, way = alternativeWay(blitzer))
        allPass()
        expect { blitzer hasKeyword Keyword.Haste }
        advanceTo(TurnStep.End)
        allPass()
        allPass()
        expect {
            alice.graveyard has blitzer
            alice.hand.size shouldBe 1
        }
    }

    // CR 702.30a: at the next upkeep, sacrifice it unless its echo cost is paid; paid, it isn't asked again
    @Test
    fun echoPaidOnce() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(echoer)
        alice.battlefield("Forest", "Forest")
        libraries()
        alice.casts(echoer, paying = listOf("Forest", "Forest#1"))
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        assertTrue(pending is Decision.May || pending is Decision.ManaPayment)
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect { alice.battlefield has echoer }
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect { stack.isEmpty() }
        assertNull(pending)
    }

    // CR 702.30a: declining the echo sacrifices it
    @Test
    fun echoDeclined() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(echoer)
        libraries()
        alice.casts(echoer)
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        alice.answers(Answer.YesNo(false))
        expect { alice.graveyard has echoer }
    }

    // CR 702.24a: an age counter each upkeep, then pay the cost for each one or sacrifice it
    @Test
    fun cumulativeUpkeep() = scenario {
        alice.battlefield(glacier, "Island", "Island")
        libraries()
        turn(number = 2, active = bob)
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            alice.battlefield has glacier
            glacier.counters(CounterKind.Named("age")) shouldBe 1
        }
        advanceTo(TurnStep.Upkeep, turn = 5)
        allPass()
        alice.answers(Answer.YesNo(false))
        expect { alice.graveyard has glacier }
    }

    // CR 702.128a: a white Zombie token copy with no mana cost
    @Test
    fun embalm() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(mummy)
        alice.activates(mummy)
        allPass()
        expect {
            alice.exile has mummy
            alice.battlefield has mummy
            mummy.view.characteristics!!.colors shouldBe listOf(Color.White)
            mummy.view.characteristics!!.manaCost shouldBe null
            mummy.view.characteristics!!.subtypes.map { it.name }.contains("Zombie") shouldBe true
        }
    }

    // CR 702.129a: a 4/4 black Zombie token copy
    @Test
    fun eternalize() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(eternal)
        alice.activates(eternal)
        allPass()
        expect {
            eternal.power shouldBe 4
            eternal.toughness shouldBe 4
            eternal.view.characteristics!!.colors shouldBe listOf(Color.Black)
        }
    }

    // CR 702.141a: a hasty token copy for each opponent, sacrificed at the next end step
    @Test
    fun encore() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard(encorer)
        libraries()
        alice.activates(encorer)
        allPass()
        expect {
            alice.battlefield has encorer
            encorer hasKeyword Keyword.Haste
        }
        // It attacks if able: declaring no attackers isn't offered
        advanceTo(TurnStep.DeclareAttackers)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>().none { it.attacks.isEmpty() })
        alice.attacks(encorer)
        advanceTo(TurnStep.End)
        allPass()
        expect {
            bob.life shouldBe 18
            alice.battlefield.size shouldBe 2
        }
    }

    // CR 702.181a: two tapped and attacking Warriors, sacrificed at the next end step
    @Test
    fun mobilize() = scenario {
        alice.battlefield(mobilizer)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mobilizer)
        allPass()
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.battlefield.size shouldBe 1 }
    }

    // CR 702.121a: +1/+1 for each opponent attacked
    @Test
    fun melee() = scenario {
        alice.battlefield(brawler)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brawler)
        allPass()
        expect { brawler.power shouldBe 3 }
    }

    // CR 702.105a: attacking the player with the most life (tied) puts a +1/+1 counter on it
    @Test
    fun dethrone() = scenario {
        alice.battlefield(usurper)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(usurper)
        allPass()
        expect { usurper.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 702.105a: not when the defending player has less life than another
    @Test
    fun dethroneNotTheMostLife() = scenario {
        alice.battlefield(usurper)
        bob.life = 10
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(usurper)
        allPass()
        expect { usurper.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 702.39a: the provoked creature untaps and must block it
    @Test
    fun provoke() = scenario {
        alice.battlefield(taunter)
        bob.battlefield(bear, tapped = true)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(taunter)
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        allPass()
        expect { bear.tapped shouldBe false }
        assertTrue(Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>().none { it.blocks.isEmpty() })
    }

    // CR 702.23a: +2/+2 for each blocker beyond the first
    @Test
    fun rampage() = scenario {
        alice.battlefield(rampager)
        bob.battlefield(bear, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rampager)
        allPass()
        bob.blocks(bear to rampager, ox to rampager)
        allPass()
        expect { rampager.power shouldBe 5 }
    }

    // CR 702.46a: dying, it may return a Spirit card with mana value 3 or less to its owner's hand
    @Test
    fun soulshift() = scenario {
        alice.battlefield(shifter)
        alice.graveyard(wisp)
        alice.hand("Storm Spark")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = shifter)
        allPass()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(wisp))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        expect { alice.hand has wisp }
    }

    // CR 702.54a: with an opponent dealt damage this turn it enters with two +1/+1 counters
    @Test
    fun bloodthirst() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", thirsty)
        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts(thirsty)
        allPass()
        expect { thirsty.power shouldBe 4 }
    }

    // CR 702.54a: without, it doesn't
    @Test
    fun noBloodthirst() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(thirsty)
        alice.casts(thirsty)
        allPass()
        expect { thirsty.power shouldBe 2 }
    }

    // CR 702.115a: combat damage to a player exiles the top card of their library
    @Test
    fun ingest() = scenario {
        alice.battlefield(ingester)
        bob.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ingester)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.exile has "Island" }
    }

    // CR 702.125a: {1} less for each opponent
    @Test
    fun undaunted() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(undaunted)
        alice.casts(undaunted)
        allPass()
        expect { alice.life shouldBe 25 }
    }

    // CR 702.131a: ten permanents give the city's blessing for the rest of the game
    @Test
    fun ascend() = scenario {
        alice.battlefield(ascender, *Array(9) { "Plains" })
        settle()
        assertTrue(Designation.CitysBlessing in state.player(alice.id).designations)
    }

    // CR 702.69a: copied for each permanent put into a graveyard from the battlefield this turn
    @Test
    fun gravestorm() = scenario {
        alice.battlefield("Swamp", "Mountain", bear)
        alice.hand("Storm Spark", graveStorm)
        alice.casts("Storm Spark", target = bear)
        allPass()
        alice.casts(graveStorm, target = "bob")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        // The copy, then the spell
        allPass()
        allPass()
        expect { bob.life shouldBe 18 }
    }

    // CR 702.56a: a copy for each time the replicate cost was paid
    @Test
    fun replicate() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(replicator)
        alice.casts(replicator, target = "bob", kicked = 2)
        allPass()
        while (pending is Decision.May) alice.answers(Answer.YesNo(false))
        repeat(3) { allPass(); while (pending is Decision.May) alice.answers(Answer.YesNo(false)) }
        expect { bob.life shouldBe 17 }
    }

    // CR 702.157a: a token copy for each time the squad cost was paid
    @Test
    fun squad() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(squad)
        alice.casts(squad, kicked = 1)
        allPass()
        allPass()
        expect { alice.battlefield.names.count { it == squad } shouldBe 2 }
    }

    // CR 702.144a: you may copy it; if you do, an opponent copies it too
    @Test
    fun demonstrate() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(demonstrator)
        players.forEach { it.library("Forest", "Forest") }
        alice.casts(demonstrator)
        allPass()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChoosePlayer) alice.answers(Answer.Player(bob.id))
        repeat(3) { allPass() }
        expect {
            alice.hand.size shouldBe 2
            bob.hand.size shouldBe 1
        }
    }

    // CR 702.137a: the spectacle cost only once an opponent lost life this turn
    @Test
    fun spectacle() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark", spectacle)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(spectacle) })
        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts(spectacle, target = "bob", way = alternativeWay(spectacle))
        allPass()
        expect { bob.life shouldBe 15 }
    }

    // CR 702.117a: the surge cost once you cast another spell this turn
    @Test
    fun surge() = scenario {
        alice.battlefield("Mountain", "Island")
        alice.hand("Storm Spark", surge)
        alice.library("Forest")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts(surge, way = alternativeWay(surge))
        allPass()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 702.185a: cast for its warp cost it is exiled at the next end step, then cast from exile on a later turn
    @Test
    fun warp() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(warper)
        libraries()
        alice.casts(warper, way = alternativeWay(warper))
        allPass()
        expect { alice.battlefield has warper }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.exile has warper }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(warper)
        allPass()
        expect { alice.battlefield has warper }
    }

    // CR 702.187b: discarded this turn, it may be cast from the graveyard for its mayhem cost; not otherwise
    @Test
    fun mayhem() = scenario {
        alice.battlefield("Swamp", rummager)
        alice.hand(mayhem)
        alice.library("Forest")
        alice.graveyard(mayhem)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(mayhem, zone = Zone.Graveyard) })
        alice.activates(rummager, choosing = listOf(card(mayhem, zone = Zone.Hand).let { mayhem }))
        allPass()
        // Only the card discarded this turn, not the one that was in the graveyard before
        val castable = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().filter { state.objOrThrow(it.card).zone == Zone.Graveyard }
        assertEquals(1, castable.size, "only the card discarded this turn: $castable")
        perform(castable.single())
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { alice.battlefield has mayhem }
    }

    // CR 702.116a: for each opponent other than the defending player, a token copy attacking that player, exiled at end of combat
    @Test
    fun myriad() = scenario(players = 3) {
        alice.battlefield(hydra)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hydra)
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
        advanceTo(TurnStep.PostcombatMain)
        expect { alice.battlefield.size shouldBe 1 }
    }

    // CR 702.38a: reveal cards sharing a creature type as it enters, +1/+1 counters for each
    @Test
    fun amplify() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(amplifier, bear)
        alice.casts(amplifier)
        allPass()
        alice.chooses(bear)
        expect { amplifier.power shouldBe 4 }
    }

    // CR 702.166a: an optional additional cost: sacrifice an artifact, enchantment or token
    @Test
    fun bargain() = scenario {
        alice.battlefield("Island", "Island", station)
        alice.hand(bargainer)
        alice.library("Forest", "Forest")
        alice.casts(bargainer, kicked = 1, choosing = listOf(station))
        allPass()
        expect {
            alice.graveyard has station
            alice.hand.size shouldBe 1
        }
    }

    // CR 702.188a: its web-slinging cost returns a tapped creature you control to its owner's hand
    @Test
    fun webSlinging() = scenario {
        alice.battlefield("Island", ox)
        alice.hand(slinger)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(slinger) })
        change(arcanum.engine.change.Changes.tap(state, card(ox)))
        alice.casts(slinger, way = alternativeWay(slinger), choosing = listOf(ox))
        allPass()
        expect {
            alice.battlefield has slinger
            alice.hand has ox
        }
    }

    // CR 702.76a: the prowl cost once a creature sharing a creature type dealt combat damage to a player this turn
    @Test
    fun prowl() = scenario {
        alice.battlefield("Swamp", rogue)
        alice.hand(prowler)
        alice.library("Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rogue)
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(prowler, way = alternativeWay(prowler))
        allPass()
        expect { alice.battlefield has prowler }
    }

    // CR 702.146a, b: cast transformed from the graveyard for its disturb cost
    @Test
    fun disturb() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(haunted)
        alice.casts(haunted)
        allPass()
        expect {
            alice.battlefield has haunted
            haunted.power shouldBe 1
            haunted hasKeyword Keyword.Flying
        }
    }

    // CR 702.124j: as it enters, a target player may search for its partner
    @Test
    fun partnerWith() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(partnerA)
        alice.library("Forest", partnerB, "Forest")
        alice.casts(partnerA)
        allPass()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(alice.id))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(partnerB)
        expect { alice.hand has partnerB }
    }

    // CR 702.59a: a creature put into your graveyard: pay to return it to your hand, or it is exiled
    @Test
    fun recover() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.graveyard(recover)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = bear)
        allPass()
        allPass()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect { alice.hand has recover }
    }

    // CR 702.53a: discard it to find a card with the same mana value
    @Test
    fun transmute() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(transmuter)
        alice.library("Forest", ox, "Forest")
        alice.activates(transmuter)
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(ox)
        expect {
            alice.hand has ox
            alice.graveyard has transmuter
        }
    }

    // CR 702.136a: a +1/+1 counter, or haste
    @Test
    fun riot() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(rioter)
        alice.casts(rioter)
        allPass()
        alice.answers(Answer.Option("haste"))
        expect {
            rioter hasKeyword Keyword.Haste
            rioter.power shouldBe 2
        }
    }

    // CR 702.156a: X +1/+1 counters; X 5 or more draws a card
    @Test
    fun ravenous() = scenario {
        alice.battlefield(*Array(6) { "Forest" })
        alice.hand(ravenous)
        alice.library("Island")
        alice.casts(ravenous, x = 5)
        allPass()
        allPass()
        expect {
            ravenous.power shouldBe 5
            alice.hand has "Island"
        }
    }

    // CR 702.82a: sacrifice creatures as it enters, two +1/+1 counters for each
    @Test
    fun devour() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(devourer)
        alice.casts(devourer)
        allPass()
        alice.chooses(bear)
        expect {
            alice.graveyard has bear
            devourer.power shouldBe 4
        }
    }

    // CR 702.58a: enters with two counters, may move one onto another creature entering
    @Test
    fun graft() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(grafter, bear)
        alice.casts(grafter)
        allPass()
        expect { grafter.power shouldBe 2 }
        alice.casts(bear)
        allPass()
        allPass()
        alice.answers(Answer.YesNo(true))
        expect {
            grafter.power shouldBe 1
            bear.power shouldBe 3
        }
    }

    // CR 702.184a: tap another creature: charge counters equal to its power
    @Test
    fun station() = scenario {
        alice.battlefield(station, ox)
        alice.activates(station, choosing = listOf(ox))
        allPass()
        expect { station.counters(CounterKind.Named("charge")) shouldBe 3 }
    }

    /** No keyword the parser reads is left to the players any more, except those listed in [KeywordMeanings.moments]. */
    // D61
    @Test
    fun theKeywordsStillByHand() {
        assertFalse(KeywordMeanings.moments.keys.any { it == Keyword.Dash.Kind || it == Keyword.Echo.Kind || it == Keyword.Station })
    }
}
