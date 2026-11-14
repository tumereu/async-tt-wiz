package arcanum.parser.sets.tmt

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 13: multicoloured cards, many with two names acting as one ("Tusk & Horn attack", CR 201.5). Combat
 * pumps for an artifact creature, "sacrifice a permanent unless you discard a card" (CR 118.12a), returning an artifact card as a 3/3
 * Robot if it isn't a creature (CR 611.2e), a flicker at end step, affinity for the next spell (CR 611.2f), "one or both" modes, looking at
 * the top card any time and playing from it with an extra counter (CR 401.5, 601.2a), counters you put (CR 122.6), "can't attack unless",
 * a card from outside the game if you cast it (CR 400.11a), an additional combat in the first combat phase (CR 500.8), revealing until a
 * creature that enters attacking (CR 508.4), triggered abilities triggering again (CR 603.2d), and mana spent below the mana value.
 */
class TmtChunk13MulticoloredTest {
    @Test
    fun professorSprocketeer() = card(
        name = "Professor Sprocketeer",
        cost = "{3}{U}{R}",
        type = "Legendary Creature — Human Scientist",
        text = """
            When Professor Sprocketeer enters, create a 1/1 colorless Robot artifact creature token.
            At the beginning of combat on your turn, target artifact creature you control gets +3/+0 and gains first strike and vigilance until end of turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, create(robotToken)),
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 3, 0, has(Keyword.FirstStrike, Keyword.Vigilance)),
            targets = listOf(slot("creature", and(artifact, creature, you)))),
    )

    // CR 118.12a: the discard is paid or the sacrifice happens
    @Test
    fun tuskAndHorn() = card(
        name = "Tusk & Horn",
        cost = "{1}{B/G}{B/G}",
        type = "Legendary Creature — Boar Rhino Mutant",
        text = "Whenever Tusk & Horn attack or block, sacrifice a permanent unless you discard a card.",
        pt = "7/5",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any))),
            Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(
                Step.Choose(Choice.Objects(and(permanentFilter, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
    )

    // CR 611.2e: it is a 3/3 Robot artifact creature with flying as it enters
    @Test
    fun geniusUnbound() = card(
        name = "Genius Unbound",
        cost = "{4}{U}{R}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Genius Unbound deals 5 damage to target creature.
            • Choose target artifact card in your graveyard. Return it to the battlefield if it's an artifact creature card. Otherwise, return it to the battlefield and it's a 3/3 Robot artifact creature with flying.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
            mode(Step.If(Condition.ObjectMatches(target("card"), and(artifact, creature)),
                    then = listOf(move(target("card"), Zone.Battlefield)),
                    otherwise = listOf(doing(Effect.Move(target("card"), Destination(Zone.Battlefield, entersAs = listOf(
                        Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Robot"))),
                            replaceSubtypes = true), // CR 205.1b: a "[creature type] artifact creature" replaces creature types
                        Mechanic.SetPowerToughness(n(3), n(3)), Mechanic.GainAbilities(listOf(Keyword.Flying)))))))),
                targets = listOf(slot("card", inYourGraveyard(artifact)))),
            min = 1, max = 2))),
    )

    // CR 400.7: they return as new objects
    @Test
    fun gearsAndBladesProblemSolvers() = card(
        name = "Gears & Blades, Problem Solvers",
        cost = "{3}{W/U}{W/U}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Vigilance
            At the beginning of your end step, exile up to one target artifact you control and up to one target creature you control. Then return them to the battlefield under their owners' control.
        """,
        pt = "4/6",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(stepEndOfYourTurn,
            move(Objects.Union(listOf(target("artifact"), target("creature"))), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
            targets = listOf(slot("artifact", and(artifact, you), min = 0), slot("creature", creatureYouControl, min = 0))),
    )

    // CR 611.2f: the next noncreature spell you cast this turn gets affinity for artifacts (CR 702.41a)
    @Test
    fun gearsAndFistsHardScience() = card(
        name = "Gears & Fists, Hard Science",
        cost = "{1}{U/R}{U/R}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Menace
            Whenever Gears & Fists attack, the next noncreature spell you cast this turn has affinity for artifacts. (It costs {1} less to cast for each artifact you control.)
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Affinity(artifact, "artifacts")))),
            Affected.NextSpells(and(not(creature), spellFilter, you), 1), Duration.EndOfTurn))),
    )

    @Test
    fun clanEliteGuard() = card(
        name = "Clan Elite Guard",
        cost = "{2}{W/B}",
        type = "Creature — Human Ninja",
        text = "Whenever this creature attacks, another target creature you control gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(target("creature"), 1, 0, has(Keyword.Indestructible)), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    @Test
    fun goShinobiGo() = card(
        name = "Go Shinobi Go",
        cost = "{R}{W}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Exile target creature you control, then return it to the battlefield under its owner's control.
            • Go Shinobi Go deals damage equal to the greatest power among creatures you control to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(move(target("creature"), Zone.Exile, postBind = "exiled"),
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                targets = listOf(slot("creature", creatureYouControl))),
            mode(damage(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), arcanum.api.Combine.Greatest), toObjects(target("creature"))),
                targets = listOf(slot("creature", creatureOpponentControls))),
            min = 1, max = 2))),
    )

    @Test
    fun lessonsFromTheSwamp() = card(
        name = "Lessons from the Swamp",
        cost = "{2}{G}{U}",
        type = "Sorcery",
        text = "Draw three cards. You may put a land card from your hand onto the battlefield tapped.",
    ) parsesTo abilities(
        spell(draw(3),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true)),
    )

    // CR 401.5: looking at the top card any time; CR 601.2a, 305.1: playing it from there
    @Test
    fun prankAndGearsPartyPlanners() = card(
        name = "Prank & Gears, Party Planners",
        cost = "{2}{G/U}{G/U}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Ward {2}
            You may look at the top card of your library any time.
            You may play lands and cast Mutant, Ninja, or Turtle spells from the top of your library. If you cast a creature spell this way, that creature enters with an additional +1/+1 counter on it.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(land, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You, spellGets = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(and(ObjectFilter.Is(Objects.Affected), creature), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(1)))))),
            affects = Objects.Each(and(or(mutant, ninja, turtle), inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 122.6: each counter put is an event; CR 603.2h: only once each turn
    @Test
    fun prankAndBladesChaosAndOrder() = card(
        name = "Prank & Blades, Chaos & Order",
        cost = "{G/W}{G/W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = "Whenever you put a counter on a creature you control, draw a card. This ability triggers only once each turn.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(on(EventPattern.CountersPut(creatureYouControl, null, causedBy = you)), draw(1), perTurn = 1),
    )

    @Test
    fun roboRodentMarkIV() = card(
        name = "Robo-Rodent Mark IV",
        cost = "{1}{U/R}",
        type = "Artifact Creature — Robot",
        text = "This creature can't attack unless you control another artifact.",
        pt = "2/3",
    ) parsesTo abilities(
        static(Mechanic.CantAttack(), condition = Condition.Not(Condition.Exists(and(another, artifact, you), atLeast = 1))),
    )

    @Test
    fun maskedNobody() = card(
        name = "Masked Nobody",
        cost = "{1}{U/R}{U/R}",
        type = "Artifact Creature — Human Hero",
        text = "When this creature enters, return up to one other target artifact you control to its owner's hand. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("artifact"), Zone.Hand), scry(1), targets = listOf(slot("artifact", and(another, artifact, you), min = 0))),
    )

    // CR 400.11a: a card from outside the game; CR 603.4: only if it was cast
    @Test
    fun northerlyGaleAvatar() = card(
        name = "Northerly Gale Avatar",
        cost = "{2}{U}{U}{R}",
        type = "Creature — Dragon Spirit Avatar",
        text = """
            Flying
            When this creature enters, if you cast it, you may put a card you own from outside the game into your hand.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Each(and(inZone(Zone.Outside), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    // CR 506.1: one beginning of combat step per combat phase; CR 500.8: the additional combat phase
    @Test
    fun fistsAndBladesSiblingRivals() = card(
        name = "Fists & Blades, Sibling Rivals",
        cost = "{1}{R/W}{R/W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = "Whenever Fists & Blades attack, if it's the first combat phase of the turn, untap one or two target attacking creatures. After this phase, there is an additional combat phase.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(attacksSelf, untapIt(target("creatures")), doing(Effect.ExtraCombat()),
            targets = listOf(slot("creatures", and(attacking, creature), min = 1, max = 2)),
            interveningIf = Condition.Compare(Value.EventsThisTurn(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), Measure.Events),
                Comparison.AtMost, n(1))),
    )

    // CR 508.4: the revealed creature enters attacking
    @Test
    fun fistsAndPrankTroublemakers() = card(
        name = "Fists & Prank, Troublemakers",
        cost = "{5}{R/G}{R/G}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Trample, haste
            Whenever Fists & Prank attack, reveal cards from the top of your library until you reveal a creature card. Put that card onto the battlefield tapped and attacking and the rest on the bottom of your library in a random order.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        triggered(attacksSelf,
            doing(Effect.RevealUntil(creature, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true, attacking = AttackTarget.Chosen))),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 603.2d: the ability triggers an additional time
    @Test
    fun oldWhiskerRadicalRat() = card(
        name = "Old Whisker, Radical Rat",
        cost = "{1}{W/B}{W/B}",
        type = "Legendary Creature — Mutant Ninja Rat",
        text = """
            If a triggered ability of a Ninja creature you control triggers, that ability triggers an additional time.
            {1}{U}: Target Ninja can't be blocked this turn.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        static(Mechanic.TriggersAdditionalTime(event = null, sources = and(ninja, creature, you))),
        activated(manaCost("{1}{U}"), untilEndOfTurn(target("Ninja"), Mechanic.CantBeBlocked), targets = listOf(slot("Ninja", ninja))),
    )

    // CR 601.2h: the mana actually spent compared with the mana value (CR 202.3)
    @Test
    fun snapAndSnarlTerribleTwos() = card(
        name = "Snap & Snarl, Terrible Twos",
        cost = "{B/R}{B/R}",
        type = "Legendary Creature — Turtle Wolf Mutant",
        text = """
            This spell can't be countered.
            Menace
            Whenever a player casts a spell, if the amount of mana spent to cast it was less than its mana value, Snap & Snarl deal 3 damage to that player.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Menace,
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), damage(3, arcanum.api.Recipients(players = Players.EventPlayer)),
            interveningIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(arcanum.api.EventRole.Object)), Comparison.Less,
                Value.Of(NumericProperty.ManaValue, Objects.EventObject(arcanum.api.EventRole.Object), arcanum.api.Combine.Single))),
    )
}
