package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
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
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
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
 * Phyrexia: All Will Be One chunk 09: cost reductions for abilities that target this creature and per counter (CR 601.2f), mana equal to a
 * sacrificed artifact's mana value (CR 106.1, 608.2h), a permanent becoming a creature for good (CR 611.2a), copies of the artifacts a spell
 * destroyed (CR 707.2), attaching to the creature that entered, copying a card exiled at random (CR 707.12), X/1 and mixed-ability tokens
 * (CR 111.4), a one-use prevention shield with an additional effect (CR 615.5, 615.7), "each other player" and a dies trigger counting the
 * creature's counters as it last existed (CR 603.10a).
 */
class OneChunk09EffectsTest {
    private val oil = CounterKind.Named("oil")
    private val oilOnSelf = Value.Of(NumericProperty.Counters(oil), Objects.Self, Combine.Single)
    private val x = Value.Announced(name = "X")
    private val cantBlock = Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
    /** 'a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."' */
    private val mite = creatureToken("Phyrexian Mite", emptyList(), 1, 1, abilities = listOf(Keyword.Toxic(1), cantBlock),
        types = listOf(CardType.Artifact, CardType.Creature))
    private val nextEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any))

    // CR 601.2f: Equipment spells cost less; CR 602.2b, 115.1: only Equipment abilities that target this creature
    @Test
    fun edgefusedAspirant() = card(
        name = "Edgefused Aspirant",
        cost = "{2}{R}",
        type = "Creature — Phyrexian Warrior",
        pt = "2/3",
        text = """
            Menace
            Equipment spells you cast cost {1} less to cast.
            Activated abilities of Equipment you control that target this creature cost {1} less to activate.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        static(Mechanic.CostChange(and(equipment, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = and(equipment, you)), ObjectFilter.Targets(objects = Objects.Self)),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 106.1: one {R} for each point of the sacrificed artifact's mana value as it last existed (CR 608.2h); CR 106.6: the restriction
    @Test
    fun scrapjawIronTinker() = card(
        name = "Scrapjaw, Iron Tinker",
        cost = "{2}{R}",
        type = "Legendary Creature — Phyrexian Goblin Artificer",
        pt = "3/3",
        text = "{T}, Sacrifice an artifact: Add an amount of {R} equal to the sacrificed artifact's mana value. Spend this mana only to cast artifact spells or activate abilities of artifacts.",
    ) parsesTo abilities(
        activated(cost(tap, sacrificeA(artifact, preBind = "sacrificed")), doing(Effect.AddMana(ManaProduction(
            ManaKind.Fixed(ManaCost.parse("{R}")!!, times = Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single)),
            restriction = or(and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact))), Players.You))),
    )

    // CR 611.2a: no duration, so it lasts while it's on the battlefield; CR 205.1b: the types are added; CR 613.1f: it loses its abilities, then
    // gains the two; CR 201.2: lands with different names among those you control; CR 602.5b: "Activate only if"
    @Test
    fun pillarOfCompletion() = card(
        name = "Pillar of Completion",
        cost = "{2}",
        type = "Artifact",
        text = """
            {3}, {T}: Search your library for a basic, Sphere, or Locus land card, reveal it, put it into your hand, then shuffle.
            {3}: This artifact becomes a 9/9 Phyrexian Construct artifact creature, loses all abilities, and gains indestructible and toxic 9. Activate only if there are nine or more lands with different names among the basic, Sphere, and Locus lands you control.
        """,
    ) parsesTo abilities(
        // "basic" and "Sphere" are only ever lands (CR 205.4c, 205.3i), so naming "land" once at the end of the list is enough
        activated(cost(mana("{3}"), tap), search(or(ObjectFilter.Super(Supertype.Basic), sub("Sphere"), and(sub("Locus"), land)), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(manaCost("{3}"), doing(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Phyrexian")), SubtypeRef.Literal(Subtype("Construct")))),
            Mechanic.SetPowerToughness(n(9), n(9)),
            Mechanic.LoseAbilities(),
            Mechanic.GainAbilities(listOf(Keyword.Indestructible, Keyword.Toxic(9)))), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            rules = anyTime.copy(condition = Condition.Compare(Value.CountDistinct(Objects.Each(or(
                and(ObjectFilter.Super(Supertype.Basic), land, you), and(sub("Sphere"), land, you), and(sub("Locus"), land, you))), SharedQuality.Name),
                Comparison.AtLeast, n(9)))),
    )

    // CR 701.8a: only the artifacts actually destroyed; CR 707.2: each token copies the card it became (CR 400.7); CR 603.7: a delayed trigger
    @Test
    fun crimsonStarsDusk() = card(
        name = "Crimson Star's Dusk",
        cost = "{X}{R}{R}",
        type = "Sorcery",
        text = "Destroy up to X target artifacts. If X is 5 or more, for each artifact destroyed this way, create a token that's a copy of it. Those tokens gain haste. Exile them at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(target("artifacts"), postBind = "destroyed")),
            Step.If(Condition.Compare(x, Comparison.AtLeast, n(5)), then = listOf(
                Step.ForEach(Each.Objects(Objects.Where(Objects.Bound("destroyed"), artifact)), bind = "card", steps = listOf(
                    doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("card")), n(1), Players.You, bind = "tokens")))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("tokens")), Duration.Indefinitely)),
                doing(Effect.CreateDelayedTrigger(nextEndStep, Instructions(steps = listOf(move(Objects.Bound("tokens"), Zone.Exile))))))),
            targets = listOf(TargetSlot("artifacts", TargetKind.Objects(artifact), min = n(0), max = x)),
            announced = listOf("X")),
    )

    // CR 701.3a: attach up to one Equipment to the creature that entered (the new object, CR 400.7); CR 613.4c: +1/+1 to equipped creatures
    @Test
    fun pashetSteadfastPride() = card(
        name = "Pashet, Steadfast Pride",
        cost = "{1}{W}",
        type = "Legendary Creature — Cat Cleric",
        pt = "2/2",
        text = """
            Whenever Pashet or another Cat you control enters, attach up to one target Equipment you control to that creature.
            Equipped creatures you control get +1/+1.
            {3}{W}{W}: Create a 2/2 white Cat creature token.
        """,
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Cat"), you))),
            doing(Effect.Attach(target("Equipment"), Recipients(objects = Objects.EventObject(EventRole.After)))),
            targets = listOf(slot("Equipment", and(equipment, you), min = 0))),
        static(pt(1, 1), affects = Objects.Each(and(ObjectFilter.Attached(attachments = equipment), creature, you))),
        activated(manaCost("{3}{W}{W}"), doing(Effect.CreateToken(creatureToken("Cat", listOf(Color.White), 2, 2), n(1), Players.You))),
    )

    // CR 601.2f: {3} less with nine or more cards in your graveyard; CR 707.12: the copy of the card is cast from exile without paying its cost
    @Test
    fun ficklePyrefiend() = card(
        name = "Fickle Pyrefiend",
        cost = "{3}{R}{R}{R}",
        type = "Creature — Phyrexian Dragon",
        pt = "4/4",
        text = """
            This spell costs {3} less to cast if you have nine or more cards in your graveyard.
            Flying
            When this creature enters, exile three cards at random from your graveyard. Choose a noncreature, nonland card from among them and copy it. You may cast the copy without paying its mana cost.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1)))),
            affects = Affected.Objects(Objects.Self), condition = Condition.Exists(ObjectFilter.And(yourGraveyard), atLeast = 9)),
        Keyword.Flying,
        triggered(entersSelf,
            move(Objects.Random(Objects.Each(inYourGraveyard(cardFilter)), n(3)), Zone.Exile, postBind = "exiled"),
            Step.Choose(arcanum.api.Choice.Objects(and(not(creature), not(land)), n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.CopyCard(Objects.Bound("chosen"), owner = Players.You, bind = "copy")),
            Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), how = PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You)),
    )

    // CR 601.2f: {1} less for each oil counter on it as the spell's cost is determined
    @Test
    fun thoughtgraftEngine() = card(
        name = "Thoughtgraft Engine",
        cost = "{3}{U}",
        type = "Artifact",
        text = """
            Flash
            At the beginning of your upkeep, put an oil counter on this artifact.
            Instant and sorcery spells you cast cost {1} less to cast for each oil counter on this artifact.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), counters(Objects.Self, 1, oil)),
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, oilOnSelf))),
    )

    // CR 111.4: an X/1 token, X counted as it's created (CR 608.2h); CR 603.7: sacrificed at the next end step
    @Test
    fun furnaceOfTheRedPraetor() = card(
        name = "Furnace of the Red Praetor",
        cost = "{2}{R}",
        type = "Artifact",
        text = "At the beginning of combat on your turn, put an oil counter on this artifact, then create an X/1 red Phyrexian Horror creature token with trample and haste, where X is the number of oil counters on this artifact. Sacrifice that token at the beginning of the next end step.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(Objects.Self, 1, oil),
            doing(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Phyrexian Horror Token", colorIndicator = listOf(Color.Red), types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Phyrexian"), Subtype("Horror")), power = PrintedNumber.X, toughness = PrintedNumber.Fixed(1),
                abilities = listOf(Keyword.Trample, Keyword.Haste)), power = oilOnSelf), n(1), Players.You, bind = "tokens")),
            doing(Effect.CreateDelayedTrigger(nextEndStep, Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("tokens")))))))),
    )

    // CR 111.4: tokens with a keyword and a quoted ability; CR 107.3: X of the spell; "other": all creatures but those tokens
    @Test
    fun paleStarsDusk() = card(
        name = "Pale Star's Dusk",
        cost = "{X}{W}{W}",
        type = "Sorcery",
        text = "You gain X life. Create X 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and \"This token can't block.\" If X is 5 or more, destroy all other creatures. (Players dealt combat damage by a creature with toxic 1 also get a poison counter.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.GainLife(Players.You, x)),
            doing(Effect.CreateToken(mite, x, Players.You, bind = "tokens")),
            Step.If(Condition.Compare(x, Comparison.AtLeast, n(5)), then = listOf(
                destroy(Objects.Each(and(creature, not(ObjectFilter.Is(Objects.Bound("tokens")))))))),
            announced = listOf("X")),
    )

    // CR 615.7: a shield for the next time only, this combat; CR 615.5: its additional effect makes a Mite for each point prevented
    @Test
    fun serenValeBaneOfHighfort() = card(
        name = "Seren Vale, Bane of Highfort",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Phyrexian Knight",
        pt = "3/4",
        text = """
            Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)
            At the beginning of combat on your turn, the next time target creature would deal combat damage to one or more players this combat, prevent that damage. If damage is prevented this way, create that many 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and "This token can't block."
        """,
    ) parsesTo abilities(
        Keyword.BattleCry,
        triggered(beginningOfCombatOnYourTurn,
            doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Is(target("creature")), RecipientFilter(players = PlayerFilter.Any), combat = true),
                additional = Instructions(steps = listOf(doing(Effect.CreateToken(mite, Value.EventAmount, Players.You)))))), null, Duration.EndOfCombat, uses = 1)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 608.2c: checked as it resolves; CR 301.5: the Equipment attached to that creature
    @Test
    fun rouseTheDormant() = card(
        name = "Rouse the Dormant",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. If it's equipped, you may destroy all Equipment attached to that creature.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            Step.If(Condition.ObjectMatches(target("creature"), ObjectFilter.Attached(attachments = equipment)), then = listOf(
                Step.May(listOf(destroy(Objects.AttachmentsOf(target("creature"), equipment))), player = Players.You))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 603.12: a reflexive trigger only if a card was discarded; CR 602.5d: as a sorcery
    @Test
    fun ichorheartManticore() = card(
        name = "Ichorheart Manticore",
        cost = "{2}{U}{R}",
        type = "Creature — Phyrexian Chimera",
        pt = "2/4",
        text = """
            Flying
            Whenever you cast a noncreature spell, put an oil counter on this creature.
            Remove three oil counters from this creature: Draw a card. Then you may discard a nonland card. When you discard a card this way, this creature deals 3 damage to target creature or planeswalker. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreatureSpell), counters(Objects.Self, 1, oil)),
        activated(cost(CostPart.RemoveCounters(oil, amount = n(3), from = CostObjects.Self)),
            draw(1),
            Step.Pay(cost(CostPart.Discard(CostObjects.Choose(nonland, n(1)), postBind = "discarded")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
                steps = listOf(damage(3, toObjects(target("permanent"))))))))),
            rules = asSorcery),
    )

    // CR 603.10a: a leaves-the-battlefield trigger counts the oil counters it had as it died
    @Test
    fun vigilantBlisterjelly() = card(
        name = "Vigilant Blisterjelly",
        cost = "{4}{U}{U}",
        type = "Creature — Phyrexian Jellyfish",
        pt = "4/4",
        text = """
            Flying
            This creature enters with an oil counter on it.
            When this creature dies, draw cards equal to the number of oil counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(oil, n(1))))), affects = Affected.Objects(Objects.Self)),
        triggered(diesSelf, doing(Effect.Draw(Players.You, Value.Of(NumericProperty.Counters(oil), Objects.EventObject(EventRole.Before), Combine.Single)))),
    )

    // CR 603.2: any player's first multicolored spell each turn; "each other player": everyone but its caster (CR 102.1)
    @Test
    fun apexAnnalist() = card(
        name = "Apex Annalist",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Construct",
        pt = "3/1",
        text = "Whenever a player casts their first multicolored spell each turn, each other player draws a card.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Multicolored, PlayerFilter.Any)), nthThisTurn = 1),
            draw(1, Players.Each(PlayerFilter.Not(PlayerFilter.Is(Players.EventPlayer))))),
    )

    // CR 613.4c: -1/-1 to the others; CR 400.7, 611.2a: the returned permanent gains the replacement (CR 614.1a: a move to exile isn't replaced)
    @Test
    fun morvekThaneOfBargains() = card(
        name = "Morvek, Thane of Bargains",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Phyrexian Zombie",
        pt = "3/4",
        text = """
            Other creatures you control get -1/-1.
            {1}{B}{B}, {T}: Return target creature card from your graveyard to the battlefield. It gains "If this creature would leave the battlefield, exile it instead of putting it anywhere else." Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(pt(-1, -1), affects = Objects.Each(and(another, creature, you))),
        activated(cost(mana("{1}{B}{B}"), tap),
            move(target("card"), Zone.Battlefield, postBind = "returned"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, notTo = Zone.Exile),
                modify = listOf(Modification.ChangeDestination(arcanum.api.Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))))),
                Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            targets = listOf(slot("card", inYourGraveyard(creature))), rules = asSorcery),
    )
}
