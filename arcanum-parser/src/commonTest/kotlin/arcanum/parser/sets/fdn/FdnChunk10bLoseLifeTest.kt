package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.CostPart
import arcanum.api.CostObjects
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 10b of FDN: "loses N life" after another verb, piles, can't lose / win, prevention to and by this creature, land that becomes a creature. Invented names, the real oracle text. */
class FdnChunk10bLoseLifeTest {
    private val eachOpponent = Players.Each(PlayerFilter.Opponent)

    // CR 701.20 (separate into piles), 608.2 (the opponent's choice)
    @Test
    fun curatorOfDestinies() = card(
        name = "Warden of Fates",
        cost = "{4}{U}{U}",
        type = "Creature — Sphinx",
        pt = "5/5",
        text = """
            This spell can't be countered.
            Flying
            When this creature enters, look at the top five cards of your library and separate them into a face-down pile and a face-up pile. An opponent chooses one of those piles. Put that pile into your hand and the other into your graveyard.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Flying,
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(arcanum.api.Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            Step.Do(Effect.SeparatePiles(Objects.Bound("looked"), separator = Players.You, chooser = Players.Bound("opponent"), chosenBind = "chosen pile",
                otherBind = "other pile", faceDownPile = true)),
            move(Objects.Bound("chosen pile"), arcanum.api.Zone.Hand),
            move(Objects.Bound("other pile"), arcanum.api.Zone.Graveyard)),
    )

    // CR 117.3 (additional cost), 119.3 (lose life), 121.1 (draw); "and loses 2 life" keeps the subject "each opponent"
    @Test
    fun arbiterOfWoe() = card(
        name = "Judge of Sorrow",
        cost = "{4}{B}{B}",
        type = "Creature — Demon",
        pt = "5/4",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Flying
            When this creature enters, each opponent discards a card and loses 2 life. You draw a card and gain 2 life.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature)))), affects = Affected.Objects(Objects.Self)),
        Keyword.Flying,
        triggered(entersSelf, discard(1, eachOpponent), loseLife(2, eachOpponent), draw(1), gainLife(2)),
    )

    // CR 119.3, 608.2h (the destroyed permanent's mana value as it last existed)
    @Test
    fun feedTheSwarm() = card(
        name = "Feed the Flock",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Destroy target creature or enchantment an opponent controls. You lose life equal to that permanent's mana value.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")),
            Step.Do(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, target("permanent"), Combine.Single))),
            targets = listOf(slot("permanent", and(or(creature, enchantment), opponents)))),
    )

    // CR 104.3b (can't lose), 104.2b (an effect can't make them win), 104.2a (the last one left still wins)
    @Test
    fun heraldOfEternalDawn() = card(
        name = "Keeper of Endless Dusk",
        cost = "{4}{W}{W}{W}",
        type = "Creature — Angel",
        pt = "6/6",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Flying
            You can't lose the game and your opponents can't win the game.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        playerStatic(Mechanic.CantLoseGame),
        playerStatic(Mechanic.CantWinGame, players = eachOpponent),
    )

    // CR 602.5d (sorcery timing), 122.1 (counters), 119.3 (lose life equal to the count)
    @Test
    fun ravenousAmulet() = card(
        name = "Hungering Idol",
        cost = "{2}",
        type = "Artifact",
        text = """
            {1}, {T}, Sacrifice a creature: Draw a card and put a soul counter on this artifact. Activate only as a sorcery.
            {4}, {T}, Sacrifice this artifact: Each opponent loses life equal to the number of soul counters on this artifact.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeA(creature)), draw(1), counters(Objects.Self, 1, CounterKind.Named("soul")), rules = asSorcery),
        activated(cost(mana("{4}"), tap, sacrificeSelf),
            Step.Do(Effect.LoseLife(eachOpponent, Value.Of(NumericProperty.Counters(CounterKind.Named("soul")), Objects.Self, Combine.Single)))),
    )

    // CR 615.1 (prevention), 510.2 (combat damage dealt by and to the creature)
    @Test
    fun fogBank() = card(
        name = "Mist Barrier",
        cost = "{1}{U}",
        type = "Creature — Wall",
        pt = "0/2",
        text = """
            Defender (This creature can't attack.)
            Flying
            Prevent all combat damage that would be dealt to and dealt by this creature.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Keyword.Flying,
        static(
            Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), combat = true)),
            Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(objects = ObjectFilter.Any, players = PlayerFilter.Any), combat = true))),
    )

    // CR 611.2a (for good), 613.4b (all creature types), 613.7 (layer 7b), 205.1b (still a land)
    @Test
    fun soulstoneSanctuary() = card(
        name = "Ghostwalk Grotto",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}: This land becomes a 3/3 creature with vigilance and all creature types. It's still a land.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))),
        activated(manaCost("{4}"), doing(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.AllCreatureTypes,
            Mechanic.SetPowerToughness(n(3), n(3)), has(Keyword.Vigilance)), Affected.Objects(Objects.Self), Duration.Indefinitely))),
    )
}
