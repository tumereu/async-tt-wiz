package arcanum.engine.sets.rvr

import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.dgm.ownAlternative
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Ravnica Remastered fix step: general engine fixes found during the set's chunks, in play. */
class RvrFixesScenariosTest {
    private val dustsweepRite = Cards.invent("Dustsweep Rite", "{2}{W}{W}", "Instant",
        "Exile target artifact or enchantment. If you cast this spell during your main phase, you may exile up to one other target artifact or enchantment.")
    private val idol = Cards.invent("Dustwork Idol", "{2}", "Artifact", "")
    private val chalice = Cards.invent("Dustwork Chalice", "{2}", "Artifact", "")
    private val graftwarden = Cards.invent("Graftwarden Adept", "{G/U}{G/U}", "Creature — Elf Wizard", """
        {1}{U}: Attach target Aura attached to a permanent to another permanent with the same controller.
    """, "2/2")
    private val graftshift = Cards.invent("Graftshift Totem", "{1}", "Artifact", "{1}: Attach target Aura you control to target land.")
    private val thornbind = Cards.invent("Thornbind Mantle", "{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
    """)
    private val mossback = Cards.invent("Mossback Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val fernhide = Cards.invent("Fernhide Recluse", "{1}{G}", "Creature — Elf", "", "2/2")
    private val lawbound = Cards.invent("Lawbound Renegade", "{W}{U}", "Legendary Creature — Human Soldier", """
        Each opponent can't cast noncreature spells with mana value greater than the number of lands that player controls.
    """, "2/2")
    private val spiteflare = Cards.invent("Spiteflare", "{X}{R}", "Sorcery", "This spell deals X damage to any target.")
    private val emberPrism = Cards.invent("Ember Prism", "{2}", "Artifact", "{T}: Add {R}.")
    private val packhuntRally = Cards.invent("Packhunt Rally", "{2}{G}", "Instant",
        "Up to two target creatures you control each deal damage equal to their power to another target creature.")
    private val reckoner = Cards.invent("Spiteguard Reckoner", "{2}{R}{R}", "Creature — Minotaur Warrior", "", "6/7")
    private val rallyWolf = Cards.invent("Rallyhorn Wolf", "{2}{G}", "Creature — Wolf", "", "3/3")
    private val rallyBear = Cards.invent("Rallyhorn Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val rubblewarden = Cards.invent("Rubblewarden Shaman", "{R}{G}", "Creature — Human Shaman",
        "{1}{R}{G}: Target land you control becomes a 4/4 Elemental creature until end of turn. It's still a land.", "2/2")
    private val thicketArbor = Cards.invent("Thicket Arbor", null, "Land Creature — Forest Dryad", "", "1/1")
    private val quarrixMastery = Cards.invent("Quarrix's Mastery", "{3}{R}", "Sorcery", """
        Exile target card that's an instant or sorcery from your graveyard. For each card exiled this way, copy it, and you may cast the copy without paying its mana cost. Exile Quarrix's Mastery.
        Overload {5}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val tidewhisper = Cards.invent("Tidewhisper Insight", "{1}{U}", "Instant", "Draw a card.")
    private val dawnmend = Cards.invent("Dawnmend Prayer", "{1}{W}", "Instant", "You gain 2 life.")
    private val turnkey = Cards.invent("Cryptgate Turnkey", "{3}{B}", "Creature — Ogre Rogue",
        "When this creature enters, return target creature card of an opponent's choice from your graveyard to your hand.", "3/2")
    private val gravecallBargain = Cards.invent("Gravecall Bargain", "{1}{B}", "Sorcery",
        "Return target creature card of an opponent's choice from your graveyard to your hand.")
    private val barrowBear = Cards.invent("Barrowmoss Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val barrowWolf = Cards.invent("Barrowmoss Wolf", "{2}{G}", "Creature — Wolf", "", "3/3")
    private val ricochet = Cards.invent("Glancing Ricochet", "{1}{W}", "Instant", """
        The next 1 damage that would be dealt to target creature this turn is dealt to another target creature instead.
        Draw a card.
    """)
    private val duskfen = Cards.invent("Duskfen Stag", "{1}{G}", "Creature — Elk", "", "2/2")
    private val bogstride = Cards.invent("Bogstride Elk", "{2}{G}", "Creature — Elk", "", "3/3")

    // CR 601.2c: the spell's text defines its number of targets as it is cast: in your main phase it has the second target
    @Test
    fun dustsweepRiteInYourMainPhaseExilesTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dustsweepRite)
        bob.battlefield(idol, chalice)
        alice.casts(dustsweepRite, targets = listOf(idol, chalice))
        assertEquals(listOf(target(idol), target(chalice)), state.stackItems.values.single().targets.values.flatten())
        resolveAll()
        may(true)
        expect {
            bob.exile has idol
            bob.exile has chalice
        }
    }

    // CR 601.2c: cast outside your main phase, the spell doesn't have the second target at all (nothing else becomes its target)
    @Test
    fun dustsweepRiteOutsideYourMainPhaseHasOneTarget() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dustsweepRite)
        bob.battlefield(idol)
        carol.battlefield(chalice)
        turn(number = 2, active = bob, step = TurnStep.PostcombatMain, priority = alice)
        alice.casts(dustsweepRite, target = idol)
        assertEquals(listOf(target(idol)), state.stackItems.values.single().targets.values.flatten())
        resolveAll()
        expect {
            bob.exile has idol
            carol.battlefield has chalice
        }
    }

    // CR 701.3b, 303.4d: the other permanent is chosen only among those the Aura can enchant (Bob's creatures, not his land or
    // artifact, nor Carol's creature)
    @Test
    fun graftwardenOffersOnlyPermanentsTheAuraCanEnchant() = scenario(players = 3) {
        alice.battlefield(graftwarden, "Island", "Island")
        bob.battlefield(mossback, fernhide, bogstride, thornbind, "Forest", idol)
        carol.battlefield(duskfen)
        attach(thornbind, mossback)
        alice.activates(graftwarden, ability = 0, target = thornbind)
        allPass()
        assertEquals(setOf(card(fernhide), card(bogstride)), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(bogstride)
        settle()
        expect {
            bogstride.power shouldBe 5
            mossback.power shouldBe 2
        }
    }

    // CR 701.3b: with no other permanent it can enchant, the Aura doesn't move (it isn't moved onto the land and then put into the graveyard)
    @Test
    fun graftwardenWithNoLegalPermanentLeavesTheAura() = scenario {
        alice.battlefield(graftwarden, "Island", "Island")
        bob.battlefield(mossback, thornbind, "Forest", idol)
        attach(thornbind, mossback)
        alice.activates(graftwarden, ability = 0, target = thornbind)
        allPass()
        settle()
        expect {
            bob.battlefield has thornbind
            mossback.power shouldBe 4
        }
    }

    // CR 701.3b: an effect attaching an Aura to an object it can't enchant leaves the Aura where it is
    @Test
    fun auraAttachedToALandItCantEnchantDoesntMove() = scenario {
        alice.battlefield(graftshift, mossback, thornbind, "Forest", "Island")
        attach(thornbind, mossback)
        alice.activates(graftshift, ability = 0, targets = listOf(thornbind, "Forest"), paying = listOf("Island"))
        allPass()
        settle()
        expect {
            alice.battlefield has thornbind
            mossback.power shouldBe 4
        }
    }

    // CR 601.2e, 202.3e: with X announced the spell's mana value is 3, more than Alice's two lands: the proposed spell is illegal and
    // the cast is refused (CR 601.3a let her begin it, X = 0 making it legal)
    @Test
    fun manaValueLimitIsCheckedAfterXIsAnnounced() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", emberPrism)
        alice.hand(spiteflare)
        bob.battlefield(lawbound)
        assertFailsWith<IllegalArgumentException> { alice.casts(spiteflare, x = 2, target = "carol") }
        // the refused answer leaves the cast open; taking it back returns the card
        perform(Input.Cancel(alice.id))
        expect { alice.hand has spiteflare }
    }

    // CR 601.2e: with X = 1 its mana value is 2, no more than her two lands
    @Test
    fun manaValueLimitAllowsASmallEnoughX() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", emberPrism)
        alice.hand(spiteflare)
        bob.battlefield(lawbound)
        alice.casts(spiteflare, x = 1, target = "carol")
        resolveAll()
        expect { carol.life shouldBe 19 }
    }

    // CR 608.2f: the two creatures' damage is one action on several objects, dealt simultaneously: one damage event with both sources
    @Test
    fun eachCreaturesDamageIsDealtAtOnce() = scenario {
        alice.battlefield(rallyWolf, rallyBear, "Forest", "Forest", "Forest")
        alice.hand(packhuntRally)
        bob.battlefield(reckoner)
        alice.casts(packhuntRally, targets = listOf(rallyWolf, rallyBear, reckoner))
        resolveAll()
        val events = state.log.map { it.change }.filterIsInstance<Change.Damage>()
        assertEquals(1, events.size)
        assertEquals(setOf(card(rallyWolf), card(rallyBear)), events.single().dealt.map { it.source }.toSet())
        expect { reckoner.view.obj?.damage shouldBe 5 }
    }

    // CR 205.1b: "It's still a land." keeps all the land's prior types and subtypes: the land creature stays a Forest Dryad and also becomes
    // an Elemental (its creature type isn't replaced)
    @Test
    fun stillALandKeepsItsCreatureTypes() = scenario {
        alice.battlefield(rubblewarden, thicketArbor, "Mountain", "Forest", "Forest")
        alice.activates(rubblewarden, ability = 0, target = thicketArbor, paying = listOf("Mountain", "Forest", "Forest"))
        resolveAll()
        expect {
            thicketArbor.power shouldBe 4
            thicketArbor.view.characteristics?.hasSubtype(Subtype("Dryad")) shouldBe true
            thicketArbor.view.characteristics?.hasSubtype(Subtype("Elemental")) shouldBe true
            thicketArbor.view.characteristics?.hasSubtype(Subtype("Forest")) shouldBe true
        }
    }

    // CR 608.2f: overloaded, the copies can't be cast at once: the caster chooses the order, so the one cast last resolves first
    @Test
    fun overloadedMasteryCastsTheCopiesInTheChosenOrder() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library("Island", "Island")
        alice.hand(quarrixMastery)
        alice.graveyard(tidewhisper, dawnmend)
        alice.casts(quarrixMastery, way = ownAlternative(quarrixMastery))
        resolve()
        val order = pending as Decision.ChooseObjects
        assertEquals(2, order.options.size)
        alice.chooses(dawnmend)
        may(true)
        may(true)
        resolve()
        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.life shouldBe 20
        }
        resolve()
        expect { alice.life shouldBe 22 }
    }

    // CR 601.2c, CR 801.5a's example: "target … of an opponent's choice": the controller picks which opponent, who then chooses the target
    @Test
    fun turnkeyTargetIsChosenByTheOpponentTheControllerPicks() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(turnkey)
        alice.graveyard(barrowBear, barrowWolf)
        alice.casts(turnkey)
        resolve()
        val who = pending as Decision.ChoosePlayer
        assertEquals(setOf(bob.id, carol.id), who.options.toSet())
        alice.answers(Answer.Player(carol.id))
        val pick = pending as Decision.Targets
        assertEquals(carol.id, pick.player)
        carol.answers(Answer.Targets(listOf(target(barrowBear))))
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(barrowBear)
            alice.graveyard.names shouldBe listOf(barrowWolf)
        }
    }

    // CR 614.9: the next 1 damage to the first creature is dealt to the second instead; the rest of that damage stays with the first
    @Test
    fun ricochetRedirectsOnlyTheNextOneDamage() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Mountain", "Mountain", "Mountain", "Mountain", rallyWolf)
        alice.library("Island")
        alice.hand(ricochet, spiteflare)
        bob.battlefield(rallyBear)
        alice.casts(ricochet, targets = listOf(rallyWolf, rallyBear), paying = listOf("Plains", "Plains"))
        resolveAll()
        alice.casts(spiteflare, x = 3, target = rallyWolf)
        resolveAll()
        expect {
            rallyWolf.view.obj?.damage shouldBe 2
            rallyBear.view.obj?.damage shouldBe 1
            alice.hand.names shouldBe listOf("Island")
        }
        // one damage event, the redirected part in it (a replacement modifies the event, CR 614.1)
        val events = state.log.map { it.change }.filterIsInstance<Change.Damage>()
        assertEquals(setOf(card(rallyWolf), card(rallyBear)), events.last().dealt.map { it.recipient.let { r -> (r as arcanum.engine.state.TargetRef.Object).id } }.toSet())
    }

    // CR 601.2c, CR 801.5a's example: casting a spell, the caster picks the opponent who chooses its target
    @Test
    fun spellTargetOfAnOpponentsChoiceIsChosenByThatOpponent() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(gravecallBargain)
        alice.graveyard(barrowBear, barrowWolf)
        perform(Input.Cast(alice.id, card(gravecallBargain)))
        val who = pending as Decision.ChoosePlayer
        assertEquals(setOf(bob.id, carol.id), who.options.toSet())
        alice.answers(Answer.Player(bob.id))
        val pick = pending as Decision.Targets
        assertEquals(bob.id, pick.player)
        bob.answers(Answer.Targets(listOf(target(barrowWolf))))
        while (pending is Decision.ManaPayment) alice.answers(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(barrowWolf)
            alice.graveyard.names shouldBe listOf(barrowBear, gravecallBargain)
        }
    }
}
