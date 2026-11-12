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
import arcanum.engine.state.CopiableValues
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind

/**
 * Layer 1 (CR 613.2): an object's copiable values. Start from what is printed (the face that is up; both halves of a
 * split card outside the Stack, CR 709.4), apply copy effects in timestamp order with their exceptions (layer 1a,
 * CR 707.2, 707.9), then the face-down values (layer 1b, CR 708.2a). The whole values, layout and all faces, are
 * [CopiableValues]; the faces an object has characteristics from now are [faces].
 */
internal class Copiable(private val state: GameState, val reads: ViewReads = ViewReads()) {
    private val memo = mutableMapOf<ObjectId, Worked?>()

    /** An object's whole copiable [values] and, of them, the [faces] whose characteristics it has now, before its doors lock. */
    private class Worked(val values: CopiableValues, val faces: List<IndexedFace>) {
        fun map(change: (FaceDefinition) -> FaceDefinition) =
            Worked(values.copy(faces = values.faces.map(change)), faces.map { if (it.gained) it else it.copy(face = change(it.face)) })

        /** With the exceptions of a copy effect (CR 707.9b). */
        fun except(except: List<CopyException>): Worked {
            if (except.isEmpty()) return this
            val excepted = exceptions(values, except)
            return Worked(excepted, faces.filterNot { it.gained }.map { it.copy(face = exceptions(it.face, onEachFace(values.layout, except))) } + gainedPart(excepted))
        }
    }

    /** The faces whose characteristics an object has, with their face index (for ability origins). Null: unknown. */
    fun faces(id: ObjectId): List<IndexedFace>? {
        val worked = worked(id, emptySet()) ?: return null
        // CR 709.5: a Room's locked halves have no name, mana cost or rules text
        return state.obj(id)?.let { arcanum.engine.room.Rooms.lockedHalves(it, worked.values, worked.faces) } ?: worked.faces
    }

    /** An object's whole copiable values (CR 707.2). Null: unknown. */
    fun values(id: ObjectId): CopiableValues? = worked(id, emptySet())?.values

    /** The values a copy effect fixes as it starts, copying [id] (CR 707.2b). Null: unknown. */
    fun forCopyEffect(id: ObjectId): CopiableValues? = values(id)?.let { copiedByEffect(it, flipped = false) }

    /** The values [copy], a copy effect being created for [you] by [self] with [names], fixes as it starts (CR 707.2b), the object
     * copied found as the effect would find it. Null: it copies nothing known. */
    fun forCopyEffect(copy: Mechanic.CopyOf, you: PlayerId, self: ObjectId?, names: arcanum.engine.state.Bindings): CopiableValues? =
        Evaluator(PrintedBoard(state, this), Context(you = you, self = self, names = names)).objects(copy.objects).firstOrNull()?.let(::forCopyEffect)

    /**
     * The values of a token that's a copy of [id] (CR 707.2, 111.3), or that a copy of a permanent spell becomes (CR 707.10f, g).
     * A double-faced permanent or card gives a double-faced token, each face the copiable values of the same face and the same
     * face up (CR 707.8a), but an object that only copies a double-faced permanent doesn't (the rule's example of Clone), and a
     * meld card or a melded permanent gives its face up (CR 712.9); the token enters unflipped (CR 110.5b) and with neither half
     * unlocked (CR 709.5d). Null: unknown.
     */
    fun forToken(id: ObjectId): CopiableValues? {
        val values = values(id) ?: return null
        val o = state.obj(id)
        val printed = o?.definition?.takeIf { !o.faceDown && !o.merged && doubleFaced(it.layout) && it.faces.size > 1 }
        // A double-faced permanent under a copy effect: each of its faces has what the effect gives (CR 707.8a)
        if (printed != null && values.layout == Layout.Normal && values.faces.size == 1)
            return CopiableValues(printed.layout, printed.faces.map { values.faces.single() }, o.face)
        return when (values.layout) {
            is Layout.Meld -> faceUp(values)
            Layout.Flip, Layout.Split -> values.copy(up = 0)
            else -> values
        }
    }

    private fun worked(id: ObjectId, visiting: Set<ObjectId>): Worked? {
        memo[id]?.let { return it }
        if (id in visiting) return null
        val result = compute(id, visiting + id)
        // Values worked out while a static copy finds what it affects leave that copy out: not kept
        if (evaluating.isEmpty()) memo[id] = result
        return result
    }

    private fun compute(id: ObjectId, visiting: Set<ObjectId>): Worked? {
        state.stackItems[id]?.kind?.let { kind ->
            // CR 707.10: a copy of a spell copies the original's copiable values
            if (kind is StackItemKind.SpellCopy) {
                return (worked(kind.copied, visiting)
                    ?: state.stackItems[id]?.snapshots?.firstOrNull { it.id == kind.copied }?.characteristics?.let { c ->
                        Worked(kind.values ?: CopiableValues.of(c.asFace()), listOf(IndexedFace(0, c.asFace())))
                    })
                    // spm: its own exceptions (CR 707.9b)
                    ?.except(kind.except)
            }
        }
        val o = state.obj(id) ?: return null
        // CR 613.2b, 708.2a: face down, its copiable values are the face-down values whatever it is, so a face-down
        // permanent the caller can't see (D60) is a 2/2 too
        if (o.faceDown) return Worked(CopiableValues.of(faceDown(o)), listOf(IndexedFace(0, faceDown(o))))
        val definition = o.definition ?: return null
        var worked = CopiableValues(definition.layout, definition.faces, o.face, o.gainedInCopying).let { Worked(it, inEffect(it, o)) }
        // CR 718.3: cast prototyped, on the Stack and the battlefield its mana cost, colour, power and toughness are its prototype's
        if (o.facts.alternative?.keyword == arcanum.api.Keyword.Prototype.Kind && (o.zone == Zone.Stack || o.zone == Zone.Battlefield))
            worked = worked.map(::prototyped)
        // CR 730.2a, 702.140e: a merged permanent has the characteristics of its topmost component and the abilities of all, a
        // copy effect timestamped as the objects merged (the latest merge)
        // fin: a melded permanent has only the combined back face's characteristics (CR 712.8g)
        var mergeDue = o.merged && !(definition.layout is arcanum.api.Layout.Meld && o.face == 1)
        val mergedAt = (o.mergedAbove + o.mergedBelow).maxOfOrNull { it.timestamp } ?: 0
        // CR 613.2a, 613.7, 707.2: copy effects (the merge among them) in timestamp order: a later copy effect overrides the merge
        copyEffectsOn(id).forEach { (effect, copy) ->
            if (mergeDue && effect.timestamp > mergedAt) { worked = merged(o, worked); mergeDue = false }
            val copied = effect.copiedValues?.let { fixed -> copiedByEffect(fixed, o.flipped).let { Worked(it, inEffect(it, o)) } }
                ?: copiedLive(effect, copy, o, visiting)
            if (copied != null) worked = copied.except(copy.except)
        }
        if (mergeDue) worked = merged(o, worked)
        // CR 702.103b: a bestowed spell or permanent is an Aura enchantment with enchant creature
        if (o.bestowed) worked = worked.map(::bestowed)
        return worked
    }

    /** The faces of [values] whose characteristics [o] has: the one up, and what it gained in copying, in neither half. */
    private fun inEffect(values: CopiableValues, o: GameObject): List<IndexedFace> = when {
        // CR 709.4: a split card outside the Stack has both halves' characteristics; a fused split spell too (709.4d)
        values.layout == Layout.Split && (o.zone != Zone.Stack || o.facts.fused) -> values.faces.mapIndexed { i, f -> IndexedFace(i, f) }
        else -> listOfNotNull(values.faces.getOrNull(values.up)?.let { IndexedFace(values.up, it) })
    } + gainedPart(values)

    /** CR 730.2a, 702.140e: the topmost component's faces, then the other components' abilities ([IndexedFace.abilitiesOnly]). In its
     * copiable values those are [CopiableValues.gained] with its own card on top, its faces as printed, so no face or door decides
     * whether it has them; with another card on top that card's face holds them all. It is one object, not double-faced (CR 701.27g). */
    private fun merged(o: GameObject, own: Worked): Worked {
        fun part(p: arcanum.engine.state.MergedPart): IndexedFace? =
            p.definition?.faces?.getOrNull(p.face)?.let { IndexedFace(p.face, it, card = p.id.card) }
        val top = o.mergedAbove.firstOrNull()?.let { listOfNotNull(part(it)) } ?: own.faces
        val others = (o.mergedAbove.drop(1) + o.mergedBelow).mapNotNull(::part) +
            (if (o.mergedAbove.isEmpty()) emptyList() else own.faces)
        val all = top + others.map { it.copy(abilitiesOnly = true) }
        if (o.mergedAbove.isEmpty() && !doubleFaced(own.values.layout))
            return Worked(own.values.copy(gained = own.values.gained + others.flatMap { it.face.abilities }), all)
        return Worked(copiedFace(all)?.let(CopiableValues::of) ?: own.values, all)
    }

    /** The copy effects on [id] in timestamp order (CR 613.7): effect objects locked on it, and static abilities of permanents
     * whose `affects` include it ([staticCopies]), as effect objects of the ability's timestamp. */
    private fun copyEffectsOn(id: ObjectId): List<Pair<EffectObject, Mechanic.CopyOf>> =
        (state.effects.values.filter { it.locked?.contains(id) == true }
            .flatMap { e -> e.mechanics.filterIsInstance<Mechanic.CopyOf>().map { e to it } } + staticCopiesOn(id))
            .sortedBy { it.first.timestamp }

    /** A static ability with a copy effect (CR 613.2a, 707.2: "… is a copy of …") printed on [source], a permanent. */
    private class StaticCopy(val source: GameObject, val index: Int, val ability: Ability.Static, val copy: Mechanic.CopyOf)

    /** Static abilities with copy effects among the permanents' printed abilities (an ability a copy effect gives isn't one). */
    private val staticCopies: List<StaticCopy> by lazy {
        state.battlefield.mapNotNull { state.obj(it) }.filter { !it.phasedOut && !it.faceDown }.flatMap { o ->
            o.definition?.faces?.getOrNull(o.face)?.abilities.orEmpty().mapIndexedNotNull { i, a ->
                (a as? Ability.Static)?.let { s -> s.mechanics.filterIsInstance<Mechanic.CopyOf>().firstOrNull()?.let { StaticCopy(o, i, s, it) } }
            }
        }
    }

    /** The static copies being worked out now: one read again while its own affected objects are found doesn't apply. */
    private val evaluating = mutableSetOf<StaticCopy>()

    /** The static copy effects that apply to [id] (CR 613.2a): the ability's condition holds and its `affects` include [id], both
     * read on the copiable values (layer 1 comes first, CR 613.8 aside). The copied values are those it fixed as it first started to
     * apply ([GameObject.fixedCopies], CR 707.2c), or, until that is kept, those the object copied has now. */
    private fun staticCopiesOn(id: ObjectId): List<Pair<EffectObject, Mechanic.CopyOf>> {
        val fixed = state.obj(id)?.fixedCopies.orEmpty()
        return applyingOn(id).map { sc ->
            val controller = sc.source.controller ?: sc.source.owner
            EffectObject(
                id = arcanum.engine.state.EffectId("static-copy:${sc.source.id}:${sc.index}"), mechanics = listOf(sc.copy),
                affects = sc.ability.affects, locked = listOf(id), source = sc.source.id, controller = controller,
                timestamp = sc.source.timestamp, createdTurn = 0, duration = arcanum.api.Duration.Indefinitely,
                copiedValues = fixed.firstOrNull { it.source == sc.source.id && it.ability == sc.index }?.values,
            ) to sc.copy
        }
    }

    /** The static copies whose condition holds and whose `affects` include [id]. */
    private fun applyingOn(id: ObjectId): List<StaticCopy> {
        if (staticCopies.isEmpty()) return emptyList()
        return staticCopies.filter { it !in evaluating }.filter { sc ->
            evaluating += sc
            try {
                val e = Evaluator(PrintedBoard(state, this), Context(you = sc.source.controller ?: sc.source.owner, self = sc.source.id))
                val affected = (sc.ability.affects as? arcanum.api.Affected.Objects)?.let { e.objects(it.objects) }.orEmpty()
                id in affected && sc.ability.condition?.let { !e.holds(it) } != true
            } finally {
                evaluating -= sc
            }
        }
    }

    /**
     * CR 707.2c: the fixed copies [id] should keep now: those it has of static copy effects that still apply, and, for each that
     * applies without one, the values the object it copies has now (a copy effect finding nothing fixes nothing). Null when the
     * permanents have no static copy effect and [id] keeps none.
     */
    fun fixedCopiesNow(id: ObjectId): List<arcanum.engine.state.FixedCopy>? {
        val o = state.obj(id) ?: return null
        if (staticCopies.isEmpty() && o.fixedCopies.isEmpty()) return null
        return applyingOn(id).mapNotNull { sc ->
            o.fixedCopies.firstOrNull { it.source == sc.source.id && it.ability == sc.index } ?: run {
                val controller = sc.source.controller ?: sc.source.owner
                val original = Evaluator(PrintedBoard(state, this), Context(you = controller, self = sc.source.id)).objects(sc.copy.objects).firstOrNull()
                original?.takeIf { it != id }?.let(::forCopyEffect)?.let { arcanum.engine.state.FixedCopy(sc.source.id, sc.index, it) }
            }
        }
    }

    /** What copying the original gives [o] while the effect is read each time ([EffectObject.copiedValues] not fixed). */
    private fun copiedLive(effect: EffectObject, copy: Mechanic.CopyOf, o: GameObject, visiting: Set<ObjectId>): Worked? {
        val evaluator = Evaluator(PrintedBoard(state, this), Context(you = effect.controller, self = effect.source, names = effect.names))
        val original = worked(evaluator.objects(copy.objects).firstOrNull() ?: return null, visiting) ?: return null
        val values = copiedByEffect(original.values, o.flipped)
        // Halves, flip versions and what is in no face come from the values themselves; else it has what the original has
        val own = values.layout == Layout.Split || values.layout == Layout.Flip || values.gained.isNotEmpty()
        return Worked(values, if (own) inEffect(values, o) else original.faces)
    }

    companion object {
        /** A merged permanent's [faces] as one face (CR 707.2): the first face, with the abilities of its other components (CR
         * 730.2a, 702.140e: they are part of its copiable values). */
        private fun copiedFace(faces: List<IndexedFace>): FaceDefinition? {
            val main = faces.firstOrNull { !it.abilitiesOnly }?.face ?: return null
            val others = faces.filter { it.abilitiesOnly }.flatMap { it.face.abilities }
            return if (others.isEmpty()) main else main.copy(abilities = main.abilities + others)
        }

        /** Whether [layout] is that of a card that can turn over to its other face (CR 712.9). */
        private fun doubleFaced(layout: Layout) = layout == Layout.Transform || layout == Layout.ModalDoubleFaced

        private fun faceUp(values: CopiableValues) = CopiableValues.of(values.faces.getOrElse(values.up) { FaceDefinition("") })

        /**
         * What a copy effect takes from [values]: of a double-faced object, the face up (CR 707.8); of a flip card both
         * versions, the copy's own [flipped] status choosing (CR 707.2, 707.3); else all of them: a split card's halves (CR
         * 709.5b) and the alternative characteristics of an Adventure, an Omen or a prepare spell (CR 715.2b, 720.2b, 722.2b).
         */
        fun copiedByEffect(values: CopiableValues, flipped: Boolean): CopiableValues = when {
            doubleFaced(values.layout) || values.layout is Layout.Meld -> faceUp(values)
            values.layout == Layout.Flip -> values.copy(up = if (flipped) 1 else 0)
            else -> values
        }

        /**
         * CR 707.9: the exceptions of a copy effect, on each face. A prepare spell's characteristics ignore them (CR 722.3c). An
         * ability a split card gains in copying is in neither half (CR 707.9a, 709.5): it is held once, in [CopiableValues.gained].
         */
        fun exceptions(values: CopiableValues, except: List<CopyException>): CopiableValues {
            val faces = values.faces.mapIndexed { i, f -> if (values.layout == Layout.Prepare && i > 0) f else exceptions(f, onEachFace(values.layout, except)) }
            return values.copy(faces = faces, gained = except.fold(values.gained) { gained, e -> when (e) {
                is CopyException.AddAbilities -> if (values.layout == Layout.Split) gained + e.abilities else gained
                is CopyException.WithoutKeyword -> gained.filterNot { it is Keyword && it.kind == e.keyword }
                else -> gained
            } })
        }

        private fun onEachFace(layout: Layout, except: List<CopyException>) =
            if (layout == Layout.Split) except.filterNot { it is CopyException.AddAbilities } else except

        /** What [values] gained in copying as a part of its own, after its faces: [AbilityOrigin.Printed] names it by a face index no face has. */
        private fun gainedPart(values: CopiableValues): List<IndexedFace> = if (values.gained.isEmpty()) emptyList()
            else listOf(IndexedFace(values.faces.size, FaceDefinition("", abilities = values.gained), abilitiesOnly = true, gained = true))

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

        /** The face-down values of [o] (CR 708.2a): with ward {2} while it is cloaked, or face down since it was cast disguised (not
         * once it has been turned face up: face down again, it is a plain 2/2). */
        fun faceDown(o: arcanum.engine.state.GameObject): FaceDefinition =
            if (arcanum.api.ObjectMark.Cloaked in o.marks ||
                (o.facts.alternative?.keyword == arcanum.api.Keyword.Disguise.Kind && !o.facts.turnedFaceUp)) FACE_DOWN_WARD
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
                    // ltr chunk 13: "it loses all other card types": only the added types and the subtypes that go with them (CR 205.1b)
                    types = if (e.replaceTypes) e.add.distinct() else (f.types + e.add).distinct(),
                    subtypes = (if (e.replaceTypes) f.subtypes.filter { s -> Subtypes.kindOf(s).types.any { it in e.add } }
                        else if (e.replaceSubtypes) f.subtypes.filter { Subtypes.kindOf(it) != SubtypeKind.Creature } else f.subtypes) +
                        e.addSubtypes.mapNotNull { (it as? arcanum.api.SubtypeRef.Literal)?.subtype },
                    supertypes = ((f.supertypes + e.addSupertypes).distinct() - e.removeSupertypes.toSet()),
                )
                is CopyException.Colors -> f.copy(
                    colorIndicator = (if (e.add) colorsOf(f) else emptyList<Color>()) +
                        e.colors.mapNotNull { (it as? arcanum.api.ColorRef.Literal)?.color },
                )
                is CopyException.PowerToughness -> f.copy(power = PrintedNumber.Fixed(e.power), toughness = PrintedNumber.Fixed(e.toughness))
                is CopyException.AddAbilities -> f.copy(abilities = f.abilities + e.abilities)
                is CopyException.WithoutKeyword -> f.copy(abilities = f.abilities.filterNot { it is Keyword && it.kind == e.keyword })
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
            val onBattlefield = o?.zone == Zone.Battlefield
            // Every object's starting values are worked out for each view (a library's cards too): one face, the usual
            // case, takes its values as they are; each face's colours are worked out once
            val single = merged.singleOrNull()
            return Characteristics(
                name = if (single != null) single.name.ifEmpty { null }
                    else merged.map { it.name }.filter { it.isNotEmpty() }.joinToString(" // ").ifEmpty { null },
                manaCost = when {
                    single != null -> single.manaCost
                    merged.all { it.manaCost == null } -> null
                    else -> ManaCost(merged.flatMap { it.manaCost?.symbols.orEmpty() })
                },
                colors = if (single != null) colorsOf(single)
                    else merged.flatMapTo(HashSet()) { colorsOf(it) }.let { all -> Color.all.filter { it in all } },
                supertypes = merged.flatMapDistinct { it.supertypes },
                types = merged.flatMapDistinct { it.types },
                subtypes = merged.flatMapDistinct { it.subtypes },
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

        /** [part] of every face, each value once: one face's list as it is when it can't hold a value twice. */
        private inline fun <T> List<FaceDefinition>.flatMapDistinct(part: (FaceDefinition) -> List<T>): List<T> {
            val single = singleOrNull() ?: return flatMap(part).distinct()
            val values = part(single)
            return if (values.size < 2) values else values.distinct()
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
 * count (CR 702.140e); [gained]: no face but what it gained in copying ([CopiableValues.gained]). */
internal data class IndexedFace(
    val index: Int, val face: FaceDefinition,
    val card: arcanum.engine.state.CardId? = null, val abilitiesOnly: Boolean = false, val gained: Boolean = false,
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
