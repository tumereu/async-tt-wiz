package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 15 (and Syncopate from chunk 16): counters on creatures, "enters tapped" and counter replacements, a draw replacement, revealing
 * the top of the library, sacrificing for a reward, searching by name and power and toughness from a count of lands.
 */
class VowChunk15PutCountersReplacementRevealSacrificeSearchSetPtTest {
    private val wolfOrWerewolf = or(sub("Wolf"), sub("Werewolf"))
    private val entersTappedUnlessTwoOtherLands = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)), condition = Condition.Not(Condition.Exists(and(and(another, land), you), atLeast = 2)))

    private fun addEither(a: String, b: String): Ability = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
        colors = listOf(Color.of(a[0])!!, Color.of(b[0])!!).map { ColorRef.Literal(it) })), Players.You)))

    // CR 122.1 (counters), 115.1 (up to two targets: min 0, max 2), 506.4
    @Test
    fun angelicQuartermaster() = card(
        name = "Dawnwatch Quartermaster", cost = "{3}{W}{W}", type = "Creature — Angel Soldier", pt = "3/3",
        text = """
            Flying
            When this creature enters, put a +1/+1 counter on each of up to two other target creatures.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, counters(target("creatures"), 1), targets = listOf(slot("creatures", and(creature, another), min = 0, max = 2))),
    )

    // CR 702.7 (first strike), 603.2, 122.1: X counters where X is this creature's power; the target gains haste until end of turn
    @Test
    fun halanaAndAlenaPartners() = card(
        name = "Marrow and Quill, Partners", cost = "{2}{R}{G}", type = "Legendary Creature — Human Ranger", pt = "2/3",
        text = """
            Reach (This creature can block creatures with flying.)
            First strike (This creature deals combat damage before creatures without first strike.)
            At the beginning of combat on your turn, put X +1/+1 counters on another target creature you control, where X is Marrow and Quill's power. That creature gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.FirstStrike,
        triggered(beginningOfCombatOnYourTurn,
            counters(target("creature"), power(Objects.Self)),
            gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 603.4 (an intervening "if" is checked on trigger and on resolution), 122.1; CR 603.10a: the power of the creature as it last existed
    @Test
    fun packsongPup() = card(
        name = "Thornpath Pup", cost = "{1}{G}", type = "Creature — Wolf", pt = "1/1",
        text = """
            At the beginning of combat on your turn, if you control another Wolf or Werewolf, put a +1/+1 counter on this creature.
            When this creature dies, you gain life equal to its power.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(Objects.Self, 1),
            interveningIf = Condition.Exists(and(another, wolfOrWerewolf, you), atLeast = 1)),
        triggered(diesSelf, doing(Effect.GainLife(Players.You, power(Objects.Self)))),
    )

    // CR 613.1f, 122.1: the abilities depend on how many +1/+1 counters this creature has
    @Test
    fun voiceOfTheBlessed() = card(
        name = "Voice of the Chorus", cost = "{W}{W}", type = "Creature — Spirit Cleric", pt = "2/2",
        text = """
            Whenever you gain life, put a +1/+1 counter on this creature.
            As long as this creature has four or more +1/+1 counters on it, it has flying and vigilance.
            As long as this creature has ten or more +1/+1 counters on it, it has indestructible.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self)),
        static(has(Keyword.Flying, Keyword.Vigilance), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 4))),
        static(has(Keyword.Indestructible), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 10))),
    )

    // CR 701.21a (sacrifice), 608.2c ("if you do"), 111.10 (a Blood token is a token with the subtype Blood), 508.1m
    @Test
    fun weddingSecurity() = card(
        name = "Chapel Guard", cost = "{3}{B}{B}", type = "Creature — Vampire Soldier", pt = "4/4",
        text = "Whenever this creature attacks, you may sacrifice a Blood token. If you do, put a +1/+1 counter on this creature and draw a card.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(and(sub("Blood"), token), count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self), draw(1)))),
    )

    // CR 614.1c (enters with counters, only when the condition holds as it would enter), 202.3 (mana value), 603.2
    @Test
    fun ascendantPackleader() = card(
        name = "Moonrise Packleader", cost = "{G}", type = "Creature — Wolf", pt = "2/1",
        text = """
            This creature enters with a +1/+1 counter on it if you control a permanent with mana value 4 or greater.
            Whenever you cast a spell with mana value 4 or greater, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(1)))),
            condition = Condition.Exists(and(permanentFilter, manaValue(Comparison.AtLeast, 4), you), atLeast = 1)),
        triggered(youCast(and(spellFilter, manaValue(Comparison.AtLeast, 4))), counters(Objects.Self, 1)),
    )

    // CR 614.1c, 614.12 (a land that enters tapped unless it sees two other lands), 305.6 (mana abilities)
    private fun checkLand(name: String, a: String, b: String) = card(
        name = name, cost = null, type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {$a} or {$b}.
        """,
    ) parsesTo abilities(entersTappedUnlessTwoOtherLands, addEither(a, b))

    @Test fun deathcapGlade() = checkLand("Mirewood Glade", "B", "G")
    @Test fun dreamrootCascade() = checkLand("Mossdream Cascade", "G", "U")
    @Test fun shatteredSanctum() = checkLand("Splintered Cloister", "W", "B")
    @Test fun stormcarvedCoast() = checkLand("Galecut Shore", "U", "R")
    @Test fun sundownPass() = checkLand("Embertwilight Pass", "R", "W")

    // CR 614.1a, 614.11 (a replaced draw), 406.1 (exile), 611.2a (may play those cards this turn)
    @Test
    fun eruthTormentedProphet() = card(
        name = "Ervan, Haunted Seer", cost = "{1}{U}{R}", type = "Legendary Creature — Human Wizard", pt = "2/4",
        text = "If you would draw a card, exile the top two cards of your library instead. You may play those cards this turn.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = Instructions(steps = listOf(
            doing(Effect.Move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)))))),
    )

    // CR 614.1a (life gain replaced with more), 119.1; CR 702.15 (lifelink granted until end of turn)
    @Test
    fun heronOfHope() = card(
        name = "Heron of Dawnlight", cost = "{3}{W}", type = "Creature — Bird", pt = "2/3",
        text = """
            Flying
            If you would gain life, you gain that much life plus 1 instead.
            {1}{W}: This creature gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You), modify = listOf(Modification.AddAmount(n(1))))),
        activated(cost(mana("{1}{W}")), gains(Objects.Self, Keyword.Lifelink)),
    )

    // CR 614.1c: a creature that enters tapped
    @Test
    fun unhallowedPhalanx() = card(
        name = "Gravebound Phalanx", cost = "{4}{B}", type = "Creature — Zombie Soldier", pt = "1/13",
        text = "This creature enters tapped.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
    )

    // CR 701.20a: reveal, land cards to hand and the rest to the graveyard
    @Test
    fun mulch() = card(
        name = "Rootmould Harvest", cost = "{1}{G}", type = "Sorcery",
        text = "Reveal the top four cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(4), fromTop = true), bind = "seen")),
            doing(Effect.Move(Objects.Where(Objects.Bound("seen"), land), Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Where(Objects.Bound("seen"), land)), Destination(Zone.Graveyard))),
        ),
    )

    // CR 701.21a (sacrifice), 608.2c ("if you do": only when a creature was sacrificed), 707.2 (a copy token of this creature)
    @Test
    fun dreadfeastDemon() = card(
        name = "Gorefeast Fiend", cost = "{5}{B}{B}", type = "Creature — Demon", pt = "6/6",
        text = """
            Flying
            At the beginning of your end step, sacrifice a non-Demon creature. If you do, create a token that's a copy of this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(yourEndStep,
            Step.Choose(Choice.Objects(and(not(sub("Demon")), creature, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"), postBind = "sacrificed")),
            Step.If(Condition.Compare(Value.Count(Objects.Bound("sacrificed")), Comparison.AtLeast, n(1)),
                then = listOf(doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You))))),
    )

    // CR 701.23 (search, then shuffle), 700.4 (dies), 701.20 (reveal)
    @Test
    fun wretchedThrong() = card(
        name = "Sorrowing Throng", cost = "{1}{U}", type = "Creature — Zombie Horror", pt = "2/1",
        text = "When this creature dies, you may search your library for a card named Sorrowing Throng, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        triggered(diesSelf, Step.May(listOf(search(ObjectFilter.Named("Sorrowing Throng"), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            player = Players.You)),
    )

    // CR 604.3 (a characteristic-defining ability), 608.2c (repeat): put a land from hand tapped, draw, and do it again
    @Test
    fun cultivatorColossus() = card(
        name = "Orchard Colossus", cost = "{4}{G}{G}{G}", type = "Creature — Plant Beast", pt = "*/*",
        text = """
            Trample
            Orchard Colossus's power and toughness are each equal to the number of lands you control.
            When this creature enters, you may put a land card from your hand onto the battlefield tapped. If you do, draw a card and repeat this process.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you))), characteristicDefining = true),
        triggered(entersSelf, Step.RepeatWhile(listOf(
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            Step.If(Condition.NonEmpty("chosen"), then = listOf(draw(1)))), Condition.NonEmpty("chosen"))),
    )

    // CR 118.12 (counter unless its controller pays), 701.6a, 614.1a: only the spell countered this way is exiled instead (audit: the
    // replacement belongs to this resolution's countering, see the scenario test for a paid spell)
    @Test
    fun syncopate() = card(
        name = "Fadeout", cost = "{X}{U}", type = "Instant",
        text = "Counter target spell unless its controller pays {X}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.",
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Announced("X")))), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", spellFilter)),
            replacements = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
            announced = listOf("X"),
        ),
    )
}
