package arcanum.parser.sets.ltr

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 02: white cards, second part. Historic permanents (CR 700.6) put into a library at a depth
 * (CR 401.4), a reflexive trigger after an optional sacrifice or payment (CR 603.12), the Ring tempting after a spell's other
 * effects (CR 701.54a), tokens being created as a trigger (CR 111.1), a card put into a graveyard from the battlefield this
 * turn (CR 400.7), exiling until this creature leaves (CR 610.3), a flicker under your control, chapter abilities with a
 * duration of controlling the Saga (CR 714.2b, 611.2b), creatures that blocked or were blocked by a legendary creature
 * (CR 509.1), tapping creatures as a cost (CR 118.3), attaching Equipment by a trigger (CR 701.3), prevention during your
 * turn (CR 615.1) and keyword counters (CR 122.1b).
 */
class LtrChunk02WhiteTest {
    // CR 700.6, 401.4: fourth from the top
    @Test
    fun lostToOldSongs() = card(
        name = "Lost to Old Songs",
        cost = "{W}{W}",
        type = "Instant",
        text = "Put target nonland historic permanent into its owner's library fourth from the top. (Artifacts, legendaries, and Sagas are historic.)",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Library, position = 3), targets = listOf(slot("permanent", and(nonland, historic, permanentFilter)))),
    )

    // CR 603.12: the reflexive trigger only when a cost was paid
    @Test
    fun quickfootHalfling() = card(
        name = "Quickfoot Halfling",
        cost = "{1}{W}",
        type = "Creature — Halfling Peasant",
        text = "Whenever this creature attacks, you may sacrifice a Food or pay {2}{W}. When you do, tap target creature an opponent controls.",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(CostPart.OneOf(listOf(cost(sacrificeA(sub("Food"))), manaCost("{2}{W}")))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("creature", creatureOpponentControls)), steps = listOf(tapIt(target("creature"))))))))),
    )

    // CR 701.54a: the Ring tempts you as the spell's last effect
    @Test
    fun rideForWrathRideForRuin() = card(
        name = "Ride for Wrath, Ride for Ruin!",
        cost = "{3}{W}",
        type = "Sorcery",
        text = "Put a +1/+1 counter on each creature you control. They gain vigilance until end of turn. The Ring tempts you.",
    ) parsesTo abilities(
        spell(counters(Objects.Each(creatureYouControl)), gains(Objects.Each(creatureYouControl), Keyword.Vigilance), ringTempts),
    )

    @Test
    fun protectorOfTheWhiteCity() = card(
        name = "Protector of the White City",
        cost = "{3}{W}",
        type = "Creature — Human Soldier",
        text = "When this creature enters, create a 1/1 white Human Soldier creature token.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, create(humanSoldier)),
    )

    @Test
    fun stayedHand() = card(
        name = "Stayed Hand",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Return target spell to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(move(target("spell"), Zone.Hand), draw(1), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 111.1: "whenever you create a token" (once for each creation event's tokens, CR 603.2c)
    @Test
    fun daisyOfNorthLane() = card(
        name = "Daisy of North Lane",
        cost = "{2}{W}",
        type = "Legendary Creature — Halfling Peasant",
        text = """
            When Daisy enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you create a token, put a +1/+1 counter on target creature you control other than Daisy.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, createFood()),
        triggered(on(EventPattern.CreateToken(you)), counters(target("creature")), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 201.5c (set check): a legendary card's shortened name is its name before " of " ("Marigold Thistle")
    @Test
    fun marigoldThistleOfElmHollow() = card(
        name = "Marigold Thistle of Elm Hollow",
        cost = "{2}{W}",
        type = "Legendary Creature — Halfling Peasant",
        text = """
            When Marigold Thistle enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you create a token, put a +1/+1 counter on target creature you control other than Marigold Thistle.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, createFood()),
        triggered(on(EventPattern.CreateToken(you)), counters(target("creature")), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 400.7: the card the permanent became as it went to the graveyard this turn
    @Test
    fun hamfastTheSteadfast() = card(
        name = "Hamfast the Steadfast",
        cost = "{1}{W}",
        type = "Legendary Creature — Halfling Peasant",
        text = """
            Flash
            When Hamfast enters, choose up to one target permanent card in your graveyard that was put there from the battlefield this turn. Return it to your hand. Then the Ring tempts you.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, move(target("card"), Zone.Hand), ringTempts,
            targets = listOf(slot("card", and(permanentCard, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You), diedThisTurn), min = 0))),
    )

    @Test
    fun elevensesFeast() = card(
        name = "Elevenses Feast",
        cost = "{2}{W}",
        type = "Instant",
        text = """Up to two target creatures each get +2/+1 until end of turn. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(pump(target("creatures"), 2, 1), createFood(), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 603.12, 610.3: exiled until this creature leaves
    @Test
    fun hamletBounder() = card(
        name = "Hamlet Bounder",
        cost = "{1}{W}",
        type = "Creature — Halfling Soldier",
        text = """
            Vigilance
            When this creature enters, you may sacrifice a token. When you do, exile target creature an opponent controls until this creature leaves the battlefield.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(token)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("creature", creatureOpponentControls)),
                steps = listOf(doing(Effect.Move(target("creature"), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self))))))))),
    )

    // CR 400.7: exiled and returned as a new object under your control; CR 701.54a
    @Test
    fun slipIntoShadow() = card(
        name = "Slip Into Shadow",
        cost = "{1}{W}",
        type = "Instant",
        text = "Exile target creature you own, then return it to the battlefield under your control. The Ring tempts you.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile, postBind = "exiled"), move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.You), ringTempts,
            targets = listOf(slot("creature", and(creature, ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You))))),
    )

    @Test
    fun soldierOfThePaleHost() = card(
        name = "Soldier of the Pale Host",
        cost = "{3}{W}",
        type = "Creature — Spirit Soldier",
        text = """
            Flash
            Flying
            When this creature enters, target creature gets +2/+0 until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, pump(target("creature"), 2, 0), targets = listOf(slot("creature", creature))),
    )

    // CR 701.54a; "your second card each turn" (CR 121.2)
    @Test
    fun stalwartsOfTheRiverFort() = card(
        name = "Stalwarts of the River Fort",
        cost = "{4}{W}",
        type = "Creature — Human Soldier",
        text = """
            When this creature enters, the Ring tempts you.
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, ringTempts),
        triggered(drawSecondCard, counters(Objects.Self)),
    )

    // CR 714.2b; CR 611.2b: "for as long as you control this Saga"
    @Test
    fun layOfLuthiel() = card(
        name = "Lay of Lúthiel",
        cost = "{3}{W}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Target creature you control gains indestructible for as long as you control this Saga.
            II — Return target creature card from your graveyard to the battlefield.
            III — Up to two target creatures you control each gain lifelink until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, doing(Effect.ApplyMechanics(listOf(has(Keyword.Indestructible)), arcanum.api.Affected.Objects(target("creature")), whileYouControlThis)),
            targets = listOf(slot("creature", creatureYouControl))),
        chapter(2, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
        chapter(3, gains(target("creatures"), Keyword.Lifelink), targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
    )

    @Test
    fun tookGleaner() = card(
        name = "Took Gleaner",
        cost = "{1}{W}",
        type = "Creature — Halfling Peasant",
        text = "When this creature dies, the Ring tempts you.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(diesSelf, ringTempts),
    )

    @Test
    fun warOfTheAncientAlliance() = card(
        name = "War of the Ancient Alliance",
        cost = "{3}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Search your library for a legendary creature card, reveal it, put it into your hand, then shuffle.
            III — Creatures you control gain double strike until end of turn. The Ring tempts you.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2), search(and(legendary, creature), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        chapter(3, gains(Objects.Each(creatureYouControl), Keyword.DoubleStrike), ringTempts),
    )

    @Test
    fun eastfoldRider() = card(
        name = "Eastfold Rider",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        text = "Sacrifice this creature: Destroy target artifact or enchantment. Activate only as a sorcery.",
        pt = "3/1",
    ) parsesTo abilities(
        activated(cost(sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment))), rules = asSorcery),
    )

    // CR 509.1: blocked a legendary creature, or was blocked by one, this turn
    @Test
    fun noneShallCross() = card(
        name = "None Shall Cross!",
        cost = "{W}",
        type = "Instant",
        text = "Destroy target creature that blocked or was blocked by a legendary creature this turn.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", and(creature, or(
            ObjectFilter.ThisTurn(EventPattern.Block(ObjectFilter.Any, and(legendary, creature)), role = EventRole.Object),
            ObjectFilter.ThisTurn(EventPattern.Block(and(legendary, creature), ObjectFilter.Any), role = EventRole.OtherCombatant)))))),
    )

    // CR 118.3: tapping untapped creatures as a cost
    @Test
    fun marmadasMasterOfTheMarish() = card(
        name = "Marmadas, Master of the Marish",
        cost = "{3}{W}",
        type = "Legendary Creature — Halfling Citizen",
        text = """
            Whenever Marmadas or another nontoken creature you control with power 2 or less enters, create a 1/1 white Halfling creature token.
            Tap two other untapped Halflings you control: Marmadas gets +2/+0 and gains lifelink until end of turn.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, not(token), creature, you, power(Comparison.AtMost, 2)))),
            create(creatureToken("Halfling", listOf(arcanum.api.Color.White), 1, 1))),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(and(another, sub("Halfling")), count = n(2)))),
            pump(Objects.Self, 2, 0, has(Keyword.Lifelink))),
    )

    // CR 701.3a: attach by a trigger; CR 615.1: prevention during your turn
    @Test
    fun bingoSteadfastHero() = card(
        name = "Bingo, Steadfast Hero",
        cost = "{1}{W}",
        type = "Legendary Creature — Halfling Warrior",
        text = """
            Whenever Bingo enters or attacks, you may attach target Equipment you control with mana value 2 or 3 to Bingo.
            During your turn, prevent all damage that would be dealt to Bingo.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, Step.May(listOf(doing(Effect.Attach(target("Equipment"), Recipients(objects = Objects.Self)))), Players.You),
            targets = listOf(slot("Equipment", and(equipment, you, or(manaValue(Comparison.Equal, 2), manaValue(Comparison.Equal, 3)))))),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
            condition = Condition.YourTurn),
    )

    // CR 401.4: fifth from the top; CR 400.7: the card in the graveyard
    @Test
    fun olorinPaleRider() = card(
        name = "Olórin, Pale Rider",
        cost = "{3}{W}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Vigilance
            Whenever you cast a spell, creatures you control get +1/+0 until end of turn. Scry 1.
            When Olórin dies, you may put it into its owner's library fifth from the top.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(youCast(ObjectFilter.Any), pump(Objects.Each(creatureYouControl), 1, 0), scry(1)),
        triggered(diesSelf, Step.May(listOf(move(Objects.EventObject(EventRole.After), Zone.Library, position = 4)), Players.You)),
    )

    // CR 122.1b: an indestructible counter
    @Test
    fun eagleOfRescue() = card(
        name = "Eagle of Rescue",
        cost = "{4}{W}{W}",
        type = "Creature — Bird Soldier",
        text = """
            Flying
            When this creature enters, put an indestructible counter on another target creature you control. Draw a card if that creature's power is 2 or less.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, counters(target("creature"), kind = CounterKind.Keyword(Keyword.Indestructible)),
            Step.If(Condition.ObjectMatches(target("creature"), power(Comparison.AtMost, 2)), then = listOf(draw(1))),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    private val historic = or(artifact, legendary, sub("Saga"))
    private val diedThisTurn = ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Battlefield, to = Zone.Graveyard), role = EventRole.After)
    private val whileYouControlThis = Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you))
}
