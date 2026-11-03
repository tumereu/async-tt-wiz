package arcanum.parser

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
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
import arcanum.parser.cube.allOf
import arcanum.parser.cube.card
import arcanum.parser.cube.drawCards
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.objectSlot
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import arcanum.parser.cube.withoutText
import kotlin.test.Test

/**
 * Wordings of mechanics the engine plays since its lanes 1–4 (exert, costs to attack and block, actions any time you could mulligan,
 * starting hand sizes, copy effects of static abilities, keyword actions), one test per wording, on invented cards.
 */
class EngineLaneWordingsTest {
    private val creature = ObjectFilter.Type(CardType.Creature)
    private val creatures = Objects.Each(creature)
    private val exertThis = Cost(listOf(CostPart.Action(KeywordAction.Exert(Objects.Self))))
    private fun action(action: KeywordAction) = Step.Do(Effect.Action(action))
    private val anyTime = ActivationRules(Timing.Any, PlayerFilter.You)

    // ---- Exert (CR 701.43)

    // CR 701.43d, 508.1g, 603.12: an optional cost to attack and its linked "when you do" reflexive trigger; "it" is this creature
    @Test
    fun exertAsItAttacksWhenYouDo() = card(
        name = "Sunscorched Duelist",
        cost = "{1}{W}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = "You may exert this creature as it attacks. When you do, it gets +1/+3 and gains lifelink until end of turn. " +
            "(An exerted creature won't untap during your next untap step.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AsAttacks(exertThis, Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(3)), Mechanic.GainAbilities(listOf(Keyword.Lifelink))),
        Affected.Objects(Objects.Self), Duration.EndOfTurn)))))), Affected.Objects(Objects.Self)))

    // CR 701.43d: the optional cost alone (other abilities refer to exerting)
    @Test
    fun exertAsItAttacksAlone() = card(
        name = "Dune Sentry",
        cost = "{2}{W}",
        type = "Creature — Jackal Warrior",
        pt = "3/2",
        text = "You may exert this creature as it attacks. (It won't untap during your next untap step.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AsAttacks(exertThis)), Affected.Objects(Objects.Self)))

    // CR 701.43d, 603.12: the reflexive trigger's own target
    @Test
    fun exertWhenYouDoTargets() = card(
        name = "Dune Harrier",
        cost = "{3}{R}",
        type = "Creature — Minotaur Warrior",
        pt = "4/3",
        text = "You may exert this creature as it attacks. When you do, target creature can't block this turn. " +
            "(An exerted creature won't untap during your next untap step.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AsAttacks(exertThis, Instructions(targets = listOf(objectSlot("creature", creature)),
        steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(Objects.Target("creature")),
            Duration.EndOfTurn)))))), Affected.Objects(Objects.Self)))

    // CR 701.43a, b: exerting as a cost of an activated ability
    @Test
    fun exertAsACost() = card(
        name = "Dune Scribe",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/1",
        text = "{T}, Exert this creature: Draw a card. (An exerted creature won't untap during your next untap step.)",
    ) parsesTo abilities(Ability.Activated(Cost(listOf(CostPart.TapSelf) + exertThis.parts), Instructions(steps = listOf(drawCards(1))), anyTime))

    // ---- Costs to attack and block (CR 508.1d, h; 509.1d)

    // CR 508.1h: a mana cost for each creature attacking you
    @Test
    fun cantAttackYouUnlessPaysForEachAttacking() = card(
        name = "Wailing Bastion",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AttackCost(manaCost("{2}"), Recipients(players = Players.You))), Affected.Objects(creatures)))

    // CR 508.1d, 509.1d: an Aura's mana cost to attack and to block with the enchanted creature
    @Test
    fun enchantedCantAttackOrBlockUnlessPays() = card(
        name = "Leaden Doubt",
        cost = "{W}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block unless its controller pays {3}.
        """,
    ) parsesTo abilities(Keyword.Enchant(TargetKind.Objects(creature)), Ability.Static(listOf(Mechanic.AttackCost(manaCost("{3}")),
        Mechanic.BlockCost(manaCost("{3}"))), Affected.Objects(Objects.AttachedTo(Objects.Self))))

    // CR 508.1h: a cost other than mana (sacrificing permanents), paid as attackers are declared
    @Test
    fun cantAttackUnlessYouSacrifice() = card(
        name = "Tidewrack Leviathan",
        cost = "{5}{U}{U}",
        type = "Creature — Leviathan",
        pt = "8/8",
        text = "This creature can't attack unless you sacrifice two Islands. (This cost is paid as attackers are declared.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AttackCost(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(
        ObjectFilter.Sub(SubtypeRef.Literal(Subtype("Island"))), count = Value.Fixed(2))))))), Affected.Objects(Objects.Self)))

    // CR 508.1h: returning a permanent as a cost to attack
    @Test
    fun cantAttackUnlessYouReturn() = card(
        name = "Ebbing Serpent",
        cost = "{4}{U}",
        type = "Creature — Serpent",
        pt = "5/5",
        text = "This creature can't attack unless you return an enchantment you control to its owner's hand. (This cost is paid as attackers are declared.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AttackCost(Cost(listOf(CostPart.ReturnToHand(CostObjects.Choose(
        allOf(ObjectFilter.Type(CardType.Enchantment), ObjectFilter.ControlledBy(PlayerFilter.You)), count = Value.Fixed(1))))))),
        Affected.Objects(Objects.Self)))

    // CR 508.1h: a sacrifice for each attacking creature of a kind
    @Test
    fun cantAttackUnlessControllerSacrificesForEach() = card(
        name = "Ashen Reclaiming",
        cost = "{2}{B}",
        type = "Enchantment",
        text = "Black creatures can't attack unless their controller sacrifices a land of their choice for each black creature they control " +
            "that's attacking. (This cost is paid as attackers are declared.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.AttackCost(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Land),
        count = Value.Fixed(1))))))), Affected.Objects(Objects.Each(allOf(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(arcanum.api.Color.Black)), creature)))))

    // CR 509.1d: a mana cost for each blocker, while this creature attacks
    @Test
    fun cantBlockUnlessPaysWhileAttacking() = card(
        name = "Tithing Seraph",
        cost = "{2}{W}",
        type = "Creature — Angel",
        pt = "2/5",
        text = """
            Flying
            As long as this creature is attacking, creatures can't block unless their controller pays {1} for each of those creatures.
        """,
    ) parsesTo abilities(Keyword.Flying, Ability.Static(listOf(Mechanic.BlockCost(manaCost("{1}"))), Affected.Objects(creatures),
        condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Attacking))))

    // CR 509.1d: a life cost for each blocker
    @Test
    fun cantBlockUnlessPaysLife() = card(
        name = "Toll of Thorns",
        cost = "{1}{B}",
        type = "Enchantment",
        text = "Creatures can't block unless their controller pays 2 life for each blocking creature they control.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.BlockCost(Cost(listOf(CostPart.PayLife(Value.Fixed(2)))))), Affected.Objects(creatures)))

    // ---- Starting the game (CR 103.5)

    // CR 103.5b: an action any time its owner could mulligan, while the card is in their hand
    @Test
    fun anyTimeYouCouldMulligan() = card(
        name = "Ashen Reshuffle",
        cost = "{2}",
        type = "Artifact",
        text = "Any time you could mulligan and this card is in your hand, you may exile all the cards from your hand, then draw that many " +
            "cards. (You can do this in addition to taking mulligans.)",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.WhenYouCouldMulligan(Instructions(steps = listOf(
        Step.Do(Effect.Move(Objects.Each(allOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), Destination(Zone.Exile),
            postBind = "exiled")),
        Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Bound("exiled")))))))), Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)))

    // CR 103.5, 905.4: a conspiracy in the command zone increases its owner's starting hand size
    @Test
    fun startingHandSizeIncreased() = card(
        name = "Generous Opening",
        cost = null,
        type = "Conspiracy",
        text = "Your starting hand size is increased by one.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.StartingHandSize(1)), Affected.Players(Players.You), functionsIn = listOf(Zone.Command)))

    // ---- Copy effects of static abilities (CR 613.2a, 707.2)

    // CR 607.2d, 707.2: the enchanted creature is a copy of the creature chosen as the Aura entered
    @Test
    fun enchantedIsACopyOfTheChosen() = card(
        name = "Shifting Likeness",
        cost = "{1}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            As this Aura enters, choose a creature.
            Enchanted creature is a copy of the chosen creature.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(arcanum.api.Choice.Objects(creature, Value.Fixed(1), Value.Fixed(1)),
                bind = "chosen", chooser = Players.You, remember = true)))))), Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.CopyOf(Objects.Remembered("chosen"))), Affected.Objects(Objects.AttachedTo(Objects.Self))),
    )

    // CR 613.2a, 707.2: other creatures are copies of the enchanted creature
    @Test
    fun othersAreCopiesOfEnchanted() = card(
        name = "Mirrorbond Charm",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Other creatures you control are copies of enchanted creature.
        """,
    ) parsesTo abilities(Keyword.Enchant(TargetKind.Objects(creature)), Ability.Static(listOf(Mechanic.CopyOf(Objects.AttachedTo(Objects.Self))),
        Affected.Objects(Objects.Each(allOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, ObjectFilter.ControlledBy(PlayerFilter.You))))))

    // ---- Keyword actions (CR 701)

    // CR 701.44a: "it explores" in its own enters trigger: this creature
    @Test
    fun itExplores() = card(
        name = "Canopy Seeker",
        cost = "{1}{G}",
        type = "Creature — Merfolk Scout",
        pt = "2/2",
        text = "When this creature enters, it explores. (Reveal the top card of your library. Put that card into your hand if it's a land. " +
            "Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)",
    ) parsesTo abilities(whenThisEnters(action(KeywordAction.Explore(Objects.Self))))

    // CR 701.44a: a target explores
    @Test
    fun targetExplores() = card(
        name = "Trailguide Totem",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Target creature you control explores. Activate only as a sorcery.",
    ) parsesTo abilities(Ability.Activated(Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf)), Instructions(
        targets = listOf(objectSlot("creature", allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.You)))),
        steps = listOf(action(KeywordAction.Explore(Objects.Target("creature"))))), ActivationRules(Timing.Sorcery, PlayerFilter.You)))

    // CR 701.44a: each creature of a kind explores
    @Test
    fun eachExplores() = card(
        name = "Tidecaller of the Deep",
        cost = "{2}{G}{U}",
        type = "Creature — Merfolk Shaman",
        pt = "3/3",
        text = "At the beginning of combat on your turn, each Merfolk creature you control explores.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You)), Instructions(steps = listOf(
        action(KeywordAction.Explore(Objects.Each(allOf(ObjectFilter.Sub(SubtypeRef.Literal(Subtype("Merfolk"))), creature,
            ObjectFilter.ControlledBy(PlayerFilter.You)))))))))

    // CR 701.37a, b: monstrosity as an activated ability, and a static ability while it is monstrous
    @Test
    fun monstrosity() = card(
        name = "Gorge Behemoth",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            {5}{G}{G}: Monstrosity 3. (If this creature isn't monstrous, put three +1/+1 counters on it and it becomes monstrous.)
            As long as this creature is monstrous, it has trample.
        """,
    ) parsesTo abilities(
        Ability.Activated(manaCost("{5}{G}{G}"), Instructions(steps = listOf(action(KeywordAction.Monstrosity(Value.Fixed(3))))), anyTime),
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Trample))), Affected.Objects(Objects.Self),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(ObjectMark.Monstrous)))),
    )

    // CR 701.37a, 107.3a: "Monstrosity X." with X announced with the activation
    @Test
    fun monstrosityX() = card(
        name = "Swelling Hydra",
        cost = "{2}{G}",
        type = "Creature — Hydra",
        pt = "2/2",
        text = "{X}{G}{G}: Monstrosity X. (If this creature isn't monstrous, put X +1/+1 counters on it and it becomes monstrous.)",
    ) parsesTo abilities(Ability.Activated(manaCost("{X}{G}{G}"), Instructions(steps = listOf(
        action(KeywordAction.Monstrosity(Value.Announced(name = "X")))), announced = listOf("X")), anyTime))

    // CR 701.46a
    @Test
    fun adapt() = card(
        name = "Reef Mutant",
        cost = "{1}{U}",
        type = "Creature — Fish Mutant",
        pt = "1/2",
        text = "{2}{U}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)",
    ) parsesTo abilities(Ability.Activated(manaCost("{2}{U}"), Instructions(steps = listOf(action(KeywordAction.Adapt(Value.Fixed(2))))), anyTime))

    // CR 701.41a: support on a permanent: up to N other target creatures
    @Test
    fun supportOnAPermanent() = card(
        name = "Shieldline Captain",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = "When this creature enters, support 2. (Put a +1/+1 counter on each of up to two other target creatures.)",
    ) parsesTo abilities(whenThisEnters(action(KeywordAction.Support(Value.Fixed(2), "supported")), targets = listOf(TargetSlot("supported",
        TargetKind.Objects(allOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)), min = Value.Fixed(0), max = Value.Fixed(2)))))

    // CR 701.41a: support on an instant or sorcery: up to N target creatures
    @Test
    fun supportOnASpell() = card(
        name = "Rally the Line",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Support 2. (Put a +1/+1 counter on each of up to two target creatures.)
            Draw a card.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(targets = listOf(TargetSlot("supported", TargetKind.Objects(creature), min = Value.Fixed(0),
        max = Value.Fixed(2))), steps = listOf(action(KeywordAction.Support(Value.Fixed(2), "supported")), drawCards(1)))))

    // CR 701.48a
    @Test
    fun learn() = card(
        name = "Quiz Night",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Draw a card.
            Learn. (You may reveal a Lesson card you own from outside the game and put it into your hand, or discard a card to draw a card.)
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(steps = listOf(drawCards(1), action(KeywordAction.Learn)))))

    // CR 701.57a
    @Test
    fun discover() = card(
        name = "Cavern Unearther",
        cost = "{3}{G}",
        type = "Creature — Dinosaur",
        pt = "3/3",
        text = "When this creature enters, discover 3. (Exile cards from the top of your library until you exile a nonland card with mana " +
            "value 3 or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)",
    ) parsesTo abilities(whenThisEnters(action(KeywordAction.Discover(Value.Fixed(3)))))

    // CR 701.40a
    @Test
    fun manifestTheTopCard() = card(
        name = "Veiled Grower",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        pt = "1/1",
        text = "When this creature dies, manifest the top card of your library. (Put it onto the battlefield face down as a 2/2 creature. " +
            "Turn it face up any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
        Instructions(steps = listOf(action(KeywordAction.Manifest(Value.Fixed(1)))))))

    // CR 701.58a
    @Test
    fun cloakTheTopCard() = card(
        name = "Shrouded Entrance",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Cloak the top card of your library. (Put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up " +
            "any time for its mana cost if it's a creature card.)",
    ) parsesTo abilities(Ability.Spell(Instructions(steps = listOf(action(KeywordAction.Cloak(Value.Fixed(1)))))))

    // CR 701.56a
    @Test
    fun timeTravel() = card(
        name = "Clockwise Nudge",
        cost = "{U}",
        type = "Instant",
        text = """
            Time travel. (For each suspended card you own and each permanent you control with a time counter on it, you may add or remove a time counter.)
            Draw a card.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(steps = listOf(action(KeywordAction.TimeTravel), drawCards(1)))))

    // CR 701.15a: goading a target
    @Test
    fun goadTarget() = card(
        name = "Jeering Imp",
        cost = "{1}{R}",
        type = "Creature — Goblin",
        pt = "2/1",
        text = "When this creature enters, goad target creature. (Until your next turn, that creature attacks each combat if able and attacks " +
            "a player other than you if able.)",
    ) parsesTo abilities(whenThisEnters(action(KeywordAction.Goad(Objects.Target("creature"))), targets = listOf(objectSlot("creature", creature))))

    // CR 701.15a: "goad it" after another effect: that effect's target, not this creature
    @Test
    fun goadIt() = card(
        name = "Taunting Wyrm",
        cost = "{4}{R}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = "When this creature enters, tap target creature an opponent controls. Goad it.",
    ) parsesTo abilities(whenThisEnters(Step.Do(Effect.Tap(Objects.Target("creature"))), action(KeywordAction.Goad(Objects.Target("creature"))),
        targets = listOf(objectSlot("creature", allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent))))))

    // CR 701.15a: goading every creature of a kind
    @Test
    fun goadAll() = card(
        name = "Brawl Instigation",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Goad all creatures your opponents control. (Until your next turn, those creatures attack each combat if able and attack a " +
            "player other than you if able.)",
    ) parsesTo abilities(Ability.Spell(Instructions(steps = listOf(action(KeywordAction.Goad(Objects.Each(allOf(creature,
        ObjectFilter.ControlledBy(PlayerFilter.Opponent)))))))))

    // ---- Battles (CR 310)

    // CR 207.2a, 310.12a, b: a Siege's reminder text is no ability (its protector and its intrinsic trigger are the rules'); "this Siege" is
    // this object
    @Test
    fun siege() {
        val face = OracleParser.parse(OracleCard("Siege of Cinder Gate", "{2}{R}", "Battle — Siege", """
            (As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, exile it, then cast it transformed.)
            When this Siege enters, it deals 3 damage to target creature an opponent controls.
        """.trimIndent(), defense = "4"))
        kotlin.test.assertEquals(arcanum.api.PrintedNumber.Fixed(4), face.defense)
        kotlin.test.assertEquals(listOf(whenThisEnters(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Fixed(3),
            source = Objects.Self)), targets = listOf(objectSlot("creature", allOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))))),
            face.abilities.map(::withoutText))
    }
}
