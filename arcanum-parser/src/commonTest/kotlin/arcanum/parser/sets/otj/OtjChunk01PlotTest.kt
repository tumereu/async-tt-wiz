package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 01: plot (CR 702.170): the special action, "when this card becomes plotted" (it is in exile, CR 702.170c, 113.6k), effects that
 * make a card in exile plotted, and the other wordings of the set's first plot cards.
 */
class OtjChunk01PlotTest {
    private val trample = Keyword.Trample
    private val spells = Value.EventsThisTurn(EventPattern.Cast(by = PlayerFilter.You, spell = ObjectFilter.Any), measure = Measure.Events)

    // CR 702.170c, 113.6k: the trigger works in exile, where the card becomes plotted
    @Test
    fun cactusBrewmaster() = card(
        name = "Cactus Brewmaster",
        cost = "{1}{G}",
        type = "Creature — Plant Warlock",
        pt = "3/2",
        text = """
            Trample
            When this card becomes plotted, target creature gets +3/+2 and gains trample until end of turn.
            Plot {1}{G} (You may pay {1}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        trample,
        triggered(becomesPlotted, pump(target("creature"), 3, 2, has(trample)), targets = listOf(slot("creature", creature))),
        plot("{1}{G}"),
    )

    // CR 702.170c: the exiled spell's card becomes plotted (its owner may cast it, 702.170d); CR 601.2f: a cost increase by where it's cast from
    @Test
    fun hawkWaylayer() = card(
        name = "Hawk Waylayer",
        cost = "{1}{W}{W}",
        type = "Creature — Bird Rogue",
        pt = "2/2",
        text = """
            Flash
            Flying
            When this creature enters, exile target spell. It becomes plotted. (Its owner may cast it as a sorcery on a later turn without paying its mana cost.)
            Spells your opponents cast from graveyards or from exile cost {2} more to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, move(target("spell"), Zone.Exile, postBind = "exiled"), markPlotted(Objects.Bound("exiled")),
            targets = listOf(slot("spell", spellFilter))),
        static(Mechanic.CostChange(and(spellFilter, opponents, or(ObjectFilter.WasCast(Zone.Graveyard), ObjectFilter.WasCast(Zone.Exile))),
            CostChange.More(ManaCost.parse("{2}")!!, n(1)))),
    )

    // CR 603.2: the second spell each turn; CR 111.4, 113.1a: a token with a quoted ability of two sentences
    @Test
    fun cinderStampede() = card(
        name = "Cinder Stampede",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            Whenever you cast your second spell each turn, create a 1/1 red Mercenary creature token with "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery."
            Plot {2}{R} (You may pay {2}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            create(creatureToken("Mercenary", listOf(Color.Red), 1, 1, abilities = listOf(
                activated(cost(tap), pump(target("creature"), 1, 0), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery))))),
        plot("{2}{R}"),
    )

    // CR 614.1c: counters of two kinds as it enters, if the condition holds then
    @Test
    fun duneWraith() = card(
        name = "Dune Wraith",
        cost = "{1}{W}",
        type = "Creature — Spirit",
        pt = "2/3",
        text = """
            Flying
            If you control five or more untapped lands, this creature enters with two +1/+1 counters and a lifelink counter on it.
            Plot {1}{W} (You may pay {1}{W} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersWith(p1p1 to n(2), CounterKind.Keyword(Keyword.Lifelink.kind) to n(1),
            condition = youControl(and(not(tapped), land), 5)),
        plot("{1}{W}"),
    )

    // CR 614.1c, 601.2h: no mana spent when cast without paying its mana cost (a plotted card, 702.170d); one not cast at all also counts
    @Test
    fun freehoofRaider() = card(
        name = "Freehoof Raider",
        cost = "{2}{G}",
        type = "Creature — Centaur Mercenary",
        pt = "3/3",
        text = """
            This creature enters with two +1/+1 counters on it if it wasn't cast or no mana was spent to cast it.
            Plot {3}{G} (You may pay {3}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        entersWith(p1p1 to n(2), condition = Condition.Or(listOf(Condition.Not(Condition.CastFrom(null, Objects.Self)),
            Condition.Compare(Value.ManaSpent(of = Objects.Self), Comparison.Equal, n(0))))),
        plot("{3}{G}"),
    )

    // CR 702.170c: "If you do, it becomes plotted": the card exiled from the hand; CR 707.10c: copies of a delayed trigger with new targets
    @Test
    fun veskUnbound() = card(
        name = "Vesk Unbound",
        cost = "{U}{U}",
        type = "Legendary Planeswalker — Jace",
        loyalty = "3",
        text = """
            You can't cast Vesk Unbound during your first, second, or third turns of the game.
            [+1]: Draw a card, then discard a card.
            [+1]: You may exile a nonland card with mana value 3 or less from your hand. If you do, it becomes plotted.
            [−6]: Until end of turn, whenever you cast a spell, copy it. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        static(Mechanic.CastRestriction(condition = Condition.Not(Condition.And(listOf(Condition.YourTurn,
            Condition.Compare(left = Value.TurnsTaken(Players.You), comparison = Comparison.AtMost, right = n(3))))))),
        loyalty(1, draw(1), discard(1)),
        loyalty(1, Step.Pay(cost(CostPart.Exile(CostObjects.Choose(and(nonland, manaValue(Comparison.AtMost, 3)), n(1)), from = Zone.Hand, postBind = "exiled")),
                payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), markPlotted(Objects.Bound("exiled")))),
        loyalty(-6, doing(Effect.CreateDelayedTrigger(youCast(ObjectFilter.Any),
            Instructions(steps = listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))),
            duration = Duration.EndOfTurn, repeats = true))),
    )

    // CR 702.170c; CR 122.1: a counter on each creature you control
    @Test
    fun marshalRidesOut() = card(
        name = "Marshal Rides Out",
        cost = "{G}{W}{U}",
        type = "Legendary Enchantment",
        text = """
            When Marshal Rides Out enters, you may exile a nonland card with mana value 3 or less from your hand. If you do, it becomes plotted. (You may cast it as a sorcery on a later turn without paying its mana cost.)
            Whenever a legendary creature you control enters, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(CostPart.Exile(CostObjects.Choose(and(nonland, manaValue(Comparison.AtMost, 3)), n(1)), from = Zone.Hand, postBind = "exiled")),
                payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), markPlotted(Objects.Bound("exiled")))),
        triggered(enters(and(legendary, creature, you)), counters(Objects.Each(creatureYouControl))),
    )

    // CR 614.1a: the resolving spell is exiled instead of put into the graveyard, and becomes plotted there (CR 702.170c)
    @Test
    fun miraUnbeatenGunhand() = card(
        name = "Mira, Unbeaten Gunhand",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Human Rogue",
        pt = "3/3",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever you cast a multicolored instant or sorcery spell from your hand, exile that spell instead of putting it into your graveyard as it resolves. If you do, it becomes plotted. (You may cast it as a sorcery on a later turn without paying its mana cost.)
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        triggered(on(EventPattern.Cast(and(or(instant, sorcery), ObjectFilter.Multicolored, spellFilter), PlayerFilter.You, from = Zone.Hand)),
            doing(Effect.SpellGets(Objects.EventObject(EventRole.Object), listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), from = Zone.Stack, to = Zone.Graveyard, resolving = true),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile)), Modification.EntersMarked(ObjectMark.Plotted))))))),
    )

    // CR 702.170c, 113.6k; the card in exile is the damage's source (CR 120.3)
    @Test
    fun hornbackMarksman() = card(
        name = "Hornback Marksman",
        cost = "{2}{R}",
        type = "Creature — Minotaur Rogue",
        pt = "3/3",
        text = """
            Reach
            When this card becomes plotted, it deals 2 damage to any target.
            Plot {3}{R} (You may pay {3}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(becomesPlotted, damage(2, toAny), targets = listOf(anyTarget)),
        plot("{3}{R}"),
    )

    // CR 401.5, 702.170c: one looked-at card exiled and plotted, the rest into the hand
    @Test
    fun forgeYourFortune() = card(
        name = "Forge Your Fortune",
        cost = "{3}{G}{U}",
        type = "Sorcery",
        text = "Look at the top three cards of your library. You may exile a nonland card from among them. If you do, it becomes plotted. Put the rest into your hand. (You may cast it as a sorcery on a later turn without paying its mana cost.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(nonland, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.NonEmpty("exiled"), markPlotted(Objects.Bound("exiled"))),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Hand),
        ),
    )

    // CR 111.1: counters on the token just created, two for each spell cast this turn after the first (none below zero, CR 107.1b)
    @Test
    fun banditSeamster() = card(
        name = "Bandit Seamster",
        cost = "{3}{U}",
        type = "Creature — Human Warlock",
        pt = "1/4",
        text = """
            When this creature enters, create a 2/2 blue and black Zombie Rogue creature token, then put two +1/+1 counters on that token for each spell you've cast this turn other than the first.
            Plot {4}{U} (You may pay {4}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            create(creatureToken("Zombie Rogue", listOf(Color.Blue, Color.Black), 2, 2), bind = "token"),
            counters(Objects.Bound("token"), Value.Times(Value.Max(listOf(Value.Difference(spells, n(1)), n(0))), 2))),
        plot("{4}{U}"),
    )

    // CR 701.21a: sacrifice any number (an effect; zero is allowed), then draw as many as were sacrificed
    @Test
    fun ruthlessButchery() = card(
        name = "Ruthless Butchery",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Sacrifice any number of permanents you control, then draw that many cards.
            Plot {1}{B}{B} (You may pay {1}{B}{B} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(permanentFilter, you), n(0), null), bind = "chosen", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("chosen"), postBind = "sacrificed")),
            doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("sacrificed")))),
        ),
        plot("{1}{B}{B}"),
    )

    // CR 614.1c: one counter plus one for each other creature you control as it enters
    @Test
    fun wardenOfQuietRoads() = card(
        name = "Warden of Quiet Roads",
        cost = "{2}{W}",
        type = "Creature — Human Knight",
        pt = "0/0",
        text = """
            This creature enters with a +1/+1 counter on it plus an additional +1/+1 counter on it for each other creature you control.
            Plot {1}{W} (You may pay {1}{W} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        entersWith(p1p1 to Value.Sum(listOf(n(1), count(and(another, creature, you))))),
        plot("{1}{W}"),
    )

    // CR 101.4: each player decides in turn order; those who shuffled draw seven
    @Test
    fun strideAcrossRealms() = card(
        name = "Stride Across Realms",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = """
            Each player may shuffle their hand and graveyard into their library. Each player who does draws seven cards. Exile Stride Across Realms.
            Plot {4}{U}{U} (You may pay {4}{U}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Pay(steps = listOf(
                    move(Objects.Each(and(ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player"))), or(inZone(Zone.Hand), inZone(Zone.Graveyard)))), Zone.Library),
                    doing(Effect.Shuffle(Players.Bound("player")))), payer = Players.Bound("player"), bind = "shuffled"),
                ifThen(Condition.Did("shuffled"), draw(7, Players.Bound("player"))))),
            move(Objects.Self, Zone.Exile),
        ),
        plot("{4}{U}{U}"),
    )

    // CR 613.4c: -1/-1 for each card in its controller's hand
    @Test
    fun tailstingerHorror() = card(
        name = "Tailstinger Horror",
        cost = "{2}{R}{R}",
        type = "Creature — Scorpion Dragon",
        pt = "7/7",
        text = """
            Flying, trample
            This creature gets -1/-1 for each card in your hand.
            Plot {2}{R} (You may pay {2}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        trample,
        static(Mechanic.ModifyPowerToughness(Value.Times(Value.HandSize(Players.You), -1), Value.Times(Value.HandSize(Players.You), -1))),
        plot("{2}{R}"),
    )

    // CR 707.9b: the copy is also a Shapeshifter Rogue
    @Test
    fun maskOutlaw() = card(
        name = "Mask Outlaw",
        cost = "{3}{U}",
        type = "Creature — Shapeshifter Rogue",
        pt = "2/2",
        text = """
            You may have this creature enter as a copy of a creature you control, except it's a Shapeshifter Rogue in addition to its other types.
            Plot {2}{U} (You may pay {2}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersAsCopy(of = creatureYouControl, except = listOf(CopyException.Types(addSubtypes = listOf(
                SubtypeRef.Literal(Subtype("Shapeshifter")), SubtypeRef.Literal(Subtype("Rogue"))))), optional = true)))),
            affects = Affected.Objects(Objects.Self)),
        plot("{2}{U}"),
    )

    // CR 603.4: an intervening "if"; CR 702.170a: plot
    @Test
    fun wildkinExile() = card(
        name = "Wildkin Exile",
        cost = "{2}{G}",
        type = "Creature — Human Druid",
        pt = "3/3",
        text = """
            When this creature enters, if you control a creature with power 4 or greater, draw a card.
            Plot {1}{G} (You may pay {1}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), interveningIf = youControl(and(creature, power(Comparison.AtLeast, 4)), 1)),
        plot("{1}{G}"),
    )
}
