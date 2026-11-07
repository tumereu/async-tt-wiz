package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.api.CardTypeRef
import arcanum.api.FaceDefinition
import arcanum.api.ObjectFilter
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.SubtypeKind
import arcanum.engine.view.Subtypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.storage.AndroidCatalogStorage
import org.asyncttwiz.storage.CardDefinitions
import org.asyncttwiz.storage.CatalogCardSearch

/**
 * Card names for "choose a card name" (CR 201.4, final cast review #1): every card's name that contains what the player
 * typed and passes the effect's restriction ([filterQuery], the card search's words for it, null: none), by name.
 * Blocking: called off the main thread.
 */
internal fun interface CardNameSuggestions {
    fun names(text: String, filterQuery: String?): List<String>
}

/** The device's card catalog: names containing [text], by name. Nothing while the catalog isn't installed. */
internal class CatalogCardNames(private val context: Context) : CardNameSuggestions {
    override fun names(text: String, filterQuery: String?): List<String> {
        val query = "\"" + text.replace("\"", "") + "\"" + (filterQuery?.let { " $it" } ?: "")
        val page = AndroidCatalogStorage.repository(context).searchCards(query, limit = SUGGESTIONS, budget = 20_000) as? CatalogCardSearch.Page
        return page?.cards?.map { it.name }.orEmpty()
    }
}

/** Where the panel's card-name field takes its suggestions from; null: the device's card catalog. */
internal val LocalCardNameSuggestions = staticCompositionLocalOf<CardNameSuggestions?> { null }

private const val SUGGESTIONS = 12

/**
 * The restriction of a card name to choose ("a nonland card name", CR 201.4) on cards themselves: by their printed card
 * types, supertypes and subtypes. A part of it that isn't about these (it can't be judged on a card) lets every card pass.
 */
internal object CardNameFilter {
    /** Whether [face] passes [filter]. */
    fun allows(filter: ObjectFilter, face: FaceDefinition): Boolean = when (filter) {
        ObjectFilter.Any -> true
        is ObjectFilter.And -> filter.all.all { allows(it, face) }
        is ObjectFilter.Or -> filter.any.any { allows(it, face) }
        is ObjectFilter.Not -> judged(filter.filter)?.let { !allows(filter.filter, face) } ?: true
        is ObjectFilter.Type -> (filter.cardType as? CardTypeRef.Literal)?.let { it.cardType in face.types } ?: true
        is ObjectFilter.Super -> filter.supertype in face.supertypes
        is ObjectFilter.Sub -> (filter.subtype as? SubtypeRef.Literal)?.let { it.subtype in face.subtypes } ?: true
        else -> true
    }

    /** [filter] as card search words ("-t:land"), or null when there is nothing to search by. */
    fun query(filter: ObjectFilter): String? = judged(filter)

    private fun judged(filter: ObjectFilter): String? = when (filter) {
        ObjectFilter.Any -> null
        is ObjectFilter.And -> filter.all.mapNotNull(::judged).takeIf { it.isNotEmpty() }?.joinToString(" ")
        is ObjectFilter.Or -> filter.any.map { judged(it) ?: return null }.takeIf { it.isNotEmpty() }?.joinToString(" or ", "(", ")")
        is ObjectFilter.Not -> judged(filter.filter)?.let { "-($it)" }
        is ObjectFilter.Type -> (filter.cardType as? CardTypeRef.Literal)?.let { "t:${it.cardType.toString().lowercase()}" }
        is ObjectFilter.Super -> "t:${filter.supertype.toString().lowercase()}"
        is ObjectFilter.Sub -> (filter.subtype as? SubtypeRef.Literal)?.let { "t:\"${it.subtype.name.lowercase()}\"" }
        else -> null
    }
}

/** Where a suggested name comes from (STK-041): the viewer's own library, the cards seen this game, or every card. */
internal sealed interface NameSource {
    data object Library : NameSource
    data object Seen : NameSource
    data object Catalog : NameSource
}

/** A name to choose with where it comes from. */
internal data class NameSuggestion(val name: String, val source: NameSource)

/**
 * STK-041 (owner gallery review, 2026-11-06): the game's names offered first for "choose a card name", those passing
 * [decision]'s restriction and containing [typed]: the cards of [viewer]'s own library as their device knows them, then
 * the cards [viewer] may see now (public zones, their own hand: [arcanum.engine.Engine.visible]), each name once, by
 * name in each group. Nothing of the other player's hidden cards (I-D27), whoever is deciding.
 */
internal fun gameNames(state: GameState, viewer: String, decision: Decision.ChooseCardName, typed: String): List<NameSuggestion> {
    fun face(id: ObjectId) = state.obj(id)?.takeIf { !it.token }?.definition?.faces?.firstOrNull()
    fun names(ids: Collection<ObjectId>) = ids.mapNotNull(::face)
        .filter { it.name.contains(typed, ignoreCase = true) && CardNameFilter.allows(decision.filter, it) }
        .map { it.name }.distinct().sorted()
    val me = state.players.firstOrNull { it.id.value == viewer } ?: return emptyList()
    val own = me.library.toSet()
    val library = names(own)
    val seen = names(arcanum.engine.Engine.visible(state, me.id).filter { it !in own }) - library.toSet()
    return library.map { NameSuggestion(it, NameSource.Library) } + seen.map { NameSuggestion(it, NameSource.Seen) }
}

/**
 * "Name a card" (STK-041, CR 201.4; owner gallery review, 2026-11-06): a search field autocompleting card names, never
 * free text: the names of your own library first (a card-stack icon), then of cards seen this game (an eye icon), then
 * every card of the device's card records, each passing the effect's restriction ("a nonland card name"). A tap on a name
 * chooses it (written in the field); OK names it.
 */
@Composable
internal fun CardNameSection(cast: FlowQuestions, decision: Decision.ChooseCardName) {
    val context = LocalContext.current
    val viewer = LocalChoiceFrame.current.viewer
    val source = LocalCardNameSuggestions.current ?: remember(context) { CatalogCardNames(context.applicationContext) }
    var text by remember(decision.id) { mutableStateOf("") }
    var chosen by remember(decision.id) { mutableStateOf<String?>(null) }
    var catalog by remember(decision.id) { mutableStateOf(emptyList<String>()) }
    val typed = text.trim()
    val game = remember(decision.id, typed) {
        runCatching { gameNames(cast.engine, viewer, decision, typed) }.getOrDefault(emptyList())
    }
    LaunchedEffect(decision.id, typed) {
        if (typed.length < 2) { catalog = emptyList(); return@LaunchedEffect }
        catalog = withContext(Dispatchers.IO) {
            runCatching { source.names(typed, CardNameFilter.query(decision.filter)) }.getOrDefault(emptyList())
        }
    }
    val known = game.map { it.name.lowercase() }.toSet()
    val suggestions = game + catalog.filter { it.lowercase() !in known }.distinct().map { NameSuggestion(it, NameSource.Catalog) }
    // What is written names a card when it is one of the names offered
    val named = chosen ?: suggestions.firstOrNull { it.name.equals(typed, ignoreCase = true) }?.name
    val focus = LocalFocusManager.current
    Section(choiceWhat(decision), choiceHint(decision)) {
        OutlinedTextField(text, { text = it; chosen = null }, Modifier.fillMaxWidth().semantics { contentDescription = "Card name" },
            singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
            placeholder = { Text("Find a card", fontSize = 12.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
        if (suggestions.isEmpty()) Text(if (typed.length < 2) "Write at least two letters of its name." else "No card's name has that in it.",
            fontSize = 11.sp, color = Color(0xff4b5a5f))
        else Column(Modifier.heightIn(max = 176.dp).verticalScroll(rememberScrollState())) {
            for (suggestion in suggestions.take(40)) NameRow(suggestion, suggestion.name == named) {
                // The choice is made: the keyboard goes, leaving the panel's OK in sight (and no cursor left inside the name)
                chosen = suggestion.name; text = suggestion.name; focus.clearFocus()
            }
        }
        OkRow(decision, named != null) { named?.let { cast.answer(Answer.CardName(it)) } }
    }
}

/** A suggested name: its source's icon (your library: a card stack; seen: an eye), the name; chosen: violet. */
@Composable
private fun NameRow(suggestion: NameSuggestion, chosen: Boolean, choose: () -> Unit) {
    val where = when (suggestion.source) {
        NameSource.Library -> "in your library"
        NameSource.Seen -> "seen this game"
        NameSource.Catalog -> "a card"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp)
        .background(if (chosen) AutomatumViolet.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(6.dp))
        .clickable(onClickLabel = "Name it") { choose() }
        .semantics(mergeDescendants = true) { contentDescription = "${suggestion.name}, $where" + if (chosen) ", chosen" else "" }
        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val icon = when (suggestion.source) {
            NameSource.Library -> Icons.Outlined.Layers
            NameSource.Seen -> Icons.Outlined.Visibility
            NameSource.Catalog -> null
        }
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (chosen) AutomatumViolet else Color(0xff4b5a5f))
        }
        Text(suggestion.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal, color = if (chosen) AutomatumViolet else Color(0xff26343a))
        if (chosen) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = AutomatumViolet)
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
    Section("Choose a creature type", "Find a type and tap it.") {
        OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Find a type", fontSize = 12.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
        val shown = creatureTypes.filter { it.name.contains(filter.trim(), ignoreCase = true) }
        FlowRow(Modifier.heightIn(max = 168.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (type in shown) SuggestionChip({ cast.answer(Answer.SubtypeChoice(type)) }, label = { Text(type.name, fontSize = 12.sp) },
                icon = { Icon(Icons.Outlined.Label, null, Modifier.size(16.dp)) })
        }
        if (shown.isEmpty()) Text("No creature type has that in its name.", fontSize = 11.sp)
    }
}
