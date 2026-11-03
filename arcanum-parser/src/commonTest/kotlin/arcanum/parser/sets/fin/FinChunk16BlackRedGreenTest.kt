package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 16: black, red and green cards with shapes tested elsewhere in parts: sacrifice outlets, upkeep reveals (CR 701.20),
 * graveyard returns, kicker by sacrifice (CR 702.33), a target creature dealing damage by its power to each other creature, a land destroyed
 * and replaced, cards attacking alone (CR 506.5), casting from the graveyard free (CR 118.9), extra land plays (CR 305.2), Birds and
 * landfall, doubling counters (CR 701.10), mana abilities for any colour granted to other permanents. Hill Gigas, Iron Giant and Goobbue
 * Gardener have only covered keywords and mana abilities.
 */
class FinChunk16BlackRedGreenTest {
    @Test
    fun ahrimanEye() = card(
        name = "Ahriel Eye",
        cost = "{2}{B}",
        type = "Creature — Eye Horror",
        pt = "2/2",
        text = """
            Flying, deathtouch
            {3}, Sacrifice another creature or artifact: Draw a card.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Deathtouch, activated(cost(mana("{3}"), sacrificeA(and(another, or(creature, artifact)))), draw(1)))

    @Test
    fun salvageCrew() = card(
        name = "Salvage Crew",
        cost = "{2}{B}",
        type = "Creature — Human Artificer Warrior",
        pt = "2/3",
        text = "Whenever this creature or another creature or artifact you control dies, target opponent loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, or(creature, artifact), you))), loseLife(1, Players.Target("opponent")), gainLife(1),
            targets = listOf(targetOpponent)),
    )

    // CR 701.20
    @Test
    fun shadowConfidant() = card(
        name = "Shadow Confidant",
        cost = "{1}{B}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = "At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.",
    ) parsesTo abilities(
        triggered(yourUpkeep, doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")), move(Objects.Bound("revealed"), Zone.Hand),
            doing(Effect.LoseLife(Players.You, Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("revealed"), arcanum.api.Combine.Single)))),
    )

    @Test
    fun keepFighting() = card(
        name = "Keep Fighting",
        cost = "{2}{B}",
        type = "Instant",
        text = "Return up to two target creature cards from your graveyard to your hand.",
    ) parsesTo abilities(spell(move(target("cards"), Zone.Hand), targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))))

    @Test
    fun manyEyedOoze() = card(
        name = "Many-Eyed Ooze",
        cost = "{1}{B}",
        type = "Creature — Ooze Horror",
        pt = "1/1",
        text = "When this creature enters, each opponent discards a card.",
    ) parsesTo abilities(triggered(entersSelf, discard(1, Players.Each(PlayerFilter.Opponent))))

    @Test
    fun catfishTrader() = card(
        name = "Catfish Trader",
        cost = "{3}{B}",
        type = "Creature — Fish Citizen",
        pt = "3/4",
        text = """
            When this creature enters, you lose 1 life and create a Treasure token.
            Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, loseLife(1), createTreasure),
        triggered(attacksSelf, Step.Pay(cost(sacrificeA(and(another, or(creature, artifact)))), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), surveil(2))),
    )

    @Test
    fun overwhelm() = card(
        name = "Crushing Gravity",
        cost = "{2}{B}",
        type = "Instant",
        text = "Target creature gets -0/-9999 until end of turn.",
    ) parsesTo abilities(spell(pump(target("creature"), 0, -9999), targets = listOf(slot("creature", creature))))

    @Test
    fun bitterRevelation() = card(
        name = "Rancorous Revelation",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Look at the top three cards of your library. Put one of them into your hand and the rest into your graveyard.
            Flashback {6}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand), move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Graveyard)),
        Keyword.Flashback(manaCost("{6}{B}")),
    )

    // CR 113.6m (a triggered ability that works from the graveyard)
    @Test
    fun shamblingHusk() = card(
        name = "Shambling Husk",
        cost = "{2}{B}",
        type = "Creature — Mutant Horror",
        pt = "3/3",
        text = """
            This creature enters tapped.
            Whenever you cast a noncreature spell, you may pay {B}. If you do, return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        entersTapped,
        Ability.Triggered(youCast(noncreature), Instructions(steps = listOf(Step.Pay(cost(mana("{B}")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), move(Objects.Self, Zone.Hand)))), functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun cohortReinforcements() = card(
        name = "Cohort Reinforcements",
        cost = "{2}{B}",
        type = "Creature — Human Soldier",
        pt = "2/3",
        text = "When this creature enters, mill three cards and you gain 3 life. (To mill three cards, put the top three cards of your library into your graveyard.)",
    ) parsesTo abilities(triggered(entersSelf, mill(3), gainLife(3)))

    // CR 702.33a (kicker by sacrifice), 702.33d
    @Test
    fun vaynesBetrayal() = card(
        name = "Treacherous Envoy's Plot",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
            Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -6/-6 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(sacrificeA(or(artifact, creature))))),
        spell(ifThen(kicked, pump(target("creature"), -6, -6), otherwise = listOf(pump(target("creature"), -2, -2))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun seymourVoid() = card(
        name = "Sevmar Void",
        cost = "{4}{B}",
        type = "Legendary Creature — Spirit Avatar",
        pt = "5/5",
        text = "At the beginning of your upkeep, you may pay 1 life. If you do, draw a card and put a +1/+1 counter on Sevmar Void.",
    ) parsesTo abilities(
        triggered(yourUpkeep, Step.Pay(cost(CostPart.PayLife(n(1))), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), draw(1), counters(Objects.Self))),
    )

    @Test
    fun callTheRidgeRunner() = card(
        name = "Call the Ridge Runner",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Search your library for a Mountain card, reveal it, put it into your hand, then shuffle. Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
            Flashback {5}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(search(sub("Mountain"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, create(birdToken)),
        Keyword.Flashback(manaCost("{5}{R}")),
    )

    @Test
    fun runnerComet() = card(
        name = "Runner Comet",
        cost = "{X}{R}{R}",
        type = "Sorcery",
        text = """
            Runner Comet deals X damage to any target.
            Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
        """,
    ) parsesTo abilities(spell(damage(x, toAny), create(birdToken), targets = listOf(anyTarget), announced = listOf("X")))

    @Test
    fun reefSword() = card(
        name = "Reef Sword",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains first strike until end of turn.
            Equipped creature gets +1/+0.
            Equip {1}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), gains(target("creature"), Keyword.FirstStrike),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 0), affects = equipped),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 707.2, 603.7
    @Test
    fun theBlazeCrystal() = card(
        name = "The Blaze Crystal",
        cost = "{2}{R}{R}",
        type = "Legendary Artifact",
        text = """
            Red spells you cast cost {1} less to cast.
            Creatures you control have haste.
            {4}{R}{R}, {T}: Create a token that's a copy of target creature you control. Sacrifice it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Red)), spellFilter, you), arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(has(Keyword.Haste), affects = Objects.Each(creatureYouControl)),
        activated(cost(mana("{4}{R}{R}"), tap), doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.You, bind = "tokens")),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("tokens")))), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun manicLaughter() = card(
        name = "Manic Laughter",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards.
            Flashback {3}{R} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.Discard(arcanum.api.CostObjects.Choose(cardFilter, n(1)))))),
        spell(draw(2)),
        Keyword.Flashback(manaCost("{3}{R}")),
    )

    /** "Choose target creature you control. It deals damage equal to its power to each other creature." (the chosen creature deals it) */
    @Test
    fun villageAflame() = card(
        name = "Village Aflame",
        cost = "{2}{R}{R}",
        type = "Sorcery",
        text = """
            Choose target creature you control. It deals damage equal to its power to each other creature. If this spell was cast from a graveyard, discard your hand and draw four cards.
            Flashback {5}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(Objects.Each(and(not(ObjectFilter.Is(target("creature"))), creature))), source = target("creature")),
            ifThen(Condition.CastFrom(Zone.Graveyard, Objects.Self),
                doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))), draw(4)),
            targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Flashback(manaCost("{5}{R}{R}")),
    )

    @Test
    fun spinyPlant() = card(
        name = "Spiny Plant",
        cost = "{1}{R}",
        type = "Creature — Plant",
        pt = "2/1",
        text = """
            Reach
            Landfall — Whenever a land you control enters, this creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(Keyword.Reach, triggered(landfall, damage(1, eachOpponent), abilityWord = "Landfall"))

    @Test
    fun dunesWorm() = card(
        name = "Dunes Worm",
        cost = "{4}{R}",
        type = "Creature — Worm",
        pt = "5/4",
        text = """
            Haste
            When this creature enters, destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, destroy(target("land")), Step.May(listOf(
            doing(Effect.Search(Players.ControllerOf(target("land")), basicLand, max = n(1), reveal = false, zones = listOf(Zone.Library), bind = "found",
                searcher = Players.ControllerOf(target("land")))),
            doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true, controller = Players.ControllerOf(target("land"))))),
            doing(Effect.Shuffle(Players.ControllerOf(target("land"))))), Players.ControllerOf(target("land"))),
            targets = listOf(slot("land", land))),
    )

    // CR 506.5 (attacks alone), 118.9
    @Test
    fun saiferAlmase() = card(
        name = "Saifer Almase",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Knight",
        pt = "3/4",
        text = """
            Whenever a creature you control attacks alone, it gains double strike until end of turn.
            Fire Cross — Whenever Saifer Almase deals combat damage to a player, you may cast target instant or sorcery card with mana value 3 or less from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl, alone = true)), gains(thatSpell, Keyword.DoubleStrike)),
        triggered(combatDamageToAPlayer,
            Step.May(listOf(doing(Effect.Cast(target("card"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You))), Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely)),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery), manaValue(arcanum.api.Comparison.AtMost, 3)))), abilityWord = "Fire Cross"),
    )

    // CR 305.2
    @Test
    fun zellerDinch() = card(
        name = "Zeller Dinch",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Monk",
        pt = "0/3",
        text = """
            You may play an additional land on each of your turns.
            Zeller Dinch gets +1/+0 for each land you control.
            At the beginning of your end step, return a land you control to its owner's hand.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.ExtraLandPlays(1)),
        static(Mechanic.ModifyPowerToughness(count(and(land, you)), n(0))),
        triggered(yourEndStep, Step.Choose(Choice.Objects(and(land, you), n(1), n(1)), bind = "chosen", chooser = Players.You), move(Objects.Bound("chosen"), Zone.Hand)),
    )

    @Test
    fun skyshipCrash() = card(
        name = "Skyship Crash",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Destroy target artifact, enchantment, or creature with flying.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying)))))),
        Keyword.Cycling(manaCost("{2}")),
    )

    @Test
    fun meadowRexaur() = card(
        name = "Meadow Rexaur",
        cost = "{4}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "6/6",
        text = """
            Trample
            When this creature enters, you gain 3 life.
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(Keyword.Trample, triggered(entersSelf, gainLife(3)), Keyword.Typecycling(sub("Forest"), "Forestcycling", manaCost("{2}")))

    @Test
    fun strikerShot() = card(
        name = "Striker Shot",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature gets +3/+3 and gains trample until end of turn.",
    ) parsesTo abilities(spell(pump(target("creature"), 3, 3, has(Keyword.Trample)), targets = listOf(slot("creature", creature))))

    @Test
    fun runnerRacetrack() = card(
        name = "Runner Racetrack",
        cost = "{3}{G}{G}",
        type = "Artifact",
        text = "Landfall — Whenever a land you control enters, create a 2/2 green Bird creature token with \"Whenever a land you control enters, this token gets +1/+0 until end of turn.\"",
    ) parsesTo abilities(triggered(landfall, create(birdToken), abilityWord = "Landfall"))

    @Test
    fun communeWithOtters() = card(
        name = "Commune with Otters",
        cost = "{G}",
        type = "Sorcery",
        text = "Look at the top three cards of your library. You may reveal an artifact, creature, or land card from among them and put it into your hand. Put the rest on the bottom of your library in any order.",
    ) parsesTo abilities(
        spell(doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(artifact, creature, land), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))), move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Library, position = -1)),
    )

    @Test
    fun greatToad() = card(
        name = "Great Toad",
        cost = "{3}{G}",
        type = "Creature — Frog",
        pt = "4/4",
        text = "As long as you control seven or more lands, this creature gets +2/+2.",
    ) parsesTo abilities(static(pt(2, 2), condition = Condition.Exists(and(land, you), atLeast = 7)))

    @Test
    fun runnerGreens() = card(
        name = "Runner Greens",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
            Flashback {6}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(spell(create(birdToken)), Keyword.Flashback(manaCost("{6}{G}")))

    @Test
    fun colossalCactus() = card(
        name = "Colossal Cactus",
        cost = "{5}{G}{G}",
        type = "Creature — Plant",
        pt = "1/7",
        text = "10,000 Needles — Whenever this creature attacks, it gets +9999/+0 until end of turn.",
    ) parsesTo abilities(triggered(attacksSelf, pump(Objects.Self, 9999, 0), abilityWord = "10,000 Needles"))

    @Test
    fun burrowScout() = card(
        name = "Burrow Scout",
        cost = "{2}{G}",
        type = "Creature — Rabbit Scout",
        pt = "3/2",
        text = "Whenever another creature you control enters, this creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(triggered(enters(and(another, creature, you)), pump(Objects.Self, 1, 1)))

    // CR 613.1f (an ability granted to other permanents)
    @Test
    fun aWorldReborn() = card(
        name = "A World Reborn",
        cost = "{4}{G}{G}",
        type = "Enchantment",
        text = "Other permanents you control have \"{T}: Add one mana of any color.\"",
    ) parsesTo abilities(static(has(addAnyColor), affects = Objects.Each(and(another, permanentFilter, you))))

    // CR 701.10e (double counters)
    @Test
    fun saszKatroy() = card(
        name = "Sasz Katroy",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Pilot",
        pt = "3/3",
        text = """
            When Sasz Katroy enters, you may search your library for a Bird or basic land card, reveal it, put it into your hand, then shuffle.
            Whenever Sasz Katroy attacks, put a +1/+1 counter on target creature, then double the number of +1/+1 counters on that creature.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(or(sub("Bird"), basicLand), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), Players.You)),
        triggered(attacksSelf, counters(target("creature")), doing(Effect.Action(KeywordAction.Double(arcanum.api.Doubled.Counters(target("creature"), p1p1)))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun saszsChick() = card(
        name = "Sasz's Chick",
        cost = "{G}",
        type = "Creature — Bird",
        pt = "0/1",
        text = "Landfall — Whenever a land you control enters, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(triggered(landfall, counters(Objects.Self), abilityWord = "Landfall"))
}
