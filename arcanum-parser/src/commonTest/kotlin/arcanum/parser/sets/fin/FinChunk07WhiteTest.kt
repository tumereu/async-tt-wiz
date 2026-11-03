package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 07: white cards: ending the turn (CR 724.1), counters spread as a creature dies (its counters as it last existed),
 * returning another permanent you control, triggers of Equipment and their creature triggering twice (CR 603.2d), searching the library
 * and/or graveyard (shuffle only if the library was searched), a cost reduction for a tapped target (CR 601.2f), a card put onto the
 * battlefield instead when the spell was cast from a graveyard, giving an opponent control of a permanent, doubled life gain (CR 614.1a),
 * a sacrificed creature's counters and Equipment moved to another creature, Equipment attached at the beginning of combat.
 */
class FinChunk07WhiteTest {
    // CR 724.1
    @Test
    fun finalOblivion() = card(
        name = "Final Oblivion",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = """
            Destroy all artifacts and creatures. End the turn. (Exile all spells and abilities from the stack, including this card. The player whose turn it is discards down to their maximum hand size. Damage wears off, and "this turn" and "until end of turn" effects end.)
        """,
    ) parsesTo abilities(spell(destroy(Objects.Each(or(artifact, creature))), doing(Effect.EndTurn)))

    /** "where X is the number of +1/+1 counters on Aerith": on it as it last existed (CR 603.10a). */
    @Test
    fun aeraFlowerSeller() = card(
        name = "Aera, Flower Seller",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "2/2",
        text = """
            Lifelink
            Whenever you gain life, put a +1/+1 counter on Aera, Flower Seller.
            When Aera, Flower Seller dies, put X +1/+1 counters on each legendary creature you control, where X is the number of +1/+1 counters on Aera, Flower Seller.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self)),
        triggered(diesSelf, counters(Objects.Each(and(legendary, creature, you)), Value.Of(NumericProperty.Counters(p1p1), leftOne, Combine.Single))),
    )

    @Test
    fun ambergaleWhiteheart() = card(
        name = "Ambergale Whiteheart",
        cost = "{1}{W}",
        type = "Legendary Creature — Bird",
        pt = "2/2",
        text = """
            Flash
            When Ambergale Whiteheart enters, you may return another permanent you control to its owner's hand.
            Landfall — Whenever a land you control enters, Ambergale Whiteheart gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, Step.Choose(Choice.Objects(and(another, permanentFilter, you), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        triggered(landfall, pump(Objects.Self, 1, 0), abilityWord = "Landfall"),
    )

    // CR 603.2d
    @Test
    fun claudeMercenaryOfMidgard() = card(
        name = "Claude, Sellsword of the Slums",
        cost = "{W}{W}",
        type = "Legendary Creature — Human Soldier Mercenary",
        pt = "2/1",
        text = """
            When Claude enters, search your library for an Equipment card, reveal it, put it into your hand, then shuffle.
            As long as Claude is equipped, if a triggered ability of Claude or an Equipment attached to it triggers, that ability triggers an additional time.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(equipment, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        static(Mechanic.TriggersAdditionalTime(null, or(ObjectFilter.Is(Objects.Self), and(equipment, ObjectFilter.Attached(to = Objects.Self)))),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(to = null, attachments = equipment))),
    )

    /** "search your library and/or graveyard"; "If you search your library this way, shuffle." (CR 701.23) */
    @Test
    fun parcelMoogle() = card(
        name = "Parcel Moogle",
        cost = "{3}{W}",
        type = "Creature — Moogle",
        pt = "3/2",
        text = """
            Flying
            When this creature enters, search your library and/or graveyard for an artifact card with mana value 2 or less, reveal it, and put it into your hand. If you search your library this way, shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            doing(Effect.Search(Players.You, and(artifact, manaValue(arcanum.api.Comparison.AtMost, 2)), max = n(1), reveal = true, zones = listOf(Zone.Library, Zone.Graveyard),
                bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            ifThen(Condition.Searched("found", Zone.Library), shuffle)),
    )

    // CR 601.2f: the reduction reads the chosen target
    @Test
    fun crystalsFate() = card(
        name = "Crystal's Fate",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets a tapped creature.
            Destroy target nonland permanent.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.ObjectMatches(target("permanent"), and(tapped, creature))),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", nonlandPermanent))),
    )

    @Test
    fun legacyOfTheAirship() = card(
        name = "Legacy of the Airship",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Search your library for a Vehicle card, reveal it, and put it into your hand. If this spell was cast from a graveyard, put that card onto the battlefield instead. Then shuffle.
            Flashback {4}{W}{W}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(search(sub("Vehicle"), reveal = true),
            ifThen(Condition.CastFrom(Zone.Graveyard, Objects.Self), move(Objects.Bound("found"), Zone.Battlefield), otherwise = listOf(move(Objects.Bound("found"), Zone.Hand))),
            shuffle),
        Keyword.Flashback(manaCost("{4}{W}{W}{W}")),
    )

    /** "Target opponent gains control of another target permanent you control. If they do, you draw a card." (CR 613.1b) */
    @Test
    fun stilkinMoogleTrader() = card(
        name = "Stilkin, Moogle Trader",
        cost = "{W}",
        type = "Legendary Creature — Moogle",
        pt = "1/2",
        text = """
            Lifelink
            {2}, {T}: Target opponent gains control of another target permanent you control. If they do, you draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(cost(mana("{2}"), tap),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Target("opponent"))), Affected.Objects(target("permanent")), Duration.Indefinitely)),
            ifThen(Condition.ObjectMatches(target("permanent"), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), draw(1)),
            targets = listOf(targetOpponent, slot("permanent", and(another, permanentFilter, you)))),
    )

    // CR 614.1a
    @Test
    fun theGaleCrystal() = card(
        name = "The Gale Crystal",
        cost = "{2}{W}{W}",
        type = "Legendary Artifact",
        text = """
            White spells you cast cost {1} less to cast.
            If you would gain life, you gain twice that much life instead.
            {4}{W}{W}, {T}: Creatures you control gain flying and lifelink until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(arcanum.api.Color.White)), spellFilter, you),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You), modify = listOf(Modification.MultiplyAmount(2)))),
        activated(cost(mana("{4}{W}{W}"), tap), gains(Objects.Each(creatureYouControl), Keyword.Flying, Keyword.Lifelink)),
    )

    /** "Put Zane's counters on that creature and attach an Equipment that was attached to Zane": as it last existed (CR 608.2h). */
    @Test
    fun zaneFaithfulSoldier() = card(
        name = "Zane Valecourt",
        cost = "{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "0/1",
        text = """
            Zane Valecourt enters with a +1/+1 counter on it.
            {1}, Sacrifice Zane Valecourt: Target creature you control gains indestructible until end of turn. Put Zane Valecourt's counters on that creature and attach an Equipment that was attached to Zane Valecourt to that creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
        activated(cost(mana("{1}"), sacrificeSelf),
            gains(target("creature"), Keyword.Indestructible),
            doing(Effect.PutCountersOf(target("creature"), Objects.Self)),
            Step.Choose(Choice.Objects(equipment, n(1), n(1), from = Objects.AttachmentsOf(Objects.Self, equipment)), bind = "equipment", chooser = Players.You),
            doing(Effect.Attach(Objects.Bound("equipment"), Recipients(objects = target("creature")))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun beatrixWarden() = card(
        name = "Bellatrix, Loyal General",
        cost = "{4}{W}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "4/4",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            At the beginning of combat on your turn, you may attach any number of Equipment you control to target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(beginningOfCombatOnYourTurn,
            Step.Choose(Choice.Objects(and(equipment, you), n(0), null), bind = "equipment", chooser = Players.You),
            doing(Effect.Attach(Objects.Bound("equipment"), Recipients(objects = target("creature")))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun rosaWhiteMage() = card(
        name = "Rosalind, Resolute Healer",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Noble Cleric",
        pt = "2/3",
        text = """
            Reach (This creature can block creatures with flying.)
            At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. It gains lifelink until end of turn. (Damage dealt by the creature also causes you to gain that much life.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(beginningOfCombatOnYourTurn, counters(target("creature")), gains(target("creature"), Keyword.Lifelink), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.2c ("one or more"), 603.4 (only once each turn)
    @Test
    fun graHawkeye() = card(
        name = "Gerra Tiel",
        cost = "{4}{W}",
        type = "Legendary Creature — Cat Archer",
        pt = "3/5",
        text = """
            Reach
            The Allagan Eye — Whenever one or more other creatures and/or artifacts you control die, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(and(another, or(creature, artifact), you), from = Zone.Battlefield, to = Zone.Graveyard)),
            oneOrMore = true), arcanum.api.Instructions(steps = listOf(draw(1))), perTurn = 1, abilityWord = "The Allagan Eye"),
    )

    @Test
    fun armsDealer() = card(
        name = "Armament Peddler",
        cost = "{3}{W}",
        type = "Creature — Human Artificer",
        pt = "2/2",
        text = """
            When this creature enters, draw a card.
            At the beginning of combat on your turn, if you control an Equipment, you may pay {1}. When you do, attach target Equipment you control to target creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        triggered(beginningOfCombatOnYourTurn, Step.Pay(cost(mana("{1}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(doing(Effect.Attach(target("Equipment"), Recipients(objects = target("creature")))),
                targets = listOf(slot("Equipment", and(equipment, you)), slot("creature", creatureYouControl)))),
            interveningIf = Condition.Exists(and(equipment, you), atLeast = 1)),
    )

    // CR 610.3 ("until this artifact leaves the battlefield")
    @Test
    fun paleAuracite() = card(
        name = "Pale Auracite",
        cost = "{2}{W}{W}",
        type = "Artifact",
        text = """
            When this artifact enters, exile target nonland permanent an opponent controls until this artifact leaves the battlefield.
            {T}: Add {W}.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("permanent"), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonlandPermanent, opponents)))),
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{W}")!!)), Players.You))),
    )
}
