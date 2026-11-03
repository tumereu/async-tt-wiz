package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.AttackTarget
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 17: multicoloured cards: a bonus by an "A or B" condition, counters put from a creature that left (CR
 * 603.10a), counters on up to two targets, two kinds of permanents put into a graveyard, a reflexive return by the dead creature's power
 * (CR 603.12), discarding up to two then drawing that many, "and at least one other creature attack" (CR 508.1), a characteristic-defining
 * power (CR 604.3), a token entering attacking (CR 508.4), sacrifices during your turn once each turn, and modes chosen only once each
 * (CR 700.2) with a life payment and giving itself to an opponent.
 */
class TlaChunk17MulticoloredTest {
    private val drewTwo = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events)

    @Test
    fun windWandererLegacy() = card(
        name = "Wind Wanderer Legacy",
        cost = "{W}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Creatures you control with flying get +1/+1.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createClue),
        static(pt(1, 1), affects = Objects.Each(and(creature, you, kw(Keyword.Flying)))),
    )

    @Test
    fun lynxHooter() = card(
        name = "Lynx-Hooter",
        cost = "{3}{W/U}",
        type = "Creature — Cat Bird",
        text = """
            Flying
            Whenever this creature attacks, untap target artifact or creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, untapIt(target("permanent")), targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    @Test
    fun stoneRealmSoldier() = card(
        name = "Stone Realm Soldier",
        cost = "{4}{G/W}",
        type = "Creature — Human Soldier",
        text = """
            Vigilance
            When this creature enters, put a +1/+1 counter on each of up to two target creatures you control.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, counters(target("creatures")), targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
    )

    // CR 613.4c, 613.1f while either condition holds
    @Test
    fun stoneBrawlGrapplers() = card(
        name = "Stone Brawl Grapplers",
        cost = "{3}{R/G}",
        type = "Creature — Human Warrior Performer",
        text = """
            Reach
            This creature gets +1/+0 and has trample as long as you control a land creature or a land entered the battlefield under your control this turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Reach,
        static(pt(1, 0), has(Keyword.Trample), condition = Condition.Or(listOf(
            Condition.Exists(and(land, creature, you), atLeast = 1),
            Condition.HappenedThisTurn(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)))),
    )

    @Test
    fun mistyBogSpiritWarden() = card(
        name = "Misty Bog Spirit Warden",
        cost = "{1}{U}{B}",
        type = "Creature — Human Druid Ally",
        text = """
            Lifelink
            Whenever you draw your second card each turn, create a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(drawSecondCard, create(spiritToken)),
    )

    // CR 608.2h: the counters it had as it last existed (as Bloomburrow's dies trigger reads them)
    @Test
    fun moroLunSpiritOfBalance() = card(
        name = "Moro Lun, Spirit of Balance",
        cost = "{2}{W/B}{W/B}",
        type = "Legendary Creature — Bear Spirit",
        text = """
            Whenever Moro Lun enters or attacks, you may sacrifice another creature or artifact. If you do, put two +1/+1 counters on Moro Lun.
            When Moro Lun leaves the battlefield, put its counters on target creature you control.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf,
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(and(another, or(creature, artifact)), count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self, 2)))),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), doing(Effect.MoveCounters(Objects.Self, target("creature"), null,
            arcanum.api.Value.Of(arcanum.api.NumericProperty.Counters(null), Objects.Self, arcanum.api.Combine.Single))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun rookLibertyFighter() = card(
        name = "Rook, Liberty Fighter",
        cost = "{2}{R/W}{R/W}{R/W}",
        type = "Legendary Creature — Human Rebel Ally",
        text = """
            When Rook enters, he deals damage equal to the number of creatures you control to target creature an opponent controls.
            When Rook dies, put a +1/+1 counter on each of up to two target creatures.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(entersSelf, damage(count(creatureYouControl), toObjects(target("creature"))), targets = listOf(slot("creature", creatureOpponentControls))),
        triggered(diesSelf, counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 700.4: put into a graveyard from the battlefield
    @Test
    fun vosskGrandChancellor() = card(
        name = "Vossk, Grand Chancellor",
        cost = "{1}{B/G}{B/G}",
        type = "Legendary Creature — Human Advisor",
        text = "Whenever another creature you control or a land you control is put into a graveyard from the battlefield, put a +1/+1 counter on target creature you control.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(dies(and(another, or(and(creature, you), land), you)), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun courierFalcon() = card(
        name = "Courier Falcon",
        cost = "{2}{U/B}",
        type = "Creature — Bird Scout",
        text = """
            Flying
            When this creature enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            This creature gets +2/+0 as long as you've drawn two or more cards this turn.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, createClue),
        static(pt(2, 0), condition = drewTwo),
    )

    @Test
    fun feigningPlaguebearers() = card(
        name = "Feigning Plaguebearers",
        cost = "{1}{W/B}",
        type = "Creature — Human Citizen Ally",
        text = "When this creature dies, create a 1/1 white Ally creature token.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(diesSelf, create(allyToken)),
    )

    @Test
    fun scholarQuillAntiquarian() = card(
        name = "Scholar Quill, Antiquarian",
        cost = "{U/R}{U/R}",
        type = "Legendary Creature — Human Advisor Ally",
        text = """
            {T}, Discard a card: Draw a card.
            {1}, {T}, Sacrifice Scholar Quill: Return target instant or sorcery card from your graveyard to your hand. Activate only during your turn.
        """,
        pt = "0/3",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), draw(1)),
        activated(cost(mana("{1}"), tap, sacrificeSelf), move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery)))),
            rules = duringYourTurn),
    )

    // CR 603.12; the creature's power as it last existed (CR 603.10a)
    @Test
    fun dunecallerScavengers() = card(
        name = "Dunecaller Scavengers",
        cost = "{W}{B}",
        type = "Creature — Human Rogue",
        text = """
            Whenever you sacrifice another permanent, put a +1/+1 counter on this creature.
            When this creature dies, you may exile it. When you do, return target creature card with mana value less than or equal to this creature's power from your graveyard to the battlefield.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(youSacrificeAnother, counters(Objects.Self)),
        triggered(diesSelf,
            Step.Pay(cost(CostPart.Exile(CostObjects.These(enteredOne), from = Zone.Graveyard)), payer = Players.You, bind = "exiled"),
            Step.If(Condition.Did("exiled"), then = listOf(reflexive(move(target("card"), Zone.Battlefield),
                targets = listOf(slot("card", and(creature, ObjectFilter.ManaValue(Comparison.AtMost, powerOf(leftOne)), inZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You)))))))),
    )

    // discarding up to two cards, then drawing as many as were discarded
    @Test
    fun tammBoldBladeThrower() = card(
        name = "Tamm, Bold Blade-Thrower",
        cost = "{U}{R}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            When Tamm enters, discard up to two cards, then draw that many cards.
            Whenever you cast an artifact or Lesson spell, put a +1/+1 counter on Tamm.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(2)), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.You, n(2), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            doing(Effect.Draw(Players.You, arcanum.api.Value.Count(Objects.Bound("chosen"))))),
        triggered(youCast(or(artifact, lesson)), counters(Objects.Self)),
    )

    // CR 508.1: attacking with at least one other creature
    @Test
    fun tammLateralStrategist() = card(
        name = "Tamm, Lateral Strategist",
        cost = "{1}{W/U}{W/U}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Vigilance
            Whenever Tamm and at least one other creature attack, draw a card.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self), withOthers = 1)), draw(1)),
    )

    // CR 604.3, 508.4
    @Test
    fun yarraFanbladeWarrior() = card(
        name = "Yarra, Fanblade Warrior",
        cost = "{2}{G/W}{G/W}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Yarra's power is equal to the number of creatures you control.
            Whenever Yarra attacks, create a 1/1 white Ally creature token that's tapped and attacking.
        """,
        pt = "*/4",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(creatureYouControl), null), characteristicDefining = true),
        triggered(attacksSelf, create(allyToken, tapped = true, attacking = AttackTarget.Chosen)),
    )

    @Test
    fun costsOfConflict() = card(
        name = "Costs of Conflict",
        cost = "{W}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you sacrifice a permanent during your turn, create a 1/1 white Ally creature token. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createClue),
        triggered(on(EventPattern.Sacrifice(you)), create(allyToken), perTurn = 1, triggersOnlyIf = Condition.YourTurn),
    )

    @Test
    fun roamingMinstrels() = card(
        name = "Roaming Minstrels",
        cost = "{3}{R/W}",
        type = "Creature — Human Bard Ally",
        text = "Whenever this creature attacks, creatures you control get +1/+0 until end of turn.",
        pt = "2/5",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(Objects.Each(creatureYouControl), 1, 0)),
    )

    // CR 700.2 (a mode that hasn't been chosen for this object), 505.1 (the first main phase)
    @Test
    fun darrowTorn() = card(
        name = "Darrow, Torn",
        cost = "{B}{R}",
        type = "Legendary Creature — Human Rogue",
        text = """
            At the beginning of your first main phase, choose one that hasn't been chosen and you lose 2 life —
            • Draw a card.
            • Put a +1/+1 counter on Darrow.
            • Add {R}.
            • Exile Darrow, then return him to the battlefield under an opponent's control.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Ability.Triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), Instructions(
            modes = Modes(listOf(
                mode(draw(1)),
                mode(counters(Objects.Self)),
                mode(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))),
                mode(move(Objects.Self, Zone.Exile, postBind = "exiled"),
                    Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                    doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.Bound("opponent"))))),
            ), min = 1, max = 1, onlyUnchosen = true, chooser = Players.You),
            steps = listOf(loseLife(2)))),
    )
}
