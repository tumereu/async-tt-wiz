package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
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
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 01: kicker, among them two kicker costs ("Kicker {B} and/or {R}", each payable once, CR 702.33b, d) and what counts
 * them ("if it was kicked twice", "for each time it was kicked"); milling as a permanent enters (CR 614.1c); a basic land type of your choice
 * (CR 305.7); counter unless its controller pays for each creature (CR 118.12); phasing out chosen permanents (CR 702.26).
 */
class DmuChunk01KickerTest {
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private val kickedTwice = Condition.Paid(Keyword.Kicker.Kind, times = 2, of = Objects.Self)
    private val timesKicked = Value.TimesPaid(Keyword.Kicker.Kind, Objects.Self)
    private fun kicker(vararg costs: String) = Keyword.Kicker(costs.map { manaCost(it) })
    private val entersReplacement = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private fun atNextEndStep(vararg steps: Step) = Step.Do(Effect.CreateDelayedTrigger(
        Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Instructions(steps = steps.toList())))
    private val cardsInYourGraveyard = Objects.Each(ObjectFilter.And(yourGraveyard))

    // CR 702.33b, d: two kicker costs; "kicked twice" is both paid; CR 603.4
    @Test
    fun archangelOfWrath() = card(
        name = "Seraph of Twin Furies",
        cost = "{2}{W}{W}",
        type = "Creature — Angel",
        pt = "3/4",
        text = """
            Kicker {B} and/or {R} (You may pay an additional {B} and/or {R} as you cast this spell.)
            Flying, lifelink
            When this creature enters, if it was kicked, it deals 2 damage to any target.
            When this creature enters, if it was kicked twice, it deals 2 damage to any target.
        """,
    ) parsesTo abilities(
        kicker("{B}", "{R}"),
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget), interveningIf = kicked),
        triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget), interveningIf = kickedTwice),
    )

    // CR 611.2a: "It gains haste." lasts while it stays on the battlefield; CR 603.7: the delayed sacrifice
    @Test
    fun balduvianAtrocity() = card(
        name = "Grimfrost Ravager",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Berserker",
        pt = "2/3",
        text = """
            Kicker {R} (You may pay an additional {R} as you cast this spell.)
            Menace
            When this creature enters, if it was kicked, return target creature card with mana value 3 or less from your graveyard to the battlefield. It gains haste. Sacrifice it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        kicker("{R}"),
        Keyword.Menace,
        triggered(
            entersSelf,
            move(target("card"), Zone.Battlefield, postBind = "returned"),
            Step.Do(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            atNextEndStep(Step.Do(Effect.Sacrifice(Objects.Bound("returned")))),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)))),
            interveningIf = kicked,
        ),
    )

    // CR 611.2c: "those creatures" are the ones the first effect affected
    @Test
    fun heroicCharge() = card(
        name = "Valiant Onrush",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = """
            Kicker {1}{R} (You may pay an additional {1}{R} as you cast this spell.)
            Creatures you control get +2/+1 until end of turn. If this spell was kicked, those creatures also gain trample until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{1}{R}"),
        spell(
            pump(Objects.Each(creatureYouControl), 2, 1),
            Step.If(kicked, then = listOf(gains(Objects.Each(creatureYouControl), Keyword.Trample))),
        ),
    )

    // CR 613.1b (control), CR 603.7 (the delayed sacrifice only if kicked)
    @Test
    fun inThrallToThePit() = card(
        name = "Shackled by the Abyss",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Kicker {2}{B} (You may pay an additional {2}{B} as you cast this spell.)
            Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. If this spell was kicked, sacrifice that creature at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        kicker("{2}{B}"),
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            Step.If(kicked, then = listOf(atNextEndStep(Step.Do(Effect.Sacrifice(target("creature")))))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 614.1c: milled as it enters; CR 604.3: its power and toughness everywhere
    @Test
    fun monstrousWarLeech() = card(
        name = "Ravenous Mire-Leech",
        cost = "{3}{B}",
        type = "Creature — Leech Horror",
        pt = "*/*",
        text = """
            Kicker {U} (You may pay an additional {U} as you cast this spell.)
            As this creature enters, if it was kicked, mill four cards. (To mill a card, put the top card of your library into your graveyard.)
            Ravenous Mire-Leech's power and toughness are each equal to the greatest mana value among cards in your graveyard.
        """,
    ) parsesTo abilities(
        kicker("{U}"),
        static(Mechanic.Replacement(entersReplacement, modify = listOf(Modification.AsEnters(listOf(Step.If(kicked, then = listOf(mill(4)))))))),
        Value.Of(NumericProperty.ManaValue, cardsInYourGraveyard, Combine.Greatest).let { greatest ->
            static(Mechanic.SetPowerToughness(greatest, greatest), characteristicDefining = true)
        },
    )

    // CR 305.7: the chosen basic land type replaces its land types until end of turn
    @Test
    fun pixieIllusionist() = card(
        name = "Sprite Glamourweaver",
        cost = "{U}",
        type = "Creature — Faerie Wizard",
        pt = "1/1",
        text = """
            Kicker {3}{G} (You may pay an additional {3}{G} as you cast this spell.)
            Flying
            If this creature was kicked, it enters with two +1/+1 counters on it.
            {T}: Target land you control becomes the basic land type of your choice until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{3}{G}"),
        Keyword.Flying,
        Ability.Static(
            listOf(Mechanic.Replacement(entersReplacement, modify = listOf(Modification.EntersWithCounters(p1p1, n(2))))),
            affects = Affected.Objects(Objects.Self),
            condition = kicked,
        ),
        activated(
            cost(tap),
            Step.Choose(Choice.BasicLandType, bind = "type", chooser = Players.You),
            untilEndOfTurn(target("land"), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Chosen("type")), replaceSubtypes = true)),
            targets = listOf(slot("land", and(land, you))),
        ),
    )

    // CR 118.12: unless its controller pays {1} for each creature you control (counted as it resolves)
    @Test
    fun protectTheNegotiators() = card(
        name = "Guard the Envoys",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Kicker {W} (You may pay an additional {W} as you cast this spell.)
            If this spell was kicked, create a 1/1 white Soldier creature token.
            Counter target spell unless its controller pays {1} for each creature you control.
        """,
    ) parsesTo abilities(
        kicker("{W}"),
        spell(
            Step.If(kicked, then = listOf(create(soldier))),
            Step.Pay(
                cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!, times = count(creatureYouControl)))),
                payer = Players.ControllerOf(target("spell")),
                bind = "paid",
            ),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    // CR 702.33d: kicked, it goes to the bottom of its owner's library instead of the hand
    @Test
    fun ronasVortex() = card(
        name = "Vortex of Dismissal",
        cost = "{U}",
        type = "Instant",
        text = """
            Kicker {2}{B} (You may pay an additional {2}{B} as you cast this spell.)
            Return target creature or planeswalker you don't control to its owner's hand. If this spell was kicked, put that permanent on the bottom of its owner's library instead.
        """,
    ) parsesTo abilities(
        kicker("{2}{B}"),
        spell(
            Step.If(kicked,
                then = listOf(move(target("permanent"), Zone.Library, position = -1)),
                otherwise = listOf(move(target("permanent"), Zone.Hand))),
            targets = listOf(slot("permanent", and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 702.33d; CR 608.2h: the returned card's mana value
    @Test
    fun sheoldredsRestoration() = card(
        name = "Grim Restoration Rite",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
            Return target creature card from your graveyard to the battlefield. If this spell was kicked, you gain life equal to that card's mana value. Otherwise, you lose that much life.
            Exile Grim Restoration Rite.
        """,
    ) parsesTo abilities(
        kicker("{2}{W}"),
        Value.Of(NumericProperty.ManaValue, target("card"), Combine.Single).let { mv ->
            spell(
                move(target("card"), Zone.Battlefield),
                Step.If(kicked, then = listOf(Step.Do(Effect.GainLife(Players.You, mv))), otherwise = listOf(Step.Do(Effect.LoseLife(Players.You, mv)))),
                move(Objects.Self, Zone.Exile),
                targets = listOf(slot("card", inYourGraveyard(creature))),
            )
        },
    )

    // CR 702.33b, d: 3 life for each kicker cost paid
    @Test
    fun strongholdArena() = card(
        name = "Citadel Proving Pit",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            Kicker {G} and/or {W} (You may pay an additional {G} and/or {W} as you cast this spell.)
            When this enchantment enters, you gain 3 life for each time it was kicked.
            Whenever one or more creatures you control deal combat damage to a player, you may reveal the top card of your library and put it into your hand. If you do, you lose life equal to its mana value.
        """,
    ) parsesTo abilities(
        kicker("{G}", "{W}"),
        triggered(entersSelf, Step.Do(Effect.GainLife(Players.You, Value.Times(timesKicked, 3)))),
        // CR 603.2c: once for each player dealt combat damage; "you may … If you do" (CR 608.2d)
        triggered(
            Trigger.On(listOf(EventPattern.Damage(creatureYouControl, arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true)), oneOrMore = true,
                batchPerPlayer = true),
            Step.Pay(
                steps = listOf(
                    Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
                    move(Objects.Bound("revealed"), Zone.Hand),
                ),
                payer = Players.You,
                mandatory = false,
                bind = "paid",
            ),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, Objects.Bound("revealed"), Combine.Single))))),
        ),
    )

    // CR 702.26: up to one permanent phases out for each kicker cost paid; CR 702.26b: phased-out ones aren't dealt damage
    @Test
    fun temporalFirestorm() = card(
        name = "Chronal Blazestorm",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            Kicker {1}{W} and/or {1}{U} (You may pay an additional {1}{W} and/or {1}{U} as you cast this spell.)
            Choose up to X creatures and/or planeswalkers you control, where X is the number of times this spell was kicked. Those permanents phase out.
            Chronal Blazestorm deals 5 damage to each creature and each planeswalker.
        """,
    ) parsesTo abilities(
        kicker("{1}{W}", "{1}{U}"),
        spell(
            Step.Choose(Choice.Objects(and(or(creature, planeswalker), you), n(0), timesKicked), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.PhaseOut(Objects.Bound("chosen"))),
            damage(5, toObjects(Objects.Each(or(creature, planeswalker)))),
        ),
    )

    // CR 509.1c: kicked, it must block this turn if able
    @Test
    fun timelyInterference() = card(
        name = "Untimely Meddling",
        cost = "{U}",
        type = "Instant",
        text = """
            Kicker {1}{R} (You may pay an additional {1}{R} as you cast this spell.)
            Target creature gets -1/-0 until end of turn. If this spell was kicked, that creature blocks this turn if able.
            Draw a card.
        """,
    ) parsesTo abilities(
        kicker("{1}{R}"),
        spell(
            pump(target("creature"), -1, 0),
            Step.If(kicked, then = listOf(untilEndOfTurn(target("creature"), Mechanic.MustBlock(ObjectFilter.Any)))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 613.4c: both changes apply; CR 608.2h: the cards counted as it resolves
    @Test
    fun tributeToUrborg() = card(
        name = "Offering to the Bog",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets an additional -1/-1 until end of turn for each instant and sorcery card in your graveyard.
        """,
    ) parsesTo abilities(
        kicker("{1}{U}"),
        Value.Times(count(inYourGraveyard(or(instant, sorcery))), -1).let { minus ->
            spell(
                pump(target("creature"), -2, -2),
                Step.If(kicked, then = listOf(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(minus, minus)))),
                targets = listOf(slot("creature", creature)),
            )
        },
    )

    // CR 614.1c: milled as it enters, three cards for each kicker cost paid; CR 604.3
    @Test
    fun urborgLhurgoyf() = card(
        name = "Fenmire Lhurgoyf",
        cost = "{1}{G}",
        type = "Creature — Lhurgoyf",
        pt = "*/1+*",
        text = """
            Kicker {U} and/or {B} (You may pay an additional {U} and/or {B} as you cast this spell.)
            As this creature enters, mill three cards for each time it was kicked.
            Fenmire Lhurgoyf's power is equal to the number of creature cards in your graveyard and its toughness is equal to that number plus 1.
        """,
    ) parsesTo abilities(
        kicker("{U}", "{B}"),
        static(Mechanic.Replacement(entersReplacement, modify = listOf(Modification.AsEnters(listOf(
            Step.Do(Effect.Mill(Players.You, Value.Times(timesKicked, 3)))))))),
        count(inYourGraveyard(creature)).let { creatures ->
            static(Mechanic.SetPowerToughness(creatures, Value.Sum(listOf(creatures, n(1)))), characteristicDefining = true)
        },
    )

    // CR 702.33b, d, CR 614.1c: two counters for each kicker cost paid; CR 611.2b: control for as long as you control this creature
    @Test
    fun vodalianMindsinger() = card(
        name = "Merrow Thoughtcaller",
        cost = "{1}{U}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "2/2",
        text = """
            Kicker {1}{R} and/or {1}{G}
            This creature enters with two +1/+1 counters on it for each time it was kicked.
            When this creature enters, gain control of target creature with power less than this creature's power for as long as you control this creature.
        """,
    ) parsesTo abilities(
        kicker("{1}{R}", "{1}{G}"),
        static(Mechanic.Replacement(entersReplacement, modify = listOf(Modification.EntersWithCounters(p1p1, Value.Times(timesKicked, 2))))),
        triggered(
            entersSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.Power(Comparison.Less, power(Objects.Self))))),
        ),
    )

    // CR 702.33d: the damage only if kicked, to the targeted player
    @Test
    fun aggressiveSabotage() = card(
        name = "Hostile Subversion",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Kicker {R} (You may pay an additional {R} as you cast this spell.)
            Target player discards two cards. If this spell was kicked, it deals 3 damage to that player.
        """,
    ) parsesTo abilities(
        kicker("{R}"),
        spell(
            discard(2, Players.Target("player")),
            Step.If(kicked, then = listOf(damage(3, Recipients(players = Players.Target("player"))))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 701.9a: "discard your hand" discards every card in it (the objects given, no count); CR 603.4
    @Test
    fun battlewingMystic() = card(
        name = "Stormwing Seer",
        cost = "{1}{U}",
        type = "Creature — Bird Wizard",
        pt = "2/1",
        text = """
            Kicker {R} (You may pay an additional {R} as you cast this spell.)
            Flying
            When this creature enters, if it was kicked, discard your hand, then draw two cards.
        """,
    ) parsesTo abilities(
        kicker("{R}"),
        Keyword.Flying,
        triggered(
            entersSelf,
            Step.Do(Effect.Discard(Players.You, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))),
                count = n(1), filter = ObjectFilter.Any)),
            draw(2),
            interveningIf = kicked,
        ),
    )

    // CR 101.4: each player chooses, then all sacrifice at once; CR 603.4
    @Test
    fun benalishSleeper() = card(
        name = "Dormant Infiltrator",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Human Soldier",
        pt = "3/1",
        text = """
            Kicker {B} (You may pay an additional {B} as you cast this spell.)
            When this creature enters, if it was kicked, each player sacrifices a creature of their choice.
        """,
    ) parsesTo abilities(
        kicker("{B}"),
        triggered(
            entersSelf,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Bound("player")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))),
            interveningIf = kicked,
        ),
    )

    // CR 611.2c: the creatures you control as it resolves gain menace; CR 702.111b
    @Test
    fun bogBadger() = card(
        name = "Marsh Brawler Badger",
        cost = "{2}{G}",
        type = "Creature — Badger",
        pt = "3/3",
        text = """
            Kicker {B} (You may pay an additional {B} as you cast this spell.)
            When this creature enters, if it was kicked, creatures you control gain menace until end of turn. (A creature with menace can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
        kicker("{B}"),
        triggered(entersSelf, gains(Objects.Each(creatureYouControl), Keyword.Menace), interveningIf = kicked),
    )

    // CR 702.33d; CR 613.4c: the counter and the -2/-2 both apply
    @Test
    fun chokingMiasma() = card(
        name = "Strangling Vapors",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            Kicker {G} (You may pay an additional {G} as you cast this spell.)
            If this spell was kicked, put a +1/+1 counter on a creature you control.
            All creatures get -2/-2 until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{G}"),
        spell(
            Step.If(kicked, then = listOf(
                Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen")))),
            pump(Objects.Each(creature), -2, -2),
        ),
    )

    // CR 603.4; CR 608.2h: the attacking creatures counted as it resolves
    @Test
    fun cleavingSkyrider() = card(
        name = "Splitting Skyknight",
        cost = "{2}{W}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = """
            Flash
            Kicker {2}{R} (You may pay an additional {2}{R} as you cast this spell.)
            Flying
            When this creature enters, if it was kicked, it deals X damage to any target, where X is the number of attacking creatures.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        kicker("{2}{R}"),
        Keyword.Flying,
        triggered(entersSelf, damage(count(and(attacking, creature)), toAny), targets = listOf(anyTarget), interveningIf = kicked),
    )

    // CR 702.33d: the target is chosen whether or not it's kicked (601.2c): "instead" changes only what happens to it
    @Test
    fun colossalGrowth() = card(
        name = "Titanic Surge",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Kicker {R} (You may pay an additional {R} as you cast this spell.)
            Target creature gets +3/+3 until end of turn. If this spell was kicked, instead that creature gets +4/+4 and gains trample and haste until end of turn.
        """,
    ) parsesTo abilities(
        kicker("{R}"),
        spell(
            Step.If(kicked,
                then = listOf(pump(target("creature"), 4, 4, has(Keyword.Trample, Keyword.Haste))),
                otherwise = listOf(pump(target("creature"), 3, 3))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 707.2: a copy of the target's copiable values; CR 603.4
    @Test
    fun elvishHydromancer() = card(
        name = "Sylvan Tidecaller",
        cost = "{2}{G}",
        type = "Creature — Elf Wizard",
        pt = "3/2",
        text = """
            Kicker {3}{U} (You may pay an additional {3}{U} as you cast this spell.)
            When this creature enters, if it was kicked, create a token that's a copy of target creature you control.
        """,
    ) parsesTo abilities(
        kicker("{3}{U}"),
        triggered(entersSelf, create(TokenSpec.CopyOf(target("creature"))), targets = listOf(slot("creature", creatureYouControl)), interveningIf = kicked),
    )
}
