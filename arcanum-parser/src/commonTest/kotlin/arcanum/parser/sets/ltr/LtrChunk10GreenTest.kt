package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 10: green cards, second part. A creature type noted once for each chapter with the next creature spell of
 * that type entering with a counter (CR 714.2b, 603.7), Halflings attacking a player, sacrificing Foods, an extra Food with each token
 * creation (CR 614.1a), an optional sacrifice choosing the pump, Treefolk entering, looking at cards by a creature's mana value and a
 * creature card sharing no creature type (CR 700.8), revealing until X lands, a permanent having left the battlefield this turn, one
 * creature dealing damage to another, a Food sacrificed this turn, modes on entering or attacking, and a pump by the number of Forests.
 */
class LtrChunk10GreenTest {
    // CR 714.2b: one chapter ability for each chapter; CR 603.7: the next creature spell of the noted type this turn
    @Test
    fun longRollOfTheEnts() = card(
        name = "Long Roll of the Ents",
        cost = "{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after VI.)
            I, II, III, IV, V, VI — Note a creature type that hasn't been noted for this Saga. When you next cast a creature spell of that type this turn, that creature enters with an additional +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2, 3, 4, 5, 6),
            Step.Choose(Choice.CreatureTypeUnchosen, bind = "type", chooser = Players.You),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.Cast(and(creature, ObjectFilter.Sub(SubtypeRef.Chosen("type"))), PlayerFilter.You)),
                Instructions(steps = listOf(doing(Effect.SpellGets(Objects.EventObject(arcanum.api.EventRole.Object), listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))))))),
                duration = Duration.EndOfTurn))),
    )

    @Test
    fun goldenWoodLookout() = card(
        name = "Golden Wood Lookout",
        cost = "{1}{G}",
        type = "Creature — Elf Scout",
        text = "Whenever this creature attacks, scry 1.",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(attacksSelf, scry(1)),
    )

    @Test
    fun manyFarewells() = card(
        name = "Many Farewells",
        cost = "{G}",
        type = "Sorcery",
        text = """Search your library for a basic land card, reveal it, put it into your hand, then shuffle. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, createFood()),
    )

    // CR 508.1: one or more Halflings attacking a player
    @Test
    fun fredegarTheSteady() = card(
        name = "Fredegar the Steady",
        cost = "{1}{G}",
        type = "Legendary Creature — Halfling Citizen",
        text = """Whenever one or more Halflings you control attack a player, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(and(sub("Halfling"), you), target = RecipientFilter(players = PlayerFilter.Any))), oneOrMore = true, batchPerPlayer = true),
            createFood()),
    )

    @Test
    fun gloomwoodSpider() = card(
        name = "Gloomwood Spider",
        cost = "{G}",
        type = "Creature — Spider",
        text = """
            Deathtouch
            Whenever this creature attacks, target legendary creature you control gains deathtouch until end of turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(attacksSelf, gains(target("creature"), Keyword.Deathtouch), targets = listOf(slot("creature", and(legendary, creature, you)))),
    )

    @Test
    fun stillLakeGuardian() = card(
        name = "Still-Lake Guardian",
        cost = "{2}{G}",
        type = "Creature — Dwarf Soldier",
        text = "When this creature dies, the Ring tempts you.",
        pt = "4/2",
    ) parsesTo abilities(
        triggered(diesSelf, ringTempts),
    )

    @Test
    fun mushroomHounds() = card(
        name = "Mushroom Hounds",
        cost = "{1}{G}",
        type = "Creature — Dog",
        text = "Sacrifice a Food: Put a +1/+1 counter on this creature. It gains vigilance until end of turn. Activate only as a sorcery.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(sacrificeA(sub("Food"))), counters(Objects.Self), gains(Objects.Self, Keyword.Vigilance), rules = asSorcery),
    )

    // CR 614.1a: one more Food with the tokens created
    @Test
    fun paladinTook() = card(
        name = "Paladin Took",
        cost = "{2}{G}",
        type = "Legendary Creature — Halfling Citizen",
        text = """
            If one or more tokens would be created under your control, those tokens plus an additional Food token are created instead. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Sacrifice three Foods: Draw a card.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.AlsoCreate(food, n(1))))),
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(sub("Food"), count = n(3)))), draw(1)),
    )

    // CR 118.12: the pump depends on whether a Food was sacrificed
    @Test
    fun halflingsBravery() = card(
        name = "Halfling's Bravery",
        cost = "{G}",
        type = "Instant",
        text = "You may sacrifice a Food. If you do, target creature gets +4/+4 until end of turn. Otherwise, that creature gets +2/+2 until end of turn.",
    ) parsesTo abilities(
        spell(Step.Pay(cost(sacrificeA(sub("Food"))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(pump(target("creature"), 4, 4)), otherwise = listOf(pump(target("creature"), 2, 2))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun leaflockUpstartEnt() = card(
        name = "Leaflock, Upstart Ent",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Treefolk",
        text = "Whenever Leaflock or another Treefolk you control enters, up to two target creatures each get +2/+2 and gain trample until end of turn.",
        pt = "5/6",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Treefolk"), you))), pump(target("creatures"), 2, 2, has(Keyword.Trample)),
            targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 700.8: a creature card sharing no creature type with a creature you control
    @Test
    fun aiwendilTheBrown() = card(
        name = "Aiwendil the Brown",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Avatar Wizard",
        text = "Whenever Aiwendil or another nontoken creature you control enters, look at the top X cards of your library, where X is that creature's mana value. You may reveal a creature card that doesn't share a creature type with a creature you control from among those cards and put it into your hand. Put the rest on the bottom of your library in a random order.",
        pt = "2/5",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, not(token), creature, you))),
            doing(Effect.LookAt(lookedAt, Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(and(creature, not(ObjectFilter.Shares(SharedQuality.CreatureType, Objects.Each(creatureYouControl)))), n(0), n(1),
                from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))), move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun restoreTheShire() = card(
        name = "Restore the Shire",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """Return target permanent card from your graveyard to your hand. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand), createFood(), targets = listOf(slot("card", inYourGraveyard(permanentCard)))),
    )

    // CR 701.20: revealing until X lands, X the legendary creatures you control
    @Test
    fun theRingGoesEast() = card(
        name = "The Ring Goes East",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "The Ring tempts you. Then reveal cards from the top of your library until you reveal X land cards, where X is the number of legendary creatures you control. Put those land cards onto the battlefield tapped and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(ringTempts,
            doing(Effect.RevealUntil(land, Players.You, count(and(legendary, creature, you)), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 603.4: a permanent you controlled left the battlefield this turn
    @Test
    fun shortcutToTheFerry() = card(
        name = "Shortcut to the Ferry",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, the Ring tempts you.
            At the beginning of your end step, if a permanent you controlled left the battlefield this turn, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, ringTempts),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentFilter, you), from = Zone.Battlefield), atLeast = 1,
                measure = arcanum.api.Measure.Events)),
    )

    @Test
    fun volleyOfArrows() = card(
        name = "Volley of Arrows",
        cost = "{2}{G}",
        type = "Instant",
        text = "Destroy target artifact, enchantment, or creature with flying. Scry 1.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), scry(1), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying)))))),
    )

    @Test
    fun roastTheConeys() = card(
        name = "Roast the Coneys",
        cost = "{2}{G}",
        type = "Instant",
        text = """Target creature you control deals damage equal to its power to target creature you don't control. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""",
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")), createFood(),
            targets = listOf(slot("creature", creatureYouControl), slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    @Test
    fun woodwosePathfinder() = card(
        name = "Woodwose Pathfinder",
        cost = "{1}{G}",
        type = "Creature — Human Shaman",
        text = """
            {T}: Add one mana of any color.
            {6}{G}, {T}: Another target creature gets +3/+3 and gains trample until end of turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        activated(cost(mana("{6}{G}"), tap), pump(target("creature"), 3, 3, has(Keyword.Trample)), targets = listOf(slot("creature", and(another, creature)))),
    )

    // CR 603.4: a Food sacrificed this turn
    @Test
    fun rosamundGardner() = card(
        name = "Rosamund Gardner",
        cost = "{3}{G}",
        type = "Legendary Creature — Halfling Scout",
        text = """
            When Rosamund enters, create a Food token.
            At the beginning of your end step, if you sacrificed a Food this turn, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(entersSelf, createFood()),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.May(listOf(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle), Players.You),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Sacrifice(and(sub("Food"), you)), atLeast = 1, measure = arcanum.api.Measure.Objects)),
    )

    // CR 700.2: modes on entering or attacking
    @Test
    fun nimrodelGiftGiver() = card(
        name = "Nimrodel, Gift-Giver",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever Nimrodel enters or attacks, choose one —
            • Put a +1/+1 counter on another target creature.
            • Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Ability.Triggered(entersOrAttacksSelf, Instructions(modes = modes(
            mode(counters(target("creature")), targets = listOf(slot("creature", and(another, creature)))),
            mode(createFood()),
            mode(createTreasure())))),
    )

    // the number of Forests you control
    @Test
    fun greenwoodChanneler() = card(
        name = "Greenwood Channeler",
        cost = "{3}{G}",
        type = "Creature — Elf Druid",
        text = "At the beginning of combat on your turn, target Elf you control gains trample and gets +X/+X until end of turn, where X is the number of Forests you control.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, untilEndOfTurn(target("Elf"), Mechanic.GainAbilities(listOf(Keyword.Trample)), Mechanic.ModifyPowerToughness(forests, forests)),
            targets = listOf(slot("Elf", and(sub("Elf"), you)))),
    )

    private val forests = count(and(sub("Forest"), you))
    private val lookedAt = Objects.LibraryCards(Players.You, Value.Of(NumericProperty.ManaValue, Objects.EventObject(arcanum.api.EventRole.After), Combine.Single), fromTop = true)
    @Suppress("unused") private val unused = listOf(Affected.Players(Players.You), Comparison.AtLeast)
}
