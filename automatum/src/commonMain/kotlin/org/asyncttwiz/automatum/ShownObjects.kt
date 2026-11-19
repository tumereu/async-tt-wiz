package org.asyncttwiz.automatum

import arcanum.api.FaceDefinition
import arcanum.api.Zone
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.status
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameCards

/**
 * MDLPM7 (owner 2026-11-19): the one answer to "what does this engine object look like" on a game's board: for any object
 * or Stack item, the card data it is drawn with (name, type line, rules text and picture) and the table card it points at.
 * A copy is the card its copiable values came from (CR 707.2, 707.10): the engine carries the printed face's identity in the
 * copiable values ([arcanum.api.FaceDefinition.identity], set by [GameCards.identified]), so a picture is found by that
 * identity, never by name: a spell copy shows the copied card, a token copy, a permanent that became a copy (D8), a card
 * copy in exile (a prepare spell's copy included) and an ability whose source has gone show the card they came from. A
 * picture that can't be found once the card data is there is a fault ([missing]: logged and reported).
 *
 * [base] is the play as it started (its card data); [definitions] collects the card data the board uses (made ones added);
 * [pictures] the token pictures the caller keeps across boards ([TokenArt], SYS-072); [madeBy] what made each token.
 */
internal class ShownObjects(
    private val base: PlayState,
    private val state: GameState,
    private val view: arcanum.engine.view.View?,
    val definitions: MutableMap<String, SubmittedDefinition>,
    private val tokenArt: TokenArt,
    private val pictures: MutableMap<ObjectId, SubmittedDefinition>,
    private val madeBy: Map<ObjectId, arcanum.engine.change.CausedBy?>,
) {
    private val byCard: Map<String, PlayCard> = base.board.cards.associateBy { it.id }
    private val byDigest: Map<String, SubmittedDefinition> = base.definitions.associateBy { it.digest }
    private val digests = HashMap<ObjectId, String>()

    /** The card data [obj] is drawn with, by digest (added to [definitions]). */
    fun digestOf(obj: GameObject): String = digests.getOrPut(obj.id) {
        val prepared = byCard[obj.card.value]
        if (prepared != null && !obj.token && !obj.isCopyOfCard) {
            // D8 (owner 2026-11-19): a permanent that became a copy (Clone) is the copied card: its picture, name and text (CR 707.2)
            val copied = view?.get(obj.id)?.copiable?.takeIf { values -> !obj.faceDown && !obj.merged &&
                values.faces.map { it.identity } != obj.definition.faces.map { it.identity } }
                ?: return@getOrPut prepared.definition
            return@getOrPut made(copied.definition, token = false, gained = copied.gained, "copy ${obj.card.value}").digest
        }
        val definition = obj.definition
        val made = AtwTokens.submitted(definition, token = true, gained = obj.statuses.status<arcanum.engine.state.ObjectStatus.GainedInCopying>()?.abilities.orEmpty())
        // A copy of a card (offspring, embalm, myriad, a prepare spell's copy, a named card) is drawn with that card's own picture,
        // by the identity its copiable values carry; a token of no card with its best matching token printing (SYS-072)
        val shown = pictured(made, definition.faces)
            ?: if (definition.faces.any { it.identity != null }) made.also { missing("copy of a card", obj.card.value, made) }
            else if (!obj.token || tokenArt === TokenArt.None) made
            else kept(obj.id, made) { tokenPicture(made, madeBy[obj.id]) }
        shown.also { definitions[it.digest] = it }.digest
    }

    /** Card data made from engine [definition] (a copy's values), drawn with the pictures of the faces its identities name. */
    private fun made(definition: arcanum.api.CardDefinition, token: Boolean, gained: List<arcanum.api.Ability>, what: String): SubmittedDefinition {
        val made = AtwTokens.submitted(definition, token = token, gained = gained)
        val shown = pictured(made, definition.faces)
            // A copy of a token, which no printed face is: the token's best matching printing (SYS-072)
            ?: if (definition.faces.none { it.identity != null } && tokenArt !== TokenArt.None) {
                val asToken = AtwTokens.submitted(definition, token = true, gained = gained)
                tokenPicture(asToken, null).definition.faces.singleOrNull()?.scryfallId?.let { id ->
                    made.definition.copy(faces = made.definition.faces.map { it.copy(scryfallId = id) }).let { SubmittedDefinition(DeckJson.digest(it), it) }
                } ?: made
            } else made.also { if (definition.faces.any { f -> f.identity != null }) missing(what, it.definition.name, it)
                else AutomatumLog.mismatch("", "copy picture", "${it.definition.name} ($what): its values name no printed face") }
        return shown.also { definitions[it.digest] = it }
    }

    /**
     * What Stack item [item] is drawn as: [sourceCard], the table card it points at (a spell's card, an ability's source on the
     * table), else [frozen], card data of its own (a spell copy as the copied card, an ability whose source has gone as that
     * source's last known card); [copy] for a copy of a spell or ability (D7: "Copy of …" and the copy marker).
     */
    data class StackShown(val sourceCard: String?, val frozen: SubmittedDefinition?, val copy: Boolean)

    fun stackItem(item: StackItem, cardIds: Set<String>): StackShown {
        val copy = item.copyOf != null || item.kind is StackItemKind.SpellCopy
        val id = item.id
        if (id is ObjectId.Card) return StackShown(id.card.value, null, copy)
        val kind = item.kind
        if (kind is StackItemKind.SpellCopy) return StackShown(null, spellCopy(item, kind), true)
        val source = (item.source as? ObjectId.Card)?.card?.value?.takeIf { it in cardIds }
        if (source != null) return StackShown(source, null, copy)
        return StackShown(null, ability(item), copy)
    }

    /**
     * D1, D2: a copy of a spell as the copied card (CR 707.10): the copied spell's own card data while it is there and the copy has no
     * exceptions, else card data made from the copy's copiable values (the identity of the copied face among them), never an
     * "Ability" frame; a permanent spell's copy too, which has no ability of its own.
     */
    private fun spellCopy(item: StackItem, kind: StackItemKind.SpellCopy): SubmittedDefinition {
        val original = (kind.copied as? ObjectId.Card)?.let { state.obj(it) }
        if (original != null && !original.faceDown && kind.except.isEmpty()) return definitions.getValue(digestOf(original))
        val values = view?.get(item.id)?.copiable ?: kind.values
            ?: arcanum.engine.Inspect.lastKnown(state, kind.copied)?.let { seen -> seen.copiable ?: seen.characteristics?.let { c ->
                arcanum.engine.state.CopiableValues.of(FaceDefinition(c.name ?: "", c.manaCost, c.colors, c.supertypes, c.types, c.subtypes,
                    identity = c.identity)) } }
        if (values == null) {
            AutomatumLog.mismatch("", "spell copy", "Copy ${item.id} of ${kind.copied}: no copiable values found")
            return AtwTokens.ability(null, item.ability, mapOf("entry" to "${item.id}", "copied" to "${kind.copied}")).also { definitions[it.digest] = it }
        }
        return made(values.definition, token = false, gained = values.gained, "spell copy ${item.id}")
    }

    /**
     * STK-022, D4: an ability whose source isn't on the table, as an entry of its own: named after its source as last known (or the
     * rule giving it), its own text, and the picture of the face its source's last known values came from (a token's kept picture).
     */
    private fun ability(item: StackItem): SubmittedDefinition {
        val lastKnown = item.snapshots.firstOrNull { it.id == item.source }?.characteristics
            ?: item.source?.let { arcanum.engine.Inspect.lastKnown(state, it) }?.characteristics
        val now = (item.source as? ObjectId.Card)?.let { source -> state.objects.values.firstOrNull { it.card == source.card } }
        val name = lastKnown?.name
            ?: StackAbilityText.ruleName(item)
            // Its source as last known elsewhere (a token gone from the game, CR 111.7, seen as it left), or the card it is now
            ?: now?.definition?.faces?.firstOrNull()?.name
            // A token as it was last drawn, once the log has forgotten it (D4)
            ?: item.source?.let { pictures[it] }?.definition?.faces?.firstOrNull()?.name
        // Prod faults K87CEQ, VHDZ2A, CV29AN: what the entry is and where its source went, when no name is found
        val plain = AtwTokens.ability(name, item.ability, mapOf("entry" to "${item.id}", "kind" to "${item.kind}", "origin" to "${item.origin}",
            "source" to "${item.source}", "snapshots" to item.snapshots.joinToString { "${it.id}" }))
        val identity = lastKnown?.identity ?: now?.definition?.faces?.getOrNull(now.face)?.identity
        val face = plain.definition.faces.single()
        val picture = picture(identity)?.takeIf { (from) -> from.scryfallId != null || from.imageUri != null }?.let { (from, index) -> face.picturedFrom(from, index, 0) }
            ?: item.source?.let { pictures[it] }?.definition?.faces?.firstOrNull()?.takeIf { it.scryfallId != null || it.imageUri != null }
                ?.let { kept -> face.copy(scryfallId = kept.scryfallId, imageUri = kept.imageUri) }
        val shown = picture?.let { f -> plain.definition.copy(faces = listOf(f)).let { SubmittedDefinition(DeckJson.digest(it), it) } }
            // A token's picture is the card records' (their lookup reports a token without one, [TokenArt]); a card's is a fault
            ?: plain.also { if (identity != null && picture(identity) == null) missing("ability of a source that has gone", "${item.source}", it) }
        return shown.also { definitions[it.digest] = it }
    }

    /** The printed face (and its index on its card data) [identity] names: the game's card data by digest, or a card of the card
     * records by its printing ([GameCards.printing], a named card, CR 707.12); null when it names none known. */
    private fun picture(identity: String?): Pair<DefinitionFace, Int>? {
        GameCards.printingOf(identity)?.let { (printing, index) -> return DefinitionFace("", "", scryfallId = printing) to index }
        val (digest, index) = GameCards.of(identity) ?: return null
        val card = byDigest[digest]?.definition ?: return null
        val face = card.faces.getOrNull(index) ?: return null
        return face to index
    }

    /** [made] (card data made from engine faces [faces]) with each face's picture from the face its identity names; null when
     * none has one. A card whose parts share one face (a split card, an adventurer) is that face. */
    private fun pictured(made: SubmittedDefinition, faces: List<FaceDefinition>): SubmittedDefinition? {
        var found = false
        val shown = made.definition.faces.mapIndexed { i, face ->
            val identity = (if (made.definition.faces.size == faces.size) faces.getOrNull(i) else null)?.identity
                ?: faces.firstNotNullOfOrNull { it.identity }
            picture(identity)?.let { (from, index) -> found = true; face.picturedFrom(from, index, i) } ?: face
        }
        // A printed face without a picture of its own (card data without one) is drawn as that card is: nothing missing
        if (shown == made.definition.faces) return made.takeIf { found }
        val card = made.definition.copy(faces = shown)
        return SubmittedDefinition(DeckJson.digest(card), card)
    }

    /**
     * SYS-072 (owner playtest 2026-11-19, N25): the card data kept in [pictures] for token [id] when it is that of the token
     * now there ([made], but for its picture), else [find]'s, kept. A token's id comes from the timestamp it was created at
     * ([arcanum.engine.state.TokenIds]), so after an undo, a rewind or on another branch another token made at the same
     * point has the same id: it must not be drawn as the one kept before (another token's name, text and picture).
     */
    private fun kept(id: ObjectId, made: SubmittedDefinition, find: () -> SubmittedDefinition): SubmittedDefinition {
        val kept = pictures[id]
        if (kept != null && kept.definition.copy(faces = kept.definition.faces.map { it.copy(scryfallId = null, imageUri = null) }) == made.definition) return kept
        if (kept != null) AutomatumLog.mismatch("", "token picture", "Token ${(id as? ObjectId.Card)?.card?.value ?: id} is now " +
            "${made.definition.name}, kept as ${kept.definition.name}: looked up again")
        return find().also { pictures[id] = it }
    }

    /** [token] drawn with the picture [tokenArt] finds for it and the card whose effect made it ([by]); as it is when
     * none is found (the plain frame; the card records' lookup reports that, [TokenArt]). */
    private fun tokenPicture(token: SubmittedDefinition, by: arcanum.engine.change.CausedBy?): SubmittedDefinition {
        if (tokenArt === TokenArt.None) return token
        val face = token.definition.faces.singleOrNull() ?: return token
        val digest = (by?.source as? ObjectId.Card)?.let { byCard[it.card.value]?.definition }
        val source = byDigest[digest]?.definition
            ?: by?.name?.let { name -> base.definitions.firstOrNull { !it.definition.token && it.definition.name == name }?.definition }
        val id = try { tokenArt.find(TokenArtQuery(face, source?.name ?: by?.name, source?.printingId)) }
            catch (problem: Exception) { if (problem is kotlin.coroutines.cancellation.CancellationException) throw problem; null }
            ?: return token
        val card = token.definition.copy(faces = listOf(face.copy(scryfallId = id)))
        return SubmittedDefinition(DeckJson.digest(card), card)
    }

    /** The owner's rule (2026-11-19): a picture missing once the card data is there is a fault, logged and reported once per kind
     * of object per run, naming what it was. */
    private fun missing(what: String, id: String, shown: SubmittedDefinition) {
        Faults.report("ShownObjects", "no picture for a $what", mapOf("object" to id, "name" to shown.definition.name,
            "identities" to "${shown.definition.faces.map { it.faceId }}"))
    }

    companion object {
        /** A face of card data with the picture of [from] (face [fromIndex] of its card), as face [index] of its own card: the
         * provider's side is kept so the right side's picture shows whatever its index here. */
        internal fun DefinitionFace.picturedFrom(from: DefinitionFace, fromIndex: Int, index: Int): DefinitionFace {
            val source = from.sourceData?.let { runCatching { DeckJson.format.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            val sided = if (fromIndex == index || source?.get("side") != null) from.sourceData
                else JsonObject((source ?: JsonObject(emptyMap())) + ("side" to JsonPrimitive("${'a' + fromIndex}"))).toString()
            return copy(scryfallId = from.scryfallId, imageUri = from.imageUri, sourceData = sided,
                printedName = from.printedName.takeIf { from.name == name })
        }
    }
}

/** A copy of a card (CR 707.12), not a card ([arcanum.engine.state.ObjectStatus.CopyOfCard]). */
internal val GameObject.isCopyOfCard: Boolean get() = statuses.status<arcanum.engine.state.ObjectStatus.CopyOfCard>() != null

/**
 * MDLPM7, D7, D10 (owner 2026-11-19): how a Stack item is named in texts (history, the logs): a spell by its card, a copy of a
 * spell "copy of Storm Spark", an ability by its source and kind ("Ember Herald's ability", a keyword's "Shattered Reason's
 * cycling trigger"), a copied ability "copy of Bre of Clan Stoutarm's triggered ability"; [nameOf] names an object as the reader
 * may see it (null: hidden or unknown). Lower case: the caller capitalises a sentence's start.
 */
object ShownNames {
    fun of(state: GameState, item: StackItem, nameOf: (ObjectId?) -> String?): String? {
        val copy = item.copyOf != null || item.kind is StackItemKind.SpellCopy
        return when (val kind = item.kind) {
            StackItemKind.Spell -> nameOf(item.id)
            is StackItemKind.SpellCopy -> (nameOf(kind.copied) ?: arcanum.engine.Inspect.lastKnown(state, kind.copied)?.characteristics?.name)
                ?.let { "copy of $it" }
            else -> {
                val source = item.snapshots.firstOrNull { it.id == item.source }?.characteristics?.name ?: nameOf(item.source)
                    ?: StackAbilityText.ruleName(item) ?: return null
                val label = StackAbilityText.kind(item) ?: "Ability"
                if (copy) "copy of $source's ${label.replaceFirstChar { it.lowercase() }}"
                else "$source's ${label.takeIf { it != "Activated ability" && it != "Triggered ability" }?.replaceFirstChar { it.lowercase() } ?: "ability"}"
            }
        }
    }

    /** D10: what the logs say of Stack item [item] ([of] with its id), "copy:221 copy of Bre of Clan Stoutarm's triggered ability
     * (copies stack:trigger:219)" telling copies and originals apart. */
    fun logged(state: GameState, item: StackItem, nameOf: (ObjectId?) -> String?): String =
        "${item.id} ${of(state, item, nameOf) ?: "(unnamed ${item.kind})"}" + (item.copyOf?.let { " (copies $it)" } ?: "")
}
