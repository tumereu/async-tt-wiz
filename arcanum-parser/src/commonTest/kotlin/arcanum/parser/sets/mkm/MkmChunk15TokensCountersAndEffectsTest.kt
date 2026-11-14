package arcanum.parser.sets.mkm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 15: counter, create-token, destroy, discard, draw, gain-abilities, lose-life and modify-pt cards. */
class MkmChunk15TokensCountersAndEffectsTest {
    private val detectiveToken = creatureToken("Detective", listOf(Color.White, Color.Blue), 2, 2)
    private val plant = creatureToken("Plant", listOf(Color.Green), 0, 1)
    private val detective = sub("Detective")

    // CR 118.12, 614.15: the exile is a replacement of the spell's own effect
    @Test
    fun flatDenial() = card(
        name = "Flat Denial Test",
        cost = "{W}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {3}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.",
    ) parsesTo abilities(
        spell(*counterUnless("{3}").toTypedArray(), targets = listOf(slot("spell", spellFilter)),
            replacements = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile)))))),
    )

    // CR 111.1: the Plant is created first, the count is taken after it exists (CR 608.2c)
    @Test
    fun plantChorus() = card(
        name = "Plant Chorus Test",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Create a 0/1 green Plant creature token, then draw cards equal to the number of differently named creature tokens you control.",
    ) parsesTo abilities(
        spell(create(plant), doing(Effect.Draw(Players.You, Value.CountDistinct(Objects.Each(and(creature, token, you)), SharedQuality.Name)))),
    )

    @Test
    fun hiddenSource() = card(
        name = "Hidden Source Test",
        cost = "{2}{W}",
        type = "Creature — Human Citizen",
        pt = "1/1",
        text = """
            When this creature enters, create a 2/2 white and blue Detective creature token.
            {3}, {T}: Target Detective you control gets +2/+0 and gains vigilance until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(detectiveToken)),
        activated(cost(mana("{3}"), tap), pump(target("Detective"), 2, 0, has(Keyword.Vigilance)),
            targets = listOf(slot("Detective", and(detective, you))), rules = asSorcery),
    )

    // CR 111.1, 110.5b: the Bats enter tapped; the ability works from the graveyard
    @Test
    fun duskOnlooker() = card(
        name = "Dusk Onlooker Test",
        cost = "{1}{B}",
        type = "Creature — Vampire",
        pt = "1/3",
        text = """
            Flying
            {2}{B}{B}, Exile this card from your graveyard: Create two tapped 1/1 black Bat creature tokens with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{2}{B}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            Step.Do(Effect.CreateToken(creatureToken("Bat", listOf(Color.Black), 1, 1, listOf(Keyword.Flying)), n(2), Players.You, tapped = true))),
    )

    // CR 701.8: its controller searches, not the caster
    @Test
    fun bitterTrophy() = card(
        name = "Bitter Trophy Test",
        cost = "{B}{G}",
        type = "Instant",
        text = "Destroy target permanent an opponent controls. Its controller may search their library for a basic land card, put it onto the battlefield, then shuffle.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")),
            Step.May(listOf(
                doing(Effect.Search(Players.ControllerOf(target("permanent")), basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found",
                    searcher = Players.ControllerOf(target("permanent")))),
                doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, controller = Players.ControllerOf(target("permanent"))))),
                doing(Effect.Shuffle(Players.ControllerOf(target("permanent"))))), player = Players.ControllerOf(target("permanent"))),
            targets = listOf(slot("permanent", and(permanentFilter, opponents)))),
    )

    // CR 700.2: modes; the first has up to one target (CR 601.2c), the second sacrifices as it resolves and "if you do" follows
    @Test
    fun rustyOrangutan() = card(
        name = "Rusty Orangutan Test",
        cost = "{2}{R}",
        type = "Creature — Ape",
        pt = "2/2",
        text = """
            Reach
            When this creature enters, choose one —
            • Destroy up to one target artifact.
            • Sacrifice an artifact. If you do, put two +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact, min = 0))),
            mode(Step.Choose(Choice.Objects(and(artifact, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"), postBind = "sacrificed")),
                ifThen(Condition.Compare(Value.Count(Objects.Bound("sacrificed")), Comparison.AtLeast, n(1)), counters(Objects.Self, 2)))))),
    )

    @Test
    fun tokenWhipcracker() = card(
        name = "Token Whipcracker Test",
        cost = "{B}{G}",
        type = "Creature — Insect Assassin",
        pt = "3/2",
        text = """
            Reach
            When this creature enters, destroy target token an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, destroy(target("token")), targets = listOf(slot("token", and(token, opponents)))),
    )

    // CR 700.2, 701.9
    @Test
    fun mentalConfiscation() = card(
        name = "Mental Confiscation Test",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target opponent discards two cards.
            • Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(discard(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
            mode(doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
                Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetOpponent))))),
    )

    @Test
    fun deepCriminologists() = card(
        name = "Deep Criminologists Test",
        cost = "{4}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "4/5",
        text = "Whenever this creature enters or attacks, you may sacrifice an artifact. If you do, draw a card.",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, Step.Pay(cost(sacrificeA(artifact)), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(1))),
    )

    // CR 603.2: one ability, two triggering events
    @Test
    fun projectorInspector() = card(
        name = "Projector Inspector Test",
        cost = "{2}{U}",
        type = "Creature — Human Detective",
        pt = "3/2",
        text = "Whenever this creature or another Detective you control enters and whenever a Detective you control is turned face up, you may draw a card. If you do, discard a card.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(listOf(
            EventPattern.ZoneChange(or(isSelf, and(another, detective, you)), to = Zone.Battlefield),
            EventPattern.TurnedFaceUp(and(detective, you)))),
            Instructions(steps = listOf(Step.Pay(steps = listOf(draw(1)), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), discard(1))))),
    )

    // CR 608.2c: either cost makes the pump and the draw happen
    @Test
    fun hastyDetective() = card(
        name = "Hasty Detective Test",
        cost = "{1}{R}",
        type = "Creature — Devil Detective",
        pt = "0/3",
        text = "Whenever this creature attacks, you may sacrifice an artifact or discard a card. If you do, draw a card and this creature gets +2/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(CostPart.OneOf(listOf(cost(sacrificeA(artifact)), cost(discardACard)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1), untilEndOfTurn(Objects.Self, pt(2, 0)))),
    )

    // CR 701.9: discarding a card with the right type avoids the second discard
    @Test
    fun steamScholar() = card(
        name = "Steam Scholar Test",
        cost = "{2}{U}",
        type = "Creature — Weird Detective",
        pt = "2/2",
        text = """
            Flying, vigilance
            When this creature enters, draw two cards. Then discard two cards unless you discard an instant or sorcery card or a creature card with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(entersSelf, draw(2),
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(or(instant, sorcery, and(creature, kw(Keyword.Flying))), count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(discard(2)))),
    )

    // CR 613.1f, 111.1: the granted mana ability belongs to creature tokens; the second ability is a batch trigger
    @Test
    fun hollowRoots() = card(
        name = "Hollow Roots Test",
        cost = "{B}{G}",
        type = "Enchantment",
        text = """
            Creature tokens you control have "{T}: Add one mana of any color."
            Whenever one or more creature cards leave your graveyard, create a 0/1 green Plant creature token, then put a +1/+1 counter on each Plant you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.GainAbilities(listOf(Ability.Activated(cost(tap), Instructions(steps = listOf(
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))), anyTime))), affects = Objects.Each(and(creature, token, you))),
        triggered(Trigger.On(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            create(plant), counters(Objects.Each(and(sub("Plant"), you)))),
    )

    // CR 603.2
    @Test
    fun sippingVampire() = card(
        name = "Sipping Vampire Test",
        cost = "{2}{W}{B}",
        type = "Creature — Vampire Rogue",
        pt = "2/4",
        text = """
            Flying
            Whenever another creature you control with power 2 or less enters, each opponent loses 1 life and you gain 1 life.
            {5}{W}{B}: Creatures you control with power 2 or less gain deathtouch and lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(another, creature, you, power(Comparison.AtMost, 2))), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
        activated(cost(mana("{5}{W}{B}")), gains(Objects.Each(and(creature, you, power(Comparison.AtMost, 2))), Keyword.Deathtouch, Keyword.Lifelink)),
    )

    // CR 613.4c; CR 603.2 and 702.12: the target of the second ability is any Detective
    @Test
    fun quietEye() = card(
        name = "Quiet Eye Test",
        cost = "{1}{W}{U}",
        type = "Creature — Homunculus Detective",
        pt = "3/3",
        text = """
            Other Detectives you control get +1/+1.
            Whenever you draw your second card each turn, target Detective can't be blocked this turn.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, detective, you))),
        triggered(drawSecondCard, untilEndOfTurn(target("Detective"), Mechanic.CantBeBlocked), targets = listOf(slot("Detective", detective))),
    )

    // CR 613.4c, 603.2
    @Test
    fun rallyCall() = card(
        name = "Rally Call Test",
        cost = "{1}{R}{W}",
        type = "Enchantment",
        text = """
            Creatures you control get +1/+1.
            Whenever a creature you control enters, this enchantment deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(creatureYouControl)),
        triggered(enters(creatureYouControl), damage(1, eachOpponent)),
    )
}
