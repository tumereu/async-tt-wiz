package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A one-shot effect (CR 610): something done once when a spell or ability resolves.
 *
 * Effects that produce objects name them for later steps ("the exiled card", "cards drawn this way"). An effect that
 * doesn't change the objects' identity has one `bind` (create token, copy, search, reveal, look). An effect that moves
 * objects between zones gives each object a new identity (CR 400.7): `preBind` names them as they were (their last
 * known information, readable but not acted on, CR 608.2h) and `postBind` as they are after the move. Continuous rules are
 * [Mechanic]s; [ApplyMechanics] is the bridge that creates a continuous effect for a [Duration] (CR 611.2).
 * Keyword actions that are shorthand for other effects (investigate, amass, …) are [KeywordAction]s, expanded by the
 * shared table like keywords.
 */
@Serializable
sealed interface Effect {
    /** [source] deals [amount] damage to [to] (CR 120). [to] may hold objects and players. */
    @Serializable @SerialName("damage") data class DealDamage(
        val to: Recipients, val amount: Value, val source: Objects,
        /** "This damage can't be prevented" (CR 615.12). */
        val cantBePrevented: Boolean = false,
        /** Names the damage actually dealt, after prevention and replacement ("the damage dealt this way"). */
        val bind: String? = null,
    ) : Effect
    /** [first] and [second] each deal damage equal to their power to the other (CR 701.14). */
    @Serializable @SerialName("fight") data class Fight(
        val first: Objects, val second: Objects,
        // tla
        /** Names the damage [first] deals to [second] ("If the creature the opponent controls is dealt excess damage this way", read with
         * [Value.Excess]). */
        val bind: String? = null,
    ) : Effect

    @Serializable @SerialName("destroy") data class Destroy(
        val objects: Objects, val noRegeneration: Boolean = false, val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    @Serializable @SerialName("sacrifice") data class Sacrifice(val objects: Objects, val preBind: String? = null, val postBind: String? = null) : Effect
    /** Move [objects] to another zone (exile, return to hand, put onto the battlefield, put into a library, …).
     * [untilLeaves]: "exile … until ~ leaves the battlefield": when that object leaves, the moved cards return at once
     * without using the Stack, and if it has already left, nothing is moved (CR 610.3). */
    @Serializable @SerialName("move") data class Move(
        val objects: Objects, val to: Destination, val untilLeaves: Objects? = null,
        val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    @Serializable @SerialName("draw") data class Draw(
        val players: Players, val count: Value, val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    /** [players] discard [count] cards matching [filter] of their own choice (or at [random]); or exactly [objects]. When
     * someone else chooses ("You choose a nonland card from it. That player discards that card") it is a [Step.Choose]
     * then `Discard(objects = …)`. */
    @Serializable @SerialName("discard") data class Discard(
        val players: Players, val count: Value, val filter: ObjectFilter, val objects: Objects? = null,
        val random: Boolean = false, val preBind: String? = null, val postBind: String? = null,
    ) : Effect
    @Serializable @SerialName("mill") data class Mill(val players: Players, val count: Value, val preBind: String? = null, val postBind: String? = null) : Effect
    /** [bind] names the cards put on the bottom (scry) or into the graveyard (surveil). */
    @Serializable @SerialName("scry") data class Scry(val players: Players, val count: Value, val bind: String? = null) : Effect
    @Serializable @SerialName("surveil") data class Surveil(val players: Players, val count: Value, val bind: String? = null) : Effect
    /** [searcher] searches [library]'s [zones] (their library, "and/or graveyard") for up to [max] cards matching
     * [filter], with [differentNames] if they must all have different names; the found cards are named [bind] and moved by
     * a following step. [reveal] shows them (CR 701.23). Any player may be told to search any player's library: "search
     * target opponent's library" is `library = Target`, searcher you; "its controller may search their library" is both
     * that player. */
    @Serializable @SerialName("search") data class Search(
        val library: Players, val filter: ObjectFilter, val max: Value, val reveal: Boolean = false,
        val zones: List<Zone>, val differentNames: Boolean = false, val bind: String? = null,
        val searcher: Players,
    ) : Effect
    /** Shuffle [library]'s library, leaving out [except] (cards just found by a search: "then shuffle and put that card
     * on top", CR 701.24b); the cards left out keep their identity. */
    @Serializable @SerialName("shuffle") data class Shuffle(val library: Players, val except: Objects? = null) : Effect
    /** [chooser] puts [objects] back in any order where they are ("put them back in any order"): not a zone change,
     * they keep their identity (unless they were revealed, CR 701.20d). */
    @Serializable @SerialName("reorder") data class Reorder(val objects: Objects, val chooser: Players) : Effect
    @Serializable @SerialName("reveal") data class Reveal(val objects: Objects, val bind: String? = null) : Effect
    /** [players] look at [objects] (hidden from others); [bind] names them for "from among them". */
    @Serializable @SerialName("look") data class LookAt(val objects: Objects, val players: Players, val bind: String? = null) : Effect

    @Serializable @SerialName("gain-life") data class GainLife(val players: Players, val amount: Value, val bind: String? = null) : Effect
    @Serializable @SerialName("lose-life") data class LoseLife(val players: Players, val amount: Value, val bind: String? = null) : Effect
    @Serializable @SerialName("set-life") data class SetLife(val players: Players, val amount: Value) : Effect

    /** Put [amount] counters of [kind] on each of [objects], or, [distributed], [amount] in total distributed among them
     * as the controller chooses, at least one each ("distribute three +1/+1 counters among any number of creatures you
     * control", CR 608.2d). */
    @Serializable @SerialName("put-counters") data class PutCounters(
        val objects: Objects, val kind: CounterKind, val amount: Value, val distributed: Boolean = false,
    ) : Effect
    /** Remove [amount] counters of [kind] (any kind when null) from each of [objects]; [bind] names the number removed ("Draw
     * that many cards", dgm). */
    @Serializable @SerialName("remove-counters") data class RemoveCounters(
        val objects: Objects, val kind: CounterKind?, val amount: Value,
        // dgm
        val bind: String? = null,
    ) : Effect
    @Serializable @SerialName("player-counters") data class GivePlayerCounters(val players: Players, val kind: CounterKind, val amount: Value) : Effect

    @Serializable @SerialName("tap") data class Tap(val objects: Objects) : Effect
    @Serializable @SerialName("untap") data class Untap(val objects: Objects) : Effect
    /** Attach an Aura, Equipment or Fortification to an object or player (CR 701.3). */
    @Serializable @SerialName("attach") data class Attach(val objects: Objects, val to: Recipients) : Effect
    @Serializable @SerialName("transform") data class Transform(val objects: Objects) : Effect
    /** Flip a flip card (CR 710). */
    @Serializable @SerialName("flip") data class Flip(val objects: Objects) : Effect
    /** [players] skip their next turn. */
    @Serializable @SerialName("skip-next-turn") data class SkipNextTurn(val players: Players) : Effect
    /** Turn [objects] face up, putting [counters] on as it is turned (megamorph, CR 702.37b). [x]: the X of the cost it is turned face
     * up for (a morph or disguise cost with {X}, CR 702.37f, 702.168e), which its "When this is turned face up" abilities read
     * ([Value.EventX]). */
    @Serializable @SerialName("turn-face-up") data class TurnFaceUp(
        val objects: Objects, val counters: List<CountersToPut> = emptyList(), val x: Value? = null,
    ) : Effect
    @Serializable @SerialName("turn-face-down") data class TurnFaceDown(val objects: Objects) : Effect
    /** [objects] phase out (CR 702.26); mh2: [untilLeaves], "phase out until this enchantment leaves the battlefield": they don't phase in
     * during untap steps but when that object leaves the battlefield, and if it has already left, nothing phases out (CR 610.3,
     * 702.26b); [bind] names those that phased out ("for each creature that phased out this way"). */
    @Serializable @SerialName("phase-out") data class PhaseOut(val objects: Objects, val untilLeaves: Objects? = null, val bind: String? = null) : Effect
    /** mh2: [objects] phase in (CR 702.26c): those [PhaseOut.untilLeaves] holds out, as the object they wait for leaves. */
    @Serializable @SerialName("phase-in") data class PhaseIn(val objects: Objects) : Effect
    /** Put [mark] on [objects], or take it off ([remove], "is no longer suspected"): only on those where it can live ([MarkRules]): a
     * permanent's marks on permanents, "becomes plotted" or "becomes foretold" on a card in exile (CR 702.170c, 702.143d). When it clears
     * and what it gives are in the table too. [cost]: the cost of the mark's keyword the effect gives as it is gained ("It becomes
     * foretold. Its foretell cost is …", CR 702.143d), cast for besides any the card has. */
    @Serializable @SerialName("mark") data class Mark(val objects: Objects, val mark: ObjectMark, val remove: Boolean = false, val cost: Cost? = null) : Effect
    @Serializable @SerialName("designation") data class GiveDesignation(val players: Players, val designation: Designation) : Effect

    /** Create [count] tokens, as one event; [attacking]: what they attack (CR 508.4); [attachedTo]: created attached
     * ("a Role token attached to target creature", CR 303.4f). */
    @Serializable @SerialName("create-token") data class CreateToken(
        val token: TokenSpec, val count: Value, val controller: Players,
        val tapped: Boolean = false, val attacking: AttackTarget? = null, val attachedTo: Recipients? = null,
        val bind: String? = null,
    ) : Effect
    // dsk gaps
    /** "Copy them" (CR 707.12): a copy of each card of [objects], created in the zone the card is in and owned by
     * [owner], the player instructed to create it (CR 112.2a); named [bind] for the "cast the copies" that follows. A
     * copy of a card isn't a card; in any zone other than the Stack or the battlefield it ceases to exist (CR 704.5e). */
    @Serializable @SerialName("copy-card") data class CopyCard(val objects: Objects, val owner: Players, val bind: String) : Effect
    // mh2
    /** "Create a copy of the card with the chosen name" (CR 707.12): a copy of the card named by the name choice bound as [name],
     * a card that needn't be in the game, so the host supplies it; made in exile and owned by [owner], named [bind] for "you may
     * cast the copy". Not cast, it ceases to exist (CR 704.5e). */
    @Serializable @SerialName("copy-named-card") data class CopyNamedCard(val name: String, val owner: Players, val bind: String) : Effect
    /** Copy a spell or ability on the Stack (CR 707.10); [newTargets] lets the copy's controller choose new ones. */
    @Serializable @SerialName("copy-spell") data class CopySpell(
        val objects: Objects, val controller: Players, val newTargets: Boolean, val bind: String? = null,
        // spm
        /** Exceptions to the copy ("copy that spell, except the copy isn't legendary", CR 707.9b, 707.10): part of its copiable
         * values, so the token a permanent spell's copy becomes has them too (CR 707.10c). */
        val except: List<CopyException> = emptyList(),
        // inr chunk 09
        /** "Copy that spell for each other creature they control that the spell could target. Each copy targets a different one of those
         * creatures" (CR 707.10d): one copy for each of these objects, every target of that copy being the object; an object that isn't a legal
         * target for each target of the spell gets no copy. Null: one copy. */
        val forEachCouldTarget: Objects? = null,
    ) : Effect
    /** Counter a spell or ability (CR 701.6); "unless its controller pays" is a [Step.Pay] by that player and a
     * [Step.If] that counters when they didn't (CR 118.12a). */
    @Serializable @SerialName("counter") data class Counter(val objects: Objects) : Effect
    @Serializable @SerialName("add-mana") data class AddMana(val mana: ManaProduction, val players: Players) : Effect

    /** Create a continuous effect (CR 611.2): [mechanics] apply to [affects] for [duration]. It becomes an effect
     * object. Each mechanic that changes characteristics or control locks in its affected objects when created; rule
     * changes keep applying to new objects; numbers, choices ("the colour of your choice") and named objects are fixed
     * at creation (CR 611.2c, 608.2h). Gaining control is [Mechanic.ControlledBy] here. [uses]: a replacement shield that
     * ends after applying that many times ("the next time …"); the effect object counts down. Giant Growth: +3/+3 to
     * the target until end of turn. */
    @Serializable @SerialName("apply") data class ApplyMechanics(
        /** Null for mechanics with their own scope (prevention, replacement, cost change, "damage can't be prevented":
         * Fog, "spells cost {1} more this turn"); they change the rules of the game (CR 611.2c). */
        val mechanics: List<Mechanic>, val affects: Affected?, val duration: Duration, val uses: Int? = null,
    ) : Effect
    /** "Prevent the next [amount] damage that would be dealt to [to] this turn" (CR 615.7): a shield effect object
     * whose amount goes down by the damage it prevents. [from] limits the sources ("by a source of your choice"). */
    @Serializable @SerialName("prevent-damage") data class PreventDamage(
        val to: Recipients, val amount: Value, val from: ObjectFilter, val duration: Duration,
    ) : Effect
    /** Remove [objects] from combat (CR 506.4). */
    @Serializable @SerialName("remove-from-combat") data class RemoveFromCombat(val objects: Objects) : Effect
    /** Remove all damage marked on [objects]. */
    @Serializable @SerialName("remove-damage") data class RemoveAllDamage(val objects: Objects) : Effect
    /** "At the beginning of the next end step, …" (CR 603.7): a trigger created now, an effect object. It triggers once
     * (the next time its event happens), ending unused when its [duration] ends ("the next instant you cast this turn");
     * with [repeats], every time during [duration] ("this turn, whenever …", CR 603.7b). The objects it refers to are
     * fixed when it is created (CR 603.7c), as are announced values and earlier binds; values it computes are read when
     * it resolves (608.2h). */
    @Serializable @SerialName("delayed-trigger") data class CreateDelayedTrigger(
        val trigger: Trigger, val instructions: Instructions, val duration: Duration? = null, val repeats: Boolean = false,
        // eoe
        /** Triggers only in a later turn than the one it was created in ("at the beginning of the end step on your next turn"). */
        val laterTurn: Boolean = false,
    ) : Effect
    /** Unattach [objects] from what they are attached to (CR 701.3d). */
    @Serializable @SerialName("unattach") data class Unattach(val objects: Objects) : Effect
    /** Roll a [sides]-sided die [count] times; [bind] names the result (read with [Value.Bound]). Answered by the host
     * (D25). */
    @Serializable @SerialName("roll-die") data class RollDie(val sides: Int, val count: Int, val players: Players, val bind: String? = null) : Effect
    /** Flip a coin; [bind] names whether it was won ([Condition.WonFlip]). Answered by the host (D25). */
    @Serializable @SerialName("flip-coin") data class FlipCoin(
        val players: Players, val bind: String? = null,
        // mh2
        /** Flip this many coins at once ("Flip that many coins", CR 705); null: one. [bind] then names all of them, read with
         * [Value.FlipsWon] and [Value.FlipsLost]. */
        val count: Value? = null,
    ) : Effect
    /** Exchange control of [first] and [second] (CR 701.12); nothing happens unless both can be exchanged (701.12a). */
    @Serializable @SerialName("exchange-control") data class ExchangeControl(val first: Objects, val second: Objects) : Effect
    /** Move [amount] counters of [kind] (any kind when null) from [from] onto [to]. */
    @Serializable @SerialName("move-counters") data class MoveCounters(
        val from: Objects, val to: Objects, val kind: CounterKind?, val amount: Value,
        // ltr chunk 03
        /** Kinds of counter already on these objects aren't moved ("a counter of each kind not on Goldberry", CR 122.5). */
        val exceptKindsOn: Objects? = null,
        /** The effect's controller chooses which counters on [from] to move, at least [amount] of them in all ("Move one or more
         * counters from …"); [amount] is then that minimum. */
        val chosen: Boolean = false,
        /** Names the number of counters moved ("If you do": at least one, CR 608.2c). */
        val bind: String? = null,
    ) : Effect
    /** "Change the target of target spell or ability" / "you may choose new targets for it" (CR 115.7). */
    @Serializable @SerialName("change-targets") data class ChangeTargets(
        val objects: Objects, val chooser: Players, val optional: Boolean,
        /** The new target when the text fixes it ("change a target of target spell or ability to this creature", CR 115.7b). */
        val to: Objects? = null,
        // mh2
        /** The new targets are chosen at random among the legal ones ("reselect the targets at random", CR 115.7d); [chooser] doesn't choose. */
        val atRandom: Boolean = false,
        /** What the new targets can't be ("The new targets can't be you or a permanent you control"); null: no such limit. */
        val notTo: RecipientFilter? = null,
    ) : Effect
    /** "[separator] separates [objects] into two piles; [chooser] chooses one" (Fact or Fiction style); [chosenBind] and
     * [otherBind] name the piles. */
    @Serializable @SerialName("two-piles") data class SeparatePiles(
        val objects: Objects, val separator: Players, val chooser: Players, val chosenBind: String? = null, val otherBind: String? = null,
        // hob
        /** One pile is face down and one face up ("separate them into a face-down pile and a face-up pile"): only the face-up
         * pile's cards are shown to the chooser and the other players. */
        val faceDownPile: Boolean = false,
    ) : Effect
    /** Keep [objects] on the source as card memory under [key] (CR 607 linked abilities: "exiled with ~"). */
    /** Keep [objects] as card memory of the source under [key], added to what is already kept there ("exiled with ~"
     * covers every card it ever exiled, CR 607.2a); [replace] starts over. */
    @Serializable @SerialName("remember") data class Remember(val key: String, val objects: Objects, val replace: Boolean = false) : Effect
    /** Forget [objects] under [key], or everything kept there when null. */
    @Serializable @SerialName("forget") data class Forget(val key: String, val objects: Objects? = null) : Effect

    @Serializable @SerialName("extra-turn") data class ExtraTurn(val players: Players) : Effect
    // fin
    /** "End the turn" (CR 724.1): every spell and ability on the Stack is exiled (this one included), then the turn goes on from its cleanup
     * step (CR 514), with no other phases or steps in between. */
    @Serializable @SerialName("end-turn") data object EndTurn : Effect
    /** "There is an additional [step] after this step" (CR 500.9): the step happens again right after the current one. */
    @Serializable @SerialName("additional-step") data class AdditionalStep(val step: TurnStep) : Effect
    /** An additional combat phase after this one, followed by an additional main phase when [followedByMain] (CR 500.8). */
    @Serializable @SerialName("extra-combat") data class ExtraCombat(
        val untapAttackers: Boolean = false, val followedByMain: Boolean = false,
        // tla
        /** Only creatures matching this can attack during that combat phase ("Only land creatures can attack during that combat
         * phase"); null: any. */
        val onlyAttackers: ObjectFilter? = null,
    ) : Effect
    @Serializable @SerialName("exchange-life") data class ExchangeLife(val first: Players, val second: Players) : Effect
    // inr
    /** "Exchange target opponent's life total with this creature's toughness" (CR 701.12g): [player]'s life total becomes
     * [creature]'s [property] ([NumericProperty.Power] or [NumericProperty.Toughness]), gained or lost, and a continuous
     * effect sets that [property] to the player's previous life total (layer 7b, CR 613.4b), for as long as the creature
     * stays. Nothing happens unless both are there (CR 701.12a). */
    @Serializable @SerialName("exchange-life-with-stat") data class ExchangeLifeWithStat(
        val player: Players, val creature: Objects, val property: NumericProperty,
    ) : Effect
    /** [mechanics] apply to the spell [objects] and to the permanent it becomes (CR 400.7d): "that creature enters with
     * two additional +1/+1 counters", "it can't be countered". Inside [mechanics], [Objects.Affected] is the spell and the
     * permanent it becomes; values are fixed when this effect happens (CR 608.2h). Mana riders use the same idea
     * ([ManaProduction.spellGets]). */
    @Serializable @SerialName("spell-gets") data class SpellGets(
        val objects: Objects, val mechanics: List<Mechanic>,
        /** How long it lasts ("until you lose control of the spell or the permanent it becomes"); null: for good. */
        val duration: Duration? = null,
    ) : Effect
    @Serializable @SerialName("win") data class WinGame(val players: Players) : Effect
    @Serializable @SerialName("lose") data class LoseGame(val players: Players) : Effect
    /** [caster] casts [objects] now ([how] play: a land is played instead, CR 305.1), without paying their mana cost or by paying
     * [cost]; [bind] names the resulting spell ("If that spell would be put into your graveyard, exile it instead"). */
    @Serializable @SerialName("cast") data class Cast(
        val objects: Objects, val how: PlayKind, val withoutPaying: Boolean = false, val cost: Cost? = null,
        val bind: String? = null, val caster: Players,
        /** Only if the resulting spell matches ("if the resulting spell's mana value is less", cascade, CR 702.85a). */
        val resultingSpell: ObjectFilter? = null,
        /** "Cast it transformed": a transforming double-faced card cast with its back face up (a Siege, CR 310.12b, 712.11a). */
        val transformed: Boolean = false,
        // fin
        /** "and mana of any type can be spent to cast that spell" (CR 601.2h). */
        val manaAsAnyType: Boolean = false,
    ) : Effect
    /** "When you do, …" (CR 603.12): a reflexive triggered ability, put on the Stack as its own item, targets chosen
     * then. Placed inside a [Step.If] on what was done. It triggers once per time the action is taken, but only once
     * for a cost paid several times (603.12a). */
    @Serializable @SerialName("reflexive-trigger") data class ReflexiveTrigger(val instructions: Instructions) : Effect
    /** "Reveal cards from the top of [library]'s library until you reveal [count] cards matching [filter]" (cascade,
     * discover too): [foundBind] names the matching cards, [restBind] the other cards turned over. [into]: each card is
     * put there as it is turned over ("exile cards from the top until you exile a nonland card"); null: revealed in
     * place ([reveal] false: looked at only by the player). */
    @Serializable @SerialName("reveal-until") data class RevealUntil(
        val filter: ObjectFilter, val library: Players, val count: Value,
        val reveal: Boolean, val into: Zone? = null, val foundBind: String? = null, val restBind: String? = null,
        // sos
        /** Instead of [count] matching cards: until the matching cards turned over have [Total.property] adding up to at least
         * [Total.atLeast] ("exile cards from the top of your library until you exile cards with total mana value 4 or greater"); every
         * card turned over is then found ([foundBind]), [count] isn't used. */
        val totalAtLeast: Total? = null,
    ) : Effect
    /** Create an emblem (CR 114) with [abilities] in the command zone of [players]; "you" in them is its owner, and the
     * names and choices they use are fixed when it is created. */
    /** Pair [objects] (one object) with one of [with] its controller chooses (soulbond, CR 702.95a): only creatures on the battlefield
     * under the controller of the ability, both unpaired; otherwise neither becomes paired (702.95c). */
    @Serializable @SerialName("pair") data class Pair(val objects: Objects, val with: Objects) : Effect
    /** "Exile [card] haunting [creature]" (haunt, CR 702.55a, b): the card, in a graveyard, is exiled and haunts the creature, kept as
     * the exiled card's memory under the haunt keyword's id ("the creature it haunts" is [Objects.LinkedTo] haunt). */
    @Serializable @SerialName("haunt") data class Haunt(val card: Objects, val creature: Objects) : Effect
    @Serializable @SerialName("emblem") data class CreateEmblem(val abilities: List<Ability>, val players: Players) : Effect
    /** A keyword action; [bind] names what it acted on ("the beheld card", "the evidence collected"). */
    @Serializable @SerialName("keyword-action") data class Action(val action: KeywordAction, val bind: String? = null) : Effect
    /** [objects]'s class level becomes [level] (CR 716.2a); a level is a designation any permanent can have (716.2b). */
    @Serializable @SerialName("set-class-level") data class SetClassLevel(val objects: Objects, val level: Int) : Effect
    /** "Unlock a locked door of [objects]" (CR 709.5f): for each of [objects] with a locked half, [chooser] chooses one of
     * its locked halves (or [door] only, when given: the unlock special action of CR 709.5e) and it gets that half's
     * unlocked designation ([ObjectMark.Unlocked]); [bind] names the permanents unlocked. Unlocking triggers "when you
     * unlock this door" (709.5h). */
    @Serializable @SerialName("unlock-door") data class UnlockDoor(
        val objects: Objects, val chooser: Players, val door: Door? = null, val bind: String? = null,
    ) : Effect
    /** "Lock a door of [objects]" (CR 709.5g): [chooser] chooses an unlocked half of each and it loses that half's
     * unlocked designation. */
    @Serializable @SerialName("lock-door") data class LockDoor(val objects: Objects, val chooser: Players) : Effect
    /** Put on each of [objects] the counters [of] has: every kind, as many of each ("put those counters on up to one
     * target creature"), read as [of] last existed when it has left (CR 608.2h). Nothing is removed from [of]; moving
     * counters is [MoveCounters]. */
    @Serializable @SerialName("put-counters-of") data class PutCountersOf(val objects: Objects, val of: Objects) : Effect
    // ltr chunk 11
    /** Put on each of [objects] one counter of each kind the triggering event put ("put one of each of those kinds of counters on up to
     * one other target creature", CR 122.1). */
    @Serializable @SerialName("put-event-counter-kinds") data class PutEventCounterKinds(val objects: Objects) : Effect
    // msh
    /** For each kind of counter on each of [recipients], give it another counter of that kind ("For each kind of counter on
     * target permanent or player, give that permanent or player another counter of that kind"; as proliferate does for
     * each permanent and player chosen, CR 701.34a). */
    @Serializable @SerialName("one-more-of-each-kind") data class OneMoreCounterOfEachKind(val recipients: Recipients) : Effect
    /** "Increase your speed by [amount]" (CR 702.179c): a player with no speed gets that speed; speed is at most 4. */
    @Serializable @SerialName("increase-speed") data class IncreaseSpeed(val players: Players, val amount: Value) : Effect
    // dft
    /** "Reduce [players]'s speed by [amount]. This effect can't reduce their speed below [atLeast]." (CR 702.179): a player with no speed
     * keeps none, and one already at or below [atLeast] keeps their speed. */
    @Serializable @SerialName("reduce-speed") data class ReduceSpeed(val players: Players, val amount: Value, val atLeast: Int) : Effect
    /** [controller] controls [players] during their next turn: makes every decision they could make, seeing what they could see
     * (CR 723.1, 723.2; "you control target opponent during their next turn"). */
    @Serializable @SerialName("control-next-turn") data class ControlNextTurn(
        val players: Players, val controller: Players,
        // tla
        /** Only during their next combat phase ("you control target opponent during their next combat phase", CR 723.2). */
        val combatPhase: Boolean = false,
        // inr
        /** "After that turn, that player takes an extra turn" (CR 500.7): the extra turn comes directly after the controlled turn. */
        val thenExtraTurn: Boolean = false,
    ) : Effect
}

/** Who or what receives damage or an attachment: objects, players, or both; [target] is everything chosen for that
 * target slot, objects and players ("any target"). */
@Serializable
data class Recipients(val objects: Objects? = null, val players: Players? = null, val target: String? = null)

/** Where [Effect.Move] puts things (CR 400.7): the owner's zone of that kind. */
@Serializable
data class Destination(
    val zone: Zone,
    /** Library position counted from 0 at the top ("third from the top" = 2); -1 = bottom. */
    val libraryPosition: Int? = null,
    val tapped: Boolean = false,
    val faceDown: Boolean = false,
    /** Onto the battlefield under this player's control; null = the player putting it there (CR 110.2a). "Under its
     * owner's control" is [Players.OwnerOf]. */
    val controller: Players? = null,
    /** Onto the battlefield attached to … (an Aura returned to the battlefield). */
    val attachedTo: Recipients? = null,
    /** Onto the battlefield transformed / with its back face up. */
    val transformed: Boolean = false,
    /** Put into the library in a random order (several cards). */
    val randomOrder: Boolean = false,
    /** Enters the battlefield with these counters ("return it with a -1/-1 counter on it", CR 122.6). */
    val counters: List<CountersToPut> = emptyList(),
    /** Onto the battlefield attacking ([attacking] = what it attacks; null = not attacking). Not declared as an
     * attacker, so "whenever attacks" doesn't trigger (CR 508.4). */
    val attacking: AttackTarget? = null,
    // dsk gaps
    /** What the instruction says the permanent is as it enters ("return it to the battlefield … It's an enchantment."):
     * a continuous effect that applies as it enters the battlefield, not after (CR 611.2e), so its enters triggers and
     * state-based actions see it. */
    val entersAs: List<Mechanic> = emptyList(),
)

/** [amount] counters of [kind]. */
@Serializable
data class CountersToPut(val kind: CounterKind, val amount: Value)

/** What token to create (CR 111). */
@Serializable
sealed interface TokenSpec {
    /** A token described by its characteristics ("a 1/1 white Soldier creature token with flying"). Its name, when the
     * text gives none, is its subtypes plus "Token" ("Soldier Token", CR 111.4); its colour is written as a colour
     * indicator. [power]/[toughness] set computed values ("an X/X Ooze token"), fixed when created and part of its
     * copiable values (CR 707.2). */
    @Serializable @SerialName("defined") data class Defined(val face: FaceDefinition, val power: Value? = null, val toughness: Value? = null) : TokenSpec
    /** A predefined token (CR 111.10) by name; [PredefinedTokens] holds the definitions it knows. */
    @Serializable @SerialName("predefined") data class Predefined(val name: String) : TokenSpec
    /** A token that's a copy of [objects] (CR 707), with the exceptions [except] (CR 707.9b). */
    @Serializable @SerialName("copy") data class CopyOf(val objects: Objects, val except: List<CopyException> = emptyList()) : TokenSpec
}

/** What something put onto the battlefield attacking attacks (CR 508.4). */
@Serializable
sealed interface AttackTarget {
    /** Its controller chooses a player, planeswalker or battle it could attack ("tapped and attacking"). */
    @Serializable @SerialName("chosen") data object Chosen : AttackTarget
    /** The same player, planeswalker or battle [objects] is attacking (ninjutsu, myriad: "attacking that player"). */
    @Serializable @SerialName("same-as") data class SameAs(val objects: Objects) : AttackTarget
    /** These ("attacking that opponent"). */
    @Serializable @SerialName("these") data class These(val recipients: Recipients) : AttackTarget
}

/** What [KeywordAction.Double] doubles. */
@Serializable
sealed interface Doubled {
    @Serializable @SerialName("counters") data class Counters(val objects: Objects, val kind: CounterKind?) : Doubled
    @Serializable @SerialName("life") data class Life(val players: Players) : Doubled
    @Serializable @SerialName("power") data class Power(val objects: Objects) : Doubled
    @Serializable @SerialName("mana") data class Mana(val players: Players) : Doubled
}

/** Keyword actions (CR 701) that are shorthand for other effects; the shared table expands them. Actions with their
 * own effect type above (destroy, scry, mill, fight, …) are not repeated here. */
@Serializable
sealed interface KeywordAction {
    @Serializable @SerialName("investigate") data class Investigate(val count: Value) : KeywordAction
    @Serializable @SerialName("proliferate") data object Proliferate : KeywordAction
    @Serializable @SerialName("populate") data object Populate : KeywordAction
    @Serializable @SerialName("explore") data class Explore(val objects: Objects) : KeywordAction
    @Serializable @SerialName("amass") data class Amass(val subtype: Subtype, val count: Value) : KeywordAction
    @Serializable @SerialName("connive") data class Connive(val objects: Objects, val count: Value) : KeywordAction
    @Serializable @SerialName("discover") data class Discover(val count: Value) : KeywordAction
    /** "Venture into the dungeon" (CR 701.49a–c). */
    @Serializable @SerialName("venture") data object Venture : KeywordAction
    /** "Venture into [quality]" (CR 701.49d: "venture into Undercity"): a new dungeon is one of [dungeons]. */
    @Serializable @SerialName("venture-into") data class VentureInto(val dungeons: ObjectFilter) : KeywordAction
    @Serializable @SerialName("manifest") data class Manifest(val count: Value) : KeywordAction
    /** "[Players] manifest(s) dread" (CR 701.62a): look at the top two cards of their library, manifest one of them
     * (CR 701.40a: onto the battlefield face down as a 2/2 creature, [ObjectMark.Manifested]) and put the other into
     * their graveyard. The bind of [Effect.Action] names the manifested permanent ("that creature"). Triggers "whenever
     * you manifest dread" once it is done (701.62b). */
    @Serializable @SerialName("manifest-dread") data class ManifestDread(val players: Players) : KeywordAction
    @Serializable @SerialName("monstrosity") data class Monstrosity(val count: Value) : KeywordAction
    @Serializable @SerialName("adapt") data class Adapt(val count: Value) : KeywordAction
    @Serializable @SerialName("goad") data class Goad(val objects: Objects) : KeywordAction
    @Serializable @SerialName("learn") data object Learn : KeywordAction
    /** Double counters, life, power, or mana (CR 701.10). */
    @Serializable @SerialName("double") data class Double(val what: Doubled) : KeywordAction
    @Serializable @SerialName("bolster") data class Bolster(val count: Value) : KeywordAction
    /** "Support N" (CR 701.39a, b): put a +1/+1 counter on each of up to N (other, on a permanent) target creatures, the targets of
     * [slot] declared in the enclosing [Instructions.targets] (at most [count]). [KeywordExpansions.expand] writes it out. */
    @Serializable @SerialName("support") data class Support(val count: Value, val slot: String) : KeywordAction
    @Serializable @SerialName("detain") data class Detain(val objects: Objects) : KeywordAction
    /** [objects] endure [count] (CR 701.63a): each one's controller creates a [count]/[count] white Spirit creature token unless they put
     * [count] +1/+1 counters on it; enduring 0 does nothing (701.63b). tdm: "Anafenza endures 2", "it endures X" (the creature that
     * entered). */
    @Serializable @SerialName("endure") data class Endure(val objects: Objects, val count: Value) : KeywordAction
    /** "Exile three cards from your graveyard or sacrifice a Food" (CR 701.61a). */
    @Serializable @SerialName("forage") data object Forage : KeywordAction
    /** Exile this card from its owner's hand face down; it becomes foretold: what the foretell special action does once its {2} is paid (CR 702.143a, b). */
    @Serializable @SerialName("foretell") data object Foretell : KeywordAction
    @Serializable @SerialName("cloak") data class Cloak(val count: Value) : KeywordAction
    @Serializable @SerialName("time-travel") data object TimeTravel : KeywordAction
    @Serializable @SerialName("ring-tempts") data object TheRingTemptsYou : KeywordAction
    /** Exert [objects]: they don't untap during their controller's next untap step (CR 701.43). */
    @Serializable @SerialName("exert") data class Exert(val objects: Objects) : KeywordAction
    /** Behold [count] [filter]s: reveal them from your hand and/or choose them among permanents you control, each a different
     * object (CR 701.4a; ecl: "Behold three Elementals"). */
    @Serializable @SerialName("behold") data class Behold(val filter: ObjectFilter, val count: Value) : KeywordAction
    /** Exile cards from your graveyard with total mana value [amount] or more (CR 701.59a). */
    @Serializable @SerialName("collect-evidence") data class CollectEvidence(val amount: Value) : KeywordAction
    /** "Earthbend N": target land you control becomes … (CR 701.66a). Keyword actions that target name a [slot]
     * declared in the enclosing [Instructions.targets], one per use. */
    @Serializable @SerialName("earthbend") data class Earthbend(val amount: Value, val slot: String) : KeywordAction
    /** "The next time [objects] would be destroyed this turn, instead remove all damage marked on it and its controller
     * taps it. If it's an attacking or blocking creature, remove it from combat" (CR 701.19a): a one-use shield effect
     * object. */
    @Serializable @SerialName("regenerate") data class Regenerate(val objects: Objects) : KeywordAction
    // fra
    /** "Empower Jace N" (CR 701.71a): if you don't control a Jace planeswalker token, create a blue Jace planeswalker token
     * with 0 loyalty, "[−1]: Surveil 1" and "[−3]: Draw a card"; then choose a Jace planeswalker token you control and put
     * [count] loyalty counters on it. [KeywordExpansions.expand] writes it out. */
    @Serializable @SerialName("empower-jace") data class EmpowerJace(val count: Value) : KeywordAction
    // hob
    /** "Recruit" (CR 701.70a): draw a card, then discard a card; if a nonland card was discarded this way, create a 1/1 white
     * Human Soldier creature token. [KeywordExpansions.expand] writes it out. */
    @Serializable @SerialName("recruit") data object Recruit : KeywordAction
    // tla
    /** "Airbend [objects]" (CR 701.65a): exile those permanents and/or spells; for each card exiled this way, for as long as it
     * remains exiled, its owner may cast it by paying {2} rather than paying its mana cost. The bind of [Effect.Action] names the
     * exiled cards. Triggers "whenever you airbend" once for the instruction (701.65b). */
    @Serializable @SerialName("airbend") data class Airbend(val objects: Objects) : KeywordAction
    // fin
    /** "Meld them into [into]" (CR 701.42a): the two cards of [objects], a meld pair, are put onto the battlefield with their back faces up and
     * combined as one permanent (CR 712.4): [into], the back face both cards share. If they can't be melded, they stay where they are (701.42c). */
    @Serializable @SerialName("meld") data class Meld(val objects: Objects, val into: String) : KeywordAction
    // ecl
    /** "[Players] blight(s) N" (CR 701.68a): each of [players] puts [count] -1/-1 counters on a creature they control. A player who
     * can't do it (no creatures) can't choose to blight (701.68b); the bind of [Effect.Action] or [CostPart.Action] names the creature
     * chosen, "the blighted creature" (701.68c). As a cost, [players] is the payer. */
    @Serializable @SerialName("blight") data class Blight(val players: Players, val count: Value) : KeywordAction
}
