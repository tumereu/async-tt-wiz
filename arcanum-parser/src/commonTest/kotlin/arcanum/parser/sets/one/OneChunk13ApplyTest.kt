package arcanum.parser.sets.one

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.*
import kotlin.test.Test

/**
 * Chunk 13: "gets +N/+N and gains ..." until end of turn in its many shapes (CR 611.2a, 613.4c, 702.164 toxic), X counted from
 * the battlefield (CR 107.2), an oil counter put on the target (CR 122.1), fights (CR 701.14), activation limits (CR 602.5b)
 * and "equipped creatures you control" (CR 301.5).
 */
class OneChunk13ApplyTest {
    private fun spellOn(targets: List<arcanum.api.TargetSlot>, vararg steps: Step, announced: List<String> = emptyList()) =
        spell(*steps, targets = targets, announced = announced)
    private val oil = CounterKind.Named("oil")
    private val equippedCreatures = Objects.Each(and(ObjectFilter.Attached(attachments = equipment), creature, you))
    private val artifactsYouControl = Value.Count(Objects.Each(and(artifact, you)))

    // CR 611.2a, 702.164a: toxic is granted along with flying
    @Test
    fun aspirantsAscent() = card(
        name = "Hopeful Surge",
        cost = "{U}",
        type = "Instant",
        text = "Until end of turn, target creature gets +1/+3 and gains flying and toxic 1. (Players dealt combat damage by that creature also get a poison counter.)",
    ) parsesTo abilities(
        spellOn(listOf(slot("creature", creature)), pump(target("creature"), 1, 3, has(Keyword.Flying, Keyword.Toxic(1)))),
    )

    // CR 611.2a, 400.7: the exiled card is playable until the end of your next turn
    @Test
    fun blazingCrescendo() = card(
        name = "Scorching Crescendo",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature gets +3/+1 until end of turn.
            Exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 3, 1),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 602.5b: once each turn; CR 118.3: the sacrificed permanent is another artifact or creature
    @Test
    fun cutthroatCenturion() = card(
        name = "Ruthless Legionary",
        cost = "{2}{B}",
        type = "Artifact Creature — Phyrexian Warrior",
        pt = "2/2",
        text = "Sacrifice another artifact or creature: This creature gets +2/+2 until end of turn. Activate only once each turn.",
    ) parsesTo abilities(
        activated(cost(sacrificeA(and(another, or(artifact, creature)))), pump(Objects.Self, 2, 2), rules = anyTime.copy(perTurn = 1)),
    )

    // CR 107.2: X is the number of artifacts you control as the ability resolves; -X/-0 (CR 613.4c)
    @Test
    fun escapedExperiment() = card(
        name = "Fled Prototype",
        cost = "{1}{U}",
        type = "Artifact Creature — Phyrexian Beast",
        pt = "2/1",
        text = "Whenever this creature attacks, target creature an opponent controls gets -X/-0 until end of turn, where X is the number of artifacts you control.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Times(artifactsYouControl, -1), n(0))),
            targets = listOf(slot("creature", and(creature, opponents)))),
    )

    // CR 122.1: oil counters are a named kind; "it" is the target
    @Test
    fun freeFromFlesh() = card(
        name = "Freed from Marrow",
        cost = "{R}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. Put two oil counters on it.",
    ) parsesTo abilities(
        spellOn(listOf(slot("creature", creature)), pump(target("creature"), 2, 2), counters(target("creature"), 2, oil)),
    )

    // CR 603.4, 613.4c: X counts equipped creatures; "Then if" checks the power after the bonus (CR 608.2c)
    @Test
    fun jorKadeen() = card(
        name = "Jarn Kodel, First Warden",
        cost = "{R}{W}",
        type = "Legendary Creature — Human Rebel",
        pt = "2/2",
        text = """
            Trample
            Whenever Jarn Kodel attacks, it gets +X/+X until end of turn, where X is the number of equipped creatures you control. Then if Jarn Kodel's power is 4 or greater, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(attacksSelf,
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(and(ObjectFilter.Attached(attachments = equipment), creature, you)),
                count(and(ObjectFilter.Attached(attachments = equipment), creature, you)))),
            Step.If(Condition.ObjectMatches(Objects.Self, power(Comparison.AtLeast, 4)), then = listOf(draw(1)))),
    )

    // CR 613.4c: X counts permanents with oil counters
    @Test
    fun kuldothaCackler() = card(
        name = "Kuldor Cackler",
        cost = "{2}{R}",
        type = "Creature — Phyrexian Hyena",
        pt = "2/3",
        text = """
            Trample
            Whenever this creature attacks, it gets +X/+0 until end of turn, where X is the number of permanents you control with oil counters on them.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(attacksSelf,
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(and(permanentFilter, you, ObjectFilter.HasCounter(oil, atLeast = 1))), n(0)))),
    )

    // CR 603.2: another artifact only, not the creature itself
    @Test
    fun mandibleJusticiar() = card(
        name = "Chitin Magistrate",
        cost = "{1}{W}",
        type = "Artifact Creature — Phyrexian Cleric",
        pt = "2/1",
        text = """
            Lifelink
            Whenever another artifact you control enters, this creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(enters(and(another, artifact, you)), pump(Objects.Self, 1, 1)),
    )

    // CR 301.5, 702.12: only equipped creatures you control gain indestructible
    @Test
    fun resistanceReunited() = card(
        name = "Rebels Rejoined",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Target creature gets +2/+2 until end of turn.
            Equipped creatures you control gain indestructible until end of turn. (Damage and effects that say "destroy" don't destroy them.)
        """,
    ) parsesTo abilities(
        spellOn(listOf(slot("creature", creature)), pump(target("creature"), 2, 2), gains(equippedCreatures, Keyword.Indestructible)),
    )

    // CR 701.14a: the pumped creature fights a creature you don't control
    @Test
    fun ruthlessPredation() = card(
        name = "Pitiless Hunt",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Target creature you control gets +1/+2 until end of turn. It fights target creature you don't control. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spellOn(
            listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Not(arcanum.api.PlayerFilter.You))))),
            pump(target("creature"), 1, 2),
            doing(Effect.Fight(target("creature"), target("second creature"))),
        ),
    )

    // CR 107.3a, 702.11, 702.12: +X/+X with X paid
    @Test
    fun tyvarsStand() = card(
        name = "Tolvar's Stand",
        cost = "{X}{G}",
        type = "Instant",
        text = "Target creature you control gets +X/+X and gains hexproof and indestructible until end of turn. (It can't be the target of spells or abilities your opponents control. Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spellOn(listOf(slot("creature", creatureYouControl)),
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Announced("X"), Value.Announced("X")), has(Keyword.Hexproof, Keyword.Indestructible)),
            announced = listOf("X")),
    )

    // CR 603.2, 611.2a: this artifact or another one
    @Test
    fun veilOfAssimilation() = card(
        name = "Shroud of Absorption",
        cost = "{1}{W}",
        type = "Artifact",
        text = "Whenever this artifact or another artifact you control enters, target creature you control gets +1/+1 and gains vigilance until end of turn.",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, artifact, you))), pump(target("creature"), 1, 1, has(Keyword.Vigilance)),
            targets = listOf(slot("creature", creatureYouControl))),
    )
}
