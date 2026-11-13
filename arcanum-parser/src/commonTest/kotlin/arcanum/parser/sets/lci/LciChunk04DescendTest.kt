package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.ActivationRules
import arcanum.api.Timing
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 04: descend N (an ability word: N or more permanent cards in your graveyard, CR 207.2c, 700.11) and the cards around it. */
class LciChunk04DescendTest {
    /** "there are N or more permanent cards in your graveyard" (CR 110.4b: a permanent card has a permanent type). */
    private fun descend(n: Int) = Condition.Exists(and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = n)
    private fun descendStatic(n: Int, vararg mechanics: Mechanic) = static(*mechanics, condition = descend(n)).copy(abilityWord = "Descend $n")
    private val nonlandPermanent = and(nonland, permanentFilter)

    // CR 613.4c: both +2/+2 apply with eight; CR 509.1b: at most one blocker
    @Test
    fun grubhollowTheFumingSpire() = card(
        name = "Grubhollow, the Fuming Spire",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Fungus",
        pt = "3/3",
        text = """
            Descend 4 — As long as there are four or more permanent cards in your graveyard, Grubhollow gets +2/+2 and has trample.
            Descend 8 — As long as there are eight or more permanent cards in your graveyard, Grubhollow gets an additional +2/+2 and can't be blocked by more than one creature.
        """,
    ) parsesTo abilities(
        descendStatic(4, pt(2, 2), has(Keyword.Trample)),
        descendStatic(8, pt(2, 2), Mechanic.MaximumBlockers(1)),
    )

    // CR 608.2c: the second paragraph replaces the first when the condition holds as it resolves
    @Test
    fun plungeIntoGloom() = card(
        name = "Plunge into Gloom",
        cost = "{1}{B}{B}",
        type = "Instant",
        text = """
            Target creature gets -5/-5 until end of turn.
            Descend 4 — That creature gets -10/-10 until end of turn instead if there are four or more permanent cards in your graveyard.
        """,
    ) parsesTo abilities(
        spell(ifThen(descend(4), pump(target("creature"), -10, -10), otherwise = listOf(pump(target("creature"), -5, -5))),
            targets = listOf(slot("creature", creature))).copy(abilityWord = "Descend 4"),
    )

    // CR 701.25a: the cards surveil leaves on top, counted once; CR 603.4: descend 8 checked as the draw triggers and resolves
    @Test
    fun hungeringWraithkin() = card(
        name = "Hungering Wraithkin",
        cost = "{2}{B}{B}",
        type = "Creature — Spirit Horror",
        pt = "4/4",
        text = """
            When this creature enters, surveil 2. Then for each card you put on top of your library, you draw a card and you lose 3 life.
            Descend 8 — Whenever you draw a card, if there are eight or more permanent cards in your graveyard, target opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Surveil(Players.You, n(2), onTop = "put on top")),
            Step.Repeat(Value.Count(Objects.Bound("put on top")), listOf(draw(1), loseLife(3)))),
        triggered(on(EventPattern.Draw(PlayerFilter.You)), loseLife(1, Players.Target("opponent")), gainLife(1),
            targets = listOf(targetOpponent), interveningIf = descend(8), abilityWord = "Descend 8"),
    )

    // CR 506.5 (can't attack or block unless); CR 603.12: the reflexive trigger, "its" the discarded card (CR 608.2h)
    @Test
    fun theDrownedElder() = card(
        name = "The Drowned Elder",
        cost = "{U}{B}",
        type = "Legendary Creature — Spirit God",
        pt = "8/8",
        text = """
            Descend 8 — The Drowned Elder can't attack or block unless there are eight or more permanent cards in your graveyard.
            {2}{U}{B}: Draw a card, then discard a card. When you discard a card this way, target player mills cards equal to its mana value.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Not(descend(8))).copy(abilityWord = "Descend 8"),
        activated(manaCost("{2}{U}{B}"), draw(1), doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, preBind = "discarded")),
            ifThen(Condition.NonEmpty("discarded"), reflexive(
                doing(Effect.Mill(Players.Target("player"), Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("discarded"), arcanum.api.Combine.Single))),
                targets = listOf(targetPlayer)))),
    )

    // CR 712.2 (transform); CR 106.3, 707.2: a permanent becomes a copy of the spell the mana paid for, until end of turn
    @Test
    fun theBrimmingCistern() = cardFaces(
        Layout.Transform,
        card(
            name = "The Brimming Cistern",
            cost = "{2}{U}",
            type = "Legendary Artifact",
            text = """
                When The Brimming Cistern enters, mill two cards, then draw two cards.
                Descend 8 — At the beginning of your upkeep, if there are eight or more permanent cards in your graveyard, transform The Brimming Cistern.
            """,
        ) to abilities(
            triggered(entersSelf, mill(2), draw(2)),
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), transformSelf, interveningIf = descend(8), abilityWord = "Descend 8"),
        ),
        card(
            name = "The Thousand Springs",
            cost = null,
            type = "Legendary Artifact Land",
            text = """
                (Transforms from The Brimming Cistern.)
                {T}: Add {U}.
                Whenever you cast a permanent spell using mana produced by The Thousand Springs, up to one other target permanent you control becomes a copy of that spell until end of turn.
            """,
        ) to abilities(
            addOne("{U}"),
            // CR 110.4b: a permanent spell is an artifact, battle, creature, enchantment, or planeswalker spell
            triggered(youCast(ObjectFilter.Or(listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Battle, arcanum.api.CardType.Creature,
                arcanum.api.CardType.Enchantment, arcanum.api.CardType.Planeswalker).map { ObjectFilter.Type(it) })),
                doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.EventObject(EventRole.Object))), Affected.Objects(target("permanent")), Duration.EndOfTurn)),
                targets = listOf(slot("permanent", and(another, permanentFilter, you), min = 0)),
                triggersOnlyIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.Object), source = ObjectFilter.Is(Objects.Self)),
                    arcanum.api.Comparison.Greater, n(0))),
        ),
    )

    // CR 700.2, 601.2b: one mode, or one or more with eight permanent cards as it is cast
    @Test
    fun lamentOfTheSunken() = card(
        name = "Lament of the Sunken",
        cost = "{U}{B}",
        type = "Sorcery",
        text = """
            Descend 8 — Choose one. If there are eight or more permanent cards in your graveyard as you cast this spell, choose one or more instead.
            • Return target nonland permanent to its owner's hand.
            • Target opponent discards a card.
            • Look at the top three cards of your library. Put one of them into your hand and the rest into your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(modes = Modes(
                options = listOf(
                    mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", nonlandPermanent))),
                    mode(discard(1, Players.Target("opponent")), targets = listOf(targetOpponent)),
                    mode(doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "looked")),
                        Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "picked", chooser = Players.You),
                        move(Objects.Bound("picked"), Zone.Hand), move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), Zone.Graveyard)),
                ),
                min = 1,
                max = 1,
                maxIf = descend(8),
                maxIfCount = 3,
                upToMaxIf = true,
                chooser = Players.You,
            )),
            abilityWord = "Descend 8",
        ),
    )

    // CR 613.4c
    @Test
    fun sunningMarmot() = card(
        name = "Sunning Marmot",
        cost = "{1}{G}",
        type = "Creature — Capybara",
        pt = "1/3",
        text = "Descend 4 — This creature gets +3/+0 as long as there are four or more permanent cards in your graveyard.",
    ) parsesTo abilities(descendStatic(4, pt(3, 0)))

    // CR 603.4: an intervening "if" checked as it triggers and resolves
    @Test
    fun burrowPilferer() = card(
        name = "Burrow Pilferer",
        cost = "{2}{G}",
        type = "Creature — Raccoon",
        pt = "3/2",
        text = "Descend 4 — When this creature enters, if there are four or more permanent cards in your graveyard, return target permanent card from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(permanentCard))),
            interveningIf = descend(4), abilityWord = "Descend 4"),
    )

    // CR 603.4; CR 115.1: "other than this creature"
    @Test
    fun conclaveOfWhispers() = card(
        name = "Conclave of Whispers",
        cost = "{4}{U}{U}",
        type = "Creature — Spirit Advisor",
        pt = "4/4",
        text = """
            Flying
            Descend 4 — When this creature enters, if there are four or more permanent cards in your graveyard, return up to one target nonland permanent other than this creature to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonlandPermanent, another), min = 0)),
            interveningIf = descend(4), abilityWord = "Descend 4"),
    )

    // CR 613.1f (layer 6)
    @Test
    fun tutorShade() = card(
        name = "Tutor Shade",
        cost = "{4}{U}",
        type = "Creature — Spirit Cleric",
        pt = "3/2",
        text = """
            When this creature enters, draw a card.
            Descend 4 — This creature has flying as long as there are four or more permanent cards in your graveyard.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        descendStatic(4, has(Keyword.Flying)),
    )

    // CR 613.4c, 613.1f
    @Test
    fun twilightRemnant() = card(
        name = "Twilight Remnant",
        cost = "{1}{B}",
        type = "Creature — Vampire Spirit",
        pt = "2/2",
        text = "Descend 4 — As long as there are four or more permanent cards in your graveyard, this creature gets +1/+1 and has lifelink.",
    ) parsesTo abilities(descendStatic(4, pt(1, 1), has(Keyword.Lifelink)))

    // CR 613.4c
    @Test
    fun gilledGrottoWurm() = card(
        name = "Gilled Grotto-Wurm",
        cost = "{3}{U}",
        type = "Creature — Salamander Wurm",
        pt = "2/5",
        text = "Descend 4 — This creature gets +2/+0 as long as there are four or more permanent cards in your graveyard.",
    ) parsesTo abilities(descendStatic(4, pt(2, 0)))

    // CR 603.4, 508.1m
    @Test
    fun stalkfangVeteran() = card(
        name = "Stalkfang Veteran",
        cost = "{4}{G}",
        type = "Creature — Cat Warrior",
        pt = "5/4",
        text = """
            Trample
            Descend 4 — Whenever this creature attacks, if there are four or more permanent cards in your graveyard, put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(attacksSelf, counters(target("creature")), targets = listOf(slot("creature", creature)), interveningIf = descend(4), abilityWord = "Descend 4"),
    )

    // CR 603.4, 508.1m
    @Test
    fun venomousGrottoCrawler() = card(
        name = "Venomous Grotto Crawler",
        cost = "{2}{B}",
        type = "Creature — Insect Horror",
        pt = "1/3",
        text = """
            Deathtouch
            Descend 4 — Whenever this creature attacks, if there are four or more permanent cards in your graveyard, you draw a card and you lose 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(attacksSelf, draw(1), loseLife(1), interveningIf = descend(4), abilityWord = "Descend 4"),
    )

    // CR 602.5b, 122.1: returned with a finality counter, only with eight permanent cards and as a sorcery
    @Test
    fun ossuarchTheGrandBlunder() = card(
        name = "Ossuarch, the Grand Blunder",
        cost = "{3}{U}{B}",
        type = "Legendary Creature — Skeleton Horror",
        pt = "6/4",
        text = """
            Vigilance, menace
            Descend 8 — {4}{U}{B}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if there are eight or more permanent cards in your graveyard and only as a sorcery. (If a creature with a finality counter on it would die, exile it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Menace,
        Ability.Activated(
            cost = manaCost("{4}{U}{B}"),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield,
                counters = listOf(CountersToPut(CounterKind.Finality, Value.Fixed(1)))))))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You, condition = descend(8)),
            abilityWord = "Descend 8",
        ),
    )
}
