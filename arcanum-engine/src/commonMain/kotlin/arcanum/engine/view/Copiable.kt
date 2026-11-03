package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.KeywordExpansions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.PrintedNumber
import arcanum.api.Zone
import arcanum.engine.query.Board
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind

/**
 * Layer 1 (CR 613.2): an object's copiable values. Start from what is printed (the face that is up; both halves of a
 * split card outside the Stack, CR 709.4), apply copy effects in timestamp order with their exceptions (layer 1a,
 * CR 707.2, 707.9), then the face-down values (layer 1b, CR 708.2a).
 */
internal class Copiable(private val state: GameState, val reads: ViewReads = ViewReads()) {
    private val memo = mutableMapOf<ObjectId, List<IndexedFace>?>()

    /** The faces whose characteristics an object has, with their face index (for ability origins). Null: unknown. */
    fun faces(id: ObjectId, visiting: Set<ObjectId> = emptySet()): List<IndexedFace>? {
        memo[id]?.let { return it }
        if (id in visiting) return null
        val result = compute(id, visiting + id)
        memo[id] = result
        return result
    }

    private fun compute(id: ObjectId, visiting: Set<ObjectId>): List<IndexedFace>? {
        state.stackItems[id]?.kind?.let { kind ->
            // CR 707.10: a copy of a spell copies the original's copiable values
            if (kind is StackItemKind.SpellCopy) {
                return (faces(kind.copied, visiting)
                    ?: state.stackItems[id]?.snapshots?.firstOrNull { it.id == kind.copied }?.characteristics?.let { listOf(IndexedFace(0, it.asFace())) })
                    // spm: its own exceptions (CR 707.9b)
                    ?.map { if (kind.except.isEmpty()) it else it.copy(face = exceptions(it.face, kind.except)) }
            }
        }
        val o = state.obj(id) ?: return null
        // CR 613.2b, 708.2a: face down, its copiable values are the face-down values whatever it is, so a face-down
        // permanent the caller can't see (D60) is a 2/2 too
        if (o.faceDown) return listOf(IndexedFace(0, faceDown(o)))
        val definition = o.definition ?: return null
        var faces: List<IndexedFace> = when {
            // CR 709.4: a split card outside the Stack has both halves' characteristics; a fused split spell too (709.4d)
            definition.layout == Layout.Split && (o.zone != Zone.Stack || o.facts.fused) -> definition.faces.mapIndexed { i, f -> IndexedFace(i, f) }
            else -> listOfNotNull(definition.faces.getOrNull(o.face)?.let { IndexedFace(o.face, it) })
        }
        // CR 718.3: cast prototyped, on the Stack and the battlefield its mana cost, colour, power and toughness are its prototype's
        if (o.facts.alternative?.keyword == arcanum.api.Keyword.Prototype.Kind && (o.zone == Zone.Stack || o.zone == Zone.Battlefield))
            faces = faces.map { it.copy(face = prototyped(it.face)) }
        // CR 613.2a, 707.2: copy effects in timestamp order
        copyEffectsOn(id).forEach { (effect, copy) ->
            val copied = effect.copied?.let { listOf(IndexedFace(0, it)) } ?: copiedFaces(effect, copy, visiting)
            if (copied != null) faces = copied.map { it.copy(face = exceptions(it.face, copy.except)) }
        }
        // CR 730.2a, 702.140e: a merged permanent has the characteristics of its topmost component and the abilities of all
        if (o.merged) faces = merged(o, faces)
        // CR 702.103b: a bestowed spell or permanent is an Aura enchantment with enchant creature
        if (o.bestowed) faces = faces.map { it.copy(face = bestowed(it.face)) }
        // CR 709.5: a Room's locked halves have no name, mana cost or rules text
        faces = arcanum.engine.room.Rooms.lockedHalves(o, faces)
        return faces
    }

    /** CR 730.2a, 702.140e: the topmost component's faces, then the other components' abilities ([IndexedFace.abilitiesOnly]). */
    private fun merged(o: GameObject, own: List<IndexedFace>): List<IndexedFace> {
        fun part(p: arcanum.engine.state.MergedPart): IndexedFace? =
            p.definition?.faces?.getOrNull(p.face)?.let { IndexedFace(p.face, it, card = p.id.card) }
        val top = o.mergedAbove.firstOrNull()?.let { listOfNotNull(part(it)) } ?: own
        val others = (o.mergedAbove.drop(1) + o.mergedBelow).mapNotNull(::part) +
            (if (o.mergedAbove.isEmpty()) emptyList() else own)
        return top + others.map { it.copy(abilitiesOnly = true) }
    }

    private fun copyEffectsOn(id: ObjectId): List<Pair<EffectObject, Mechanic.CopyOf>> =
        state.effects.values.filter { it.locked?.contains(id) == true }.sortedBy { it.timestamp }
            .flatMap { e -> e.mechanics.filterIsInstance<Mechanic.CopyOf>().map { e to it } }

    private fun copiedFaces(effect: EffectObject, copy: Mechanic.CopyOf, visiting: Set<ObjectId>): List<IndexedFace>? {
        val evaluator = Evaluator(PrintedBoard(state, this), Context(you = effect.controller, self = effect.source, names = effect.names))
        val original = evaluator.objects(copy.objects).firstOrNull() ?: return null
        return faces(original, visiting)
    }

    companion object {
        /** CR 708.2a: a 2/2 face-down creature with no text, no name, no subtypes and no mana cost. */
        val FACE_DOWN = FaceDefinition(
            name = "", types = listOf(CardType.Creature), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2),
        )

        /** CR 718.3b: [face] with its prototype's mana cost, power and toughness, and the colour of that mana cost (CR 202.2). */
        fun prototyped(face: FaceDefinition): FaceDefinition {
            val p = face.abilities.filterIsInstance<arcanum.api.Keyword.Prototype>().firstOrNull() ?: return face
            val cost = p.cost.parts.filterIsInstance<arcanum.api.CostPart.Mana>().firstOrNull()?.cost ?: return face
            return face.copy(manaCost = cost, colorIndicator = null, power = PrintedNumber.Fixed(p.power), toughness = PrintedNumber.Fixed(p.toughness))
        }

        /** CR 702.168a, 701.58a: a disguised or cloaked face-down object is a 2/2 creature with ward {2}. */
        val FACE_DOWN_WARD = FACE_DOWN.copy(abilities = listOf(arcanum.api.Keyword.Ward(arcanum.api.Cost.mana(arcanum.api.ManaCost.parse("{2}")!!))))

        /** The face-down values of [o] (CR 708.2a): with ward {2} when it was cast disguised or cloaked. */
        fun faceDown(o: arcanum.engine.state.GameObject): FaceDefinition =
            if (arcanum.api.ObjectMark.Cloaked in o.marks || o.facts.alternative?.keyword == arcanum.api.Keyword.Disguise.Kind) FACE_DOWN_WARD
            else FACE_DOWN

        /** CR 702.103b: "becomes an Aura enchantment and gains enchant creature" (no longer a creature, nor of a creature
         * type). */
        fun bestowed(face: FaceDefinition): FaceDefinition = face.copy(
            types = listOf(CardType.Enchantment),
            subtypes = face.subtypes.filter { Subtypes.kindOf(it) != SubtypeKind.Creature && it.name != "Aura" } + arcanum.api.Subtype("Aura"),
            abilities = face.abilities + Keyword.Enchant(arcanum.api.TargetKind.Objects(arcanum.api.ObjectFilter.Type(CardType.Creature))),
        )

        /** CR 707.9: an exception becomes part of the copiable values. */
        fun exceptions(face: FaceDefinition, except: List<CopyException>): FaceDefinition = except.fold(face) { f, e ->
            when (e) {
                is CopyException.Name -> f.copy(name = e.name)
                is CopyException.Types -> f.copy(
                    types = (f.types + e.add).distinct(),
                    subtypes = (if (e.replaceSubtypes) f.subtypes.filter { Subtypes.kindOf(it) != SubtypeKind.Creature } else f.subtypes) +
                        e.addSubtypes.mapNotNull { (it as? arcanum.api.SubtypeRef.Literal)?.subtype },
                    supertypes = ((f.supertypes + e.addSupertypes).distinct() - e.removeSupertypes.toSet()),
                )
                is CopyException.Colors -> f.copy(
                    colorIndicator = (if (e.add) colorsOf(f) else emptyList<Color>()) +
                        e.colors.mapNotNull { (it as? arcanum.api.ColorRef.Literal)?.color },
                )
                is CopyException.PowerToughness -> f.copy(power = PrintedNumber.Fixed(e.power), toughness = PrintedNumber.Fixed(e.toughness))
                is CopyException.AddAbilities -> f.copy(abilities = f.abilities + e.abilities)
                is CopyException.ManaCostOf -> f.copy(manaCost = e.cost)
                // gtc chunk 12: replaced by the ability itself as the copy effect is created (CR 707.9b)
                CopyException.ThisAbility -> f
            }
        }

        /** CR 202.2, 204: colours from the mana cost and colour indicator. */
        fun colorsOf(face: FaceDefinition): List<Color> {
            val fromCost = face.manaCost?.symbols.orEmpty().flatMap { Evaluator.symbolColors(it) }
            return Color.all.filter { it in fromCost || it in face.colorIndicator.orEmpty() }
        }

        /** An object's characteristics before layers 2–7, from its copiable faces. */
        fun initial(o: GameObject?, faces: List<IndexedFace>, timestamp: Long?): Characteristics {
            val merged = faces.filterNot { it.abilitiesOnly }.map { it.face }
            val symbols = merged.flatMap { it.manaCost?.symbols.orEmpty() }
            val onBattlefield = o?.zone == Zone.Battlefield
            return Characteristics(
                name = merged.map { it.name }.filter { it.isNotEmpty() }.joinToString(" // ").ifEmpty { null },
                manaCost = if (merged.all { it.manaCost == null }) null else ManaCost(symbols),
                colors = Color.all.filter { c -> merged.any { c in colorsOf(it) } },
                supertypes = merged.flatMap { it.supertypes }.distinct(),
                types = merged.flatMap { it.types }.distinct(),
                subtypes = merged.flatMap { it.subtypes }.distinct(),
                power = merged.firstNotNullOfOrNull { it.power }?.let(::number),
                toughness = merged.firstNotNullOfOrNull { it.toughness }?.let(::number),
                // CR 306.5c, 310.4c: on the battlefield, loyalty and defense are the counters
                loyalty = if (onBattlefield && merged.any { it.loyalty != null }) o.counters(CounterKind.Loyalty)
                    else merged.firstNotNullOfOrNull { it.loyalty }?.let(::number),
                defense = if (onBattlefield && merged.any { it.defense != null }) o.counters(CounterKind.Defense)
                    else merged.firstNotNullOfOrNull { it.defense }?.let(::number),
                abilities = expand(faces.flatMap { f ->
                    f.face.abilities.mapIndexed { i, a -> AbilityInstance(a, AbilityOrigin.Printed(f.index, i, f.card), timestamp) }
                }),
            )
        }

        /** A printed number before characteristic-defining abilities (CR 208.2a): `*` counts as its addend, X as 0. */
        fun number(printed: PrintedNumber): Int = when (printed) {
            is PrintedNumber.Fixed -> printed.value
            is PrintedNumber.Star -> printed.plus
            PrintedNumber.X -> 0
        }

        /**
         * Each ability with its keyword's expansion after it (D18, D38: keywords expand while the view is derived) and
         * the rules of its label ([KeywordExpansions.withLabelRules]). A keyword without an expansion gets abilities that
         * stop with a manual step at its natural moment ([KeywordMeanings]).
         */
        fun expand(instances: List<AbilityInstance>, depth: Int = 0): List<AbilityInstance> = instances.flatMapIndexed { i, inst ->
            val ability = KeywordExpansions.withLabelRules(inst.ability)
            val head = if (ability == inst.ability) inst else inst.copy(ability = ability)
            if (ability is Keyword && depth < 3) {
                // CR 702.165a: the abilities printed below it (backup)
                val below = if (ability.kind in KeywordMeanings.faceDependent)
                    instances.drop(i + 1).filter { it.origin is AbilityOrigin.Printed }.map { it.ability } else emptyList()
                val expansion = KeywordMeanings.expand(ability, below)
                    .mapIndexed { i, a -> AbilityInstance(a, AbilityOrigin.Expansion(inst.origin, i), inst.timestamp) }
                listOf(head) + expand(expansion, depth + 1)
            } else listOf(head)
        }

        private fun Characteristics.asFace() = FaceDefinition(
            name = name ?: "", manaCost = manaCost, colorIndicator = colors, supertypes = supertypes, types = types,
            subtypes = subtypes, power = power?.let { PrintedNumber.Fixed(it) }, toughness = toughness?.let { PrintedNumber.Fixed(it) },
            abilities = abilities.filter { it.origin is AbilityOrigin.Printed }.map { it.ability },
        )
    }
}

/** A face and its index on the card; [card]: of another component of a merged permanent, [abilitiesOnly]: only its abilities
 * count (CR 702.140e). */
internal data class IndexedFace(
    val index: Int, val face: FaceDefinition,
    val card: arcanum.engine.state.CardId? = null, val abilitiesOnly: Boolean = false,
)

/** The board as printed (copiable values only): what copy effects look at to find their original. */
internal class PrintedBoard(override val state: GameState, private val copiable: Copiable) : Board {
    override fun characteristics(id: ObjectId): Characteristics? =
        copiable.faces(id)?.let { Copiable.initial(state.obj(id), it, state.obj(id)?.timestamp) }

    override fun controller(id: ObjectId): PlayerId? = state.obj(id)?.let { it.controller ?: it.owner }
        ?: state.stackItems[id]?.controller

    override fun readingLog() {
        copiable.reads.log = true
    }
}

/** Whether the ability is an instant's or sorcery's (CR 113.6: they work on the Stack). */
internal fun Characteristics.isInstantOrSorcery(): Boolean = isType(CardType.Instant) || isType(CardType.Sorcery)

internal fun Ability.isStatic(): Boolean = this is Ability.Static
