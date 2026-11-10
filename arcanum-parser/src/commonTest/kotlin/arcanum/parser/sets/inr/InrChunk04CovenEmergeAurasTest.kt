package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 04: Coven (ability word, CR 207.2c), Emerge (CR 702.119), Auras (CR 303.4, 702.5). "Can't transform" is CR 701.27/701.28f.
 */
class InrChunk04CovenEmergeAurasTest {
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private fun emerge(cost: String) = Keyword.Emerge(manaCost(cost))
    /** "When you cast this spell, …": it triggers as the spell is cast, from the stack (CR 603.2, 601.2i) */
    private fun whenYouCastThis(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) =
        triggered(youCast(ObjectFilter.Is(Objects.Self)), *steps, targets = targets)

    // CR 701.14a (fight), 608.2c (the same two targets in each sentence), 122.1 (counter before the fight)
    @Test
    fun contestOfFangs() = card(
        name = "Contest of Fangs",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Coven — Choose target creature you control and target creature you don't control. If you control three or more creatures with different powers, put a +1/+1 counter on the chosen creature you control. Then the chosen creatures fight each other. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creatureYouControl), slot("other creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
            steps = listOf(ifThen(coven, counters(target("creature"))), doing(Effect.Fight(target("creature"), target("other creature")))),
        ), abilityWord = "Coven"),
    )

    // CR 702.145 is not needed here; CR 712 (transform), 602.5b (activate only if), 701.23 (search, reveal)
    @Test
    fun ambitiousCartwright() = cardFaces(Layout.Transform,
        card(
            name = "Ambitious Cartwright",
            cost = "{1}{W}",
            type = "Creature — Human Peasant",
            text = """
                When this creature enters, you may search your library for a basic Plains card, reveal it, put it into your hand, then shuffle.
                Coven — {1}{W}{W}: Transform this creature. Activate only if you control three or more creatures with different powers.
            """,
            pt = "1/1",
        ) to abilities(
            triggered(entersSelf, Step.May(listOf(search(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), sub("Plains")), reveal = true),
                move(Objects.Bound("found"), Zone.Hand), shuffle), Players.You)),
            Ability.Activated(cost = manaCost("{1}{W}{W}"), instructions = Instructions(steps = listOf(transformSelf)),
                rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = coven), abilityWord = "Coven"),
        ),
        card(
            name = "Seasoned Wainwright",
            cost = null,
            type = "Creature — Human Knight",
            text = "Lifelink",
            pt = "3/3",
            colorIndicator = "W",
        ) to abilities(Keyword.Lifelink),
    )

    // CR 702.119a (emerge cost), 700.? none; 701.9 (reveal, choose, discard): one choice per described card, from the revealed hand
    @Test
    fun vesselMindbender() = card(
        name = "Vessel Mindbender",
        cost = "{8}",
        type = "Creature — Eldrazi Insect",
        text = """
            Emerge {5}{B}{B} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, target opponent reveals their hand. You choose from it a nonland card with mana value 3 or less and a card with mana value 4 or greater. That player discards those cards.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        emerge("{5}{B}{B}"),
        whenYouCastThis(
            Step.Do(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(and(nonland, manaValue(Comparison.AtMost, 3)), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            Step.Choose(Choice.Objects(and(cardFilter, manaValue(Comparison.AtLeast, 4)), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosenSecond", chooser = Players.You),
            Step.Do(Effect.Discard(Players.Target("opponent"), n(2), ObjectFilter.Any, objects = Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("chosenSecond"))))),
            targets = listOf(targetOpponent.copy(name = "opponent")),
        ),
    )

    @Test
    fun hollowMaw() = card(
        name = "Hollow Maw",
        cost = "{8}",
        type = "Creature — Eldrazi Leech",
        text = """
            Emerge {6}{B} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, target opponent loses 3 life and you gain 3 life.
        """,
        pt = "6/4",
    ) parsesTo abilities(
        emerge("{6}{B}"),
        whenYouCastThis(loseLife(3, Players.Target("opponent")), gainLife(3), targets = listOf(targetOpponent.copy(name = "opponent"))),
    )

    // CR 611.2a (a continuous effect from a resolving ability), 702.119a
    @Test
    fun provincialDecimator() = card(
        name = "Provincial Decimator",
        cost = "{10}",
        type = "Creature — Eldrazi Boar",
        text = """
            Emerge {6}{G}{G}{G} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, creatures you control get +2/+2 and gain trample until end of turn.
            Trample, haste
        """,
        pt = "7/7",
    ) parsesTo abilities(
        emerge("{6}{G}{G}{G}"),
        whenYouCastThis(pump(Objects.Each(creatureYouControl), 2, 2, has(Keyword.Trample))),
        Keyword.Trample,
        Keyword.Haste,
    )

    // CR 115.1d: "up to four" targets may be none or fewer
    @Test
    fun murkyDreadFiend() = card(
        name = "Murky Dread-Fiend",
        cost = "{8}",
        type = "Creature — Eldrazi Octopus",
        text = """
            Flash
            Emerge {5}{U}{U} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, tap up to four target permanents.
        """,
        pt = "5/6",
    ) parsesTo abilities(
        Keyword.Flash,
        emerge("{5}{U}{U}"),
        whenYouCastThis(tapIt(target("permanents")), targets = listOf(slot("permanents", permanentFilter, min = 0, max = 4))),
    )

    @Test
    fun itOfTheCreepingSwarm() = card(
        name = "It of the Creeping Swarm",
        cost = "{8}",
        type = "Creature — Eldrazi Insect",
        text = """
            Emerge {6}{G} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, create two 1/1 green Insect creature tokens.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        emerge("{6}{G}"),
        whenYouCastThis(create(creatureToken("Insect", listOf(Color.Green), 1, 1), n(2))),
    )

    @Test
    fun swampGryff() = card(
        name = "Swamp Gryff",
        cost = "{7}",
        type = "Creature — Eldrazi Hippogriff",
        text = """
            Emerge {5}{U} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
            When you cast this spell, draw a card.
            Flying
        """,
        pt = "3/4",
    ) parsesTo abilities(
        emerge("{5}{U}"),
        whenYouCastThis(draw(1)),
        Keyword.Flying,
    )

    // CR 701.27 and 701.28f: can't transform; 508.1 and 509.1 (can't attack or block); 701.3 (attach, once each turn, as a sorcery)
    @Test
    fun boundByMoonlight() = card(
        name = "Bound by Moonlight",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack, block, or transform.
            Sacrifice another permanent: Attach this Aura to target creature. Activate only as a sorcery and only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantTransform, affects = enchanted),
        activated(cost(sacrificeA(and(another, permanentFilter))),
            doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creature)),
            rules = asSorcery.copy(perTurn = 1)),
    )

    // CR 613.1f: the keyword; 603.1 the granted triggered ability belongs to the enchanted creature
    @Test
    fun hearthCall() = card(
        name = "Hearth Call",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has vigilance and "At the beginning of your end step, create a 1/1 white Human creature token."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(has(Keyword.Vigilance, triggered(yourEndStep, create(creatureToken("Human", listOf(Color.White), 1, 1)))), affects = enchanted),
    )

    // CR 208.1, 604.3: the number of cards is counted continuously; -X/-0 leaves toughness alone
    @Test
    fun spontaneousDecay() = card(
        name = "Spontaneous Decay",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets -X/-0, where X is the number of cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.ModifyPowerToughness(Value.Times(count(inYourGraveyard(cardFilter)), -1), n(0)), affects = enchanted),
    )

    // CR 303.4 (Aura enters attached), 106.1b (any color mana), 603.1
    @Test
    fun abundantBloom() = card(
        name = "Abundant Bloom",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, draw a card.
            Enchanted land has "{T}: Add one mana of any color."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        triggered(entersSelf, draw(1)),
        static(has(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))), affects = enchanted),
    )

    // CR 610.3 (until this Aura leaves), 303.4
    @Test
    fun faithRestored() = card(
        name = "Faith Restored",
        cost = "{3}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When this Aura enters, exile target creature an opponent controls until this Aura leaves the battlefield.
            Enchanted creature gets +2/+2.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        triggered(entersSelf, doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", creatureOpponentControls))),
        static(pt(2, 2), affects = enchanted),
    )

    // CR 400.7 (a new object returns), 303.4g (enters attached to the chosen creature), 602.5d (sorcery timing)
    @Test
    fun gryffsBlessing() = card(
        name = "Gryff's Blessing",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+0 and has flying.
            {3}{W}: Return this card from your graveyard to the battlefield attached to target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(1, 0), has(Keyword.Flying), affects = enchanted),
        activated(manaCost("{3}{W}"),
            doing(Effect.Move(Objects.Self, Destination(Zone.Battlefield, attachedTo = Recipients(objects = target("creature"))))),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 205.1a, 613.1 (layers 4, 5, 6)
    @Test
    fun imprisonedInMist() = card(
        name = "Imprisoned in Mist",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature, land, or planeswalker
            Enchanted permanent is a colorless land with "{T}: Add {C}" and loses all other card types and abilities.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(or(creature, land, planeswalker))),
        static(
            Mechanic.SetTypes(add = listOf(CardType.Land), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetColors(emptyList()),
            Mechanic.LoseAbilities(),
            has(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))),
            affects = enchanted,
        ),
    )

    // CR 613.1 (layer 6 and 7c), 113.10 (the granted ability is the creature's own)
    @Test
    fun lunarchCloak() = card(
        name = "Lunarch Cloak",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has "{1}, Sacrifice a permanent: This creature gains flying until end of turn."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(2, 2), has(activated(cost(mana("{1}"), sacrificeA(permanentFilter)), gains(Objects.Self, Keyword.Flying))), affects = enchanted),
    )
}
