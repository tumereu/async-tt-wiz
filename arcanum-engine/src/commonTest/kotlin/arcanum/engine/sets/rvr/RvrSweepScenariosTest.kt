package arcanum.engine.sets.rvr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.ownAlternative
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Ravnica Remastered sweep: siblings of the set's engine and parser fixes, probed in play (each a regression test once it passes). */
class RvrSweepScenariosTest {
    private val cinderjab = Cards.invent("Cinderjab", "{R}", "Instant", "Cinderjab deals 3 damage to any target.")
    private val wardbound = Cards.invent("Wardbound Paladin", "{2}{W}{W}", "Creature — Human Knight", """
        Protection from red
        All damage that would be dealt to you and other permanents you control is dealt to this creature instead.
    """, "2/4")
    private val stingback = Cards.invent("Stingback Brute", "{3}{R}", "Creature — Beast",
        "Whenever this creature is dealt damage, put a +1/+1 counter on it.", "3/5")
    private val gravewarden = Cards.invent("Gravewarden Ogre", "{2}{B}", "Creature — Ogre Shaman",
        "{1}{B}, {T}: Return target creature card of an opponent's choice from your graveyard to your hand.", "2/2")
    private val barrowHound = Cards.invent("Marshgrip Ogre", "{2}{B}", "Creature — Ogre", "", "3/3")
    private val fangpack = Cards.invent("Fangpack Herald", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val dawnhoof = Cards.invent("Dawnhoof Sentinel", "{3}{W}", "Enchantment Creature — Unicorn", """
        Bestow {2}{W}
        Enchanted creature gets +2/+2.
    """, "2/2")
    private val thicketcall = Cards.invent("Thicketcall Warden", "{2}{G}", "Creature — Elf Druid",
        "You may cast creature spells as though they had flash.", "2/3")
    private val rustbreakDecree = Cards.invent("Rustbreak Decree", "{1}{G}", "Instant", """
        Kicker {2}
        Destroy target artifact. If this spell was kicked, destroy target enchantment.
    """)
    private val rustbreakIdol = Cards.invent("Rustbreak Idol", "{2}", "Artifact", "")
    private val glimmerfold = Cards.invent("Glimmerfold Mimic", "{2}{U}{U}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any creature on the battlefield, except it enters with an additional +1/+1 counter on it.", "2/2")
    private val recallwing = Cards.invent("Recallwing Keeper", "{2}{W}", "Creature — Bird Cleric",
        "When this creature dies, exile another target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.", "1/1")
    private val doomBolt = Cards.invent("Duskmill Dirge", "{1}{B}", "Instant", "Destroy target creature.")
    private val volleyroar = Cards.invent("Volleyroar Totem", "{2}{R}{R}", "Enchantment",
        "Whenever a creature you control enters, it deals damage equal to its power to any target.")
    private val lifebrandOx = Cards.invent("Lifebrand Ox", "{2}{W}", "Creature — Ox", "Lifelink", "3/3")
    private val twinblock = Cards.invent("Twinblock Sentry", "{3}{W}", "Creature — Human Soldier",
        "Whenever this creature blocks or becomes blocked by a creature, you gain 1 life.", "4/6")
    private val ironhide = Cards.invent("Ironhide Steer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val groundling = Cards.invent("Groundling Squire", "{1}{W}", "Creature — Human", "", "2/2")
    private val steelGuard = Cards.invent("Echoing Colossus", "{2}", "Artifact — Equipment", "Equip {2}")
    private val ensoul = Cards.invent("Rallying Warcry Hound", "{1}{U}", "Sorcery",
        "Target noncreature artifact becomes a 4/4 artifact creature until end of turn.")

    /** An effect of [controller]'s on their opponents: they can't cast spells matching [spells] (CR 101.2). */
    private fun Scenario.opponentsCantCast(controller: arcanum.engine.scenario.ScenarioPlayer, spells: ObjectFilter) {
        effect(controller, Mechanic.CantCast(spells), affects = Affected.Players(Players.Each(PlayerFilter.Opponent)), duration = Duration.Indefinitely)
    }

    // CR 614.9, 616.1, 702.16e: damage a redirection effect sends to a new recipient then meets that recipient's own replacement and
    // prevention effects: the Paladin has protection from red, so the redirected damage is prevented
    @Test
    fun redirectedDamageMeetsTheNewRecipientsProtection() = scenario {
        alice.battlefield(wardbound, "Mountain")
        alice.hand(cinderjab)
        alice.casts(cinderjab, target = "alice")
        resolveAll()
        expect {
            alice.life shouldBe 20
            wardbound.view.obj?.damage shouldBe 0
        }
    }

    // CR 608.2f (Band Together's sibling with three players): the creatures' damage to another player's creature is one damage event
    @Test
    fun eachCreaturesDamageFromAPlayersCreaturesIsOneEvent() = scenario(players = 3) {
        val volley = Cards.invent("Fangpack Volley", "{2}{R}", "Instant",
            "Up to two target creatures you control each deal damage equal to their power to another target creature.")
        alice.battlefield(fangpack, ironhide, "Mountain", "Mountain", "Mountain")
        alice.hand(volley)
        carol.battlefield(stingback)
        alice.casts(volley, targets = listOf(fangpack, ironhide, stingback))
        resolveAll()
        val events = state.log.map { it.change }.filterIsInstance<Change.Damage>()
        assertEquals(1, events.size, events.toString())
        expect { stingback.view.obj?.damage shouldBe 4 }
    }

    // CR 603.2c, 608.2f: "whenever this creature is dealt damage" triggers once for one damage event, however many sources deal damage
    // in it (unlike "whenever a source deals damage to …"). Out of the suite: the engine triggers once per source (EventMatcher's Damage
    // case makes one trigger event per DamageDealt part); it needs the API to tell "is dealt damage" from "a source deals damage" and the
    // matcher to group parts by recipient, summing the amount, for the former (combat damage from several blockers alike).
    @Ignore
    @Test
    fun isDealtDamageTriggersOnceForOneDamageEvent() = scenario(players = 3) {
        val volley = Cards.invent("Fangpack Barrage", "{2}{R}", "Instant",
            "Up to two target creatures you control each deal damage equal to their power to another target creature.")
        alice.battlefield(fangpack, ironhide, "Mountain", "Mountain", "Mountain")
        alice.hand(volley)
        carol.battlefield(stingback)
        alice.casts(volley, targets = listOf(fangpack, ironhide, stingback))
        resolveAll()
        expect { stingback.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 601.2c, CR 801.5a's example: an activated ability's target "of an opponent's choice": the controller picks the opponent, who chooses
    @Test
    fun activatedAbilityTargetOfAnOpponentsChoice() = scenario(players = 3) {
        alice.battlefield(gravewarden, "Swamp", "Swamp")
        alice.graveyard(barrowHound, fangpack)
        val origin = view()[card(gravewarden)]!!.characteristics!!.abilities.first { it.ability is Ability.Activated }.origin
        perform(Input.Activate(alice.id, card(gravewarden), origin))
        val who = pending as Decision.ChoosePlayer
        assertEquals(setOf(bob.id, carol.id), who.options.toSet())
        alice.answers(Answer.Player(carol.id))
        val pick = pending as Decision.Targets
        assertEquals(carol.id, pick.player)
        carol.answers(Answer.Targets(listOf(target(fangpack))))
        while (pending is Decision.ManaPayment) alice.answers(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(fangpack)
            alice.graveyard.names shouldBe listOf(barrowHound)
        }
    }

    // CR 601.3e, 718.3b: a prototyped spell is tested with its prototype's characteristics: mana value 2 isn't prohibited by "can't cast
    // spells with mana value 4 or greater"
    @Test
    fun aPrototypedSpellIsTestedWithItsPrototypeManaValue() = scenario(players = 3) {
        opponentsCantCast(bob, ObjectFilter.ManaValue(Comparison.AtLeast, arcanum.api.Value.Fixed(4)))
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Smelter Colossus")
        alice.casts("Smelter Colossus", way = ownAlternative("Smelter Colossus"))
        resolveAll()
        expect { "Smelter Colossus".power shouldBe 2 }
    }

    // CR 601.3e, 718.3b: prototyped it is red, so "can't cast red spells" prohibits it, though the card is colourless
    @Test
    fun aPrototypedSpellIsTestedWithItsPrototypeColour() = scenario {
        opponentsCantCast(bob, ObjectFilter.OfColor(Color.Red))
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Smelter Colossus")
        assertFailsWith<IllegalArgumentException> { alice.casts("Smelter Colossus", way = ownAlternative("Smelter Colossus")) }
    }

    // CR 601.3a, 702.103b: cast bestowed it is an Aura spell, not a creature spell: "can't cast creature spells" doesn't stop it
    @Test
    fun aBestowedSpellIsntACreatureSpell() = scenario(players = 3) {
        opponentsCantCast(carol, ObjectFilter.Type(CardType.Creature))
        alice.battlefield(groundling, "Plains", "Plains", "Plains")
        alice.hand(dawnhoof)
        alice.casts(dawnhoof, way = ownAlternative(dawnhoof), target = groundling)
        resolveAll()
        expect { groundling.power shouldBe 4 }
    }

    // CR 601.3a, 601.2e, 702.103b: cast bestowed it is a noncreature (Aura) spell: "can't cast noncreature spells" prohibits it as proposed
    @Test
    fun aBestowedSpellIsANoncreatureSpell() = scenario {
        opponentsCantCast(bob, ObjectFilter.Not(ObjectFilter.Type(CardType.Creature)))
        alice.battlefield(groundling, "Plains", "Plains", "Plains")
        alice.hand(dawnhoof)
        assertFailsWith<IllegalArgumentException> { alice.casts(dawnhoof, way = ownAlternative(dawnhoof), target = groundling) }
    }

    // CR 601.3b, 702.103b: "cast creature spells as though they had flash" doesn't reach a bestowed spell, an Aura spell: on another
    // player's turn it can't be cast bestowed
    @Test
    fun creatureFlashDoesntReachABestowedSpell() = scenario(players = 3) {
        alice.battlefield(thicketcall, groundling, "Plains", "Plains", "Plains", "Plains")
        alice.hand(dawnhoof)
        turn(number = 2, active = bob, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.casts(dawnhoof, way = ownAlternative(dawnhoof), target = groundling) }
        alice.casts(dawnhoof)
        resolveAll()
        expect { dawnhoof.power shouldBe 2 }
    }

    // CR 707.9e, 614.1c (Mirrored Self's sibling): "except it enters with an additional counter" is an exception of the copy effect: not
    // copying, it enters without the counter
    @Test
    fun notCopyingEntersWithoutTheCopysAdditionalCounter() = scenario {
        alice.battlefield(fangpack, "Island", "Island", "Island", "Island")
        alice.hand(glimmerfold)
        alice.casts(glimmerfold)
        resolve()
        alice.answers(Answer.Objects(emptyList()))
        resolveAll()
        expect {
            glimmerfold.power shouldBe 2
            glimmerfold.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 707.9e: copying, it enters as the copy with the additional counter
    @Test
    fun copyingEntersWithTheAdditionalCounter() = scenario {
        alice.battlefield(ironhide, "Island", "Island", "Island", "Island")
        alice.hand(glimmerfold)
        alice.casts(glimmerfold)
        resolve()
        alice.chooses(ironhide)
        resolveAll()
        expect { glimmerfold.power shouldBe 3 }
    }

    // CR 603.10a, 610.3c (Voyager Staff's sibling): a dies trigger's "return that card" refers to the card its own ability exiled, though
    // its source has left the battlefield
    @Test
    fun aDiesTriggersExiledCardReturnsAtTheNextEndStep() = scenario(players = 3) {
        alice.battlefield(recallwing, fangpack, "Swamp", "Swamp")
        alice.hand(doomBolt)
        alice.casts(doomBolt, target = recallwing)
        resolveAll()
        expect { alice.exile has fangpack }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has fangpack }
    }

    // CR 603.2, 608.2h (Stalking Vengeance's sibling): in "whenever a creature you control enters, it deals damage equal to its power", the
    // entering creature is the source, so its lifelink applies
    @Test
    fun theEnteringCreatureDealsTheDamage() = scenario(players = 3) {
        alice.battlefield(volleyroar, "Plains", "Plains", "Plains")
        alice.hand(lifebrandOx)
        alice.casts(lifebrandOx)
        resolve()
        assertEquals(true, pending is Decision.Targets, pending.toString())
        alice.answers(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(carol.id))))
        resolveAll()
        expect {
            carol.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 509.3d: "becomes blocked by a creature" triggers once for each creature blocking it
    @Test
    fun blocksOrBecomesBlockedByTriggersForEachBlocker() = scenario {
        alice.battlefield(twinblock)
        bob.battlefield(ironhide, fangpack)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(twinblock)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ironhide to twinblock, fangpack to twinblock)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 601.2c, 702.33d (the addendum fix's sibling): "if this spell was kicked, destroy target enchantment": unkicked, the spell has no
    // second target, so it can be cast with no enchantment on the battlefield
    @Test
    fun anUnkickedSpellHasNoTargetOfItsKickedEffect() = scenario(players = 3) {
        alice.battlefield(rustbreakIdol, "Forest", "Forest")
        alice.hand(rustbreakDecree)
        alice.casts(rustbreakDecree, target = rustbreakIdol, kicked = 0)
        resolveAll()
        expect { alice.graveyard has rustbreakIdol }
    }

    // CR 205.1b (the 'still a land' fix's sibling): "becomes an artifact creature" keeps the object's prior types and subtypes: the
    // Equipment stays an Equipment
    @Test
    fun becomingAnArtifactCreatureKeepsItsSubtypes() = scenario {
        alice.battlefield(steelGuard, "Island", "Island")
        alice.hand(ensoul)
        alice.casts(ensoul, target = steelGuard)
        resolveAll()
        expect {
            steelGuard.power shouldBe 4
            steelGuard.view.characteristics?.hasSubtype(Subtype("Equipment")) shouldBe true
        }
    }
}
