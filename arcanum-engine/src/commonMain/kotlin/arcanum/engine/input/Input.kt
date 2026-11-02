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
     * the host vouches for it. The engine then waits on a [arcanum.engine.decision.Decision.Manual] saying the game is a
     * draw; once it is answered the game is a draw ([arcanum.engine.setup.GameResult.Draw]), unless the state was
     * changed by hand meanwhile so that its key differs, when the game goes on. Never offered by
     * [arcanum.engine.Engine.options].
     */
    @Serializable @SerialName("mandatory-loop")
    data object MandatoryLoop : Input
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
)

/** Which [arcanum.api.Mechanic.MayPlay] lets a card be played: one in a static ability of [source], or in effect
 * object [effect]. */
@Serializable
data class Permission(val source: ObjectId? = null, val ability: AbilityOrigin? = null, val effect: EffectId? = null)

/** [attacker] attacks [target]: a player, planeswalker or battle (CR 508.1b). */
@Serializable
data class Attack(val attacker: ObjectId, val target: TargetRef)

/** [blocker] blocks [attacker] (CR 509.1a). */
@Serializable
data class Block(val blocker: ObjectId, val attacker: ObjectId)
