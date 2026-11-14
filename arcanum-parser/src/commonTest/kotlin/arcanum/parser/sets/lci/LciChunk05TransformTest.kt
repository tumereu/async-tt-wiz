package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/** LCI chunk 05: transforming double-faced cards (CR 712): the Ojer gods and their temples, and permanents that transform themselves. */
class LciChunk05TransformTest {
    private val time = CounterKind.Time
    private val dread = CounterKind.Named("dread")
    private val selfPower = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)
    private fun addAny(colors: String): Ability = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors.map { c -> arcanum.api.ColorRef.Literal(mapOf('W' to Color.White, 'U' to Color.Blue, 'B' to Color.Black, 'R' to Color.Red, 'G' to Color.Green).getValue(c)) })), Players.You)))

    // CR 701.9a, 603.2: each opponent discards; one draw for each who couldn't; an opponent's land discard makes a Bat (any opponent, 3+ players)
    // CR 712.14: dies, returns transformed; CR 602.5b: the land transforms only if any player has one or fewer cards in hand
    @Test
    fun zotlacaiHollowTreachery() = cardFaces(Layout.Transform,
        card(
            name = "Zotlacai, Hollow Treachery",
            cost = "{3}{B}{B}",
            type = "Legendary Creature — Bat God",
            pt = "4/4",
            text = """
                Flying, lifelink
                Whenever Zotlacai attacks, each opponent discards a card. For each opponent who can't, you draw a card.
                Whenever an opponent discards a land card, create a 1/1 black Bat creature token with flying.
                When Zotlacai dies, return it to the battlefield tapped and transformed under its owner's control.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Lifelink,
            triggered(attacksSelf, Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Opponent)),
                bind = "opponent",
                together = true,
                steps = listOf(
                    Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))), payer = Players.Bound("opponent"),
                        mandatory = true, bind = "discarded"),
                    Step.If(Condition.CouldNot("discarded"), then = listOf(draw(1))),
                ),
            )),
            triggered(on(EventPattern.Discard(land, PlayerFilter.Opponent)),
                create(creatureToken("Bat", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Flying)))),
            diesReturnTransformed(),
        ),
        card(
            name = "Sanctum of the Hollow",
            cost = null,
            type = "Land",
            text = """
                (Transforms from Zotlacai, Hollow Treachery.)
                {T}: Add {B}.
                {2}{B}, {T}: Transform this land. Activate only if a player has one or fewer cards in hand and only as a sorcery.
            """,
        ) to abilities(
            addOne("{B}"),
            templeTransform("{2}{B}", Condition.PlayerExists(PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.AtMost, n(1))),
                atLeast = 1)),
        ),
    )

    // CR 714.2b: chapter II gives the Saga a static ability for as long as it stays (CR 611.2a), granting a mana ability (layer 6, CR 613.1f)
    @Test
    fun itzelSingerOfUnity() = cardFaces(Layout.Transform,
        card(
            name = "Itzel, Singer of Unity",
            cost = "{2}{G}",
            type = "Legendary Creature — Human Warrior Bard",
            pt = "2/3",
            text = """
                When Itzel enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
                {3}{R/W}{R/W}: Exile Itzel, then return her to the battlefield transformed under her owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            triggered(entersSelf, search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            activated(cost(mana("{3}{R/W}{R/W}")),
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))),
                rules = asSorcery),
        ),
        card(
            name = "Chant of the Sixth Tribe",
            cost = null,
            type = "Enchantment — Saga",
            colorIndicator = "RGW",
            text = """
                (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
                I — Create two 3/3 green Dinosaur creature tokens.
                II — This Saga gains "Creatures you control have '{T}: Add {R}, {G}, or {W}.'"
                III — Search your library for a Dinosaur card, reveal it, put it into your hand, then shuffle.
                IV — Dinosaurs you control gain double strike and trample until end of turn.
            """,
        ) to abilities(
            chapter(1, create(creatureToken("Dinosaur", listOf(Color.Green), 3, 3), count = n(2))),
            chapter(2, Step.Do(Effect.ApplyMechanics(listOf(has(static(has(addAny("RGW")), affects = Objects.Each(creatureYouControl)))),
                Affected.Objects(Objects.Self), Duration.Indefinitely))),
            chapter(3, search(sub("Dinosaur"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            chapter(4, gains(Objects.Each(and(sub("Dinosaur"), you)), Keyword.DoubleStrike, Keyword.Trample)),
        ),
    )

    // CR 614.1a, 616.1f: noncombat damage of a red source you control to an opponent, less than this creature's power, is raised to it
    // CR 602.5b, 120.1: the land counts all noncombat damage red sources you controlled dealt this turn
    @Test
    fun ojenTavrixDeepestFury() = cardFaces(Layout.Transform,
        card(
            name = "Ojen Tavrix, Deepest Fury",
            cost = "{2}{R}{R}",
            type = "Legendary Creature — God",
            pt = "4/4",
            text = """
                Trample
                If a red source you control would deal an amount of noncombat damage less than Ojen Tavrix's power to an opponent, that source deals damage equal to Ojen Tavrix's power instead.
                When Ojen Tavrix dies, return it to the battlefield tapped and transformed under its owner's control.
            """,
        ) to abilities(
            Keyword.Trample,
            static(Mechanic.Replacement(EventPattern.Damage(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Red)), you), RecipientFilter(players = PlayerFilter.Opponent),
                combat = false), modify = listOf(Modification.RaiseAmountTo(selfPower)))),
            diesReturnTransformed(),
        ),
        card(
            name = "Shrine of Fury",
            cost = null,
            type = "Land",
            text = """
                (Transforms from Ojen Tavrix, Deepest Fury.)
                {T}: Add {R}.
                {2}{R}, {T}: Transform this land. Activate only if red sources you controlled dealt 4 or more noncombat damage this turn and only as a sorcery.
            """,
        ) to abilities(
            addOne("{R}"),
            templeTransform("{2}{R}", Condition.HappenedThisTurn(EventPattern.Damage(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Red)), you), RecipientFilter(),
                combat = false), atLeast = 4, measure = Measure.Amount)),
        ),
    )

    // CR 510.2, 603.2: reveal as many cards as the combat damage dealt; up to one creature card and up to one land card onto the battlefield
    // together, the rest on the bottom in a random order (CR 401.4)
    @Test
    fun ojenMoskalDeepestBloom() = cardFaces(Layout.Transform,
        card(
            name = "Ojen Moskal, Deepest Bloom",
            cost = "{3}{G}{G}",
            type = "Legendary Creature — God",
            pt = "6/5",
            text = """
                Trample
                Whenever Ojen Moskal deals combat damage to a player, reveal that many cards from the top of your library. You may put a creature card and/or a land card from among them onto the battlefield. Put the rest on the bottom in a random order.
                When Ojen Moskal dies, return it to the battlefield tapped and transformed under its owner's control.
            """,
        ) to abilities(
            Keyword.Trample,
            triggered(combatDamageToAPlayer,
                doing(Effect.Reveal(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), bind = "looked")),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
                Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen"))),
                    bind = "chosen second", chooser = Players.You),
                move(Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("chosen second"))), Zone.Battlefield),
                doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("chosen second")))),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
            diesReturnTransformed(),
        ),
        card(
            name = "Shrine of Bloom",
            cost = null,
            type = "Land",
            text = """
                (Transforms from Ojen Moskal, Deepest Bloom.)
                {T}: Add {G}.
                {2}{G}, {T}: Transform this land. Activate only if you control ten or more permanents and only as a sorcery.
            """,
        ) to abilities(
            addOne("{G}"),
            templeTransform("{2}{G}", Condition.Exists(and(permanentFilter, you), atLeast = 10)),
        ),
    )

    // CR 702.88a, 400.7: the instant spell cast from your hand gains rebound while it is on the Stack; CR 712.14, 122.6: it returns with
    // three time counters; the land transforms only without time counters (CR 602.5b)
    @Test
    fun ojenQuetziDeepestTide() = cardFaces(Layout.Transform,
        card(
            name = "Ojen Quetzi, Deepest Tide",
            cost = "{2}{U}{U}",
            type = "Legendary Creature — God",
            pt = "4/3",
            text = """
                Flying
                Whenever you cast an instant spell from your hand, it gains rebound. (Exile it as it resolves. At the beginning of your next upkeep, you may cast it from exile without paying its mana cost.)
                When Ojen Quetzi dies, return it to the battlefield tapped and transformed under its owner's control with three time counters on it.
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(on(EventPattern.Cast(and(instant, spellFilter), PlayerFilter.You, from = Zone.Hand)),
                doing(Effect.ApplyMechanics(listOf(has(Keyword.Rebound)), Affected.Objects(Objects.EventObject(EventRole.Object)), Duration.Indefinitely))),
            diesReturnTransformed(listOf(CountersToPut(time, n(3)))),
        ),
        card(
            name = "Shrine of Turning Hours",
            cost = null,
            type = "Land",
            text = """
                (Transforms from Ojen Quetzi, Deepest Tide.)
                {T}: Add {U}. Remove a time counter from this land.
                {2}{U}, {T}: Transform this land. Activate only if it has no time counters on it and only as a sorcery.
            """,
        ) to abilities(
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)), Players.You)),
                Step.Do(Effect.RemoveCounters(Objects.Self, time, n(1)))),
            templeTransform("{2}{U}", Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(time, atLeast = 1)))),
        ),
    )

    // CR 614.1a: only creature tokens are tripled; CR 602.5b, 508.1: three or more creatures attacked this turn
    @Test
    fun ojenTolqaDeepestBedrock() = cardFaces(Layout.Transform,
        card(
            name = "Ojen Tolqa, Deepest Bedrock",
            cost = "{4}{W}{W}",
            type = "Legendary Creature — God",
            pt = "6/6",
            text = """
                Vigilance
                If one or more creature tokens would be created under your control, three times that many of those tokens are created instead.
                When Ojen Tolqa dies, return it to the battlefield tapped and transformed under its owner's control.
            """,
        ) to abilities(
            Keyword.Vigilance,
            static(Mechanic.Replacement(EventPattern.CreateToken(and(creature, you)), modify = listOf(Modification.MultiplyAmount(3)))),
            diesReturnTransformed(),
        ),
        card(
            name = "Shrine of Masonry",
            cost = null,
            type = "Land",
            text = """
                (Transforms from Ojen Tolqa, Deepest Bedrock.)
                {T}: Add {W}.
                {2}{W}, {T}: Transform this land. Activate only if you attacked with three or more creatures this turn and only as a sorcery.
            """,
        ) to abilities(
            addOne("{W}"),
            templeTransform("{2}{W}", Condition.HappenedThisTurn(EventPattern.Attack(and(creature, you)), atLeast = 3, measure = Measure.Objects)),
        ),
    )

    // CR 701.27a; CR 601.2a, 400.7d: one creature spell from your graveyard this turn; that spell and its permanent get a finality counter
    // as it enters and the Vampire type (layer 4, CR 613.1d)
    @Test
    fun velmirasDiary() = cardFaces(Layout.Transform,
        card(
            name = "Velmira's Diary",
            cost = "{1}{B}",
            type = "Legendary Artifact — Book",
            text = """
                {T}, Sacrifice another artifact or creature: Draw a card. Activate only as a sorcery.
                {2}, {T}, Discard your hand: Transform Velmira's Diary.
            """,
        ) to abilities(
            activated(cost(tap, sacrificeA(and(another, or(artifact, creature)))), draw(1), rules = asSorcery),
            activated(cost(mana("{2}"), tap, CostPart.Discard(CostObjects.These(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))))), transformSelf),
        ),
        card(
            name = "The Crypt of Velmira",
            cost = null,
            type = "Legendary Land — Cave",
            text = """
                (Transforms from Velmira's Diary.)
                {T}: Add {B}.
                {T}: You may cast a creature spell from your graveyard this turn. If you do, it enters with a finality counter on it and is a Vampire in addition to its other types. (If a creature with a finality counter on it would die, exile it instead.)
            """,
        ) to abilities(
            addOne("{B}"),
            activated(cost(tap), doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, perTurn = 1, holder = PlayerFilter.You,
                spellGets = listOf(
                    Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                        modify = listOf(Modification.EntersWithCounters(CounterKind.Finality, n(1)))),
                    Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire")))),
                ))), Affected.Objects(Objects.Each(inYourGraveyard(creature))), Duration.EndOfTurn))),
        ),
    )

    // CR 604.3: the token's power and toughness are defined by a characteristic-defining ability; CR 106.3, 603.2: mana from the land
    // spent on an artifact or creature spell
    @Test
    fun hundredSunsForge() {
        val artifactsAndCreatures = count(and(or(artifact, creature), you))
        val gnome = TokenSpec.Defined(FaceDefinition("Gnome Soldier Token", colorIndicator = listOf(Color.White), types = listOf(CardType.Artifact, CardType.Creature),
            subtypes = listOf(Subtype("Gnome"), Subtype("Soldier")), power = PrintedNumber.Star(), toughness = PrintedNumber.Star(), abilities = listOf(
                Ability.Static(listOf(Mechanic.SetPowerToughness(artifactsAndCreatures, artifactsAndCreatures)), affects = Affected.Objects(Objects.Self),
                    characteristicDefining = true))))
        cardFaces(Layout.Transform,
            card(
                name = "Hundred Suns Forge",
                cost = "{2}{W}{W}",
                type = "Legendary Artifact",
                text = """
                    When Hundred Suns Forge enters, create a white Gnome Soldier artifact creature token with "This token's power and toughness are each equal to the number of artifacts and/or creatures you control."
                    At the beginning of your first main phase, you may tap five untapped artifacts and/or creatures you control. If you do, transform Hundred Suns Forge.
                """,
            ) to abilities(
                triggered(entersSelf, create(gnome)),
                triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
                    Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), count = n(5)))), payer = Players.You, mandatory = false, bind = "paid"),
                    ifThen(Condition.Did("paid"), transformSelf)),
            ),
            card(
                name = "Garrison of the Hundred",
                cost = null,
                type = "Legendary Artifact Land",
                text = """
                    (Transforms from Hundred Suns Forge.)
                    {T}: Add {W}.
                    Whenever you cast an artifact or creature spell using mana produced by Garrison of the Hundred, create a white Gnome Soldier artifact creature token with "This token's power and toughness are each equal to the number of artifacts and/or creatures you control."
                """,
            ) to abilities(
                addOne("{W}"),
                triggered(youCast(or(artifact, creature)), create(gnome),
                    triggersOnlyIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.Object), source = ObjectFilter.Is(Objects.Self)),
                        Comparison.Greater, n(0))),
            ),
        )
    }

    // CR 603.2, 701.27a: this or another artifact entering pumps up to one creature; then it transforms with four or more artifacts
    @Test
    fun diviningRodEngine() = cardFaces(Layout.Transform,
        card(
            name = "Divining Rod Engine",
            cost = "{1}{R}",
            type = "Artifact",
            text = "Whenever this artifact or another artifact you control enters, up to one target creature you control gets +1/+0 and gains haste until end of turn. Then transform this artifact if you control four or more artifacts.",
        ) to abilities(
            triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, artifact, you))),
                pump(target("creature"), 1, 0, has(Keyword.Haste)),
                ifThen(Condition.Exists(and(artifact, you), atLeast = 4), transformSelf),
                targets = listOf(slot("creature", creatureYouControl, min = 0))),
        ),
        card(
            name = "Crystal Hollow",
            cost = null,
            type = "Land — Cave",
            text = """
                (Transforms from Divining Rod Engine.)
                {T}: Add {R}.
                {2}{R}, {T}: Until end of turn, target creature gains haste and gets +X/+0, where X is the number of artifacts you control. Activate only as a sorcery.
            """,
        ) to abilities(
            addOne("{R}"),
            activated(cost(mana("{2}{R}"), tap),
                untilEndOfTurn(target("creature"), has(Keyword.Haste), Mechanic.ModifyPowerToughness(count(and(artifact, you)), n(0))),
                targets = listOf(slot("creature", creature)), rules = asSorcery),
        ),
    )

    // CR 506.5: a creature attacking alone; CR 701.27a: "transform it" is this enchantment (the "it" with the dread counters), not the
    // attacking creature (audit: misread fixed)
    @Test
    fun clutchingGloom() = cardFaces(Layout.Transform,
        card(
            name = "Clutching Gloom",
            cost = "{3}{B}",
            type = "Enchantment",
            text = "Whenever a creature you control attacks alone, it gains deathtouch and lifelink until end of turn. Put a dread counter on this enchantment. Then if there are three or more dread counters on it, transform it.",
        ) to abilities(
            triggered(on(EventPattern.Attack(creatureYouControl, alone = true)),
                gains(Objects.EventObject(EventRole.Object), Keyword.Deathtouch, Keyword.Lifelink),
                counters(Objects.Self, 1, dread),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(dread, atLeast = 3)), transformSelf)),
        ),
        card(
            name = "Gloom's Den",
            cost = null,
            type = "Land — Cave",
            text = """
                (Transforms from Clutching Gloom.)
                {T}: Add {B}.
                {B}, {T}, Remove a dread counter from this land: You draw a card and you lose 1 life.
            """,
        ) to abilities(
            addOne("{B}"),
            activated(cost(mana("{B}"), tap, CostPart.RemoveCounters(dread, n(1), CostObjects.Self)), draw(1), loseLife(1)),
        ),
    )

    // CR 401.4: the rest on the bottom in any order; CR 603.4: an intervening "if" at the end step; CR 106.1: one {G} per creature
    @Test
    fun buddingRitesOfTzalmec() = cardFaces(Layout.Transform,
        card(
            name = "Budding Rites of Tzalmec",
            cost = "{2}{G}",
            type = "Legendary Enchantment",
            text = """
                When Budding Rites of Tzalmec enters, look at the top four cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in any order.
                At the beginning of your end step, if you control four or more creatures, transform Budding Rites of Tzalmec.
            """,
        ) to abilities(
            triggered(entersSelf,
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                doing(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1)))),
            triggered(yourEndStep, transformSelf, interveningIf = Condition.Exists(creatureYouControl, atLeast = 4)),
        ),
        card(
            name = "Tzalmec, Seat of Dawn",
            cost = null,
            type = "Legendary Land",
            text = """
                (Transforms from Budding Rites of Tzalmec.)
                {T}: Add {G}.
                {T}: Add {G} for each creature you control.
            """,
        ) to abilities(
            addOne("{G}"),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!, times = count(creatureYouControl))), Players.You))),
        ),
    )
}
