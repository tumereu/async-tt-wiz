package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 12: investigate (CR 701.16a), part 2. */
class MkmChunk12InvestigateTwoTest {
    private val sacrificeAClue = sacrificeA(sub("Clue"))
    private val sacrificeAnArtifact = sacrificeA(artifact)

    // CR 107.3a: X is announced as the ability is activated; CR 701.16a; CR 118.3 a Clue is sacrificed as a cost
    @Test
    fun alquistProof() = card(
        name = "Alquist Proof",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human Detective",
        pt = "3/3",
        text = """
            Vigilance
            When Alquist Proof enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            {X}{W}{U}{U}, {T}, Sacrifice a Clue: You draw X cards and gain X life.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, investigate()),
        Ability.Activated(cost(mana("{X}{W}{U}{U}"), tap, sacrificeAClue),
            Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Announced("X"))), Step.Do(Effect.GainLife(Players.You, Value.Announced("X")))), announced = listOf("X")),
            anyTime),
    )

    // CR 701.16a: investigate follows the pump
    @Test
    fun briskArrival() = card(
        name = "Brisk Arrival",
        cost = "{1}{W}",
        type = "Instant",
        text = """Target creature gets +2/+2 until end of turn. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2), investigate(), targets = listOf(slot("creature", creature))),
    )

    // CR 603.2: one or more cards leaving at once is one event; CR 701.16a
    @Test
    fun chalkSketch() = card(
        name = "Chalk Sketch",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """Whenever one or more creature cards leave your graveyard, create a 2/2 white and blue Detective creature token, then investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        triggered(
            Trigger.On(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            create(creatureToken("Detective", listOf(Color.White, Color.Blue), 2, 2)), investigate(),
        ),
    )

    // CR 700.4, 603.10a: a dies trigger looks back
    @Test
    fun coldCaseCleaner() = card(
        name = "Cold Case Cleaner",
        cost = "{3}{U}",
        type = "Creature — Spirit Detective",
        pt = "3/3",
        text = """
            Flying
            When this creature dies, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(diesSelf, investigate()))

    @Test
    fun deduceTwice() = card(
        name = "Deduce Twice",
        cost = "{1}{U}",
        type = "Instant",
        text = """Draw a card. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(spell(draw(1), investigate()))

    // CR 701.16a twice; CR 700.4 sacrificed this turn; CR 602.5b an activation restriction
    @Test
    fun detectivesPouch() = card(
        name = "Detective's Pouch",
        cost = "{2}{U}{R}",
        type = "Artifact",
        text = """
            When this artifact enters, investigate twice. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            {T}: Create a 1/1 colorless Thopter artifact creature token with flying. Activate only if you've sacrificed an artifact this turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, investigate(n(2))),
        Ability.Activated(cost(tap), Instructions(steps = listOf(create(thopter))),
            anyTime.copy(condition = Condition.HappenedThisTurn(EventPattern.Sacrifice(and(artifact, you)), atLeast = 1, measure = Measure.Events))),
    )

    // CR 700.2 a choice among keywords made on resolution; CR 701.21 sacrifice as a cost
    @Test
    fun ezraAgencyChief() = card(
        name = "Ezra Agency Chief",
        cost = "{1}{W}{W}{U}{U}",
        type = "Legendary Creature — Archon Detective",
        pt = "5/5",
        text = """
            Flying
            When Ezra enters, investigate twice. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            {1}, Sacrifice an artifact: Ezra gains your choice of vigilance, lifelink, or hexproof until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, investigate(n(2))),
        activated(cost(mana("{1}"), sacrificeAnArtifact),
            Step.Choose(Choice.Option(listOf("vigilance", "lifelink", "hexproof")), bind = "keyword", chooser = Players.You),
            Step.If(Condition.Chose("keyword", "vigilance"), then = listOf(gains(Objects.Self, Keyword.Vigilance)), otherwise = listOf(
                Step.If(Condition.Chose("keyword", "lifelink"), then = listOf(gains(Objects.Self, Keyword.Lifelink)), otherwise = listOf(gains(Objects.Self, Keyword.Hexproof))))),
        ),
    )

    // CR 701.21: sacrificing an artifact (a Clue token is one) triggers it; CR 122.1
    @Test
    fun gleamingGearwing() = card(
        name = "Gleaming Gearwing",
        cost = "{U}{R}",
        type = "Artifact Creature — Drake",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you sacrifice an artifact, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, investigate()),
        triggered(on(EventPattern.Sacrifice(and(artifact, you))), counters(Objects.Self)),
    )

    // CR 603.2c: one or more die at once is a single event; CR 111.1 a token isn't a nontoken creature; a once-a-turn limit
    @Test
    fun homicideInspector() = card(
        name = "Homicide Inspector",
        cost = "{1}{B}",
        type = "Creature — Human Detective",
        pt = "2/2",
        text = """Whenever one or more nontoken creatures you control die, investigate. This ability triggers only once each turn. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(and(not(token), creatureYouControl), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            instructions = Instructions(steps = listOf(investigate())),
            perTurn = 1,
        ),
    )

    // CR 121.2 the second card drawn in a turn
    @Test
    fun elephantEavesdropper() = card(
        name = "Elephant Eavesdropper",
        cost = "{3}{G}",
        type = "Creature — Elephant Detective",
        pt = "3/3",
        text = """
            When this creature enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you draw your second card each turn, this creature gets +1/+1 and gains vigilance until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, investigate()),
        triggered(drawSecondCard, pump(Objects.Self, 1, 1, has(Keyword.Vigilance))),
    )

    // CR 602.5a: the tap cost is shared by the mana ability and the investigate ability
    @Test
    fun magnifyingLens() = card(
        name = "Magnifying Lens",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add {C}.
            {4}, {T}: Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{4}"), tap), investigate()),
    )

    // CR 508.1, 603.2: attacking with three or more creatures
    @Test
    fun meddlingPupils() = card(
        name = "Meddling Pupils",
        cost = "{3}{R}{W}",
        type = "Creature — Human Detective",
        pt = "4/5",
        text = """
            Haste
            Whenever you attack with three or more creatures, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(Trigger.On(listOf(EventPattern.Attack(creatureYouControl)), oneOrMore = true, atLeast = 3), investigate()),
    )

    @Test
    fun onTheBeat() = card(
        name = "On the Beat",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = """Creatures you control get +2/+1 until end of turn. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(pump(Objects.Each(creatureYouControl), 2, 1), investigate()),
    )

    @Test
    fun theChaseGoesOn() = card(
        name = "The Chase Goes On",
        cost = "{2}{R}",
        type = "Instant",
        text = """Target creature gets +3/+0 and gains first strike until end of turn. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 0, has(Keyword.FirstStrike)), investigate(), targets = listOf(slot("creature", creature))),
    )

    // CR 701.23: the found land is put onto the battlefield tapped; CR 701.16a follows the shuffle
    @Test
    fun theyWentThatWay() = card(
        name = "They Went That Way",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle, investigate()),
    )

    @Test
    fun toxinStudy() = card(
        name = "Toxin Study",
        cost = "{B}",
        type = "Instant",
        text = """Target creature gains deathtouch and lifelink until end of turn. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
    ) parsesTo abilities(
        spell(gains(target("creature"), Keyword.Deathtouch, Keyword.Lifelink), investigate(), targets = listOf(slot("creature", creature))),
    )
}
