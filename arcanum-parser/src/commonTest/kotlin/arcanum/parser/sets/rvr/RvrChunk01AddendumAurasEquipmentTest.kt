package arcanum.parser.sets.rvr

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.TargetSlot
import arcanum.api.TargetKind
import arcanum.api.Destination
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk01AddendumAurasEquipmentTest {
    private val artifactOrEnchantment = or(artifact, enchantment)

    // CR 207.2c (addendum is an ability word), 505.1: the paragraph refers to the spell's target (CR 113.3a, 608.2c)
    @Test
    fun wardensFervor() = card(
        name = "Warden's Fervor",
        cost = "{W}",
        type = "Instant",
        text = """
            Target creature gets +2/+2 until end of turn.
            Addendum — If you cast this spell during your main phase, that creature gains flying until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 2, 2),
            ifThen(addendum, gains(target("creature"), Keyword.Flying)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 207.2c: "it deals 5 damage instead": the same damage to the same target, another amount
    @Test
    fun swiftVerdict() = card(
        name = "Swift Verdict",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Swift Verdict deals 3 damage to target tapped creature.
            Addendum — If you cast this spell during your main phase, it deals 5 damage instead.
        """,
    ) parsesTo abilities(
        spell(
            ifThen(
                addendum,
                damage(5, Recipients(target("creature"))),
                otherwise = listOf(damage(3, Recipients(target("creature")))),
            ),
            targets = listOf(slot("creature", and(tapped, creature))),
        ).copy(abilityWord = "Addendum"),
    )

    // CR 207.2c; "those creatures": the creatures the first paragraph gave indestructible (CR 608.2c)
    @Test
    fun unyieldingPhalanx() = card(
        name = "Unyielding Phalanx",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Creatures you control gain indestructible until end of turn.
            Addendum — If you cast this spell during your main phase, put a +1/+1 counter on each of those creatures and they gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            gains(Objects.Each(creatureYouControl), Keyword.Indestructible),
            ifThen(
                addendum,
                counters(Objects.Each(creatureYouControl)),
                gains(Objects.Each(creatureYouControl), Keyword.Vigilance),
            ),
        ),
    )

    // CR 303.4a; CR 605.1a: "unless they're mana abilities" keeps its mana abilities
    @Test
    fun shacklesOfDevotion() = card(
        name = "Shackles of Devotion",
        cost = "{3}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant permanent
            When this Aura enters, you gain 4 life.
            Enchanted permanent can't attack or block, and its activated abilities can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        enchant(permanentFilter),
        triggered(entersSelf, gainLife(4)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = false), affects = attached),
    )

    // CR 613.1f (layer 6, loses all abilities), 613.4b (layer 7b, base power and toughness)
    @Test
    fun magesUnmaking() = card(
        name = "Mage's Unmaking",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature loses all abilities and has base power and toughness 1/1.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(Mechanic.LoseAbilities(), Mechanic.SetPowerToughness(n(1), n(1)), affects = attached),
    )

    // CR 207.2c (hellbent); "an additional +2/+0": another +2/+0 while the condition holds (CR 613.4c)
    @Test
    fun hungerForHavoc() = card(
        name = "Hunger for Havoc",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+0.
            Hellbent — Enchanted creature gets an additional +2/+0 as long as you have no cards in hand.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 0), affects = attached),
        static(pt(2, 0), affects = attached, condition = hellbent).copy(abilityWord = "Hellbent"),
    )

    // CR 605.1b: a triggered mana ability; "its controller" is the Forest's controller
    @Test
    fun verdantOvergrowth() = card(
        name = "Verdant Overgrowth",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant Forest
            As this Aura enters, choose a color.
            Whenever enchanted Forest is tapped for mana, its controller adds an additional one mana of the chosen color.
        """,
    ) parsesTo abilities(
        enchant(sub("Forest")),
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(attached), forMana = true)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))), Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
    )

    // CR 303.4, 702.19 (trample)
    @Test
    fun knucklesOfOakbark() = card(
        name = "Knuckles of Oakbark",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, create two 1/1 green Saproling creature tokens.
            Enchanted creature has trample.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(entersSelf, create(creatureToken("Saproling", listOf(Color.Green), 1, 1), count = n(2))),
        static(has(Keyword.Trample), affects = attached),
    )

    // CR 503.1a: "your upkeep" is the Aura's controller's
    @Test
    fun coercedEvolution() = card(
        name = "Coerced Evolution",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            At the beginning of your upkeep, put a +1/+1 counter on enchanted creature.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(yourUpkeep(), counters(attached)),
    )

    // CR 503.1a: the upkeep of the enchanted creature's controller; "that player" is that controller
    @Test
    fun ranklingPuncture() = card(
        name = "Rankling Puncture",
        cost = "{2}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -2/-2.
            At the beginning of the upkeep of enchanted creature's controller, that player loses 2 life.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(-2, -2), affects = attached),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Is(Players.ControllerOf(attached))), loseLife(2, Players.EventPlayer)),
    )

    // CR 303.4a ("Enchant green creature"), 111.1 (the token)
    @Test
    fun wurmthreadSpiral() = card(
        name = "Wurmthread Spiral",
        cost = "{4}{G}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant green creature
            Enchanted creature gets +6/+6.
            {G}{G}{G}, Sacrifice this Aura: Create a 6/6 green Wurm creature token.
        """,
    ) parsesTo abilities(
        enchant(and(ObjectFilter.OfColor(Color.Green), creature)),
        static(pt(6, 6), affects = attached),
        activated(cost(mana("{G}{G}{G}"), sacrificeSelf), create(creatureToken("Wurm", listOf(Color.Green), 6, 6))),
    )

    // CR 614.1a: a redirection replacement; with nothing equipped there is nothing to redirect to
    @Test
    fun outcastsAegis() = card(
        name = "Outcast's Aegis",
        cost = "{5}",
        type = "Artifact — Equipment",
        text = """
            All damage that would be dealt to you is dealt to equipped creature instead.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(
            event = EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter(players = PlayerFilter.You)),
            modify = listOf(Modification.ChangeRecipient(Recipients(attached))),
        )),
        equip("{3}"),
    )

    // CR 611.3a (a static ability's condition is checked continuously); 701.26 (tap, untap); the "you may" leaves both undone
    @Test
    fun bladeOfTheTwinHouses() = card(
        name = "Blade of the Twin Houses",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            As long as equipped creature is tapped, tapped creatures you control get +2/+0.
            As long as equipped creature is untapped, untapped creatures you control get +0/+2.
            {3}: You may tap or untap equipped creature.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = Objects.Each(and(tapped, creature, you)), condition = Condition.ObjectMatches(attached, tapped)),
        static(pt(0, 2), affects = Objects.Each(and(untapped, creature, you)), condition = Condition.ObjectMatches(attached, untapped)),
        activated(manaCost("{3}"), Step.May(listOf(
            Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
            ifThen(Condition.Chose("tapOrUntap", "tap"), tapIt(attached), otherwise = listOf(untapIt(attached)))), Players.You)),
        equip("{3}"),
    )

    // CR 105.2: "for each of its colors" counts the equipped creature's colours
    @Test
    fun guildsmansCutlass() = card(
        name = "Guildsman's Cutlass",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 for each of its colors.
            Equip {1}
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(Value.CountDistinct(attached, SharedQuality.Color), n(0)), affects = attached),
        equip("{1}"),
    )

    // CR 605.1a: mana abilities don't trigger it (audit: the event's manaAbilities = false is "if it isn't a mana ability"); CR 707.10
    @Test
    fun phantasmistsCuffs() = card(
        name = "Phantasmist's Cuffs",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Whenever an ability of equipped creature is activated, if it isn't a mana ability, copy that ability. You may choose new targets for the copy.
            Equip {3}
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Activate(ObjectFilter.Is(attached), by = PlayerFilter.Any, manaAbilities = false)),
            doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true))),
        equip("{3}"),
    )
    // CR 603.4: "it" after an intervening if about the enchanted creature is that creature, not the Aura (a fix found in this chunk:
    // with "is untapped" newly read for an attached creature, "tap it" read as tapping the Aura)
    @Test
    fun drowsingHex() = card(
        name = "Drowsing Hex",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            At the beginning of each upkeep, if enchanted creature is untapped, tap it.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), tapIt(attached), interveningIf = Condition.ObjectMatches(attached, untapped)),
    )

    // CR 603.4: likewise "untap it" is the enchanted permanent, not the Aura that entered
    @Test
    fun reclaimedWill() = card(
        name = "Reclaimed Will",
        cost = "{4}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant permanent
            When this Aura enters, if enchanted permanent is tapped, untap it.
            You control enchanted permanent.
        """,
    ) parsesTo abilities(
        enchant(permanentFilter),
        triggered(entersSelf, untapIt(attached), interveningIf = Condition.ObjectMatches(attached, tapped)),
        static(Mechanic.ControlledBy(Players.You), affects = attached),
    )
    // CR 115.3: "up to one other target artifact or enchantment" after "target artifact or enchantment" is a different object from the
    // first target (a fix found in this chunk: "other" read as other than the spell itself); CR 505.1 for the main-phase condition
    @Test
    fun dustwardRite() = card(
        name = "Dustward Rite",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = "Exile target artifact or enchantment. If you cast this spell during your main phase, you may exile up to one other target artifact or enchantment.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile))),
            ifThen(addendum, Step.May(listOf(Step.Do(Effect.Move(target("second permanent"), Destination(Zone.Exile), postBind = "exiled"))), Players.You)),
            targets = listOf(
                slot("permanent", artifactOrEnchantment),
                TargetSlot("second permanent", TargetKind.Objects(artifactOrEnchantment), min = n(0), max = n(1), distinctFrom = listOf("permanent")),
            ),
        ),
    )
}
