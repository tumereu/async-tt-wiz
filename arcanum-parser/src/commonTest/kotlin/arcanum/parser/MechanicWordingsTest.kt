package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.drawCards
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** Wordings of mechanics that used to be done by hand ([MechanicWordings]), one test per wording, on invented cards. */
class MechanicWordingsTest {
    private val tempts = Step.Do(Effect.Action(KeywordAction.TheRingTemptsYou))
    private val yourRingBearer = ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Marked(ObjectMark.RingBearer)),
        ObjectFilter.ControlledBy(PlayerFilter.You)))

    // CR 701.54a: "the Ring tempts you" as an effect of a trigger
    @Test
    fun ringTemptsYouAsItEnters() = card(
        name = "Barrow Lurker",
        cost = "{1}{B}",
        type = "Creature — Wraith",
        pt = "2/1",
        text = "When this creature enters, the Ring tempts you.",
    ) parsesTo abilities(whenThisEnters(tempts))

    // CR 701.54a: "The Ring tempts you." as a spell's last sentence
    @Test
    fun ringTemptsYouAfterASpell() = card(
        name = "Shadow's Reach",
        cost = "{2}{B}",
        type = "Instant",
        text = "Destroy target creature. The Ring tempts you.",
    ) parsesTo abilities(Ability.Spell(Instructions(
        targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))),
        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature"))), tempts))))

    // CR 701.54d: "Whenever the Ring tempts you, if you chose a creature other than [this] as your Ring-bearer, …"
    @Test
    fun wheneverTheRingTemptsYouAnotherBearer() = card(
        name = "Grey Wanderer",
        cost = "{2}{U}",
        type = "Legendary Creature — Avatar Wizard",
        pt = "2/3",
        text = "Whenever the Ring tempts you, if you chose a creature other than Grey Wanderer as your Ring-bearer, draw a card.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.RingTempts(PlayerFilter.You)), Instructions(steps = listOf(drawCards(1))),
        interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))))

    // CR 701.54e: "if you don't control a Ring-bearer"
    @Test
    fun temptsIfNoRingBearer() = card(
        name = "Shire Wayfarer",
        cost = "{G}",
        type = "Creature — Halfling Scout",
        pt = "1/1",
        text = "Whenever a land you control enters, if you don't control a Ring-bearer, the Ring tempts you.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land),
        ObjectFilter.ControlledBy(PlayerFilter.You))), to = Zone.Battlefield)), Instructions(steps = listOf(tempts)),
        interveningIf = Condition.Not(Condition.Exists(yourRingBearer, 1))))

    // CR 701.54e: "As long as [this] is your Ring-bearer, …"
    @Test
    fun asLongAsItIsYourRingBearer() = card(
        name = "Bold Ringward",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/2",
        text = "As long as Bold Ringward is your Ring-bearer, it gets +2/+0.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0))), Affected.Objects(Objects.Self),
        condition = Condition.ObjectMatches(Objects.Self, yourRingBearer)))

    // ---- Soulbond (CR 702.95)

    private val pair = Affected.Objects(Objects.Union(listOf(Objects.Self, Objects.PairedWith(Objects.Self))))
    private val paired = Condition.ObjectMatches(Objects.Self, ObjectFilter.Paired)

    // CR 702.95b: "both creatures have [keywords]"
    @Test
    fun pairedBothHaveKeywords() = card(
        name = "Bonded Skystalker",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Soulbond
            As long as this creature is paired with another creature, both creatures have flying and lifelink.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Flying, arcanum.api.Keyword.Lifelink))), pair, condition = paired))

    // CR 702.95b: "each of those creatures gets +N/+N"
    @Test
    fun pairedEachGets() = card(
        name = "Bonded Grovebrute",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "3/3",
        text = """
            Soulbond
            As long as Bonded Grovebrute is paired with another creature, each of those creatures gets +2/+2.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2))), pair, condition = paired))

    // CR 702.95b: "each of those creatures has "[ability]""
    @Test
    fun pairedEachHasAnAbility() = card(
        name = "Bonded Tinkerer",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/2",
        text = """
            Soulbond
            As long as this creature is paired with another creature, each of those creatures has "{2}{U}: Untap this creature."
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Ability.Activated(arcanum.api.Cost.mana(arcanum.api.ManaCost.parse("{2}{U}")!!),
            Instructions(steps = listOf(Step.Do(Effect.Untap(Objects.Self)))), arcanum.api.ActivationRules(arcanum.api.Timing.Any, PlayerFilter.You)
            )))), pair, condition = paired))
}
