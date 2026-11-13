package arcanum.parser.sets.one

import arcanum.api.*
import arcanum.parser.cube.*
import kotlin.test.Test

/**
 * Chunk 16: oil counters put on and removed from the card itself (CR 122.1), static abilities that depend on counters or the number of
 * artifacts (CR 604.1, 613.4c), "choose one or both" and "choose one" modes (CR 700.2), reflexive triggers (CR 603.12), each opponent
 * sacrificing (CR 701.21a) and tutors for a card with a subtype (CR 701.23).
 */
class OneChunk16OilModesAndSearchTest {
    private val oil = CounterKind.Named("oil")
    private val hasOil = ObjectFilter.HasCounter(oil, atLeast = 1)
    private fun selfHasOil(n: Int) = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(oil, atLeast = n))
    private val removeOil = CostPart.RemoveCounters(oil, n(1), CostObjects.Self)
    private val oilOnSelf = counters(Objects.Self, 1, oil)
    private val cardInAGraveyard = and(cardFilter, inZone(Zone.Graveyard))
    private val creatureOrArtifact = or(artifact, creature)
    private fun eachOpponentSacrifices(kind: ObjectFilter): Step =
        Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", steps = listOf(
            Step.Choose(Choice.Objects(and(kind, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Bound("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ), together = true)
    private val cantBlock = Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
    private val mite = creatureToken("Phyrexian Mite", emptyList(), 1, 1, abilities = listOf(Keyword.Toxic(1), cantBlock),
        types = listOf(CardType.Artifact, CardType.Creature))
    private fun dies(filter: ObjectFilter): Trigger = on(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))
    private fun reflexive(vararg steps: Step, targets: List<TargetSlot> = emptyList()): Step =
        Step.Do(Effect.ReflexiveTrigger(Instructions(targets = targets, steps = steps.toList())))
    private val anyColor = Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))

    // CR 613.4c: +3/+0 while it has three or more oil counters; CR 106.1: any color; CR 603.2: becomes tapped; CR 122.1
    @Test
    fun armoredScrapgorger() = card(
        name = "Tarnished Scrapgorger",
        cost = "{1}{G}",
        type = "Creature — Phyrexian Beast",
        pt = "0/3",
        text = """
            This creature gets +3/+0 as long as it has three or more oil counters on it.
            {T}: Add one mana of any color.
            Whenever this creature becomes tapped, exile target card from a graveyard and put an oil counter on this creature.
        """,
    ) parsesTo abilities(
        static(pt(3, 0), condition = selfHasOil(3)),
        activated(cost(tap), anyColor),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), move(target("card"), Zone.Exile, postBind = "exiled"), oilOnSelf,
            targets = listOf(slot("card", cardInAGraveyard))),
    )

    // CR 611.3: static during your turn; CR 613.4c, 702.7: first strike; CR 111.1: Mite token
    @Test
    fun miteOverseer() = card(
        name = "Oilspark Overseer",
        cost = "{3}{W}",
        type = "Creature — Phyrexian Soldier",
        pt = "4/2",
        text = """
            First strike
            During your turn, creature tokens you control get +1/+0 and have first strike.
            {3}{W/P}: Create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block." (Players dealt combat damage by it also get a poison counter. {W/P} can be paid with either {W} or 2 life.)
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        static(pt(1, 0), has(Keyword.FirstStrike), affects = Objects.Each(and(creature, token, you)), condition = Condition.YourTurn),
        activated(cost(mana("{3}{W/P}")), create(mite, n(1), bind = "token")),
    )

    // CR 604.1, 613.4c: +2/+0 while you control two or more artifacts
    @Test
    fun orthodoxyEnforcer() = card(
        name = "Rusted Enforcer",
        cost = "{3}{W}",
        type = "Creature — Phyrexian Cleric",
        pt = "2/4",
        text = """
            Vigilance
            This creature gets +2/+0 as long as you control two or more artifacts.
        """,
    ) parsesTo abilities(Keyword.Vigilance, static(pt(2, 0), condition = Condition.Exists(and(artifact, you), atLeast = 2)))

    // CR 700.2d: one or both; CR 400.7, 110.2a: the exiled permanent returns as a new object under its owner's control
    @Test
    fun againstAllOdds() = card(
        name = "Against Long Odds",
        cost = "{3}{W}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Exile target artifact or creature you control, then return it to the battlefield under its owner's control.
            • Return target artifact or creature card with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(
                move(target("permanent"), Zone.Exile, postBind = "exiled"),
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                targets = listOf(slot("permanent", and(creatureOrArtifact, you))),
            ),
            mode(
                move(target("card"), Zone.Battlefield),
                targets = listOf(slot("card", inYourGraveyard(creatureOrArtifact, manaValue(Comparison.AtMost, 3)))),
            ),
            max = 2,
        ))),
    )

    // CR 400.7, 401.4: an up-to-one target put on top of the library
    @Test
    fun meldwebCurator() = card(
        name = "Meldloom Curator",
        cost = "{3}{U}",
        type = "Creature — Phyrexian Wizard",
        pt = "3/4",
        text = "When this creature enters, put up to one target instant or sorcery card from your graveyard on top of your library.",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Library, position = 0),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery)), min = 0))),
    )

    // CR 122.1, 603.2: oil on a cast trigger; CR 602.5d: removing a counter as a cost, sorcery speed
    @Test
    fun atmosphereSurgeon() = card(
        name = "Cloudmind Surgeon",
        cost = "{1}{U}",
        type = "Creature — Phyrexian Wizard",
        pt = "2/1",
        text = """
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            Remove an oil counter from this creature: Target creature gains flying until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), oilOnSelf),
        activated(cost(removeOil), gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 613.4c, 509.1b: +2/+0 and can't be blocked while it has four or more oil counters
    @Test
    fun ichorSynthesizer() = card(
        name = "Ichor Distiller",
        cost = "{1}{U}",
        type = "Creature — Phyrexian Wizard",
        pt = "1/3",
        text = """
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            As long as this creature has four or more oil counters on it, it gets +2/+0 and can't be blocked.
        """,
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), oilOnSelf),
        static(pt(2, 0), Mechanic.CantBeBlocked, condition = selfHasOil(4)),
    )

    // CR 603.4: the entering creature must have an oil counter (checked on entering and on resolving); CR 613.4c: +1/+1 for creatures with oil
    @Test
    fun ichorplateGolem() = card(
        name = "Ichorcrown Golem",
        cost = "{3}",
        type = "Artifact Creature — Phyrexian Golem",
        pt = "2/3",
        text = """
            Whenever a creature you control enters, if it has one or more oil counters on it, put an oil counter on it.
            Creatures you control with oil counters on them get +1/+1.
        """,
    ) parsesTo abilities(
        triggered(enters(creatureYouControl), counters(Objects.EventObject(EventRole.After), 1, oil),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.After), hasOil)),
        static(pt(1, 1), affects = Objects.Each(and(creature, you, hasOil))),
    )

    // CR 602.5d: a tap cost; CR 122.1: oil counters on itself; CR 701.26: untap
    @Test
    fun rustvineCultivator() = card(
        name = "Rustroot Cultivator",
        cost = "{G}",
        type = "Creature — Phyrexian Elf Druid",
        pt = "1/2",
        text = """
            {T}: Put an oil counter on this creature.
            {T}, Remove an oil counter from this creature: Untap target land.
        """,
    ) parsesTo abilities(
        activated(cost(tap), oilOnSelf),
        activated(cost(tap, removeOil), untapIt(target("land")), targets = listOf(slot("land", land))),
    )

    // CR 120.3: damage to each opponent
    @Test
    fun sawbladeScamp() = card(
        name = "Sawtooth Scamp",
        cost = "{R}",
        type = "Creature — Phyrexian Beast",
        pt = "1/1",
        text = """
            Haste
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            {T}, Remove an oil counter from this creature: It deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(youCast(noncreatureSpell), oilOnSelf),
        activated(cost(tap, removeOil), damage(1, eachOpponent)),
    )

    // CR 602.5b: "Activate only if" on the second and third ability
    @Test
    fun tabletOfCompleation() = card(
        name = "Tablet of Completion",
        cost = "{2}",
        type = "Artifact",
        text = """
            {T}: Put an oil counter on this artifact.
            {T}: Add {C}. Activate only if this artifact has two or more oil counters on it.
            {1}, {T}: Draw a card. Activate only if this artifact has five or more oil counters on it.
        """,
    ) parsesTo abilities(
        activated(cost(tap), oilOnSelf),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)),
            rules = anyTime.copy(condition = selfHasOil(2))),
        activated(cost(mana("{1}"), tap), draw(1), rules = anyTime.copy(condition = selfHasOil(5))),
    )

    // CR 700.4: put into a graveyard from the battlefield; CR 602.5d: remove four counters as a cost, sorcery speed
    @Test
    fun vatOfRebirth() = card(
        name = "Vat of Renewal",
        cost = "{B}",
        type = "Artifact",
        text = """
            Whenever another artifact or creature you control is put into a graveyard from the battlefield, put an oil counter on this artifact.
            {2}{B}, {T}, Remove four oil counters from this artifact: Return target creature card from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(dies(and(another, creatureOrArtifact, you)), oilOnSelf),
        activated(cost(mana("{2}{B}"), tap, CostPart.RemoveCounters(oil, n(4), CostObjects.Self)), move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature))), rules = asSorcery),
    )

    // CR 601.2f: the cost reduction counts oil counters; CR 701.9: discard the whole hand; CR 701.21: sacrifice cost
    @Test
    fun vindictiveFlamestoker() = card(
        name = "Vengeful Flamestoker",
        cost = "{R}",
        type = "Creature — Phyrexian Wizard",
        pt = "1/2",
        text = """
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            {6}{R}, Sacrifice this creature: Discard your hand, then draw four cards. This ability costs {1} less to activate for each oil counter on this creature.
        """,
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), oilOnSelf),
        activated(cost(mana("{6}{R}"), sacrificeSelf),
            doing(Effect.Discard(Players.You, Value.Fixed(1), ObjectFilter.Any,
                objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))), draw(4),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!,
                Value.Of(NumericProperty.Counters(oil), Objects.Self, Combine.Single)))),
    )

    // CR 603.12: the reflexive trigger's target is chosen after the life is paid
    @Test
    fun ambulatoryEdifice() = card(
        name = "Ambient Edifice",
        cost = "{2}{B}",
        type = "Artifact Creature — Phyrexian Construct",
        pt = "3/2",
        text = "When this creature enters, you may pay 2 life. When you do, target creature gets -1/-1 until end of turn.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(CostPart.PayLife(n(2))), mandatory = false, payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(untilEndOfTurn(target("creature"), pt(-1, -1)),
                targets = listOf(slot("creature", creature)))))),
    )

    // CR 603.12: the artifact is chosen once the creature was sacrificed
    @Test
    fun shrapnelSlinger() = card(
        name = "Shrapnel Hurler",
        cost = "{1}{R}",
        type = "Artifact Creature — Phyrexian Beast",
        pt = "2/2",
        text = "When this creature enters, you may sacrifice a creature. When you do, destroy target artifact an opponent controls.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(sacrificeA(creature)), mandatory = false, payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("artifact")),
                targets = listOf(slot("artifact", and(artifact, opponents))))))),
    )

    // CR 700.2, 701.21a: each opponent chooses the permanent to sacrifice
    @Test
    fun sheoldredsEdict() = card(
        name = "Sheoldred's Mandate",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Each opponent sacrifices a nontoken creature of their choice.
            • Each opponent sacrifices a creature token of their choice.
            • Each opponent sacrifices a planeswalker of their choice.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(eachOpponentSacrifices(and(not(token), creature))),
            mode(eachOpponentSacrifices(and(creature, token))),
            mode(eachOpponentSacrifices(planeswalker)),
        ))),
    )

    // CR 701.23: search for a basic Mountain; CR 120.3: damage equal to the Mountains; CR 114: the emblem's trigger
    @Test
    fun kothFireOfResistance() = card(
        name = "Koth, Fire of Insurgence",
        cost = "{2}{R}{R}",
        type = "Legendary Planeswalker — Koth",
        loyalty = "4",
        text = """
            [+2]: Search your library for a basic Mountain card, reveal it, put it into your hand, then shuffle.
            [−3]: Koth deals damage to target creature equal to the number of Mountains you control.
            [−7]: You get an emblem with "Whenever a Mountain you control enters, this emblem deals 4 damage to any target."
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(2))), search(and(ObjectFilter.Super(Supertype.Basic), sub("Mountain")), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(cost(CostPart.Loyalty(n(-3))), damage(count(and(sub("Mountain"), you)), toObjects(target("creature"))),
            targets = listOf(slot("creature", creature))),
        activated(cost(CostPart.Loyalty(n(-7))), doing(Effect.CreateEmblem(listOf(
            triggered(enters(and(sub("Mountain"), you)), damage(4, toAny), targets = listOf(anyTarget))), Players.You))),
    )

    // CR 701.23: an optional search, revealed and put into the hand
    @Test
    fun myrKinsmith() = card(
        name = "Myr Gatherer",
        cost = "{4}",
        type = "Artifact Creature — Myr",
        pt = "3/1",
        text = "When this creature enters, you may search your library for a Myr card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(
            listOf(search(sub("Myr"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            player = Players.You)),
    )

    // CR 604.3, 208.2a: characteristic-defining power equal to the artifacts you control
    @Test
    fun cephalopodSentry() = card(
        name = "Cephalopod Warden",
        cost = "{2}{W}{U}",
        type = "Artifact Creature — Phyrexian Squid",
        pt = "*/5",
        text = """
            Flying
            Cephalopod Warden's power is equal to the number of artifacts you control.
        """,
    ) parsesTo abilities(Keyword.Flying, static(Mechanic.SetPowerToughness(count(and(artifact, you)), null), characteristicDefining = true))

    // CR 701.26a: tap a creature an opponent controls
    @Test
    fun chromeProwler() = card(
        name = "Chrome Stalker",
        cost = "{2}{U}",
        type = "Artifact Creature — Phyrexian Cat",
        pt = "3/2",
        text = """
            Flash
            When this creature enters, tap target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
    )
}
