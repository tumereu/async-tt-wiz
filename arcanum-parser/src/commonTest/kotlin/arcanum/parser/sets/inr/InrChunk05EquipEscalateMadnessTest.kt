package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
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
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 05: Equipment (CR 301.5, 702.6), Escalate (CR 702.120), Madness (CR 702.35).
 */
class InrChunk05EquipEscalateMadnessTest {
    private val equipped = Objects.AttachedTo(Objects.Self)
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))
    private fun madness(cost: String) = Keyword.Madness(manaCost(cost))
    private fun escalate(cost: String) = Keyword.Escalate(manaCost(cost))
    private val madnessText = "(If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)"
    private val escalateText = "(Pay this cost for each mode chosen beyond the first.)"
    private val creatureSlot = slot("creature", creature)

    // ---- Equipment

    // CR 113.10, 301.5c: the granted ability names the Equipment: it is sacrificed and is the source of the damage
    // (as it last existed, CR 608.2h). CR 509.1b: blocking restriction.
    @Test
    fun kindledLanternPole() = card(
        name = "Kindled Lantern Pole",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature can't be blocked by Vampires or Zombies.
            Equipped creature has "{T}, Sacrifice Kindled Lantern Pole: Kindled Lantern Pole deals 2 damage to any target."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(or(sub("Vampire"), sub("Zombie"))), affects = equipped),
        static(has(activated(cost(tap, CostPart.Sacrifice(CostObjects.These(Objects.Granter))),
            damage(2, toAny, source = Objects.Granter), targets = listOf(anyTarget))), affects = equipped),
        equip("{1}"),
    )

    // CR 712.14: returned to the battlefield transformed, under its dying controller's control ("your"); "it" is the card in
    // the graveyard (CR 400.7). Back: CR 301.5, 611.3a.
    @Test
    fun gleaningScarecrow() = cardFaces(Layout.Transform,
        card(
            name = "Gleaning Scarecrow",
            cost = "{3}",
            type = "Artifact Creature — Scarecrow",
            pt = "2/2",
            text = "When this creature dies, return it to the battlefield transformed under your control.",
        ) to abilities(
            triggered(diesSelf, doing(Effect.Move(Objects.EventObject(EventRole.After),
                Destination(Zone.Battlefield, transformed = true, controller = Players.You)))),
        ),
        card(
            name = "Salvaged Sickle",
            cost = null,
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +1/+1.
                As long as equipped creature is a Human, it has menace. (It can't be blocked except by two or more creatures.)
                Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
            """,
        ) to abilities(
            static(pt(1, 1), affects = equipped),
            static(has(Keyword.Menace), affects = equipped, condition = Condition.ObjectMatches(equipped, sub("Human"))),
            equip("{2}"),
        ),
    )

    // CR 701.27a: "transforms" is the event; this Equipment transforms in turn
    @Test
    fun forgottenKeepsake() = cardFaces(Layout.Transform,
        card(
            name = "Forgotten Keepsake",
            cost = "{1}",
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +1/+1.
                When equipped creature transforms, transform this Equipment.
                Equip {1}
            """,
        ) to abilities(
            static(pt(1, 1), affects = equipped),
            triggered(on(EventPattern.Transform(ObjectFilter.Is(equipped))), doing(Effect.Transform(Objects.Self))),
            equip("{1}"),
        ),
        card(
            name = "Gloommouth Edge",
            cost = null,
            type = "Artifact — Equipment",
            text = """
                Equipped creature gets +3/+3 and has first strike.
                Equip {3}
            """,
        ) to abilities(
            static(pt(3, 3), has(Keyword.FirstStrike), affects = equipped),
            equip("{3}"),
        ),
    )

    // CR 611.3a: the lifelink applies only while the equipped creature is a Human
    @Test
    fun flensersChopper() = card(
        name = "Flenser's Chopper",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+0.
            As long as equipped creature is a Human, it has lifelink.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(pt(3, 0), affects = equipped),
        static(has(Keyword.Lifelink), affects = equipped, condition = Condition.ObjectMatches(equipped, sub("Human"))),
        equip("{3}"),
    )

    // CR 702.6a
    @Test
    fun patchworkGlider() = card(
        name = "Patchwork Glider",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has flying.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), affects = equipped),
        equip("{1}"),
    )

    // CR 702.6a, 602.2b: a non-mana equip cost
    @Test
    fun fiendlinkMail() = card(
        name = "Fiendlink Mail",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +4/+2.
            Equip—Sacrifice a creature.
        """,
    ) parsesTo abilities(
        static(pt(4, 2), affects = equipped),
        Keyword.Equip(cost(sacrificeA(creature))),
    )

    // CR 508.1 (attack trigger), 502.3 (doesn't untap during its controller's next untap step), 701.3d ("that permanent" is the
    // one it was attached to)
    @Test
    fun tannersSplice() = card(
        name = "Tanner's Splice",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+3.
            Whenever equipped creature attacks, it doesn't untap during its controller's next untap step.
            Whenever this Equipment becomes unattached from a permanent, sacrifice that permanent.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(3, 3), affects = equipped),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(Objects.EventObject(EventRole.Object)),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(Objects.EventObject(EventRole.Object)))))),
        triggered(on(EventPattern.Unattach(objects = ObjectFilter.Is(Objects.Self), from = ObjectFilter.Category(ObjectCategory.Permanent))),
            doing(Effect.Sacrifice(Objects.EventObject(EventRole.From)))),
        equip("{2}"),
    )

    // ---- Escalate

    // CR 702.120a, 700.2: one to three modes, the escalate cost once for each beyond the first
    @Test
    fun communalUprising() = card(
        name = "Communal Uprising",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = """
            Escalate {1} $escalateText
            Choose one or more —
            • Target player discards all the cards in their hand, then draws that many cards.
            • Communal Uprising deals 4 damage to target creature.
            • Communal Uprising deals 3 damage to target opponent or planeswalker.
        """,
    ) parsesTo abilities(
        escalate("{1}"),
        Ability.Spell(Instructions(modes = modes(
            mode(
                doing(Effect.Discard(Players.Target("player"), n(1), ObjectFilter.Any,
                    objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))),
                    postBind = "discarded")),
                doing(Effect.Draw(Players.Target("player"), Value.Count(Objects.Bound("discarded")))),
                targets = listOf(targetPlayer),
            ),
            mode(damage(4, toObjects(target("creature"))), targets = listOf(creatureSlot)),
            mode(damage(3, Recipients(target = "opponent or planeswalker")), targets = listOf(TargetSlot("opponent or planeswalker",
                TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Opponent), min = n(1), max = n(1)))),
            min = 1, max = 3,
        ))),
    )

    // CR 702.120a; CR 115.1 ("each creature target opponent controls": only that opponent's creatures)
    @Test
    fun feralCompact() = card(
        name = "Feral Compact",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Escalate {1} $escalateText
            Choose one or more —
            • Creatures target player controls gain trample until end of turn.
            • Feral Compact deals 2 damage to target creature.
            • Feral Compact deals 1 damage to each creature target opponent controls.
        """,
    ) parsesTo abilities(
        escalate("{1}"),
        Ability.Spell(Instructions(modes = modes(
            mode(gains(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), Keyword.Trample),
                targets = listOf(targetPlayer)),
            mode(damage(2, toObjects(target("creature"))), targets = listOf(creatureSlot)),
            mode(damage(1, toObjects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))))),
                targets = listOf(targetOpponent)),
            min = 1, max = 3,
        ))),
    )

    // CR 702.120a
    @Test
    fun lentFury() = card(
        name = "Lent Fury",
        cost = "{R}",
        type = "Instant",
        text = """
            Escalate {3} $escalateText
            Choose one or both —
            • Target creature gets +3/+0 until end of turn.
            • Target creature gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        escalate("{3}"),
        Ability.Spell(Instructions(modes = modes(
            mode(pump(target("creature"), 3, 0), targets = listOf(creatureSlot)),
            mode(gains(target("creature"), Keyword.FirstStrike), targets = listOf(creatureSlot)),
            min = 1, max = 2,
        ))),
    )

    // CR 702.120a: a non-mana escalate cost
    @Test
    fun communalMalice() = card(
        name = "Communal Malice",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Escalate—Discard a card. $escalateText
            Choose one or more —
            • Target opponent reveals their hand. You choose an instant or sorcery card from it. That player discards that card.
            • Target creature gets -2/-2 until end of turn.
            • Target opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Escalate(cost(discardACard)),
        Ability.Spell(Instructions(modes = modes(
            mode(
                // CR 701.20a: the revealed hand is what "it" chooses from
                doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))),
                    bind = "revealed")),
                Step.Choose(arcanum.api.Choice.Objects(or(instant, sorcery), n(1), n(1), from = Objects.Bound("revealed")),
                    bind = "chosen", chooser = Players.You),
                doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                targets = listOf(targetOpponent),
            ),
            mode(pump(target("creature"), -2, -2), targets = listOf(creatureSlot)),
            mode(loseLife(2, Players.Target("opponent")), gainLife(2), targets = listOf(targetOpponent)),
            min = 1, max = 3,
        ))),
    )

    // ---- Madness

    // CR 603.4 (intervening if, checked for the upkeep's player), CR 702.35a
    @Test
    fun bedlamCaller() = card(
        name = "Bedlam Caller",
        cost = "{1}{B}",
        type = "Creature — Vampire Wizard",
        pt = "3/1",
        text = """
            At the beginning of each player's upkeep, if that player has no cards in hand, you draw a card and you lose 1 life.
            Madness {1}{B} $madnessText
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), draw(1), loseLife(1),
            interveningIf = Condition.Compare(Value.HandSize(Players.EventPlayer), arcanum.api.Comparison.AtMost, n(0))),
        madness("{1}{B}"),
    )

    // CR 702.35a
    @Test
    fun distillersWelcome() = card(
        name = "Distiller's Welcome",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Distiller's Welcome deals 4 damage to target creature.
            Madness {1}{R} $madnessText
        """,
    ) parsesTo abilities(
        spell(damage(4, toObjects(target("creature"))), targets = listOf(creatureSlot)),
        madness("{1}{R}"),
    )

    // CR 603.4: intervening if on an enters-or-attacks trigger
    @Test
    fun gorehallAcolyte() = card(
        name = "Gorehall Acolyte",
        cost = "{2}{B}{R}",
        type = "Creature — Vampire Cleric",
        pt = "4/4",
        text = """
            Whenever this creature enters or attacks, if you have no cards in hand, this creature deals 2 damage to any target.
            Madness {1}{B}{R} $madnessText
        """,
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, damage(2, toAny), targets = listOf(anyTarget),
            interveningIf = Condition.Not(Condition.Exists(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))),
        madness("{1}{B}{R}"),
    )

    // CR 510.3a (combat damage trigger), 702.35a
    @Test
    fun frenziedBloodseeker() = card(
        name = "Frenzied Bloodseeker",
        cost = "{2}{R}",
        type = "Creature — Vampire Berserker",
        pt = "4/1",
        text = """
            Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
            Madness {1}{R} $madnessText
        """,
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer, counters(Objects.Self)),
        madness("{1}{R}"),
    )

    // CR 702.35a
    @Test
    fun scorchingMood() = card(
        name = "Scorching Mood",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = """
            Scorching Mood deals 3 damage to any target.
            Madness {R} $madnessText
        """,
    ) parsesTo abilities(
        spell(damage(3, toAny), targets = listOf(anyTarget)),
        madness("{R}"),
    )

    // CR 111.1
    @Test
    fun necromancersSummons() = card(
        name = "Necromancer's Summons",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = """
            Create two 2/2 black Zombie creature tokens.
            Madness {2}{B} $madnessText
        """,
    ) parsesTo abilities(
        spell(create(zombie, n(2))),
        madness("{2}{B}"),
    )

    // CR 701.8a
    @Test
    fun killingUrge() = card(
        name = "Killing Urge",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Destroy target tapped creature.
            Madness {1}{B} $madnessText
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(tapped, creature)))),
        madness("{1}{B}"),
    )

    // CR 702.7a; CR 510.3a: "it" is the Vampire that dealt the damage
    @Test
    fun ravensgateRevel() = card(
        name = "Ravensgate Revel",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            Attacking creatures you control have first strike.
            Whenever a Vampire you control deals combat damage to a player, put a +1/+1 counter on it.
            Madness {2}{R} $madnessText
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), affects = Objects.Each(and(attacking, creature, you))),
        triggered(on(EventPattern.Damage(and(sub("Vampire"), you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            counters(Objects.EventObject(EventRole.Source))),
        madness("{2}{R}"),
    )

    // CR 510.3a; CR 611.2a: the exiled card may be played until end of turn
    @Test
    fun highkeepOccultist() = card(
        name = "Highkeep Occultist",
        cost = "{2}{R}",
        type = "Creature — Vampire Horror",
        pt = "3/2",
        text = """
            Trample
            Whenever this creature deals combat damage to a player, exile the top card of your library. Until end of turn, you may play that card.
            Madness {1}{R} $madnessText
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(combatDamageToAPlayer,
            doing(Effect.Move(Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(from = Zone.Exile, how = arcanum.api.PlayKind.Play, holder = PlayerFilter.You))),
        madness("{1}{R}"),
    )
}
