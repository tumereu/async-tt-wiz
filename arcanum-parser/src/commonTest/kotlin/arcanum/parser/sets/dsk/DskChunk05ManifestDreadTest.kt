package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.manifestDread
import kotlin.test.Test

/**
 * Duskmourn chunk 05: manifest dread (CR 701.62a: look at the top two cards, manifest one, CR 701.40a, put the other into
 * the graveyard) by you or by another player ("its controller manifests dread", "the exiled card's owner"), as a spell, a
 * mode, a triggered ability, a delayed trigger and an activated ability; "manifest dread, then attach this Equipment to
 * that creature"; "whenever you manifest dread" and the card put into the graveyard that way (701.62b); an Aura that keeps
 * a face-down creature from being turned face up.
 */
class DskChunk05ManifestDreadTest {
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val dies = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private fun modes(vararg options: Mode) = Ability.Spell(Instructions(modes = Modes(options.toList(), min = 1, max = 1, chooser = Players.You)))
    private val counterTargetSpell = Mode(Instructions(
        targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
        steps = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))),
    ))
    private val manifestDreadMode = Mode(Instructions(steps = listOf(manifestDread(bind = "manifested"))))

    /** "The exiled card's owner": the card the ability linked to this Vehicle exiled (CR 607.2a). */
    @Test
    fun unknownSkimmer() = card(
        name = "Unknown Skimmer",
        cost = "{1}{W}{W}",
        type = "Artifact — Vehicle",
        pt = "2/2",
        text = """
            Flying
            When this Vehicle enters, exile up to one target creature with toughness 5 or less.
            When this Vehicle leaves the battlefield, the exiled card's owner manifests dread.
            Crew 1
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Toughness(Comparison.AtMost, Value.Fixed(5)))), min = 0)),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(manifestDread(Players.OwnerOf(Objects.Remembered("exiled"))))),
        ),
        Keyword.Crew(1),
    )

    @Test
    fun uncannyTwins() = card(
        name = "Uncanny Twins",
        cost = "{3}{W}",
        type = "Creature — Human",
        pt = "2/2",
        text = "When this creature enters, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(manifestDread(bind = "manifested")))),
    )

    /** "Its controller" is the destroyed creature's, as it last existed (CR 608.2h). */
    @Test
    fun unwelcomeRedo() = card(
        name = "Unwelcome Redo",
        cost = "{W}",
        type = "Instant",
        text = "Destroy target creature. Its controller manifests dread. (That player looks at the top two cards of their library, then puts one onto the battlefield face down as a 2/2 creature and the other into their graveyard. If it's a creature card, it can be turned face up any time for its mana cost.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("creature"))),
            manifestDread(Players.ControllerOf(Objects.Target("creature"))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun loathsomeEye() = card(
        name = "Loathsome Eye",
        cost = "{2}{U}",
        type = "Creature — Eye",
        pt = "5/5",
        text = """
            As an additional cost to cast this spell, exile six cards from your graveyard.
            Flying
            At the beginning of each opponent's upkeep, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(
                CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(6)), from = Zone.Graveyard))))),
            affects = Affected.Objects(Objects.Self),
        ),
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.Opponent)),
            instructions = Instructions(steps = listOf(manifestDread(bind = "manifested"))),
        ),
    )

    /** "Manifest dread, then attach this Equipment to that creature" (CR 701.3). */
    @Test
    fun hexedWindcoat() = card(
        name = "Hexed Windcoat",
        cost = "{2}{U}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, manifest dread, then attach this Equipment to that creature. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
            Equipped creature has flying.
            Equip {3}
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            manifestDread(bind = "manifested"),
            Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("manifested")))),
        ))),
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Flying))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun dreadOfDoubles() = card(
        name = "Dread of Doubles",
        cost = "{1}{U}{U}",
        type = "Enchantment Creature — Nightmare",
        pt = "3/2",
        text = """
            Flash
            When this creature enters, counter target spell. Its controller manifests dread. (That player looks at the top two cards of their library, then puts one onto the battlefield face down as a 2/2 creature and the other into their graveyard. If it's a creature card, it can be turned face up any time for its mana cost.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
            steps = listOf(
                Step.Do(Effect.Counter(Objects.Target("spell"))),
                manifestDread(Players.ControllerOf(Objects.Target("spell"))),
            ),
        )),
    )

    /** CR 701.62b: the trigger sees the card put into the graveyard by the manifest dread (its "rest"). */
    @Test
    fun occultAnalyst() = card(
        name = "Occult Analyst",
        cost = "{1}{U}",
        type = "Creature — Human Detective",
        pt = "1/3",
        text = "Whenever you manifest dread, put a card you put into your graveyard this way into your hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ManifestDread(PlayerFilter.You)),
            instructions = Instructions(steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.Any, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.EventObject(EventRole.Rest)),
                    bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
            )),
        ),
    )

    @Test
    fun keepQuietKeepHidden() = card(
        name = "Keep Quiet, Keep Hidden",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
            {4}{U}{U}: Shuffle enchanted creature into its owner's library, then manifest dread. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Triggered(thisEnters, Instructions(steps = listOf(Step.Do(Effect.Tap(enchanted))))),
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(enchanted)),
        Ability.Activated(
            cost = manaCost("{4}{U}{U}"),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Move(enchanted, Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(Players.OwnerOf(enchanted))),
                manifestDread(bind = "manifested"),
            )),
            rules = arcanum.api.ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun wrenchedReality() = card(
        name = "Wrenched Reality",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target spell.
            • Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        """,
    ) parsesTo abilities(modes(counterTargetSpell, manifestDreadMode))

    /** Losing all abilities, becoming a Toy artifact creature, base P/T (layers 4, 6, 7b, CR 613.1); "can't be turned face
     * up" while it is face down. */
    @Test
    fun tooScaredToScream() = card(
        name = "Too Scared to Scream",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature loses all abilities and is a Toy artifact creature with base power and toughness 0/2 in addition to its other types.
            As long as enchanted creature is face down, it can't be turned face up.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(
            mechanics = listOf(
                Mechanic.LoseAbilities(),
                Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Toy")))),
                Mechanic.SetPowerToughness(Value.Fixed(0), Value.Fixed(2)),
            ),
            affects = Affected.Objects(enchanted),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.CantBeTurnedFaceUp),
            affects = Affected.Objects(enchanted),
            condition = Condition.ObjectMatches(enchanted, ObjectFilter.HasStatus(Status.FaceDown)),
        ),
    )

    @Test
    fun unnervingClutch() = card(
        name = "Unnerving Clutch",
        cost = "{2}{U}",
        type = "Sorcery",
        text = "Return up to one target nonland permanent to its owner's hand. Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Hand))),
            manifestDread(bind = "manifested"),
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), ObjectFilter.Category(ObjectCategory.Permanent))), min = 0)),
        ),
    )

    @Test
    fun harmlessRat() = card(
        name = "Harmless Rat",
        cost = "{1}{B}",
        type = "Creature — Rat",
        pt = "1/1",
        text = "When this creature dies, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(Ability.Triggered(dies, Instructions(steps = listOf(manifestDread(bind = "manifested")))))

    @Test
    fun slayersVisage() = card(
        name = "Slayer's Visage",
        cost = "{2}{B}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, manifest dread, then attach this Equipment to that creature. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
            Equipped creature has menace.
            Equip {2}
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            manifestDread(bind = "manifested"),
            Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("manifested")))),
        ))),
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Menace))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Keyword.Equip(manaCost("{2}")),
    )

    /** "When it dies this turn": a delayed trigger about the target, for the turn, triggering once (CR 603.7b, c). */
    @Test
    fun flipInsideOut() = card(
        name = "Flip Inside Out",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +3/+0 until end of turn. When it dies this turn, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(3), Value.Fixed(0))),
                Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn)),
            Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Target("creature")), from = Zone.Battlefield, to = Zone.Graveyard)),
                instructions = Instructions(steps = listOf(manifestDread(bind = "manifested"))),
                duration = Duration.EndOfTurn,
            )),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun bashfulBehemoth() = card(
        name = "Bashful Behemoth",
        cost = "{4}{G}",
        type = "Creature — Beast",
        pt = "5/4",
        text = "When this creature dies, manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(Ability.Triggered(dies, Instructions(steps = listOf(manifestDread(bind = "manifested")))))

    @Test
    fun smashThroughTheDoor() = card(
        name = "Smash Through the Door",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Exile target artifact.
            • Exile target enchantment.
            • Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)
        """,
    ) parsesTo abilities(
        modes(
            Mode(Instructions(targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("artifact"), Destination(Zone.Exile)))))),
            Mode(Instructions(targets = listOf(slot("enchantment", ObjectFilter.Type(CardType.Enchantment))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("enchantment"), Destination(Zone.Exile)))))),
            manifestDreadMode,
        ),
    )

    @Test
    fun summonTheDread() = card(
        name = "Summon the Dread",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Manifest dread. (Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(spell(manifestDread(bind = "manifested")))
}
