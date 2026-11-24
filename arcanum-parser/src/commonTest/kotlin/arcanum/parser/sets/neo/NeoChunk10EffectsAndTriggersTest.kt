package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 10: a face-down exile cast only as an instant of mana value 2 or less, an exile-and-search by name with one card playable, the first
 * activated ability of an artifact each turn costing less, a count taken as the spell was cast, a bonus per creature sharing its name, "blocks or
 * becomes blocked", "an opponent casts an artifact, instant, or sorcery spell", noncreature permanents returned to hand, a ninjutsu activation
 * trigger, a choice of keyword counter for each token, "up to X target creatures phase out", a flicker with a counter, "up to two of those cards". */
class NeoChunk10EffectsAndTriggersTest {
    private val vehicle = sub("Vehicle")
    private val x: Value = Value.Announced(name = "X")
    private val exiled: Objects = Objects.Bound("exiled")

    // CR 406.3 (the looker may go on looking at the face-down card), 406.3b (the qualities are the resulting spell's), 401.4
    @Test
    fun discoverTheImpossible() = card(
        name = "Unearth the Unthinkable",
        cost = "{2}{U}",
        type = "Instant",
        text = "Look at the top five cards of your library. Exile one of them face down and put the rest on the bottom of your library in a random order. You may cast the exiled card without paying its mana cost if it's an instant spell with mana value 2 or less. If you don't, put that card into your hand.",
    ) parsesTo abilities(spell(
        doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
        doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
        doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        Step.Pay(steps = listOf(doing(Effect.Cast(exiled, PlayKind.Cast, withoutPaying = true, caster = Players.You,
            resultingSpell = and(instant, manaValue(Comparison.AtMost, 2))))), payer = Players.You, bind = "cast"),
        ifThen(Condition.Not(Condition.Did("cast")), move(exiled, Zone.Hand)),
    ))

    // CR 701.23 (searching hidden zones for cards by name), 611.2a-b (one card in all, for as long as you control it), 609.4b (any colour)
    @Test
    fun kotoseTheSilentSpider() = card(
        name = "Kotara, the Hushed Spider",
        cost = "{3}{U}{B}",
        type = "Legendary Creature — Human Ninja",
        pt = "4/4",
        text = "When Kotara enters, exile target card other than a basic land card from an opponent's graveyard. Search that player's graveyard, hand, and library for any number of cards with the same name as that card and exile them. Then that player shuffles. For as long as you control Kotara, you may play one of the exiled cards, and you may spend mana as though it were mana of any color to cast it.",
    ) parsesTo abilities(run {
        val player = Players.OwnerOf(exiled)
        val zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library)
        triggered(entersSelf,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Search(player, ObjectFilter.SameNameAs(exiled), Value.Count(Objects.Each(and(ObjectFilter.OwnedBy(PlayerFilter.Is(player)),
                or(*zones.map { inZone(it) }.toTypedArray())))), zones = zones, bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Exile, postBind = "found exiled"),
            doing(Effect.Shuffle(player)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, total = 1, manaAsAnyColorOnly = true)),
                Affected.Objects(Objects.Union(listOf(exiled, Objects.Bound("found exiled")))),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(slot("card", and(cardFilter, not(basicLand()), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)))),
        )
    })

    // CR 601.2f, 602.2b (an activation cost reduced; mana abilities are activated abilities too, CR 605.1a), 606.3, 205.1b, 613.4b, 301.7
    @Test
    fun tezzeretBetrayerOfFlesh() = card(
        name = "Tezzarin, Flesh Forsworn",
        cost = "{2}{U}{U}",
        type = "Legendary Planeswalker — Tezzeret",
        loyalty = "4",
        text = """
            The first activated ability of an artifact you activate each turn costs {2} less to activate.
            [+1]: Draw two cards. Then discard two cards unless you discard an artifact card.
            [−2]: Target artifact becomes an artifact creature. If it isn't a Vehicle, it has base power and toughness 4/4.
            [−6]: You get an emblem with "Whenever an artifact you control becomes tapped, draw a card."
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact), you), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Activate(artifact, PlayerFilter.You, manaAbilities = true), atLeast = 1,
                measure = Measure.Events))),
        loyalty(1, draw(2), Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(artifact, count = n(1))))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), discard(2))),
        loyalty(-2,
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))), Affected.Objects(target("artifact")), Duration.Indefinitely)),
            ifThen(Condition.Not(Condition.ObjectMatches(target("artifact"), vehicle)),
                doing(Effect.ApplyMechanics(listOf(Mechanic.SetPowerToughness(n(4), n(4))), Affected.Objects(target("artifact")), Duration.Indefinitely))),
            targets = listOf(slot("artifact", artifact))),
        loyalty(-6, doing(Effect.CreateEmblem(listOf(triggered(on(EventPattern.Tap(and(artifact, you))), draw(1))), Players.You))),
    )

    // CR 601.2i: the modified creatures are counted as the spell finishes being cast; CR 700.9 (modified)
    @Test
    fun lethalExploit() = card(
        name = "Deadly Overreach",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -2/-2 until end of turn. It gets an additional -1/-1 until end of turn for each modified creature you controlled as you cast this spell. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(run {
        val minus = Value.Times(Value.AsCast(count(and(creature, you, ObjectFilter.Modified))), -1)
        spell(pump(target("creature"), -2, -2),
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(minus, minus)),
            targets = listOf(slot("creature", creature)))
    })

    // CR 704.5j (the legend rule), 201.2 (the same name), 613.4c
    @Test
    fun mirrorBox() = card(
        name = "Mirrored Coffer",
        cost = "{3}",
        type = "Artifact",
        text = """
            The "legend rule" doesn't apply to permanents you control.
            Each legendary creature you control gets +1/+1.
            Each nontoken creature you control gets +1/+1 for each other creature you control with the same name as that creature.
        """,
    ) parsesTo run {
        val same = count(and(not(ObjectFilter.Is(Objects.Affected)), creature, you, ObjectFilter.SameNameAs(Objects.Affected)))
        abilities(
            static(Mechanic.LegendRuleDoesntApply, affects = Objects.Each(and(permanentFilter, you))),
            static(pt(1, 1), affects = Objects.Each(and(legendary, creature, you))),
            static(Mechanic.ModifyPowerToughness(same, same), affects = Objects.Each(and(not(token), creature, you))),
        )
    }

    // CR 608.2c ("Then if" checked after the destruction); "up to one" may be none (CR 115.6)
    @Test
    fun banishingSlash() = card(
        name = "Exiling Cut",
        cost = "{W}{W}",
        type = "Sorcery",
        text = "Destroy up to one target artifact, enchantment, or tapped creature. Then if you control an artifact and an enchantment, create a 2/2 white Samurai creature token with vigilance.",
    ) parsesTo abilities(spell(
        destroy(target("permanent")),
        ifThen(controlArtifactAndEnchantment, create(creatureToken("Samurai", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance)))),
        targets = listOf(slot("permanent", or(artifact, enchantment, and(tapped, creature)), min = 0)),
    ))

    // CR 603.12 (a reflexive trigger after a cost paid on resolution, CR 118.12), 107.3c ("where X is", counted as it resolves)
    @Test
    fun goShintaiOfHiddenCruelty() = card(
        name = "Go-Shrine of Veiled Cruelty",
        cost = "{3}{B}",
        type = "Legendary Enchantment Creature — Shrine",
        pt = "2/2",
        text = """
            Deathtouch
            At the beginning of your end step, you may pay {1}. When you do, destroy target creature with toughness X or less, where X is the number of Shrines you control.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(yourEndStep,
            Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(destroy(target("creature")),
                targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtMost, count(and(sub("Shrine"), you))))))))),
    )

    // CR 707.2, 111.1 (a token copy of the artifact that entered, as it last existed if it left, CR 608.2h)
    @Test
    fun replicationSpecialist() = card(
        name = "Duplication Adept",
        cost = "{4}{U}",
        type = "Creature — Moonfolk Artificer",
        pt = "3/4",
        text = """
            Flying
            Whenever a nontoken artifact you control enters, you may pay {1}{U}. If you do, create a token that's a copy of that artifact.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(not(token), artifact, you)),
            Step.Pay(manaCost("{1}{U}"), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.EventObject(EventRole.After)), n(1), Players.You)))),
    )

    // CR 611.2a (two of the cards in all), 601.2b, 601.2f (an additional cost that reduces the cost)
    @Test
    fun marchOfRecklessJoy() = card(
        name = "Procession of Wild Joy",
        cost = "{X}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may exile any number of red cards from your hand. This spell costs {2} less to cast for each card exiled this way.
            Exile the top X cards of your library. You may play up to two of those cards until the end of your next turn.
        """,
    ) parsesTo abilities(
        marchCost(Color.Red),
        Ability.Spell(Instructions(steps = listOf(
            move(Objects.LibraryCards(Players.You, x, fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, total = 2)), Affected.Objects(exiled),
                Duration.UntilEndOfYourNextTurn))),
            announced = listOf("X"))),
    )

    // CR 603.4 (an intervening "if"), 113.6m (it works from the graveyard), 400.3 (your graveyard: an enchantment you own)
    @Test
    fun kamiOfTransience() = card(
        name = "Kami of Passing Moments",
        cost = "{1}{G}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            Trample
            Whenever you cast an enchantment spell, put a +1/+1 counter on this creature.
            At the beginning of each end step, if an enchantment was put into your graveyard from the battlefield this turn, you may return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(youCast(enchantment), counters(Objects.Self)),
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any), Step.May(listOf(move(Objects.Self, Zone.Hand)), Players.You),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(enchantment, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Battlefield,
                to = Zone.Graveyard), atLeast = 1, measure = Measure.Events),
            functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 400.7 ("that permanent": the one the card became), 608.2c
    @Test
    fun okibaSalvage() = card(
        name = "Okiba Scrapping",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Return target creature or Vehicle card from your graveyard to the battlefield. Then put two +1/+1 counters on that permanent if you control an artifact and an enchantment.",
    ) parsesTo abilities(spell(
        move(target("card"), Zone.Battlefield, postBind = "returned"),
        ifThen(controlArtifactAndEnchantment, counters(Objects.Bound("returned"), 2)),
        targets = listOf(slot("card", inYourGraveyard(or(creature, vehicle)))),
    ))

    // CR 205.2 ("artifact and enchantment cards": each card that is either)
    @Test
    fun brilliantRestoration() = card(
        name = "Radiant Recovery",
        cost = "{3}{W}{W}{W}{W}",
        type = "Sorcery",
        text = "Return all artifact and enchantment cards from your graveyard to the battlefield.",
    ) parsesTo abilities(spell(move(Objects.Each(inYourGraveyard(or(artifact, enchantment))), Zone.Battlefield)))

    // CR 603.7 (a delayed trigger at your next end step, about the Vehicle that returned, CR 400.7)
    @Test
    fun greasefangOkibaBoss() = card(
        name = "Oilfang, Okiba Chief",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Rat Pilot",
        pt = "4/3",
        text = "At the beginning of combat on your turn, return target Vehicle card from your graveyard to the battlefield. It gains haste. Return it to its owner's hand at the beginning of your next end step.",
    ) parsesTo abilities(triggered(beginningOfCombatOnYourTurn,
        move(target("card"), Zone.Battlefield, postBind = "returned"),
        doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
        delayed(yourEndStep, move(Objects.Bound("returned"), Zone.Hand)),
        targets = listOf(slot("card", inYourGraveyard(vehicle))),
    ))

    // CR 107.3 (X announced, then the sacrifice must have that mana value), 601.2c
    @Test
    fun scrapWelder() = card(
        name = "Junk Welder",
        cost = "{2}{R}",
        type = "Creature — Goblin Artificer",
        pt = "3/3",
        text = "{T}, Sacrifice an artifact with mana value X: Return target artifact card with mana value less than X from your graveyard to the battlefield. It gains haste until end of turn.",
    ) parsesTo abilities(Ability.Activated(
        cost(tap, sacrificeA(and(artifact, ObjectFilter.ManaValue(Comparison.Equal, x)))),
        Instructions(
            targets = listOf(slot("card", inYourGraveyard(artifact, ObjectFilter.ManaValue(Comparison.Less, x)))),
            steps = listOf(move(target("card"), Zone.Battlefield, postBind = "moved-card"), gains(Objects.Bound("moved-card"), Keyword.Haste)),
            announced = listOf("X")),
        anyTime,
    ))

    // CR 509.1b (both can be blocked only by creatures with flying or reach this turn)
    @Test
    fun tatsunariToadRider() = card(
        name = "Tatsuro, Toad Rider",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Ninja",
        pt = "3/3",
        text = """
            Whenever you cast an enchantment spell, if you don't control a creature named Keimi, create Keimi, a legendary 3/3 black and green Frog creature token with "Whenever you cast an enchantment spell, each opponent loses 1 life and you gain 1 life."
            {1}{G/U}: Tatsuro and target Frog you control can't be blocked this turn except by creatures with flying or reach.
        """,
    ) parsesTo abilities(
        triggered(youCast(enchantment),
            create(creatureToken("Frog", listOf(Color.Black, Color.Green), 3, 3, name = "Keimi",
                abilities = listOf(triggered(youCast(enchantment), doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), n(1))), gainLife(1))))
                .let { t -> (t as TokenSpec.Defined).copy(face = t.face.copy(supertypes = listOf(Supertype.Legendary))) }),
            interveningIf = Condition.Not(Condition.Exists(and(creature, ObjectFilter.Named("Keimi"), you), atLeast = 1))),
        activated(manaCost("{1}{G/U}"),
            untilEndOfTurn(Objects.Union(listOf(Objects.Self, target("Frog"))), Mechanic.CanBeBlockedOnlyBy(and(creature, or(kw(Keyword.Flying), kw(Keyword.Reach))))),
            targets = listOf(slot("Frog", and(sub("Frog"), you)))),
    )

    // CR 122.1b (keyword counters), 608.2c (a choice for each token)
    @Test
    fun invokeTheAncients() = card(
        name = "Call the Old Ones",
        cost = "{1}{G}{G}{G}{G}",
        type = "Sorcery",
        text = "Create two 4/5 green Spirit creature tokens. For each of them, put your choice of a reach counter, a vigilance counter, or a trample counter on it.",
    ) parsesTo abilities(run {
        val token = Objects.Bound("token")
        fun put(k: KeywordKind) = counters(token, 1, CounterKind.Keyword(k))
        spell(
            create(creatureToken("Spirit", listOf(Color.Green), 4, 5), n(2), bind = "tokens"),
            Step.ForEach(Each.Objects(Objects.Bound("tokens")), bind = "token", steps = listOf(
                Step.Choose(Choice.Option(listOf("reach", "vigilance", "trample")), bind = "counter", chooser = Players.You),
                ifThen(Condition.Chose("counter", "reach"), put(Keyword.Reach), otherwise = listOf(
                    ifThen(Condition.Chose("counter", "vigilance"), put(Keyword.Vigilance), otherwise = listOf(put(Keyword.Trample))))))),
        )
    })

    // CR 702.26 (phasing), 601.2c (up to X targets, X announced, CR 601.2b)
    @Test
    fun marchOfSwirlingMist() = card(
        name = "Procession of Drifting Fog",
        cost = "{X}{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may exile any number of blue cards from your hand. This spell costs {2} less to cast for each card exiled this way.
            Up to X target creatures phase out. (While they're phased out, they're treated as though they don't exist. Each one phases in before its controller untaps during their next untap step.)
        """,
    ) parsesTo abilities(
        marchCost(Color.Blue),
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = n(0), max = x)),
            steps = listOf(doing(Effect.PhaseOut(target("creatures")))),
            announced = listOf("X"))),
    )

    // CR 400.7 (it returns as a new object, with the counter, CR 614.1c)
    @Test
    fun planarIncision() = card(
        name = "Planar Slice",
        cost = "{1}{U}",
        type = "Instant",
        text = "Exile target artifact or creature, then return it to the battlefield under its owner's control with a +1/+1 counter on it.",
    ) parsesTo abilities(spell(
        move(target("permanent"), Zone.Exile, postBind = "exiled"),
        doing(Effect.Move(exiled, Destination(Zone.Battlefield, controller = Players.OwnerOf(exiled), counters = listOf(CountersToPut(p1p1, n(1)))))),
        targets = listOf(slot("permanent", or(artifact, creature))),
    ))

    // CR 702.49a (ninjutsu, granted ones too: an activated ability), 603.2, 401.4
    @Test
    fun satoruUmezawa() = card(
        name = "Satori Umeno",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Human Ninja",
        pt = "2/4",
        text = """
            Whenever you activate a ninjutsu ability, look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. This ability triggers only once each turn.
            Each creature card in your hand has ninjutsu {2}{U}{B}.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, ability = ObjectFilter.AbilityOf(keyword = Keyword.Ninjutsu.Kind))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1))),
            perTurn = 1),
        static(has(Keyword.Ninjutsu(manaCost("{2}{U}{B}"))), affects = Objects.Each(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 509.3a, 509.3c (once each combat either way); "it": the creature that blocked or became blocked, this one
    @Test
    fun jukaiTrainee() = card(
        name = "Jukai Novice",
        cost = "{1}{G}",
        type = "Creature — Human Samurai",
        pt = "2/2",
        text = "Whenever this creature blocks or becomes blocked, it gets +1/+1 until end of turn.",
    ) parsesTo abilities(triggered(
        Trigger.On(listOf(EventPattern.Block(ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any),
            EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))),
        pump(Objects.EventObject(EventRole.Object), 1, 1),
    ))

    // CR 707.10 (copy that spell), 701.6 (counter), 603.2h ("triggers only once each turn"), 102.3 (an opponent)
    @Test
    fun jinGitaxiasProgressTyrant() = card(
        name = "Gin-Taxias, Progress Despot",
        cost = "{5}{U}{U}",
        type = "Legendary Creature — Phyrexian Praetor",
        pt = "5/5",
        text = """
            Whenever you cast an artifact, instant, or sorcery spell, copy that spell. You may choose new targets for the copy. This ability triggers only once each turn. (A copy of a permanent spell becomes a token.)
            Whenever an opponent casts an artifact, instant, or sorcery spell, counter that spell. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(youCast(or(artifact, instant, sorcery)), doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)), perTurn = 1),
        triggered(on(EventPattern.Cast(or(artifact, instant, sorcery), PlayerFilter.Opponent)), doing(Effect.Counter(thatSpell)), perTurn = 1),
    )

    // CR 700.9 (modified), 603.10a (looked back at as it died: its counters, Equipment and Auras then)
    @Test
    fun akkiEmberKeeper() = card(
        name = "Akki Spark-Keeper",
        cost = "{1}{R}",
        type = "Enchantment Creature — Goblin Warrior",
        pt = "2/1",
        text = "Whenever a nontoken modified creature you control dies, create a 1/1 colorless Spirit creature token. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(triggered(
        on(EventPattern.ZoneChange(and(not(token), ObjectFilter.Modified, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
        create(creatureToken("Spirit", emptyList(), 1, 1)),
    ))

    // CR 603.2c (one or more at once: one trigger), 603.2h, 400.3; CR 107.3, 602.5d (activate only as a sorcery)
    @Test
    fun tameshiRealityArchitect() = card(
        name = "Tamesho, Reality Builder",
        cost = "{2}{U}",
        type = "Legendary Creature — Moonfolk Wizard",
        pt = "2/3",
        text = """
            Whenever one or more noncreature permanents are returned to hand, draw a card. This ability triggers only once each turn.
            {X}{W}, Return a land you control to its owner's hand: Return target artifact or enchantment card with mana value X or less from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(not(creature), permanentFilter), from = Zone.Battlefield, to = Zone.Hand), oneOrMore = true), draw(1), perTurn = 1),
        Ability.Activated(
            cost(mana("{X}{W}"), CostPart.ReturnToHand(CostObjects.Choose(and(land, you), count = n(1)))),
            Instructions(
                targets = listOf(slot("card", inYourGraveyard(or(artifact, enchantment), ObjectFilter.ManaValue(Comparison.AtMost, x)))),
                steps = listOf(move(target("card"), Zone.Battlefield)),
                announced = listOf("X")),
            asSorcery),
    )

    /** "As an additional cost to cast this spell, you may exile any number of <color> cards from your hand. This spell costs {2} less to cast for each
     * card exiled this way." (CR 601.2b, 601.2f) */
    private fun marchCost(color: Color) = static(
        Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(color), count = null), from = Zone.Hand, postBind = "exiled")))),
        Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, Value.Count(Objects.Bound("exiled")))),
    )
}
