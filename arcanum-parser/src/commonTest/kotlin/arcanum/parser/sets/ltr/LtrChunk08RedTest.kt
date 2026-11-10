package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 08: red cards, second part. Sacrificing any number of lands and putting revealed cards onto the
 * battlefield (CR 701.21, 701.20), playing an exiled card this turn (CR 601.2), can't block unless you control a Goblin or Orc
 * (CR 509.1c), a creature that loses indestructible and is exiled if it would die this turn (CR 614.1a), damage to each creature
 * as it enters, one Treasure for the batch of an opponent's creatures dying (CR 603.2c), a legendary Dragon token with an ability of
 * its own (CR 111.4), amass X, Equipment attached to the damaged creature destroyed, a reflexive trigger on playing a card this way
 * (CR 603.12) and affinity for Humans (CR 702.41).
 */
class LtrChunk08RedTest {
    // CR 701.21, 701.20: the revealed artifact and land cards chosen go onto the battlefield, the rest to the bottom
    @Test
    fun fellTheOldForest() = card(
        name = "Fell the Old Forest",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = "Sacrifice any number of lands. Reveal the top X cards of your library, where X is the number of lands sacrificed this way. Choose any number of artifact and/or land cards revealed this way. Put all nonland cards chosen this way onto the battlefield, then put all land cards chosen this way onto the battlefield tapped, then put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(land, you), n(0), null), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Count(Objects.Bound("sacrificed")), fromTop = true), bind = "revealed")),
            Step.Choose(Choice.Objects(or(artifact, land), n(0), null, from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            move(Objects.Where(Objects.Bound("chosen"), nonland), Zone.Battlefield),
            move(Objects.Where(Objects.Bound("chosen"), land), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 601.2b: an artifact or creature sacrificed as an additional cost
    @Test
    fun makeshiftClub() = card(
        name = "Makeshift Club",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Makeshift Club deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature))))),
        spell(damage(4, toAny), targets = listOf(anyTarget)),
    )

    // CR 601.2: an exiled card played this turn
    @Test
    fun deepMineMarauder() = card(
        name = "Deep Mine Marauder",
        cost = "{R}{R}",
        type = "Creature — Goblin Warrior",
        text = """
            Double strike
            Whenever a Goblin or Orc you control deals combat damage to a player, exile the top card of your library. You may play that card this turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        triggered(on(EventPattern.Damage(and(goblinOrOrc, you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
    )

    // CR 702.29e: mountaincycling
    @Test
    fun southernMumak() = card(
        name = "Southern Mumak",
        cost = "{5}{R}",
        type = "Creature — Elephant",
        text = """
            Trample
            Whenever this creature attacks, another target creature you control gets +2/+0 and gains trample until end of turn.
            Mountaincycling {1} ({1}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "6/4",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(attacksSelf, pump(target("creature"), 2, 0, has(Keyword.Trample)), targets = listOf(slot("creature", and(another, creature, you)))),
        Keyword.Typecycling(sub("Mountain"), "Mountaincycling", manaCost("{1}")),
    )

    // CR 509.1c: can't block unless you control a Goblin or Orc
    @Test
    fun hillTrollCrusher() = card(
        name = "Hill-troll Crusher",
        cost = "{3}{R}",
        type = "Creature — Troll Soldier",
        text = """
            Trample
            This creature can't block unless you control a Goblin or Orc.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.CantBlock(attackers = ObjectFilter.Any), condition = Condition.Not(Condition.Exists(and(goblinOrOrc, you), atLeast = 1))),
    )

    @Test
    fun feudsEnd() = card(
        name = "Feud's End",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards and create a 1/1 white Human Soldier creature token.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2), create(humanSoldier)),
    )

    @Test
    fun rallyAtTheDeepKeep() = card(
        name = "Rally at the Deep Keep",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Create two 1/1 white Human Soldier creature tokens. Humans you control gain haste until end of turn.",
    ) parsesTo abilities(
        spell(create(humanSoldier, n(2)), gains(Objects.Each(and(sub("Human"), you)), Keyword.Haste)),
    )

    @Test
    fun wanderersFirebrand() = card(
        name = "Wanderer's Firebrand",
        cost = "{R}",
        type = "Sorcery",
        text = "Wanderer's Firebrand deals 2 damage to any target. The Ring tempts you.",
    ) parsesTo abilities(
        spell(damage(2, toAny), ringTempts, targets = listOf(anyTarget)),
    )

    @Test
    fun relentlessHorselords() = card(
        name = "Relentless Horselords",
        cost = "{3}{R}",
        type = "Creature — Human Knight",
        text = "When this creature enters, the Ring tempts you.",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, ringTempts),
    )

    @Test
    fun risingOfTheDawn() = card(
        name = "Rising of the Dawn",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            Creatures you control have haste.
            Legendary creatures you control get +1/+0.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(creatureYouControl)),
        static(pt(1, 0), affects = Objects.Each(and(legendary, creature, you))),
    )

    @Test
    fun horselordLancer() = card(
        name = "Horselord Lancer",
        cost = "{R}",
        type = "Creature — Human Knight",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature dies, the Ring tempts you.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(diesSelf, ringTempts),
    )

    @Test
    fun stormTheHall() = card(
        name = "Storm the Hall",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +1/+0 and gains first strike until end of turn. If it's a Goblin or Orc, it also gains haste until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 0, has(Keyword.FirstStrike)),
            Step.If(Condition.ObjectMatches(target("creature"), goblinOrOrc), then = listOf(gains(target("creature"), Keyword.Haste))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 614.1a: if it would die this turn, exile it instead; it loses indestructible
    @Test
    fun smiteTheUndying() = card(
        name = "Smite the Undying",
        cost = "{1}{R}",
        type = "Instant",
        text = "Smite the Undying deals 3 damage to target creature. That creature loses indestructible until end of turn. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(damage(3, toObjects(target("creature"))),
            untilEndOfTurn(target("creature"), Mechanic.LoseAbilities(Keyword.Indestructible)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 603.2c: once for a batch of opponents' creatures dying; once each turn
    @Test
    fun spitefulThievery() = card(
        name = "Spiteful Thievery",
        cost = "{X}{R}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, it deals X damage to each creature.
            Whenever one or more creatures your opponents control die, you create a Treasure token. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(Value.CastX(Objects.Self), toObjects(Objects.Each(creature)))),
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard)), oneOrMore = true),
            createTreasure(), perTurn = 1),
    )

    @Test
    fun swarmingOfTheDeepMines() = card(
        name = "Swarming of the Deep Mines",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(createTreasure(), amassOrcs(2)),
    )

    // CR 611.2b, 111.4: a legendary Dragon token with its own dies trigger
    @Test
    fun outAndHomeAgain() = card(
        name = "Out and Home Again",
        cost = "{3}{R}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Up to one target creature can't block for as long as you control this Saga. The Ring tempts you.
            II — Search your library for a Mountain card, put it onto the battlefield, then shuffle.
            III — Create Smaug, a legendary 6/6 red Dragon creature token with flying, haste, and "When Smaug dies, create fourteen Treasure tokens."
        """,
    ) parsesTo abilities(
        chapter(1, doing(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)), Affected.Objects(target("creature")),
            Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))), ringTempts, targets = listOf(slot("creature", creature, min = 0))),
        chapter(2, search(sub("Mountain")), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
        chapter(3, create(TokenSpec.Defined(FaceDefinition("Smaug", colorIndicator = listOf(Color.Red), supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Dragon")), power = PrintedNumber.Fixed(6), toughness = PrintedNumber.Fixed(6),
            abilities = listOf(Keyword.Flying, Keyword.Haste, triggered(diesSelf, createTreasure(n(14)))))))),
    )

    @Test
    fun warbeastOfTheAshPlain() = card(
        name = "Warbeast of the Ash Plain",
        cost = "{4}{R}",
        type = "Creature — Beast",
        text = "Whenever this creature or another creature you control with power 4 or greater dies, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
        pt = "5/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, creature, you, power(Comparison.AtLeast, 4))), from = Zone.Battlefield, to = Zone.Graveyard)),
            amassOrcs(2)),
    )

    // CR 701.47a with X
    @Test
    fun assaultOnTheRiverCity() = card(
        name = "Assault on the River City",
        cost = "{X}{R}{R}{R}",
        type = "Sorcery",
        text = "Amass Orcs X, then Goblins and Orcs you control gain double strike and haste until end of turn. (To amass Orcs X, put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
    ) parsesTo abilities(
        spell(amassOrcs(Value.Announced(name = "X")), gains(Objects.Each(and(goblinOrOrc, you)), Keyword.DoubleStrike, Keyword.Haste), announced = listOf("X")),
    )

    // CR 301.5: Equipment attached to that creature; CR 603.12: "When you play a card this way"
    @Test
    fun firesOfTheBurningPeak() = card(
        name = "Fires of the Burning Peak",
        cost = "{2}{R}",
        type = "Legendary Enchantment",
        text = """
            When Fires of the Burning Peak enters, it deals 2 damage to target creature an opponent controls. Destroy all Equipment attached to that creature.
            {2}{R}: Exile the top card of your library. You may play that card this turn. When you play a card this way, Fires of the Burning Peak deals 2 damage to each player.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(2, toObjects(target("creature"))), destroy(Objects.AttachmentsOf(target("creature"), equipment)),
            targets = listOf(slot("creature", creatureOpponentControls))),
        activated(cost(mana("{2}{R}")),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("exiled")), from = Zone.Exile)),
                Instructions(steps = listOf(damage(2, arcanum.api.Recipients(players = Players.Each(PlayerFilter.Any)))))))),
    )

    // CR 702.41a: affinity for Humans; "if this creature attacked this turn", "If you do"
    @Test
    fun ridersOfThePlains() = card(
        name = "Riders of the Plains",
        cost = "{6}{R}",
        type = "Creature — Human Knight",
        text = """
            Affinity for Humans (This spell costs {1} less to cast for each Human you control.)
            Trample, haste
            At the beginning of your end step, if this creature attacked this turn, return it to its owner's hand. If you do, create a number of 1/1 white Human Soldier creature tokens equal to its toughness.
        """,
        pt = "7/4",
    ) parsesTo abilities(
        Keyword.Affinity(sub("Human"), "Humans"),
        Keyword.Trample,
        Keyword.Haste,
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            // CR 608.2c, 400.7: "if you do" asks whether it was returned (not once it has left the battlefield)
            move(Objects.Self, Zone.Hand, postBind = "returned"),
            Step.If(Condition.NonEmpty("returned"), then = listOf(create(humanSoldier, toughness(Objects.Self)))),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Is(Objects.Self))))),
    )

    @Suppress("unused") private val unused = listOf(CostObjects.Self, CostPart.TapSelf, EventRole.Object, Ability.Unparsed(""))
}
