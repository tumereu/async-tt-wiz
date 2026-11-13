package arcanum.parser.sets.rvr

import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk06ReplacementTest {
    private val selfEnters = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)

    private val entersTapped = static(Mechanic.Replacement(selfEnters, modify = listOf(Modification.EntersTapped)))

    private fun tapForEither(a: Color, b: Color) = activated(cost(tap), doing(Effect.AddMana(
        ManaProduction(ManaKind.Choose(amount = n(1), colors = listOf(ColorRef.Literal(a), ColorRef.Literal(b)))), Players.You)))

    // CR 614.1c: "enters tapped" is a replacement of the zone change; CR 106.1 mana colours chosen from the two named
    @Test
    fun guildgateLookalike() = card(
        name = "Vesper Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(entersTapped, tapForEither(Color.White, Color.Blue))

    // CR 614.1c, 614.12: the life payment is made as it enters; paying nothing makes it enter tapped. Its basic land types give the mana (CR 305.6).
    @Test
    fun shockLandLookalike() = card(
        name = "Cinder Font",
        cost = null,
        type = "Land — Swamp Mountain",
        text = """
            ({T}: Add {B} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(selfEnters, modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(cost = Cost(listOf(CostPart.PayLife(Value.Fixed(2)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))),
        ))))),
    )

    // CR 707.9, 614.1c: an optional enters-as-copy of any enchantment on the battlefield
    @Test
    fun copyEnchantmentLookalike() = card(
        name = "Mimic Veil",
        cost = "{2}{U}",
        type = "Enchantment",
        text = "You may have this enchantment enter as a copy of any enchantment on the battlefield.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(selfEnters, modify = listOf(Modification.EntersAsCopy(of = enchantment, optional = true)))),
    )

    // CR 111.1, 614.1: "one or more creature tokens would be created" replaced by that many 4/4 Angels with flying and vigilance
    @Test
    fun divineVisitationLookalike() = card(
        name = "Heavenly Calling",
        cost = "{3}{W}{W}",
        type = "Enchantment",
        text = "If one or more creature tokens would be created under your control, that many 4/4 white Angel creature tokens with flying and vigilance are created instead.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CreateToken(and(creature, you)),
            modify = listOf(Modification.CreateInstead(creatureToken("Angel", listOf(Color.White), 4, 4, abilities = listOf(Keyword.Flying, Keyword.Vigilance)))))),
    )
}
