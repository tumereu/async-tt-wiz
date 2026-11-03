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
 * cast, as the permanent enters, attacks, dies, …), so it is never silently left out (D61). Where the keyword is an
 * activated ability, the engine pays its cost and only its effect is done by hand. Keywords whose meaning is engine code
 * ([engineCode]: phasing) have no abilities.
 */
object KeywordMeanings {
    /** Keywords the engine applies in its own code rather than through abilities. */
    val engineCode: Set<KeywordKind> = setOf(Keyword.Phasing,
        // gtc: casting both halves of a split card (CR 702.102) is a way to cast it ([arcanum.engine.input.CastWay.fused])
        Keyword.Fuse)

    /** The abilities [keyword] stands for; empty for [engineCode] keywords and those with no rules meaning. */
    fun expand(keyword: Keyword): List<Ability> =
        KeywordExpansions.expand(keyword) ?: if (keyword.kind in engineCode) emptyList() else manual(keyword)

    /** Whether [keyword] is done by hand ([expand] gives `Manual` abilities only). */
    fun isManual(keyword: Keyword): Boolean = KeywordExpansions.expand(keyword) == null && keyword.kind !in engineCode

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
        data object BecomesBlocked : On(listOf(EventPattern.BecomesBlocked(self, ObjectFilter.Any)))
        data object Dies : On(listOf(EventPattern.ZoneChange(self, from = Zone.Battlefield, to = Zone.Graveyard)))
        data object YourUpkeep : On(listOf(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)))
        data object CombatDamageToAPlayer : On(listOf(EventPattern.Damage(self, RecipientFilter(players = PlayerFilter.Any), combat = true)))
        data object YouCastASpell : On(listOf(EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.You)))
        data object AnotherCreatureYouControlEnters : On(listOf(EventPattern.ZoneChange(ObjectFilter.And(listOf(
            ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(self))), to = Zone.Battlefield)))
        data object AnotherCreatureEnters : On(listOf(EventPattern.ZoneChange(ObjectFilter.And(listOf(
            ObjectFilter.Type(CardType.Creature), ObjectFilter.Not(self))), to = Zone.Battlefield)))
        /** This card is put into a graveyard (cast from or activated in the graveyard). */
        data object PutIntoAGraveyard : On(listOf(EventPattern.ZoneChange(self, to = Zone.Graveyard)), functionsIn = listOf(Zone.Graveyard))
        /** This card is put into a hand (drawn; also when returned there from elsewhere). */
        data object PutIntoAHand : On(listOf(EventPattern.ZoneChange(self, to = Zone.Hand)), functionsIn = listOf(Zone.Hand))
        /** While this card is in the graveyard, a creature card is put into its owner's graveyard from the battlefield. */
        data object YourCreatureDies : On(listOf(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
            ObjectFilter.OwnedBy(PlayerFilter.You))), from = Zone.Battlefield, to = Zone.Graveyard)), functionsIn = listOf(Zone.Graveyard))

        /** An activated ability: [extra] cost parts after the keyword's own cost, paid by the engine; the effect by hand
         * unless [effect] gives it. */
        sealed class Activated(
            private val zone: Zone, private val timing: Timing, private val extra: (Keyword) -> List<CostPart>,
            private val effect: (Keyword) -> List<Step>? = { null },
        ) : Moment {
            override fun ability(keyword: Keyword, text: String): Ability {
                val own = (keyword as? arcanum.api.HasCost)?.cost?.parts.orEmpty()
                return Ability.Activated(Cost(own + extra(keyword)), Instructions(steps = effect(keyword) ?: listOf(Step.Manual(text))),
                    ActivationRules(timing = timing, activatedBy = PlayerFilter.You), functionsIn = listOf(zone))
            }
        }

        /** "[Cost], Exile this card from your graveyard: … Activate only as a sorcery." (embalm, eternalize, scavenge, encore) */
        data object ExileFromGraveyard : Activated(Zone.Graveyard, Timing.Sorcery, { listOf(CostPart.Exile(CostObjects.Self, Zone.Graveyard)) })
        /** "[Cost], Discard this card: … Activate only as a sorcery." (transmute) */
        data object DiscardAsSorcery : Activated(Zone.Hand, Timing.Sorcery, { listOf(CostPart.Discard(CostObjects.Self)) })
        /** "Tap any number of other untapped creatures you control with total power N or more: This permanent becomes
         * saddled until end of turn. Activate only as a sorcery." (saddle, CR 702.171a; saddled ends in the cleanup
         * step) */
        data object TapCreaturesWithTotalPower : Activated(Zone.Battlefield, Timing.Sorcery, { k ->
            listOf(CostPart.TapObjects(CostObjects.Choose(otherCreatureYouControl, count = null,
                total = Total(NumericProperty.Power, Value.Fixed((k as? HasAmount)?.amount ?: 0)))))
        }, { k -> if (k is Keyword.Saddle) listOf(Step.Do(arcanum.api.Effect.Mark(Objects.Self, arcanum.api.ObjectMark.Saddled))) else null })
        /** "Tap another untapped creature you control: … Activate only as a sorcery." (station) */
        data object TapAnotherCreature : Activated(Zone.Battlefield, Timing.Sorcery, {
            listOf(CostPart.TapObjects(CostObjects.Choose(otherCreatureYouControl, count = Value.Fixed(1))))
        })
    }

    private val self: ObjectFilter get() = ObjectFilter.Is(Objects.Self)
    private val otherCreatureYouControl: ObjectFilter get() = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
        ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
        ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped))))

    /** The natural moment(s) of each by-hand keyword; a keyword missing here comes up as its spell is cast. */
    val moments: Map<KeywordKind, List<Moment>> by lazy {
        val m = mutableMapOf<KeywordKind, List<Moment>>()
        fun at(vararg moment: Moment, kinds: () -> List<KeywordKind>) = kinds().forEach { m[it] = moment.toList() }
        at(Moment.Cast) {
            listOf(Keyword.Epic, Keyword.Gravestorm, Keyword.Conspire, Keyword.Assist,
                Keyword.Demonstrate, Keyword.Bargain, Keyword.Spree, Keyword.Tiered, Keyword.Undaunted, Keyword.Ascend, Keyword.Ripple.Kind,
                Keyword.Entwine.Kind, Keyword.Replicate.Kind, Keyword.Prowl.Kind,
                Keyword.Dash.Kind, Keyword.Surge.Kind, Keyword.Emerge.Kind, Keyword.Spectacle.Kind, Keyword.Mutate.Kind,
                Keyword.Foretell.Kind, Keyword.Blitz.Kind, Keyword.Squad.Kind, Keyword.Disguise.Kind, Keyword.Plot.Kind,
                Keyword.Freerunning.Kind, Keyword.Warp.Kind, Keyword.WebSlinging.Kind,
                Keyword.Awaken.Kind, Keyword.Impending.Kind, Keyword.Prototype.Kind)
        }
        at(Moment.Enters) {
            listOf(Keyword.HiddenAgenda, Keyword.Riot, Keyword.Daybound, Keyword.Nightbound, Keyword.Compleated,
                Keyword.ReadAhead, Keyword.Ravenous, Keyword.StartYourEngines, Keyword.Amplify.Kind,
                Keyword.Bloodthirst.Kind, Keyword.Devour.Kind, Keyword.Tribute.Kind, Keyword.Backup.Kind, Keyword.Echo.Kind,
                Keyword.PartnerWith.Kind)
        }
        at(Moment.Enters, Moment.AnotherCreatureYouControlEnters) { listOf(Keyword.Soulbond) }
        at(Moment.Enters, Moment.AnotherCreatureEnters) { listOf(Keyword.Graft.Kind) }
        at(Moment.Attacks) {
            listOf(Keyword.Provoke, Keyword.Myriad, Keyword.Melee, Keyword.Dethrone, Keyword.Enlist, Keyword.Mobilize.Kind)
        }
        at(Moment.AttacksOrBlocks) { listOf(Keyword.Banding) }
        at(Moment.BecomesBlocked) { listOf(Keyword.Rampage.Kind) }
        at(Moment.Dies) { listOf(Keyword.Soulshift.Kind) }
        at(Moment.Dies, Moment.Cast) { listOf(Keyword.Haunt) }
        at(Moment.YourUpkeep) { listOf(Keyword.CumulativeUpkeep.Kind) }
        at(Moment.CombatDamageToAPlayer) { listOf(Keyword.Ingest) }
        at(Moment.PutIntoAGraveyard) { listOf(Keyword.Aftermath, Keyword.Disturb.Kind, Keyword.Harmonize.Kind, Keyword.Mayhem.Kind) }
        at(Moment.YourCreatureDies) { listOf(Keyword.Recover.Kind) }
        at(Moment.ExileFromGraveyard) { listOf(Keyword.Embalm.Kind, Keyword.Eternalize.Kind, Keyword.Encore.Kind) }
        at(Moment.DiscardAsSorcery) { listOf(Keyword.Transmute.Kind) }
        at(Moment.TapCreaturesWithTotalPower) { listOf(Keyword.Saddle.Kind) }
        at(Moment.TapAnotherCreature) { listOf(Keyword.Station) }
        m
    }
}
