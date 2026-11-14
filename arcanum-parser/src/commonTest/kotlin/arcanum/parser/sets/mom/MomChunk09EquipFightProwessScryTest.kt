package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 09: Equipment (CR 301.5), fight (CR 701.14), prowess (CR 702.108) and scry (CR 701.22). */
class MomChunk09EquipFightProwessScryTest {
    private val equipped = Objects.AttachedTo(Objects.Self)
    private val youDontControl = ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))
    private val creatureYouDontControl = and(creature, youDontControl)
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))
    private val attachToTargetCreature = Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature"))))
    private fun siege(name: String, cost: String, text: String, defense: String) =
        OracleCard(name, cost, "Battle — Siege", siegeReminder + "\n" + text.trimIndent(), defense = defense)
    private fun chapter(number: Int, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        Ability.Triggered(Trigger.Chapter(listOf(number)), Instructions(targets = targets, steps = steps.toList()))

    // CR 301.5a, 702.111a; CR 510.2, 310.6: combat damage to a battle counts too; CR 111.10a
    @Test
    fun docksideCudgel() = card(
        name = "Dockside Cudgel",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has menace. (It can't be blocked except by two or more creatures.)
            Whenever equipped creature deals combat damage to a player or battle, create a Treasure token.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Menace), affects = equipped),
        triggered(on(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(objects = battle, players = PlayerFilter.Any), combat = true)), createTreasure()),
        equip("{2}"),
    )

    // CR 603.12: "When you do" after the optional sacrifice; CR 508.1m: "that creature" is the attacker; the counters go on all at once (CR 122.1)
    @Test
    fun invasionOfValdrith() = cardFaces(Layout.Transform,
        siege("Invasion of Valdrith", "{W}{B}",
            "When this Siege enters, you may sacrifice an artifact or creature. When you do, exile target artifact or creature an opponent controls.",
            "4") to abilities(
            triggered(entersSelf, Step.Pay(cost(sacrificeA(or(artifact, creature))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(reflexive(move(target("permanent"), Zone.Exile),
                    targets = listOf(slot("permanent", and(or(artifact, creature), opponents))))))),
        ),
        card(
            name = "Gilded Hymn-Launcher",
            cost = null,
            type = "Artifact — Equipment",
            colorIndicator = "WB",
            text = """
                Whenever equipped creature attacks, put a +1/+1 counter on that creature and each other creature you control that shares a creature type with it.
                Equip {1}
            """,
        ) to abilities(
            triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))), counters(Objects.Union(listOf(attacker, Objects.Each(
                and(not(ObjectFilter.Is(attacker)), creature, you, ObjectFilter.Shares(SharedQuality.CreatureType, attacker))))))),
            equip("{1}"),
        ),
    )
    private val attacker = Objects.EventObject(EventRole.Object)

    // CR 702.16; CR 701.25a; CR 608.2c: the spell is chosen in your graveyard as the trigger resolves, after surveilling; CR 614.1a
    @Test
    fun swordOfEbbAndReturn() = card(
        name = "Sword of Ebb and Return",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+2 and has protection from blue and from black.
            Whenever equipped creature deals combat damage to a player, surveil 2. Then you may cast an instant or sorcery spell with mana value 2 or less from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 2), has(Keyword.Protection(ObjectFilter.OfColor(Color.Blue)), Keyword.Protection(ObjectFilter.OfColor(Color.Black))), affects = equipped),
        triggered(on(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            doing(Effect.Surveil(Players.You, n(2))),
            Step.Choose(Choice.Objects(inYourGraveyard(or(instant, sorcery), manaValue(arcanum.api.Comparison.AtMost, 2)), n(0), n(1)), bind = "chosen",
                chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("chosen"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack,
                to = Zone.Graveyard), modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely))),
        equip("{2}"),
    )

    // CR 702.8a; CR 301.5c: attached by the enters trigger
    @Test
    fun edgedParadeFan() = card(
        name = "Edged Parade-Fan",
        cost = "{1}{B}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains indestructible until end of turn.
            Equipped creature gets +1/+0.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, attachToTargetCreature, gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 0), affects = equipped),
        equip("{1}"),
    )

    // CR 702.9a, 702.6a
    @Test
    fun gliderwingRig() = card(
        name = "Gliderwing Rig",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has flying.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Flying), affects = equipped),
        equip("{2}"),
    )

    // CR 702.20a, 702.6a
    @Test
    fun ridgeguardPike() = card(
        name = "Ridgeguard Pike",
        cost = "{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has vigilance.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), has(Keyword.Vigilance), affects = equipped),
        equip("{1}"),
    )

    // CR 702.8a; CR 301.5c
    @Test
    fun copperlineCleaver() = card(
        name = "Copperline Cleaver",
        cost = "{R}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +2/+0.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, attachToTargetCreature, targets = listOf(slot("creature", creatureYouControl))),
        static(pt(2, 0), affects = equipped),
        equip("{3}"),
    )

    // CR 701.14a: "that creature" is the first target; CR 115.1: the second target is optional; CR 613.1f, 613.4c: no abilities, +2/+2 still
    @Test
    fun invasionOfYerrow() = cardFaces(Layout.Transform,
        siege("Invasion of Yerrow", "{4}{G}",
            "When this Siege enters, put a +1/+1 counter on target creature you control. Then that creature fights up to one target creature you don't control.",
            "6") to abilities(
            triggered(entersSelf, counters(target("creature")), doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureYouDontControl, min = 0))),
        ),
        card(
            name = "Firstborn Slurry",
            cost = null,
            type = "Creature — Ooze",
            pt = "4/4",
            colorIndicator = "G",
            text = "At the beginning of combat on your turn, another target creature gets +2/+2 and loses all abilities until end of turn.",
        ) to abilities(
            triggered(beginningOfCombatOnYourTurn, untilEndOfTurn(target("creature"), pt(2, 2), Mechanic.LoseAbilities()),
                targets = listOf(slot("creature", and(another, creature)))),
        ),
    )

    // CR 700.2: a modal triggered ability; CR 701.14a; CR 400.7: the discarded card is a new object in the graveyard, shuffled from there
    @Test
    fun brakkaAndThulm() = card(
        name = "Brakka and Thulm",
        cost = "{2}{R}{R}{G}{G}",
        type = "Legendary Creature — Ape Dinosaur Turtle",
        pt = "7/7",
        text = """
            When Brakka and Thulm enters, choose one —
            • It gains trample and haste until end of turn.
            • It fights target creature you don't control.
            {2}{R}{G}, Discard this card: Destroy up to one target artifact or enchantment. Shuffle this card into your library from your graveyard, then draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(gains(Objects.Self, Keyword.Trample, Keyword.Haste)),
            mode(doing(Effect.Fight(Objects.EventObject(EventRole.After), target("creature"))), targets = listOf(slot("creature", creatureYouDontControl)))))),
        activated(cost(mana("{2}{R}{G}"), CostPart.Discard(CostObjects.Self)),
            destroy(target("permanent")),
            move(Objects.SelfAfter, Zone.Library),
            shuffle,
            draw(1),
            targets = listOf(slot("permanent", or(artifact, enchantment), min = 0))),
    )

    // CR 701.27a, 712.14; CR 601.2d: divided as the chapter ability is put on the Stack, at least one counter each; CR 701.14a
    @Test
    fun thornvex() = cardFaces(Layout.Transform,
        card(
            name = "Thornvex",
            cost = "{3}{G}{G}",
            type = "Legendary Creature — Phyrexian Praetor",
            pt = "6/6",
            text = """
                Reach, trample
                When Thornvex enters, search your library for up to two Forest cards, reveal them, put them into your hand, then shuffle.
                {6}{G}{G}: Exile Thornvex, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
        ) to abilities(
            Keyword.Reach,
            Keyword.Trample,
            triggered(entersSelf, search(sub("Forest"), max = 2, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            activated(manaCost("{6}{G}{G}"),
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))),
                rules = asSorcery),
        ),
        card(
            name = "The Rampant Ascent",
            cost = null,
            type = "Enchantment — Saga",
            colorIndicator = "G",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I — Mill ten cards. Put up to two creature cards from among the milled cards onto the battlefield.
                II — Distribute seven +1/+1 counters among any number of target creatures you control.
                III — Until end of turn, creatures you control gain "{1}: This creature fights target creature you don't control." Exile this Saga, then return it to the battlefield (front face up).
            """,
        ) to abilities(
            chapter(1, mill(10, postBind = "milled"),
                Step.Choose(Choice.Objects(creature, n(0), n(2), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield)),
            chapter(2, counters(target("creatures"), Value.DividedShare),
                targets = listOf(TargetSlot("creatures", TargetKind.Objects(creatureYouControl), min = n(0), max = null, divided = n(7)))),
            chapter(3,
                untilEndOfTurn(Objects.Each(creatureYouControl), has(activated(manaCost("{1}"), doing(Effect.Fight(Objects.Self, target("creature"))),
                    targets = listOf(slot("creature", creatureYouDontControl))))),
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield)))),
        ),
    )

    // CR 115.4: "another target battle" isn't this Siege; CR 614.1a: +2 to damage from your noncreature sources to any creature or battle or an
    // opponent; CR 702.108a
    @Test
    fun invasionOfCorvanth() = cardFaces(Layout.Transform,
        siege("Invasion of Corvanth", "{2}{R}",
            "When this Siege enters, it deals 4 damage to another target battle or opponent and 1 damage to up to one target creature.",
            "5") to abilities(
            triggered(entersSelf,
                damage(4, Recipients(target = "battle or opponent")),
                damage(1, toObjects(target("creature"))),
                targets = listOf(TargetSlot("battle or opponent", TargetKind.Either(and(another, battle), PlayerFilter.Opponent), min = n(1), max = n(1)),
                    slot("creature", creature, min = 0))),
        ),
        card(
            name = "Acolytes of the Pyre",
            cost = null,
            type = "Creature — Human Monk",
            pt = "4/4",
            colorIndicator = "R",
            text = """
                Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
                If a noncreature source you control would deal damage to a creature, battle, or opponent, it deals that much damage plus 2 instead.
            """,
        ) to abilities(
            Keyword.Prowess,
            static(Mechanic.Replacement(EventPattern.Damage(and(not(creature), you), RecipientFilter(objects = or(creature, battle), players = PlayerFilter.Opponent)),
                modify = listOf(Modification.AddAmount(n(2))))),
        ),
    )

    // CR 702.108b: each instance triggers separately; CR 107.4f: {U/P}; CR 702.21a
    @Test
    fun sandjackalLancer() = cardFaces(Layout.Transform,
        card(
            name = "Sandjackal Lancer",
            cost = "{1}{R}",
            type = "Creature — Jackal Warrior",
            pt = "2/2",
            text = """
                Trample
                Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
                {3}{U/P}: Transform this creature. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
            """,
        ) to abilities(
            Keyword.Trample,
            Keyword.Prowess,
            transformAsSorcery("{3}{U/P}"),
        ),
        card(
            name = "Glistening Lancestalker",
            cost = null,
            type = "Creature — Phyrexian Jackal",
            pt = "3/3",
            colorIndicator = "UR",
            text = "Trample, ward {2}, prowess, prowess (Each instance of prowess triggers separately.)",
        ) to abilities(
            Keyword.Trample,
            Keyword.Ward(manaCost("{2}")),
            Keyword.Prowess,
            Keyword.Prowess,
        ),
    )

    // CR 702.108a; CR 111.4: the token has prowess too
    @Test
    fun cloisterPreceptor() = card(
        name = "Cloister Preceptor",
        cost = "{2}{W}",
        type = "Creature — Human Monk",
        pt = "2/2",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever you cast a noncreature spell, create a 1/1 white Monk creature token with prowess.
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        triggered(youCast(noncreatureSpell), create(creatureToken("Monk", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Prowess)))),
    )

    // CR 701.22a; CR 712.1: a double-faced card; CR 702.21a, 613.1f: other transformed permanents gain ward
    @Test
    fun invasionOfMireholt() = cardFaces(Layout.Transform,
        siege("Invasion of Mireholt", "{G}{U}",
            "When this Siege enters, scry 3, then reveal the top card of your library. If it's a land or double-faced card, draw a card.",
            "4") to abilities(
            triggered(entersSelf, scry(3), doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
                Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), or(land, ObjectFilter.DoubleFaced)), then = listOf(draw(1)))),
        ),
        card(
            name = "Towering Slabhide",
            cost = null,
            type = "Creature — Beast",
            pt = "4/4",
            colorIndicator = "GU",
            text = """
                Trample, ward {2}
                Other transformed permanents you control have trample and ward {2}.
            """,
        ) to abilities(
            Keyword.Trample,
            Keyword.Ward(manaCost("{2}")),
            static(has(Keyword.Trample, Keyword.Ward(manaCost("{2}"))),
                affects = Objects.Each(and(another, ObjectFilter.HasStatus(arcanum.api.Status.Transformed), permanentFilter, you))),
        ),
    )

    // CR 702.7a; CR 701.22a
    @Test
    fun arrivingAblaze() = card(
        name = "Arriving Ablaze",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +1/+0 and gains first strike until end of turn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(1, 0), has(Keyword.FirstStrike)), scry(1), targets = listOf(slot("creature", creature))),
    )
}
