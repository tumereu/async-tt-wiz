package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.AssumeReveals
import org.asyncttwiz.protocol.GameSummary

/** LOB-135: the switch texts of the game's assume reveal settings. */
internal object GameSettingsText {
    const val LIBRARY = "Allow assumes that reveal the opponent's library"
    const val LIBRARY_MEANING = "A card of the other player's library never makes you wait: each line of play reshuffles the libraries"
    const val HANDS = "Allow assumes that reveal hands"
    const val HANDS_MEANING = "A card of the other player's hand (a reveal, a random discard) never makes you wait"
    fun hostOnly(host: String) = "Only $host can change these."
}

/**
 * LOB-135: the game's assume reveal settings as two switches: both players see the current values, only the game's host
 * (its inviter) changes them ([change]); the other player's are greyed with who can.
 */
@Composable
internal fun AssumeRevealSwitches(game: GameSummary, accountId: String, change: (AssumeReveals) -> Unit) {
    val reveals = game.assumeReveals
    val host = game.inviter.accountId == accountId
    Column {
        RevealSwitch(GameSettingsText.LIBRARY, GameSettingsText.LIBRARY_MEANING, reveals.library, host, "assume-library") {
            change(reveals.copy(library = it))
        }
        RevealSwitch(GameSettingsText.HANDS, GameSettingsText.HANDS_MEANING, reveals.hands, host, "assume-hands") {
            change(reveals.copy(hands = it))
        }
        if (!host) Text(GameSettingsText.hostOnly(game.inviter.username), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RevealSwitch(label: String, meaning: String, on: Boolean, enabled: Boolean, tag: String, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = set),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel(label, meaning, Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = on, onCheckedChange = null, enabled = enabled)
    }
}

/** LOB-135: the lobby's Game settings card, below Starting player. */
@Composable
internal fun GameSettingsCard(game: GameSummary, accountId: String, change: (AssumeReveals) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Game settings", style = MaterialTheme.typography.titleSmall)
            AssumeRevealSwitches(game, accountId, change)
        }
    }
}

/** LOB-135: what Table actions → Game settings shows ([game]'s current values, as [accountId] sees them) and changes. */
class GameSettingsAccess(val game: GameSummary, val accountId: String, val change: (AssumeReveals) -> Unit)

/** LOB-135: Table actions → Game settings: the same switches in the game. */
@Composable
internal fun GameSettingsPanel(game: GameSummary, accountId: String, change: (AssumeReveals) -> Unit, onDismiss: () -> Unit) {
    TableMenuPanel(onDismiss, width = 400.dp, title = "Game settings") {
        AssumeRevealSwitches(game, accountId, change)
    }
}
