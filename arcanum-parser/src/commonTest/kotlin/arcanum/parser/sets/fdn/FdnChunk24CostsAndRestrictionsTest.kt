package arcanum.parser.sets.fdn

import arcanum.api.Step
import arcanum.api.ObjectCategory
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Ability
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** The FDN cards with additional costs, blocking restrictions, "can't gain life", flash permissions and cost changes (chunk 24). The invented names carry the oracle text of the real cards. */
class FdnChunk24CostsAndRestrictionsTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val less1 = CostChange.Less(ManaCost.parse("{1}")!!, n(1))
    private val less3 = CostChange.Less(ManaCost.parse("{3}")!!, n(1))
    private val selfCostChange: (CostChange) -> Mechanic = { Mechanic.CostChange(ObjectFilter.Is(arcanum.api.Objects.Self), it) }
    private val spellsYouCast = and(spellFilter, you)

    // CR 601.2b, 601.2f, 117.1: a sacrifice or a mana payment, chosen as the spell is cast
    @Test
    fun eatenAlive() = card(
        name = "Eaten Dead",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or pay {3}{B}.
            Exile target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(Cost(listOf(sacrificeA(creature))), manaCost("{3}{B}"))))))),
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 601.2b, 601.2f: discarding is a cost, paid before the spell resolves
    @Test
    fun thrillOfPossibility() = card(
        name = "Thrill of Chance",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2)),
    )

    // CR 509.1b: the restriction applies to the blockers
    @Test
    fun stromkirkNoble() = card(
        name = "Stromkirk Lord",
        cost = "{R}",
        type = "Creature — Vampire Noble",
        pt = "1/1",
        text = """
            This creature can't be blocked by Humans.
            Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(sub("Human"))),
        triggered(combatDamageToAPlayer, counters(arcanum.api.Objects.Self, n(1))),
    )

    // CR 509.1a: a creature that can't block is not declared as a blocker
    @Test
    fun vampireInterloper() = card(
        name = "Vampire Intruder",
        cost = "{1}{B}",
        type = "Creature — Vampire Scout",
        pt = "2/1",
        text = """
            Flying
            This creature can't block.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantBlock(ObjectFilter.Any)),
    )

    // CR 509.1a, 603.6a
    @Test
    fun vampireSoulcaller() = card(
        name = "Vampire Spiritcaller",
        cost = "{4}{B}",
        type = "Creature — Vampire Warlock",
        pt = "3/2",
        text = """
            Flying
            This creature can't block.
            When this creature enters, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        whenThisEnters(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 119.7: the life gain is not replaced, it just doesn't happen, for every player
    @Test
    fun giantCindermaw() = card(
        name = "Giant Cinderjaw",
        cost = "{2}{R}",
        type = "Creature — Dinosaur Beast",
        pt = "4/3",
        text = """
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
            Players can't gain life.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(listOf(Mechanic.CantGainLife), affects = Affected.Players(Players.Each(PlayerFilter.Any))),
    )

    // CR 702.8a, 601.3: the permission covers every spell of the player
    @Test
    fun highFaeTrickster() = card(
        name = "High Fae Swindler",
        cost = "{3}{U}",
        type = "Creature — Faerie Wizard",
        pt = "4/2",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Flying
            You may cast spells as though they had flash.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.CastAsThoughFlash(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell))), affects = Affected.Players(Players.You)),
    )

    // CR 601.2f, 118.7: a reduction that applies if the condition holds as the cost is determined
    @Test
    fun arcaneEpiphany() = card(
        name = "Arcane Revelation",
        cost = "{3}{U}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a Wizard.
            Draw three cards.
        """,
    ) parsesTo abilities(
        static(selfCostChange(less1), condition = Condition.Exists(and(sub("Wizard"), you), atLeast = 1)),
        spell(draw(3)),
    )

    // CR 601.2f: affects the spells the controller casts, instants and sorceries
    @Test
    fun archmageOfRunes() = card(
        name = "Archmage of Glyphs",
        cost = "{3}{U}{U}",
        type = "Creature — Giant Wizard",
        pt = "3/6",
        text = """
            Instant and sorcery spells you cast cost {1} less to cast.
            Whenever you cast an instant or sorcery spell, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), less1)),
        triggered(youCast(or(instant, sorcery)), draw(1)),
    )

    // CR 601.2f: a condition on the power of a creature
    @Test
    fun boltBend() = card(
        name = "Bolt Twist",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if you control a creature with power 4 or greater.
            Change the target of target spell or ability with a single target.
        """,
    ) parsesTo abilities(
        static(selfCostChange(less3), condition = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), atLeast = 1)),
        spell(
            Step.Do(Effect.ChangeTargets(target("spell"), chooser = Players.You, optional = false)),
            targets = listOf(slot("spell", and(or(spellFilter, ObjectFilter.Category(ObjectCategory.StackAbility)), ObjectFilter.SingleTarget()))),
        ),
    )

    // CR 601.2f: only spells of one subtype
    @Test
    fun dragonlordsServant() = card(
        name = "Dragonking's Servant",
        cost = "{1}{R}",
        type = "Creature — Goblin Shaman",
        pt = "1/3",
        text = "Dragon spells you cast cost {1} less to cast.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(sub("Dragon"), spellFilter, you), less1)),
    )

    // CR 601.2f, 107.3: X is worked out as the cost is determined
    @Test
    fun ghaltaPrimalHunger() = card(
        name = "Ghalta, Primal Hungerer",
        cost = "{10}{G}{G}",
        type = "Legendary Creature — Elder Dinosaur",
        pt = "12/12",
        text = """
            This spell costs {X} less to cast, where X is the total power of creatures you control.
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
        """,
    ) parsesTo abilities(
        static(selfCostChange(CostChange.Less(ManaCost.parse("{1}")!!, Value.Of(NumericProperty.Power, arcanum.api.Objects.Each(and(creature, you)), Combine.Total)))),
        Keyword.Trample,
    )

    // CR 601.2f, 601.2c: the targets are chosen before the cost is determined, so the condition reads the chosen target
    @Test
    fun luminousRebuke() = card(
        name = "Luminous Reproach",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped creature.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(selfCostChange(less3), condition = Condition.ObjectMatches(target("creature"), tapped)),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 601.2f
    @Test
    fun mockingSprite() = card(
        name = "Mimicking Sprite",
        cost = "{2}{U}",
        type = "Creature — Faerie Rogue",
        pt = "2/1",
        text = """
            Flying
            Instant and sorcery spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), less1)),
    )

    // CR 601.2f, 119.1: the amount of life gained this turn, summed over events
    @Test
    fun sanguineIndulgence() = card(
        name = "Sanguine Excess",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            This spell costs {3} less to cast if you've gained 3 or more life this turn.
            Return up to two target creature cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        static(selfCostChange(less3), condition = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 3, measure = Measure.Amount)),
        spell(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))),
    )
}
