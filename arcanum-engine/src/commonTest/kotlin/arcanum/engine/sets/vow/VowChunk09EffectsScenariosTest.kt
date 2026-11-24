package arcanum.engine.sets.vow

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VOW chunk 09: -1/-1 for each counter on each creature an opponent controls with three players, the activated abilities of a card exiled
 * with a creature, casting from the top of the library only what shares a card type with an exiled card, "exactly thirteen permanents" with
 * a sacrifice and a reflexive trigger, a creature returned attacking with a granted state trigger, revealing the card looked at for life,
 * a basic land sharing no land type, a play-or-cast trigger of any player checking the exiled card, and a cast trigger for a spell named
 * like an exiled card. */
class VowChunk09EffectsScenariosTest {
    private val toxrill = Cards.invent("Vorlax, the Dissolving", "{5}{B}{B}", "Legendary Creature — Slug Horror", """
        At the beginning of each end step, put a slime counter on each creature you don't control.
        Creatures you don't control get -1/-1 for each slime counter on them.
        Whenever a creature you don't control with a slime counter on it dies, create a 1/1 black Slug creature token.
        {U}{B}, Sacrifice a Slug: Draw a card.
    """.trimIndent(), "7/7")
    private val crawler = Cards.invent("Stitchwork Skitterer", "{1}{U}", "Creature — Zombie Horror", """
        {2}{U}: Exile target creature card from your graveyard and put a +1/+1 counter on this creature.
        This creature has all activated abilities of all creature cards exiled with it.
    """.trimIndent(), "1/2")
    private val illuminator = Cards.invent("Gravelight Wisp", "{1}{U}{U}", "Creature — Spirit", """
        Flying
        Whenever this creature enters or attacks, exile a card from a graveyard.
        You may look at the top card of your library any time.
        Once each turn, you may cast a spell from the top of your library if it shares a card type with a card exiled with this creature.
    """.trimIndent(), "2/3")
    private val uprising = Cards.invent("Highland Revolt", "{2}{R}{R}", "Enchantment",
        "At the beginning of your end step, create a 1/1 red Human creature token. Then if you control exactly thirteen permanents, you may sacrifice this enchantment. When you do, it deals 7 damage to any target.")
    private val olivia = Cards.invent("Seraphine, Scarlet Bride", "{4}{B}{R}", "Legendary Creature — Vampire Noble", """
        Flying, haste
        Whenever Seraphine, Scarlet Bride attacks, return target creature card from your graveyard to the battlefield tapped and attacking. It gains "When you don't control a legendary Vampire, exile this creature."
    """.trimIndent(), "3/4")
    private val sorin = Cards.invent("Valdric the Joyless", "{2}{B}{B}", "Legendary Planeswalker — Sorin", """
        [+1]: Look at the top card of your library. You may reveal that card and put it into your hand. If you do, you lose life equal to its mana value.
        [−2]: Create a 2/3 black Vampire creature token with flying and lifelink.
        [−7]: Valdric the Joyless deals 13 damage to any target. You gain 13 life.
    """.trimIndent())
    private val shaman = Cards.invent("Broodcall Mystic", "{3}{G}", "Creature — Human Shaman", """
        Whenever this creature attacks, you may search your library for a basic land card that doesn't share a land type with a land you control, put that card onto the battlefield, then shuffle.
        {5}{G}: Create a 1/1 green Insect creature token. Put X +1/+1 counters on it, where X is the number of basic land types among lands you control. Activate only as a sorcery.
    """.trimIndent(), "3/5")
    private val gatekeeper = Cards.invent("Gravesward Warden", "{1}{R}", "Creature — Vampire", """
        First strike
        When this creature enters, exile a card from a graveyard.
        Whenever a player plays a land or casts a spell, if it shares a card type with the exiled card, this creature deals 2 damage to that player.
    """.trimIndent(), "2/1")
    private val circle = Cards.invent("Ring of Detainment", "{1}{W}", "Enchantment", """
        When this enchantment enters, exile target creature an opponent controls with mana value 3 or less until this enchantment leaves the battlefield.
        Whenever an opponent casts a Vampire spell with the same name as a card exiled with this enchantment, you gain 2 life.
    """.trimIndent())

    private val badger = Cards.invent("Gloam Badger", "{1}{G}", "Creature — Badger", "", "2/2")
    private val sentinel = Cards.invent("Hedge Sentinel", "{1}{W}", "Creature — Human Soldier", "", "1/3")
    private val kestrel = Cards.invent("Skyreach Kestrel", "{1}{W}", "Creature — Bird", "Flying, vigilance", "1/1")
    private val ogre = Cards.invent("Ashcap Ogre", "{4}{R}", "Creature — Ogre", "", "5/5")
    private val cinders = Cards.invent("Sudden Cinders", "{R}", "Instant", "Sudden Cinders deals 5 damage to any target.")
    private val healer = Cards.invent("Salve Mender", "{1}{W}", "Creature — Human Cleric", "{T}: You gain 2 life.", "1/1")
    private val thrall = Cards.invent("Nightfang Thrall", "{1}{B}", "Creature — Vampire", "", "2/1")
    private val fledgling = Cards.invent("Duskwing Fledgling", "{1}{B}", "Creature — Vampire Bat", "", "1/1")

    // CR 613.4c: each creature an opponent controls gets -1/-1 for each slime counter on that creature, not on the others; CR 704.5f, 603.10a:
    // a 1/1 with a counter dies and makes a Slug; Alice's own creature gets nothing (three players)
    @Test
    fun slimeCountersShrinkOpponentsCreatures() = scenario(players = 3) {
        alice.battlefield(toxrill, sentinel)
        bob.battlefield(badger)
        carol.battlefield(kestrel)
        advanceTo(TurnStep.End)
        playOut()
        expect {
            badger.counters(CounterKind.Named("slime")) shouldBe 1
            badger.power shouldBe 1
            badger.toughness shouldBe 1
            carol.graveyard has kestrel
            alice.battlefield has "Slug Token"
            sentinel.counters(CounterKind.Named("slime")) shouldBe 0
            sentinel.toughness shouldBe 3
        }
    }

    // CR 607.2a, 613.1f: after exiling a creature card with an activated ability, this creature has that ability (its own index after its own)
    @Test
    fun hasTheActivatedAbilityOfTheExiledCard() = scenario {
        alice.battlefield(crawler, "Island", "Island", "Island")
        alice.graveyard(healer)
        alice.activates(crawler, target = healer)
        resolveAll()
        expect {
            alice.exile has healer
            crawler.power shouldBe 2
        }
        alice.activates(crawler, ability = 1)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 601.3, 607.2a: with a creature card exiled, a creature spell on top of the library may be cast once this turn
    @Test
    fun castsACreatureSharingATypeFromTheTop() = scenario {
        alice.battlefield("Island", "Island", "Island", "Plains", "Plains")
        alice.hand(illuminator)
        alice.library(sentinel, "Island")
        bob.graveyard(badger)
        alice.casts(illuminator)
        playOut("Gloam Badger")
        expect { bob.exile has badger }
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(sentinel) })
        playOut()
        expect { alice.battlefield has sentinel }
    }

    // CR 205.2a: an instant on top shares no card type with an exiled creature card: it can't be cast from there
    @Test
    fun cantCastAnInstantWhenACreatureWasExiled() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain")
        alice.hand(illuminator)
        alice.library(cinders, "Island")
        bob.graveyard(badger)
        alice.casts(illuminator)
        playOut("Gloam Badger")
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().none { it.card == card(cinders) })
    }

    // CR 608.2c, 603.12: the Human makes thirteen permanents; the enchantment is sacrificed and its reflexive trigger deals 7 damage
    @Test
    fun thirteenPermanentsSacrificeForSevenDamage() = scenario {
        alice.battlefield(uprising, *Array(11) { "Mountain" })
        advanceTo(TurnStep.End)
        playOut("bob")
        expect {
            alice.graveyard has uprising
            bob.life shouldBe 13
        }
    }

    // CR 608.2c: with twelve permanents after the token, nothing is asked and the enchantment stays
    @Test
    fun twelvePermanentsDontOfferTheSacrifice() = scenario {
        alice.battlefield(uprising, *Array(10) { "Mountain" })
        advanceTo(TurnStep.End)
        playOut("bob")
        expect {
            alice.battlefield has uprising
            alice.battlefield has "Human Token"
            bob.life shouldBe 20
        }
    }

    // CR 508.4: the returned creature attacks without being declared and deals its combat damage
    @Test
    fun returnsACreatureTappedAndAttacking() = scenario {
        alice.battlefield(olivia)
        alice.graveyard(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(olivia)
        playOut()
        expect {
            alice.battlefield has ogre
            ogre.tapped shouldBe true
        }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 12 }
    }

    // CR 603.8, 109.5: once Alice controls no legendary Vampire, the returned creature's granted state trigger exiles it
    @Test
    fun returnedCreatureIsExiledWithoutALegendaryVampire() = scenario {
        alice.battlefield(olivia, "Mountain")
        alice.hand(cinders)
        alice.graveyard(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(olivia)
        playOut()
        expect { alice.battlefield has ogre }
        alice.casts(cinders, target = olivia)
        playOut()
        expect {
            alice.graveyard has olivia
            alice.exile has ogre
        }
    }

    // CR 701.20a, 118.12, 202.3: revealing the top card and putting it into the hand costs life equal to its mana value
    @Test
    fun revealsTheTopCardAndLosesItsManaValue() = scenario {
        alice.battlefield(sorin, counters = listOf(CounterKind.Loyalty to 4))
        alice.library(ogre, "Swamp")
        alice.activates(sorin)
        playOut()
        expect {
            alice.hand has ogre
            alice.life shouldBe 15
        }
    }

    // CR 118.12: declining keeps the card on top and the life
    @Test
    fun decliningKeepsTheLife() = scenario {
        alice.battlefield(sorin, counters = listOf(CounterKind.Loyalty to 4))
        alice.library(ogre, "Swamp")
        alice.activates(sorin)
        playOut(may = false)
        expect {
            alice.library has ogre
            alice.life shouldBe 20
        }
    }

    // CR 205.3i: with a Forest, only the other basic land types are offered
    @Test
    fun searchesForABasicLandOfAnotherType() = scenario {
        alice.battlefield(shaman, "Forest")
        alice.library("Forest", "Island", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shaman)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        assertEquals(setOf(card("Island"), card("Plains")), decision<Decision.ChooseObjects>().options.toSet())
        playOut("Island")
        expect { alice.battlefield has "Island" }
    }

    // CR 603.4, 607.2a, 102.1: with a land card exiled, each player who plays a land is dealt 2 damage, Alice too; a spell that shares no card
    // type deals nothing (three players)
    @Test
    fun eachPlayerPlayingALandIsDealtDamage() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(gatekeeper, "Mountain")
        bob.graveyard("Forest")
        bob.hand("Plains")
        bob.library("Island")
        alice.casts(gatekeeper)
        playOut("Forest")
        expect { bob.exile has "Forest" }
        alice.plays("Mountain")
        playOut()
        expect { alice.life shouldBe 18 }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.plays("Plains")
        playOut()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 20
        }
    }

    // CR 603.4: a creature spell shares no card type with an exiled land card
    @Test
    fun aSpellOfAnotherTypeDealsNothing() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(gatekeeper, cinders)
        bob.graveyard("Forest")
        alice.casts(gatekeeper)
        playOut("Forest")
        alice.casts(cinders, target = "bob")
        playOut()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 15
        }
    }

    // CR 607.2a, 201.2: an opponent casting a Vampire spell with the exiled card's name gains Alice 2 life; another name gains nothing
    @Test
    fun opponentCastsAVampireOfTheExiledName() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(circle)
        alice.library("Plains")
        bob.battlefield(thrall, "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(thrall, fledgling)
        bob.library("Swamp")
        alice.casts(circle)
        playOut()
        expect { bob.exile has thrall }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(fledgling)
        playOut()
        expect { alice.life shouldBe 20 }
        bob.casts(thrall)
        playOut()
        expect { alice.life shouldBe 22 }
    }
}
