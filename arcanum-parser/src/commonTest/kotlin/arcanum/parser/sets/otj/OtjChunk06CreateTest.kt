package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 06: tokens with quoted abilities (the Mercenary, a named legendary token, a named tapped artifact token with two abilities),
 * a trigger on activating an ability that targets, extra mana from an artifact token.
 */
class OtjChunk06CreateTest {
    private val mercenaryText =
        "create a 1/1 red Mercenary creature token with \"{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery.\""

    // CR 111.4: the token has the quoted activated ability; CR 602.5d: "Activate only as a sorcery"
    private fun createMercenary(): Step = create(creatureToken("Mercenary", listOf(Color.Red), 1, 1, abilities = listOf(
        activated(cost(tap), pump(target("creature"), 1, 0), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery))))

    private fun dies(filter: ObjectFilter): Trigger = on(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))

    // CR 111.4: a token's name and supertype as the effect gives them; CR 201.5: its own name in its quoted ability means itself;
    // CR 604.3: a characteristic-defining ability; CR 508.1: "whenever you attack"
    @Test
    fun martaVellTimberline() = card(
        name = "Marta Vell, Timberline",
        cost = "{3}{G}{U}{U}",
        type = "Legendary Creature — Giant Scout",
        pt = "6/5",
        text = """
            Reach
            When Marta Vell enters, create Cobb, a legendary blue Ox creature token with "Cobb's power and toughness are each equal to the number of lands you control."
            Whenever you attack, draw a card, then you may put a land card from your hand or graveyard onto the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, create(TokenSpec.Defined(FaceDefinition("Cobb", supertypes = listOf(Supertype.Legendary), colorIndicator = listOf(Color.Blue),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Ox")), power = PrintedNumber.Star(), toughness = PrintedNumber.Star(),
            abilities = listOf(static(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you))), characteristicDefining = true)))))),
        triggered(youAttack, draw(1),
            Step.Choose(Choice.Objects(and(land, or(inZone(Zone.Hand), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 602.2b, 115.1b: the activated ability on the Stack with its targets; CR 707.10, 707.10c: copy it, new targets may be chosen
    @Test
    fun ostraQuillRangeTutor() = card(
        name = "Ostra Quill, Range Tutor",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Kor Advisor",
        pt = "2/4",
        text = """
            When Ostra Quill enters, $mercenaryText.
            Whenever you activate an ability that targets a creature or player, copy that ability. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createMercenary()),
        triggered(on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You,
            ability = ObjectFilter.Targets(objects = Objects.Each(creature), players = Players.Each(PlayerFilter.Any)))),
            doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true))),
    )

    // CR 610.3: exiled until it leaves; CR 111.4
    @Test
    fun ropedByTheMarshal() = card(
        name = "Roped by the Marshal",
        cost = "{3}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.
            When this enchantment enters, $mercenaryText.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
        triggered(entersSelf, createMercenary()),
    )

    // CR 115.1, 601.2c: up to one target; CR 111.4
    @Test
    fun widowsAmbush() = card(
        name = "Widow's Ambush",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Return up to one target creature card from your graveyard to your hand. ${mercenaryText.replaceFirstChar { it.uppercase() }}.",
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), createMercenary(), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0))),
    )

    // CR 700.4: dies
    @Test
    fun nezumiChainsnapper() = card(
        name = "Nezumi Chainsnapper",
        cost = "{B}",
        type = "Creature — Rat Warlock",
        pt = "1/1",
        text = "When this creature dies, $mercenaryText.",
    ) parsesTo abilities(
        triggered(diesSelf, createMercenary()),
    )

    @Test
    fun thornyTwosome() = card(
        name = "Thorny Twosome",
        cost = "{2}{R}",
        type = "Creature — Plant Mercenary",
        pt = "2/2",
        text = "When this creature enters, $mercenaryText.",
    ) parsesTo abilities(
        triggered(entersSelf, createMercenary()),
    )

    // CR 701.21a: sacrifice a token as a cost; CR 702.12b
    @Test
    fun boomtownMagnate() = card(
        name = "Boomtown Magnate",
        cost = "{3}{W}",
        type = "Creature — Human Noble",
        pt = "4/2",
        text = """
            When this creature enters, $mercenaryText.
            {2}, Sacrifice a token: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createMercenary()),
        activated(cost(mana("{2}"), sacrificeA(token)), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
    )

    // CR 700.12: outlaws; CR 700.4: dies
    @Test
    fun raffishGang() = card(
        name = "Raffish Gang",
        cost = "{2}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, $mercenaryText.
            Whenever an outlaw you control dies, each opponent loses 1 life and you gain 1 life. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createMercenary()),
        triggered(dies(and(outlaw, you)), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 111.4: a named tapped artifact token with two quoted abilities; CR 106.12: tapping an artifact token for mana; CR 106.12a:
    // a mana type that artifact token produced
    @Test
    fun corvinaSkyfallSeer() = card(
        name = "Corvina, Skyfall Seer",
        cost = "{3}{R}{G}",
        type = "Legendary Creature — Cat Druid",
        pt = "4/3",
        text = """
            Whenever Corvina enters or attacks, create a tapped colorless artifact token named Skyrock with "When this token enters, it deals 2 damage to any target" and "{T}: Add one mana of any color."
            Whenever you tap an artifact token for mana, add one mana of any type that artifact token produced.
        """,
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Skyrock", colorIndicator = emptyList(), types = listOf(CardType.Artifact), abilities = listOf(
            triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget)),
            tapForAnyColor))), n(1), Players.You, tapped = true))),
        triggered(on(EventPattern.Tap(and(artifact, token, you), forMana = true)), doing(Effect.AddMana(ManaProduction(ManaKind.SameAsProduced(n(1))), Players.You))),
    )

    // CR 601.2: cast a creature spell; CR 106.1
    @Test
    fun liraelKeenPathfinder() = card(
        name = "Lirael, Keen Pathfinder",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Elf Scout",
        pt = "4/5",
        text = """
            Vigilance
            Whenever you cast a creature spell, $mercenaryText.
            {T}: Choose a color. Add one mana of that color for each different power among creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(youCast(creature), createMercenary()),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(
            Value.CountDistinct(Objects.Each(creatureYouControl), SharedQuality.Number(NumericProperty.Power)))), Players.You))),
    )

    @Test
    fun bountiedGriffin() = card(
        name = "Bountied Griffin",
        cost = "{3}{W}",
        type = "Creature — Griffin",
        pt = "3/2",
        text = """
            Flying
            When this creature dies, $mercenaryText.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelf, createMercenary()),
    )
}
