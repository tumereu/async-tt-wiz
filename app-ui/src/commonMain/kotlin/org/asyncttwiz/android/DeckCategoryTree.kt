package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** LOB-244: one row of Manage decks' deck tree: a category (folded open or shut) or a deck, at its depth. */
sealed interface DeckTreeRow {
    val depth: Int
    /** [decks]: the decks in it and its subcategories. */
    data class Category(val category: DeckCategory, override val depth: Int, val decks: Int, val expanded: Boolean) : DeckTreeRow
    data class Deck(val deck: DeckSummary, override val depth: Int) : DeckTreeRow
}

/**
 * LOB-244: [decks] under [categories] as Manage decks lists them: each category (siblings by name) followed, while
 * [expanded], by its subcategories and then its decks; decks without a category (or in one that isn't listed) come after
 * the categories, in their own order.
 */
fun deckTreeRows(decks: List<DeckSummary>, categories: List<DeckCategory>, expanded: Set<String>): List<DeckTreeRow> {
    val ids = categories.map { it.id }.toSet()
    val byCategory = decks.groupBy { it.category?.takeIf { category -> category in ids } }
    val children = categories.groupBy { it.parent?.takeIf { parent -> parent in ids } }
    val rows = mutableListOf<DeckTreeRow>()
    val seen = mutableSetOf<String>()
    fun count(id: String): Int = DeckCategories.within(categories, listOf(id)).sumOf { byCategory[it]?.size ?: 0 }
    fun visit(parent: String?, depth: Int) {
        for (category in children[parent].orEmpty().sortedWith(compareBy({ it.name.lowercase() }, { it.id }))) {
            if (!seen.add(category.id)) continue
            val open = category.id in expanded
            rows.add(DeckTreeRow.Category(category, depth, count(category.id), open))
            if (open) {
                visit(category.id, depth + 1)
                byCategory[category.id].orEmpty().forEach { rows.add(DeckTreeRow.Deck(it, depth + 1)) }
            }
        }
    }
    visit(null, 0)
    byCategory[null].orEmpty().forEach { rows.add(DeckTreeRow.Deck(it, 0)) }
    return rows
}

/** LOB-244: a category row's deck count. */
fun categoryDeckCount(decks: Int) = if (decks == 1) "1 deck" else "$decks decks"

/** LOB-244: a category row of the deck tree: a tap folds it open or shut; [menu] offers its changes (LOB-245). */
@Composable
fun DeckCategoryRow(row: DeckTreeRow.Category, toggle: () -> Unit, menu: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (row.expanded) "Fold shut" else "Fold open", onClick = toggle)
        .semantics { stateDescription = if (row.expanded) "Open" else "Shut" }.testTag("category-${row.category.name}")
        .padding(start = (row.depth * 20).dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(screenIcon("chevron"), null, Modifier.size(24.dp).rotate(if (row.expanded) 90f else 0f))
        Spacer(Modifier.width(4.dp))
        // LOB-245 (owner playtest 2026-11-19): the name and count take the room left of the ⋮, which sits at the row's right
        // edge at every depth, like the deck rows' ends
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(row.category.name, Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(categoryDeckCount(row.decks), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        menu?.invoke()
    }
}

/** LOB-245: a category row's ⋮ menu: New subcategory, Rename, Move to… and Delete. */
@Composable
fun DeckCategoryMenu(category: DeckCategory, open: (CategoryDialog) -> Unit, enabled: Boolean = true) {
    var shown by remember { mutableStateOf(false) }
    Box {
        IconButton({ shown = true }, Modifier.size(48.dp).semantics { contentDescription = "${category.name} options" }, enabled) {
            Icon(screenIcon("more"), null)
        }
        DropdownMenu(shown, { shown = false }) {
            DropdownMenuItem({ Text("New subcategory") }, { shown = false; open(CategoryDialog.Create(category.id)) },
                leadingIcon = { Icon(screenIcon("add"), null) })
            DropdownMenuItem({ Text("Rename") }, { shown = false; open(CategoryDialog.Rename(category)) },
                leadingIcon = { Icon(screenIcon("rename"), null) })
            DropdownMenuItem({ Text("Move to…") }, { shown = false; open(CategoryDialog.Move(category)) },
                leadingIcon = { Icon(screenIcon("switch"), null) })
            DropdownMenuItem({ Text("Delete") }, { shown = false; open(CategoryDialog.Delete(category)) },
                leadingIcon = { Icon(screenIcon("delete"), null, tint = MaterialTheme.colorScheme.error) })
        }
    }
}

/** LOB-245, LOB-246: the open category dialog of Manage decks. */
@Composable
fun DeckCategoryDialogs(controller: DeckLibraryController) {
    val state = controller.state
    val dialog = state.categoryDialog ?: return
    val busy = if (state.categoryBusy) "Saving…" else null
    when (dialog) {
        is CategoryDialog.Create, is CategoryDialog.Rename -> {
            val creating = dialog is CategoryDialog.Create
            val parent = (dialog as? CategoryDialog.Create)?.parent
            ScreenDialog(controller::dismissCategory, busyMessage = busy, error = state.error, dismissError = controller::dismissError,
                title = if (!creating) "Rename category" else if (parent != null) "New subcategory" else "New category", actions = {
                    DialogCancelButton("Cancel", controller::dismissCategory, enabled = !state.categoryBusy)
                    DialogMainButton(if (creating) "Create" else "Rename", controller::confirmCategory,
                        enabled = !state.categoryBusy && DeckCategories.name(state.categoryName.trim()), icon = if (creating) "add" else "ready")
                }) {
                if (parent != null) Text("In ${DeckCategories.pathName(state.categories, parent)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScreenTextField(state.categoryName, controller::categoryName, Modifier.fillMaxWidth().testTag("category-name")
                    .semantics { contentDescription = "Category name" }, enabled = !state.categoryBusy, placeholder = "Category name")
            }
        }
        is CategoryDialog.Move -> ScreenDialog(controller::dismissCategory, busyMessage = busy, error = state.error,
            dismissError = controller::dismissError, title = "Move ${dialog.category.name}", actions = {
                DialogCancelButton("Cancel", controller::dismissCategory, enabled = !state.categoryBusy)
            }) {
            CategoryTreeChoices(state.categories, "Top level", dialog.category.parent, !state.categoryBusy, nested = true,
                excluded = DeckCategories.within(state.categories, listOf(dialog.category.id)), choose = controller::moveCategory)
        }
        // LOB-262: a deck moves to a category or the top level from its row, as a category does (LOB-245)
        is CategoryDialog.MoveDeck -> ScreenDialog(controller::dismissCategory, busyMessage = busy, error = state.error,
            dismissError = controller::dismissError, title = "Move ${dialog.deck.name}", actions = {
                DialogCancelButton("Cancel", controller::dismissCategory, enabled = !state.categoryBusy)
            }) {
            // Owner 2026-11-21 (E10): each place a row a tap moves the deck to at once; its own place greyed, "here"
            CategoryTreeChoices(state.categories, "Top level", dialog.deck.category?.takeIf { id -> state.categories.any { it.id == id } },
                !state.categoryBusy, nested = true, places = true, choose = controller::moveDeck)
        }
        is CategoryDialog.Delete -> ScreenDialog(controller::dismissCategory, busyMessage = busy, error = state.error,
            dismissError = controller::dismissError, title = "Delete category?", actions = {
                DialogCancelButton("Keep category", controller::dismissCategory, enabled = !state.categoryBusy)
                DialogMainButton("Delete category", controller::confirmCategory, enabled = !state.categoryBusy, danger = true)
            }) {
            Text(dialog.category.name, fontWeight = FontWeight.Bold)
            Text(deleteCategoryNote(state.categories, dialog.category))
        }
    }
}

/** LOB-246: what deleting [category] does with what it holds. */
fun deleteCategoryNote(categories: List<DeckCategory>, category: DeckCategory): String =
    "Its decks and subcategories move up to " + (category.parent?.let { "“${DeckCategories.pathName(categories, it)}”" } ?: "the top level") +
        ". No deck is deleted."

/**
 * LOB-245, LOB-258, LOB-262: the choice of a category as a tree, [top] (the top level, or none) first and [current] marked;
 * [nested]: the categories indented under [top], as places to move into. [excluded] categories aren't offered. [places]
 * (LOB-262's Move to…): plain rows a tap chooses, without radio buttons, [current] greyed with "here" and not tappable.
 */
@Composable
fun CategoryTreeChoices(categories: List<DeckCategory>, top: String, current: String?, enabled: Boolean, nested: Boolean = false,
    excluded: Set<String> = emptySet(), places: Boolean = false, choose: (String?) -> Unit) {
    val indent = if (nested) 1 else 0
    @Composable fun row(label: String, depth: Int, here: Boolean, pick: () -> Unit) =
        if (places) CategoryPlaceRow(label, depth, here, enabled, pick) else CategoryChoiceRow(label, depth, here, enabled, pick)
    row(top, 0, current == null) { choose(null) }
    for ((category, depth) in DeckCategories.ordered(categories).filter { it.first.id !in excluded })
        row(category.name, depth + indent, current == category.id) { choose(category.id) }
}

/** LOB-262 (owner 2026-11-21, E10): one place of Move to…'s tree: a row a tap moves to; where it is now ([here]) greyed with
 * "here" at its end, not tappable. */
@Composable
fun CategoryPlaceRow(label: String, depth: Int, here: Boolean, enabled: Boolean, choose: () -> Unit) {
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled && !here, onClickLabel = "Move here", onClick = choose)
        .padding(start = (depth * 20 + 12).dp, end = 12.dp).testTag("move-place-$label")
        .semantics(mergeDescendants = true) { if (here) stateDescription = "here" }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (here) faded else Color.Unspecified)
        if (here) {
            Spacer(Modifier.width(8.dp))
            Text("here", style = MaterialTheme.typography.bodySmall, color = faded)
        }
    }
}

/** LOB-262: a saved deck row's ⋮ menu in Manage decks: Move to… (shown greyed while there is no category to move into). */
@Composable
fun DeckRowMenu(deck: DeckSummary, hasCategories: Boolean, open: (CategoryDialog) -> Unit, enabled: Boolean = true) {
    var shown by remember { mutableStateOf(false) }
    Box {
        IconButton({ shown = true }, Modifier.size(48.dp).semantics { contentDescription = "${deck.name} options" }, enabled) {
            Icon(screenIcon("more"), null)
        }
        DropdownMenu(shown, { shown = false }) {
            DropdownMenuItem({ Text("Move to…") }, { shown = false; open(CategoryDialog.MoveDeck(deck)) },
                leadingIcon = { Icon(screenIcon("switch"), null) }, enabled = hasCategories)
        }
    }
}

/** One choice of a list of categories as a tree (a single choice: [selected] marks the current one). */
@Composable
fun CategoryChoiceRow(label: String, depth: Int, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = choose)
        .padding(start = (depth * 20).dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One check of a list of categories as a tree (several may be chosen). */
@Composable
fun CategoryCheckRow(label: String, depth: Int, checked: Boolean, enabled: Boolean, toggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = toggle)
        .padding(start = (depth * 20).dp).testTag("lend-category-$label"), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** LOB-258: the deck editor's Category row: its path or "None", a tap opening the choice of [categories] as a tree. */
@Composable
fun DeckCategoryField(categories: List<DeckCategory>, chosen: String?, enabled: Boolean, choose: (String?) -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val shown = chosen?.let { DeckCategories.pathName(categories, it) }?.takeIf { it.isNotEmpty() } ?: "None"
    Row((if (compact) modifier.widthIn(max = 160.dp) else modifier.fillMaxWidth()).heightIn(min = 40.dp)
        .clickable(enabled, onClickLabel = "Choose category") { open = true }
        .testTag("deck-category").semantics(mergeDescendants = true) { contentDescription = "Category: $shown" },
        verticalAlignment = Alignment.CenterVertically) {
        // Beside the name (where it shares the title row) a folder stands for the word, to leave the name its room
        if (compact) Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
        Text(shown, if (compact) Modifier.weight(1f, fill = false) else Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(screenIcon("chevron"), null, Modifier.size(20.dp))
    }
    if (open) ScreenDialog({ open = false }, title = "Category", actions = { DialogCancelButton("Cancel", { open = false }) }) {
        CategoryTreeChoices(categories, "None", chosen, true) { choose(it); open = false }
        if (categories.isEmpty()) Text("Make categories on Manage decks' Decks tab with New category.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
