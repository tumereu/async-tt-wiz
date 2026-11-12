package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 01: transforming double-faced cards and Sieges, part 1 (CR 701.27, 712, 310). */
class MomChunk01TransformTest {
    private fun siege(name: String, cost: String, text: String, defense: String) =
        OracleCard(name, cost, "Battle — Siege", siegeReminder + "\n" + text.trimIndent(), defense = defense)

    // CR 702.2, 701.27a; CR 510.2, 310.6: combat damage to a battle counts too
    @Test
    fun veilbladeCourier() = cardFaces(Layout.Transform,
        card(
            name = "Veilblade Courier",
            cost = "{1}{B}",
            type = "Creature — Human Rogue",
            text = """
                Deathtouch
                {4}{U/P}: Transform this creature. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
            """,
            pt = "1/1",
        ) to abilities(
            Keyword.Deathtouch,
            transformAsSorcery("{4}{U/P}"),
        ),
        card(
            name = "Glassmind Stinger",
            cost = null,
            type = "Creature — Phyrexian Rogue",
            text = """
                Deathtouch
                Whenever this creature deals combat damage to a player or battle, draw a card.
            """,
            pt = "3/3",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Deathtouch,
            triggered(combatDamageToAPlayerOrBattle, draw(1)),
        ),
    )

    // CR 510.2, 310.6; CR 701.27a: an "up to one" target may be none
    @Test
    fun watchfulCloudwarden() = card(
        name = "Watchful Cloudwarden",
        cost = "{2}{W}",
        type = "Creature — Phyrexian Kor",
        text = """
            Flying
            Whenever this creature deals combat damage to a player or battle, transform up to one target Incubator token you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayerOrBattle, doing(Effect.Transform(target("token"))),
            targets = listOf(slot("token", and(sub("Incubator"), token, you), min = 0))),
    )

    // CR 608.2h: the sacrificed permanent's mana value as it last existed; CR 310.6, 115.4: an opponent or a battle;
    // CR 611.2a: "It gains haste." has no duration, it lasts while that permanent stays (CR 400.7)
    @Test
    fun vessaWidowOfAsh() = cardFaces(Layout.Transform,
        card(
            name = "Vessa, Widow of Ash",
            cost = "{1}{B}{B}",
            type = "Legendary Creature — Elf Noble",
            text = """
                {T}, Sacrifice another creature or artifact: Vessa deals X damage to target opponent or battle and you gain X life, where X is the sacrificed permanent's mana value.
                {5}{R/P}: Transform Vessa. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)
            """,
            pt = "3/3",
        ) to abilities(
            activated(
                cost(tap, CostPart.Sacrifice(CostObjects.Choose(and(another, or(creature, artifact)), n(1)), preBind = "sacrificed")),
                damage(Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("sacrificed"), arcanum.api.Combine.Single),
                    Recipients(target = "opponent or battle")),
                Step.Do(Effect.GainLife(Players.You, Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("sacrificed"), arcanum.api.Combine.Single))),
                targets = listOf(TargetSlot("opponent or battle", TargetKind.Either(battle, PlayerFilter.Opponent), min = n(1), max = n(1))),
            ),
            transformAsSorcery("{5}{R/P}"),
        ),
        card(
            name = "Vessa, Kiln Queen",
            cost = null,
            type = "Legendary Creature — Phyrexian Elf Noble",
            text = "At the beginning of combat on your turn, return up to one target artifact or creature card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step.",
            pt = "4/4",
            colorIndicator = "R",
        ) to abilities(
            triggered(beginningOfCombatOnYourTurn,
                move(target("card"), Zone.Battlefield, postBind = "returned"),
                doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
                delayed(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Bound("returned"), Zone.Exile)),
                targets = listOf(slot("card", inYourGraveyard(or(artifact, creature)), min = 0))),
        ),
    )

    // CR 603.2: one ability that triggers on either event; CR 603.6c, 603.10a: dies looks back
    @Test
    fun rotreaperSporeling() = cardFaces(Layout.Transform,
        card(
            name = "Rotreaper Sporeling",
            cost = "{1}{B}",
            type = "Creature — Fungus",
            text = "{3}{G/P}: Transform this creature. Activate only as a sorcery. ({G/P} can be paid with either {G} or 2 life.)",
            pt = "2/2",
        ) to abilities(
            transformAsSorcery("{3}{G/P}"),
        ),
        card(
            name = "Rotsower Sporeling",
            cost = null,
            type = "Creature — Phyrexian Fungus",
            text = "When this creature transforms into Rotsower Sporeling or dies, create a 1/1 green Phyrexian Saproling creature token.",
            pt = "3/3",
            colorIndicator = "G",
        ) to abilities(
            Ability.Triggered(
                arcanum.api.Trigger.On(listOf(
                    EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Rotsower Sporeling"))),
                    EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
                )),
                Instructions(steps = listOf(create(creatureToken("Phyrexian Saproling", listOf(Color.Green), 1, 1, name = "Phyrexian Saproling Token")))),
            ),
        ),
    )

    // CR 101.4: each library in turn; CR 608.2g: the spells are cast while the ability resolves; CR 120.3, 122.1f: poison equal to the damage
    @Test
    fun okkarPrimalTyrant() = cardFaces(Layout.Transform,
        card(
            name = "Okkar, Primal Tyrant",
            cost = "{5}{R}{R}",
            type = "Legendary Creature — Elder Dinosaur",
            text = """
                Trample
                When Okkar enters, each player exiles cards from the top of their library until they exile a nonland card. You may cast any number of spells from among the nonland cards exiled this way without paying their mana costs.
                {9}{G/P}: Transform Okkar. Activate only as a sorcery.
            """,
            pt = "7/7",
        ) to abilities(
            Keyword.Trample,
            triggered(entersSelf,
                doing(Effect.RevealUntil(nonland, Players.Each(PlayerFilter.Any), n(1), reveal = true, into = Zone.Exile, foundBind = "found", restBind = "rest")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), Value.Count(Objects.Bound("found")), from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
                Step.ForEach(Each.Objects(Objects.Bound("chosen")), bind = "card", steps = listOf(
                    doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))),
            ),
            transformAsSorcery("{9}{G/P}"),
        ),
        card(
            name = "Okkar, Primal Blight",
            cost = null,
            type = "Legendary Creature — Phyrexian Elder Dinosaur",
            text = """
                Trample, indestructible
                Whenever Okkar deals combat damage to a player, they get that many poison counters. (A player with ten or more poison counters loses the game.)
            """,
            pt = "11/11",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Trample,
            Keyword.Indestructible,
            triggered(combatDamageToAPlayer, doing(Effect.GivePlayerCounters(Players.EventPlayer, CounterKind.Poison, Value.EventAmount))),
        ),
    )

    // CR 613.4b: base power and toughness (layer 7b); CR 115.1: "other" excludes this creature
    @Test
    fun fjordmossHermit() = cardFaces(Layout.Transform,
        card(
            name = "Fjordmoss Hermit",
            cost = "{3}{G}",
            type = "Creature — Troll",
            text = "{5}{U/P}: Transform this creature. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)",
            pt = "4/4",
        ) to abilities(
            transformAsSorcery("{5}{U/P}"),
        ),
        card(
            name = "Chromeback Hulk",
            cost = null,
            type = "Creature — Phyrexian Troll",
            text = "Whenever this creature attacks, up to one other target creature has base power and toughness 5/5 until end of turn.",
            pt = "5/5",
            colorIndicator = "U",
        ) to abilities(
            triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(5), n(5))),
                targets = listOf(slot("creature", and(another, creature), min = 0))),
        ),
    )

    // CR 205.3 (God is a creature type); CR 601.2f: the reduction counts each card drawn (CR 121.2) by every opponent this turn
    @Test
    fun solmaraTheBrightDawn() = cardFaces(Layout.Transform,
        card(
            name = "Solmara, the Bright Dawn",
            cost = "{2}{W}{W}",
            type = "Legendary Enchantment Creature — God",
            text = """
                When Solmara enters, return target enchantment card that isn't a God from your graveyard to your hand.
                {3}{U/P}: Transform Solmara. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
            """,
            pt = "4/4",
        ) to abilities(
            triggered(entersSelf, move(target("card"), Zone.Hand),
                targets = listOf(slot("card", and(enchantment, not(sub("God")), inYourGraveyard())))),
            transformAsSorcery("{3}{U/P}"),
        ),
        card(
            name = "Solmara, the Bent Eclipse",
            cost = null,
            type = "Legendary Enchantment Creature — Phyrexian God",
            text = """
                You may cast spells as though they had flash.
                Spells you cast cost {1} less to cast for each card your opponents have drawn this turn.
            """,
            pt = "4/6",
            colorIndicator = "U",
        ) to abilities(
            playerStatic(Mechanic.CastAsThoughFlash(spellFilter)),
            static(Mechanic.CostChange(and(spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!,
                Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.Opponent), Measure.Events)))),
        ),
    )

    // CR 700.4: any permanent put into a graveyard from the battlefield; the condition is checked as the ability resolves (CR 608.2c)
    @Test
    fun invasionOfCindral() = cardFaces(Layout.Transform,
        siege("Invasion of Cindral", "{B}{R}", "When this Siege enters, target player sacrifices a creature or planeswalker of their choice and loses 1 life.", "4") to abilities(
            triggered(entersSelf,
                Step.Choose(Choice.Objects(and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                    bind = "sacrificed", chooser = Players.Target("player")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                loseLife(1, Players.Target("player")),
                targets = listOf(targetPlayer)),
        ),
        card(
            name = "Cinder Reaper",
            cost = null,
            type = "Creature — Zombie Elemental",
            text = """
                Menace
                At the beginning of your end step, put a +1/+1 counter on this creature if a permanent was put into a graveyard from the battlefield this turn.
            """,
            pt = "2/1",
            colorIndicator = "BR",
        ) to abilities(
            Keyword.Menace,
            triggered(yourEndStep, ifThen(Condition.HappenedThisTurn(EventPattern.ZoneChange(permanentFilter, from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 1, measure = Measure.Events), counters(Objects.Self))),
        ),
    )

    // CR 603.4: an intervening "if" checked as it triggers and as it resolves; "since your last turn": every turn after your last one (CR 500.1)
    @Test
    fun invasionOfVelloro() = cardFaces(Layout.Transform,
        siege("Invasion of Velloro", "{4}{B}{B}", """
            When this Siege enters, choose one or both —
            • Destroy all legendary creatures.
            • Destroy all nonlegendary creatures.
        """, "4") to abilities(
            Ability.Triggered(entersSelf, Instructions(modes = modes(
                mode(destroy(Objects.Each(and(legendary, creature)))),
                mode(destroy(Objects.Each(and(not(legendary), creature)))),
                min = 1, max = 2))),
        ),
        card(
            name = "Ilsabet, Steadfast Monarch",
            cost = null,
            type = "Legendary Creature — Human Noble",
            text = """
                Menace, deathtouch
                Whenever Ilsabet attacks, remove all counters from up to one target permanent.
                At the beginning of your upkeep, if you haven't been dealt combat damage since your last turn, you draw a card and you lose 1 life.
            """,
            pt = "3/6",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Menace,
            Keyword.Deathtouch,
            triggered(attacksSelf, doing(Effect.RemoveCounters(target("permanent"), null,
                Value.Of(arcanum.api.NumericProperty.Counters(null), target("permanent"), arcanum.api.Combine.Single))),
                targets = listOf(slot("permanent", permanentFilter, min = 0))),
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), draw(1), loseLife(1),
                interveningIf = Condition.Not(Condition.HappenedSinceYourLastTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You),
                    combat = true), atLeast = 1, measure = Measure.Events))),
        ),
    )

    // CR 400.7: the exiled card is a new object its owner may play for as long as it stays exiled; CR 601.2f: {2} more
    @Test
    fun invasionOfSandmere() = cardFaces(Layout.Transform,
        siege("Invasion of Sandmere", "{1}{W}",
            "When this Siege enters, look at target opponent's hand. You may exile a nonland card from it. For as long as that card remains exiled, its owner may play it. A spell cast this way costs {2} more to cast.",
            "3") to abilities(
            triggered(entersSelf,
                doing(Effect.LookAt(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), players = Players.You)),
                Step.Choose(Choice.Objects(and(nonland, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(0), n(1)),
                    bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
                Step.ForEach(Each.Objects(Objects.Bound("exiled")), bind = "card", steps = listOf(
                    doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play,
                        holder = PlayerFilter.Is(Players.OwnerOf(Objects.Bound("card"))), costChange = CostChange.More(ManaCost.parse("{2}")!!, times = n(1)))),
                        Affected.Objects(Objects.Bound("card")), Duration.Indefinitely)))),
                targets = listOf(targetOpponent)),
        ),
        card(
            name = "Glintshield Array",
            cost = null,
            type = "Enchantment",
            text = """
                At the beginning of your end step, put a +1/+1 counter on each creature that attacked this turn.
                Sacrifice this enchantment: Creatures you control gain hexproof and indestructible until end of turn.
            """,
            colorIndicator = "W",
        ) to abilities(
            triggered(yourEndStep, counters(Objects.Each(and(creature, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any)))), 1)),
            activated(cost(sacrificeSelf), gains(Objects.Each(creatureYouControl), Keyword.Hexproof, Keyword.Indestructible)),
        ),
    )

    // CR 510.1c: each such creature's controller may have it assign its damage as though it weren't blocked
    @Test
    fun invasionOfVarrok() = cardFaces(Layout.Transform,
        siege("Invasion of Varrok", "{X}{G}{G}",
            "When this Siege enters, search your library and/or graveyard for a non-Human creature card with mana value X or less and put it onto the battlefield. If you search your library this way, shuffle.",
            "6") to abilities(
            triggered(entersSelf,
                search(and(not(sub("Human")), creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.CastX(Objects.Self))), zones = listOf(Zone.Library, Zone.Graveyard)),
                move(Objects.Bound("found"), Zone.Battlefield),
                ifThen(Condition.Searched("found", Zone.Library), shuffle)),
        ),
        card(
            name = "Grothmaw, Apex of Varrok",
            cost = null,
            type = "Legendary Creature — Dinosaur",
            text = """
                Reach
                For each non-Human creature you control, you may have that creature assign its combat damage as though it weren't blocked.
            """,
            pt = "8/8",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Reach,
            static(Mechanic.AssignsDamageAsThoughUnblocked, affects = Objects.Each(and(not(sub("Human")), creature, you))),
        ),
    )

    // CR 400.7: the exiled cards may be played until the end of your next turn
    @Test
    fun invasionOfSkjoldheim() = cardFaces(Layout.Transform,
        siege("Invasion of Skjoldheim", "{3}{R}",
            "When this Siege enters, exile all cards from your hand, then draw that many cards. Until the end of your next turn, you may play cards exiled this way.",
            "4") to abilities(
            triggered(entersSelf,
                move(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Bound("exiled")))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
        ),
        card(
            name = "Pyre of the Rime Tree",
            cost = null,
            type = "Enchantment",
            text = """
                Discard a land card: This enchantment deals 2 damage to any target.
                Whenever you discard a land card, exile the top card of your library. You may play that card this turn.
            """,
            colorIndicator = "R",
        ) to abilities(
            activated(cost(CostPart.Discard(CostObjects.Choose(land, n(1)))), damage(2, toAny), targets = listOf(anyTarget)),
            triggered(on(EventPattern.Discard(land, PlayerFilter.You)),
                move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
        ),
    )

    // CR 122.1d: a stun counter; CR 510.2, 310.6
    @Test
    fun invasionOfTessuri() = cardFaces(Layout.Transform,
        siege("Invasion of Tessuri", "{3}{U}",
            "When this Siege enters, tap target artifact or creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
            "4") to abilities(
            triggered(entersSelf, tapIt(target("permanent")), counters(target("permanent"), 1, CounterKind.Stun),
                targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
        ),
        card(
            name = "Eaveline Saboteurs",
            cost = null,
            type = "Creature — Moonfolk Ninja",
            text = """
                Flying
                Whenever this creature deals combat damage to a player or battle, draw a card.
            """,
            pt = "2/3",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Flying,
            triggered(combatDamageToAPlayerOrBattle, draw(1)),
        ),
    )

    // CR 508.1a: the token and another creature token declared as attackers together
    @Test
    fun invasionOfBrannor() = cardFaces(Layout.Transform,
        siege("Invasion of Brannor", "{2}{R}{W}",
            "When this Siege enters, up to two target creatures each get +2/+0 and gain vigilance and haste until end of turn.", "5") to abilities(
            triggered(entersSelf, pump(target("creatures"), 2, 0, has(Keyword.Vigilance, Keyword.Haste)),
                targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        ),
        card(
            name = "Brannor Tag Team",
            cost = null,
            type = "Sorcery",
            text = "Create two 3/2 red and white Warrior creature tokens with \"Whenever this token and at least one other creature token attack, put a +1/+1 counter on this token.\"",
            colorIndicator = "RW",
        ) to abilities(
            spell(create(creatureToken("Warrior", listOf(Color.Red, Color.White), 3, 2, abilities = listOf(
                triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self), withOthers = 1, others = and(creature, token))), counters(Objects.Self)),
            )), count = n(2))),
        ),
    )

    // CR 603.12: "When you do" is a reflexive trigger; X counts the creatures tapped (CR 107.3c); CR 701.24a: shuffled into the library
    @Test
    fun invasionOfGlimmerhold() = cardFaces(Layout.Transform,
        siege("Invasion of Glimmerhold", "{X}{W}{U}", "When this Siege enters, create X 2/2 white and blue Knight creature tokens with vigilance.", "6") to abilities(
            triggered(entersSelf, create(creatureToken("Knight", listOf(Color.White, Color.Blue), 2, 2, abilities = listOf(Keyword.Vigilance)),
                count = Value.CastX(Objects.Self))),
        ),
        card(
            name = "Aldric Vanth of Glimmerhold",
            cost = null,
            type = "Legendary Planeswalker — Teferi",
            text = """
                [+1]: Draw two cards. Then discard two cards unless you discard a creature card.
                [−2]: You get an emblem with "Knights you control get +1/+0 and have ward {1}."
                [−3]: Tap any number of untapped creatures you control. When you do, shuffle target nonland permanent an opponent controls with mana value X or less into its owner's library, where X is the number of creatures tapped this way.
            """,
            loyalty = "4",
            colorIndicator = "WU",
        ) to abilities(
            loyalty(1, draw(2),
                Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(creature, n(1))))), payer = Players.You, mandatory = false, bind = "paid"),
                ifThen(Condition.Not(Condition.Did("paid")), discard(2))),
            loyalty(-2, doing(Effect.CreateEmblem(listOf(static(pt(1, 0), has(Keyword.Ward(manaCost("{1}"))), affects = Objects.Each(and(sub("Knight"), you)))),
                Players.You))),
            loyalty(-3,
                Step.Choose(Choice.Objects(and(not(tapped), creature, you), min = n(0), max = null), bind = "tapped", chooser = Players.You),
                tapIt(Objects.Bound("tapped")),
                reflexive(move(target("permanent"), Zone.Library), Step.Do(Effect.Shuffle(Players.OwnerOf(target("permanent")))),
                    targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents,
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.Count(Objects.Bound("tapped"))))))),
            ),
        ),
    )

    // CR 105.2: exactly two of the five colours
    @Test
    fun invasionOfConcordia() = cardFaces(Layout.Transform,
        siege("Invasion of Concordia", "{5}", "When this Siege enters, exile target nonland permanent an opponent controls that isn't exactly two colors.", "4") to abilities(
            triggered(entersSelf, move(target("permanent"), Zone.Exile),
                targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents, not(ObjectFilter.ColorCount(2)))))),
        ),
        card(
            name = "Accord Paragon",
            cost = null,
            type = "Artifact Creature — Construct",
            text = "Whenever you cast a spell that's exactly two colors, look at the top six cards of your library. You may reveal a card that's exactly two colors from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
            pt = "5/5",
        ) to abilities(
            triggered(youCast(and(spellFilter, ObjectFilter.ColorCount(2))),
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), players = Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(and(cardFilter, ObjectFilter.ColorCount(2)), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                doing(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        ),
    )

    // CR 400.11: cards outside the game (a sideboard) can be searched for; CR 701.23: shuffle only after searching the library
    @Test
    fun invasionOfMirrowyn() = cardFaces(Layout.Transform,
        siege("Invasion of Mirrowyn", "{3}{U}{U}",
            "When this Siege enters, search your library, graveyard, and/or outside the game for an instant or sorcery card you own, reveal it, and put it into your hand. If you search your library this way, shuffle.",
            "7") to abilities(
            triggered(entersSelf,
                search(and(or(instant, sorcery), ObjectFilter.OwnedBy(PlayerFilter.You)), reveal = true, zones = listOf(Zone.Library, Zone.Graveyard, Zone.Outside)),
                move(Objects.Bound("found"), Zone.Hand),
                ifThen(Condition.Searched("found", Zone.Library), shuffle)),
        ),
        card(
            name = "Invocation of the Lorekeepers",
            cost = null,
            type = "Enchantment",
            text = "Whenever you cast an instant or sorcery spell from your hand, you may copy that spell. You may choose new targets for the copy.",
            colorIndicator = "U",
        ) to abilities(
            triggered(on(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You, from = Zone.Hand)),
                Step.May(listOf(doing(Effect.CopySpell(Objects.EventObject(arcanum.api.EventRole.Object), controller = Players.You, newTargets = true))),
                    player = Players.You)),
        ),
    )
}
