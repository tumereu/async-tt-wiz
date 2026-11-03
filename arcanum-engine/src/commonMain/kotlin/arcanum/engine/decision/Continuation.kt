package arcanum.engine.decision

import arcanum.api.Ability
import arcanum.engine.action.PlannedEvent
import arcanum.engine.change.Change
import arcanum.engine.input.CastWay
import arcanum.engine.state.Bindings
import arcanum.engine.state.DividedShare
import arcanum.engine.state.EffectId
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TriggerEvent
import arcanum.engine.view.AbilityOrigin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A flow the engine left part-way to wait for a decision (D58): an open cast or activation, a resolution, triggers
 * waiting to go on the Stack, a state-based action or turn-based action with a choice. Hosts store it as-is (opaque
 * JSON) and pass it back with the state; only the engine reads it. Everything done before the decision was already
 * returned as changes and applied, so a continuation holds only the position and what was chosen so far.
 *
 * [then] is what the engine goes on with after this flow ends (a resolution is followed by trigger placement, which is
 * followed by priority).
 */
@Serializable
sealed interface Continuation {
    val then: Continuation?

    /** Casting a spell (CR 601.2) or activating an ability (CR 602.2), at [stage]. [made] are the changes made so far,
     * so [arcanum.engine.input.Input.Cancel] can take them all back (CR 601.2, D58). */
    @Serializable @SerialName("announcing")
    data class Announcing(
        val player: PlayerId,
        val what: Announced,
        val stage: AnnounceStage,
        val choices: Choices = Choices(),
        val made: List<Change> = emptyList(),
        override val then: Continuation? = null,
    ) : Continuation

    /** Resolving Stack item [item] (CR 608.2): [step] is the index of the next step of its instructions (the chosen
     * modes' steps, then the common ones); [names] what was bound so far (targets still legal, announced values, binds
     * of earlier steps). A step that needs decisions is redone from its start with [answers] so far (D29, D31); steps
     * before it are committed. [left] is the item once its card has left the Stack during its own resolution (a spell
     * shuffled into its library by its own steps, CR 400.7): it is then in no zone and not in `stackItems`, and its
     * remaining steps still run. */
    @Serializable @SerialName("resolving")
    data class Resolving(
        val item: ObjectId,
        val step: Int = 0,
        val names: Bindings = Bindings.EMPTY,
        val answers: List<Answer> = emptyList(),
        val left: arcanum.engine.state.StackItem? = null,
        override val then: Continuation? = null,
        /** Set once the resolution reached cards only another player may look at in this viewer's state: from [step]
         * on it is worked out as in that player's view ([Decision.OtherView], D60, D31). */
        val otherView: OtherViewFlow? = null,
    ) : Continuation

    /** Triggered abilities waiting to be put on the Stack (CR 603.3), next time a player would get priority; asks for
     * an order, targets or modes when there is a choice (P5). */
    @Serializable @SerialName("placing-triggers")
    data class PlacingTriggers(
        val waiting: List<WaitingTrigger>,
        /** The trigger being put on the Stack right now, with its choices so far. */
        val current: Announcing? = null,
        override val then: Continuation? = null,
        /** The answers so far to the order, mode and target decisions of putting [waiting] on the Stack; the placement is
         * redone from its start with them (D29). */
        val answers: List<Answer> = emptyList(),
    ) : Continuation

    /** State-based actions waiting on a choice (the legend rule, CR 704.5j); checked again afterwards (CR 704.3). */
    @Serializable @SerialName("state-based")
    data class StateBased(override val then: Continuation? = null, val answers: List<Answer> = emptyList()) : Continuation

    /** A turn-based action with a choice (CR 703): combat damage assignment (CR 510.1), the cleanup discard (CR 514.1). */
    @Serializable @SerialName("turn-based")
    data class TurnBased(val action: TurnAction, override val then: Continuation? = null) : Continuation

    /** Starting the game (CR 103) at [stage] with [options]: the stage is redone from its start with [answers] so far
     * (D29); earlier stages are committed ([arcanum.engine.setup.Setup]). */
    @Serializable @SerialName("setup")
    data class Setup(
        val stage: arcanum.engine.setup.SetupStage,
        val options: arcanum.engine.setup.StartOptions,
        val answers: List<Answer> = emptyList(),
        override val then: Continuation? = null,
    ) : Continuation

    /** A land play, cast or activation ([input]) of a card the state's viewer doesn't know, waiting for the host to fill
     * the card in publicly (D60; it becomes public as it is played, CR 305.1, 601.2a, 602.2a); [input] is then redone. */
    @Serializable @SerialName("played")
    data class Played(val input: arcanum.engine.input.Input, override val then: Continuation? = null) : Continuation

    /** An event paused for a replacement choice or an "as enters" step (CR 614.12, 616.1, D29/D36): [event] is redone
     * from its start with [answers] so far. */
    @Serializable @SerialName("event")
    data class Event(
        val event: List<Change>,
        val answers: List<Answer> = emptyList(),
        override val then: Continuation? = null,
        /** The event to redo: what was about to happen before replacements (D29). */
        val planned: PlannedEvent? = null,
    ) : Continuation

    /** The game stopped for a loop ([stop]), waiting on the loop choice: a [Decision.ChooseOption] of
     * [LoopStop.CONTINUE] and [LoopStop.DRAW] whose text says why (CR 104.4b). */
    @Serializable @SerialName("loop")
    data class Loop(val stop: LoopStop, override val then: Continuation? = null) : Continuation
}

/** Why a [Continuation.Loop] stopped the game. Either way the loop choice follows: [CONTINUE] goes on with the game,
 * [DRAW] ends it as a draw (CR 104.4b). */
@Serializable
sealed interface LoopStop {
    /** The safety cap of one [arcanum.engine.Engine.perform] call: too many rounds of state-based actions and triggers,
     * or too many changes (a possible loop no player can stop). */
    @Serializable @SerialName("cap") data object Cap : LoopStop

    /** A loop of mandatory actions the host found (CR 104.4b, 732.4) at a state with [key]. */
    @Serializable @SerialName("mandatory") data class Mandatory(val key: arcanum.engine.turn.LoopKey) : LoopStop

    companion object {
        /** The loop choice's option that goes on with the game (in a new call, up to the cap again). */
        const val CONTINUE = "Continue"
        /** The loop choice's option that ends the game as a draw (CR 104.4b). */
        const val DRAW = "Draw"
    }
}

/** A resolution worked out as in [player]'s view ([Decision.OtherView]): [source] and [reason] are those of the look that
 * began it; [hidden] the cards only [player] has seen in it so far, by card (their identities aren't kept: the host
 * sends them again with each answer). */
@Serializable
data class OtherViewFlow(
    val player: PlayerId,
    val source: ObjectId?,
    val reason: String,
    val hidden: List<arcanum.engine.state.CardId> = emptyList(),
)

/** What is being cast or activated. */
@Serializable
sealed interface Announced {
    /** The card [card], cast [way] (CR 601.2a); [permitted] is the permission it is cast under as it was before the card
     * moved (retrace granted only to cards in a graveyard still adds its cost, CR 601.2f). [byEffect]: cast while another
     * spell or ability resolves (CR 608.2g), on the terms [permitted] gives (without paying its mana cost, or for
     * another cost); nobody gets priority after it. */
    @Serializable @SerialName("spell") data class Spell(
        val card: ObjectId, val way: CastWay, val permitted: arcanum.api.Mechanic.MayPlay? = null,
        val byEffect: Boolean = false,
        // tmt
        /** The alternative cost [way] names as the card had it where it was cast from, before it moved to the Stack (CR 601.2a,
         * b): "creature cards in your graveyard have sneak" is used though the spell no longer has it. */
        val alternativeAsCast: arcanum.api.Mechanic.AlternativeCost? = null,
    ) : Announced
    /** Ability [origin] of [source] (CR 602.2a), with a copy of the [ability] (D23). */
    @Serializable @SerialName("ability") data class Ability(
        val source: ObjectId, val origin: AbilityOrigin, val ability: arcanum.api.Ability,
    ) : Announced
    /** A triggered ability being put on the Stack (CR 603.3d). */
    @Serializable @SerialName("trigger") data class Trigger(val trigger: WaitingTrigger) : Announced
}

/** Where a cast or activation is (CR 601.2b–h, 602.2b). */
@Serializable
sealed interface AnnounceStage {
    @Serializable @SerialName("modes") data object Modes : AnnounceStage
    @Serializable @SerialName("announce") data object Announce : AnnounceStage
    @Serializable @SerialName("targets") data object Targets : AnnounceStage
    @Serializable @SerialName("divide") data object Divide : AnnounceStage
    @Serializable @SerialName("costs") data object Costs : AnnounceStage
    @Serializable @SerialName("mana") data object Mana : AnnounceStage
}

/** The choices made so far while casting or activating; they become the [arcanum.engine.state.StackItem]. */
@Serializable
data class Choices(
    val modes: List<Int> = emptyList(),
    val announced: Map<String, Int> = emptyMap(),
    val targets: Map<String, List<TargetRef>> = emptyMap(),
    val divided: List<DividedShare> = emptyList(),
    /** Optional and additional costs chosen (kicker, buyback), by name. */
    val costs: Bindings = Bindings.EMPTY,
    /** Names bound by paying costs ("the sacrificed creature"). */
    val names: Bindings = Bindings.EMPTY,
    val snapshots: List<ObjectSnapshot> = emptyList(),
    /** The payment under way when paying waits for a decision (a replacement effect on a cost's event, CR 601.2h,
     * 614): the mana [Paying.uses] answered and the answers so far; the payment is redone from its start with them (D29). */
    val paying: Paying? = null,
)

/** A payment being redone with its [uses] and the [answers] to the decisions it asked so far (D29). */
@Serializable
data class Paying(val uses: List<ManaUse>, val answers: List<Answer> = emptyList())

/** A triggered ability that triggered and waits to be put on the Stack (CR 603.3), with its controller (CR 109.4b),
 * a copy of the ability (D23), its source's last known information and the event it triggered on; a delayed trigger's
 * [snapshots] of the objects it refers to (D21). [oneShot]: not a triggered ability but the second one-shot effect of an
 * "until" (CR 610.3), done right after the event without the Stack. */
@Serializable
data class WaitingTrigger(
    val controller: PlayerId,
    val ability: Ability,
    val origin: AbilityOrigin?,
    val source: ObjectId?,
    val sourceSeen: ObjectSnapshot? = null,
    val event: TriggerEvent = TriggerEvent(),
    val delayedTrigger: EffectId? = null,
    val names: Bindings = Bindings.EMPTY,
    val snapshots: List<ObjectSnapshot> = emptyList(),
    val oneShot: Boolean = false,
)

/** A turn-based action waiting for a choice (CR 703). */
@Serializable
sealed interface TurnAction {
    /** Assigning combat damage of [attackers] still to do (CR 510.1). */
    @Serializable @SerialName("combat-damage") data class CombatDamage(
        val firstStrike: Boolean, val assigned: List<Assignment> = emptyList(), val attackers: List<ObjectId>,
    ) : TurnAction
    /** The active player discards down to their maximum hand size (CR 514.1), through the event pipeline (madness):
     * redone from its start with the [answers] so far (D29). */
    @Serializable @SerialName("cleanup-discard") data class CleanupDiscard(val answers: List<Answer> = emptyList()) : TurnAction
}

/** [amount] combat damage from [source] assigned to [target]. */
@Serializable
data class Assignment(val source: ObjectId, val target: TargetRef, val amount: Int)
