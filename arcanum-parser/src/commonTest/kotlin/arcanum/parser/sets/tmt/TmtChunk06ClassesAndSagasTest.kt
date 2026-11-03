package arcanum.parser.sets.tmt

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 06: Classes (CR 716: level abilities gained as a sorcery) and Sagas (CR 714): counters as you
 * attack, life when a creature with a counter leaves (CR 603.10a), cards in your graveyard given sneak and cast with it (CR 601.2a),
 * a discard-and-draw loot, each player creating a Food, "one or more of your opponents are attacked" (CR 508.3d), token copies that
 * aren't legendary and are Mutants (CR 707.9b) of cards exiled with the Saga (CR 607.2a), a mill with a reflexive trigger (CR 603.12),
 * and a delayed trigger for creatures attacking alone this turn (CR 603.7c).
 */
class TmtChunk06ClassesAndSagasTest {
    private fun level(cost: String, level: Int, vararg abilities: Ability) = Keyword.ClassLevel(manaCost(cost), level, abilities.toList())
    private fun becomesLevel(level: Int) = Trigger.On(EventPattern.BecomesLevel(ObjectFilter.Is(Objects.Self), level))
    private fun becomesCreature(objects: Objects, subtype: String) = Step.If(Condition.ObjectMatches(objects, not(creature)), then = listOf(doing(Effect.ApplyMechanics(listOf(
        Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype(subtype)))),
        Mechanic.SetPowerToughness(n(0), n(0))), Affected.Objects(objects), Duration.Indefinitely))))
    private fun copyExcept(of: Objects) = create(TokenSpec.CopyOf(of, except = listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary),
        addSubtypes = listOf(SubtypeRef.Literal(Subtype("Mutant")))))))
    private val handSize = count(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val yourGraveyardCreatures = Objects.Each(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))

    // CR 603.10a: whether it had a counter is looked back at
    @Test
    fun commandersKnack() = card(
        name = "Commander's Knack",
        cost = "{1}{W}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever you attack, put a +1/+1 counter on target attacking creature.
            {2}{W}: Level 2
            Whenever a creature you control leaves the battlefield, if it had a counter on it, you gain 2 life.
            {3}{W}: Level 3
            Whenever you cast a spell, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        triggered(youAttack, counters(target("creature")), targets = listOf(slot("creature", and(attacking, creature)))),
        level("{2}{W}", 2, triggered(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield)), gainLife(2),
            interveningIf = Condition.ObjectMatches(leftOne, ObjectFilter.HasCounter(null, atLeast = 1)))),
        level("{3}{W}", 3, triggered(youCast(ObjectFilter.Any), counters(Objects.Each(creatureYouControl)))),
    )

    @Test
    fun gadgeteersCraft() = card(
        name = "Gadgeteer's Craft",
        cost = "{1}{U}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, mill two cards, draw two cards, then discard two cards.
            {1}{U}: Level 2
            When this Class becomes level 2, return up to two target artifact cards from your graveyard to your hand.
            {4}{U}: Level 3
            At the beginning of combat on your turn, put three +1/+1 counters on target artifact you control. If it isn't a creature, it becomes a 0/0 Robot creature in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(2), draw(2), discard(2)),
        level("{1}{U}", 2, triggered(becomesLevel(2), move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(artifact), min = 0, max = 2)))),
        level("{4}{U}", 3, triggered(beginningOfCombatOnYourTurn, counters(target("artifact"), 3), becomesCreature(target("artifact"), "Robot"),
            targets = listOf(slot("artifact", and(artifact, you))))),
    )

    // CR 702.190a: sneak given to cards in the graveyard; CR 601.2a: cast from there only that way
    @Test
    fun apprenticeShinobi() = card(
        name = "Apprentice Shinobi",
        cost = "{2}{B}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever a creature you control leaves the battlefield, each opponent loses 1 life.
            {1}{B}: Level 2
            Creatures you control get +1/+0 and have menace.
            {B}: Level 3
            Creature cards in your graveyard have sneak {3}{B}.
            You may cast creature spells from your graveyard using their sneak abilities.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield)), loseLife(1, Players.Each(PlayerFilter.Opponent))),
        level("{1}{B}", 2, static(pt(1, 0), has(Keyword.Menace), affects = Objects.Each(creatureYouControl))),
        level("{B}", 3,
            static(has(sneak("{3}{B}")), affects = yourGraveyardCreatures),
            static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You, viaAlternative = Keyword.Sneak.Kind), affects = yourGraveyardCreatures)),
    )

    @Test
    fun snarkyAttitude() = card(
        name = "Snarky Attitude",
        cost = "{1}{R}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            Whenever you attack, you may discard a card. If you do, draw a card.
            {1}{R}: Level 2
            Whenever you discard a card, this Class deals 2 damage to each opponent.
            {1}{R}: Level 3
            When this Class becomes level 3, search your library for a card, put it into your hand, shuffle, then discard a card at random.
        """,
    ) parsesTo abilities(
        triggered(youAttack, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
        level("{1}{R}", 2, triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)), damage(2, eachOpponent))),
        level("{1}{R}", 3, triggered(becomesLevel(3), search(ObjectFilter.Any), move(Objects.Bound("found"), Zone.Hand), shuffle,
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, random = true)))),
    )

    // CR 508.3d: "one or more … are attacked" triggers once for the declaration; X is the hand size as it resolves (CR 107.3c)
    @Test
    fun partyAnimalLifestyle() = card(
        name = "Party Animal Lifestyle",
        cost = "{G}",
        type = "Enchantment — Class",
        text = """
            (Gain the next level as a sorcery to add its ability.)
            When this Class enters, each player creates a Food token.
            {1}{G}: Level 2
            Whenever an artifact an opponent controls is put into a graveyard from the battlefield, draw a card.
            {4}{G}: Level 3
            Whenever one or more of your opponents are attacked, up to one target attacking creature gets +X/+X until end of turn, where X is the number of cards in your hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(food, n(1), Players.Each(PlayerFilter.Any)))),
        level("{1}{G}", 2, triggered(dies(and(artifact, opponents)), draw(1))),
        level("{4}{G}", 3, triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Any, target = arcanum.api.RecipientFilter(players = PlayerFilter.Opponent)), oneOrMore = true),
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(handSize, handSize)),
            targets = listOf(slot("creature", and(attacking, creature), min = 0)))),
    )

    // CR 707.9b: exceptions to the copy; CR 607.2a: the cards exiled with this Saga
    @Test
    fun theCloningVats() = card(
        name = "The Cloning Vats",
        cost = "{4}{B}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Exile target creature card from your graveyard. Create a token that's a copy of it, except it isn't legendary and is a Mutant in addition to its other types.
            II, III — Create a token that's a copy of a card exiled with this Saga, except it isn't legendary and is a Mutant in addition to its other types.
        """,
    ) parsesTo abilities(
        chapter(1, move(target("card"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))), copyExcept(Objects.Bound("exiled")),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
        chapters(listOf(2, 3),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile))), bind = "chosen", chooser = Players.You),
            copyExcept(Objects.Bound("chosen"))),
    )

    // CR 603.12: a reflexive trigger after the mill; CR 603.7c: a delayed trigger for the rest of the turn
    @Test
    fun theLoneSurvivorsTale() = card(
        name = "The Lone Survivor's Tale",
        cost = "{4}{B}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Destroy all creatures.
            II — Mill four cards. When you do, return target creature card from your graveyard to your hand.
            III — Whenever a creature you control attacks alone this turn, put three +1/+1 counters on it. It gains trample, lifelink, and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, destroy(Objects.Each(creature))),
        chapter(2, mill(4), reflexive(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature))))),
        chapter(3, doing(Effect.CreateDelayedTrigger(on(EventPattern.Attack(creatureYouControl, alone = true)), arcanum.api.Instructions(steps = listOf(
            counters(Objects.EventObject(arcanum.api.EventRole.Object), 3),
            gains(Objects.EventObject(arcanum.api.EventRole.Object), Keyword.Trample, Keyword.Lifelink, Keyword.Indestructible))),
            duration = Duration.EndOfTurn, repeats = true))),
    )
}
