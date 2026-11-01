package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 42: creatures whose main text is an activated ability paid for by sacrificing, discarding or tapping other
 * permanents: tutors that put a card onto the battlefield or into hand, token makers, life and cards for sacrifices,
 * an "any number of target players" edict, a Clue-paid reveal; with them the graveyard-counting bodies ("gets +1/+1 for
 * each creature card in your graveyard") and the "whenever you sacrifice" triggers.
 */
class Chunk42SacrificeEnginesAndTutorCreaturesTest {
    // ---- Shared shapes

    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private val creature = ObjectFilter.Type(CardType.Creature)
    private val land = ObjectFilter.Type(CardType.Land)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val notSelf = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private fun draw(n: Int) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))
    private fun gainLife(n: Int) = Step.Do(Effect.GainLife(Players.You, Value.Fixed(n)))

    private fun activated(
        vararg cost: CostPart,
        steps: List<Step>,
        targets: List<TargetSlot> = emptyList(),
        rules: ActivationRules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
    ) =
        Ability.Activated(
            cost = Cost(cost.toList()),
            instructions = Instructions(targets = targets, steps = steps),
            rules = rules,
        )

    private fun sacrifice(filter: ObjectFilter, count: Value = Value.Fixed(1)) = CostPart.Sacrifice(CostObjects.Choose(filter, count = count))

    /** Cards of [kind] in your graveyard. */
    private fun inYourGraveyard(kind: ObjectFilter) = ObjectFilter.And(listOf(kind, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))

    /** "This creature gets +1/+1 for each [kind] card in your graveyard." (layer 7c, CR 613.4c) */
    private fun getsForEachInGraveyard(kind: ObjectFilter) = Value.Count(Objects.Each(inYourGraveyard(kind))).let { count ->
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(count, count)), affects = Affected.Objects(Objects.Self))
    }

    /** "Search your library for a [filter] card, put it [to], then shuffle." */
    private fun search(filter: ObjectFilter, to: Destination, reveal: Boolean = false) = listOf(
        Step.Do(Effect.Search(
            Players.You,
            filter,
            reveal = reveal,
            bind = "found",
            max = Value.Fixed(1),
            zones = listOf(Zone.Library),
            searcher = Players.You,
        )),
        Step.Do(Effect.Move(Objects.Bound("found"), to = to)),
        Step.Do(Effect.Shuffle(Players.You)),
    )

    private fun creatureToken(subtype: String, power: Int, toughness: Int, color: Color) = TokenSpec.Defined(FaceDefinition(
        name = "$subtype Token",
        colorIndicator = listOf(color),
        types = listOf(CardType.Creature),
        subtypes = listOf(Subtype(subtype)),
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
    ))

    /** "Whenever you sacrifice [kind]": only a permanent's controller can sacrifice it (CR 701.21a). */
    private fun youSacrifice(kind: ObjectFilter) = Trigger.On(EventPattern.Sacrifice(ObjectFilter.And(listOf(kind, youControl))))

    // ---- Tutors

    @Test
    fun wildlifeShaman() = card(
        name = "Wildlife Shaman",
        cost = "{1}{G}",
        type = "Creature — Elf Shaman",
        pt = "2/2",
        text = "{G}, {T}, Discard a creature card: Search your library for a creature card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        activated(
            mana("{G}"), CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(creature, count = Value.Fixed(1))),
            steps = search(creature, to = Destination(Zone.Hand), reveal = true),
        ),
    )

    @Test
    fun elvishSalvager() = card(
        name = "Elvish Salvager",
        cost = "{G}",
        type = "Creature — Elf Warrior",
        pt = "1/2",
        text = """
            This creature gets +2/+2 as long as there are three or more land cards in your graveyard.
            {2}, {T}, Sacrifice a land: Search your library for a land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2))),
            condition = Condition.Exists(inYourGraveyard(land), atLeast = 3),
            affects = Affected.Objects(Objects.Self),
        ),
        activated(
            mana("{2}"), CostPart.TapSelf, sacrifice(land),
            steps = search(land, to = Destination(Zone.Battlefield, tapped = true)),
        ),
    )

    @Test
    fun knightOfTheRelicVault() = card(
        name = "Knight of the Relic Vault",
        cost = "{1}{G}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            This creature gets +1/+1 for each land card in your graveyard.
            {T}, Sacrifice a Forest or Plains: Search your library for a land card, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        getsForEachInGraveyard(land),
        activated(
            CostPart.TapSelf, sacrifice(ObjectFilter.Or(listOf(ObjectFilter.Sub(Subtype("Forest")), ObjectFilter.Sub(Subtype("Plains"))))),
            steps = search(land, to = Destination(Zone.Battlefield)),
        ),
    )

    @Test
    fun wightOfTheRelicVault() = card(
        name = "Wight of the Relic Vault",
        cost = "{B}{G}",
        type = "Creature — Zombie Knight",
        pt = "2/2",
        text = """
            Vigilance
            This creature gets +1/+1 for each creature card in your graveyard.
            {T}, Sacrifice another creature: Search your library for a land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        getsForEachInGraveyard(creature),
        activated(
            CostPart.TapSelf, sacrifice(ObjectFilter.And(listOf(notSelf, creature))),
            steps = search(land, to = Destination(Zone.Battlefield, tapped = true)),
        ),
    )

    // CR 107.3a: X in the activation cost is announced as the ability is activated
    @Test
    fun fiendSculptor() = card(
        name = "Fiend Sculptor",
        cost = "{B/G}{B/G}",
        type = "Creature — Nightmare",
        pt = "1/1",
        text = """
            This creature gets +1/+1 for each creature card in your graveyard.
            {X}{B/G}, {T}, Sacrifice another creature: Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        getsForEachInGraveyard(creature),
        Ability.Activated(
            cost = Cost(listOf(mana("{X}{B/G}"), CostPart.TapSelf, sacrifice(ObjectFilter.And(listOf(notSelf, creature))))),
            instructions = Instructions(
                steps = search(
                    ObjectFilter.And(listOf(
                        creature,
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X")),
                    )),
                    to = Destination(Zone.Battlefield),
                ),
                announced = listOf("X"),
            ),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    // ---- Sacrifice and discard engines

    // CR 302.6: only {T} and {Q} costs need a creature free of summoning sickness; tapping untapped Zombies doesn't
    @Test
    fun tombcracker() = card(
        name = "Tombcracker",
        cost = "{B}",
        type = "Creature — Zombie",
        pt = "1/1",
        text = """
            {1}{B}, {T}, Discard a card: Create a 2/2 black Zombie creature token.
            Tap three untapped Zombies you control: You draw a card and lose 1 life.
        """,
    ) parsesTo abilities(
        activated(
            mana("{1}{B}"), CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(
                ObjectFilter.Category(ObjectCategory.Card),
                count = Value.Fixed(1),
            )),
            steps = listOf(Step.Do(Effect.CreateToken(
                creatureToken("Zombie", 2, 2, Color.Black),
                count = Value.Fixed(1),
                controller = Players.You,
            ))),
        ),
        activated(
            CostPart.TapObjects(CostObjects.Choose(ObjectFilter.Sub(Subtype("Zombie")), count = Value.Fixed(3))),
            steps = listOf(draw(1), Step.Do(Effect.LoseLife(Players.You, Value.Fixed(1)))),
        ),
    )

    // CR 122.1d: a stun counter replaces the next untap; CR 614.1c: "enters tapped with" changes how it enters
    @Test
    fun balothSovereign() = card(
        name = "Baloth Sovereign",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "10/10",
        text = """
            This creature enters tapped with six stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Whenever you sacrifice a land, create a tapped 4/4 green Beast creature token and untap this creature.
            {4}, Sacrifice a land: You gain 2 life.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped, Modification.EntersWithCounters(CounterKind.Stun, Value.Fixed(6))),
        )), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = youSacrifice(land),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.CreateToken(
                    creatureToken("Beast", 4, 4, Color.Green),
                    tapped = true,
                    count = Value.Fixed(1),
                    controller = Players.You,
                )),
                Step.Do(Effect.Untap(Objects.Self)),
            )),
        ),
        activated(mana("{4}"), sacrifice(land), steps = listOf(gainLife(2))),
    )

    @Test
    fun famishedSquirrel() = card(
        name = "Famished Squirrel",
        cost = "{B/G}",
        type = "Creature — Squirrel",
        pt = "1/1",
        text = """
            Whenever you sacrifice an artifact or creature, put a +1/+1 counter on this creature.
            {1}{B}{G}, Sacrifice an artifact or creature: You gain 1 life and draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = youSacrifice(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, plusOne, amount = Value.Fixed(1))))),
        ),
        activated(
            mana("{1}{B}{G}"), sacrifice(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature))),
            steps = listOf(gainLife(1), draw(1)),
        ),
    )

    // CR 608.2e, 101.4: each target player chooses, then all sacrifice at once. The reminder "(Activate only as an
    // instant.)" changes nothing: an ability with a target isn't a mana ability (CR 605.1a).
    @Test
    fun priestOfNamelessGods() = card(
        name = "Priest of Nameless Gods",
        cost = "{1}{B}",
        type = "Creature — Human Cleric",
        pt = "1/2",
        text = "{T}, Sacrifice two other creatures: Any number of target players each lose 2 life and sacrifice a creature of their choice. You add {B}{B} and draw a card. (Activate only as an instant.)",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf, sacrifice(ObjectFilter.And(listOf(notSelf, creature)), count = Value.Fixed(2)),
            targets = listOf(TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(0), max = null)),
            steps = listOf(
                Step.Do(Effect.LoseLife(Players.Target("players"), Value.Fixed(2))),
                Step.ForEach(
                    each = Each.Players(Players.Target("players")),
                    bind = "player",
                    together = true,
                    steps = listOf(
                        Step.Choose(
                            choice = Choice.Objects(
                                filter = ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))),
                                min = Value.Fixed(1),
                                max = Value.Fixed(1),
                            ),
                            bind = "sacrificed",
                            chooser = Players.Bound("player"),
                        ),
                        Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                    ),
                ),
                Step.Do(Effect.AddMana(
                    ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}{B}")!!)),
                    players = Players.You,
                )),
                draw(1),
            ),
        ),
    )

    // CR 701.16a: investigate; the revealed cards not put onto the battlefield go to the bottom in a random order
    @Test
    fun thessaBeastwatcher() = card(
        name = "Thessa, Beastwatcher",
        cost = "{G}{U}",
        type = "Legendary Creature — Snake Elf Scout",
        pt = "1/2",
        text = """
            Whenever another nontoken creature you control enters, investigate.
            {T}, Sacrifice X Clues: Target opponent reveals the top X cards of their library. You may put a nonland permanent card with mana value X or less from among them onto the battlefield under your control. That player puts the rest on the bottom of their library in a random order.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                ObjectFilter.And(listOf(notSelf, ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)), creature, youControl)),
                to = Zone.Battlefield,
            )),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Action(KeywordAction.Investigate(count = Value.Fixed(1))))),
            ),
        ),
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                sacrifice(ObjectFilter.Sub(Subtype("Clue")), count = Value.Announced(name = "X")),
            )),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    "opponent",
                    TargetKind.Players(PlayerFilter.Opponent),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(
                    Step.Do(Effect.Reveal(
                        Objects.LibraryCards(Players.Target("opponent"), Value.Announced(name = "X"), fromTop = true),
                        bind = "revealed",
                    )),
                    // "You may put a card from among them" is a choice of zero or one
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = ObjectFilter.And(listOf(
                                ObjectFilter.Not(land),
                                // CR 110.4a: a permanent card
                                ObjectFilter.Or(listOf(
                                    ObjectFilter.Type(CardType.Artifact),
                                    ObjectFilter.Type(CardType.Battle),
                                    creature,
                                    ObjectFilter.Type(CardType.Enchantment),
                                    land,
                                    ObjectFilter.Type(CardType.Planeswalker),
                                )),
                                ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X")),
                            )),
                            min = Value.Fixed(0),
                            max = Value.Fixed(1),
                            from = Objects.Bound("revealed"),
                        ),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Battlefield, controller = Players.You))),
                    Step.Do(Effect.Move(
                        Objects.Except(Objects.Bound("revealed"), Objects.Bound("chosen")),
                        to = Destination(Zone.Library, libraryPosition = -1, randomOrder = true),
                    )),
                ),
                announced = listOf("X"),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )
}
