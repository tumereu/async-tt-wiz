package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * INR chunk 08: damage ("that much damage to each creature that player controls", "and 1 damage to its controller", each Wolf or
 * Werewolf deals damage, "any opponent may have it deal 4 damage to them"), a discard after a bounce, an exchange of life and
 * toughness, exiles (linked to "the exiled card", "you may cast red spells from among them"), haste, "twice the number" and
 * "Creatures attacking you".
 */
class InrChunk08DamageDiscardExchangeExileTest {
    private val exiled = Objects.Bound("exiled")
    private fun topCards(n: Int) = Objects.LibraryCards(Players.You, n(n), fromTop = true)
    private val red = ObjectFilter.OfColor(ColorRef.Literal(Color.Red))
    private val leavesSelf = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))

    // CR 510.2, 120.3: the combat damage it dealt the player, dealt again by it to each creature that player controls
    @Test
    fun pyreheartWyrm() = card(
        name = "Pyreheart Wyrm",
        cost = "{5}{R}{R}",
        type = "Creature — Dragon",
        pt = "6/6",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, it deals that much damage to each creature that player controls.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer,
            damage(Value.EventAmount, toObjects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))))))),
    )

    // CR 606.4 (loyalty abilities), 115.4 (a player or planeswalker target), 601.2a (cast from exile this turn, only as a red spell),
    // 114.4 (the emblem's ability works from the command zone), 601.2h (the mana spent to cast that spell)
    @Test
    fun vessaFlameDancer() = card(
        name = "Vessa, Flame Dancer",
        cost = "{1}{R}{R}",
        type = "Legendary Planeswalker — Vessa",
        loyalty = "3",
        text = """
            [+1]: Add {R}. Vessa deals 1 damage to up to one target player or planeswalker.
            [+1]: Exile the top card of your library. If it's red, you may cast it this turn.
            [−7]: Exile the top five cards of your library. You may cast red spells from among them this turn. You get an emblem with "Whenever you cast a red spell, this emblem deals X damage to any target, where X is the amount of mana spent to cast that spell."
        """,
    ) parsesTo abilities(
        loyalty(1,
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), players = Players.You)),
            damage(1, Recipients(target = "player or planeswalker")),
            targets = listOf(TargetSlot("player or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Any),
                min = n(0), max = n(1)))),
        loyalty(1,
            move(topCards(1), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.ObjectMatches(exiled, red), untilEndOfTurn(exiled, Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)))),
        loyalty(-7,
            move(topCards(5), Zone.Exile, postBind = "exiled"),
            untilEndOfTurn(exiled, Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, resultingSpell = red, holder = PlayerFilter.You)),
            doing(Effect.CreateEmblem(listOf(
                triggered(youCast(red), damage(Value.ManaSpent(of = Objects.EventObject(EventRole.Object)), toAny), targets = listOf(anyTarget)),
            ), Players.You))),
    )

    // CR 608.2d: "you may have it deal" is the same damage, if its controller chooses it
    @Test
    fun spectreSnareEngine() = card(
        name = "Spectre Snare Engine",
        cost = "{6}",
        type = "Artifact Creature — Construct",
        pt = "4/5",
        text = "When this creature enters, you may have it deal 4 damage to target creature with flying.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(damage(4, toObjects(target("creature")))), Players.You),
            targets = listOf(slot("creature", and(creature, kw(Keyword.Flying.kind))))),
    )

    // CR 120.3: each Wolf and Werewolf is the source of its own damage, all dealt at once
    @Test
    fun lunarPursuit() = card(
        name = "Lunar Pursuit",
        cost = "{1}{G}",
        type = "Instant",
        text = "Choose target creature you don't control. Each creature you control that's a Wolf or a Werewolf deals damage equal to its power to that creature.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Objects(Objects.Each(and(creature, you, or(sub("Wolf"), sub("Werewolf"))))), bind = "source", together = true, steps = listOf(
                damage(power(Objects.Bound("source")), toObjects(target("creature")), source = Objects.Bound("source")))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 601.2f (cost reduction), 120.3: 1 damage to the creature and 1 to its controller, then a draw
    @Test
    fun fledglingsDash() = card(
        name = "Fledgling's Dash",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a Vampire.
            Fledgling's Dash deals 1 damage to target creature and 1 damage to its controller. Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
            affects = Affected.Objects(Objects.Self), condition = youControl(sub("Vampire"), 1)),
        spell(
            damage(1, toObjects(target("creature"))),
            damage(1, Recipients(players = Players.ControllerOf(target("creature")))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 115.1 (another target), 205.1b (a Vampire in addition to its other types, for good: CR 611.2a), 122.1 (the counter on her),
    // 611.2b (control for as long as you control her)
    @Test
    fun mirelaVonkarth() = card(
        name = "Mirela Vonkarth",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Vampire",
        pt = "3/3",
        text = """
            Flying
            {1}{R}: Mirela Vonkarth deals 1 damage to another target creature. That creature becomes a Vampire in addition to its other types. Put a +1/+1 counter on Mirela Vonkarth.
            {3}{B}{B}: Gain control of target Vampire for as long as you control Mirela Vonkarth.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{1}{R}"),
            damage(1, toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire"))))),
                Affected.Objects(target("creature")), Duration.Indefinitely)),
            counters(Objects.Self),
            targets = listOf(slot("creature", and(another, creature)))),
        activated(manaCost("{3}{B}{B}"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("Vampire")), Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(slot("Vampire", sub("Vampire")))),
    )

    // CR 120.3: damage equal to the Wolves and Werewolves you control, counted as it resolves
    @Test
    fun glyphmarkedWolf() = card(
        name = "Glyphmarked Wolf",
        cost = "{1}{R}",
        type = "Creature — Wolf",
        pt = "2/2",
        text = "{3}{R}, {T}: This creature deals damage equal to the number of Wolves and Werewolves you control to target opponent.",
    ) parsesTo abilities(
        activated(cost(mana("{3}{R}"), tap),
            damage(count(and(or(sub("Wolf"), sub("Werewolf")), you)), Recipients(players = Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    // CR 101.4: each opponent in turn order may have it deal the damage, until one does; CR 608.2c: "If a player does"
    @Test
    fun tauntingImp() = card(
        name = "Taunting Imp",
        cost = "{R}",
        type = "Creature — Devil",
        pt = "4/3",
        text = "When this creature enters, any opponent may have it deal 4 damage to them. If a player does, sacrifice this creature.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(steps = listOf(damage(4, Recipients(players = Players.Bound("payer")))), payer = Players.Each(PlayerFilter.Opponent), bind = "had",
                payerBind = "payer"),
            ifThen(Condition.Did("had"), sacrificeIt)),
    )

    // CR 108.3: "that player" is the owner, whose hand the permanent went to; the condition is checked as the spell resolves (CR 608.2c)
    @Test
    fun cowingRebuff() = card(
        name = "Cowing Rebuff",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target nonland permanent to its owner's hand. Then that player discards a card if you control a Zombie.",
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Hand),
            ifThen(youControl(sub("Zombie"), 1), discard(1, Players.OwnerOf(target("permanent")))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 701.12g: the life total becomes the toughness, and the toughness is set to the old life total (layer 7b, CR 613.4b)
    @Test
    fun gallowsOak() = card(
        name = "Gallows Oak",
        cost = "{3}{B}",
        type = "Creature — Plant",
        pt = "0/13",
        text = """
            Defender
            {T}: Exchange target opponent's life total with this creature's toughness.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), doing(Effect.ExchangeLifeWithStat(Players.Target("opponent"), Objects.Self, NumericProperty.Toughness)), targets = listOf(targetOpponent)),
    )

    // CR 607.2a: the leaves trigger returns the card the enters trigger exiled (two abilities, not an "until" exile: CR 610.3 doesn't apply)
    @Test
    fun ghoulWarden() = card(
        name = "Ghoul Warden",
        cost = "{1}{W}{W}",
        type = "Creature — Human Cleric",
        pt = "1/3",
        text = """
            When this creature enters, you may exile another target creature.
            When this creature leaves the battlefield, return the exiled card to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.May(listOf(move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", exiled))), Players.You),
            targets = listOf(slot("creature", and(another, creature)))),
        triggered(leavesSelf, move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 702.10 (haste, for good), 603.7 (the delayed sacrifice), 702.47 (splice)
    @Test
    fun intoTheRift() = card(
        name = "Into the Rift",
        cost = "{4}{R}",
        type = "Instant — Arcane",
        text = """
            You may put a creature card from your hand onto the battlefield. That creature gains haste. Sacrifice that creature at the beginning of the next end step.
            Splice onto Arcane {2}{R}{R} (As you cast an Arcane spell, you may reveal this card from your hand and pay its splice cost. If you do, add this card's effects to that spell.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(arcanum.api.Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen",
                chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("put")), Duration.Indefinitely)),
            delayed(on(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Step.Do(Effect.Sacrifice(Objects.Bound("put")))),
        ),
        Keyword.Splice(onto = sub("Arcane"), cost = manaCost("{2}{R}{R}")),
    )

    // CR 107.3a (X defined by the text, counted as it resolves), 602.5d (sorcery timing)
    @Test
    fun bloodtaxReaper() = card(
        name = "Bloodtax Reaper",
        cost = "{B}{R}",
        type = "Creature — Vampire",
        pt = "3/2",
        text = """
            When this creature enters, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            {T}, Sacrifice this creature: Target creature gets -X/-X until end of turn, where X is twice the number of Blood tokens you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(blood)),
        activated(cost(tap, sacrificeSelf),
            untilEndOfTurn(target("creature"), Value.Times(Value.Times(count(and(sub("Blood"), token, you)), 2), -1).let { minus ->
                Mechanic.ModifyPowerToughness(minus, minus) }),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 506.3, 508.1b: only creatures attacking you (not your planeswalkers); CR 603.4: the damage you were dealt this turn, checked twice
    @Test
    fun shutteredPane() = card(
        name = "Shuttered Pane",
        cost = "{3}",
        type = "Artifact",
        text = """
            Creatures attacking you get -1/-0.
            At the beginning of each end step, if you were dealt 4 or more damage this turn, exile this artifact.
        """,
    ) parsesTo abilities(
        static(pt(-1, 0), affects = Objects.Each(and(creature, attacking,
            ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You)),
            ObjectFilter.Where(Condition.Compare(Value.Count(Objects.AttackedBy(Objects.Tested)), Comparison.Equal, n(0)))))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Self, Zone.Exile),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)), atLeast = 4,
                measure = Measure.Amount)),
    )

    // CR 608.2d (if its controller chooses), 205.1b (it stays an artifact), 613.1d/f/4b (type, colour, base P/T until end of turn)
    @Test
    fun seraphsCrypt() = card(
        name = "Seraph's Crypt",
        cost = "{3}",
        type = "Artifact",
        text = "Whenever a creature you control enters, you may have this artifact become a 3/3 white Angel artifact creature with flying until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(creature, you)), Step.May(listOf(doing(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Angel")))),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.White))),
            Mechanic.SetPowerToughness(n(3), n(3)),
            has(Keyword.Flying)), Affected.Objects(Objects.Self), Duration.EndOfTurn))), Players.You)),
    )
}
