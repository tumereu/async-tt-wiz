package arcanum.parser.sets.mom

import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
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
import arcanum.api.PrintedNumber
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 13: putting and repeating, revealing, sacrificing, and triggers on attacking, becoming a target, casting and dealing damage. */
class MomChunk13PutRepeatRevealAndTriggersTest {
    private val yourHand = listOf(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun topOfLibrary(count: Int, bind: String) =
        doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(count), fromTop = true), Players.You, bind = bind))
    private val bottom = Destination(Zone.Library, libraryPosition = -1)

    // CR 701.23 (that player searches), 115.1 (up to one target)
    @Test
    fun rootkinsBoon() = card(
        name = "Rootkin's Boon",
        cost = "{3}{G}",
        type = "Instant",
        text = "Target player searches their library for a basic land card, puts it onto the battlefield tapped, then shuffles. Put two +1/+1 counters on up to one target artifact or creature.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(arcanum.api.Each.Players(Players.Target("player")), "player", listOf(
                doing(Effect.Search(Players.Bound("player"), basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.Bound("player"))),
                move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
                doing(Effect.Shuffle(Players.Bound("player")))), together = true),
            counters(target("permanent"), 2),
            targets = listOf(targetPlayer, slot("permanent", or(artifact, creature), min = 0)),
        ),
    )

    // CR 603.4 (intervening "if"), 700.4 (put into a graveyard from the battlefield)
    @Test
    fun seepwrackShade() = card(
        name = "Seepwrack Shade",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Shade",
        pt = "2/3",
        text = "At the beginning of your end step, if an artifact or creature was put into a graveyard from the battlefield this turn, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(yourEndStep, counters(Objects.Self),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(or(artifact, creature), from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 1, measure = Measure.Events)),
    )

    // CR 401.4 (the bottom of the library), 608.2c (in the order written)
    @Test
    fun instantOfClarity() = card(
        name = "Instant of Clarity",
        cost = "{1}{U}",
        type = "Instant",
        text = "Look at the top three cards of your library. Put one of those cards into your hand, one into your graveyard, and one on the bottom of your library.",
    ) parsesTo abilities(
        spell(
            topOfLibrary(3, "seen"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "toHand", chooser = Players.You),
            move(Objects.Bound("toHand"), Zone.Hand),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand"))),
                bind = "toGraveyard", chooser = Players.You),
            move(Objects.Bound("toGraveyard"), Zone.Graveyard),
            doing(Effect.Move(Objects.Except(Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand")), Objects.Bound("toGraveyard")), bottom)),
        ),
    )

    // CR 603.2 (the second card drawn this turn), 608.2c ("repeat this process once": the steps again if the condition holds then)
    @Test
    fun velkaAndOrrin() {
        val process = arrayOf(
            draw(1),
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(land) + yourHand), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
        )
        card(
            name = "Velka and Orrin",
            cost = "{B}{G}{U}",
            type = "Legendary Creature — Human Dryad",
            pt = "3/4",
            text = """
                Whenever you draw your second card each turn, target opponent loses 2 life and you gain 2 life.
                {T}, Sacrifice another creature: Draw a card. You may put a land card from your hand onto the battlefield tapped. If you control eight or more lands, repeat this process once.
            """,
        ) parsesTo abilities(
            triggered(drawSecondCard, loseLife(2, Players.Target("opponent")), gainLife(2), targets = listOf(targetOpponent)),
            activated(cost(tap, sacrificeA(and(another, creature))), *process, ifThen(youControl(land, 8), *process)),
        )
    }

    // CR 603.7 (a delayed trigger at end of combat), 603.7c (the object fixed when it's created), 400.7
    @Test
    fun brawlingRingtail() = card(
        name = "Brawling Ringtail",
        cost = "{3}{R}",
        type = "Creature — Raccoon Warrior",
        pt = "3/4",
        text = "Whenever this creature attacks, up to one target attacking creature gets +2/+0 and gains trample until end of turn. Return that creature to its owner's hand at end of combat. (Return it only if it's on the battlefield.)",
    ) parsesTo abilities(
        triggered(attacksSelf,
            pump(target("creature"), 2, 0, has(Keyword.Trample)),
            delayed(stepBegins(TurnStep.EndOfCombat, PlayerFilter.Any), move(target("creature"), Zone.Hand)),
            targets = listOf(slot("creature", and(attacking, creature), min = 0))),
    )

    // CR 107.4, 202.1 (coloured mana symbols, each counted once), 608.2c ("if you do": it was revealed), 106.1 (any combination of its colours)
    @Test
    fun ysmarHearthOfHues() {
        val top = Objects.Bound("top")
        val toHand = move(top, Zone.Hand)
        card(
            name = "Ysmar, Hearth of Hues",
            cost = "{W}{U}{B/P}{R}{G}",
            type = "Legendary Creature — Phyrexian Elemental",
            pt = "4/4",
            text = """
                If you would lose unspent mana, that mana becomes black instead.
                At the beginning of your first main phase, look at the top card of your library. You may reveal that card if it has three or more colored mana symbols in its mana cost. If you do, add three mana in any combination of its colors and put it into your hand. If you don't reveal it, put it into your hand.
            """,
        ) parsesTo abilities(
            playerStatic(Mechanic.UnspentManaBecomes(Color.Black)),
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
                topOfLibrary(1, "top"),
                ifThen(Condition.Compare(Value.Of(NumericProperty.ColoredManaSymbols, top, Combine.Single), Comparison.AtLeast, n(3)),
                    Step.Pay(steps = listOf(doing(Effect.Reveal(top))), payer = Players.You, bind = "revealed")),
                ifThen(Condition.Did("revealed"),
                    doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(3), listOf(ColorRef.Of(top)), combination = true)), Players.You)), toHand,
                    otherwise = listOf(toHand))),
        )
    }

    // CR 701.20a (reveal), 712.1 (a double-faced card), 401.4 (a random order)
    @Test
    fun thicketbornPest() {
        val seen = Objects.Bound("seen")
        val chosen = Objects.Bound("chosen")
        card(
            name = "Thicketborn Pest",
            cost = "{2}{G}",
            type = "Creature — Pest",
            pt = "2/2",
            text = "When this creature enters, look at the top five cards of your library. You may reveal a land or double-faced card from among them and put that card into your hand. Put the rest on the bottom of your library in a random order.",
        ) parsesTo abilities(
            triggered(entersSelf,
                topOfLibrary(5, "seen"),
                Step.Choose(Choice.Objects(or(land, ObjectFilter.DoubleFaced), n(0), n(1), from = seen), bind = "chosen", chooser = Players.You),
                doing(Effect.Reveal(chosen)),
                move(chosen, Zone.Hand),
                doing(Effect.Move(Objects.Except(seen, chosen), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        )
    }

    // CR 305.2 (an additional land), 614.1c (enters tapped), 701.21a (sacrifice), 608.2c ("then")
    @Test
    fun mirelaAndTheBogColossus() = card(
        name = "Mirela and The Bog Colossus",
        cost = "{1}{W}{B}{G}",
        type = "Legendary Creature — Human Frog Horror",
        pt = "4/4",
        text = """
            First strike, deathtouch
            You may play an additional land on each of your turns.
            Creatures and nonbasic lands your opponents control enter tapped.
            Whenever Mirela and The Bog Colossus attacks, sacrifice a creature or land, then draw a card.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Deathtouch,
        playerStatic(Mechanic.ExtraLandPlays(1)),
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(or(creature, and(not(ObjectFilter.Super(Supertype.Basic)), land)), opponents), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped))),
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(or(creature, land), you), min = n(1), max = n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            draw(1)),
    )

    // CR 506.2, 508.1b (an attack on a battle)
    @Test
    fun lashingVanguard() = card(
        name = "Lashing Vanguard",
        cost = "{1}{R}",
        type = "Creature — Phyrexian Lizard",
        pt = "2/2",
        text = """
            Trample
            Whenever this creature attacks a battle, it gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.Self), target = RecipientFilter(objects = battle))), pump(Objects.Self, 1, 1)),
    )

    // CR 702.165a (backup's triggered ability), 707.10 (copying an ability), 603.2i (only once each turn)
    @Test
    fun glassguardPhalanx() = card(
        name = "Glassguard Phalanx",
        cost = "{R}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Vigilance
            Whenever a creature you control becomes the target of a backup ability, copy that ability. You may choose new targets for the copy. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.BecomesTarget(creatureYouControl, by = ObjectFilter.AbilityOf(keyword = Keyword.Backup))),
            doing(Effect.CopySpell(Objects.EventObject(EventRole.Source), Players.You, newTargets = true)),
            perTurn = 1),
    )

    // CR 603.2 (the first such spell this turn), 608.2g (cast while it resolves), 608.2h (the spell as it last existed), 608.2c ("If you don't")
    @Test
    fun torvinAndAshkaRel() {
        val it = Objects.EventObject(EventRole.Object)
        card(
            name = "Torvin and Ashka Rel",
            cost = "{1}{U}{R}",
            type = "Legendary Creature — Human",
            pt = "2/4",
            text = """
                First strike, menace
                Whenever you cast your first instant or sorcery spell each turn, you may cast a spell with lesser mana value that shares a card type with it from your hand without paying its mana cost. If you don't, create Deckhand Mirko, a legendary 2/1 red Monkey Pirate creature token. It gains haste until end of turn.
            """,
        ) parsesTo abilities(
            Keyword.FirstStrike,
            Keyword.Menace,
            triggered(Trigger.On(listOf(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You)), nthThisTurn = 1),
                Step.Choose(Choice.Objects(and(ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue, it, Combine.Single)),
                    ObjectFilter.Shares(SharedQuality.CardType, it), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                    bind = "card", chooser = Players.You),
                doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You)),
                ifThen(Condition.Not(Condition.NonEmpty("spell")),
                    create(TokenSpec.Defined(FaceDefinition("Deckhand Mirko", colorIndicator = listOf(Color.Red), supertypes = listOf(Supertype.Legendary),
                        types = listOf(CardType.Creature), subtypes = listOf(Subtype("Monkey"), Subtype("Pirate")), power = PrintedNumber.Fixed(2),
                        toughness = PrintedNumber.Fixed(1))), bind = "token"),
                    untilEndOfTurn(Objects.Bound("token"), has(Keyword.Haste)))),
        )
    }

    // CR 702.51 (a spell that has convoke), 310.8 (the battles they protect)
    @Test
    fun gleefulSquallshaper() = card(
        name = "Gleeful Squallshaper",
        cost = "{3}{U}{R}",
        type = "Creature — Human Shaman",
        pt = "2/3",
        text = """
            When this creature enters, create two 1/1 blue and red Elemental creature tokens.
            Whenever you cast a spell that has convoke, this creature deals 1 damage to each opponent and each battle they protect.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Elemental", listOf(Color.Blue, Color.Red), 1, 1), n(2))),
        triggered(youCast(and(spellFilter, kw(Keyword.Convoke))),
            damage(1, Recipients(players = Players.Each(PlayerFilter.Opponent), objects = Objects.Each(and(battle,
                ObjectFilter.Where(Condition.PlayerMatches(Players.ProtectorOf(Objects.Tested), PlayerFilter.Opponent))))))),
    )

    // CR 120.1 (a spell deals damage), 310.6 (to a battle), 603.6 (a trigger that works in the graveyard)
    @Test
    fun cinderplumePhoenix() = card(
        name = "Cinderplume Phoenix",
        cost = "{1}{R}",
        type = "Creature — Phoenix",
        pt = "2/2",
        text = """
            Flying
            This creature can't block.
            Whenever an instant or sorcery spell you control deals damage to an opponent or battle, you may pay {R}. If you do, return this card from your graveyard to the battlefield. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CantBlock(attackers = ObjectFilter.Any)),
        triggered(on(EventPattern.Damage(and(or(instant, sorcery), spellFilter, you), RecipientFilter(objects = battle, players = PlayerFilter.Opponent))),
            Step.Pay(manaCost("{R}"), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), move(Objects.Self, Zone.Battlefield, postBind = "returned")),
            // nothing returned when not paid: the haste has no object then
            untilEndOfTurn(Objects.Bound("returned"), has(Keyword.Haste)),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 603.2c (one or more: once per player or battle dealt damage), 401.4, 400.7 (those Dragons still on the battlefield)
    @Test
    fun groshAndValkiran() = card(
        name = "Grosh and Valkiran",
        cost = "{2}{U}{R}{W}",
        type = "Legendary Creature — Orc Dragon",
        pt = "4/4",
        text = """
            Flying, haste
            Grosh and Valkiran has hexproof as long as it entered this turn.
            Whenever one or more Dragons you control deal combat damage to a player or battle, look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. You may return one of those Dragons to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        static(has(Keyword.Hexproof), condition = Condition.ObjectMatches(Objects.Self,
            ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield, objects = ObjectFilter.Is(Objects.Self))))),
        triggered(Trigger.On(listOf(EventPattern.Damage(and(sub("Dragon"), you), RecipientFilter(objects = battle, players = PlayerFilter.Any), combat = true)),
            oneOrMore = true, batchPerPlayer = true),
            topOfLibrary(3, "seen"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), bottom)),
            Step.Choose(Choice.Objects(and(sub("Dragon"), inZone(Zone.Battlefield)), n(0), n(1), from = Objects.EventObject(EventRole.Source)),
                bind = "returned", chooser = Players.You),
            move(Objects.Bound("returned"), Zone.Hand)),
    )
}
