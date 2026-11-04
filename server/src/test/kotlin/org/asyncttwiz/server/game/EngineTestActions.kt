package org.asyncttwiz.server.game

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import java.util.UUID
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.game.Act
import org.asyncttwiz.protocol.game.Submission
import org.asyncttwiz.server.accounts.AccountService

/** [actor]'s concession at the head of [gameId]'s play (CR 104.3a): it ends the play at once (I-D56 §11). The head must be
 * [actor]'s local head (true at a game's start). */
internal fun GameService.concession(actor: AccountService.Principal, gameId: String): GameAction.Submit {
    val head = requireNotNull(head(actor, gameId).engine) { "$gameId has no engine play" }.headId
    return GameAction.Submit(Submission(UUID.randomUUID().toString(), head,
        listOf(Act.Perform(Input.Concede(PlayerId(actor.account.accountId))))))
}
