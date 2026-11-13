package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CostPart
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.PlayKind
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.api.isManaAbility
import arcanum.engine.input.CastWay
import arcanum.engine.input.Permission
import arcanum.engine.mana.Mana
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TurnCount
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.AppliedMechanic
import arcanum.engine.view.Functioning

/**
 * When a player may play a land, cast a spell or activate an ability (CR 117.1, 305, 307.1, 601.3, 602.5, 606.3), and
 * the permissions that let a card be played from elsewhere than its owner's hand (D41).
 */
internal object Legality {
    /** A main phase of [player]'s turn with an empty Stack while they have priority: sorcery timing (CR 307.1). */
    fun sorceryTiming(snap: Snapshot, player: PlayerId): Boolean {
        val s = snap.state
        return s.turn.active == player && s.stack.isEmpty() && s.priority.holder == player &&
            (s.turn.step == TurnStep.PrecombatMain || s.turn.step == TurnStep.PostcombatMain)
    }

    fun hasPriority(snap: Snapshot, player: PlayerId): Boolean = snap.state.priority.holder == player

    // ---- Lands (CR 305)

    /** Why [player] can't play the land [card] now, or null when they can (CR 305.1–305.3). */
    fun cantPlayLand(snap: Snapshot, player: PlayerId, card: ObjectId, face: Int = 0): String? {
        val o = snap.state.obj(card) ?: return "no such card"
        if (face != 0 && (o.definition.layout != Layout.ModalDoubleFaced || face !in o.definition.faces.indices)) return "no face $face to play"
        val types = if (face == 0) snap.view[card]?.characteristics?.types.orEmpty() else o.definition.faces[face].types
        if (CardType.Land !in types) return "not a land"
        if (o.zone != Zone.Hand || o.owner != player) {
            if (permissions(snap, player, card, PlayKind.Play).isEmpty()) return "no permission to play it from ${o.zone}"
        }
        if (!sorceryTiming(snap, player)) return "lands are played in your main phase with an empty Stack (CR 305.1)"
        if (snap.state.turn.landsPlayed[player] ?: 0 >= landPlays(snap, player)) return "no land plays left this turn (CR 305.2)"
        return null
    }

    /** Lands [player] may play this turn: one plus extra land plays (CR 305.2); [Int.MAX_VALUE] for any number. */
    fun landPlays(snap: Snapshot, player: PlayerId): Int {
        val extras = snap.view.players[player]?.mechanics.orEmpty().mapNotNull { it.mechanic as? Mechanic.ExtraLandPlays }
        if (extras.any { it.count == null }) return Int.MAX_VALUE
        return 1 + extras.sumOf { it.count ?: 0 }
    }

    // ---- Spells (CR 601.3)

    /** The face a spell has when cast [way] (CR 709.3, 715.3, 712.11). */
    fun face(o: GameObject, way: CastWay): FaceDefinition? = o.definition.faces.getOrNull(way.face)

    /** Faces a card can be cast as: the front, the other half of a split card, an Adventure, a modal double-faced
     * card's back (CR 709.3, 715.3, 712.11). */
    fun castableFaces(o: GameObject): List<Int> {
        val d = o.definition
        return when (d.layout) {
            Layout.Split, Layout.Adventure, Layout.ModalDoubleFaced, Layout.Omen -> d.faces.indices.toList()
            else -> listOf(0)
        }
    }

    /** The [Mechanic.MayPlay] permissions [player] has for [card] in its zone (CR 601.3a, D41), with how ([kind]). */
    fun permissions(snap: Snapshot, player: PlayerId, card: ObjectId, kind: PlayKind): List<Pair<Permission, AppliedMechanic>> {
        val o = snap.state.obj(card) ?: return emptyList()
        return snap.view[card]?.mechanics.orEmpty().mapNotNull { applied ->
            val m = applied.mechanic as? Mechanic.MayPlay ?: return@mapNotNull null
            if (m.from != o.zone) return@mapNotNull null
            // A card in a hand is cast by its owner (CR 601.2, "from their hand")
            if (o.zone == Zone.Hand && o.owner != player) return@mapNotNull null
            if (kind == PlayKind.Play && m.how != PlayKind.Play) return@mapNotNull null
            val eval = snap.evaluator(applied.controller, self = applied.source, names = applied.names, holder = player)
            if (!eval.matches(m.holder, player)) return@mapNotNull null
            // hob: "you may play them if you control a Wizard"
            if (m.condition != null && !eval.holds(m.condition!!)) return@mapNotNull null
            if (m.topOnly && snap.state.player(o.owner).library.firstOrNull() != card) return@mapNotNull null
            if (m.perTurn != null && usedThisTurn(snap, applied) >= m.perTurn!!) return@mapNotNull null
            Permission(source = applied.source, ability = applied.origin, effect = applied.effect) to applied
        }
    }

    private fun usedThisTurn(snap: Snapshot, applied: AppliedMechanic): Int {
        val source = applied.source ?: return 0
        val key = PERMISSION_PREFIX + (applied.origin?.key ?: applied.effect?.value ?: "")
        return snap.state.obj(source)?.uses?.activations.orEmpty()
            .filter { it.key == key && it.turn == snap.state.turn.number }.sumOf { it.count }
    }

    /** The permission [way] names, or the first that applies when it names none and the card isn't in its owner's hand. */
    fun permission(snap: Snapshot, player: PlayerId, card: ObjectId, way: CastWay, kind: PlayKind): Pair<Permission, AppliedMechanic>? {
        val all = permissions(snap, player, card, kind)
        return if (way.permission == null) all.firstOrNull() else all.firstOrNull { it.first == way.permission }
    }

    /** Why [player] can't begin to cast [card] [way] now, or null when they can (CR 601.3, 117.1a). Costs and targets
     * are checked as the cast goes on. */
    // gtc
    /** Why [o] can't be cast fused (CR 702.102a): a split card with fuse, from its owner's hand, both halves at once. */
    private fun cantFuse(o: GameObject, way: CastWay, fromHand: Boolean, permitted: Boolean): String? {
        val d = o.definition
        if (d.layout != arcanum.api.Layout.Split || d.faces.none { f -> f.abilities.any { it is Keyword.Fuse } }) return "it has no fuse"
        if (!fromHand || permitted) return "fused only from your hand (CR 702.102a)"
        if (way.face != 0 || way.alternative != null) return "a fused spell is both halves, for their mana costs"
        return null
    }

    fun cantCast(snap: Snapshot, player: PlayerId, card: ObjectId, way: CastWay): String? {
        val o = snap.state.obj(card) ?: return "no such card"
        if (way.face !in castableFaces(o)) return "face ${way.face} can't be cast"
        val face = face(o, way) ?: return "no face ${way.face}"
        val chars = snap.view[card]?.characteristics
        val faceDown = alternative(snap, card, way, player)?.second?.faceDown == true
        val types = if (faceDown) listOf(CardType.Creature)
            else if (way.fused) o.definition.faces.flatMap { it.types }.distinct().let { t -> if (CardType.Sorcery in t) t - CardType.Instant else t }
            else face.types
        if (CardType.Land in types) return "lands are played, not cast (CR 305.9)"
        if (!hasPriority(snap, player)) return "$player doesn't have priority"
        val fromHand = o.zone == Zone.Hand && o.owner == player
        val permission = if (fromHand && way.permission == null) null else permission(snap, player, card, way, PlayKind.Cast)
        if (!fromHand && permission == null) return "no permission to cast it from ${o.zone}"
        // A permission for some spells only ("it may not be cast as an Adventure this way", CR 715.4): the face cast; a filter
        // the face alone can't answer ("a spell with mana value less than or equal to …", ecl) is checked on the card
        (permission?.second?.mechanic as? Mechanic.MayPlay)?.resultingSpell?.let { filter ->
            val applied = permission.second
            val matches = faceMatches(filter, face)
                ?: snap.evaluator(applied.controller, self = applied.source, names = applied.names, holder = player).matches(filter, card)
            if (!matches) return "that permission doesn't let it be cast as ${face.name}"
        }
        // CR 702.127a: an aftermath half is cast only from a graveyard, and its permission is for that half only
        val aftermathHalf = face.abilities.any { it == Keyword.Aftermath }
        if (aftermathHalf && o.zone != Zone.Graveyard) return "that half is cast only from a graveyard (CR 702.127a)"
        val byAftermath = (permission?.second?.origin as? AbilityOrigin.Expansion)?.let { e ->
            chars?.abilities.orEmpty().firstOrNull { it.origin == e.keyword }?.ability == Keyword.Aftermath } == true
        if (byAftermath && !aftermathHalf) return "only the aftermath half is cast from a graveyard (CR 702.127a)"
        if (way.alternative != null && alternative(snap, card, way, player) == null) return "no such alternative cost, or its condition isn't met"
        // eoe: an alternative cost for a card cast from one zone only (warp: "from your hand", CR 702.185a), or from where a permission
        // names its keyword ("You may cast this card from your graveyard using its warp ability")
        alternative(snap, card, way, player)?.second?.let { alt ->
            val only = alt.from ?: return@let
            val via = (permission?.second?.mechanic as? Mechanic.MayPlay)?.viaAlternative
            if (o.zone != only && (via == null || via != alt.keyword)) return "that alternative cost is only for a card cast from the ${only.toString().lowercase()}"
        }
        // tmt: a permission to cast only by an alternative cost ("using their sneak abilities", "using its bestow ability")
        (permission?.second?.mechanic as? Mechanic.MayPlay)?.viaAlternative?.let { via ->
            if (alternative(snap, card, way, player)?.second?.keyword != via) return "only by its ${via.text.lowercase()} cost"
        }
        // msh: sneak (CR 702.190a): only during the caster's declare blockers step, then as though it had flash
        val during = alternative(snap, card, way, player)?.second?.duringYour
        if (during != null && (snap.state.turn.active != player || snap.state.turn.step != during)) return "only during your $during step (CR 702.190a)"
        if (way.fused) cantFuse(o, way, fromHand, permission != null)?.let { return it }
        // CR 307.1, 702.8a: sorcery timing unless it's an instant or has flash
        // A card cast from face down in exile (foretold, CR 406.3a) has its card's abilities
        val flash = chars?.hasKeyword(Keyword.Flash) == true || o.faceDown && face.abilities.any { it == Keyword.Flash }
        // A permission a mark gives may hold it to sorcery timing (a plotted card, CR 702.170d)
        val asSorcery = arcanum.engine.foretell.MarkCasts.asSorcery(permission?.second?.origin)
        val instant = !asSorcery && during != null || !asSorcery && !faceDown && (CardType.Instant in types || flash ||
            snap.view[card]?.has { it is Mechanic.InstantSpeed } == true || castsAsThoughFlash(snap, player, card) || nextSpellFlash(snap, player, card) ||
            // tdm: an additional cost whose payment lets it be cast as though it had flash (the cast then pays it)
            chars?.abilities.orEmpty().any { inst -> (inst.ability as? arcanum.api.Ability.Static)?.mechanics.orEmpty().any { it is Mechanic.AdditionalCost && it.flash } })
        if (!instant && !sorceryTiming(snap, player)) return "only as a sorcery: your main phase, empty Stack (CR 307.1)"
        if (onlyAsSorcery(snap, player, card) && !sorceryTiming(snap, player)) return "$player can cast it only as a sorcery (CR 307.5)"
        legendarySpellWithoutLegend(snap, player, face, types)?.let { return it }
        restriction(snap, player, card)?.let { return it }
        if (cantCastBy(snap, player, card)) return "$player can't cast it"
        return null
    }

    /**
     * Why [player] can't cast [card] as its [face] while a spell or ability resolves and tells them to (CR 608.2g, 601.3):
     * the effect allows it, so timing and permissions don't matter, but lands aren't cast (CR 305.9), and an effect
     * that says they can't (CR 101.2) or the card's own casting condition (CR 601.3e) still stops it.
     */
    fun cantCastByEffect(snap: Snapshot, player: PlayerId, card: ObjectId, face: Int): String? {
        val o = snap.state.obj(card) ?: return "no such card"
        val f = o.definition.faces.getOrNull(face) ?: return "no face $face"
        if (CardType.Land in f.types) return "lands are played, not cast (CR 305.9)"
        restriction(snap, player, card, timing = false)?.let { return it }
        if (cantCastBy(snap, player, card)) return "$player can't cast it"
        // rvr chunk 04: "can cast spells only any time they could cast a sorcery" needs priority, so not while resolving (CR 307.5)
        if (onlyAsSorcery(snap, player, card)) return "$player can cast it only as a sorcery (CR 307.5)"
        return null
    }

    /** rvr chunk 04: [Mechanic.CastOnlyAsSorcery] on [player] for this spell (CR 307.5). */
    private fun onlyAsSorcery(snap: Snapshot, player: PlayerId, card: ObjectId): Boolean =
        snap.view.players[player]?.mechanics.orEmpty().any { applied ->
            val m = applied.mechanic as? Mechanic.CastOnlyAsSorcery ?: return@any false
            snap.evaluator(applied.controller, self = applied.source, names = applied.names).let { e ->
                e.with(e.ctx.copy(tested = card, affectedPlayer = player)).matches(asSpell(m.spells), card)
            }
        }

    /** rvr chunk 04: "The next sorcery spell you cast this turn can be cast as though it had flash": [Mechanic.InstantSpeed] of an effect
     * on [player]'s next spells, tested on the card as the spell it would be (CR 601.3a, 611.2f); casting it uses the effect up. */
    private fun nextSpellFlash(snap: Snapshot, player: PlayerId, card: ObjectId): Boolean = snap.view.game.rules.any { applied ->
        if (applied.mechanic != Mechanic.InstantSpeed || applied.controller != player) return@any false
        val id = applied.effect ?: return@any false
        val effect = snap.state.effects[id]?.takeIf { it.affects is arcanum.api.Affected.NextSpells } ?: return@any false
        val e = snap.evaluator(player, self = effect.source, names = effect.names)
        NextSpells.appliesTo(snap.state, id, e.with(e.ctx.copy(prospectiveCast = true)), card)
    }

    /** Whether the printed [face] matches a type filter, or null when the filter asks more than types and subtypes. */
    private fun faceMatches(filter: ObjectFilter, face: arcanum.api.FaceDefinition): Boolean? = when (filter) {
        is ObjectFilter.Not -> faceMatches(filter.filter, face)?.not()
        is ObjectFilter.Sub -> (filter.subtype as? arcanum.api.SubtypeRef.Literal)?.let { it.subtype in face.subtypes }
        is ObjectFilter.Type -> (filter.cardType as? arcanum.api.CardTypeRef.Literal)?.let { it.cardType in face.types }
        else -> null
    }


    /** ltr chunk 05: a legendary instant or sorcery is cast only by a player who controls a legendary creature or planeswalker (CR 205.4e). */
    private fun legendarySpellWithoutLegend(snap: Snapshot, player: PlayerId, face: FaceDefinition, types: List<CardType>): String? {
        if (arcanum.api.Supertype.Legendary !in face.supertypes || (CardType.Instant !in types && CardType.Sorcery !in types)) return null
        val has = snap.state.battlefield.any { id ->
            val v = snap.view[id] ?: return@any false
            val c = v.characteristics ?: return@any false
            v.controller == player && arcanum.api.Supertype.Legendary in c.supertypes && (c.isType(CardType.Creature) || c.isType(CardType.Planeswalker))
        }
        return if (has) null else "a legendary instant or sorcery needs a legendary creature or planeswalker (CR 205.4e)"
    }

    /** ltr: "you may activate equip abilities any time you could cast an instant" ([Mechanic.ActivateAtInstantSpeed]): the ability is the
     * expansion of a keyword the filter names, or of an ability of a source the filter's source matches (CR 602.5d). */
    fun activatesAtInstantSpeed(snap: Snapshot, player: PlayerId, source: ObjectId, instance: AbilityInstance): Boolean =
        snap.view.players[player]?.mechanics.orEmpty().any { applied ->
            val m = applied.mechanic as? Mechanic.ActivateAtInstantSpeed ?: return@any false
            val filter = m.abilities as? ObjectFilter.AbilityOf ?: return@any false
            val keywordOk = filter.keyword?.let { kind ->
                // CR 702.143a: foretelling is the rules' special action of the foretell keyword, not its expansion
                if (arcanum.engine.foretell.Foretell.isForetell(instance.origin)) return@let kind == Keyword.Foretell.Kind
                val root = (instance.origin as? arcanum.engine.view.AbilityOrigin.Expansion)?.let { instance.origin.root } ?: return@any false
                snap.view[source]?.characteristics?.abilities.orEmpty().any { it.origin == root && (it.ability as? arcanum.api.Keyword)?.kind == kind }
            } ?: true
            val sourceOk = filter.source?.let { snap.evaluator(applied.controller, self = applied.source, holder = player).matches(it, source) } ?: true
            keywordOk && sourceOk
        }

    private fun castsAsThoughFlash(snap: Snapshot, player: PlayerId, card: ObjectId): Boolean =
        snap.view.players[player]?.mechanics.orEmpty().any { applied ->
            val m = applied.mechanic as? Mechanic.CastAsThoughFlash ?: return@any false
            // blb backfill: the card is judged as the spell it would be ("noncreature spells", CR 601.3a)
            val e = snap.evaluator(applied.controller, self = applied.source, holder = player)
            e.with(e.ctx.copy(prospectiveCast = true)).matches(m.spells, card)
        }

    /** "Cast this spell only …" (CR 601.3e): the card's own [Mechanic.CastRestriction]s; [timing] too unless an effect
     * has it cast while resolving (CR 608.2g). */
    private fun restriction(snap: Snapshot, player: PlayerId, card: ObjectId, timing: Boolean = true): String? {
        val abilities = snap.view[card]?.characteristics?.abilities.orEmpty()
        abilities.forEach { inst ->
            val static = inst.ability as? Ability.Static ?: return@forEach
            static.mechanics.filterIsInstance<Mechanic.CastRestriction>().forEach { r ->
                val eval = snap.evaluator(player, self = card)
                if (r.condition != null && !eval.holds(r.condition!!)) return "its casting condition isn't met"
                if (timing && r.timing == Timing.Sorcery && !sorceryTiming(snap, player)) return "only as a sorcery"
            }
        }
        return null
    }

    /** [Mechanic.CantCast] on [player] for this spell (CR 101.2). */
    private fun cantCastBy(snap: Snapshot, player: PlayerId, card: ObjectId): Boolean =
        snap.view.players[player]?.mechanics.orEmpty().any { applied ->
            val m = applied.mechanic as? Mechanic.CantCast ?: return@any false
            snap.evaluator(applied.controller, self = applied.source, names = applied.names).let { e ->
                e.with(e.ctx.copy(tested = card, affectedPlayer = player)).matches(asSpell(m.spells), card)
            }
        }

    /** CR 601.3: the card is tested as the spell it would become: "noncreature spells" match a noncreature card. */
    private fun asSpell(filter: arcanum.api.ObjectFilter): arcanum.api.ObjectFilter = when (filter) {
        is arcanum.api.ObjectFilter.Category ->
            if (filter.category == arcanum.api.ObjectCategory.Spell) arcanum.api.ObjectFilter.Any else filter
        is arcanum.api.ObjectFilter.And -> arcanum.api.ObjectFilter.And(filter.all.map { asSpell(it) })
        is arcanum.api.ObjectFilter.Or -> arcanum.api.ObjectFilter.Or(filter.any.map { asSpell(it) })
        else -> filter
    }

    /** The alternative cost [way] names: its static ability and mechanic, when it exists and its condition holds (CR 118.9). */
    fun alternative(snap: Snapshot, card: ObjectId, way: CastWay, player: PlayerId): Pair<AbilityInstance, Mechanic.AlternativeCost>? {
        val origin = way.alternative ?: return null
        return alternativeCosts(snap, card, player).firstOrNull { it.first.origin == origin }
    }

    /** The alternative costs [player] may cast a card for, whose conditions hold: [Mechanic.AlternativeCost] in its
     * static abilities, and those another object's static ability gives the spells its controller casts (dsk gaps: "you
     * may pay {0} rather than pay the mana cost for a spell you cast from exile", tested on the card as the spell cast
     * from where it is, CR 601.2a; their origin is [AbilityOrigin.FromAbility] of that ability), each
     * [Mechanic.AlternativeCost.perTurn] times each turn. */
    fun alternativeCosts(snap: Snapshot, card: ObjectId, player: PlayerId): List<Pair<AbilityInstance, Mechanic.AlternativeCost>> {
        val owner = snap.state.obj(card)?.owner ?: return emptyList()
        val you = snap.board.controller(card) ?: owner
        val own = snap.view[card]?.characteristics?.abilities.orEmpty().flatMap { inst ->
            val static = inst.ability as? Ability.Static ?: return@flatMap emptyList()
            if (static.condition != null && !snap.evaluator(you, self = card).holds(static.condition!!)) return@flatMap emptyList()
            static.mechanics.filterIsInstance<Mechanic.AlternativeCost>().map { inst to it }
        }
        // dsk gaps
        val granted = snap.view[card]?.mechanics.orEmpty().mapNotNull { applied ->
            val m = applied.mechanic as? Mechanic.AlternativeCost ?: return@mapNotNull null
            val source = applied.source?.takeIf { it != card } ?: return@mapNotNull null
            val by = applied.origin ?: return@mapNotNull null
            if (applied.controller != player) return@mapNotNull null
            val index = (snap.view[source]?.characteristics?.abilities.orEmpty().firstOrNull { it.origin == by }?.ability as? Ability.Static)
                ?.mechanics?.indexOf(m)?.takeIf { it >= 0 } ?: 0
            val origin = AbilityOrigin.FromAbility(source, by, index, 0)
            if (m.perTurn != null && activationsThisTurn(snap, source, alternativeKey(origin)) >= m.perTurn!!) return@mapNotNull null
            AbilityInstance(Ability.Static(listOf(m), affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self)), origin) to m
        }
        // msh: "the next red or green creature spell you cast this turn can be cast without paying its mana cost": an
        // effect's alternative cost for the next spells (CR 611.2f), tested on the card as the spell it would be (CR 601.2a)
        val next = snap.view.game.rules.mapNotNull { applied ->
            val m = applied.mechanic as? Mechanic.AlternativeCost ?: return@mapNotNull null
            val id = applied.effect ?: return@mapNotNull null
            val effect = snap.state.effects[id]?.takeIf { it.affects is arcanum.api.Affected.NextSpells } ?: return@mapNotNull null
            if (applied.controller != player) return@mapNotNull null
            val e = snap.evaluator(player, self = effect.source, names = effect.names)
            if (!NextSpells.appliesTo(snap.state, id, e.with(e.ctx.copy(prospectiveCast = true)), card)) return@mapNotNull null
            AbilityInstance(Ability.Static(listOf(m), affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self)),
                AbilityOrigin.FromEffect(id, effect.mechanics.indexOf(m), 0)) to m
        }
        return own + granted + next
    }

    /** The use count key of a granted alternative cost with a limit each turn, kept on the granting object. */
    fun alternativeKey(origin: AbilityOrigin.FromAbility): String = ALTERNATIVE_PREFIX + "${origin.by.key}/m${origin.mechanic}"

    private const val ALTERNATIVE_PREFIX = "$" + "alternative:"

    // ---- Activated abilities (CR 602, 606)

    /** Why [player] can't activate [instance] of [source] now, or null (CR 602.2, 602.5, 605.3a, 606.3). */
    fun cantActivate(snap: Snapshot, player: PlayerId, source: ObjectId, instance: AbilityInstance): String? {
        val ability = instance.ability
        if (ability is Ability.SpecialAction) return cantTakeSpecialAction(snap, player, source, instance, ability)
        ability as? Ability.Activated ?: return "not an activated ability"
        if (!hasPriority(snap, player)) return "$player doesn't have priority"
        if (!Mana.canActivate(snap, player, source, instance)) return "it can't be activated now (zone, cost, condition or who may activate)"
        val loyalty = ability.cost.parts.any { it is CostPart.Loyalty }
        // CR 606.3: loyalty abilities as a sorcery, once each turn per permanent
        // fra: "you may activate loyalty abilities of … any time you could cast an instant" (still once each turn)
        val instantLoyalty = snap.view[source]?.has { it == arcanum.api.Mechanic.LoyaltyAtInstantSpeed } == true
        if (loyalty && (!(sorceryTiming(snap, player) || instantLoyalty) || activationsThisTurn(snap, source, LOYALTY_KEY) > 0)) {
            return "a loyalty ability: once each turn, as a sorcery (CR 606.3)"
        }
        // CR 606.6: a negative loyalty cost needs that many loyalty counters
        ability.cost.parts.filterIsInstance<CostPart.Loyalty>().forEach { part ->
            // mh2: "cost an additional [+1]"
            val n = ((part.amount as? arcanum.api.Value.Fixed)?.value ?: 0) + snap.view.players[player]?.mechanics.orEmpty()
                .sumOf { (it.mechanic as? arcanum.api.Mechanic.AdditionalLoyaltyCost)?.amount ?: 0 }
            if (n < 0 && (snap.state.obj(source)?.counters(arcanum.api.CounterKind.Loyalty) ?: 0) < -n) return "not enough loyalty (CR 606.6)"
        }
        when (ability.rules.timing) {
            Timing.Sorcery -> if (!sorceryTiming(snap, player) && !activatesAtInstantSpeed(snap, player, source, instance)) return "only as a sorcery (CR 602.5d)"
            Timing.AsThisCard -> if (cantCast(snap, player, source, CastWay()) != null) return "only when this card could be cast"
            Timing.Any, Timing.Instant -> Unit
        }
        return usedUp(snap, source, instance)
    }

    /**
     * Why [player] may not use [instance] of [source] (an activated ability or a special action) from where [source] is now
     * whatever the moment, or null when they may (CR 113.6, 602.2, 116.2): it functions in that zone, [player] may activate or
     * take it (who may activate it, its controller for a special action), its condition holds and it has uses left (CR 602.5b).
     * Not whether they can now: priority, timing, costs and effects that stop it aren't checked, as a cast permission doesn't
     * check them ([permissions]). [cantActivate] refuses whatever this refuses, so a host's status and the options agree.
     */
    fun cantUse(snap: Snapshot, player: PlayerId, source: ObjectId, instance: AbilityInstance): String? =
        notTheirs(snap, player, source, instance) ?: usedUp(snap, source, instance)

    /** [cantUse] without the uses: it doesn't function where [source] is, or [player] may not activate or take it there. */
    private fun notTheirs(snap: Snapshot, player: PlayerId, source: ObjectId, instance: AbilityInstance): String? =
        when (val ability = instance.ability) {
            is Ability.Activated ->
                if (Mana.functionsFor(snap, player, source, ability)) null else "it can't be activated now (zone, cost, condition or who may activate)"
            is Ability.SpecialAction -> {
                val o = snap.state.obj(source) ?: return "no such object"
                val you = snap.board.controller(source) ?: o.owner
                when {
                    o.zone !in Functioning.zones(ability, false) -> "it doesn't work from ${o.zone}"
                    you != player -> "only its controller may take it"
                    ability.condition != null && !snap.evaluator(you, self = source).holds(ability.condition!!) -> "its condition isn't met"
                    else -> null
                }
            }
            else -> "not an activated ability"
        }

    /** [cantUse]'s uses: an activated ability used as often as it may be each turn or at all (CR 602.5b), a special action
     * once each game used. */
    private fun usedUp(snap: Snapshot, source: ObjectId, instance: AbilityInstance): String? {
        val key = instance.origin.key
        return when (val ability = instance.ability) {
            is Ability.Activated -> {
                ability.rules.perTurn?.let { if (activationsThisTurn(snap, source, key) >= it) return "only $it time(s) each turn (CR 602.5b)" }
                if (ability.rules.once && activationsEver(snap, source, key) >= 1 + additionalUses(snap, source, ability) && !asThoughNotActivated(snap, source, ability))
                    "only once" else null
            }
            is Ability.SpecialAction -> if (ability.oncePerGame && activationsEver(snap, source, key) > 0) "only once each game" else null
            else -> null
        }
    }

    /**
     * The objects whose activated abilities and special actions [player] may activate (CR 602.2, 116.2): the battlefield, their
     * hand and graveyard, exile, the command zone and their cards outside the game (a revealed companion, CR 702.139a), in that
     * order. What [arcanum.engine.Engine.options] tries, and where a host's status looks outside a hand.
     */
    fun abilitySources(state: arcanum.engine.state.GameState, player: PlayerId): List<ObjectId> =
        state.battlefield + state.player(player).hand + state.player(player).graveyard + state.exile + state.command + state.player(player).outside

    /** [id]'s activated abilities and special actions, those it shows and those the rules give it ([SpecialActions]). */
    fun activatable(snap: Snapshot, id: ObjectId): List<AbilityInstance> =
        (snap.view[id]?.characteristics?.abilities.orEmpty() + SpecialActions.of(snap, id))
            .filter { it.ability is Ability.Activated || it.ability is Ability.SpecialAction }

    /** How many more times mana ability [instance] of [source] may be activated (CR 602.5b): its limit each turn or once only,
     * else no limit of its own. */
    fun manaUsesLeft(snap: Snapshot, source: ObjectId, instance: AbilityInstance): Int {
        val ability = instance.ability as? Ability.Activated ?: return 0
        var left = Int.MAX_VALUE
        ability.rules.perTurn?.let { left = minOf(left, it - activationsThisTurn(snap, source, instance.origin.key)) }
        if (ability.rules.once && activationsEver(snap, source, instance.origin.key) >= 1) left = 0
        return left
    }

    /** dft: a rule lets its [ability] be activated as though it hadn't been (CR 702.177b). */
    private fun asThoughNotActivated(snap: Snapshot, source: ObjectId, ability: Ability.Activated): Boolean = ability.label?.let { label ->
        snap.view[source]?.mechanics.orEmpty().any { (it.mechanic as? arcanum.api.Mechanic.AsThoughNotActivated)?.label == label }
    } ?: false

    private fun cantTakeSpecialAction(snap: Snapshot, player: PlayerId, source: ObjectId, instance: AbilityInstance, action: Ability.SpecialAction): String? {
        if (snap.state.obj(source) == null) return "no such object"
        if (!hasPriority(snap, player)) return "$player doesn't have priority"
        notTheirs(snap, player, source, instance)?.let { return it }
        when (action.timing) {
            Timing.Sorcery -> if (!sorceryTiming(snap, player)) return "only as a sorcery"
            Timing.AsThisCard -> if (cantCast(snap, player, source, CastWay()) != null) return "only when this card could be cast"
            else -> Unit
        }
        usedUp(snap, source, instance)?.let { return it }
        // Unable to Scream: "can't be turned face up"
        if (action.instructions.steps.any { (it as? arcanum.api.Step.Do)?.effect is arcanum.api.Effect.TurnFaceUp } &&
            snap.view[source]?.has { it == arcanum.api.Mechanic.CantBeTurnedFaceUp } == true) return "it can't be turned face up"
        return null
    }

    /** msh: "Each power-up ability of permanents you control can be activated an additional time" (CR 702.193a). */
    private fun additionalUses(snap: Snapshot, source: ObjectId, ability: Ability.Activated): Int = ability.label?.let { label ->
        snap.view[source]?.mechanics.orEmpty().sumOf { (it.mechanic as? arcanum.api.Mechanic.LabelAdditionalUses)?.takeIf { m -> m.label == label }?.count ?: 0 }
    } ?: 0

    fun activationsThisTurn(snap: Snapshot, source: ObjectId, key: String): Int =
        snap.state.obj(source)?.uses?.activations.orEmpty().filter { it.key == key && it.turn == snap.state.turn.number }.sumOf { it.count }

    private fun activationsEver(snap: Snapshot, source: ObjectId, key: String): Int =
        snap.state.obj(source)?.uses?.activations.orEmpty().filter { it.key == key }.sumOf { it.count }

    /** [counts] with one more use of [key] this turn. */
    fun counted(counts: List<TurnCount>, key: String, turn: Int): List<TurnCount> {
        val existing = counts.firstOrNull { it.key == key && it.turn == turn }
        return if (existing == null) counts + TurnCount(key, turn, 1)
            else counts.map { if (it === existing) it.copy(count = it.count + 1) else it }
    }

    /** Whether [ability] is a mana ability that isn't limited to instant timing (CR 605.3a). */
    fun isManaAbility(ability: Ability): Boolean = ability is Ability.Activated && ability.isManaAbility()

    /** The activation count key every loyalty ability of a permanent shares (CR 606.3). */
    const val LOYALTY_KEY = "\$loyalty"
    /** Prefix of the use count of a once-per-turn permission (Lurrus), kept on the permission's source. */
    const val PERMISSION_PREFIX = "\$permission:"

    /** The origin of an ability instance by key, for lookups that survive the object changing zones. */
    fun instance(snap: Snapshot, id: ObjectId, origin: AbilityOrigin): AbilityInstance? =
        snap.view[id]?.characteristics?.abilities?.firstOrNull { it.origin == origin }
            ?: SpecialActions.of(snap, id).firstOrNull { it.origin == origin }
}
