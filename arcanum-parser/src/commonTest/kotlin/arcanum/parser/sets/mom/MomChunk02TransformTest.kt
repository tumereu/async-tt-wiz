package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
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
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 02: transforming double-faced cards and Sieges, part 2 (CR 701.27, 712, 310). */
class MomChunk02TransformTest {
    private fun siege(name: String, cost: String, text: String, defense: String) =
        OracleCard(name, cost, "Battle — Siege", siegeReminder + "\n" + text.trimIndent(), defense = defense)

    /** "any other target": a creature, planeswalker, battle or player other than this object (CR 115.4) */
    private val anyOtherTarget = TargetSlot("any", TargetKind.Either(and(or(creature, ObjectFilter.Type(CardType.Planeswalker), battle), another),
        PlayerFilter.Any), min = n(1), max = n(1))
    private val hand: List<ObjectFilter> = listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You)))
    private val transformedPermanentYouControl = and(ObjectFilter.HasStatus(Status.Transformed), permanentFilter, you)
    /** "Exile [this], then return it to the battlefield transformed under its owner's control." (CR 701.27) */
    private val exileAndReturnTransformed = arrayOf(move(Objects.Self, Zone.Exile, postBind = "exiled"),
        Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))))
    /** "Exile this Saga, then return it to the battlefield (front face up)." (a new object, CR 400.7, 712.14) */
    private val exileAndReturnFrontFaceUp = arrayOf(move(Objects.Self, Zone.Exile, postBind = "exiled"),
        Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield))))
    private fun chapter(number: Int, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        Ability.Triggered(Trigger.Chapter(listOf(number)), Instructions(targets = targets, steps = steps.toList()))
    private fun transformsInto(name: String): Trigger = on(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named(name))))
    private val sagaReminder = "(As this Saga enters and after your draw step, add a lore counter.)"

    // CR 603.12: revealing no cards still triggers the reflexive ability ("X can be 0"); CR 608.2h: X counted as it resolves; CR 115.4
    @Test
    fun invasionOfDrakmoor() = cardFaces(Layout.Transform,
        siege("Invasion of Drakmoor", "{1}{R}",
            "When this Siege enters, reveal any number of Dragon cards from your hand. When you do, this Siege deals X plus 2 damage to any other target, where X is the number of cards revealed this way. (X can be 0.)",
            "5") to abilities(
            triggered(entersSelf,
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(sub("Dragon")) + hand), n(0), null), bind = "revealed", chooser = Players.You),
                doing(Effect.Reveal(Objects.Bound("revealed"))),
                reflexive(damage(Value.Sum(listOf(Value.Count(Objects.Bound("revealed")), n(2))), Recipients(target = "any")), targets = listOf(anyOtherTarget))),
        ),
        card(
            name = "Unbowed Stormwing",
            cost = null,
            type = "Creature — Dragon",
            text = """
                Flying, trample
                Whenever a Dragon you control attacks, it deals 2 damage to any target.
            """,
            pt = "4/4",
            colorIndicator = "R",
        ) to abilities(
            Keyword.Flying,
            Keyword.Trample,
            triggered(on(EventPattern.Attack(and(sub("Dragon"), you))), damage(2, toAny, source = Objects.EventObject(EventRole.Object)), targets = listOf(anyTarget)),
        ),
    )

    // CR 115.4: "any other target" excludes this Siege; CR 700.4
    @Test
    fun invasionOfGrimhollow() = cardFaces(Layout.Transform,
        siege("Invasion of Grimhollow", "{4}{B}", "When this Siege enters, it deals 3 damage to any other target and you gain 3 life.", "5") to abilities(
            triggered(entersSelf, damage(3, Recipients(target = "any")), gainLife(3), targets = listOf(anyOtherTarget)),
        ),
        card(
            name = "Granny Vesh Morrow",
            cost = null,
            type = "Legendary Creature — Human Wizard",
            text = """
                Flying
                Whenever a creature an opponent controls dies, put a +1/+1 counter on Granny Vesh Morrow and you gain 1 life.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            triggered(dies(creatureOpponentControls), counters(Objects.Self), gainLife(1)),
        ),
    )

    // CR 701.23; CR 205.1b, 613.1d: a land in addition to its other types while on the battlefield (its mana ability is its own, CR 305.6)
    @Test
    fun invasionOfTerrastone() = cardFaces(Layout.Transform,
        siege("Invasion of Terrastone", "{3}{G}",
            "When this Siege enters, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.", "3") to abilities(
            triggered(entersSelf, search(basicLand(), max = 2),
                move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        ),
        card(
            name = "Stirred Cliffvault",
            cost = null,
            type = "Creature — Elemental",
            text = """
                Vigilance, haste
                As long as this creature is on the battlefield, it's a land in addition to its other types.
                {T}: Add one mana of any color.
            """,
            pt = "4/4",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Vigilance,
            Keyword.Haste,
            static(Mechanic.SetTypes(add = listOf(CardType.Land)), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Battlefield))),
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        ),
    )

    // CR 702.21; CR 602.5b, 602.5d: both restrictions; CR 402.2, 611.2b: no maximum hand size while you control the Saga; CR 608.2g: the spells
    // are cast while the chapter resolves; CR 305.9: a land can't be cast; CR 712.14: it returns front face up
    @Test
    fun velQuorias() = cardFaces(Layout.Transform,
        card(
            name = "Vel-Quorias",
            cost = "{3}{U}{U}",
            type = "Legendary Creature — Phyrexian Praetor",
            text = """
                Ward {2}
                Whenever you cast a noncreature spell with mana value 3 or greater, draw a card.
                {3}{U}: Exile Vel-Quorias, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery and only if you have seven or more cards in hand.
            """,
            pt = "5/5",
        ) to abilities(
            Keyword.Ward(manaCost("{2}")),
            triggered(youCast(and(noncreatureSpell, manaValue(Comparison.AtLeast, 3))), draw(1)),
            activated(manaCost("{3}{U}"), *exileAndReturnTransformed,
                rules = asSorcery.copy(condition = Condition.Compare(Value.HandSize(Players.You), Comparison.AtLeast, n(7)))),
        ),
        card(
            name = "The Grand Distillation",
            cost = null,
            type = "Enchantment — Saga",
            text = """
                $sagaReminder
                I — Draw cards equal to the number of cards in your hand. You have no maximum hand size for as long as you control this Saga.
                II — Return all non-Phyrexian creatures to their owners' hands.
                III — You may cast any number of spells from your hand without paying their mana costs. Exile this Saga, then return it to the battlefield (front face up).
            """,
            colorIndicator = "U",
        ) to abilities(
            chapter(1, doing(Effect.Draw(Players.You, count(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You),
                    Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you))))),
            chapter(2, move(Objects.Each(and(not(sub("Phyrexian")), creature)), Zone.Hand)),
            chapter(3,
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(nonland) + hand), n(0), null), bind = "chosen", chooser = Players.You),
                Step.ForEach(Each.Objects(Objects.Bound("chosen")), bind = "card", steps = listOf(
                    doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))),
                *exileAndReturnFrontFaceUp),
        ),
    )

    // CR 701.27g: a transformed permanent is a double-faced permanent with its back face up; CR 613.4c
    @Test
    fun mutantAppraiser() = card(
        name = "Mutant Appraiser",
        cost = "{1}{G}{U}",
        type = "Creature — Vedalken Mutant",
        text = """
            Flying, vigilance
            This creature gets +1/+0 for each transformed permanent you control.
        """,
        pt = "0/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(Mechanic.ModifyPowerToughness(count(transformedPermanentYouControl), n(0))),
    )

    // CR 701.27g; CR 613.1f: the granted ability while the condition holds; CR 603.10a: a dies trigger looks back
    @Test
    fun lensbornDrakeling() = card(
        name = "Lensborn Drakeling",
        cost = "{3}{U}",
        type = "Creature — Phyrexian Dragon",
        text = """
            Flying
            As long as you control a transformed permanent, this creature has "When this creature dies, draw a card."
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(triggered(diesSelf, draw(1))), condition = Condition.Exists(transformedPermanentYouControl, atLeast = 1)),
    )

    // CR 509.3d: once for each creature blocking it; "the blocking creature" is that blocker
    @Test
    fun orderOfTheGlass() = cardFaces(Layout.Transform,
        card(
            name = "Order of the Glass",
            cost = "{1}{U}",
            type = "Creature — Human Knight",
            text = "{3}{W/P}: Transform this creature. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)",
            pt = "2/1",
        ) to abilities(transformAsSorcery("{3}{W/P}")),
        card(
            name = "Order of the Chalk Legion",
            cost = null,
            type = "Creature — Phyrexian Knight",
            text = "Whenever this creature becomes blocked by a creature, the blocking creature gets -1/-1 until end of turn.",
            pt = "3/3",
            colorIndicator = "WU",
        ) to abilities(
            triggered(on(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), creature, perBlocker = true)),
                pump(Objects.EventObject(EventRole.OtherCombatant), -1, -1)),
        ),
    )

    // CR 120.3: any source; the source's controller exiles at random (D25); CR 601.2, 608.2g: you may cast it while the ability resolves;
    // CR 305.9: a land is put onto the battlefield instead
    @Test
    fun maeveHeraldOfRuin() = cardFaces(Layout.Transform,
        card(
            name = "Maeve, Herald of Ruin",
            cost = "{1}{U}",
            type = "Legendary Creature — Human Wizard",
            text = """
                Whenever you cast a legendary spell, untap Maeve.
                {T}: Draw a card, then discard a card.
                {5}{B/P}: Transform Maeve. Activate only as a sorcery. ({B/P} can be paid with either {B} or 2 life.)
            """,
            pt = "1/3",
        ) to abilities(
            triggered(youCast(legendary), untapIt(Objects.Self)),
            activated(cost(tap), draw(1), discard(1)),
            transformAsSorcery("{5}{B/P}"),
        ),
        card(
            name = "Maeve, Academy Ravager",
            cost = null,
            type = "Legendary Creature — Phyrexian Wizard",
            text = """
                Trample
                Whenever a source deals damage to Maeve, that source's controller exiles a card from their hand at random. If it's a land card, you may put it onto the battlefield under your control. Otherwise, you may cast it without paying its mana cost.
            """,
            pt = "5/5",
            colorIndicator = "UB",
        ) to abilities(
            Keyword.Trample,
            run {
                val exiled = Objects.Bound("exiled")
                val player = Players.ControllerOf(Objects.EventObject(EventRole.Source))
                triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
                    doing(Effect.Move(Objects.Random(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))), n(1)),
                        Destination(Zone.Exile), postBind = "exiled")),
                    Step.If(Condition.NonEmpty("exiled"), then = listOf(
                        Step.If(Condition.ObjectMatches(exiled, land),
                            then = listOf(Step.May(listOf(move(exiled, Zone.Battlefield, controller = Players.You)), Players.You)),
                            otherwise = listOf(Step.May(listOf(doing(Effect.Cast(exiled, PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You))))))
            },
        ),
    )

    // CR 700.2: one mode; CR 701.27a, 701.27c: transforming an Incubator token (a double-faced token)
    @Test
    fun spawnpodTender() = card(
        name = "Spawnpod Tender",
        cost = "{2}{W}",
        type = "Creature — Phyrexian Cleric",
        text = """
            When this creature enters, choose one —
            • Put a +1/+1 counter on target artifact or creature you control.
            • Transform target Incubator token you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(counters(target("permanent")), targets = listOf(slot("permanent", and(or(artifact, creature), you)))),
            mode(doing(Effect.Transform(target("token"))), targets = listOf(slot("token", and(sub("Incubator"), token, you))))))),
    )

    // CR 508.4: the copy attacks what its controller chooses; CR 707.2, 707.8: a copy of the transformed permanent has its back face's
    // characteristics; CR 603.7: the delayed trigger sacrifices it
    @Test
    fun dawnKissedWarden() = cardFaces(Layout.Transform,
        card(
            name = "Dawn-Kissed Warden",
            cost = "{1}{W}",
            type = "Creature — Human Cleric",
            text = "{5}{R/P}: Transform this creature. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)",
            pt = "2/2",
        ) to abilities(transformAsSorcery("{5}{R/P}")),
        card(
            name = "Forge-Kissed Victor",
            cost = null,
            type = "Creature — Phyrexian Cleric",
            text = "Whenever this creature attacks, create a tapped and attacking token that's a copy of it. Put a +1/+1 counter on that token for each +1/+1 counter on this creature. Sacrifice that token at the beginning of the next end step.",
            pt = "3/3",
            colorIndicator = "WR",
        ) to abilities(
            triggered(attacksSelf,
                doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You, tapped = true, attacking = arcanum.api.AttackTarget.Chosen, bind = "token")),
                counters(Objects.Bound("token"), Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single)),
                delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("token"))))),
        ),
    )

    // CR 602.5b, 602.5d; CR 120.2: one damage event to the opponent and their creatures; CR 611.2c: the permission also covers cards put into
    // graveyards later this turn; CR 614.1a: a spell cast this way is exiled instead
    @Test
    fun kharavex() = cardFaces(Layout.Transform,
        card(
            name = "Kharavex",
            cost = "{2}{R}{R}",
            type = "Legendary Creature — Phyrexian Praetor",
            text = """
                First strike
                Whenever you cast an instant or sorcery spell, Kharavex deals 1 damage to target opponent. Add {R}.
                {R}: Exile Kharavex, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery and only if you've cast three or more instant and/or sorcery spells this turn.
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.FirstStrike,
            triggered(youCast(or(instant, sorcery)), damage(1, Recipients(players = Players.Target("opponent"))),
                doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{R}")!!)), Players.You)), targets = listOf(targetOpponent)),
            activated(manaCost("{R}"), *exileAndReturnTransformed,
                rules = asSorcery.copy(condition = Condition.HappenedThisTurn(EventPattern.Cast(or(instant, sorcery), PlayerFilter.You), atLeast = 3,
                    measure = Measure.Events))),
        ),
        card(
            name = "The Grand Labor",
            cost = null,
            type = "Enchantment — Saga",
            text = """
                $sagaReminder
                I — This Saga deals 3 damage to target opponent and each creature they control.
                II — Create three Treasure tokens.
                III — Until end of turn, you may cast instant and sorcery spells from any graveyard. If a spell cast this way would be put into a graveyard, exile it instead. Exile this Saga, then return it to the battlefield (front face up).
            """,
            colorIndicator = "R",
        ) to abilities(
            chapter(1, damage(3, Recipients(players = Players.Target("opponent"),
                objects = Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))))), targets = listOf(targetOpponent)),
            chapter(2, createTreasure(n(3))),
            chapter(3,
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, then = Destination(Zone.Exile), thenInsteadOf = Zone.Graveyard,
                    holder = PlayerFilter.You)), Affected.Objects(Objects.Each(and(or(instant, sorcery), ObjectFilter.InZone(Zone.Graveyard)))),
                    Duration.EndOfTurn)),
                *exileAndReturnFrontFaceUp),
        ),
    )

    // CR 701.27a; CR 702.111
    @Test
    fun yokedGrazer() = cardFaces(Layout.Transform,
        card(
            name = "Yoked Grazer",
            cost = "{4}{G}",
            type = "Creature — Beast",
            text = "{4}{R/P}: Transform this creature. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)",
            pt = "4/5",
        ) to abilities(transformAsSorcery("{4}{R/P}")),
        card(
            name = "Armored Kilngrazer",
            cost = null,
            type = "Creature — Phyrexian Beast",
            text = "Menace (This creature can't be blocked except by two or more creatures.)",
            pt = "7/5",
            colorIndicator = "GR",
        ) to abilities(Keyword.Menace),
    )

    // CR 701.27f: "transforms into [back face]"; CR 611.2: play it until the end of your next turn
    @Test
    fun cagedOddity() = cardFaces(Layout.Transform,
        card(
            name = "Caged Oddity",
            cost = "{U}",
            type = "Creature — Weird",
            text = """
                Defender
                {3}{R/P}: Transform this creature. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)
            """,
            pt = "1/3",
        ) to abilities(Keyword.Defender, transformAsSorcery("{3}{R/P}")),
        card(
            name = "Perfected Oddmage",
            cost = null,
            type = "Creature — Phyrexian Weird",
            text = "When this creature transforms into Perfected Oddmage, exile the top card of your library. Until the end of your next turn, you may play that card.",
            pt = "3/3",
            colorIndicator = "UR",
        ) to abilities(
            triggered(transformsInto("Perfected Oddmage"), move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.UntilEndOfYourNextTurn))),
        ),
    )

    // CR 701.27a; CR 702.10
    @Test
    fun hurriedTinker() = cardFaces(Layout.Transform,
        card(
            name = "Hurried Tinker",
            cost = "{2}{R}",
            type = "Creature — Human Artificer",
            text = """
                Haste
                {3}{W/P}: Transform this creature. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)
            """,
            pt = "2/3",
        ) to abilities(Keyword.Haste, transformAsSorcery("{3}{W/P}")),
        card(
            name = "Oilwing Skinner",
            cost = null,
            type = "Creature — Phyrexian Artificer",
            text = "Flying, haste",
            pt = "3/4",
            colorIndicator = "RW",
        ) to abilities(Keyword.Flying, Keyword.Haste),
    )

    // CR 701.27f; CR 608.2h: X is this creature's power as the ability resolves (not a cast X, CR 107.3)
    @Test
    fun botanyTutor() = cardFaces(Layout.Transform,
        card(
            name = "Botany Tutor",
            cost = "{1}{G}",
            type = "Creature — Treefolk Druid",
            text = """
                When this creature enters, you gain 3 life.
                {6}{B/P}: Transform this creature. Activate only as a sorcery. ({B/P} can be paid with either {B} or 2 life.)
            """,
            pt = "1/3",
        ) to abilities(triggered(entersSelf, gainLife(3)), transformAsSorcery("{6}{B/P}")),
        card(
            name = "Blight Summoner",
            cost = null,
            type = "Creature — Phyrexian Treefolk",
            text = "When this creature transforms into Blight Summoner, target creature an opponent controls gets -0/-X until end of turn, where X is this creature's power.",
            pt = "3/3",
            colorIndicator = "GB",
        ) to abilities(
            triggered(transformsInto("Blight Summoner"),
                untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(n(0), Value.Times(power(Objects.Self), -1))),
                targets = listOf(slot("creature", creatureOpponentControls))),
        ),
    )

    // CR 107.3c: X is defined by "where X is", not the X it was cast with; CR 603.10a, 608.2h: its power as it last existed
    @Test
    fun hushbladeInformant() = card(
        name = "Hushblade Informant",
        cost = "{2}{B}",
        type = "Creature — Human Rogue",
        text = "When this creature dies, target creature an opponent controls gets -X/-X until end of turn, where X is this creature's power.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(diesSelf, run {
            val x = Value.Times(power(Objects.EventObject(EventRole.Before)), -1)
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(x, x))
        }, targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 111.4; CR 613.4c
    @Test
    fun invasionOfCorvane() = cardFaces(Layout.Transform,
        siege("Invasion of Corvane", "{2}{W}", "When this Siege enters, create a 2/2 white and blue Knight creature token with vigilance.", "5") to abilities(
            triggered(entersSelf, create(creatureToken("Knight", listOf(Color.White, Color.Blue), 2, 2, abilities = listOf(Keyword.Vigilance)))),
        ),
        card(
            name = "Corvane Battle Hymn",
            cost = null,
            type = "Enchantment",
            text = "Creatures you control get +1/+1.",
            colorIndicator = "W",
        ) to abilities(static(pt(1, 1), affects = Objects.Each(creatureYouControl))),
    )

    // CR 310.11: the Siege's own enters trigger
    @Test
    fun invasionOfAldmere() = cardFaces(Layout.Transform,
        siege("Invasion of Aldmere", "{2}{W}", "When this Siege enters, you gain 4 life and draw a card.", "5") to abilities(
            triggered(entersSelf, gainLife(4), draw(1)),
        ),
        card(
            name = "Aldmere Oathkeeper",
            cost = null,
            type = "Creature — Angel",
            text = "Flying, vigilance",
            pt = "4/4",
            colorIndicator = "W",
        ) to abilities(Keyword.Flying, Keyword.Vigilance),
    )

    // CR 603.4: the hand size is checked as it triggers and as it resolves; each opponent's own upkeep (CR 503)
    @Test
    fun invasionOfThornwald() = cardFaces(Layout.Transform,
        siege("Invasion of Thornwald", "{3}{B}", "When this Siege enters, target opponent discards two cards.", "4") to abilities(
            triggered(entersSelf, discard(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
        ),
        card(
            name = "Bramble Sprites",
            cost = null,
            type = "Creature — Faerie",
            text = """
                Flying
                At the beginning of each opponent's upkeep, if that player has two or fewer cards in hand, this creature deals 2 damage to them.
            """,
            pt = "2/2",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Opponent), damage(2, Recipients(players = Players.EventPlayer)),
                interveningIf = Condition.Compare(Value.HandSize(Players.EventPlayer), Comparison.AtMost, n(2))),
        ),
    )
}
