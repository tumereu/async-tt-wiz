package arcanum.parser.sets.tmt

import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 07: Foods (CR 111.10b, artifacts with their own sacrifice ability) and Equipment (CR 301.5): enters
 * triggers that destroy, deal damage to any target and to you, pump and untap; a five-colour ability with several targets; "If you don't
 * put a card into your hand this way"; "if its mana value was 4 or less" as it last existed (CR 608.2h); granted triggers in quotes (CR
 * 113.2c); attaching to a Ninja as it enters; equip costs that aren't mana, once each turn (CR 602.5b); a copy of the Equipment; attaching
 * as it enters with a reflexive fight (CR 603.12, 701.14).
 */
class TmtChunk07FoodAndEquipmentTest {
    private val equipped: Objects = Objects.AttachedTo(Objects.Self)
    private val foodAbility = activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3))
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))

    @Test
    fun scatterbrainedPigeon() = card(
        name = "Scatterbrained Pigeon",
        cost = "{W}",
        type = "Creature — Bird Mutant",
        text = """
            Flying
            When this creature leaves the battlefield, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
        pt = "0/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelfLeaves, createFood()),
    )

    @Test
    fun sardineAndPineapplePie() = card(
        name = "Sardine & Pineapple Pie",
        cost = "{2}{B}{B}",
        type = "Artifact — Food",
        text = """
            When this artifact enters, destroy target creature.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature", creature))),
        foodAbility,
    )

    // CR 115.4: any target
    @Test
    fun fieryPorridgePie() = card(
        name = "Fiery Porridge Pie",
        cost = "{2}{R}",
        type = "Artifact — Food",
        text = """
            When this artifact enters, it deals 4 damage to any target and 3 damage to you.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(4, toAny), damage(3, Recipients(players = Players.You)), targets = listOf(anyTarget)),
        foodAbility,
    )

    @Test
    fun avocadoAndCocoaPie() = card(
        name = "Avocado & Cocoa Pie",
        cost = "{G}",
        type = "Artifact — Food",
        text = """
            Flash
            When this artifact enters, target creature gets +2/+2 until end of turn. Untap it.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(target("creature"), 2, 2), untapIt(target("creature")), targets = listOf(slot("creature", creature))),
        foodAbility,
    )

    // CR 115.1: a target for each "target" in the ability
    @Test
    fun kitchenSinkPie() = card(
        name = "Kitchen Sink Pie",
        cost = "{2}",
        type = "Artifact — Food",
        text = """
            When this artifact enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {2}{W}{U}{B}{R}{G}, {T}, Sacrifice this artifact: Target player gains 3 life and draws a card. Each of your opponents discards a card. This artifact deals 3 damage to any target. Put three +1/+1 counters on up to one target creature.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(cost(mana("{2}{W}{U}{B}{R}{G}"), tap, sacrificeSelf),
            gainLife(3, Players.Target("player")),
            draw(1, Players.Target("player")),
            discard(1, Players.Each(PlayerFilter.Opponent)),
            damage(3, toAny),
            counters(target("creature"), 3),
            targets = listOf(targetPlayer, anyTarget, slot("creature", creature, min = 0))),
    )

    @Test
    fun everyCheesePie() = card(
        name = "Every-Cheese Pie",
        cost = "{2}",
        type = "Artifact — Food",
        text = """
            When this artifact enters, draw a card.
            {1}, {T}, Sacrifice this artifact: Add one mana of any color.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        activated(cost(mana("{1}"), tap, sacrificeSelf), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        foodAbility,
    )

    // CR 701.23: a search may find nothing; then the token is created
    @Test
    fun snackCourier() = card(
        name = "Snack Courier",
        cost = "{1}{G}",
        type = "Creature — Human Citizen",
        text = "When this creature enters, you may search your library for a Food card, reveal it, put it into your hand, then shuffle. If you don't put a card into your hand this way, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
        pt = "1/2",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.May(listOf(search(sub("Food"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You),
            Step.If(Condition.Not(Condition.NonEmpty("found")), then = listOf(createFood()))),
    )

    // CR 608.2h: the destroyed permanent's mana value as it last existed
    @Test
    fun spoiledSnacks() = card(
        name = "Spoiled Snacks",
        cost = "{1}{B}{G}",
        type = "Instant",
        text = "Destroy target artifact or creature. If its mana value was 4 or less, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
    ) parsesTo abilities(
        spell(destroy(target("permanent")),
            Step.If(Condition.ObjectMatches(target("permanent"), manaValue(Comparison.AtMost, 4)), then = listOf(createFood())),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    @Test
    fun gelatoKitten() = card(
        name = "Gelato Kitten",
        cost = "{1}{B/G}",
        type = "Artifact Creature — Food Cat Mutant",
        text = """
            {2}, Sacrifice another creature or token: Draw a card. Activate only as a sorcery.
            {2}, {T}, Sacrifice this creature: You gain 3 life.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), CostPart.Sacrifice(CostObjects.Choose(and(another, or(creature, token)), count = n(1)))), draw(1), rules = asSorcery),
        foodAbility,
    )

    // CR 113.2c: the equipped creature has the quoted ability; "it" is that creature
    @Test
    fun peerlessKatana() = card(
        name = "Peerless Katana",
        cost = "{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has "Whenever this creature deals combat damage, untap it and you gain 2 life."
            Whenever a Ninja you control enters, you may attach this Equipment to it.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), has(triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(), combat = true)), untapIt(Objects.Self), gainLife(2))),
            affects = equipped),
        triggered(enters(and(ninja, you)), Step.May(listOf(doing(Effect.Attach(Objects.Self, Recipients(objects = enteredOne)))), player = Players.You)),
        equip("{2}"),
    )

    @Test
    fun tailoredBoStaff() = card(
        name = "Tailored Bo Staff",
        cost = "{2}{U}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, return up to one other target nonland permanent to its owner's hand.
            Equipped creature gets +2/+1 and has vigilance.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, nonland, permanentFilter), min = 0))),
        static(pt(2, 1), has(Keyword.Vigilance), affects = equipped),
        equip("{3}"),
    )

    // CR 702.6a: an equip cost that isn't mana; CR 602.5b: once each turn
    @Test
    fun warlordsBladedArmor() = card(
        name = "Warlord's Bladed Armor",
        cost = "{1}{B}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+1.
            When this Equipment enters, attach it to target creature you control.
            Equip—Sacrifice another nonland permanent. Activate only once each turn.
        """,
    ) parsesTo abilities(
        static(pt(2, 1), affects = equipped),
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Equip(cost(CostPart.Sacrifice(CostObjects.Choose(and(another, nonland, permanentFilter), count = n(1)))), perTurn = 1),
    )

    @Test
    fun hardFoughtSai() = card(
        name = "Hard-Fought Sai",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has double strike.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike), affects = equipped),
        equip("{2}"),
    )

    // CR 707.2: a token copy of this Equipment
    @Test
    fun scrapheapArmory() = card(
        name = "Scrapheap Armory",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 for each artifact you control.
            {4}{R}: Create a token that's a copy of this Equipment.
            Equip {R}
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(count(and(artifact, you)), n(0)), affects = equipped),
        activated(manaCost("{4}{R}"), create(TokenSpec.CopyOf(Objects.Self))),
        equip("{R}"),
    )

    // CR 603.12: the fight is a reflexive trigger with its own target
    @Test
    fun neonNunchaku() = card(
        name = "Neon Nunchaku",
        cost = "{2}{G}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control. When you do, equipped creature fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
            Equipped creature gets +1/+1 and has trample.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))),
            reflexive(doing(Effect.Fight(equipped, target("other creature"))), targets = listOf(slot("other creature", creatureOpponentControls, min = 0))),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 1), has(Keyword.Trample), affects = equipped),
        equip("{3}"),
    )

    @Test
    fun sewerSkateboard() = card(
        name = "Sewer Skateboard",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, tap target permanent.
            Equipped creature gets +1/+0 and has haste.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("permanent")), targets = listOf(slot("permanent", permanentFilter))),
        static(pt(1, 0), has(Keyword.Haste), affects = equipped),
        equip("{1}"),
    )
}
