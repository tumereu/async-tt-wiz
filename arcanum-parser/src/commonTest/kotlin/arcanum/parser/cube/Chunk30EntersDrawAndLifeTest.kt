package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 30: creatures whose enters trigger draws, looks at the library (scry, surveil, investigate), loots, discards
 * hands or moves life; with their other lines: affinity, escape and its counter, unearth, persist, a conditional
 * keyword, an upkeep trigger from the graveyard and a return from the graveyard.
 */
class Chunk30EntersDrawAndLifeTest {
    // ---- Shared shapes

    private val affinityForArtifacts = Keyword.Affinity(forWhat = ObjectFilter.Type(CardType.Artifact), printed = "artifacts")
    private val aCard = ObjectFilter.Category(ObjectCategory.Card)

    // ---- Draw

    @Test
    fun venomousOwlet() = card(
        name = "Venomous Owlet",
        cost = "{U}{B}",
        type = "Artifact Creature — Bird",
        pt = "1/1",
        text = """
            Flying, deathtouch
            When this creature enters, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        whenThisEnters(drawCards(1)),
    )

    @Test
    fun elvishSeerling() = card(
        name = "Elvish Seerling",
        cost = "{1}{G}",
        type = "Creature — Elf Shaman",
        pt = "1/1",
        text = "When this creature enters, draw a card.",
    ) parsesTo abilities(
        whenThisEnters(drawCards(1)),
    )

    @Test
    fun loyalHearthhound() = card(
        name = "Loyal Hearthhound",
        cost = "{1}{W}",
        type = "Enchantment Creature — Dog",
        pt = "1/1",
        text = "When this creature enters, draw a card.",
    ) parsesTo abilities(
        whenThisEnters(drawCards(1)),
    )

    @Test
    fun wallOfPortents() = card(
        name = "Wall of Portents",
        cost = "{1}{W}",
        type = "Creature — Wall",
        pt = "0/4",
        text = """
            Defender
            When this creature enters, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        whenThisEnters(drawCards(1)),
    )

    @Test
    fun frostScaleSerpent() = card(
        name = "Frost-Scale Serpent",
        cost = "{G}{U}",
        type = "Snow Creature — Snake",
        pt = "1/1",
        text = """
            Flash
            Flying
            When this creature enters, draw a card.
            This creature has deathtouch as long as you control at least three other snow permanents.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        whenThisEnters(drawCards(1)),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Deathtouch))),
            condition = Condition.Exists(
                filter = allOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Super(Supertype.Snow),
                    ObjectFilter.Category(ObjectCategory.Permanent),
                    ObjectFilter.ControlledBy(PlayerFilter.You),
                ),
                atLeast = 3,
            ),
        ),
    )

    // CR 702.41a: "This spell costs {1} less to cast for each artifact you control."
    @Test
    fun cogitatingSentry() = card(
        name = "Cogitating Sentry",
        cost = "{6}{U}",
        type = "Artifact Creature — Construct",
        pt = "2/2",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            When this creature enters, draw two cards.
        """,
    ) parsesTo abilities(
        affinityForArtifacts,
        Keyword.Flying,
        whenThisEnters(drawCards(2)),
    )

    // CR 107.3c: X is defined by the text. CR 700.4: "dies" is put into a graveyard from the battlefield.
    @Test
    fun gravecallerBanneret() = card(
        name = "Gravecaller Banneret",
        cost = "{2}{B}",
        type = "Creature — Zombie Knight",
        pt = "3/1",
        text = """
            Flash
            When this creature enters, draw X cards, where X is the number of creatures that died under your control this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        whenThisEnters(
            Step.Do(Effect.Draw(
                players = Players.You,
                count = Value.EventsThisTurn(
                    event = EventPattern.ZoneChange(
                        objects = allOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You)),
                        from = Zone.Battlefield,
                        to = Zone.Graveyard,
                    ),
                    measure = Measure.Objects,
                ),
            )),
        ),
    )

    // ---- Library and clues

    @Test
    fun faerieAugur() = card(
        name = "Faerie Augur",
        cost = "{U}",
        type = "Creature — Faerie Wizard",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(Step.Do(Effect.Scry(Players.You, count = Value.Fixed(2)))),
    )

    // CR 603.4: the intervening "if" is checked when it triggers and as it resolves; CR 113.6b: it says the ability works
    // in the graveyard.
    @Test
    fun deathlessSavant() = card(
        name = "Deathless Savant",
        cost = "{1}{U}{B}",
        type = "Creature — Zombie Wizard",
        pt = "3/1",
        text = """
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            At the beginning of your upkeep, if this card is in your graveyard, you may pay 1 life. If you do, return it to your hand.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.Surveil(Players.You, count = Value.Fixed(2)))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(cost = Cost(listOf(CostPart.PayLife(Value.Fixed(1)))), bind = "paid"),
                    Step.If(
                        condition = Condition.Did("paid"),
                        then = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Hand)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun hollowellInvestigator() = card(
        name = "Hollowell Investigator",
        cost = "{W}",
        type = "Creature — Human Soldier",
        pt = "1/2",
        text = """When this creature enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.Action(KeywordAction.Investigate()))),
    )

    // ---- Discard

    // CR 118.12: "you may discard a card. If you do" is a cost paid on resolution.
    @Test
    fun junkyardCur() = card(
        name = "Junkyard Cur",
        cost = "{2}",
        type = "Artifact Creature — Dog",
        pt = "2/1",
        text = """
            When this creature enters, you may discard a card. If you do, draw a card.
            Unearth {1}{R} ({1}{R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Pay(cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(aCard)))), bind = "paid"),
            Step.If(condition = Condition.Did("paid"), then = listOf(drawCards(1))),
        ),
        Keyword.Unearth(manaCost("{1}{R}")),
    )

    // CR 702.138c: "escapes with" means "If this permanent escaped, it enters with …".
    @Test
    fun bullOfThePyre() = card(
        name = "Bull of the Pyre",
        cost = "{3}{R}{R}",
        type = "Creature — Ox",
        pt = "4/2",
        text = """
            When this creature enters, discard your hand, then draw three cards.
            Escape—{R}{R}, Exile eight other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)
            This creature escapes with a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.Discard(Players.You, objects = Objects.Each(allOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            drawCards(3),
        ),
        Keyword.Escape(Cost(listOf(
            CostPart.Mana(ManaCost.parse("{R}{R}")!!),
            CostPart.Exile(
                what = CostObjects.Choose(allOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), aCard), count = Value.Fixed(8)),
                from = Zone.Graveyard,
            ),
        ))),
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(1))),
            )),
            condition = Condition.Paid(Keyword.Escape.Kind),
        ),
    )

    // CR 118.12: each opponent's discard is a mandatory payment, so whether they couldn't is known; CR 101.4: together.
    @Test
    fun rebuiltRatling() = card(
        name = "Rebuilt Ratling",
        cost = "{3}{B}",
        type = "Artifact Creature — Zombie Rat",
        pt = "2/1",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            When this creature enters, each opponent discards a card. For each opponent who can't, you draw a card.
        """,
    ) parsesTo abilities(
        affinityForArtifacts,
        Keyword.Flying,
        whenThisEnters(
            Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Opponent)),
                bind = "opponent",
                together = true,
                steps = listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(aCard)))),
                        payer = Players.Bound("opponent"),
                        mandatory = true,
                        bind = "discarded",
                    ),
                    Step.If(condition = Condition.CouldNot("discarded"), then = listOf(drawCards(1))),
                ),
            ),
        ),
    )

    @Test
    fun festeringVermin() = card(
        name = "Festering Vermin",
        cost = "{1}{B}",
        type = "Creature — Zombie Rat",
        pt = "1/1",
        text = """
            When this creature enters, each player discards a card.
            Unearth {1}{B} ({1}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.Discard(Players.Each(PlayerFilter.Any), count = Value.Fixed(1)))),
        Keyword.Unearth(manaCost("{1}{B}")),
    )

    // ---- Life

    @Test
    fun larderOuphes() = card(
        name = "Larder Ouphes",
        cost = "{1}{G/W}{G/W}",
        type = "Creature — Ouphe",
        pt = "3/2",
        text = """
            When this creature enters, you gain 2 life.
            Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.GainLife(Players.You, Value.Fixed(2)))),
        Keyword.Persist,
    )

    @Test
    fun kettleKitten() = card(
        name = "Kettle Kitten",
        cost = "{B}",
        type = "Creature — Cat",
        pt = "1/1",
        text = """
            When this creature enters, each opponent loses 1 life and you gain 1 life.
            Sacrifice a Food: Return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1))),
            Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Sub(Subtype("Food")))))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield), postBind = "returned")))),
        ),
    )
}
