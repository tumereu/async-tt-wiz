package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 04: Cleave (CR 702.148): an alternative cost; if it was paid, the words in square brackets are gone (CR 702.148a). The
 * cost is chosen before the targets (CR 601.2b, 601.2c), so a target whose words differ is two slots, each only when its reading applies.
 */
class VowChunk04CleaveTest {
    private val cleaved = Condition.Paid(Keyword.Cleave.Kind, times = 1, of = Objects.Self)
    private val notCleaved = Condition.Not(cleaved)
    private fun cleave(cost: String): Keyword = Keyword.Cleave(manaCost(cost))
    private fun onlyIf(slot: TargetSlot, condition: Condition) = slot.copy(onlyIf = condition)
    private val reminder = "(You may cast this spell for its cleave cost. If you do, remove the words in square brackets.)"

    // CR 702.148a, 500.7, 615.12 (damage can't be prevented during that extra turn only), 603.7 (a delayed trigger of that turn)
    @Test
    fun gamblersLastFlask() = card(
        name = "Gambler's Last Flask",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = """
            Cleave {4}{U}{U}{R} $reminder
            Take an extra turn after this one. During that turn, damage can't be prevented. [At the beginning of that turn's end step, you lose the game.]
            Exile Gambler's Last Flask.
        """,
    ) parsesTo abilities(
        cleave("{4}{U}{U}{R}"),
        spell(
            doing(Effect.ExtraTurn(Players.You, mark = "that turn")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), null,
                Duration.DuringTurn("that turn"))),
            ifThen(notCleaved, doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(doing(Effect.LoseGame(Players.You)))), inTurn = "that turn"))),
            move(Objects.Self, Zone.Exile),
        ),
    )

    // CR 702.148a, 601.2c: "target nonland permanent [you control]": yours unless cleaved
    @Test
    fun retrievalDraught() = card(
        name = "Retrieval Draught",
        cost = "{U}",
        type = "Instant",
        text = """
            Cleave {1}{U} $reminder
            Return target nonland permanent [you control] to its owner's hand.
        """,
    ) parsesTo abilities(
        cleave("{1}{U}"),
        spell(
            ifThen(notCleaved, move(target("permanent"), Zone.Hand), otherwise = listOf(move(target("second permanent"), Zone.Hand))),
            targets = listOf(
                onlyIf(slot("permanent", and(nonland, permanentFilter, you)), notCleaved),
                onlyIf(slot("second permanent", and(nonland, permanentFilter)), cleaved),
            ),
        ),
    )

    // CR 702.148a, 601.2c: "target [attacking] creature"
    @Test
    fun lanternReprisal() = card(
        name = "Lantern Reprisal",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Cleave {5}{W} $reminder
            Destroy target [attacking] creature.
        """,
    ) parsesTo abilities(
        cleave("{5}{W}"),
        spell(
            ifThen(notCleaved, destroy(target("creature")), otherwise = listOf(destroy(target("second creature")))),
            targets = listOf(
                onlyIf(slot("creature", and(attacking, creature)), notCleaved),
                onlyIf(slot("second creature", creature), cleaved),
            ),
        ),
    )

    // CR 702.148a, 402.2: the maximum hand size goes down by three for the rest of the game unless cleaved
    @Test
    fun suddenNotion() = card(
        name = "Sudden Notion",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Cleave {3}{U}{U} $reminder
            Draw three cards. [Your maximum hand size is reduced by three for the rest of the game.]
        """,
    ) parsesTo abilities(
        cleave("{3}{U}{U}"),
        spell(
            draw(3),
            ifThen(notCleaved, doing(Effect.ApplyMechanics(listOf(Mechanic.MaximumHandSizeChange(-3)), Affected.Players(Players.You),
                Duration.Indefinitely))),
        ),
    )

    // CR 702.148a, 107.3c: X is the number of creatures you control unless cleaved; cleaved, X is announced for the cleave cost (CR 107.3)
    @Test
    fun lamplightBurst() = card(
        name = "Lamplight Burst",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Cleave {X}{R}{W} $reminder
            Lamplight Burst deals X damage to target creature or planeswalker and you gain X life. [X is the number of creatures you control.]
        """,
    ) parsesTo abilities(
        cleave("{X}{R}{W}"),
        spell(
            ifThen(notCleaved,
                damage(count(and(creature, you)), Recipients(objects = target("permanent"))),
                Step.Do(Effect.GainLife(Players.You, count(and(creature, you)))),
                otherwise = listOf(
                    damage(Value.Announced(name = "X"), Recipients(objects = target("permanent"))),
                    Step.Do(Effect.GainLife(Players.You, Value.Announced(name = "X"))),
                ),
            ),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
        ),
    )

    // CR 702.148a, 601.2c: "target [Wolf or Werewolf] creature"; the draw happens either way
    @Test
    fun moonwardRepulse() = card(
        name = "Moonward Repulse",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Cleave {3}{U} $reminder
            Return target [Wolf or Werewolf] creature to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        cleave("{3}{U}"),
        spell(
            ifThen(notCleaved, move(target("creature"), Zone.Hand), otherwise = listOf(move(target("second creature"), Zone.Hand))),
            draw(1),
            targets = listOf(
                onlyIf(slot("creature", and(or(sub("Wolf"), sub("Werewolf")), creature)), notCleaved),
                onlyIf(slot("second creature", creature), cleaved),
            ),
        ),
    )

    // CR 702.148a, 601.2c: "target [Human] creature"; the life gain happens either way
    @Test
    fun leechingClutch() = card(
        name = "Leeching Clutch",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Cleave {1}{B}{B} $reminder
            Leeching Clutch deals 3 damage to target [Human] creature. You gain 3 life.
        """,
    ) parsesTo abilities(
        cleave("{1}{B}{B}"),
        spell(
            ifThen(notCleaved, damage(3, Recipients(objects = target("creature"))),
                otherwise = listOf(damage(3, Recipients(objects = target("second creature"))))),
            gainLife(3),
            targets = listOf(
                onlyIf(slot("creature", and(sub("Human"), creature)), notCleaved),
                onlyIf(slot("second creature", creature), cleaved),
            ),
        ),
    )

    // CR 702.148a, 601.2a: "target spell [that wasn't cast from its owner's hand]" (a card is only ever in its owner's hand, CR 400.3)
    @Test
    fun rinseAside() = card(
        name = "Rinse Aside",
        cost = "{U}",
        type = "Instant",
        text = """
            Cleave {1}{U}{U} $reminder
            Counter target spell [that wasn't cast from its owner's hand].
        """,
    ) parsesTo abilities(
        cleave("{1}{U}{U}"),
        spell(
            ifThen(notCleaved, doing(Effect.Counter(target("spell"))), otherwise = listOf(doing(Effect.Counter(target("second spell"))))),
            targets = listOf(
                onlyIf(slot("spell", and(spellFilter, not(ObjectFilter.WasCast(Zone.Hand)))), notCleaved),
                onlyIf(slot("second spell", spellFilter), cleaved),
            ),
        ),
    )

    // CR 702.148a, 701.23a: two bracketed parts in one sentence; cleaved, any card, not revealed
    @Test
    fun unearthTheRoot() = card(
        name = "Unearth the Root",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Cleave {1}{B}{B}{G} $reminder
            Search your library for a [basic land] card, [reveal it,] put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        cleave("{1}{B}{B}{G}"),
        spell(
            ifThen(notCleaved,
                search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle,
                otherwise = listOf(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
            ),
        ),
    )

    // CR 702.148a, 701.9b: you choose a nonland card [with mana value 2 or less]
    @Test
    fun hollowDirge() = card(
        name = "Hollow Dirge",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Cleave {2}{B} $reminder
            Target player reveals their hand. You choose a nonland card from it [with mana value 2 or less]. That player discards that card.
        """,
    ) parsesTo abilities(
        cleave("{2}{B}"),
        spell(
            doing(Effect.Reveal(handOf(Players.Target("player")), bind = "revealed")),
            ifThen(notCleaved,
                chooseFromHand(and(nonland, manaValue(Comparison.AtMost, 2))),
                otherwise = listOf(chooseFromHand(nonland)),
            ),
            doing(Effect.Discard(Players.Target("player"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 702.148a: "Destroy all creatures [with mana value 2 or less]."
    @Test
    fun roadOfDread() = card(
        name = "Road of Dread",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            Cleave {4}{W}{B} $reminder
            Destroy all creatures [with mana value 2 or less].
        """,
    ) parsesTo abilities(
        cleave("{4}{W}{B}"),
        spell(
            ifThen(notCleaved, destroy(Objects.Each(and(creature, manaValue(Comparison.AtMost, 2)))),
                otherwise = listOf(destroy(Objects.Each(creature)))),
        ),
    )

    // CR 702.148a: a card for each creature you control [with flying]
    @Test
    fun featheredOmen() = card(
        name = "Feathered Omen",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Cleave {4}{G}{U} $reminder
            Draw a card for each creature you control [with flying].
        """,
    ) parsesTo abilities(
        cleave("{4}{G}{U}"),
        spell(
            ifThen(notCleaved, doing(Effect.Draw(Players.You, count(and(creature, you, kw(Keyword.Flying))))),
                otherwise = listOf(doing(Effect.Draw(Players.You, count(and(creature, you)))))),
        ),
    )

    private fun chooseFromHand(filter: ObjectFilter): Step = Step.Choose(
        Choice.Objects(filter, min = n(1), max = n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You)
}
