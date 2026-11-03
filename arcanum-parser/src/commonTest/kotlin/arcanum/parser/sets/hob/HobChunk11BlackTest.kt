package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 11: black cards. Exiled instead of dying this turn (CR 614.1a); creatures a target player controls; "choose
 * odd or even" kept as card memory and a spell of that quality (zero is even; CR 607.2a, 202.3) with modes not chosen before
 * (CR 700.2); a graveyard card exiled; a card that returns itself from the graveyard (CR 113.6m); "one or more other creatures
 * die" as a batch (CR 603.2c); an opponent's top cards played this turn for life instead of mana (CR 118.9); a player's life
 * loss milling that many; sacrificing a token; an additional cost of one of two kinds (CR 601.2f); creature cards that died
 * this turn returning as Food artifacts (CR 611.2e, 205.1a).
 */
class HobChunk11BlackTest {
    @Test
    fun tobbosLethalCut() = card(
        name = "Tobbo's Lethal Cut",
        cost = "{1}{B}{B}",
        type = "Instant",
        text = "Destroy target creature.",
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 614.1a: "if that creature would die this turn, exile it instead" (a replacement for the turn)
    @Test
    fun grindingOfFangs() = card(
        name = "Grinding of Fangs",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target creature gets -5/-5 until end of turn. If that creature would die this turn, exile it instead.
            • Creatures target player controls get -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(pump(target("creature"), -5, -5), Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
                targets = listOf(slot("creature", creature))),
            mode(pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), -1, -1), targets = listOf(targetPlayer))))),
    )

    /** The chosen quality is card memory (CR 607.2a); the cast spell is tested against it (zero is even); each mode once for this
     * object (CR 700.2). */
    // CR 607.2a, 700.2
    @Test
    fun smegglePuzzleMaster() = card(
        name = "Smeggle, Puzzle Master",
        cost = "{1}{B}",
        type = "Legendary Creature — Halfling Horror",
        text = """
            As Smeggle enters, choose odd or even. (Zero is even.)
            Whenever an opponent casts a spell with mana value of the chosen quality, choose one that hasn't been chosen —
            • Put a +1/+1 counter on Smeggle.
            • Each opponent loses 2 life and you gain 2 life.
            • Draw a card.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Option(listOf("odd", "even")), bind = "quality", chooser = Players.You, remember = true)))))),
        Ability.Triggered(on(EventPattern.Cast(ObjectFilter.Where(Condition.Or(listOf(
            Condition.And(listOf(Condition.RememberedOption("quality", "odd"), Condition.Odd(Value.Of(NumericProperty.ManaValue, Objects.Tested, Combine.Single)))),
            Condition.And(listOf(Condition.RememberedOption("quality", "even"),
                Condition.Not(Condition.Odd(Value.Of(NumericProperty.ManaValue, Objects.Tested, Combine.Single)))))))), PlayerFilter.Opponent)),
            Instructions(modes = modes(
                mode(counters(Objects.Self)),
                mode(loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
                mode(draw(1)), onlyUnchosen = true))),
    )

    // CR 113.6m: the last ability returns the card from the graveyard, where it works
    @Test
    fun smeggleTheForsaken() = card(
        name = "Smeggle the Forsaken",
        cost = "{1}{B}",
        type = "Legendary Creature — Halfling Horror",
        text = """
            Smeggle can't block.
            When Smeggle enters, exile up to one target card from an opponent's graveyard. Each opponent loses 2 life.
            {2}, Sacrifice an artifact or creature: Return this card from your graveyard to your hand. Activate only as a sorcery.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(entersSelf, move(target("card"), Zone.Exile), loseLife(2, Players.Each(PlayerFilter.Opponent)),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)), min = 0))),
        activated(cost(mana("{2}"), sacrificeA(or(artifact, creature))), move(Objects.Self, Zone.Hand), rules = asSorcery, functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 603.2c: once for the batch
    @Test
    fun hugeAngryHornet() = card(
        name = "Huge Angry Hornet",
        cost = "{2}{B}",
        type = "Creature — Insect",
        text = """
            Flying
            Whenever one or more other creatures die, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, creature), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true), scry(1)),
    )

    // CR 118.9: life equal to the spell's mana value rather than its mana cost
    @Test
    fun secretKnowledge() = card(
        name = "Secret Knowledge",
        cost = "{X}{B}{B}",
        type = "Sorcery",
        text = "Exile the top X cards of target opponent's library. You may play those cards this turn. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.Target("opponent"), Value.Announced("X"), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You,
                cost = cost(CostPart.PayLife(Value.Of(NumericProperty.ManaValue, Objects.Affected, Combine.Single))))),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            targets = listOf(targetOpponent), announced = listOf("X")),
    )

    // CR 120.3a: damage causes loss of life; that player mills that many
    @Test
    fun theMayorOfMereTown() = card(
        name = "The Mayor of Mere-town",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Human Advisor",
        text = """
            Deathtouch
            Whenever a player loses life, that player mills that many cards. (Damage causes loss of life.)
            When The Mayor of Mere-town dies, draw a card for each graveyard with seven or more cards in it.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.LoseLife(PlayerFilter.Any)), Step.Do(Effect.Mill(Players.EventPlayer, Value.EventAmount))),
        triggered(diesSelf, Step.Do(Effect.Draw(Players.You, Value.CountPlayers(graveyardsWithSeven)))),
    )

    // CR 700.2
    @Test
    fun solemnHowl() = card(
        name = "Solemn Howl",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Target player draws two cards and loses 2 life.
            • Target creature gets +2/+2 and gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(draw(2, Players.Target("player")), loseLife(2, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(pump(target("creature"), 2, 2, has(Keyword.Lifelink)), targets = listOf(slot("creature", creature)))))),
    )

    // CR 118.12; 701.21: "whenever you sacrifice a token"
    @Test
    fun theStingeworthTunnelys() = card(
        name = "The Stingeworth-Tunnelys",
        cost = "{1}{B}",
        type = "Legendary Creature — Halfling Citizen",
        text = """
            When The Stingeworth-Tunnelys enter, you may sacrifice another creature or artifact. If you do, draw a card and create a Treasure token.
            Whenever you sacrifice a token, target opponent loses 1 life.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(sacrificeA(and(another, or(creature, artifact)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1), createTreasure()))),
        triggered(on(EventPattern.Sacrifice(and(token, you))), loseLife(1, Players.Target("opponent")), targets = listOf(targetOpponent)),
    )

    // CR 601.2f: one of two additional costs
    @Test
    fun kickUpAFuss() = card(
        name = "Kick Up a Fuss",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature or pay {4}.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(cost(sacrificeA(or(artifact, creature))), manaCost("{4}")))))),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 701.9a: each opponent discards a card of their choice
    @Test
    fun gravelVoicedGoblins() = card(
        name = "Gravel-Voiced Goblins",
        cost = "{1}{B}",
        type = "Creature — Goblin Bard",
        text = "When this creature enters, each opponent discards a card.",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, discard(1, Players.Each(PlayerFilter.Opponent))),
    )

    /** The creature cards that died this turn come back as Food artifacts only, their other types and subtypes gone, as they enter
     * (CR 611.2e, 205.1a). */
    // CR 611.2e
    @Test
    fun dinnerForWebs() = card(
        name = "Dinner for Webs",
        cost = "{1}{B}",
        type = "Instant",
        text = """Put onto the battlefield under your control all creature cards in your opponents' graveyards that were put there from the battlefield this turn. They are Food artifacts with "{2}, {T}, Sacrifice this artifact: You gain 3 life." (They lose all other types and subtypes.)""",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(Objects.Each(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent),
            ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Battlefield, to = Zone.Graveyard), role = EventRole.After))),
            Destination(Zone.Battlefield, controller = Players.You, entersAs = listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Food"))), replaceTypes = true, replaceSubtypes = true),
                has(activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)))))))),
    )
}
