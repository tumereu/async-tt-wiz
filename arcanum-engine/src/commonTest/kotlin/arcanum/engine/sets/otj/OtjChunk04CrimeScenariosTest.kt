package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import kotlin.test.Test

/** OTJ chunk 04: "Whenever you commit a crime" (CR 700.13). */
class OtjChunk04CrimeScenariosTest {
    private val crimeNote = "(Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)"
    private val ledger = Cards.invent("Ledger of Misdeeds", "{1}", "Artifact", "Whenever you commit a crime, you gain 1 life.")
    private val crow = Cards.invent("Crow of Ill Tidings", "{1}{B}", "Creature — Bird",
        "Flying\nWhenever you commit a crime, each opponent loses 1 life and you gain 1 life. This ability triggers only once each turn. $crimeNote", "1/2")
    private val grifter = Cards.invent("Bloodcoin Grifter", "{1}{B}", "Creature — Vampire Rogue",
        "Whenever you commit a crime, put a +1/+1 counter on this creature. This ability triggers only once each turn. $crimeNote\n" +
            "{3}{B}: Target opponent loses 1 life and you gain 1 life.", "1/1")
    private val enforcer = Cards.invent("Overeager Enforcer", "{4}{B}", "Creature — Ogre Mercenary",
        "Whenever you commit a crime during your turn, this creature gains indestructible until end of turn. $crimeNote", "5/4")
    private val varrik = Cards.invent("Varrik, the Penitent", "{1}{B}{B}", "Legendary Creature — Human Warlock",
        "Whenever you commit a crime, exile up to one target black card from your graveyard and copy it. You may cast the copy. If you do, you lose 2 life. " +
            "(Targeting opponents, anything they control, and/or cards in their graveyards is a crime. Copies of permanent spells become tokens.)", "3/3")
    private val vesk = Cards.invent("Vesk, Borrowed Face", "{1}{U}{B}", "Legendary Creature — Shapeshifter",
        "Whenever you commit a crime, put a +1/+1 counter on Vesk. Then you may exile a card from a graveyard. If a creature card was exiled this way, " +
            "you may have Vesk become a copy of that card until end of turn. This ability triggers only once each turn. $crimeNote", "1/4")
    private val volley = Cards.invent("Warning Shot Volley", "{R}", "Instant", "Warning Shot Volley deals 1 damage to any target.")
    private val pilfer = Cards.invent("Tombside Pilfer", "{B}", "Instant", "Exile target card from a graveyard.")
    private val echo = Cards.invent("Echo of the Gunshot", "{U}", "Instant", "Copy target instant or sorcery spell. You may choose new targets for the copy.")
    private val redirect = Cards.invent("Ricochet Redirect", "{U}", "Instant", "Change the target of target spell or ability with a single target.")
    private val deputy = Cards.invent("Spiteful Deputy", "{1}{R}", "Creature — Human", "When this creature enters, target opponent loses 1 life.", "1/1")
    private val extortion = Cards.invent("Midnight Extortion", "{1}{B}", "Sorcery", "Target opponent loses 2 life.")
    private val grizzly = Cards.invent("Dustbowl Grizzly", "{1}{G}", "Creature — Bear", "Trample", "4/4")

    // CR 700.13: a spell targeting an opponent is a crime; with three players each opponent loses 1 life; CR 603.2h: only once each turn
    @Test
    fun targetingAnOpponentIsACrimeOnceEachTurn() = scenario(players = 3) {
        alice.battlefield(crow)
        alice.hand(volley, volley)
        alice.castsWithoutCosts(volley, target = "bob")
        resolveAll()
        alice.castsWithoutCosts(volley, target = "carol")
        resolveAll()
        expect {
            alice.life shouldBe 21
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // CR 700.13: targeting a creature an opponent controls is a crime; targeting your own creature or yourself isn't
    @Test
    fun targetingWhatAnOpponentControlsIsACrimeButNotYourOwn() = scenario {
        alice.battlefield(ledger, grizzly)
        bob.battlefield(deputy)
        alice.hand(volley)
        alice.castsWithoutCosts(volley, target = grizzly)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    // CR 700.13: a card in an opponent's graveyard is a crime's target; a card in your own graveyard isn't
    @Test
    fun targetingACardInAnOpponentsGraveyardIsACrime() = scenario {
        alice.battlefield(ledger)
        alice.graveyard(extortion)
        bob.graveyard(grizzly)
        alice.hand(pilfer)
        alice.castsWithoutCosts(pilfer, target = grizzly)
        resolveAll()
        expect {
            alice.life shouldBe 21
            bob.exile has grizzly
        }
    }

    @Test
    fun targetingACardInYourOwnGraveyardIsNoCrime() = scenario {
        alice.battlefield(ledger)
        alice.graveyard(extortion)
        bob.graveyard(grizzly)
        alice.hand(pilfer)
        alice.castsWithoutCosts(pilfer, target = extortion)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.exile has extortion
        }
    }

    // CR 700.13: activating an ability that targets an opponent is a crime (the creature's own ability counts)
    @Test
    fun activatingAnAbilityThatTargetsAnOpponentIsACrime() = scenario {
        alice.battlefield(grifter)
        alice.activatesWithoutCosts(grifter, target = "bob")
        resolveAll()
        expect {
            grifter.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 700.13: putting a triggered ability that targets an opponent on the Stack is a crime
    @Test
    fun aTriggeredAbilityTargetingAnOpponentIsACrime() = scenario {
        alice.battlefield(ledger)
        alice.hand(deputy)
        alice.castsWithoutCosts(deputy)
        resolveAll()
        expect {
            alice.battlefield has deputy
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 700.13, 707.10: a copy of a spell is put on the Stack, not cast: the copy targeting an opponent is no crime (only the
    // original spell was)
    @Test
    fun aCopyOfASpellIsNoCrime() = scenario {
        alice.battlefield(ledger)
        alice.hand(volley, echo)
        alice.castsWithoutCosts(volley, target = "bob")
        alice.castsWithoutCosts(echo, target = volley)
        resolve()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 21
        }
    }

    // CR 700.13, 115.7: changing a spell's target to an opponent isn't casting, activating or putting a trigger on the Stack: no crime
    @Test
    fun changingATargetToAnOpponentIsNoCrime() = scenario {
        alice.battlefield(ledger, grizzly)
        alice.hand(volley, redirect)
        alice.castsWithoutCosts(volley, target = grizzly)
        alice.castsWithoutCosts(redirect, target = volley)
        resolve()
        answer(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    // CR 603.2, 700.13: "during your turn": a crime in an opponent's turn doesn't trigger it, one in your own does
    @Test
    fun duringYourTurnOnly() = scenario {
        alice.battlefield(enforcer)
        alice.hand(volley)
        turn(number = 2, active = bob, priority = alice)
        alice.castsWithoutCosts(volley, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 19
            enforcer lacksKeyword Keyword.Indestructible.kind
        }
    }

    @Test
    fun inYourTurnItGainsIndestructible() = scenario {
        alice.battlefield(enforcer)
        alice.hand(volley)
        alice.castsWithoutCosts(volley, target = "bob")
        resolveAll()
        expect { enforcer hasKeyword Keyword.Indestructible.kind }
    }

    // CR 707.12: the exiled black card is copied and the copy cast, paying its costs; CR 608.2c: casting it is what "if you do" asks;
    // CR 700.13: the copy's cast targets an opponent too, a second crime (its trigger then has no card to target)
    @Test
    fun castsTheCopyOfABlackCardAndLosesTwoLife() = scenario {
        alice.battlefield("Swamp", "Swamp", varrik)
        alice.graveyard(extortion)
        alice.hand(volley)
        alice.castsWithoutCosts(volley, target = "bob")
        answer(Answer.Targets(listOf(target(extortion))))
        resolve()
        answer(Answer.YesNo(true))
        paySuggested()
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(emptyList()))
        resolveAll()
        expect {
            alice.exile has extortion
            alice.life shouldBe 18
            bob.life shouldBe 17
        }
    }

    // CR 707.2, 613.1a: Vesk becomes a copy of the exiled creature card until end of turn, keeping its +1/+1 counter (CR 122.1)
    @Test
    fun becomesACopyOfTheExiledCreatureUntilEndOfTurn() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield(vesk)
        bob.graveyard(grizzly)
        alice.hand(volley)
        alice.castsWithoutCosts(volley, target = "bob")
        resolve()
        if (pending is Decision.ChooseObjects) alice.chooses(grizzly)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.exile has grizzly
            card(vesk).power shouldBe 5
            card(vesk).toughness shouldBe 5
            card(vesk) hasKeyword Keyword.Trample.kind
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            card(vesk).power shouldBe 2
            card(vesk).toughness shouldBe 5
            card(vesk) lacksKeyword Keyword.Trample.kind
        }
    }
}
