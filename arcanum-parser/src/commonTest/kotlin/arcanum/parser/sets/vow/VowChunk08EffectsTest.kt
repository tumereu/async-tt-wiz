package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 08: effects first read here: it becoming night (CR 732.1), a chosen number, each player's choice and the rest returned, modes
 * that may all be skipped (CR 700.2b), a land gaining a mana ability, counters as it enters by the greatest count of a player's creatures,
 * a token copy of a card a cost exiled, keywords counted among creatures, all graveyards exiled, a card exiled from a graveyard with a
 * reflexive modal trigger (CR 603.12), a cost reduction by card types shared with exiled cards, and an attack trigger that checks the
 * creatures attacked with this combat.
 */
class VowChunk08EffectsTest {
    private val otherAttacker = and(another, ObjectFilter.AttackedThisCombat, creature, you)
    private val exiled = Objects.Bound("exiled")
    private val graveyardCard = and(cardFilter, inZone(Zone.Graveyard))
    private val exileAllGraveyards = move(Objects.Each(graveyardCard), Zone.Exile, postBind = "exiled")

    // CR 506.4, 508.1 (attacked with: declared as attackers, even if since removed from combat); CR 603.4 (intervening if); CR 613.4b
    @Test
    fun creepyPuppeteer() = card(
        name = "Grinning Marionettist",
        cost = "{3}{R}",
        type = "Creature — Human Rogue",
        pt = "4/3",
        text = """
            Haste
            Whenever this creature attacks, if you attacked with exactly one other creature this combat, you may have that creature's base power and toughness become 4/3 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf,
            Step.May(listOf(untilEndOfTurn(Objects.Each(otherAttacker), Mechanic.SetPowerToughness(n(4), n(3)))), player = Players.You),
            interveningIf = Condition.And(listOf(Condition.Exists(otherAttacker, atLeast = 1), Condition.Not(Condition.Exists(otherAttacker, atLeast = 2))))),
    )

    // CR 732.1 (it becomes night); CR 701.9 (discard any number, then draw that many plus one)
    @Test
    fun intoTheNight() = card(
        name = "Dusk Descends Early",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "It becomes night. Discard any number of cards, then draw that many cards plus one.",
    ) parsesTo abilities(
        spell(
            doing(Effect.BecomeDayOrNight(night = true)),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))), n(0), null), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
            doing(Effect.Draw(Players.You, Value.Sum(listOf(Value.Count(Objects.Bound("discarded")), n(1)))))),
    )

    // CR 607.2a (cards exiled with this creature), CR 601.2f (a cost reduction for each card type the spell shares with them, CR 205.2a)
    @Test
    fun cemeteryProwler() = card(
        name = "Tombyard Stalker",
        cost = "{1}{G}{G}",
        type = "Creature — Wolf",
        pt = "3/4",
        text = """
            Vigilance
            Whenever this creature enters or attacks, exile a card from a graveyard.
            Spells you cast cost {1} less to cast for each card type they share with cards exiled with this creature.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersOrAttacksSelf,
            Step.Choose(Choice.Objects(graveyardCard, n(1), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", exiled))),
        static(Mechanic.CostChange(and(spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, Value.Difference(
            Value.Sum(listOf(Value.CountDistinct(Objects.Tested, SharedQuality.CardType), Value.CountDistinct(exiledWith, SharedQuality.CardType))),
            Value.CountDistinct(Objects.Union(listOf(Objects.Tested, exiledWith)), SharedQuality.CardType))))),
    )

    // CR 608.2d (the number is chosen as it resolves); CR 101.4, 701.21a (each player chooses that many of theirs, then all are sacrificed)
    @Test
    fun byInvitationOnly() = card(
        name = "Guest List Purge",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Choose a number between 0 and 13. Each player sacrifices that many creatures of their choice.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Number(n(0), n(13)), bind = "number", chooser = Players.You),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), Value.Bound("number"), Value.Bound("number")),
                    bind = "sacrificed", chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
    )

    // CR 101.4 (each player chooses in turn order, then the rest return at once); CR 402.1 (cards in hand)
    @Test
    fun consumingTide() = card(
        name = "Swallowing Undertow",
        cost = "{2}{U}{U}",
        type = "Sorcery",
        text = "Each player chooses a nonland permanent they control. Return all nonland permanents not chosen this way to their owners' hands. Then you draw a card for each opponent who has more cards in their hand than you.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(not(land), permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                    bind = "chosen", chooser = Players.Bound("player")))),
            move(Objects.Except(Objects.Each(and(not(land), permanentFilter)), Objects.Bound("chosen")), Zone.Hand),
            doing(Effect.Draw(Players.You, Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.Greater, Value.HandSize(Players.You)))))))))),
    )

    // CR 700.2b (choose up to one: with no mode chosen, the ability is removed); CR 502.3
    @Test
    fun dreamshackleGeist() = card(
        name = "Sleepbinder Shade",
        cost = "{1}{U}{U}",
        type = "Creature — Spirit",
        pt = "3/1",
        text = """
            Flying
            At the beginning of combat on your turn, choose up to one —
            • Tap target creature.
            • Target creature doesn't untap during its controller's next untap step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(beginningOfCombatOnYourTurn, Instructions(modes = modes(
            mode(tapIt(target("creature")), targets = listOf(slot("creature", creature))),
            mode(doing(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(target("creature")),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(target("creature"))))), targets = listOf(slot("creature", creature))),
            min = 0))),
    )

    // CR 700.2; CR 605.1a (a land gains a mana ability until end of turn); CR 608.2c
    @Test
    fun gloriousSunrise() = card(
        name = "Radiant Daybreak Rite",
        cost = "{3}{G}{G}",
        type = "Enchantment",
        text = """
            At the beginning of combat on your turn, choose one —
            • Creatures you control get +1/+1 and gain trample until end of turn.
            • Target land gains "{T}: Add {G}{G}{G}" until end of turn.
            • Draw a card if you control a creature with power 3 or greater.
            • You gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(beginningOfCombatOnYourTurn, Instructions(modes = modes(
            mode(pump(Objects.Each(creatureYouControl), 1, 1, has(Keyword.Trample))),
            mode(gains(target("land"), Ability.Activated(cost(tap), Instructions(steps = listOf(
                doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{G}{G}")!!)), Players.You)))), anyTime, text = "{T}: Add {G}{G}{G}.")),
                targets = listOf(slot("land", land))),
            mode(ifThen(Condition.Exists(and(creature, power(Comparison.AtLeast, 3), you), atLeast = 1), draw(1))),
            mode(gainLife(3))))),
    )

    // CR 614.1c (enters with counters, counted as it enters); CR 122.1
    @Test
    fun investigatorsJournal() = card(
        name = "Sleuth's Casebook",
        cost = "{2}",
        type = "Artifact — Book Clue",
        text = """
            This artifact enters with a number of suspect counters on it equal to the greatest number of creatures a player controls.
            {2}, {T}, Remove a suspect counter from this artifact: Draw a card.
            {2}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(CounterKind.Named("suspect"), Value.GreatestFor(Players.Each(PlayerFilter.Any),
                Value.Count(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested)))))))))),
        activated(cost(mana("{2}"), tap, CostPart.RemoveCounters(CounterKind.Named("suspect"), n(1), CostObjects.Self)), draw(1)),
        activated(cost(mana("{2}"), sacrificeSelf), draw(1)),
    )

    // CR 613.1 (layers 4, 6, 7b), CR 613.4b, 205.1b (in addition to their other types)
    @Test
    fun sigardasSummons() = card(
        name = "Seraphic Muster",
        cost = "{4}{W}{W}",
        type = "Enchantment",
        text = "Creatures you control with +1/+1 counters on them have base power and toughness 4/4, have flying, and are Angels in addition to their other types.",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.Flying), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Angel")))),
            affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
    )

    // CR 707.9b (copy exceptions), 707.2; CR 601.2h (the card this activation's cost exiled); CR 608.2c ("It": the token)
    @Test
    fun dollhouseOfHorrors() = card(
        name = "Puppet Cabinet of Dread",
        cost = "{5}",
        type = "Artifact",
        text = "{1}, {T}, Exile a creature card from your graveyard: Create a token that's a copy of the exiled card, except it's a 0/0 Construct artifact in addition to its other types and it has \"This token gets +1/+1 for each Construct you control.\" It gains haste until end of turn. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, CostPart.Exile(CostObjects.Choose(creature, count = n(1)), from = Zone.Graveyard, postBind = "exiled")),
            create(TokenSpec.CopyOf(exiled, listOf(
                CopyException.PowerToughness(0, 0),
                CopyException.Types(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Construct")))),
                CopyException.AddAbilities(listOf(static(Mechanic.ModifyPowerToughness(count(and(sub("Construct"), you)), count(and(sub("Construct"), you))))
                    .copy(text = "This token gets +1/+1 for each Construct you control."))),
            )), bind = "token"),
            gains(Objects.Bound("token"), Keyword.Haste),
            rules = asSorcery),
    )

    // CR 702 (each listed keyword counted once if any creature you control has it); CR 111.10q (Blood tokens)
    @Test
    fun odricBloodCursed() = card(
        name = "Corvan, Blight-Sworn",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Vampire Soldier",
        pt = "3/3",
        text = "When Corvan enters, create X Blood tokens, where X is the number of abilities from among flying, first strike, double strike, deathtouch, haste, hexproof, indestructible, lifelink, menace, reach, trample, and vigilance found among creatures you control. (Count abilities, not creatures.)",
    ) parsesTo abilities(
        triggered(entersSelf, create(blood, Value.Sum(listOf(Keyword.Flying, Keyword.FirstStrike, Keyword.DoubleStrike, Keyword.Deathtouch, Keyword.Haste,
            Keyword.Hexproof, Keyword.Indestructible, Keyword.Lifelink, Keyword.Menace, Keyword.Reach, Keyword.Trample, Keyword.Vigilance).map {
            Value.If(Condition.Exists(and(creature, you, kw(it.kind)), atLeast = 1), n(1), n(0))
        }))),
    )

    // CR 120.2 (one damage event to each opponent and each of their creatures and planeswalkers)
    @Test
    fun endTheFestivities() = card(
        name = "Revelry's Last Ember",
        cost = "{R}",
        type = "Sorcery",
        text = "Revelry's Last Ember deals 1 damage to each opponent and each creature and planeswalker they control.",
    ) parsesTo abilities(
        spell(damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent),
            objects = Objects.Each(and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), opponents))))),
    )

    // CR 603.12 (when you do: a reflexive trigger only when a card was exiled), 700.2; CR 608.2h (the exiled card's mana value); CR 400.7
    // ("another": not this card, also once it's a new object in the graveyard); CR 122.8
    @Test
    fun cemeteryDesecrator() = card(
        name = "Gravefield Defiler",
        cost = "{4}{B}{B}",
        type = "Creature — Zombie",
        pt = "4/4",
        text = """
            Menace
            When this creature enters or dies, exile another card from a graveyard. When you do, choose one —
            • Remove X counters from target permanent, where X is the mana value of the exiled card.
            • Target creature an opponent controls gets -X/-X until end of turn, where X is the mana value of the exiled card.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))), Instructions(steps = listOf(
            Step.Choose(Choice.Objects(and(another, not(ObjectFilter.Is(Objects.SelfAfter)), cardFilter, inZone(Zone.Graveyard)), n(1), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.NonEmpty("exiled"), doing(Effect.ReflexiveTrigger(Instructions(modes = modes(
                mode(doing(Effect.RemoveCounters(target("permanent"), null, Value.Of(NumericProperty.ManaValue, exiled, Combine.Total))),
                    targets = listOf(slot("permanent", permanentFilter))).copy(text = "Remove X counters from target permanent, where X is the mana value of the exiled card."),
                mode(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(
                    Value.Times(Value.Of(NumericProperty.ManaValue, exiled, Combine.Total), -1), Value.Times(Value.Of(NumericProperty.ManaValue, exiled, Combine.Total), -1))),
                    targets = listOf(slot("creature", creatureOpponentControls))).copy(text = "Target creature an opponent controls gets -X/-X until end of turn, where X is the mana value of the exiled card."),
            )))))))),
    )

    // CR 606.3 (loyalty abilities); CR 614.1a (twice that many tokens); CR 400.7 (each card exiled this way)
    @Test
    fun kayaGeistHunter() = card(
        name = "Mirela, Shade Warden",
        cost = "{1}{W}{B}",
        type = "Legendary Planeswalker — Kaya",
        loyalty = "3",
        text = """
            [+1]: Creatures you control gain deathtouch until end of turn. Put a +1/+1 counter on up to one target creature token you control.
            [−2]: Until end of turn, if one or more tokens would be created under your control, twice that many of those tokens are created instead.
            [−6]: Exile all cards from all graveyards, then create a 1/1 white Spirit creature token with flying for each card exiled this way.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))), gains(Objects.Each(creatureYouControl), Keyword.Deathtouch), counters(target("token")),
            targets = listOf(slot("token", and(creature, token, you), min = 0))),
        activated(cost(CostPart.Loyalty(n(-2))), doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.CreateToken(you),
            modify = listOf(Modification.MultiplyAmount(2)))), null, Duration.EndOfTurn))),
        activated(cost(CostPart.Loyalty(n(-6))), exileAllGraveyards,
            create(creatureToken("Spirit", listOf(Color.White), 1, 1, listOf(Keyword.Flying)), Value.Count(exiled))),
    )

    // CR 406.1, 400.7
    @Test
    fun lanternOfTheLost() = card(
        name = "Lamp of Faded Souls",
        cost = "{1}",
        type = "Artifact",
        text = """
            When this artifact enters, exile target card from a graveyard.
            {1}, {T}, Exile this artifact: Exile all cards from all graveyards, then draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", graveyardCard))),
        activated(cost(mana("{1}"), tap, CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)), exileAllGraveyards, draw(1)),
    )
}
