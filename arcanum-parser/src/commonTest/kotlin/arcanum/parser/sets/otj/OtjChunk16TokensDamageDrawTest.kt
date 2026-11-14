package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 16: create-token, damage, destroy, draw, gained abilities, gain-life, keyword-action and look-at-the-top cards.
 */
class OtjChunk16TokensDamageDrawTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val mount = sub("Mount")
    private val vehicle = sub("Vehicle")

    // CR 603.2c (one or more), 603.2h (once each turn), 111.1; CR 701.21a (sacrifice) as a cost, 121.1
    @Test
    fun duchessGreywater() = card(
        name = "Duchess Valmar Greywater", cost = "{2}{W}{B}", type = "Legendary Creature — Vampire Noble", pt = "3/4",
        text = """
            Whenever one or more tokens you control enter, create a 1/1 black Vampire Rogue creature token with lifelink. This ability triggers only once each turn.
            {1}{B}, Sacrifice another creature or artifact: Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.ZoneChange(and(token, you), to = Zone.Battlefield), oneOrMore = true),
            Instructions(steps = listOf(create(creatureToken("Vampire Rogue", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Lifelink))))),
            perTurn = 1),
        activated(cost(mana("{1}{B}"), sacrificeA(and(another, or(creature, artifact)))), draw(1)),
    )

    // CR 508.1 (attacks), 111.1, 208.1 (power)
    @Test
    fun stormscaleConjurer() = card(
        name = "Stormscale Conjurer", cost = "{2}{R}", type = "Creature — Human Warlock", pt = "3/3",
        text = "Whenever this creature attacks, create a 3/1 red Dinosaur creature token if you control a creature with power 4 or greater.",
    ) parsesTo abilities(
        triggered(attacksSelf, ifThen(Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1),
            create(creatureToken("Dinosaur", listOf(Color.Red), 3, 1)))),
    )

    // CR 120.3 (damage), 603.2d (triggers an additional time)
    @Test
    fun martaJoinsForces() = card(
        name = "Marta Joins Forces", cost = "{1}{R}{G}{W}", type = "Legendary Enchantment",
        text = """
            When Marta Joins Forces enters, it deals 5 damage to target creature or planeswalker an opponent controls.
            If a triggered ability of a legendary creature you control triggers, that ability triggers an additional time.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(5, toObjects(target("permanent"))), targets = listOf(slot("permanent", and(or(creature, planeswalker), opponents)))),
        static(Mechanic.TriggersAdditionalTime(event = null, sources = and(legendary, creature, you))),
    )

    // CR 602.1 (activated), 120.3
    @Test
    fun sharpshotDuelist() = card(
        name = "Sharpshot Duelist", cost = "{1}{R}", type = "Creature — Human Assassin", pt = "1/3",
        text = """
            Reach
            {1}, {T}: This creature deals 1 damage to target opponent.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(mana("{1}"), tap), damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
    )

    // CR 601.2a, 117.1 (cast another spell this turn)
    @Test
    fun quickCadence() = card(
        name = "Quick Cadence", cost = "{U}{R}", type = "Instant",
        text = "Quick Cadence deals 2 damage to any target. If you've cast another spell this turn, draw a card.",
    ) parsesTo abilities(
        spell(damage(2, toAny), ifThen(Condition.HappenedThisTurn(EventPattern.Cast(not(ObjectFilter.Is(Objects.Self)), PlayerFilter.You), atLeast = 1, measure = Measure.Events), draw(1)), targets = listOf(anyTarget)),
    )

    // CR 701.8a (destroy), 111.2 (its controller creates the token)
    @Test
    fun bovineRemedy() = card(
        name = "Bovine Remedy", cost = "{1}{W}", type = "Instant",
        text = "Destroy target artifact or creature. Its controller creates a 2/2 white Ox creature token.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")),
            doing(Effect.CreateToken(creatureToken("Ox", listOf(Color.White), 2, 2), n(1), Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 701.8a, 119.1 (gain life)
    @Test
    fun lullabyOfAshes() = card(
        name = "Lullaby of Ashes", cost = "{1}{W}", type = "Sorcery",
        text = "Destroy target tapped creature. You gain 2 life.",
    ) parsesTo abilities(
        spell(destroy(target("creature")), gainLife(2), targets = listOf(slot("creature", and(tapped, creature)))),
    )

    // CR 120.1 (dealt damage this turn), 701.8a
    @Test
    fun perchAssassin() = card(
        name = "Perch Assassin", cost = "{3}{B}", type = "Creature — Vampire Assassin", pt = "2/2",
        text = """
            Flash
            Flying, lifelink
            When this creature enters, destroy target creature an opponent controls that was dealt damage this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash, Keyword.Flying, Keyword.Lifelink,
        triggered(entersSelf, destroy(target("creature")), targets = listOf(slot("creature", and(creature, opponents,
            ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Object))))),
    )

    // CR 121.1 (draw), 119.3 (lose life), 115.1 (target player)
    @Test
    fun vaultPilferer() = card(
        name = "Vault Pilferer", cost = "{2}{B}", type = "Creature — Human Rogue", pt = "3/1",
        text = "When this creature enters, target player draws a card and loses 1 life.",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1, Players.Target("player")), loseLife(1, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 702.36a (flash), 604.1 (static), 205.3i
    @Test
    fun titanicSandwurm() = card(
        name = "Titanic Sandwurm", cost = "{2}{G}{G}", type = "Creature — Wurm", pt = "6/5",
        text = """
            Titanic Sandwurm has flash as long as you control a Desert.
            Trample
            {1}{G}, Exile this card from your graveyard: Search your library for a Desert card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flash), condition = Condition.Exists(and(sub("Desert"), you), 1)),
        Keyword.Trample,
        activated(cost(mana("{1}{G}"), CostPart.Exile(CostObjects.Self, Zone.Graveyard)),
            search(sub("Desert")), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 603.2 (tap trigger), 111.10 (copy of token), 613.1f; CR 701.6a
    @Test
    fun gerdaMirrorOfDunes() = card(
        name = "Gerda, Mirror of Dunes", cost = "{R}{G}{W}", type = "Legendary Creature — Human Shaman", pt = "3/3",
        text = """
            Haste
            Nontoken creatures you control have "{T}: Create a token that's a copy of target token you control that entered this turn."
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        static(has(activated(cost(tap), create(TokenSpec.CopyOf(target("token"))),
            targets = listOf(slot("token", and(token, you, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))))))),
            affects = Objects.Each(and(not(token), creature, you))),
    )

    // CR 611.3 (static during your turn), 702.11 (hexproof), 508.1
    @Test
    fun mirabelHerdTender() = card(
        name = "Mirabel, Herd Tender", cost = "{G}{W}", type = "Legendary Creature — Human Druid", pt = "3/2",
        text = """
            During your turn, Mounts and Vehicles you control have hexproof.
            Whenever a Mount or Vehicle you control attacks, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), affects = Objects.Each(and(or(mount, vehicle), you)), condition = Condition.YourTurn),
        triggered(on(EventPattern.Attack(and(or(mount, vehicle), you))), counters(Objects.EventObject(EventRole.Object))),
    )

    // CR 701.26a (tap), 119.1, 121.1
    @Test
    fun wynnDukeHillHero() = card(
        name = "Wynn Duke, Hill Hero", cost = "{1}{G}{W}", type = "Legendary Creature — Human Ranger", pt = "4/2",
        text = """
            Vigilance
            Whenever Wynn Duke becomes tapped, you gain 1 life and draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Tap(objects = ObjectFilter.Is(Objects.Self))), gainLife(1), draw(1)),
    )

    // CR 701.16a (investigate), 603.2 with "your second spell each turn"
    @Test
    fun malcolmTheEars() = card(
        name = "Malcolm, the Ears", cost = "{U}{R}", type = "Legendary Creature — Siren Pirate", pt = "2/2",
        text = """
            Flying, haste
            Whenever you cast your second spell each turn, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Haste,
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
            doing(Effect.Action(KeywordAction.Investigate(n(1))))),
    )

    // CR 701.20 (look/reveal), 401.4 (random order on the bottom)
    @Test
    fun frontierPathfinder() = card(
        name = "Frontier Pathfinder", cost = "{1}{W}", type = "Creature — Human Scout", pt = "2/1",
        text = "When this creature enters, look at the top five cards of your library. You may reveal a Mount creature card or a Plains card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(and(mount, creature), sub("Plains")), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )
}
