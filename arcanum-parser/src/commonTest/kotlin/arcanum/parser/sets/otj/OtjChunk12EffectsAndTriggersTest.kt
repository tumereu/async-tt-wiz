package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 12: "… instead if …" after a step, outlaw lords and triggers, an outlaw spell copied, a crime-gated search, and more.
 */
class OtjChunk12EffectsAndTriggersTest {
    private val crimeThisTurn = Condition.HappenedThisTurn(EventPattern.CommitCrime(PlayerFilter.You), atLeast = 1, measure = Measure.Events)
    private val controlMount = Condition.Exists(and(sub("Mount"), you), atLeast = 1)

    private fun createMercenaries(count: Int): Step = create(creatureToken("Mercenary", listOf(Color.Red), 1, 1, abilities = listOf(
        activated(cost(tap), pump(target("creature"), 1, 0), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery))), n(count))

    // CR 700.12: outlaws; CR 613.1f: a static ability granting haste to other outlaws you control
    @Test
    fun cinderspurGangBoss() = card(
        name = "Cinderspur Gang Boss",
        cost = "{2}{R}{R}",
        type = "Creature — Lizard Rogue",
        pt = "2/4",
        text = """
            Other outlaws you control have haste. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
            When this creature enters, create two 1/1 red Mercenary creature tokens with "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery."
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(and(another, outlaw, you))),
        triggered(entersSelf, createMercenaries(2)),
    )

    // CR 700.12: outlaws; CR 603.2: "another outlaw you control" excludes this creature
    @Test
    fun flaskBreakerMerryBombardier() = card(
        name = "Flask Breaker, Merry Bombardier",
        cost = "{B}{R}",
        type = "Legendary Creature — Goblin Mercenary",
        pt = "3/2",
        text = "Whenever another outlaw you control enters, Flask Breaker deals 1 damage to target opponent. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)",
    ) parsesTo abilities(
        triggered(enters(and(another, outlaw, you)), damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
    )

    // CR 700.12: an outlaw spell; CR 707.10: the copy of a permanent spell becomes a token (CR 111.1)
    @Test
    fun doubledWager() = card(
        name = "Doubled Wager",
        cost = "{3}{U}",
        type = "Enchantment",
        text = "Whenever you cast an outlaw spell, copy that spell. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws. Copies of permanent spells become tokens.)",
    ) parsesTo abilities(
        triggered(youCast(outlaw), doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = false))),
    )

    // CR 603.4: an intervening "if" clause; CR 700.13: a crime; CR 701.23: search
    @Test
    fun acolyteOfTheBarb() = card(
        name = "Acolyte of the Barb",
        cost = "{1}{B}",
        type = "Creature — Human Warlock",
        pt = "1/3",
        text = """
            Deathtouch
            Whenever this creature deals combat damage to a player, if you've committed a crime this turn, you may sacrifice this creature. If you do, search your library for a card, put it into your hand, then shuffle. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(combatDamageToAPlayer,
            Step.Pay(cost(sacrificeSelf), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
            interveningIf = crimeThisTurn),
    )

    // CR 608.2c: whether you control an outlaw is checked as the spell resolves; CR 700.12
    @Test
    fun shoulderTheBlame() = card(
        name = "Shoulder the Blame",
        cost = "{U}",
        type = "Instant",
        text = """
            Target creature gets -1/-0 until end of turn. It gets -4/-0 until end of turn instead if you control an outlaw. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            ifThen(youControl(outlaw, 1), pump(target("creature"), -4, 0), otherwise = listOf(pump(target("creature"), -1, 0))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 608.2c: "it's a Mount" is checked as the spell resolves; CR 122.1a: a +1/+1 counter instead of the pump
    @Test
    fun hurlFromTheStirrups() = card(
        name = "Hurl from the Stirrups",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Target creature you control gets +1/+1 until end of turn. Put a +1/+1 counter on it instead if it's a Mount. Then it deals damage equal to its power to target creature you don't control.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.ObjectMatches(target("creature"), sub("Mount")), counters(target("creature")),
                otherwise = listOf(pump(target("creature"), 1, 1))),
            damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl),
                slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 608.2c: whether you control a Mount is checked as the ability resolves; the card goes to one zone or the other
    @Test
    fun wardenOfTheMists() = card(
        name = "Warden of the Mists",
        cost = "{4}{W}",
        type = "Creature — Pegasus",
        pt = "4/3",
        text = """
            Flying, vigilance
            When this creature enters, return target permanent card with mana value 3 or less from your graveyard to your hand. Return that card to the battlefield instead if you control a Mount.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(entersSelf,
            ifThen(controlMount, move(target("card"), Zone.Battlefield), otherwise = listOf(move(target("card"), Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(permanentCard, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 608.2c: the Deserts counted as the spell resolves; CR 613.4c: both changes apply in layer 7c
    @Test
    fun duneReckoning() = card(
        name = "Dune Reckoning",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -2/-2 until end of turn. It gets an additional -1/-1 until end of turn for each Desert you control.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), -2, -2),
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Times(count(and(sub("Desert"), you)), -1),
                Value.Times(count(and(sub("Desert"), you)), -1))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.16a: protection from a creature type; CR 509.1b: a blocking restriction until end of turn
    @Test
    fun featherfootCourier() = card(
        name = "Featherfoot Courier",
        cost = "{1}{R}",
        type = "Creature — Bird",
        pt = "2/2",
        text = """
            Haste, protection from Coyotes
            {3}: This creature can't be blocked this turn except by creatures with haste.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Keyword.Protection(sub("Coyote")),
        activated(manaCost("{3}"), untilEndOfTurn(Objects.Self, Mechanic.CanBeBlockedOnlyBy(ObjectFilter.HasKeyword(Keyword.Haste.kind)))),
    )

    // CR 611.2c: the creatures that player controls as it resolves lose the keywords; CR 613.1f
    @Test
    fun duelAtNoon() = card(
        name = "Duel at Noon",
        cost = "{R}",
        type = "Instant",
        text = "Target creature you control gets +1/+1 and gains first strike until end of turn. Creatures target opponent controls lose first strike and double strike until end of turn.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 1, 1, has(Keyword.FirstStrike)),
            untilEndOfTurn(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))),
                Mechanic.LoseAbilities(Keyword.FirstStrike.kind), Mechanic.LoseAbilities(Keyword.DoubleStrike.kind)),
            targets = listOf(slot("creature", creatureYouControl), targetOpponent),
        ),
    )

    // CR 603.4: the intervening "if" checked as it triggers and resolves, and again before the second search (CR 608.2c); CR 701.23a
    @Test
    fun lodeStalker() = card(
        name = "Lode Stalker",
        cost = "{2}{W}",
        type = "Creature — Rabbit Mercenary",
        pt = "3/3",
        text = """
            Vigilance
            When this creature enters, if an opponent controls more lands than you, you may search your library for a Plains card and put it onto the battlefield tapped. Then if an opponent controls more lands than you, repeat this process once. If you search your library this way, shuffle.
        """,
    ) parsesTo run {
        // an opponent with more lands than you, each opponent compared on their own (CR 109.5)
        val moreLands = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
            Value.Count(Objects.Each(and(land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))))), Comparison.Greater,
            Value.Count(Objects.Each(and(land, you))))))), atLeast = 1)
        fun searchOnce(bind: String) = Step.Pay(steps = listOf(search(sub("Plains")), move(Objects.Bound("found"), Zone.Battlefield, tapped = true)),
            payer = Players.You, bind = bind)
        abilities(
            Keyword.Vigilance,
            triggered(entersSelf,
                searchOnce("searched"),
                ifThen(moreLands, searchOnce("searched again")),
                ifThen(Condition.Or(listOf(Condition.Did("searched"), Condition.Did("searched again"))), shuffle),
                interveningIf = moreLands),
        )
    }

    // CR 702.19c: double strike for each Ox you control; CR 400.7: the exiled card may be cast until the end of your next turn
    @Test
    fun tamsinRookOxHerder() = card(
        name = "Tamsin Rook, Ox Herder",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Warrior",
        pt = "4/3",
        text = "Oxen you control have double strike.",
    ) parsesTo abilities(
        static(has(Keyword.DoubleStrike), affects = Objects.Each(and(sub("Ox"), you))),
    )

    // CR 701.23a: search, reveal, shuffle, then the card on top; CR 205.3m: Desert
    @Test
    fun brassConstable() = card(
        name = "Brass Constable",
        cost = "{2}",
        type = "Artifact Creature — Mercenary",
        pt = "1/2",
        text = "When this creature enters, you may search your library for a basic land card or a Desert card, reveal it, then shuffle and put it on top.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(
            search(or(basicLand(), sub("Desert")), reveal = true),
            doing(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0)), Players.You)),
    )

    // CR 608.2h: "its mana value" is the sacrificed creature's as it last existed; CR 609.4b: mana of any type; CR 115.1: target player
    @Test
    fun bloodcoinEnforcer() = card(
        name = "Bloodcoin Enforcer",
        cost = "{2}{B}{B}{R}",
        type = "Legendary Creature — Demon Mercenary",
        pt = "6/5",
        text = """
            Flying, trample
            Whenever you sacrifice another creature, exile cards equal to its mana value from the top of target player's library. Until your next end step, you may play those cards, and mana of any type can be spent to cast those spells.
            Sacrifice another creature: Bloodcoin Enforcer gains indestructible until end of turn. Tap it. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        triggered(on(EventPattern.Sacrifice(and(another, creature, you))),
            move(Objects.LibraryCards(Players.Target("player"), Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single),
                fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, manaAsAnyColor = true)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilStep(TurnStep.End, PlayerFilter.You))),
            targets = listOf(targetPlayer)),
        activated(cost(sacrificeA(and(another, creature))), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self),
            rules = anyTime.copy(perTurn = 1)),
    )

    // CR 611.2e: what the returned card is applies as it enters; CR 205.1a: its card types and subtypes replaced; CR 111.10a: Treasure
    @Test
    fun ilsaTheGildedHush() = card(
        name = "Ilsa, the Gilded Hush",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Gorgon Assassin",
        pt = "3/3",
        text = """
            Deathtouch
            Whenever a nontoken creature an opponent controls dies, you may pay {1}. If you do, return that card to the battlefield tapped under your control. It's a Treasure artifact with "{T}, Sacrifice this artifact: Add one mana of any color," and it loses all other card types.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.ZoneChange(and(not(token), creature, opponents), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, tapped = true,
                controller = Players.You, entersAs = listOf(
                    Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Treasure"))),
                        replaceTypes = true, replaceSubtypes = true),
                    Mechanic.GainAbilities(listOf(activated(cost(tap, sacrificeSelf),
                        doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))))))))),
        ),
    )

    // CR 500.10: each additional upkeep step is a phase of its own after this phase; "that many": the combat damage dealt (CR 120.4)
    @Test
    fun garthSecondsThief() = card(
        name = "Garth, Seconds Thief",
        cost = "{1}{U}{B}{R}",
        type = "Legendary Creature — Ogre Warlock",
        pt = "2/5",
        text = """
            Menace
            Whenever Garth deals combat damage to a player, you get that many additional upkeep steps after this phase.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer, doing(Effect.AdditionalStep(TurnStep.Upkeep, afterPhase = true, count = Value.EventAmount))),
    )

    // CR 603.2c: one trigger for the batch, a copy of each token in it (CR 707.2); CR 603.2h: once each turn
    @Test
    fun tollkeeperMayor() = card(
        name = "Tollkeeper Mayor",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/4",
        text = """
            Whenever one or more tokens your opponents control enter, for each of them, create a tapped token that's a copy of it. This ability triggers only once each turn.
            Whenever one or more tokens you control enter, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(token, opponents), to = Zone.Battlefield), oneOrMore = true),
            Step.ForEach(Each.Objects(Objects.EventObject(EventRole.After)), bind = "entered", steps = listOf(
                doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("entered")), n(1), Players.You, tapped = true)))),
            perTurn = 1),
        triggered(Trigger.On(EventPattern.ZoneChange(and(token, you), to = Zone.Battlefield), oneOrMore = true),
            loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 603.2: checked as it would trigger (your turn, not your first spell); CR 122.1a: one counter for each other Zombie that entered
    @Test
    fun corpsewrightVell() = card(
        name = "Corpsewright Vell",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Warlock",
        pt = "2/3",
        text = """
            Whenever you cast a spell during your turn other than your first spell that turn, create a 2/2 blue and black Zombie Rogue creature token.
            Whenever a Zombie you control enters, put a +1/+1 counter on it for each other Zombie that entered the battlefield under your control this turn.
        """,
    ) parsesTo abilities(
        triggered(youCast(not(ObjectFilter.NthSpellThisTurn(ObjectFilter.Any, 1))),
            create(creatureToken("Zombie Rogue", listOf(Color.Blue, Color.Black), 2, 2)), triggersOnlyIf = Condition.YourTurn),
        triggered(enters(and(sub("Zombie"), you)), counters(Objects.EventObject(EventRole.After), Value.Sum(listOf(
            Value.EventsThisTurn(EventPattern.ZoneChange(and(sub("Zombie"), you), to = Zone.Battlefield), Measure.Objects), n(-1))))),
    )

    // CR 608.2c: chosen as it resolves (not targeted); CR 400.7: each return is a new object; CR 107.3: X more times
    @Test
    fun encoreShuffle() = card(
        name = "Encore Shuffle",
        cost = "{X}{X}{2}{W}",
        type = "Sorcery",
        text = "Exile any number of creatures you control, then return them to the battlefield under their owner's control. Then repeat this process X more times.",
    ) parsesTo abilities(
        spell(Step.Repeat(Value.Sum(listOf(Value.Announced(name = "X"), n(1))), listOf(
            Step.Choose(Choice.Objects(and(creature, you), n(0), null), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))), announced = listOf("X")),
    )
}
