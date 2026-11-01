package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Comparison
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 15: spells that return permanents from the graveyard (all of a kind, "those creatures" exiled by a delayed
 * trigger, cards "put there from the battlefield this turn"), turn-long triggers that draw or add mana, digging for
 * creatures, an extra turn, an instant whose triggered ability returns it from the graveyard, and tapping or untapping
 * all permanents of a chosen card type.
 */
class Chunk15ReturnsTriggersAndTurnsTest {
    // ---- Shared shapes

    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))

    /** "that were put there from the battlefield this turn" */
    private val putThereFromBattlefieldThisTurn = ObjectFilter.ThisTurn(EventPattern.ZoneChange(
        from = Zone.Battlefield,
        to = Zone.Graveyard,
        objects = ObjectFilter.Any,
    ))

    /** "Permanent card" (CR 110.4a). */
    private val permanentCard = ObjectFilter.Or(listOf(
        ObjectFilter.Type(CardType.Artifact),
        ObjectFilter.Type(CardType.Battle),
        ObjectFilter.Type(CardType.Creature),
        ObjectFilter.Type(CardType.Enchantment),
        ObjectFilter.Type(CardType.Land),
        ObjectFilter.Type(CardType.Planeswalker),
    ))

    private fun drawCards(n: Int) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))

    // ---- From the graveyard to the battlefield

    /** CR 603.7: "at the beginning of your next upkeep" triggers once; the returned creatures are fixed when it is
     * created (CR 603.7c). */
    @Test
    fun musterTheForebears() = card(
        name = "Muster the Forebears",
        cost = "{X}{W}{W}",
        type = "Instant",
        text = "Return each creature card with mana value X or less from your graveyard to the battlefield. Exile those creatures at the beginning of your next upkeep. Exile Muster the Forebears.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            steps = listOf(
                Step.Do(Effect.Move(
                    Objects.Each(ObjectFilter.And(listOf(
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X")),
                    ) + yourGraveyard)),
                    to = Destination(Zone.Battlefield),
                    postBind = "returned",
                )),
                Step.Do(Effect.CreateDelayedTrigger(
                    trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                    instructions = Instructions(steps = listOf(
                        Step.Do(Effect.Move(Objects.Bound("returned"), to = Destination(Zone.Exile))),
                    )),
                )),
                Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Exile))),
            ),
        )),
    )

    @Test
    fun hauledBack() = card(
        name = "Hauled Back",
        cost = "{W}{W}",
        type = "Instant",
        text = "Choose up to two target permanent cards in your graveyard that were put there from the battlefield this turn. Return them to the battlefield tapped.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "cards",
                what = TargetKind.Objects(ObjectFilter.And(listOf(permanentCard) + yourGraveyard + putThereFromBattlefieldThisTurn)),
                min = Value.Fixed(0),
                max = Value.Fixed(2),
            )),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("cards"), to = Destination(Zone.Battlefield, tapped = true))),
            ),
        )),
    )

    @Test
    fun devotionsReward() = card(
        name = "Devotion's Reward",
        cost = "{3}{W}",
        type = "Instant",
        text = "Return to the battlefield all permanent cards in your graveyard that were put there from the battlefield this turn.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Move(
                Objects.Each(ObjectFilter.And(listOf(permanentCard) + yourGraveyard + putThereFromBattlefieldThisTurn)),
                to = Destination(Zone.Battlefield),
            )),
        ))),
    )

    /** Each player puts their own cards onto the battlefield, so each card enters under its owner's control
     * (CR 110.2a). */
    @Test
    fun secondDawning() = card(
        name = "Second Dawning",
        cost = "{1}{W}{W}",
        type = "Instant",
        text = "Each player returns to the battlefield all artifact, creature, enchantment, and land cards in their graveyard that were put there from the battlefield this turn.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Move(
                returnedBySecondDawning,
                to = Destination(Zone.Battlefield, controller = Players.OwnerOf(returnedBySecondDawning)),
            )),
        ))),
    )

    private val returnedBySecondDawning = Objects.Each(ObjectFilter.And(listOf(
        ObjectFilter.Or(listOf(
            ObjectFilter.Type(CardType.Artifact),
            ObjectFilter.Type(CardType.Creature),
            ObjectFilter.Type(CardType.Enchantment),
            ObjectFilter.Type(CardType.Land),
        )),
        ObjectFilter.InZone(Zone.Graveyard),
        putThereFromBattlefieldThisTurn,
    )))

    // ---- Turn-long triggers

    /** CR 603.7b: a delayed trigger "this turn" triggers every time until the turn ends. */
    @Test
    fun riteOfConcord() = card(
        name = "Rite of Concord",
        cost = "{G}{W}",
        type = "Instant",
        text = """
            Whenever a creature or enchantment you control enters this turn, draw a card.
            Flashback {2}{G}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.ZoneChange(
                    objects = ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.Type(CardType.Enchantment))),
                        ObjectFilter.ControlledBy(PlayerFilter.You),
                    )),
                    to = Zone.Battlefield,
                )),
                instructions = Instructions(steps = listOf(drawCards(1))),
                duration = Duration.EndOfTurn,
                repeats = true,
            )),
        ))),
        Keyword.Flashback(manaCost("{2}{G}{W}")),
    )

    /** "That player" is whoever tapped the Island, its controller; the trigger is a mana ability (CR 605.1b). */
    @Test
    fun swellingTide() = card(
        name = "Swelling Tide",
        cost = "{U}",
        type = "Instant",
        text = "Until end of turn, whenever a player taps an Island for mana, that player adds an additional {U}.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.Tap(ObjectFilter.Sub(Subtype("Island")), forMana = true)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.AddMana(
                        mana = ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)),
                        players = Players.ControllerOf(Objects.EventObject(role = EventRole.Object)),
                    )),
                )),
                duration = Duration.EndOfTurn,
                repeats = true,
            )),
        ))),
    )

    /** The trigger works from the graveyard because it moves this card out of it (CR 113.6k). */
    @Test
    fun chasteningFlame() = card(
        name = "Chastening Flame",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Chastening Flame deals 2 damage to any target.
            Whenever an opponent gains life, you may pay {R}. If you do, return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "any",
                what = TargetKind.Either(
                    objects = ObjectFilter.Or(listOf(
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.Type(CardType.Planeswalker),
                        ObjectFilter.Type(CardType.Battle),
                    )),
                    players = PlayerFilter.Any,
                ),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(Step.Do(Effect.DealDamage(
                Recipients(target = "any"),
                amount = Value.Fixed(2),
                source = Objects.Self,
            ))),
        )),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.GainLife(PlayerFilter.Opponent)),
            instructions = Instructions(steps = listOf(
                Step.Pay(cost = manaCost("{R}"), bind = "paid", payer = Players.You),
                Step.If(
                    condition = Condition.Did("paid"),
                    then = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Hand)))),
                ),
            )),
        ),
    )

    // ---- Digging and turns

    @Test
    fun gatheredFellowship() = card(
        name = "Gathered Fellowship",
        cost = "{3}{G}",
        type = "Instant",
        text = "Look at the top six cards of your library. Put up to two creature cards with mana value 3 or less from among them onto the battlefield. Put the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.LookAt(
                Objects.LibraryCards(Players.You, count = Value.Fixed(6), fromTop = true),
                bind = "seen",
                players = Players.You,
            )),
            Step.Choose(
                choice = Choice.Objects(
                    filter = ObjectFilter.And(listOf(
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                    )),
                    min = Value.Fixed(0),
                    max = Value.Fixed(2),
                    from = Objects.Bound("seen"),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Battlefield))),
            Step.Do(Effect.Move(
                Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                to = Destination(Zone.Library, libraryPosition = -1),
            )),
        ))),
    )

    @Test
    fun fleetingWhim() = card(
        name = "Fleeting Whim",
        cost = "{1}{U}",
        type = "Instant",
        text = "Look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.LookAt(
                Objects.LibraryCards(Players.You, count = Value.Fixed(4), fromTop = true),
                bind = "seen",
                players = Players.You,
            )),
            Step.Choose(
                choice = Choice.Objects(ObjectFilter.Any, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Bound("seen")),
                bind = "kept",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("kept"), to = Destination(Zone.Hand))),
            Step.Do(Effect.Move(
                Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")),
                to = Destination(Zone.Library, libraryPosition = -1),
            )),
        ))),
    )

    /** CR 500.7: the extra turn comes directly after this one. */
    @Test
    fun timeStride() = card(
        name = "Time Stride",
        cost = "{1}{U}",
        type = "Sorcery",
        text = "Take an extra turn after this one.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.ExtraTurn(Players.You))))),
    )

    // ---- Tapping or untapping a chosen card type

    /** "permanents of the chosen type target player controls" with [status] (D55: a card type referenced like a
     * subtype). */
    private fun ofTheChosenType(status: ObjectFilter) = Objects.Each(ObjectFilter.And(listOf(
        ObjectFilter.Category(ObjectCategory.Permanent),
        ObjectFilter.Type(CardTypeRef.Chosen("type")),
        status,
        ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))),
    )))

    /** The card type and whether to tap or untap are chosen as it resolves (CR 608.2d); the player is targeted on
     * casting. */
    @Test
    fun tidecallReversal() = card(
        name = "Tidecall Reversal",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = "Choose artifact, creature, or land. Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents of that type that player controls.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "player",
                what = TargetKind.Players(PlayerFilter.Any),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(
                Step.Choose(
                    choice = Choice.CardType(among = listOf(CardType.Artifact, CardType.Creature, CardType.Land)),
                    bind = "type",
                    chooser = Players.You,
                ),
                Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(
                    condition = Condition.Chose("tapOrUntap", "tap"),
                    then = listOf(Step.Do(Effect.Tap(ofTheChosenType(ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped)))))),
                    otherwise = listOf(Step.Do(Effect.Untap(ofTheChosenType(ObjectFilter.HasStatus(Status.Tapped))))),
                ),
            ),
        )),
    )
}
