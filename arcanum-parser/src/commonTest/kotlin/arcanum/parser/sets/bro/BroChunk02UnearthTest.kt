package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.FaceDefinition
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.CostObjects
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.PlayKind
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Brothers' War chunk 02: artifact cards with Unearth (CR 702.84) and the wordings of their other abilities: a destroyed
 * permanent's controller creating a tapped Powerstone, a trigger that works from the Stack and the battlefield, entering from
 * your own graveyard, and "choose three" modes.
 */
class BroChunk02UnearthTest {
    private val unearthReminder = "({cost}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)"
    private fun reminder(cost: String) = unearthReminder.replace("{cost}", cost)
    private val powerstoneReminder = "(It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")"
    private val yourHand = Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val artifactCreatures = and(artifact, creature, you)

    // CR 702.84a, 111.10h, 608.2h: the destroyed permanent's controller, as it last existed, creates the tapped token
    @Test
    fun rubbleColossus() = card(
        name = "Rubble Colossus",
        cost = "{8}",
        type = "Artifact Creature — Construct",
        text = """
            Trample
            When you cast this spell and whenever this creature attacks, destroy up to one target nonland permanent. Its controller creates a tapped Powerstone token. $powerstoneReminder
            Unearth {8}
        """,
        pt = "8/8",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(Trigger.On(listOf(EventPattern.Cast(isSelf, PlayerFilter.You), EventPattern.Attack(isSelf))),
            destroy(target("permanent")),
            createPowerstone(players = Players.ControllerOf(target("permanent"))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter), min = 0))),
        Keyword.Unearth(manaCost("{8}")),
    )

    // CR 702.84a, 603.6a, 108.3: it enters from its owner's graveyard; CR 712 and 701.42: the front face of a meld pair
    @Test
    fun emberWyrmEngine() = cardFaces(Layout.Meld("Orrin, Claimed by Rot"),
        card(
            name = "Ember Wyrm Engine",
            cost = "{3}",
            type = "Artifact Creature — Phyrexian Dragon",
            text = """
                Double strike
                When this creature enters from your graveyard, you may discard your hand. If you do, draw three cards.
                Unearth {3}{R}{R}
                (Melds with Orrin, Claimed by Rot.)
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.DoubleStrike,
            triggered(Trigger.On(EventPattern.ZoneChange(and(isSelf, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard, to = Zone.Battlefield)),
                Step.Pay(cost(CostPart.Discard(CostObjects.These(yourHand))), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), draw(3))),
            Keyword.Unearth(manaCost("{3}{R}{R}")),
        ),
        card(
            name = "Orrin, Lost to Rust",
            cost = null,
            type = "Legendary Artifact Creature — Phyrexian Artificer",
            colorIndicator = "R",
            text = """
                Whenever Orrin enters or attacks, choose three —
                • Target opponent discards two cards.
                • Orrin deals 3 damage to any target.
                • Destroy target artifact or planeswalker.
                • Creatures you control gain menace and trample until end of turn.
                • Creatures you don't control get -1/-1 until end of turn.
                • Create two tapped Powerstone tokens. $powerstoneReminder
            """,
            pt = "9/9",
        ) to abilities(
            Ability.Triggered(entersOrAttacksSelf, Instructions(modes = modes(min = 3, max = 3, options = arrayOf(
                mode(Step.Do(Effect.Discard(Players.Target("opponent"), n(2), ObjectFilter.Any)), targets = listOf(targetOpponent.copy(name = "opponent"))),
                mode(damage(3, toAny), targets = listOf(anyTarget)),
                mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, ObjectFilter.Type(arcanum.api.CardType.Planeswalker))))),
                mode(gains(Objects.Each(creatureYouControl), Keyword.Menace, Keyword.Trample)),
                mode(pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))), -1, -1)),
                mode(createPowerstone(n(2))),
            )))),
        ),
    )

    // CR 602.1: "{R}: Artifact creatures you control get +1/+0 until end of turn"
    @Test
    fun forgewingDrake() = card(
        name = "Forgewing Drake",
        cost = "{6}",
        type = "Artifact Creature — Dragon",
        text = """
            Flying
            {R}: Artifact creatures you control get +1/+0 until end of turn.
            Unearth {3}{R}{R} ${reminder("{3}{R}{R}")}
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{R}")), pump(Objects.Each(artifactCreatures), 1, 0)),
        Keyword.Unearth(manaCost("{3}{R}{R}")),
    )

    // CR 702.84a, 701.13a
    @Test
    fun gravepickerRig() = card(
        name = "Gravepicker Rig",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        text = """
            Whenever this creature attacks, exile target card from a graveyard.
            Unearth {1}{B} ${reminder("{1}{B}")}
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        Keyword.Unearth(manaCost("{1}{B}")),
    )

    // CR 702.84a
    @Test
    fun skirmishRunner() = card(
        name = "Skirmish Runner",
        cost = "{1}",
        type = "Artifact Creature — Construct",
        text = """
            {2}, Sacrifice this creature: Draw a card.
            Unearth {U} ${reminder("{U}")}
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), sacrificeSelf), draw(1)),
        Keyword.Unearth(manaCost("{U}")),
    )

    // CR 118.12: "tap this creature unless you pay {3}"
    @Test
    fun hulkingCrusher() = card(
        name = "Hulking Crusher",
        cost = "{7}",
        type = "Artifact Creature — Construct",
        text = """
            Menace
            At the beginning of your upkeep, tap this creature unless you pay {3}.
            Unearth {6}{R}{R} ${reminder("{6}{R}{R}")}
        """,
        pt = "8/6",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            Step.Pay(cost(mana("{3}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), tapIt(Objects.Self))),
        Keyword.Unearth(manaCost("{6}{R}{R}")),
    )

    // CR 602.5d: activate only as a sorcery; the X is paid, the token is X/X (CR 107.3)
    @Test
    fun moldOfTheClayworker() = card(
        name = "Mold of the Clayworker",
        cost = "{2}",
        type = "Artifact",
        text = """
            {X}, {T}, Sacrifice this artifact: Create an X/X colorless Golem artifact creature token. Activate only as a sorcery.
            Unearth {2}{G} ${reminder("{2}{G}").replace("It gains haste. ", "")}
        """,
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{X}"), tap, sacrificeSelf),
            Instructions(steps = listOf(create(golemX)), announced = listOf("X")), rules = asSorcery),
        Keyword.Unearth(manaCost("{2}{G}")),
    )

    // CR 508.1d: "attacks each combat if able"
    @Test
    fun chargingJuggernaut() = card(
        name = "Charging Juggernaut",
        cost = "{5}",
        type = "Artifact Creature — Juggernaut",
        text = """
            Trample
            This creature attacks each combat if able.
            Unearth {5}{R} ${reminder("{5}{R}")}
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.MustAttack()),
        Keyword.Unearth(manaCost("{5}{R}")),
    )

    // CR 608.2c, 611.2: "choose one of them", playable until the end of your next turn
    @Test
    fun scribesWritingDesk() = card(
        name = "Scribe's Writing Desk",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}, {T}, Sacrifice this artifact: Exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.
            Unearth {1}{R} ${reminder("{1}{R}").replace("It gains haste. ", "")}
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeSelf),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("chosen")), Duration.UntilEndOfYourNextTurn))),
        Keyword.Unearth(manaCost("{1}{R}")),
    )

    // CR 305.1, 601.2: lands are played from the graveyard, using a land play
    @Test
    fun everlastingColossus() = card(
        name = "Everlasting Colossus",
        cost = "{5}",
        type = "Artifact Creature — Beast",
        text = """
            You may play lands from your graveyard.
            Unearth {G}{G} ${reminder("{G}{G}")}
        """,
        pt = "2/7",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(inYourGraveyard(land)))),
        Keyword.Unearth(manaCost("{G}{G}")),
    )

    // CR 603.4: intervening "if" of the end step trigger, checked as it would trigger and on resolution
    @Test
    fun squadForge() = card(
        name = "Squad Forge",
        cost = "{5}",
        type = "Artifact Creature — Construct",
        text = """
            At the beginning of your end step, if you control two or more other creatures, draw a card.
            {3}{W}: Create a 1/1 colorless Soldier artifact creature token.
            Unearth {2}{W}{W}
        """,
        pt = "4/6",
    ) parsesTo abilities(
        triggered(yourEndStep, draw(1), interveningIf = Condition.Exists(and(another, creature, you), atLeast = 2)),
        activated(cost(mana("{3}{W}")), create(colorlessSoldier)),
        Keyword.Unearth(manaCost("{2}{W}{W}")),
    )

    // CR 702.84a
    @Test
    fun patchedSkyflitter() = card(
        name = "Patched Skyflitter",
        cost = "{3}",
        type = "Artifact Creature — Thopter",
        text = """
            Flying
            Unearth {2} ${reminder("{2}")}
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Unearth(manaCost("{2}")),
    )

    // CR 111.4: the token is a colorless artifact creature
    @Test
    fun salvageSquadmate() = card(
        name = "Salvage Squadmate",
        cost = "{4}",
        type = "Artifact Creature — Soldier",
        text = """
            When this creature enters, create a 1/1 colorless Soldier artifact creature token.
            Unearth {2}{W} ${reminder("{2}{W}")}
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(entersSelf, create(colorlessSoldier)),
        Keyword.Unearth(manaCost("{2}{W}")),
    )

    // CR 608.2c: "you may discard a card. If you do, draw a card"
    @Test
    fun rustfurHound() = card(
        name = "Rustfur Hound",
        cost = "{2}",
        type = "Artifact Creature — Dog",
        text = """
            When this creature enters, you may discard a card. If you do, draw a card.
            Unearth {1}{R} ${reminder("{1}{R}")}
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1))),
        Keyword.Unearth(manaCost("{1}{R}")),
    )

    @Test
    fun wreckyardBrute() = card(
        name = "Wreckyard Brute",
        cost = "{4}",
        type = "Artifact Creature — Phyrexian Horror",
        text = """
            When this creature enters, you draw a card and you lose 1 life.
            Unearth {3}{B} ${reminder("{3}{B}")}
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), loseLife(1)),
        Keyword.Unearth(manaCost("{3}{B}")),
    )

    // CR 122.1
    @Test
    fun clockworkMacaque() = card(
        name = "Clockwork Macaque",
        cost = "{3}",
        type = "Artifact Creature — Ape",
        text = """
            When this creature enters, put two +1/+1 counters on target creature you control.
            Unearth {2}{G}{G} ${reminder("{2}{G}{G}")}
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature"), 2), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Unearth(manaCost("{2}{G}{G}")),
    )

    // CR 603.12: the reflexive trigger's target is chosen when it is put on the Stack
    @Test
    fun dreadCatapult() = card(
        name = "Dread Catapult",
        cost = "{7}",
        type = "Artifact Creature — Construct",
        text = """
            Menace
            Whenever this creature attacks, you may sacrifice another creature. When you do, destroy target creature an opponent controls.
            Unearth {3}{B}{B} ${reminder("{3}{B}{B}")}
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(1))))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("creature", creatureOpponentControls)), steps = listOf(destroy(target("creature")))))))),
        Keyword.Unearth(manaCost("{3}{B}{B}")),
    )

    // CR 603.10a: a leaves-the-battlefield trigger looks back in time
    @Test
    fun archivistsAutomaton() = card(
        name = "Archivist's Automaton",
        cost = "{5}",
        type = "Artifact Creature — Construct",
        text = """
            When this creature leaves the battlefield, you gain 2 life.
            Unearth {3}{W} ${reminder("{3}{W}")}
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield)), gainLife(2)),
        Keyword.Unearth(manaCost("{3}{W}")),
    )

    // CR 115.1: "another target creature": the target can't be this creature
    @Test
    fun tinplateVanguard() = card(
        name = "Tinplate Vanguard",
        cost = "{1}",
        type = "Artifact Creature — Soldier",
        text = """
            Whenever this creature attacks, another target creature you control gets +1/+1 until end of turn.
            Unearth {W} ${reminder("{W}")}
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(target("creature"), 1, 1), targets = listOf(slot("creature", and(another, creatureYouControl)))),
        Keyword.Unearth(manaCost("{W}")),
    )

    private val colorlessSoldier = creatureToken("Soldier", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
    private val golemX = TokenSpec.Defined(FaceDefinition("Golem Token", types = listOf(CardType.Artifact, CardType.Creature), subtypes = listOf(Subtype("Golem")),
        colorIndicator = emptyList(), power = PrintedNumber.X, toughness = PrintedNumber.X), power = Value.Announced("X"), toughness = Value.Announced("X"))
}
