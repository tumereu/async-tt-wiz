package arcanum.engine.sets.dsk

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
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
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Duskmourn cards that needed more than their parse: an alternative cost for spells cast from exile, "Do this only
 * once each turn", Enduring cards returning as enchantments, a prevention effect with an additional effect, and a Saga
 * that repeats a process and casts copies of cards.
 */
class DskGapsScenariosTest {
    private val gallery = Cards.invent("Smoldering Gallery", "{4}{R}{R}", "Enchantment",
        "Once each turn, you may pay {0} rather than pay the mana cost for a spell you cast from exile.")
    private val lash = Cards.invent("Ember Lash", "{2}{R}", "Sorcery", "Ember Lash deals 2 damage to each opponent.")
    private val gust = Cards.invent("Cinder Gust", "{2}{R}", "Sorcery", "Cinder Gust deals 2 damage to each opponent.")

    /** "You may cast those cards this turn" on the named cards in exile. */
    private fun Scenario.mayCastFromExile(vararg names: String) {
        effect(alice, Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You), on = names.toList())
    }

    /** The cast of [name] for the alternative cost another permanent gives it, as [Engine.options] lists it. */
    private fun Scenario.grantedWay(name: String): CastWay? = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()
        .firstOrNull { it.card == card(name) && it.way.alternative is AbilityOrigin.FromAbility }?.way

    // CR 601.2a, 601.2b, 601.2f, 118.9: the spell moves to the Stack first, then its costs are determined; "a spell you
    // cast from exile" is the spell cast from where its card was, so the {0} alternative cost applies to it
    @Test
    fun alternativeCostForASpellCastFromExile() = scenario {
        alice.battlefield(gallery)
        alice.exile(lash)
        mayCastFromExile(lash)

        val way = assertNotNull(grantedWay(lash), "The {0} alternative cost is offered")
        alice.casts(lash, way = way)
        allPass()

        expect {
            bob.life shouldBe 18
            alice.graveyard has lash
        }
    }

    // "Once each turn": the second spell cast from exile that turn isn't offered the alternative cost
    @Test
    fun alternativeCostOnceEachTurn() = scenario {
        alice.battlefield(gallery)
        alice.exile(lash, gust)
        mayCastFromExile(lash, gust)

        val way = grantedWay(lash)!!
        alice.casts(lash, way = way)
        allPass()

        assertNull(grantedWay(gust))
        assertFailsWith<IllegalArgumentException> { alice.casts(gust, way = way) }
    }

    // CR 601.2a: a spell cast from the hand isn't cast from exile
    @Test
    fun alternativeCostNotFromTheHand() = scenario {
        alice.battlefield(gallery)
        alice.hand(lash)
        expect { assertNull(grantedWay(lash)) }
    }

    // CR 118.9: the alternative cost is for the spells its controller casts
    @Test
    fun alternativeCostOnlyItsControllersSpells() = scenario {
        bob.battlefield(gallery)
        alice.exile(lash)
        mayCastFromExile(lash)
        expect { assertNull(grantedWay(lash)) }
    }

    // ---- "Do this only once each turn" (CR 603.2h)

    private val gremlin = Cards.invent("Impious Gremlin", "{1}{R}", "Creature — Gremlin", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever another creature you control with power 2 or less enters, you may discard a card. If you do, draw a card. Do this only once each turn.
    """, "2/2")
    private val pup = Cards.invent("Hallway Pup", "{G}", "Creature — Dog", "", "1/1")
    private val stray = Cards.invent("Hallway Stray", "{G}", "Creature — Dog", "", "1/1")
    private val twins = Cards.invent("Twin Strays", "{1}{G}", "Sorcery", "Create two 1/1 green Dog creature tokens.")

    // CR 603.2h: once its controller has discarded and drawn this turn, it no longer triggers
    @Test
    fun doThisOnlyOnceEachTurn() = scenario {
        alice.battlefield(gremlin, "Forest", "Forest")
        alice.hand(pup, stray, "Swamp")
        alice.library("Island", "Plains")

        alice.casts(pup)
        allPass()
        allPass() // the trigger
        answer(Answer.YesNo(true))
        alice.chooses("Swamp")
        expect {
            alice.hand has "Island"
            alice.graveyard has "Swamp"
        }
        alice.casts(stray)
        allPass()
        expect {
            stack.names shouldBe emptyList()
            alice.library has "Plains"
        }
    }

    // CR 603.2h: declining the discard isn't taking the action, so it triggers again
    @Test
    fun declinedActionTriggersAgain() = scenario {
        alice.battlefield(gremlin, "Forest", "Forest")
        alice.hand(pup, stray, "Swamp")
        alice.library("Island")

        alice.casts(pup)
        allPass()
        allPass()
        answer(Answer.YesNo(false))
        alice.casts(stray)
        allPass()
        expect { stack.names.size shouldBe 1 }
        allPass()
        answer(Answer.YesNo(true)) // the only card in hand is discarded
        expect { alice.hand has "Island" }
    }

    // CR 603.2h: two creatures entering at once trigger it twice; once the first instance's action is taken, the second
    // does nothing ("Do this only once each turn")
    @Test
    fun simultaneousTriggersTakeTheActionOnce() = scenario {
        alice.battlefield(gremlin, "Forest", "Forest")
        alice.hand(twins, "Swamp", "Plains")
        alice.library("Island", "Mountain")

        alice.casts(twins)
        allPass()
        if (pending is Decision.OrderTriggers) answer(Answer.Order(listOf(0, 1)))
        expect { stack.names.size shouldBe 2 }
        allPass()
        answer(Answer.YesNo(true))
        alice.chooses("Swamp")
        allPass()
        expect {
            stack.names shouldBe emptyList()
            alice.hand has "Island"
            alice.hand has "Plains"
            alice.library has "Mountain"
        }
    }

    // ---- Enduring (CR 611.2e)

    private val curiosity = Cards.invent("Lasting Curiosity", "{2}{U}{U}", "Enchantment Creature — Cat Glimmer", """
        Flash
        Whenever a creature you control deals combat damage to a player, draw a card.
        When Lasting Curiosity dies, if it was a creature, return it to the battlefield under its owner's control. It's an enchantment. (It's not a creature.)
    """, "4/3")
    private val creatureWatcher = Cards.invent("Census Taker", "{1}{W}", "Creature — Human", "Whenever another creature you control enters, you gain 1 life.", "1/1")
    private val enchantmentWatcher = Cards.invent("Curio Keeper", "{1}{U}", "Creature — Human", "Whenever an enchantment you control enters, draw a card.", "1/1")
    private val push = Cards.invent("Grave Push", "{B}", "Instant", "Destroy target creature.")

    // CR 611.2e: "It's an enchantment" applies as it returns, so it never enters as a creature: "a creature enters"
    // doesn't trigger, "an enchantment enters" does
    @Test
    fun enduringReturnsAsAnEnchantment() = scenario {
        alice.battlefield(curiosity, creatureWatcher, enchantmentWatcher, "Swamp")
        alice.hand(push)
        alice.library("Island")

        alice.casts(push, target = curiosity)
        allPass()
        allPass() // the return trigger
        allPass() // the enchantment watcher
        expect {
            card(curiosity).zone shouldBe Zone.Battlefield
            assertTrue(CardType.Creature !in curiosity.types)
            assertTrue(CardType.Enchantment in curiosity.types)
            alice.life shouldBe 20
            alice.hand has "Island"
            stack.names shouldBe emptyList()
        }
    }

    // ---- A prevention effect with an additional effect (CR 615)

    private val mindflayer = Cards.invent("The Mindflayer", "{U}{U}{U}", "Legendary Enchantment Creature — Nightmare", """
        The Mindflayer can't be blocked.
        If a source you control would deal damage to an opponent, prevent that damage and each opponent mills that many cards.
    """, "10/1")

    /** "Prevent all damage that would be dealt to you." (written in the model, as the cube's veil) */
    private val veil = Cards.define("Gap Veil", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Gap Veil", ManaCost.parse("{2}{W}"), types = listOf(CardType.Enchantment),
        abilities = listOf(Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
            RecipientFilter(players = PlayerFilter.You)))), Affected.Objects(Objects.Self))),
    ))))

    // CR 615.1a, 615.5: the damage is prevented, then each opponent mills that many cards
    @Test
    fun preventThatDamageAndMill() = scenario {
        alice.battlefield(mindflayer, "Mountain")
        alice.hand("Storm Spark")
        bob.library("Island", "Plains", "Swamp", "Forest")

        alice.casts("Storm Spark", target = "bob")
        allPass()

        expect {
            bob.life shouldBe 20
            bob.graveyard has "Island"
            bob.graveyard has "Swamp"
            bob.library.names shouldBe listOf("Forest")
        }
    }

    // CR 615.12: damage that can't be prevented is dealt, and the prevention effect's additional effect still happens
    @Test
    fun unpreventableDamageStillMills() = scenario {
        alice.battlefield(mindflayer, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        bob.library("Island", "Plains", "Swamp", "Forest", "Island", "Island")

        alice.casts("Doomfire", x = 5, target = "bob")
        allPass()

        expect {
            bob.life shouldBe 15
            bob.library.names shouldBe listOf("Island")
        }
    }

    // CR 616.1, 616.1e: two prevention effects apply to the damage to bob; bob chooses which first. The other one's
    // prevention finds no damage left, so it doesn't apply
    @Test
    fun affectedPlayerOrdersPreventions() {
        for (mindflayerFirst in listOf(true, false)) scenario {
            alice.battlefield(mindflayer, "Mountain")
            alice.hand("Storm Spark")
            bob.battlefield(veil)
            bob.library("Island", "Plains", "Swamp", "Forest")

            alice.casts("Storm Spark", target = "bob")
            allPass()
            val choice = pending as Decision.ChooseReplacement
            assertEquals(bob.id, choice.player)
            val pick = choice.options.first { (it.source == card(mindflayer)) == mindflayerFirst }
            answer(Answer.Replacement(pick.index))

            expect {
                bob.life shouldBe 20
                bob.library.names.size shouldBe if (mindflayerFirst) 1 else 4
            }
        }
    }

    // ---- A Saga that repeats a process and casts copies of cards

    private val ledger = Cards.invent("The Ledger of Tamiyo", "{2}{U}", "Legendary Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III — Mill two cards. If two cards that share a card type were milled this way, draw a card and repeat this process.
        IV — Exile any number of target instant, sorcery, and/or Tamiyo planeswalker cards from your graveyard. Copy them. You may cast any number of the copies.
    """)

    // CR 714.2b, 608.2c: chapter I mills two; while two milled cards share a card type, draw and mill again
    @Test
    fun repeatThisProcess() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(ledger)
        alice.library("Island", "Plains", "Swamp", "Elvish Seer", lash, "Forest")

        alice.casts(ledger)
        allPass()
        allPass() // chapter I

        expect {
            alice.graveyard has "Island"
            alice.graveyard has "Plains"
            alice.graveyard has "Elvish Seer"
            alice.graveyard has lash
            alice.hand.names shouldBe listOf("Swamp")
            alice.library.names shouldBe listOf("Forest")
        }
    }

    // CR 707.12, 707.12a, 112.2a: chapter IV exiles the targets and copies them in exile; each copy may be cast, its
    // costs paid; a copy of a card that leaves the Stack for the graveyard ceases to exist (CR 704.5e); CR 714.4: the
    // Saga is sacrificed after its last chapter
    @Test
    fun castCopiesOfExiledCards() = scenario {
        alice.battlefield(ledger, "Mountain", "Mountain", "Mountain", counters = listOf(CounterKind.Lore to 3))
        alice.graveyard(lash, gust)
        alice.library("Island")
        turn(number = 1, active = alice, step = TurnStep.Draw)

        advanceTo(TurnStep.PrecombatMain)
        answer(Answer.Targets(listOf(target(lash), target(gust))))
        allPass()
        alice.chooses(lash) // CR 608.2f: the caster chooses the order the copies are cast in
        // The copy of Ember Lash is cast, paying {2}{R}; the copy of Cinder Gust isn't
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        answer(Answer.YesNo(false))
        expect {
            stack.names shouldBe listOf(lash)
            alice.graveyard has ledger
        }
        allPass()

        expect {
            bob.life shouldBe 18
            alice.exile has lash
            alice.exile has gust
            alice.graveyard lacks lash
            alice.exile.names.size shouldBe 2
            stack.names shouldBe emptyList()
        }
    }

    // ---- Spells cast while another resolves, for an alternative cost another permanent gives

    /** The ways to pay for a spell an effect casts, as offered. */
    private fun Scenario.payOptions(): List<String> = (pending as Decision.ChooseOption).options

    /** Answers the open way-to-pay choice with the option that mentions [text]. */
    private fun Scenario.payWith(text: String) {
        answer(Answer.Option(payOptions().single { text in it }))
    }

    // CR 608.2g, 601.2b, 118.9: a copy cast while chapter IV resolves is a spell cast from exile, so the alternative cost
    // another permanent gives such spells may be announced, as for a cast with priority; "once each turn" leaves the
    // second copy only its mana cost
    @Test
    fun copiesCastByAnEffectUseAGrantedAlternativeCostOnceEachTurn() = scenario {
        alice.battlefield(ledger, counters = listOf(CounterKind.Lore to 3))
        alice.battlefield(gallery, "Mountain", "Mountain", "Mountain")
        alice.graveyard(lash, gust)
        alice.library("Island")
        turn(number = 1, active = alice, step = TurnStep.Draw)

        advanceTo(TurnStep.PrecombatMain)
        answer(Answer.Targets(listOf(target(lash), target(gust))))
        allPass()
        alice.chooses(lash) // CR 608.2f: the caster chooses the order the copies are cast in
        answer(Answer.YesNo(true))
        expect { payOptions() shouldBe listOf("Pay its mana cost {2}{R}", "Pay {0} rather than its mana cost (Smoldering Gallery)") }
        payWith("{0}")
        // The second copy: the {0} was used this turn, so its mana cost is paid without a choice
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        allPass()
        allPass()

        expect {
            bob.life shouldBe 16
            alice.battlefield.names.filter { it == "Mountain" }.size shouldBe 3
            stack.names shouldBe emptyList()
        }
        assertEquals(3, state.battlefield.count { state.obj(it)?.tapped == true }, "only one copy's mana cost was paid")
    }

    // CR 601.2b, 118.9: the alternative cost is the player's choice; paying the mana cost of the first copy leaves the
    // once-each-turn {0} for the second
    @Test
    fun aSpellCastByAnEffectMayStillPayItsManaCost() = scenario {
        alice.battlefield(ledger, counters = listOf(CounterKind.Lore to 3))
        alice.battlefield(gallery, "Mountain", "Mountain", "Mountain")
        alice.graveyard(lash, gust)
        alice.library("Island")
        turn(number = 1, active = alice, step = TurnStep.Draw)

        advanceTo(TurnStep.PrecombatMain)
        answer(Answer.Targets(listOf(target(lash), target(gust))))
        allPass()
        alice.chooses(lash) // CR 608.2f: the caster chooses the order the copies are cast in
        answer(Answer.YesNo(true))
        payWith("mana cost {2}{R}")
        answer(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        answer(Answer.YesNo(true))
        payWith("{0}")
        allPass()
        allPass()

        expect { bob.life shouldBe 16 }
    }

    private val flick = Cards.invent("Ember Flick", "{R}", "Sorcery", "Ember Flick deals 1 damage to each opponent.")

    // CR 118.9a, 702.85a: cascade casts the card from exile without paying its mana cost, itself an alternative cost, so
    // no other alternative cost is offered
    @Test
    fun noOtherAlternativeCostWhenCastWithoutPaying() = scenario {
        alice.battlefield(gallery, "Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Forest", flick, "Island")

        alice.casts("Splintered Envoy")
        allPass()
        answer(Answer.YesNo(true))
        expect {
            assertTrue(pending !is Decision.ChooseOption, "no way-to-pay choice: $pending")
            stack.names shouldBe listOf("Splintered Envoy", flick)
        }
        allPass()
        expect { bob.life shouldBe 19 }
    }
}
