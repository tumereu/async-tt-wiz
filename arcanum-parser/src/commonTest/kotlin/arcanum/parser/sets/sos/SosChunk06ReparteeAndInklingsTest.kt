package arcanum.parser.sets.sos

import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 06: repartee (an ability word) — "whenever you cast an instant or sorcery spell that targets a creature"
 * (a spell targeting is part of its cast event, CR 601.2c) — and the white-black Inklings: a blink returning at the next end step (a
 * delayed trigger, CR 603.7), counters passed on as a creature dies (CR 603.10a), and a token for a destroyed creature's controller.
 */
class SosChunk06ReparteeAndInklingsTest {
    private val wordRepartee = "Repartee"
    private fun reparteeTrigger(vararg steps: arcanum.api.Step, targets: List<arcanum.api.TargetSlot> = emptyList()) =
        triggered(repartee, *steps, targets = targets, abilityWord = wordRepartee)

    @Test
    fun commencementMorning() = card(
        name = "Commencement Morning",
        cost = "{W}",
        type = "Enchantment",
        text = "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(reparteeTrigger(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))))

    @Test
    fun wellReadInkscribe() = card(
        name = "Well-Read Inkscribe",
        cost = "{1}{W}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            Vigilance
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, create a 1/1 white and black Inkling creature token with flying.
        """,
    ) parsesTo abilities(Keyword.Vigilance, reparteeTrigger(create(inkling)))

    @Test
    fun inkformExhibitor() = card(
        name = "Inkform Exhibitor",
        cost = "{3}{W}",
        type = "Creature — Elephant Cleric",
        pt = "3/4",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature gets +1/+0 and gains lifelink until end of turn.
        """,
    ) parsesTo abilities(Keyword.Ward(manaCost("{2}")), reparteeTrigger(pump(Objects.Self, 1, 0, has(Keyword.Lifelink))))

    @Test
    fun practicedDisputant() = card(
        name = "Practiced Disputant",
        cost = "{2}{W}",
        type = "Creature — Djinn Bard",
        pt = "3/3",
        text = """
            Vigilance
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Vigilance, reparteeTrigger(pump(Objects.Self, 1, 1)))

    @Test
    fun rousingHopecaller() = card(
        name = "Rousing Hopecaller",
        cost = "{2}{W}",
        type = "Creature — Bird Bard",
        pt = "1/3",
        text = """
            Flying, lifelink
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Lifelink, reparteeTrigger(counters(Objects.Each(creatureYouControl))))

    // CR 702.21a: ward with a cost that isn't mana
    @Test
    fun agoraGravescribe() = card(
        name = "Agora Gravescribe",
        cost = "{5}{B}",
        type = "Creature — Troll Warlock",
        pt = "5/4",
        text = """
            Ward—Discard a card.
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost(discardACard)),
        reparteeTrigger(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun sermonizingSneermage() = card(
        name = "Sermonizing Sneermage",
        cost = "{B}",
        type = "Creature — Human Warlock",
        pt = "1/1",
        text = "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(reparteeTrigger(counters(Objects.Self)))

    @Test
    fun wistfulVersifier() = card(
        name = "Wistful Versifier",
        cost = "{1}{B}",
        type = "Creature — Elf Bard",
        pt = "2/2",
        text = "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, each opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(reparteeTrigger(loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)))

    // CR 603.7: the card returns at the beginning of the next end step; CR 400.7: it is a new object
    @Test
    fun mediatorsFencer() = card(
        name = "Mediator's Fencer",
        cost = "{W}{W}{B}{B}",
        type = "Creature — Kor Warlock",
        pt = "4/3",
        text = """
            When this creature enters, draw a card. Each player loses 1 life.
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, exile up to one target creature. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), loseLife(1, Players.Each(PlayerFilter.Any))),
        reparteeTrigger(
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                    postBind = "returned")))))),
            targets = listOf(slot("creature", and(creature), min = 0)),
        ),
    )

    @Test
    fun inklingPennantBearer() = card(
        name = "Inkling Pennant-Bearer",
        cost = "{W}{B}",
        type = "Creature — Inkling Cat",
        pt = "2/2",
        text = "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature gains flying until end of turn. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(reparteeTrigger(gains(Objects.Self, Keyword.Flying), doing(Effect.Surveil(Players.You, n(1)))))

    // CR 603.10a: the counters it had as it last existed
    @Test
    fun chidingRegistrar() = card(
        name = "Chiding Registrar",
        cost = "{W}{B}",
        type = "Creature — Dwarf Cleric",
        pt = "2/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, put a +1/+1 counter on this creature.
            When this creature dies, if it had counters on it, put those counters on up to one target creature.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        reparteeTrigger(counters(Objects.Self)),
        triggered(diesSelf, doing(Effect.PutCountersOf(target("creature"), of = Objects.EventObject(EventRole.Before))),
            targets = listOf(slot("creature", creature, min = 0)),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(null, atLeast = 1))),
    )

    @Test
    fun eavesdroppingAide() = card(
        name = "Eavesdropping Aide",
        cost = "{1}{W}{B}",
        type = "Creature — Human Cleric",
        pt = "2/3",
        text = """
            Repartee — Whenever you cast an instant or sorcery spell that targets a creature, this creature can't be blocked this turn.
            Whenever this creature deals combat damage to a player, you draw a card and lose 1 life.
        """,
    ) parsesTo abilities(
        reparteeTrigger(untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)),
        triggered(combatDamageToAPlayer, draw(1), loseLife(1)),
    )

    @Test
    fun keenRunecaster() = card(
        name = "Keen Runecaster",
        cost = "{3}{W}",
        type = "Creature — Cat Cleric",
        pt = "3/3",
        text = "When this creature enters, create a 1/1 white and black Inkling creature token with flying.",
    ) parsesTo abilities(triggered(entersSelf, create(inkling)))

    // CR 608.2h: the controller of the destroyed creature as it last existed
    @Test
    fun scathingMarginalia() = card(
        name = "Scathing Marginalia",
        cost = "{1}{W}",
        type = "Instant",
        text = "Destroy target creature. Its controller creates a 1/1 white and black Inkling creature token with flying.",
    ) parsesTo abilities(
        spell(destroy(target("creature")), doing(Effect.CreateToken(inkling, n(1), Players.ControllerOf(target("creature")))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun haughtyInkcaller() = card(
        name = "Haughty Inkcaller",
        cost = "{1}{W}{B}",
        type = "Creature — Orc Warlock",
        pt = "3/3",
        text = """
            Vigilance
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(Keyword.Vigilance, triggered(entersSelf, doing(Effect.Surveil(Players.You, n(2)))))
}
