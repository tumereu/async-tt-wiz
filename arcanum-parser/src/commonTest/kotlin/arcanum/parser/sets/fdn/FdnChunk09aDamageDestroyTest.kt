package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CounterKind
import arcanum.api.Timing
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Duration
import arcanum.api.NumericProperty
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 09a of FDN: damage, destroy, discard, draw and exile wordings. The invented names carry the oracle text of the real cards. */
class FdnChunk09aDamageDestroyTest {
    // CR 115.3: the second slot takes different targets than the first; "up to two" allows none
    @Test
    fun cinderhornTyrant() = card(
        name = "Cinderhorn Tyrant",
        cost = "{4}{R}{R}{R}",
        type = "Legendary Creature — Dragon",
        text = """
            Flying
            Whenever this creature attacks, this creature deals 4 damage to any target and 3 damage to each of up to two other targets.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            attacksSelf,
            damage(4, toAny),
            damage(3, Recipients(target = "other")),
            targets = listOf(
                anyTarget,
                TargetSlot("other", TargetKind.Either(or(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)), PlayerFilter.Any),
                    min = Value.Fixed(0), max = Value.Fixed(2), distinctFrom = listOf("any")),
            ),
        ),
    )

    // CR 608.2c: the counter is put first, then the same creature deals damage equal to its (new) power
    @Test
    fun thornwakeBlow() = card(
        name = "Thornwake Blow",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Put a +1/+1 counter on target creature you control. Then that creature deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(
            counters(target("creature")),
            damage(power(target("creature")), toObjects(target("second creature")), target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls)),
        ),
    )

    // CR 201.2, 608.2b: the target and every other nonland permanent with its name, together
    @Test
    fun gorgebrandPulse() = card(
        name = "Gorgebrand Pulse",
        cost = "{1}{B}{G}",
        type = "Sorcery",
        text = "Destroy target nonland permanent and all other permanents with the same name as that permanent.",
    ) parsesTo abilities(
        spell(
            destroy(Objects.Union(listOf(
                target("permanent"),
                Objects.Each(and(not(ObjectFilter.Is(target("permanent"))), permanentFilter, ObjectFilter.SameNameAs(target("permanent")))),
            ))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 120.2a, 700.1: X is announced, "this creature" is the damage source, this turn's combat damage to a player; once each turn
    @Test
    fun cindersteelWyrm() = card(
        name = "Cindersteel Wyrm",
        cost = "{6}",
        type = "Artifact Creature — Dragon",
        text = "{X}: Destroy each nonland permanent with mana value X whose controller was dealt combat damage by this creature this turn. Activate only once each turn.",
        pt = "5/5",
    ) parsesTo abilities(
        Ability.Activated(manaCost("{X}"), Instructions(steps = listOf(destroy(Objects.Each(and(nonland, permanentFilter,
            ObjectFilter.ManaValue(Comparison.Equal, Value.Announced(name = "X")),
            ObjectFilter.ControlledBy(PlayerFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true))))))), announced = listOf("X")),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)),
    )

    // CR 701.9a, 101.4: each opponent discards the whole hand; the cost is removing the counter
    @Test
    fun nightfoldSentinel() = card(
        name = "Nightfold Sentinel",
        cost = "{5}{B}{B}{B}",
        type = "Legendary Creature — Spirit",
        text = "Remove a divinity counter from this creature: Each opponent discards their hand.",
        pt = "5/2",
    ) parsesTo abilities(
        Ability.Activated(cost(CostPart.RemoveCounters(CounterKind.Named("divinity"), n(1), from = CostObjects.Self)),
            Instructions(steps = listOf(Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), n(1), ObjectFilter.Any,
                objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Opponent))))))),
            anyTime),
    )

    // CR 202.3: different mana values (not a count of permanents), nonland only
    @Test
    fun prismInsight() = card(
        name = "Prism Insight",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Draw a card for each different mana value among nonland permanents you control.",
    ) parsesTo abilities(
        spell(doing(Effect.Draw(Players.You, Value.CountDistinct(Objects.Each(and(nonland, permanentFilter, you)), SharedQuality.Number(NumericProperty.ManaValue))))),
    )

    // CR 707.2, 111.1: the token copies the exiled card and is also a Nightmare; the older Nightmare tokens go, the new one stays
    @Test
    fun gravetitheReaper() = card(
        name = "Gravetithe Reaper",
        cost = "{1}{B}{B}",
        type = "Creature — Demon Warlock",
        text = "{T}: Exile target creature card from a graveyard that was put there this turn. Create a token that's a copy of it, except it's a Nightmare in addition to its other types. Then exile all other Nightmare tokens you control.",
        pt = "3/2",
    ) parsesTo abilities(
        activated(
            cost(tap),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            create(TokenSpec.CopyOf(Objects.Bound("exiled"), except = listOf(CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Nightmare")))))), bind = "token"),
            move(Objects.Each(and(sub("Nightmare"), token, you, not(ObjectFilter.Is(Objects.Bound("token"))))), Zone.Exile),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard),
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Graveyard), role = EventRole.After)))),
        ),
    )

    // CR 118.9, 305.1: every library's top card, then any number of the spells (not lands) cast without paying
    @Test
    fun stormcallerElder() = card(
        name = "Stormcaller Elder",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Elder Dinosaur",
        text = "Whenever this creature attacks, exile the top card of each player's library, then you may cast any number of spells from among those cards without paying their mana costs.",
        pt = "6/6",
    ) parsesTo abilities(
        triggered(
            attacksSelf,
            move(Objects.LibraryCards(Players.Each(PlayerFilter.Any), n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(not(land), n(0), Value.Count(Objects.Bound("exiled")), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            Step.ForEach(Each.Objects(Objects.Bound("chosen")), bind = "card", steps = listOf(
                doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))),
        ),
    )

    // CR 120.3, 614.6: the Equipment is a second, optional target tied to the creature; the creature is exiled if it would die this turn
    @Test
    fun searingDissolution() = card(
        name = "Searing Dissolution",
        cost = "{2}{R}",
        type = "Instant",
        text = "This spell deals 5 damage to target creature. Exile up to one target Equipment attached to that creature. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(
            damage(5, toObjects(target("creature")), Objects.Self),
            move(target("Equipment"), Zone.Exile),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(arcanum.api.Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature),
                slot("Equipment", and(equipment, ObjectFilter.Attached(to = target("creature"))), min = 0)),
        ),
    )
}
