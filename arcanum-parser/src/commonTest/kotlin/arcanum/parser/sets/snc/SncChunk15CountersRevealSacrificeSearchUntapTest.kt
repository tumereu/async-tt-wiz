package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** Streets of New Capenna chunk 15: put counters, reveal, sacrifice, search, untap. */
class SncChunk15CountersRevealSacrificeSearchUntapTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val opponent = Players.Target("opponent")
    private val ownedByOpponentInHand = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(opponent))))

    // CR 701.21 (sacrifice), 122.1 (counters), 702.110 (menace); the cost's sacrificed permanent is one you control (CR 701.21a)
    @Test
    fun corpseDropper() = card(
        name = "Corpse Dropper",
        cost = "{B}{R}",
        type = "Creature — Devil Warrior",
        pt = "2/2",
        text = """
            Whenever you sacrifice another creature, put a +1/+1 counter on this creature.
            {B}{R}, Sacrifice another creature: This creature gains menace until end of turn. (It can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Sacrifice(and(another, creature, you))), counters(Objects.Self)),
        activated(cost(mana("{B}{R}"), sacrificeA(and(another, creature))), gains(Objects.Self, Keyword.Menace)),
    )

    // CR 700.4 (dies), 122.1c (shield counter)
    @Test
    fun brokersRecruit() = card(
        name = "Ledger Recruit",
        cost = "{1}{U}",
        type = "Creature — Human Soldier",
        pt = "2/1",
        text = """
            When this creature dies, put a shield counter on target creature you control. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
        """,
    ) parsesTo abilities(
        triggered(diesSelf, counters(target("creature"), 1, CounterKind.Shield), targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 119.3 (gain life), 119.4 (lose life), 603.4: each trigger works only during your turn
    @Test
    fun crimsonScrivener() = card(
        name = "Crimson Scrivener",
        cost = "{4}{B}",
        type = "Creature — Vampire Warlock",
        pt = "2/2",
        text = """
            Flying
            Whenever you gain life during your turn, put a +1/+1 counter on this creature.
            Whenever you lose life during your turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(on(EventPattern.GainLife(PlayerFilter.You)), Instructions(steps = listOf(counters(Objects.Self))), triggersOnlyIf = Condition.YourTurn),
        Ability.Triggered(on(EventPattern.LoseLife(PlayerFilter.You)), Instructions(steps = listOf(counters(Objects.Self))), triggersOnlyIf = Condition.YourTurn),
    )

    // CR 700.2 (modes), 701.20 (reveal), 701.9 (discard), 701.21 (sacrifice), 101.4
    @Test
    fun withdrawTheTruth() = card(
        name = "Withdraw the Truth",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target opponent reveals their hand. You may choose a creature, enchantment, or planeswalker card from it. That player discards that card.
            • Target opponent sacrifices an enchantment of their choice.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(
                doing(Effect.Reveal(ownedByOpponentInHand, bind = "revealed")),
                Step.Choose(Choice.Objects(or(creature, enchantment, planeswalker), n(0), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(opponent, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetOpponent)),
            mode(
                Step.Choose(Choice.Objects(and(enchantment, ObjectFilter.ControlledBy(PlayerFilter.Is(opponent))), n(1), n(1)), bind = "sacrificed", chooser = opponent),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                targets = listOf(targetOpponent)),
        ))),
    )

    // CR 701.20 (reveal), 400.3, 406 (exile)
    @Test
    fun ledgerLarcenist() = card(
        name = "Ledger Larcenist",
        cost = "{W}{U}{B}",
        type = "Creature — Bird Rogue",
        pt = "2/1",
        text = """
            Flying
            When this creature enters, target opponent reveals their hand. You choose an artifact, instant, or sorcery card from it and exile that card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(entersSelf, Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                doing(Effect.Reveal(ownedByOpponentInHand, bind = "revealed")),
                Step.Choose(Choice.Objects(or(artifact, instant, sorcery), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
            ))),
    )

    // CR 603.12 (the reflexive trigger), 701.21 (sacrifice), 701.23 (search), 614.1 (tapped), 119.3 (gain life)
    private fun fetchLand(name: String, text: String, vararg types: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = text,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Sacrifice(Objects.Self, by = Players.You)), reflexive(
            search(and(ObjectFilter.Super(Supertype.Basic), or(*types.map(::sub).toTypedArray()))),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
            gainLife(1),
        )),
    )

    @Test
    fun forestPlainsIslandLand() = fetchLand("Ledger Hideout",
        "When this land enters, sacrifice it. When you do, search your library for a basic Forest, Plains, or Island card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        "Forest", "Plains", "Island")

    @Test
    fun mountainForestPlainsLand() = fetchLand("Ledger Courtyard",
        "When this land enters, sacrifice it. When you do, search your library for a basic Mountain, Forest, or Plains card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        "Mountain", "Forest", "Plains")

    @Test
    fun islandSwampMountainLand() = fetchLand("Ledger Theater",
        "When this land enters, sacrifice it. When you do, search your library for a basic Island, Swamp, or Mountain card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        "Island", "Swamp", "Mountain")

    @Test
    fun plainsIslandSwampLand() = fetchLand("Ledger Storefront",
        "When this land enters, sacrifice it. When you do, search your library for a basic Plains, Island, or Swamp card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        "Plains", "Island", "Swamp")

    @Test
    fun swampMountainForestLand() = fetchLand("Ledger Overlook",
        "When this land enters, sacrifice it. When you do, search your library for a basic Swamp, Mountain, or Forest card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        "Swamp", "Mountain", "Forest")

    // CR 701.23 (search), 701.20 (reveal), 602.2 (two abilities of the artifact that each sacrifice it)
    @Test
    fun grimParcel() = card(
        name = "Grim Parcel",
        cost = "{1}",
        type = "Artifact",
        text = """
            {2}, {T}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            {5}, {T}, Sacrifice this artifact: It deals 4 damage to target creature.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, sacrificeSelf), search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        activated(cost(mana("{5}"), tap, sacrificeSelf), damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 701.23 (search), 202.3 (mana value, X is 0 on the battlefield but the trigger's X is the cast's, CR 107.3f), 603.4 (if you cast it)
    @Test
    fun roccoLikeCaterer() = card(
        name = "Vasco, Ledger Caterer",
        cost = "{X}{R}{G}{W}",
        type = "Legendary Creature — Elf Druid",
        pt = "3/1",
        text = "When Vasco enters, if you cast it, you may search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(
            search(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.CastX(Objects.Self)))),
            move(Objects.Bound("found"), Zone.Battlefield), shuffle), player = Players.You),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    // CR 506.5 area: "can't attack or block unless" (CR 508.1c, 509.1b), 701.23 (search), 702.20 (vigilance)
    @Test
    fun lushStomper() = card(
        name = "Lush Stomper",
        cost = "{1}{G}{G}",
        type = "Creature — Plant Dinosaur",
        pt = "4/4",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            When this creature enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            This creature can't attack or block unless you control seven or more lands.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        whenThisEnters(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any),
            condition = Condition.Not(Condition.Exists(and(land, you), atLeast = 7))),
    )

    // CR 701.26 (untap), 508.1 (attacks)
    @Test
    fun parkGardener() = card(
        name = "Park Gardener",
        cost = "{1}{G}",
        type = "Creature — Human Citizen",
        pt = "2/2",
        text = "Whenever this creature attacks, untap target creature or land.",
    ) parsesTo abilities(
        triggered(attacksSelf, untapIt(target("permanent")), targets = listOf(slot("permanent", or(creature, land)))),
    )
}
