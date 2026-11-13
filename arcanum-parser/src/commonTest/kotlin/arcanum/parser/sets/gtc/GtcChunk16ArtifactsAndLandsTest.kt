package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
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
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 16: artifacts and lands. Damage prevented from its blockers (CR 615), Keyrunes that become creatures
 * until end of turn (CR 205.1b, 613.1d–e), targets that ignore hexproof for your spells and abilities (CR 702.11b),
 * copying the equipped creature's activated abilities (CR 707.10), damage to an opponent or planeswalker, Equipment, a
 * land that copies another and keeps this ability (CR 707.9b), a Guildgate and a land that taps unless you pay 2 life
 * (CR 614.12).
 */
class GtcChunk16ArtifactsAndLandsTest {
    private fun manaOf(a: Color, b: Color) = Ability.Activated(costOf(CostPart.TapSelf),
        steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(a), ColorRef.Literal(b)))), Players.You))), rules = anyTime)

    /** "[cost]: This artifact becomes a P/T [colours] [type] artifact creature [with …] until end of turn" (CR 205.1b). */
    private fun keyrune(cost: String, p: Int, t: Int, colors: List<Color>, subtype: String, vararg also: Mechanic) = Ability.Activated(mana(cost), steps(
        untilEndOfTurn(Objects.Self, *(listOf<Mechanic>(
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype(subtype)))),
            Mechanic.SetColors(colors.map { ColorRef.Literal(it) })) + also.toList()).toTypedArray()),
    ), rules = anyTime)

    // CR 615: combat damage from the creatures blocking it is prevented
    @Test
    fun armoredConvoy() = card(
        name = "Armored Convoy",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        pt = "2/1",
        text = "Prevent all combat damage that would be dealt to this creature by creatures blocking it.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.And(listOf(creature, ObjectFilter.Blocking(Objects.Self))),
            RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), combat = true))), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun legionKeyrune() = card(
        name = "Legion Keyrune",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {R} or {W}.
            {R}{W}: This artifact becomes a 1/1 red and white Soldier artifact creature with double strike until end of turn. (It deals both first-strike and regular combat damage.)
        """,
    ) parsesTo abilities(
        manaOf(Color.Red, Color.White),
        keyrune("{R}{W}", 1, 1, listOf(Color.Red, Color.White), "Soldier", Mechanic.SetPowerToughness(n(1), n(1)), Mechanic.GainAbilities(listOf(Keyword.DoubleStrike))),
    )

    // "can't be blocked this turn" ends with the turn, as the rest does
    @Test
    fun shadowKeyrune() = card(
        name = "Shadow Keyrune",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {U} or {B}.
            {U}{B}: This artifact becomes a 2/2 blue and black Horror artifact creature until end of turn and can't be blocked this turn.
        """,
    ) parsesTo abilities(
        manaOf(Color.Blue, Color.Black),
        keyrune("{U}{B}", 2, 2, listOf(Color.Blue, Color.Black), "Horror", Mechanic.SetPowerToughness(n(2), n(2)), Mechanic.CantBeBlocked),
    )

    @Test
    fun clanKeyrune() = card(
        name = "Clan Keyrune",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {R} or {G}.
            {R}{G}: This artifact becomes a 3/2 red and green Beast artifact creature with trample until end of turn.
        """,
    ) parsesTo abilities(
        manaOf(Color.Red, Color.Green),
        keyrune("{R}{G}", 3, 2, listOf(Color.Red, Color.Green), "Beast", Mechanic.SetPowerToughness(n(3), n(2)), Mechanic.GainAbilities(listOf(Keyword.Trample))),
    )

    @Test
    fun syndicateKeyrune() = card(
        name = "Syndicate Keyrune",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {W} or {B}.
            {W}{B}: This artifact becomes a 1/4 white and black Thrull artifact creature with lifelink until end of turn.
        """,
    ) parsesTo abilities(
        manaOf(Color.White, Color.Black),
        keyrune("{W}{B}", 1, 4, listOf(Color.White, Color.Black), "Thrull", Mechanic.SetPowerToughness(n(1), n(4)), Mechanic.GainAbilities(listOf(Keyword.Lifelink))),
    )

    @Test
    fun hybridKeyrune() = card(
        name = "Hybrid Keyrune",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {G} or {U}.
            {G}{U}: This artifact becomes a 2/3 green and blue Crab artifact creature with hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        manaOf(Color.Green, Color.Blue),
        keyrune("{G}{U}", 2, 3, listOf(Color.Green, Color.Blue), "Crab", Mechanic.SetPowerToughness(n(2), n(3)), Mechanic.GainAbilities(listOf(Keyword.Hexproof))),
    )

    // CR 702.11b: only your spells and abilities ignore their hexproof
    @Test
    fun glaringSearchlight() = card(
        name = "Glaring Searchlight",
        cost = "{1}",
        type = "Artifact",
        text = """
            Creatures your opponents control with hexproof can be the targets of spells and abilities you control as though they didn't have hexproof.
            {3}, Sacrifice this artifact: Creatures you control gain hexproof until end of turn and can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.TargetableDespiteHexproof(by = you)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, opponents, ObjectFilter.HasKeyword(Keyword.Hexproof)))))),
        Ability.Activated(costOf(manaPart("{3}"), CostPart.Sacrifice(CostObjects.Self)),
            steps(untilEndOfTurn(Objects.Each(creatureYouControl), Mechanic.GainAbilities(listOf(Keyword.Hexproof)), Mechanic.CantBeBlocked)), rules = anyTime),
    )

    // CR 605.3b: mana abilities don't use the Stack and aren't copied; CR 707.10: the copy, new targets allowed
    @Test
    fun mirrorImageBracers() = card(
        name = "Mirror Image Bracers",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Whenever an ability of equipped creature is activated, if it isn't a mana ability, copy that ability. You may choose new targets for the copy.
            Equip {3}
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Activate(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), by = PlayerFilter.Any, manaAbilities = false)),
            steps(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true))),
        ),
        Keyword.Equip(mana("{3}")),
    )

    @Test
    fun augursPrism() = card(
        name = "Augur's Prism",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, draw a card.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.Draw(Players.You, n(1)))),
        Ability.Activated(costOf(manaPart("{1}"), CostPart.TapSelf), steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))), rules = anyTime),
    )

    // CR 115.4: an opponent or a planeswalker
    @Test
    fun bladetipWhip() = card(
        name = "Bladetip Whip",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: This artifact deals 1 damage to target opponent or planeswalker.",
    ) parsesTo abilities(
        Ability.Activated(costOf(manaPart("{1}"), CostPart.TapSelf), Instructions(
            targets = listOf(TargetSlot("opponent or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Opponent), min = n(1), max = n(1))),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "opponent or planeswalker"), n(1), source = Objects.Self))),
        ), rules = anyTime),
    )

    @Test
    fun riotersGear() = card(
        name = "Rioter's Gear",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+2.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(2))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Keyword.Equip(mana("{2}")),
    )

    @Test
    fun skyblindStaff() = card(
        name = "Skyblind Staff",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and can't be blocked by creatures with flying.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.CantBeBlockedBy(ObjectFilter.And(listOf(creature, ObjectFilter.HasKeyword(Keyword.Flying))))),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Keyword.Equip(mana("{3}")),
    )

    // CR 707.9b: the copy keeps this ability
    @Test
    fun actorsStage() = card(
        name = "Actor's Stage",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}: This land becomes a copy of target land, except it has this ability.
        """,
    ) parsesTo abilities(
        Ability.Activated(costOf(CostPart.TapSelf), steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))), rules = anyTime),
        Ability.Activated(costOf(manaPart("{2}"), CostPart.TapSelf), Instructions(
            targets = listOf(slot("land", land)),
            steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Target("land"), listOf(CopyException.ThisAbility))),
                Affected.Objects(Objects.Self), Duration.Indefinitely))),
        ), rules = anyTime),
    )

    @Test
    fun legionGatehouse() = card(
        name = "Legion Gatehouse",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {R} or {W}.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
            affects = Affected.Objects(Objects.Self)),
        manaOf(Color.Red, Color.White),
    )

    // CR 614.12: decided as it enters; its basic land types give its mana (CR 305.6)
    @Test
    fun sanctifiedForge() = card(
        name = "Sanctified Forge",
        cost = null,
        type = "Land — Mountain Plains",
        text = """
            ({T}: Add {R} or {W}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.AsEnters(listOf(
                Step.Pay(Cost(listOf(CostPart.PayLife(n(2)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))),
            ))))), affects = Affected.Objects(Objects.Self)),
    )
}
