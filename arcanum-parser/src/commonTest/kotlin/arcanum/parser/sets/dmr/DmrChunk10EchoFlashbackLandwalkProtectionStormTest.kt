package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 10: echo, flashback with several kinds of cost, landwalk, protection and storm. */
class DmrChunk10EchoFlashbackLandwalkProtectionStormTest {
    private val goblin = creatureToken("Goblin", listOf(Color.Red), 1, 1)

    // CR 702.30a, 700.4: echo; one trigger for entering and dying
    @Test
    fun moggRaiderCaptain() = card(
        name = "Mogg Raider Captain", cost = "{1}{R}", type = "Creature — Goblin Warrior", pt = "1/1",
        text = """
            Echo {1}{R} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
            When this creature enters or dies, create a 1/1 red Goblin creature token.
        """,
    ) parsesTo abilities(
        Keyword.Echo(cost(mana("{1}{R}"))),
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))), create(goblin)),
    )

    // CR 702.30a, 602.1
    @Test
    fun urzasBlueprintVault() = card(
        name = "Urza's Blueprint Vault", cost = "{6}", type = "Artifact",
        text = """
            Echo {6} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)
            {T}: Draw a card.
        """,
    ) parsesTo abilities(Keyword.Echo(cost(mana("{6}"))), activated(cost(tap), draw(1)))

    // CR 702.34a, 118.3: the tapped creatures must be white and untapped, and are chosen from your own
    @Test
    fun gullScreech() = card(
        name = "Gull Screech", cost = "{2}{W}{W}", type = "Sorcery",
        text = """
            Create two 1/1 white Bird creature tokens with flying.
            Flashback—Tap three untapped white creatures you control. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Bird", listOf(Color.White), 1, 1, listOf(Keyword.Flying)), n(2))),
        Keyword.Flashback(cost(CostPart.TapObjects(CostObjects.Choose(and(ObjectFilter.OfColor(Color.White), creature), n(3))))),
    )

    // CR 702.34a
    @Test
    fun callOfThePack() = card(
        name = "Call of the Pack", cost = "{2}{G}", type = "Sorcery",
        text = """
            Create a 3/3 green Elephant creature token.
            Flashback {3}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Elephant", listOf(Color.Green), 3, 3))),
        Keyword.Flashback(cost(mana("{3}{G}"))),
    )

    // CR 701.21a, 702.34a
    @Test
    fun chainersWrit() = card(
        name = "Chainer's Writ", cost = "{1}{B}", type = "Sorcery",
        text = """
            Target player sacrifices a creature of their choice.
            Flashback {5}{B}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetPlayer)),
        Keyword.Flashback(cost(mana("{5}{B}{B}"))),
    )

    // CR 702.34a, 118.3: flashback cost of mana and life
    @Test
    fun deepReflection() = card(
        name = "Deep Reflection", cost = "{3}{U}", type = "Sorcery",
        text = """
            Target player draws two cards.
            Flashback—{1}{U}, Pay 3 life. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(draw(2, Players.Target("player")), targets = listOf(targetPlayer)),
        Keyword.Flashback(cost(mana("{1}{U}"), CostPart.PayLife(n(3)))),
    )

    // CR 702.34a, 118.3: a sacrifice cost of three creatures
    @Test
    fun dreadRecall() = card(
        name = "Dread Recall", cost = "{2}{B}{B}", type = "Sorcery",
        text = """
            Return target creature card from your graveyard to the battlefield.
            Flashback—Sacrifice three creatures. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
        Keyword.Flashback(cost(CostPart.Sacrifice(CostObjects.Choose(creature, n(3))))),
    )

    // CR 702.34a: flashback on an instant with a different colour of mana
    @Test
    fun momentaryShift() = card(
        name = "Momentary Shift", cost = "{1}{W}", type = "Instant",
        text = """
            Exile target creature you control, then return it to the battlefield under its owner's control.
            Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        Keyword.Flashback(cost(mana("{3}{U}"))),
    )

    // CR 707.10, 702.34a, 608.2b: the copy is made only when it was cast from a graveyard
    @Test
    fun sevinnesRecovery() = card(
        name = "Sevinne's Recovery", cost = "{2}{W}", type = "Sorcery",
        text = """
            Return target permanent card with mana value 3 or less from your graveyard to the battlefield. If this spell was cast from a graveyard, you may copy this spell and may choose a new target for the copy.
            Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield),
            Step.If(Condition.CastFrom(Zone.Graveyard, Objects.Self),
                listOf(Step.May(listOf(Step.Do(Effect.CopySpell(Objects.Self, Players.You, newTargets = true))), Players.You))),
            targets = listOf(slot("card", inYourGraveyard(permanentCard, manaValue(Comparison.AtMost, 3))))),
        Keyword.Flashback(cost(mana("{4}{W}"))),
    )

    // CR 702.14c, 603.2: landwalk and a trigger on any player's black spell
    @Test
    fun solkanarTheMarshKing() = card(
        name = "Sol'kanar the Marsh King", cost = "{2}{U}{B}{R}", type = "Legendary Creature — Demon", pt = "5/5",
        text = """
            Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
            Whenever a player casts a black spell, you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Landwalk(sub("Swamp"), "Swampwalk"),
        triggered(on(EventPattern.Cast(ObjectFilter.OfColor(Color.Black), PlayerFilter.Any)), gainLife(1)),
    )

    // CR 702.16a, 207.2c: protection from a colour, threshold bonus as long as the condition holds
    @Test
    fun mysticWarden() = card(
        name = "Mystic Warden", cost = "{2}{G}{W}", type = "Creature — Human Nomad Mystic", pt = "3/3",
        text = """
            Protection from black
            Threshold — As long as there are seven or more cards in your graveyard, this creature gets +3/+3 and has flying.
        """,
    ) parsesTo abilities(
        Keyword.Protection(ObjectFilter.OfColor(Color.Black)),
        static(pt(3, 3), has(Keyword.Flying), condition = threshold).copy(abilityWord = "Threshold"),
    )

    // CR 702.16a, 701.19
    @Test
    fun spectralCat() = card(
        name = "Spectral Cat", cost = "{1}{W}", type = "Creature — Cat Spirit", pt = "2/1",
        text = """
            Protection from green
            {B}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        Keyword.Protection(ObjectFilter.OfColor(Color.Green)),
        activated(cost(mana("{B}")), Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Self)))),
    )

    // CR 702.16a: protection from a creature type; 701.34a proliferate
    @Test
    fun yawgmothThranSurgeon() = card(
        name = "Yawgmoth, Thran Surgeon", cost = "{2}{B}{B}", type = "Legendary Creature — Human Cleric", pt = "2/4",
        text = """
            Protection from Humans
            Pay 1 life, Sacrifice another creature: Put a -1/-1 counter on up to one target creature and draw a card.
            {B}{B}, Discard a card: Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Protection(sub("Human")),
        activated(cost(CostPart.PayLife(n(1)), sacrificeA(and(another, creature))),
            counters(target("creature"), 1, CounterKind.PowerToughness(-1, -1)), draw(1),
            targets = listOf(slot("creature", creature, min = 0))),
        activated(cost(mana("{B}{B}"), discardACard), doing(Effect.Action(KeywordAction.Proliferate))),
    )

    // CR 702.40a: storm
    @Test
    fun emptyTheBurrows() = card(
        name = "Empty the Burrows", cost = "{3}{R}", type = "Sorcery",
        text = """
            Create two 1/1 red Goblin creature tokens.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(spell(create(goblin, n(2))), Keyword.Storm)

    // CR 702.40a: copies may choose new targets
    @Test
    fun grapeshotVolley() = card(
        name = "Grapeshot Volley", cost = "{1}{R}", type = "Sorcery",
        text = """
            Grapeshot Volley deals 1 damage to any target.
            Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
        """,
    ) parsesTo abilities(spell(damage(1, toAny), targets = listOf(anyTarget)), Keyword.Storm)
}
