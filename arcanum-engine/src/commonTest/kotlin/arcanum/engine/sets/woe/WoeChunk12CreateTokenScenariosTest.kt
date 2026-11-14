package arcanum.engine.sets.woe

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.hob.pickTargets
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * WOE chunk 12: the invented names carry the oracle text of Ogre Chitterlord, Song of Totentanz, Totentanz Swarm Piper, The
 * Huntsman's Redemption and Three Blind Mice.
 */
class WoeChunk12CreateTokenScenariosTest {
    private val chatterlord = Cards.invent("Ogre Chatterlord", "{4}{R}{R}", "Creature — Ogre Warrior", """
        Menace
        Whenever this creature enters or attacks, create two 1/1 black Rat creature tokens with "This token can't block." Then if you control five or more Rats, each Rat you control gets +2/+0 until end of turn.
    """, "6/5")
    private val song = Cards.invent("Song of Totenwalz", "{X}{R}", "Sorcery",
        "Create X 1/1 black Rat creature tokens with \"This token can't block.\" Creatures you control gain haste until end of turn.")
    private val piper = Cards.invent("Totenwalz, Swarm Piper", "{1}{B}{R}", "Legendary Creature — Human Warlock Bard", """
        Whenever Totenwalz or another nontoken creature you control dies, create a 1/1 black Rat creature token with "This token can't block."
        {1}{B}: Target attacking Rat you control gains deathtouch until end of turn.
    """, "2/3")
    private val reward = Cards.invent("The Huntsman's Reward", "{2}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Create a 3/3 green Beast creature token.
        II — You may sacrifice a creature. If you do, search your library for a creature or basic land card, reveal it, put it into your hand, then shuffle.
        III — Up to two target creatures each get +2/+2 and gain trample until end of turn.
    """)
    private val moles = Cards.invent("Three Blind Moles", "{2}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Create a 1/1 white Mole creature token.
        II, III — Create a token that's a copy of target token you control.
        IV — Creatures you control get +1/+1 and gain vigilance until end of turn.
    """)
    private val jolt = Cards.invent("Quarrybolt Jolt", "{R}", "Instant", "Quarrybolt Jolt deals 3 damage to target creature.")
    private val cellarRat = Cards.invent("Cellar Rat", "{1}", "Creature — Rat", "", "1/1")
    private val squire = Cards.invent("Moss Squire", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val hare = Cards.invent("Briar Hare", "{G}", "Creature — Rabbit", "", "1/1")
    private val boar = Cards.invent("Thicket Boar", "{2}{G}", "Creature — Boar", "", "3/3")

    // CR 608.2c: only two Rats exist after the tokens (one before): "Then if you control five or more" is false, no pump
    @Test
    fun ogreChatterlordDoesNotBoostBelowFiveRats() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", cellarRat)
        alice.hand(chatterlord)
        alice.casts(chatterlord)
        resolveAll()
        assertEquals(2, tokens("Rat Token").size)
        expect { cellarRat.power shouldBe 1 }
    }

    // CR 608.2c: three Rats already plus the two new tokens make five when the condition is checked: each Rat gets +2/+0
    @Test
    fun ogreChatterlordCountsTheNewTokens() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", cellarRat,
            Cards.invent("Barn Rat", "{1}", "Creature — Rat", "", "1/1"), Cards.invent("Pantry Rat", "{1}", "Creature — Rat", "", "1/1"))
        alice.hand(chatterlord)
        alice.casts(chatterlord)
        resolveAll()
        assertEquals(2, tokens("Rat Token").size)
        expect { cellarRat.power shouldBe 3 }
    }

    // CR 603.2: the attack trigger also makes two Rats; three already plus two make five, so Cellar Rat gets +2/+0
    @Test
    fun ogreChatterlordTriggersWhenItAttacks() = scenario {
        alice.battlefield(chatterlord, cellarRat, Cards.invent("Barn Rat", "{1}", "Creature — Rat", "", "1/1"),
            Cards.invent("Pantry Rat", "{1}", "Creature — Rat", "", "1/1"))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(chatterlord)
        resolveAll()
        assertEquals(2, tokens("Rat Token").size)
        expect { cellarRat.power shouldBe 3 }
    }

    // CR 107.3 (X is chosen on casting), 702.10 (haste): three Rats and the Squire gains haste
    @Test
    fun songOfTotenwalzMakesXRatsAndGivesHaste() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", squire)
        alice.hand(song)
        alice.casts(song, x = 3)
        resolveAll()
        assertEquals(3, tokens("Rat Token").size)
        expect { squire hasKeyword Keyword.Haste.kind }
    }

    // CR 700.4, 603.10a (leaves-the-battlefield look back): Totenwalz and a nontoken creature each leave a Rat, a Rat token dying makes none
    @Test
    fun totenwalzMakesRatsForItselfAndNontokenCreaturesOnly() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", piper, squire, cellarRat)
        alice.hand(jolt, jolt, jolt)
        alice.casts(jolt, target = squire)
        resolveAll()
        assertEquals(1, tokens("Rat Token").size)
        alice.casts(jolt, target = "Rat Token")
        resolveAll()
        assertEquals(0, tokens("Rat Token").size)
        expect { piper.zone shouldBe Zone.Battlefield }
    }

    // CR 700.4: Totenwalz dying itself also triggers (it sees itself leave)
    @Test
    fun totenwalzTriggersForItself() = scenario {
        alice.battlefield("Mountain", piper)
        alice.hand(jolt)
        alice.casts(jolt, target = piper)
        resolveAll()
        expect { piper.zone shouldBe Zone.Graveyard }
        assertEquals(1, tokens("Rat Token").size)
    }

    // CR 702.2: an attacking Rat gains deathtouch and kills the larger blocker
    @Test
    fun totenwalzGivesAnAttackingRatDeathtouch() = scenario {
        alice.battlefield("Swamp", "Swamp", piper, cellarRat)
        bob.battlefield(boar)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cellarRat)
        alice.activates(piper, ability = 0, target = cellarRat)
        paySuggested()
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(boar to cellarRat)
        allPass()
        allPass()
        expect { boar.zone shouldBe Zone.Graveyard }
    }

    // CR 714.3b, 111.2: chapter I makes a 3/3 Beast
    @Test
    fun huntsmansRewardChapterOneMakesABeast() = scenario {
        loreChapter(reward, 1)
        resolveAll()
        val beast = tokens("Beast Token").single()
        expect { CardRef(beast, "beast").power shouldBe 3 }
    }

    // CR 701.21a, 701.23 (search), 608.2c ("If you do"): sacrificing the Hare finds the Boar
    @Test
    fun huntsmansRewardChapterTwoSacrificesToSearch() = scenario {
        alice.battlefield(hare)
        loreChapter(reward, 2, "Forest", boar)
        resolveAll()
        may(true)
        alice.chooses(boar)
        resolveAll()
        expect {
            hare.zone shouldBe Zone.Graveyard
            alice.hand has boar
        }
    }

    // CR 115.1, 700.2b: chapter III picks up to two creatures: each gets +2/+2 and trample
    @Test
    fun huntsmansRewardChapterThreePumpsTwoCreatures() = scenario {
        alice.battlefield(boar, hare, squire)
        loreChapter(reward, 3)
        pickTargets(boar, hare)
        resolveAll()
        expect {
            boar.power shouldBe 5
            hare.power shouldBe 3
            boar hasKeyword Keyword.Trample.kind
            hare hasKeyword Keyword.Trample.kind
            squire lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 707.2, 111.2: chapter II copies the Mole token (a token with its own characteristics, not a card)
    @Test
    fun threeBlindMolesChapterTwoCopiesTheToken() = scenario {
        loreChapter(moles, 1)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        resolveAll()
        assertEquals(2, tokens("Mole Token").size)
    }

    // CR 611.2a: chapter IV: creatures get +1/+1 and vigilance until end of turn
    @Test
    fun threeBlindMolesChapterFourPumpsCreatures() = scenario {
        alice.battlefield(squire)
        bob.battlefield(boar)
        loreChapter(moles, 4)
        resolveAll()
        expect {
            squire.power shouldBe 3
            squire hasKeyword Keyword.Vigilance.kind
            boar.power shouldBe 3
        }
    }
}
