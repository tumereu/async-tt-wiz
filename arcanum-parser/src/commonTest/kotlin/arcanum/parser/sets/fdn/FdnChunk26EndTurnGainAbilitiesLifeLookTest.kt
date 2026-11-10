package arcanum.parser.sets.fdn

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** The FDN end-the-turn, ability-granting, life-gain and look effects (chunk 26). The invented names carry the oracle text of the real cards. */
class FdnChunk26EndTurnGainAbilitiesLifeLookTest {
    private val vampire = sub("Vampire")

    // CR 723.1: "End the turn." is one effect
    @Test
    fun hourHalt() = card(name = "Hour Halt", cost = "{4}{U}{U}", type = "Instant", text = "End the turn.") parsesTo abilities(
        spell(doing(Effect.EndTurn)),
    )

    // CR 613.1f, 702.19: a static ability grants trample to every other creature you control
    @Test
    fun mightyElephant() = card(
        name = "Mighty Elephant", cost = "{3}{G}{G}{G}", type = "Creature — Elephant", pt = "8/8", text = """
            Trample
            Other creatures you control have trample.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(has(Keyword.Trample), affects = Objects.Each(and(another, creature, you))),
    )

    // CR 613.1f, 702.2, 702.15: the group is the attacking Vampires you control; the second ability is an optional payment (CR 119.4, 603.12)
    @Test
    fun bloodlineTroublemakers() = card(
        name = "Bloodline Troublemakers", cost = "{5}{B}", type = "Creature — Vampire", pt = "5/5", text = """
            Attacking Vampires you control have deathtouch and lifelink.
            Whenever a Vampire you control dies, you may pay 2 life. If you do, draw a card.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Deathtouch, Keyword.Lifelink), affects = Objects.Each(and(attacking, vampire, you))),
        triggered(
            dies(and(vampire, you)),
            Step.Pay(cost(CostPart.PayLife(n(2))), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(draw(1))),
        ),
    )

    // CR 611.3a, 505.1: "during your turn" is a condition of the static ability; second group has +1/+1 counters
    @Test
    fun zealousPaladin() = card(
        name = "Zealous Paladin", cost = "{2}{W}", type = "Creature — Human Knight", pt = "3/3", text = """
            During your turn, this creature has first strike.
            During your turn, creatures you control with +1/+1 counters on them have first strike.
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), condition = Condition.YourTurn),
        static(
            has(Keyword.FirstStrike), condition = Condition.YourTurn,
            affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, 1))),
        ),
    )

    // CR 611.3a: "as long as you control a Dragon"
    @Test
    fun drakeRider() = card(
        name = "Drake Rider", cost = "{1}{R}", type = "Creature — Human Warrior", pt = "2/2",
        text = "As long as you control a Dragon, this creature has flying.",
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.Exists(and(sub("Dragon"), you), 1)),
    )

    // CR 611.3a, 508.1k: flying only while this creature is attacking
    @Test
    fun skysailCorsair() = card(
        name = "Skysail Corsair", cost = "{1}{U}", type = "Creature — Human Pirate", pt = "2/1",
        text = "This creature has flying as long as it's attacking.",
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.ObjectMatches(Objects.Self, attacking)),
    )

    // CR 508.1m, 603.2c: one trigger however many attack; 119.1 gains one life for each attacking creature
    @Test
    fun elderDragon() = card(
        name = "Elder Dragon", cost = "{4}{W}{W}", type = "Creature — Dragon", pt = "5/6", text = """
            Flying
            Whenever one or more creatures you control attack, you gain 1 life for each attacking creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Attack(and(creature, you)), oneOrMore = true), gainLifeAmount(count(and(attacking, creature)))),
    )

    // CR 119.1: two life for each Gate
    @Test
    fun gatekeeperAngel() = card(
        name = "Gatekeeper Angel", cost = "{5}{W}", type = "Creature — Angel", pt = "3/4", text = """
            Flying
            When this creature enters, you gain 2 life for each Gate you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(gainLifeAmount(Value.Times(count(and(sub("Gate"), you)), 2))),
    )

    // CR 701.21a, 118.12: the sacrificed creature's toughness is used; 118.3: the second ability needs 10 more life than the starting life total (CR 103.4)
    @Test
    fun korPilgrim() = card(
        name = "Kor Pilgrim", cost = "{W}{B}", type = "Legendary Creature — Kor Cleric", pt = "2/3", text = """
            Deathtouch
            {1}, Sacrifice another creature: You gain life equal to the sacrificed creature's toughness.
            {1}{W}{B}, Sacrifice another creature: Exile target nonland permanent. Activate only if you have at least 10 life more than your starting life total.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        activated(
            cost(mana("{1}"), sacrificeA(and(another, creature), preBind = "sacrificed")),
            gainLifeAmount(toughness(Objects.Bound("sacrificed"))),
        ),
        activated(
            cost(mana("{1}{W}{B}"), sacrificeA(and(another, creature))),
            move(target("permanent"), Zone.Exile),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
            rules = anyTime.copy(
                condition = Condition.Compare(Value.LifeOf(Players.You), Comparison.AtLeast, Value.Sum(listOf(Value.StartingLife(Players.You), n(10)))),
            ),
        ),
    )

    // CR 119.1, 121.1, 608.2c: gain, then draw in order
    @Test
    fun cloudmender() = card(
        name = "Cloudmender", cost = "{3}{W}{U}", type = "Creature — Human Scout", pt = "2/2", text = """
            Flying
            When this creature enters, you gain 2 life and draw two cards.
        """,
    ) parsesTo abilities(Keyword.Flying, whenThisEnters(gainLife(2), draw(2)))

    @Test
    fun inspiringWarden() = card(
        name = "Inspiring Warden", cost = "{2}{W}", type = "Creature — Angel Cleric", pt = "2/1", text = """
            Flying
            When this creature enters, you gain 1 life and draw a card.
        """,
    ) parsesTo abilities(Keyword.Flying, whenThisEnters(gainLife(1), draw(1)))

    // CR 508.1m, 105.1: a white creature you control attacks
    @Test
    fun lindenTheBrightQueen() = card(
        name = "Linden, the Bright Queen", cost = "{W}{W}{W}", type = "Legendary Creature — Human Noble", pt = "3/3", text = """
            Vigilance
            Whenever a white creature you control attacks, you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Attack(and(ObjectFilter.OfColor(Color.White), creature, you))), gainLife(1)),
    )

    // CR 118.1, 701.21a: sacrifice any creature as a cost; gain then draw
    @Test
    fun vampiricRite() = card(
        name = "Vampiric Rite", cost = "{B}", type = "Enchantment", text = "{1}{B}, Sacrifice a creature: You gain 1 life and draw a card.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{B}"), sacrificeA(creature)), gainLife(1), draw(1)),
    )

    // CR 701.20a/701.20e look and reveal, 701.24 (put on the bottom in a random order, CR 401.4)
    @Test
    fun squadMarshal() = card(
        name = "Squad Marshal", cost = "{3}{W}", type = "Creature — Human Scout", pt = "3/4", text = """
            {2}{W}: Look at the top four cards of your library. You may reveal a creature card with power 2 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}{W}")), *lookFour(and(creature, power(Comparison.AtMost, 2)))),
    )

    // CR 606.4, 114 (emblem 114.1), 701.20a: a loyalty look, a destroy with an Or filter, an emblem with a quoted static ability
    @Test
    fun vivienReed() = card(
        name = "Vivien Reed", cost = "{3}{G}{G}", type = "Legendary Planeswalker — Vivien", loyalty = "5", text = """
            [+1]: Look at the top four cards of your library. You may reveal a creature or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            [−3]: Destroy target artifact, enchantment, or creature with flying.
            [−8]: You get an emblem with "Creatures you control get +2/+2 and have vigilance, trample, and indestructible."
        """,
    ) parsesTo abilities(
        loyalty(1, *lookFour(or(creature, land))),
        loyalty(-3, destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying.kind)))))),
        loyalty(
            -8,
            doing(Effect.CreateEmblem(
                listOf(static(pt(2, 2), has(Keyword.Vigilance, Keyword.Trample, Keyword.Indestructible), affects = Objects.Each(and(creature, you)))),
                Players.You,
            )),
        ),
    )

    private fun gainLifeAmount(amount: Value): Step = Step.Do(Effect.GainLife(Players.You, amount))

    /** "Look at the top four cards of your library. You may reveal [filter] from among them and put it into your hand. Put the rest on the bottom ... random order." */
    private fun lookFour(filter: ObjectFilter): Array<Step> = arrayOf(
        Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(filter, min = n(0), max = n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
        Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
        move(Objects.Bound("chosen"), Zone.Hand),
        Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
    )
}
