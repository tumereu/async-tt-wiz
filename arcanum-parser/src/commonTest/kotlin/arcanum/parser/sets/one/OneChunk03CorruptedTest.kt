package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 03: Corrupted, "an opponent has three or more poison counters" (counters on a player, CR 122.1f) as the
 * condition of statics, intervening ifs (CR 603.4), activation limits (CR 602.5b), cost reductions (CR 601.2f) and "instead" paragraphs
 * (CR 608.2c); "each opponent who has …" (only those opponents); "its total toxic value" (CR 702.164c). "Corrupted" is an ability word of
 * ONE that the pinned CR 207.2c list no longer names: a label without rules meaning.
 */
class OneChunk03CorruptedTest {
    private val toxic = ObjectFilter.HasKeyword(Keyword.Toxic.Kind)
    private val atYourUpkeep = stepBegins(TurnStep.Upkeep, PlayerFilter.You)
    /** "a 3/3 green Phyrexian Beast creature token with toxic 1" */
    private val beast = creatureToken("Phyrexian Beast", listOf(Color.Green), 3, 3, abilities = listOf(Keyword.Toxic(1)))
    private val ifCorrupted = ActivationRules(Timing.Any, PlayerFilter.You, condition = corrupted)
    private val exileCreature = move(target("creature"), Zone.Exile)

    private fun Ability.word(): Ability = when (this) {
        is Ability.Static -> copy(abilityWord = "Corrupted")
        is Ability.Activated -> copy(abilityWord = "Corrupted")
        is Ability.Triggered -> copy(abilityWord = "Corrupted")
        is Ability.Spell -> copy(abilityWord = "Corrupted")
        else -> this
    }

    // CR 608.2c: the exile with no mana value limit replaces the limited one while the target's controller has three or more poison counters
    @Test
    fun blightOfJudgment() = card(
        name = "Blight of Judgment",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Exile target creature if it has mana value 3 or less.
            Corrupted — Exile that creature instead if its controller has three or more poison counters.
        """,
    ) parsesTo abilities(
        spell(
            Step.If(
                condition = hasPoison(Players.ControllerOf(target("creature"))),
                then = listOf(exileCreature),
                otherwise = listOf(Step.If(Condition.ObjectMatches(target("creature"), manaValue(Comparison.AtMost, 3)), then = listOf(exileCreature))),
            ),
            targets = listOf(slot("creature", creature)),
        ).word(),
    )

    // CR 613.1f: double strike while an opponent has three or more poison counters
    @Test
    fun heraldOfTheIchorHost() = card(
        name = "Herald of the Ichor Host",
        cost = "{4}{W}{W}",
        type = "Creature — Phyrexian Angel",
        pt = "4/4",
        text = """
            Flying
            Corrupted — As long as an opponent has three or more poison counters, this creature has double strike.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.DoubleStrike), condition = corrupted).word(),
    )

    @Test
    fun marrowpickSkirge() = card(
        name = "Marrowpick Skirge",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Imp",
        pt = "2/2",
        text = """
            Flying
            Corrupted — As long as an opponent has three or more poison counters, this creature has deathtouch and lifelink.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.Deathtouch, Keyword.Lifelink), condition = corrupted).word(),
    )

    // CR 608.2c, 118.12a: corrupted, the spell is countered with no payment offered; otherwise its controller may pay {2}
    @Test
    fun hastenTheSilence() = card(
        name = "Hasten the Silence",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Counter target spell unless its controller pays {2}.
            Corrupted — Counter that spell instead if its controller has three or more poison counters.
        """,
    ) parsesTo abilities(
        spell(
            Step.If(
                condition = hasPoison(Players.ControllerOf(target("spell"))),
                then = listOf(doing(Effect.Counter(target("spell")))),
                otherwise = listOf(
                    Step.Pay(manaCost("{2}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                    Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
                ),
            ),
            targets = listOf(slot("spell", spellFilter)),
        ).word(),
    )

    // CR 602.5b: two activation limits at once
    @Test
    fun clatteringScuttler() = card(
        name = "Clattering Scuttler",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Rat",
        pt = "1/4",
        text = "Corrupted — Sacrifice an artifact or creature: Draw a card. Activate only if an opponent has three or more poison counters and only once each turn.",
    ) parsesTo abilities(
        activated(cost(sacrificeA(or(artifact, creature))), draw(1), rules = ifCorrupted.copy(perTurn = 1)).word(),
    )

    // CR 601.2f
    @Test
    fun warpedInquiry() = card(
        name = "Warped Inquiry",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Corrupted — This spell costs {2} less to cast if an opponent has three or more poison counters.
            Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1))),
            condition = corrupted).word(),
        spell(draw(2)),
    )

    // CR 102.3: only the opponents with three or more poison counters lose life
    @Test
    fun nourishTheBlight() = card(
        name = "Nourish the Blight",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            You draw three cards and you lose 3 life.
            Corrupted — Each opponent who has three or more poison counters loses 3 life.
        """,
    ) parsesTo abilities(
        spell(draw(3), loseLife(3)),
        spell(loseLife(3, eachCorruptedOpponent)).word(),
    )

    // CR 602.5b: a graveyard ability with an activation condition
    @Test
    fun sinewlessDuelist() = card(
        name = "Sinewless Duelist",
        cost = "{1}{B}",
        type = "Creature — Phyrexian Skeleton",
        pt = "2/2",
        text = "Corrupted — {2}{B}: Return this card from your graveyard to the battlefield tapped. You lose 1 life. Activate only if an opponent has three or more poison counters.",
    ) parsesTo abilities(
        activated(cost(mana("{2}{B}")), move(Objects.Self, Zone.Battlefield, tapped = true, postBind = "returned"), loseLife(1), rules = ifCorrupted).word(),
    )

    // CR 603.4; CR 702.164c: the chosen creature's total toxic value
    @Test
    fun colossusBroodPit() = card(
        name = "Colossus Brood Pit",
        cost = "{4}{G}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create two 3/3 green Phyrexian Beast creature tokens with toxic 1. (Players dealt combat damage by them also get a poison counter.)
            Corrupted — At the beginning of your upkeep, if an opponent has three or more poison counters, choose a creature you control, then draw cards equal to its total toxic value.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(beast, count = n(2))),
        triggered(atYourUpkeep,
            Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Draw(Players.You, Value.Of(NumericProperty.KeywordTotal(Keyword.Toxic.Kind), Objects.Bound("chosen"), Combine.Single))),
            interveningIf = corrupted, abilityWord = "Corrupted"),
    )

    // CR 603.4
    @Test
    fun fangSoarer() = card(
        name = "Fang Soarer",
        cost = "{1}{W}",
        type = "Artifact Creature — Phyrexian Construct",
        pt = "1/3",
        text = """
            Flying
            Corrupted — Whenever this creature attacks, if an opponent has three or more poison counters, creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, pump(Objects.Each(creatureYouControl), 1, 1), interveningIf = corrupted, abilityWord = "Corrupted"),
    )

    // CR 102.3: each opponent with three or more poison counters, checked as the trigger resolves
    @Test
    fun oilboundOrrery() = card(
        name = "Oilbound Orrery",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            Corrupted — Whenever this artifact becomes tapped, each opponent who has three or more poison counters loses 1 life.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        triggered(on(arcanum.api.EventPattern.Tap(ObjectFilter.Is(Objects.Self))), loseLife(1, eachCorruptedOpponent), abilityWord = "Corrupted"),
    )

    // CR 603.4 doesn't apply ("unless" is checked as it resolves)
    @Test
    fun gluttonousBonetitan() = card(
        name = "Gluttonous Bonetitan",
        cost = "{2}{B}{B}",
        type = "Creature — Phyrexian Horror",
        pt = "6/6",
        text = "Corrupted — When this creature enters, sacrifice a creature unless an opponent has three or more poison counters.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.If(Condition.Not(corrupted), then = listOf(
                Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "sacrificed", chooser = Players.You),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))))),
            abilityWord = "Corrupted"),
    )

    // CR 602.5b
    @Test
    fun tendonWaltzer() = card(
        name = "Tendon Waltzer",
        cost = "{W}",
        type = "Creature — Phyrexian Soldier",
        pt = "1/1",
        text = """
            {3}{W}, {T}: Tap target creature.
            Corrupted — {W}, {T}: Tap target creature. Activate only if an opponent has three or more poison counters.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{3}{W}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{W}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature)), rules = ifCorrupted).word(),
    )

    // CR 613.1f: creatures you control with toxic (any toxic value) have lifelink
    @Test
    fun miteWarren() = card(
        name = "Mite Warren",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, you lose 1 life and create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."
            Corrupted — As long as an opponent has three or more poison counters, creatures you control with toxic have lifelink.
        """,
    ) parsesTo abilities(
        triggered(atYourUpkeep, loseLife(1), create(creatureToken("Phyrexian Mite", emptyList(), 1, 1,
            abilities = listOf(Keyword.Toxic(1), Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = arcanum.api.Affected.Objects(Objects.Self))),
            types = listOf(CardType.Artifact, CardType.Creature)), bind = "token")),
        static(has(Keyword.Lifelink), affects = Objects.Each(and(creature, you, toxic)), condition = corrupted).word(),
    )

    // CR 106.6: mana only for Phyrexian creature spells; CR 602.5b
    @Test
    fun theOilcoreVault() = card(
        name = "The Oilcore Vault",
        cost = null,
        type = "Land — Sphere",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast Phyrexian creature spells.
            Corrupted — {T}: Target 1/1 creature gets +2/+1 until end of turn. Activate only if an opponent has three or more poison counters.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1), restriction = and(spellFilter, sub("Phyrexian"), creature)), Players.You))),
        activated(cost(tap), pump(target("creature"), 2, 1),
            targets = listOf(slot("creature", and(creature, power(Comparison.Equal, 1), ObjectFilter.Toughness(Comparison.Equal, n(1))))), rules = ifCorrupted).word(),
    )

    // CR 702.34a: flashback from the graveyard while an opponent has three or more poison counters
    @Test
    fun contagionBrood() = card(
        name = "Contagion Brood",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Create a 3/3 green Phyrexian Beast creature token with toxic 1. (Players dealt combat damage by it also get a poison counter.)
            Corrupted — As long as an opponent has three or more poison counters and this card is in your graveyard, it has flashback {2}{G}. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(beast)),
        static(has(Keyword.Flashback(manaCost("{2}{G}"))),
            condition = Condition.And(listOf(corrupted, Condition.ObjectMatches(Objects.Self, inZone(Zone.Graveyard))))).copy(functionsIn = listOf(Zone.Graveyard)).word(),
    )

    // CR 603.4: no trigger without an opponent at three or more poison counters
    @Test
    fun dissectionPreacher() = card(
        name = "Dissection Preacher",
        cost = "{3}{W}{B}",
        type = "Creature — Phyrexian Cleric",
        pt = "4/4",
        text = """
            Vigilance
            Corrupted — When this creature enters, if an opponent has three or more poison counters, destroy target creature or planeswalker an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, destroy(target("permanent")),
            targets = listOf(slot("permanent", and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), opponents))),
            interveningIf = corrupted, abilityWord = "Corrupted"),
    )

    // CR 613.4c: +1/+1 always and an additional +1/+0 (with first strike, CR 613.1f) while corrupted
    @Test
    fun fanaticsResolve() = card(
        name = "Fanatic's Resolve",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets +1/+1.
            Corrupted — As long as an opponent has three or more poison counters, enchanted creature gets an additional +1/+0 and has first strike.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        static(pt(1, 1), affects = Objects.AttachedTo(Objects.Self)),
        static(pt(1, 0), has(Keyword.FirstStrike), affects = Objects.AttachedTo(Objects.Self), condition = corrupted).word(),
    )
}
