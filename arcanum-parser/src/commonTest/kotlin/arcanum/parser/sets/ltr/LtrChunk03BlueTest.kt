package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 03: blue cards, first part. A cost reduction for two legendary creatures (CR 601.2f), shuffling
 * target cards into a library and adding mana in a chapter (CR 714.2b), an untap replaced by removing a counter (CR 614.1a,
 * 502.3), a modal trigger on becoming blocked giving control of a Horse (CR 700.2, 509.3c), flash for every spell this turn,
 * "whenever you scry" working from the graveyard (CR 701.22), base power and toughness until end of turn (CR 613.4b), the n-th
 * resolution of an ability this turn (CR 113.7), countering a legendary spell, moving counters (CR 122.5), untapping
 * targets and "second from the top" (CR 401.4).
 */
class LtrChunk03BlueTest {
    // CR 601.2f: two or more legendary creatures
    @Test
    fun evenstarsGift() = card(
        name = "Evenstar's Gift",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            This spell costs {1} less to cast if you control two or more legendary creatures.
            Scry 2, then draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(legendary, creature, you), atLeast = 2)),
        spell(scry(2), draw(2)),
    )

    // CR 714.2b, 701.24: shuffled in; mana added by a chapter ability
    @Test
    fun theWashingSong() = card(
        name = "The Washing Song",
        cost = "{3}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Draw two cards, then discard a card.
            III — Shuffle any number of target cards from your graveyard into your library. Add {U}{U}.
        """,
    ) parsesTo abilities(
        chapters(listOf(1, 2), draw(2), discard(1)),
        chapter(3, move(target("cards"), Zone.Library), shuffle,
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}{U}")!!)), Players.You)),
            targets = listOf(slot("cards", inYourGraveyard(cardFilter), min = 0, max = null))),
    )

    // CR 614.1a, 502.3: the untap replaced by removing a counter; without one it stays tapped
    @Test
    fun bewitchingHerbcraft() = card(
        name = "Bewitching Herbcraft",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature has "If this creature would untap during your untap step, remove a +1/+1 counter from it instead. If you do, untap it." (Otherwise, it doesn't untap.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(Objects.AttachedTo(Objects.Self))),
        static(has(Ability.Static(listOf(Mechanic.UntapReplacedByRemovingCounter(p1p1)), affects = Affected.Objects(Objects.Self))),
            affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 509.3c, 700.2: a modal trigger; "If they do": the Horse is now theirs
    @Test
    fun hobFerrowInnSwindler() = card(
        name = "Hob Ferrow, Inn Swindler",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Rogue",
        text = """
            Whenever Hob Ferrow becomes blocked, choose one —
            • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            • Target opponent gains control of target Horse you control. If they do, remove Hob Ferrow from combat and create three Treasure tokens.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Ability.Triggered(on(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)), Instructions(modes = modes(
            mode(createTreasure()),
            mode(doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Target("opponent"))), Affected.Objects(target("Horse")), Duration.Indefinitely)),
                Step.If(Condition.ObjectMatches(target("Horse"), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), then = listOf(
                    doing(Effect.RemoveFromCombat(Objects.Self)), createTreasure(n(3)))),
                targets = listOf(targetOpponent, slot("Horse", and(sub("Horse"), you))))))),
    )

    // CR 701.54a
    @Test
    fun partyVanishing() = card(
        name = "Party Vanishing",
        cost = "{U}",
        type = "Sorcery",
        text = "Draw a card. The Ring tempts you.",
    ) parsesTo abilities(
        spell(draw(1), ringTempts),
    )

    // CR 702.8a: every spell this turn as though it had flash
    @Test
    fun carriedOnTheBreeze() = card(
        name = "Carried on the Breeze",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            You may cast spells this turn as though they had flash.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.CastAsThoughFlash(spellFilter)), Affected.Players(Players.You), Duration.EndOfTurn)), draw(1)),
    )

    @Test
    fun captainOfTheCorsairs() = card(
        name = "Captain of the Corsairs",
        cost = "{2}{U}",
        type = "Creature — Human Pirate",
        text = "{1}, {T}: Draw a card, then discard a card.",
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), draw(1), discard(1)),
    )

    // CR 701.22, 113.6: a triggered ability that works in the graveyard
    @Test
    fun councilsMusing() = card(
        name = "Council's Musing",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Draw a card.
            Whenever you scry, if you control an Island, you may exile this card from your graveyard. If you do, draw a card.
        """,
    ) parsesTo abilities(
        spell(draw(1)),
        triggered(youScry,
            Step.Pay(cost(CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1))),
            interveningIf = Condition.Exists(and(sub("Island"), you), atLeast = 1), functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 701.47a
    @Test
    fun misleadTheHerald() = card(
        name = "Mislead the Herald",
        cost = "{U}",
        type = "Instant",
        text = """
            Target creature gets -3/-0 until end of turn.
            Amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -3, 0), amassOrcs(1), targets = listOf(slot("creature", creature))),
    )

    // CR 613.4b: base power and toughness 5/5 until end of turn
    @Test
    fun terribleAsTheTempest() = card(
        name = "Terrible as the Tempest",
        cost = "{2}{U}",
        type = "Instant",
        text = "Target creature has base power and toughness 5/5 until end of turn. The Ring tempts you.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(5), n(5))), ringTempts, targets = listOf(slot("creature", creature))),
    )

    // CR 113.7: the second time this ability resolved this turn
    @Test
    fun elrohirLordOfTheValley() = card(
        name = "Elrohir, Lord of the Valley",
        cost = "{2}{U}",
        type = "Legendary Creature — Elf Noble",
        text = "Whenever Elrohir or another creature you control enters, scry 1. If this is the second time this ability has resolved this turn, the Ring tempts you.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, creature, you))), scry(1),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2)), then = listOf(ringTempts))),
    )

    // CR 702.8a; CR 701.54a, d
    @Test
    fun tharkunFriendOfTheHills() = card(
        name = "Tharkun, Friend of the Hills",
        cost = "{3}{U}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Flash
            You may cast sorcery spells as though they had flash.
            Whenever the Ring tempts you, if you chose a creature other than Tharkun as your Ring-bearer, draw a card.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Flash,
        playerStatic(Mechanic.CastAsThoughFlash(and(sorcery, spellFilter))),
        triggered(ringTemptsYou, draw(1), interveningIf = choseAnotherBearer),
    )

    // CR 701.54a: tempted only if the countered spell was legendary
    @Test
    fun gloriousGust() = card(
        name = "Glorious Gust",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target creature spell. If it was a legendary spell, the Ring tempts you.",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), Step.If(Condition.ObjectMatches(target("spell"), legendary), then = listOf(ringTempts)),
            targets = listOf(slot("spell", and(creature, spellFilter)))),
    )

    // CR 122.5: counters moved between permanents
    @Test
    fun lilywenRiverDaughter() = card(
        name = "Lilywen, River-Daughter",
        cost = "{1}{U}",
        type = "Legendary Creature — Nymph",
        text = """
            {T}: Move a counter of each kind not on Lilywen from another target permanent you control onto Lilywen.
            {U}, {T}: Move one or more counters from Lilywen onto another target permanent you control. If you do, draw a card.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.MoveCounters(target("permanent"), Objects.Self, null, n(1), exceptKindsOn = Objects.Self)),
            targets = listOf(slot("permanent", and(another, permanentFilter, you)))),
        activated(cost(mana("{U}"), tap),
            doing(Effect.MoveCounters(Objects.Self, target("permanent"), null, n(1), chosen = true, bind = "moved")),
            Step.If(Condition.Compare(Value.Bound("moved"), Comparison.AtLeast, n(1)), then = listOf(draw(1))),
            targets = listOf(slot("permanent", and(another, permanentFilter, you)))),
    )

    @Test
    fun harborNavigatorOfTheWest() = card(
        name = "Harbor Navigator of the West",
        cost = "{2}{U}",
        type = "Creature — Elf Pilot",
        text = """
            Flash
            When this creature enters, scry 1.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, scry(1)),
    )

    @Test
    fun elvenRopeKnots() = card(
        name = "Elven Rope Knots",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Tap target creature. Scry 1.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(tapIt(target("creature")), scry(1), draw(1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun horsesOfTheLoudwater() = card(
        name = "Horses of the Loudwater",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = "Return up to two target creatures to their owners' hands. Scry 1. The Ring tempts you.",
    ) parsesTo abilities(
        spell(move(target("creatures"), Zone.Hand), scry(1), ringTempts, targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 115.1: two other target legendary creatures
    @Test
    fun berethielOfTheHealingHall() = card(
        name = "Berethiel of the Healing Hall",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Cleric",
        text = """
            {T}: Untap another target permanent.
            {T}: Untap two other target legendary creatures.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("permanent")), targets = listOf(slot("permanent", and(another, permanentFilter)))),
        activated(cost(tap), untapIt(target("creatures")), targets = listOf(slot("creatures", and(another, legendary, creature), min = 2, max = 2))),
    )

    // CR 401.4: second from the top
    @Test
    fun isolationAtTheTower() = card(
        name = "Isolation at the Tower",
        cost = "{3}{U}",
        type = "Instant",
        text = "Put target creature into its owner's library second from the top.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Library, position = 1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun riverKingfisherOfTheSouth() = card(
        name = "River Kingfisher of the South",
        cost = "{2}{U}",
        type = "Creature — Bird",
        text = """
            Flying
            When this creature dies, draw a card.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelf, draw(1)),
    )

    @Suppress("unused") private val unused = listOf(EventRole.Object, TargetSlot("x", TargetKind.Players(PlayerFilter.Any), n(1), n(1)))
}
