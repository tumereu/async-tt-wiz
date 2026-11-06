package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 05: black cards, first part. A cost reduction for a target dealt damage this turn (CR 601.2f), choosing
 * a Ring-bearer as a trigger (CR 701.54a), abilities of cards in the graveyard (CR 113.6), sacrificing the creature that dealt
 * damage as a cost paid on resolution with a reflexive modal trigger (CR 603.12), tokens with deathtouch, opponents who can't gain
 * life (CR 119.7), the sacrificed creature as it last existed (CR 608.2h), a Vehicle that is a creature on your turn with an Army
 * (CR 301.7), hand sizes over four exiled down (CR 402), an "or" additional cost (CR 601.2b), an exiled card's power, Army attacks,
 * creating or sacrificing tokens, and a token that's tapped and attacking, sacrificed at end of combat (CR 508.4).
 */
class LtrChunk05BlackTest {
    // CR 601.2f: the target was dealt damage this turn
    @Test
    fun bitterUndoing() = card(
        name = "Bitter Undoing",
        cost = "{3}{B}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a creature that was dealt damage this turn.
            Destroy target creature. Its controller loses 2 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), dealtDamageThisTurn)),
        spell(destroy(target("creature")), loseLife(2, Players.ControllerOf(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun theDarkBreath() = card(
        name = "The Dark Breath",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Creatures your opponents control get -1/-1 until end of turn. The Ring tempts you.",
    ) parsesTo abilities(
        spell(pump(Objects.Each(creatureOpponentControls), -1, -1), ringTempts),
    )

    // CR 701.54a: choosing a Ring-bearer
    @Test
    fun lureOfTheRing() = card(
        name = "Lure of the Ring",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, the Ring tempts you.
            Whenever you choose a creature as your Ring-bearer, you may pay 2 life. If you do, draw a card.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), ringTempts),
        triggered(on(EventPattern.RingTempts(PlayerFilter.You, chosen = true)),
            Step.Pay(cost(CostPart.PayLife(n(2))), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    @Test
    fun spiderPassPatrol() = card(
        name = "Spider Pass Patrol",
        cost = "{4}{B}",
        type = "Creature — Orc Soldier",
        text = """{1}, {T}, Sacrifice another creature: Draw a card, then create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
        pt = "4/5",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeA(and(another, creature))), draw(1), createFood()),
    )

    @Test
    fun graspThePrecious() = card(
        name = "Grasp the Precious",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = "Destroy target creature. The Ring tempts you.",
    ) parsesTo abilities(
        spell(destroy(target("creature")), ringTempts, targets = listOf(slot("creature", creature))),
    )

    // CR 701.47a
    @Test
    fun fenlandCrebain() = card(
        name = "Fenland Crebain",
        cost = "{2}{B}",
        type = "Creature — Bird Horror",
        text = """
            Flying
            When this creature enters, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, amassOrcs(2)),
    )

    @Test
    fun southronVanguard() = card(
        name = "Southron Vanguard",
        cost = "{1}{B}",
        type = "Creature — Human Warrior",
        text = "When this creature dies, amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(diesSelf, amassOrcs(1)),
    )

    // CR 113.6: an activated ability that works in the graveyard
    @Test
    fun smeagorPatientPlotter() = card(
        name = "Smeagor, Patient Plotter",
        cost = "{1}{B}",
        type = "Legendary Creature — Halfling Horror",
        text = """
            When Smeagor leaves the battlefield, the Ring tempts you.
            {B}, Sacrifice a creature: Return this card from your graveyard to your hand. Activate only as a sorcery.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), ringTempts),
        activated(cost(mana("{B}"), sacrificeA(creature)), move(Objects.Self, Zone.Hand), rules = asSorcery, functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun wretchsBite() = card(
        name = "Wretch's Bite",
        cost = "{B}",
        type = "Instant",
        text = """
            Target creature gets -2/-2 until end of turn.
            {3}{B}, Exile this card from your graveyard: The Ring tempts you. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -2, -2), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{3}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), ringTempts, rules = asSorcery),
    )

    // CR 603.12: "you may sacrifice it. When you do, choose one —"
    @Test
    fun shagulOfTheDeadTower() = card(
        name = "Shagul of the Dead Tower",
        cost = "{1}{B}",
        type = "Legendary Creature — Orc Soldier",
        text = """
            Whenever a Goblin or Orc you control deals combat damage to a player, you may sacrifice it. When you do, choose one —
            • Draw a card.
            • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(and(goblinOrOrc, you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.These(Objects.EventObject(EventRole.Source)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(modes = modes(mode(draw(1)).copy(text = "Draw a card."), mode(createTreasure()).copy(text = "Create a Treasure token.")))))))),
    )

    @Test
    fun gorthakMorgulLieutenant() = card(
        name = "Gorthak, Morgul Lieutenant",
        cost = "{3}{B}",
        type = "Legendary Creature — Human Soldier",
        text = """
            When Gorthak enters, amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
            Creature tokens you control have deathtouch.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, amassOrcs(1)),
        static(has(Keyword.Deathtouch), affects = Objects.Each(and(creature, token, you))),
    )

    // CR 119.7: opponents can't gain life; CR 608.2h: the sacrificed creature as it last existed
    @Test
    fun wormwoodTheWhisperer() = card(
        name = "Wormwood the Whisperer",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Advisor",
        text = """
            Your opponents can't gain life.
            {T}, Sacrifice another creature: Target player loses 1 life. If the sacrificed creature was legendary, amass Orcs 2.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        playerStatic(Mechanic.CantGainLife, players = Players.Each(PlayerFilter.Opponent)),
        activated(cost(tap, sacrificeA(and(another, creature), preBind = "sacrificed")), loseLife(1, Players.Target("player")),
            Step.If(Condition.ObjectMatches(Objects.Bound("sacrificed"), legendary), then = listOf(amassOrcs(2))), targets = listOf(targetPlayer)),
    )

    // CR 301.7: a Vehicle that is a creature on your turn while you control an Army
    @Test
    fun grundorTheGatebreaker() = card(
        name = "Grundor, the Gatebreaker",
        cost = "{3}{B}",
        type = "Legendary Artifact — Vehicle",
        text = """
            Trample
            As long as it's your turn and you control an Army, Grundor is an artifact creature.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
            condition = Condition.And(listOf(Condition.YourTurn, Condition.Exists(and(sub("Army"), you), atLeast = 1)))),
        Keyword.Crew(3),
    )

    // CR 113.6: returned from the graveyard while you control a legendary creature
    @Test
    fun hauntOfTheMereOfGhosts() = card(
        name = "Haunt of the Mere of Ghosts",
        cost = "{B}",
        type = "Creature — Nightmare Elf",
        text = """
            When this creature enters, scry 1.
            {2}{B}: Return this card from your graveyard to the battlefield tapped. Activate only if you control a legendary creature.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, scry(1)),
        activated(cost(mana("{2}{B}")), move(Objects.Self, Zone.Battlefield, tapped = true, postBind = "returned"),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.Exists(and(legendary, creature, you), atLeast = 1))),
    )

    // CR 205.4e: a legendary instant; the cards over four exiled from the destroyed creature's controller's hand
    @Test
    fun theKingsFatefulStrike() = card(
        name = "The King's Fateful Strike",
        cost = "{2}{B}{B}",
        type = "Legendary Instant",
        text = """
            (You may cast a legendary instant only if you control a legendary creature or planeswalker.)
            Destroy target creature. If its controller has more than four cards in hand, they exile cards from their hand equal to the difference.
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")),
            Step.If(Condition.Compare(Value.HandSize(controllerOfTarget), Comparison.Greater, n(4)), then = listOf(
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(controllerOfTarget))),
                    Value.Difference(Value.HandSize(controllerOfTarget), n(4)), Value.Difference(Value.HandSize(controllerOfTarget), n(4))),
                    bind = "exiled", chooser = controllerOfTarget),
                move(Objects.Bound("exiled"), Zone.Exile))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 601.2b: sacrifice a creature or pay {4}
    @Test
    fun lashOfTheFireDemon() = card(
        name = "Lash of the Fire Demon",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or pay {4}.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(cost(sacrificeA(creature)), manaCost("{4}")))))),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 400.7: a card put into an opponent's graveyard from the battlefield this turn; its power as it was exiled (CR 608.2h)
    @Test
    fun hildaBracegirdlePott() = card(
        name = "Hilda Bracegirdle-Pott",
        cost = "{2}{B}",
        type = "Legendary Creature — Halfling Citizen",
        text = """
            Flash
            Menace
            When Hilda enters, exile target creature card from an opponent's graveyard that was put there from the battlefield this turn, then create X Treasure tokens, where X is the exiled card's power.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Menace,
        triggered(entersSelf, move(target("card"), Zone.Exile, postBind = "exiled"), createTreasure(power(Objects.Bound("exiled"))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent), diedThisTurn)))),
    )

    // CR 701.47a: an Army you control attacking
    @Test
    fun marchFromTheDarkGate() = card(
        name = "March from the Dark Gate",
        cost = "{1}{B}",
        type = "Enchantment",
        text = "When this enchantment enters and whenever an Army you control attacks, amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.Attack(and(sub("Army"), you)))),
            amassOrcs(1)),
    )

    // CR 111.1, 701.21: creating or sacrificing a token
    @Test
    fun gloomwoodBats() = card(
        name = "Gloomwood Bats",
        cost = "{3}{B}",
        type = "Creature — Bat",
        text = """
            Flying
            Whenever you create or sacrifice a token, each opponent loses 1 life.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(listOf(EventPattern.CreateToken(you), EventPattern.Sacrifice(and(token, you)))), loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    @Test
    fun darkLandMuster() = card(
        name = "Dark Land Muster",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            You draw a card and you lose 1 life.
            Amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(draw(1), loseLife(1), amassOrcs(1)),
    )

    // CR 508.4: a token put onto the battlefield attacking; sacrificed at end of combat (CR 603.7)
    @Test
    fun darkLandTrebuchet() = card(
        name = "Dark Land Trebuchet",
        cost = "{2}{B}",
        type = "Artifact Creature — Wall",
        text = """
            Defender
            Whenever you attack with one or more Goblins and/or Orcs, create a 2/1 colorless Construct artifact creature token with flying named Ballistic Boulder that's tapped and attacking. Sacrifice that token at end of combat.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(Trigger.On(listOf(EventPattern.Attack(and(goblinOrOrc, you))), oneOrMore = true),
            Step.Do(Effect.CreateToken(boulder, n(1), Players.You, tapped = true, attacking = arcanum.api.AttackTarget.Chosen, bind = "token")),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.EndOfCombat, PlayerFilter.Any)),
                Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("token")))))))),
    )

    private val dealtDamageThisTurn = ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Object)
    private val controllerOfTarget = Players.ControllerOf(target("creature"))
    private val diedThisTurn = ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Battlefield, to = Zone.Graveyard), role = EventRole.After)
    private val boulder = arcanum.api.TokenSpec.Defined(arcanum.api.FaceDefinition("Ballistic Boulder", colorIndicator = emptyList(),
        types = listOf(CardType.Artifact, CardType.Creature), subtypes = listOf(arcanum.api.Subtype("Construct")),
        power = arcanum.api.PrintedNumber.Fixed(2), toughness = arcanum.api.PrintedNumber.Fixed(1), abilities = listOf(Keyword.Flying)))
    @Suppress("unused") private val unused = listOf(Ability.Unparsed(""), Affected.Players(Players.You), Cost(emptyList()))
}
