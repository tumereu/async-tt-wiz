package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 14: cascade (CR 702.85), flashback (CR 702.34), proliferate (CR 701.34), storm (CR 702.40), threshold and
 * ferocious (ability words, CR 207.2c), vanishing (CR 702.63) and mana lands and creatures with extra abilities (CR 605).
 */
class Mh3Chunk14CascadeFlashbackProliferateTest {
    private val proliferate: Step = Step.Do(Effect.Action(KeywordAction.Proliferate))
    private val thresholdCondition: Condition = Condition.Exists(ObjectFilter.And(yourGraveyard), atLeast = 7)
    private fun modal(vararg options: Mode): Ability.Triggered =
        Ability.Triggered(entersSelf, Instructions(modes = modes(*options)))
    private fun makeMana(symbols: String): Step =
        Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(symbols)!!)), Players.You))
    private val tapForColorless: Ability = activated(cost(tap), makeMana("{C}"))
    private fun pingYou(amount: Int): Step = Step.Do(Effect.DealDamage(Recipients(players = Players.You), n(amount), Objects.Self))

    // CR 702.85a
    @Test
    fun annoyedAltisaur() = card(
        name = "Gorging Altisaur",
        cost = "{5}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "6/5",
        text = """
            Reach, trample
            Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        Keyword.Cascade,
    )

    // CR 301.5 (Equipment), CR 702.85a
    @Test
    fun meteoricMace() = card(
        name = "Meteoric Maul",
        cost = "{4}{R}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +4/+0 and has trample.
            Equip {4}
            Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)
        """,
    ) parsesTo abilities(
        static(pt(4, 0), has(Keyword.Trample), affects = equipped),
        equip("{4}"),
        Keyword.Cascade,
    )

    // CR 702.34a: the flashback cost is an alternative cost with an extra payment
    @Test
    fun deepAnalysis() = card(
        name = "Deep Reflection",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Target player draws two cards.
            Flashback—{1}{U}, Pay 3 life. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(draw(2, Players.Target("player")), targets = listOf(targetPlayer)),
        Keyword.Flashback(cost(mana("{1}{U}"), CostPart.PayLife(n(3)))),
    )

    // CR 601.2b, CR 702.34a: flashback keeps additional costs
    @Test
    fun eviscerator() = card(
        name = "Gruesome Insight",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Draw two cards.
            Flashback {4}{B} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature))))),
        spell(draw(2)),
        Keyword.Flashback(manaCost("{4}{B}")),
    )

    // CR 702.34a, CR 707.10
    @Test
    fun sevinnesReclamation() = card(
        name = "Sevinne's Reclaiming",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Return target permanent card with mana value 3 or less from your graveyard to the battlefield. If this spell was cast from a graveyard, you may copy this spell and may choose a new target for the copy.
            Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield),
            ifThen(Condition.CastFrom(Zone.Graveyard, of = Objects.Self),
                Step.May(listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = Players.You, newTargets = true))), player = Players.You)),
            targets = listOf(slot("card", and(permanentCard, manaValue(Comparison.AtMost, 3), *yourGraveyard.toTypedArray()))),
        ),
        Keyword.Flashback(manaCost("{4}{W}")),
    )

    // CR 702.63a, CR 603.2 (each player's own second spell)
    @Test
    fun dreamtideWhale() = card(
        name = "Dreamtide Orca",
        cost = "{2}{U}",
        type = "Creature — Whale",
        pt = "7/5",
        text = """
            Vanishing 2 (This creature enters with two time counters on it. At the beginning of your upkeep, remove a time counter from it. When the last is removed, sacrifice it.)
            Whenever a player casts their second spell each turn, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Vanishing(2),
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), nthThisTurn = 2),
            Instructions(steps = listOf(proliferate)),
        ),
    )

    // CR 701.34a
    @Test
    fun metastaticEvangel() = card(
        name = "Metastatic Herald",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Human Cleric",
        pt = "3/1",
        text = "Whenever another nontoken creature you control enters, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        triggered(enters(and(another, not(token), creature, you)), proliferate),
    )

    // CR 700.2, CR 122.1c, CR 701.34a
    @Test
    fun proudPackRhino() = card(
        name = "Proud Pack-Hippo",
        cost = "{2}{W}",
        type = "Creature — Rhino",
        pt = "3/3",
        text = """
            When this creature enters, choose one —
            • Put a shield counter on target permanent. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            • Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        modal(
            mode(counters(target("permanent"), 1, CounterKind.Shield), targets = listOf(slot("permanent", permanentFilter))),
            mode(proliferate),
        ),
    )

    // CR 702.40a, CR 118.12
    @Test
    fun flusterstorm() = card(
        name = "Flusterbreeze",
        cost = "{U}",
        type = "Instant",
        text = """
            Counter target instant or sorcery spell unless its controller pays {1}.
            Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost.mana(ManaCost.parse("{1}")!!), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter))),
        ),
        Keyword.Storm,
    )

    // CR 605.1a, CR 207.2c (threshold: seven or more cards in your graveyard)
    @Test
    fun barbarianRing() = card(
        name = "Barbarian Fen",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {R}. This land deals 1 damage to you.
            Threshold — {R}, {T}, Sacrifice this land: It deals 2 damage to any target. Activate only if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        activated(cost(tap), makeMana("{R}"), pingYou(1)),
        Ability.Activated(cost(mana("{R}"), tap, sacrificeSelf), Instructions(targets = listOf(anyTarget), steps = listOf(damage(2, toAny))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = thresholdCondition), abilityWord = "Threshold"),
    )

    @Test
    fun cephalidColiseum() = card(
        name = "Cephalid Arena",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {U}. This land deals 1 damage to you.
            Threshold — {U}, {T}, Sacrifice this land: Target player draws three cards, then discards three cards. Activate only if there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        activated(cost(tap), makeMana("{U}"), pingYou(1)),
        Ability.Activated(cost(mana("{U}"), tap, sacrificeSelf),
            Instructions(targets = listOf(targetPlayer), steps = listOf(draw(3, Players.Target("player")), discard(3, Players.Target("player")))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = thresholdCondition), abilityWord = "Threshold"),
    )

    // CR 207.2c, CR 611.3a: a static ability with a condition granting an ability
    @Test
    fun fledglingDragon() = card(
        name = "Fledgling Wyrm",
        cost = "{2}{R}{R}",
        type = "Creature — Dragon",
        pt = "2/2",
        text = """
            Flying
            Threshold — As long as there are seven or more cards in your graveyard, this creature gets +3/+3 and has "{R}: This creature gets +1/+0 until end of turn."
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(
            listOf(pt(3, 3), has(activated(cost(mana("{R}")), pump(Objects.Self, 1, 0)))),
            affects = Affected.Objects(Objects.Self), condition = thresholdCondition, abilityWord = "Threshold"),
    )

    // CR 605.1a
    @Test
    fun desertedTemple() = card(
        name = "Deserted Shrine",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Untap target land.
        """,
    ) parsesTo abilities(
        tapForColorless,
        activated(cost(mana("{1}"), tap), untapIt(target("land")), targets = listOf(slot("land", land))),
    )

    // CR 207.2c (ferocious), CR 602.5b (activate only if)
    @Test
    fun fanaticOfRhonas() = card(
        name = "Fanatic of Ammit",
        cost = "{1}{G}",
        type = "Creature — Snake Druid",
        pt = "1/4",
        text = """
            {T}: Add {G}.
            Ferocious — {T}: Add {G}{G}{G}{G}. Activate only if you control a creature with power 4 or greater.
            Eternalize {2}{G}{G} ({2}{G}{G}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Snake Druid with no mana cost. Eternalize only as a sorcery.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), makeMana("{G}")),
        Ability.Activated(cost(tap), Instructions(steps = listOf(makeMana("{G}{G}{G}{G}"))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You,
                condition = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1)),
            abilityWord = "Ferocious"),
        Keyword.Eternalize(manaCost("{2}{G}{G}")),
    )

    // CR 605.1a: two mana abilities on one permanent
    @Test
    fun nightshadeDryad() = card(
        name = "Nightshade Nymph",
        cost = "{1}{G}",
        type = "Creature — Dryad",
        pt = "1/2",
        text = """
            Deathtouch
            {T}: Add {C}.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        tapForColorless,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
    )

    // CR 605.1a, CR 701.21a (sacrifice as a cost)
    @Test
    fun phyrexianTower() = card(
        name = "Phyrexian Spire",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {C}.
            {T}, Sacrifice a creature: Add {B}{B}.
        """,
    ) parsesTo abilities(
        tapForColorless,
        activated(cost(tap, sacrificeA(creature)), makeMana("{B}{B}")),
    )

    // CR 701.23a (search), CR 701.21a
    @Test
    fun urzasCave() = card(
        name = "Urza's Grotto",
        cost = null,
        type = "Land — Urza's Cave",
        text = """
            {T}: Add {C}.
            {3}, {T}, Sacrifice this land: Search your library for a land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        tapForColorless,
        activated(cost(mana("{3}"), tap, sacrificeSelf),
            search(land),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle),
    )
}
