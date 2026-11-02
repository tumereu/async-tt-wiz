package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.api.Subtype
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.view.SubtypeKind
import arcanum.engine.view.Subtypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.storage.AndroidCatalogStorage
import org.asyncttwiz.storage.CardDefinitions
import org.asyncttwiz.storage.CatalogCardSearch

/**
 * Card names for "choose a card name" (CR 201.3, final cast review #1): suggestions for what the player typed. Never
 * the game's own cards: the game's card data holds both decks, so listing from it would show the other player's hidden
 * cards (I-D27). Blocking: called off the main thread.
 */
internal fun interface CardNameSuggestions {
    fun names(text: String): List<String>
}

/** The device's card catalog: names containing [text], by name. Nothing while the catalog isn't installed. */
internal class CatalogCardNames(private val context: Context) : CardNameSuggestions {
    override fun names(text: String): List<String> {
        val query = "\"" + text.replace("\"", "") + "\""
        val page = AndroidCatalogStorage.repository(context).searchCards(query, limit = SUGGESTIONS, budget = 20_000) as? CatalogCardSearch.Page
        return page?.cards?.map { it.name }.orEmpty()
    }
}

/** Where the panel's card-name field takes its suggestions from; null: the device's card catalog. */
internal val LocalCardNameSuggestions = staticCompositionLocalOf<CardNameSuggestions?> { null }

private const val SUGGESTIONS = 12

/**
 * "Choose a card name": any name may be typed (the engine takes any name); the device's card catalog suggests names as the
 * player types, and a tap on one fills the field. OK names it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardNameSection(cast: FlowQuestions, decision: Decision.ChooseCardName) {
    val context = LocalContext.current
    val source = LocalCardNameSuggestions.current ?: remember(context) { CatalogCardNames(context.applicationContext) }
    var text by remember(decision.id) { mutableStateOf("") }
    var suggestions by remember(decision.id) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(decision.id, text) {
        val typed = text.trim()
        if (typed.length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        suggestions = withContext(Dispatchers.IO) { runCatching { source.names(typed) }.getOrDefault(emptyList()) }
    }
    Section("Name a card") {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Card name", fontSize = 12.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
        val shown = suggestions.filter { !it.equals(text.trim(), ignoreCase = true) }
        if (shown.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (name in shown) SuggestionChip({ text = name }, label = { Text(name, fontSize = 12.sp) })
        }
        Button({ cast.answer(Answer.CardName(text.trim())) }, enabled = text.isNotBlank()) { Text("OK") }
    }
}

/** Every creature type of the pinned rules (CR 205.3m), by name. */
internal val creatureTypes: List<Subtype> by lazy {
    CardDefinitions.ruleSubtypes.map(::Subtype).filter { Subtypes.kindOf(it) == SubtypeKind.Creature }.sortedBy { it.name }
}

/**
 * "Choose a creature type" with no given list (CR 205.3m): every creature type, narrowed by what the player types; a tap
 * chooses one. Never only the types in the game, which would hint at the other player's hidden cards (I-D27).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreatureTypeSection(cast: FlowQuestions, decision: Decision.ChooseSubtype) {
    var filter by remember(decision.id) { mutableStateOf("") }
    Section("Choose a creature type") {
        OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Find a type", fontSize = 12.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
        val shown = creatureTypes.filter { it.name.contains(filter.trim(), ignoreCase = true) }
        FlowRow(Modifier.heightIn(max = 168.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (type in shown) SuggestionChip({ cast.answer(Answer.SubtypeChoice(type)) }, label = { Text(type.name, fontSize = 12.sp) })
        }
        if (shown.isEmpty()) Text("No creature type has that in its name.", fontSize = 11.sp)
    }
}
