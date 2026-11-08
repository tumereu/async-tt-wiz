package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.CostChange
import arcanum.api.Effect
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Costs that read the spell's targets (CR 601.2f), on invented cards. */
class CostPerTargetWordingsTest {
    private fun self(mechanic: Mechanic) = Ability.Static(listOf(mechanic), affects = Affected.Objects(Objects.Self))
    private val thisSpell = ObjectFilter.Is(Objects.Self)
    private val tapThem = Ability.Spell(arcanum.api.Instructions(
        targets = listOf(TargetSlot("creatures", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(0), Value.Fixed(5))),
        steps = listOf(Step.Do(Effect.Tap(Objects.Target("creatures"))))))
    private val creatures = Objects.Target("creatures")

    // CR 601.2f: "for each target creature"
    @Test
    fun lessForEachTarget() = card(
        name = "Sweeping Hush",
        cost = "{5}",
        type = "Sorcery",
        text = "This spell costs {1} less to cast for each target creature.\nTap up to five target creatures.",
    ) parsesTo abilities(self(Mechanic.CostChange(thisSpell, CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(creatures)))), tapThem)

    // CR 601.2f (strive's template): "for each target beyond the first"
    @Test
    fun moreForEachTargetBeyondTheFirst() = card(
        name = "Striving Hush",
        cost = "{R}",
        type = "Sorcery",
        text = "This spell costs {1}{R} more to cast for each target creature beyond the first.\nTap up to five target creatures.",
    ) parsesTo abilities(self(Mechanic.CostChange(thisSpell, CostChange.More(ManaCost.parse("{1}{R}")!!,
        Value.Max(listOf(Value.Fixed(0), Value.Difference(Value.Count(creatures), Value.Fixed(1))))))), tapThem)

    // CR 601.2f: each creature type among the targets once (CR 205.3m)
    @Test
    fun moreForEachCreatureTypeAmongTargets() = card(
        name = "Typed Hush",
        cost = null,
        type = "Sorcery",
        text = "This spell costs {B}{B} more to cast for each creature type among target creatures.\nTap up to five target creatures.",
    ) parsesTo abilities(self(Mechanic.CostChange(thisSpell, CostChange.More(ManaCost.parse("{B}{B}")!!,
        Value.CountDistinct(creatures, SharedQuality.CreatureType)))), tapThem)

    // CR 601.2f: on the other side of the table
    @Test
    fun opponentsSpellsTargetingThisCostMore() = card(
        name = "Warded Hound",
        cost = "{1}",
        type = "Creature — Dog",
        pt = "1/1",
        text = "Spells your opponents cast that target this creature cost {2} more to cast.",
    ) parsesTo abilities(self(Mechanic.CostChange(
        ObjectFilter.And(listOf(ObjectFilter.ControlledBy(PlayerFilter.Opponent), ObjectFilter.Targets(objects = Objects.Self))),
        CostChange.More(ManaCost.parse("{2}")!!, Value.Fixed(1)))))
}
