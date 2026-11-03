package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 18: more multicoloured cards. Exiled and returned tapped (a new object, CR 400.7); base power and toughness
 * becoming 6/6 (layer 7b); X +1/+1 counters as it enters and "choose up to X" modes (CR 107.3m, 700.2); an ability copy from an
 * artifact source (CR 707.10); ward by poison counters (CR 702.21a, 122.1); "you may tap him. When you do" (CR 603.12); "can't become
 * untapped for as long as you control" it; a copy of a creature or creature card with exceptions (CR 707.9b); prowess twice (CR
 * 702.108b); "discard a card or pay {2}" as an additional cost and as ward; a named Equipment token attached to it (CR 111.4); a search of
 * library and/or graveyard with X counters; +X/+0 by its power; a return from the graveyard with a finality counter and an attached
 * Equipment; damage that heals other damage (CR 701.69a); everything exiled and creatures put onto the battlefield from hands.
 */
class MshChunk18MulticoloredMoreTest {
    // a new object returned tapped under its owner's control (CR 400.7)
    @Test
    fun theMightyThundererJanaForge() = card(
        name = "The Mighty Thunderer, Jana Forge",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human God Hero",
        text = """
            Flying
            Whenever The Mighty Thunderer attacks, exile up to one target nontoken artifact or creature, then return that card to the battlefield tapped under its owner's control.
            Whenever an Equipment you control enters, draw a card.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, move(target("permanent"), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.Bound("exiled"))),
            targets = listOf(slot("permanent", and(not(token), or(artifact, creature)), min = 0))),
        triggered(enters(and(equipment, you)), draw(1)),
    )

    // layer 7b: base 6/6 until end of turn ("they": the two-named creature)
    @Test
    fun skyGirlAndThunderLizard() = card(
        name = "Sky Girl and Thunder Lizard",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Human Dinosaur Hero",
        text = """
            Whenever you draw your second card each turn, until end of turn, Sky Girl and Thunder Lizard's base power and toughness become 6/6 and they gain trample.
            Whenever an artifact you control enters, draw a card. This ability triggers only once each turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(secondCardEachTurn, untilEndOfTurn(Objects.Self, Mechanic.SetPowerToughness(n(6), n(6)), has(Keyword.Trample))),
        triggered(enters(and(artifact, you)), draw(1), perTurn = 1),
    )

    // CR 107.3m: X as it was cast; CR 700.2: up to X modes
    @Test
    fun theRuinousDemolitionGang() = card(
        name = "The Ruinous Demolition Gang",
        cost = "{X}{B}{R}",
        type = "Legendary Creature — Human Villain",
        text = """
            The Ruinous Demolition Gang enters with X +1/+1 counters on it.
            When The Ruinous Demolition Gang enters, choose up to X —
            • Discard a card, then draw a card.
            • Target opponent loses 2 life.
            • Destroy target token.
            • Each player sacrifices a creature of their choice.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(of = Objects.Self))))),
        Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            mode(discard(1), draw(1)),
            mode(loseLife(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
            mode(destroy(target("token")), targets = listOf(slot("token", token))),
            mode(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "sacrificed",
                    chooser = Players.Bound("player")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true))),
            min = 0, max = 4, chooser = Players.You, maxCount = Value.CastX(of = Objects.Self)))),
    )

    // CR 707.10: an ability copy from an artifact source, during your turn and once each turn
    @Test
    fun scientistSupremeOfTheSyndicate() = card(
        name = "Scientist Supreme of the Syndicate",
        cost = "{U}{B}",
        type = "Legendary Creature — Human Scientist Villain",
        text = "Pay 2 life: Copy target activated or triggered ability you control from an artifact source. You may choose new targets for the copy. Activate only during your turn and only once each turn. (Mana abilities can't be targeted.)",
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.PayLife(n(2)))), Instructions(targets = listOf(slot("ability", and(or(
                ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact), ObjectFilter.AbilityOf(kind = AbilityKind.Triggered, source = artifact)), you))),
                steps = listOf(Step.Do(Effect.CopySpell(target("ability"), Players.You, newTargets = true)))),
            anyTime.copy(perTurn = 1, condition = Condition.YourTurn)),
    )

    // CR 702.21a, 122.1: ward paid by getting poison counters
    @Test
    fun theViperBrotherhood() = card(
        name = "The Viper Brotherhood",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Human Snake Villain",
        text = """
            Deathtouch
            Ward—Get five poison counters. (A player with ten or more poison counters loses the game.)
            Whenever another creature you control with deathtouch dies, each opponent sacrifices a nontoken creature of their choice.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Ward(Cost(listOf(CostPart.GetCounters(CounterKind.Poison, n(5))))),
        triggered(on(EventPattern.ZoneChange(and(another, creatureYouControl, kw(Keyword.Deathtouch.kind)), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Choose(Choice.Objects(and(not(token), creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)),
    )

    // CR 603.12: tapping it as a cost on resolution, then a reflexive trigger
    @Test
    fun arachnidKidToTheRescue() = card(
        name = "Arachnid Kid, To the Rescue",
        cost = "{2}{G/W}",
        type = "Legendary Creature — Spider Human Hero",
        text = """
            Flash
            Reach, vigilance
            No One Dies! — When Arachnid Kid enters, you may tap him. When you do, another target nonattacking creature you control gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Reach,
        Keyword.Vigilance,
        triggered(entersSelf, Step.Pay(Cost(listOf(tap)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(gains(target("creature"), Keyword.Indestructible),
                targets = listOf(slot("creature", and(another, not(attacking), creatureYouControl)))))),
            abilityWord = "No One Dies!"),
    )

    // can't become untapped for as long as you control it
    @Test
    fun arachnidWomanSecretAgent() = card(
        name = "Arachnid Woman, Secret Agent",
        cost = "{3}{W/U}",
        type = "Legendary Creature — Spider Human Spy Hero",
        text = """
            Flash
            When Arachnid Woman enters, tap target creature an opponent controls. That creature can't become untapped for as long as you control Arachnid Woman.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, tapIt(target("creature")), Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBecomeUntapped), arcanum.api.Affected.Objects(target("creature")),
            Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 707.9b: a copy of a creature or a creature card in a graveyard, its name and types replaced (CR 205.1b)
    @Test
    fun copyistMercenaryMimic() = card(
        name = "Copyist, Mercenary Mimic",
        cost = "{2}{U}{B}",
        type = "Legendary Creature — Human Mercenary Villain",
        text = "Photographic Reflexes — At the beginning of your first main phase, until your next turn, Copyist becomes a copy of up to one target creature on the battlefield or creature card in a graveyard, except his name is Copyist, Mercenary Mimic and he's a legendary Human Mercenary Villain creature.",
        pt = "3/5",
    ) parsesTo abilities(
        triggered(stepBegins(arcanum.api.TurnStep.PrecombatMain, PlayerFilter.You),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("target"), except = listOf(
                arcanum.api.CopyException.Name("Copyist, Mercenary Mimic"),
                arcanum.api.CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf("Human", "Mercenary", "Villain").map { SubtypeRef.Literal(Subtype(it)) },
                    addSupertypes = listOf(Supertype.Legendary), replaceSubtypes = true)))), arcanum.api.Affected.Objects(Objects.Self), Duration.UntilYourNextTurn)),
            targets = listOf(slot("target", or(and(creature, inZone(Zone.Battlefield)), and(creature, cardFilter, inZone(Zone.Graveyard))), min = 0)),
            abilityWord = "Photographic Reflexes"),
    )

    // CR 702.108b: each prowess triggers on its own
    @Test
    fun thundarAllfatherson() = card(
        name = "Thundar Allfatherson",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — God Warrior Hero",
        text = "Flying, vigilance, prowess, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn twice.)",
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Prowess,
        Keyword.Prowess,
    )

    // CR 601.2f: one of two additional costs; ward with one of two costs (CR 702.21a)
    @Test
    fun tetraRuggedRumbler() = card(
        name = "Tetra, Rugged Rumbler",
        cost = "{2}{B/G}",
        type = "Legendary Creature — Human Villain",
        text = """
            As an additional cost to cast this spell, discard a card or pay {2}.
            Ward—Discard a card or pay {2}. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player discards a card or pays {2}.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(discardOrTwo)))),
        Keyword.Ward(Cost(listOf(discardOrTwo))),
    )

    // CR 111.4: a named Equipment token, then attached to it (CR 701.3); a dotted name
    @Test
    fun unAgentJackWarden() = card(
        name = "U.N.Agent, Jack Warden",
        cost = "{3}{W/B}",
        type = "Legendary Creature — Human Soldier Hero",
        text = "When U.N.Agent enters, create a colorless Equipment artifact token named Sturdy Shield with \"Equipped creature gets +1/+2\" and equip {2}. Attach it to U.N.Agent.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf,
            create(TokenSpec.Defined(FaceDefinition("Sturdy Shield", colorIndicator = emptyList(), types = listOf(CardType.Artifact), subtypes = listOf(Subtype("Equipment")),
                abilities = listOf(static(pt(1, 2), affects = Objects.AttachedTo(Objects.Self)), Keyword.Equip(manaCost("{2}"))))), bind = "token"),
            Step.Do(Effect.Attach(Objects.Bound("token"), Recipients(objects = Objects.Self)))),
    )

    // CR 701.23: library and/or graveyard; X additional counters as it enters (CR 122.6); a shuffle only if the library was searched
    @Test
    fun syntheticQuest() = card(
        name = "Synthetic Quest",
        cost = "{X}{U}{R}",
        type = "Sorcery",
        text = "Search your library and/or graveyard for an artifact creature card with mana value X or less and put it onto the battlefield with X additional +1/+1 counters on it. If X is 4 or greater, it gains haste until end of turn. If you search your library this way, shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Search(Players.You, and(artifact, creature, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, Value.Announced("X"))), max = n(1),
                zones = listOf(Zone.Library, Zone.Graveyard), bind = "found", searcher = Players.You)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, counters = listOf(arcanum.api.CountersToPut(p1p1, Value.Announced("X")))), postBind = "put")),
            Step.If(Condition.Compare(Value.Announced("X"), arcanum.api.Comparison.AtLeast, n(4)), then = listOf(gains(Objects.Bound("put"), Keyword.Haste))),
            Step.If(Condition.Searched("found", Zone.Library), then = listOf(shuffle)),
            announced = listOf("X")),
    )

    @Test
    fun battleEngineLegacyOfSteel() = card(
        name = "Battle Engine, Legacy of Steel",
        cost = "{2}{R/W}",
        type = "Legendary Artifact Creature — Human Hero",
        text = """
            Flying
            At the beginning of combat on your turn, another target creature you control gets +X/+0 until end of turn, where X is Battle Engine's power.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(beginningOfCombatOnYourTurn, untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(Objects.Self), n(0))),
            targets = listOf(slot("creature", and(another, creatureYouControl)))),
    )

    // CR 113.6m: it works in the graveyard; a finality counter as it enters (CR 122.1h); then an Equipment attached to it
    @Test
    fun frostSoldierIcyAssassin() = card(
        name = "Frost Soldier, Icy Assassin",
        cost = "{W}{B}",
        type = "Legendary Creature — Human Assassin Villain",
        text = """
            Vigilance, menace
            Frost Soldier gets +2/+0 for each Equipment attached to him.
            {3}{W}{B}: Return this card from your graveyard to the battlefield with a finality counter on him. Then you may attach an Equipment you control to him. (If a creature with a finality counter on it would die, exile it instead.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Menace,
        static(Mechanic.ModifyPowerToughness(Value.Times(count(and(equipment, ObjectFilter.Attached(to = Objects.Self))), 2), n(0))),
        activated(manaCost("{3}{W}{B}"),
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, counters = listOf(arcanum.api.CountersToPut(CounterKind.Finality, n(1)))), postBind = "returned")),
            Step.Choose(Choice.Objects(and(equipment, you), n(0), n(1)), bind = "equipment", chooser = Players.You),
            Step.Do(Effect.Attach(Objects.Bound("equipment"), Recipients(objects = Objects.Bound("returned"))))),
    )

    // CR 701.69a: the damage is dealt and the damage marked before it is healed
    @Test
    fun badgerFierceFighter() = card(
        name = "Badger, Fierce Fighter",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Mutant Berserker Hero",
        text = """
            Haste
            When Badger enters, he fights up to one other target creature.
            If damage would be dealt to Badger, instead that damage is dealt, but all other damage already dealt to him is healed.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, Step.Do(Effect.Fight(Objects.Self, target("creature"))), targets = listOf(slot("creature", and(another, creature), min = 0))),
        static(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))), modify = listOf(Modification.HealsOtherDamage))),
    )

    // each player puts creature cards from their hand at once (CR 101.4); the exiled cards go to their owners' hands
    @Test
    fun realmsWithinRealms() = card(
        name = "Realms Within Realms",
        cost = "{5}{G}{U}",
        type = "Sorcery",
        text = "Exile all creatures. Each player may put any number of creature cards from their hand onto the battlefield. Then put all cards exiled this way into their owners' hands. Exile Realms Within Realms.",
    ) parsesTo abilities(
        spell(
            move(Objects.Each(creature), Zone.Exile, postBind = "exiled"),
            Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))), n(0), null),
                    bind = "chosen", chooser = Players.Bound("player")),
                move(Objects.Bound("chosen"), Zone.Battlefield)), together = true),
            move(Objects.Bound("exiled"), Zone.Hand),
            move(Objects.Self, Zone.Exile)),
    )

    private val discardOrTwo = CostPart.OneOf(listOf(Cost(listOf(discardACard)), manaCost("{2}")))
}
