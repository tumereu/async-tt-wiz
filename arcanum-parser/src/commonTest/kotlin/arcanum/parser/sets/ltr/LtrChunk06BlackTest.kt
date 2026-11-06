package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
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
 * The Lord of the Rings chunk 06: black cards, second part. A granted upkeep trigger paid with life (CR 118.12), the sacrificed
 * creature as it last existed for "instead" (CR 608.2h), a deck limit of nine (CR 100.2a), each player creating a Food, milling by your
 * Ring-bearer's power (CR 701.54e), opponents losing life per creature card in their graveyards, drawing except the first card in a draw
 * step, a copy token that's a 3/3 Wraith exiled at the next end step unless its creator is your Ring-bearer (CR 707.9b, 603.7), casting
 * exiled cards with mana of any type (CR 601.2h), a hand revealed and a card chosen (CR 701.20), a minimum of three blockers (CR 509.1b),
 * Food per creature sacrificed, creatures that dealt combat damage to you this turn, a guessing game, and the least power among creatures.
 */
class LtrChunk06BlackTest {
    // CR 118.12: "unless you pay 2 life"
    @Test
    fun witchBladeWound() = card(
        name = "Witch-Blade Wound",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -3/-0 and has "At the beginning of your upkeep, exile this creature unless you pay 2 life."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        static(pt(-3, 0), has(triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Pay(cost(CostPart.PayLife(n(2))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(move(Objects.Self, Zone.Exile))))),
            affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 608.2h: "If the sacrificed creature was legendary, draw three cards instead."
    @Test
    fun wretchedEnd() = card(
        name = "Wretched End",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Draw two cards. If the sacrificed creature was legendary, draw three cards instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature, preBind = "sacrificed")))),
        spell(Step.If(Condition.ObjectMatches(Objects.Bound("sacrificed"), legendary), then = listOf(draw(3)), otherwise = listOf(draw(2)))),
    )

    // CR 100.2a: up to nine in a deck; CR 701.54d
    @Test
    fun ringwight() = card(
        name = "Ringwight",
        cost = "{2}{B}",
        type = "Creature — Wraith Knight",
        text = """
            Deathtouch
            When this creature enters, the Ring tempts you.
            Whenever the Ring tempts you, put a +1/+1 counter on each Wraith you control.
            A deck can have up to nine cards named Ringwight.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(entersSelf, ringTempts),
        triggered(ringTemptsYou, counters(Objects.Each(and(sub("Wraith"), you)))),
        static(Mechanic.UpToInDeck(9)),
    )

    // CR 714.2b: you and the target opponent each create a Food
    @Test
    fun oathOfTheSilentHost() = card(
        name = "Oath of the Silent Host",
        cost = "{3}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — You and target opponent each create a Food token.
            II — Each opponent loses 3 life. Create a Treasure token.
            III — Create three tapped 1/1 white Spirit creature tokens with flying.
        """,
    ) parsesTo abilities(
        chapter(1, createFood(), doing(Effect.CreateToken(food, n(1), Players.Target("opponent"))), targets = listOf(targetOpponent)),
        chapter(2, loseLife(3, Players.Each(PlayerFilter.Opponent)), createTreasure()),
        chapter(3, doing(Effect.CreateToken(spiritFlyer, n(3), Players.You, tapped = true))),
    )

    // CR 701.54e: your Ring-bearer's power; CR 714.2b
    @Test
    fun oneRingToBindThemAll() = card(
        name = "One Ring to Bind Them All",
        cost = "{2}{B}{B}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — The Ring tempts you, then each player mills cards equal to your Ring-bearer's power.
            II — Destroy all nonlegendary creatures.
            III — Each opponent loses 1 life for each creature card in that player's graveyard.
        """,
    ) parsesTo abilities(
        chapter(1, ringTempts, doing(Effect.Mill(Players.Each(PlayerFilter.Any), Value.Of(NumericProperty.Power, Objects.Each(yourRingBearer), Combine.Total)))),
        chapter(2, destroy(Objects.Each(and(not(legendary), creature)))),
        chapter(3, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "player", listOf(
            doing(Effect.LoseLife(Players.Bound("player"), count(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player")))))))),
            together = true)),
    )

    // CR 120.3; "except the first one they draw in each of their draw steps"
    @Test
    fun orcishArrowmasters() = card(
        name = "Orcish Arrowmasters",
        cost = "{1}{B}",
        type = "Creature — Orc Archer",
        text = """
            Flash
            When this creature enters and whenever an opponent draws a card except the first one they draw in each of their draw steps, this creature deals 1 damage to any target. Then amass Orcs 1.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.Draw(PlayerFilter.Opponent, exceptFirstIn = TurnStep.Draw))),
            damage(1, toAny), amassOrcs(1), targets = listOf(anyTarget)),
    )

    @Test
    fun orcishPoultice() = card(
        name = "Orcish Poultice",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Target creature gains your choice of lifelink or indestructible until end of turn.
            Amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(Step.Choose(Choice.Option(listOf("lifelink", "indestructible")), bind = "keyword", chooser = Players.You),
            Step.If(Condition.Chose("keyword", "lifelink"), then = listOf(gains(target("creature"), Keyword.Lifelink)),
                otherwise = listOf(gains(target("creature"), Keyword.Indestructible))),
            amassOrcs(1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun hamfastsDesperateRescue() = card(
        name = "Hamfast's Desperate Rescue",
        cost = "{B}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to your hand. The Ring tempts you.",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand), ringTempts, targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 707.9b: a copy except a 3/3 black Wraith with menace; CR 603.7: exiled at the next end step unless Gorthaur is your Ring-bearer
    @Test
    fun gorthaurTheNecromancer() = card(
        name = "Gorthaur, the Necromancer",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Avatar Horror",
        text = """
            Menace
            Whenever Gorthaur attacks, exile target creature card from your graveyard. Create a tapped and attacking token that's a copy of that card, except it's a 3/3 black Wraith with menace. At the beginning of the next end step, exile that token unless Gorthaur is your Ring-bearer.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("exiled"), except = listOf(
                CopyException.PowerToughness(3, 3), CopyException.Colors(listOf(arcanum.api.ColorRef.Literal(Color.Black))),
                CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Wraith"))), replaceSubtypes = true),
                CopyException.AddAbilities(listOf(Keyword.Menace)))), n(1), Players.You, tapped = true, attacking = arcanum.api.AttackTarget.Chosen, bind = "token")),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Instructions(steps = listOf(
                Step.If(Condition.Not(Condition.ObjectMatches(Objects.Self, yourRingBearer)), then = listOf(move(Objects.Bound("token"), Zone.Exile))))))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 601.2h: cast from exile with mana of any type, for as long as the cards remain exiled
    @Test
    fun shadowOfTheFoe() = card(
        name = "Shadow of the Foe",
        cost = "{3}{B}{B}{B}",
        type = "Sorcery",
        text = "Exile all creature cards from target player's graveyard. You may cast spells from among those cards for as long as they remain exiled, and mana of any type can be spent to cast them.",
    ) parsesTo abilities(
        spell(move(Objects.Each(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, manaAsAnyColor = true, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)),
            targets = listOf(targetPlayer)),
    )

    @Test
    fun spidersAmbush() = card(
        name = "Spider's Ambush",
        cost = "{B}",
        type = "Instant",
        text = """Target creature gets +1/+2 and gains deathtouch until end of turn. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 2, has(Keyword.Deathtouch)), createFood(), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun snarlingWolfHound() = card(
        name = "Snarling Wolf-Hound",
        cost = "{3}{B}",
        type = "Creature — Wolf",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            As long as you control a Goblin or Orc, this creature gets +1/+0.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Menace,
        static(pt(1, 0), condition = Condition.Exists(and(goblinOrOrc, you), atLeast = 1)),
    )

    // CR 701.20: the opponent reveals; you choose a nonland card
    @Test
    fun theTormentOfTheWretch() = card(
        name = "The Torment of the Wretch",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
            Amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            amassOrcs(2), targets = listOf(targetOpponent)),
    )

    // CR 509.1b: three or more blockers; CR 702.29e: swampcycling
    @Test
    fun trollOfTheDeepHalls() = card(
        name = "Troll of the Deep Halls",
        cost = "{5}{B}",
        type = "Creature — Troll",
        text = """
            This creature can't be blocked except by three or more creatures.
            Swampcycling {1} ({1}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        static(Mechanic.MinimumBlockers(3)),
        Keyword.Typecycling(sub("Swamp"), "Swampcycling", manaCost("{1}")),
    )

    @Test
    fun urukBerserker() = card(
        name = "Uruk Berserker",
        cost = "{2}{B}",
        type = "Creature — Orc Berserker",
        text = "When this creature enters, the Ring tempts you.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, ringTempts),
    )

    // CR 701.21a: each opponent sacrifices; a Food for each creature sacrificed this way
    @Test
    fun ravenousFellSteed() = card(
        name = "Ravenous Fell Steed",
        cost = "{4}{B}{B}",
        type = "Creature — Drake Beast",
        text = """
            Flying
            When this creature enters, each opponent sacrifices a creature of their choice. Create a Food token for each creature sacrificed this way. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true),
            createFood(Value.Count(Objects.Bound("sacrificed")))),
    )

    // CR 510.1: the creatures that dealt combat damage to you this turn; CR 701.54a
    @Test
    fun witchLordOfTheNorth() = card(
        name = "Witch-lord of the North",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Wraith Noble",
        text = """
            Flying
            Whenever one or more creatures deal combat damage to you, each opponent sacrifices a creature of their choice that dealt combat damage to you this turn. The Ring tempts you.
            Discard a card: Witch-lord of the North gains indestructible until end of turn. Tap him.
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(listOf(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.You), combat = true)), oneOrMore = true),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))),
                    ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You), combat = true), role = EventRole.Source)), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true),
            ringTempts),
        activated(cost(discardACard), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
    )

    // CR 113.6: a trigger working in the graveyard
    @Test
    fun ringboundWraiths() = card(
        name = "Ringbound Wraiths",
        cost = "{4}{B}{B}",
        type = "Creature — Wraith Knight",
        text = """
            When this creature enters, target creature an opponent controls gets -3/-3 until end of turn. If that creature is legendary, its controller loses 3 life.
            When the Ring tempts you, return this card from your graveyard to your hand.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        triggered(entersSelf, pump(target("creature"), -3, -3),
            Step.If(Condition.ObjectMatches(target("creature"), legendary), then = listOf(loseLife(3, Players.ControllerOf(target("creature"))))),
            targets = listOf(slot("creature", creatureOpponentControls))),
        triggered(ringTemptsYou, move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard)),
    )

    // A guess by an opponent: whether the top card is the chosen kind (CR 701.20)
    @Test
    fun smeagorSchemingGuide() = card(
        name = "Smeagor, Scheming Guide",
        cost = "{1}{B}",
        type = "Legendary Creature — Halfling Horror",
        text = "Whenever Smeagor attacks, look at the top two cards of your library, put them back in any order, then choose land or nonland. An opponent guesses whether the top card of your library is the chosen kind. Reveal that card. If they guessed right, remove Smeagor from combat. Otherwise, you draw a card and Smeagor can't be blocked this turn.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(attacksSelf,
            doing(Effect.LookAt(topTwo, Players.You)), doing(Effect.Reorder(topTwo, Players.You)),
            Step.Choose(Choice.Option(listOf("land", "nonland")), bind = "kind", chooser = Players.You),
            Step.Choose(Choice.Option(listOf("yes", "no")), bind = "guess", chooser = Players.Each(PlayerFilter.Opponent)),
            doing(Effect.Reveal(topOne)),
            Step.If(guessedRight, then = listOf(doing(Effect.RemoveFromCombat(Objects.Self))),
                otherwise = listOf(draw(1), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)))),
    )

    // CR 208.1: the least power among the defending player's creatures
    @Test
    fun witchLordBringerOfRuin() = card(
        name = "Witch-lord, Bringer of Ruin",
        cost = "{4}{B}{B}",
        type = "Legendary Creature — Wraith Noble",
        text = """
            Flying
            Whenever Witch-lord attacks, defending player sacrifices a creature with the least power among creatures they control.
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(defending)),
                ObjectFilter.Power(Comparison.Equal, Value.Of(NumericProperty.Power, Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(defending)))), Combine.Least))),
                n(1), n(1)), bind = "sacrificed", chooser = defending),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))),
    )

    @Test
    fun wolfRider() = card(
        name = "Wolf Rider",
        cost = "{4}{B}",
        type = "Creature — Orc Warrior",
        text = """
            Menace
            Other Orcs and Goblins you control have menace.
            At the beginning of combat on your turn, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Menace,
        static(has(Keyword.Menace), affects = Objects.Each(and(another, or(sub("Orc"), sub("Goblin")), you))),
        triggered(beginningOfCombatOnYourTurn, amassOrcs(2)),
    )

    private val topTwo = Objects.LibraryCards(Players.You, n(2), fromTop = true)
    private val topOne = Objects.LibraryCards(Players.You, n(1), fromTop = true)
    private val isKind = Condition.Or(listOf(
        Condition.And(listOf(Condition.Chose("kind", "land"), Condition.ObjectMatches(topOne, land))),
        Condition.And(listOf(Condition.Chose("kind", "nonland"), Condition.ObjectMatches(topOne, nonland)))))
    private val guessedRight = Condition.Or(listOf(
        Condition.And(listOf(isKind, Condition.Chose("guess", "yes"))),
        Condition.And(listOf(Condition.Not(isKind), Condition.Chose("guess", "no")))))
    private val defending = Players.DefendingPlayerOf(Objects.Self)
    @Suppress("unused") private val unused = listOf(Ability.Unparsed(""), Cost(emptyList()), CostObjects.Self, CardType.Creature)
}
