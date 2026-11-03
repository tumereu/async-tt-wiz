package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 06: the Seasons and other modal cards. "Choose up to five {P} worth of modes. You may choose the
 * same mode more than once." lets any modes be chosen whose pawprints add up to at most five, each mode as often as
 * wanted (CR 700.2i, 107.18); each mode keeps its line. With them, modes of modal triggers and spells: edicts, copies
 * of a chosen permanent, a chosen card type, a delayed trigger lasting until the end of your next turn, "if that
 * creature would die this turn, exile it instead", fighting a token.
 */
class BlbChunk06SeasonsAndModesTest {
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val permanent = ObjectFilter.Category(ObjectCategory.Permanent)
    private val token = ObjectFilter.Category(ObjectCategory.Token)
    private val permanentCard = ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
        CardType.Planeswalker).map { ObjectFilter.Type(it) })
    private val artifactOrEnchantment = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment)))

    /** "Choose up to five {P} worth of modes. You may choose the same mode more than once." with [modes] (pawprints, steps). */
    private fun season(vararg modes: Pair<Int, Instructions>) = Ability.Spell(Instructions(modes = Modes(
        options = modes.map { (paws, instructions) -> Mode(instructions, pawprints = paws) },
        min = 1,
        max = 5,
        repeatable = true,
        pawprintLimit = 5,
        chooser = Players.You,
    )))

    // CR 700.2i: up to five pawprints of modes, repeatable; CR 122.1b: a keyword counter
    @Test
    fun timeOfTheWarren() = card(
        name = "Time of the Warren",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = """
            Choose up to five {P} worth of modes. You may choose the same mode more than once.
            {P} — Create a 1/1 white Rabbit creature token.
            {P}{P} — Exile target nonland permanent. Its controller draws a card.
            {P}{P}{P} — Return target permanent card with mana value 3 or less from your graveyard to the battlefield with an indestructible counter on it.
        """,
    ) parsesTo abilities(
        season(
            1 to steps(Step.Do(Effect.CreateToken(rabbitToken, count = one(), controller = Players.You))),
            2 to Instructions(
                targets = listOf(slot("permanent", nonlandPermanent)),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Exile))),
                    Step.Do(Effect.Draw(Players.ControllerOf(Objects.Target("permanent")), one())),
                ),
            ),
            3 to Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(permanentCard, ObjectFilter.ManaValue(Comparison.AtMost, one(3))) + yourGraveyard))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield,
                    counters = listOf(CountersToPut(CounterKind.Keyword(Keyword.Indestructible), one())))))),
            ),
        ),
    )

    // CR 707.2: a token copy of the chosen permanent (chosen, not targeted)
    @Test
    fun timeOfTangles() = card(
        name = "Time of Tangles",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Choose up to five {P} worth of modes. You may choose the same mode more than once.
            {P} — Draw a card.
            {P}{P} — Choose an artifact or creature you control. Create a token that's a copy of it.
            {P}{P}{P} — Return each nonland, nontoken permanent to its owner's hand.
        """,
    ) parsesTo abilities(
        season(
            1 to steps(Step.Do(Effect.Draw(Players.You, one()))),
            2 to steps(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature)), you)),
                    min = one(), max = one()), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("chosen")), count = one(), controller = Players.You)),
            ),
            3 to steps(Step.Do(Effect.Move(
                Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Not(token), permanent))),
                Destination(Zone.Hand),
            ))),
        ),
    )

    // CR 101.4: each player chooses, then all sacrifice at once; CR 700.4: "died"
    @Test
    fun timeOfMourning() = card(
        name = "Time of Mourning",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = """
            Choose up to five {P} worth of modes. You may choose the same mode more than once.
            {P} — Each player sacrifices a creature of their choice.
            {P}{P} — Draw a card for each creature that died under your control this turn.
            {P}{P}{P} — Each opponent loses X life, where X is the number of creature cards in your graveyard.
        """,
    ) parsesTo abilities(
        season(
            1 to steps(Step.ForEach(
                Each.Players(Players.Each(PlayerFilter.Any)),
                bind = "player",
                together = true,
                steps = listOf(
                    Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))),
                        min = one(), max = one()), bind = "sacrificed", chooser = Players.Bound("player")),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                ),
            )),
            2 to steps(Step.Do(Effect.Draw(Players.You, Value.EventsThisTurn(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(creature, you)), from = Zone.Battlefield, to = Zone.Graveyard),
                Measure.Objects,
            )))),
            3 to steps(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent),
                Value.Count(Objects.Each(ObjectFilter.And(listOf(creature) + yourGraveyard)))))),
        ),
    )

    // CR 603.7b: a delayed trigger that triggers each time until it ends
    @Test
    fun timeOfTheDaring() = card(
        name = "Time of the Daring",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            Choose up to five {P} worth of modes. You may choose the same mode more than once.
            {P} — Create a tapped Treasure token.
            {P}{P} — Exile the top two cards of your library. Until the end of your next turn, you may play them.
            {P}{P}{P} — Until the end of your next turn, whenever you cast a spell, Time of the Daring deals 2 damage to up to one target creature.
        """,
    ) parsesTo abilities(
        season(
            1 to steps(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Treasure"), count = one(), controller = Players.You, tapped = true))),
            2 to steps(
                Step.Do(Effect.Move(Objects.LibraryCards(Players.You, one(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")),
                    Duration.UntilEndOfYourNextTurn,
                )),
            ),
            3 to steps(Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)),
                Instructions(
                    targets = listOf(slot("creature", creature, min = 0)),
                    steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), one(2), source = Objects.Self))),
                ),
                duration = Duration.UntilEndOfYourNextTurn,
                repeats = true,
            ))),
        ),
    )

    // CR 205.2a: a card type chosen as it resolves
    @Test
    fun timeOfReaping() = card(
        name = "Time of Reaping",
        cost = "{4}{G}{G}",
        type = "Sorcery",
        text = """
            Choose up to five {P} worth of modes. You may choose the same mode more than once.
            {P} — Put a +1/+1 counter on a creature you control. It gains vigilance and trample until end of turn.
            {P}{P} — Choose artifact or enchantment. Destroy all permanents of the chosen type.
            {P}{P}{P} — Draw cards equal to the greatest power among creatures you control.
        """,
    ) parsesTo abilities(
        season(
            1 to steps(
                Step.Choose(Choice.Objects(creatureYouControl, min = one(), max = one()), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen")),
                gains(Objects.Bound("chosen"), Keyword.Vigilance, Keyword.Trample),
            ),
            2 to steps(
                Step.Choose(Choice.CardType(listOf(CardType.Artifact, CardType.Enchantment)), bind = "type", chooser = Players.You),
                Step.Do(Effect.Destroy(Objects.Each(ObjectFilter.And(listOf(permanent, ObjectFilter.Type(CardTypeRef.Chosen("type"))))))),
            ),
            3 to steps(Step.Do(Effect.Draw(Players.You, Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
        ),
    )

    // ---- Other modes

    // CR 700.2: a modal triggered ability; "that was dealt damage this turn"
    @Test
    fun leewardLurker() = card(
        name = "Leeward Lurker",
        cost = "{3}{B}",
        type = "Creature — Skunk Assassin",
        pt = "4/2",
        text = """
            Flash
            When this creature enters, choose one —
            • Target creature an opponent controls gets -1/-1 until end of turn.
            • Destroy target creature an opponent controls that was dealt damage this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(modes = Modes(
                options = listOf(
                    Mode(Instructions(
                        targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, opponents)))),
                        steps = listOf(pump(Objects.Target("creature"), -1, -1)),
                    )),
                    Mode(Instructions(
                        targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, opponents,
                            ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = arcanum.api.EventRole.Object))))),
                        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature")))),
                    )),
                ),
                min = 1,
                max = 1,
                chooser = Players.You,
            )),
        ),
    )

    // CR 701.21a-like choice by the opponent: they exile one of their enchantments
    @Test
    fun firstFrost() = card(
        name = "First Frost",
        cost = "{4}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Exile target creature.
            • Target opponent exiles an enchantment they control.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.Move(Objects.Target("creature"),
                    Destination(Zone.Exile)))))),
                Mode(Instructions(
                    targets = listOf(targetOpponent),
                    steps = listOf(
                        Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Enchantment),
                            ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))), min = one(), max = one()),
                            bind = "chosen", chooser = Players.Target("opponent")),
                        Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile))),
                    ),
                )),
            ),
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )

    // CR 614.1a: a replacement created by the spell, for the rest of the turn
    @Test
    fun jasperBarrage() = card(
        name = "Jasper Barrage",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Jasper Barrage deals 4 damage to target creature. If that creature would die this turn, exile it instead.
            • Exile target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(
                    targets = listOf(slot("creature", creature)),
                    steps = listOf(
                        Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), one(4), source = Objects.Self)),
                        Step.Do(Effect.ApplyMechanics(
                            listOf(Mechanic.Replacement(
                                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
                            )),
                            null,
                            Duration.EndOfTurn,
                        )),
                    ),
                )),
                Mode(Instructions(targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact))),
                    steps = listOf(Step.Do(Effect.Move(Objects.Target("artifact"), Destination(Zone.Exile)))))),
            ),
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )

    // CR 701.14a: this creature fights the target
    @Test
    fun quillbackGlutton() = card(
        name = "Quillback Glutton",
        cost = "{3}{G}{G}",
        type = "Creature — Elemental Wolverine",
        pt = "5/4",
        text = """
            When this creature enters, choose one —
            • Put a +1/+1 counter on target creature you control.
            • This creature fights target creature token.
            • Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(modes = Modes(
                options = listOf(
                    Mode(Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(counters(Objects.Target("creature"))))),
                    Mode(Instructions(targets = listOf(slot("token", ObjectFilter.And(listOf(creature, token)))),
                        steps = listOf(Step.Do(Effect.Fight(Objects.Self, Objects.Target("token")))))),
                    Mode(Instructions(targets = listOf(slot("permanent", artifactOrEnchantment)),
                        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))))),
                ),
                min = 1,
                max = 1,
                chooser = Players.You,
            )),
        ),
    )

    @Test
    fun spellwhirl() = card(
        name = "Spellwhirl",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target spell.
            • Surveil 2, then draw two cards. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
                    steps = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))))),
                Mode(steps(Step.Do(Effect.Surveil(Players.You, one(2))), Step.Do(Effect.Draw(Players.You, one(2))))),
            ),
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )
}
