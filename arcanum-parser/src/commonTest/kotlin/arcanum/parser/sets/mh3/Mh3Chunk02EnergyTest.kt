package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.Combine
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 02: energy. "You get {E}" gives energy counters to you (CR 122.1); "Pay {E}" is a cost paid with them (CR 107.14), in an
 * activation cost, on resolution ("you may pay {E}. If you do", "unless you pay {E}", CR 118.12) or any amount of it (CR 107.1b).
 */
class Mh3Chunk02EnergyTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val x = Value.Announced("X")

    // CR 118.12a: the spell's controller pays {1} for each {E} paid; CR 107.1b: any amount, 0 up to the energy you have
    @Test
    fun staticRebuke() = card(
        name = "Static Rebuke",
        cost = "{1}{U}",
        type = "Instant",
        text = "Choose target spell. You get {E}{E} (two energy counters), then you may pay any amount of {E}. Counter that spell unless its controller pays {1} for each {E} paid this way.",
    ) parsesTo abilities(
        spell(
            getEnergy(2),
            *payAnyAmountOfEnergy.toTypedArray(),
            Step.Pay(cost(CostPart.Mana(ManaCost.parse("{1}")!!, times = energyPaid)), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("spell")))),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    // CR 118.9, 608.2g: cast while the ability resolves, paying energy equal to its mana value instead of its mana cost
    @Test
    fun chargedStormclaw() = card(
        name = "Charged Stormclaw",
        cost = "{1}{R}",
        type = "Creature — Dinosaur",
        pt = "2/1",
        text = """
            First strike
            When this creature enters, you get {E}{E} (two energy counters). Then if you cast it from your hand, exile cards from the top of your library until you exile a nonland card. You may cast that card by paying an amount of {E} equal to its mana value rather than paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(entersSelf,
            getEnergy(2),
            ifThen(Condition.CastFrom(Zone.Hand, of = Objects.Self),
                doing(Effect.RevealUntil(filter = nonland, library = Players.You, count = n(1), reveal = true, into = Zone.Exile, foundBind = "found", restBind = "rest")),
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("found"), PlayKind.Cast,
                    cost = cost(CostPart.PayEnergy(Value.Of(NumericProperty.ManaValue, Objects.Bound("found"), Combine.Single))), caster = Players.You))),
                    player = Players.You)),
        ),
    )

    // CR 107.3a, 107.14: X announced as it is activated, that much energy paid
    @Test
    fun netherdreadDream() = card(
        name = "Netherdread Dream",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, you get {E}{E}{E} (three energy counters).
            Pay X {E}, Sacrifice a creature, Return this enchantment to its owner's hand: Return target creature card with mana value X from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        entersGetEnergy(3),
        Ability.Activated(
            cost(CostPart.PayEnergy(x), sacrificeA(creature), CostPart.ReturnToHand(CostObjects.Self)),
            Instructions(
                targets = listOf(slot("card", inYourGraveyard(creature, ObjectFilter.ManaValue(Comparison.Equal, x)))),
                steps = listOf(move(target("card"), Zone.Battlefield)),
                announced = listOf("X"),
            ),
            asSorcery,
        ),
    )

    // CR 118.12: a cost paid on resolution, then "if you do"
    @Test
    fun sparkwireGoblin() = card(
        name = "Sparkwire Goblin",
        cost = "{R}{W}",
        type = "Creature — Goblin Warrior",
        pt = "2/2",
        text = """
            When this creature enters, you get {E}{E} (two energy counters).
            At the beginning of combat on your turn, you may pay {E}. If you do, another target creature you control gets +1/+0 and gains haste until end of turn.
        """,
    ) parsesTo abilities(
        entersGetEnergy(2),
        triggered(beginningOfCombatOnYourTurn,
            *mayPayEnergy(1, pump(target("creature"), 1, 0, has(Keyword.Haste))).toTypedArray(),
            targets = listOf(slot("creature", and(another, creature, you))),
        ),
    )

    // CR 118.12a: "tap this creature unless you pay {E}"
    @Test
    fun voltaicMedusa() = card(
        name = "Voltaic Medusa",
        cost = "{2}{U}",
        type = "Creature — Jellyfish",
        pt = "3/1",
        text = """
            Flash
            Flying
            When this creature enters, you get {E}{E} (two energy counters).
            At the beginning of your first main phase, tap this creature unless you pay {E}.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        entersGetEnergy(2),
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), *unlessYouPayEnergy(1, tapIt(Objects.Self)).toTypedArray()),
    )

    // CR 602.5d: activate only as a sorcery; CR 122.1b: an exalted counter is a keyword counter
    @Test
    fun heraldOfEmberLight() = card(
        name = "Herald of Ember Light",
        cost = "{1}{W}{U}",
        type = "Creature — Djinn Monk",
        pt = "1/4",
        text = """
            When this creature enters, you get {E}{E}{E} (three energy counters).
            Pay {E}{E}: Put an exalted counter on target creature you control. Activate only as a sorcery. (Whenever a creature you control attacks alone, it gets +1/+1 until end of turn for each instance of exalted among permanents you control.)
        """,
    ) parsesTo abilities(
        entersGetEnergy(3),
        activated(cost(payEnergy(2)), counters(target("creature"), 1, CounterKind.Keyword(Keyword.Exalted.kind)),
            targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    // CR 107.1b: any amount of energy, then that much damage
    @Test
    fun arcingRelease() = card(
        name = "Arcing Release",
        cost = "{R}",
        type = "Instant",
        text = "Choose target creature or planeswalker. You get {E}{E}{E} (three energy counters), then you may pay any amount of {E}. Arcing Release deals that much damage to that permanent.",
    ) parsesTo abilities(
        spell(
            getEnergy(3),
            *payAnyAmountOfEnergy.toTypedArray(),
            damage(energyPaid, toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )

    // CR 603.12: a reflexive trigger; CR 611.2a: becoming an Angel lasts for as long as it stays
    @Test
    fun shepherdOfSpirits() = card(
        name = "Shepherd of Spirits",
        cost = "{W}",
        type = "Creature — Human Cleric",
        pt = "1/2",
        text = """
            Whenever another creature you control enters, you gain 1 life and get {E} (an energy counter).
            Whenever you attack, you may pay {E}{E}{E}. When you do, put two +1/+1 counters and a flying counter on target attacking creature. It becomes an Angel in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you)), gainLife(1), getEnergy(1)),
        triggered(youAttack, *mayPayEnergyWhenYouDo(3, Instructions(
            targets = listOf(slot("creature", and(attacking, creature))),
            steps = listOf(
                counters(target("creature"), 2),
                counters(target("creature"), 1, CounterKind.Keyword(Keyword.Flying.kind)),
                doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Angel"))))),
                    Affected.Objects(target("creature")), Duration.Indefinitely)),
            ),
        )).toTypedArray()),
    )

    @Test
    fun brasslightSlith() = card(
        name = "Brasslight Slith",
        cost = "{1}{W}",
        type = "Creature — Slith",
        pt = "2/1",
        text = """
            When this creature enters, you get {E}{E} (two energy counters).
            Whenever this creature attacks, you may pay {E}{E}. If you do, it gains first strike until end of turn.
            Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        entersGetEnergy(2),
        triggered(attacksSelf, *mayPayEnergy(2, gains(Objects.Self, Keyword.FirstStrike)).toTypedArray()),
        triggered(combatDamageToAPlayer, counters(Objects.Self)),
    )

    @Test
    fun corruptedFoundry() = card(
        name = "Corrupted Foundry",
        cost = "{2}{R}",
        type = "Artifact",
        text = """
            Whenever you attack, you get {E} (an energy counter).
            {T}, Pay {E}{E}{E}: Create a 3/3 colorless Phyrexian Golem artifact creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youAttack, getEnergy(1)),
        activated(cost(tap, payEnergy(3)), create(creatureToken("Phyrexian Golem", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature))),
            rules = asSorcery),
    )

    // CR 118.9: an alternative cost for the spells you cast; CR 601.3c: as though it had flash only when cast this way
    @Test
    fun feralInvocations() = card(
        name = "Feral Invocations",
        cost = "{2}{G}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, you get {E}{E} (two energy counters).
            You may cast creature spells with mana value 3 or less by paying {E} rather than paying their mana costs. If you cast a spell this way, you may cast it as though it had flash.
        """,
    ) parsesTo abilities(
        entersGetEnergy(2),
        static(Mechanic.AlternativeCost(cost = cost(payEnergy(1)), flash = true),
            affects = Objects.Each(and(creature, manaValue(Comparison.AtMost, 3), ObjectFilter.WasCast()))),
    )

    // CR 603.12
    @Test
    fun cipherArchGargoyle() = card(
        name = "Cipher Arch Gargoyle",
        cost = "{W}{U}",
        type = "Artifact Creature — Gargoyle",
        pt = "2/2",
        text = """
            Flying
            When this creature enters, you get {E}{E}{E} (three energy counters).
            Whenever you attack, you may pay {E}{E}. When you do, target creature you control gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersGetEnergy(3),
        triggered(youAttack, *mayPayEnergyWhenYouDo(2, Instructions(
            targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(gains(target("creature"), Keyword.Lifelink)),
        )).toTypedArray()),
    )

    // CR 122.1: energy equal to a number counted as it resolves (the new tokens too, CR 608.2c)
    @Test
    fun swarmOfSparkmites() = card(
        name = "Swarm of Sparkmites",
        cost = "{2}{R}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create two 1/1 red Gremlin creature tokens. Then you get an amount of {E} (energy counters) equal to the number of creatures you control.
            Pay {E}{E}{E}{E}: Creatures you control get +1/+0 and gain haste until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Gremlin", listOf(arcanum.api.Color.Red), 1, 1), n(2)), getEnergy(count(creatureYouControl))),
        activated(cost(payEnergy(4)), pump(Objects.Each(creatureYouControl), 1, 0, has(Keyword.Haste))),
    )

    @Test
    fun forgedVoltbeetle() = card(
        name = "Forged Voltbeetle",
        cost = "{1}{R}",
        type = "Artifact Creature — Insect",
        pt = "1/3",
        text = """
            Menace
            When this creature enters, you get {E}{E} (two energy counters).
            Whenever this creature attacks, you may pay {E}. If you do, another target attacking creature gets +1/+0 and gains menace until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        entersGetEnergy(2),
        triggered(attacksSelf, *mayPayEnergy(1, pump(target("creature"), 1, 0, has(Keyword.Menace))).toTypedArray(),
            targets = listOf(slot("creature", and(another, attacking, creature)))),
    )

    // CR 106.1, 107.14
    @Test
    fun sunlitConverter() = card(
        name = "Sunlit Converter",
        cost = "{2}",
        type = "Artifact",
        text = """
            This artifact enters tapped.
            When this artifact enters, you get {E}{E}{E} (three energy counters).
            {T}: Add {C}.
            {T}, Pay {E}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        entersTapped,
        entersGetEnergy(3),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap, payEnergy(1)), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    @Test
    fun equinoxDevotee() = card(
        name = "Equinox Devotee",
        cost = "{2}{W}",
        type = "Creature — Rhino Cleric",
        pt = "2/3",
        text = """
            When this creature enters, you get {E}{E} (two energy counters).
            {T}, Pay {E}: Tap target creature.
        """,
    ) parsesTo abilities(
        entersGetEnergy(2),
        activated(cost(tap, payEnergy(1)), tapIt(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 610.3: exiled until this leaves the battlefield; CR 118.12a: sacrificed unless you pay {E}
    @Test
    fun cracklingCell() = card(
        name = "Crackling Cell",
        cost = "{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield. You get {E}{E} (two energy counters).
            At the beginning of your first main phase, sacrifice this enchantment unless you pay {E}.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            getEnergy(2),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), *unlessYouPayEnergy(1, doing(Effect.Sacrifice(Objects.Self))).toTypedArray()),
    )

    @Test
    fun squallGatherer() = card(
        name = "Squall Gatherer",
        cost = "{1}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "2/1",
        text = """
            When this creature enters, you get {E}{E} (two energy counters).
            {T}, Pay {E}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        entersGetEnergy(2),
        activated(cost(tap, payEnergy(1)), draw(1), discard(1)),
    )

    @Test
    fun flourishingCloudfang() = card(
        name = "Flourishing Cloudfang",
        cost = "{2}{R}{R}",
        type = "Creature — Cat Dragon",
        pt = "3/2",
        text = """
            Flying
            When this creature enters, you get {E}{E}{E} (three energy counters).
            Whenever this creature attacks, you may pay {E}{E}{E}. If you do, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersGetEnergy(3),
        triggered(attacksSelf, *mayPayEnergy(3, counters(Objects.Self)).toTypedArray()),
    )

    // CR 601.2b, 107.3: X announced for the additional cost; every creature gets -X/-X (CR 613.4c)
    @Test
    fun venomTorrent() = card(
        name = "Venom Torrent",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, pay X life.
            All creatures get -X/-X until end of turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.PayLife(x)))),
        spell(
            untilEndOfTurn(Objects.Each(creature), Mechanic.ModifyPowerToughness(Value.Times(x, -1), Value.Times(x, -1))),
            announced = listOf("X"),
        ),
    )

    @Test
    fun reworkTheStory() = card(
        name = "Rework the Story",
        cost = "{U}",
        type = "Instant",
        text = "Draw a card. You get {E}{E} (two energy counters).",
    ) parsesTo abilities(
        spell(draw(1), getEnergy(2)),
    )

    // CR 607.2a: the card exiled with it is playable until another card is exiled with it
    @Test
    fun volatileTalisman() = card(
        name = "Volatile Talisman",
        cost = "{1}{R}",
        type = "Artifact",
        text = """
            When this artifact enters, you get {E}{E} (two energy counters).
            Whenever you cast a spell from anywhere other than your hand, this artifact deals 1 damage to each opponent.
            {T}, Pay {E}{E}: Exile the top card of your library. You may play it until you exile another card with this artifact.
        """,
    ) parsesTo abilities(
        entersGetEnergy(2),
        triggered(youCast(ObjectFilter.Not(ObjectFilter.WasCast(Zone.Hand))), damage(1, eachOpponent)),
        activated(cost(tap, payEnergy(2)),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.NonEmpty("exiled"),
                doing(Effect.Remember("exiled", Objects.Bound("exiled"), replace = true)),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.AsLongAs(Condition.ObjectMatches(Objects.Bound("exiled"), ObjectFilter.Is(Objects.Remembered("exiled"))))))),
        ),
    )

    // CR 603.12: the reflexive trigger's mode is chosen as it is put on the Stack (CR 700.2)
    @Test
    fun thunderveilAngel() = card(
        name = "Thunderveil Angel",
        cost = "{3}{W}{W}",
        type = "Creature — Angel",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, you get {E}{E}{E} (three energy counters).
            At the beginning of combat on your turn, you may pay {E}{E}. When you do, choose one —
            • This creature gains vigilance and lifelink until end of turn.
            • Other creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        entersGetEnergy(3),
        triggered(beginningOfCombatOnYourTurn, *mayPayEnergyWhenYouDo(2, Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(gains(Objects.Self, Keyword.Vigilance, Keyword.Lifelink))), text = "This creature gains vigilance and lifelink until end of turn."),
            Mode(Instructions(steps = listOf(pump(Objects.Each(and(another, creature, you)), 1, 1))), text = "Other creatures you control get +1/+1 until end of turn."),
        ), min = 1, max = 1, chooser = Players.You))).toTypedArray()),
    )

    // CR 101.4: each player chooses in turn order, then all who chose exile and draw at once; CR 611.2a: until the end of your next turn
    @Test
    fun wheelOfPossibility() = card(
        name = "Wheel of Possibility",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            You get {E}{E}{E} (three energy counters), then you may pay any amount of {E}.
            Each player may exile their hand and draw a number of cards equal to the amount of {E} paid this way. If seven or more {E} was paid this way, you may play cards you own exiled this way until the end of your next turn.
        """,
    ) parsesTo abilities(
        spell(
            getEnergy(3),
            *payAnyAmountOfEnergy.toTypedArray(),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(Step.May(listOf(
                move(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player"))))), Zone.Exile, postBind = "exiled"),
                doing(Effect.Draw(Players.Bound("player"), energyPaid))), player = Players.Bound("player"))), together = true),
            ifThen(Condition.Compare(energyPaid, Comparison.AtLeast, n(7)),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Where(Objects.Bound("exiled"), ObjectFilter.OwnedBy(PlayerFilter.You))), Duration.UntilEndOfYourNextTurn))),
        ),
    )

    // CR 107.3: X in the spell's cost; destroyed by mana value against the energy paid
    @Test
    fun furyOfTheHeavens() = card(
        name = "Fury of the Heavens",
        cost = "{X}{W}{W}",
        type = "Sorcery",
        text = "You get X {E} (energy counters), then you may pay any amount of {E}. Destroy each artifact, creature, and enchantment with mana value less than or equal to the amount of {E} paid this way.",
    ) parsesTo abilities(
        spell(
            getEnergy(x),
            *payAnyAmountOfEnergy.toTypedArray(),
            destroy(Objects.Each(and(or(artifact, creature, enchantment), ObjectFilter.ManaValue(Comparison.AtMost, energyPaid)))),
            announced = listOf("X"),
        ),
    )
}
