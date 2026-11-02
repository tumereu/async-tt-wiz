package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.combatDamageToAPlayer
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.create
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.creatureToken
import arcanum.parser.sets.rtr.Rtr.damage
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.mana
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisAttacks
import arcanum.parser.sets.rtr.Rtr.thisDies
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import kotlin.test.Test

/**
 * Return to Ravnica chunk 12: artifacts and lands. Keyrunes becoming "[creature type] artifact creatures" until end of
 * turn (CR 205.1b: they keep their other types, the creature types replaced; layers 4, 5, 6, 7b), mana of either of two
 * colours, a card name and two colours chosen as it enters (CR 607.2d) with activated abilities stopped "unless they're
 * mana abilities" and life for each chosen colour a spell is, Auras attached to a land, coin flips; Gates, lands that may
 * enter tapped for 2 life, "sacrifice it unless you pay {1}".
 */
class RtrChunk12ArtifactsAndLandsTest {
    private fun eitherMana(a: Color, b: Color) = activated(cost(CostPart.TapSelf),
        Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(a), ColorRef.Literal(b)))), Players.You)))
    private val anyColorMana = activated(cost(CostPart.TapSelf), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))
    private val entersTapped = static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)))
    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (CR 614.12) */
    private val payTwoOrTapped = static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(Cost(listOf(CostPart.PayLife(n(2)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))))))))

    /** "[This] becomes a P/T [colours] [type] artifact creature [with keywords] until end of turn" (CR 205.1b). */
    private fun becomes(power: Int, toughness: Int, colors: List<Color>, subtype: String, vararg keywords: Ability) = Step.Do(Effect.ApplyMechanics(
        listOfNotNull(
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype(subtype))), replaceSubtypes = true),
            Mechanic.SetColors(colors.map { ColorRef.Literal(it) }),
            Mechanic.SetPowerToughness(n(power), n(toughness)),
            keywords.takeIf { it.isNotEmpty() }?.let { Mechanic.GainAbilities(it.toList()) },
        ), arcanum.api.Affected.Objects(Objects.Self), Duration.EndOfTurn))

    // ---- Keyrunes

    @Test
    fun senateRunestone() = card(
        name = "Senate Runestone",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {W} or {U}.
            {W}{U}: This artifact becomes a 2/2 white and blue Bird artifact creature with flying until end of turn.
        """,
    ) parsesTo abilities(
        eitherMana(Color.White, Color.Blue),
        activated(cost(manaPart("{W}{U}")), becomes(2, 2, listOf(Color.White, Color.Blue), "Bird", Keyword.Flying)),
    )

    @Test
    fun undercityRunestone() = card(
        name = "Undercity Runestone",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {B} or {G}.
            {B}{G}: This artifact becomes a 2/2 black and green Insect artifact creature with deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        eitherMana(Color.Black, Color.Green),
        activated(cost(manaPart("{B}{G}")), becomes(2, 2, listOf(Color.Black, Color.Green), "Insect", Keyword.Deathtouch)),
    )

    /** "Until end of turn," in front; "you may draw a card. If you do, discard a card" (CR 118.12). */
    @Test
    fun foundryRunestone() = card(
        name = "Foundry Runestone",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {U} or {R}.
            {U}{R}: Until end of turn, this artifact becomes a 2/1 blue and red Elemental artifact creature.
            Whenever this artifact deals combat damage to a player, you may draw a card. If you do, discard a card.
        """,
    ) parsesTo abilities(
        eitherMana(Color.Blue, Color.Red),
        activated(cost(manaPart("{U}{R}")), becomes(2, 1, listOf(Color.Blue, Color.Red), "Elemental")),
        triggered(combatDamageToAPlayer(),
            Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any))))),
    )

    @Test
    fun carnivalRunestone() = card(
        name = "Carnival Runestone",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {B} or {R}.
            {B}{R}: This artifact becomes a 3/1 black and red Devil artifact creature with first strike until end of turn.
        """,
    ) parsesTo abilities(
        eitherMana(Color.Black, Color.Red),
        activated(cost(manaPart("{B}{R}")), becomes(3, 1, listOf(Color.Black, Color.Red), "Devil", Keyword.FirstStrike)),
    )

    @Test
    fun conclaveRunestone() = card(
        name = "Conclave Runestone",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {G} or {W}.
            {G}{W}: This artifact becomes a 3/3 green and white Wolf artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        eitherMana(Color.Green, Color.White),
        activated(cost(manaPart("{G}{W}")), becomes(3, 3, listOf(Color.Green, Color.White), "Wolf")),
    )

    // ---- Other artifacts

    /** A granted mana ability (CR 113.10, 605.1a). */
    @Test
    fun prismaticLamp() = card(
        name = "Prismatic Lamp",
        cost = "{3}",
        type = "Artifact",
        text = """
            Lands you control have "{T}: Add one mana of any color."
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        static(Objects.Each(all(land, you)), Mechanic.GainAbilities(listOf(anyColorMana))),
        anyColorMana,
    )

    /** "For each of its colors": how many colours it is (CR 105.2). */
    @Test
    fun citizensBlade() = card(
        name = "Citizen's Blade",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 for each of its colors.
            Equip {1}
        """,
    ) parsesTo abilities(
        static(Rtr.enchanted, Mechanic.ModifyPowerToughness(Value.CountDistinct(Rtr.enchanted, SharedQuality.Color), n(0))),
        Keyword.Equip(Cost.mana(mana("{1}"))),
    )

    @Test
    fun ledgerShredderEngine() = card(
        name = "Ledger Shredder Engine",
        cost = "{1}",
        type = "Artifact",
        text = """
            {T}: Target player mills a card. (They put the top card of their library into their graveyard.)
            {5}, {T}, Sacrifice this artifact: Return target card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.TapSelf), Step.Do(Effect.Mill(Players.Target("player"), n(1))), targets = listOf(Rtr.targetPlayer)),
        activated(cost(manaPart("{5}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self)), Rtr.move(target("card"), Zone.Hand),
            targets = listOf(slot("card", all(Rtr.card, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You))))),
    )

    /** Any card name (CR 201.3), kept on it (CR 607.2d); objects with that name in every zone, mana abilities excepted. */
    @Test
    fun pinningSpike() = card(
        name = "Pinning Spike",
        cost = "{1}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a card name.
            Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You, remember = true)))))),
        static(Objects.Each(all(ObjectFilter.RememberedName("name"), ObjectFilter.Or(listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard,
            Zone.Exile, Zone.Stack, Zone.Command).map { ObjectFilter.InZone(it) }))), Mechanic.CantBeActivated(manaAbilities = false)),
    )

    @Test
    fun gutterSweeper() = card(
        name = "Gutter Sweeper",
        cost = "{6}",
        type = "Artifact Creature — Construct",
        pt = "4/6",
        text = "Whenever this creature attacks, destroy all Auras attached to target land.",
    ) parsesTo abilities(
        triggered(thisAttacks, Rtr.destroy(Objects.AttachmentsOf(target("land"), Rtr.sub("Aura"))), targets = listOf(slot("land", land))),
    )

    /** Two different colours chosen as it enters (CR 607.2d); the spell's colours among them counted once each (CR 105.2). */
    @Test
    fun slateOfTheFactions() = card(
        name = "Slate of the Factions",
        cost = "{2}",
        type = "Artifact",
        text = """
            As this artifact enters, choose two colors.
            Whenever you cast a spell, if it's at least one of the chosen colors, you gain 1 life for each of the chosen colors it is.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Colors(2), bind = "color", chooser = Players.You, remember = true)))))),
        triggered(Rtr.youCast(ObjectFilter.Any),
            Rtr.gainLife(Value.SharedColors(Objects.EventObject(arcanum.api.EventRole.Object), listOf(ColorRef.Remembered("color")))),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(arcanum.api.EventRole.Object), ObjectFilter.OfColor(ColorRef.Remembered("color")))),
    )

    /** "It deals 4 damage …" after dying: the creature as it last existed is the source (CR 608.2h). */
    @Test
    fun unstableContraption() = card(
        name = "Unstable Contraption",
        cost = "{4}",
        type = "Artifact Creature — Construct",
        pt = "4/4",
        text = """
            Trample
            This creature attacks each combat if able.
            Whenever this creature is dealt damage, flip a coin. If you lose the flip, sacrifice this creature.
            When this creature dies, flip a coin. If you lose the flip, it deals 4 damage to each creature and each player.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Objects.Self, Mechanic.MustAttack()),
        triggered(Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
            Step.Do(Effect.FlipCoin(Players.You, bind = "flip")),
            Step.If(Condition.Not(Condition.WonFlip("flip")), then = listOf(Step.Do(Effect.Sacrifice(Objects.Self))))),
        triggered(thisDies,
            Step.Do(Effect.FlipCoin(Players.You, bind = "flip")),
            Step.If(Condition.Not(Condition.WonFlip("flip")), then = listOf(
                damage(Recipients(objects = Objects.Each(creature), players = Players.Each(arcanum.api.PlayerFilter.Any)), 4)))),
    )

    // ---- Lands

    @Test
    fun senateGate() = card(
        name = "Senate Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(entersTapped, eitherMana(Color.White, Color.Blue))

    @Test
    fun undercityGate() = card(
        name = "Undercity Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {B} or {G}.
        """,
    ) parsesTo abilities(entersTapped, eitherMana(Color.Black, Color.Green))

    @Test
    fun foundryGate() = card(
        name = "Foundry Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {U} or {R}.
        """,
    ) parsesTo abilities(entersTapped, eitherMana(Color.Blue, Color.Red))

    @Test
    fun carnivalGate() = card(
        name = "Carnival Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {B} or {R}.
        """,
    ) parsesTo abilities(entersTapped, eitherMana(Color.Black, Color.Red))

    @Test
    fun conclaveGate() = card(
        name = "Conclave Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {G} or {W}.
        """,
    ) parsesTo abilities(entersTapped, eitherMana(Color.Green, Color.White))

    /** Basic land types give the mana ability (CR 305.6); its reminder text gives none. */
    @Test
    fun goreVault() = card(
        name = "Gore Vault",
        cost = null,
        type = "Land — Swamp Mountain",
        text = """
            ({T}: Add {B} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(payTwoOrTapped)

    @Test
    fun sanctifiedSpring() = card(
        name = "Sanctified Spring",
        cost = null,
        type = "Land — Plains Island",
        text = """
            ({T}: Add {W} or {U}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(payTwoOrTapped)

    @Test
    fun mossyCrypt() = card(
        name = "Mossy Crypt",
        cost = null,
        type = "Land — Swamp Forest",
        text = """
            ({T}: Add {B} or {G}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(payTwoOrTapped)

    @Test
    fun geyserChasm() = card(
        name = "Geyser Chasm",
        cost = null,
        type = "Land — Island Mountain",
        text = """
            ({T}: Add {U} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(payTwoOrTapped)

    @Test
    fun shrineOrchard() = card(
        name = "Shrine Orchard",
        cost = null,
        type = "Land — Forest Plains",
        text = """
            ({T}: Add {G} or {W}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(payTwoOrTapped)

    /** Tapping two other creatures as a cost (CR 602.2b). */
    @Test
    fun gladeOfTheSentinel() = card(
        name = "Glade of the Sentinel",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {3}{G}{W}, {T}, Tap two untapped creatures you control, Sacrifice this land: Create an 8/8 green and white Elemental creature token with vigilance.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.TapSelf), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana("{C}"))), Players.You))),
        activated(cost(manaPart("{3}{G}{W}"), CostPart.TapSelf, CostPart.TapObjects(CostObjects.Choose(creature, count = n(2))), CostPart.Sacrifice(CostObjects.Self)),
            create(creatureToken("Elemental", listOf(Color.Green, Color.White), 8, 8, Keyword.Vigilance))),
    )

    @Test
    fun smugglersAlley() = card(
        name = "Smuggler's Alley",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}, {T}: Target creature can't be blocked this turn.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.TapSelf), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana("{C}"))), Players.You))),
        activated(cost(manaPart("{4}"), CostPart.TapSelf), Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeBlocked),
            arcanum.api.Affected.Objects(target("creature")), Duration.EndOfTurn)), targets = listOf(slot("creature", creature))),
    )

    /** CR 118.12a as it enters: pay {1} or it is sacrificed. */
    @Test
    fun interguildConcourse() = card(
        name = "Interguild Concourse",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, sacrifice it unless you pay {1}.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(thisEnters,
            Step.Pay(Cost.mana(mana("{1}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Sacrifice(Objects.Self))))),
        anyColorMana,
    )
}
