package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 09: green cards, first part. A pump by the number of legendary creatures, entering or leaving
 * (CR 603.6), the cards looked at while scrying (CR 701.22), restricted mana whose spell can't be countered (CR 106.6), casting creatures
 * from the top of the library (CR 601.3), a revealed creature drawing a card, a sacrificed land and a search that changes with a big
 * creature, a fight after a conditional counter (CR 701.14), a granted dies trigger before a fight, mana for each attacking Treefolk and
 * green mana kept (CR 500.4), attaching on entering, modes that also pump (CR 700.2), drawing by the greatest toughness, and triggers on
 * spells that target this creature or creatures you don't control (CR 601.2c).
 */
class LtrChunk09GreenTest {
    @Test
    fun hillHolePorter() = card(
        name = "Hill Hole Porter",
        cost = "{3}{G}",
        type = "Creature — Dwarf",
        text = "Whenever this creature attacks, it gets +X/+X until end of turn, where X is the number of legendary creatures you control.",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(attacksSelf, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(legends, legends))),
    )

    @Test
    fun oldSingersSong() = card(
        name = "Old Singer's Song",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control gets +1/+1 and gains hexproof until end of turn. The Ring tempts you. (A creature with hexproof can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1, has(Keyword.Hexproof)), ringTempts, targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.6: entering or leaving
    @Test
    fun riverbankFarmer() = card(
        name = "Riverbank Farmer",
        cost = "{2}{G}",
        type = "Creature — Halfling Peasant",
        text = """When this creature enters or leaves the battlefield, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))), createFood()),
    )

    // CR 701.22: the number of cards looked at while scrying
    @Test
    fun amrothTheWise() = card(
        name = "Amroth the Wise",
        cost = "{3}{G}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever you attack with one or more Elves, scry 1.
            Whenever you scry, Amroth gets +1/+1 until end of turn for each card looked at while scrying this way.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(and(sub("Elf"), you))), oneOrMore = true), scry(1)),
        triggered(youScry, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.EventAmount, Value.EventAmount))),
    )

    @Test
    fun wanderingCompanyElves() = card(
        name = "Wandering Company Elves",
        cost = "{2}{G}",
        type = "Creature — Elf Warrior",
        text = "Whenever you scry, put a +1/+1 counter on this creature. This ability triggers only once each turn.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(youScry, counters(Objects.Self), perTurn = 1),
    )

    // CR 106.6: legendary spells only, and that spell can't be countered
    @Test
    fun merryHeartedHalfling() = card(
        name = "Merry-Hearted Halfling",
        cost = "{G}",
        type = "Creature — Halfling Citizen",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a legendary spell, and that spell can't be countered.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, legendary),
            spellGets = listOf(Mechanic.CantBeCountered)), Players.You))),
    )

    // CR 701.54a: without a Ring-bearer
    @Test
    fun northernRangers() = card(
        name = "Northern Rangers",
        cost = "{3}{G}",
        type = "Creature — Human Ranger",
        text = "Landfall — Whenever a land you control enters, if you don't control a Ring-bearer, the Ring tempts you.",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(landfall, ringTempts, interveningIf = Condition.Not(Condition.Exists(yourRingBearer, atLeast = 1)), abilityWord = "Landfall"),
    )

    // CR 601.3: creature spells from the top of the library; a mana ability granted
    @Test
    fun elvenChoir() = card(
        name = "Elven Choir",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            You may look at the top card of your library any time.
            You may cast creature spells from the top of your library.
            Creatures you control have "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(creature, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        static(has(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))), affects = Objects.Each(creatureYouControl)),
    )

    // CR 701.20: a creature card revealed this way draws a card
    @Test
    fun elvenForesight() = card(
        name = "Elven Foresight",
        cost = "{G}",
        type = "Sorcery",
        text = "Scry 3, then you may reveal the top card of your library. If a creature card is revealed this way, draw a card.",
    ) parsesTo abilities(
        spell(scry(3), Step.May(listOf(doing(Effect.Reveal(topCard, bind = "revealed"))), Players.You),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), creature), then = listOf(draw(1)))),
    )

    @Test
    fun wrathfulHuorn() = card(
        name = "Wrathful Huorn",
        cost = "{4}{G}",
        type = "Creature — Treefolk",
        text = """
            Trample
            When this creature enters, the Ring tempts you.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, ringTempts),
    )

    // CR 701.23: the search changes with a creature with power 4 or greater ("instead")
    @Test
    fun entishRenewal() = card(
        name = "Entish Renewal",
        cost = "{2}{G}",
        type = "Instant",
        text = "Sacrifice a land. Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle. If you control a creature with power 4 or greater, instead search your library for up to three basic land cards, put them onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(Step.Choose(Choice.Objects(and(land, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            Step.If(Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), atLeast = 1),
                then = listOf(search(basicLand(), max = 3), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
                otherwise = listOf(search(basicLand(), max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle))),
    )

    // CR 701.14: the counter only if its power is 4 or greater
    @Test
    fun entsWrath() = card(
        name = "Ent's Wrath",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Put a +1/+1 counter on target creature you control if its power is 4 or greater. Then that creature gets +1/+1 until end of turn and fights target creature you don't control.",
    ) parsesTo abilities(
        spell(Step.If(Condition.ObjectMatches(target("creature"), power(Comparison.AtLeast, 4)), then = listOf(counters(target("creature")))),
            pump(target("creature"), 1, 1), doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    // CR 714.2b; a granted dies trigger, then a fight with up to one other target (CR 115.3: a different creature from the first target)
    @Test
    fun fallOfTheElvenKing() = card(
        name = "Fall of the Elven King",
        cost = "{1}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Scry 2.
            II — Put two +1/+1 counters on target creature you control.
            III — Until end of turn, target creature you control gains "When this creature dies, draw two cards." Then that creature fights up to one other target creature.
        """,
    ) parsesTo abilities(
        chapter(1, scry(2)),
        chapter(2, counters(target("creature"), 2), targets = listOf(slot("creature", creatureYouControl))),
        chapter(3, untilEndOfTurn(target("creature"), has(triggered(diesSelf, draw(2)))), doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creature, min = 0).copy(distinctFrom = listOf("creature")))),
    )

    // CR 500.4: green mana kept; mana for each attacking Treefolk
    @Test
    fun bregaladTreeShepherd() = card(
        name = "Bregalad, Tree Shepherd",
        cost = "{4}{G}{G}{G}",
        type = "Legendary Creature — Treefolk",
        text = """
            Treefolk you control have vigilance.
            Whenever one or more Treefolk you control attack, add twice that much {G}.
            You don't lose unspent green mana as steps and phases end.
        """,
        pt = "4/10",
    ) parsesTo abilities(
        static(has(Keyword.Vigilance), affects = Objects.Each(and(sub("Treefolk"), you))),
        triggered(Trigger.On(listOf(EventPattern.Attack(and(sub("Treefolk"), you))), oneOrMore = true),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!, times = Value.Times(Value.EventAmount, 2))), Players.You))),
        playerStatic(Mechanic.KeepsUnspentMana(listOf(Color.Green))),
    )

    // CR 701.3a: attached as it enters, the creature untapped
    @Test
    fun woodlandElfBow() = card(
        name = "Woodland Elf Bow",
        cost = "{2}{G}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. Untap that creature.
            Equipped creature gets +1/+2 and has reach.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), untapIt(target("creature")),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 2), has(Keyword.Reach), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun woodlandElfGuide() = card(
        name = "Woodland Elf Guide",
        cost = "{3}{G}",
        type = "Creature — Elf Scout",
        text = "When this creature enters, scry 2.",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
    )

    @Test
    fun kindlyEnt() = card(
        name = "Kindly Ent",
        cost = "{5}{G}",
        type = "Creature — Treefolk",
        text = """
            Reach
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Forestcycling {1} ({1}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "5/7",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, createFood()),
        Keyword.Typecycling(sub("Forest"), "Forestcycling", manaCost("{1}")),
    )

    @Test
    fun giftOfGoldenStrands() = card(
        name = "Gift of Golden Strands",
        cost = "{3}{G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, scry 2.
            Enchanted creature gets +3/+3.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, scry(2)),
        static(pt(3, 3), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 700.2: "choose one and [it] gets +1/+1": the pump with either mode; CR 509.1c, 509.1b
    @Test
    fun erestorDauntlessRescuer() = card(
        name = "Erestor, Dauntless Rescuer",
        cost = "{2}{G}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever you scry, choose one and Erestor gets +1/+1 until end of turn.
            • Erestor must be blocked this turn if able.
            • Erestor can't be blocked by more than one creature each combat this turn.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Ability.Triggered(youScry, Instructions(modes = modes(
            mode(pump(Objects.Self, 1, 1), untilEndOfTurn(Objects.Self, Mechanic.MustBeBlocked)),
            mode(pump(Objects.Self, 1, 1), untilEndOfTurn(Objects.Self, Mechanic.MaximumBlockers(1)))))),
    )

    // CR 208.1: the greatest toughness among your creatures
    @Test
    fun finalMarchOfTheEnts() = card(
        name = "Final March of the Ents",
        cost = "{6}{G}{G}",
        type = "Sorcery",
        text = """
            This spell can't be countered.
            Draw cards equal to the greatest toughness among creatures you control, then put any number of creature cards from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        spell(draw(Value.Of(NumericProperty.Toughness, Objects.Each(creatureYouControl), Combine.Greatest)),
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), null), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 601.2c: spells that target this creature, and spells that target a creature you don't control
    @Test
    fun haldirMasterArcher() = card(
        name = "Haldir, Master Archer",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Elf Archer",
        text = """
            Reach
            Whenever you cast a spell that targets Haldir, put a +1/+1 counter on Haldir.
            Whenever you cast a spell that targets a creature you don't control, Haldir deals damage equal to its power to up to one target creature.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(youCast(ObjectFilter.Targets(objects = Objects.Self)), counters(Objects.Self)),
        triggered(youCast(ObjectFilter.Targets(objects = Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))), damage(power(Objects.Self), toObjects(target("creature"))),
            targets = listOf(slot("creature", creature, min = 0))),
    )

    private val legends = count(and(legendary, creature, you))
    private val topCard = Objects.LibraryCards(Players.You, n(1), fromTop = true)
    @Suppress("unused") private val unused = listOf(Affected.Players(Players.You), Duration.EndOfTurn)
}
