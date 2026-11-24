package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 05: Auras (CR 303.4): what they enchant (CR 702.5a), statics on the enchanted permanent or player (CR 303.4e), and their
 * triggers.
 */
class VowChunk05EnchantTest {
    private val enchantedPlayer = Players.AttachedTo(Objects.Self)
    private val enchantPlayer = Keyword.Enchant(TargetKind.Players(PlayerFilter.Any))
    private val spirit = creatureToken("Spirit", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying))

    // CR 506.3, 508.1b (creatures attacking that player, not a planeswalker or battle), 702.19c (trample); CR 510.3a, 611.2a, 609.4b
    // (the damaging creature's controller may play the card until end of turn, spending mana as though it were any colour)
    @Test
    fun curseOfOpenDoors() = card(
        name = "Curse of Open Doors",
        cost = "{2}{R}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            Creatures attacking enchanted player have trample.
            Whenever a creature deals combat damage to enchanted player, that player exiles the top card of their library. Until end of turn, that creature's controller may play that card and they may spend mana as though it were mana of any color to cast that spell.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, attacking,
            ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.Is(enchantedPlayer))),
            ObjectFilter.Where(Condition.Compare(Value.Count(Objects.AttackedBy(Objects.Tested)), Comparison.Equal, n(0)))))),
        triggered(on(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.Is(enchantedPlayer)), combat = true)),
            move(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.ForEach(Each.Players(Players.ControllerOf(Objects.EventObject(EventRole.Source))), bind = "player", together = true, steps = listOf(
                untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play,
                    holder = PlayerFilter.Is(Players.Bound("player")), manaAsAnyColorOnly = true))))),
    )

    // CR 400.7, 603.10a (the Aura is a new object in the graveyard), 712.14a (it returns back face up), 303.4f (attached to the player the
    // effect names), 614.1c (creatures enter tapped)
    @Test
    fun gleamingFavor() = cardFaces(Layout.Transform,
        card(
            name = "Gleaming Favor",
            cost = "{W}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                Enchanted creature gets +1/+0 and has vigilance.
                When enchanted creature dies, return this card to the battlefield transformed under your control attached to target opponent.
            """,
        ) to abilities(
            enchant(creature),
            static(pt(1, 0), has(Keyword.Vigilance), affects = enchanted),
            triggered(dies(ObjectFilter.Is(enchanted)),
                Step.Do(Effect.Move(Objects.SelfAfter, Destination(Zone.Battlefield, controller = Players.You,
                    attachedTo = Recipients(players = Players.Target("opponent")), transformed = true))),
                targets = listOf(targetOpponent)),
        ),
        card(
            name = "Gleaming Fetters",
            cost = null,
            type = "Enchantment — Aura Curse",
            colorIndicator = "W",
            text = """
                Enchant player
                Creatures enchanted player controls enter tapped.
            """,
        ) to abilities(
            enchantPlayer,
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(enchantedPlayer))),
                to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self)),
        ),
    )

    // CR 702.8a (flash), 702.11a (hexproof until end of turn), 303.4a
    @Test
    fun cocoonOfShelter() = card(
        name = "Cocoon of Shelter",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature you control
            When this Aura enters, enchanted creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
            Enchanted creature gets +1/+1.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(creatureYouControl),
        triggered(entersSelf, gains(enchanted, Keyword.Hexproof)),
        static(pt(1, 1), affects = enchanted),
    )

    // CR 701.17a (mill), 611.3a (X counted continuously)
    @Test
    fun dreadOfTheGrave() = card(
        name = "Dread of the Grave",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, mill two cards. (Put the top two cards of your library into your graveyard.)
            Enchanted creature gets -X/-0, where X is the number of cards in your graveyard.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(entersSelf, mill(2)),
        static(Mechanic.ModifyPowerToughness(Value.Times(count(inYourGraveyard(cardFilter)), -1), n(0)), affects = enchanted),
    )

    // CR 611.3a: each static applies while its condition holds; "Otherwise" is the opposite condition
    @Test
    fun boonOfTheBite() = card(
        name = "Boon of the Bite",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 as long as it's a Vampire. Otherwise, it gets -2/-2.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 2), affects = enchanted, condition = Condition.ObjectMatches(enchanted, sub("Vampire"))),
        static(pt(-2, -2), affects = enchanted, condition = Condition.Not(Condition.ObjectMatches(enchanted, sub("Vampire")))),
    )

    // CR 702.5a (one list of what it can enchant), 611.3a; audit (unsure) on "two mana of any one color": ManaKind.Choose(2) without
    // `combination` is two mana of one chosen colour (CR 106.1b) - a false alarm
    @Test
    fun wildwoodClasp() = card(
        name = "Wildwood Clasp",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature or land
            As long as enchanted permanent is a creature, it gets +2/+2.
            As long as enchanted permanent is a land, it has "{T}: Add two mana of any one color."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(or(creature, land))),
        static(pt(2, 2), affects = enchanted, condition = Condition.ObjectMatches(enchanted, creature)),
        static(has(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), combination = false)), Players.You)))),
            affects = enchanted, condition = Condition.ObjectMatches(enchanted, land)),
    )

    // CR 603.2 (a granted trigger of the creature; "this creature" is the creature, CR 113.6), 111.1
    @Test
    fun nursingPresence() = card(
        name = "Nursing Presence",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has "Whenever a creature you control enters, this creature gets +1/+1 until end of turn."
            When this Aura enters, create a 1/1 white Spirit creature token with flying.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(has(triggered(enters(creatureYouControl), pump(Objects.Self, 1, 1))), affects = enchanted),
        triggered(entersSelf, create(spirit)),
    )

    // CR 603.6a, 613.4c
    @Test
    fun shelteringCanopy() = card(
        name = "Sheltering Canopy",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, draw a card.
            Enchanted creature gets +1/+3.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(entersSelf, draw(1)),
        static(pt(1, 3), affects = enchanted),
    )

    // CR 508.1c, 509.1b (can't attack or block), 602.1, 111.10o (Blood)
    @Test
    fun angelicConfinement() = card(
        name = "Angelic Confinement",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
            {4}{W}: Exile enchanted creature. Create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = enchanted),
        activated(cost(mana("{4}{W}")), move(enchanted, Zone.Exile), create(blood)),
    )

    // CR 702.8a, 604.1 (flash while you control a Spirit), 603.2 + 601.2i (a cast trigger), 115.6 (up to two targets), 303.4a
    @Test
    fun spectralDeliverance() = card(
        name = "Spectral Deliverance",
        cost = "{3}{W}",
        type = "Enchantment — Aura",
        text = """
            This spell has flash as long as you control a Spirit.
            When you cast this spell, tap up to two target creatures you don't control.
            Enchant creature you control
            Enchanted creature gets +1/+2.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flash), condition = Condition.Exists(and(sub("Spirit"), you), 1)),
        triggered(youCast(ObjectFilter.Is(Objects.Self)), tapIt(target("creatures")),
            targets = listOf(slot("creatures", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0, max = 2))),
        enchant(creatureYouControl),
        static(pt(1, 2), affects = enchanted),
    )
}
