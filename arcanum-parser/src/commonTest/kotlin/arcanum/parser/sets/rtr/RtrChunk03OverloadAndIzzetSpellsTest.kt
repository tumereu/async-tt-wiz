package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.artifact
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.damage
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.instant
import arcanum.parser.sets.rtr.Rtr.mana
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonland
import arcanum.parser.sets.rtr.Rtr.notYou
import arcanum.parser.sets.rtr.Rtr.overload
import arcanum.parser.sets.rtr.Rtr.permanent
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.sorcery
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.you
import kotlin.test.Test

/**
 * Return to Ravnica chunk 03: overload (CR 702.96: an alternative cost, and with it the spell's text with "target"
 * changed to "each", so it has no targets, 702.96b) on bounce, tap, pumps, damage (also X), destruction, a forced attack,
 * counterspells; and the Izzet spells: casting exiled cards without paying (CR 608.2g), a counter that damages, searches
 * repeated for other mana values, "unless its controller pays" with a counter exiled instead (CR 614.15).
 */
class RtrChunk03OverloadAndIzzetSpellsTest {
    private val creatureYouDontControl = all(creature, notYou)
    private val creatureYouControl = all(creature, you)
    private fun each(filter: ObjectFilter) = Objects.Each(filter)
    private fun hand(objects: Objects) = Step.Do(Effect.Move(objects, Destination(Zone.Hand)))

    // ---- Overload (CR 702.96)

    @Test
    fun vortexBreach() = card(
        name = "Vortex Breach",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Return target nonland permanent you don't control to its owner's hand.
            Overload {6}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(hand(target("permanent")), targets = listOf(slot("permanent", all(nonland, permanent, notYou)))),
        overload("{6}{U}", hand(each(all(nonland, permanent, notYou)))),
    )

    @Test
    fun galeburst() = card(
        name = "Galeburst",
        cost = "{U}",
        type = "Instant",
        text = """
            Tap target creature you don't control.
            Overload {3}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.Tap(target("creature"))), targets = listOf(slot("creature", creatureYouDontControl))),
        overload("{3}{U}", Step.Do(Effect.Tap(each(creatureYouDontControl)))),
    )

    /** "Target …" at the start of the sentence becomes "Each …" (CR 702.96b). */
    @Test
    fun shrinkingSpell() = card(
        name = "Shrinking Spell",
        cost = "{U}",
        type = "Instant",
        text = """
            Target creature you don't control gets -4/-0 until end of turn.
            Overload {2}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -4, 0), targets = listOf(slot("creature", creatureYouDontControl))),
        overload("{2}{U}", pump(each(creatureYouDontControl), -4, 0)),
    )

    @Test
    fun alloyhideWard() = card(
        name = "Alloyhide Ward",
        cost = "{U}",
        type = "Instant",
        text = """
            Target creature you control gets +0/+1 and gains hexproof until end of turn.
            Overload {1}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 0, 1, Mechanic.GainAbilities(listOf(Keyword.Hexproof))), targets = listOf(slot("creature", creatureYouControl))),
        overload("{1}{U}", pump(each(creatureYouControl), 0, 1, Mechanic.GainAbilities(listOf(Keyword.Hexproof)))),
    )

    @Test
    fun overchargePulse() = card(
        name = "Overcharge Pulse",
        cost = "{R}",
        type = "Instant",
        text = """
            Target creature you control gets +2/+0 until end of turn.
            Overload {2}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 0), targets = listOf(slot("creature", creatureYouControl))),
        overload("{2}{R}", pump(each(creatureYouControl), 2, 0)),
    )

    @Test
    fun arcLattice() = card(
        name = "Arc Lattice",
        cost = "{R}",
        type = "Instant",
        text = """
            Arc Lattice deals 1 damage to target creature you don't control.
            Overload {1}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(damage(Recipients(objects = target("creature")), 1), targets = listOf(slot("creature", creatureYouDontControl))),
        overload("{1}{R}", damage(Recipients(objects = each(creatureYouDontControl)), 1)),
    )

    @Test
    fun foundryBarrage() = card(
        name = "Foundry Barrage",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Foundry Barrage deals 4 damage to target creature you don't control.
            Overload {3}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(damage(Recipients(objects = target("creature")), 4), targets = listOf(slot("creature", creatureYouDontControl))),
        overload("{3}{R}{R}{R}", damage(Recipients(objects = each(creatureYouDontControl)), 4)),
    )

    /** X is announced for the overloaded spell too (CR 107.3a): {X}{X} in the overload cost pays for it twice. */
    @Test
    fun cobblestoneQuake() = card(
        name = "Cobblestone Quake",
        cost = "{X}{R}",
        type = "Instant",
        text = """
            Cobblestone Quake deals X damage to target creature without flying you don't control.
            Overload {X}{X}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", all(creature, ObjectFilter.Not(ObjectFilter.HasKeyword(Keyword.Flying)), notYou))),
            steps = listOf(damage(Recipients(objects = target("creature")), Value.Announced("X"))),
            announced = listOf("X"),
        )),
        Keyword.Overload(Cost.mana(mana("{X}{X}{R}{R}")), Instructions(
            steps = listOf(damage(Recipients(objects = each(all(creature, ObjectFilter.Not(ObjectFilter.HasKeyword(Keyword.Flying)), notYou))), Value.Announced("X"))),
            announced = listOf("X"),
        )),
    )

    @Test
    fun scrapwreck() = card(
        name = "Scrapwreck",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Destroy target artifact you don't control.
            Overload {4}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(Rtr.destroy(target("artifact")), targets = listOf(slot("artifact", all(artifact, notYou)))),
        overload("{4}{R}", Rtr.destroy(each(all(artifact, notYou)))),
    )

    /** "Attacks this turn if able": a requirement for the turn (CR 508.1d). */
    @Test
    fun alchemistsFeint() = card(
        name = "Alchemist's Feint",
        cost = "{U}{R}",
        type = "Instant",
        text = """
            Target creature you don't control gets -2/-0 until end of turn and attacks this turn if able.
            Overload {3}{U}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -2, 0, Mechanic.MustAttack()), targets = listOf(slot("creature", creatureYouDontControl))),
        overload("{3}{U}{R}", pump(each(creatureYouDontControl), -2, 0, Mechanic.MustAttack())),
    )

    /** The overloaded text is the spell ability's: "This spell can't be countered." stays a static ability. */
    @Test
    fun reversalCurrent() = card(
        name = "Reversal Current",
        cost = "{U}{U}{R}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Counter target spell you don't control.
            Overload {1}{U}{U}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CantBeCountered),
        spell(Step.Do(Effect.Counter(target("spell"))), targets = listOf(slot("spell", all(Rtr.spell, notYou)))),
        overload("{1}{U}{U}{R}", Step.Do(Effect.Counter(each(all(Rtr.spell, notYou))))),
    )

    /** "Can't be blocked this turn" lasts as the pump does, until end of turn. */
    @Test
    fun blinkgate() = card(
        name = "Blinkgate",
        cost = "{U}{R}",
        type = "Sorcery",
        text = """
            Target creature you control gets +1/+0 until end of turn and can't be blocked this turn.
            Overload {3}{U}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 0, Mechanic.CantBeBlocked), targets = listOf(slot("creature", creatureYouControl))),
        overload("{3}{U}{R}", pump(each(creatureYouControl), 1, 0, Mechanic.CantBeBlocked)),
    )

    // ---- Izzet spells

    /** Cast while it resolves, each card on its own, without paying (CR 608.2g); the ones not cast go to the graveyard. */
    @Test
    fun grandHypothesis() = card(
        name = "Grand Hypothesis",
        cost = "{X}{U}{R}",
        type = "Sorcery",
        text = "Exile the top X cards of your library. You may cast instant and sorcery spells with mana value X or less from among them without paying their mana costs. Then put all cards exiled this way that weren't cast into your graveyard.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Announced("X"), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.ForEach(arcanum.api.Each.Objects(Objects.Where(Objects.Bound("exiled"),
                all(ObjectFilter.Or(listOf(instant, sorcery)), ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced("X"))))), bind = "card", steps = listOf(
                Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), player = Players.You))),
            Step.Do(Effect.Move(Objects.Where(Objects.Bound("exiled"), ObjectFilter.InZone(Zone.Exile)), Destination(Zone.Graveyard))),
        ), announced = listOf("X"))),
    )

    /** "That spell's power" and controller as it last existed once countered (CR 608.2h). */
    @Test
    fun lifeforceRecoil() = card(
        name = "Lifeforce Recoil",
        cost = "{2}{U}{R}",
        type = "Instant",
        text = "Counter target creature spell. Lifeforce Recoil deals damage equal to that spell's power to its controller.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Counter(target("spell"))),
            damage(Recipients(players = Players.ControllerOf(target("spell"))), Value.Of(NumericProperty.Power, target("spell"), Combine.Single)),
            targets = listOf(slot("spell", all(creature, Rtr.spell))),
        ),
    )

    /** "Then repeat this process for … 2 and 1": the same search and move for each mana value, then one shuffle. */
    @Test
    fun mindforgePrescience() = card(
        name = "Mindforge Prescience",
        cost = "{5}{U}{R}",
        type = "Instant",
        text = "Search your library for an instant card with mana value 3, reveal it, and put it into your hand. Then repeat this process for instant cards with mana values 2 and 1. Then shuffle.",
    ) parsesTo abilities(
        spell(*listOf(3, 2, 1).flatMap { mv ->
            listOf(
                Step.Do(Effect.Search(Players.You, all(instant, ObjectFilter.ManaValue(Comparison.Equal, n(mv))), max = n(1), reveal = true,
                    zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
            )
        }.plus(Step.Do(Effect.Shuffle(Players.You))).toTypedArray()),
    )

    @Test
    fun laboratorySigil() = card(
        name = "Laboratory Sigil",
        cost = "{U}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target noncreature spell unless its controller pays {2}.
            • Laboratory Sigil deals 2 damage to target creature.
            • Draw two cards, then discard two cards.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("spell", all(ObjectFilter.Not(creature), Rtr.spell))), steps = listOf(
                Step.Pay(Cost.mana(mana("{2}")), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell")))))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(damage(Recipients(objects = target("creature")), 2)))),
            Mode(Instructions(steps = listOf(draw(2), Step.Do(Effect.Discard(Players.You, n(2), ObjectFilter.Any))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun ideaCombustion() = card(
        name = "Idea Combustion",
        cost = "{3}{U}{R}",
        type = "Instant",
        text = "Draw four cards, then discard two cards.",
    ) parsesTo abilities(
        spell(draw(4), Step.Do(Effect.Discard(Players.You, n(2), ObjectFilter.Any))),
    )

    /** "If that spell is countered this way, exile it instead": a replacement of the spell's own effect (CR 614.15). */
    @Test
    fun offbeatDenial() = card(
        name = "Offbeat Denial",
        cost = "{X}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {X}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("spell", Rtr.spell)),
            steps = listOf(
                Step.Pay(Cost.mana(mana("{X}")), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            ),
            announced = listOf("X"),
            replacements = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        )),
    )
}
