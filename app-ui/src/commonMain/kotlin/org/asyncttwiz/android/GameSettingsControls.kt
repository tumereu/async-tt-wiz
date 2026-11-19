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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.asyncttwiz.protocol.AssumeReveals
import org.asyncttwiz.protocol.DeckCategories
import org.asyncttwiz.protocol.DeckCategory
import org.asyncttwiz.protocol.DeckLending
import org.asyncttwiz.protocol.DeckSettings
import org.asyncttwiz.protocol.GameSummary

/** LOB-135: the switch texts of the game's assume reveal settings. */
object GameSettingsText {
    const val LIBRARY = "Allow assumes that reveal the opponent's library"
    const val LIBRARY_MEANING = "A card of the other player's library never makes you wait: each line of play reshuffles the libraries"
    const val HANDS = "Allow assumes that reveal hands"
    const val HANDS_MEANING = "A card of the other player's hand (a reveal, a random discard) never makes you wait"
    fun hostOnly(host: String) = "Only $host can change these."
    // LOB-247..LOB-251, LOB-254: the Decks group
    const val DECKS = "Decks"
    const val LEND = "Lend my decks"
    fun lends(host: String) = "$host lends decks"
    const val ONLY_LENT = "Only lent decks"
    const val ONLY_LENT_MEANING = "Both players use only lent decks: a battle box"
    const val SAME_DECK = "Same deck allowed"
    const val SAME_DECK_MEANING = "Both players may use the same deck"
    const val DECK_CHOICE = "Deck choice"
    const val EACH_PICKS = "Each picks"
    const val RANDOM = "Random"
    const val RANDOM_MEANING = "When both are Ready, each player is dealt a lent deck at random"
    const val REDEAL = "New random decks on rematch"
    const val REDEAL_MEANING = "Every rematch deals new decks"
    /** What Lend my decks is set to, as [viewer]'s side shows it: a creator names their categories, the other player
     * counts them. */
    fun lending(lend: DeckLending, categories: List<DeckCategory>?): String = when (lend) {
        DeckLending.Off -> "Off"
        DeckLending.All -> "All"
        is DeckLending.Categories -> categories?.let { all -> lend.ids.mapNotNull { id -> DeckCategories.pathName(all, id).takeIf { it.isNotEmpty() } } }
            ?.takeIf { it.size == lend.ids.size }?.joinToString(", ")
            ?: if (lend.ids.size == 1) "1 category" else "${lend.ids.size} categories"
    }
}

/** LOB-247: the lobby's Decks settings as the Game settings card shows them: [categories], the creator's own (null until
 * read, [load] reads them), and [change] sending a new value. */
class DeckSettingsAccess(val categories: List<DeckCategory>?, val load: () -> Unit, val change: (DeckSettings) -> Unit)

/**
 * LOB-135: the game's assume reveal settings as two switches: both players see the current values, only the game's host
 * (its inviter) changes them ([change]); the other player's are greyed with who can.
 */
@Composable
fun AssumeRevealSwitches(game: GameSummary, accountId: String, change: (AssumeReveals) -> Unit, note: Boolean = true) {
    val reveals = game.assumeReveals
    val host = game.inviter.accountId == accountId
    Column {
        RevealSwitch(GameSettingsText.LIBRARY, GameSettingsText.LIBRARY_MEANING, reveals.library, host, "assume-library") {
            change(reveals.copy(library = it))
        }
        RevealSwitch(GameSettingsText.HANDS, GameSettingsText.HANDS_MEANING, reveals.hands, host, "assume-hands") {
            change(reveals.copy(hands = it))
        }
        if (!host && note) HostOnlyNote(game)
    }
}

@Composable
private fun HostOnlyNote(game: GameSummary) = Text(GameSettingsText.hostOnly(game.inviter.username), style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

/**
 * LOB-247..LOB-251, LOB-254: the Game settings card's Decks group: both players see the values, only the game's creator
 * changes them; while nothing is lent the settings that need lending are greyed.
 */
@Composable
fun DeckSettingsGroup(game: GameSummary, accountId: String, access: DeckSettingsAccess, enabled: Boolean) {
    val settings = game.deckSettings
    val host = game.inviter.accountId == accountId && enabled
    var lendDialog by remember { mutableStateOf(false) }
    // The creator's lent categories show by name: their categories are read once the group shows
    LaunchedEffect(host) { if (host && access.categories == null) access.load() }
    Column(Modifier.testTag("deck-settings")) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(GameSettingsText.DECKS, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp))
        val lendLabel = if (game.inviter.accountId == accountId) GameSettingsText.LEND else GameSettingsText.lends(game.inviter.username)
        val lendValue = GameSettingsText.lending(settings.lend, access.categories.takeIf { game.inviter.accountId == accountId })
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(host, onClickLabel = "Choose decks to lend") { access.load(); lendDialog = true }
            .testTag("lend-decks").semantics(mergeDescendants = true) { contentDescription = "$lendLabel: $lendValue" },
            verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel(lendLabel, modifier = Modifier.weight(1f))
            Text(lendValue, Modifier.padding(start = 12.dp).widthIn(max = 200.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (host) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (host) Icon(screenIcon("chevron"), null, Modifier.size(20.dp))
        }
        val lending = settings.lending
        RevealSwitch(GameSettingsText.ONLY_LENT, GameSettingsText.ONLY_LENT_MEANING, settings.onlyLent, host && lending, "only-lent") {
            access.change(settings.copy(onlyLent = it)) }
        RevealSwitch(GameSettingsText.SAME_DECK, GameSettingsText.SAME_DECK_MEANING, settings.sameDeck, host && lending, "same-deck") {
            access.change(settings.copy(sameDeck = it)) }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            SettingsLabel(GameSettingsText.DECK_CHOICE)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!settings.random, { access.change(settings.copy(random = false)) }, { Text(GameSettingsText.EACH_PICKS) },
                    enabled = host && lending, modifier = Modifier.testTag("deck-choice-each"))
                FilterChip(settings.random, { access.change(settings.copy(random = true)) }, { Text(GameSettingsText.RANDOM) },
                    enabled = host && lending, modifier = Modifier.testTag("deck-choice-random"))
            }
            Text(GameSettingsText.RANDOM_MEANING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RevealSwitch(GameSettingsText.REDEAL, GameSettingsText.REDEAL_MEANING, settings.redeal, host && lending && settings.random, "redeal") {
            access.change(settings.copy(redeal = it)) }
    }
    if (lendDialog) LendDecksDialog(settings.lend, access.categories, { lendDialog = false }) { lend ->
        lendDialog = false; access.change(settings.copy(lend = lend)) }
}

/** LOB-248: the creator chooses what to lend: Off, All, or categories of theirs (one or several, their subcategories
 * included, LOB-259). */
@Composable
fun LendDecksDialog(current: DeckLending, categories: List<DeckCategory>?, dismiss: () -> Unit, done: (DeckLending) -> Unit) {
    var choice by remember { mutableStateOf<DeckLending>(current) }
    var chosen by remember { mutableStateOf((current as? DeckLending.Categories)?.ids?.toSet() ?: emptySet()) }
    val picking = choice is DeckLending.Categories
    ScreenDialog(dismiss, title = GameSettingsText.LEND, busyMessage = if (picking && categories == null) "Loading categories…" else null, actions = {
        DialogCancelButton("Cancel", dismiss)
        DialogMainButton("Done", { done(if (picking) DeckLending.Categories(chosen.toList().sorted()) else choice) },
            enabled = !picking || chosen.isNotEmpty(), modifier = Modifier.testTag("lend-done"))
    }) {
        CategoryChoiceRow("Off", 0, choice == DeckLending.Off, true) { choice = DeckLending.Off }
        CategoryChoiceRow("All my decks", 0, choice == DeckLending.All, true) { choice = DeckLending.All }
        CategoryChoiceRow("Chosen categories", 0, picking, true) { choice = DeckLending.Categories(chosen.toList()) }
        if (picking) {
            val all = categories.orEmpty()
            // Choosing a category includes its subcategories: they show checked, greyed
            val included = DeckCategories.within(all, chosen) - chosen
            for ((category, depth) in DeckCategories.ordered(all))
                CategoryCheckRow(category.name, depth + 1, category.id in chosen || category.id in included, category.id !in included) { on ->
                    chosen = if (on) chosen + category.id else chosen - category.id }
            if (categories != null && all.isEmpty()) Text("You have no categories yet: make them on Manage decks' Decks tab.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

/** LOB-135: the lobby's Game settings card, below Starting player; LOB-247: its Decks group ([decks], in a game that isn't
 * a pod's). */
@Composable
fun GameSettingsCard(game: GameSummary, accountId: String, decks: DeckSettingsAccess? = null, decksEnabled: Boolean = true,
    change: (AssumeReveals) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Game settings", style = MaterialTheme.typography.titleSmall)
            AssumeRevealSwitches(game, accountId, change, note = false)
            if (decks != null && game.podId == null) DeckSettingsGroup(game, accountId, decks, decksEnabled)
            if (game.inviter.accountId != accountId) HostOnlyNote(game)
        }
    }
}

/** LOB-135: what Table actions → Game settings shows ([game]'s current values, as [accountId] sees them) and changes. */
class GameSettingsAccess(val game: GameSummary, val accountId: String, val change: (AssumeReveals) -> Unit)

/** LOB-135: Table actions → Game settings: the same switches in the game. */
@Composable
fun GameSettingsPanel(game: GameSummary, accountId: String, change: (AssumeReveals) -> Unit, onDismiss: () -> Unit) {
    TableMenuPanel(onDismiss, width = 400.dp, title = "Game settings") {
        AssumeRevealSwitches(game, accountId, change)
    }
}
