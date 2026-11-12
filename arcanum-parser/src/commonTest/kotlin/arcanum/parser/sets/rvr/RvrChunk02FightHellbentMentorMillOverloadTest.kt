package arcanum.parser.sets.rvr

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk02FightHellbentMentorMillOverloadTest {
    private val mentor = Keyword.Mentor
    private val handOf = and(cardFilter, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))

    // ---- Fight

    // CR 601.2f: the reduction looks at the spell's target you control; CR 701.14a: fight
    @Test
    fun colossalScuffle() = card(
        name = "Colossal Scuffle",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets a creature you control with a +1/+1 counter on it.
            Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
        spell(
            doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, notYours))),
        ),
    )

    // ---- Hellbent

    // CR 207.2c (hellbent is an ability word), 101.2 / 615.12: can't be countered, its damage can't be prevented while you have no cards in hand
    @Test
    fun hellblazeTorrent() = card(
        name = "Hellblaze Torrent",
        cost = "{X}{R}",
        type = "Sorcery",
        text = """
            Hellblaze Torrent deals X damage to any target. If a creature dealt damage this way would die this turn, exile it instead.
            Hellbent — If you have no cards in hand, this spell can't be countered and the damage can't be prevented.
        """,
    ) parsesTo abilities(
        spell(
            damage(Value.Announced(name = "X"), toAny),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(and(ObjectFilter.Is(target("any")), creature), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(anyTarget),
            announced = listOf("X"),
        ),
        static(Mechanic.CantBeCountered, Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter())),
            condition = hellbent).copy(abilityWord = "Hellbent"),
    )

    // CR 701.20a: the revealed card is the one "that card" names; hellbent replaces the whole first paragraph when it holds (CR 608.2c)
    @Test
    fun pitbornTutor() = card(
        name = "Pitborn Tutor",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Reveal a card from your hand. Search your library for a card with the same name as that card, reveal it, put it into your hand, then shuffle.
            Hellbent — If you have no cards in hand, instead search your library for a card, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        spell(
            ifThen(hellbent,
                search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle,
                otherwise = listOf(
                    Step.Choose(Choice.Objects(handOf, n(1), n(1)), bind = "revealed", chooser = Players.You),
                    doing(Effect.Reveal(Objects.Bound("revealed"))),
                    search(and(cardFilter, ObjectFilter.SameNameAs(Objects.Bound("revealed"))), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle,
                ),
            ),
        ).copy(abilityWord = "Hellbent"),
    )

    // CR 611.3a: double strike only while the hellbent condition holds
    @Test
    fun cinderpitWyrm() = card(
        name = "Cinderpit Wyrm",
        cost = "{2}{R}{R}",
        type = "Creature — Dragon",
        pt = "3/3",
        text = """
            {R}{R}: This creature gains flying until end of turn.
            {R}: This creature gets +1/+0 until end of turn.
            Hellbent — This creature has double strike as long as you have no cards in hand.
        """,
    ) parsesTo abilities(
        activated(manaCost("{R}{R}"), gains(Objects.Self, Keyword.Flying)),
        activated(manaCost("{R}"), pump(Objects.Self, 1, 0)),
        static(has(Keyword.DoubleStrike), condition = hellbent).copy(abilityWord = "Hellbent"),
    )

    // ---- Mentor (CR 702.134)

    // CR 702.134a; the "if it's red/white" checks happen as the ability resolves (CR 608.2c); "up to one" may choose none (CR 115.1)
    @Test
    fun valeriaParagonOfOrder() = card(
        name = "Valeria, Paragon of Order",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Angel",
        pt = "2/5",
        text = """
            Flying
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
            At the beginning of combat on your turn, choose up to one target creature you control. Until end of turn, that creature gets +2/+0, gains trample if it's red, and gains vigilance if it's white.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        mentor,
        triggered(beginningOfCombatOnYourTurn,
            pump(target("creature"), 2, 0),
            ifThen(Condition.ObjectMatches(target("creature"), ObjectFilter.OfColor(Color.Red)), gains(target("creature"), Keyword.Trample)),
            ifThen(Condition.ObjectMatches(target("creature"), ObjectFilter.OfColor(Color.White)), gains(target("creature"), Keyword.Vigilance)),
            targets = listOf(slot("creature", creatureYouControl, min = 0)),
        ),
    )

    // CR 508.1d: the token must attack this combat if able; haste lets it (CR 302.6)
    @Test
    fun hordeDrillmaster() = card(
        name = "Horde Drillmaster",
        cost = "{2}{R}",
        type = "Creature — Goblin Soldier",
        pt = "2/2",
        text = """
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
            At the beginning of combat on your turn, create a 1/1 red Goblin creature token. That token gains haste until end of turn and attacks this combat if able.
        """,
    ) parsesTo abilities(
        mentor,
        triggered(beginningOfCombatOnYourTurn,
            create(creatureToken("Goblin", listOf(Color.Red), 1, 1), bind = "token"),
            gains(Objects.Bound("token"), Keyword.Haste),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MustAttack()), arcanum.api.Affected.Objects(Objects.Bound("token")), Duration.EndOfCombat)),
        ),
    )

    // CR 702.7 (first strike), 702.134a (mentor)
    @Test
    fun skyforgeStalwart() = card(
        name = "Skyforge Stalwart",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            First strike (This creature deals combat damage before creatures without first strike.)
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
        """,
    ) parsesTo abilities(Keyword.FirstStrike, mentor)

    // CR 615.1a: a prevention effect for noncombat damage to its controller's other creatures
    @Test
    fun toricVanguardsEdge() = card(
        name = "Toric, Vanguard's Edge",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/2",
        text = """
            Haste
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
            Prevent all noncombat damage that would be dealt to other creatures you control.
            {R}{W}: Toric gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        mentor,
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = and(another, creature, you)), combat = false))),
        activated(manaCost("{R}{W}"), gains(Objects.Self, Keyword.FirstStrike)),
    )

    // CR 120.3, 603.2: that much damage, dealt by this creature, to the target player
    @Test
    fun truebrandCommander() = card(
        name = "Truebrand Commander",
        cost = "{R}{R}{W}{W}",
        type = "Creature — Human Knight",
        pt = "4/3",
        text = """
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
            Whenever this creature is dealt damage, it deals that much damage to target player.
        """,
    ) parsesTo abilities(
        mentor,
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
            damage(Value.EventAmount, Recipients(players = Players.Target("player"))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 506.5: it attacks or blocks only together with another creature
    @Test
    fun garrisonShieldbearer() = card(
        name = "Garrison Shieldbearer",
        cost = "{2}{R}",
        type = "Creature — Human Soldier",
        pt = "3/3",
        text = """
            Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
            This creature can't attack or block alone.
        """,
    ) parsesTo abilities(
        mentor,
        static(Mechanic.CantAttackAlone, Mechanic.CantBlockAlone),
    )

    // ---- Mill (CR 701.17)

    // CR 614.1a: the mill event is replaced by one of twice as many cards
    @Test
    fun orvacTheVerbose() = card(
        name = "Orvac the Verbose",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Advisor",
        pt = "1/4",
        text = "If an opponent would mill one or more cards, they mill twice that many cards instead. (To mill a card, a player puts the top card of their library into their graveyard.)",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Mill(PlayerFilter.Opponent), modify = listOf(Modification.MultiplyAmount(2)))),
    )

    // CR 608.2d: the land card is chosen as the ability resolves, no target
    @Test
    fun gloomcapShaman() = card(
        name = "Gloomcap Shaman",
        cost = "{B}{G}",
        type = "Creature — Elf Shaman",
        pt = "3/1",
        text = "When this creature enters, mill three cards. You may put a land card from your graveyard on top of your library. (To mill a card, put the top card of your library into your graveyard.)",
    ) parsesTo abilities(
        triggered(entersSelf,
            mill(3),
            Step.Choose(Choice.Objects(and(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Library, position = 0),
        ),
    )

    // Audit (incorrect) was a false alarm: "Tap four untapped Advisors you control" chooses among untapped permanents its controller controls (CR 118.3,
    // 601.2h), the canonical cost form; CR 100.2a: any number in a deck
    @Test
    fun tirelessSupplicants() = card(
        name = "Tireless Supplicants",
        cost = "{1}{U}",
        type = "Creature — Human Advisor",
        pt = "1/3",
        text = """
            {1}, {T}: Target player mills a card. (They put the top card of their library into their graveyard.)
            Tap four untapped Advisors you control: Target player mills twelve cards.
            A deck can have any number of cards named Tireless Supplicants.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), mill(1, Players.Target("player")), targets = listOf(targetPlayer)),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(sub("Advisor"), count = n(4)))), mill(12, Players.Target("player")), targets = listOf(targetPlayer)),
        static(Mechanic.AnyNumberInDeck),
    )

    // ---- Overload (CR 702.96)

    // CR 707.12: each exiled card is copied and the copy may be cast; overloaded, every instant and sorcery card (CR 702.96b)
    @Test
    fun zirriksMastery() = card(
        name = "Zirrik's Mastery",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Exile target card that's an instant or sorcery from your graveyard. For each card exiled this way, copy it, and you may cast the copy without paying its mana cost. Exile Zirrik's Mastery.
            Overload {5}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Exile, postBind = "exiled"),
            castCopies,
            move(Objects.Self, Zone.Exile),
            targets = listOf(slot("card", instantOrSorceryInYourGraveyard)),
        ),
        overload("{5}{R}{R}{R}",
            move(Objects.Each(instantOrSorceryInYourGraveyard), Zone.Exile, postBind = "exiled"),
            castCopies,
            move(Objects.Self, Zone.Exile),
        ),
    )

    private val instantOrSorceryInYourGraveyard = inYourGraveyard(cardFilter, or(instant, sorcery))
    private val castCopies = Step.ForEach(Each.Objects(Objects.Bound("exiled")), bind = "exiled card", steps = listOf(
        doing(Effect.CopyCard(Objects.Bound("exiled card"), Players.You, bind = "copy")),
        Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You),
    ))
}
