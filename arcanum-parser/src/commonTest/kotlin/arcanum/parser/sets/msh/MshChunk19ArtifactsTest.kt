package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modes
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
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 19: colourless artifacts. Improvise (CR 702.126); a creature of the defending player tapped when the
 * equipped creature attacks; a spell cast from the top six with mana value up to the greatest attacking power (CR 608.2g); crew
 * (CR 702.122); a land put onto the battlefield from your hand; an Equipment becoming a creature with a quoted ability (layers 4, 6, 7b);
 * keyword counters for each keyword the target has and it lacks (CR 122.1b); a maximum hand size and drawing the difference; a token
 * copy of an artifact that becomes a creature if it isn't one (CR 707.2); modes not chosen this turn (CR 700.2).
 */
class MshChunk19ArtifactsTest {
    @Test
    fun syndicateSynthoids() = card(
        name = "Syndicate Synthoids",
        cost = "{2}",
        type = "Artifact Creature — Robot Villain",
        text = "When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Surveil(Players.You, n(2)))),
    )

    @Test
    fun coreReactor() = card(
        name = "Core Reactor",
        cost = "{5}",
        type = "Artifact",
        text = """
            Improvise (Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
            This artifact enters tapped.
            {T}: Add {C}{C}{C}.
        """,
    ) parsesTo abilities(
        Keyword.Improvise,
        entersTapped,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}{C}")!!)), Players.You))),
    )

    // the player the equipped creature attacks
    @Test
    fun commanderLibertysShield() = card(
        name = "Commander Liberty's Shield",
        cost = "{2}",
        type = "Legendary Artifact — Equipment",
        text = """
            Indestructible
            Equipped creature gets +0/+8 and has vigilance.
            Whenever equipped creature attacks, tap target creature defending player controls.
            Equip {2}
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        static(pt(0, 8), has(Keyword.Vigilance), affects = Objects.AttachedTo(Objects.Self)),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))), tapIt(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.AttachedTo(Objects.Self)))))))),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 608.2g: cast while the trigger resolves, its mana value up to the greatest power among your attackers
    @Test
    fun infinitePrism() = card(
        name = "Infinite Prism",
        cost = "{5}",
        type = "Artifact",
        text = """
            Ward {2}
            Whenever you attack, look at the top six cards of your library. You may cast a spell from among them with mana value less than or equal to the greatest power among attacking creatures you control without paying its mana cost. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        triggered(youAttack,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(nonland, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.Of(NumericProperty.Power, Objects.Each(and(attacking, creatureYouControl)), Combine.Greatest))), n(0), n(1), from = Objects.Bound("seen")),
                bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Cast(Objects.Bound("chosen"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun dependableSkyjet() = card(
        name = "Dependable Skyjet",
        cost = "{3}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            {T}: Add one mana of any color.
            Crew 4 (Tap any number of creatures you control with total power 4 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        Keyword.Crew(4),
    )

    @Test
    fun roverScoutUnit() = card(
        name = "R.O.V.E.R. Scout Unit",
        cost = "{4}",
        type = "Artifact Creature — Robot Scout",
        text = """
            Flying
            When this creature enters, draw a card, then you may put a land card from your hand onto the battlefield tapped.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, draw(1),
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true)),
    )

    // layers 4, 6, 7b: an Equipment that isn't a creature becomes a 0/0 artifact creature with a quoted P/T ability
    @Test
    fun steelSentinelArmor() = card(
        name = "Steel Sentinel Armor",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +2/+1 and has flying.
            {2}: If this Equipment isn't a creature, it becomes a 0/0 Construct Hero artifact creature with flying and "This creature gets +1/+1 for each artifact you control" until end of turn.
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, arcanum.api.Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
        static(pt(2, 1), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
        activated(manaCost("{2}"), Step.If(Condition.Not(Condition.ObjectMatches(Objects.Self, creature)), then = listOf(untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Construct")), SubtypeRef.Literal(Subtype("Hero"))),
                replaceSubtypes = true), // CR 205.1b: a "[creature types] artifact creature" replaces creature types
            Mechanic.SetPowerToughness(n(0), n(0)),
            has(Keyword.Flying, static(Mechanic.ModifyPowerToughness(count(and(artifact, you)), count(and(artifact, you))))))))),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun bureauHelicarrier() = card(
        name = "Bureau Helicarrier",
        cost = "{4}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            When this Vehicle enters, create two 1/1 white Soldier creature tokens.
            Crew 6 (Tap any number of creatures you control with total power 6 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(soldier, n(2))),
        Keyword.Crew(6),
    )

    // CR 122.1b: a keyword counter for each keyword the target has and it lacks
    @Test
    fun superMimicoid() = card(
        name = "Super-Mimicoid",
        cost = "{2}",
        type = "Legendary Artifact Creature — Robot Villain",
        text = """
            Super-Mimicoid's power is equal to the number of legendary creatures you control.
            Whenever Super-Mimicoid enters or attacks, choose another target creature. If that creature has haste and Super-Mimicoid doesn't, put a haste counter on Super-Mimicoid. Do the same for flying, first strike, double strike, deathtouch, indestructible, lifelink, menace, reach, trample, and vigilance.
        """,
        pt = "*/2",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(ObjectFilter.Super(arcanum.api.Supertype.Legendary), creatureYouControl)), null), characteristicDefining = true),
        triggered(entersOrAttacksSelf, *listOf(Keyword.Haste.kind, Keyword.Flying.kind, Keyword.FirstStrike.kind, Keyword.DoubleStrike.kind, Keyword.Deathtouch.kind,
                Keyword.Indestructible.kind, Keyword.Lifelink.kind, Keyword.Menace.kind, Keyword.Reach.kind, Keyword.Trample.kind, Keyword.Vigilance.kind).map { k ->
                Step.If(Condition.And(listOf(Condition.ObjectMatches(target("creature"), kw(k)), Condition.Not(Condition.ObjectMatches(Objects.Self, kw(k))))),
                    then = listOf(counters(Objects.Self, 1, CounterKind.Keyword(k))))
            }.toTypedArray(),
            targets = listOf(slot("creature", and(another, creature)))),
    )

    // CR 402.2: a maximum hand size of ten; the difference drawn
    @Test
    fun theTenBracers() = card(
        name = "The Ten Bracers",
        cost = "{8}",
        type = "Legendary Artifact",
        text = """
            Your maximum hand size is ten.
            At the beginning of your end step, if you have fewer than ten cards in hand, draw cards equal to the difference.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MaximumHandSize(n(10))),
        triggered(stepBegins(arcanum.api.TurnStep.End, PlayerFilter.You), Step.Do(Effect.Draw(Players.You, Value.Difference(n(10), Value.HandSize(Players.You)))),
            interveningIf = Condition.Compare(Value.HandSize(Players.You), arcanum.api.Comparison.Less, n(10))),
    )

    // CR 707.2: a token copy of the entered artifact, made a 2/2 Robot Villain creature if it isn't one
    @Test
    fun omnitronArtificialMalevolence() = card(
        name = "Omnitron, Artificial Malevolence",
        cost = "{3}",
        type = "Legendary Artifact Creature — Robot Villain",
        text = "Whenever another nontoken artifact you control enters, you may pay {2}. If you do, create a token that's a copy of it. If the token isn't a creature, it becomes a 2/2 Robot Villain creature in addition to its other types.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(enters(and(another, not(token), artifact, you)),
            Step.Pay(manaCost("{2}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                create(arcanum.api.TokenSpec.CopyOf(Objects.EventObject(EventRole.After)), bind = "token"),
                Step.If(Condition.Not(Condition.ObjectMatches(Objects.Bound("token"), creature)), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(
                    Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Robot")), SubtypeRef.Literal(Subtype("Villain")))),
                    Mechanic.SetPowerToughness(n(2), n(2))), arcanum.api.Affected.Objects(Objects.Bound("token")), arcanum.api.Duration.Indefinitely))))))),
    )

    @Test
    fun adamantEnergyDaggers() = card(
        name = "Adamant Energy Daggers",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Indestructible (Effects that say "destroy" don't destroy this Equipment.)
            Equipped creature gets +2/+2.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        static(pt(2, 2), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 700.2: one mode not chosen this turn; flavor words name them (CR 207.2d)
    @Test
    fun theSimulacrum() = card(
        name = "The Simulacrum",
        cost = "{4}",
        type = "Legendary Artifact Creature — Robot Hero",
        text = """
            Flying, vigilance
            Whenever you cast a noncreature spell, choose one that hasn't been chosen this turn —
            • Solar Beam — The Simulacrum gains double strike until end of turn.
            • Density Control — The Simulacrum gains indestructible until end of turn.
            • Technopathy — Draw a card.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(youCast(noncreatureSpell), Instructions(modes = Modes(listOf(
            mode(gains(Objects.Self, Keyword.DoubleStrike)), mode(gains(Objects.Self, Keyword.Indestructible)), mode(draw(1))),
            min = 1, max = 1, onlyUnchosenThisTurn = true, chooser = Players.You))),
    )
}
