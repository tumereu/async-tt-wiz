package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
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
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 52: creatures with static abilities and triggers other than entering. Spell-cast triggers, a cost increase
 * (CR 601.2f), a replacement that puts one counter fewer (CR 614.1a), P/T bonuses counted from the graveyard (layer 7c),
 * choices made as the creature enters and kept on it (a card name, a permanent: CR 614.12, 607.2d) and what they
 * switch off or copy, "enters with X counters" (CR 107.3m), "for the first time each turn", "this is the second time
 * this ability has resolved this turn", revealing until a card is found (CR 701.20), looking at a hand and choosing
 * from it, and blinking a creature at end of turn.
 */
class Chunk52StaticsAndCastTriggersTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val plusOneCounter = CounterKind.PowerToughness(1, 1)
    private val entering = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)

    /** "Whenever you cast a [kind] spell, …": the cast event names only the spell's kind. */
    private fun youCast(kind: ObjectFilter) = Trigger.On(EventPattern.Cast(kind, by = PlayerFilter.You))

    /** "As this creature enters, [choose …]." (CR 614.12) */
    private fun asEnters(choose: Step.Choose) = Ability.Static(listOf(Mechanic.Replacement(
        event = entering,
        modify = listOf(Modification.AsEnters(listOf(choose))),
    )), affects = Affected.Objects(Objects.Self))

    // ---- Spell-cast triggers

    @Test
    fun briarSentry() = card(
        name = "Briar Sentry",
        cost = "{G}",
        type = "Creature — Elf Warrior",
        pt = "2/2",
        text = """
            This creature doesn't untap during your untap step.
            Whenever you cast a green spell, you may untap this creature.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = youCast(ObjectFilter.OfColor(Color.Green)),
            instructions = Instructions(
                steps = listOf(Step.May(listOf(Step.Do(Effect.Untap(Objects.Self))), player = Players.You)),
            ),
        ),
    )

    @Test
    fun scrapquiltAutomaton() = card(
        name = "Scrapquilt Automaton",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "1/1",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            Whenever you cast an artifact spell, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        Ability.Triggered(
            trigger = youCast(ObjectFilter.Type(CardType.Artifact)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, plusOneCounter, Value.Fixed(1))))),
        ),
    )

    /** CR 111.4: a token with no name given is named by its subtypes plus "Token". */
    @Test
    fun noviceFlamecaller() = card(
        name = "Novice Flamecaller",
        cost = "{1}{R}",
        type = "Creature — Human Shaman",
        pt = "2/1",
        text = "Whenever you cast an instant or sorcery spell, create a 1/1 red Elemental creature token.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = youCast(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                name = "Elemental Token",
                colorIndicator = listOf(Color.Red),
                types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Elemental")),
                power = PrintedNumber.Fixed(1),
                toughness = PrintedNumber.Fixed(1),
            )), count = Value.Fixed(1), controller = Players.You)))),
        ),
    )

    // ---- Statics: costs, counters, P/T

    /** CR 601.2f: the cost increase applies while the total cost is determined. */
    @Test
    fun seraphineKeeperOfTheGate() = card(
        name = "Seraphine, Keeper of the Gate",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/1",
        text = """
            First strike
            Noncreature spells cost {1} more to cast.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Ability.Static(listOf(Mechanic.CostChange(
            what = ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Spell))),
            change = CostChange.More(ManaCost.parse("{1}")!!, times = Value.Fixed(1)),
        )), affects = Affected.Objects(Objects.Self)),
    )

    /** CR 614.1a: the event is changed, one counter fewer. */
    @Test
    fun menderOfAfflictions() = card(
        name = "Mender of Afflictions",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "2/1",
        text = "If one or more -1/-1 counters would be put on a creature you control, that many -1/-1 counters minus one are put on it instead.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.CountersPut(ObjectFilter.And(listOf(creature, youControl)), CounterKind.PowerToughness(-1, -1)),
            modify = listOf(Modification.AddAmount(Value.Fixed(-1))),
        )), affects = Affected.Objects(Objects.Self)),
    )

    /** CR 107.3m: X is the X paid when it was cast (0 if it wasn't). */
    @Test
    fun shalecoilWyrm() = card(
        name = "Shalecoil Wyrm",
        cost = "{X}",
        type = "Artifact Creature — Snake",
        pt = "0/0",
        text = """
            Reach, trample, protection from multicolored
            This creature enters with X +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        Keyword.Protection(ObjectFilter.Multicolored),
        Ability.Static(listOf(Mechanic.Replacement(
            event = entering,
            modify = listOf(Modification.EntersWithCounters(plusOneCounter, Value.CastX(of = Objects.Self))),
        )), affects = Affected.Objects(Objects.Self)),
    )

    /** CR 613.4c: a P/T change in layer 7c, counted as it applies. "Instant and sorcery card" is each card that is
     * either. */
    @Test
    fun ysoldeTheUnboundScholar() = card(
        name = "Ysolde, the Unbound Scholar",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Human Wizard",
        pt = "0/3",
        text = """
            Ysolde gets +1/+0 for each instant and sorcery card in your graveyard.
            Whenever you discard one or more cards for the first time each turn, draw that many cards.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(
            power = Value.Count(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You),
            )))),
            toughness = Value.Fixed(0),
        )), affects = Affected.Objects(Objects.Self)),
        // One trigger for the batch of cards discarded at once, only for the first such batch each turn
        Ability.Triggered(
            trigger = Trigger.On(
                listOf(EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any)),
                oneOrMore = true,
                nthThisTurn = 1,
            ),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Count(Objects.EventObject(EventRole.After))))),
            ),
        ),
    )

    // ---- Choices made as it enters

    /** CR 614.12, 607.2d: the name is chosen as it enters and kept on it. Activated abilities of cards in every zone are
     * stopped (cycling in a hand too). */
    @Test
    fun silencingAutomaton() = card(
        name = "Silencing Automaton",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Horror",
        pt = "2/1",
        text = """
            As this creature enters, choose a nonland card name.
            Activated abilities of sources with the chosen name can't be activated.
        """,
    ) parsesTo abilities(
        asEnters(Step.Choose(
            Choice.CardName(ObjectFilter.Not(ObjectFilter.Type(CardType.Land))),
            bind = "name",
            remember = true,
            chooser = Players.You,
        )),
        Ability.Static(
            mechanics = listOf(Mechanic.CantBeActivated(manaAbilities = true)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.RememberedName("name"),
                ObjectFilter.Or(listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)
                    .map { ObjectFilter.InZone(it) }),
            )))),
        ),
    )

    /** CR 613.1f: the gained abilities apply in layer 6. */
    @Test
    fun craftyPawnbroker() = card(
        name = "Crafty Pawnbroker",
        cost = "{W}{U}",
        type = "Creature — Human Citizen",
        pt = "2/3",
        text = """
            As this creature enters, you may choose a nonland permanent.
            Activated abilities of the chosen permanent can't be activated.
            This creature has all activated abilities of the chosen permanent except for loyalty abilities. You may spend mana as though it were mana of any color to activate those abilities.
        """,
    ) parsesTo abilities(
        asEnters(Step.Choose(
            choice = Choice.Objects(
                filter = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), ObjectFilter.Category(ObjectCategory.Permanent))),
                min = Value.Fixed(0),
                max = Value.Fixed(1),
            ),
            bind = "chosen",
            remember = true,
            chooser = Players.You,
        )),
        Ability.Static(
            mechanics = listOf(Mechanic.CantBeActivated(manaAbilities = true)),
            affects = Affected.Objects(Objects.Remembered("chosen")),
        ),
        Ability.Static(
            listOf(Mechanic.GainAbilitiesOf(Objects.Remembered("chosen"), exceptLoyalty = true)),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = ObjectFilter.Is(Objects.Self)))),
            affects = Affected.Players(Players.You),
        ),
    )

    // ---- Other triggers

    @Test
    fun spiritwrangler() = card(
        name = "Spiritwrangler",
        cost = "{1}{W}{U}",
        type = "Creature — Spirit",
        pt = "1/1",
        text = """
            Whenever a creature is exiled from the battlefield, put a +1/+1 counter on this creature.
            At the beginning of your end step, you may exile another target creature you control, then return that card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Exile)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, plusOneCounter, Value.Fixed(1))))),
        ),
        // CR 603.3d: the target is chosen as it is put on the Stack, even though "you may" is decided on resolution
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "creature",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, youControl))),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(Step.May(listOf(
                    Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Move(Objects.Bound("exiled"), to = Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
                ), player = Players.You)),
            ),
        ),
    )

    /** "You may choose … If you do" is a Pay of the choice (CR 118.12). */
    @Test
    fun gossamerCoterie() = card(
        name = "Gossamer Coterie",
        cost = "{1}{U}{U}",
        type = "Legendary Creature — Faerie Wizard",
        pt = "3/1",
        text = """
            Flash
            Flying
            When Gossamer Coterie enters, look at target player's hand. You may choose a nonland card from it. If you do, that player reveals the chosen card, puts it on the bottom of their library, then draws a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(entering),
            instructions = run {
                val hand = Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))),
                )))
                val chosen = Objects.Bound("chosen")
                Instructions(
                    targets = listOf(TargetSlot(
                        "player",
                        TargetKind.Players(PlayerFilter.Any),
                        min = Value.Fixed(1),
                        max = Value.Fixed(1),
                    )),
                    steps = listOf(
                        Step.Do(Effect.LookAt(hand, players = Players.You)),
                        Step.Pay(
                            steps = listOf(Step.Choose(
                                Choice.Objects(
                                    ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                                    Value.Fixed(1),
                                    Value.Fixed(1),
                                    from = hand,
                                ),
                                bind = "chosen",
                                chooser = Players.You,
                            )),
                            bind = "paid",
                            payer = Players.You,
                        ),
                        Step.If(Condition.Did("paid"), then = listOf(
                            Step.Do(Effect.Reveal(chosen)),
                            Step.Do(Effect.Move(chosen, to = Destination(Zone.Library, libraryPosition = -1))),
                            Step.Do(Effect.Draw(Players.Target("player"), Value.Fixed(1))),
                        )),
                    ),
                )
            },
        ),
    )

    /** "This is the second time this ability has resolved this turn" counts this resolution; revealing until a card is
     * found (CR 701.20). Not a mana ability: it doesn't trigger from a mana ability (CR 605.1b). */
    @Test
    fun ilsaVerdantSeeker() = card(
        name = "Ilsa, Verdant Seeker",
        cost = "{2}{G}",
        type = "Legendary Creature — Elf Scout",
        pt = "3/3",
        text = "Landfall — Whenever a land you control enters, add one mana of any color. Then if this is the second time this ability has resolved this turn, reveal cards from the top of your library until you reveal an Elf or Elemental card. Put that card into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        Ability.Triggered(
            abilityWord = "Landfall",
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), youControl)), to = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1))), players = Players.You)),
                Step.If(
                    condition = Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(2)),
                    then = listOf(
                        Step.Do(Effect.RevealUntil(
                            filter = ObjectFilter.Or(listOf(ObjectFilter.Sub(Subtype("Elf")), ObjectFilter.Sub(Subtype("Elemental")))),
                            foundBind = "found",
                            restBind = "rest",
                            library = Players.You,
                            count = Value.Fixed(1),
                            reveal = true,
                        )),
                        Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Hand))),
                        Step.Do(Effect.Move(Objects.Bound("rest"), to = Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
                    ),
                ),
            )),
        ),
    )
}
