package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 10: copy effects (a clone entering with the copied permanent's counters, CR 707.2, 614.1c; Equipment tokens becoming copies, CR 611.2c),
 * permanents entering with additional counters (CR 614.1c, 122.6), New Planet tokens (CR 111.4), damage from several sources (CR 120.2),
 * counting dilemmas faced (CR 700.17), casting any number of exiled cards (CR 608.2g) and a delayed trigger within a delayed trigger (CR 603.7).
 */
class TrkChunk10EffectsTest {
    private val selfEnters = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val officer = creatureToken("Officer", listOf(Color.Red), 1, 1)
    private fun rift(cost: String, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        activated(cost(mana(cost), tap, sacrificeSelf), *steps, newPlanet, targets = targets)

    // ---- Copies

    // CR 707.2 (a copy of the copiable values), 707.9b (except it isn't legendary), 614.1c, 122.1 (the copied permanent's counters, which aren't
    // copiable values, only if it enters as a copy)
    @Test
    fun foundersInfiltrator() = card(
        name = "Founders' Infiltrator",
        cost = "{4}{U}",
        type = "Creature — Shapeshifter",
        pt = "3/3",
        text = "You may have this creature enter as a copy of any artifact or creature on the battlefield, except it isn't legendary. If you do, it enters with additional counters on it equal to the same number and kinds of counters the copied permanent has on it.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(selfEnters, modify = listOf(Modification.EntersAsCopy(or(artifact, creature),
            listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary))), optional = true, copiedCounters = true)))),
    )

    // CR 707.2, 611.2c (the Equipment tokens you control as it resolves), 613.2 (layer 1), 301.5c (attached Equipment stays attached)
    @Test
    fun wyndraTheOffspring() = card(
        name = "Wyndra, the Offspring",
        cost = "{1}{U}",
        type = "Legendary Creature — Vau-N'Akat Advisor",
        pt = "1/1",
        text = """
            When Wyndra enters, create a colorless Equipment artifact token named Neuroflux Keepsake with "Equipped creature gets +1/+1" and equip {2}. Attach it to Wyndra.
            {2}: Each Equipment token you control becomes a copy of target Equipment you control until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            create(TokenSpec.Defined(FaceDefinition("Neuroflux Keepsake", colorIndicator = emptyList(), types = listOf(CardType.Artifact), subtypes = listOf(Subtype("Equipment")),
                abilities = listOf(static(pt(1, 1), affects = Objects.AttachedTo(Objects.Self)), Keyword.Equip(manaCost("{2}"))))), bind = "token"),
            Step.Do(Effect.Attach(Objects.Bound("token"), Recipients(objects = Objects.Self)))),
        activated(manaCost("{2}"),
            untilEndOfTurn(Objects.Each(and(equipment, token, you)), Mechanic.CopyOf(target("Equipment"))),
            targets = listOf(slot("Equipment", and(equipment, you)))),
    )

    // CR 115.3, 707.2: "each other creature" is every creature but the target
    @Test
    fun mirroredSeam() = card(
        name = "Mirrored Seam",
        cost = "{2}{W/U}{W/U}",
        type = "Instant",
        text = "Each other creature becomes a copy of target nonlegendary creature until end of turn.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Each(and(ObjectFilter.Not(ObjectFilter.Is(target("creature"))), creature)), Mechanic.CopyOf(target("creature"))),
            targets = listOf(slot("creature", and(not(legendary), creature)))),
    )

    // ---- Entering with additional counters

    // CR 614.1c (other creatures you control enter with a counter), 614.12
    @Test
    fun deliaTrossEmpathicAdvisor() = card(
        name = "Delia Tross, Empathic Advisor",
        cost = "{1}{W}",
        type = "Legendary Creature — Betazoid Human Advisor",
        pt = "2/2",
        text = "Other creatures you control enter with an additional +1/+1 counter on them.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(another, creature, you), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
    )

    // CR 401.5 (looking at the top card), 601.3 (casting from the library once each of your turns), 614.1c (two charge counters)
    @Test
    fun zebulonCraneWarpTrailblazer() = card(
        name = "Zebulon Crane, Warp Trailblazer",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Pilot",
        pt = "2/3",
        text = """
            You may look at the top card of your library any time.
            Once during each of your turns, you may cast an artifact spell from the top of your library.
            Spacecraft you control enter with two additional charge counters on them.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, perTurn = 1, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(and(artifact, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))), condition = Condition.YourTurn),
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(sub("Spacecraft"), you), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(charge, n(2))))),
    )

    // ---- New Planet

    // CR 111.4 (a token with a quoted ability), 118.3 (sacrificing this land is a cost), 119.3
    @Test
    fun syntheticRift() = card(
        name = "Synthetic Rift",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {3}{B}, {T}, Sacrifice this land: Each opponent loses 2 life and you gain 2 life. Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        tapFor("C"),
        rift("{3}{B}", loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
    )

    // CR 111.4, 121.1
    @Test
    fun nebularRift() = card(
        name = "Nebular Rift",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {5}{U}, {T}, Sacrifice this land: Draw two cards. Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        tapFor("C"),
        rift("{5}{U}", draw(2)),
    )

    // CR 111.4, 608.2h ("it" is this land as it last existed, the source of the damage)
    @Test
    fun spatialRift() = card(
        name = "Spatial Rift",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}{R}, {T}, Sacrifice this land: It deals 2 damage to target creature. Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        tapFor("C"),
        rift("{4}{R}", damage(2, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 111.4
    @Test
    fun subspaceRift() = card(
        name = "Subspace Rift",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}{W}, {T}, Sacrifice this land: Create two 1/1 red Officer creature tokens. Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        tapFor("C"),
        rift("{4}{W}", create(officer, n(2))),
    )

    // CR 111.4, 120.3, 701.8
    @Test
    fun terraformWave() = card(
        name = "Terraform Wave",
        cost = "{4}{R}",
        type = "Instant",
        text = """Terraform Wave deals 6 damage to target creature. Destroy target land you control. Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."""",
    ) parsesTo abilities(
        spell(damage(6, toObjects(target("creature"))), destroy(target("land")), newPlanet,
            targets = listOf(slot("creature", creature), slot("land", and(land, you)))),
    )

    // CR 201.5a (a name of two people: "enter"), 111.4
    @Test
    fun danielAndClaraMorrowFounders() = card(
        name = "Daniel and Clara Morrow, Founders",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Scientist",
        pt = "1/3",
        text = """When Daniel and Clara Morrow enter, create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."""",
    ) parsesTo abilities(
        triggered(entersSelf, newPlanet),
    )

    // CR 508.1 ("whenever you attack"), 111.4
    @Test
    fun steadyWormhole() = card(
        name = "Steady Wormhole",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """Whenever you attack, create a tapped Planet land token named New Planet with "{T}: Add one mana of any color."""",
    ) parsesTo abilities(
        triggered(youAttack, newPlanet),
    )

    // ---- Damage

    // CR 120.2 (each target creature is the source of its own damage), 115.1 (one or two targets)
    @Test
    fun sharedPurpose() = card(
        name = "Shared Purpose",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "One or two target creatures you control each deal damage equal to their power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Objects(target("creatures")), bind = "dealer", steps = listOf(
                Step.Do(Effect.DealDamage(toObjects(target("creature")), power(Objects.Bound("dealer")), Objects.Bound("dealer"))))),
            targets = listOf(slot("creatures", creatureYouControl, min = 1, max = 2), slot("creature", creatureOpponentControls)),
        ),
    )

    // CR 120.2 (one damage event to each opponent and each of their creatures), 201.5c ("he": this creature)
    @Test
    fun kahlTyrantByBreeding() = card(
        name = "Kahl, Tyrant by Breeding",
        cost = "{4}{R}",
        type = "Legendary Creature — Human Rogue",
        pt = "5/4",
        text = """
            Trample
            When Kahl enters, he deals 1 damage to each opponent and each creature your opponents control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent), objects = Objects.Each(creatureOpponentControls)))),
    )

    // ---- Dilemmas and casting from exile

    // CR 700.17 (each dilemma faced this turn), 111.1; CR 608.2g, 601.2a (exiled cards cast while it resolves, nonland only), 118.9 (without
    // paying their mana costs)
    @Test
    fun captainBertramSisk() = card(
        name = "Captain Bertram Sisk",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Officer",
        pt = "2/2",
        text = """
            At the beginning of your end step, create a 1/1 red Officer creature token for each dilemma you've faced this turn.
            {2}{W}{U}{B}{R}{G}, {T}: Exile the top five cards of your library. You may cast any number of spells with mana value less than or equal to the number of Officers you control from among those cards without paying their mana costs.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), create(officer, Value.EventsThisTurn(EventPattern.FaceDilemma(PlayerFilter.You), Measure.Events))),
        activated(cost(mana("{2}{W}{U}{B}{R}{G}"), tap),
            move(Objects.LibraryCards(Players.You, n(5), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(and(not(land), ObjectFilter.ManaValue(Comparison.AtMost, count(and(sub("Officer"), you)))), n(0), null,
                from = Objects.Bound("exiled")), bind = "toCast", chooser = Players.You),
            Step.Do(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
    )

    // ---- A delayed trigger that makes a delayed trigger

    // CR 603.7 (a delayed trigger for the turn, then one at the next end step), 400.7 (the card put into the graveyard), 122.6 (with a counter)
    @Test
    fun drLionelSplintsMcKay() = card(
        name = "Dr. Lionel \"Splints\" McKay",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Human Doctor",
        pt = "3/2",
        text = """
            Flash
            When Dr. McKay enters, choose one —
            • Return target non-Doctor permanent card from your graveyard to your hand.
            • Choose up to one other target creature. When it dies this turn, return that card to the battlefield under its owner's control with a +1/+1 counter on it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(not(sub("Doctor")), permanentCard)))),
            mode(Step.Do(Effect.CreateDelayedTrigger(on(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard)),
                // the card put into the graveyard, named for the end step trigger (CR 603.7c)
                Instructions(steps = listOf(Step.Bind(Objects.EventObject(EventRole.After), "sacrificed"),
                    Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(
                        Step.Do(Effect.Move(Objects.Bound("sacrificed"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("sacrificed")),
                            counters = listOf(CountersToPut(p1p1, n(1)))), postBind = "returned")))))))),
                duration = Duration.EndOfTurn)),
                targets = listOf(slot("creature", and(another, creature), min = 0))),
        ))),
    )
}
