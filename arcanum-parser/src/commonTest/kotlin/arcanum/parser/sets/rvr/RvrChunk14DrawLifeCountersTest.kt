package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica chunk 14: drawing and discarding, coin flips, granted keywords, life gain and loss, bloodthirst, counters. The cards
 * whose text an earlier chunk's parser test already reads (Liliana, Armory Guard, Chromatic Lantern, Fresh-Faced Recruit,
 * Sunder Shaman, Tolsimir) get engine scenarios only. */
class RvrChunk14DrawLifeCountersTest {
    // CR 602.5d (activate only as a sorcery), 121.1 (draw), 701.9a (discard)
    @Test
    fun dimirGuildmageLookalike() = card(
        name = "Dusk Guildmage", cost = "{U/B}{U/B}", type = "Creature — Human Wizard", pt = "2/2",
        text = """
            {3}{U}: Target player draws a card. Activate only as a sorcery.
            {3}{B}: Target player discards a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{3}{U}")), draw(1, Players.Target("player")), targets = listOf(targetPlayer), rules = asSorcery),
        activated(cost(mana("{3}{B}")), discard(1, Players.Target("player")), targets = listOf(targetPlayer), rules = asSorcery),
    )

    // CR 705.1 (flip a coin), 705.2 (winning the flip), 500.7 (an extra turn)
    @Test
    fun stitchInTimeLookalike() = card(
        name = "Stitch in Haste", cost = "{1}{U}{R}", type = "Sorcery",
        text = "Flip a coin. If you win the flip, take an extra turn after this one.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.FlipCoin(Players.You, bind = "flip")),
            Step.If(Condition.WonFlip("flip"), then = listOf(doing(Effect.ExtraTurn(Players.You))))),
    )

    // CR 611.3 (a static ability's condition), 601.2 (casting a spell this turn)
    @Test
    fun leapfrogLookalike() = card(
        name = "Hopfrog", cost = "{2}{U}", type = "Creature — Frog", pt = "3/1",
        text = "This creature has flying as long as you've cast an instant or sorcery spell this turn.",
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.HappenedThisTurn(EventPattern.Cast(or(instant, sorcery), PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
    )

    // CR 120.1 (damage dealt), 119.3 (gaining life): the amount of the damage event
    @Test
    fun mourningThrullLookalike() = card(
        name = "Grieving Thrull", cost = "{1}{W/B}", type = "Creature — Thrull", pt = "1/1",
        text = """
            Flying
            Whenever this creature deals damage, you gain that much life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter())), doing(Effect.GainLife(Players.You, Value.EventAmount))),
    )

    // CR 119.3, 119.4 (gain or lose life), 107.4 hybrid mana
    @Test
    fun orzhovGuildmageLookalike() = card(
        name = "Gloam Guildmage", cost = "{W/B}{W/B}", type = "Creature — Human Wizard", pt = "2/2",
        text = """
            {2}{W}: Target player gains 1 life.
            {2}{B}: Each player loses 1 life.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}{W}")), gainLife(1, Players.Target("player")), targets = listOf(targetPlayer)),
        activated(cost(mana("{2}{B}")), loseLife(1, Players.Each(PlayerFilter.Any))),
    )

    // CR 702.54a (bloodthirst), 702.19 (trample)
    @Test
    fun scabClanMaulerLookalike() = card(
        name = "Scab-Clan Brawler", cost = "{R}{G}", type = "Creature — Human Berserker", pt = "1/1",
        text = """
            Bloodthirst 2 (If an opponent was dealt damage this turn, this creature enters with two +1/+1 counters on it.)
            Trample
        """,
    ) parsesTo abilities(Keyword.Bloodthirst(2), Keyword.Trample)

    // CR 503.1a (upkeep of its controller), 119.3 (lose life)
    @Test
    fun moroiiLookalike() = card(
        name = "Moroii Shade", cost = "{2}{U}{B}", type = "Creature — Vampire", pt = "4/4",
        text = """
            Flying
            At the beginning of your upkeep, you lose 1 life.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(yourUpkeep(), loseLife(1)))

    // CR 708 / 709 (split cards), 122.1 (counters), 702.12 (indestructible), 111.1 (tokens), 702.20 (vigilance)
    @Test
    fun assureAssembleLookalike() = cardFaces(
        Layout.Split,
        card(
            name = "Secure", cost = "{G/W}{G/W}", type = "Instant",
            text = "Put a +1/+1 counter on target creature. That creature gains indestructible until end of turn.",
        ) to abilities(spell(counters(target("creature")), gains(target("creature"), Keyword.Indestructible),
            targets = listOf(slot("creature", creature)))),
        card(
            name = "Muster", cost = "{4}{G}{W}", type = "Instant",
            text = "Create three 2/2 green and white Elf Knight creature tokens with vigilance.",
        ) to abilities(spell(create(creatureToken("Elf Knight", listOf(Color.Green, Color.White), 2, 2, listOf(Keyword.Vigilance)), n(3)))),
    )

    // CR 602.2b (sacrifice as a cost), 122.1
    @Test
    fun dreadmalkinLookalike() = card(
        name = "Dreadmalkin Kit", cost = "{B}", type = "Creature — Zombie Cat", pt = "1/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            {2}{B}, Sacrifice another creature or planeswalker: Put two +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(mana("{2}{B}"), sacrificeA(and(another, or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
            counters(Objects.Self, 2)),
    )

    // CR 700.4 (dies), 122.1
    @Test
    fun risingPopulaceLookalike() = card(
        name = "Swelling Populace", cost = "{2}{W}", type = "Creature — Human", pt = "2/2",
        text = "Whenever another creature or planeswalker you control dies, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(dies(and(another, or(creature, ObjectFilter.Type(CardType.Planeswalker)), you)), counters(Objects.Self)),
    )
}
