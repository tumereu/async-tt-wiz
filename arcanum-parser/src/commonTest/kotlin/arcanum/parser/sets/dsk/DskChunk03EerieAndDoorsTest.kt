package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Door
import arcanum.api.Duration
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
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.eerie
import arcanum.parser.sets.dsk.Dsk.roomsYouControl
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 03: eerie ("Whenever an enchantment you control enters and whenever you fully unlock a Room", one
 * triggered ability with two events, CR 709.5i) and the other cards about doors: unlocking a locked door of a Room
 * (CR 709.5f), locking or unlocking one (709.5g), unlock costs and mana for unlocking (709.5e), counts of unlocked doors;
 * a sacrifice of a Room as a cost; "the first, second, third time this ability has resolved this turn".
 */
class DskChunk03EerieAndDoorsTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic) =
        Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val drawACard = Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))
    private val roomSlot = slot("Room", roomsYouControl)

    /** "Lock or unlock a door of target Room you control": one of the two, chosen as it resolves (CR 709.5f, g). */
    private val lockOrUnlock = listOf(
        Step.Choose(Choice.Option(listOf("lock", "unlock")), bind = "lockOrUnlock", chooser = Players.You),
        Step.If(
            condition = Condition.Chose("lockOrUnlock", "lock"),
            then = listOf(Step.Do(Effect.LockDoor(Objects.Target("Room"), chooser = Players.You))),
            otherwise = listOf(Step.Do(Effect.UnlockDoor(Objects.Target("Room"), chooser = Players.You))),
        ),
    )

    // ---- Eerie

    @Test
    fun cultMender() = card(
        name = "Cult Mender",
        cost = "{2}{W}",
        type = "Creature — Human Doctor",
        pt = "3/3",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gains lifelink until end of turn.",
    ) parsesTo abilities(
        eerie(untilEndOfTurn(Objects.Self, Mechanic.GainAbilities(listOf(Keyword.Lifelink)))),
    )

    /** "[A] or [B]": one of the two, chosen as it resolves; the enchantment card isn't targeted. Unlocking chooses a Room
     * with a locked door (CR 709.5f). */
    @Test
    fun spectralWaltzers() = card(
        name = "Spectral Waltzers",
        cost = "{3}{W}{W}",
        type = "Creature — Spirit",
        pt = "2/5",
        text = """
            Flying
            When this creature enters, return an enchantment card from your graveyard to your hand or unlock a locked door of a Room you control.
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, create a 3/1 white Spirit creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(steps = listOf(
                Step.Choose(Choice.Option(listOf("return", "unlock")), bind = "returnOrUnlock", chooser = Players.You),
                Step.If(
                    condition = Condition.Chose("returnOrUnlock", "return"),
                    then = listOf(
                        Step.Choose(
                            choice = Choice.Objects(
                                filter = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Enchantment), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                                min = Value.Fixed(1),
                                max = Value.Fixed(1),
                            ),
                            bind = "chosen",
                            chooser = Players.You,
                        ),
                        Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                    ),
                    otherwise = listOf(
                        Step.Choose(
                            choice = Choice.Objects(
                                filter = ObjectFilter.And(listOf(
                                    ObjectFilter.Sub(Subtype("Room")),
                                    youControl,
                                    ObjectFilter.Not(ObjectFilter.And(listOf(
                                        ObjectFilter.HasStatus(Status.Marked(ObjectMark.Unlocked(Door.Left))),
                                        ObjectFilter.HasStatus(Status.Marked(ObjectMark.Unlocked(Door.Right))),
                                    ))),
                                )),
                                min = Value.Fixed(1),
                                max = Value.Fixed(1),
                            ),
                            bind = "room",
                            chooser = Players.You,
                        ),
                        Step.Do(Effect.UnlockDoor(Objects.Bound("room"), chooser = Players.You)),
                    ),
                ),
            )),
        ),
        eerie(create(token(listOf("Spirit"), listOf(Color.White), 3, 1, abilities = listOf(Keyword.Flying)))),
    )

    @Test
    fun hopefulForager() = card(
        name = "Hopeful Forager",
        cost = "{W}",
        type = "Creature — Human Scout",
        pt = "1/1",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, put a +1/+1 counter on target creature.",
    ) parsesTo abilities(
        eerie(Step.Do(Effect.PutCounters(Objects.Target("creature"), plusOne, Value.Fixed(1))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun phantomChaser() = card(
        name = "Phantom Chaser",
        cost = "{2}{U}",
        type = "Creature — Human Scout",
        pt = "2/3",
        text = """
            Flash
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, draw a card.
        """,
    ) parsesTo abilities(Keyword.Flash, eerie(drawACard))

    @Test
    fun flickeringHaunt() = card(
        name = "Flickering Haunt",
        cost = "{2}{U}",
        type = "Creature — Spirit",
        pt = "1/3",
        text = """
            Flying, vigilance
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        eerie(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1)))),
    )

    /** "Can attack this turn as though it didn't have defender" lifts defender's restriction (CR 702.3b). */
    @Test
    fun huntedScholar() = card(
        name = "Hunted Scholar",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "3/3",
        text = """
            Defender
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature can attack this turn as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        eerie(untilEndOfTurn(Objects.Self, Mechanic.CanAttackDespiteDefender)),
    )

    @Test
    fun skitteringBonecrab() = card(
        name = "Skittering Bonecrab",
        cost = "{U}",
        type = "Creature — Crab Skeleton",
        pt = "0/3",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, target player mills two cards. (They put the top two cards of their library into their graveyard.)",
    ) parsesTo abilities(
        eerie(
            Step.Do(Effect.Mill(Players.Target("player"), Value.Fixed(2))),
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))),
        ),
    )

    /** A counter without rules meaning (CR 122.1); "the number of counters on this creature" as it last existed (CR
     * 608.2h). */
    @Test
    fun reluctantHost() = card(
        name = "Reluctant Host",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "3/2",
        text = """
            Vigilance
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, put a possession counter on this creature.
            When this creature dies, create an X/X blue Spirit creature token with flying, where X is the number of counters on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        eerie(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Named("possession"), Value.Fixed(1)))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(
                TokenSpec.Defined(
                    FaceDefinition(
                        name = "Spirit Token",
                        colorIndicator = listOf(Color.Blue),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Spirit")),
                        power = PrintedNumber.X,
                        toughness = PrintedNumber.X,
                        abilities = listOf(Keyword.Flying),
                    ),
                    power = Value.Of(NumericProperty.Counters(null), Objects.EventObject(EventRole.Before), Combine.Single),
                    toughness = Value.Of(NumericProperty.Counters(null), Objects.EventObject(EventRole.Before), Combine.Single),
                ),
                count = Value.Fixed(1),
                controller = Players.You,
            )))),
        ),
    )

    @Test
    fun mirelurkLeech() = card(
        name = "Mirelurk Leech",
        cost = "{1}{B}",
        type = "Creature — Leech",
        pt = "2/2",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, each opponent loses 1 life.",
    ) parsesTo abilities(
        eerie(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1)))),
    )

    @Test
    fun dapperBloodsipper() = card(
        name = "Dapper Bloodsipper",
        cost = "{3}{B}",
        type = "Creature — Vampire Warrior",
        pt = "2/5",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gets +2/+0 and gains lifelink until end of turn.",
    ) parsesTo abilities(
        eerie(untilEndOfTurn(Objects.Self,
            Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)),
            Mechanic.GainAbilities(listOf(Keyword.Lifelink)),
        )),
    )

    /** "Its power" as it last existed on the battlefield (CR 608.2h). */
    @Test
    fun hellishSpecter() = card(
        name = "Hellish Specter",
        cost = "{3}{R}",
        type = "Creature — Spirit",
        pt = "2/3",
        text = """
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, this creature gets +2/+0 until end of turn.
            When this creature dies, it deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        eerie(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = Instructions(
                targets = listOf(TargetSlot("any", TargetKind.Either(ObjectFilter.Or(listOf(
                    creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))), PlayerFilter.Any),
                    min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"),
                    Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), source = Objects.Self))),
            ),
        ),
    )

    /** An eerie ability that works from the graveyard ("return this card from your graveyard", CR 113.6m). */
    @Test
    fun dreadOfEternity() = card(
        name = "Dread of Eternity",
        cost = "{1}{U}{B}",
        type = "Enchantment Creature — Nightmare",
        pt = "2/2",
        text = """
            Flying, lifelink
            This creature can't block.
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, you may return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        Ability.Static(listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)),
        eerie(Step.May(listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand)))), player = Players.You))
            .copy(functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun gremlinWrangler() = card(
        name = "Gremlin Wrangler",
        cost = "{W}{U}",
        type = "Creature — Human Scout",
        pt = "2/2",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, create a 1/1 red Gremlin creature token.",
    ) parsesTo abilities(
        eerie(create(token(listOf("Gremlin"), listOf(Color.Red), 1, 1))),
    )

    @Test
    fun skullclackPest() = card(
        name = "Skullclack Pest",
        cost = "{U}{B}",
        type = "Creature — Insect Skeleton",
        pt = "1/4",
        text = """
            Flying
            Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        eerie(Step.Do(Effect.Surveil(Players.You, Value.Fixed(1)))),
    )

    /** "If this is the first time this ability has resolved this turn" counts this resolution. The creature card is
     * chosen as it resolves, from any graveyard, and enters under your control (CR 110.2a). */
    @Test
    fun varrowValgavothsSteward() = card(
        name = "Varrow, Valgavoth's Steward",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "3/3",
        text = "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, surveil 2 if this is the first time this ability has resolved this turn. If it's the second time, each opponent discards a card. If it's the third time, put a creature card from a graveyard onto the battlefield under your control.",
    ) parsesTo abilities(
        eerie(
            Step.If(
                condition = Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(1)),
                then = listOf(Step.Do(Effect.Surveil(Players.You, Value.Fixed(2)))),
            ),
            Step.If(
                condition = Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(2)),
                then = listOf(Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), Value.Fixed(1), filter = ObjectFilter.Any))),
            ),
            Step.If(
                condition = Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(3)),
                then = listOf(
                    Step.Choose(
                        choice = Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard))), min = Value.Fixed(1), max = Value.Fixed(1)),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))),
                ),
            ),
        ),
    )

    // ---- Doors

    @Test
    fun ghostlyDoorwarden() = card(
        name = "Ghostly Doorwarden",
        cost = "{3}{U}",
        type = "Creature — Spirit",
        pt = "3/3",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, unlock a locked door of up to one target Room you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true)),
            instructions = Instructions(
                targets = listOf(slot("Room", roomsYouControl, min = 0)),
                steps = listOf(Step.Do(Effect.UnlockDoor(Objects.Target("Room"), chooser = Players.You))),
            ),
        ),
    )

    @Test
    fun ragingSoulreaver() = card(
        name = "Raging Soulreaver",
        cost = "{2}{R}",
        type = "Creature — Spirit",
        pt = "1/4",
        text = "This creature gets +3/+0 as long as there are two or more unlocked doors among Rooms you control.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(3), Value.Fixed(0))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Compare(
                Value.Of(NumericProperty.UnlockedDoors, Objects.Each(roomsYouControl), Combine.Total),
                Comparison.AtLeast,
                Value.Fixed(2),
            ),
        ),
    )

    /** Spell cost reductions (CR 601.2f) and unlock costs, the special action's (CR 709.5e), paid by you. */
    @Test
    fun curiousGlimmer() = card(
        name = "Curious Glimmer",
        cost = "{W}{U}",
        type = "Enchantment Creature — Fox Glimmer",
        pt = "2/3",
        text = """
            Enchantment spells you cast cost {1} less to cast.
            Unlock costs you pay cost {1} less.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(
                what = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Enchantment), ObjectFilter.Category(ObjectCategory.Spell), youControl)),
                change = CostChange.Less(ManaCost.parse("{1}")!!, Value.Fixed(1)),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(
                what = ObjectFilter.And(listOf(ObjectFilter.AbilityOf(kind = AbilityKind.Unlock), youControl)),
                change = CostChange.Less(ManaCost.parse("{1}")!!, Value.Fixed(1)),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    /** Mana restricted to spells and to special actions (CR 106.6, 709.5e, 116.2b). */
    @Test
    fun lurkingWatcher() = card(
        name = "Lurking Watcher",
        cost = "{1}{U}",
        type = "Creature — Eye",
        pt = "2/1",
        text = "{T}: Add {U}. Spend this mana only to cast an enchantment spell, unlock a door, or turn a permanent face up.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(
                ManaProduction(
                    kind = ManaKind.Fixed(ManaCost.parse("{U}")!!),
                    restriction = ObjectFilter.Or(listOf(
                        ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.Type(CardType.Enchantment))),
                        ObjectFilter.AbilityOf(kind = AbilityKind.Unlock),
                        ObjectFilter.AbilityOf(kind = AbilityKind.TurnFaceUp),
                    )),
                ),
                Players.You,
            )))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    /** "All enchantment cards from among them" and "the rest", without a choice. */
    @Test
    fun mirelaVendane() = card(
        name = "Mirela Vendane",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — Human Warlock",
        pt = "3/5",
        text = """
            When Mirela Vendane enters, reveal the top seven cards of your library. Put all enchantment cards from among them into your hand and the rest on the bottom of your library in a random order.
            {T}: Lock or unlock a door of target Room you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(7), fromTop = true), bind = "seen")),
                Step.Do(Effect.Move(Objects.Where(Objects.Bound("seen"), ObjectFilter.Type(CardType.Enchantment)), Destination(Zone.Hand))),
                Step.Do(Effect.Move(
                    Objects.Except(Objects.Bound("seen"), Objects.Where(Objects.Bound("seen"), ObjectFilter.Type(CardType.Enchantment))),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true),
                )),
            )),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(targets = listOf(roomSlot), steps = lockOrUnlock),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun ringOfOldKeys() = card(
        name = "Ring of Old Keys",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, {T}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {3}, {T}, Sacrifice this artifact: Lock or unlock a door of target Room you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))),
                    max = Value.Fixed(1), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.You)),
            )),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{3}")!!), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(targets = listOf(roomSlot), steps = lockOrUnlock),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun trespassingSoulrager() = card(
        name = "Trespassing Soulrager",
        cost = "{U}{R}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            Vigilance
            {T}, Sacrifice a Room: This creature deals 2 damage to each opponent. Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Sub(Subtype("Room")), count = Value.Fixed(1))))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), Value.Fixed(2), source = Objects.Self)),
                drawACard,
            )),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )
}
