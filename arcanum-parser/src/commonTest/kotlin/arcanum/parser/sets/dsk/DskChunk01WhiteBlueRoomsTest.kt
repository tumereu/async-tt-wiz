package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.door
import arcanum.parser.sets.dsk.Dsk.glimmer
import arcanum.parser.sets.dsk.Dsk.manifestDread
import arcanum.parser.sets.dsk.Dsk.room
import arcanum.parser.sets.dsk.Dsk.roomsYouControl
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.whenYouUnlockThisDoor
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 01: white and blue Rooms (CR 709.5), each a split card whose halves share the type line "Enchantment —
 * Room". Doors whose abilities trigger "when you unlock this door" (CR 709.5h); static doors (convoke for creature spells,
 * untapping during other players' untap steps, base P/T, hexproof, triggering an additional time); a door that wins the
 * game by the names among unlocked doors; searching for a Room by name; manifesting dread (CR 701.62a).
 */
class DskChunk01WhiteBlueRoomsTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private fun counters(objects: Objects, n: Int, kind: CounterKind = plusOne) = Step.Do(Effect.PutCounters(objects, kind, Value.Fixed(n)))
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))

    /** "Creature spells you cast have convoke": a spell's controller is the player who cast it (CR 112.2). Untapping
     * during other players' untap steps (CR 502.3). */
    @Test
    fun gildedPlayhouseCostumeLoft() = room(
        door(
            name = "Gilded Playhouse",
            cost = "{3}{W}",
            text = "Creature spells you cast have convoke. (Your creatures can help cast those spells. Each creature you tap while casting a creature spell pays for {1} or one mana of that creature's color.)",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Convoke))),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Spell), youControl)))),
            ),
        ),
        door(
            name = "Costume Loft",
            cost = "{2}{W}",
            text = "Untap each creature you control during each other player's untap step.",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.UntapsDuringOtherUntapSteps),
                affects = Affected.Objects(Objects.Each(creatureYouControl)),
            ),
        ),
    )

    /** "Whenever one or more … attack a player": once per player attacked (CR 603.2c by wording, 508.3a). Base power and
     * toughness set by a count (layer 7b, CR 613.4b). */
    @Test
    fun toymakersStallBisqueParlor() = room(
        door(
            name = "Toymaker's Stall",
            cost = "{1}{W}",
            text = "Whenever one or more non-Toy creatures you control attack a player, create a 1/1 white Toy artifact creature token.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(
                    events = listOf(EventPattern.Attack(
                        attacker = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Sub(Subtype("Toy"))), creature, youControl)),
                        target = RecipientFilter(players = PlayerFilter.Any),
                    )),
                    oneOrMore = true,
                    batchPerPlayer = true,
                ),
                instructions = Instructions(steps = listOf(
                    create(token(listOf("Toy"), listOf(Color.White), 1, 1, listOf(CardType.Artifact, CardType.Creature))),
                )),
            ),
        ),
        door(
            name = "Bisque Parlor",
            cost = "{4}{W}{W}",
            text = "Creatures you control have base power and toughness each equal to the number of creatures you control.",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.SetPowerToughness(
                    power = Value.Count(Objects.Each(creatureYouControl)),
                    toughness = Value.Count(Objects.Each(creatureYouControl)),
                )),
                affects = Affected.Objects(Objects.Each(creatureYouControl)),
            ),
        ),
    )

    @Test
    fun grandVestibuleGleamingAtrium() = room(
        door(
            name = "Grand Vestibule",
            cost = "{1}{W}",
            text = "When you unlock this door, create a 1/1 white Glimmer enchantment creature token.",
        ) to abilities(
            whenYouUnlockThisDoor(create(glimmer)),
        ),
        door(
            name = "Gleaming Atrium",
            cost = "{2}{W}",
            text = "When you unlock this door, put a +1/+1 counter on each of up to two target creatures.",
        ) to abilities(
            whenYouUnlockThisDoor(
                counters(Objects.Target("creatures"), 1),
                targets = listOf(slot("creatures", creature, min = 0, max = 2)),
            ),
        ),
    )

    @Test
    fun operatingTheaterRecoveryWard() = room(
        door(
            name = "Operating Theater",
            cost = "{1}{W}",
            text = "When you unlock this door, return target creature card with mana value 3 or less from your graveyard to the battlefield.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield))),
                targets = listOf(slot("card", ObjectFilter.And(listOf(
                    creature,
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                )))),
            ),
        ),
        door(
            name = "Recovery Ward",
            cost = "{3}{W}",
            text = "Whenever you attack, put a +1/+1 counter on target attacking creature.",
        ) to abilities(
            Ability.Triggered(
                // CR 508.3d: once when one or more creatures you control are declared as attackers
                trigger = Trigger.On(EventPattern.Attack(youControl), oneOrMore = true),
                instructions = Instructions(
                    targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature)))),
                    steps = listOf(counters(Objects.Target("creature"), 1)),
                ),
            ),
        ),
    )

    /** "One or more creatures you control deal combat damage to a player": one trigger per player dealt damage. */
    @Test
    fun sunkenBasinChangingRoom() = room(
        door(
            name = "Sunken Basin",
            cost = "{U}",
            text = "When you unlock this door, return up to one target creature to its owner's hand.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand))),
                targets = listOf(slot("creature", creature, min = 0)),
            ),
        ),
        door(
            name = "Changing Room",
            cost = "{4}{U}",
            text = "Whenever one or more creatures you control deal combat damage to a player, draw a card.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(
                    events = listOf(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)),
                    oneOrMore = true,
                    batchPerPlayer = true,
                ),
                instructions = Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
            ),
        ),
    )

    /** A Room card has both halves' names (CR 709.4a); a Room on the battlefield only its unlocked doors' (709.5), so the
     * names among unlocked doors are the names among the Rooms. */
    @Test
    fun rattlingLiftHopefulStaircase() = room(
        door(
            name = "Rattling Lift",
            cost = "{3}{U}",
            text = "When you unlock this door, search your library for a Room card that doesn't have the same name as a Room you control, reveal it, put it into your hand, then shuffle.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Search(
                    library = Players.You,
                    filter = ObjectFilter.And(listOf(
                        ObjectFilter.Sub(Subtype("Room")),
                        ObjectFilter.Not(ObjectFilter.SameNameAs(Objects.Each(roomsYouControl))),
                    )),
                    max = Value.Fixed(1),
                    reveal = true,
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                )),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.You)),
            ),
        ),
        door(
            name = "Hopeful Staircase",
            cost = "{2}{U}",
            text = "At the beginning of your upkeep, surveil 1. You win the game if there are eight or more different names among unlocked doors of Rooms you control.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.Surveil(Players.You, Value.Fixed(1))),
                    Step.If(
                        condition = Condition.Compare(
                            Value.CountDistinct(Objects.Each(roomsYouControl), SharedQuality.Name),
                            Comparison.AtLeast,
                            Value.Fixed(8),
                        ),
                        then = listOf(Step.Do(Effect.WinGame(Players.You))),
                    ),
                )),
            ),
        ),
    )

    /** Stun counters (CR 122.1d). */
    @Test
    fun butchersFreezerFloodedKitchen() = room(
        door(
            name = "Butcher's Freezer",
            cost = "{2}{U}",
            text = "When you unlock this door, tap up to one target creature and put two stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Tap(Objects.Target("creature"))),
                counters(Objects.Target("creature"), 2, CounterKind.Stun),
                targets = listOf(slot("creature", creature, min = 0)),
            ),
        ),
        door(
            name = "Flooded Kitchen",
            cost = "{3}{U}{U}",
            text = "When you unlock this door, draw three cards, then discard a card.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Draw(Players.You, Value.Fixed(3))),
                Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any)),
            ),
        ),
    )

    /** A copy exception (CR 707.9b); "triggers an additional time" for any event (CR 603.2d). */
    @Test
    fun lookingGlassHallSplinteredWing() = room(
        door(
            name = "Looking-Glass Hall",
            cost = "{2}{U}",
            text = "When you unlock this door, create a token that's a copy of target creature you control, except it's a Reflection in addition to its other creature types.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.CreateToken(
                    TokenSpec.CopyOf(Objects.Target("creature"), except = listOf(CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Reflection")))))),
                    count = Value.Fixed(1),
                    controller = Players.You,
                )),
                targets = listOf(slot("creature", creatureYouControl)),
            ),
        ),
        door(
            name = "Splintered Wing",
            cost = "{5}{U}{U}",
            text = "If a triggered ability of a permanent you control triggers, that ability triggers an additional time.",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.TriggersAdditionalTime(
                    event = null,
                    sources = ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), youControl)),
                )),
                affects = Affected.Objects(Objects.Self),
            ),
        ),
    )

    /** "Manifest dread, then put a +1/+1 counter on that creature": the manifested permanent (CR 701.62a). */
    @Test
    fun drownedPassageMurkyTank() = room(
        door(
            name = "Drowned Passage",
            cost = "{U}",
            text = "When you unlock this door, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
        ) to abilities(
            whenYouUnlockThisDoor(Step.Do(Effect.Surveil(Players.You, Value.Fixed(2)))),
        ),
        door(
            name = "Murky Tank",
            cost = "{3}{U}",
            text = "When you unlock this door, manifest dread, then put a +1/+1 counter on that creature.",
        ) to abilities(
            whenYouUnlockThisDoor(
                manifestDread(bind = "manifested"),
                counters(Objects.Bound("manifested"), 1),
            ),
        ),
    )

    @Test
    fun offLimitsStudySeminarChamber() = room(
        door(
            name = "Off-Limits Study",
            cost = "{2}{W}{W}",
            text = "When you unlock this door, destroy all creatures with power 3 or greater.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Destroy(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Power(Comparison.AtLeast, Value.Fixed(3))))))),
            ),
        ),
        door(
            name = "Seminar Chamber",
            cost = "{5}{U}{U}",
            text = "Other permanents you control have hexproof.",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Hexproof))),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Category(ObjectCategory.Permanent),
                    youControl,
                )))),
            ),
        ),
    )
}
