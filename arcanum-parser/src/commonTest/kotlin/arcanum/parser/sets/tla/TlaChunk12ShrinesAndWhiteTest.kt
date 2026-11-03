package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 12: Shrines (legendary enchantments counting Shrines you control and triggering on another Shrine
 * entering) and white cards: keeping creatures with total power 4 or less (CR 101.4), exiling until it leaves (CR 610.3), the first
 * non-Lemur flying creature spell each of your turns (CR 601.2f), a modal leaves trigger, excess damage as life (CR 120.4a), attacking alone.
 */
class TlaChunk12ShrinesAndWhiteTest {
    private val shrines = count(and(shrine, you))
    private val anotherShrineEnters = enters(and(another, shrine, you))

    @Test
    fun southernWindShrine() = card(
        name = "Southern Wind Shrine",
        cost = "{3}{W}",
        type = "Legendary Enchantment — Shrine",
        text = """
            When Southern Wind Shrine enters, put X +1/+1 counters on each creature you control, where X is the number of Shrines you control.
            Whenever another Shrine you control enters, put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(Objects.Each(creatureYouControl), shrines)),
        triggered(anotherShrineEnters, counters(Objects.Each(creatureYouControl))),
    )

    @Test
    fun theMoonlitOasis() = card(
        name = "The Moonlit Oasis",
        cost = "{2}{U}",
        type = "Legendary Enchantment — Shrine",
        text = """
            When The Moonlit Oasis enters, draw a card for each Shrine you control.
            Whenever another Shrine you control enters, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Draw(Players.You, shrines))),
        triggered(anotherShrineEnters, draw(1)),
    )

    @Test
    fun northernWindShrine() = card(
        name = "Northern Wind Shrine",
        cost = "{B}",
        type = "Legendary Enchantment — Shrine",
        text = """
            When Northern Wind Shrine enters, each opponent loses X life and you gain X life, where X is the number of Shrines you control.
            Whenever another Shrine you control enters, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), shrines)), doing(Effect.GainLife(Players.You, shrines))),
        triggered(anotherShrineEnters, loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    @Test
    fun crescentIsleShrine() = card(
        name = "Crescent Isle Shrine",
        cost = "{3}{R}",
        type = "Legendary Enchantment — Shrine",
        text = """
            When Crescent Isle Shrine enters, for each Shrine you control, create a 1/1 red Monk creature token with prowess. (Whenever you cast a noncreature spell, it gets +1/+1 until end of turn.)
            Whenever another Shrine you control enters, create a 1/1 red Monk creature token with prowess.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Monk", listOf(Color.Red), 1, 1, abilities = listOf(Keyword.Prowess)), shrines)),
        triggered(anotherShrineEnters, create(creatureToken("Monk", listOf(Color.Red), 1, 1, abilities = listOf(Keyword.Prowess)))),
    )

    @Test
    fun reedIslePlaza() = card(
        name = "Reed Isle Plaza",
        cost = "{3}{G}",
        type = "Legendary Enchantment — Shrine",
        text = """
            When Reed Isle Plaza enters, search your library for up to X basic land cards, where X is the number of Shrines you control. Put those cards onto the battlefield tapped, then shuffle.
            Whenever another Shrine you control enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, search(basicLand, max = shrines), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        triggered(anotherShrineEnters, *rampTapped.toTypedArray()),
    )

    // CR 701.26a (becomes tapped)
    @Test
    fun gentleMender() = card(
        name = "Gentle Mender",
        cost = "{1}{W}",
        type = "Creature — Human Cleric Ally",
        text = "Whenever this creature becomes tapped, you gain 1 life and scry 1. (Look at the top card of your library. You may put it on the bottom.)",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.Self))), gainLife(1), scry(1)),
    )

    @Test
    fun inquisitiveBarnyard() = card(
        name = "Inquisitive Barnyard",
        cost = "{W}",
        type = "Creature — Boar Elk Bird Ox",
        text = """
            When this creature dies, you gain 3 life.
            {2}, Sacrifice this creature: Destroy up to one target artifact or enchantment.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(diesSelf, gainLife(3)),
        activated(cost(mana("{2}"), sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment), min = 0))),
    )

    // CR 101.4 (each player chooses, then all sacrifice at once), 601.2c-style total power
    @Test
    fun fatedShowdown() = card(
        name = "Fated Showdown",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = "Each player chooses any number of creatures they control with total power 4 or less, then sacrifices all other creatures they control.",
    ) parsesTo abilities(
        spell(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(0), null,
                totalAtMost = Choice.TotalLimit(NumericProperty.Power, n(4))), bind = "kept", chooser = Players.Bound("player")),
            doing(Effect.Sacrifice(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))),
                not(ObjectFilter.Is(Objects.Bound("kept")))))))))),
    )

    // CR 610.3
    @Test
    fun stoneRealmJailer() = card(
        name = "Stone Realm Jailer",
        cost = "{2}{W}",
        type = "Creature — Human Soldier Ally",
        text = "When this creature enters, exile up to one target artifact, creature, or enchantment an opponent controls with mana value 3 or greater until this creature leaves the battlefield.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(artifact, creature, enchantment), opponents, manaValue(Comparison.AtLeast, 3)), min = 0))),
    )

    @Test
    fun kiteYoungsters() = card(
        name = "Kite Youngsters",
        cost = "{2}{W}",
        type = "Creature — Human Pilot Ally",
        text = """
            Flying
            When this creature enters, scry 1. (Look at the top card of your library. You may put it on the bottom.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, scry(1)),
    )

    @Test
    fun oldMastersDeserters() = card(
        name = "Old Master's Deserters",
        cost = "{1}{W}",
        type = "Creature — Human Rebel Ally",
        text = "When this creature enters, put a +1/+1 counter on target creature.",
        pt = "1/2",
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 601.2f: the first such spell you cast during each of your turns
    @Test
    fun pipkinFriendlyFlier() = card(
        name = "Pipkin, Friendly Flier",
        cost = "{W}",
        type = "Legendary Creature — Lemur Bat Ally",
        text = """
            Flying
            The first non-Lemur creature spell with flying you cast during each of your turns costs {1} less to cast.
            Whenever another creature you control with flying enters, Pipkin gets +1/+1 until end of turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CostChange(and(ObjectFilter.NthSpellThisTurn(and(not(sub("Lemur")), creature, spellFilter, kw(Keyword.Flying)), n = 1), you),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1))), condition = Condition.YourTurn),
        triggered(enters(and(another, creature, you, kw(Keyword.Flying))), pump(Objects.Self, 1, 1)),
    )

    // CR 700.2: a modal leaves-the-battlefield trigger
    @Test
    fun pipkinPlayfulPet() = card(
        name = "Pipkin, Playful Pet",
        cost = "{W}",
        type = "Legendary Creature — Lemur Bat Ally",
        text = """
            Flying, vigilance
            When Pipkin leaves the battlefield, choose one —
            • Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            • Put a +1/+1 counter on target creature you control.
            • Scry 2.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)), Instructions(modes = modes(
            mode(createFood),
            mode(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
            mode(scry(2)),
        ))),
    )

    // CR 608.2h: the creature it enchanted, after the Aura is sacrificed as a cost
    @Test
    fun roadToAtonement() = card(
        name = "Road to Atonement",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
            {5}, Sacrifice this Aura: Exile enchanted creature. Create a 1/1 white Ally creature token. Activate only during your turn.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = Objects.AttachedTo(Objects.Self)),
        activated(cost(mana("{5}"), sacrificeSelf), move(Objects.AttachedTo(Objects.Self), Zone.Exile), create(allyToken), rules = duringYourTurn),
    )

    // CR 207.2c (Landfall), 702.29e
    @Test
    fun hopperooTroop() = card(
        name = "Hopperoo Troop",
        cost = "{3}{W}{W}",
        type = "Creature — Rabbit Kangaroo",
        text = """
            Landfall — Whenever a land you control enters, this creature gains flying until end of turn and you gain 1 life.
            Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "3/5",
    ) parsesTo abilities(
        triggered(landfall, gains(Objects.Self, Keyword.Flying), gainLife(1), abilityWord = "Landfall"),
        Keyword.Typecycling(sub("Plains"), "Plainscycling", manaCost("{2}")),
    )

    // CR 120.4a: excess damage
    @Test
    fun edgedHoops() = card(
        name = "Edged Hoops",
        cost = "{1}{W}",
        type = "Instant",
        text = "Edged Hoops deals 4 damage to target attacking or blocking creature. You gain life equal to the excess damage dealt this way.",
    ) parsesTo abilities(
        spell(doing(Effect.DealDamage(toObjects(target("creature")), n(4), Objects.Self, bind = "damage")),
            doing(Effect.GainLife(Players.You, Value.Excess("damage"))),
            targets = listOf(slot("creature", and(or(attacking, ObjectFilter.HasStatus(arcanum.api.Status.Blocking)), creature)))),
    )

    // CR 506.5 (attacks alone); an activated ability of a card in hand (CR 602.1, discarding it as a cost)
    @Test
    fun heraldsBand() = card(
        name = "Herald's Band",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            Whenever a creature you control attacks alone, it gets +X/+X until end of turn, where X is the number of creatures you control.
            {2}{W}, Discard this card: It deals damage equal to the number of creatures you control to target creature.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl, alone = true)),
            untilEndOfTurn(Objects.EventObject(arcanum.api.EventRole.Object), Mechanic.ModifyPowerToughness(count(creatureYouControl), count(creatureYouControl)))),
        activated(cost(mana("{2}{W}"), CostPart.Discard(arcanum.api.CostObjects.Self)), damage(count(creatureYouControl), toObjects(target("creature"))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 118.12, 122.1d (stun)
    @Test
    fun wrathfulTownsfolk() = card(
        name = "Wrathful Townsfolk",
        cost = "{3}{W}",
        type = "Creature — Human Citizen",
        text = "Whenever this creature attacks, choose target creature an opponent controls. Tap it, then you may sacrifice an artifact or creature. If you do, put a stun counter on the chosen creature. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(attacksSelf, tapIt(target("creature")),
            Step.Pay(cost(sacrificeA(or(artifact, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(target("creature"), 1, CounterKind.Stun))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )
}
