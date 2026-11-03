package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.EventPattern
import arcanum.api.HasAmount
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordExpansions
import arcanum.api.KeywordKind
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Total
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone

/**
 * What a keyword ability means to the engine: its expansion from the shared table ([KeywordExpansions]), or, for a
 * keyword the engine doesn't automate yet, abilities that stop with a `Manual` step at its natural moment (as the spell is
 * cast, as the permanent enters, attacks, dies, …), so it is never silently left out (D61). Keywords whose meaning is engine
 * code ([engineCode]: phasing, fuse, foretell) have no abilities.
 */
object KeywordMeanings {
    /** Keywords the engine applies in its own code rather than through abilities. */
    val engineCode: Set<KeywordKind> = setOf(Keyword.Phasing,
        // gtc: casting both halves of a split card (CR 702.102) is a way to cast it ([arcanum.engine.input.CastWay.fused])
        Keyword.Fuse,
        // CR 702.143: foretelling is a special action the rules give a card in a hand that keeps it hidden, and casting it
        // a permission the rules give its owner (arcanum.engine.foretell.Foretell)
        Keyword.Foretell.Kind)

    /** Keywords whose abilities depend on the face they are printed on ([expand]'s `below`). */
    val faceDependent: Set<KeywordKind> = setOf(Keyword.Backup.Kind)

    /** The abilities [keyword] stands for; empty for [engineCode] keywords and those with no rules meaning. [below]: the
     * abilities printed after it on its face (backup grants them, CR 702.165a). */
    fun expand(keyword: Keyword, below: List<Ability> = emptyList()): List<Ability> =
        KeywordExpansions.expand(keyword) ?: when {
            keyword.kind in engineCode -> emptyList()
            keyword is Keyword.Backup -> listOf(backup(keyword, below))
            else -> manual(keyword)
        }

    /** Whether [keyword] is done by hand ([expand] gives `Manual` abilities only). */
    fun isManual(keyword: Keyword): Boolean = KeywordExpansions.expand(keyword) == null && keyword.kind !in engineCode &&
        keyword.kind !in faceDependent

    /**
     * CR 702.165a, c, d: "When this creature enters, put N +1/+1 counters on target creature. If that's another creature, it also
     * gains the non-backup abilities of this creature printed below this one until end of turn." [below] are the abilities printed
     * after the backup ability on its face, fixed as the trigger is created.
     */
    private fun backup(keyword: Keyword.Backup, below: List<Ability>): Ability {
        val target = Objects.Target("backup")
        val granted = below.filterNot { it is Keyword.Backup }
        return Ability.Triggered(Trigger.On(EventPattern.ZoneChange(self, to = Zone.Battlefield)), Instructions(
            targets = listOf(arcanum.api.TargetSlot("backup", arcanum.api.TargetKind.Objects(ObjectFilter.Type(CardType.Creature)),
                min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOfNotNull(
                Step.Do(arcanum.api.Effect.PutCounters(target, arcanum.api.CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount))),
                if (granted.isEmpty()) null else Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.ObjectMatches(target, self)), listOf(
                    Step.Do(arcanum.api.Effect.ApplyMechanics(listOf(arcanum.api.Mechanic.GainAbilities(granted)), arcanum.api.Affected.Objects(target),
                        arcanum.api.Duration.EndOfTurn)))),
            )))
    }

    /** The by-hand abilities of a keyword without an expansion, at its natural moment ([moments]; as cast when not listed). */
    fun manual(keyword: Keyword): List<Ability> {
        val amount = (keyword as? HasAmount)?.amount?.let { " $it" }.orEmpty()
        val text = "${keyword.text}$amount (CR ${keyword.rule}): apply it by hand"
        return (moments[keyword.kind] ?: listOf(Moment.Cast)).map { it.ability(keyword, text) }
    }

    /** When a by-hand keyword comes up. */
    sealed interface Moment {
        fun ability(keyword: Keyword, text: String): Ability

        /** A triggered ability ("when …") whose effect is the manual step. */
        sealed class On(private val events: List<EventPattern>, private val functionsIn: List<Zone>? = null) : Moment {
            override fun ability(keyword: Keyword, text: String): Ability =
                Ability.Triggered(Trigger.On(events), Instructions(steps = listOf(Step.Manual(text))), functionsIn = functionsIn)
        }

        /** As this spell is cast (its alternative or additional costs, its own rules on the Stack). */
        data object Cast : On(listOf(EventPattern.Cast(self, by = PlayerFilter.Any)))
        /** As this permanent enters (its replacements and "enters" triggers). */
        data object Enters : On(listOf(EventPattern.ZoneChange(self, to = Zone.Battlefield)))
        data object Attacks : On(listOf(EventPattern.Attack(self)))
        data object AttacksOrBlocks : On(listOf(EventPattern.Attack(self), EventPattern.Block(self, ObjectFilter.Any)))
        data object Dies : On(listOf(EventPattern.ZoneChange(self, from = Zone.Battlefield, to = Zone.Graveyard)))
        data object AnotherCreatureYouControlEnters : On(listOf(EventPattern.ZoneChange(ObjectFilter.And(listOf(
            ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(self))), to = Zone.Battlefield)))
        /** This card is put into a graveyard (cast from or activated in the graveyard). */
        data object PutIntoAGraveyard : On(listOf(EventPattern.ZoneChange(self, to = Zone.Graveyard)), functionsIn = listOf(Zone.Graveyard))
    }

    private val self: ObjectFilter get() = ObjectFilter.Is(Objects.Self)

    /** The natural moment(s) of each by-hand keyword; a keyword missing here comes up as its spell is cast. */
    val moments: Map<KeywordKind, List<Moment>> by lazy {
        val m = mutableMapOf<KeywordKind, List<Moment>>()
        fun at(vararg moment: Moment, kinds: () -> List<KeywordKind>) = kinds().forEach { m[it] = moment.toList() }
        at(Moment.Cast) {
            listOf(Keyword.Epic, Keyword.Assist, Keyword.Ripple.Kind, Keyword.Emerge.Kind, Keyword.Mutate.Kind, Keyword.Prototype.Kind)
        }
        at(Moment.Enters) {
            listOf(Keyword.HiddenAgenda, Keyword.Daybound, Keyword.Nightbound, Keyword.Compleated, Keyword.ReadAhead,
                Keyword.StartYourEngines)
        }
        at(Moment.Enters, Moment.AnotherCreatureYouControlEnters) { listOf(Keyword.Soulbond) }
        at(Moment.Attacks) { listOf(Keyword.Enlist) }
        at(Moment.AttacksOrBlocks) { listOf(Keyword.Banding) }
        at(Moment.Dies, Moment.Cast) { listOf(Keyword.Haunt) }
        at(Moment.PutIntoAGraveyard) { listOf(Keyword.Aftermath, Keyword.Harmonize.Kind) }
        m
    }
}
