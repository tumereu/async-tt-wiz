package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Chunk 13 of NEO: an opponent who chooses what to exile, a returned artifact that gains haste and is sacrificed at the next end step, a return counting
 * modified creatures, and an "attacks alone" exile you may play. */
class NeoChunk13MoveScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val debt = Cards.invent("Debt to the Kamis", "{2}{B}", "Instant", """
        Choose one —
        • Target opponent exiles a creature they control.
        • Target opponent exiles an enchantment they control.
    """.trimIndent())
    private val craft = Cards.invent("Spirit of Craft", "{4}{R}", "Creature — Spirit",
        "When this creature enters, return target artifact card with mana value 3 or less from your graveyard to the battlefield. It gains haste. Sacrifice it at the beginning of the next end step.", "3/6")
    private val unrelenting = Cards.invent("Unrelenting One", "{2}{B}", "Creature — Spirit", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever this creature attacks, return target creature card with mana value X or less from your graveyard to the battlefield, where X is the number of modified creatures you control. (Equipment, Auras you control, and counters are modifications.)
    """.trimIndent(), "2/3")
    private val still = Cards.invent("Tempered in Stillness", "{1}{R}", "Enchantment",
        "Whenever a creature you control attacks alone, exile the top card of your library. You may play that card this turn.")
    private val scout = Cards.invent("Clockwork Scout", "{2}", "Artifact Creature — Construct", "", "1/1")
    private val colossus = Cards.invent("Clockwork Colossus", "{4}", "Artifact Creature — Construct", "", "4/4")
    private val bear = Cards.invent("Hakkaido Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Fenwick Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val sprite = Cards.invent("Reed Lantern Sprite", "{G}", "Creature — Spirit", "", "1/1")
    private val wisp = Cards.invent("Veil Wisp", "{1}{G}", "Creature — Spirit", "", "2/2")

    // CR 701.21a, 115.1: the opponent chosen as the target picks which creature to exile; the other opponent is untouched (3 players)
    @Test
    fun debtLetsTheTargetedOpponentChooseWhatToExile() = scenario(players = 3) {
        alice.hand(debt)
        alice.battlefield("Swamp", "Swamp", "Swamp")
        bob.battlefield(bear, ox)
        carol.battlefield(sprite)
        alice.casts(debt, target = "bob", modes = listOf(0))
        resolveAll()
        bob.chooses(ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Battlefield
            sprite.zone shouldBe Zone.Battlefield
        }
    }

    // CR 603.6a, 400.7: mana value 3 or less returns; it gains haste and is sacrificed at the beginning of the next end step (CR 603.7)
    @Test
    fun craftReturnsAnArtifactWithHasteThenSacrificesItAtTheEndStep() = scenario {
        alice.hand(craft)
        alice.graveyard(scout, colossus)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.casts(craft)
        resolveAll()
        if (pending != null) alice.chooses(scout)
        resolveAll()
        expect {
            scout.zone shouldBe Zone.Battlefield
            colossus.zone shouldBe Zone.Graveyard
        }
        assertNotNull(view()[card(scout)]!!.characteristics)
        assertTrue(view()[card(scout)]!!.characteristics!!.hasKeyword(Keyword.Haste.kind))
        advanceTo(TurnStep.End)
        resolveAll()
        expect { scout.zone shouldBe Zone.Graveyard }
    }

    // CR 700.9, 608.2b: X counts modified creatures as the ability resolves; a mana value 2 creature is too big with one modified creature
    @Test
    fun unrelentingCountsModifiedCreatures() = scenario {
        alice.battlefield(unrelenting, bear, counters = listOf(p1p1 to 1))
        alice.graveyard(sprite, wisp)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(unrelenting)
        targets(sprite)
        resolveAll()
        expect {
            sprite.zone shouldBe Zone.Battlefield
            wisp.zone shouldBe Zone.Graveyard
        }
    }

    // CR 700.9: with no modified creature, X is 0 and nothing with mana value 1 or more can be returned
    @Test
    fun unrelentingReturnsNothingWithoutModifiedCreatures() = scenario {
        alice.battlefield(unrelenting)
        alice.graveyard(sprite)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(unrelenting)
        resolveAll()
        expect { sprite.zone shouldBe Zone.Graveyard }
    }

    // CR 506.5, 603.2, 400.7: attacking alone exiles the top card, which you may play this turn
    @Test
    fun stillnessExilesTheTopCardWhenACreatureAttacksAlone() = scenario {
        alice.battlefield(still, bear)
        alice.library("Forest", "Plains")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { "Forest".zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Forest")
        expect { "Forest".zone shouldBe Zone.Battlefield }
    }

    // CR 506.5: two attackers is not attacking alone
    @Test
    fun stillnessDoesNothingWhenTwoCreaturesAttack() = scenario {
        alice.battlefield(still, bear, ox)
        alice.library("Forest", "Plains")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear, ox)
        resolveAll()
        expect { "Forest".zone shouldBe Zone.Library }
    }
}
