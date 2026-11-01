package arcanum.parser

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone

/**
 * Rules-text lines that aren't keyword lines: activated abilities ("[cost]: [effect]") and the static wordings learned
 * so far. Each wording is one rule; a line no rule reads stays [Ability.Unparsed]. [self] holds the names the card
 * calls itself ("this land", its own name).
 */
internal class Sentences(
    private val self: Set<String>, private val isSpell: Boolean = false,
    /** The card refers to cards "exiled with" itself: its exiling abilities keep what they exile (CR 607.2a). */
    private val linksExiled: Boolean = false,
) {
    /** The card's name or "this land" / "This land" (a sentence may start with it). */
    private fun isSelf(text: String) = text in self || text.replaceFirstChar { it.lowercase() } in self

    fun line(line: String): Ability? {
        // "Channel — [ability]": an ability word, no rules meaning (CR 207.2c). Keywords with a dash ("Ward—{2}") have
        // no spaces around it.
        ABILITY_WORD.matchEntire(line)?.let { m ->
            val word = m.groupValues[1]
            // Keywords that label an ability (CR 702.142, 702.169, 702.177, 702.178, 702.193): they add rules.
            LABELS[word.lowercase()]?.let { label ->
                return when (val ability = line(m.groupValues[2])) {
                    is Ability.Activated -> ability.copy(label = label)
                    is Ability.Triggered -> ability.copy(label = label)
                    is Ability.Static -> ability.copy(label = label)
                    else -> null
                }
            }
            if (word.lowercase() !in RulesWords.abilityWords) return null
            return when (val ability = line(m.groupValues[2])) {
                is Ability.Activated -> ability.copy(abilityWord = word)
                is Ability.Triggered -> ability.copy(abilityWord = word)
                is Ability.Static -> ability.copy(abilityWord = word)
                is Ability.Spell -> ability.copy(abilityWord = word)
                else -> null
            }
        }
        return static(line) ?: triggered(line) ?: activated(line) ?: spell(line)
    }

    /**
     * "Threshold — Add {B}{B}{B}{B}{B} instead if there are seven or more cards in your graveyard.": the spell's last
     * step happens only if the condition is false; the new one if it is true (a conditional "instead", written as If).
     */
    fun insteadOf(line: String, spell: Ability.Spell): Ability.Spell? {
        val m = INSTEAD_IF.matchEntire(line) ?: return null
        if (m.groupValues[1].isNotEmpty() && m.groupValues[1].lowercase() !in RulesWords.abilityWords) return null
        val condition = Conditions.condition(m.groupValues[3], self) ?: return null
        val steps = spell.instructions.steps
        if (steps.isEmpty()) return null
        val last = steps.last()
        // "[This] deals 4 damage instead": the same damage to the same recipients, another amount.
        val damage = SELF_DEALS_N.matchEntire(m.groupValues[2])?.takeIf { isSelf(it.groupValues[1]) }?.let { d ->
            val previous = ((last as? Step.Do)?.effect as? Effect.DealDamage) ?: return null
            listOf(Step.Do(previous.copy(amount = Value.Fixed(d.groupValues[2].toInt()))))
        }
        val replacement = damage ?: instructions(m.groupValues[2] + ".", spell.instructions.targets)?.takeIf { it.targets == spell.instructions.targets }?.steps
            ?: return null
        return spell.copy(
            abilityWord = m.groupValues[1].ifEmpty { null },
            instructions = spell.instructions.copy(steps = steps.dropLast(1) + Step.If(condition, then = replacement, otherwise = listOf(last))),
        )
    }

    /** "Choose one —" with its modes; "Choose three. You may choose the same mode more than once." (CR 700.2) */
    fun modes(choose: MatchResult, modeLines: List<String>): arcanum.api.Modes? {
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it) ?: return null) }
        if (options.isEmpty()) return null
        return arcanum.api.Modes(options, min = n, max = n, repeatable = choose.value.contains("same mode"))
    }

    /** The instructions of an instant or sorcery (CR 113.3a). */
    private fun spell(line: String): Ability? = if (isSpell) instructions(line)?.let { Ability.Spell(it) } else null

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (CR 614.12, 118.12a) */
    private fun static(line: String): Ability? {
        // "[If …, ]you may [cost] rather than pay this spell's mana cost." (CR 118.9)
        ALTERNATIVE_COST.matchEntire(line)?.let { m ->
            val parts = m.groupValues[2].split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten()
            val condition = when (val c = m.groupValues[1]) {
                "" -> null
                "it's not your turn" -> arcanum.api.Condition.Not(arcanum.api.Condition.YourTurn)
                else -> Conditions.condition(c, self) ?: return null
            }
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost(parts))), condition = condition)
        }
        if (line == "This spell can't be countered.") return Ability.Static(listOf(Mechanic.CantBeCountered))
        // "If X is 5 or more, this spell can't be countered and the damage can't be prevented." (CR 107.3m, 615.12)
        X_AT_LEAST_UNCOUNTERABLE.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeCountered, Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self)))),
                condition = arcanum.api.Condition.Compare(Value.CastX(), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())))
        }
        // "As an additional cost to cast this spell, [cost][ or cost]." (CR 601.2f)
        ADDITIONAL_COST.matchEntire(line)?.let { m ->
            val options = m.groupValues[1].split(" or ").map { option ->
                Cost(option.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten())
            }
            val cost = options.singleOrNull() ?: Cost(listOf(CostPart.OneOf(options)))
            return Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost)))
        }
        OTHERS_GET.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))))
        }
        DOESNT_UNTAP.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.DoesntUntap))
        }
        // 'Equipped creature has "[ability]"' (CR 301.5)
        EQUIPPED_HAS_QUOTED.matchEntire(line)?.let { m ->
            val granted = Sentences(setOf("this creature"), false).line(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.AttachedTo()))
        }
        // "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the
        // battlefield. If you don't, put it into its owner's graveyard." (CR 614.1c)
        WOULD_ENTER_DISCARD_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                instead = Instructions(steps = listOf(
                    Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(filter)))), bind = "discarded"),
                    Step.If(arcanum.api.Condition.Did("discarded"),
                        then = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield)))),
                        otherwise = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Graveyard))))))))))
        }
        ENTERS_TAPPED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))))
        }
        AS_ENTERS_PAY_LIFE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Unless(Players.You, Cost(listOf(CostPart.PayLife(Value.Fixed(m.groupValues[2].toInt())))),
                    listOf(Step.Modify(Modification.EntersTapped)))))))))
        }
        // chunk 20: "Equipped creature gets +1/+0 for each artifact you control." (CR 301.5)
        ATTACHED_GETS.matchEntire(line)?.let { m ->
            val per = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Value.Count(Filters.objects(it) ?: return null) }
            fun amount(n: Int): Value = when {
                per == null || n == 0 -> Value.Fixed(n)
                n == 1 -> per
                else -> Value.Times(per, n)
            }
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[1].toInt()), amount(m.groupValues[2].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.AttachedTo()))
        }
        // chunk 21: "You may play lands from your graveyard." (CR 305.1)
        MAY_PLAY_FROM_GRAVEYARD.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val how = if (m.groupValues[1] == "play") arcanum.api.PlayKind.Play else arcanum.api.PlayKind.Cast
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(filter, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 21: "You may spend mana as though it were mana of any color to activate abilities of creatures you control."
        SPEND_AS_ANY_COLOR_TO_ACTIVATE.matchEntire(line)?.let { m ->
            val sources = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = sources))),
                affects = arcanum.api.Affected.Players(Players.You))
        }
        // chunk 21: "[Creatures] have all activated abilities of all creature cards exiled with ~." (CR 607.2a)
        HAVE_ABILITIES_OF_EXILED.matchEntire(line)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val affected = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val cards = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilitiesOf(Objects.Where(Objects.Remembered(EXILED_WITH), cards))),
                affects = arcanum.api.Affected.Objects(Objects.Each(affected)))
        }
        // chunk 21: "This artifact enters with three wish counters on it." (CR 614.1c)
        ENTERS_WITH_COUNTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val n = Numbers.count(m.groupValues[2]) ?: return null
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, Value.Fixed(n))))))
        }
        return null
    }

    /** "[cost]: [effect]" (CR 602.1), with "This ability costs {1} less to activate for each …" as a last sentence. */
    private fun activated(line: String): Ability? {
        val colon = line.indexOf(": ").takeIf { it > 0 } ?: return null
        val cost = cost(line.substring(0, colon)) ?: return null
        var text = line.substring(colon + 2)
        var costChange: Value? = null
        var condition: arcanum.api.Condition? = null
        var timing: arcanum.api.Timing = arcanum.api.Timing.Any
        ACTIVATE_ONLY_AS_INSTANT.find(text)?.let { m ->
            timing = arcanum.api.Timing.Instant
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_IF.find(text)?.let { m ->
            condition = Conditions.condition(m.groupValues[1]) ?: return null
            text = text.removeRange(m.range).trim()
        }
        // chunk 21
        ACTIVATE_ONLY_AS_SORCERY.find(text)?.let { m ->
            timing = arcanum.api.Timing.Sorcery
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_DURING_YOUR_TURN.find(text)?.let { m ->
            condition = arcanum.api.Condition.YourTurn
            text = text.removeRange(m.range).trim()
        }
        COSTS_LESS_FOR_EACH.find(text)?.let { m ->
            val per = Numbers.mana(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            costChange = Value.Times(Value.Count(count), -per)
            text = text.removeRange(m.range).trim()
        }
        val instructions = instructions(text)?.let { i ->
            if (cost.parts.any { (it as? CostPart.Mana)?.cost?.symbols?.any { s -> s is arcanum.api.ManaSymbol.Variable } == true })
                i.copy(announced = listOf("X")) else i
        } ?: return null
        // chunk 20: "the sacrificed creature's power" reads the sacrificed object as it last was (CR 608.2h)
        val named = if (!text.contains("the sacrificed ")) cost else cost.copy(parts = cost.parts.map {
            if (it is CostPart.Sacrifice && it.what is CostObjects.Choose) it.copy(preBind = SACRIFICED) else it
        })
        return Ability.Activated(named, instructions, arcanum.api.ActivationRules(timing = timing, costChange = costChange, condition = condition))
    }

    /** "Whenever [event], [if condition,] [effect]." (CR 603.1) */
    private fun triggered(line: String): Ability? {
        val m = WHENEVER.matchEntire(line) ?: return null
        val (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        var rest = m.groupValues[3]
        // chunk 21: "This ability triggers only once each turn."
        var perTurn: Int? = null
        TRIGGERS_ONLY_ONCE.find(rest)?.let { once ->
            perTurn = 1
            rest = rest.removeRange(once.range).trim()
        }
        val conditional = INTERVENING_IF.matchEntire(rest)
        val interveningIf = conditional?.let { Conditions.condition(it.groupValues[1], self) ?: return null }
        val instructions = instructions(conditional?.groupValues?.get(2) ?: rest, that = that(trigger)) ?: return null
        return Ability.Triggered(trigger, instructions, interveningIf = interveningIf, perTurn = perTurn, triggersOnlyIf = onlyIf)
    }

    /** "Whenever [event], choose one that hasn't been chosen this turn —" with its "• " modes (CR 700.2). */
    // chunk 21
    fun modalTrigger(header: String, modeLines: List<String>): Ability? {
        val m = WHENEVER.matchEntire(header) ?: return null
        val (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        val choose = CHOOSE_MODES.matchEntire(m.groupValues[3]) ?: return null
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it, that = that(trigger)) ?: return null) }
        if (options.isEmpty()) return null
        val unchosen = choose.groupValues[2].isNotEmpty()
        val thisTurn = choose.groupValues[3].isNotEmpty()
        return Ability.Triggered(trigger, Instructions(modes = arcanum.api.Modes(options, min = n, max = n,
            onlyUnchosen = unchosen && !thisTurn, onlyUnchosenThisTurn = thisTurn)), triggersOnlyIf = onlyIf)
    }

    /** A trigger's events. "One or more …" triggers once per batch; "during your turn" is checked as it would trigger. */
    // chunk 21
    private fun trigger(text: String): Pair<arcanum.api.Trigger.On, arcanum.api.Condition?>? {
        val during = DURING_YOUR_TURN.matchEntire(text)
        val events = during?.groupValues?.get(1) ?: text
        val onlyIf = during?.let { arcanum.api.Condition.YourTurn }
        ONE_OR_MORE_LEAVE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield), oneOrMore = true) to onlyIf
        }
        YOU_CREATE_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            // CR 111.2: the player who creates a token is its owner.
            return arcanum.api.Trigger.On(EventPattern.CreateToken(Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You))), oneOrMore = true) to onlyIf
        }
        return Events.events(events, self)?.let { arcanum.api.Trigger.On(it) to onlyIf }
    }

    /** "That creature", "that card", "that permanent" in a triggered ability: the object of its (first) event. */
    // chunk 20
    private fun that(trigger: arcanum.api.Trigger.On): Objects = when (val event = trigger.events.first()) {
        is EventPattern.ZoneChange -> Objects.EventObject(if (event.to == Zone.Battlefield) arcanum.api.EventRole.After else arcanum.api.EventRole.Before)
        is EventPattern.Discard -> Objects.EventObject(arcanum.api.EventRole.After)
        is EventPattern.Unattach -> Objects.EventObject(arcanum.api.EventRole.From)
        else -> Objects.EventObject()
    }

    /** Cost parts separated by ", " (CR 602.1a). */
    fun cost(text: String): Cost? {
        val parts = text.split(", ").map { costPart(it) ?: return null }
        if (parts.isEmpty()) return null
        return Cost(parts.flatten())
    }

    private fun costPart(text: String): List<CostPart>? {
        if (text == "{T}") return listOf(CostPart.TapSelf)
        ManaCost.parse(text)?.let { return listOf(CostPart.Mana(it)) }
        PAY_LIFE.matchEntire(text)?.let { return listOf(CostPart.PayLife(Value.Fixed(it.groupValues[1].toInt()))) }
        DISCARD_COST.matchEntire(text)?.let { m ->
            if (isSelf(m.groupValues[1])) return listOf(CostPart.Discard(CostObjects.Self))
            if (m.groupValues[1] == "your hand") return listOf(CostPart.Discard(CostObjects.These(Objects.Each(yourHand))))
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.Discard(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        // "Tap two untapped Elves you control": tapping as a cost can only tap untapped permanents you control
        // (CR 602.2b, 701.26a), so the canonical form names only the kind (as crew does).
        TAP_UNTAPPED.matchEntire(text)?.let { m ->
            val count = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2].removeSuffix(" you control")) ?: return null
            return listOf(CostPart.TapObjects(CostObjects.Choose(filter, count = Value.Fixed(count))))
        }
        PUT_COUNTER_ON_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.PutCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))
        }
        RETURN_TO_HAND_COST.matchEntire(text)?.let { m ->
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.ReturnToHand(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        EXILE_FROM_HAND_COST.matchEntire(text)?.let { m ->
            return listOf(CostPart.Exile(CostObjects.Choose(Filters.noun(m.groupValues[1]) ?: return null), from = Zone.Hand))
        }
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            return listOf(CostPart.Sacrifice(if (isSelf(what)) CostObjects.Self else CostObjects.Choose(Filters.noun(what.removeArticle()) ?: return null)))
        }
        // chunk 21: "Remove two charge counters from this artifact"
        REMOVE_COUNTERS_FROM_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.RemoveCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))
        }
        return null
    }

    /** "a card" → 1 and "card"; "two cards" → 2 and "cards". */
    private fun counted(phrase: String): Pair<Int, String>? {
        val first = phrase.substringBefore(' ')
        val count = if (first == "a" || first == "an") 1 else Numbers.count(first) ?: return 1 to phrase
        return count to phrase.substringAfter(' ')
    }

    /** The effect sentences of a spell or ability, in order. */
    fun instructions(text: String, seedTargets: List<TargetSlot> = emptyList(), that: Objects? = null): Instructions? {
        val context = Context()
        context.targets += seedTargets
        context.that = that
        val steps = mutableListOf<Step>()
        for (sentence in sentences(text)) {
            val modified = modify(sentence, steps, context)
            if (modified != null) { steps.clear(); steps += modified; continue }
            steps += effect(sentence, context) ?: return null
        }
        return Instructions(targets = context.targets, steps = steps, replacements = context.replacements)
    }

    /** Sentences that change the step before them ("If this spell was kicked, it deals 4 damage instead.", "It can't be
     * regenerated."). */
    private fun modify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        val last = steps.lastOrNull() as? Step.Do ?: return null
        val effect = last.effect
        if (effect is Effect.DealDamage) {
            KICKED_DEALS_INSTEAD.matchEntire(sentence)?.let { m ->
                return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind),
                    then = listOf(Step.Do(effect.copy(amount = Value.Fixed(m.groupValues[1].toInt())))), otherwise = listOf(last))
            }
            if (sentence == "A creature dealt damage this way can't be regenerated this turn.") {
                val damaged = effect.to.target?.let { Objects.Target(it) } ?: effect.to.objects ?: return null
                return steps + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeRegenerated),
                    arcanum.api.Affected.Objects(Objects.Where(damaged, ObjectFilter.Type(arcanum.api.CardType.Creature))), arcanum.api.Duration.EndOfTurn))
            }
        }
        if (effect is Effect.Destroy && sentence in setOf("It can't be regenerated.", "A creature destroyed this way can't be regenerated.")) {
            return steps.dropLast(1) + Step.Do(effect.copy(noRegeneration = true))
        }
        // chunk 20: "If the sacrificed creature's toughness was 4 or greater, create two Food tokens instead."
        IF_INSTEAD.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[1], self) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return steps.dropLast(1) + Step.If(condition, then = then, otherwise = listOf(last))
        }
        return null
    }

    private class Context {
        var flip = false
        // chunk 20: the event's object in a trigger ("that creature"), and the object an earlier sentence moved ("it")
        var that: Objects? = null
        var it: Objects? = null
        val replacements = mutableListOf<Mechanic.Replacement>()
        val targets = mutableListOf<TargetSlot>()
        /** "any target" (CR 115.4): creature, planeswalker or battle, or player. */
        fun anyTarget() {
            targets += TargetSlot("any", arcanum.api.TargetKind.Either(ObjectFilter.Or(listOf(
                ObjectFilter.Type(arcanum.api.CardType.Creature), ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                ObjectFilter.Type(arcanum.api.CardType.Battle))), PlayerFilter.Any))
        }

        /** "target player" */
        fun targetPlayer(): Players {
            if (targets.none { it.name == "player" }) targets += TargetSlot("player", arcanum.api.TargetKind.Players(PlayerFilter.Any))
            return Players.Target("player")
        }

        fun target(noun: String, filter: ObjectFilter): Objects {
            targets += TargetSlot(noun, TargetKind.Objects(filter))
            return Objects.Target(noun)
        }

        /** "That player": the controller of the last object target. */
        fun lastTargetController(): Players? = targets.lastOrNull()?.let {
            if (it.what is arcanum.api.TargetKind.Players) Players.Target(it.name) else Players.ControllerOf(Objects.Target(it.name))
        }
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
        // "Draw two cards. Then discard a card …": a sentence that starts with "Then" follows the one before it.
        if (sentence.startsWith("Then ")) return effect(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() }, context)
        PUT_FROM_HAND_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n, n), bind = "put"),
                Step.Do(Effect.Move(Objects.Bound("put"), Destination(Zone.Library, libraryPosition = 0))))
        }
        PLAYERS_DRAW.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(players, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))
            if (m.groupValues[3].isNotEmpty())
                steps += Step.Do(Effect.Discard(players, Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null), random = m.groupValues[4].isNotEmpty()))
            if (m.groupValues[5].isNotEmpty()) steps += Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[5].toInt())))
            return steps
        }
        EACH_PLAYER_DISCARDS_HAND_DRAWS.matchEntire(sentence)?.let { m ->
            val each = Players.Each(PlayerFilter.Any)
            return listOf(
                Step.Do(Effect.Discard(each, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)))),
                Step.Do(Effect.Draw(each, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD_UNLESS_ATTACKED.matchEntire(sentence)?.let { m ->
            val attacked = arcanum.api.Condition.HappenedThisTurn(arcanum.api.EventPattern.Attack(attacker = ObjectFilter.ControlledBy(PlayerFilter.You)))
            return listOf(Step.If(arcanum.api.Condition.Not(attacked),
                then = listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))))
        }
        SCRY_OR_SURVEIL.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            return listOf(Step.Do(if (m.groupValues[1] == "Scry") Effect.Scry(Players.You, n) else Effect.Surveil(Players.You, n)))
        }
        SEARCH_TO_TOP.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card").ifEmpty { "card" }) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found")),
                Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Library, libraryPosition = 0))))
        }
        SEARCH_PUT_SHUFFLE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card")) ?: return null
            val to = if (m.groupValues[2] == "into your hand") Destination(Zone.Hand) else Destination(Zone.Battlefield)
            val steps = mutableListOf<Step>(
                Step.Do(Effect.Search(Players.You, filter, bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), to)))
            if (m.groupValues[3].isNotEmpty()) steps += Step.Do(Effect.Discard(Players.You, random = true))
            steps += Step.Do(Effect.Shuffle(Players.You))
            return steps
        }
        if (sentence == "Look at target player's hand.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.LookAt(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))))))
        }
        TARGET_PLAYER_MILLS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        LOOK_PUT_BACK.matchEntire(sentence)?.let { m ->
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)), bind = "seen")),
                Step.Do(Effect.Reorder(Objects.Bound("seen"))))
        }
        if (sentence == "You may shuffle.") return listOf(Step.May(listOf(Step.Do(Effect.Shuffle(Players.You)))))
        if (sentence == "Investigate.") return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Investigate())))
        TARGET_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        RETURN_UP_TO_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            context.targets += TargetSlot("cards", TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
            return listOf(Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Hand))))
        }
        YOU_LOSE_LIFE.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.LoseLife(Players.You, Value.Fixed(m.groupValues[1].toInt())))) }
        SELF_DEALS_DAMAGE_TO_EACH.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val amount = Value.Fixed(m.groupValues[2].toInt())
            val to = Filters.players(m.groupValues[3])?.let { arcanum.api.Recipients(players = it) }
                ?: arcanum.api.Recipients(objects = Objects.Each(Filters.noun(m.groupValues[3].removePrefix("each ")) ?: return null))
            return listOf(Step.Do(Effect.DealDamage(to, amount)))
        }
        EXILE_THEN_RETURN.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(
                Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))
        }
        TARGET_PLAYER_DRAWS.matchEntire(sentence)?.let { m ->
            val player = context.targetPlayer()
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(player, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
            if (m.groupValues[2].isNotEmpty()) steps += Step.Do(Effect.Discard(player, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)))
            return steps
        }
        TARGET_PLAYER_DISCARDS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        TARGET_PLAYER_CREATES.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = context.targetPlayer())))
        }
        RETURN_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Hand))))
        }
        EACH_PLAYER_SHUFFLES_HAND_AND_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val each = Players.Each(PlayerFilter.Any)
            return listOf(
                Step.Do(Effect.Move(Objects.Each(ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.InZone(Zone.Graveyard)))),
                    Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(each)),
                Step.Do(Effect.Draw(each, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        EXILE_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile))))
        }
        LOOK_PUT_N_INTO_HAND_REST_BOTTOM.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            val kept = Numbers.count(m.groupValues[2]) ?: return null
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen)), bind = "seen")),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(kept), Value.Fixed(kept), from = Objects.Bound("seen")), bind = "kept"),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1))))
        }
        LOOK_HAND_BOTTOM_EXILE.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            fun one(from: Objects, bind: String) = Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = from), bind = bind)
            val rest = Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand"))
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen)), bind = "seen")),
                one(Objects.Bound("seen"), "toHand"),
                Step.Do(Effect.Move(Objects.Bound("toHand"), Destination(Zone.Hand))),
                one(rest, "toBottom"),
                Step.Do(Effect.Move(Objects.Bound("toBottom"), Destination(Zone.Library, libraryPosition = -1))),
                Step.Do(Effect.Move(Objects.Except(rest, Objects.Bound("toBottom")), Destination(Zone.Exile), postBind = "exiled")))
        }
        if (sentence == "You may play the exiled card this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        SELF_DEALS_DAMAGE_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val amount = if (m.groupValues[2] == "X") Value.Announced() else Value.Fixed(m.groupValues[2].toInt())
            val to = when (val what = m.groupValues[3]) {
                "any target" -> { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                else -> {
                    val noun = what.removePrefix("target ").takeIf { what.startsWith("target ") } ?: return null
                    arcanum.api.Recipients(objects = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null))
                }
            }
            return listOf(Step.Do(Effect.DealDamage(to, amount)))
        }
        // chunk 21: "Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact."
        DESTROY_EACH.matchEntire(sentence)?.let { m -> counterNoun(m.groupValues[1])?.let { return listOf(Step.Do(Effect.Destroy(Objects.Each(it)))) } }
        // chunk 21: "Exile target … until this artifact leaves the battlefield." (CR 610.3)
        EXILE_TARGET_UNTIL_LEAVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Exile), untilLeaves = Objects.Self)))
        }
        // chunk 21: "Exile target card from a graveyard."
        EXILE_TARGET_FROM_A_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard)))
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled"))) + remembered(Objects.Bound("exiled"))
        }
        DESTROY_TARGET_IF.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val target = if (m.groupValues[1] == "target") context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
                else context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target,
                ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, Value.Fixed(m.groupValues[3].toInt()))),
                then = listOf(Step.Do(Effect.Destroy(target)))))
        }
        DESTROY_EACH.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Destroy(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null))))
        }
        EXILE_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            val exile = Step.Do(Effect.Move(target, Destination(Zone.Exile)))
            if (m.groupValues[2].isEmpty()) return listOf(exile)
            // Converge: "if its mana value is less than or equal to the number of colors of mana spent to cast this spell"
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.ManaSpent(distinctColors = true))), then = listOf(exile)))
        }
        RETURN_TARGET_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            return listOf(Step.Do(Effect.Move(context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null), Destination(Zone.Hand))))
        }
        ITS_CONTROLLER_GAINS_LIFE_EQUAL_TO_POWER.matchEntire(sentence)?.let {
            val target = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.GainLife(Players.ControllerOf(target),
                Value.Of(arcanum.api.NumericProperty.Power, target, arcanum.api.Combine.Single))))
        }
        (UNTAP_UP_TO.matchEntire(sentence) ?: YOU_UNTAP_UP_TO.matchEntire(sentence))?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(n)), bind = "untapped"),
                Step.Do(Effect.Untap(Objects.Bound("untapped"))))
        }
        COUNTER_TARGET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val spell = context.target(Filters.slotName(m.groupValues[1]), filter)
            val counter = Step.Do(Effect.Counter(spell))
            if (m.groupValues[2].isEmpty()) return listOf(counter)
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return listOf(Step.Unless(Players.ControllerOf(spell), Cost.mana(cost), listOf(counter)))
        }
        COUNTERED_THIS_WAY.matchEntire(sentence)?.let { m ->
            val spell = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            val to = when (m.groupValues[1]) {
                "exile it" -> Destination(Zone.Exile)
                "put it on top of its owner's library" -> Destination(Zone.Library, libraryPosition = 0)
                "put it into its owner's hand" -> Destination(Zone.Hand)
                else -> return null
            }
            // CR 614.15: a replacement of this spell's own effect, applied while it resolves
            context.replacements += Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(spell), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(to)))
            return emptyList()
        }
        NEXT_MAIN_PHASE_ADD_MANA_VALUE.matchEntire(sentence)?.let {
            val spell = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(listOf(EventPattern.StepBegins(arcanum.api.TurnStep.PrecombatMain, PlayerFilter.You),
                    EventPattern.StepBegins(arcanum.api.TurnStep.PostcombatMain, PlayerFilter.You))),
                Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!,
                    times = Value.Of(arcanum.api.NumericProperty.ManaValue, spell, arcanum.api.Combine.Single))))))))))
        }
        ADD_X_ANY_COMBINATION.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Announced(), combination = true)))))
        }
        ADD_ANY_OF_REMEMBERED_COLORS.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1),
                colors = listOf(ColorRef.Of(Objects.Remembered(IMPRINTED))))))))
        }
        FLIP_A_COIN.matchEntire(sentence)?.let {
            context.flip = true
            return listOf(Step.Do(Effect.FlipCoin(bind = "flip")))
        }
        IF_YOU_LOSE_THE_FLIP.matchEntire(sentence)?.let { m ->
            if (!context.flip) return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.WonFlip("flip")), then = then))
        }
        SELF_DEALS_DAMAGE_TO_YOU.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.You), Value.Fixed(m.groupValues[2].toInt()))))
        }
        YOU_MAY_PAY_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.Pay(Cost.mana(cost), bind = "paid"), Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        UNTAP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Untap(Objects.Self)))
        }
        YOU_MAY_EXILE_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember(IMPRINTED, Objects.Bound("exiled"))))
        }
        ADD_MANA_RESTRICTED.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = Filters.all(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), spells)))))
        }
        ADD_MANA_FOR_EACH.matchEntire(sentence)?.let { m ->
            val symbols = ManaCost.parse(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(symbols, times = Value.Count(count))))))
        }
        ADD_MANA.matchEntire(sentence)?.let { m -> mana(m.groupValues[1])?.let { return listOf(Step.Do(Effect.AddMana(ManaProduction(it)))) } }
        DRAW.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        CREATE_TOKEN.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count))))
        }
        THAT_PLAYER_MAY_SEARCH.matchEntire(sentence)?.let { m ->
            val player = context.lastTargetController() ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.May(player = player, steps = listOf(
                Step.Do(Effect.Search(player, filter, bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty()))),
                Step.Do(Effect.Shuffle(player)))))
        }
        PLAYERS_GAIN_LIFE.matchEntire(sentence)?.let { m ->
            Filters.players(m.groupValues[1])?.let { return listOf(Step.Do(Effect.GainLife(it, Value.Fixed(m.groupValues[2].toInt())))) }
        }
        DESTROY_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val filter = Filters.noun(noun) ?: return null
            return listOf(Step.Do(Effect.Destroy(context.target(Filters.slotName(noun), filter))))
        }
        // chunk 20: "It deals 2 damage to any target." before any target is named: "it" is the source itself.
        if (sentence.startsWith("It deals ") && context.targets.isEmpty()) effect("This card" + sentence.removePrefix("It"), context)?.let { return it }
        // chunk 20: "Attach this Equipment to target creature you control." (CR 701.3)
        ATTACH_SELF_TO_TARGET.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val noun = m.groupValues[2]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Attach(Objects.Self, arcanum.api.Recipients(objects = target))))
        }
        // chunk 20: "Attach it to that creature."
        if (sentence == "Attach it to that creature.") {
            val moved = context.it ?: return null
            val that = context.that ?: return null
            return listOf(Step.Do(Effect.Attach(moved, arcanum.api.Recipients(objects = that))))
        }
        // chunk 20: "Sacrifice that permanent."
        SACRIFICE_THAT.matchEntire(sentence)?.let { context.that?.let { that -> return listOf(Step.Do(Effect.Sacrifice(that))) } }
        // chunk 20: "Return this card from your graveyard to the battlefield"
        RETURN_SELF_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            context.it = Objects.Bound("returned")
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield), postBind = "returned")))
        }
        // chunk 20: "Target player mills cards equal to the sacrificed creature's power."
        TARGET_PLAYER_MILLS_SACRIFICED.matchEntire(sentence)?.let { m ->
            val property = if (m.groupValues[1] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.Toughness
            return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Of(property, Objects.Bound(SACRIFICED), arcanum.api.Combine.Single))))
        }
        // chunk 20: "Put up to one target card from your graveyard on the bottom of your library."
        PUT_UP_TO_TARGET_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[2]
            val filter = Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val slot = Filters.slotName(noun)
            context.targets += TargetSlot(slot, TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(n))
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Library, libraryPosition = -1))))
        }
        // chunk 20: "Look at the top card of target player's library."
        LOOK_TOP_OF_TARGET_LIBRARY.matchEntire(sentence)?.let { m ->
            val n = if (m.groupValues[2].isEmpty()) 1 else Numbers.count(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(context.targetPlayer(), Value.Fixed(n)))))
        }
        // chunk 20: "Look at a card at random in target player's hand."
        if (sentence == "Look at a card at random in target player's hand.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.LookAt(Objects.Random(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.Is(player))))))))
        }
        // chunk 20: "Draw a card at the beginning of the next turn's upkeep.": a delayed trigger (CR 603.7)
        AT_NEXT_UPKEEP.matchEntire(sentence)?.let { m ->
            val inner = Context()
            val steps = effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", inner)?.takeIf { inner.targets.isEmpty() } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep)),
                Instructions(steps = steps))))
        }
        // chunk 20: "Put this artifact on top of its owner's library."
        PUT_SELF_ON_TOP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Library, libraryPosition = 0))))
        }
        // chunk 20: "You may draw a card. If you do, discard a card." (CR 603.5: decided on resolution)
        YOU_MAY_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val action = effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context)
            val then = action?.let { effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) }
            if (action != null && then != null)
                return listOf(Step.Pay(steps = action, bind = "paid"), Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        // chunk 21: "You may put a creature card with mana value … from your hand onto the battlefield." (zero or one)
        YOU_MAY_PUT_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = counterNoun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))))
        }
        // chunk 21: "Put a charge counter on this artifact." / "Put a +1/+1 counter on target creature you control."
        PUT_COUNTERS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val on = m.groupValues[3]
            val objects = when {
                isSelf(on) -> Objects.Self
                on.startsWith("target ") -> on.removePrefix("target ").let { context.target(Filters.slotName(it), Filters.noun(it) ?: return null) }
                else -> return null
            }
            return listOf(Step.Do(Effect.PutCounters(objects, kind, Value.Fixed(n))))
        }
        // chunk 21: "When a creature card is exiled this way, [effect]": a reflexive trigger (CR 603.12)
        WHEN_EXILED_THIS_WAY.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val reflexive = instructions(m.groupValues[2]) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound("exiled"), filter),
                then = listOf(Step.Do(Effect.ReflexiveTrigger(reflexive)))))
        }
        // chunk 21: "Put a card exiled with this artifact into its owner's graveyard."
        PUT_EXILED_WITH_INTO_GRAVEYARD.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            context.it = Objects.Bound("moved")
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Remembered(EXILED_WITH)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Graveyard), postBind = "moved")))
        }
        // chunk 21: "If it's a land card, create a Treasure token."
        IF_ITS.matchEntire(sentence)?.let { m ->
            val moved = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(moved, filter), then = then))
        }
        // chunk 21: "Exile that card from your graveyard." (a discarded card; nothing happens if it has left)
        if (sentence == "Exile that card from your graveyard.") {
            val that = context.that ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Where(that, ObjectFilter.InZone(Zone.Graveyard)), Destination(Zone.Exile), postBind = "exiled"))) +
                remembered(Objects.Bound("exiled"))
        }
        // chunk 21: "Exile the top card of your library."
        if (sentence == "Exile the top card of your library.") {
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1)), Destination(Zone.Exile), postBind = "exiled")))
        }
        // chunk 21: "Until end of turn, you may play that card."
        if (sentence == "Until end of turn, you may play that card.") {
            val exiled = context.it ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play)),
                arcanum.api.Affected.Objects(exiled), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 21: "That creature's controller creates a 1/1 colorless Myr artifact creature token."
        THAT_CONTROLLER_CREATES.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.ControllerOf(that))))
        }
        // chunk 21: "Each opponent loses 3 life."
        PLAYERS_LOSE_LIFE.matchEntire(sentence)?.let { m ->
            Filters.players(m.groupValues[1])?.let { return listOf(Step.Do(Effect.LoseLife(it, Value.Fixed(m.groupValues[2].toInt())))) }
        }
        // chunk 21: "You gain 1 life for each spell you've cast this turn."
        GAIN_LIFE_FOR_EACH_SPELL_CAST.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val spells = Value.EventsThisTurn(EventPattern.Cast(by = PlayerFilter.You))
            val n = m.groupValues[2].toInt()
            return listOf(Step.Do(Effect.GainLife(players, if (n == 1) spells else Value.Times(spells, n))))
        }
        // chunk 21: "An opponent gains control of this artifact." (the opponent is chosen on resolution)
        OPPONENT_GAINS_CONTROL.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(
                Step.Choose(arcanum.api.Choice.Player(PlayerFilter.Opponent), bind = "opponent"),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("opponent"))),
                    arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 21: "You may [do something]." with nothing depending on it (CR 608.2d)
        YOU_MAY.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context)?.let { return listOf(Step.May(it)) }
        }
        // "[A], then [B].": tried last, after the wordings that contain ", then" themselves.
        THEN.matchEntire(sentence)?.let { m ->
            val first = effect(m.groupValues[1] + ".", context) ?: return null
            val second = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return first + second
        }
        return null
    }

    /** "nonland permanent with mana value equal to the number of charge counters on this artifact" (read from the source,
     * its last known information once it has left, CR 608.2h). */
    // chunk 21
    private fun counterNoun(phrase: String): ObjectFilter? {
        val m = WITH_MANA_VALUE_EQUAL_TO_COUNTERS.matchEntire(phrase)?.takeIf { isSelf(it.groupValues[3]) } ?: return null
        val kind = Counters.kind(m.groupValues[2]) ?: return null
        return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal,
            Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single)))
    }

    /** Keeps [objects] as cards "exiled with" this object when the card refers to them (CR 607.2a). */
    // chunk 21
    private fun remembered(objects: Objects): List<Step> =
        if (linksExiled) listOf(Step.Do(Effect.Remember(EXILED_WITH, objects))) else emptyList()

    /** "{C}", "{B}{B}", "{R} or {G}", "one mana of any color", "three mana of any one color" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        ANY_COMBINATION.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), combination = true) }
        ANY_COLOR.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null)) }
        ManaCost.parse(text)?.let { return ManaKind.Fixed(it) }
        val colors = text.split(Regex(""",? or |, """)).map { symbol -> ManaCost.parse(symbol)?.symbols?.singleOrNull()
            ?.let { (it as? arcanum.api.ManaSymbol.Colored)?.color } ?: return null }
        return ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })
    }

    /** Sentences in order; a "Spend this mana only …" sentence stays with the mana it restricts. */
    private fun sentences(text: String): List<String> {
        val raw = SENTENCE_END.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.endsWith(".")) it else "$it." }.map { it.replaceFirstChar { c -> c.uppercase() } }
        return raw.fold(mutableListOf()) { acc, sentence ->
            if ((sentence.startsWith("Spend this mana only") || sentence.startsWith("If you do,") ||
                    (sentence.startsWith("Put ") && acc.lastOrNull()?.startsWith("Look at the top") == true)) && acc.isNotEmpty())
                acc[acc.size - 1] = acc.last() + " " + sentence
            else acc += sentence
            acc
        }
    }

    private companion object {
        val SENTENCE_END = Regex("""(?<=\.)\s+""")
        val ABILITY_WORD = Regex("""([A-Z][A-Za-z' ]+) — (.+)""")
        const val IMPRINTED = "imprinted"
        val LABELS = mapOf("boast" to arcanum.api.AbilityLabel.Boast, "solved" to arcanum.api.AbilityLabel.Solved,
            "exhaust" to arcanum.api.AbilityLabel.Exhaust, "max speed" to arcanum.api.AbilityLabel.MaxSpeed,
            "power-up" to arcanum.api.AbilityLabel.PowerUp)
        val ALTERNATIVE_COST = Regex("""(?:If (.+?), )?[Yy]ou may (.+) rather than pay this spell's mana cost\.""")
        val X_AT_LEAST_UNCOUNTERABLE = Regex("""If X is (\d+) or more, this spell can't be countered and the damage can't be prevented\.""")
        val ADDITIONAL_COST = Regex("""As an additional cost to cast this spell, (.+)\.""")
        val SELF_DEALS_N = Regex("""(.+) deals (\d+) damage""")
        val SELF_DEALS_DAMAGE_TO = Regex("""(.+?) deals (\d+|X) damage to (any target|target .+)\.""")
        val KICKED_DEALS_INSTEAD = Regex("""If this spell was kicked, it deals (\d+) damage instead\.""")
        val DESTROY_TARGET_IF = Regex("""Destroy (target|that) (.+?) if it has mana value (\d+) or less\.""")
        val DESTROY_EACH = Regex("""Destroy each (.+)\.""")
        val EXILE_TARGET = Regex("""Exile target (.+?)( if its mana value is less than or equal to the number of colors of mana spent to cast this spell)?\.""")
        val RETURN_TARGET_TO_HAND = Regex("""Return target (.+) to its owner's hand\.""")
        val ITS_CONTROLLER_GAINS_LIFE_EQUAL_TO_POWER = Regex("""Its controller gains life equal to its power\.""")
        val UNTAP_UP_TO = Regex("""Untap up to (\w+) (.+)\.""")
        val RETURN_TO_HAND_COST = Regex("""Return (.+) to (?:its|their) owners?'s? hands?""")
        val EXILE_FROM_HAND_COST = Regex("""Exile an? (.+) from your hand""")
        val COUNTER_TARGET = Regex("""Counter target (.+?)(?: unless its controller pays (\{.+\}))?\.""")
        val COUNTERED_THIS_WAY = Regex("""If that spell is countered this way, (exile it|put it on top of its owner's library|put it into its owner's hand) instead of .+\.""")
        val NEXT_MAIN_PHASE_ADD_MANA_VALUE = Regex("""At the beginning of your next main phase, add an amount of \{C\} equal to that spell's mana value\.""")
        val yourHand = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
        val INSTEAD_IF = Regex("""(?:([A-Z][A-Za-z ]+) — )?(.+) instead if (.+)\.""")
        val DOESNT_UNTAP = Regex("""(.+) doesn't untap during your untap step\.""")
        val EQUIPPED_HAS_QUOTED = Regex("""Equipped creature has "(.+)"""")
        val WOULD_ENTER_DISCARD_INSTEAD = Regex("""If (.+) would enter, you may discard an? (.+) instead\. If you do, put .+ onto the battlefield\. If you don't, put it into its owner's graveyard\.""")
        val ACTIVATE_ONLY_AS_INSTANT = Regex("""Activate only as an instant\.""")
        val ADD_X_ANY_COMBINATION = Regex("""Add X mana in any combination of colors\.""")
        val ADD_ANY_OF_REMEMBERED_COLORS = Regex("""Add one mana of any of the exiled card's colors\.""")
        val FLIP_A_COIN = Regex("""Flip a coin\.""")
        val IF_YOU_LOSE_THE_FLIP = Regex("""If you lose the flip, (.+)""")
        val SELF_DEALS_DAMAGE_TO_YOU = Regex("""(.+) deals (\d+) damage to you\.""")
        val YOU_MAY_PAY_IF_YOU_DO = Regex("""You may pay (\{.+?\})\. If you do, (.+)""")
        val UNTAP = Regex("""Untap (.+)\.""")
        val YOU_MAY_EXILE_FROM_HAND = Regex("""You may exile an? (.+) from your hand\.""")
        val ACTIVATE_ONLY_IF = Regex("""Activate only if (.+?)\.""")
        val TAP_UNTAPPED = Regex("""Tap (an|two|three|four|five) untapped (.+)""")
        val PUT_COUNTER_ON_SELF = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+)""")
        val OTHERS_GET = Regex("""Other (.+) get ([+-]\d+)/([+-]\d+)\.""")
        val ANY_COLOR = Regex("""(one) mana of any color|(\w+) mana of any one color""")
        val ENTERS_TAPPED = Regex("""(.+) enters tapped\.""")
        val WHENEVER = Regex("""(Whenever|When|At) (.+?), (.+)""")
        val INTERVENING_IF = Regex("""if (.+?), (.+)""")
        val COSTS_LESS_FOR_EACH = Regex("""This ability costs (\{\d+\}) less to activate for each (.+?)\.""")
        val DISCARD_COST = Regex("""Discard (.+)""")
        val THEN = Regex("""(.+?), then (.+)""")
        val ADD_MANA_RESTRICTED = Regex("""Add (.+)\. Spend this mana only to cast (.+) spells\.""")
        val ADD_MANA_FOR_EACH = Regex("""Add (\{.\}) for each (.+)\.""")
        val DRAW = Regex("""Draw (a|one|two|three|four|five|six|seven) cards?\.""")
        val DISCARD = Regex("""[Dd]iscard (a|one|two|three|four|five|six|seven) cards?\.""")
        val CREATE_TOKEN = Regex("""[Cc]reate (.+ tokens?(?: with .+)?)\.""")
        val SELF_DEALS_DAMAGE_TO_EACH = Regex("""(.+?) deals (\d+) damage to (each .+)\.""")
        val EXILE_THEN_RETURN = Regex("""Exile target (.+), then return it to the battlefield under its owner's control\.""")
        val TARGET_PLAYER_DRAWS = Regex("""Target player draws (\w+) cards?(?:, then discards (\w+) cards?)?\.""")
        val TARGET_PLAYER_DISCARDS = Regex("""Target player discards (\w+) cards?\.""")
        val TARGET_PLAYER_CREATES = Regex("""Target player creates (.+ tokens?)\.""")
        val RETURN_FROM_GRAVEYARD_TO_HAND = Regex("""Return target (.+) from your graveyard to your hand\.""")
        val EACH_PLAYER_SHUFFLES_HAND_AND_GRAVEYARD = Regex("""Each player shuffles their hand and graveyard into their library, then draws (\w+) cards\.""")
        val EXILE_SELF = Regex("""Exile (.+)\.""")
        val LOOK_PUT_N_INTO_HAND_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put (\w+) of them into your hand and the rest on the bottom of your library in any order\.""")
        val LOOK_HAND_BOTTOM_EXILE = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your hand, put one of them on the bottom of your library, and exile one of them\.""")
        val YOU_UNTAP_UP_TO = Regex("""You untap up to (\w+) (.+)\.""")
        val THAT_PLAYER_MAY_SEARCH = Regex("""(?:That player|Its controller) may search their library for (.+), put (?:it|that card) onto the battlefield( tapped)?, then shuffle\.""")
        val AS_ENTERS_PAY_LIFE = Regex("""As (.+) enters, you may pay (\d+) life\. If you don't, it enters tapped\.""")
        val PAY_LIFE = Regex("""Pay (\d+) life""")
        val SACRIFICE = Regex("""Sacrifice (.+)""")
        val ADD_MANA = Regex("""Add (.+)\.""")
        val PLAYERS_GAIN_LIFE = Regex("""(.+) gains? (\d+) life\.""")
        val DESTROY_TARGET = Regex("""Destroy target (.+)\.""")
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+?), put (?:it|that card) (onto the battlefield|into your hand)(, discard a card at random)?, then shuffle\.""")
        val SEARCH_TO_TOP = Regex("""Search your library for (.+?), (reveal it, )?then shuffle and put that card on top\.""")
        val PUT_FROM_HAND_ON_TOP = Regex("""Put (\w+) cards? from your hand on top of your library(?: in any order)?\.""")
        val PLAYERS_DRAW = Regex("""(Each player|You) draws? (\w+) cards?(?:, then discards? (\w+) cards?( at random)?)?(?: and loses? (\d+) life)?\.""")
        val EACH_PLAYER_DISCARDS_HAND_DRAWS = Regex("""Each player discards their hand, then draws (\w+) cards\.""")
        val DISCARD_UNLESS_ATTACKED = Regex("""Discard (a|one|two) cards? unless you attacked this turn\.""")
        val SCRY_OR_SURVEIL = Regex("""(Scry|Surveil) (\d+)\.""")
        val TARGET_PLAYER_MILLS = Regex("""Target player mills (\w+) cards?\.""")
        val LOOK_PUT_BACK = Regex("""Look at the top (\w+) cards of your library, then put them back in any order\.""")
        val TARGET_GETS_UNTIL_EOT = Regex("""Target (.+) gets ([+-]\d+)/([+-]\d+) until end of turn\.""")
        val RETURN_UP_TO_FROM_GRAVEYARD_TO_HAND = Regex("""Return up to (\w+) target (.+) from your graveyard to your hand\.""")
        val YOU_LOSE_LIFE = Regex("""You lose (\d+) life\.""")
        val ANY_COMBINATION = Regex("""(\w+) mana in any combination of colors""")
        // chunk 20
        val ATTACHED_GETS = Regex("""(?:Equipped|Enchanted) creature gets ([+-]\d+)/([+-]\d+)(?: for each (.+))?\.""")
        val ATTACH_SELF_TO_TARGET = Regex("""Attach (.+) to target (.+)\.""")
        val SACRIFICE_THAT = Regex("""Sacrifice that (\w+)\.""")
        val RETURN_SELF_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (.+) from your graveyard to the battlefield\.""")
        val TARGET_PLAYER_MILLS_SACRIFICED = Regex("""Target player mills cards equal to the sacrificed \w+'s (power|toughness)\.""")
        val PUT_UP_TO_TARGET_ON_BOTTOM = Regex("""Put up to (\w+) target (.+) from your graveyard on the bottom of your library\.""")
        val LOOK_TOP_OF_TARGET_LIBRARY = Regex("""Look at the top (card|(\w+) cards) of target player's library\.""")
        val AT_NEXT_UPKEEP = Regex("""(.+) at the beginning of the next turn's upkeep\.""")
        val PUT_SELF_ON_TOP = Regex("""Put (.+) on top of its owner's library\.""")
        val YOU_MAY_IF_YOU_DO = Regex("""You may (.+?)\. If you do, (.+)""")
        val IF_INSTEAD = Regex("""If (.+?), (.+) instead\.""")
        // chunk 21
        const val EXILED_WITH = "exiled"
        val MAY_PLAY_FROM_GRAVEYARD = Regex("""You may (play|cast) (.+) from your graveyard\.""")
        val SPEND_AS_ANY_COLOR_TO_ACTIVATE = Regex("""You may spend mana as though it were mana of any color to activate abilities of (.+)\.""")
        val HAVE_ABILITIES_OF_EXILED = Regex("""(.+) have all activated abilities of all (.+) cards exiled with (.+)\.""")
        val ENTERS_WITH_COUNTERS = Regex("""(.+) enters with (\w+) (\S+) counters? on it\.""")
        val ACTIVATE_ONLY_AS_SORCERY = Regex("""Activate only as a sorcery\.""")
        val ACTIVATE_ONLY_DURING_YOUR_TURN = Regex("""Activate only during your turn\.""")
        val TRIGGERS_ONLY_ONCE = Regex("""This ability triggers only once each turn\.""")
        val CHOOSE_MODES = Regex("""choose (one|two)( that hasn't been chosen( this turn)?)? —""")
        val DURING_YOUR_TURN = Regex("""(.+) during your turn""")
        val ONE_OR_MORE_LEAVE = Regex("""one or more (.+) leave the battlefield""")
        val YOU_CREATE_ONE_OR_MORE = Regex("""you create one or more (.+) tokens""")
        val REMOVE_COUNTERS_FROM_SELF = Regex("""Remove (a|an|one|two|three) (\S+) counters? from (.+)""")
        val WITH_MANA_VALUE_EQUAL_TO_COUNTERS = Regex("""(.+) with mana value equal to the number of (\S+) counters on (.+)""")
        val EXILE_TARGET_UNTIL_LEAVES = Regex("""Exile target (.+) until (.+) leaves the battlefield\.""")
        val EXILE_TARGET_FROM_A_GRAVEYARD = Regex("""Exile target (.+) from a graveyard\.""")
        val YOU_MAY_PUT_FROM_HAND = Regex("""You may put an? (.+) from your hand onto the battlefield\.""")
        val PUT_COUNTERS = Regex("""Put (a|an|one|two|three) (\S+) counters? on (.+)\.""")
        val WHEN_EXILED_THIS_WAY = Regex("""When an? (.+) is exiled this way, (.+)""")
        val PUT_EXILED_WITH_INTO_GRAVEYARD = Regex("""Put an? (.+?) exiled with (.+) into its owner's graveyard\.""")
        val IF_ITS = Regex("""If it's an? (.+?), (.+)""")
        val THAT_CONTROLLER_CREATES = Regex("""That \w+'s controller creates (.+ tokens?)\.""")
        val PLAYERS_LOSE_LIFE = Regex("""(.+) loses? (\d+) life\.""")
        val GAIN_LIFE_FOR_EACH_SPELL_CAST = Regex("""(.+) gains? (\d+) life for each spell you've cast this turn\.""")
        val OPPONENT_GAINS_CONTROL = Regex("""An opponent gains control of (.+)\.""")
        val YOU_MAY = Regex("""You may (.+)\.""")
    }
}

internal fun String.removeArticle() = removePrefix("a ").removePrefix("an ")
