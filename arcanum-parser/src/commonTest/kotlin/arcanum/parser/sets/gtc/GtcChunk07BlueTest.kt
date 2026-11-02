package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 07: blue. Returning attackers, Auras that return themselves, casting cards from each opponent's
 * graveyard (CR 601.2c, 608.2g) and exiling them instead, drawing a whole library, exactly X targets (CR 601.2c), the
 * second spell each turn (CR 603.2), returning a creature that dealt combat damage, a basic land type chosen as it enters
 * (CR 614.12) and given to lands (CR 305.7), "unless its controller pays {X}, where X is" (CR 118.12a, 107.3c), a
 * permanent put on top of its owner's library, and an Aura's condition on controlling a Gate.
 */
class GtcChunk07BlueTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val nonlandPermanent = ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Category(ObjectCategory.Permanent)))

    @Test
    fun aetherwindReversal() = card(
        name = "Aetherwind Reversal",
        cost = "{3}{U}",
        type = "Instant",
        text = "Return all attacking creatures to their owner's hand.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature))), Destination(Zone.Hand)))),
    )

    @Test
    fun crowdphobia() = card(
        name = "Crowdphobia",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -5/-0.
            {2}{U}: Return this Aura to its owner's hand.
        """,
    ) parsesTo abilities(
        enchantCreature,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(-5), n(0))), affects = Affected.Objects(enchanted)),
        Ability.Activated(mana("{2}{U}"), steps(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand)))), rules = anyTime),
    )

    // CR 601.2c: up to one target in each opponent's graveyard; CR 608.2g: cast while it resolves; exiled instead of put
    // into a graveyard
    @Test
    fun tidalPrimordial() = card(
        name = "Tidal Primordial",
        cost = "{5}{U}{U}",
        type = "Creature — Avatar",
        pt = "5/5",
        text = """
            Flying
            When this creature enters, for each opponent, you may cast up to one target instant or sorcery card from that player's graveyard without paying its mana cost. If a spell cast this way would be put into a graveyard, exile it instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Target("card")), bind = "toCast", chooser = Players.You),
            Step.Do(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, bind = "cast", caster = Players.You)),
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                    modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
                affects = null, duration = Duration.Indefinitely,
            )),
            targets = listOf(TargetSlot("card", TargetKind.Objects(ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.SlotPlayer)),
            ))), min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent)),
        ),
    )

    // CR 402.2: no maximum hand size until your next turn
    @Test
    fun embraceTheInfinite() = card(
        name = "Embrace the Infinite",
        cost = "{8}{U}{U}{U}{U}",
        type = "Sorcery",
        text = "Draw cards equal to the number of cards in your library, then put a card from your hand on top of your library. You have no maximum hand size until your next turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))))))),
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You)))), n(1), n(1)),
                bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Library, libraryPosition = 0))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You), Duration.UntilYourNextTurn)),
        ),
    )

    @Test
    fun frilledWatcher() = card(
        name = "Frilled Watcher",
        cost = "{1}{U}",
        type = "Creature — Homunculus",
        pt = "1/3",
        text = "{1}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.",
    ) parsesTo abilities(
        Ability.Activated(mana("{1}{G}"), steps(pump(Objects.Self, 2, 2)), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)),
    )

    // CR 601.2c: exactly X targets
    @Test
    fun gridlockSnare() = card(
        name = "Gridlock Snare",
        cost = "{X}{U}",
        type = "Instant",
        text = "Tap X target nonland permanents.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Tap(Objects.Target("permanent"))),
            targets = listOf(TargetSlot("permanent", TargetKind.Objects(nonlandPermanent), min = Value.Announced("X"), max = Value.Announced("X"))),
            announced = listOf("X"),
        ),
    )

    // CR 603.2: the second spell its controller casts each turn
    @Test
    fun breachSpecialist() = card(
        name = "Breach Specialist",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/3",
        text = "Whenever you cast your second spell each turn, this creature gets +2/+0 until end of turn and can't be blocked this turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            steps(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(n(2), n(0)), Mechanic.CantBeBlocked)),
        ),
    )

    @Test
    fun vaultkeyRogue() = card(
        name = "Vaultkey Rogue",
        cost = "{3}{U}",
        type = "Creature — Human Rogue",
        pt = "3/2",
        text = """
            This creature can't be blocked.
            When this creature enters, return a creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self)),
        whenThisEnters(
            Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
        ),
    )

    // Combat damage to a player or a permanent; "it" is this creature
    @Test
    fun leylineSpecter() = card(
        name = "Leyline Specter",
        cost = "{4}{U}",
        type = "Creature — Illusion",
        pt = "5/5",
        text = "When this creature deals combat damage, return it to its owner's hand. (Return it only if it survived combat.)",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(), combat = true)),
            steps(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand)))),
        ),
    )

    @Test
    fun skylineSprite() = card(
        name = "Skyline Sprite",
        cost = "{1}{U}",
        type = "Creature — Faerie Rogue",
        pt = "1/2",
        text = """
            Flying
            {U}: This creature gets +1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Activated(mana("{U}"), steps(pump(Objects.Self, 1, -1)), rules = anyTime),
    )

    @Test
    fun mindglassDrake() = card(
        name = "Mindglass Drake",
        cost = "{4}{U}",
        type = "Creature — Drake",
        pt = "2/5",
        text = """
            Flying
            When this creature dies, target player mills five cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(diesSelf, Instructions(targets = listOf(targetPlayer), steps = listOf(Step.Do(Effect.Mill(Players.Target("player"), n(5)))))),
    )

    // CR 111.2: the destroyed creature's controller creates the token
    @Test
    fun hybridConversion() = card(
        name = "Hybrid Conversion",
        cost = "{U}",
        type = "Instant",
        text = "Destroy target creature. It can't be regenerated. That creature's controller creates a 3/3 green Frog Lizard creature token.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("creature"), noRegeneration = true)),
            Step.Do(Effect.CreateToken(creatureToken("Frog Lizard", listOf(Color.Green), 3, 3), n(1), Players.ControllerOf(Objects.Target("creature")))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 614.12, 607.2d: the type chosen as it enters; CR 305.7: the lands keep their other types and abilities
    @Test
    fun realmshaper() = card(
        name = "Realmshaper",
        cost = "{U}",
        type = "Creature — Vedalken Wizard",
        pt = "1/1",
        text = """
            As this creature enters, choose a basic land type.
            Lands you control are the chosen type in addition to their other types.
        """,
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.BasicLandType, bind = "type", chooser = Players.You, remember = true)))))),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(land, you))))),
    )

    @Test
    fun scholarsRowDenizen() = card(
        name = "Scholar's Row Denizen",
        cost = "{2}{U}",
        type = "Creature — Vedalken Wizard",
        pt = "2/3",
        text = "Whenever another blue creature you control enters, target player mills two cards.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.OfColor(Color.Blue), creature, you)),
                to = Zone.Battlefield)),
            Instructions(targets = listOf(targetPlayer), steps = listOf(Step.Do(Effect.Mill(Players.Target("player"), n(2))))),
        ),
    )

    @Test
    fun scatterWard() = card(
        name = "Scatter Ward",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            Counter target noncreature spell.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Counter(Objects.Target("spell"))),
            Step.Do(Effect.Draw(Players.You, n(1))),
            targets = listOf(slot("spell", ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Spell))))),
        ),
    )

    // CR 113.10: granted to the enchanted land, activated as a sorcery
    @Test
    fun skywardGames() = card(
        name = "Skyward Games",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "{T}: Target creature gains flying until end of turn. Activate only as a sorcery."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                cost = costOf(CostPart.TapSelf),
                instructions = Instructions(targets = listOf(slot("creature", creature)), steps = listOf(gains(Objects.Target("creature"), Keyword.Flying))),
                rules = sorcerySpeed,
            )))),
            affects = Affected.Objects(enchanted),
        ),
    )

    // CR 118.12a: the controller may pay {X}; CR 107.3c: X is the greatest power as it resolves
    @Test
    fun spellFracture() = card(
        name = "Spell Fracture",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {X}, where X is the greatest power among creatures you control.",
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!,
                x = Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
                payer = Players.ControllerOf(Objects.Target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(Objects.Target("spell"))))),
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
        ),
    )

    @Test
    fun utterlyLost() = card(
        name = "Utterly Lost",
        cost = "{4}{U}",
        type = "Instant",
        text = "Put target nonland permanent on top of its owner's library.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Library, libraryPosition = 0))), targets = listOf(slot("permanent", nonlandPermanent))),
    )

    @Test
    fun pathOfTheThief() = card(
        name = "Path of the Thief",
        cost = "{3}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2.
            Enchanted creature can't be blocked as long as you control a Gate.
        """,
    ) parsesTo abilities(
        enchantCreature,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(2), n(2))), affects = Affected.Objects(enchanted)),
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(enchanted),
            condition = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Gate")), you)), atLeast = 1)),
    )
}
