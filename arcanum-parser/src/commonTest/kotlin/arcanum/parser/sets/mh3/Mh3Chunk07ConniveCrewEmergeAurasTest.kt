package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.Doubled
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.Combine
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MH3 chunk 07: connive (also connive X), crew Vehicles, doubling (counters, power), emerge (also "emerge from artifact" and emerge given to
 * spells) and Auras (umbra armor, enchant player with an additional beginning phase, losing all abilities).
 */
class Mh3Chunk07ConniveCrewEmergeAurasTest {
    private val entersEvent = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val yourHand = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))

    // CR 701.50d: connive X; the number of creatures that died this turn, anyone's (CR 700.4)
    @Test
    fun informantsCellar() = card(
        name = "Informant's Cellar",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Swamp.
            {T}: Add {B}.
            {B}, {T}: Target creature you control connives X, where X is the number of creatures that died this turn. (Draw X cards, then discard X cards. Put a +1/+1 counter on that creature for each nonland card discarded this way.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(entersEvent, modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(Condition.Exists(and(sub("Swamp"), you), atLeast = 1))),
        addOne("{B}"),
        activated(cost(mana("{B}"), tap),
            connive(target("creature"), Value.EventsThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), Measure.Objects)),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 707.9a: the copy's exception adds the ability, part of its copiable values
    @Test
    fun mimicPickpocket() = card(
        name = "Mimic Pickpocket",
        cost = "{2}{U}{U}",
        type = "Creature — Shapeshifter Rogue",
        pt = "0/0",
        text = """
            You may have this creature enter as a copy of any creature on the battlefield, except it has "Whenever this creature attacks, it connives." (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.EntersAsCopy(creature,
            listOf(CopyException.AddAbilities(listOf(triggered(attacksSelf, connive())))), optional = true)))),
    )

    // CR 701.50a; one ability with two events (CR 603.2)
    @Test
    fun shiftyWatchman() = card(
        name = "Shifty Watchman",
        cost = "{3}{B}",
        type = "Creature — Human Knight Rogue",
        pt = "2/3",
        text = """
            Menace
            Whenever this creature enters or attacks, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersOrAttacksSelf, connive()),
    )

    // CR 107.14 (energy), 301.7b (a Vehicle becoming a creature), 702.122a (crew)
    @Test
    fun tailoredWagon() = card(
        name = "Tailored Wagon",
        cost = "{3}{U}",
        type = "Artifact — Vehicle",
        pt = "5/6",
        text = """
            {T}: You get {E}{E} (two energy counters).
            {T}, Pay {E}{E}: Tap target creature.
            {T}, Pay {E}{E}{E}: Draw a card.
            Pay {E}{E}{E}{E}: This Vehicle becomes an artifact creature until end of turn.
            Crew 4
        """,
    ) parsesTo abilities(
        activated(cost(tap), getEnergy(2)),
        activated(cost(tap, payEnergy(2)), tapIt(target("creature")), targets = listOf(slot("creature", creature))),
        activated(cost(tap, payEnergy(3)), draw(1)),
        activated(cost(payEnergy(4)), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)))),
        Keyword.Crew(4),
    )

    // CR 701.21a: sacrificing another artifact or creature as a cost
    @Test
    fun grimcart() = card(
        name = "Grimcart",
        cost = "{2}{B}",
        type = "Artifact — Vehicle",
        pt = "3/3",
        text = """
            Menace
            {1}, Sacrifice another artifact or creature: Put a +1/+1 counter on this Vehicle.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(mana("{1}"), sacrificeA(and(another, or(artifact, creature)))), counters(Objects.Self)),
        Keyword.Crew(1),
    )

    // CR 701.10e (double each kind of counter), 707.2 and 303.4f, 301.5c (copies created attached to the creature); modified, CR 700.9
    @Test
    fun velkaStrandmoor() = card(
        name = "Velka Strandmoor, Skywarden",
        cost = "{2}{W}{U}{B}",
        type = "Legendary Creature — Human Knight",
        pt = "4/4",
        text = """
            Flying, lifelink
            Ward—Discard a card.
            Whenever a modified creature you control attacks, double the number of each kind of counter on it. Then for each nontoken permanent attached to it, create a token that's a copy of that permanent attached to that creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        Keyword.Ward(cost(discardACard)),
        triggered(on(EventPattern.Attack(and(creature, you, ObjectFilter.Modified))),
            doing(Effect.Action(KeywordAction.Double(Doubled.Counters(Objects.EventObject(EventRole.Object), null)))),
            Step.ForEach(Each.Objects(Objects.Each(and(not(token), permanentFilter, ObjectFilter.Attached(to = Objects.EventObject(EventRole.Object))))), "attached",
                listOf(doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("attached")), n(1), Players.You,
                    attachedTo = Recipients(objects = Objects.EventObject(EventRole.Object))))))),
    )

    // CR 701.10e; overload changes "target" to "each" (CR 702.96b)
    @Test
    fun tusksOfTheGrove() = card(
        name = "Tusks of the Grove",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on target creature you control, then double the number of +1/+1 counters on each creature that had a +1/+1 counter put on it this way.
            Overload {4}{G}{G} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(counters(target("creature")), doing(Effect.Action(KeywordAction.Double(Doubled.Counters(target("creature"), p1p1)))),
            targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Overload(manaCost("{4}{G}{G}"), Instructions(steps = listOf(counters(Objects.Each(creatureYouControl)),
            doing(Effect.Action(KeywordAction.Double(Doubled.Counters(Objects.Each(creatureYouControl), p1p1))))))),
    )

    // CR 701.10b: doubling power gives +X/+0, X its power as it resolves; both effects until end of turn
    @Test
    fun cohortCommand() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Cohort Command",
            cost = "{1}{R/W}",
            type = "Instant",
            text = "Until end of turn, double target creature's power and it gains first strike.",
        ) to abilities(
            spell(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), n(0))),
                gains(target("creature"), Keyword.FirstStrike), targets = listOf(slot("creature", creature))),
        ),
        card(
            name = "Cohort Keep",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {R} or {W}.
            """,
        ) to abilities(entersTapped, addEither("R", "W")),
    )

    // CR 702.119b (emerge from artifact); CR 608.2g: a spell cast from among the exiled cards while the ability resolves
    @Test
    fun clawmonstrosity() = card(
        name = "Clawmonstrosity",
        cost = "{4}{B}{B}",
        type = "Creature — Crab Demon",
        pt = "5/5",
        text = """
            Emerge from artifact {5}{B}{B} (You may cast this spell by sacrificing an artifact and paying the emerge cost reduced by that artifact's mana value.)
            When this creature enters, target opponent exiles the top card of their library, a card at random from their graveyard, and a card at random from their hand. You may cast a spell from among cards exiled this way without paying its mana cost.
        """,
    ) parsesTo run {
        val opponent = Players.Target("opponent")
        fun theirs(zone: Zone) = Objects.Each(and(inZone(zone), ObjectFilter.OwnedBy(PlayerFilter.Is(opponent))))
        abilities(
            Keyword.Emerge(manaCost("{5}{B}{B}"), from = artifact),
            triggered(entersSelf,
                move(Objects.LibraryCards(opponent, n(1), fromTop = true), Zone.Exile, postBind = "exiled 0"),
                move(Objects.Random(theirs(Zone.Graveyard), n(1)), Zone.Exile, postBind = "exiled 1"),
                move(Objects.Random(theirs(Zone.Hand), n(1)), Zone.Exile, postBind = "exiled 2"),
                Step.Choose(arcanum.api.Choice.Objects(not(land), min = n(0), max = n(1),
                    from = Objects.Union(listOf(Objects.Bound("exiled 0"), Objects.Bound("exiled 1"), Objects.Bound("exiled 2")))), bind = "cast", chooser = Players.You),
                doing(Effect.Cast(Objects.Bound("cast"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
                targets = listOf(targetOpponent)),
        )
    }

    // CR 702.119a (emerge given to spells, its cost their mana cost); "you may exile your hand. If you do" (CR 118.12)
    @Test
    fun vorlathRisingNullwyrm() = card(
        name = "Vorlath, Rising Nullwyrm",
        cost = "{9}",
        type = "Legendary Creature — Eldrazi Dragon",
        pt = "6/6",
        text = """
            Emerge {6}{R}{R} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, you may exile your hand. If you do, draw three cards.
            Flying
            Each creature spell you cast has emerge. The emerge cost is equal to its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Emerge(manaCost("{6}{R}{R}")),
        triggered(youCastThis, Step.Pay(cost(CostPart.Exile(CostObjects.These(Objects.Each(yourHand)), from = Zone.Hand)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(3)))),
        Keyword.Flying,
        static(has(Keyword.Emerge(Cost(listOf(CostPart.ItsManaCost)))), affects = Objects.Each(and(creature, spellFilter, you))),
    )

    // CR 702.119a; stun counters (CR 122.1d)
    @Test
    fun warpedEnigmist() = card(
        name = "Warped Enigmist",
        cost = "{8}",
        type = "Creature — Eldrazi Sphinx",
        pt = "5/5",
        text = """
            Emerge {5}{C}{U} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, tap up to two target permanents. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Flying
        """,
    ) parsesTo abilities(
        Keyword.Emerge(manaCost("{5}{C}{U}")),
        triggered(youCastThis, tapIt(target("permanent")), counters(target("permanent"), 1, CounterKind.Stun),
            targets = listOf(slot("permanent", permanentFilter, min = 0, max = 2))),
        Keyword.Flying,
    )

    // CR 611.3a: each static ability applies while its side of the condition holds; umbra armor (CR 702.89a)
    @Test
    fun houndShade() = card(
        name = "Hound Shade",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            As long as another player controls enchanted creature, it can't attack or block. Otherwise, this Aura has umbra armor. (If enchanted creature would be destroyed, instead remove all damage from it and destroy this Aura.)
        """,
    ) parsesTo run {
        val theirs = Condition.ObjectMatches(enchanted, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))
        abilities(
            Keyword.Flash,
            enchantCreature,
            static(Mechanic.CantAttack(), Mechanic.CantBlock(attackers = ObjectFilter.Any), affects = enchanted, condition = theirs),
            static(has(Keyword.UmbraArmor), condition = Condition.Not(theirs)),
        )
    }

    // CR 303.4 (an Aura enchanting a player), 500.8 (an additional phase), 505.1a (each postcombat main phase)
    @Test
    fun echoOfTheTwinDawn() = card(
        name = "Echo of the Twin Dawn",
        cost = "{4}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant player
            At the beginning of each of enchanted player's postcombat main phases, there is an additional beginning phase after this phase. (The end step happens after the added untap, upkeep, and draw steps.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Players(PlayerFilter.Any)),
        triggered(stepBegins(TurnStep.PostcombatMain, PlayerFilter.Is(Players.AttachedTo(Objects.Self))), doing(Effect.AdditionalPhase(TurnStep.Untap))),
    )

    // CR 613.1f (layer 6), 613.4b (layer 7b)
    @Test
    fun totalUnimportance() = card(
        name = "Total Unimportance",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature loses all abilities and has base power and toughness 1/1.
            {2}{C}: Exile enchanted creature.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(Mechanic.LoseAbilities(), Mechanic.SetPowerToughness(n(1), n(1)), affects = enchanted),
        activated(cost(mana("{2}{C}")), move(enchanted, Zone.Exile)),
    )

    // CR 702.40a (storm copies of an Aura spell become tokens, 707.10c); layers 4, 5, 6, 7b
    @Test
    fun toadfallTorrent() = card(
        name = "Toadfall Torrent",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies. Copies become tokens.)
            Enchant creature
            Enchanted creature loses all abilities and is a blue Frog creature with base power and toughness 1/1.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Storm,
        enchantCreature,
        static(
            Mechanic.LoseAbilities(),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue))),
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Frog"))), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)),
            affects = enchanted,
        ),
    )

    // CR 712.8b: a modal double-faced card's front face is an Aura
    @Test
    fun crystalwingFavor() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Crystalwing Favor",
            cost = "{3}{W/B}{W/B}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                Enchanted creature gets +2/+2 and has flying and lifelink.
            """,
        ) to abilities(enchantCreature, static(pt(2, 2), has(Keyword.Flying, Keyword.Lifelink), affects = enchanted)),
        card(
            name = "Time-Blessed Shrine",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {W} or {B}.
            """,
        ) to abilities(entersTapped, addEither("W", "B")),
    )

    // CR 700.9 (enchant modified creature), 702.89a (umbra armor)
    @Test
    fun lynxShade() = card(
        name = "Lynx Shade",
        cost = "{G}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant modified creature (Equipment, Auras its controller controls, and counters are modifications.)
            Enchanted creature gets +3/+3 and has reach and vigilance.
            Umbra armor (If enchanted creature would be destroyed, instead remove all damage from it and destroy this Aura.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(and(creature, ObjectFilter.Modified))),
        static(pt(3, 3), has(Keyword.Reach, Keyword.Vigilance), affects = enchanted),
        Keyword.UmbraArmor,
    )

    // CR 613.4c: +1/+1 for each creature and/or enchantment you control (an object of both types counts once)
    @Test
    fun mightOfTheReaping() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Might of the Reaping",
            cost = "{2}{G/W}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                Enchanted creature gets +1/+1 for each creature and/or enchantment you control.
            """,
        ) to run {
            val each = count(and(or(creature, enchantment), you))
            abilities(enchantCreature, static(Mechanic.ModifyPowerToughness(each, each), affects = enchanted))
        },
        card(
            name = "Refuge of the Reaping",
            cost = null,
            type = "Land",
            text = """
                This land enters tapped.
                {T}: Add {G} or {W}.
            """,
        ) to abilities(entersTapped, addEither("G", "W")),
    )
}
