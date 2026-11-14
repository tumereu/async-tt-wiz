package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
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
 * Dominaria United chunk 06: choices (a target chosen for a delayed trigger, modes, a card type other than some), counters distributed,
 * tokens, a destroy by total power and toughness, a discard condition, an optional extra draw, and exiles (several targets, crew, until a
 * card with lesser mana value, until this leaves).
 */
class DmuChunk06ChoicesAndExileTest {
    private val red = ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Red))
    private val bottomRandom = Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
    private val totalPowerToughness = Value.Sum(listOf(Value.Of(NumericProperty.Power, Objects.Tested, Combine.Single),
        Value.Of(NumericProperty.Toughness, Objects.Tested, Combine.Single)))

    // CR 603.7b-c: a delayed trigger for the rest of the turn about the creature chosen as the ability resolved; CR 508.3d: "whenever you
    // attack" triggers once per combat; CR 707.10: copies of a spell
    @Test
    fun tallaEmberEnvoy() = card(
        name = "Talla, Ember Envoy",
        cost = "{2}{R}{R}",
        type = "Legendary Planeswalker — Talla",
        loyalty = "4",
        text = """
            [+1]: Create a 1/1 red Monk creature token with prowess.
            [−1]: Exile the top two cards of your library. Choose one of them. You may play that card this turn.
            [−2]: Choose target creature an opponent controls. Whenever you attack this turn, Talla deals damage equal to the number of attacking creatures to that creature.
            [−8]: You get an emblem with "Whenever you cast a red instant or sorcery spell, copy it twice. You may choose new targets for the copies."
        """,
    ) parsesTo abilities(
        loyalty(1, create(creatureToken("Monk", listOf(Color.Red), 1, 1, abilities = listOf(Keyword.Prowess)))),
        loyalty(-1,
            move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("chosen")),
                Duration.EndOfTurn)),
        ),
        loyalty(-2,
            Step.Bind(target("creature"), "chosen"),
            doing(Effect.CreateDelayedTrigger(youAttack, Instructions(steps = listOf(
                damage(count(and(attacking, creature)), toObjects(Objects.Bound("chosen"))))), duration = Duration.EndOfTurn, repeats = true)),
            targets = listOf(slot("creature", creatureOpponentControls)),
        ),
        loyalty(-8, doing(Effect.CreateEmblem(listOf(triggered(youCast(or(and(red, instant), and(red, sorcery))),
            Step.Repeat(n(2), listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))))), Players.You))),
    )

    // CR 700.2: a modal triggered ability; CR 701.20a: a look at cards; CR 401.4: the rest on the bottom in a random order
    @Test
    fun bramblehideElder() = card(
        name = "Bramblehide Elder",
        cost = "{2}{G}{G}{G}",
        type = "Creature — Ape Shaman",
        pt = "5/7",
        text = """
            Whenever you cast a creature spell, choose one —
            • Destroy target artifact or enchantment.
            • Look at the top five cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest on the bottom of your library in a random order.
            • You gain 4 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(youCast(creature), Instructions(modes = modes(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            mode(
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "looked")),
                Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
                doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), bottomRandom)),
            ),
            mode(gainLife(4)),
        ))),
    )

    // CR 700.2: each mode once over the game ("that hasn't been chosen"); CR 115.1: an optional target; CR 110.2a: put onto the battlefield
    // under the chosen opponent's control; CR 400.7: the permanent returned is a new object
    @Test
    fun vorrakTheTainted() = card(
        name = "Vorrak the Tainted",
        cost = "{2}{U}{B}{R}",
        type = "Legendary Creature — Elemental Demon",
        pt = "5/5",
        text = """
            At the beginning of your end step, choose one that hasn't been chosen —
            • Draw a card.
            • Each opponent loses 2 life and you gain 2 life.
            • Vorrak deals 3 damage to up to one other target creature or planeswalker.
            • Exile Vorrak, then return it to the battlefield under an opponent's control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(stepBegins(TurnStep.End, PlayerFilter.You), Instructions(modes = modes(
            mode(draw(1)),
            mode(loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
            mode(damage(3, toObjects(target("permanent"))), targets = listOf(slot("permanent", and(another, or(creature, planeswalker)), min = 0))),
            mode(
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.Bound("opponent")),
            ),
            onlyUnchosen = true,
        ))),
    )

    // CR 614.12, 607.2d: a card type other than creature or land, chosen as it enters (CR 205.2a); CR 601.2f: the cost reduction
    @Test
    fun oskelWaryPartisan() = card(
        name = "Oskel, Wary Partisan",
        cost = "{W}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/2",
        text = """
            As Oskel enters, choose a card type other than creature or land.
            Spells you cast of the chosen type cost {1} less to cast.
            {1}{W}{U}: Exile Oskel. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CardType(listOf(CardType.Artifact, CardType.Battle, CardType.Enchantment, CardType.Instant, CardType.Kindred,
                CardType.Planeswalker, CardType.Sorcery)), bind = "type", chooser = Players.You, remember = true)))))),
        static(Mechanic.CostChange(and(spellFilter, you, ObjectFilter.Type(CardTypeRef.Remembered("type"))), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        activated(manaCost("{1}{W}{U}"),
            move(Objects.Self, Zone.Exile, postBind = "exiled"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
        ),
    )

    // CR 701.14a: fight; CR 601.2c: "if you cast this spell during your main phase" is settled as it is cast (CR 505.1)
    @Test
    fun tailLashGambit() = card(
        name = "Tail Lash Gambit",
        cost = "{G}",
        type = "Instant",
        text = "Choose target creature you control and target creature you don't control. If you cast this spell during your main phase, the creature you control gets +1/+1 until end of turn. Then those creatures fight each other. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(
            Step.If(Condition.AsCast(Condition.And(listOf(Condition.YourTurn,
                Condition.Or(listOf(Condition.StepIs(TurnStep.PrecombatMain), Condition.StepIs(TurnStep.PostcombatMain)))))), then = listOf(pump(target("creature"), 1, 1))),
            doing(Effect.Fight(target("creature"), target("other creature"))),
            targets = listOf(slot("creature", creatureYouControl),
                slot("other creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 603.7: a delayed trigger, once, this turn; CR 707.10: the copy; CR 702.4: double strike
    @Test
    fun mirrorflame() = card(
        name = "Mirrorflame",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • When you cast your next instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.
            • Target creature you control gains double strike until end of turn. (It deals both first-strike and regular combat damage.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.Cast(and(or(instant, sorcery), spellFilter), by = PlayerFilter.You)), Instructions(steps = listOf(
                doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true)))), duration = Duration.EndOfTurn))),
            mode(gains(target("creature"), Keyword.DoubleStrike), targets = listOf(slot("creature", creatureYouControl))),
        ))),
    )

    // CR 601.2d: X divided among any number of targets, each getting at least one; CR 603.10a: X counted as the creature last existed
    @Test
    fun thicketBeastcaller() = card(
        name = "Thicket Beastcaller",
        cost = "{1}{G}",
        type = "Creature — Dryad Warrior",
        pt = "2/2",
        text = """
            Whenever you cast a creature spell, put a +1/+1 counter on this creature.
            When this creature dies, distribute X +1/+1 counters among any number of target creatures you control, where X is the number of +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        triggered(youCast(creature), counters(Objects.Self)),
        triggered(diesSelf, doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creatureYouControl), min = n(0), max = null,
                divided = Value.Of(NumericProperty.Counters(p1p1), Objects.EventObject(EventRole.Before), Combine.Single)))),
    )

    // CR 111.10h: a Powerstone token; CR 107.1c, 118.3: any amount of mana, chosen then paid; CR 114.1: an emblem
    @Test
    fun varnEnduringLegacy() = card(
        name = "Varn, Enduring Legacy",
        cost = "{4}",
        type = "Legendary Planeswalker — Varn",
        loyalty = "4",
        text = """
            [+1]: Create a tapped Powerstone token. (It's an artifact with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
            [−1]: Pay any amount of mana. Look at that many cards from the top of your library, then put one of those cards into your hand and the rest on the bottom of your library in a random order.
            [−7]: You get an emblem with "Tap an untapped artifact you control: This emblem deals 1 damage to any target."
        """,
    ) parsesTo abilities(
        loyalty(1, doing(Effect.CreateToken(TokenSpec.Predefined("Powerstone"), n(1), Players.You, tapped = true))),
        loyalty(-1,
            Step.Choose(Choice.Number(n(0)), bind = "X", chooser = Players.You),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X")))), payer = Players.You, mandatory = true, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Bound("X"), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                move(Objects.Bound("kept"), Zone.Hand),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), bottomRandom)),
            )),
        ),
        loyalty(-7, doing(Effect.CreateEmblem(listOf(activated(Cost(listOf(CostPart.TapObjects(arcanum.api.CostObjects.Choose(artifact, n(1)))), spans = listOf(arcanum.api.TextSpan(4, 36))),
            damage(1, toAny), targets = listOf(anyTarget))), Players.You))),
    )

    // CR 208.1: a creature's power and toughness added together
    @Test
    fun shearDown() = card(
        name = "Shear Down",
        cost = "{B}",
        type = "Instant",
        text = "Destroy target creature with total power and toughness 5 or less.",
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(creature,
            ObjectFilter.Where(Condition.Compare(totalPowerToughness, Comparison.AtMost, n(5))))))),
    )

    // CR 603.4: an intervening "if" about any player's discard this turn (CR 701.9a)
    @Test
    fun theCrowWoman() = card(
        name = "The Crow Woman",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/1",
        text = """
            At the beginning of each end step, if a player discarded a card this turn, create a 1/1 black Bird creature token with flying and "This token can't block."
            {3}{B}, {T}: Each opponent discards a card. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any),
            create(creatureToken("Bird", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Flying, static(Mechanic.CantBlock(ObjectFilter.Any)))), bind = "token"),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Discard(players = PlayerFilter.Any, cards = ObjectFilter.Any), atLeast = 1,
                measure = Measure.Events)),
        activated(cost(mana("{3}{B}"), tap), discard(1, Players.Each(PlayerFilter.Opponent)), rules = asSorcery),
    )

    // CR 608.2d: that player decides; CR 601.2f: their spells cost more for the rest of the turn; CR 504.1: the draw step
    @Test
    fun tutorOfTheSpires() = card(
        name = "Tutor of the Spires",
        cost = "{U}{U}",
        type = "Creature — Human Wizard",
        pt = "2/3",
        text = "At the beginning of each player's draw step, that player may draw an additional card. If they do, spells they cast this turn cost {2} more to cast.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Draw, PlayerFilter.Any),
            Step.Pay(steps = listOf(draw(1, Players.EventPlayer)), payer = Players.EventPlayer, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.CostChange(
                and(spellFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))), CostChange.More(ManaCost.parse("{2}")!!, n(1)))),
                null, Duration.EndOfTurn)))),
        ),
    )

    // CR 115.1: up to one target of each type; CR 608.2h: each exiled permanent as it last existed (its controller, its card types);
    // CR 701.20a: reveal; CR 110.2a: the card enters under the control of the player who put it there; CR 701.24a: shuffle
    @Test
    fun wildfluxUpheaval() = card(
        name = "Wildflux Upheaval",
        cost = "{5}{R}",
        type = "Sorcery",
        text = "Exile up to one target artifact, up to one target creature, up to one target enchantment, up to one target planeswalker, and/or up to one target land. For each permanent exiled this way, its controller reveals cards from the top of their library until they reveal a card that shares a card type with it, puts that card onto the battlefield, then shuffles.",
    ) parsesTo run {
        val names = listOf("artifact", "creature", "enchantment", "planeswalker", "land")
        val permanent = Objects.Bound("permanent")
        val player = Players.ControllerOf(permanent)
        abilities(spell(
            doing(Effect.Move(Objects.Union(names.map { target(it) }), Destination(Zone.Exile), preBind = "exiled")),
            Step.ForEach(Each.Objects(Objects.Bound("exiled")), bind = "permanent", steps = listOf(
                doing(Effect.RevealUntil(ObjectFilter.Shares(SharedQuality.CardType, permanent), player, n(1), reveal = true, foundBind = "found")),
                move(Objects.Bound("found"), Zone.Battlefield, controller = player),
                doing(Effect.Shuffle(player)),
            )),
            targets = listOf(slot("artifact", artifact, min = 0), slot("creature", creature, min = 0), slot("enchantment", enchantment, min = 0),
                slot("planeswalker", planeswalker, min = 0), slot("land", land, min = 0)),
        ))
    }

    // CR 702.122: crew; CR 603.7: the creatures exiled return at the next end step, tapped, under their owners' control (CR 400.7)
    @Test
    fun gildedBarque() = card(
        name = "Gilded Barque",
        cost = "{4}",
        type = "Legendary Artifact — Vehicle",
        pt = "3/6",
        text = """
            Whenever Gilded Barque attacks, exile each creature that crewed it this turn. Return them to the battlefield tapped under their owner's control at the beginning of the next end step.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        triggered(attacksSelf,
            move(Objects.Each(and(creature, ObjectFilter.CrewedThisTurn(Objects.Self))), Zone.Exile, postBind = "exiled"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                move(Objects.Bound("exiled"), Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.Bound("exiled"))))))),
        ),
        Keyword.Crew(1),
    )

    // CR 701.20a: exile until a card is found; CR 608.2h: lesser than the triggering spell's mana value; CR 601.2, 118.9: cast without
    // paying; CR 401.4: the rest, and the found card if not cast, on the bottom in a random order
    @Test
    fun ulmarTheGatherer() = card(
        name = "Ulmar, the Gatherer",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — Human Wizard",
        pt = "5/5",
        text = """
            Legendary creatures you control get +X/+X, where X is the number of legendary creatures you control.
            Whenever you cast a legendary spell from your hand, exile cards from the top of your library until you exile a legendary nonland card with lesser mana value. You may cast that card without paying its mana cost. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo run {
        val legendaryCreatures = and(legendary, creature, you)
        val lesser = ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single))
        abilities(
            static(Mechanic.ModifyPowerToughness(count(legendaryCreatures), count(legendaryCreatures)), affects = Objects.Each(legendaryCreatures)),
            triggered(on(EventPattern.Cast(and(legendary, spellFilter), by = PlayerFilter.You, from = Zone.Hand)),
                doing(Effect.RevealUntil(and(legendary, nonland, lesser), Players.You, n(1), reveal = true, into = Zone.Exile, foundBind = "found",
                    restBind = "rest")),
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("found"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You),
                doing(Effect.Move(Objects.Where(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), ObjectFilter.InZone(Zone.Exile)), bottomRandom)),
            ),
        )
    }

    // CR 610.3: exiled until this enchantment leaves the battlefield; CR 202.3: mana value
    @Test
    fun briefInterdiction() = card(
        name = "Brief Interdiction",
        cost = "{1}{W}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile each nonland permanent with mana value 2 or less until this enchantment leaves the battlefield.",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(Objects.Each(and(nonland, permanentFilter, manaValue(Comparison.AtMost, 2))), Destination(Zone.Exile),
            untilLeaves = Objects.Self))),
    )
}
