package arcanum.parser.sets.fdn

import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 09b of FDN: gain / gains wordings (life, control, keywords, flashback) and Tinybones's exile-with-counter. Invented names, the real oracle text. */
class FdnChunk09bGainControlTest {
    // CR 701.9 (discard), 400.7 (the card is a new object in exile), 122.1 (a counter on it), 118.14 and 609.4b (mana of any type),
    // 117.1a (sorcery timing), 601.2 / 305.1 (play from exile)
    @Test
    fun tinybonesBaubleBurglar() = card(
        name = "Stashbone Pilferer",
        cost = "{1}{B}",
        type = "Legendary Creature — Skeleton Rogue",
        pt = "1/3",
        text = """
            Whenever an opponent discards a card, exile it from their graveyard with a stash counter on it.
            During your turn, you may play cards you don't own with stash counters on them from exile, and mana of any type can be spent to cast those spells.
            {3}{B}, {T}: Each opponent discards a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.Opponent)),
            move(Objects.EventObject(arcanum.api.EventRole.After), Zone.Exile, postBind = "exiled"),
            counters(Objects.Bound("exiled"), 1, CounterKind.Named("stash"))),
        static(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You), condition = Condition.YourTurn,
            affects = Objects.Each(and(inZone(Zone.Exile), not(ObjectFilter.OwnedBy(PlayerFilter.You)), ObjectFilter.HasCounter(CounterKind.Named("stash"), 1)))),
        playerStatic(Mechanic.SpendManaAsAnyColor(onlyFor = and(ObjectFilter.WasCast(Zone.Exile), not(ObjectFilter.OwnedBy(PlayerFilter.You))), anyType = true),
            condition = Condition.YourTurn),
        activated(cost(mana("{3}{B}"), tap), discard(1, Players.Each(PlayerFilter.Opponent)), rules = asSorcery),
    )

    // CR 702.19b (trample), 611.2c, 208.1 (the creature's power as the ability resolves)
    @Test
    fun ashrootAnimist() = card(
        name = "Rootwhip Animist",
        cost = "{2}{R}{G}",
        type = "Creature — Lizard Druid",
        pt = "4/4",
        text = """
            Trample
            Whenever this creature attacks, another target creature you control gains trample and gets +X/+X until end of turn, where X is this creature's power.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(attacksSelf,
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(Objects.Self), power(Objects.Self)), has(Keyword.Trample)),
            targets = listOf(slot("creature", and(another, creatureYouControl)))),
    )

    // CR 120.4a (life lost), 119.3 (gain life), 603.2 (triggers on each loss)
    @Test
    fun bloodthirstyConqueror() = card(
        name = "Bloodbound Usurper",
        cost = "{3}{B}{B}",
        type = "Creature — Vampire Knight",
        pt = "5/5",
        text = """
            Flying, deathtouch
            Whenever an opponent loses life, you gain that much life. (Damage causes loss of life.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        triggered(on(EventPattern.LoseLife(PlayerFilter.Opponent)), Step.Do(Effect.GainLife(Players.You, Value.EventAmount))),
    )

    // CR 701.8 (destroy), 608.2c (counts only those destroyed: indestructible ones aren't), 119.3
    @Test
    fun fumigate() = card(
        name = "Pyre of Reckoning",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Destroy all creatures. You gain 1 life for each creature destroyed this way.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Destroy(Objects.Each(creature), postBind = "destroyed")),
            Step.Do(Effect.GainLife(Players.You, Value.Times(Value.Count(Objects.Bound("destroyed")), 1)))),
    )

    // CR 613.1b (control: layer 2, no end), 115.1 (both targets)
    @Test
    fun harmlessOffering() = card(
        name = "Cursed Tribute",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Target opponent gains control of target permanent you control.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Target("opponent"))), Affected.Objects(target("permanent")), Duration.Indefinitely)),
            targets = listOf(targetOpponent, slot("permanent", and(permanentFilter, you)))),
    )

    // CR 611.2c ("those creatures" are the ones counted), 702.12a (indestructible)
    @Test
    fun inspiringCall() = card(
        name = "Rallying Cry of Growth",
        cost = "{2}{G}",
        type = "Instant",
        text = "Draw a card for each creature you control with a +1/+1 counter on it. Those creatures gain indestructible until end of turn.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Draw(Players.You, count(and(creatureYouControl, ObjectFilter.HasCounter(p1p1, 1))))),
            gains(Objects.Each(and(creatureYouControl, ObjectFilter.HasCounter(p1p1, 1))), Keyword.Indestructible)),
    )

    // CR 702.34a (flashback: cast it from the graveyard for the cost), 202.1 (its mana cost)
    @Test
    fun sphinxOfForgottenLore() = card(
        name = "Sphinx of Buried Memory",
        cost = "{2}{U}{U}",
        type = "Creature — Sphinx",
        pt = "3/3",
        text = """
            Flash
            Flying
            Whenever this creature attacks, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to that card's mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(attacksSelf,
            untilEndOfTurn(target("card"), Mechanic.GainAbilities(listOf(Keyword.Flashback(arcanum.api.Cost(listOf(arcanum.api.CostPart.ItsManaCost)))))),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
    )

    // CR 207.2c (Alliance), 603.4, 611.2c; "the second time this ability has resolved this turn" counts this resolution
    @Test
    fun venomConnoisseur() = card(
        name = "Fangtooth Brewer",
        cost = "{1}{G}",
        type = "Creature — Human Druid",
        pt = "2/2",
        text = "Alliance — Whenever another creature you control enters, this creature gains deathtouch until end of turn. If this is the second time this ability has resolved this turn, all creatures you control gain deathtouch until end of turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(another, creatureYouControl), to = Zone.Battlefield)),
            gains(Objects.Self, Keyword.Deathtouch),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(2)),
                then = listOf(gains(Objects.Each(creatureYouControl), Keyword.Deathtouch))),
            abilityWord = "Alliance"),
    )
}
