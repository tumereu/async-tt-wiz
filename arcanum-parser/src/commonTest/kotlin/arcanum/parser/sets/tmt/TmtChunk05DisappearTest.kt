package arcanum.parser.sets.tmt

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 05: disappear ("if a permanent left the battlefield under your control this turn", an ability word,
 * CR 207.2c, looking back over the turn): end step and enters triggers with an intervening "if" (CR 603.4), entering with counters (CR
 * 614.1c); sacrificing three Rats to return a card and those with its name; exiling until a nonland card is exiled for each opponent and
 * casting one of the exiled cards for free (CR 607.2a); counters on an artifact that then becomes a creature.
 */
class TmtChunk05DisappearTest {
    @Test
    fun shadowClanMystic() = card(
        name = "Shadow Clan Mystic",
        cost = "{3}{B}",
        type = "Creature — Human Ninja Warlock",
        text = """
            Lifelink
            Disappear — When this creature enters, if a permanent left the battlefield under your control this turn, create a 1/1 black Ninja creature token.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, create(ninjaToken), interveningIf = permanentLeftThisTurn, abilityWord = "Disappear"),
    )

    @Test
    fun bugbaneExterminator() = card(
        name = "Bugbane Exterminator",
        cost = "{2}{B}",
        type = "Creature — Insect Mutant",
        text = """
            Flying
            Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        disappearAtEndStep(scry(1)),
    )

    @Test
    fun overlordChitinSwarmInvader() = card(
        name = "Overlord Chitin, Swarm Invader",
        cost = "{3}{B}",
        type = "Legendary Creature — Insect Warrior",
        text = """
            Flying
            Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, create a 1/1 black Insect Warrior creature token with flying.
            {3}{G}, Sacrifice a token: Draw a card.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flying,
        disappearAtEndStep(create(creatureToken("Insect Warrior", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Flying)))),
        activated(cost(mana("{3}{G}"), sacrificeA(token)), draw(1)),
    )

    @Test
    fun squeakVerminMonarch() = card(
        name = "Squeak, Vermin Monarch",
        cost = "{1}{B}",
        type = "Legendary Creature — Rat Avatar",
        text = """
            Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, create a 1/1 black Rat creature token and put a +1/+1 counter on Squeak.
            {T}, Sacrifice three Rats: Return target creature card and all other cards with the same name as that card from your graveyard to the battlefield tapped.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        disappearAtEndStep(create(creatureToken("Rat", listOf(Color.Black), 1, 1)), counters(Objects.Self)),
        activated(cost(tap, CostPart.Sacrifice(CostObjects.Choose(sub("Rat"), count = n(3)))),
            move(Objects.Union(listOf(target("card"), Objects.Each(and(not(ObjectFilter.Is(target("card"))), ObjectFilter.SameNameAs(target("card")),
                inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))), Zone.Battlefield, tapped = true),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun diceGameMaster() = card(
        name = "Dice, Game Master",
        cost = "{2}{G}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = "Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, put a +1/+1 counter on Dice.",
        pt = "3/3",
    ) parsesTo abilities(
        disappearAtEndStep(counters(Objects.Self)),
    )

    // CR 118.12: "you may [cost]. If you do"
    @Test
    fun westerlyGaleAvatar() = card(
        name = "Westerly Gale Avatar",
        cost = "{5}{G}{G}",
        type = "Creature — Cat Spirit Avatar",
        text = """
            Trample
            Whenever this creature enters or attacks, you may sacrifice a token or a land. If you do, you gain 3 life.
            Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, draw a card.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersOrAttacksSelf,
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(or(token, land), count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(gainLife(3)))),
        disappearAtEndStep(draw(1)),
    )

    // CR 607.2a: the cards each opponent exiled are linked to the second ability; CR 601.2a: cast for free from exile
    @Test
    fun brainlordAndBladefist() = card(
        name = "Brainlord & Bladefist",
        cost = "{4}{U/B}{U/B}",
        type = "Legendary Creature — Utrom Human Ninja",
        text = """
            Whenever Brainlord & Bladefist enter or attack, each opponent exiles cards from the top of their library until they exile a nonland card.
            Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, you may cast a card exiled with Brainlord & Bladefist without paying its mana cost.
        """,
        pt = "6/7",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "player", together = true, steps = listOf(
            doing(Effect.RevealUntil(nonland, Players.Bound("player"), n(1), reveal = false, into = Zone.Exile, foundBind = "found", restBind = "rest")),
            doing(Effect.Remember("exiled", Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest")))))))),
        disappearAtEndStep(
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile))), bind = "toCast", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
    )

    // CR 205.1b: a creature in addition to its other types (layer 4), 0/0 (layer 7b)
    @Test
    fun crustfaceGastromancer() = card(
        name = "Crustface, Gastromancer",
        cost = "{3}{B}{G}",
        type = "Legendary Artifact Creature — Food Mutant",
        text = """
            When Crustface enters, create a Food token.
            Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, put three +1/+1 counters on up to one other target artifact or creature. If it isn't a creature, it becomes a 0/0 Mutant creature in addition to its other types.
            {10}, {T}, Sacrifice Crustface: You gain 15 life.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(entersSelf, createFood()),
        disappearAtEndStep(
            counters(target("permanent"), 3),
            Step.If(Condition.ObjectMatches(target("permanent"), not(creature)), then = listOf(doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Mutant")))),
                Mechanic.SetPowerToughness(n(0), n(0))), Affected.Objects(target("permanent")), arcanum.api.Duration.Indefinitely)))),
            targets = listOf(slot("permanent", and(another, or(artifact, creature)), min = 0))),
        activated(cost(mana("{10}"), tap, sacrificeSelf), gainLife(15)),
    )

    // CR 614.1c: a replacement effect as it enters, checked then
    @Test
    fun festeringFriends() = card(
        name = "Festering Friends",
        cost = "{2}{B/G}{B/G}",
        type = "Creature — Human Ooze Mutant",
        text = """
            Deathtouch
            Disappear — This creature enters with two +1/+1 counters on it if a permanent left the battlefield under your control this turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(2)))), condition = permanentLeftThisTurn).copy(abilityWord = "Disappear"),
    )
}
