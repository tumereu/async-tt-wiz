package arcanum.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [isManaAbility] decides mana abilities from the ability's shape (D54). */
class ManaAbilitiesTest {
    private fun mana(text: String) = ManaCost.parse(text)!!
    private fun addMana(text: String) = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana(text))), players = Players.You))
    private val addAnyColor = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1))), players = Players.You))
    private fun tapFor(vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(targets = targets, steps = steps.toList()), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))

    // CR 605.1a: "{T}: Add {G}."
    @Test
    fun tappingForManaIsAManaAbility() {
        assertTrue(tapFor(addMana("{G}")).isManaAbility())
    }

    // CR 605.1a: the Treasure token's "{T}, Sacrifice this token: Add one mana of any color." (CR 111.10a)
    @Test
    fun treasureIsAManaAbility() {
        assertTrue(PredefinedTokens.face("Treasure")!!.abilities.single().isManaAbility())
    }

    // CR 605.1a: mana added only on one branch still "could add mana": "{T}: You may pay {1}. If you do, add {B}{B}."
    @Test
    fun manaInsideAPaidBranchCounts() {
        val ability = tapFor(
            Step.Pay(Cost.mana(mana("{1}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(addMana("{B}{B}"))),
        )
        assertTrue(ability.isManaAbility())
    }

    // CR 605.1a, 605.5a: "{T}, Exile target land card from a graveyard: Add one mana of any color." has a target.
    @Test
    fun aTargetMakesItNotAManaAbility() {
        val ability = Ability.Activated(
            Cost(listOf(CostPart.TapSelf, CostPart.Exile(CostObjects.These(Objects.Target("land")), from = Zone.Graveyard))),
            Instructions(
                targets = listOf(TargetSlot("land", TargetKind.Objects(ObjectFilter.And(listOf(
                    ObjectFilter.Type(CardType.Land), ObjectFilter.InZone(Zone.Graveyard)))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(addAnyColor),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        )
        assertFalse(ability.isManaAbility())
    }

    // CR 605.1a, 606: "+1: Add {R}{R}." is a loyalty ability.
    @Test
    fun aLoyaltyAbilityIsNotAManaAbility() {
        val ability = Ability.Activated(Cost(listOf(CostPart.Loyalty(Value.Fixed(1)))), Instructions(steps = listOf(addMana("{R}{R}"))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))
        assertFalse(ability.isManaAbility())
    }

    // CR 605.1a: "{T}: Add {G}. Mill a card." moves a card from a library.
    @Test
    fun movingALibraryCardMakesItNotAManaAbility() {
        assertFalse(tapFor(addMana("{G}"), Step.Do(Effect.Mill(Players.You, Value.Fixed(1)))).isManaAbility())
    }

    // CR 605.1a: a cost that moves a card from a library ("{T}, Mill a card: Add {C}.").
    @Test
    fun aCostFromALibraryMakesItNotAManaAbility() {
        val ability = Ability.Activated(Cost(listOf(CostPart.TapSelf, CostPart.Mill(Value.Fixed(1)))), Instructions(steps = listOf(addMana("{C}"))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))
        assertFalse(ability.isManaAbility())
    }

    // CR 605.1a: an ability that can't add mana ("{T}: You gain 1 life.").
    @Test
    fun noManaNoManaAbility() {
        assertFalse(tapFor(Step.Do(Effect.GainLife(Players.You, Value.Fixed(1)))).isManaAbility())
    }

    // CR 605.1b: "Whenever you tap a Forest for mana, add an additional {G}."
    @Test
    fun aTriggerFromTappingForManaThatAddsManaIsAManaAbility() {
        val ability = Ability.Triggered(
            Trigger.On(EventPattern.Tap(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Forest")), ObjectFilter.ControlledBy(PlayerFilter.You))), forMana = true)),
            Instructions(steps = listOf(addMana("{G}"))),
        )
        assertTrue(ability.isManaAbility())
    }

    // CR 605.1b, 605.5a: triggering from another event ("Whenever you cast a spell, add {R}.") or adding no mana
    // ("Whenever you tap a land for mana, you gain 1 life.") isn't enough.
    @Test
    fun otherTriggersAreNotManaAbilities() {
        val onCast = Ability.Triggered(
            Trigger.On(EventPattern.Cast(by = PlayerFilter.You, spell = ObjectFilter.Any)),
            Instructions(steps = listOf(addMana("{R}"))),
        )
        val noMana = Ability.Triggered(
            Trigger.On(EventPattern.Tap(ObjectFilter.Type(CardType.Land), forMana = true)),
            Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))))),
        )
        assertFalse(onCast.isManaAbility())
        assertFalse(noMana.isManaAbility())
    }

    // CR 605.5b: a spell that adds mana is a spell ("Add {R}{R}{R}.").
    @Test
    fun aSpellIsNeverAManaAbility() {
        assertFalse(Ability.Spell(Instructions(steps = listOf(addMana("{R}{R}{R}")))).isManaAbility())
    }
}
