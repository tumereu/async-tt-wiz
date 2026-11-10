package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 12: Disturb (CR 702.146), Investigate (CR 701.16), Miracle (CR 702.94), Morbid (CR 207.2c ability word, a creature died = CR 700.4)
 * and Training (CR 702.149).
 */
class InrChunk12DisturbInvestigateMiracleMorbidTrainingTest {
    private val disturbLine = "(You may cast this card from your graveyard transformed for its disturb cost.)"
    private val investigateLine = "(Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")"
    private val trainingLine = "(Whenever this creature attacks with another creature with greater power, put a +1/+1 counter on this creature.)"
    private val miracleLine = "(You may cast this card for its miracle cost when you draw it if it's the first card you drew this turn.)"
    private val investigate = doing(Effect.Action(KeywordAction.Investigate(count = Value.Fixed(1))))

    /** CR 700.4: a creature died means it went from the battlefield to a graveyard. */
    private val morbid = Condition.HappenedThisTurn(
        EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
        atLeast = 1,
        measure = Measure.Events,
    )

    // CR 702.146b, 614.1a: a disturb back face's "If [this] would be put into a graveyard from anywhere, exile it instead."
    private val exileInstead = Ability.Static(
        listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        Affected.Objects(Objects.Self),
        functionsIn = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Exile, Zone.Stack, Zone.Command),
    )

    // CR 702.146a, 712 (back face), 701.17a (mill), 701.24 (shuffle), 603.6c/700.4 (dies)
    @Test
    fun covetousCastaway() = cardFaces(Layout.Transform,
        card(
            name = "Covetous Castaway",
            cost = "{1}{U}",
            type = "Creature — Human",
            pt = "1/3",
            text = """
                When this creature dies, mill three cards. (Put the top three cards of your library into your graveyard.)
                Disturb {3}{U}{U} $disturbLine
            """,
        ) to abilities(
            triggered(diesSelf, mill(3)),
            Keyword.Disturb(manaCost("{3}{U}{U}")),
        ),
        card(
            name = "Ghostly Castigator",
            cost = null,
            type = "Creature — Spirit",
            pt = "3/4",
            colorIndicator = "U",
            text = """
                Flying
                When this creature enters, you may shuffle up to three target cards from your graveyard into your library.
                If Ghostly Castigator would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(entersSelf,
                Step.May(listOf(
                    doing(Effect.Move(Objects.Target("cards"), Destination(Zone.Library))),
                    doing(Effect.Shuffle(Players.You)),
                ), player = Players.You),
                targets = listOf(slot("cards", and(cardFilter, inYourGraveyard()), min = 0, max = 3)),
            ),
            exileInstead,
        ),
    )

    // CR 702.146a, 303.4 (Aura), 702.6 (enchant), 613.1f
    @Test
    fun lanternBearer() = cardFaces(Layout.Transform,
        card(
            name = "Lantern Bearer",
            cost = "{U}",
            type = "Creature — Spirit",
            pt = "1/1",
            text = """
                Flying
                Disturb {2}{U} $disturbLine
            """,
        ) to abilities(Keyword.Flying, Keyword.Disturb(manaCost("{2}{U}"))),
        card(
            name = "Lanterns' Lift",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "U",
            text = """
                Enchant creature
                Enchanted creature gets +1/+1 and has flying.
                If Lanterns' Lift would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            Keyword.Enchant(TargetKind.Objects(creature)),
            static(pt(1, 1), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
            exileInstead,
        ),
    )

    // CR 702.146a, 603.2 (trigger on another creature entering), 119.1 (gain life), 603.10a (leaves the battlefield looks back)
    @Test
    fun lunarchVeteran() = cardFaces(Layout.Transform,
        card(
            name = "Lunarch Veteran",
            cost = "{W}",
            type = "Creature — Human Cleric",
            pt = "1/1",
            text = """
                Whenever another creature you control enters, you gain 1 life.
                Disturb {1}{W} $disturbLine
            """,
        ) to abilities(
            triggered(enters(and(another, creature, you)), gainLife(1)),
            Keyword.Disturb(manaCost("{1}{W}")),
        ),
        card(
            name = "Luminous Phantom",
            cost = null,
            type = "Creature — Spirit Cleric",
            pt = "1/1",
            colorIndicator = "W",
            text = """
                Flying
                Whenever another creature you control leaves the battlefield, you gain 1 life.
                If Luminous Phantom would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield)), gainLife(1)),
            exileInstead,
        ),
    )

    // CR 702.146a, 702.4 (double strike), 303.4
    @Test
    fun twinbladeGeist() = cardFaces(Layout.Transform,
        card(
            name = "Twinblade Geist",
            cost = "{1}{W}",
            type = "Creature — Spirit Warrior",
            pt = "1/1",
            text = """
                Double strike
                Disturb {2}{W} $disturbLine
            """,
        ) to abilities(Keyword.DoubleStrike, Keyword.Disturb(manaCost("{2}{W}"))),
        card(
            name = "Twinblade Invocation",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "W",
            text = """
                Enchant creature
                Enchanted creature has double strike.
                If Twinblade Invocation would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            Keyword.Enchant(TargetKind.Objects(creature)),
            static(has(Keyword.DoubleStrike), affects = Objects.AttachedTo(Objects.Self)),
            exileInstead,
        ),
    )

    // CR 701.16a (investigate), 603.2 (upkeep trigger), 502.1; {T}, sacrifice three Clues (CR 602.2) then a tutor (CR 701.23, 701.24)
    @Test
    fun tamiyosJournalLike() = card(
        name = "Chronicler's Ledger",
        cost = "{5}",
        type = "Legendary Artifact — Book",
        text = """
            At the beginning of your upkeep, investigate. $investigateLine
            {T}, Sacrifice three Clues: Search your library for a card, put that card into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), investigate),
        activated(cost(tap, CostPart.Sacrifice(CostObjects.Choose(sub("Clue"), count = n(3)))),
            search(cardFilter),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle),
    )

    // CR 207.2c (landfall), 701.16a, 701.21 (sacrifice), 122.1 (counters)
    @Test
    fun tirelessTracker() = card(
        name = "Tireless Stalker",
        cost = "{2}{G}",
        type = "Creature — Human Scout",
        pt = "3/2",
        text = """
            Landfall — Whenever a land you control enters, investigate. $investigateLine
            Whenever you sacrifice a Clue, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        triggered(landfall, investigate, abilityWord = "Landfall"),
        triggered(on(EventPattern.Sacrifice(and(sub("Clue"), you))), counters(Objects.Self)),
    )

    // CR 700.4-free: a nontoken creature dying (CR 700.4), 701.16a, 111 (tokens), 205.3 (Human Soldier)
    @Test
    fun ulvenwaldMysteries() = card(
        name = "Wildwood Enigmas",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            Whenever a nontoken creature you control dies, investigate. $investigateLine
            Whenever you sacrifice a Clue, create a 1/1 white Human Soldier creature token.
        """,
    ) parsesTo abilities(
        triggered(dies(and(not(token), creature, you)), investigate),
        triggered(on(EventPattern.Sacrifice(and(sub("Clue"), you))), create(creatureToken("Human Soldier", listOf(Color.White), 1, 1))),
    )

    // CR 702.94a (miracle), 701.8 (discard), 121.1 (draw)
    @Test
    fun reforgeTheSoul() = card(
        name = "Remake the Spirit",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            Each player discards their hand, then draws seven cards.
            Miracle {1}{R} $miracleLine
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Discard(Players.Each(PlayerFilter.Any), objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)), count = Value.Fixed(1), filter = ObjectFilter.Any)),
            draw(7, Players.Each(PlayerFilter.Any)),
        ),
        Keyword.Miracle(manaCost("{1}{R}")),
    )

    // CR 500.7 (extra turn: after this one), 702.94a, 400.7 / 608.2k ("exile Temporal Mastery" is this spell)
    @Test
    fun temporalMastery() = card(
        name = "Eternal Command",
        cost = "{5}{U}{U}",
        type = "Sorcery",
        text = """
            Take an extra turn after this one. Exile Eternal Command.
            Miracle {1}{U} $miracleLine
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ExtraTurn(Players.You)), move(Objects.Self, Zone.Exile)),
        Keyword.Miracle(manaCost("{1}{U}")),
    )

    // CR 207.2c (Morbid), 700.4, 614.1c (enters with counters), 702.19 (trample), 122.6
    @Test
    fun festerhideBoar() = card(
        name = "Mudhide Boar",
        cost = "{3}{G}",
        type = "Creature — Boar",
        pt = "3/3",
        text = """
            Trample
            Morbid — This creature enters with two +1/+1 counters on it if a creature died this turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, Value.Fixed(2))))),
            affects = Affected.Objects(Objects.Self),
            condition = morbid,
            abilityWord = "Morbid",
        ),
    )

    // CR 207.2c, 700.4, 603.4 (intervening if), 613.4c (-4/-4 until end of turn)
    @Test
    fun morkrutBanshee() = card(
        name = "Gloomrut Banshee",
        cost = "{3}{B}{B}",
        type = "Creature — Spirit",
        pt = "4/4",
        text = "Morbid — When this creature enters, if a creature died this turn, target creature gets -4/-4 until end of turn.",
    ) parsesTo abilities(
        triggered(entersSelf, pump(target("creature"), -4, -4), targets = listOf(slot("creature", creature)),
            interveningIf = morbid, abilityWord = "Morbid"),
    )

    // CR 207.2c, 602.5b (activate only if), 701.26 (tap two untapped creatures as cost, 118.3), 111.1
    @Test
    fun skirsdagHighPriest() = card(
        name = "Skirvale High Cleric",
        cost = "{1}{B}",
        type = "Creature — Human Cleric",
        pt = "1/2",
        text = "Morbid — {T}, Tap two untapped creatures you control: Create a 5/5 black Demon creature token with flying. Activate only if a creature died this turn.",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(creature, count = n(2)))),
            create(creatureToken("Demon", listOf(Color.Black), 5, 5, listOf(Keyword.Flying))),
            rules = anyTime.copy(condition = morbid)).copy(abilityWord = "Morbid"),
    )

    // CR 207.2c, 700.4, 614.1a ("instead" replaces the -1/-1 with -13/-13), 613.4c
    @Test
    fun tragicSlip() = card(
        name = "Grim Stumble",
        cost = "{B}",
        type = "Instant",
        text = """
            Target creature gets -1/-1 until end of turn.
            Morbid — That creature gets -13/-13 until end of turn instead if a creature died this turn.
        """,
    ) parsesTo abilities(
        spell(
            Step.If(condition = morbid, then = listOf(pump(target("creature"), -13, -13)), otherwise = listOf(pump(target("creature"), -1, -1))),
            targets = listOf(slot("creature", creature)),
        ).copy(abilityWord = "Morbid"),
    )

    // CR 702.149a (training), 122.1 (remove counters as cost), 701.8 (destroy), 118.3
    @Test
    fun hopefulInitiate() = card(
        name = "Eager Novice",
        cost = "{W}",
        type = "Creature — Human Warlock",
        pt = "1/2",
        text = """
            Training $trainingLine
            {2}{W}, Remove two +1/+1 counters from among creatures you control: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        activated(cost(mana("{2}{W}"), CostPart.RemoveCounters(p1p1, n(2), CostObjects.Choose(and(creature, you), count = null), distributed = true)),
            destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    // CR 702.149a, 111.10 (token with a keyword), 601.2 (cast trigger)
    @Test
    fun torensFistOfTheAngels() = card(
        name = "Tarens, Hand of the Seraphs",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "2/2",
        text = """
            Training $trainingLine
            Whenever you cast a creature spell, create a 1/1 green and white Human Soldier creature token with training.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        triggered(on(EventPattern.Cast(creature, by = PlayerFilter.You)),
            create(creatureToken("Human Soldier", listOf(Color.Green, Color.White), 1, 1, listOf(Keyword.Training)))),
    )
}
