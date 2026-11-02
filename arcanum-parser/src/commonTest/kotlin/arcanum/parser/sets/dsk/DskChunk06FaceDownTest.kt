package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.manifestDread
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 06: face-down permanents (CR 708) and turning them face up: "a face-down permanent you control enters",
 * "is turned face up", "you turn a permanent face up" (CR 708.8); revealing a face-down permanent and turning it face up
 * if it's a creature card; looking at face-down creatures; mana only for turning permanents face up (CR 116.2b); manifest
 * dread X times; Equipment that manifest dread and attach to that creature; "when you sacrifice this enchantment".
 */
class DskChunk06FaceDownTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private val faceDownPermanentYouControl = ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.FaceDown), ObjectFilter.Category(ObjectCategory.Permanent), youControl))
    private val permanentCard = ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
        CardType.Planeswalker).map { ObjectFilter.Type(it) })
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val manifestAndAttach = Ability.Triggered(thisEnters, Instructions(steps = listOf(
        manifestDread(bind = "manifested"),
        Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("manifested")))),
    )))
    private val any = PlayerFilter.You

    @Test
    fun dangersBehindEveryDoor() = card(
        name = "Dangers Behind Every Door",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, manifest dread.
            Whenever a face-down permanent you control enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(manifestDread(bind = "manifested")))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(faceDownPermanentYouControl, to = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))),
                    max = Value.Fixed(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
                Step.Do(Effect.Shuffle(Players.You)),
            )),
        ),
    )

    /** "You may return a permanent card from your graveyard to your hand": a choice of zero or one, not targeted (D49). */
    @Test
    fun beneathTheFlesh() = card(
        name = "Beneath the Flesh",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
            You may return a permanent card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            manifestDread(bind = "manifested"),
            Step.Choose(
                Choice.Objects(ObjectFilter.And(listOf(permanentCard, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                    min = Value.Fixed(0), max = Value.Fixed(1)),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
        ))),
    )

    /** "Those creatures": every creature manifested by the repeated action (CR 701.40e: one at a time). */
    @Test
    fun valgavothsOnrush() = card(
        name = "Valgavoth's Onrush",
        cost = "{X}{X}{G}",
        type = "Sorcery",
        text = "Manifest dread X times, then put X +1/+1 counters on each of those creatures. (To manifest dread, look at the top two cards of your library, then put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Repeat(Value.Announced("X"), listOf(manifestDread(bind = "manifested"))),
                Step.Do(Effect.PutCounters(Objects.Bound("manifested"), plusOne, Value.Announced("X"))),
            ),
            announced = listOf("X"),
        )),
    )

    /** "When you sacrifice this enchantment" (CR 701.21a). */
    @Test
    fun unsettlingGlee() = card(
        name = "Unsettling Glee",
        cost = "{B}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, you may sacrifice another enchantment or creature. If you do, draw two cards.
            When you sacrifice this enchantment, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            Step.Pay(
                cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Enchantment), creature)),
                )), count = Value.Fixed(1))))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(2))))),
        ))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(ObjectFilter.Is(Objects.Self))),
            instructions = Instructions(steps = listOf(manifestDread(bind = "manifested"))),
        ),
    )

    /** "Whenever you turn a permanent face up" (CR 708.8); "it" is that permanent. */
    @Test
    fun swellingDread() = card(
        name = "Swelling Dread",
        cost = "{G}{U}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
            Whenever you turn a permanent face up, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(thisEnters, Instructions(steps = listOf(manifestDread(bind = "manifested")))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.TurnedFaceUp(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), youControl)))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.EventObject(EventRole.Object), plusOne, Value.Fixed(1))))),
        ),
    )

    /** Two events in one triggered ability: entering face down, and being turned face up. */
    @Test
    fun cryptidExaminer() = card(
        name = "Cryptid Examiner",
        cost = "{2}{G}",
        type = "Creature — Elf Warrior",
        pt = "2/3",
        text = """
            Vigilance
            Whenever a face-down permanent you control enters and whenever this creature or another permanent you control is turned face up, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(faceDownPermanentYouControl, to = Zone.Battlefield),
                EventPattern.TurnedFaceUp(ObjectFilter.Or(listOf(
                    ObjectFilter.Is(Objects.Self),
                    ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Category(ObjectCategory.Permanent), youControl)),
                ))),
            )),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, plusOne, Value.Fixed(1))))),
        ),
    )

    /** "If it's a creature card": the card representing the face-down permanent, not its face-down characteristics (CR
     * 708.2a). Turning it face up by an effect (CR 708.8). */
    @Test
    fun hauntwoodsHowler() = card(
        name = "Hauntwoods Howler",
        cost = "{1}{G}{G}",
        type = "Creature — Beast Mutant",
        pt = "3/3",
        text = """
            Whenever this creature attacks, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
            {1}{G}: Reveal target face-down permanent. If it's a creature card, you may turn it face up.
        """,
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(manifestDread(bind = "manifested")))),
        Ability.Activated(
            cost = manaCost("{1}{G}"),
            instructions = Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.FaceDown), ObjectFilter.Category(ObjectCategory.Permanent))))),
                steps = listOf(
                    Step.Do(Effect.Reveal(Objects.Target("permanent"))),
                    Step.If(
                        condition = Condition.ObjectMatches(Objects.Target("permanent"), ObjectFilter.Type(CardType.Creature, ignoringTypeChanges = true)),
                        then = listOf(Step.May(listOf(Step.Do(Effect.TurnFaceUp(Objects.Target("permanent")))), player = Players.You)),
                    ),
                ),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun conductiveCleaver() = card(
        name = "Conductive Cleaver",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, manifest dread, then attach this Equipment to that creature. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
            Equipped creature gets +2/+1.
            Equip {4}
        """,
    ) parsesTo abilities(
        manifestAndAttach,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(1))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Keyword.Equip(manaCost("{4}")),
    )

    /** "Equip—Sacrifice a creature.": an equip cost that isn't mana (CR 702.6a). */
    @Test
    fun autopsyKit() = card(
        name = "Autopsy Kit",
        cost = "{5}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, manifest dread, then attach this Equipment to that creature.
            Equipped creature gets +2/+2 and has deathtouch and lifelink.
            Equip—Sacrifice a creature.
        """,
    ) parsesTo abilities(
        manifestAndAttach,
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)), Mechanic.GainAbilities(listOf(Keyword.Deathtouch, Keyword.Lifelink))),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self)),
        ),
        Keyword.Equip(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = Value.Fixed(1)))))),
    )

    /** Looking at face-down creatures (CR 708.5: their controller may look at them; this lets you too). */
    @Test
    fun lostFootage() = card(
        name = "Lost Footage",
        cost = "{1}",
        type = "Artifact — Clue",
        text = """
            You may look at face-down creatures your opponents control any time.
            {2}, Sacrifice this artifact: Surveil 2, then draw a card. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.MayLookAt(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.HasStatus(Status.FaceDown), creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))))),
            affects = Affected.Players(Players.You),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{2}")!!), CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Surveil(Players.You, Value.Fixed(2))),
                Step.Do(Effect.Draw(Players.You, Value.Fixed(1))),
            )),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun overgrownDevotee() = card(
        name = "Overgrown Devotee",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        pt = "0/4",
        text = """
            {T}: Add one mana of any color.
            {T}: Add two mana of any one color. Spend this mana only to turn permanents face up.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(
                ManaProduction(anyOneColor(2), restriction = ObjectFilter.AbilityOf(kind = AbilityKind.TurnFaceUp)), Players.You)))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    /** "You may draw a card. If you do, discard a card unless …": the draw is a cost paid on resolution (CR 118.12, D40). */
    @Test
    fun absentMindedBookworm() = card(
        name = "Absent-Minded Bookworm",
        cost = "{G}{U}",
        type = "Creature — Human Wizard",
        pt = "2/3",
        text = "At the beginning of your end step, you may draw a card. If you do, discard a card unless a permanent entered the battlefield face down under your control this turn or you turned a permanent face up this turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
            instructions = Instructions(steps = listOf(
                Step.Pay(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.If(
                    condition = Condition.Not(Condition.Or(listOf(
                        Condition.HappenedThisTurn(
                            EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), ObjectFilter.HasStatus(Status.FaceDown), youControl)),
                                to = Zone.Battlefield),
                            atLeast = 1, measure = Measure.Events,
                        ),
                        Condition.HappenedThisTurn(
                            EventPattern.TurnedFaceUp(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), youControl))),
                            atLeast = 1, measure = Measure.Events,
                        ),
                    ))),
                    then = listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any))),
                ))),
            )),
        ),
    )
}
