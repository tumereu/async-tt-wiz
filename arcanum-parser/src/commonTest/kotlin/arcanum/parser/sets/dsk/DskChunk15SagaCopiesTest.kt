package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Duskmourn chunk 15: the last unparsed card, a Saga whose chapters repeat a process while a condition holds ("… and
 * repeat this process", CR 608.2c) and copy cards to cast the copies (CR 707.12).
 */
class DskChunk15SagaCopiesTest {
    /** "Two cards that share a card type were milled this way": one milled card shares a card type with another. */
    private val milledShareACardType = Condition.ObjectMatches(
        Objects.Where(Objects.Bound("milled"), ObjectFilter.Shares(SharedQuality.CardType, Objects.Bound("milled"))),
        ObjectFilter.Any,
    )

    /** Mill, then draw and mill again for as long as two milled cards share a card type; copies of exiled cards, each of
     * which may be cast (CR 707.12a), paying its costs. */
    @Test
    fun theLedgerOfTamiyo() = card(
        name = "The Ledger of Tamiyo",
        cost = "{2}{U}",
        type = "Legendary Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I, II, III — Mill two cards. If two cards that share a card type were milled this way, draw a card and repeat this process.
            IV — Exile any number of target instant, sorcery, and/or Tamiyo planeswalker cards from your graveyard. Copy them. You may cast any number of the copies.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.Chapter(listOf(1, 2, 3)),
            instructions = Instructions(steps = listOf(
                Step.RepeatWhile(
                    steps = listOf(
                        Step.Do(Effect.Mill(Players.You, Value.Fixed(2), postBind = "milled")),
                        Step.If(milledShareACardType, then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
                    ),
                    condition = milledShareACardType,
                ),
            )),
        ),
        Ability.Triggered(
            trigger = Trigger.Chapter(listOf(4)),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "cards",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(
                            ObjectFilter.Type(CardType.Instant),
                            ObjectFilter.Type(CardType.Sorcery),
                            ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Tamiyo")), ObjectFilter.Type(CardType.Planeswalker))),
                        )),
                        ObjectFilter.InZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                    ))),
                    min = Value.Fixed(0),
                    max = null,
                )),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.CopyCard(Objects.Bound("exiled"), owner = Players.You, bind = "copies")),
                    // CR 707.12a: each copy may be cast or not
                    Step.ForEach(Each.Objects(Objects.Bound("copies")), bind = "copy", steps = listOf(
                        Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, caster = Players.You))), player = Players.You),
                    )),
                ),
            ),
        ),
    )
}
