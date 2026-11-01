package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import kotlin.test.Test

/**
 * Chunk 57: the cube's Un-cards (D48). Only one of them reads as rules text: a burn spell carrying five keywords. The
 * other two stay manual and have no test: one adds half a red mana, which has no mana symbol in the rules, and one
 * depends on card borders, which aren't game characteristics.
 */
class Chunk57UnCardsTest {
    /** Each keyword is its own ability from the shared table: madness (CR 702.35a), cycling (702.29a), kicker
     * (702.33a), flashback (702.34a), buyback (702.27a). "If this spell was kicked" asks whether its kicker cost was
     * paid (CR 702.33d). */
    @Test
    fun blastFromBefore() = card(
        name = "Blast from Before",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Madness {R}, cycling {1}{R}, kicker {2}{R}, flashback {3}{R}, buyback {4}{R}
            Blast from Before deals 2 damage to any target. If this spell was kicked, create a 1/1 red Goblin creature token.
        """,
    ) parsesTo abilities(
        Keyword.Madness(manaCost("{R}")),
        Keyword.Cycling(manaCost("{1}{R}")),
        Keyword.Kicker(listOf(manaCost("{2}{R}"))),
        Keyword.Flashback(manaCost("{3}{R}")),
        Keyword.Buyback(manaCost("{4}{R}")),
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "any",
                what = TargetKind.Either(
                    objects = ObjectFilter.Or(listOf(
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.Type(CardType.Planeswalker),
                        ObjectFilter.Type(CardType.Battle),
                    )),
                    players = PlayerFilter.Any,
                ),
            )),
            steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(2))),
                Step.If(
                    condition = Condition.Paid(Keyword.Kicker.Kind),
                    then = listOf(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                        name = "Goblin Token",
                        colorIndicator = listOf(Color.Red),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Goblin")),
                        power = PrintedNumber.Fixed(1),
                        toughness = PrintedNumber.Fixed(1),
                    ))))),
                ),
            ),
        )),
    )
}
