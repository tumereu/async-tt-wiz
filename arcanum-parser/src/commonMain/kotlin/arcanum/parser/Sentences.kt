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
internal class Sentences(private val self: Set<String>, private val isSpell: Boolean = false) {
    /** The card's name or "this land" / "This land" (a sentence may start with it). */
    private fun isSelf(text: String) = text in self || text.replaceFirstChar { it.lowercase() } in self

    fun line(line: String): Ability? {
        // "Channel — [ability]": an ability word, no rules meaning (CR 207.2c). Keywords with a dash ("Ward—{2}") have
        // no spaces around it.
        ABILITY_WORD.matchEntire(line)?.let { m ->
            return when (val ability = line(m.groupValues[2])) {
                is Ability.Activated -> ability.copy(abilityWord = m.groupValues[1])
                is Ability.Triggered -> ability.copy(abilityWord = m.groupValues[1])
                is Ability.Static -> ability.copy(abilityWord = m.groupValues[1])
                is Ability.Spell -> ability.copy(abilityWord = m.groupValues[1])
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
                affects = arcanum.api.Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)))))
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
        return Ability.Activated(cost, instructions, arcanum.api.ActivationRules(timing = timing, costChange = costChange, condition = condition))
    }

    /** "Whenever [event], [if condition,] [effect]." (CR 603.1) */
    private fun triggered(line: String): Ability? {
        val m = WHENEVER.matchEntire(line) ?: return null
        val event = Events.event(m.groupValues[2], self) ?: return null
        val rest = m.groupValues[3]
        val conditional = INTERVENING_IF.matchEntire(rest)
        val interveningIf = conditional?.let { Conditions.condition(it.groupValues[1], self) ?: return null }
        val instructions = instructions(conditional?.groupValues?.get(2) ?: rest) ?: return null
        return Ability.Triggered(arcanum.api.Trigger.On(event), instructions, interveningIf = interveningIf)
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
            return listOf(CostPart.Discard(CostObjects.Choose(Filters.noun(m.groupValues[1].removeArticle()) ?: return null)))
        }
        TAP_UNTAPPED.matchEntire(text)?.let { m ->
            val count = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(CostPart.TapObjects(CostObjects.Choose(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.HasStatus(arcanum.api.Status.Tapped)), filter)), count = Value.Fixed(count))))
        }
        PUT_COUNTER_ON_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.PutCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)))
        }
        RETURN_TO_HAND_COST.matchEntire(text)?.let { m ->
            return listOf(CostPart.ReturnToHand(CostObjects.Choose(Filters.noun(m.groupValues[1]) ?: return null)))
        }
        EXILE_FROM_HAND_COST.matchEntire(text)?.let { m ->
            return listOf(CostPart.Exile(CostObjects.Choose(Filters.noun(m.groupValues[1]) ?: return null), from = Zone.Hand))
        }
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            return listOf(CostPart.Sacrifice(if (isSelf(what)) CostObjects.Self else CostObjects.Choose(Filters.noun(what.removeArticle()) ?: return null)))
        }
        return null
    }

    /** The effect sentences of a spell or ability, in order. */
    fun instructions(text: String, seedTargets: List<TargetSlot> = emptyList()): Instructions? {
        val context = Context()
        context.targets += seedTargets
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
        return null
    }

    private class Context {
        var flip = false
        val replacements = mutableListOf<Mechanic.Replacement>()
        val targets = mutableListOf<TargetSlot>()
        /** "any target" (CR 115.4): creature, planeswalker or battle, or player. */
        fun anyTarget() {
            targets += TargetSlot("any", arcanum.api.TargetKind.Either(ObjectFilter.Or(listOf(
                ObjectFilter.Type(arcanum.api.CardType.Creature), ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                ObjectFilter.Type(arcanum.api.CardType.Battle))), PlayerFilter.Any))
        }

        fun target(noun: String, filter: ObjectFilter): Objects {
            targets += TargetSlot(noun, TargetKind.Objects(filter))
            return Objects.Target(noun)
        }

        /** "That player": the controller of the last object target. */
        fun lastTargetController(): Players? = targets.lastOrNull()?.let { Players.ControllerOf(Objects.Target(it.name)) }
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
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
        UNTAP_UP_TO.matchEntire(sentence)?.let { m ->
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
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.And(listOf(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen"),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember(IMPRINTED, Objects.Bound("exiled"))))
        }
        ADD_MANA_RESTRICTED.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = ObjectFilter.And(listOf(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), spells))))))
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
            return listOf(Step.Do(Effect.CreateToken(Tokens.defined(m.groupValues[1]) ?: return null)))
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
            val players = Filters.players(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.GainLife(players, Value.Fixed(m.groupValues[2].toInt()))))
        }
        DESTROY_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val filter = Filters.noun(noun) ?: return null
            return listOf(Step.Do(Effect.Destroy(context.target(Filters.slotName(noun), filter))))
        }
        SEARCH_PUT_SHUFFLE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card")) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                Step.Do(Effect.Shuffle(Players.You)))
        }
        // "[A], then [B].": tried last, after the wordings that contain ", then" themselves.
        THEN.matchEntire(sentence)?.let { m ->
            val first = effect(m.groupValues[1] + ".", context) ?: return null
            val second = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return first + second
        }
        return null
    }

    /** "{C}", "{B}{B}", "{R} or {G}", "one mana of any color", "three mana of any one color" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        ANY_COLOR.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)) }
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
            if ((sentence.startsWith("Spend this mana only") || sentence.startsWith("If you do,")) && acc.isNotEmpty())
                acc[acc.size - 1] = acc.last() + " " + sentence
            else acc += sentence
            acc
        }
    }

    private companion object {
        val SENTENCE_END = Regex("""(?<=\.)\s+""")
        val ABILITY_WORD = Regex("""([A-Z][A-Za-z' ]+) — (.+)""")
        const val IMPRINTED = "imprinted"
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
        val RETURN_TO_HAND_COST = Regex("""Return an? (.+) to its owner's hand""")
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
        val ANY_COLOR = Regex("""(\w+) mana of any (?:one )?color""")
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
        val CREATE_TOKEN = Regex("""[Cc]reate (an? .+ tokens?)\.""")
        val THAT_PLAYER_MAY_SEARCH = Regex("""(?:That player|Its controller) may search their library for (.+), put (?:it|that card) onto the battlefield( tapped)?, then shuffle\.""")
        val AS_ENTERS_PAY_LIFE = Regex("""As (.+) enters, you may pay (\d+) life\. If you don't, it enters tapped\.""")
        val PAY_LIFE = Regex("""Pay (\d+) life""")
        val SACRIFICE = Regex("""Sacrifice (.+)""")
        val ADD_MANA = Regex("""Add (.+)\.""")
        val PLAYERS_GAIN_LIFE = Regex("""(.+) gains? (\d+) life\.""")
        val DESTROY_TARGET = Regex("""Destroy target (.+)\.""")
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+), put it onto the battlefield, then shuffle\.""")
    }
}

internal fun String.removeArticle() = removePrefix("a ").removePrefix("an ")
