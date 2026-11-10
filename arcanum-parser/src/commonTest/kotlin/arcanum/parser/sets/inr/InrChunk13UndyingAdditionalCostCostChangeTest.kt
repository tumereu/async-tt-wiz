package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 13: Undying (CR 702.93), a mana ability restricted to creature spells (CR 106.6), additional costs (CR 601.2b),
 * "can't be countered" (CR 101.2, 701.6), "can't block" (CR 509.1b) and cost reductions (CR 601.2f).
 */
class InrChunk13UndyingAdditionalCostCostChangeTest {
    private val undyingLine = "(When this creature dies, if it had no +1/+1 counters on it, return it to the battlefield under its owner's control with a +1/+1 counter on it.)"
    private val exileCreatureFromGraveyard = Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(
        CostObjects.Choose(creature, count = n(1)), from = Zone.Graveyard))))

    // CR 702.93a
    @Test
    fun grimGhoul() = card(
        name = "Grim Ghoul", cost = "{1}{B}", type = "Creature — Zombie", pt = "1/1",
        text = "Undying $undyingLine",
    ) parsesTo abilities(Keyword.Undying)

    // CR 702.93a
    @Test
    fun yearlingWolf() = card(
        name = "Yearling Wolf", cost = "{G}", type = "Creature — Wolf", pt = "1/1",
        text = "Undying $undyingLine",
    ) parsesTo abilities(Keyword.Undying)

    // CR 106.6: the three mana of one chosen color can be spent only on creature spells
    @Test
    fun woodlandSeer() = card(
        name = "Woodland Seer", cost = "{2}{G}", type = "Creature — Human Druid", pt = "0/1",
        text = "{T}: Add three mana of any one color. Spend this mana only to cast creature spells.",
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(
            ManaProduction(ManaKind.Choose(n(3)), restriction = and(spellFilter, creature)), players = Players.You))),
    )

    // CR 601.2b, 601.2h: the sacrificed permanent is chosen as the spell is cast
    @Test
    fun cullingRite() = card(
        name = "Culling Rite", cost = "{2}{W}", type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a permanent.
            Exile target artifact, creature, or enchantment.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(sacrificeA(permanentFilter))))),
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(artifact, creature, enchantment)))),
    )

    // CR 601.2b, 118.8: exiling a card from the graveyard is a cost; CR 602.2: an ability that works from the graveyard
    @Test
    fun hollowLancer() = card(
        name = "Hollow Lancer", cost = "{U}", type = "Creature — Zombie Horse", pt = "3/3",
        text = """
            As an additional cost to cast this spell, exile a creature card from your graveyard.
            {3}{U}, Exile this card from your graveyard: Draw a card.
        """,
    ) parsesTo abilities(
        static(exileCreatureFromGraveyard),
        activated(cost(mana("{3}{U}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), draw(1)),
    )

    // CR 601.2b, 601.2f: a sacrifice or a mana payment, chosen as the spell is cast
    @Test
    fun cullTheWeak() = card(
        name = "Cull the Weak", cost = "{B}", type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or pay {3}{B}.
            Exile target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(Cost(listOf(sacrificeA(creature))), manaCost("{3}{B}"))))))),
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 601.2b, 601.2f: a discard or a mana payment
    @Test
    fun forkedBolt() = card(
        name = "Forked Bolt", cost = "{R}", type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card or pay {5}.
            Forked Bolt deals 5 damage to target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(Cost(listOf(discardACard)), manaCost("{5}"))))))),
        spell(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 601.2b, 118.8
    @Test
    fun scrapheapMauler() = card(
        name = "Scrapheap Mauler", cost = "{3}{U}", type = "Creature — Zombie Horror", pt = "4/5",
        text = "As an additional cost to cast this spell, exile a creature card from your graveyard.",
    ) parsesTo abilities(static(exileCreatureFromGraveyard))

    // CR 601.2b, 701.21a
    @Test
    fun graveBargain() = card(
        name = "Grave Bargain", cost = "{B}", type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(sacrificeA(creature))))),
        spell(draw(2)),
    )

    // CR 101.2, 701.6, 702.8a (flash), 603.2 (cast trigger), 700.2 (modes, "up to one")
    @Test
    fun deepwaterTerror() = card(
        name = "Deepwater Terror", cost = "{5}{U}{U}", type = "Creature — Kraken Horror", pt = "7/8",
        text = """
            Flash
            This spell can't be countered.
            Whenever you cast a spell, choose up to one —
            • Return target spell you don't control to its owner's hand.
            • Return target nonland permanent to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.CantBeCountered),
        Ability.Triggered(youCast(ObjectFilter.Any), Instructions(modes = modes(
            mode(move(target("spell"), Zone.Hand), targets = listOf(slot("spell", and(spellFilter, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
            mode(move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
            min = 0,
        ))),
    )

    // CR 509.1b, 112.6 (a card that works from the graveyard), 601.2a
    @Test
    fun gravewalker() = card(
        name = "Gravewalker", cost = "{B}", type = "Creature — Zombie", pt = "2/1",
        text = """
            This creature can't block.
            You may cast this card from your graveyard as long as you control a Zombie.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBlock(attackers = ObjectFilter.Any)),
        static(Mechanic.MayPlay(Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You),
            condition = Condition.Exists(and(sub("Zombie"), you), atLeast = 1)),
    )

    // CR 601.2f: a reduction for each matching card in your graveyard; prowess CR 702.108a; "discard your hand, then draw" CR 701.9
    @Test
    fun frenzyReveler() = card(
        name = "Frenzy Reveler", cost = "{6}{R}{R}", type = "Creature — Devil Horror", pt = "3/4",
        text = """
            This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, discard your hand, then draw three cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(or(instant, sorcery)))))),
        Keyword.Prowess,
        triggered(entersSelf,
            Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            draw(3),
        ),
    )

    // CR 601.2f
    @Test
    fun gravetreeColossus() = card(
        name = "Gravetree Colossus", cost = "{7}{G}", type = "Creature — Zombie Treefolk", pt = "10/10",
        text = "This spell costs {1} less to cast for each creature card in your graveyard.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(creature))))),
    )

    // CR 601.2f: creature spells you cast; CR 613.4c (-1/-1 to creatures you control)
    @Test
    fun hollowSummoning() = card(
        name = "Hollow Summoning", cost = "{1}{B}", type = "Enchantment",
        text = """
            Creature spells you cast cost {2} less to cast.
            Creatures you control get -1/-1.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(creature, spellFilter, you), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
        static(pt(-1, -1), affects = Objects.Each(and(creature, you))),
    )

    // CR 601.2f
    @Test
    fun routTheHorde() = card(
        name = "Rout the Horde", cost = "{6}{W}{W}", type = "Sorcery",
        text = """
            This spell costs {1} less to cast for each creature on the battlefield.
            Destroy all creatures.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(creature)))),
        spell(destroy(Objects.Each(creature))),
    )
}
