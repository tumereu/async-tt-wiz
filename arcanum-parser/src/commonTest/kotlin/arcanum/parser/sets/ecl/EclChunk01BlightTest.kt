package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 01: blight (CR 701.68). "Blight N" puts N -1/-1 counters on a creature you control: as a cost (an activated
 * ability's, an additional cost, "blight 2 or pay {1}", an optional "you may blight 1" read with "if this spell's additional cost was
 * paid"), as a payment on resolution ("you may blight 1. If you do", "If you don't"), and as an effect ("blight 1", "Target opponent
 * blights 2", "each opponent blights 1"). The blighted creature is named "blighted" (701.68c). Also "blight X" with an upper limit
 * (CR 107.3a), "if it isn't your main phase" (CR 505.1), a delayed play permission until your next end step, a reflexive trigger
 * (CR 603.12) and creature spells cast from among cards exiled with a creature by removing counters (CR 601.2f).
 */
class EclChunk01BlightTest {
    private val treasure = arcanum.api.TokenSpec.Predefined("Treasure")

    @Test
    fun wingshadeBoon() = card(
        name = "Wingshade Boon",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+0 and has flying.
            {1}{W}, Blight 2: Return this card from your graveyard to your hand. Activate only as a sorcery. (To blight 2, put two -1/-1 counters on a creature you control.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(1, 0), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
        // CR 113.6m: it works only in the graveyard
        activated(cost(mana("{1}{W}"), blight(2)), move(Objects.Self, Zone.Hand), rules = asSorcery, functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 700.2, 601.2b: the optional cost is chosen before the modes
    @Test
    fun costlyReprisal() = card(
        name = "Costly Reprisal",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may blight 2. (You may put two -1/-1 counters on a creature you control.)
            Choose one. If this spell's additional cost was paid, choose both instead.
            • Destroy target artifact or enchantment.
            • Destroy target creature with mana value 3 or greater.
        """,
    ) parsesTo abilities(
        additionalCost(blight(2), optional = true),
        Ability.Spell(arcanum.api.Instructions(modes = Modes(listOf(
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, manaValue(Comparison.AtLeast, 3)))))),
            min = 1, max = 1, maxIf = additionalCostPaid, maxIfCount = 2, chooser = Players.You))),
    )

    @Test
    fun driftIntoStillness() = card(
        name = "Drift into Stillness",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
            {1}{W}, Blight 1, Sacrifice this Aura: Exile enchanted creature. (To blight 1, put a -1/-1 counter on a creature you control.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = Objects.AttachedTo(Objects.Self)),
        activated(cost(mana("{1}{W}"), blight(1), sacrificeSelf), move(Objects.AttachedTo(Objects.Self), Zone.Exile)),
    )

    // CR 601.2f: one of two additional costs
    @Test
    fun feralUndoing() = card(
        name = "Feral Undoing",
        cost = "{U}{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, blight 2 or pay {1}. (To blight 2, put two -1/-1 counters on a creature you control.)
            Counter target spell.
        """,
    ) parsesTo abilities(
        additionalCostOneOf(cost(blight(2)), manaCost("{1}")),
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 118.12: a cost paid on resolution
    @Test
    fun sickenedThornbark() = card(
        name = "Sickened Thornbark",
        cost = "{4}{B}",
        type = "Creature — Treefolk Warlock",
        text = "Whenever this creature enters or attacks, you may blight 2. If you do, you draw a card and lose 1 life. (To blight 2, put two -1/-1 counters on a creature you control.)",
        pt = "3/7",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, *mayPay(blight(2), then = listOf(draw(1), loseLife(1))).toTypedArray()),
    )

    @Test
    fun hobgoblinRevelry() = card(
        name = "Hobgoblin Revelry",
        cost = "{2}{B}",
        type = "Kindred Enchantment — Goblin",
        text = """
            When this enchantment enters, you may blight 1. If you do, create two 1/1 black and red Goblin creature tokens. (To blight 1, put a -1/-1 counter on a creature you control.)
            Whenever a Goblin creature you control dies, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, *mayPay(blight(1), then = listOf(create(goblin, n(2)))).toTypedArray()),
        triggered(on(EventPattern.ZoneChange(and(sub("Goblin"), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1)),
    )

    @Test
    fun mirecrawlersGrip() = card(
        name = "Mirecrawler's Grip",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, blight 1 or pay {3}. (To blight 1, put a -1/-1 counter on a creature you control.)
            Exile target creature.
        """,
    ) parsesTo abilities(
        additionalCostOneOf(cost(blight(1)), manaCost("{3}")),
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", creature))),
    )

    // CR 701.68a: an opponent chooses one of their creatures
    @Test
    fun paragonOfTheUncanny() = card(
        name = "Paragon of the Uncanny",
        cost = "{3}{B}",
        type = "Creature — Goblin Berserker",
        text = """
            As an additional cost to cast this spell, behold a Goblin and exile it. (Exile a Goblin you control or a Goblin card from your hand.)
            Pay 1 life, Blight 2: Target opponent blights 2. Activate only as a sorcery.
            When this creature leaves the battlefield, return the exiled card to its owner's hand.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        beholdAndExile(sub("Goblin")),
        activated(cost(CostPart.PayLife(n(1)), blight(2)), blights(Players.Target("opponent"), 2), targets = listOf(targetOpponent), rules = asSorcery),
        returnTheExiledCard,
    )

    // CR 607.2a: the cards exiled with it; CR 601.2f: removing counters as an additional cost
    @Test
    fun duskpalmDissenter() = card(
        name = "Duskpalm Dissenter",
        cost = "{B}",
        type = "Creature — Elf Warlock",
        text = """
            {T}, Blight 1: Surveil 1.
            {T}, Blight 2: Exile target card from a graveyard.
            During your turn, you may cast creature spells from among cards you own exiled with this creature by removing three counters from among creatures you control in addition to paying their other costs.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        activated(cost(tap, blight(1)), doing(Effect.Surveil(Players.You, n(1)))),
        activated(cost(tap, blight(2)),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You, additionalCost = cost(
            CostPart.RemoveCounters(null, n(3), CostObjects.Choose(creatureYouControl, count = null), distributed = true)))),
            affects = arcanum.api.Affected.Objects(Objects.Each(and(creature, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Is(Objects.Remembered("exiled"))))),
            condition = Condition.YourTurn),
    )

    // CR 505.1: your main phases are in your own turn
    @Test
    fun draughtOfDuskshine() = card(
        name = "Draught of Duskshine",
        cost = "{4}{B}",
        type = "Instant",
        text = "Return target creature card from your graveyard to the battlefield. Then if it isn't your main phase, blight 2. (Put two -1/-1 counters on a creature you control.)",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield),
            Step.If(Condition.Not(Condition.And(listOf(Condition.YourTurn,
                Condition.Or(listOf(Condition.StepIs(TurnStep.PrecombatMain), Condition.StepIs(TurnStep.PostcombatMain)))))),
                then = listOf(blights(Players.You, 2))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun slumberSnatcher() = card(
        name = "Slumber Snatcher",
        cost = "{3}{B}",
        type = "Creature — Faerie Rogue",
        text = """
            Flying
            When this creature enters, you may blight 1. If you do, each opponent discards a card. (To blight 1, put a -1/-1 counter on a creature you control.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, *mayPay(blight(1), then = listOf(discard(1, Players.Each(PlayerFilter.Opponent)))).toTypedArray()),
    )

    // CR 118.12: "If you don't"
    @Test
    fun bellyripperCrew() = card(
        name = "Bellyripper Crew",
        cost = "{3}{B}",
        type = "Creature — Goblin Berserker",
        text = "At the beginning of your first main phase, you may blight 2. If you don't, you lose 3 life. (To blight 2, put two -1/-1 counters on a creature you control.)",
        pt = "6/6",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
            Step.Pay(cost(blight(2)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(loseLife(3)))),
    )

    @Test
    fun repayingJinx() = card(
        name = "Repaying Jinx",
        cost = "{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may blight 1. (You may put a -1/-1 counter on a creature you control.)
            Destroy target creature with mana value 2 or less. If this spell's additional cost was paid, you gain 2 life.
        """,
    ) parsesTo abilities(
        additionalCost(blight(1), optional = true),
        spell(destroy(target("creature")), Step.If(additionalCostPaid, then = listOf(gainLife(2))),
            targets = listOf(slot("creature", and(creature, manaValue(Comparison.AtMost, 2))))),
    )

    @Test
    fun searingInquisitiveness() = card(
        name = "Searing Inquisitiveness",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may blight 1. (You may put a -1/-1 counter on a creature you control.)
            Exile the top two cards of your library. If this spell's additional cost was paid, exile the top three cards instead. Until the end of your next turn, you may play those cards.
        """,
    ) parsesTo abilities(
        additionalCost(blight(1), optional = true),
        spell(
            Step.If(additionalCostPaid,
                then = listOf(move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Zone.Exile, postBind = "exiled")),
                otherwise = listOf(move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
    )

    @Test
    fun emberJab() = card(
        name = "Ember Jab",
        cost = "{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may blight 1. (You may put a -1/-1 counter on a creature you control.)
            Ember Jab deals 2 damage to target creature. It deals 4 damage to that creature instead if this spell's additional cost was paid.
        """,
    ) parsesTo abilities(
        additionalCost(blight(1), optional = true),
        spell(Step.If(additionalCostPaid,
            then = listOf(damage(4, toObjects(target("creature")))),
            otherwise = listOf(damage(2, toObjects(target("creature"))))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 118.12: a mandatory discard; drawing only if a card was discarded
    @Test
    fun sinewGobbler() = card(
        name = "Sinew Gobbler",
        cost = "{1}{R}",
        type = "Creature — Goblin Scout",
        text = "{T}, Blight 1: Discard a card. If you do, draw a card. (To blight 1, put a -1/-1 counter on a creature you control.)",
        pt = "1/3",
    ) parsesTo abilities(
        activated(cost(tap, blight(1)),
            Step.Pay(cost(discardACard), payer = Players.You, mandatory = true, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    @Test
    fun grimebackForager() = card(
        name = "Grimeback Forager",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        text = """
            At the beginning of your first main phase, you may blight 1. If you do, create a Treasure token. (To blight 1, put a -1/-1 counter on a creature you control. A Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), *mayPay(blight(1), then = listOf(create(treasure))).toTypedArray()),
    )

    // CR 107.3a: X chosen for the additional cost, at most the greatest toughness; CR 601.2b
    @Test
    fun spiritPyre() = card(
        name = "Spirit Pyre",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, blight X. X can't be greater than the greatest toughness among creatures you control. (Put X -1/-1 counters on a creature you control.)
            Spirit Pyre deals X damage to each opponent and each creature they control.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AdditionalCost(cost = cost(CostPart.Action(KeywordAction.Blight(Players.You, Value.CastX(of = Objects.Self)), bind = "blighted"))),
            Mechanic.XAtMost(Value.Of(NumericProperty.Toughness, Objects.Each(creatureYouControl), arcanum.api.Combine.Greatest))),
            affects = arcanum.api.Affected.Objects(Objects.Self)),
        spell(damage(Value.Announced(name = "X"), Recipients(players = Players.Each(PlayerFilter.Opponent), objects = Objects.Each(creatureOpponentControls))),
            announced = listOf("X")),
    )

    @Test
    fun stalecrustGranny() = card(
        name = "Stalecrust Granny",
        cost = "{2}{R}{R}",
        type = "Creature — Goblin Warrior",
        text = "When this creature enters, you may blight 2. If you do, create two 1/1 black and red Goblin creature tokens. (To blight 2, put two -1/-1 counters on a creature you control.)",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, *mayPay(blight(2), then = listOf(create(goblin, n(2)))).toTypedArray()),
    )

    // CR 701.68a: it may blight itself
    @Test
    fun barbHurler() = card(
        name = "Barb-Hurler",
        cost = "{2}{R}",
        type = "Creature — Goblin Warrior",
        text = "{1}{R}, {T}, Blight 1: This creature deals 2 damage to each opponent. (To blight 1, put a -1/-1 counter on a creature you control.)",
        pt = "3/3",
    ) parsesTo abilities(
        activated(cost(mana("{1}{R}"), tap, blight(1)), damage(2, eachOpponent)),
    )

    // CR 603.12: the target is chosen when the reflexive trigger is put on the Stack
    @Test
    fun burrowFlamekeeper() = card(
        name = "Burrow Flamekeeper",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        text = "At the beginning of combat on your turn, you may blight 1. When you do, target creature gains haste until end of turn. (To blight 1, put a -1/-1 counter on a creature you control.)",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, *mayPay(blight(1), then = listOf(
            reflexive(gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", creature))))).toTypedArray()),
    )

    // CR 118.12: "you may pay {2}. If you don't, …"
    @Test
    fun havocRetcher() = card(
        name = "Havoc Retcher",
        cost = "{2}{B/R}",
        type = "Creature — Goblin Warlock",
        text = "When this creature enters, you may pay {2}. If you don't, blight 2. (To blight 2, put two -1/-1 counters on a creature you control.)",
        pt = "5/4",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(manaCost("{2}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(blights(Players.You, 2)))),
    )

    // CR 701.34a: proliferate
    @Test
    fun highExaltedVelsyn() = card(
        name = "High Exalted Velsyn",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Elf Noble",
        text = """
            Whenever High Exalted Velsyn or another Elf you control enters, each opponent blights 1. (They each put a -1/-1 counter on a creature they control.)
            Tap three untapped Elves you control: Proliferate. Activate only as a sorcery. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Elf"), you))), blights(Players.Each(PlayerFilter.Opponent), 1)),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(sub("Elf"), count = n(3)))), doing(Effect.Action(KeywordAction.Proliferate)), rules = asSorcery),
    )

    // CR 603.10a: the counters on the creature as it last existed
    @Test
    fun gloomImp() = card(
        name = "Gloom Imp",
        cost = "{2}{B/R}",
        type = "Creature — Ouphe",
        text = """
            Whenever this creature attacks, blight 1. (Put a -1/-1 counter on a creature you control.)
            Whenever a creature you control with one or more counters on it dies, exile that many cards from the top of your library. Until your next end step, you may play those cards.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(attacksSelf, blights(Players.You, 1)),
        triggered(on(EventPattern.ZoneChange(and(creature, you, ObjectFilter.HasCounter(null, atLeast = 1)), from = Zone.Battlefield, to = Zone.Graveyard)),
            move(Objects.LibraryCards(Players.You, Value.Of(NumericProperty.Counters(null), Objects.EventObject(arcanum.api.EventRole.Before),
                arcanum.api.Combine.Single), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilStep(TurnStep.End, PlayerFilter.You)))),
    )
}
