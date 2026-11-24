package arcanum.parser.sets.vow

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AttackTarget
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
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
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
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/** VOW chunk 01: transforming double-faced cards (CR 701.27, 712). Invented names, the real cards' oracle text. */
class VowChunk01TransformTest {

    // CR 508.1, 603.2c: one trigger for the declaration, only when exactly two creatures attack; CR 106.4: the mana is kept until end of turn
    @Test
    fun alluringSuitor() = cardFaces(Layout.Transform,
        card(
            name = "Courting Swain",
            cost = "{2}{R}",
            type = "Creature — Vampire",
            text = "When you attack with exactly two creatures, transform this creature.",
            pt = "2/3",
        ) to abilities(
            triggered(Trigger.On(listOf(EventPattern.Attack(and(creature, you))), oneOrMore = true, atLeast = 2, atMost = 2), transformSelf),
        ),
        card(
            name = "Ruinous Partner",
            cost = null,
            type = "Creature — Vampire",
            text = """
                Trample
                When this creature transforms into Ruinous Partner, add {R}{R}. Until end of turn, you don't lose this mana as steps and phases end.
                {R}{R}: This creature and another target creature each get +1/+0 until end of turn.
            """,
            pt = "3/3",
            colorIndicator = "R",
        ) to abilities(
            Keyword.Trample,
            triggered(transformsInto("Ruinous Partner"),
                doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!), keptUntil = Duration.EndOfTurn), Players.You))),
            // CR 115.3: "another target creature" is a creature other than this one
            activated(manaCost("{R}{R}"), pump(Objects.Union(listOf(Objects.Self, target("creature"))), 1, 0),
                targets = listOf(slot("creature", and(another, creature)))),
        ),
    )

    // CR 603.4: the intervening "if" counts creatures whose toughness is greater than their power; CR 608.2h: the sacrificed creature's
    // toughness as it last existed
    @Test
    fun catapultFodder() = cardFaces(Layout.Transform,
        card(
            name = "Siege Fodder",
            cost = "{2}{B}",
            type = "Creature — Zombie",
            text = "At the beginning of combat on your turn, if you control three or more creatures that each have toughness greater than their power, transform this creature.",
            pt = "1/5",
        ) to abilities(
            triggered(beginningOfCombatOnYourTurn, transformSelf, interveningIf = Condition.Exists(and(creature,
                ObjectFilter.Toughness(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Tested, Combine.Single)), you), atLeast = 3)),
        ),
        card(
            name = "Siege Overseer",
            cost = null,
            type = "Creature — Zombie",
            text = "{2}{B}, {T}, Sacrifice another creature: Target opponent loses life equal to the sacrificed creature's toughness.",
            pt = "2/6",
            colorIndicator = "B",
        ) to abilities(
            activated(cost(mana("{2}{B}"), tap, sacrificeA(and(another, creature), preBind = "sacrificed")),
                doing(Effect.LoseLife(Players.Target("opponent"), Value.Of(NumericProperty.Toughness, Objects.Bound("sacrificed"), Combine.Single))),
                targets = listOf(targetOpponent)),
        ),
    )

    // CR 701.27f: "transforms into" triggers only on becoming that face; CR 608.2d: you may choose no card, and then nothing is discarded or drawn
    @Test
    fun concealingCurtains() = cardFaces(Layout.Transform,
        card(
            name = "Veiling Drapes",
            cost = "{B}",
            type = "Creature — Wall",
            text = """
                Defender
                {2}{B}: Transform this creature. Activate only as a sorcery.
            """,
            pt = "0/4",
        ) to abilities(
            Keyword.Defender,
            activated(manaCost("{2}{B}"), transformSelf, rules = asSorcery),
        ),
        card(
            name = "Unveiled Watcher",
            cost = null,
            type = "Creature — Eye Horror",
            text = """
                Menace
                When this creature transforms into Unveiled Watcher, target opponent reveals their hand. You may choose a nonland card from it. If you do, that player discards that card, then draws a card.
            """,
            pt = "3/4",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Menace,
            triggered(transformsInto("Unveiled Watcher"),
                doing(Effect.Reveal(handOf(Players.Target("opponent")), bind = "revealed")),
                Step.Pay(steps = listOf(Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You)),
                    payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"),
                    doing(Effect.Discard(Players.Target("opponent"), objects = Objects.Bound("chosen"), count = n(1), filter = ObjectFilter.Any)),
                    draw(1, Players.Target("opponent"))),
                targets = listOf(targetOpponent)),
        ),
    )

    // CR 608.2h: the target's toughness is checked after the counter is put on it, as the ability resolves
    @Test
    fun dormantGrove() = cardFaces(Layout.Transform,
        card(
            name = "Sleeping Copse",
            cost = "{3}{G}",
            type = "Enchantment",
            text = "At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. Then if that creature has toughness 6 or greater, transform this enchantment.",
        ) to abilities(
            triggered(beginningOfCombatOnYourTurn, counters(target("creature")),
                ifThen(Condition.ObjectMatches(target("creature"), ObjectFilter.Toughness(Comparison.AtLeast, n(6))), transformSelf),
                targets = listOf(slot("creature", creatureYouControl))),
        ),
        card(
            name = "Gnarled Copsewalker",
            cost = null,
            type = "Creature — Treefolk",
            text = """
                Vigilance
                Other creatures you control have vigilance.
            """,
            pt = "3/6",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Vigilance,
            static(has(Keyword.Vigilance), affects = Objects.Each(and(another, creature, you))),
        ),
    )

    // CR 712.14 (returns transformed); CR 122.1: "remove those counters" removes every bloodline counter on it; both "it"s are this artifact
    @Test
    fun edgarCharmedGroom() = cardFaces(Layout.Transform,
        card(
            name = "Ansel, Doting Groom",
            cost = "{2}{W}{B}",
            type = "Legendary Creature — Vampire Noble",
            text = """
                Other Vampires you control get +1/+1.
                When Ansel dies, return it to the battlefield transformed under its owner's control.
            """,
            pt = "4/4",
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(and(another, sub("Vampire"), you))),
            triggered(diesSelf, doing(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, transformed = true,
                controller = Players.OwnerOf(Objects.EventObject(EventRole.After)))))),
        ),
        card(
            name = "Ansel's Casket",
            cost = null,
            type = "Legendary Artifact",
            text = "At the beginning of your upkeep, create a 1/1 white and black Vampire creature token with lifelink and put a bloodline counter on Ansel's Casket. Then if there are three or more bloodline counters on it, remove those counters and transform it.",
            colorIndicator = "WB",
        ) to abilities(
            triggered(yourUpkeep,
                create(creatureToken("Vampire", listOf(Color.White, Color.Black), 1, 1, abilities = listOf(Keyword.Lifelink))),
                counters(Objects.Self, 1, CounterKind.Named("bloodline")),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("bloodline"), atLeast = 3)),
                    doing(Effect.RemoveCounters(Objects.Self, CounterKind.Named("bloodline"),
                        Value.Of(NumericProperty.Counters(CounterKind.Named("bloodline")), Objects.Self, Combine.Single))),
                    transformSelf)),
        ),
    )

    // CR 700.2d: a mode chosen once is never available again; CR 101.4: each player sacrifices in turn order; "and/or" is any of the three
    @Test
    fun henrikaDomnathi() = cardFaces(Layout.Transform,
        card(
            name = "Mirela Vashti",
            cost = "{2}{B}{B}",
            type = "Legendary Creature — Vampire",
            text = """
                Flying
                At the beginning of combat on your turn, choose one that hasn't been chosen —
                • Each player sacrifices a creature of their choice.
                • You draw a card and you lose 1 life.
                • Transform Mirela.
            """,
            pt = "1/3",
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(beginningOfCombatOnYourTurn, Instructions(modes = modes(
                mode(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                    Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), min = n(1), max = n(1)),
                        bind = "sacrificed", chooser = Players.Bound("player")),
                    doing(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
                mode(draw(1), loseLife(1)),
                mode(transformSelf),
                onlyUnchosen = true,
            ))),
        ),
        card(
            name = "Mirela, Abyssal Seer",
            cost = null,
            type = "Legendary Creature — Vampire",
            text = """
                Flying, deathtouch, lifelink
                {1}{B}{B}: Each creature you control with flying, deathtouch, and/or lifelink gets +1/+0 until end of turn.
            """,
            pt = "3/4",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying, Keyword.Deathtouch, Keyword.Lifelink,
            activated(manaCost("{1}{B}{B}"), pump(Objects.Each(and(creature, you,
                or(kw(Keyword.Flying.kind), kw(Keyword.Deathtouch.kind), kw(Keyword.Lifelink.kind)))), 1, 0)),
        ),
    )

    // CR 101.4: each opponent in turn order may sacrifice; it transforms only if none of them did
    @Test
    fun innocentTraveler() = cardFaces(Layout.Transform,
        card(
            name = "Guileless Wayfarer",
            cost = "{2}{B}{B}",
            type = "Creature — Human",
            text = "At the beginning of your upkeep, any opponent may sacrifice a creature of their choice. If no one does, transform this creature.",
            pt = "1/3",
        ) to abilities(
            triggered(yourUpkeep,
                Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1))))), payer = Players.Each(PlayerFilter.Opponent), bind = "sacrificed"),
                ifThen(Condition.Not(Condition.Did("sacrificed")), transformSelf)),
        ),
        card(
            name = "Hungering Invader",
            cost = null,
            type = "Creature — Vampire",
            text = """
                Flying
                This creature gets +2/+0 as long as an opponent controls a Human.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            static(pt(2, 0), condition = Condition.Exists(and(sub("Human"), opponents), atLeast = 1)),
        ),
    )

    // CR 406.3: you may look at the face-down card while it stays exiled; CR 607.2a: Insight plays only the cards its own first ability
    // exiled (the front face's ability exiles none "with" it); CR 305.1, 601.2a: one land or spell a turn, without paying its mana cost
    @Test
    fun jacobHaukenInspector() = cardFaces(Layout.Transform,
        card(
            name = "Odric Vane, Examiner",
            cost = "{1}{U}",
            type = "Legendary Creature — Human Advisor",
            text = "{T}: Draw a card, then exile a card from your hand face down. You may look at that card for as long as it remains exiled. You may pay {4}{U}{U}. If you do, transform Odric Vane.",
            pt = "0/2",
        ) to abilities(
            activated(cost(tap),
                draw(1),
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))), n(1), n(1)), bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound("exiled"))), Affected.Players(Players.You), Duration.Indefinitely)),
                Step.Pay(manaCost("{4}{U}{U}"), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), transformSelf)),
        ),
        card(
            name = "Vane's Insight",
            cost = null,
            type = "Legendary Enchantment",
            text = """
                At the beginning of your upkeep, exile the top card of your library face down. You may look at that card for as long as it remains exiled.
                Once during each of your turns, you may play a land or cast a spell from among the cards exiled with this permanent without paying its mana cost.
            """,
            colorIndicator = "U",
        ) to abilities(
            triggered(yourUpkeep,
                doing(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound("exiled"))), Affected.Players(Players.You), Duration.Indefinitely))),
            Ability.Static(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, withoutPaying = true, perTurn = 1, holder = PlayerFilter.You)),
                affects = Affected.Objects(exiledWith), condition = Condition.YourTurn),
        ),
    )

    // CR 603.4: checked at your end step and again on resolution; CR 701.9: a discard this turn
    @Test
    fun raggedRecluse() = cardFaces(Layout.Transform,
        card(
            name = "Tattered Hermit",
            cost = "{1}{B}",
            type = "Creature — Human Peasant",
            text = "At the beginning of your end step, if you discarded a card this turn, transform this creature.",
            pt = "2/1",
        ) to abilities(
            triggered(yourEndStep, transformSelf, interveningIf = Condition.HappenedThisTurn(EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any),
                atLeast = 1, measure = Measure.Events)),
        ),
        card(
            name = "Hateful Hag",
            cost = null,
            type = "Creature — Human Warlock",
            text = "Whenever this creature attacks, defending player loses 1 life and you gain 1 life.",
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            triggered(attacksSelf, loseLife(1, Players.DefendingPlayerOf(Objects.EventObject(EventRole.Object))), gainLife(1)),
        ),
    )

    // CR 701.20b: looked at by you alone, then revealed if you choose; CR 707.2, 508.4: tapped and attacking copies, attacking whom you choose
    @Test
    fun runoStromkirk() = cardFaces(Layout.Transform,
        card(
            name = "Volo Tidehold",
            cost = "{1}{U}{B}",
            type = "Legendary Creature — Vampire Cleric",
            text = """
                Flying
                When Volo enters, put up to one target creature card from your graveyard on top of your library.
                At the beginning of your upkeep, look at the top card of your library. You may reveal that card. If a creature card with mana value 6 or greater is revealed this way, transform Volo.
            """,
            pt = "1/4",
        ) to abilities(
            Keyword.Flying,
            triggered(entersSelf, move(target("card"), Zone.Library, position = 0),
                targets = listOf(slot("card", inYourGraveyard(creature), min = 0))),
            triggered(yourUpkeep,
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You)),
                Step.May(listOf(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed"))), Players.You),
                ifThen(Condition.ObjectMatches(Objects.Bound("revealed"), and(creature, manaValue(Comparison.AtLeast, 6))), transformSelf)),
        ),
        card(
            name = "Groth, Lord of the Fathoms",
            cost = null,
            type = "Legendary Creature — Kraken Horror",
            text = """
                Flying
                Whenever Groth attacks, create a tapped and attacking token that's a copy of another target attacking creature. If that creature is a Kraken, Leviathan, Octopus, or Serpent, create two of those tokens instead.
            """,
            pt = "3/5",
            colorIndicator = "UB",
        ) to abilities(
            Keyword.Flying,
            triggered(attacksSelf,
                ifThen(Condition.ObjectMatches(target("creature"), or(sub("Kraken"), sub("Leviathan"), sub("Octopus"), sub("Serpent"))),
                    copyToken(2),
                    otherwise = listOf(copyToken(1))),
                targets = listOf(slot("creature", and(another, attacking, creature)))),
        ),
    )

    private fun copyToken(count: Int) = doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), count = n(count), controller = Players.You,
        tapped = true, attacking = AttackTarget.Chosen))

    // CR 607.2a: the card exiled with its first ability; CR 400.7: played (cast or played as a land) from exile, it is still that card for the
    // trigger
    @Test
    fun voltaicVisionary() = cardFaces(Layout.Transform,
        card(
            name = "Sparkwild Seer",
            cost = "{1}{R}",
            type = "Creature — Human Wizard",
            text = """
                {T}: This creature deals 2 damage to you. Exile the top card of your library. You may play that card this turn. Activate only as a sorcery.
                When you play a card exiled with this creature, transform this creature.
            """,
            pt = "3/1",
        ) to abilities(
            activated(cost(tap),
                damage(2, Recipients(players = Players.You)),
                doing(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.EndOfTurn)),
                rules = asSorcery),
            triggered(Trigger.On(listOf(EventPattern.PlayLand(ObjectFilter.Is(exiledWith), PlayerFilter.You), EventPattern.Cast(ObjectFilter.Is(exiledWith), PlayerFilter.You))),
                transformSelf),
        ),
        card(
            name = "Spark-Charged Raider",
            cost = null,
            type = "Creature — Human Berserker",
            text = "This creature can't block.",
            pt = "4/3",
            colorIndicator = "R",
        ) to abilities(
            static(Mechanic.CantBlock(ObjectFilter.Any)),
        ),
    )

    // CR 604.3: a characteristic-defining ability; CR 701.27a: it transforms only with four or more creature cards in your graveyard
    @Test
    fun bloodswornSquire() = cardFaces(Layout.Transform,
        card(
            name = "Vowbound Squire",
            cost = "{3}{B}",
            type = "Creature — Vampire Soldier",
            text = """{1}{B}, Discard a card: This creature gains indestructible until end of turn. Tap it. Then if there are four or more creature cards in your graveyard, transform this creature. (Damage and effects that say "destroy" don't destroy it.)""",
            pt = "3/3",
        ) to abilities(
            activated(cost(mana("{1}{B}"), discardACard), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self),
                ifThen(Condition.Exists(inYourGraveyard(creature), atLeast = 4), transformSelf)),
        ),
        card(
            name = "Vowbound Knight",
            cost = null,
            type = "Creature — Vampire Knight",
            text = """
                Vowbound Knight's power and toughness are each equal to the number of creature cards in your graveyard.
                {1}{B}, Discard a card: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it.)
            """,
            pt = "*/*",
            colorIndicator = "B",
        ) to abilities(
            static(Mechanic.SetPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature))), characteristicDefining = true),
            activated(cost(mana("{1}{B}"), discardACard), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
        ),
    )

    // CR 603.10a: a leaves-the-battlefield trigger looks back
    @Test
    fun desperateFarmer() = cardFaces(Layout.Transform,
        card(
            name = "Frantic Herdsman",
            cost = "{2}{B}",
            type = "Creature — Human Peasant",
            text = """
                Lifelink
                When another creature you control dies, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.Lifelink,
            triggered(dies(and(another, creature, you)), transformSelf),
        ),
        card(
            name = "Ravaged Herdsman",
            cost = null,
            type = "Creature — Human Knight",
            text = "Lifelink",
            pt = "4/3",
            colorIndicator = "B",
        ) to abilities(Keyword.Lifelink),
    )

    // CR 605.1a: a mana ability even with the counter; CR 603.4: the end-step "if"; "untap it, then transform it"; CR 505.1: your first main
    // phase
    @Test
    fun forebodingStatue() = cardFaces(Layout.Transform,
        card(
            name = "Ominous Idol",
            cost = "{3}",
            type = "Artifact Creature — Construct",
            text = """
                {T}: Add one mana of any color. Put an omen counter on this creature.
                At the beginning of your end step, if there are three or more omen counters on this creature, untap it, then transform it.
            """,
            pt = "1/2",
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(arcanum.parser.cube.anyOneColor(1)), Players.You)), counters(Objects.Self, 1, CounterKind.Named("omen"))),
            triggered(yourEndStep, untapIt(Objects.Self), transformSelf,
                interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("omen"), atLeast = 3))),
        ),
        card(
            name = "Forsaken Grinder",
            cost = null,
            type = "Artifact Creature — Construct",
            text = "At the beginning of your first main phase, add one mana of any color.",
            pt = "5/5",
        ) to abilities(
            // CR 505.1a: the precombat main phase is the turn's first main phase
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
                doing(Effect.AddMana(ManaProduction(arcanum.parser.cube.anyOneColor(1)), Players.You))),
        ),
    )

    // CR 700.4: dies; CR 603.4: life gained this turn counted at the end step
    @Test
    fun panickedBystander() = cardFaces(Layout.Transform,
        card(
            name = "Frightened Onlooker",
            cost = "{1}{W}",
            type = "Creature — Human Peasant",
            text = """
                Whenever this creature or another creature you control dies, you gain 1 life.
                At the beginning of your end step, if you gained 3 or more life this turn, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, creature, you))), gainLife(1)),
            triggered(yourEndStep, transformSelf, interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 3, measure = Measure.Amount)),
        ),
        card(
            name = "Snickering Culprit",
            cost = null,
            type = "Creature — Human Rogue",
            text = """
                Whenever this creature or another creature you control dies, you gain 1 life.
                {1}{B}: This creature gains deathtouch until end of turn.
            """,
            pt = "3/5",
            colorIndicator = "B",
        ) to abilities(
            triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, creature, you))), gainLife(1)),
            activated(manaCost("{1}{B}"), gains(Objects.Self, Keyword.Deathtouch)),
        ),
    )

    // CR 701.27a; "sacrifice two Blood tokens" is a cost, "only as a sorcery" its timing (CR 602.5d)
    @Test
    fun restlessBloodseeker() = cardFaces(Layout.Transform,
        card(
            name = "Restless Bloodtaster",
            cost = "{1}{B}",
            type = "Creature — Vampire",
            text = """
                At the beginning of your end step, if you gained life this turn, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                Sacrifice two Blood tokens: Transform this creature. Activate only as a sorcery.
            """,
            pt = "1/3",
        ) to abilities(
            triggered(yourEndStep, create(blood), interveningIf = gainedLifeThisTurn),
            Ability.Activated(cost(CostPart.Sacrifice(CostObjects.Choose(and(sub("Blood"), token), n(2)))), Instructions(steps = listOf(transformSelf)), asSorcery),
        ),
        card(
            name = "Bloodsoaked Carouser",
            cost = null,
            type = "Creature — Vampire",
            text = """
                At the beginning of your end step, if you gained life this turn, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                {4}{B}: Each opponent loses 2 life and you gain 2 life.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            triggered(yourEndStep, create(blood), interveningIf = gainedLifeThisTurn),
            activated(manaCost("{4}{B}"), loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
        ),
    )

    // CR 614.1c: "enters with" counters; CR 603.4: "then if" checks after the counter is removed
    @Test
    fun soulcipherBoard() = cardFaces(Layout.Transform,
        card(
            name = "Omenwright Board",
            cost = "{1}{U}",
            type = "Artifact",
            text = """
                This artifact enters with three omen counters on it.
                {1}{U}, {T}: Look at the top two cards of your library. Put one of them into your graveyard.
                Whenever a creature card is put into your graveyard from anywhere, remove an omen counter from this artifact. Then if it has no omen counters on it, transform it.
            """,
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.Named("omen"), amount = n(3))))), affects = Affected.Objects(Objects.Self)),
            activated(cost(mana("{1}{U}"), tap),
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Graveyard)))),
            triggered(on(EventPattern.ZoneChange(and(creature, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard)),
                doing(Effect.RemoveCounters(Objects.Self, CounterKind.Named("omen"), n(1))),
                ifThen(Condition.ObjectMatches(Objects.Self, not(ObjectFilter.HasCounter(CounterKind.Named("omen"), atLeast = 1))), transformSelf)),
        ),
        card(
            name = "Omenbound Spirit",
            cost = null,
            type = "Creature — Spirit",
            text = """
                Flying
                This creature can block only creatures with flying.
                {3}{U}: Draw two cards, then discard a card.
            """,
            pt = "3/2",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Flying,
            static(Mechanic.CanBlockOnly(and(creature, kw(Keyword.Flying.kind)))),
            activated(manaCost("{3}{U}"), draw(2), discard(1)),
        ),
    )

    // CR 613.4c: the back face's +1/+1 for other creatures; CR 702.10: haste
    @Test
    fun ulvenwaldOddity() = cardFaces(Layout.Transform,
        card(
            name = "Thicket Oddity",
            cost = "{2}{G}{G}",
            type = "Creature — Beast",
            text = """
                Trample, haste
                {5}{G}{G}: Transform this creature.
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.Trample, Keyword.Haste,
            activated(manaCost("{5}{G}{G}"), transformSelf),
        ),
        card(
            name = "Thicket Behemoth",
            cost = null,
            type = "Creature — Beast Horror",
            text = """
                Trample, haste
                Other creatures you control get +1/+1 and have trample and haste.
            """,
            pt = "8/8",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Trample, Keyword.Haste,
            static(pt(1, 1), has(Keyword.Trample, Keyword.Haste), affects = Objects.Each(and(another, creature, you))),
        ),
    )

    // CR 111.2: the Blood token being created counts for "if you control five or more Blood tokens"
    @Test
    fun voldarenBloodcaster() = cardFaces(Layout.Transform,
        card(
            name = "Crimson Bloodcaster",
            cost = "{1}{B}",
            type = "Creature — Vampire Wizard",
            text = """
                Flying
                Whenever this creature or another nontoken creature you control dies, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                Whenever you create a Blood token, if you control five or more Blood tokens, transform this creature.
            """,
            pt = "2/1",
        ) to abilities(
            Keyword.Flying,
            triggered(dies(or(ObjectFilter.Is(Objects.Self), and(another, not(token), creature, you))), create(blood)),
            triggered(on(EventPattern.CreateToken(and(sub("Blood"), ObjectFilter.OwnedBy(PlayerFilter.You)))), transformSelf,
                interveningIf = Condition.Exists(and(sub("Blood"), token, you), atLeast = 5)),
        ),
        card(
            name = "Bloodbat Caller",
            cost = null,
            type = "Creature — Vampire Wizard",
            text = """
                Flying
                At the beginning of combat on your turn, up to one target Blood token you control becomes a 2/2 black Bat creature with flying and haste in addition to its other types.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            triggered(beginningOfCombatOnYourTurn, doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype("Bat")))),
                Mechanic.SetColors(listOf(arcanum.api.ColorRef.Literal(Color.Black))),
                Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.Haste)),
                Mechanic.SetPowerToughness(n(2), n(2))), Affected.Objects(target("token")), Duration.Indefinitely)),
                targets = listOf(slot("token", and(sub("Blood"), token, you), min = 0))),
        ),
    )

    // CR 603.4, 701.27a: the counter is put first, and "then if" counts it
    @Test
    fun weddingAnnouncement() = cardFaces(Layout.Transform,
        card(
            name = "Nuptial Announcement",
            cost = "{2}{W}",
            type = "Enchantment",
            text = "At the beginning of your end step, put an invitation counter on this enchantment. If you attacked with two or more creatures this turn, draw a card. Otherwise, create a 1/1 white Human creature token. Then if this enchantment has three or more invitation counters on it, transform it.",
        ) to abilities(
            triggered(yourEndStep, counters(Objects.Self, 1, CounterKind.Named("invitation")),
                ifThen(attackedWithTwo, draw(1), otherwise = listOf(create(creatureToken("Human", listOf(Color.White), 1, 1)))),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("invitation"), atLeast = 3)), transformSelf)),
        ),
        card(
            name = "Nuptial Festivity",
            cost = null,
            type = "Enchantment",
            text = "Creatures you control get +1/+1.",
            colorIndicator = "W",
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(and(creature, you))),
        ),
    )
}
