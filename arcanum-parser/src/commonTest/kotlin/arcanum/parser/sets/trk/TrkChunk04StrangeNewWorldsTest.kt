package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 04: "Strange new worlds" (the number of differently named lands you control, CR 201.2) and the cards around it. */
class TrkChunk04StrangeNewWorldsTest {
    private val named = Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.Name)
    private val isSelf = ObjectFilter.Is(Objects.Self)
    private fun dies(filter: ObjectFilter) = Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))
    private val minusX = Mechanic.ModifyPowerToughness(Value.Times(named, -1), Value.Times(named, -1))
    private val revealed = Objects.Bound("revealed")
    private fun costLess() = CostChange.Less(ManaCost.parse("{1}")!!, named)
    private fun Ability.word(): Ability = when (this) {
        is Ability.Static -> copy(abilityWord = "Strange new worlds")
        is Ability.Triggered -> copy(abilityWord = "Strange new worlds")
        is Ability.Activated -> copy(abilityWord = "Strange new worlds")
        is Ability.Spell -> copy(abilityWord = "Strange new worlds")
        else -> error("no ability word on $this")
    }

    // CR 701.20 (reveal), 401.4 (the rest on the bottom in a random order), 110.4 (permanent cards)
    @Test
    fun splittingChronicle() = card(
        name = "Splitting Chronicle",
        cost = "{6}{G}{G}",
        type = "Sorcery",
        text = "Strange new worlds — Reveal the top X cards of your library, where X is the number of differently named lands you control. Put all permanent cards revealed this way onto the battlefield tapped and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, named, fromTop = true), bind = "revealed")),
            move(Objects.Where(revealed, permanentCard), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Except(revealed, Objects.Where(revealed, permanentCard)), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ).word(),
    )

    // CR 101.4 (each opponent), 608.2e, 400.7 (exiled cards stay playable only while they remain exiled)
    @Test
    fun ensignVarnek() = card(
        name = "Ensign Varnek",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Pilot",
        pt = "1/5",
        text = "Strange new worlds — When Ensign Varnek enters, each opponent exiles the top X cards of their library, where X is the number of differently named lands you control. For each opponent, choose a land card that player owns exiled this way. You may play the chosen cards for as long as they remain exiled.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.Move(Objects.LibraryCards(Players.Each(PlayerFilter.Opponent), named, fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "owner", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(land, ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("owner")))), n(1), n(1), from = Objects.Bound("exiled")),
                    bind = "chosen", chooser = Players.You))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("chosen")), Duration.Indefinitely)),
            abilityWord = "Strange new worlds"),
    )

    // CR 702.11, 115.1 (abilities, not spells, from artifact sources an opponent controls), 509.1b (can't be blocked by)
    @Test
    fun beastOfOctarii() = card(
        name = "Beast of Octarii",
        cost = "{4}{G}{G}",
        type = "Creature — Insect Beast",
        pt = "7/5",
        text = """
            Strange new worlds — When this creature enters, you gain life equal to the number of differently named lands you control.
            This creature can't be blocked by artifact creatures.
            This creature can't be the target of abilities from artifact sources your opponents control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.GainLife(Players.You, named)), abilityWord = "Strange new worlds"),
        static(Mechanic.CantBeBlockedBy(and(artifact, creature))),
        static(Mechanic.CantBeTargeted(by = and(ObjectFilter.Category(ObjectCategory.StackAbility), opponents), fromSource = artifact)),
    )

    // CR 602.5b (once each turn), 601.2f (cost reduction)
    @Test
    fun keenTerraformer() = card(
        name = "Keen Terraformer",
        cost = "{1}{G}",
        type = "Creature — Vulcan Scientist",
        pt = "2/2",
        text = "Strange new worlds — {6}: This creature gets +2/+2 until end of turn. This ability costs {X} less to activate, where X is the number of differently named lands you control. Activate only once each turn.",
    ) parsesTo abilities(
        activated(cost(mana("{6}")), pump(Objects.Self, 2, 2), rules = anyTime.copy(perTurn = 1, costChange = costLess())).word(),
    )

    // CR 604.3 (characteristic-defining), 305.2 (land plays), 401.5 (looking at the top card)
    @Test
    fun captainVossFullnessOfTime() = card(
        name = "Captain Voss, Hour of Plenty",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Human Officer Scout",
        pt = "*/*",
        text = """
            Strange new worlds — Captain Voss's power and toughness are each equal to the number of differently named lands you control.
            You may look at the top card of your library any time.
            You may play lands from the top of your library.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(power = named, toughness = named), characteristicDefining = true).word(),
        Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true))), affects = Affected.Players(Players.You)),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Play, topOnly = true, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(and(land, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    )

    // CR 601.2f
    @Test
    fun spliceAndPhase() = card(
        name = "Splice and Phase",
        cost = "{8}{U}",
        type = "Sorcery",
        text = """
            Strange new worlds — This spell costs {X} less to cast, where X is the number of differently named lands you control.
            Create two 2/2 black Romulan creature tokens. Draw two cards, then discard a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(what = isSelf, change = costLess())).word(),
        spell(create(creatureToken("Romulan", listOf(Color.Black), 2, 2), n(2)), draw(2), discard(1)),
    )

    // CR 402.2 (maximum hand size)
    @Test
    fun deepSpaceSurvey() = card(
        name = "Deep Space Survey",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = "Strange new worlds — Draw X cards, where X is the number of differently named lands you control. You have no maximum hand size until your next turn.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Draw(Players.You, named)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You), Duration.UntilYourNextTurn))).word(),
    )

    // CR 111.1 (tokens), 700.4 (dies)
    @Test
    fun galacticGamemaster() = card(
        name = "Galactic Gamemaster",
        cost = "{5}{W}",
        type = "Creature — Excalbian",
        pt = "6/6",
        text = """
            Strange new worlds — When this creature enters, create X 1/1 red Officer creature tokens, where X is the number of differently named lands you control.
            Whenever this creature or another creature you control dies, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.CreateToken(creatureToken("Officer", listOf(Color.Red), 1, 1), named, Players.You)), abilityWord = "Strange new worlds"),
        triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, creature, you))), counters(target("creature")),
            targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 702.7, 702.10, 508.1, 611.2
    @Test
    fun wraithOfTheVoid() = card(
        name = "Wraith of the Void",
        cost = "{5}{R}{R}",
        type = "Creature — Wraith",
        pt = "4/4",
        text = """
            First strike, haste
            Strange new worlds — Whenever this creature attacks, creatures you control get +X/+0 until end of turn, where X is the number of differently named lands you control.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Haste,
        triggered(attacksSelf, untilEndOfTurn(Objects.Each(and(creature, you)), Mechanic.ModifyPowerToughness(named, n(0))), abilityWord = "Strange new worlds"),
    )

    // CR 701.26 (tap), 601.2f
    @Test
    fun suusPractitioner() = card(
        name = "Suus Practitioner",
        cost = "{2}{U}",
        type = "Creature — Vulcan Officer",
        pt = "3/2",
        text = "Strange new worlds — {4}{U}, {T}: Tap target creature. This ability costs {X} less to activate, where X is the number of differently named lands you control.",
    ) parsesTo abilities(
        activated(cost(mana("{4}{U}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature)),
            rules = anyTime.copy(costChange = costLess())).word(),
    )

    // CR 107.3, 608.2c (the number is the same through the whole resolution), 701.34
    @Test
    fun razingOfPraxis() = card(
        name = "Razing of Praxis",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = "Strange new worlds — All creatures get -X/-X until end of turn, where X is the number of differently named lands you control. If X is 6 or more, create two 2/1 red Klingon Warrior creature tokens with haste.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Each(creature), minusX),
            Step.If(Condition.Compare(named, Comparison.AtLeast, n(6)),
                then = listOf(create(creatureToken("Klingon Warrior", listOf(Color.Red), 2, 1, listOf(Keyword.Haste)), n(2)))),
        ).word(),
    )

    // CR 604.3
    @Test
    fun theWanderer() = card(
        name = "The Wanderer of Worlds",
        cost = "{G}{U}",
        type = "Legendary Creature — Scout",
        pt = "*/3",
        text = """
            Reach, vigilance
            Strange new worlds — The Wanderer's power is equal to the number of differently named lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        static(Mechanic.SetPowerToughness(power = named, toughness = null), characteristicDefining = true).word(),
    )

    // CR 305.7, 614.1d (enters untapped), 601.2f
    @Test
    fun hyperspaceGarden() = card(
        name = "Hyperspace Garden",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            Lands you control enter untapped.
            When this enchantment enters, search your library for a basic land card or Planet card, reveal it, put it into your hand, then shuffle.
            Strange new worlds — {7}{U}{U}, Sacrifice this enchantment: Draw two cards. This ability costs {X} less to activate, where X is the number of differently named lands you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield), modify = listOf(Modification.EntersUntapped))),
        triggered(entersSelf, search(or(basicLand(), sub("Planet")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(cost(mana("{7}{U}{U}"), sacrificeSelf), draw(2), rules = anyTime.copy(costChange = costLess())).word(),
    )
}
