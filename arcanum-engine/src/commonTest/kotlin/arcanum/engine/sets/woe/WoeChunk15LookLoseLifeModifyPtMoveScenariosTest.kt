package arcanum.engine.sets.woe

import arcanum.engine.input.CastWay
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.fin.choose
import arcanum.engine.sets.hob.pickTargets
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * WOE chunk 15: the invented names carry the oracle text of Commune with Nature, Pollen-Shield Hare, A Tale for the Ages,
 * Obyra, Wicked Visitor, Dutiful Griffin, Redtooth Vanguard, The Princess Takes Flight and Werefox Bodyguard; the other
 * cards of the chunk are mechanically the same as cards tested in earlier sets.
 */
class WoeChunk15LookLoseLifeModifyPtMoveScenariosTest {
    private val commune = Cards.invent("Briarlit Whisperer", "{G}", "Sorcery",
        "Look at the top five cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in any order.")
    private val hare = Cards.inventFaces("Adventure",
        face("Meadowmere Hare", "{1}{W}", "Creature — Rabbit", "Creature tokens you control get +1/+1.", "2/2", null, null),
        face("Hedgerow Surge", "{G}", "Sorcery — Adventure",
            "Target creature you control gains vigilance and gets +X/+X until end of turn, where X is the number of creatures you control. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val tokenMaker = Cards.invent("Rat Maker", "{B}", "Sorcery", "Create a 1/1 black Rat creature token.")
    private val tale = Cards.invent("Gleamward Tale", "{1}{W}", "Enchantment", "Enchanted creatures you control get +2/+2.")
    private val mantle = Cards.invent("Wardlight Mantle", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val duelist = Cards.invent("Hedgemaze Duelist", "{U}{B}", "Legendary Creature — Faerie Warrior", """
        Flash
        Flying
        Whenever another Faerie you control enters, each opponent loses 1 life.
    """, "2/2")
    private val faerie = Cards.invent("Dusk Sprite", "{B}", "Creature — Faerie", "", "1/1")
    private val visitor = Cards.invent("Gravebell Visitor", "{1}{B}", "Creature — Nightmare",
        "Whenever an enchantment you control is put into a graveyard from the battlefield, each opponent loses 1 life.", "2/2")
    private val shrine = Cards.invent("Mossgrown Shrine", "{1}", "Enchantment", "")
    private val smash = Cards.invent("Grave Hymn", "{1}", "Instant", "Destroy target enchantment.")
    private val griffin = Cards.invent("Lanternlit Griffin", "{3}{W}{W}", "Creature — Griffin", """
        Flying
        {2}{W}, Sacrifice two enchantments: Return this card from your graveyard to your hand.
    """, "4/4")
    private val vanguard = Cards.invent("Thornvault Vanguard", "{1}{G}", "Creature — Elf Warrior", """
        Trample
        Whenever an enchantment you control enters, you may pay {2}. If you do, return this card from your graveyard to your hand.
    """, "3/1")
    private val princess = Cards.invent("Crowned Vigil", "{2}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Exile up to one target creature.
        II — Target creature you control gets +2/+2 and gains flying until end of turn.
        III — Return the exiled card to the battlefield under its owner's control.
    """)
    private val bodyguard = Cards.invent("Hedgehound Bodyguard", "{1}{W}{W}", "Creature — Elf Fox Knight", """
        Flash
        When this creature enters, exile up to one other target non-Fox creature until this creature leaves the battlefield.
        {1}{W}, Sacrifice this creature: You gain 2 life.
    """, "2/2")
    private val bear = Cards.invent("Pilgrim Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bear2 = Cards.invent("Pilgrim Cub", "{1}{G}", "Creature — Bear", "", "1/1")
    private val fox = Cards.invent("Mistmoor Fox", "{1}{W}", "Creature — Fox", "", "2/1")

    // CR 701.20 (look), 701.16 (reveal), 401.4: the creature goes to hand, the other four to the bottom in an order the player picks
    @Test
    fun communeKeepsACreatureAndOrdersTheRest() = scenario {
        alice.library(bear, "Forest", "Island", "Swamp", "Mountain", "Plains")
        alice.battlefield("Forest")
        alice.hand(commune)
        alice.casts(commune)
        resolveAll()
        choose(bear)
        assertNotNull(pending, "the order of the cards put on the bottom is asked")
        choose("Forest", "Island", "Swamp", "Mountain")
        expect {
            alice.hand has bear
            alice.library.size shouldBe 5
            alice.library.names.last() shouldBe "Mountain"
        }
    }

    // CR 608.2c: "may" reveal: taking nothing puts all five on the bottom
    @Test
    fun communeMayTakeNothing() = scenario {
        alice.library(bear, "Forest", "Island", "Swamp", "Mountain", "Plains")
        alice.battlefield("Forest")
        alice.hand(commune)
        alice.casts(commune)
        resolveAll()
        choose()
        expect {
            alice.hand.size shouldBe 0
            alice.library.size shouldBe 6
        }
    }

    // CR 613.4c: creature tokens (not other creatures) you control get +1/+1
    @Test
    fun hareBoostsOnlyCreatureTokens() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", bear)
        alice.hand(hare, tokenMaker)
        alice.casts("Meadowmere Hare")
        resolveAll()
        alice.casts(tokenMaker)
        resolveAll()
        expect {
            "Meadowmere Hare".power shouldBe 2
            bear.power shouldBe 2
            card("Rat Token").power shouldBe 2
        }
    }

    // CR 715.3, 608.2h: the Adventure face counts the creatures you control as it resolves (three): +3/+3 and vigilance
    @Test
    fun hareRaisingGivesPlusXAndVigilance() = scenario {
        alice.battlefield("Forest", bear, bear2, fox)
        alice.hand(hare)
        alice.casts("Hedgerow Surge", target = bear, way = CastWay(face = 1))
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Vigilance.kind
            bear2.power shouldBe 1
        }
    }

    // CR 303.4: only creatures with an Aura attached are enchanted; opponents' enchanted creatures aren't yours
    @Test
    fun taleBoostsEnchantedCreaturesYouControl() = scenario {
        alice.battlefield(tale, bear, bear2, mantle)
        bob.battlefield(Cards.invent("Dunefield Wolf", "{1}{G}", "Creature — Wolf", "", "2/2"), Cards.invent("Wardlight Cloak", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1."))
        attach(mantle, bear)
        attach("Wardlight Cloak", "Dunefield Wolf")
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 5
            bear2.power shouldBe 1
            "Dunefield Wolf".power shouldBe 3
        }
    }

    // CR 603.2, 102.2: three players, each opponent loses 1 life when another Faerie enters
    @Test
    fun obyraDrainsEachOpponentForAnotherFaerie() = scenario(players = 3) {
        alice.battlefield("Swamp", duelist)
        alice.hand(faerie)
        alice.casts(faerie)
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 603.2: Obyra herself entering is not "another"
    @Test
    fun obyraDoesNotTriggerForHerself() = scenario(players = 3) {
        alice.battlefield("Island", "Swamp")
        alice.hand(duelist)
        alice.casts(duelist)
        resolveAll()
        expect {
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 700.4, 603.10a: the enchantment you control destroyed (put into a graveyard from the battlefield) drains each opponent; an opponent's does not
    @Test
    fun visitorDrainsWhenYourEnchantmentIsDestroyedButNotTheirs() = scenario(players = 3) {
        alice.battlefield("Swamp", "Plains", visitor, shrine)
        bob.battlefield(Cards.invent("Hushed Altar", "{1}", "Enchantment", ""))
        alice.hand(smash, Cards.invent("Bracken Whisper", "{1}", "Instant", "Destroy target enchantment."))
        alice.casts("Bracken Whisper", target = "Hushed Altar")
        resolveAll()
        expect {
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
        alice.casts(smash, target = shrine)
        resolveAll()
        expect {
            "Mossgrown Shrine".zone shouldBe Zone.Graveyard
            alice.life shouldBe 20
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 113.6m, 602.2: the ability works from the graveyard; sacrificing two enchantments (and {2}{W}) returns the card
    @Test
    fun griffinReturnsFromTheGraveyardForTwoEnchantments() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", shrine, mantle, tale)
        alice.graveyard(griffin)
        alice.activates(griffin, ability = 0, choosing = listOf(shrine, mantle))
        resolveAll()
        expect {
            alice.hand has griffin
            shrine.zone shouldBe Zone.Graveyard
            mantle.zone shouldBe Zone.Graveyard
            tale.zone shouldBe Zone.Battlefield
        }
    }

    // CR 118.12, 603.2: an enchantment entering lets you pay {2} to return the Vanguard from the graveyard
    @Test
    fun vanguardReturnsWhenYouPayAfterAnEnchantmentEnters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard(vanguard)
        alice.hand(shrine)
        alice.casts(shrine, paying = listOf("Plains"))
        resolveAll()
        may(true)
        paySuggested()
        resolveAll()
        expect { alice.hand has vanguard }
    }

    // CR 118.12: declining the payment leaves it in the graveyard
    @Test
    fun vanguardStaysWhenYouDecline() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard(vanguard)
        alice.hand(shrine)
        alice.casts(shrine, paying = listOf("Plains"))
        resolveAll()
        may(false)
        resolveAll()
        expect { vanguard.zone shouldBe Zone.Graveyard }
    }

    // CR 714.2b, 610.3, 400.7, 303.4: chapter I exiles an opponent's creature, II pumps, III returns it under its owner's control
    @Test
    fun princessExilesThenReturnsTheCreatureUnderItsOwner() = scenario {
        alice.battlefield(bear)
        bob.battlefield(Cards.invent("Dunefield Boar", "{2}{G}", "Creature — Boar", "", "3/3"))
        loreChapter(princess, 1, "Forest", "Forest")
        resolveAll()
        pickTargets("Dunefield Boar")
        resolveAll()
        expect { "Dunefield Boar".zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        resolveAll()
        pickTargets(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying.kind
        }
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        resolveAll()
        expect {
            "Dunefield Boar".zone shouldBe Zone.Battlefield
            "Dunefield Boar".controller shouldBe bob.id
        }
    }

    // CR 610.3, 603.7: the non-Fox creature is exiled until the Bodyguard leaves; sacrificing it returns it and gains 2 life
    @Test
    fun bodyguardExilesUntilItLeavesAndGainsLifeWhenSacrificed() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(bodyguard)
        bob.battlefield(bear, fox)
        alice.casts(bodyguard)
        resolveAll()
        pickTargets(bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Exile }
        alice.activates(bodyguard, ability = 0)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            alice.life shouldBe 22
        }
    }

    // CR 115.1: a Fox is not a legal target; with only a Fox to choose, nothing is exiled
    @Test
    fun bodyguardCannotExileAFox() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(bodyguard)
        bob.battlefield(fox)
        alice.casts(bodyguard)
        resolveAll()
        assertNull(pending)
        expect { fox.zone shouldBe Zone.Battlefield }
    }
}
