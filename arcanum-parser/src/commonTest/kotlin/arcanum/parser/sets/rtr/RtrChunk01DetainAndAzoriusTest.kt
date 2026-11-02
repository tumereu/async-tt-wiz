package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.detain
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.enchant
import arcanum.parser.sets.rtr.Rtr.enchanted
import arcanum.parser.sets.rtr.Rtr.enchantment
import arcanum.parser.sets.rtr.Rtr.gainLife
import arcanum.parser.sets.rtr.Rtr.gains
import arcanum.parser.sets.rtr.Rtr.mana
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonland
import arcanum.parser.sets.rtr.Rtr.opponents
import arcanum.parser.sets.rtr.Rtr.permanent
import arcanum.parser.sets.rtr.Rtr.planeswalker
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisAttacks
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.thisLeaves
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import arcanum.parser.sets.rtr.Rtr.yourUpkeep
import kotlin.test.Test

/**
 * Return to Ravnica chunk 01: detain (CR 701.35a: until your next turn the permanent can't attack or block and its
 * activated abilities, mana abilities too, can't be activated) on entering, attacking, upkeep, spells and activated
 * abilities, "up to two"; and the Azorius cards: an Arrest, a modal charm, an O-Ring for permanents with the same name
 * (CR 607.2a), hand-size counts, filibuster counters with a win, attack triggers on you or your planeswalkers, a cost to
 * attack (CR 508.1h), counterspells and life.
 */
class RtrChunk01DetainAndAzoriusTest {
    private val creatureOpponentControls = all(creature, opponents)
    private val nonlandPermanentOpponentControls = all(nonland, permanent, opponents)

    // ---- Detain

    // CR 701.35a
    @Test
    fun precinctWarden() = card(
        name = "Precinct Warden",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/1",
        text = "When this creature enters, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)",
    ) parsesTo abilities(
        triggered(thisEnters, detain("creature"), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    /** "up to two target creatures": zero, one or two targets (CR 115.1, 601.2c). */
    @Test
    fun tribunalAdjudicator() = card(
        name = "Tribunal Adjudicator",
        cost = "{2}{W}{W}",
        type = "Creature — Human Wizard",
        pt = "2/3",
        text = "When this creature enters, detain up to two target creatures your opponents control. (Until your next turn, those creatures can't attack or block and their activated abilities can't be activated.)",
    ) parsesTo abilities(
        triggered(thisEnters, detain("creatures"), targets = listOf(slot("creatures", creatureOpponentControls, min = 0, max = 2))),
    )

    @Test
    fun curfewDecree() = card(
        name = "Curfew Decree",
        cost = "{2}{W}{W}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)",
    ) parsesTo abilities(
        triggered(yourUpkeep, detain("creature"), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    @Test
    fun stayOfMotion() = card(
        name = "Stay of Motion",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(detain("creature"), draw(1), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    @Test
    fun skyCourtSentinel() = card(
        name = "Sky Court Sentinel",
        cost = "{5}{U}",
        type = "Creature — Vedalken Knight",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisEnters, detain("creature"), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    @Test
    fun oathboundShade() = card(
        name = "Oathbound Shade",
        cost = "{3}{U}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            This creature can't be blocked.
            When this creature enters, detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CantBeBlocked),
        triggered(thisEnters, detain("creature"), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    /** A nonland permanent: an artifact or planeswalker can be detained too ("can't attack or block" means nothing to it,
     * its activated abilities can't be activated). */
    @Test
    fun lawwingKnight() = card(
        name = "Lawwing Knight",
        cost = "{1}{W}{U}",
        type = "Creature — Human Knight",
        pt = "3/1",
        text = """
            Flying
            When this creature enters, detain target nonland permanent an opponent controls. (Until your next turn, that permanent can't attack or block and its activated abilities can't be activated.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisEnters, detain("permanent"), targets = listOf(slot("permanent", nonlandPermanentOpponentControls))),
    )

    @Test
    fun senateClerkMage() = card(
        name = "Senate Clerk-Mage",
        cost = "{W}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            {W}{U}: Target creature gains flying until end of turn.
            {3}{W}{U}: Detain target nonland permanent an opponent controls. (Until your next turn, that permanent can't attack or block and its activated abilities can't be activated.)
        """,
    ) parsesTo abilities(
        activated(Cost(listOf(manaPart("{W}{U}"))), gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", creature))),
        activated(Cost(listOf(manaPart("{3}{W}{U}"))), detain("permanent"), targets = listOf(slot("permanent", nonlandPermanentOpponentControls))),
    )

    @Test
    fun archonOfTheNinefoldSeal() = card(
        name = "Archon of the Ninefold Seal",
        cost = "{5}{W}{U}",
        type = "Creature — Archon",
        pt = "4/5",
        text = """
            Flying
            Whenever this creature attacks, detain up to two target nonland permanents your opponents control. (Until your next turn, those permanents can't attack or block and their activated abilities can't be activated.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisAttacks, detain("permanents"), targets = listOf(slot("permanents", nonlandPermanentOpponentControls, min = 0, max = 2))),
    )

    // ---- Azorius

    /** Like detain but for as long as the Aura is attached; mana abilities can't be activated either (CR 602.5). */
    @Test
    fun lawfulShackles() = card(
        name = "Lawful Shackles",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block, and its activated abilities can't be activated.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true)),
    )

    /** CR 700.2: one mode; the third mode's target exists only when it is chosen (CR 700.2c). */
    @Test
    fun senateSigil() = card(
        name = "Senate Sigil",
        cost = "{W}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures you control gain lifelink until end of turn.
            • Draw a card.
            • Put target attacking or blocking creature on top of its owner's library.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(gains(Objects.Each(all(creature, you)), Keyword.Lifelink)))),
            Mode(Instructions(steps = listOf(draw(1)))),
            Mode(Instructions(
                targets = listOf(slot("creature", all(ObjectFilter.Or(listOf(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Blocking))), creature))),
                steps = listOf(Step.Do(Effect.Move(target("creature"), Destination(Zone.Library, libraryPosition = 0)))),
            )),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    /** An O-Ring for every permanent with the target's name (CR 201.2): the exiled cards are linked to it (CR 607.2a) and come
     * back under their owners' control when it leaves. "Not named [this]" uses its own name (CR 201.5). */
    @Test
    fun containmentOrb() = card(
        name = "Containment Orb",
        cost = "{1}{W}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, you may exile target nonland permanent not named Containment Orb and all other permanents with the same name as that permanent.
            When this enchantment leaves the battlefield, return the exiled cards to the battlefield under their owner's control.
        """,
    ) parsesTo abilities(
        triggered(thisEnters,
            Step.May(listOf(
                Step.Do(Effect.Move(Objects.Union(listOf(target("permanent"), Objects.Each(all(
                    ObjectFilter.Not(ObjectFilter.Is(target("permanent"))), permanent, ObjectFilter.SameNameAs(target("permanent")))))),
                    Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ), player = Players.You),
            targets = listOf(slot("permanent", all(nonland, permanent, ObjectFilter.Not(ObjectFilter.Named("Containment Orb")))))),
        triggered(thisLeaves, Step.Do(Effect.Move(Objects.Remembered("exiled"),
            Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))))),
    )

    @Test
    fun writOfInspection() = card(
        name = "Writ of Inspection",
        cost = "{W}{U}",
        type = "Sorcery",
        text = "Target player reveals their hand. You gain life equal to the number of cards in that player's hand.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Reveal(Objects.Each(all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), bind = "revealed")),
            gainLife(Value.HandSize(Players.Target("player"))),
            targets = listOf(Rtr.targetPlayer),
        ),
    )

    /** The enchanted creature's controller's hand, counted as it changes (layer 7c); their draw step, theirs to draw (CR 504.1). */
    @Test
    fun mandateOfTheBench() = card(
        name = "Mandate of the Bench",
        cost = "{3}{W}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 for each card in its controller's hand.
            At the beginning of the draw step of enchanted creature's controller, that player draws an additional card.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.ModifyPowerToughness(Value.HandSize(Players.ControllerOf(enchanted)), Value.HandSize(Players.ControllerOf(enchanted)))),
        triggered(Rtr.step(TurnStep.Draw, PlayerFilter.Is(Players.ControllerOf(enchanted))), draw(1, Players.EventPlayer)),
    )

    /** A counter of its own name (CR 122.1); "Then if …" is checked after the counter is put (CR 608.2c). */
    @Test
    fun senateFilibusterers() = card(
        name = "Senate Filibusterers",
        cost = "{3}{W/U}{W/U}",
        type = "Creature — Human Advisor",
        pt = "3/5",
        text = """
            At the beginning of your upkeep, put a filibuster counter on this creature. Then if this creature has five or more filibuster counters on it, you win the game.
            Whenever a source deals damage to you, remove a filibuster counter from this creature.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep,
            Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Named("filibuster"), n(1))),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("filibuster"), atLeast = 5)),
                then = listOf(Step.Do(Effect.WinGame(Players.You))))),
        triggered(Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You))),
            Step.Do(Effect.RemoveCounters(Objects.Self, CounterKind.Named("filibuster"), n(1)))),
    )

    /** CR 508.1b: what a creature attacks; one trigger per attacking creature. */
    @Test
    fun valeriaArbiterOfSky() = card(
        name = "Valeria, Arbiter of Sky",
        cost = "{2}{W}{W}{U}{U}",
        type = "Legendary Creature — Sphinx",
        pt = "6/6",
        text = """
            Flying
            Whenever a creature attacks you or a planeswalker you control, you may draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Attack(creature, target = RecipientFilter(objects = all(planeswalker, you), players = PlayerFilter.You))),
            Step.May(listOf(draw(1)), player = Players.You)),
    )

    /** A cost to attack (CR 508.1d, 508.1h): {X} for each creature attacking you or a planeswalker you control, X counted as
     * attackers are declared. */
    @Test
    fun bulwarkOfStatutes() = card(
        name = "Bulwark of Statutes",
        cost = "{4}{W}",
        type = "Enchantment",
        text = "Creatures can't attack you or planeswalkers you control unless their controller pays {X} for each of those creatures, where X is the number of enchantments you control.",
    ) parsesTo abilities(
        static(Objects.Each(creature), Mechanic.AttackCost(
            Cost(listOf(CostPart.Mana(mana("{X}"), x = Value.Count(Objects.Each(all(enchantment, you)))))),
            target = Recipients(objects = Objects.Each(all(planeswalker, you)), players = Players.You))),
    )

    @Test
    fun abruptPardon() = card(
        name = "Abrupt Pardon",
        cost = "{W}{U}",
        type = "Instant",
        text = "Return target creature to its owner's hand. You gain 2 life.",
    ) parsesTo abilities(
        spell(Rtr.move(target("creature"), Zone.Hand), gainLife(2), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun gavelsVerdict() = card(
        name = "Gavel's Verdict",
        cost = "{3}{W}{U}",
        type = "Instant",
        text = "Counter target spell. You gain 5 life.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Counter(target("spell"))), gainLife(5), targets = listOf(slot("spell", Rtr.spell))),
    )

    /** "unless its controller pays {1}" (CR 118.12a): that player may pay; if they don't, the spell is countered. */
    @Test
    fun docketOwl() = card(
        name = "Docket Owl",
        cost = "{W/U}",
        type = "Creature — Bird",
        pt = "1/1",
        text = """
            Flying
            Sacrifice this creature: Counter target instant or sorcery spell unless its controller pays {1}.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(Cost(listOf(CostPart.Sacrifice(arcanum.api.CostObjects.Self))),
            Step.Pay(Cost.mana(mana("{1}")), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", all(ObjectFilter.Or(listOf(Rtr.instant, Rtr.sorcery)), Rtr.spell)))),
    )

    /** "defending player" in an attack trigger: the player this creature attacks (CR 506.2). */
    @Test
    fun cloudmarkCondor() = card(
        name = "Cloudmark Condor",
        cost = "{2}{W}{U}",
        type = "Creature — Bird",
        pt = "2/2",
        text = """
            Flying
            Whenever this creature attacks, you may return target creature defending player controls with toughness 2 or less to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisAttacks, Step.May(listOf(Rtr.move(target("creature"), Zone.Hand)), player = Players.You),
            targets = listOf(slot("creature", all(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))),
                ObjectFilter.Toughness(arcanum.api.Comparison.AtMost, n(2)))))),
    )

    /** X announced as it is cast (CR 107.3a), the same for both. */
    @Test
    fun sphinxsDisclosure() = card(
        name = "Sphinx's Disclosure",
        cost = "{X}{W}{U}{U}",
        type = "Instant",
        text = "You gain X life and draw X cards.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            gainLife(Value.Announced("X")),
            Step.Do(Effect.Draw(Players.You, Value.Announced("X"))),
        ), announced = listOf("X"))),
    )

    @Test
    fun unanimousSentence() = card(
        name = "Unanimous Sentence",
        cost = "{1}{W}{W}{U}",
        type = "Sorcery",
        text = """
            This spell can't be countered.
            Destroy all creatures.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CantBeCountered),
        spell(Rtr.destroy(Objects.Each(creature))),
    )
}
