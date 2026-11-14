package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MKM chunk 11: more cards with disguise (CR 702.168) and the abilities that go with it. */
class MkmChunk11DisguiseTwoTest {
    // CR 702.168a, 301.5, 701.3: the turned-face-up trigger attaches the Equipment itself to a target creature
    @Test
    fun veiledCudgel() = card(
        name = "Veiled Cudgel",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+0.
            Disguise {2}{R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this Equipment is turned face up, attach it to target creature you control.
            Equip {1}{R}
        """,
    ) parsesTo abilities(
        static(pt(3, 0), affects = Objects.AttachedTo(Objects.Self)),
        disguise("{2}{R}"),
        triggered(turnedFaceUpSelf, Step.Do(Effect.Attach(Objects.Self, toObjects(target("creature")))),
            targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Equip(manaCost("{1}{R}")),
    )

    @Test
    fun windowfallWraith() = card(
        name = "Windowfall Wraith",
        cost = "{4}{W}{W}",
        type = "Creature — Spirit",
        pt = "4/3",
        text = """
            Flying
            Disguise {4}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(Keyword.Flying, disguise("{4}{W}"))

    // CR 111.10 (token), 110.5b (enters tapped)
    @Test
    fun leashHandler() = card(
        name = "Leash Handler",
        cost = "{R}{W}",
        type = "Creature — Human Citizen",
        pt = "3/1",
        text = """
            Vigilance
            Disguise {R/W}{R/W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, create two tapped 1/1 white Dog creature tokens.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        disguise("{R/W}{R/W}"),
        triggered(turnedFaceUpSelf, Step.Do(Effect.CreateToken(creatureToken("Dog", listOf(arcanum.api.Color.White), 1, 1), n(2), Players.You, tapped = true))),
    )

    @Test
    fun coreOfAntiquity() = card(
        name = "Antique Essence Golem",
        cost = "{3}{W}{W}",
        type = "Artifact Creature — Golem",
        pt = "1/10",
        text = """
            Disguise {2}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, creatures you control gain hexproof until end of turn. Untap them.
        """,
    ) parsesTo abilities(
        disguise("{2}{W}"),
        triggered(turnedFaceUpSelf,
            gains(Objects.Each(creatureYouControl), Keyword.Hexproof),
            untapIt(Objects.Each(creatureYouControl))),
    )

    // CR 509.1b: a restriction on which creatures may block it
    @Test
    fun exitClerk() = card(
        name = "Exit Clerk",
        cost = "{1}{U}",
        type = "Creature — Human Detective",
        pt = "2/1",
        text = """
            This creature can't be blocked by creatures with power 3 or greater.
            Disguise {1}{U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, return another target creature to its owner's hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtLeast, 3)))),
        disguise("{1}{U}"),
        triggered(turnedFaceUpSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(another, creature)))),
    )

    // CR 701.20 (look), 700.2 (put one, the other)
    @Test
    fun pixieSnoop() = card(
        name = "Pixie Snoop",
        cost = "{1}{U}{B}",
        type = "Creature — Faerie Detective",
        pt = "1/4",
        text = """
            Flying
            Disguise {1}{U/B}{U/B} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, look at the top two cards of your library. Put one into your hand and the other into your graveyard.
        """,
    ) parsesTo abilities(Keyword.Flying, disguise("{1}{U/B}{U/B}"),
        triggered(turnedFaceUpSelf,
            doing(Effect.LookAt(Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Zone.Graveyard)),
    )

    @Test
    fun forumTabby() = card(
        name = "Forum Tabby",
        cost = "{W}",
        type = "Creature — Cat",
        pt = "1/1",
        text = """
            Disguise {1}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, return another target permanent you control to its owner's hand and put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        disguise("{1}{W}"),
        triggered(turnedFaceUpSelf, move(target("permanent"), Zone.Hand), counters(Objects.Self),
            targets = listOf(slot("permanent", and(another, permanentFilter, you)))),
    )

    // CR 701.26: tap or untap, "you may" makes the choice optional
    @Test
    fun basaltWitness() = card(
        name = "Basalt Witness",
        cost = "{2}{W}{U}",
        type = "Artifact Creature — Gargoyle Detective",
        pt = "3/2",
        text = """
            Flying, vigilance
            Disguise {W/U}{W/U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, you may tap or untap target creature.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Vigilance, disguise("{W/U}{W/U}"),
        triggered(turnedFaceUpSelf,
            Step.May(listOf(Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                ifThen(Condition.Chose("tapOrUntap", "tap"), tapIt(target("creature")), otherwise = listOf(untapIt(target("creature"))))), Players.You),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun greenbeltZealot() = card(
        name = "Greenbelt Zealot",
        cost = "{3}{G}",
        type = "Creature — Centaur Citizen",
        pt = "4/4",
        text = """
            Disguise {5}{G}{G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, put a +1/+1 counter on each creature you control. Creatures you control gain trample until end of turn.
        """,
    ) parsesTo abilities(
        disguise("{5}{G}{G}"),
        triggered(turnedFaceUpSelf,
            counters(Objects.Each(creatureYouControl)),
            gains(Objects.Each(creatureYouControl), Keyword.Trample)),
    )

    // CR 701.16a (investigate), 510.2 (combat damage), 611.2a (until end of turn)
    @Test
    fun mistwayLookout() = card(
        name = "Mistway Lookout",
        cost = "{U}",
        type = "Creature — Merfolk Detective",
        pt = "1/1",
        text = """
            Flying
            Disguise {1}{U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, until end of turn, whenever a creature you control deals combat damage to a player, investigate.
        """,
    ) parsesTo abilities(Keyword.Flying, disguise("{1}{U}"),
        triggered(turnedFaceUpSelf, doing(Effect.CreateDelayedTrigger(
            on(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)),
            arcanum.api.Instructions(steps = listOf(investigate())), duration = Duration.EndOfTurn, repeats = true))),
    )

    // CR 700.4: dies
    @Test
    fun galleryNightguard() = card(
        name = "Gallery Nightguard",
        cost = "{3}{W}",
        type = "Creature — Centaur Soldier",
        pt = "3/2",
        text = """
            When this creature dies, create a 2/2 white and blue Detective creature token.
            Disguise {1}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        triggered(diesSelf, create(creatureToken("Detective", listOf(arcanum.api.Color.White, arcanum.api.Color.Blue), 2, 2))),
        disguise("{1}{W}"),
    )

    // CR 701.23: search for a land card with a basic land type, reveal, hand, shuffle
    @Test
    fun timidGardener() = card(
        name = "Timid Gardener",
        cost = "{1}{G}",
        type = "Creature — Dryad",
        pt = "2/2",
        text = """
            Disguise {G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, search your library for a land card with a basic land type, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(disguise("{G}"),
        triggered(turnedFaceUpSelf,
            search(and(land, or(*listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map { sub(it) }.toTypedArray())), reveal = true),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle),
    )

    @Test
    fun nightsipperMoroii() = card(
        name = "Nightsipper Moroii",
        cost = "{3}{B}",
        type = "Creature — Vampire",
        pt = "4/2",
        text = """
            Flying
            When this creature enters, you lose 3 life.
            Disguise {B}{B} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, loseLife(3)), disguise("{B}{B}"))

    @Test
    fun riftburstBrute() = card(
        name = "Riftburst Brute",
        cost = "{5}{R}{G}",
        type = "Creature — Hellion",
        pt = "6/7",
        text = """
            Reach
            Disguise {4}{R/G}{R/G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(Keyword.Reach, disguise("{4}{R/G}{R/G}"))

    @Test
    fun sanguineSteward() = card(
        name = "Sanguine Steward",
        cost = "{1}{W}{B}",
        type = "Creature — Vampire Cleric",
        pt = "2/1",
        text = """
            Flying, lifelink
            Disguise {W/B}{W/B} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, another target creature you control gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Lifelink, disguise("{W/B}{W/B}"),
        triggered(turnedFaceUpSelf, gains(target("creature"), Keyword.Lifelink),
            targets = listOf(slot("creature", and(another, creatureYouControl)))),
    )

    // CR 700.4: dies; the creature deals the damage (CR 120.3)
    @Test
    fun shadyBroker() = card(
        name = "Shady Broker",
        cost = "{3}{B}{R}",
        type = "Creature — Ogre Rogue",
        pt = "4/2",
        text = """
            When this creature dies, it deals 2 damage to any target.
            Disguise {2}{B/R}{B/R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        triggered(diesSelf, damage(2, toAny), targets = listOf(anyTarget)),
        disguise("{2}{B/R}{B/R}"),
    )

    // CR 701.16a, 510.2
    @Test
    fun undercoverCaiman() = card(
        name = "Undercover Caiman",
        cost = "{4}{G}{U}",
        type = "Creature — Elf Crocodile Detective",
        pt = "5/5",
        text = """
            Whenever this creature deals combat damage to a player, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Disguise {3}{G/U}{G/U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(triggered(combatDamageToAPlayer, investigate()), disguise("{3}{G/U}{G/U}"))

    // CR 701.8: destroy an artifact or enchantment an opponent controls
    @Test
    fun vengefulSapling() = card(
        name = "Vengeful Sapling",
        cost = "{4}{G}",
        type = "Creature — Plant Elemental",
        pt = "5/5",
        text = """
            Disguise {5}{G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, destroy target artifact or enchantment an opponent controls.
        """,
    ) parsesTo abilities(
        disguise("{5}{G}"),
        triggered(turnedFaceUpSelf, destroy(target("permanent")),
            targets = listOf(slot("permanent", and(or(artifact, enchantment), opponents)))),
    )
}
