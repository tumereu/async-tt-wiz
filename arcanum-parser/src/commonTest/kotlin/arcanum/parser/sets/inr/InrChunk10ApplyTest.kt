package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * INR chunk 10: "until end of turn" effects (pumps, granted keywords, doubling, a Vehicle that becomes a creature) on triggers,
 * activations and spells.
 */
class InrChunk10ApplyTest {
    private val instantOrSorcery = or(instant, sorcery)
    private val human = sub("Human")
    private val spirit = sub("Spirit")
    private val vampire = sub("Vampire")
    private val creatureOrWerewolf = or(sub("Wolf"), sub("Werewolf"))
    private val yourCreatures = Objects.Each(and(creature, you))
    private val humanSoldier = creatureToken("Human Soldier", listOf(Color.White), 1, 1)
    private val doubleStep = Step.ForEach(
        each = Each.Objects(objects = Objects.Each(filter = and(creature, you))),
        bind = "creature",
        steps = listOf(doing(Effect.ApplyMechanics(
            mechanics = listOf(Mechanic.ModifyPowerToughness(
                power = Value.Of(NumericProperty.Power, Objects.Bound("creature"), Combine.Single),
                toughness = Value.Of(NumericProperty.Toughness, Objects.Bound("creature"), Combine.Single))),
            affects = Affected.Objects(Objects.Bound("creature")),
            duration = Duration.EndOfTurn))),
    )
    private val namedInGraveyard = Value.Sum(listOf(n(1), Value.Count(Objects.Each(inYourGraveyard(ObjectFilter.Named("Ancestral Fury"))))))

    // CR 107.3c: X counted on resolution (CR 201.2: the same name)
    @Test
    fun ancestralFury() = card(
        name = "Ancestral Fury",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Target creature gains trample and gets +X/+0 until end of turn, where X is 1 plus the number of cards named Ancestral Fury in your graveyard.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), has(Keyword.Trample), Mechanic.ModifyPowerToughness(namedInGraveyard, n(0))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 603.2, 507.1: at the beginning of combat on its controller's turn
    @Test
    fun bloodFog() = card(
        name = "Blood Fog",
        cost = "{3}{R}",
        type = "Enchantment",
        text = "At the beginning of combat on your turn, target creature you control gains double strike until end of turn.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, gains(target("creature"), Keyword.DoubleStrike), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 608.2c: the pumped creature deals the damage, with its power after the pump
    @Test
    fun clearAim() = card(
        name = "Clear Aim",
        cost = "{2}{G}",
        type = "Instant",
        text = "Target creature you control gets +1/+1 until end of turn. It deals damage equal to its power to target creature you don't control.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 1, 1),
            damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 603.2: "another Human you control" excludes this creature
    @Test
    fun dawnwatchDisciple() = card(
        name = "Dawnwatch Disciple",
        cost = "{1}{G}",
        type = "Creature — Human Warlock",
        pt = "2/2",
        text = "Whenever another Human you control enters, this creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(triggered(enters(and(another, human, you)), pump(Objects.Self, 1, 1)))

    // CR 205.1 ("other" excludes this creature): the trigger affects all the others at resolution
    @Test
    fun gravemarkShieldmate() = card(
        name = "Gravemark Shieldmate",
        cost = "{2}{W}",
        type = "Creature — Spirit Soldier",
        pt = "2/3",
        text = """
            Flash
            When this creature enters, other creatures you control get +0/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(Objects.Each(and(another, creature, you)), 0, 1)),
    )

    // CR 601.2i: cast triggers once the spell is on the stack
    @Test
    fun festivalCrasherCousin() = card(
        name = "Carnival Crasher",
        cost = "{1}{R}",
        type = "Creature — Devil",
        pt = "1/3",
        text = "Whenever you cast an instant or sorcery spell, this creature gets +2/+0 until end of turn.",
    ) parsesTo abilities(triggered(youCast(instantOrSorcery), pump(Objects.Self, 2, 0)))

    // CR 603.12 would need a reflexive trigger; "If you do" after an optional discard is not one (CR 608.2c)
    @Test
    fun furiousBloodsucker() = card(
        name = "Furious Bloodsucker",
        cost = "{1}{R}",
        type = "Creature — Vampire Berserker",
        pt = "1/2",
        text = """
            Trample
            At the beginning of combat on your turn, you may discard a card. If you do, this creature gets +3/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(beginningOfCombatOnYourTurn,
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(pump(Objects.Self, 3, 0)))),
    )

    @Test
    fun keeperOfPilgrims() = card(
        name = "Keeper of Pilgrims",
        cost = "{1}{W}",
        type = "Creature — Spirit Cleric",
        pt = "2/2",
        text = "When this creature enters, target creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(triggered(entersSelf, pump(target("creature"), 1, 1), targets = listOf(slot("creature", creature))))

    // CR 508.1m: "attacks or blocks" are two events of one trigger
    @Test
    fun villageCaptain() = card(
        name = "Village Captain",
        cost = "{1}{G}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = "Whenever this creature attacks or blocks, other Humans you control get +1/+1 until end of turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))),
            pump(Objects.Each(and(another, human, you)), 1, 1)),
    )

    // CR 702.122: tapping untapped creatures for a cost is not crew; CR 301.7: the Vehicle is an artifact creature until end of turn
    @Test
    fun wakeHearse() = card(
        name = "Wake Hearse",
        cost = "{2}{R}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            Trample
            Tap two untapped creatures you control: This Vehicle becomes an artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, n(2)))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
                Affected.Objects(Objects.Self), Duration.EndOfTurn))),
    )

    @Test
    fun rallyingCaptain() = card(
        name = "Rallying Captain",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "3/3",
        text = "When this creature enters, creatures you control get +1/+1 until end of turn.",
    ) parsesTo abilities(triggered(entersSelf, pump(yourCreatures, 1, 1)))

    // CR 115.1: "another target" Human, chosen as the trigger goes on the stack
    @Test
    fun pilgrimProvisioner() = card(
        name = "Pilgrim Provisioner",
        cost = "{3}{G}",
        type = "Creature — Human Scout",
        pt = "3/3",
        text = """
            Trample
            When this creature enters, another target Human you control gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, pump(target("Human"), 2, 2), targets = listOf(slot("Human", and(another, human, you)))),
    )

    // CR 115.1d: "up to two" targets, each gets the bonus
    @Test
    fun waltzOfMarkov() = card(
        name = "Waltz of Markov",
        cost = "{2}{R}{W}",
        type = "Creature — Vampire",
        pt = "1/3",
        text = """
            Flying, haste
            At the beginning of combat on your turn, up to two target creatures you control each get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Haste,
        triggered(beginningOfCombatOnYourTurn, pump(target("creatures"), 1, 0), targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
    )

    // CR 603.2 (another Spirit you control); CR 701.6 / 118.12: counter unless its controller pays X, X being this creature's power
    @Test
    fun crypticWanderer() = card(
        name = "Cryptic Wanderer",
        cost = "{U}",
        type = "Creature — Spirit",
        pt = "1/1",
        text = """
            Flying
            Whenever another Spirit you control enters, this creature gets +1/+1 until end of turn.
            Sacrifice this creature: Counter target instant or sorcery spell unless its controller pays {X}, where X is this creature's power.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(another, spirit, you)), pump(Objects.Self, 1, 1)),
        activated(Cost(listOf(CostPart.Sacrifice(CostObjects.Self))),
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = power(Objects.Self)))), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", and(instantOrSorcery, spellFilter)))),
    )

    // CR 602.1: discarding a card as a cost
    @Test
    fun hangmanConstrictor() = card(
        name = "Hangman Constrictor",
        cost = "{1}{G}",
        type = "Creature — Snake",
        pt = "2/2",
        text = """
            Reach
            Discard a card: This creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Reach, activated(cost(discardACard), pump(Objects.Self, 1, 1)))

    // CR 306.5d, 606.4: three loyalty abilities; CR 603.12: "when you do" is a reflexive trigger; CR 614.1 (counter only if a Vampire)
    @Test
    fun corinImperiousBloodlord() = card(
        name = "Corin, Imperious Bloodlord",
        cost = "{2}{B}",
        type = "Legendary Planeswalker — Corin",
        loyalty = "4",
        text = """
            [+1]: Target creature you control gains deathtouch and lifelink until end of turn. If it's a Vampire, put a +1/+1 counter on it.
            [+1]: You may sacrifice a Vampire. When you do, Corin deals 3 damage to any target and you gain 3 life.
            [−3]: You may put a Vampire creature card from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        loyalty(1, gains(target("creature"), Keyword.Deathtouch, Keyword.Lifelink),
            Step.If(Condition.ObjectMatches(target("creature"), vampire), then = listOf(counters(target("creature")))),
            targets = listOf(slot("creature", creatureYouControl))),
        loyalty(1,
            Step.Pay(Cost(listOf(sacrificeA(vampire))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(damage(3, toAny), gainLife(3), targets = listOf(anyTarget))))),
        loyalty(-3,
            Step.Choose(
                choice = Choice.Objects(filter = and(vampire, creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = n(1)),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(zone = Zone.Battlefield)))),
    )

    // CR 608.2c: the token is created only if you control an Equipment as the spell resolves
    @Test
    fun forceOfArms() = card(
        name = "Force of Arms",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. If you control an Equipment, create a 1/1 white Human Soldier creature token.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 2, 2),
            Step.If(Condition.Exists(and(equipment, you), atLeast = 1), then = listOf(create(humanSoldier))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 113.3b: an activated ability with a target restricted to attacking Wolves and Werewolves
    @Test
    fun ulrichsPack() = card(
        name = "Ulrich's Pack",
        cost = "{2}{R}",
        type = "Creature — Wolf",
        pt = "3/2",
        text = """
            Trample
            {3}{G}: Target attacking Wolf or Werewolf gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(mana("{3}{G}")), gains(target("permanent"), Keyword.Indestructible),
            targets = listOf(slot("permanent", and(creatureOrWerewolf, attacking)))),
    )

    @Test
    fun wildStrike() = card(
        name = "Wild Strike",
        cost = "{2}{R}",
        type = "Instant",
        text = "Target creature gets +1/+1 and gains double strike until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1, has(Keyword.DoubleStrike)), targets = listOf(slot("creature", creature))),
    )

    // CR 701.10a: double the power and toughness; CR 603.2: each combat is every player's
    @Test
    fun overgrowthSurge() = card(
        name = "Overgrowth Surge",
        cost = "{1}{G}{G}{G}{G}",
        type = "Enchantment",
        text = "At the beginning of each combat, double the power and toughness of each creature you control until end of turn.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), doubleStep),
    )

    // CR 700.2: a choice of modes; CR 702.12: indestructible; the second mode targets a creature with toughness 4 or greater
    @Test
    fun resoluteStance() = card(
        name = "Resolute Stance",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
            • Destroy target creature with toughness 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creature))),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtLeast, n(4)))))),
        ))),
    )
}
