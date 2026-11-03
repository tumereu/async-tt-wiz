package arcanum.engine.input

import arcanum.engine.decision.DecisionId
import arcanum.engine.state.EffectId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a caller asks the engine to do ([arcanum.engine.Engine.perform], D56). Actions name the player acting; any
 * player may act for another (P7, D11), so the engine never checks who sent it, only that the player named may do it.
 * While a continuation is open only its [Answer] (or [Cancel] for a cast) is accepted.
 */
@Serializable
sealed interface Input {
    /** [player] plays the land [card] (a special action, CR 116.2a, 305.1), with its [face] up: a modal double-faced
     * card's back face (CR 712.11); default the front. */
    @Serializable @SerialName("play-land")
    data class PlayLand(val player: PlayerId, val card: ObjectId, val face: Int = 0) : Input

    /** [player] begins to cast [card] (CR 601.2) the [way] chosen. */
    @Serializable @SerialName("cast")
    data class Cast(val player: PlayerId, val card: ObjectId, val way: CastWay = CastWay()) : Input

    /** [player] activates [ability] of [source] (CR 602), or takes a special action it offers (CR 116.2). */
    @Serializable @SerialName("activate")
    data class Activate(val player: PlayerId, val source: ObjectId, val ability: AbilityOrigin) : Input

    /** [player] passes priority (CR 117.3d); when all pass in a row, the top of the Stack resolves or the step ends
     * (CR 117.4, 500.2, D57). */
    @Serializable @SerialName("pass-priority")
    data class PassPriority(val player: PlayerId) : Input

    /** The active player declares attackers (CR 508.1); an empty list declares none. */
    @Serializable @SerialName("declare-attackers")
    data class DeclareAttackers(val player: PlayerId, val attacks: List<Attack>) : Input

    /** The defending player declares blockers (CR 509.1). */
    @Serializable @SerialName("declare-blockers")
    data class DeclareBlockers(val player: PlayerId, val blocks: List<Block>) : Input

    /** Answers the open decision [decision] (D58); anyone may answer for its player (P7). */
    @Serializable @SerialName("answer")
    data class Answer(val decision: DecisionId, val answer: arcanum.engine.decision.Answer) : Input

    /** [player] cancels their open cast or activation, rewinding everything it did (CR 601.2, D58): only the player
     * casting or activating may (the input names them, as every action names its player; who sends it is the host's
     * business, P7). Answers to its decisions may still come from anyone. */
    @Serializable @SerialName("cancel")
    data class Cancel(val player: PlayerId) : Input

    /** Starts a game made by [arcanum.engine.Engine.newGame] (CR 103): starting player, companions, shuffles, opening
     * hands and mulligans, then the first turn ([arcanum.engine.setup.Setup]). */
    @Serializable @SerialName("start-game")
    data class StartGame(val options: arcanum.engine.setup.StartOptions = arcanum.engine.setup.StartOptions()) : Input

    /** [player] concedes (CR 104.3a): accepted at any time, also while a decision is open; they leave the game and lose. */
    @Serializable @SerialName("concede")
    data class Concede(val player: PlayerId) : Input

    /**
     * The host found a loop of mandatory actions (CR 104.4b, 732.4): where a player has priority, the game is in a state
     * with the same [arcanum.engine.Engine.loopKey] as earlier, with only forced actions in between (priority passes
     * [arcanum.engine.Engine.shouldPassFor] said, no decision answered). The engine can't see what happened in between:
     * the host vouches for it. The engine then waits on the loop choice (a [arcanum.engine.decision.Decision.ChooseOption]
     * saying the game is in such a loop): [arcanum.engine.decision.LoopStop.DRAW] makes the game a draw
     * ([arcanum.engine.setup.GameResult.Draw]), [arcanum.engine.decision.LoopStop.CONTINUE] goes on. Never offered by
     * [arcanum.engine.Engine.options].
     */
    @Serializable @SerialName("mandatory-loop")
    data object MandatoryLoop : Input

    /**
     * The players still in the game agreed to end it with [result]: a draw (CR 104.4i: all players in the game may agree
     * to an intentional draw), or a win of one of them ([arcanum.engine.setup.GameResult.Won]): every other player still
     * in the game concedes at once (CR 104.3a). The engine can't see the agreement: the host vouches for it, as for
     * [MandatoryLoop]. Accepted at any time, also while a decision is open (a cast being announced is cancelled first);
     * the game ends at once (CR 104.1). Never offered by [arcanum.engine.Engine.options].
     */
    @Serializable @SerialName("agreed-result")
    data class AgreedResult(val result: arcanum.engine.setup.GameResult) : Input

    /**
     * [player]'s own mulligan declaration ([arcanum.engine.decision.Answer.Option] keep or mulligan) or choice of cards to
     * put on the bottom ([arcanum.engine.decision.Answer.Objects]), made while another player's is the open decision, in a
     * game started with [arcanum.engine.setup.StartOptions.simultaneousMulligans] (CR 103.5, as a host variant: players
     * who don't sit together decide in any order). Accepted while the game's setup waits on a declaration or a bottom
     * choice of this round that [player] hasn't made yet; the open decision is then asked again (with a new id) unless it
     * is no longer needed.
     */
    @Serializable @SerialName("setup-answer")
    data class SetupAnswer(val player: PlayerId, val answer: arcanum.engine.decision.Answer) : Input
}

/**
 * How a card is cast (CR 601.2a–b): which [face] (a split card's half, an Adventure, a modal double-faced card's back),
 * by which [alternative] cost ability (evoke, overload, morph face down, …), and under which [permission] when it is
 * cast from somewhere other than the hand (flashback, "you may cast it from exile"). Defaults: the front face, its mana
 * cost, from the hand.
 */
@Serializable
data class CastWay(
    val face: Int = 0,
    /** The static ability holding the [arcanum.api.Mechanic.AlternativeCost] used. */
    val alternative: AbilityOrigin? = null,
    /** The permission used ([arcanum.api.Mechanic.MayPlay]). */
    val permission: Permission? = null,
    // gtc
    /** Both halves of a split card with fuse cast from the hand as one spell (CR 702.102a), [face] 0. */
    val fused: Boolean = false,
)

/** Which [arcanum.api.Mechanic.MayPlay] lets a card be played: one in a static ability of [source], or in effect
 * object [effect]. */
@Serializable
data class Permission(val source: ObjectId? = null, val ability: AbilityOrigin? = null, val effect: EffectId? = null)

/** [attacker] attacks [target]: a player, planeswalker or battle (CR 508.1b). [enlisted]: the creatures it enlists, one for
 * each of its enlist abilities at most (an optional cost to attack, CR 508.1g, 702.154a): untapped creatures its controller
 * controls that don't attack, have haste or have been under their control since the turn began. */
@Serializable
data class Attack(
    val attacker: ObjectId, val target: TargetRef, val enlisted: List<ObjectId> = emptyList(),
    /** Banding (CR 702.22c): attackers with the same number form one attacking band. */
    val band: Int? = null,
    /** The optional costs to attack chosen for it (CR 508.1g): the "you may [cost] as this creature attacks" abilities
     * ([arcanum.api.Mechanic.AsAttacks]: exert, CR 701.43d) whose cost is paid, by their origin on the attacker. Paid with
     * the other costs to attack (CR 508.1h–j); each one's "when you do" trigger then triggers (CR 607.2h). */
    val paying: List<AbilityOrigin> = emptyList(),
)

/** [blocker] blocks [attacker] (CR 509.1a). */
@Serializable
data class Block(val blocker: ObjectId, val attacker: ObjectId)
