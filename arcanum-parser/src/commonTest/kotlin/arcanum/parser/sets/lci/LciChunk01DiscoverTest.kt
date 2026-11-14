package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
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
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 01: discover (CR 701.57) and the cards around it. */
class LciChunk01DiscoverTest {
    private val bore = CounterKind.Named("bore")
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    /** CR 110.4b: an artifact, battle, creature, enchantment, or planeswalker spell. */
    private val permanentSpell = or(artifact, ObjectFilter.Type(CardType.Battle), creature, enchantment, planeswalker)
    private fun handOf(player: Players) = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))
    private fun reminder(n: Int) = "(Exile cards from the top of your library until you exile a nonland card with mana value $n or less. Cast it without " +
        "paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)"
    private fun manaValueOf(objects: Objects) = Value.Of(NumericProperty.ManaValue, objects, Combine.Single)

    /** CR 700.11: "you descended this turn": a permanent card was put into your graveyard from anywhere this turn. */
    private val descended = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentCard, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
        to = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)

    // CR 700.11, 701.27a, 603.4: the counter only if you descended; then three or more counters are removed and it transforms
    // CR 106.3, 701.57a: the land's trigger: a permanent spell cast with its mana discovers that spell's mana value
    @Test
    fun gearwrightsBoreEngine() = cardFaces(Layout.Transform,
        card(
            name = "Gearwright's Bore-Engine",
            cost = "{2}{R}",
            type = "Legendary Artifact",
            text = """
                When Gearwright's Bore-Engine enters, discard any number of cards, then draw that many cards plus one.
                At the beginning of your end step, if you descended this turn, put a bore counter on Gearwright's Bore-Engine. Then if there are three or more bore counters on it, remove those counters and transform it. (You descended if a permanent card was put into your graveyard from anywhere.)
            """,
        ) to abilities(
            triggered(entersSelf,
                Step.Choose(Choice.Objects(handOf(Players.You), n(0), null), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
                doing(Effect.Draw(Players.You, Value.Sum(listOf(Value.Count(Objects.Bound("discarded")), n(1)))))),
            triggered(yourEndStep,
                counters(Objects.Self, 1, bore),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(bore, atLeast = 3)),
                    doing(Effect.RemoveCounters(Objects.Self, bore, Value.Of(NumericProperty.Counters(bore), Objects.Self, Combine.Single))),
                    transformSelf),
                interveningIf = descended),
        ),
        card(
            name = "Cinderdeep, the Molten Rift",
            cost = null,
            type = "Legendary Land — Cave",
            text = """
                (Transforms from Gearwright's Bore-Engine.)
                {T}: Add {R}.
                Whenever you cast a permanent spell using mana produced by Cinderdeep, discover X, where X is that spell's mana value.
            """,
        ) to abilities(
            addOne("{R}"),
            triggered(youCast(permanentSpell), discover(manaValueOf(Objects.EventObject(EventRole.Object))),
                triggersOnlyIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.Object), source = ObjectFilter.Is(Objects.Self)),
                    Comparison.Greater, n(0))),
        ),
    )

    // CR 115.1, 508.1: up to three targets can't block; then discover 4
    @Test
    fun boldExcavation() = card(
        name = "Bold Excavation",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Up to three target creatures can't block this turn.
            Discover 4. ${reminder(4)}
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(target("creatures")), Duration.EndOfTurn)),
            discover(4), targets = listOf(slot("creatures", creature, min = 0, max = 3))),
    )

    // CR 701.57c: the discovered card's mana value; with no discovered card nothing is created
    @Test
    fun strikeTheDeepSeam() = card(
        name = "Strike the Deep Seam",
        cost = "{4}{R}{R}{R}",
        type = "Sorcery",
        text = "Discover 10. If the discovered card's mana value is less than 10, create a number of tapped Treasure tokens equal to the difference. " +
            "(To discover 10, exile cards from the top of your library until you exile a nonland card with mana value 10 or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)",
    ) parsesTo abilities(
        spell(doing(Effect.Action(KeywordAction.Discover(n(10)), bind = "discovered")),
            ifThen(Condition.And(listOf(Condition.NonEmpty("discovered"),
                Condition.Compare(manaValueOf(Objects.Bound("discovered")), Comparison.Less, n(10)))),
                createTreasure(Value.Difference(n(10), manaValueOf(Objects.Bound("discovered"))), tapped = true))),
    )

    // CR 701.6a, 701.57a: X is the countered spell's mana value, as it last existed (CR 608.2h)
    @Test
    fun flingIntoAntiquity() = card(
        name = "Fling into Antiquity",
        cost = "{3}{U}{U}",
        type = "Instant",
        text = "Counter target artifact or creature spell. Discover X, where X is that spell's mana value. (Exile cards from the top of your library until you exile a nonland card with that mana value or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), discover(manaValueOf(target("spell"))),
            targets = listOf(slot("spell", and(or(artifact, creature), spellFilter)))),
    )

    // CR 606.3; the −6 adds one red mana per card actually exiled, and those cards may be played this turn (CR 305.1)
    @Test
    fun velorianThask() = card(
        name = "Velorian Thask",
        cost = "{3}{R}{W}",
        type = "Legendary Planeswalker — Quintorius",
        loyalty = "4",
        text = """
            Whenever you cast a spell from exile, Velorian Thask deals 2 damage to each opponent and you gain 2 life.
            [+1]: Create a 3/2 red and white Spirit creature token.
            [−3]: Discover 4.
            [−6]: Exile any number of target cards from your graveyard. Add {R} for each card exiled this way. You may play those cards this turn.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Exile)), damage(2, eachOpponent), gainLife(2)),
        loyalty(1, create(creatureToken("Spirit", listOf(Color.Red, Color.White), 3, 2))),
        loyalty(-3, discover(4)),
        loyalty(-6,
            move(target("cards"), Zone.Exile, postBind = "exiled"),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!, times = Value.Count(Objects.Bound("exiled")))), Players.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            targets = listOf(slot("cards", inYourGraveyard(cardFilter), min = 0, max = null))),
    )

    // CR 702.6a, 113.1a: the equipped creature has reach and two activated abilities
    @Test
    fun corsairsLash() = card(
        name = "Corsair's Lash",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has reach, "{2}, {T}: Tap target artifact or creature," and "{8}, {T}: Discover 10." ${reminder(10)}
            Equip {1}
        """,
    ) parsesTo abilities(
        static(has(Keyword.Reach),
            has(activated(cost(mana("{2}"), tap), tapIt(target("permanent")), targets = listOf(slot("permanent", or(artifact, creature))))),
            has(activated(cost(mana("{8}"), tap), discover(10))),
            affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 613.1d, 613.4b: a Golem creature with base 5/4, and its other types kept; discover 3 when the Aura goes to a graveyard
    @Test
    fun animatingSigil() = card(
        name = "Animating Sigil",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact
            Enchanted artifact is a Golem creature with base power and toughness 5/4 in addition to its other types.
            When this Aura is put into a graveyard from the battlefield, discover 3. ${reminder(3)}
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(artifact)),
        static(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Golem")))),
            Mechanic.SetPowerToughness(n(5), n(4)), affects = Objects.AttachedTo(Objects.Self)),
        triggered(dies(ObjectFilter.Is(Objects.Self)), discover(3)),
    )

    // CR 701.57a: the owner discovers, from their own library; X is the target's mana value as it last existed (CR 608.2h)
    @Test
    fun matriarchsVerdict() = card(
        name = "Matriarch's Verdict",
        cost = "{1}{R}",
        type = "Instant",
        text = "The owner of target artifact or creature with mana value 1 or greater shuffles it into their library. Then that player discovers X, where X is its mana value. (They exile cards from the top of their library until they exile a nonland card with that mana value or less. They cast it without paying its mana cost or put it into their hand. They put the rest on the bottom in a random order.)",
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Library),
            doing(Effect.Shuffle(Players.OwnerOf(target("permanent")))),
            Step.ForEach(arcanum.api.Each.Players(Players.OwnerOf(target("permanent"))), "player", listOf(discover(manaValueOf(target("permanent")))),
                together = true),
            targets = listOf(slot("permanent", and(or(artifact, creature), manaValue(Comparison.AtLeast, 1))))),
    )

    // CR 113.6m, 602.5d: activated from the graveyard, as a sorcery
    @Test
    fun sunkenHoard() = card(
        name = "Sunken Hoard",
        cost = "{2}",
        type = "Artifact — Treasure",
        text = """
            {T}, Sacrifice this artifact: Add one mana of any color.
            {5}, Exile this card from your graveyard: Discover 5. Activate only as a sorcery. ${reminder(5)}
        """,
    ) parsesTo abilities(
        activated(cost(tap, sacrificeSelf), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{5}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), discover(5), rules = asSorcery),
    )

    // CR 118.12: tapping two untapped artifacts and/or creatures you control is a cost paid on resolution; then discover 3
    @Test
    fun itzaroDawnblade() = card(
        name = "Itzaro Dawnblade",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "4/4",
        text = "Whenever Itzaro Dawnblade attacks, you may tap two untapped artifacts and/or creatures you control. If you do, discover 3. ${reminder(3)}",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost = cost(CostPart.TapObjects(CostObjects.Choose(filter = or(artifact, creature), count = n(2)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), discover(3))),
    )

    // CR 101.2, 701.6a: spells you control can't be countered; discover 5 at your end step
    @Test
    fun tonalliTheCoreFlame() = card(
        name = "Tonalli, the Core Flame",
        cost = "{6}",
        type = "Legendary Artifact",
        text = """
            Spells you control can't be countered.
            At the beginning of your end step, discover 5. ${reminder(5)}
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered, affects = Objects.Each(and(spellFilter, you))),
        triggered(yourEndStep, discover(5)),
    )

    // CR 115.1, 602.5d: up to four target cards from one graveyard; CR 118.12: "you may pay {4}. If you do"
    @Test
    fun digSiteCustodian() = card(
        name = "Dig Site Custodian",
        cost = "{2}",
        type = "Artifact Creature — Gnome",
        pt = "2/1",
        text = """
            Sacrifice this creature: Exile up to four target cards from a single graveyard. Activate only as a sorcery.
            When this creature dies, you may pay {4}. If you do, discover 4. ${reminder(4)}
        """,
    ) parsesTo abilities(
        activated(cost(sacrificeSelf), move(target("card"), Zone.Exile),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 4).copy(sameOwner = true)), rules = asSorcery),
        triggered(diesSelf, Step.Pay(manaCost("{4}"), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), discover(4))),
    )

    // CR 303.4a: enchant creature you control; CR 701.57a: discover 3 when it enters
    @Test
    fun ochreTitansFavor() = card(
        name = "Ochre Titan's Favor",
        cost = "{2}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When this Aura enters, discover 3. ${reminder(3)}
            Enchanted creature gets +1/+1 and has trample.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        triggered(entersSelf, discover(3)),
        static(pt(1, 1), has(Keyword.Trample), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 603.4: only when it was cast (cast from any zone)
    @Test
    fun strataAssessor() = card(
        name = "Strata Assessor",
        cost = "{2}{R}{R}",
        type = "Creature — Human Artificer",
        pt = "3/2",
        text = "When this creature enters, if you cast it, discover 3. ${reminder(3)}",
    ) parsesTo abilities(
        triggered(entersSelf, discover(3), interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    /** The five Caves that enter tapped, tap for one colour and sacrifice to discover 4. */
    private fun veiledCave(name: String, color: String) = card(
        name = name,
        cost = null,
        type = "Land — Cave",
        text = """
            This land enters tapped.
            {T}: Add {$color}.
            {4}{$color}, {T}, Sacrifice this land: Discover 4. Activate only as a sorcery. ${reminder(4)}
        """,
    ) parsesTo abilities(
        entersTapped,
        addOne("{$color}"),
        activated(cost(mana("{4}{$color}"), tap, sacrificeSelf), discover(4), rules = asSorcery),
    )

    // CR 614.1c, 602.5d
    @Test
    fun veiledCascade() = veiledCave("Veiled Cascade", "U")

    // CR 614.1c, 602.5d
    @Test
    fun veiledAtrium() = veiledCave("Veiled Atrium", "W")

    // CR 614.1c, 602.5d
    @Test
    fun veiledOssuary() = veiledCave("Veiled Ossuary", "B")

    // CR 614.1c, 602.5d
    @Test
    fun veiledGrove() = veiledCave("Veiled Grove", "G")

    // CR 614.1c, 602.5d
    @Test
    fun veiledCaldera() = veiledCave("Veiled Caldera", "R")

    // CR 700.4: discover 3 when it dies
    @Test
    fun ancientBurrowmaw() = card(
        name = "Ancient Burrowmaw",
        cost = "{4}{B}",
        type = "Creature — Insect Horror",
        pt = "5/2",
        text = "When this creature dies, discover 3. ${reminder(3)}",
    ) parsesTo abilities(
        triggered(diesSelf, discover(3)),
    )

    // CR 702.19a; CR 113.6m: an activated ability whose cost discards the card works from the hand (the card deals the damage, CR 113.7a)
    @Test
    fun bellowingMegasaur() = card(
        name = "Bellowing Megasaur",
        cost = "{4}{R}{R}",
        type = "Creature — Dinosaur",
        pt = "7/6",
        text = """
            Trample
            When this creature enters, discover 5.
            {2}{R}, Discard this card: It deals 3 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, discover(5)),
        activated(cost(mana("{2}{R}"), CostPart.Discard(CostObjects.Self)), damage(3, toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 115.4: up to one target, then discover 4
    @Test
    fun strideWithTheElders() = card(
        name = "Stride with the Elders",
        cost = "{4}{G}",
        type = "Sorcery",
        text = "Return up to one target permanent card from your graveyard to your hand. Discover 4. ${reminder(4)}",
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Hand), discover(4), targets = listOf(slot("cards", inYourGraveyard(permanentCard), min = 0))),
    )
}
