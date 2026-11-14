package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** DMR chunk 14: sacrifice unless you return, library searches, set power/toughness, tapping, untapping, winning. */
class DmrChunk14SacrificeSearchSetPtTapUntapWinTest {
    private fun threeColors(a: Char, b: Char, c: Char): Ability = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors = listOf(a, b, c).map { ColorRef.Literal(Color.of(it)!!) })), Players.You)))

    /** "sacrifice it unless you return [filter] you control to its owner's hand" (CR 603.2, 118.12) */
    private fun sacrificeUnlessReturn(filter: ObjectFilter, count: Int) = whenThisEnters(
        Step.Pay(Cost(listOf(CostPart.ReturnToHand(CostObjects.Choose(filter, count = n(count))))), payer = Players.You, bind = "paid"),
        Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Sacrifice(Objects.Self, by = Players.You)))),
    )

    private val nonLairLand = and(not(sub("Lair")), land, you)

    // CR 603.2, 118.12: a land that bounces a non-Lair land of its controller or is sacrificed
    @Test
    fun lairLand() = card(
        name = "Ruinous Lair", cost = null, type = "Land — Lair", text = """
            When this land enters, sacrifice it unless you return a non-Lair land you control to its owner's hand.
            {T}: Add {U}, {B}, or {R}.
        """,
    ) parsesTo abilities(sacrificeUnlessReturn(nonLairLand, 1), threeColors('U', 'B', 'R'))

    // CR 106.1b: the same shape with another colour trio
    @Test
    fun lairLandOtherColors() = card(
        name = "Hollow Wyrmden", cost = null, type = "Land — Lair", text = """
            When this land enters, sacrifice it unless you return a non-Lair land you control to its owner's hand.
            {T}: Add {G}, {W}, or {U}.
        """,
    ) parsesTo abilities(sacrificeUnlessReturn(nonLairLand, 1), threeColors('G', 'W', 'U'))

    // CR 603.2, 118.12, 602.2: three basic lands as the cost of staying; the second ability returns itself as a cost
    @Test
    fun sheepWizard() = card(
        name = "Flockbinder Mage", cost = "{2}{U}", type = "Creature — Human Wizard", pt = "0/1", text = """
            When this creature enters, sacrifice it unless you return three basic lands you control to their owner's hand.
            {T}, Return this creature to its owner's hand: Destroy target creature. It can't be regenerated. That creature's controller creates a 0/1 green Sheep creature token.
        """,
    ) parsesTo abilities(
        sacrificeUnlessReturn(and(basicLand(), you), 3),
        activated(cost(tap, CostPart.ReturnToHand(CostObjects.Self)),
            Step.Do(Effect.Destroy(target("creature"), noRegeneration = true)),
            Step.Do(Effect.CreateToken(creatureToken("Sheep", listOf(Color.Green), 0, 1), n(1), Players.ControllerOf(target("creature")))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 701.23: the found card goes to the graveyard, then the library is shuffled
    @Test
    fun graveDigger() = card(
        name = "Crypt Delve Spell", cost = "{B}", type = "Instant",
        text = "Search your library for a card, put that card into your graveyard, then shuffle.",
    ) parsesTo abilities(spell(search(cardFilter), move(Objects.Bound("found"), Zone.Graveyard), shuffle))

    // CR 701.23, 701.20: a may-search for a subtype, revealed, into the hand
    @Test
    fun goblinFetcher() = card(
        name = "Warren Matron", cost = "{2}{R}", type = "Creature — Goblin", pt = "1/1",
        text = "When this creature enters, you may search your library for a Goblin card, reveal that card, put it into your hand, then shuffle.",
    ) parsesTo abilities(whenThisEnters(Step.May(listOf(
        search(sub("Goblin"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle,
    ).flatMap { listOf(it) }, player = Players.You)))

    // CR 701.23, 305.4: the land card enters untapped (the text doesn't say tapped)
    @Test
    fun forestFetcher() = card(
        name = "Wildwood Lore", cost = "{1}{G}", type = "Sorcery",
        text = "Search your library for a Forest card, put that card onto the battlefield, then shuffle.",
    ) parsesTo abilities(spell(search(sub("Forest")), move(Objects.Bound("found"), Zone.Battlefield), shuffle))

    // CR 701.23, 508.1m, 202.3: an attack trigger with a mana value limit
    @Test
    fun zurLike() = card(
        name = "Zul the Warlock", cost = "{1}{W}{U}{B}", type = "Legendary Creature — Human Wizard", pt = "1/4", text = """
            Flying
            Whenever Zul the Warlock attacks, you may search your library for an enchantment card with mana value 3 or less, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, Step.May(listOf(
            search(and(enchantment, manaValue(Comparison.AtMost, 3))),
            move(Objects.Bound("found"), Zone.Battlefield), shuffle,
        ), player = Players.You)),
    )

    // CR 604.3, 208.2a: a characteristic-defining ability counting land cards in every graveyard
    @Test
    fun landEater() = card(
        name = "Soilgorger", cost = "{1}{G}{G}", type = "Creature — Lhurgoyf", pt = "*/*", text = """
            Trample
            Soilgorger's power and toughness are each equal to the number of land cards in all graveyards.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.SetPowerToughness(count(and(land, inZone(Zone.Graveyard))), count(and(land, inZone(Zone.Graveyard)))),
            characteristicDefining = true),
    )

    private val artifactCreatureOrLand = slot("permanent", or(artifact, creature, land))

    // CR 701.26a, 602.2: an activated tap with a mana cost
    @Test
    fun freezingRod() = card(
        name = "Frost Rod", cost = "{4}", type = "Artifact",
        text = "{1}, {T}: Tap target artifact, creature, or land.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), tapIt(target("permanent")), targets = listOf(artifactCreatureOrLand)))

    // CR 701.26a, 118.3, 602.2b: tapping an untapped creature you control is the cost (the cost choice only offers those)
    @Test
    fun oppositionLike() = card(
        name = "Resistance Field", cost = "{2}{U}{U}", type = "Enchantment",
        text = "Tap an untapped creature you control: Tap target artifact, creature, or land.",
    ) parsesTo abilities(activated(
        Cost(listOf(CostPart.TapObjects(CostObjects.Choose(creature, count = n(1))))),
        tapIt(target("permanent")), targets = listOf(artifactCreatureOrLand)))

    private fun ofTheChosenType(status: ObjectFilter) = Objects.Each(ObjectFilter.And(listOf(
        ObjectFilter.Category(ObjectCategory.Permanent),
        ObjectFilter.Type(CardTypeRef.Chosen("type")),
        status,
        ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))),
    )))

    // CR 700.2 (choice), 701.26: "the chosen type" and "that player" link the sentences
    @Test
    fun turnaroundLike() = card(
        name = "Flip Reverse", cost = "{2}{U}{U}", type = "Instant",
        text = "Choose artifact, creature, or land. Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents of that type that player controls.",
    ) parsesTo abilities(Ability.Spell(Instructions(
        targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = n(1), max = n(1))),
        steps = listOf(
            Step.Choose(Choice.CardType(among = listOf(CardType.Artifact, CardType.Creature, CardType.Land)), bind = "type", chooser = Players.You),
            Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
            Step.If(Condition.Chose("tapOrUntap", "tap"),
                then = listOf(Step.Do(Effect.Tap(ofTheChosenType(not(tapped))))),
                otherwise = listOf(Step.Do(Effect.Untap(ofTheChosenType(tapped))))),
        ),
    )))

    // CR 700.2: three modes, one chosen; CR 702.6 / 613.1f: "loses flying"
    @Test
    fun verdantCharm() = card(
        name = "Wildwood Charm", cost = "{G}", type = "Instant", text = """
            Choose one —
            • Untap target permanent.
            • Destroy target non-Aura enchantment.
            • Target creature loses flying until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = modes(
        mode(untapIt(target("permanent")), targets = listOf(slot("permanent", permanentFilter))),
        mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", and(not(sub("Aura")), enchantment)))),
        mode(untilEndOfTurn(target("creature"), Mechanic.LoseAbilities(Keyword.Flying)), targets = listOf(slot("creature", creature))),
    ))))

    // CR 701.26b, 608.2d: "up to five" is chosen on resolution; no target
    @Test
    fun driftingBird() = card(
        name = "Cloudhawk Drake", cost = "{4}{U}", type = "Creature — Drake", pt = "2/3", text = """
            Flying
            When this creature enters, untap up to five lands.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(
            Step.Choose(Choice.Objects(land, min = n(0), max = n(5)), bind = "untapped", chooser = Players.You),
            Step.Do(Effect.Untap(Objects.Bound("untapped"))),
        ),
    )

    // CR 104.2b, 603.4: the intervening "if" is checked on trigger and on resolution
    @Test
    fun enduranceTrial() = card(
        name = "Trial of Stamina", cost = "{2}{W}{W}", type = "Enchantment",
        text = "At the beginning of your upkeep, if you have 50 or more life, you win the game.",
    ) parsesTo abilities(triggered(yourUpkeep, Step.Do(Effect.WinGame(Players.You)),
        interveningIf = Condition.Compare(Value.LifeOf(Players.You), Comparison.AtLeast, n(50))))
}
