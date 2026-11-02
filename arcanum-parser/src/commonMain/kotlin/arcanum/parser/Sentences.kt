package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
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
    private val self: Set<String>,
    private val isSpell: Boolean = false,
    /** chunks 21, 26: another ability of the card refers to cards "exiled with" it, so its exiling effects keep the exiled
     * cards as card memory (CR 607.2a). */
    private val linkedExile: Boolean = false,
) {
    /** chunk 40: parsing an activated ability with X in its cost: X is the announced value (CR 107.3a). */
    private var announcedX = false

    /** The card's name or "this land" / "This land" (a sentence may start with it). */
    private fun isSelf(text: String) = text in self || text.replaceFirstChar { it.lowercase() } in self

    /** Lines that are more than one ability. */
    fun multi(line: String): List<Ability>? {
        // chunk 25: "Any player may cast creature spells with mana value 3 or less without paying their mana costs and as
        // though they had flash." A permission to cast from hand for free (each player for their own cards) and
        // player-wide flash (CR 601.2a, 118.9, 702.8a).
        MAY_CAST_WITHOUT_PAYING.matchEntire(line)?.let { m ->
            val anyPlayer = m.groupValues[1] == "Any player"
            val spells = Filters.noun(m.groupValues[2].replace(" spells", "")) ?: return null
            val owner = if (anyPlayer) PlayerFilter.Is(Players.Holder) else PlayerFilter.You
            val free = Ability.Static(
                listOf(Mechanic.MayPlay(Zone.Hand, withoutPaying = true, holder = if (anyPlayer) PlayerFilter.Any else PlayerFilter.You, how = PlayKind.Cast)),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(spells, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(owner)))))
            if (m.groupValues[3].isEmpty()) return listOf(free)
            val players = if (anyPlayer) Players.Each(PlayerFilter.Any) else Players.You
            return listOf(free, Ability.Static(listOf(Mechanic.CastAsThoughFlash(spells)), affects = arcanum.api.Affected.Players(players)))
        }
        // chunk 52: "This creature has all activated abilities of the chosen permanent except for loyalty abilities. You may
        // spend mana as though it were mana of any color to activate those abilities." (layer 6, CR 613.1f)
        HAS_ACTIVATED_ABILITIES_OF.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val of = chosenObjects(m.groupValues[2]) ?: return null
            val gains = Ability.Static(listOf(Mechanic.GainAbilitiesOf(of, exceptLoyalty = m.groupValues[3].isNotEmpty())), affects = Affected.Objects(Objects.Self))
            if (m.groupValues[4].isEmpty()) return listOf(gains)
            return listOf(gains, Ability.Static(listOf(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = ObjectFilter.Is(Objects.Self)))),
                affects = arcanum.api.Affected.Players(Players.You)))
        }
        return null
    }

    /** chunk 33: what "the exiled card" means on this card (linked abilities, CR 607.2a); null when nothing links. */
    var exiledCard: Objects? = null

    /** chunk 31: what "it" means in the triggered ability being read, and the zone it is in ("you may exile it" in a
     * dies trigger: the new card in the graveyard, CR 400.7). */
    private var triggerIt: Pair<Objects, Zone?>? = null

    fun line(line: String): Ability? {
        // chunk 56: a Saga's "I, II — [effect]" is a chapter ability for each listed chapter (CR 714.2b, c)
        CHAPTERS.matchEntire(line)?.let { m ->
            val chapters = m.groupValues[1].split(", ").mapNotNull { ROMAN[it] }
            if (chapters.size == m.groupValues[1].split(", ").size)
                return Ability.Triggered(arcanum.api.Trigger.Chapter(chapters), instructions(m.groupValues[2]) ?: return null)
        }
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
            // chunk 50: flavor words too (CR 207.2d)
            if (!RulesWords.isAbilityOrFlavorWord(word)) return null
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
        if (m.groupValues[1].isNotEmpty() && !RulesWords.isAbilityOrFlavorWord(m.groupValues[1])) return null
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
        val options = modeLines.map { arcanum.api.Mode(instructions(it) ?: return null, text = it) }
        if (options.isEmpty()) return null
        // "Choose one or more —" / "Choose one or both —": up to every mode, each at most once.
        val max = if (choose.groupValues[2].isNotEmpty()) options.size else n
        return arcanum.api.Modes(options, min = n, max = max, repeatable = choose.value.contains("same mode"), chooser = Players.You)
    }

    /** The instructions of an instant or sorcery (CR 113.3a). */
    private fun spell(line: String): Ability? = if (isSpell) instructions(line)?.let { Ability.Spell(it) } else null

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (CR 614.12, 118.12a) */
    private fun static(line: String): Ability? {
        // dsk chunk 02: "You have no maximum hand size[ and don't lose the game for having 0 or less life]." (CR 402.2, 704.5a)
        NO_MAXIMUM_HAND_SIZE.matchEntire(line)?.let { m ->
            val mechanics = listOf(Mechanic.NoMaximumHandSize) + listOfNotNull(Mechanic.NoLossForZeroLife.takeIf { m.groupValues[1].isNotEmpty() })
            return Ability.Static(mechanics, affects = Affected.Players(Players.You))
        }
        // dsk chunk 02: "[Once each turn, ]you may pay {0} rather than pay the mana cost for a spell you cast from exile." An
        // alternative cost (CR 118.9) for the cards its controller casts (from exile, or from anywhere)
        ALTERNATIVE_COST_FOR_SPELLS.matchEntire(line)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            val cards = if (m.groupValues[3].isEmpty()) ObjectFilter.Or(EVERY_ZONE.filter { it != Zone.Battlefield && it != Zone.Stack }.map { ObjectFilter.InZone(it) })
                else ObjectFilter.InZone(Zone.Exile)
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost.mana(cost), perTurn = 1.takeIf { m.groupValues[1].isNotEmpty() })),
                affects = Affected.Objects(Objects.Each(cards)))
        }
        // dsk chunk 05: "Enchanted creature loses all abilities and is a Toy artifact creature with base power and toughness 0/2 in
        // addition to its other types." (layers 4, 6, 7b, CR 613.1d, f, 613.4b)
        LOSES_ALL_AND_IS.matchEntire(line)?.let { m ->
            val affected = attachedSubject(m.groupValues[1]) ?: return null
            val add = mutableListOf<CardType>()
            val subtypes = mutableListOf<arcanum.api.SubtypeRef>()
            for (word in m.groupValues[2].split(' ')) {
                val type = Filters.CARD_TYPES[word]
                when {
                    type != null -> add += type
                    word in RulesWords.subtypes -> subtypes += arcanum.api.SubtypeRef.Literal(Subtype(word))
                    else -> return null
                }
            }
            return Ability.Static(listOf(Mechanic.LoseAbilities(), Mechanic.SetTypes(add = add, addSubtypes = subtypes),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[3].toInt()), Value.Fixed(m.groupValues[4].toInt()))), affects = Affected.Objects(affected))
        }
        // dsk chunk 05: "As long as enchanted creature is face down, it can't be turned face up."
        FACE_DOWN_CANT_BE_TURNED_FACE_UP.matchEntire(line)?.let { m ->
            val affected = attachedSubject(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CantBeTurnedFaceUp), affects = Affected.Objects(affected),
                condition = arcanum.api.Condition.ObjectMatches(affected, ObjectFilter.HasStatus(arcanum.api.Status.FaceDown)))
        }
        // dsk chunk 05: "Enchanted creature doesn't untap during its controller's untap step."
        DOESNT_UNTAP_DURING_CONTROLLERS.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(attachedSubject(m.groupValues[1]) ?: return null))
        }
        // dsk chunk 04: "This creature can't be blocked by Glimmers." (CR 509.1b)
        CANT_BE_BLOCKED_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeBlockedBy(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 03: "Unlock costs you pay cost {1} less." (the unlock special action, CR 709.5e)
        UNLOCK_COSTS_LESS.matchEntire(line)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.CostChange(Filters.all(ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Unlock), ObjectFilter.ControlledBy(PlayerFilter.You)),
                change = arcanum.api.CostChange.Less(mana, Value.Fixed(1)))), affects = Affected.Objects(Objects.Self))
        }
        // dsk chunk 01: "Untap each creature you control during each other player's untap step." (CR 502.3)
        UNTAP_DURING_OTHER_UNTAP_STEPS.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.UntapsDuringOtherUntapSteps), affects = Affected.Objects(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null)))
        }
        // dsk chunk 01: "Creatures you control have base power and toughness each equal to the number of creatures you control."
        // (layer 7b, CR 613.4b)
        HAVE_BASE_PT_EQUAL_TO_NUMBER.matchEntire(line)?.let { m ->
            val affected = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val count = Value.Count(Filters.objects(m.groupValues[2]) ?: return null)
            return Ability.Static(listOf(Mechanic.SetPowerToughness(count, count)), affects = Affected.Objects(Objects.Each(affected)))
        }
        // dsk chunk 01: "If a triggered ability of a permanent you control triggers, that ability triggers an additional time."
        // (CR 603.2d)
        TRIGGERS_ADDITIONAL_TIME.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.TriggersAdditionalTime(null, Filters.noun(m.groupValues[1]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // "[If …, ]you may [cost] rather than pay this spell's mana cost." (CR 118.9)
        ALTERNATIVE_COST.matchEntire(line)?.let { m ->
            val parts = m.groupValues[2].split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten()
            val condition = when (val c = m.groupValues[1]) {
                "" -> null
                "it's not your turn" -> arcanum.api.Condition.Not(arcanum.api.Condition.YourTurn)
                else -> Conditions.condition(c, self) ?: return null
            }
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost(parts))), condition = condition, affects = Affected.Objects(Objects.Self))
        }
        if (line == "This spell can't be countered.") return Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self))
        // "If X is 5 or more, this spell can't be countered and the damage can't be prevented." (CR 107.3m, 615.12)
        X_AT_LEAST_UNCOUNTERABLE.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.CantBeCountered, Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self), to = RecipientFilter()))),
                condition = arcanum.api.Condition.Compare(Value.CastX(of = Objects.Self), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())), affects = Affected.Objects(Objects.Self))
        }
        // chunk 13
        // "As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell
        // costs {2} less to cast for each card exiled this way." (CR 601.2f): the reduction counts the cost's cards.
        ADDITIONAL_EXILE_ANY_NUMBER_REDUCTION.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val per = ManaCost.parse(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(
                Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(CostObjects.Choose(filter, count = null), from = Zone.Hand, postBind = "exiled")))),
                Mechanic.CostChange(ObjectFilter.Is(Objects.Self), change = arcanum.api.CostChange.Less(per, Value.Count(Objects.Bound("exiled"))))), affects = Affected.Objects(Objects.Self))
        }
        // "As an additional cost to cast this spell, [cost][ or cost]." (CR 601.2f)
        ADDITIONAL_COST.matchEntire(line)?.let { m ->
            val options = m.groupValues[1].split(" or ").map { option ->
                Cost(option.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }.flatten())
            }
            val cost = options.singleOrNull() ?: Cost(listOf(CostPart.OneOf(options)))
            return Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost)), affects = Affected.Objects(Objects.Self))
        }
        OTHERS_GET.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))))
        }
        DOESNT_UNTAP.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(Objects.Self))
        }
        // 'Equipped creature has "[ability]"' (CR 301.5)
        EQUIPPED_HAS_QUOTED.matchEntire(line)?.let { m ->
            val granted = Sentences(setOf("this creature"), false).line(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the
        // battlefield. If you don't, put it into its owner's graveyard." (CR 614.1c)
        WOULD_ENTER_DISCARD_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                instead = Instructions(steps = listOf(
                    Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(filter, count = Value.Fixed(1))))), bind = "discarded", payer = Players.You),
                    Step.If(arcanum.api.Condition.Did("discarded"),
                        then = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield)))),
                        otherwise = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Graveyard))))))))), affects = Affected.Objects(Objects.Self))
        }
        ENTERS_TAPPED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self))
        }
        AS_ENTERS_PAY_LIFE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(unlessPays(Players.You, Cost(listOf(CostPart.PayLife(Value.Fixed(m.groupValues[2].toInt())))),
                    listOf(Step.Modify(Modification.EntersTapped))))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 26: 'Creature tokens you control have "[ability]"' (CR 113.10)
        HAVE_QUOTED.matchEntire(line)?.let { m ->
            // chunk 55: a subtype keeps its capital ("Elves you control")
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val granted = Sentences(setOf("this creature", "this permanent")).line(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.GainAbilities(listOf(granted))), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 26: "This token can't be blocked."
        CANT_BE_BLOCKED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self))
        }
        // chunk 25
        if (line == "Play with your hand revealed.")
            return Ability.Static(listOf(Mechanic.MayLookAt(Objects.Affected, reveal = true)), affects = arcanum.api.Affected.Objects(Objects.Each(yourHand)))
        // chunk 25: "If you would draw a card, [instead]." (CR 614.1a)
        IF_YOU_WOULD_DRAW.matchEntire(line)?.let { m ->
            val instead = instructions(m.groupValues[1].replaceFirstChar { it.uppercase() } + ". " + m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = instead)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 25: "Creatures don't untap during their controllers' untap steps."
        DONT_UNTAP_PLURAL.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return Ability.Static(listOf(Mechanic.DoesntUntap), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 25: "You may play an additional land on each of your turns." (CR 305.2)
        EXTRA_LAND_PLAYS.matchEntire(line)?.let { m ->
            val count = if (m.groupValues[1] == "any number of lands") null else Numbers.count(m.groupValues[1].substringBefore(' ')) ?: return null
            return Ability.Static(listOf(Mechanic.ExtraLandPlays(count)), affects = arcanum.api.Affected.Players(Players.You))
        }
        // chunk 25: "Enchanted permanent is a Vehicle artifact with crew 5 and it loses all other card types." (CR 205.1a)
        ENCHANTED_IS.matchEntire(line)?.let { m ->
            val add = mutableListOf<CardType>()
            val subtypes = mutableListOf<arcanum.api.SubtypeRef>()
            for (word in m.groupValues[1].split(' ')) {
                val type = Filters.CARD_TYPES[word]
                when {
                    type != null -> add += type
                    word in RulesWords.subtypes -> subtypes += arcanum.api.SubtypeRef.Literal(Subtype(word))
                    else -> return null
                }
            }
            val keywords = m.groupValues[2].takeIf { it.isNotEmpty() }?.split(" and ")?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
            val mechanics = listOf<Mechanic>(Mechanic.SetTypes(add = add, addSubtypes = subtypes, replaceTypes = m.groupValues[3].isNotEmpty())) +
                (if (keywords.isEmpty()) emptyList() else listOf(Mechanic.GainAbilities(keywords)))
            return Ability.Static(mechanics, affects = arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // chunk 20: "Equipped creature gets +1/+0 for each artifact you control." (CR 301.5)
        ATTACHED_GETS.matchEntire(line)?.let { m ->
            val per = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { perEach(it) ?: return null }
            fun amount(n: Int): Value = when {
                per == null || n == 0 -> Value.Fixed(n)
                n == 1 -> per
                else -> Value.Times(per, n)
            }
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[1].toInt()), amount(m.groupValues[2].toInt()))),
                affects = arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self)))
        }
        // chunk 21: "You may play lands from your graveyard." (CR 305.1)
        MAY_PLAY_FROM_GRAVEYARD.matchEntire(line)?.let { m ->
            val how = if (m.groupValues[1] == "play") arcanum.api.PlayKind.Play else arcanum.api.PlayKind.Cast
            // chunk 45: "You may cast this card from your graveyard." (on the card itself, CR 113.6f)
            if (isSelf(m.groupValues[2])) return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self))
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how, holder = PlayerFilter.You)), affects = arcanum.api.Affected.Objects(Objects.Each(
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
        // chunk 41: "enters with X +1/+1 counters": the X of the spell it was (CR 107.3m); chunk 42: "enters tapped with"
        ENTERS_WITH_COUNTERS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val n = if (m.groupValues[3] == "X") Value.CastX(of = Objects.Self) else Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null)
            val kind = Counters.kind(m.groupValues[4]) ?: return null
            val modify = listOfNotNull(Modification.EntersTapped.takeIf { m.groupValues[2].isNotEmpty() }, Modification.EntersWithCounters(kind, n))
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = modify)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 40: "Green spells you control can't be countered."
        GROUP_CANT_BE_COUNTERED.matchEntire(line)?.let { m ->
            val spells = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            return Ability.Static(listOf(Mechanic.CantBeCountered), affects = arcanum.api.Affected.Objects(Objects.Each(spells)))
        }
        // chunk 40: "If one or more tokens would be created under your control, those tokens plus that many 1/1 green Squirrel
        // creature tokens are created instead." (CR 614.1a)
        TOKENS_PLUS_THAT_MANY.matchEntire(line)?.let { m ->
            val token = Tokens.defined("a " + m.groupValues[1] + " token") ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.CreateToken(ObjectFilter.ControlledBy(PlayerFilter.You)),
                modify = listOf(Modification.AlsoCreate(token, Value.EventAmount)))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 40: "If a modular triggered ability would put one or more +1/+1 counters on a creature you control, that many
        // plus one +1/+1 counters are put on it instead." (CR 614.1a)
        KEYWORD_COUNTERS_PLUS.matchEntire(line)?.let { m ->
            val keyword = arcanum.api.KeywordKind.byId(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2])?.takeIf { it == Counters.kind(m.groupValues[5]) } ?: return null
            val on = Filters.noun(m.groupValues[3].removeArticle()) ?: return null
            val plus = Numbers.count(m.groupValues[4]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.CountersPut(on, kind, causedBy = ObjectFilter.AbilityOf(arcanum.api.AbilityKind.Triggered, keyword = keyword)),
                modify = listOf(Modification.AddAmount(Value.Fixed(plus))))), affects = Affected.Objects(Objects.Self))
        }
        // chunks 35, 41: "This creature can't block.", "This token can't block."
        CANT_BLOCK.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 42: "This creature gets +2/+2 as long as there are three or more land cards in your graveyard."
        SELF_GETS_AS_LONG_AS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val condition = Conditions.condition(m.groupValues[4], self) ?: return null
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                condition = condition, affects = Affected.Objects(Objects.Self))
        }
        // chunk 42: "This creature gets +1/+1 for each creature card in your graveyard."
        SELF_GETS_FOR_EACH.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val per = perEach(m.groupValues[4]) ?: return null
            return Ability.Static(listOf(Mechanic.ModifyPowerToughness(times(per, m.groupValues[2].toInt()), times(per, m.groupValues[3].toInt()))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 30: "This creature has deathtouch as long as you control at least three other snow permanents."
        HAS_KEYWORDS_AS_LONG_AS.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords(m.groupValues[2]) ?: return null)),
                condition = Conditions.condition(m.groupValues[3], self) ?: return null, affects = Affected.Objects(Objects.Self))
        }
        // chunk 30: "This creature escapes with a +1/+1 counter on it." means "If this permanent escaped, it enters with
        // [those counters]" (CR 702.138c).
        ESCAPES_WITH.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[3]) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))),
                condition = arcanum.api.Condition.Paid(arcanum.api.Keyword.Escape.Kind, times = 1, of = Objects.Self), affects = Affected.Objects(Objects.Self))
        }
        // chunk 33: "X can't be 0."
        X_CANT_BE.matchEntire(line)?.let { m -> return Ability.Static(listOf(Mechanic.XAtLeast(m.groupValues[1].toInt() + 1)), affects = Affected.Objects(Objects.Self)) }
        // chunk 32: "Golems you control have first strike."
        HAVE_KEYWORDS.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() })
            val keywords = keywords(m.groupValues[2])
            if (filter != null && keywords != null)
                return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = arcanum.api.Affected.Objects(Objects.Each(filter)))
        }
        // chunk 32: "As long as you've discarded a card this turn, you may pay {B/R} to cast this spell." (CR 118.9)
        MAY_PAY_TO_CAST.matchEntire(line)?.let { m ->
            return Ability.Static(listOf(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse(m.groupValues[2]) ?: return null))),
                condition = Conditions.condition(m.groupValues[1], self) ?: return null, affects = Affected.Objects(Objects.Self))
        }
        // chunk 35: "Anax's power is equal to your devotion to red." (a characteristic-defining ability, CR 604.3)
        POWER_IS_EQUAL_TO.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val value = selfValue(m.groupValues[2]) ?: return null
            return Ability.Static(listOf(Mechanic.SetPowerToughness(value, null)), characteristicDefining = true, affects = Affected.Objects(Objects.Self))
        }
        // chunk 36: "This creature attacks each combat if able." (a requirement, CR 508.1d)
        ATTACKS_EACH_COMBAT.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self))
        }
        // chunk 36: "This creature can't be blocked except by creatures with flying or reach." (CR 509.1b)
        CANT_BE_BLOCKED_EXCEPT_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 36: "During your turn, [static ability]."
        DURING_YOUR_TURN_STATIC.matchEntire(line)?.let { m ->
            val inner = static(m.groupValues[1].replaceFirstChar { it.uppercase() }) as? Ability.Static ?: return null
            if (inner.condition != null) return null
            return inner.copy(condition = arcanum.api.Condition.YourTurn)
        }
        // chunk 36: "Nonland permanent cards in your graveyard have retrace."
        IN_YOUR_GRAVEYARD_HAVE.matchEntire(line)?.let { m ->
            val cards = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return Ability.Static(listOf(Mechanic.GainAbilities(keywords)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 45: "As long as [condition], [static ability]." / "[static ability] as long as [condition]."; "as long as this
        // card is in your graveyard" states the zone it works in (CR 113.6b)
        (AS_LONG_AS_FIRST.matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2].replaceFirstChar { c -> c.uppercase() } }
            ?: AS_LONG_AS_LAST.matchEntire(line)?.let { it.groupValues[2] to it.groupValues[1] + "." })?.let { (text, rest) ->
            val condition = conditionWithAnd(text)
            // dsk chunk 04: "As long as …, it gets +3/+3 and has hexproof.": "it" is this object
            val inner = condition?.let { static(if (rest.startsWith("It ")) "This permanent " + rest.removePrefix("It ") else rest) as? Ability.Static }
            if (inner != null && inner.condition == null) return inner.copy(condition = condition, functionsIn = zoneOf(condition))
        }
        // chunk 46: "Each player who has cast a nonartifact spell this turn can't cast additional nonartifact spells."
        CANT_CAST_ADDITIONAL.matchEntire(line)?.takeIf { it.groupValues[1] == it.groupValues[2] }?.let { m ->
            Filters.noun(m.groupValues[1])?.let { kind ->
                return Ability.Static(listOf(Mechanic.CantCast(kind)), affects = arcanum.api.Affected.Players(Players.Each(PlayerFilter.Any)),
                    condition = arcanum.api.Condition.HappenedThisTurn(EventPattern.Cast(kind, by = PlayerFilter.Is(Players.Affected)), atLeast = 1, measure = Measure.Events))
            }
        }
        // chunks 45-47: "This creature can't block and can't be blocked.", "Creatures you control have haste.", "Enchanted
        // creature gets +2/+2 and has flying and haste."
        SUBJECT_PREDICATES.matchEntire(line)?.let { m ->
            val subject = m.groupValues[1]
            val affects = when {
                isSelf(subject) -> arcanum.api.Affected.Objects(Objects.Self)
                subject == "Enchanted creature" -> arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self))
                // dsk chunk 05: "Equipped creature has menace."
                subject == "Equipped creature" -> arcanum.api.Affected.Objects(Objects.AttachedTo(of = Objects.Self))
                else -> Filters.noun(subject.replaceFirstChar { it.lowercase() })?.let { arcanum.api.Affected.Objects(Objects.Each(it)) }
            }
            val mechanics = predicates(m.groupValues[2])
            if (affects != null && mechanics != null) return Ability.Static(mechanics, affects = affects)
        }
        // chunk 46: "Each other creature you control enters with a number of additional +1/+1 counters on it equal to
        // Arwen's toughness." (CR 614.1c)
        EACH_OTHER_ENTERS_WITH.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val chosen = if (m.groupValues[2].isNotEmpty()) listOf(ObjectFilter.Sub(arcanum.api.SubtypeRef.Remembered(CHOSEN_TYPE))) else emptyList()
            val kind = Counters.kind(m.groupValues[3].ifEmpty { m.groupValues[4] }) ?: return null
            val amount = if (m.groupValues[5].isEmpty()) Value.Fixed(1) else selfNumber(m.groupValues[5]) ?: return null
            val entering = Filters.all(*(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter) + chosen).toTypedArray())
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(entering, to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind, amount)))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "As this creature enters, choose a creature type." (CR 614.12, 607.2d)
        AS_ENTERS_CHOOSE_TYPE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(arcanum.api.Choice.CreatureType, bind = CHOSEN_TYPE, remember = true, chooser = Players.You)))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "This creature is the chosen type in addition to its other types."
        IS_THE_CHOSEN_TYPE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Remembered(CHOSEN_TYPE)))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "Master of Etherium's power and toughness are each equal to the number of artifacts you control." (CR 604.3)
        PT_EACH_EQUAL_TO_NUMBER.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val count = Value.Count(Filters.objects(m.groupValues[2]) ?: return null)
            return Ability.Static(listOf(Mechanic.SetPowerToughness(count, count)), characteristicDefining = true, affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "Nonartifact spells cost {1} more to cast."
        SPELLS_COST.matchEntire(line)?.let { m ->
            val kind = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() } + " spell") ?: return null
            val spells = if (m.groupValues[2].isEmpty()) kind else Filters.all(kind, ObjectFilter.ControlledBy(PlayerFilter.You))
            val mana = ManaCost.parse(m.groupValues[3]) ?: return null
            val change = if (m.groupValues[4] == "more") arcanum.api.CostChange.More(mana, Value.Fixed(1)) else arcanum.api.CostChange.Less(mana, Value.Fixed(1))
            return Ability.Static(listOf(Mechanic.CostChange(spells, change = change)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 46: "Prevent all combat damage that would be dealt to attacking artifact creatures you control." (CR 615)
        PREVENT_ALL_COMBAT_DAMAGE_TO.matchEntire(line)?.let { m ->
            val to = Filters.noun(m.groupValues[1]) ?: return null
            return Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(to = arcanum.api.RecipientFilter(objects = to), combat = true, source = ObjectFilter.Any))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 45: "You may look at the top card of your library any time."
        if (line == "You may look at the top card of your library any time.")
            return Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true))), affects = arcanum.api.Affected.Players(Players.You))
        // chunk 45: "You may play lands from the top of your library."
        MAY_PLAY_FROM_TOP.matchEntire(line)?.let { m ->
            val how = if (m.groupValues[1] == "play") arcanum.api.PlayKind.Play else arcanum.api.PlayKind.Cast
            val cards = Filters.noun(m.groupValues[2].removeSuffix(" spells")) ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Library, how, topOnly = true, holder = PlayerFilter.You)), affects = arcanum.api.Affected.Objects(Objects.Each(
                Filters.all(cards, ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 45: "Once during each of your turns, you may cast a permanent spell with mana value 2 or less from your
        // graveyard." A permanent spell is cast from a permanent card (CR 110.4a).
        ONCE_EACH_TURN_CAST_FROM_GRAVEYARD.matchEntire(line)?.let { m ->
            val cards = Filters.noun(m.groupValues[1].replace(" spell", " card")) ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, perTurn = 1, how = PlayKind.Cast, holder = PlayerFilter.You)), condition = arcanum.api.Condition.YourTurn,
                affects = arcanum.api.Affected.Objects(Objects.Each(Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        }
        // chunk 45: "You may cast this card from your graveyard using its bestow ability."
        CAST_FROM_GRAVEYARD_USING.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val keyword = arcanum.api.KeywordKind.all.firstOrNull { it.text.lowercase() == m.groupValues[2] } ?: return null
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, viaAlternative = keyword, how = PlayKind.Cast, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 45
        if (line == "You can't spend mana to cast this spell.") return Ability.Static(listOf(Mechanic.CantSpendMana), affects = Affected.Objects(Objects.Self))
        // chunk 52: "If one or more -1/-1 counters would be put on a creature you control, that many -1/-1 counters minus one
        // are put on it instead." (CR 614.1a)
        COUNTERS_MINUS_ONE.matchEntire(line)?.takeIf { it.groupValues[1] == it.groupValues[3] }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            val on = Filters.noun(m.groupValues[2].removeArticle()) ?: return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.CountersPut(on, kind), modify = listOf(Modification.AddAmount(Value.Fixed(-1))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 52: "As this creature enters, choose a nonland card name." / "…, you may choose a nonland permanent." A choice
        // made as it enters, kept on it (CR 614.12, 607.2d)
        AS_ENTERS_CHOOSE.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val name = CARD_NAME.matchEntire(m.groupValues[3])
            val choose = when {
                name != null && m.groupValues[2].isEmpty() -> Filters.noun(name.groupValues[1])?.let {
                    Step.Choose(arcanum.api.Choice.CardName(it), bind = CHOSEN_NAME, remember = true, chooser = Players.You) }
                name == null -> Filters.noun(m.groupValues[3].removeArticle())?.let {
                    Step.Choose(arcanum.api.Choice.Objects(it, Value.Fixed(if (m.groupValues[2].isEmpty()) 1 else 0), Value.Fixed(1)), bind = CHOSEN, remember = true, chooser = Players.You) }
                else -> null
            }
            if (choose != null) return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(choose))))), affects = Affected.Objects(Objects.Self))
        }
        // chunk 52: "Activated abilities of sources with the chosen name can't be activated."
        CANT_BE_ACTIVATED.matchEntire(line)?.let { m ->
            chosenObjects(m.groupValues[1])?.let { return Ability.Static(listOf(Mechanic.CantBeActivated(manaAbilities = true)), affects = arcanum.api.Affected.Objects(it)) }
        }
        // chunk 50: "You may cast this creature from your graveyard by paying {B}{B} and sacrificing two creatures rather than
        // paying its mana cost." An alternative cost (CR 118.9) with a permission to cast from the graveyard.
        MAY_CAST_SELF_FROM_GRAVEYARD_BY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val parts = m.groupValues[2].split(" and ").map { gerund ->
                val verb = GERUNDS.keys.firstOrNull { gerund.startsWith("$it ") } ?: return null
                costPart((GERUNDS.getValue(verb) + " " + gerund.removePrefix("$verb ")).trim()) ?: return null
            }.flatten()
            return Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, cost = Cost(parts), how = PlayKind.Cast, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 56: "This creature can block only creatures with flying." (CR 509.1b)
        CAN_BLOCK_ONLY.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Ability.Static(listOf(Mechanic.CanBlockOnly(Filters.noun(m.groupValues[2]) ?: return null)), affects = Affected.Objects(Objects.Self))
        }
        // chunk 56: "Creatures you control can boast twice during each of your turns rather than once." (CR 702.142a)
        LABEL_TWICE_DURING_YOUR_TURNS.matchEntire(line)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val label = LABELS[m.groupValues[2]] ?: return null
            return Ability.Static(listOf(Mechanic.LabelUsesPerTurn(label, 2)), affects = arcanum.api.Affected.Objects(Objects.Each(filter)),
                condition = arcanum.api.Condition.YourTurn)
        }
        return null
    }

    /** chunk 52: what a choice made as this object entered refers to: "the chosen permanent" (the object kept), "sources
     * with the chosen name" (objects with the kept name, in every zone: cards in hand and graveyard have activated
     * abilities too). */
    private fun chosenObjects(phrase: String): Objects? = when (phrase) {
        "the chosen permanent" -> Objects.Remembered(CHOSEN)
        "sources with the chosen name" -> Objects.Each(Filters.all(ObjectFilter.RememberedName(CHOSEN_NAME),
            ObjectFilter.Or(EVERY_ZONE.map { ObjectFilter.InZone(it) })))
        else -> null
    }

    /** chunk 35: "your devotion to red" (CR 700.5). */
    private fun selfValue(text: String): Value? {
        DEVOTION_TO.matchEntire(text)?.let { m ->
            return Value.Devotion(m.groupValues[1].split(" and ").map { ((Filters.noun(it) as? ObjectFilter.OfColor)?.color as? ColorRef.Literal)?.color ?: return null }, players = Players.You)
        }
        return null
    }

    /** chunk 30: "first strike", "trample and haste": keywords named in a sentence. */
    private fun keywords(text: String): List<Ability>? =
        text.split(Regex(""",? and |, """)).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }

    /** chunk 41: what "for each [phrase]" counts: "+1/+1 counter on this Equipment" (its counters), or matching objects. */
    private fun perEach(phrase: String): Value? {
        COUNTER_ON.matchEntire(phrase)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            return Value.Of(arcanum.api.NumericProperty.Counters(Counters.kind(m.groupValues[1]) ?: return null), Objects.Self, arcanum.api.Combine.Single)
        }
        // chunk 52: "instant and sorcery card in your graveyard": each card that is either
        return (Filters.objects(phrase, self) ?: Filters.objects(phrase.replace(" and ", " or "), self))?.let { Value.Count(it) }
    }

    /** chunk 41: [n] times [per] (+1 per object is the count itself). */
    private fun times(per: Value, n: Int): Value = when (n) {
        0 -> Value.Fixed(0)
        1 -> per
        else -> Value.Times(per, n)
    }

    /** chunk 45: a condition, or several joined by "and" ("this card is in your graveyard and you control a Mountain"). */
    private fun conditionWithAnd(text: String): arcanum.api.Condition? {
        Conditions.condition(text, self)?.let { return it }
        val parts = text.split(" and ").takeIf { it.size > 1 } ?: return null
        return arcanum.api.Condition.And(parts.map { Conditions.condition(it, self) ?: return null })
    }

    /** chunk 45: the zone a condition on the object itself names ("this card is in your graveyard", "this card is
     * suspended"): an ability that states its zone works there (CR 113.6b). */
    private fun zoneOf(condition: arcanum.api.Condition?): List<Zone>? {
        val parts = (condition as? arcanum.api.Condition.And)?.all ?: listOfNotNull(condition)
        val filter = parts.firstNotNullOfOrNull { (it as? arcanum.api.Condition.ObjectMatches)?.takeIf { c -> c.objects == Objects.Self }?.filter } ?: return null
        val zone = ((filter as? ObjectFilter.And)?.all ?: listOf(filter)).firstNotNullOfOrNull { (it as? ObjectFilter.InZone)?.zone } ?: return null
        return listOf(zone)
    }

    /** chunks 45-47: "gets +2/+2", "has flying and haste", "attacks each combat if able", "can't block", "can't be
     * blocked", joined by commas and "and". */
    private fun predicates(text: String): List<Mechanic>? = text.split(PREDICATE_SPLIT).map { predicate ->
        GETS_PT.matchEntire(predicate)?.let { m ->
            val per = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Value.Count(Filters.objects(it) ?: return null) }
            fun amount(n: Int): Value = when {
                per == null || n == 0 -> Value.Fixed(n)
                n == 1 -> per
                else -> Value.Times(per, n)
            }
            Mechanic.ModifyPowerToughness(amount(m.groupValues[1].toInt()), amount(m.groupValues[2].toInt()))
        } ?: HAS_KEYWORDS.matchEntire(predicate)?.let { m ->
            Mechanic.GainAbilities(m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null })
        } ?: when (predicate) {
            "attacks each combat if able" -> Mechanic.MustAttack()
            "can't block" -> Mechanic.CantBlock(attackers = ObjectFilter.Any)
            "can't be blocked" -> Mechanic.CantBeBlocked
            else -> return null
        }
    }

    /** chunk 46: "Arwen's toughness": a number of the object itself. */
    private fun selfNumber(text: String): Value? {
        val m = SELF_PROPERTY.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) } ?: return null
        val property = if (m.groupValues[2] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.Toughness
        return Value.Of(property, Objects.Self, arcanum.api.Combine.Single)
    }

    /** "[cost]: [effect]" (CR 602.1), with "This ability costs {1} less to activate for each …" as a last sentence. */
    private fun activated(line: String): Ability? {
        val colon = line.indexOf(": ").takeIf { it > 0 } ?: return null
        var cost = cost(line.substring(0, colon)) ?: return null
        var text = line.substring(colon + 2)
        // chunk 25: "the sacrificed land" reads the sacrificed object as it last existed (CR 608.2h)
        if (text.contains("the sacrificed ")) cost = Cost(cost.parts.map { if (it is CostPart.Sacrifice) it.copy(preBind = SACRIFICED) else it })
        var costChange: arcanum.api.CostChange? = null
        var condition: arcanum.api.Condition? = null
        var timing: arcanum.api.Timing = arcanum.api.Timing.Any
        // chunk 40: "Activate only once each turn."
        var perTurn: Int? = null
        ACTIVATE_ONLY_ONCE_EACH_TURN.find(text)?.let { m ->
            perTurn = 1
            text = text.removeRange(m.range).trim()
        }
        // chunk 51: "Any player may activate this ability." (CR 602.2)
        var activatedBy: PlayerFilter = PlayerFilter.You
        ANY_PLAYER_MAY_ACTIVATE.find(text)?.let { m ->
            activatedBy = PlayerFilter.Any
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_AS_INSTANT.find(text)?.let { m ->
            timing = arcanum.api.Timing.Instant
            text = text.removeRange(m.range).trim()
        }
        ACTIVATE_ONLY_IF.find(text)?.let { m ->
            condition = Conditions.condition(m.groupValues[1], self) ?: return null
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
            val per = ManaCost.parse(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            costChange = arcanum.api.CostChange.Less(per, Value.Count(count))
            text = text.removeRange(m.range).trim()
        }
        // chunk 40: an X in the activation cost ("{X}", "Sacrifice X Squirrels") is announced as it is activated (CR 107.3a, k)
        val xInCost = X_IN_COST.containsMatchIn(line.substring(0, colon))
        announcedX = xInCost
        val parsed = try { instructions(text) } finally { announcedX = false }
        val instructions = parsed?.let { i ->
            if (xInCost || cost.parts.any { (it as? CostPart.Mana)?.cost?.symbols?.any { s -> s is arcanum.api.ManaSymbol.Variable } == true })
                i.copy(announced = listOf("X")) else i
        } ?: return null
        // chunk 20: "the sacrificed creature's power" reads the sacrificed object as it last was (CR 608.2h)
        val named = if (!text.contains("the sacrificed ")) cost else cost.copy(parts = cost.parts.map {
            if (it is CostPart.Sacrifice && it.what is CostObjects.Choose) it.copy(preBind = SACRIFICED) else it
        })
        // chunk 45: "Activate only if this card is suspended" states the zone it works in (CR 113.6b)
        return Ability.Activated(named, instructions, arcanum.api.ActivationRules(timing = timing, perTurn = perTurn, costChange = costChange, condition = condition,
            activatedBy = activatedBy),
            functionsIn = zoneOf(condition))
    }

    /** "Whenever [event], [if condition,] [effect]." (CR 603.1) */
    private fun triggered(line: String): Ability? {
        val m = WHENEVER.matchEntire(line) ?: return null
        var (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        var rest = m.groupValues[3]
        // chunk 50: "Whenever you cast a spell, if it's the second creature spell you cast this turn, …": which creature spell
        // of the turn it is can't change after it is cast, so the condition (CR 603.4) is the trigger event itself.
        NTH_SPELL_IF.matchEntire(rest)?.let { n ->
            val cast = trigger.events.singleOrNull() as? EventPattern.Cast
            if (cast != null && cast.spell == ObjectFilter.Any && cast.by == PlayerFilter.You && cast.from == null) {
                trigger = trigger.copy(events = listOf(cast.copy(spell = Filters.noun(n.groupValues[2]) ?: return null)),
                    nthThisTurn = Numbers.ordinal(n.groupValues[1]) ?: return null)
                rest = n.groupValues[3]
            }
        }
        // chunk 21: "This ability triggers only once each turn."
        var perTurn: Int? = null
        TRIGGERS_ONLY_ONCE.find(rest)?.let { once ->
            perTurn = 1
            rest = rest.removeRange(once.range).trim()
        }
        // dsk chunk 04: "This ability triggers only once." (for as long as its object exists)
        var once = false
        TRIGGERS_ONLY_ONCE_EVER.find(rest)?.let { m ->
            once = true
            rest = rest.removeRange(m.range).trim()
        }
        val conditional = INTERVENING_IF.matchEntire(rest)
        // dsk chunk 04: "if it had counters on it": the object of the event as it last existed (CR 603.10a)
        val interveningIf = conditional?.let {
            if (it.groupValues[1] == "it had counters on it") arcanum.api.Condition.ObjectMatches(that(trigger), ObjectFilter.HasCounter(null, atLeast = 1))
            else Conditions.condition(it.groupValues[1], self) ?: return null
        }
        // chunk 25: "it"/"them" in the effect: the event's object, or the objects the condition is about;
        // chunk 20: "that creature" is the event's object
        val about = (interveningIf as? arcanum.api.Condition.ObjectMatches)
        triggerIt = itOf(trigger.events)
        var instructions = instructions(conditional?.groupValues?.get(2) ?: rest, event = trigger.events.first(),
            them = about?.objects?.takeIf { it != Objects.Self }, that = that(trigger))
        triggerIt = null
        // chunk 33: X in this permanent's enters trigger is the X chosen for the spell it was (CR 107.3m)
        if (trigger.events == listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)) && !rest.contains("where X is"))
            instructions = instructions?.withX(Value.CastX(of = Objects.Self))
        if (instructions == null) return null
        // chunk 25: "one or more [objects]" triggers once for the batch; "if this card is in your graveyard" states the
        // zone the ability works in (CR 113.6b)
        val oneOrMore = trigger.oneOrMore || m.groupValues[2].startsWith("one or more ")
        // chunk 15: "return this card from your graveyard" works only there (CR 113.6m)
        val zone = (about?.takeIf { it.objects == Objects.Self }?.filter as? ObjectFilter.InZone)?.zone
            ?: Zone.Graveyard.takeIf { rest.contains("this card from your graveyard") }
        return Ability.Triggered(trigger.copy(oneOrMore = oneOrMore), instructions, interveningIf = interveningIf,
            functionsIn = zone?.let { listOf(it) }, perTurn = perTurn, triggersOnlyIf = onlyIf, once = once)
    }

    /** "Whenever [event], choose one that hasn't been chosen this turn —" with its "• " modes (CR 700.2). */
    // chunk 21
    fun modalTrigger(header: String, modeLines: List<String>): Ability? {
        val m = WHENEVER.matchEntire(header) ?: return null
        val (trigger, onlyIf) = trigger(m.groupValues[2]) ?: return null
        val choose = CHOOSE_MODES.matchEntire(m.groupValues[3]) ?: return null
        val n = Numbers.count(choose.groupValues[1]) ?: return null
        val options = modeLines.map { arcanum.api.Mode(instructions(it, that = that(trigger)) ?: return null, text = it) }
        if (options.isEmpty()) return null
        val unchosen = choose.groupValues[2].isNotEmpty()
        val thisTurn = choose.groupValues[3].isNotEmpty()
        return Ability.Triggered(trigger, Instructions(modes = arcanum.api.Modes(options, min = n, max = n,
            onlyUnchosen = unchosen && !thisTurn, onlyUnchosenThisTurn = thisTurn, chooser = Players.You)), triggersOnlyIf = onlyIf)
    }

    /** A trigger's events. "One or more …" triggers once per batch; "during your turn" is checked as it would trigger. */
    // chunk 21
    private fun trigger(text: String): Pair<arcanum.api.Trigger.On, arcanum.api.Condition?>? {
        val during = DURING_YOUR_TURN.matchEntire(text)
        val events = during?.groupValues?.get(1) ?: text
        val onlyIf = during?.let { arcanum.api.Condition.YourTurn }
        // dsk chunk 04: "the beginning of your second main phase": the first postcombat main phase of the turn (CR 505.1a, b)
        if (events == "the beginning of your second main phase")
            return arcanum.api.Trigger.On(listOf(EventPattern.StepBegins(arcanum.api.TurnStep.PostcombatMain, PlayerFilter.You)), nthThisTurn = 1) to onlyIf
        // dsk chunk 01: "one or more non-Toy creatures you control attack a player", "one or more creatures you control deal
        // combat damage to a player": one trigger per player (CR 603.2c by wording)
        ONE_OR_MORE_ATTACK_A_PLAYER.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(listOf(EventPattern.Attack(filter, target = RecipientFilter(players = PlayerFilter.Any))),
                oneOrMore = true, batchPerPlayer = true) to onlyIf
        }
        ONE_OR_MORE_DEAL_COMBAT_DAMAGE_TO_A_PLAYER.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(listOf(EventPattern.Damage(filter, RecipientFilter(players = PlayerFilter.Any), combat = true)),
                oneOrMore = true, batchPerPlayer = true) to onlyIf
        }
        ONE_OR_MORE_LEAVE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield), oneOrMore = true) to onlyIf
        }
        // chunk 40: "one or more other Elves you control enter"
        ONE_OR_MORE_ENTER.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.ZoneChange(filter, to = Zone.Battlefield), oneOrMore = true) to onlyIf
        }
        // chunk 52: "you discard one or more cards for the first time each turn": only the first such batch
        FOR_THE_FIRST_TIME_EACH_TURN.matchEntire(events)?.let { m ->
            val (first, _) = trigger(m.groupValues[1]) ?: return null
            return first.copy(nthThisTurn = 1) to onlyIf
        }
        // chunk 50: "you draw your third card in a turn"
        NTH_CARD_DRAWN.matchEntire(events)?.let { m ->
            return arcanum.api.Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = Numbers.ordinal(m.groupValues[1]) ?: return null) to onlyIf
        }
        YOU_CREATE_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            // CR 111.2: the player who creates a token is its owner.
            return arcanum.api.Trigger.On(EventPattern.CreateToken(Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You))), oneOrMore = true) to onlyIf
        }
        // chunk 56: "you create a Blood token": once per token (CR 111.2: its creator owns it)
        YOU_CREATE_A.matchEntire(events)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.CreateToken(Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You)))) to onlyIf
        }
        // chunk 47: "Whenever you attack" triggers once when one or more creatures you control are declared as attackers
        // (CR 508.3d)
        if (events == "you attack")
            return arcanum.api.Trigger.On(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You)), oneOrMore = true) to onlyIf
        // chunks 36, 47: "you discard one or more cards", "… nonland cards": once for the batch
        YOU_DISCARD_ONE_OR_MORE.matchEntire(events)?.let { m ->
            val cards = if (m.groupValues[1] == "cards") ObjectFilter.Any else Filters.noun(m.groupValues[1]) ?: return null
            return arcanum.api.Trigger.On(EventPattern.Discard(cards, PlayerFilter.You), oneOrMore = true) to onlyIf
        }
        // chunk 47: "an opponent casts their first noncreature spell each turn"
        NTH_SPELL_EACH_TURN.matchEntire(events)?.let { m ->
            val by = if (m.groupValues[1] == "an opponent") PlayerFilter.Opponent else PlayerFilter.Any
            val n = Numbers.ordinal(m.groupValues[2]) ?: return null
            val kind = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            return arcanum.api.Trigger.On(listOf(EventPattern.Cast(kind, by)), nthThisTurn = n) to onlyIf
        }
        return Events.events(events, self)?.let { arcanum.api.Trigger.On(it) to onlyIf }
    }

    /** "That creature", "that card", "that permanent" in a triggered ability: the object of its (first) event. */
    // chunk 20
    private fun that(trigger: arcanum.api.Trigger.On): Objects = when (val event = trigger.events.first()) {
        is EventPattern.ZoneChange -> Objects.EventObject(if (event.to == Zone.Battlefield) arcanum.api.EventRole.After else arcanum.api.EventRole.Before)
        is EventPattern.Discard -> Objects.EventObject(arcanum.api.EventRole.After)
        is EventPattern.Unattach -> Objects.EventObject(arcanum.api.EventRole.From)
        else -> Objects.EventObject(role = EventRole.Object)
    }

    /** chunk 31: "it" in a trigger on this object moving: the permanent that entered, or the new card in a graveyard
     * (CR 400.7). */
    private fun itOf(events: List<EventPattern>): Pair<Objects, Zone?>? {
        val move = events.singleOrNull() as? EventPattern.ZoneChange ?: return null
        if (move.objects != ObjectFilter.Is(Objects.Self)) return null
        return when (move.to) {
            Zone.Battlefield -> Objects.Self to Zone.Battlefield
            Zone.Graveyard -> Objects.EventObject(arcanum.api.EventRole.After) to Zone.Graveyard
            else -> null
        }
    }

    /** Cost parts separated by ", " (CR 602.1a). */
    fun cost(text: String): Cost? {
        // chunk 45: "Sacrifice an artifact, creature, or land": a piece starting in lower case continues the list before it
        val pieces = text.split(", ").fold(mutableListOf<String>()) { acc, piece ->
            if (acc.isNotEmpty() && piece.firstOrNull()?.isLowerCase() == true) acc[acc.size - 1] = acc.last() + ", " + piece else acc += piece
            acc
        }
        val parts = pieces.map { costPart(it) ?: return null }
        if (parts.isEmpty()) return null
        return Cost(parts.flatten())
    }

    private fun costPart(text: String): List<CostPart>? {
        if (text == "{T}") return listOf(CostPart.TapSelf)
        // chunk 26: "[+1]", "[−2]", "[0]" (CR 606.4)
        LOYALTY.matchEntire(text)?.let { m ->
            val n = m.groupValues[2].toInt()
            return listOf(CostPart.Loyalty(Value.Fixed(if (m.groupValues[1].isEmpty() || m.groupValues[1] == "+") n else -n)))
        }
        ManaCost.parse(text)?.let { return listOf(CostPart.Mana(it)) }
        PAY_LIFE.matchEntire(text)?.let { return listOf(CostPart.PayLife(Value.Fixed(it.groupValues[1].toInt()))) }
        // chunk 36: "pay {1}" (in "you may pay {1} and discard a card")
        PAY_MANA.matchEntire(text)?.let { m -> ManaCost.parse(m.groupValues[1])?.let { return listOf(CostPart.Mana(it)) } }
        // chunk 14: "Flashback—{R}{R}, Discard X cards.": X in a cost of the card's own keyword is the X chosen as the
        // spell is cast (CR 107.3a, Value.CastX). Keyword costs are read with only "this card" as the self name; an
        // activated ability's X stays unread here.
        if (text == DISCARD_X_CARDS && self == setOf("this card"))
            return listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = Value.CastX(of = Objects.Self))))
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
            return listOf(CostPart.PutCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), on = CostObjects.Self))
        }
        RETURN_TO_HAND_COST.matchEntire(text)?.let { m ->
            val (count, noun) = counted(m.groupValues[1]) ?: return null
            return listOf(CostPart.ReturnToHand(CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))))
        }
        EXILE_FROM_HAND_COST.matchEntire(text)?.let { m ->
            return listOf(CostPart.Exile(CostObjects.Choose(Filters.noun(m.groupValues[1]) ?: return null, count = Value.Fixed(1)), from = Zone.Hand))
        }
        // chunks 30, 41: "Exile eight other cards from your graveyard", "Exile two cards from your graveyard",
        // "Exile another creature card from your graveyard"
        EXILE_FROM_GRAVEYARD_COST.matchEntire(text)?.let { m ->
            val phrase = m.groupValues[1]
            val what = when {
                isSelf(phrase) -> CostObjects.Self
                phrase.contains(" other ") -> {
                    val (count, noun) = counted(phrase) ?: return null
                    val filter = Filters.noun(noun.removePrefix("other ")) ?: return null
                    CostObjects.Choose(Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter), count = Value.Fixed(count))
                }
                else -> costChoice(phrase) ?: return null
            }
            return listOf(CostPart.Exile(what, from = Zone.Graveyard))
        }
        // chunk 31: "exile it" in a trigger on this object moving
        if (text == "Exile it") triggerIt?.let { (it, zone) -> return listOf(CostPart.Exile(CostObjects.These(it), from = zone ?: return null)) }
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            // chunk 40: counted ("two artifacts", "X Squirrels", "another creature")
            return listOf(CostPart.Sacrifice(if (isSelf(what)) CostObjects.Self else costChoice(what) ?: return null))
        }
        // chunk 21: "Remove two charge counters from this artifact"
        REMOVE_COUNTERS_FROM_SELF.matchEntire(text)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(CostPart.RemoveCounters(kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), from = CostObjects.Self))
        }
        // chunk 45: "Collect evidence 6" (CR 701.59a)
        COLLECT_EVIDENCE.matchEntire(text)?.let { m ->
            return listOf(CostPart.Action(arcanum.api.KeywordAction.CollectEvidence(Value.Fixed(m.groupValues[1].toInt()))))
        }
        return null
    }

    /** chunk 40: the objects a cost chooses: "a Forest or Plains", "two artifacts", "X Squirrels" (X announced, CR 107.3a),
     * "another creature". */
    private fun costChoice(phrase: String): CostObjects.Choose? {
        if (phrase.startsWith("X ")) return CostObjects.Choose(Filters.noun(phrase.removePrefix("X ")) ?: return null, count = Value.Announced(name = "X"))
        val (count, noun) = counted(phrase) ?: return null
        return CostObjects.Choose(Filters.noun(noun) ?: return null, count = Value.Fixed(count))
    }

    /** "a card" → 1 and "card"; "two cards" → 2 and "cards". */
    private fun counted(phrase: String): Pair<Int, String>? {
        val first = phrase.substringBefore(' ')
        val count = if (first == "a" || first == "an") 1 else Numbers.count(first) ?: return 1 to phrase
        return count to phrase.substringAfter(' ')
    }

    /** The effect sentences of a spell or ability, in order. */
    fun instructions(text: String, seedTargets: List<TargetSlot> = emptyList(), event: EventPattern? = null, them: Objects? = null,
                     that: Objects? = null): Instructions? {
        // chunk 30: "…, where X is [a number]" defines X for the whole ability (CR 107.3c)
        // An X this reader can't define stays in the text for the sentence rules that read it themselves.
        val x = WHERE_X.find(text)?.let { Values.value(it.groupValues[1], exiledCard) }
        val whereX = WHERE_X.find(text)?.takeIf { x != null }
        val context = Context()
        context.targets += seedTargets
        // chunk 25
        context.event = event
        context.it = event?.let { if (it is EventPattern.ZoneChange) Objects.EventObject(arcanum.api.EventRole.After) else Objects.EventObject(role = EventRole.Object) }
        // chunk 30: a step has no object, so "it" is this object ("return it to your hand")
        if (event is EventPattern.StepBegins) context.it = Objects.Self
        // chunk 36: "Whenever this creature attacks, put a +1/+1 counter on it": it is this creature
        if (event is EventPattern.Attack && event.attacker == ObjectFilter.Is(Objects.Self)) context.it = Objects.Self
        context.them = them
        // chunk 20
        context.that = that
        val steps = mutableListOf<Step>()
        for (sentence in sentences(if (whereX == null) text else text.removeRange(whereX.range))) {
            val modified = modify(sentence, steps, context)
            if (modified != null) { steps.clear(); steps += modified; continue }
            steps += effect(sentence, context) ?: return null
        }
        val result = Instructions(targets = context.targets, steps = steps, replacements = context.replacements)
        return if (x == null) result else result.withX(x)
    }

    /** Sentences that change the step before them ("If this spell was kicked, it deals 4 damage instead.", "It can't be
     * regenerated."). */
    private fun modify(sentence: String, steps: List<Step>, context: Context): List<Step>? {
        // chunk 26: "Untap those creatures." after an effect on them
        if (sentence == "Untap those creatures.") {
            val apply = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.ApplyMechanics) ?: return null
            val objects = (apply.affects as? arcanum.api.Affected.Objects)?.objects ?: return null
            return steps + Step.Do(Effect.Untap(objects))
        }
        // chunk 26: "They gain haste." / "Sacrifice them at the beginning of the next end step." after creating tokens
        THEY_GAIN.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val bound = bindTokens(steps) ?: return null
            return bound + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)),
                arcanum.api.Affected.Objects(Objects.Bound(TOKENS)), arcanum.api.Duration.Indefinitely))
        }
        if (sentence == "Sacrifice them at the beginning of the next end step.") {
            val bound = bindTokens(steps) ?: return null
            // CR 603.7: a delayed trigger, triggering once at the next end step
            return bound + Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound(TOKENS)))))))
        }
        // chunk 36: "You may put a land card from among them into your hand." after milling: zero or one of the milled cards
        MAY_PUT_FROM_AMONG_THEM_INTO_HAND.matchEntire(sentence)?.let { m ->
            val mill = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.Mill)?.takeIf { it.postBind == null } ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return steps.dropLast(1) + Step.Do(mill.copy(postBind = "milled")) +
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You) +
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand)))
        }
        // chunk 36: "If it entered under your control, …" after a delayed trigger that returns a card: part of that trigger
        IF_IT_ENTERED_UNDER_YOUR_CONTROL.matchEntire(sentence)?.let { m ->
            val delayed = ((steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateDelayedTrigger) ?: return null
            val returned = ((delayed.instructions.steps.lastOrNull() as? Step.Do)?.effect as? Effect.Move)?.postBind ?: return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            val iff = Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound(returned), ObjectFilter.ControlledBy(PlayerFilter.You)), then = then)
            return steps.dropLast(1) + Step.Do(delayed.copy(instructions = delayed.instructions.copy(steps = delayed.instructions.steps + iff)))
        }
        // chunk 25: "If …, …. Otherwise, …."
        OTHERWISE.matchEntire(sentence)?.let { m ->
            val iff = (steps.lastOrNull() as? Step.If)?.takeIf { it.otherwise.isEmpty() } ?: return null
            val otherwise = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return steps.dropLast(1) + iff.copy(otherwise = otherwise)
        }
        // chunk 33: "If this creature was kicked, copy that spell twice instead." after a delayed copy trigger
        COPY_TWICE_IF_KICKED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            val delayed = (steps.lastOrNull() as? Step.Do)?.effect as? Effect.CreateDelayedTrigger ?: return null
            val twice = delayed.copy(instructions = delayed.instructions.copy(steps = listOf(Step.Repeat(Value.Fixed(2), delayed.instructions.steps))))
            return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind, times = 1, of = Objects.Self),
                then = listOf(Step.Do(twice)), otherwise = listOf(Step.Do(delayed)))
        }
        // chunk 33: "You may choose new targets for the copies." (CR 707.10c)
        if (sentence == "You may choose new targets for the copies." && steps.isNotEmpty())
            return steps.map { withNewTargets(it) ?: return null }
        val previous = (steps.lastOrNull() as? Step.Do)?.effect
        // chunk 32: "The token enters tapped and attacking." (CR 508.4: its controller chooses what it attacks)
        if (previous is Effect.CreateToken && sentence == "The token enters tapped and attacking.")
            return steps.dropLast(1) + Step.Do(previous.copy(tapped = true, attacking = arcanum.api.AttackTarget.Chosen))
        // chunk 30: "Each opponent discards a card. For each opponent who can't, you draw a card." Each opponent's
        // discard is a mandatory payment, so "can't" is known per opponent (CR 118.12).
        if (previous is Effect.Discard && previous.players == Players.Each(PlayerFilter.Opponent) && previous.objects == null &&
            sentence == "For each opponent who can't, you draw a card.") {
            val discard = CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = previous.count))
            return steps.dropLast(1) + Step.ForEach(arcanum.api.Each.Players(previous.players), bind = "opponent", together = true, steps = listOf(
                Step.Pay(Cost(listOf(discard)), payer = Players.Bound("opponent"), mandatory = true, bind = "discarded"),
                Step.If(arcanum.api.Condition.CouldNot("discarded"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))))))
        }
        // chunk 52: "Put that card into your hand and the rest on the bottom of your library in a random order." after
        // revealing until a card is found, inside the same condition
        PUT_FOUND_IN_HAND_REST_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            val moves = listOf(Step.Do(Effect.Move(Objects.Bound(FOUND), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Bound(REST), Destination(Zone.Library, libraryPosition = -1, randomOrder = m.groupValues[1] == "a random"))))
            fun revealsUntil(step: Step?) = ((step as? Step.Do)?.effect as? Effect.RevealUntil)?.foundBind == FOUND
            val previous = steps.lastOrNull()
            return when {
                revealsUntil(previous) -> steps + moves
                previous is Step.If && previous.otherwise.isEmpty() && revealsUntil(previous.then.lastOrNull()) ->
                    steps.dropLast(1) + previous.copy(then = previous.then + moves)
                else -> null
            }
        }
        val last = steps.lastOrNull() as? Step.Do ?: return null
        val effect = last.effect
        // chunk 56: "Add {R}. Until end of turn, you don't lose this mana as steps and phases end." (CR 106.4)
        if (sentence == "Until end of turn, you don't lose this mana as steps and phases end." && effect is Effect.AddMana) {
            return steps.dropLast(1) + Step.Do(effect.copy(mana = effect.mana.copy(keptUntil = arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56: "You may cast target … card from your graveyard this turn. If that spell would be put into your
        // graveyard, exile it instead.": the replacement goes with the permission, to the spell cast with it
        if (sentence == "If that spell would be put into your graveyard, exile it instead." && effect is Effect.ApplyMechanics) {
            val may = effect.mechanics.singleOrNull() as? Mechanic.MayPlay ?: return null
            return steps.dropLast(1) + Step.Do(effect.copy(mechanics = listOf(may.copy(then = Destination(Zone.Exile), thenInsteadOf = Zone.Graveyard))))
        }
        // chunk 12
        // "If you search your library this way, shuffle.": only when an earlier search could search the library.
        if (sentence == "If you search your library this way, shuffle.") {
            val search = steps.mapNotNull { (it as? Step.Do)?.effect as? Effect.Search }.lastOrNull() ?: return null
            if (Zone.Library !in search.zones || search.zones.size < 2) return null
            return steps + Step.If(arcanum.api.Condition.Searched(search.bind ?: return null, Zone.Library),
                then = listOf(Step.Do(Effect.Shuffle(search.library))))
        }
        // "Create [a token] for each card type among cards discarded this way.": names the discarded cards.
        if (effect is Effect.Discard && effect.postBind == null) CREATE_FOR_EACH_CARD_TYPE_DISCARDED.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            if (count != 1) return null
            return steps.dropLast(1) + Step.Do(effect.copy(postBind = DISCARDED)) + Step.Do(Effect.CreateToken(token,
                count = Value.CountDistinct(Objects.Bound(DISCARDED), arcanum.api.SharedQuality.CardType), controller = Players.You))
        }
        // "Draw a card for each card returned to your hand this way.": names the returned objects.
        if (effect is Effect.Move && effect.to.zone == Zone.Hand && effect.postBind == null) DRAW_FOR_EACH_RETURNED_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
            return steps.dropLast(1) + Step.Do(effect.copy(postBind = RETURNED)) +
                Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Where(Objects.Bound(RETURNED), filter))))
        }
        if (effect is Effect.DealDamage) {
            KICKED_DEALS_INSTEAD.matchEntire(sentence)?.let { m ->
                return steps.dropLast(1) + Step.If(arcanum.api.Condition.Paid(arcanum.api.Keyword.Kicker.Kind, times = 1, of = Objects.Self),
                    then = listOf(Step.Do(effect.copy(amount = Value.Fixed(m.groupValues[1].toInt())))), otherwise = listOf(last))
            }
            if (sentence == "A creature dealt damage this way can't be regenerated this turn.") {
                val damaged = effect.to.target?.let { Objects.Target(it) } ?: effect.to.objects ?: return null
                return steps + Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeRegenerated),
                    arcanum.api.Affected.Objects(Objects.Where(damaged, ObjectFilter.Type(arcanum.api.CardType.Creature))), arcanum.api.Duration.EndOfTurn))
            }
        }
        // chunk 14: "If this spell was cast from a graveyard, copy that spell twice instead." (CR 707.10)
        if (effect is Effect.CopySpell && sentence == "If this spell was cast from a graveyard, copy that spell twice instead.") {
            return steps.dropLast(1) + Step.If(arcanum.api.Condition.CastFrom(Zone.Graveyard, of = Objects.Self),
                then = listOf(Step.Repeat(Value.Fixed(2), listOf(last))), otherwise = listOf(last))
        }
        if (effect is Effect.Destroy && sentence in setOf("It can't be regenerated.", "A creature destroyed this way can't be regenerated.")) {
            return steps.dropLast(1) + Step.Do(effect.copy(noRegeneration = true))
        }
        // chunk 20: "If the sacrificed creature's toughness was 4 or greater, create two Food tokens instead."
        IF_INSTEAD.matchEntire(sentence)?.let { m ->
            // chunk 35: "If the creature had power 4 or greater": the trigger event's object as it last existed (CR 603.10a)
            val had = THE_CREATURE_HAD.matchEntire(m.groupValues[1])?.let { h ->
                val n = Value.Fixed(h.groupValues[2].toInt())
                arcanum.api.Condition.ObjectMatches(context.that ?: return null,
                    if (h.groupValues[1] == "power") ObjectFilter.Power(arcanum.api.Comparison.AtLeast, n) else ObjectFilter.Toughness(arcanum.api.Comparison.AtLeast, n))
            }
            val condition = had ?: Conditions.condition(m.groupValues[1], self) ?: return null
            // chunk 35: "create two of those tokens instead": the same token, another number
            val more = OF_THOSE_TOKENS.matchEntire(m.groupValues[2])?.let { t ->
                val create = effect as? Effect.CreateToken ?: return null
                listOf(Step.Do(create.copy(count = Value.Fixed(Numbers.count(t.groupValues[1]) ?: return null))))
            }
            val then = more ?: effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return steps.dropLast(1) + Step.If(condition, then = then, otherwise = listOf(last))
        }
        return null
    }

    private class Context {
        var flip = false
        // chunk 25: the triggering event; what "it" and "them" refer to; the amount "that much" refers to
        var event: EventPattern? = null
        var it: Objects? = null
        var them: Objects? = null
        var thatMuch: Value? = null
        // chunk 20: the event's object in a trigger ("that creature")
        var that: Objects? = null
        // dsk chunk 01: the creature manifested ("that creature" after "manifest dread")
        var manifested: Objects? = null
        val replacements = mutableListOf<Mechanic.Replacement>()
        val targets = mutableListOf<TargetSlot>()
        /** "any target" (CR 115.4): creature, planeswalker or battle, or player. */
        fun anyTarget() {
            targets += TargetSlot("any", arcanum.api.TargetKind.Either(ObjectFilter.Or(listOf(
                ObjectFilter.Type(arcanum.api.CardType.Creature), ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
                ObjectFilter.Type(arcanum.api.CardType.Battle))), PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
        }

        /** "target player" */
        fun targetPlayer(): Players {
            if (targets.none { it.name == "player" }) targets += TargetSlot("player", arcanum.api.TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
            return Players.Target("player")
        }

        fun target(noun: String, filter: ObjectFilter): Objects {
            targets += TargetSlot(noun, TargetKind.Objects(filter), min = Value.Fixed(1), max = Value.Fixed(1))
            return Objects.Target(noun)
        }

        // chunk 13
        /** The player whose hand was revealed ("that player"), the revealed hand ("from it"), the names of the cards
         * chosen ("that card", "the chosen cards") and of the chosen card name ("that name"). */
        var player: Players? = null
        var revealed: Objects? = null
        val chosen = mutableListOf<String>()
        var cardName: String? = null

        /** "target opponent" */
        fun targetOpponent(): Players {
            if (targets.none { it.name == "opponent" }) targets += TargetSlot("opponent", arcanum.api.TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))
            return Players.Target("opponent").also { lastPlayer = it }
        }

        // chunk 14
        /** A copy was made ("You may choose new targets for the copies"); who may pay ("If the player does"). */
        var copied = false
        var payer: Players? = null

        // chunk 31: the zone "that card" is in; the cards looked at or revealed and the one picked from among them.
        var itZone: Zone? = null
        var seen: String? = null
        var picked: String? = null
        /** chunk 33: the player whose hand "it" is ("look at target opponent's hand. You may exile a card from it"). */
        var lastPlayer: Players? = null

        /** "That player": the controller of the last object target. */
        fun lastTargetController(): Players? = targets.lastOrNull()?.let {
            if (it.what is arcanum.api.TargetKind.Players) Players.Target(it.name) else Players.ControllerOf(Objects.Target(it.name))
        }

        // chunk 35
        /** The trigger is this object leaving the battlefield for a graveyard ("When this creature dies"). */
        fun selfDied(): Boolean = (event as? EventPattern.ZoneChange)?.let { it.to == Zone.Graveyard && it.from == Zone.Battlefield &&
            it.objects == ObjectFilter.Is(Objects.Self) } == true

        /** "another": not this object; once it has died, not the new card it became in the graveyard (CR 400.7, 603.6e). */
        fun another(): ObjectFilter = ObjectFilter.Not(ObjectFilter.Is(if (selfDied()) Objects.SelfAfter else Objects.Self))
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
        // "Draw two cards. Then discard a card …": a sentence that starts with "Then" follows the one before it.
        if (sentence.startsWith("Then ")) return effect(sentence.removePrefix("Then ").replaceFirstChar { it.uppercase() }, context)
        // dsk chunk 01: "Tap up to one target creature." ("it" afterwards is that creature)
        TAP_TARGET.matchEntire(sentence)?.takeIf { Filters.noun(it.groupValues[3]) != null }?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun)!!
            val name = Filters.slotName(noun)
            val max = if (m.groupValues[1].isEmpty()) 1 else Numbers.count(m.groupValues[1]) ?: return null
            context.targets += TargetSlot(name, TargetKind.Objects(if (m.groupValues[2].isEmpty()) filter else Filters.all(context.another(), filter)),
                min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0), max = Value.Fixed(max))
            context.it = Objects.Target(name)
            return listOf(Step.Do(Effect.Tap(Objects.Target(name))))
        }
        // dsk chunk 01: "Put a +1/+1 counter on each of up to two target creatures."
        PUT_COUNTERS_ON_UP_TO.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[4]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0),
                max = Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null))
            return listOf(Step.Do(Effect.PutCounters(Objects.Target(name), Counters.kind(m.groupValues[2]) ?: return null,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // dsk chunk 01: "Return up to one target creature to its owner's hand."
        RETURN_UP_TO_TARGET_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0),
                max = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
            return listOf(Step.Do(Effect.Move(Objects.Target(name), Destination(Zone.Hand))))
        }
        // dsk chunk 01: "You win the game if there are eight or more …"
        YOU_WIN_IF.matchEntire(sentence)?.let { m ->
            return listOf(Step.If(Conditions.condition(m.groupValues[1], self) ?: return null, then = listOf(Step.Do(Effect.WinGame(Players.You)))))
        }
        // dsk chunk 01: "Manifest dread." ("that creature" afterwards is the manifested permanent, CR 701.62a)
        if (sentence == "Manifest dread.") {
            context.manifested = Objects.Bound(MANIFESTED)
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.ManifestDread(Players.You), bind = MANIFESTED)))
        }
        // dsk chunk 01: "Put a +1/+1 counter on that creature." after manifesting dread
        PUT_COUNTERS.matchEntire(sentence)?.takeIf { it.groupValues[3] == "that creature" && context.manifested != null }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(context.manifested!!, kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // dsk chunk 02: "It gets +1/+0 and gains deathtouch until end of turn." ("it": the event's object, or this object)
        GETS_AND_GAINS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val objects = when {
                isSelf(m.groupValues[1]) -> Objects.Self
                m.groupValues[1] == "It" -> context.it
                m.groupValues[1] == "That creature" -> context.that
                else -> null
            }
            val keywords = m.groupValues[4].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) }
            if (objects != null && keywords.all { it != null }) return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null),
                    Mechanic.GainAbilities(keywords.filterNotNull())), arcanum.api.Affected.Objects(objects), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 02: "[This Room] deals damage equal to the number of cards in your hand to target creature an opponent
        // controls."
        DEALS_DAMAGE_EQUAL_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            Values.value(m.groupValues[2], exiledCard)?.let { amount ->
                val what = m.groupValues[3]
                val to = when {
                    what == "any target" -> { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                    what.startsWith("target ") -> what.removePrefix("target ").let { noun ->
                        arcanum.api.Recipients(objects = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)) }
                    else -> arcanum.api.Recipients(players = Filters.players(what) ?: return null)
                }
                return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
            }
        }
        // dsk chunk 02: "You may play it this turn." / "You may play them until the end of your next turn." (the exiled cards)
        YOU_MAY_PLAY_EXILED.matchEntire(sentence)?.let { m ->
            val exiled = (if (m.groupValues[1] == "them") context.them else context.it) as? Objects.Bound ?: return null
            val duration = if (m.groupValues[2] == "this turn") arcanum.api.Duration.EndOfTurn else arcanum.api.Duration.UntilEndOfYourNextTurn
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(exiled), duration)))
        }
        // dsk chunk 02: "Mill four cards, then return up to two permanent cards from among them to your hand."
        MILL_THEN_RETURN_FROM_AMONG_THEM.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val most = Numbers.count(m.groupValues[2]) ?: return null
            val filter = Filters.noun(m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(n), postBind = "milled")),
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(most), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // dsk chunk 02: "You may cast spells from your graveyard this turn, and if a card would be put into your graveyard from
        // anywhere this turn, exile it instead." A permission and a replacement for the turn (CR 611.2c, 614.1a).
        if (sentence == "You may cast spells from your graveyard this turn, and if a card would be put into your graveyard from anywhere this turn, exile it instead.") {
            return listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, arcanum.api.PlayKind.Cast, holder = PlayerFilter.You)),
                    arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    arcanum.api.Duration.EndOfTurn)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                    modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 02: "Add {R}{R}. Spend this mana only to cast Room spells and unlock doors." (CR 106.6)
        ADD_MANA_SPEND_ONLY_TO.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = spendOnlyTo(m.groupValues[2]) ?: return null), players = Players.You)))
        }
        // dsk chunk 03: "Unlock a locked door of up to one target Room you control." / "… of a Room you control." (CR 709.5f)
        UNLOCK_A_LOCKED_DOOR.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun) ?: return null
            if (m.groupValues[1].isEmpty() && m.groupValues[2] != "target ") {
                return listOf(
                    Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, HAS_A_LOCKED_DOOR), Value.Fixed(1), Value.Fixed(1)), bind = "room", chooser = Players.You),
                    Step.Do(Effect.UnlockDoor(Objects.Bound("room"), chooser = Players.You)))
            }
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0),
                max = Value.Fixed(m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Numbers.count(it) ?: return null } ?: 1))
            return listOf(Step.Do(Effect.UnlockDoor(Objects.Target(name), chooser = Players.You)))
        }
        // dsk chunk 03: "Lock or unlock a door of target Room you control." (CR 709.5f, g; one of the two, chosen as it resolves)
        LOCK_OR_UNLOCK.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("lock", "unlock")), bind = "lockOrUnlock", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("lockOrUnlock", "lock"),
                    then = listOf(Step.Do(Effect.LockDoor(target, chooser = Players.You))),
                    otherwise = listOf(Step.Do(Effect.UnlockDoor(target, chooser = Players.You)))))
        }
        // dsk chunk 03: "Return an enchantment card from your graveyard to your hand or unlock a locked door of a Room you
        // control.": one of the two, chosen as it resolves
        RETURN_OR_UNLOCK.matchEntire(sentence)?.let { m ->
            val card = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val unlock = effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("return", "unlock")), bind = "returnOrUnlock", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("returnOrUnlock", "return"),
                    then = listOf(Step.Choose(arcanum.api.Choice.Objects(card, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                        Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand)))),
                    otherwise = unlock))
        }
        // dsk chunk 03: "This creature can attack this turn as though it didn't have defender." (CR 702.3b)
        CAN_ATTACK_AS_THOUGH_NO_DEFENDER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CanAttackDespiteDefender), arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 03: "Surveil 2 if this is the first time this ability has resolved this turn."
        EFFECT_IF.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self)
            val then = condition?.let { effect(m.groupValues[1] + ".", context) }
            if (condition != null && then != null) return listOf(Step.If(condition, then = then))
        }
        // dsk chunk 03: "Put a creature card from a graveyard onto the battlefield under your control." (chosen as it resolves)
        PUT_CARD_FROM_A_GRAVEYARD_ONTO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard))
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))))
        }
        // dsk chunk 03: "Put all enchantment cards from among them into your hand and the rest on the bottom of your library in a
        // random order." (no choice)
        PUT_ALL_FROM_AMONG_THEM_REST_BOTTOM.matchEntire(sentence)?.let { m ->
            val them = Objects.Bound(context.seen ?: return null)
            val chosen = Objects.Where(them, Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.Move(chosen, Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(them, chosen), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // dsk chunk 04: "Put a flying, lifelink, or +1/+1 counter on it.": the kind chosen as it resolves
        PUT_ONE_OF_COUNTERS.matchEntire(sentence)?.let { m ->
            val kinds = m.groupValues[1].split(Regex(""",? or |, """))
            val objects = if (m.groupValues[2] == "it") context.it ?: return null else if (isSelf(m.groupValues[2])) Objects.Self else return null
            return listOf(Step.Choose(arcanum.api.Choice.Option(kinds), bind = "counter", chooser = Players.You)) + kinds.map { k ->
                Step.If(arcanum.api.Condition.Chose("counter", k), then = listOf(Step.Do(Effect.PutCounters(objects, Counters.kind(k) ?: return null, Value.Fixed(1)))))
            }
        }
        // dsk chunk 04: "Put those counters on up to one target creature." (the counters the event's object had)
        PUT_THOSE_COUNTERS_ON.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null),
                min = Value.Fixed(if (m.groupValues[1].isEmpty()) 1 else 0), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.PutCountersOf(Objects.Target(name), of = that)))
        }
        // dsk chunk 04: "That land becomes a 0/0 Elemental creature in addition to its other types." (for good, CR 611.2a)
        THAT_BECOMES_CREATURE.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull()?.takeIf { it.name == Filters.slotName(m.groupValues[1]) }?.let { Objects.Target(it.name) } ?: return null
            val subtype = m.groupValues[4].takeIf { it in RulesWords.subtypes } ?: return null
            context.it = target
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[2].toInt()), Value.Fixed(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.Indefinitely)))
        }
        // dsk chunk 04: "It gains haste until your next turn."
        IT_GAINS_UNTIL_YOUR_NEXT_TURN.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[1].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)), arcanum.api.Affected.Objects(context.it ?: return null),
                arcanum.api.Duration.UntilYourNextTurn)))
        }
        // dsk chunk 04: "Put any number of creature and/or Vehicle cards with different powers from among them into your hand."
        PUT_ANY_NUMBER_WITH_DIFFERENT_FROM_AMONG.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val property = if (m.groupValues[2] == "powers") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.ManaValue
            context.picked = "chosen"
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = null, from = Objects.Bound(seen),
                    distinct = arcanum.api.SharedQuality.Number(property)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // dsk chunk 04: "Put the rest on the bottom of your library in a random order." (after picking from among them)
        if (sentence == "Put the rest on the bottom of your library in a random order." && context.seen != null && context.picked != null) {
            return listOf(Step.Do(Effect.Move(Objects.Except(Objects.Bound(context.seen!!), Objects.Bound(context.picked!!)),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // dsk chunk 05: "Its controller manifests dread." (the last target's), "The exiled card's owner manifests dread." (CR 701.62a)
        PLAYER_MANIFESTS_DREAD.matchEntire(sentence)?.let { m ->
            val player = when (m.groupValues[1]) {
                "Its controller" -> context.lastTargetController()
                "The exiled card's owner" -> exiledCard?.let { Players.OwnerOf(it) }
                else -> null
            } ?: return null
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.ManifestDread(player))))
        }
        // dsk chunk 05: "Attach this Equipment to that creature." after manifesting dread
        ATTACH_SELF_TO_THAT_CREATURE.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) && context.manifested != null }?.let {
            return listOf(Step.Do(Effect.Attach(Objects.Self, arcanum.api.Recipients(objects = context.manifested!!))))
        }
        // dsk chunk 05: "When it dies this turn, [effect]." (a delayed trigger about the target, for the turn, CR 603.7b, c)
        WHEN_IT_DIES_THIS_TURN.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) } ?: return null
            val then = instructions(m.groupValues[1].replaceFirstChar { it.uppercase() }) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(target), from = Zone.Battlefield, to = Zone.Graveyard)), then,
                duration = arcanum.api.Duration.EndOfTurn)))
        }
        // dsk chunk 05: "Tap enchanted creature."
        if (sentence == "Tap enchanted creature." || sentence == "Tap equipped creature.") return listOf(Step.Do(Effect.Tap(Objects.AttachedTo(Objects.Self))))
        // dsk chunk 05: "Shuffle enchanted creature into its owner's library."
        if (sentence == "Shuffle enchanted creature into its owner's library.") {
            val enchanted = Objects.AttachedTo(Objects.Self)
            return listOf(Step.Do(Effect.Move(enchanted, Destination(Zone.Library))), Step.Do(Effect.Shuffle(Players.OwnerOf(enchanted))))
        }
        // dsk chunk 05: "Put a card you put into your graveyard this way into your hand." (in a manifest-dread trigger, CR 701.62b)
        if (sentence == "Put a card you put into your graveyard this way into your hand." && context.event is EventPattern.ManifestDread) {
            return listOf(Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.EventObject(EventRole.Rest)),
                bind = "chosen", chooser = Players.You), Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // dsk chunk 01: "Destroy all creatures with power 3 or greater."
        DESTROY_ALL.matchEntire(sentence)?.let { m -> Filters.noun(m.groupValues[1])?.let { return listOf(Step.Do(Effect.Destroy(Objects.Each(it)))) } }
        // chunk 12
        SEARCH_LIBRARY_AND_OR_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, zones = listOf(Zone.Library, Zone.Graveyard), bind = "found", max = Value.Fixed(1), searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))))
        }
        IF_X_AT_LEAST.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context)?.let { then ->
                return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(name = "X"), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())), then = then))
            }
        }
        // "Creatures you control get +X/+X and gain haste until end of turn." (locked in when it resolves, CR 611.2c)
        GROUP_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            // chunk 40: a subtype stays capitalised ("Elves you control")
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() })
            val keywords = m.groupValues[4].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) }
            if (filter != null && keywords?.contains(null) != true) {
                val mechanics = listOf<Mechanic>(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)) +
                    listOfNotNull(keywords?.let { Mechanic.GainAbilities(it.filterNotNull()) })
                return listOf(Step.Do(Effect.ApplyMechanics(mechanics, arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
            }
        }
        SHUFFLE_INTO_OWNERS_LIBRARY.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))))
        }
        DRAW_OR_DISCARD_X.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(if (m.groupValues[1] == "Draw") Effect.Draw(Players.You, Value.Announced(name = "X")) else Effect.Discard(Players.You, Value.Announced(name = "X"), filter = ObjectFilter.Any)))
        }
        RETURN_ANY_NUMBER_OF_TARGETS_TO_HAND.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val filter = Filters.noun(noun) ?: return null
            context.targets += TargetSlot(Filters.slotName(noun), TargetKind.Objects(filter), min = Value.Fixed(0), max = null)
            return listOf(Step.Do(Effect.Move(Objects.Target(Filters.slotName(noun)), Destination(Zone.Hand))))
        }
        // chunk 13
        // Cleave (CR 702.148a): the text in square brackets is there unless the cleave cost was paid.
        BRACKETED.matchEntire(sentence)?.let { m ->
            val slots = context.targets.size
            val full = effect(m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4], context) ?: return null
            val cleaved = effect(m.groupValues[1] + m.groupValues[4], context) ?: return null
            if (context.targets.size != slots) return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Paid(arcanum.api.Keyword.Cleave.Kind, times = 1, of = Objects.Self)), then = full, otherwise = cleaved))
        }
        // chunk 15: "Choose artifact, creature, or land." (a card type, chosen as it resolves)
        CHOOSE_CARD_TYPE.matchEntire(sentence)?.let { m ->
            val types = m.groupValues[1].split(Regex(""",? or |, """)).map { Filters.CARD_TYPES[it] }
            if (types.all { it != null }) return listOf(Step.Choose(arcanum.api.Choice.CardType(types.filterNotNull()),
                bind = CHOSEN_TYPE, chooser = Players.You))
        }
        // chunk 15: "Tap all untapped permanents of the chosen type target player controls, or untap all tapped permanents
        // of that type that player controls." (tap or untap is chosen as it resolves)
        TAP_OR_UNTAP_ALL_OF_CHOSEN_TYPE.matchEntire(sentence)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val player = context.targetPlayer()
            fun all(status: ObjectFilter) = Objects.Each(Filters.all(noun, ObjectFilter.Type(arcanum.api.CardTypeRef.Chosen(CHOSEN_TYPE)),
                status, ObjectFilter.ControlledBy(PlayerFilter.Is(player))))
            val tapped = ObjectFilter.HasStatus(arcanum.api.Status.Tapped)
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("tapOrUntap", "tap"),
                    then = listOf(Step.Do(Effect.Tap(all(ObjectFilter.Not(tapped))))), otherwise = listOf(Step.Do(Effect.Untap(all(tapped))))))
        }
        CHOOSE_CARD_NAME.matchEntire(sentence)?.let { m ->
            val filter = m.groupValues[1].ifEmpty { null }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            context.cardName = "name"
            return listOf(Step.Choose(arcanum.api.Choice.CardName(filter), bind = "name", chooser = Players.You))
        }
        // "Target player reveals their hand[ and discards all cards with that name]."
        TARGET_REVEALS_HAND.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            val hand = Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player))))
            context.player = player
            context.revealed = Objects.Bound("revealed")
            val steps = mutableListOf<Step>(Step.Do(Effect.Reveal(hand, bind = "revealed")))
            if (m.groupValues[2].isNotEmpty()) {
                val name = context.cardName ?: return null
                steps += Step.Do(Effect.Discard(player, objects = Objects.Where(Objects.Bound("revealed"), ObjectFilter.ChosenName(name)), count = Value.Fixed(1), filter = ObjectFilter.Any))
            }
            return steps
        }
        // "You choose an artifact or creature card from it, then choose an artifact or creature card from their graveyard."
        YOU_CHOOSE_FROM_IT_AND_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val player = context.player ?: return null
            val from = context.revealed ?: return null
            val inHand = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            val inGraveyard = Filters.all(Filters.noun(m.groupValues[2].removeArticle()) ?: return null,
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))
            context.chosen += listOf("chosen", "chosenFromGraveyard")
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(inHand, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You),
                Step.Choose(arcanum.api.Choice.Objects(inGraveyard, Value.Fixed(1), Value.Fixed(1)), bind = "chosenFromGraveyard", chooser = Players.You))
        }
        // "You choose a nonland card from it[ with mana value 3 or less]." (someone else chooses: Choose + Discard(objects))
        YOU_CHOOSE_FROM_IT.matchEntire(sentence)?.let { m ->
            val from = context.revealed ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle() + m.groupValues[2]) ?: return null
            if ("chosen" !in context.chosen) context.chosen += "chosen"
            return listOf(Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You))
        }
        if (sentence == "That player discards that card.") {
            val player = context.player ?: return null
            val card = context.chosen.lastOrNull() ?: return null
            return listOf(Step.Do(Effect.Discard(player, objects = Objects.Bound(card), count = Value.Fixed(1), filter = ObjectFilter.Any)))
        }
        if (sentence == "Exile the chosen cards.") {
            val cards = context.chosen.map { Objects.Bound(it) }.ifEmpty { return null }
            return listOf(Step.Do(Effect.Move(cards.singleOrNull() ?: Objects.Union(cards), Destination(Zone.Exile))))
        }
        TARGET_LOSES_YOU_GAIN.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            return listOf(
                Step.Do(Effect.LoseLife(player, Value.Fixed(m.groupValues[2].toInt()))),
                Step.Do(Effect.GainLife(Players.You, Value.Fixed(m.groupValues[3].toInt()))))
        }
        PUT_INTO_LIBRARY_FROM_TOP.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val position = FROM_TOP[m.groupValues[2]] ?: return null
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.Move(target, Destination(Zone.Library, libraryPosition = position))))
        }
        ITS_CONTROLLER_GAINS_N.matchEntire(sentence)?.let { m ->
            val target = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.GainLife(Players.ControllerOf(target), Value.Fixed(m.groupValues[1].toInt()))))
        }
        // "Each opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and
        // planeswalkers they control." Each opponent chooses, then all sacrifice at once (CR 101.4, 608.2e).
        EACH_OPPONENT_SACRIFICES_GREATEST.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[1]) ?: return null
            val among = Filters.noun(m.groupValues[2].replace(" and ", " or ")) ?: return null
            val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))
            val greatest = Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Each(Filters.all(among, theirs)), arcanum.api.Combine.Greatest)
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(kind, theirs, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, greatest)),
                    Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))))
        }
        // CR 702.51c, 701.50
        if (sentence == "Each creature that convoked this spell connives.")
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Connive(Objects.PaidWith(arcanum.api.ManaPaymentWay.TapCreatures, of = Objects.Self), count = Value.Fixed(1)))))
        DESTROY_UP_TO.matchEntire(sentence)?.let { m ->
            val max = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val noun = m.groupValues[2].replace(" and/or ", " or ")
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = max)
            return listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        // 'Create twice X 1/1 black and green Pest creature tokens with "When this token dies, you gain 1 life."'
        CREATE_TOKENS_WITH_QUOTED.matchEntire(sentence)?.let { m ->
            val count = if (m.groupValues[1] == "twice X") Value.Times(Value.Announced(name = "X"), 2) else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val token = Tokens.defined("a " + m.groupValues[2] + " token") ?: return null
            val granted = Sentences(setOf("this token", "this creature")).line(m.groupValues[3]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token.copy(face = token.face.copy(abilities = listOf(granted))), count = count, controller = Players.You)))
        }
        // chunk 14
        // "[This] deals X damage divided as you choose among any number of targets." (CR 601.2d, 115.4)
        DIVIDED_AMONG_ANY_NUMBER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            val slot = context.targets.removeAt(context.targets.lastIndex)
            context.targets += slot.copy(min = Value.Fixed(0), max = null, divided = Value.Announced(name = "X"))
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = slot.name), Value.DividedShare, source = Objects.Self)))
        }
        // "Then that player or that permanent's controller may pay {R}{R}." after damage to any target
        THAT_PLAYER_OR_CONTROLLER_MAY_PAY.matchEntire(sentence)?.let { m ->
            val slot = context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Either }?.name ?: return null
            val cost = ManaCost.parse(m.groupValues[1]) ?: return null
            val payer = Players.Each(PlayerFilter.Or(listOf(PlayerFilter.Is(Players.Target(slot)), PlayerFilter.Is(Players.ControllerOf(Objects.Target(slot))))))
            context.payer = payer
            return listOf(Step.Pay(Cost.mana(cost), payer = payer, bind = "paid"))
        }
        if (sentence == "If the player does, they may copy this spell and may choose a new target for that copy.") {
            val payer = context.payer ?: return null
            context.copied = true
            return listOf(Step.If(arcanum.api.Condition.Did("paid"), then = listOf(
                Step.May(player = payer, steps = listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = payer, newTargets = true)))))))
        }
        // "Copy target instant or sorcery spell[ you control]." (CR 707.10)
        COPY_TARGET.matchEntire(sentence)?.let { m ->
            context.copied = true
            return listOf(Step.Do(Effect.CopySpell(context.target("spell", Filters.noun(m.groupValues[1]) ?: return null), controller = Players.You, newTargets = true)))
        }
        // CR 707.10c: the copy's controller may choose new targets, which CopySpell allows by default.
        COPY_NEW_TARGETS.matchEntire(sentence)?.let { return if (context.copied) emptyList() else null }
        if (sentence == "You may copy this spell and may choose a new target for the copy.") {
            context.copied = true
            return listOf(Step.May(listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = Players.You, newTargets = true))), player = Players.You))
        }
        IF_CAST_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.CastFrom(Zone.Graveyard, of = Objects.Self), then = then))
        }
        // "Until end of turn, whenever …" / "Whenever … this turn, …": a delayed trigger for the turn (CR 603.7b)
        (UNTIL_END_OF_TURN_WHENEVER.matchEntire(sentence) ?: WHENEVER_THIS_TURN.matchEntire(sentence))?.let { m ->
            val event = Events.event(m.groupValues[1], self) ?: return null
            val then = instructions(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(event), then,
                duration = arcanum.api.Duration.EndOfTurn, repeats = true)))
        }
        if (sentence == "That player copies it and may choose new targets for the copy.") {
            return listOf(Step.Do(Effect.CopySpell(Objects.EventObject(role = EventRole.Object), controller = Players.EventPlayer, newTargets = true)))
        }
        RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            // chunk 35: "another target"
            val another = if (m.groupValues[1].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(another + listOf(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))).toTypedArray())
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Battlefield, tapped = m.groupValues[3].isNotEmpty()))))
        }
        if (sentence == "Shuffle your library.") return listOf(Step.Do(Effect.Shuffle(Players.You)))
        if (sentence == "Until end of turn, you may play that card without paying its mana cost.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, withoutPaying = true, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        // "Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal
        // to its mana cost." (CR 702.34a)
        GAINS_FLASHBACK_OF_ITS_MANA_COST.matchEntire(sentence)?.let { m ->
            val cards = Filters.all(Filters.noun(m.groupValues[1].replace(" and ", " or ")) ?: return null,
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
                arcanum.api.Affected.Objects(Objects.Each(cards)), arcanum.api.Duration.EndOfTurn)))
        }
        // A permission, so it also covers cards put into the graveyard later this turn (CR 611.2c).
        if (sentence == "Until end of turn, you may play lands and cast spells from your graveyard.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "If a card would be put into your graveyard from anywhere this turn, exile that card instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.EndOfTurn)))
        }
        // "You may cast up to one target instant card and/or up to one target sorcery card from your graveyard each with
        // mana value X or less without paying their mana costs."
        CAST_UP_TO_ONE_EACH_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val most = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(m.groupValues[2].toInt())
            val slots = m.groupValues[1].split(" and/or ").map { part ->
                val noun = UP_TO_ONE_TARGET_CARD.matchEntire(part)?.groupValues?.get(1) ?: return null
                val filter = Filters.all(Filters.noun(noun) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost, most))
                TargetSlot(noun, TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(1))
            }
            context.targets += slots
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(0), Value.Fixed(slots.size),
                    from = Objects.Union(slots.map { Objects.Target(it.name) })), bind = "toCast", chooser = Players.You),
                Step.Do(Effect.Cast(Objects.Bound("toCast"), withoutPaying = true, bind = "cast", how = PlayKind.Cast, caster = Players.You)))
        }
        if (sentence == "If a spell cast this way would be put into your graveyard, exile it instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("cast")), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = arcanum.api.Duration.Indefinitely)))
        }
        IF_X_AT_LEAST_COPY_TWICE.matchEntire(sentence)?.let { m ->
            context.copied = true
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(name = "X"), arcanum.api.Comparison.AtLeast, Value.Fixed(m.groupValues[1].toInt())),
                then = listOf(Step.Repeat(Value.Fixed(2), listOf(Step.Do(Effect.CopySpell(Objects.Bound("cast"), controller = Players.You, newTargets = true)))))))
        }
        // chunk 15
        RETURN_SELF_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand))))
        }
        // "Return each creature card with mana value X or less from your graveyard to the battlefield."
        RETURN_EACH_FROM_GRAVEYARD_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            return listOf(Step.Do(Effect.Move(Objects.Each(filter), Destination(Zone.Battlefield), postBind = "returned")))
        }
        // "Exile those creatures at the beginning of your next upkeep." (CR 603.7: triggers once)
        if (sentence == "Exile those creatures at the beginning of your next upkeep.") {
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Bound("returned"), Destination(Zone.Exile))))))))
        }
        // "Choose up to two target permanent cards in your graveyard that were put there from the battlefield this turn."
        CHOOSE_UP_TO_TARGET_PUT_THERE_THIS_TURN.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You), PUT_THERE_FROM_BATTLEFIELD_THIS_TURN)
            context.targets += TargetSlot("cards", TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(n))
            return emptyList()
        }
        RETURN_THEM_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val slot = context.targets.lastOrNull()?.name ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Battlefield, tapped = m.groupValues[1].isNotEmpty()))))
        }
        // "[Each player returns|Return] to the battlefield all … cards in [their|your] graveyard that were put there from
        // the battlefield this turn." Each card returns under its owner's control.
        RETURN_ALL_PUT_THERE_THIS_TURN.matchEntire(sentence)?.let { m ->
            val each = m.groupValues[1] == "Each player returns"
            if (each != (m.groupValues[3] == "their")) return null
            val kind = Filters.noun(m.groupValues[2].replace(", and ", ", or ")) ?: return null
            val cards = Objects.Each(Filters.all(*listOfNotNull(kind, ObjectFilter.InZone(Zone.Graveyard),
                if (each) null else ObjectFilter.OwnedBy(PlayerFilter.You), PUT_THERE_FROM_BATTLEFIELD_THIS_TURN).toTypedArray()))
            return listOf(Step.Do(Effect.Move(cards, Destination(Zone.Battlefield, controller = if (each) Players.OwnerOf(cards) else null))))
        }
        // "Look at the top six cards of your library. Put up to two creature cards with mana value 3 or less from among
        // them onto the battlefield. Put the rest on the bottom of your library in any order."
        LOOK_PUT_UP_TO_ONTO_BATTLEFIELD_REST_BOTTOM.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            val most = Numbers.count(m.groupValues[2]) ?: return null
            val filter = Filters.noun(m.groupValues[3]) ?: return null
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(most), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1))))
        }
        // In a mana trigger on tapping a permanent: its controller tapped it (CR 605.1b).
        THAT_PLAYER_ADDS_ADDITIONAL.matchEntire(sentence)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana)), players = Players.ControllerOf(Objects.EventObject(role = EventRole.Object)))))
        }
        if (sentence == "Take an extra turn after this one.") return listOf(Step.Do(Effect.ExtraTurn(Players.You)))
        PUT_FROM_HAND_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n, n), bind = "put", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("put"), Destination(Zone.Library, libraryPosition = 0))))
        }
        PLAYERS_DRAW.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(players, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))))
            if (m.groupValues[3].isNotEmpty())
                steps += Step.Do(Effect.Discard(players, Value.Fixed(Numbers.count(m.groupValues[3]) ?: return null), random = m.groupValues[4].isNotEmpty(), filter = ObjectFilter.Any))
            if (m.groupValues[5].isNotEmpty()) steps += Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[5].toInt())))
            return steps
        }
        EACH_PLAYER_DISCARDS_HAND_DRAWS.matchEntire(sentence)?.let { m ->
            val each = Players.Each(PlayerFilter.Any)
            return listOf(
                Step.Do(Effect.Discard(each, objects = Objects.Each(ObjectFilter.InZone(Zone.Hand)), count = Value.Fixed(1), filter = ObjectFilter.Any)),
                Step.Do(Effect.Draw(each, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD_UNLESS_ATTACKED.matchEntire(sentence)?.let { m ->
            val attacked = arcanum.api.Condition.HappenedThisTurn(arcanum.api.EventPattern.Attack(attacker = ObjectFilter.ControlledBy(PlayerFilter.You)), atLeast = 1, measure = Measure.Events)
            return listOf(Step.If(arcanum.api.Condition.Not(attacked),
                then = listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))))
        }
        SCRY_OR_SURVEIL.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            return listOf(Step.Do(if (m.groupValues[1] == "Scry") Effect.Scry(Players.You, n) else Effect.Surveil(Players.You, n)))
        }
        SEARCH_TO_TOP.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card").ifEmpty { "card" }) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found", max = Value.Fixed(1), zones = listOf(Zone.Library), searcher = Players.You)),
                Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Library, libraryPosition = 0))))
        }
        SEARCH_PUT_SHUFFLE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card")) ?: return null
            // chunks 32, 42: "reveal it, ", "into your graveyard", "onto the battlefield tapped"
            val to = when (m.groupValues[3]) {
                "into your hand" -> Destination(Zone.Hand)
                "into your graveyard" -> Destination(Zone.Graveyard)
                else -> Destination(Zone.Battlefield, tapped = m.groupValues[3].endsWith(" tapped"))
            }
            val steps = mutableListOf<Step>(
                Step.Do(Effect.Search(Players.You, filter, reveal = m.groupValues[2].isNotEmpty(), bind = "found", max = Value.Fixed(1), zones = listOf(Zone.Library), searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), to)))
            if (m.groupValues[4].isNotEmpty()) steps += Step.Do(Effect.Discard(Players.You, random = true, count = Value.Fixed(1), filter = ObjectFilter.Any))
            steps += Step.Do(Effect.Shuffle(Players.You))
            return steps
        }
        if (sentence == "Look at target player's hand.") {
            val player = context.targetPlayer()
            val hand = Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player))))
            // chunk 52: "that player", "from it"
            context.player = player
            context.revealed = hand
            return listOf(Step.Do(Effect.LookAt(hand, players = Players.You)))
        }
        TARGET_PLAYER_MILLS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        LOOK_PUT_BACK.matchEntire(sentence)?.let { m ->
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), fromTop = true), bind = "seen", players = Players.You)),
                Step.Do(Effect.Reorder(Objects.Bound("seen"), chooser = Players.You)))
        }
        if (sentence == "You may shuffle.") return listOf(Step.May(listOf(Step.Do(Effect.Shuffle(Players.You))), player = Players.You))
        if (sentence == "Investigate.") return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Investigate(count = Value.Fixed(1)))))
        TARGET_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            // chunk 40: "+X/-X"
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
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
            return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
        }
        EXILE_THEN_RETURN.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            // chunk 52: "another target creature you control"
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun), if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))
            return listOf(
                Step.Do(Effect.Move(target, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))))
        }
        TARGET_PLAYER_DRAWS.matchEntire(sentence)?.let { m ->
            val player = context.targetPlayer()
            // chunk 56: "Target player draws X cards."
            val drawn = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val steps = mutableListOf<Step>(Step.Do(Effect.Draw(player, drawn)))
            if (m.groupValues[2].isNotEmpty()) steps += Step.Do(Effect.Discard(player, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null), filter = ObjectFilter.Any))
            return steps
        }
        TARGET_PLAYER_DISCARDS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(context.targetPlayer(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))
        }
        TARGET_PLAYER_CREATES.matchEntire(sentence)?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = context.targetPlayer())))
        }
        RETURN_FROM_GRAVEYARD_TO_HAND.matchEntire(sentence)?.let { m ->
            // chunk 35: "another target"
            val another = if (m.groupValues[1].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(another + listOf(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))).toTypedArray())
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
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(kept), Value.Fixed(kept), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1))))
        }
        LOOK_HAND_BOTTOM_EXILE.matchEntire(sentence)?.let { m ->
            val seen = Numbers.count(m.groupValues[1]) ?: return null
            fun one(from: Objects, bind: String) = Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = from), bind = bind, chooser = Players.You)
            val rest = Objects.Except(Objects.Bound("seen"), Objects.Bound("toHand"))
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(seen), fromTop = true), bind = "seen", players = Players.You)),
                one(Objects.Bound("seen"), "toHand"),
                Step.Do(Effect.Move(Objects.Bound("toHand"), Destination(Zone.Hand))),
                one(rest, "toBottom"),
                Step.Do(Effect.Move(Objects.Bound("toBottom"), Destination(Zone.Library, libraryPosition = -1))),
                Step.Do(Effect.Move(Objects.Except(rest, Objects.Bound("toBottom")), Destination(Zone.Exile), postBind = "exiled")))
        }
        if (sentence == "You may play the exiled card this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        SELF_DEALS_DAMAGE_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val amount = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(m.groupValues[2].toInt())
            val to = when (val what = m.groupValues[3]) {
                "any target" -> { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                else -> {
                    val noun = what.removePrefix("target ").takeIf { what.startsWith("target ") } ?: return null
                    arcanum.api.Recipients(objects = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null))
                }
            }
            return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
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
            // chunk 41: "If it was a permanent card, …"
            context.it = Objects.Bound("exiled")
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
            if (m.groupValues[2].isEmpty()) return this.exile(target) // chunk 33: linked to "the exiled card"
            // Converge: "if its mana value is less than or equal to the number of colors of mana spent to cast this spell"
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.ManaSpent(distinctColors = true, of = Objects.Self))), then = listOf(exile)))
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
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(n)), bind = "untapped", chooser = Players.You),
                Step.Do(Effect.Untap(Objects.Bound("untapped"))))
        }
        COUNTER_TARGET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val spell = context.target(Filters.slotName(m.groupValues[1]), filter)
            val counter = Step.Do(Effect.Counter(spell))
            if (m.groupValues[2].isEmpty()) return listOf(counter)
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return unlessPays(Players.ControllerOf(spell), Cost.mana(cost), listOf(counter))
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
                    times = Value.Of(arcanum.api.NumericProperty.ManaValue, spell, arcanum.api.Combine.Single))), players = Players.You)))))))
        }
        ADD_X_ANY_COMBINATION.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Announced(name = "X"), combination = true)), players = Players.You)))
        }
        ADD_ANY_OF_REMEMBERED_COLORS.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1),
                colors = listOf(ColorRef.Of(Objects.Remembered(IMPRINTED))))), players = Players.You)))
        }
        FLIP_A_COIN.matchEntire(sentence)?.let {
            context.flip = true
            return listOf(Step.Do(Effect.FlipCoin(bind = "flip", players = Players.You)))
        }
        IF_YOU_LOSE_THE_FLIP.matchEntire(sentence)?.let { m ->
            if (!context.flip) return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.WonFlip("flip")), then = then))
        }
        SELF_DEALS_DAMAGE_TO_YOU.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.You), Value.Fixed(m.groupValues[2].toInt()), source = Objects.Self)))
        }
        YOU_MAY_PAY_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[1]) ?: return null
            // chunk 35: "you may pay {X}. If you do, return … target … card with mana value X …": X is the target's mana
            // value (CostPart.Mana.x); the target is chosen before X (CR 603.3d).
            if (cost.symbols.any { it is arcanum.api.ManaSymbol.Variable }) {
                val text = m.groupValues[2].takeIf { it.contains(WITH_MANA_VALUE_X) } ?: return null
                val then = effect(text.replace(WITH_MANA_VALUE_X, "").replaceFirstChar { it.uppercase() }, context) ?: return null
                val slot = context.targets.lastOrNull()?.name ?: return null
                val x = Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Target(slot), arcanum.api.Combine.Single)
                return listOf(Step.Pay(Cost(listOf(CostPart.Mana(cost, x = x))), bind = "paid", payer = Players.You), Step.If(arcanum.api.Condition.Did("paid"), then = then))
            }
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.Pay(Cost.mana(cost), bind = "paid", payer = Players.You), Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        UNTAP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Untap(Objects.Self)))
        }
        YOU_MAY_EXILE_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember(IMPRINTED, Objects.Bound("exiled"))))
        }
        ADD_MANA_RESTRICTED.matchEntire(sentence)?.let { m ->
            val kind = mana(m.groupValues[1]) ?: return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(kind, restriction = Filters.all(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), spells)), players = Players.You)))
        }
        ADD_MANA_FOR_EACH.matchEntire(sentence)?.let { m ->
            val symbols = ManaCost.parse(m.groupValues[1]) ?: return null
            val count = Filters.objects(m.groupValues[2], self) ?: return null
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(symbols, times = Value.Count(count))), players = Players.You)))
        }
        ADD_MANA.matchEntire(sentence)?.let { m -> mana(m.groupValues[1])?.let { return listOf(Step.Do(Effect.AddMana(ManaProduction(it), players = Players.You))) } }
        DRAW.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), filter = ObjectFilter.Any)))
        }
        // chunk 35: "Create a 1/1 … token for each +1/+1 counter on this creature." (read from the source, its last known
        // information once it has left, CR 113.7a, 608.2h)
        CREATE_FOR_EACH_COUNTER_ON_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            if (count != 1) return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single), controller = Players.You)))
        }
        // chunk 35: "Draw a card for each +1/+1 counter on it." in an ability of this object
        DRAW_FOR_EACH_COUNTER_ON.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) || it.groupValues[2] == "it" && context.selfDied() }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Of(arcanum.api.NumericProperty.Counters(kind), Objects.Self, arcanum.api.Combine.Single))))
        }
        // chunk 35: "Create X tapped 1/1 green Squirrel creature tokens, where X is this creature's power."
        CREATE_X_WHERE_SELF_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val (_, token) = Tokens.spec("a " + m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single),
                tapped = m.groupValues[1].isNotEmpty(), controller = Players.You)))
        }
        // chunk 35: "It deals damage equal to its power to any target." (the source's power, CR 113.7a)
        DEALS_DAMAGE_EQUAL_TO_ITS_POWER.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] == "It" }?.let { m ->
            val amount = Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single)
            val to = if (m.groupValues[2] == "any target") { context.anyTarget(); arcanum.api.Recipients(target = "any") }
                else arcanum.api.Recipients(players = Filters.players(m.groupValues[2]) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(to, amount, source = Objects.Self)))
        }
        // chunk 35: "Return to your hand target artifact card in your graveyard with lesser mana value." (lesser than the
        // object of the trigger event, as it last existed, CR 603.10a)
        RETURN_TO_HAND_WITH_LESSER_MANA_VALUE.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.ManaValue(arcanum.api.Comparison.Less, Value.Of(arcanum.api.NumericProperty.ManaValue, that, arcanum.api.Combine.Single)))
            return listOf(Step.Do(Effect.Move(context.target("card", filter), Destination(Zone.Hand))))
        }
        // chunk 35: "Each player may search their library for up to two basic land cards, put them onto the battlefield, then
        // shuffle." Each player decides and searches in turn order, then the lands enter together (CR 101.4).
        EACH_PLAYER_MAY_SEARCH_UP_TO.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val player = Players.Bound("player")
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.May(player = player, steps = listOf(
                    Step.Do(Effect.Search(player, filter, max = Value.Fixed(n), bind = "found", searcher = player, zones = listOf(Zone.Library))),
                    Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                    Step.Do(Effect.Shuffle(player)))))))
        }
        CREATE_TOKEN.matchEntire(sentence)?.let { m ->
            // chunk 42: "a tapped 4/4 green Beast creature token"
            val tokens = TAPPED_TOKENS.replaceFirst(m.groupValues[1], "$1 ")
            val (count, token) = Tokens.spec(tokens) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), tapped = tokens != m.groupValues[1], controller = Players.You)))
        }
        THAT_PLAYER_MAY_SEARCH.matchEntire(sentence)?.let { m ->
            val player = context.lastTargetController() ?: return null
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.May(player = player, steps = listOf(
                Step.Do(Effect.Search(player, filter, bind = "found", searcher = player, max = Value.Fixed(1), zones = listOf(Zone.Library))),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty(), controller = player))),
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
        // chunk 26: "You may [cost or action]. If you do, [effect]." (CR 118.12)
        YOU_MAY_IF_YOU_DO.matchEntire(sentence)?.let { m ->
            val action = m.groupValues[1].replaceFirstChar { it.uppercase() }
            // chunk 36: "pay {1} and discard a card" is one cost of two parts; "exile one of them from your graveyard" in a
            // discard trigger exiles one of the discarded cards
            val them = EXILE_ONE_OF_THEM_FROM_GRAVEYARD.takeIf { action == it && context.event is EventPattern.Discard }?.let {
                context.it = Objects.Bound("exiled")
                listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.Is(Objects.EventObject(arcanum.api.EventRole.After)), count = Value.Fixed(1)), from = Zone.Graveyard,
                    postBind = "exiled"))
            }
            val parts = them ?: costPart(action) ?: exileThatCard(action, context) ?: action.split(" and ").map { costPart(it.replaceFirstChar { c -> c.uppercase() }) }
                .takeIf { it.size > 1 && it.all { p -> p != null } }?.flatMap { it!! }
            val pay = if (parts != null) Step.Pay(Cost(parts), bind = "paid", payer = Players.You)
                else Step.Pay(steps = effect("$action.", context) ?: return null, bind = "paid", payer = Players.You)
            val then = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(pay, Step.If(arcanum.api.Condition.Did("paid"), then = then))
        }
        // chunk 26: "That creature's controller may draw a card." (the creature as it last existed, CR 603.10a)
        THAT_CREATURES_CONTROLLER_MAY_DRAW.matchEntire(sentence)?.let { m ->
            if (context.event !is EventPattern.ZoneChange) return null
            val player = Players.ControllerOf(Objects.EventObject(arcanum.api.EventRole.Before))
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return listOf(Step.May(listOf(Step.Do(Effect.Draw(player, Value.Fixed(n)))), player = player))
        }
        // chunk 26: "Exile the top card of your library. You may play that card for as long as it remains exiled."
        EXILE_TOP_CARD.matchEntire(sentence)?.let { m ->
            context.it = Objects.Bound("exiled")
            // chunk 36: "face down" (CR 406.3)
            val exile = Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile, faceDown = m.groupValues[1].isNotEmpty()),
                postBind = "exiled"))
            return if (linkedExile) listOf(exile, Step.Do(Effect.Remember(EXILED_WITH, Objects.Bound("exiled")))) else listOf(exile)
        }
        // chunk 55: "Shuffle your library, then exile the top card." ("the top card" of that library)
        if (sentence == "Shuffle your library, then exile the top card.") {
            context.it = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Shuffle(Players.You)),
                Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")))
        }
        if (sentence == "You may play that card for as long as it remains exiled.") {
            // CR 400.7: once it leaves exile it is a new object, so the permission has no other end
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 26: "Each opponent loses 1 life."
        PLAYERS_LOSE_LIFE.matchEntire(sentence)?.let { m ->
            Filters.players(m.groupValues[1])?.let { return listOf(Step.Do(Effect.LoseLife(it, Value.Fixed(m.groupValues[2].toInt())))) }
        }
        // chunk 26: "This enchantment deals that much damage to each opponent."
        DEALS_THAT_MUCH.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val players = Filters.players(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = players), context.thatMuch ?: return null, source = Objects.Self)))
        }
        // chunk 26: "Put a loyalty counter on each red planeswalker you control."
        PUT_COUNTERS_ON_EACH.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(Objects.Each(Filters.noun(m.groupValues[3]) ?: return null), kind, Value.Fixed(n))))
        }
        // chunk 26: "Each creature gets -X/-X until end of turn.", "Creatures you control get +1/+1 until end of turn."
        EACH_GETS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1].ifEmpty { m.groupValues[2].replaceFirstChar { it.lowercase() } }
            Filters.noun(noun)?.let { filter ->
                return listOf(Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[3]) ?: return null, ptChange(m.groupValues[4]) ?: return null)),
                    arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
            }
        }
        // chunk 26: "Creatures you control gain haste until end of turn."
        GAIN_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val subject = m.groupValues[1]
            val objects = when {
                isSelf(subject) -> Objects.Self
                // chunk 47: "It gains trample" after an effect on a target: that target
                subject == "It" -> context.targets.lastOrNull()?.takeIf { it.what is TargetKind.Objects }?.let { Objects.Target(it.name) }
                else -> Filters.noun(subject.replaceFirstChar { it.lowercase() })?.let { Objects.Each(it) }
            }
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) }
            if (objects != null && keywords.all { it != null })
                return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords.filterNotNull())),
                    arcanum.api.Affected.Objects(objects), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 26: "You may cast target instant or sorcery card … from your graveyard." (cast during resolution, CR 608.2g)
        MAY_CAST_TARGET_FROM_GRAVEYARD.matchEntire(sentence)?.let { m ->
            // chunk 36: "with mana value less than or equal to this creature's power", "without paying its mana cost"
            val most = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { if (isSelf(it)) ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single)) else return null }
            val filter = Filters.all(*listOfNotNull(Filters.noun(m.groupValues[1]) ?: return null, most, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You)).toTypedArray())
            val card = context.target("card", filter)
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(card, withoutPaying = m.groupValues[4].isNotEmpty(), bind = "spell", how = PlayKind.Cast, caster = Players.You))), player = Players.You))
        }
        // chunk 26: "If that spell would be put into your graveyard, exile it instead." after casting it (CR 614.1a)
        if (sentence == "If that spell would be put into your graveyard, exile it instead.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, arcanum.api.Duration.Indefinitely)))
        }
        // chunk 26: "He phases out." (CR 702.26b)
        PHASES_OUT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) || it.groupValues[1] in setOf("He", "She") }?.let {
            return listOf(Step.Do(Effect.PhaseOut(Objects.Self)))
        }
        // chunk 26: 'You get an emblem with "[ability]".' (CR 114.2)
        EMBLEM.matchEntire(sentence)?.let { m ->
            val ability = Sentences(setOf("this emblem")).line(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateEmblem(listOf(ability), players = Players.You)))
        }
        // chunk 26: "Target artifact you control becomes a copy of another target artifact or creature you control until end
        // of turn, except it's an artifact in addition to its other types." (CR 707.9b)
        BECOMES_COPY.matchEntire(sentence)?.let { m ->
            val first = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            val name = Filters.slotName(m.groupValues[2])
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(m.groupValues[2]) ?: return null),
                distinctFrom = listOf((first as Objects.Target).slot), min = Value.Fixed(1), max = Value.Fixed(1))
            val except = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { listOf(arcanum.api.CopyException.Types(add = listOf(Filters.CARD_TYPES[it] ?: return null))) }.orEmpty()
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Target(name), except)),
                arcanum.api.Affected.Objects(first), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 25
        UNTAP_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            return listOf(Step.Do(Effect.Untap(context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null))))
        }
        UNTAP_ALL.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Untap(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null))))
        }
        if (sentence == "Reveal the top card of your library.") {
            context.it = Objects.Bound("revealed")
            return listOf(Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), bind = "revealed")))
        }
        // chunk 21: "If it's a land card, create a Treasure token."
        IF_ITS_A.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, filter), then = then))
        }
        PUT_IT_INTO.matchEntire(sentence)?.let { m ->
            val zone = if (m.groupValues[2] == "hand") Zone.Hand else Zone.Graveyard
            // chunk 36: "that card"; chunk 47: "put that card into your hand" is "it"
            // chunk 50: in an activated ability, "it" is the source (no event, no target)
            val source = Objects.Self.takeIf { m.groupValues[1] == "it" && context.event == null && context.targets.isEmpty() && !isSpell }
            if (m.groupValues[1] != "them") return listOf(Step.Do(Effect.Move(context.it ?: source ?: return null, Destination(zone))))
            context.thatMuch = Value.Count(Objects.Bound("moved"))
            return listOf(Step.Do(Effect.Move(context.them ?: return null, Destination(zone), postBind = "moved")))
        }
        ADD_AS_SACRIFICED_COULD.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.AsCouldProduce(ObjectFilter.Is(Objects.Bound(SACRIFICED)), amount = Value.Fixed(1))), players = Players.You)))
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
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = it.groupValues[2].isNotEmpty()), postBind = "returned")))
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
            return listOf(Step.Do(Effect.LookAt(Objects.LibraryCards(context.targetPlayer(), Value.Fixed(n), fromTop = true), players = Players.You)))
        }
        // chunk 20: "Look at a card at random in target player's hand."
        if (sentence == "Look at a card at random in target player's hand.") {
            val player = context.targetPlayer()
            return listOf(Step.Do(Effect.LookAt(Objects.Random(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.Is(player)))), count = Value.Fixed(1)), players = Players.You)))
        }
        // chunk 20: "Draw a card at the beginning of the next turn's upkeep.": a delayed trigger (CR 603.7)
        AT_NEXT_UPKEEP.matchEntire(sentence)?.let { m ->
            val inner = Context()
            val steps = effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", inner)?.takeIf { inner.targets.isEmpty() } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, whose = PlayerFilter.Any)),
                Instructions(steps = steps))))
        }
        // chunk 20: "Put this artifact on top of its owner's library."
        PUT_SELF_ON_TOP.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Library, libraryPosition = 0))))
        }
        // chunk 21: "You may put a creature card with mana value … from your hand onto the battlefield." (zero or one)
        YOU_MAY_PUT_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = counterNoun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(Filters.all(filter, ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))))
        }
        // chunk 21: "Put a charge counter on this artifact." / "Put a +1/+1 counter on target creature you control."
        PUT_COUNTERS.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            val on = m.groupValues[3]
            val objects = when {
                isSelf(on) -> Objects.Self
                // chunk 36: "on it"
                on == "it" -> context.it
                on.startsWith("target ") -> on.removePrefix("target ").let { context.target(Filters.slotName(it), Filters.noun(it) ?: return null) }
                // chunk 35: other wordings ("on Korvold and draw a card") are left to the rules below
                else -> null
            }
            if (objects != null) return listOf(Step.Do(Effect.PutCounters(objects, kind, Value.Fixed(n))))
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
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Remembered(EXILED_WITH)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Graveyard), postBind = "moved")))
        }
        // chunk 21: "Exile that card from your graveyard." (a discarded card; nothing happens if it has left)
        if (sentence == "Exile that card from your graveyard.") {
            val that = context.that ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Where(that, ObjectFilter.InZone(Zone.Graveyard)), Destination(Zone.Exile), postBind = "exiled"))) +
                remembered(Objects.Bound("exiled"))
        }
        // chunk 21: "Until end of turn, you may play that card."
        if (sentence == "Until end of turn, you may play that card.") {
            val exiled = context.it ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(exiled), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 21: "That creature's controller creates a 1/1 colorless Myr artifact creature token."
        THAT_CONTROLLER_CREATES.matchEntire(sentence)?.let { m ->
            val that = context.that ?: return null
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.ControllerOf(that))))
        }
        // chunk 21: "You gain 1 life for each spell you've cast this turn."
        GAIN_LIFE_FOR_EACH_SPELL_CAST.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val spells = Value.EventsThisTurn(EventPattern.Cast(by = PlayerFilter.You, spell = ObjectFilter.Any), measure = Measure.Events)
            val n = m.groupValues[2].toInt()
            return listOf(Step.Do(Effect.GainLife(players, if (n == 1) spells else Value.Times(spells, n))))
        }
        // chunk 21: "An opponent gains control of this artifact." (the opponent is chosen on resolution)
        OPPONENT_GAINS_CONTROL.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(
                Step.Choose(arcanum.api.Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Bound("opponent"))),
                    arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.Indefinitely)))
        }
        // chunk 56: "Exile the top two cards of your library." ("those cards")
        EXILE_TOP_N.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            context.them = Objects.Bound("exiled")
            return listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(n), fromTop = true), Destination(Zone.Exile), postBind = "exiled")))
        }
        // chunk 56: "You may play those cards this turn." (lands too, CR 305.1)
        if (sentence == "You may play those cards this turn.") {
            val them = context.them ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(them), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56
        TARGET_OPPONENT_MILLS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(context.targetOpponent(), Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // chunk 56: "Up to one target creature gets -2/-0 until your next turn."
        UP_TO_ONE_TARGET_GETS.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(0), max = Value.Fixed(1))
            val duration = if (m.groupValues[4] == "your next turn") arcanum.api.Duration.UntilYourNextTurn else arcanum.api.Duration.EndOfTurn
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Target(name)), duration)))
        }
        // chunk 56: "Target creature gets +1/+1 until end of turn for each artifact and/or enchantment you control." (counted
        // once, as it resolves, CR 608.2h)
        TARGET_GETS_FOR_EACH_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            val per = Value.Count(Filters.objects(m.groupValues[4]) ?: return null)
            fun amount(n: Int): Value = when (n) { 0 -> Value.Fixed(0); 1 -> per; else -> Value.Times(per, n) }
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(amount(m.groupValues[2].toInt()), amount(m.groupValues[3].toInt()))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56: "You may cast target instant or sorcery card from your graveyard this turn.": a permission for the turn
        MAY_CAST_TARGET_FROM_GRAVEYARD_THIS_TURN.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val card = context.target("card", filter)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You)), arcanum.api.Affected.Objects(card),
                arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 56: "Exile Jace, then return him to the battlefield transformed under his owner's control." (a new object,
        // back face up, CR 400.7, 712.14a)
        EXILE_SELF_RETURN_TRANSFORMED.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val controller = if (m.groupValues[2] == "your") Players.You else Players.OwnerOf(Objects.Bound("exiled"))
            return listOf(
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, transformed = true, controller = controller))))
        }
        // chunk 56: "Transform this creature." / "transform it" (CR 701.27)
        TRANSFORM.matchEntire(sentence)?.let { m ->
            val objects = if (isSelf(m.groupValues[1])) Objects.Self else if (m.groupValues[1] == "it") context.it ?: return null else return null
            return listOf(Step.Do(Effect.Transform(objects)))
        }
        // chunk 56: "Return all non-Horror creatures to their owners' hands."
        RETURN_ALL_TO_HANDS.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Move(Objects.Each(Filters.noun(m.groupValues[1]) ?: return null), Destination(Zone.Hand))))
        }
        // chunk 56: "Up to one target Blood token you control becomes a 2/2 black Bat creature with flying and haste in
        // addition to its other types." No duration: for good (CR 611.2a). Layers 4, 5, 6, 7b (CR 613.1).
        BECOMES_CREATURE.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val name = Filters.slotName(noun)
            val upTo = m.groupValues[1] == "Up to one target"
            context.targets += TargetSlot(name, TargetKind.Objects(Filters.noun(noun) ?: return null), min = Value.Fixed(if (upTo) 0 else 1), max = Value.Fixed(1))
            val color = BECOME_COLORS[m.groupValues[5]] ?: return null
            val subtype = m.groupValues[6].takeIf { it in RulesWords.subtypes } ?: return null
            val keywords = m.groupValues[7].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
            val mechanics = listOf<Mechanic>(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))),
                Mechanic.SetColors(listOf(ColorRef.Literal(color)))) +
                (if (keywords.isEmpty()) emptyList() else listOf(Mechanic.GainAbilities(keywords))) +
                Mechanic.SetPowerToughness(Value.Fixed(m.groupValues[3].toInt()), Value.Fixed(m.groupValues[4].toInt()))
            return listOf(Step.Do(Effect.ApplyMechanics(mechanics, arcanum.api.Affected.Objects(Objects.Target(name)), arcanum.api.Duration.Indefinitely)))
        }
        // chunks 31, 47: "You may exile it. When you do, [reflexive trigger]." (CR 603.12)
        YOU_MAY_WHEN_YOU_DO.matchEntire(sentence)?.let { m ->
            costPart(m.groupValues[1].replaceFirstChar { it.uppercase() })?.let { parts ->
                val reflexive = instructions(m.groupValues[2].replaceFirstChar { it.uppercase() }) ?: return null
                return listOf(Step.Pay(Cost(parts), bind = "paid", payer = Players.You),
                    Step.If(arcanum.api.Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(reflexive)))))
            }
        }
        // chunks 30–33
        enters(sentence, context)?.let { return it }
        // chunk 40: "Until end of turn, each Elf creature you control has base power and toughness 5/5 and becomes a Dinosaur
        // in addition to its other creature types." / "… creatures you control have base power and toughness X/X and gain
        // all creature types." (layers 4 and 7b, CR 613.4b; the affected set is fixed as it resolves, CR 611.2c)
        UNTIL_EOT_BASE_PT.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removePrefix("each ")) ?: return null
            val x = if (isSpell || announcedX) Value.Announced(name = "X") else Value.CastX(of = Objects.Self)
            fun pt(text: String): Value = if (text == "X") x else Value.Fixed(text.toInt())
            val mechanics = mutableListOf<Mechanic>(Mechanic.SetPowerToughness(pt(m.groupValues[2]), pt(m.groupValues[3])))
            when (val also = m.groupValues[4]) {
                "" -> {}
                "gain all creature types", "gains all creature types" -> mechanics += Mechanic.AllCreatureTypes
                else -> {
                    val type = BECOMES_ADDITIONAL_TYPE.matchEntire(also)?.groupValues?.get(1)?.takeIf { it in RulesWords.subtypes } ?: return null
                    mechanics += Mechanic.SetTypes(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(type))))
                }
            }
            return listOf(Step.Do(Effect.ApplyMechanics(mechanics, arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 40: "This creature gets +2/+2 until end of turn."
        SELF_GETS_UNTIL_EOT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(ptChange(m.groupValues[2]) ?: return null, ptChange(m.groupValues[3]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 40: "This creature can't be blocked this turn except by creatures with haste." (blockers are always creatures,
        // so the filter names only the keyword, as flying's does)
        CANT_BE_BLOCKED_EXCEPT_BY_THIS_TURN.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            val keyword = OracleParser.keyword(m.groupValues[2].replaceFirstChar { it.uppercase() }) ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.HasKeyword(keyword.kind))),
                arcanum.api.Affected.Objects(Objects.Self), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 40: "Tap or untap another target permanent.": one of the two, chosen as it resolves
        TAP_OR_UNTAP.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun),
                if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("tapOrUntap", "tap"),
                    then = listOf(Step.Do(Effect.Tap(target))), otherwise = listOf(Step.Do(Effect.Untap(target)))))
        }
        // chunk 40: "Another target creature you control gains protection from colorless or from the color of your choice
        // until end of turn." (chosen as it resolves)
        GAINS_PROTECTION_OF_CHOICE.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val target = context.target(Filters.slotName(noun),
                if (m.groupValues[1] == "Target") filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter))
            fun protection(from: ObjectFilter) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Protection(from)))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn))
            val color = listOf(Step.Choose(arcanum.api.Choice.Color(), bind = "color", chooser = Players.You), protection(ObjectFilter.OfColor(ColorRef.Chosen("color"))))
            if (m.groupValues[3].isEmpty()) return color
            return listOf(
                Step.Choose(arcanum.api.Choice.Option(listOf("colorless", "a color")), bind = "quality", chooser = Players.You),
                Step.If(arcanum.api.Condition.Chose("quality", "colorless"), then = listOf(protection(ObjectFilter.Colorless)), otherwise = color))
        }
        // chunk 40: "Change a target of target spell or ability to this creature." (CR 115.7b)
        CHANGE_A_TARGET_TO.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val spell = context.target("spell", Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ChangeTargets(spell, optional = false, to = Objects.Self, chooser = Players.You)))
        }
        // chunk 41: "Choose target artifact a player controls and target artifact card in that player's graveyard."
        CHOOSE_TARGET_AND_CARD_IN_THAT_GRAVEYARD.matchEntire(sentence)?.let { m ->
            val permanent = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            context.target("card", Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.ControllerOf(permanent)))))
            return emptyList()
        }
        // chunk 41: "If both targets are still legal as this ability resolves, that player simultaneously sacrifices the artifact
        // and returns the artifact card to the battlefield." (CR 608.2b; one event, CR 608.2c)
        BOTH_LEGAL_SACRIFICE_AND_RETURN.matchEntire(sentence)?.let {
            val slots = context.targets.takeLast(2).map { it.name }.takeIf { it.size == 2 } ?: return null
            val (permanent, card) = slots
            return listOf(Step.If(arcanum.api.Condition.TargetsLegal(slots), then = listOf(Step.Simultaneously(listOf(
                Step.Do(Effect.Sacrifice(Objects.Target(permanent))),
                Step.Do(Effect.Move(Objects.Target(card), Destination(Zone.Battlefield, controller = Players.ControllerOf(Objects.Target(permanent))))))))))
        }
        // chunk 41: "If it was a permanent card, put a +1/+1 counter on this permanent."
        IF_IT_WAS.matchEntire(sentence)?.let { m ->
            val it = context.it ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val then = effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(it, filter), then = then))
        }
        // chunk 41: "Return a tapped land you control to its owner's hand." (chosen as it resolves, not targeted)
        RETURN_CHOSEN_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        // chunk 42: "Target opponent reveals the top X cards of their library."
        TARGET_REVEALS_TOP.matchEntire(sentence)?.let { m ->
            val player = if (m.groupValues[1] == "player") context.targetPlayer() else context.targetOpponent()
            val n = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            context.player = player
            context.revealed = Objects.Bound("revealed")
            return listOf(Step.Do(Effect.Reveal(Objects.LibraryCards(player, n, fromTop = true), bind = "revealed")))
        }
        // chunk 42: "You may put a nonland permanent card with mana value X or less from among them onto the battlefield under
        // your control.": zero or one of them
        YOU_MAY_PUT_FROM_AMONG_THEM.matchEntire(sentence)?.let { m ->
            val from = context.revealed ?: return null
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            context.chosen += "chosen"
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, Value.Fixed(0), Value.Fixed(1), from = from), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = if (m.groupValues[2].isEmpty()) null else Players.You))))
        }
        // chunk 42: "That player puts the rest on the bottom of their library in a random order."
        if (sentence == "That player puts the rest on the bottom of their library in a random order.") {
            val from = context.revealed ?: return null
            val chosen = context.chosen.lastOrNull() ?: return null
            return listOf(Step.Do(Effect.Move(Objects.Except(from, Objects.Bound(chosen)), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        // chunk 42: "Any number of target players each lose 2 life and sacrifice a creature of their choice." Each player
        // chooses, then all sacrifice at once (CR 101.4, 608.2e).
        TARGET_PLAYERS_LOSE_AND_SACRIFICE.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[2]) ?: return null
            context.targets += TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(0), max = null)
            val players = Players.Target("players")
            val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))
            return listOf(
                Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[1].toInt()))),
                Step.ForEach(arcanum.api.Each.Players(players), bind = "player", together = true, steps = listOf(
                    Step.Choose(arcanum.api.Choice.Objects(Filters.all(kind, theirs), Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed",
                        chooser = Players.Bound("player")),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))))
        }
        // chunk 42: "Create a tapped 4/4 green Beast creature token and untap this creature."
        CREATE_AND.matchEntire(sentence)?.let { m ->
            val first = effect("Create " + m.groupValues[1] + ".", context)
            val second = first?.let { effect(m.groupValues[2].replaceFirstChar { c -> c.uppercase() } + ".", context) }
            if (first != null && second != null) return first + second
        }
        // chunk 42: "You gain 1 life and draw a card.", "You add {B}{B} and draw a card."
        YOU_AND.matchEntire(sentence)?.let { m ->
            fun part(text: String) = effect("You $text.", context) ?: effect(text.replaceFirstChar { c -> c.uppercase() } + ".", context)
            val first = part(m.groupValues[1])
            val second = first?.let { part(m.groupValues[2]) }
            if (first != null && second != null) return first + second
        }
        // chunk 45: "Remove a time counter from this card." (chunk 56: "it" afterwards is this object)
        REMOVE_COUNTERS_FROM_SELF.matchEntire(sentence.removeSuffix("."))?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            context.it = Objects.Self
            return listOf(Step.Do(Effect.RemoveCounters(Objects.Self, kind, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // chunk 46: "Bolster 1." (CR 701.39a)
        BOLSTER.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Bolster(Value.Fixed(m.groupValues[1].toInt()))))) }
        // chunk 47: "This creature connives." (CR 701.50a)
        CONNIVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Action(arcanum.api.KeywordAction.Connive(objects = Objects.Self, count = Value.Fixed(1)))))
        }
        // chunk 47: "Put that many +1/+1 counters on this creature." after discarding one or more cards: one per card
        PUT_THAT_MANY_COUNTERS.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) && context.event is EventPattern.Discard }?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.PutCounters(Objects.Self, kind, Value.Count(Objects.EventObject(arcanum.api.EventRole.After)))))
        }
        // chunk 47: "You lose life equal to its mana value." (the card the ability revealed)
        YOU_LOSE_LIFE_EQUAL_TO_ITS.matchEntire(sentence)?.let { m ->
            val it = (context.it as? Objects.Bound) ?: return null
            val property = if (m.groupValues[1] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.ManaValue
            return listOf(Step.Do(Effect.LoseLife(Players.You, Value.Of(property, it, arcanum.api.Combine.Single))))
        }
        // chunk 47: "You may play that card this turn." / "… until your next end step." (the exiled card)
        YOU_MAY_PLAY_THAT_CARD.matchEntire(sentence)?.let { m ->
            val exiled = (context.it as? Objects.Bound) ?: return null
            val duration = if (m.groupValues[1] == "this turn") arcanum.api.Duration.EndOfTurn
                else arcanum.api.Duration.UntilStep(arcanum.api.TurnStep.End, PlayerFilter.You)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Play, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(exiled), duration)))
        }
        // chunk 47: "If you win the flip, …"
        IF_YOU_WIN_THE_FLIP.matchEntire(sentence)?.let { m ->
            if (!context.flip) return null
            val then = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context) ?: return null
            return listOf(Step.If(arcanum.api.Condition.WonFlip("flip"), then = then))
        }
        // chunk 47: "Return that spell to its owner's hand." (the event's object)
        RETURN_THAT_TO_OWNERS_HAND.matchEntire(sentence)?.let {
            return listOf(Step.Do(Effect.Move(context.that ?: return null, Destination(Zone.Hand))))
        }
        // chunk 47: "Copy that spell, and you may choose new targets for the copy." (CR 707.10c)
        COPY_THAT_SPELL.matchEntire(sentence)?.let {
            context.copied = true
            return listOf(Step.Do(Effect.CopySpell(context.that ?: return null, controller = Players.You, newTargets = true)))
        }
        // chunk 47: "Draw a card unless that player pays {X}, where X is this creature's power." (CR 118.12a)
        UNLESS_THAT_PLAYER_PAYS.matchEntire(sentence)?.let { m ->
            val mana = ManaCost.parse(m.groupValues[2]) ?: return null
            val x = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { selfNumber(it) ?: return null }
            val steps = effect(m.groupValues[1] + ".", context) ?: return null
            return unlessPays(Players.EventPlayer, Cost(listOf(CostPart.Mana(mana, x = x))), steps)
        }
        // chunk 46: "Until your next turn, target player who was dealt combat damage by Hope of Ghirapur this turn can't cast
        // noncreature spells."
        UNTIL_YOUR_NEXT_TURN_CANT_CAST.matchEntire(sentence)?.let { m ->
            val source = m.groupValues[1]
            if (source.isNotEmpty() && !isSelf(source)) return null
            val spells = Filters.noun(m.groupValues[2]) ?: return null
            val filter = if (source.isEmpty()) PlayerFilter.Any else PlayerFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self),
                arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true))
            context.targets += TargetSlot("player", TargetKind.Players(filter), min = Value.Fixed(1), max = Value.Fixed(1))
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(spells)),
                arcanum.api.Affected.Players(Players.Target("player")), arcanum.api.Duration.UntilYourNextTurn)))
        }
        // chunks 52, 56: "If [condition], [effect]." (checked as it resolves)
        IF_CONDITION.matchEntire(sentence)?.let { m ->
            val condition = itCondition(m.groupValues[1], context) ?: Conditions.condition(m.groupValues[1], self)
            val then = condition?.let { effect(m.groupValues[2].replaceFirstChar { it.uppercase() } + ".", context) }
            if (condition != null && then != null) return listOf(Step.If(condition, then = then))
        }
        // chunk 21: "You may [do something]." with nothing depending on it (CR 608.2d)
        // chunk 51: "Target creature you control gains shroud until end of turn."
        TARGET_GAINS_UNTIL_EOT.matchEntire(sentence)?.let { m ->
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it) ?: return null }
            val target = context.target(Filters.slotName(m.groupValues[1]), Filters.noun(m.groupValues[1]) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords)), arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        // chunk 50: "Sacrifice [this creature]."
        SACRIFICE_SELF.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Sacrifice(Objects.Self)))
        }
        // chunk 50: "[Do X] unless you [pay a cost]." (CR 118.12a)
        UNLESS_YOU.matchEntire(sentence)?.let { m ->
            costPart(m.groupValues[2].replaceFirstChar { it.uppercase() })?.let { parts ->
                effect(m.groupValues[1] + ".", context)?.let { return unlessPays(Players.You, Cost(parts), it) }
            }
        }
        // chunk 52: "Reveal cards from the top of your library until you reveal an Elf or Elemental card." (CR 701.20)
        REVEAL_UNTIL.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return listOf(Step.Do(Effect.RevealUntil(filter, foundBind = FOUND, restBind = REST, library = Players.You, count = Value.Fixed(1), reveal = true)))
        }
        // chunk 52: "Draw that many cards." (the cards of a discard batch)
        if (sentence == "Draw that many cards." && context.event is EventPattern.Discard) {
            return listOf(Step.Do(Effect.Draw(Players.You, Value.Count(context.that ?: return null))))
        }
        // chunk 52: "That player reveals the chosen card, puts it on the bottom of their library, then draws a card."
        if (sentence == "That player reveals the chosen card, puts it on the bottom of their library, then draws a card.") {
            val player = context.player ?: return null
            val card = Objects.Bound(context.chosen.lastOrNull() ?: return null)
            return listOf(Step.Do(Effect.Reveal(card)), Step.Do(Effect.Move(card, Destination(Zone.Library, libraryPosition = -1))),
                Step.Do(Effect.Draw(player, Value.Fixed(1))))
        }
        YOU_MAY.matchEntire(sentence)?.let { m ->
            effect(m.groupValues[1].replaceFirstChar { it.uppercase() } + ".", context)?.let { return listOf(Step.May(it, player = Players.You)) }
        }
        // chunk 36
        if (sentence == "Discard your hand.") return listOf(Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand), count = Value.Fixed(1), filter = ObjectFilter.Any)))
        // "Put all cards exiled with this creature into their owners' hands." (CR 607.2a)
        PUT_ALL_EXILED_WITH_INTO_HANDS.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return listOf(Step.Do(Effect.Move(Objects.Remembered(EXILED_WITH), Destination(Zone.Hand))))
        }
        // "You may cast it this turn." (a permission for the exiled card, CR 601.2a)
        if (sentence == "You may cast it this turn." && context.it == Objects.Bound("exiled")) {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, arcanum.api.PlayKind.Cast, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), arcanum.api.Duration.EndOfTurn)))
        }
        // "Sacrifice another permanent.": the controller chooses one they control (CR 701.21a)
        SACRIFICE_A.matchEntire(sentence)?.let { m ->
            val kind = Filters.noun(m.groupValues[2]) ?: return null
            val another = if (m.groupValues[1] == "another") listOf(context.another()) else emptyList()
            val filter = Filters.all(*(another + listOf(kind, ObjectFilter.ControlledBy(PlayerFilter.You))).toTypedArray())
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "sacrificed", chooser = Players.You),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))
        }
        // "Exile up to one other target nonland permanent."
        EXILE_UP_TO_TARGET.matchEntire(sentence)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[3]
            val other = if (m.groupValues[2].isEmpty()) emptyList() else listOf(context.another())
            val filter = Filters.all(*(other + listOf(Filters.noun(noun) ?: return null)).toTypedArray())
            val slot = Filters.slotName(noun)
            context.targets += TargetSlot(slot, TargetKind.Objects(filter), min = Value.Fixed(0), max = Value.Fixed(n))
            context.it = Objects.Bound("exiled")
            // dsk chunk 04: linked to "cards exiled with this creature" (CR 607.2a)
            return listOf(Step.Do(Effect.Move(Objects.Target(slot), Destination(Zone.Exile), postBind = "exiled"))) +
                (if (linkedExile || exiledCard == Objects.Remembered(EXILED_WITH)) remembered(Objects.Bound("exiled")) else emptyList())
        }
        // "At the beginning of the next end step, [effect].": a delayed trigger (CR 603.7), about the objects named now
        AT_BEGINNING_OF_NEXT_END_STEP.matchEntire(sentence)?.let { m ->
            val targets = context.targets.size
            val inner = effect(m.groupValues[1].replaceFirstChar { it.uppercase() }, context)?.takeIf { context.targets.size == targets } ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(arcanum.api.Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, whose = PlayerFilter.Any)),
                Instructions(steps = inner))))
        }
        // "Return that card to the battlefield under its owner's control."
        RETURN_IT_UNDER_OWNERS_CONTROL.matchEntire(sentence)?.let {
            val card = context.it ?: return null
            return listOf(Step.Do(Effect.Move(card, Destination(Zone.Battlefield, controller = Players.OwnerOf(card)), postBind = "returned")))
        }
        MILL.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        // "Each opponent loses life equal to that card's mana value." (read as it last was, CR 608.2h)
        LOSE_LIFE_EQUAL_TO_THAT_CARDS_MANA_VALUE.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val card = context.it ?: return null
            return listOf(Step.Do(Effect.LoseLife(players, Value.Of(arcanum.api.NumericProperty.ManaValue, card, arcanum.api.Combine.Single))))
        }
        // "[Effect] unless [condition]." (not "unless [player] pays", which is a pay, see unlessPays)
        UNLESS_CONDITION.matchEntire(sentence)?.let { m ->
            Conditions.condition(m.groupValues[2], self)?.let { condition ->
                val then = effect(m.groupValues[1] + ".", context) ?: return null
                return listOf(Step.If(arcanum.api.Condition.Not(condition), then = then))
            }
        }
        // dsk chunk 04: "Draw a card if you control a Glimmer creature." (checked as it resolves)
        EFFECT_IF_CONDITION.matchEntire(sentence)?.let { m ->
            val condition = Conditions.condition(m.groupValues[2], self)
            val targets = context.targets.size
            val then = condition?.let { effect(m.groupValues[1] + ".", context) }
            if (condition != null && then != null) return listOf(Step.If(condition, then = then))
            while (context.targets.size > targets) context.targets.removeAt(context.targets.lastIndex)
        }
        // "[A], then [B].": tried last, after the wordings that contain ", then" themselves.
        THEN.matchEntire(sentence)?.let { m ->
            val first = effect(m.groupValues[1] + ".", context) ?: return null
            val second = effect(m.groupValues[2].replaceFirstChar { it.uppercase() }, context) ?: return null
            return first + second
        }
        // chunk 35: "[A] and [B].", two effects in one sentence ("each opponent loses 1 life and you gain 1 life"): tried
        // last, at each " and " from the left, only when both halves are whole effects.
        if (sentence.endsWith(".")) {
            var at = sentence.indexOf(" and ")
            while (at > 0) {
                val targets = context.targets.size
                val first = effect(sentence.substring(0, at) + ".", context)
                val second = first?.let { effect(sentence.substring(at + 5).replaceFirstChar { it.uppercase() }, context) }
                if (first != null && second != null) return first + second
                while (context.targets.size > targets) context.targets.removeAt(context.targets.lastIndex)
                at = sentence.indexOf(" and ", at + 5)
            }
        }
        return null
    }

    /** chunk 47: "Exile that card from your graveyard" as a cost paid on resolution (the discarded card, CR 118.12); "that
     * card" afterwards is the exiled card. */
    private fun exileThatCard(action: String, context: Context): List<CostPart>? {
        if (action != "Exile that card from your graveyard") return null
        val that = context.that ?: return null
        context.it = Objects.Bound("exiled")
        return listOf(CostPart.Exile(CostObjects.These(that), from = Zone.Graveyard, postBind = "exiled"))
    }

    /** dsk chunk 02: what mana may be spent on ("cast Room spells and unlock doors", "cast an enchantment spell, unlock a door,
     * or turn a permanent face up"): spells, and the special actions of unlocking (CR 709.5e) and turning face up (116.2b). */
    private fun spendOnlyTo(text: String): ObjectFilter? {
        val uses = text.split(Regex(""",? or |,? and |, """)).map { use ->
            when (use) {
                "unlock doors", "unlock a door" -> ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Unlock)
                "turn permanents face up", "turn a permanent face up" -> ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.TurnFaceUp)
                // as "Spend this mana only to cast artifact spells" reads (chunk 03)
                else -> CAST_SPELLS.matchEntire(use)?.let { m -> Filters.noun(m.groupValues[1])?.let { Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), it) } } ?: return null
            }
        }
        return uses.singleOrNull() ?: ObjectFilter.Or(uses)
    }

    /** dsk chunk 05: "Enchanted creature", "Equipped creature": the object this Aura or Equipment is attached to. */
    private fun attachedSubject(text: String): Objects? =
        if (text == "Enchanted creature" || text == "Equipped creature" || text == "enchanted creature") Objects.AttachedTo(Objects.Self) else null

    /** dsk chunk 01: "it's a Reflection in addition to its other creature types" (CR 707.9b). */
    private fun copyExceptions(text: String): List<arcanum.api.CopyException>? {
        ADDITIONAL_CREATURE_TYPE.matchEntire(text)?.let { m ->
            val subtype = m.groupValues[1].takeIf { it in RulesWords.subtypes } ?: return null
            return listOf(arcanum.api.CopyException.Types(addSubtypes = listOf(arcanum.api.SubtypeRef.Literal(Subtype(subtype)))))
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

    /** chunk 56: a condition about "it", the object an earlier sentence acted on ("it has no ice counters on it"). */
    private fun itCondition(text: String, context: Context): arcanum.api.Condition? {
        val it = context.it ?: return null
        val m = IT_HAS_NO_COUNTERS.matchEntire(text) ?: return null
        val kind = Counters.kind(m.groupValues[1]) ?: return null
        return arcanum.api.Condition.ObjectMatches(it, ObjectFilter.Not(ObjectFilter.HasCounter(kind, atLeast = 1)))
    }

    /** Keeps [objects] as cards "exiled with" this object when the card refers to them (CR 607.2a). */
    // chunk 21
    private fun remembered(objects: Objects): List<Step> =
        if (linkedExile) listOf(Step.Do(Effect.Remember(EXILED_WITH, objects))) else emptyList()

    /** chunks 30–33: effect wordings of creatures' enters (and leaves) abilities; null when none reads [sentence]. */
    private fun enters(sentence: String, context: Context): List<Step>? {
        // chunk 30
        LOSE_LIFE_AND_YOU_GAIN.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            val steps = mutableListOf<Step>(Step.Do(Effect.LoseLife(players, Value.Fixed(m.groupValues[2].toInt()))))
            if (m.groupValues[3].isNotEmpty()) steps += Step.Do(Effect.GainLife(Players.You, Value.Fixed(m.groupValues[3].toInt())))
            return steps
        }
        PLAYERS_DISCARD.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.Discard(Filters.players(m.groupValues[1]) ?: return null, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null), filter = ObjectFilter.Any)))
        }
        if (sentence == "Discard your hand.") return listOf(Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand), count = Value.Fixed(1), filter = ObjectFilter.Any)))

        // chunk 31
        RETURN_ALL_OR_SELF_TO_BATTLEFIELD.matchEntire(sentence)?.let { m ->
            val to = Destination(Zone.Battlefield, tapped = m.groupValues[3].isNotEmpty())
            val what = m.groupValues[2]
            if (m.groupValues[1].isEmpty()) return if (isSelf(what)) listOf(Step.Do(Effect.Move(Objects.Self, to))) else null
            val filter = Filters.all(Filters.noun(what) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
            val objects = if (m.groupValues[1] == "target ") context.target(Filters.slotName(what), filter) else Objects.Each(filter)
            return listOf(Step.Do(Effect.Move(objects, to)))
        }
        if (sentence == "Return it to your hand.") return listOf(Step.Do(Effect.Move(context.it ?: return null, Destination(Zone.Hand))))
        MAY_PUT_FROM_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, yourHand)
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, tapped = m.groupValues[2].isNotEmpty()))))
        }
        PUT_IT.matchEntire(sentence)?.let { m ->
            val to = if (m.groupValues[1] == "into your hand") Destination(Zone.Hand) else Destination(Zone.Battlefield)
            return listOf(Step.Do(Effect.Move(context.it ?: return null, to)))
        }
        LOOK_PUT_UP_TO_ONE_ON_TOP.matchEntire(sentence)?.let { m ->
            val n = if (m.groupValues[1] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            return listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n, fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Library, libraryPosition = 0))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }
        LOOK_OR_REVEAL_TOP.matchEntire(sentence)?.let { m ->
            // dsk chunk 04: "the top X cards" with X defined by the ability ("where X is its power")
            val n = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null)
            context.seen = "seen"
            val cards = Objects.LibraryCards(Players.You, n, fromTop = true)
            return listOf(Step.Do(if (m.groupValues[1] == "Look at") Effect.LookAt(cards, bind = "seen", players = Players.You) else Effect.Reveal(cards, bind = "seen")))
        }
        MAY_FROM_AMONG_INTO_HAND.matchEntire(sentence)?.let { m ->
            val seen = context.seen ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            context.picked = "chosen"
            return listOfNotNull(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound(seen)), bind = "chosen", chooser = Players.You),
                if (m.groupValues[1] == "reveal") Step.Do(Effect.Reveal(Objects.Bound("chosen"))) else null,
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        PUT_THE_REST.matchEntire(sentence)?.let { m ->
            val rest = Objects.Except(Objects.Bound(context.seen ?: return null), Objects.Bound(context.picked ?: return null))
            val to = if (m.groupValues[1] == "into your graveyard") Destination(Zone.Graveyard)
                else Destination(Zone.Library, libraryPosition = -1, randomOrder = true)
            return listOf(Step.Do(Effect.Move(rest, to)))
        }
        WIN_IF_X_AT_LEAST.matchEntire(sentence)?.let { m ->
            val other = Values.value(m.groupValues[1], exiledCard) ?: return null
            return listOf(Step.If(arcanum.api.Condition.Compare(Value.Announced(name = "X"), arcanum.api.Comparison.AtLeast, other),
                then = listOf(Step.Do(Effect.WinGame(Players.You)))))
        }
        // "The flashback cost is equal to its mana cost" (CR 702.34a: cast from the graveyard for that cost)
        GAINS_FLASHBACK.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
                arcanum.api.Affected.Objects(target), arcanum.api.Duration.EndOfTurn)))
        }
        CHOOSE_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            context.it = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            context.itZone = if (noun.endsWith(" in your graveyard")) Zone.Graveyard else null
            return emptyList()
        }
        if (sentence == "You may cast that card this turn.") {
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(context.itZone ?: return null, how = PlayKind.Cast, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(context.it ?: return null), arcanum.api.Duration.EndOfTurn)))
        }
        GRAVEYARD_ON_BOTTOM.matchEntire(sentence)?.let { m ->
            context.targets += TargetSlot("player", TargetKind.Players(PlayerFilter.Any),
                min = Value.Fixed(if (m.groupValues[1] == "Target") 1 else 0), max = Value.Fixed(1))
            val graveyard = Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))
            return listOf(Step.Do(Effect.Move(Objects.Each(graveyard), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
        }

        // chunk 32
        RETURN_YOURS_TO_HAND.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))
        }
        TARGET_DEALS_DAMAGE_TO_ITSELF.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(objects = target), Value.Fixed(m.groupValues[2].toInt()), source = target)))
        }
        UNTAP_ANOTHER_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val chosen = if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)
            return listOf(Step.Do(Effect.Untap(context.target(Filters.slotName(noun), chosen))))
        }
        GAIN_AND_GET.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].replaceFirstChar { it.lowercase() }) ?: return null
            val keywords = m.groupValues[2].split(Regex(""",? and |, """)).map { OracleParser.keyword(it.replaceFirstChar { c -> c.uppercase() }) ?: return null }
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.GainAbilities(keywords), Mechanic.ModifyPowerToughness(xChange(m.groupValues[3]) ?: return null, xChange(m.groupValues[4]) ?: return null)),
                arcanum.api.Affected.Objects(Objects.Each(filter)), arcanum.api.Duration.EndOfTurn)))
        }
        CREATE_COPY_OF_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[2]
            val filter = Filters.noun(noun) ?: return null
            val chosen = if (m.groupValues[1].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)
            // dsk chunk 01: ", except it's a Reflection in addition to its other creature types" (CR 707.9b)
            val except = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { copyExceptions(it) ?: return null }.orEmpty()
            return listOf(Step.Do(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(context.target(Filters.slotName(noun), chosen), except), count = Value.Fixed(1), controller = Players.You)))
        }

        // chunk 33
        EACH_SACRIFICES.matchEntire(sentence)?.let { m ->
            val player = m.groupValues[1]
            val who = if (player == "player") PlayerFilter.Any else PlayerFilter.Opponent
            val filter = Filters.all(Filters.noun(m.groupValues[2]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound(player))))
            return listOf(Step.ForEach(arcanum.api.Each.Players(Players.Each(who)), bind = player, together = true, steps = listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "sacrificed", chooser = Players.Bound(player)),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))))))
        }
        // "gain control of": a control-changing effect with no end (CR 613.1b)
        GAIN_CONTROL_OF_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val target = context.target(Filters.slotName(noun), Filters.noun(noun) ?: return null)
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), arcanum.api.Affected.Objects(target),
                arcanum.api.Duration.Indefinitely)))
        }
        IF_THAT_IS_ATTACH_IT.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[3]) }?.let { m ->
            val slot = context.targets.lastOrNull()?.takeIf { it.name == m.groupValues[1] } ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            val target = Objects.Target(slot.name)
            return listOf(Step.If(arcanum.api.Condition.ObjectMatches(target, filter),
                then = listOf(Step.Do(Effect.Attach(target, arcanum.api.Recipients(objects = Objects.Self))))))
        }
        // "copy the next … spell you cast this turn when you cast it": a delayed trigger that fires once (CR 603.7)
        COPY_NEXT_SPELL.matchEntire(sentence)?.let { m ->
            val spell = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateDelayedTrigger(
                arcanum.api.Trigger.On(EventPattern.Cast(spell, by = PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(role = EventRole.Object), newTargets = false, controller = Players.You)))),
                duration = arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "Look at target opponent's hand.") {
            return listOf(Step.Do(Effect.LookAt(Objects.Each(handOf(context.targetOpponent())), players = Players.You)))
        }
        TARGET_REVEALS_HAND_YOU_CHOOSE.matchEntire(sentence)?.let { m ->
            val opponent = context.targetOpponent()
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, handOf(opponent))
            context.it = Objects.Bound("chosen")
            return listOf(
                Step.Do(Effect.Reveal(Objects.Each(handOf(opponent)))),
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You))
        }
        if (sentence == "Exile that card.") return exile(context.it ?: return null)
        MAY_EXILE_FROM_IT_UNTIL_LEAVES.matchEntire(sentence)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            val filter = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, handOf(context.lastPlayer ?: return null))
            return listOf(
                Step.Choose(arcanum.api.Choice.Objects(filter, min = Value.Fixed(0), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), untilLeaves = Objects.Self)))
        }
        EXILED_TO_HAND.matchEntire(sentence)?.let { return listOf(Step.Do(Effect.Move(exiledCard ?: return null, Destination(Zone.Hand)))) }
        EXILED_OWNER_CREATES.matchEntire(sentence)?.let { m ->
            val exiled = exiledCard ?: return null
            val (count, token) = Tokens.spec(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.OwnerOf(exiled))))
        }
        if (sentence == "The exiled card's owner may cast that card without paying its mana cost.") {
            val exiled = exiledCard ?: return null
            val owner = Players.OwnerOf(exiled)
            return listOf(Step.May(listOf(Step.Do(Effect.Cast(exiled, withoutPaying = true, caster = owner, how = PlayKind.Cast))), player = owner))
        }
        // An exile no card memory needs is read by EXILE_UP_TO_TARGET, which names it for "that card" later.
        UP_TO_ONE_TARGET.matchEntire(sentence)?.takeIf { it.groupValues[1] == "Destroy" || exiledCard == Objects.Remembered(EXILED_WITH) }?.let { m ->
            val noun = m.groupValues[3]
            val filter = Filters.noun(noun) ?: return null
            val name = Filters.slotName(noun)
            context.targets += TargetSlot(name, TargetKind.Objects(if (m.groupValues[2].isEmpty()) filter
                else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter)), min = Value.Fixed(0), max = Value.Fixed(1))
            return if (m.groupValues[1] == "Exile") exile(Objects.Target(name)) else listOf(Step.Do(Effect.Destroy(Objects.Target(name))))
        }
        DAMAGE_EQUAL_TO_POWER.matchEntire(sentence)?.takeIf { it.groupValues[1] == "It" || isSelf(it.groupValues[1]) }?.let {
            context.anyTarget()
            return listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(target = "any"),
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, arcanum.api.Combine.Single), source = Objects.Self)))
        }
        OPPONENTS_CANT_CAST.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(Filters.noun(m.groupValues[1]) ?: return null)),
                arcanum.api.Affected.Players(Players.Each(PlayerFilter.Opponent)), arcanum.api.Duration.EndOfTurn)))
        }
        if (sentence == "That creature's owner can't cast spells with the same name as that creature until your next turn.") {
            val creature = context.targets.lastOrNull()?.let { Objects.Target(it.name) } ?: return null
            return listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.CantCast(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), ObjectFilter.SameNameAs(creature)))),
                arcanum.api.Affected.Players(Players.OwnerOf(creature)), arcanum.api.Duration.UntilYourNextTurn)))
        }
        YOU_AND_TARGET_OPPONENT_DRAW.matchEntire(sentence)?.let { m ->
            val n = Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null)
            val players = Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(context.targetOpponent()))))
            return listOf(Step.Do(Effect.Draw(players, n)))
        }
        return null
    }

    /** chunk 33: [step] with its spell copies (in delayed triggers, ifs and repeats too) allowed new targets. */
    private fun withNewTargets(step: Step): Step? = when (step) {
        is Step.If -> step.copy(then = step.then.map { withNewTargets(it) ?: return null }, otherwise = step.otherwise.map { withNewTargets(it) ?: return null })
        is Step.Repeat -> step.copy(steps = step.steps.map { withNewTargets(it) ?: return null })
        is Step.Do -> when (val e = step.effect) {
            is Effect.CopySpell -> Step.Do(e.copy(newTargets = true))
            is Effect.CreateDelayedTrigger -> Step.Do(e.copy(instructions = e.instructions.copy(steps = e.instructions.steps.map { withNewTargets(it) ?: return null })))
            else -> null
        }
        else -> null
    }

    /** chunk 32: "+3", "-1", "+X": X as announced, defined later by "where X is …" or the spell's X (CR 107.3c, m). */
    private fun xChange(text: String): Value? = when (text) {
        "+X" -> Value.Announced(name = "X")
        "-X" -> Value.Times(Value.Announced(name = "X"), -1)
        else -> text.removePrefix("+").toIntOrNull()?.let { Value.Fixed(it) }
    }

    /** chunk 33: the cards in [player]'s hand. */
    private fun handOf(player: Players) = Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(player)))

    /** chunk 33: exile [objects]; when a later ability refers to "the exiled card", keep them as card memory (CR 607.2a). */
    private fun exile(objects: Objects): List<Step> =
        if (exiledCard == Objects.Remembered(EXILED_WITH)) listOf(
            Step.Do(Effect.Move(objects, Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember(EXILED_WITH, Objects.Bound("exiled"))))
        else listOf(Step.Do(Effect.Move(objects, Destination(Zone.Exile))))

    /** "{C}", "{B}{B}", "{R} or {G}", "one mana of any color", "three mana of any one color" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        ANY_COMBINATION.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null), combination = true) }
        ANY_COLOR.matchEntire(text)?.let { m -> return ManaKind.Choose(Value.Fixed(Numbers.count(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null)) }
        ManaCost.parse(text)?.let { return ManaKind.Fixed(it) }
        val colors = text.split(Regex(""",? or |, """)).map { symbol -> ManaCost.parse(symbol)?.symbols?.singleOrNull()
            ?.let { (it as? arcanum.api.ManaSymbol.Colored)?.color } ?: return null }
        return ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })
    }

    /** chunk 26: "+1", "-X" in "gets +1/+1": X of the spell, or of the permanent's spell (CR 107.3a, 107.3m). */
    private fun ptChange(text: String): Value? {
        val x = if (isSpell || announcedX) Value.Announced(name = "X") else Value.CastX(of = Objects.Self)
        return when (text) {
            "+X" -> x
            "-X" -> Value.Times(x, -1)
            else -> text.removePrefix("+").toIntOrNull()?.let { Value.Fixed(it) }
        }
    }

    /** chunk 26: the steps with the last created tokens named [TOKENS] ("they", "them"). */
    private fun bindTokens(steps: List<Step>): List<Step>? {
        val index = steps.indexOfLast { (it as? Step.Do)?.effect is Effect.CreateToken }.takeIf { it >= 0 } ?: return null
        val create = (steps[index] as Step.Do).effect as Effect.CreateToken
        if (create.bind != null && create.bind != TOKENS) return null
        return steps.toMutableList().also { it[index] = Step.Do(create.copy(bind = TOKENS)) }
    }

    /** Sentences in order; a "Spend this mana only …" sentence stays with the mana it restricts. */
    private fun sentences(text: String): List<String> {
        val raw = SENTENCE_END.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.endsWith(".")) it else "$it." }.map { it.replaceFirstChar { c -> c.uppercase() } }
        return raw.fold(mutableListOf()) { acc, sentence ->
            // chunk 14: "The flashback cost is equal to its mana cost." completes the sentence before it.
            if ((sentence.startsWith("Spend this mana only") || sentence.startsWith("If you do,") || sentence.startsWith("The flashback cost is") ||
                    sentence.startsWith("When you do,") || // chunk 31
                    (sentence.startsWith("Put ") && acc.lastOrNull()?.startsWith("Look at the top") == true) ||
                    // chunk 47: a reflexive trigger ("When you do, …") runs to the end of the ability (CR 603.12)
                    acc.lastOrNull()?.contains(" When you do, ") == true) && acc.isNotEmpty())
                acc[acc.size - 1] = acc.last() + " " + sentence
            else acc += sentence
            acc
        }
    }

    private companion object {
        // chunk 35: also after a quoted ability that ends a sentence ('… with "This token can't block." If …')
        val SENTENCE_END = Regex("""(?<=\.)\s+|(?<=\.")\s+""")
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
        val ITS_CONTROLLER_GAINS_LIFE_EQUAL_TO_POWER = Regex("""(?:Its|That creature's) controller gains life equal to its power\.""")
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
        val COSTS_LESS_FOR_EACH = Regex("""This ability costs ((?:\{[^}]+\})+) less to activate for each (.+?)\.""")
        val DISCARD_COST = Regex("""Discard (.+)""")
        val THEN = Regex("""(.+?), then (.+)""")
        val ADD_MANA_RESTRICTED = Regex("""Add (.+)\. Spend this mana only to cast (.+) spells\.""")
        val ADD_MANA_FOR_EACH = Regex("""Add (\{.\}) for each (.+)\.""")
        val DRAW = Regex("""Draw (a|one|two|three|four|five|six|seven) cards?\.""")
        val DISCARD = Regex("""[Dd]iscard (a|one|two|three|four|five|six|seven) cards?\.""")
        val CREATE_TOKEN = Regex("""[Cc]reate (.+ tokens?(?: with .+)?)\.""")
        val SELF_DEALS_DAMAGE_TO_EACH = Regex("""(.+?) deals (\d+) damage to (each .+)\.""")
        val EXILE_THEN_RETURN = Regex("""Exile (another )?target (.+), then return (?:it|that card) to the battlefield under its owner's control\.""")
        val TARGET_PLAYER_DRAWS = Regex("""Target player draws (\w+) cards?(?:, then discards (\w+) cards?)?\.""")
        val TARGET_PLAYER_DISCARDS = Regex("""Target player discards (\w+) cards?\.""")
        val TARGET_PLAYER_CREATES = Regex("""Target player creates (.+ tokens?)\.""")
        val RETURN_FROM_GRAVEYARD_TO_HAND = Regex("""Return (another )?target (.+) from your graveyard to your hand\.""")
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
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+?)(, reveal (?:it|that card))?, put (?:it|that card) (onto the battlefield(?: tapped)?|into your hand|into your graveyard)(, discard a card at random)?, then shuffle\.""")
        val SEARCH_TO_TOP = Regex("""Search your library for (.+?), (reveal it, )?then shuffle and put that card on top\.""")
        val PUT_FROM_HAND_ON_TOP = Regex("""Put (\w+) cards? from your hand on top of your library(?: in any order)?\.""")
        val PLAYERS_DRAW = Regex("""(Each player|You) draws? (\w+) cards?(?:, then discards? (\w+) cards?( at random)?)?(?: and loses? (\d+) life)?\.""")
        val EACH_PLAYER_DISCARDS_HAND_DRAWS = Regex("""Each player discards their hand, then draws (\w+) cards\.""")
        val DISCARD_UNLESS_ATTACKED = Regex("""Discard (a|one|two) cards? unless you attacked this turn\.""")
        val SCRY_OR_SURVEIL = Regex("""(Scry|Surveil) (\d+)\.""")
        val TARGET_PLAYER_MILLS = Regex("""Target player mills (\w+) cards?\.""")
        val LOOK_PUT_BACK = Regex("""Look at the top (\w+) cards of your library, then put them back in any order\.""")
        val TARGET_GETS_UNTIL_EOT = Regex("""Target (.+) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val RETURN_UP_TO_FROM_GRAVEYARD_TO_HAND = Regex("""Return up to (\w+) target (.+) from your graveyard to your hand\.""")
        val YOU_LOSE_LIFE = Regex("""You lose (\d+) life\.""")
        val ANY_COMBINATION = Regex("""(\w+) mana in any combination of colors""")
        // dsk chunk 01
        val ONE_OR_MORE_ATTACK_A_PLAYER = Regex("""one or more (.+) attack a player""")
        val ONE_OR_MORE_DEAL_COMBAT_DAMAGE_TO_A_PLAYER = Regex("""one or more (.+) deal combat damage to a player""")
        val UNTAP_DURING_OTHER_UNTAP_STEPS = Regex("""Untap each (.+) during each other player's untap step\.""")
        val HAVE_BASE_PT_EQUAL_TO_NUMBER = Regex("""(.+) have base power and toughness each equal to the number of (.+)\.""")
        val TRIGGERS_ADDITIONAL_TIME = Regex("""If a triggered ability of an? (.+) triggers, that ability triggers an additional time\.""")
        val TAP_TARGET = Regex("""Tap (?:up to (\w+) )?(another )?target (.+)\.""")
        val PUT_COUNTERS_ON_UP_TO = Regex("""Put (a|an|one|two|three) (\S+) counters? on (?:each of )?up to (\w+) target (.+)\.""")
        val RETURN_UP_TO_TARGET_TO_HAND = Regex("""Return up to (\w+) target (.+) to (?:its|their) owners?'s? hands?\.""")
        val YOU_WIN_IF = Regex("""You win the game if (.+)\.""")
        val ADDITIONAL_CREATURE_TYPE = Regex("""it's an? (\w+) in addition to its other creature types""")
        const val MANIFESTED = "manifested"
        val DESTROY_ALL = Regex("""Destroy all (.+)\.""")
        // dsk chunk 05
        val LOSES_ALL_AND_IS = Regex("""(Enchanted creature) loses all abilities and is an? (.+?) with base power and toughness (\d+)/(\d+) in addition to its other types\.""")
        val FACE_DOWN_CANT_BE_TURNED_FACE_UP = Regex("""As long as (enchanted creature) is face down, it can't be turned face up\.""")
        val DOESNT_UNTAP_DURING_CONTROLLERS = Regex("""(Enchanted creature|Equipped creature) doesn't untap during its controller's untap step\.""")
        val PLAYER_MANIFESTS_DREAD = Regex("""(Its controller|The exiled card's owner) manifests dread\.""")
        val ATTACH_SELF_TO_THAT_CREATURE = Regex("""Attach (.+) to that creature\.""")
        val WHEN_IT_DIES_THIS_TURN = Regex("""When it dies this turn, (.+)""")
        // dsk chunk 04
        val TRIGGERS_ONLY_ONCE_EVER = Regex("""This ability triggers only once\.""")
        val CANT_BE_BLOCKED_BY = Regex("""(.+) can't be blocked by (.+)\.""")
        val PUT_ONE_OF_COUNTERS = Regex("""Put an? ((?:[^ ,]+, )+or [^ ]+) counter on (.+)\.""")
        val PUT_THOSE_COUNTERS_ON = Regex("""Put those counters on (up to one )?target (.+)\.""")
        val THAT_BECOMES_CREATURE = Regex("""That (\w+) becomes an? (\d+)/(\d+) (\w+) creature in addition to its other types\.""")
        val IT_GAINS_UNTIL_YOUR_NEXT_TURN = Regex("""It gains (.+) until your next turn\.""")
        val PUT_ANY_NUMBER_WITH_DIFFERENT_FROM_AMONG = Regex("""Put any number of (.+?) with different (powers|mana values) from among them into your hand\.""")
        val EFFECT_IF_CONDITION = Regex("""(.+?) if (.+)\.""")
        // dsk chunk 03
        val UNLOCK_COSTS_LESS = Regex("""Unlock costs you pay cost ((?:\{[^}]+\})+) less\.""")
        val UNLOCK_A_LOCKED_DOOR = Regex("""Unlock a locked door of (?:up to (\w+) )?(target |an? )(.+)\.""")
        /** A Room with at least one locked door (CR 709.5c). */
        val HAS_A_LOCKED_DOOR = ObjectFilter.Not(ObjectFilter.And(listOf(
            ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Unlocked(arcanum.api.Door.Left))),
            ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Unlocked(arcanum.api.Door.Right))))))
        val LOCK_OR_UNLOCK = Regex("""Lock or unlock a door of target (.+)\.""")
        val RETURN_OR_UNLOCK = Regex("""Return an? (.+?) from your graveyard to your hand or (unlock .+)\.""")
        val CAN_ATTACK_AS_THOUGH_NO_DEFENDER = Regex("""(.+) can attack this turn as though it didn't have defender\.""")
        val EFFECT_IF = Regex("""(.+) if (this is the \w+ time this ability has resolved this turn)\.""")
        val PUT_CARD_FROM_A_GRAVEYARD_ONTO_BATTLEFIELD = Regex("""Put an? (.+?) from a graveyard onto the battlefield under your control\.""")
        val PUT_ALL_FROM_AMONG_THEM_REST_BOTTOM = Regex("""Put all (.+?) from among them into your hand and the rest on the bottom of your library in a random order\.""")
        // dsk chunk 02
        val NO_MAXIMUM_HAND_SIZE = Regex("""You have no maximum hand size( and don't lose the game for having 0 or less life)?\.""")
        val ALTERNATIVE_COST_FOR_SPELLS = Regex("""(Once each turn, )?[Yy]ou may pay (\{[^ ]+\}) rather than pay the mana cost for (?:a spell|spells) you cast( from exile)?\.""")
        val GETS_AND_GAINS_UNTIL_EOT = Regex("""(.+?) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) and gains (.+) until end of turn\.""")
        val DEALS_DAMAGE_EQUAL_TO = Regex("""(.+?) deals damage equal to (the number of .+?) to (any target|target .+|each opponent)\.""")
        val YOU_MAY_PLAY_EXILED = Regex("""You may play (it|them) (this turn|until the end of your next turn)\.""")
        val MILL_THEN_RETURN_FROM_AMONG_THEM = Regex("""Mill (\w+) cards, then return up to (\w+) (.+) from among them to your hand\.""")
        val ADD_MANA_SPEND_ONLY_TO = Regex("""Add (.+)\. Spend this mana only to (.+)\.""")
        val CAST_SPELLS = Regex("""cast (?:an? )?(.+?) spells?""")
        // chunk 12
        const val DISCARDED = "discarded"
        const val RETURNED = "returned"
        val SEARCH_LIBRARY_AND_OR_GRAVEYARD = Regex("""Search your library and/or graveyard for (.+?) and put it onto the battlefield\.""")
        val IF_X_AT_LEAST = Regex("""If X is (\d+) or more, (.+)""")
        val GROUP_GETS_UNTIL_EOT = Regex("""(.+?) get ([+-](?:\d+|X))/([+-](?:\d+|X))(?: and gain (.+?))? until end of turn\.""")
        val SHUFFLE_INTO_OWNERS_LIBRARY = Regex("""Shuffle (.+) into its owner's library\.""")
        val DRAW_OR_DISCARD_X = Regex("""(Draw|Discard) X cards\.""")
        val RETURN_ANY_NUMBER_OF_TARGETS_TO_HAND = Regex("""Return any number of target (.+) to their owners' hands\.""")
        val CREATE_FOR_EACH_CARD_TYPE_DISCARDED = Regex("""Create (.+ token(?: with .+?)?) for each card type among cards discarded this way\.""")
        val DRAW_FOR_EACH_RETURNED_TO_HAND = Regex("""Draw a card for each (.+) returned to your hand this way\.""")
        // chunk 25
        val MAY_CAST_WITHOUT_PAYING = Regex("""(Any player|You) may cast (.+ spells.*?) without paying their mana costs( and as though they had flash)?\.""")
        val IF_YOU_WOULD_DRAW = Regex("""If you would draw a card, (.+?) instead\.(?: (.+))?""")
        val DONT_UNTAP_PLURAL = Regex("""(.+) don't untap during their controllers' untap steps\.""")
        val EXTRA_LAND_PLAYS = Regex("""You may play (an additional land|any number of lands|\w+ additional lands) on each of your turns\.""")
        val ENCHANTED_IS = Regex("""Enchanted \w+ is an? (.+?)(?: with (.+?))?( and it loses all other card types)?\.""")
        const val SACRIFICED = "sacrificed"
        val OTHERWISE = Regex("""Otherwise, (.+)""")
        val UNTAP_TARGET = Regex("""Untap target (.+)\.""")
        val UNTAP_ALL = Regex("""Untap all (.+)\.""")
        val IF_ITS_A = Regex("""If it's an? (.+?), (.+)""")
        val PUT_IT_INTO = Regex("""(?:Put|Return) (it|them|that card) (?:into|to) (?:your|its owner's|their owner's|their owners') (hand|graveyard)s?\.""")
        val ADD_AS_SACRIFICED_COULD = Regex("""Add one mana of any type the sacrificed \w+ could produce\.""")
        // chunk 26
        val HAVE_QUOTED = Regex("""(.+) have "(.+)"""")
        val CANT_BE_BLOCKED = Regex("""(.+) can't be blocked\.""")
        val LOYALTY = Regex("""\[([+−-]?)(\d+)\]""")
        const val TOKENS = "tokens"
        val THEY_GAIN = Regex("""They gain (.+)\.""")
        val YOU_MAY_IF_YOU_DO = Regex("""You may (.+?)\. If you do, (.+)""")
        val THAT_CREATURES_CONTROLLER_MAY_DRAW = Regex("""That creature's controller may draw (a|one|two) cards?\.""")
        val DEALS_THAT_MUCH = Regex("""(.+?) deals that much damage to (.+)\.""")
        val PUT_COUNTERS_ON_EACH = Regex("""Put (a|an|one|two|three) (\S+) counters? on each (.+)\.""")
        val EACH_GETS_UNTIL_EOT = Regex("""(?:Each (.+) gets|(.+) get) ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val GAIN_UNTIL_EOT = Regex("""(.+) gains? (.+) until end of turn\.""")
        val MAY_CAST_TARGET_FROM_GRAVEYARD = Regex("""You may cast target (.+?)( with mana value less than or equal to (.+)'s power)? from your graveyard( without paying its mana cost)?\.""")
        val PHASES_OUT = Regex("""(.+) phases out\.""")
        val PLAYERS_LOSE_LIFE = Regex("""(.+) loses? (\d+) life\.""")
        val EMBLEM = Regex("""You get an emblem with "(.+)"\.""")
        val BECOMES_COPY = Regex("""Target (.+) becomes a copy of another target (.+) until end of turn(?:, except it's an? (\w+) in addition to its other types)?\.""")
        // chunk 13
        val ADDITIONAL_EXILE_ANY_NUMBER_REDUCTION = Regex("""As an additional cost to cast this spell, you may exile any number of (.+) from your hand\. This spell costs ((?:\{[^}]+\})+) less to cast for each card exiled this way\.""")
        val BRACKETED = Regex("""(.*?)( ?)\[([^\]]+)\](.*)""")
        val CHOOSE_CARD_NAME = Regex("""Choose an? (?:(.+?) )?card name\.""")
        val CHOOSE_CARD_TYPE = Regex("""Choose ((?:\w+, )*\w+,? or \w+)\.""")
        val TAP_OR_UNTAP_ALL_OF_CHOSEN_TYPE =
            Regex("""Tap all untapped (\w+) of the chosen type target player controls, or untap all tapped \1 of that type that player controls\.""")
        val TARGET_REVEALS_HAND = Regex("""Target (player|opponent) reveals their hand( and discards all cards with that name)?\.""")
        val YOU_CHOOSE_FROM_IT_AND_GRAVEYARD = Regex("""You choose (an? .+?) from it, then choose (an? .+?) from their graveyard\.""")
        val YOU_CHOOSE_FROM_IT = Regex("""(?:You c|C)hoose (an? .+?) from it( with .+)?\.""")
        val TARGET_LOSES_YOU_GAIN = Regex("""Target (player|opponent) loses (\d+) life and you gain (\d+) life\.""")
        val PUT_INTO_LIBRARY_FROM_TOP = Regex("""Put target (.+) into its owner's library (second|third|fourth) from the top\.""")
        val FROM_TOP = mapOf("second" to 1, "third" to 2, "fourth" to 3)
        val ITS_CONTROLLER_GAINS_N = Regex("""Its controller gains (\d+) life\.""")
        val EACH_OPPONENT_SACRIFICES_GREATEST = Regex("""Each opponent sacrifices an? (.+?) with the greatest mana value among (.+) they control\.""")
        val DESTROY_UP_TO = Regex("""Destroy up to (\w+) target (.+)\.""")
        val CREATE_TOKENS_WITH_QUOTED = Regex("""Create (twice X|a|an|one|two|three|four|five) (\d+/\d+ .+? creature) tokens? with "(.+)"\.""")
        // chunk 14
        val DIVIDED_AMONG_ANY_NUMBER = Regex("""(.+?) deals X damage divided as you choose among any number of targets\.""")
        val THAT_PLAYER_OR_CONTROLLER_MAY_PAY = Regex("""That player or that permanent's controller may pay (\{.+\})\.""")
        val COPY_TARGET = Regex("""Copy target (.+)\.""")
        val COPY_NEW_TARGETS = Regex("""You may choose new targets for the cop(?:y|ies)\.""")
        val IF_CAST_FROM_GRAVEYARD = Regex("""If this spell was cast from a graveyard, (.+)""")
        val UNTIL_END_OF_TURN_WHENEVER = Regex("""Until end of turn, whenever (.+?), (.+)""")
        val WHENEVER_THIS_TURN = Regex("""Whenever (.+?) this turn, (.+)""")
        val RETURN_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (another )?target (.+?) from your graveyard to the battlefield( tapped)?\.""")
        val GAINS_FLASHBACK_OF_ITS_MANA_COST = Regex("""Each (.+?) card in your graveyard gains flashback until end of turn\. The flashback cost is equal to its mana cost\.""")
        val CAST_UP_TO_ONE_EACH_FROM_GRAVEYARD = Regex("""You may cast (.+?) from your graveyard each with mana value (X|\d+) or less without paying their mana costs\.""")
        val UP_TO_ONE_TARGET_CARD = Regex("""up to one target (\w+) card""")
        val IF_X_AT_LEAST_COPY_TWICE = Regex("""If X is (\d+) or more, copy each of those spells twice\.""")
        val DISCARD_X_CARDS = "Discard X cards"
        // chunk 15
        val RETURN_SELF_FROM_GRAVEYARD_TO_HAND = Regex("""Return (.+) from your graveyard to your hand\.""")
        val RETURN_EACH_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return each (.+?) from your graveyard to the battlefield\.""")
        val CHOOSE_UP_TO_TARGET_PUT_THERE_THIS_TURN = Regex("""Choose up to (\w+) target (.+?) in your graveyard that were put there from the battlefield this turn\.""")
        val RETURN_THEM_TO_BATTLEFIELD = Regex("""Return them to the battlefield( tapped)?\.""")
        val RETURN_ALL_PUT_THERE_THIS_TURN = Regex("""(Each player returns|Return) to the battlefield all (.+?) in (their|your) graveyard that were put there from the battlefield this turn\.""")
        val PUT_THERE_FROM_BATTLEFIELD_THIS_TURN = ObjectFilter.ThisTurn(EventPattern.ZoneChange(from = Zone.Battlefield, to = Zone.Graveyard, objects = ObjectFilter.Any))
        val LOOK_PUT_UP_TO_ONTO_BATTLEFIELD_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put up to (\w+) (.+?) from among them onto the battlefield\. Put the rest on the bottom of your library in any order\.""")
        val THAT_PLAYER_ADDS_ADDITIONAL = Regex("""That player adds an additional (\{.\})\.""")
        // chunk 20
        val ATTACHED_GETS = Regex("""(?:Equipped|Enchanted) creature gets ([+-]\d+)/([+-]\d+)(?: for each (.+))?\.""")
        val ATTACH_SELF_TO_TARGET = Regex("""Attach (.+) to target (.+)\.""")
        val SACRIFICE_THAT = Regex("""Sacrifice that (\w+)\.""")
        val RETURN_SELF_FROM_GRAVEYARD_TO_BATTLEFIELD = Regex("""Return (.+) from your graveyard to the battlefield( tapped)?\.""")
        val TARGET_PLAYER_MILLS_SACRIFICED = Regex("""Target player mills cards equal to the sacrificed \w+'s (power|toughness)\.""")
        val PUT_UP_TO_TARGET_ON_BOTTOM = Regex("""Put up to (\w+) target (.+) from your graveyard on the bottom of your library\.""")
        val LOOK_TOP_OF_TARGET_LIBRARY = Regex("""Look at the top (card|(\w+) cards) of target player's library\.""")
        val AT_NEXT_UPKEEP = Regex("""(.+) at the beginning of the next turn's upkeep\.""")
        val PUT_SELF_ON_TOP = Regex("""Put (.+) on top of its owner's library\.""")
        val IF_INSTEAD = Regex("""If (.+?), (.+) instead\.""")
        // chunk 21
        val MAY_PLAY_FROM_GRAVEYARD = Regex("""You may (play|cast) (.+) from your graveyard\.""")
        val SPEND_AS_ANY_COLOR_TO_ACTIVATE = Regex("""You may spend mana as though it were mana of any color to activate abilities of (.+)\.""")
        val HAVE_ABILITIES_OF_EXILED = Regex("""(.+) have all activated abilities of all (.+) cards exiled with (.+)\.""")
        val ENTERS_WITH_COUNTERS = Regex("""(.+) enters( tapped)? with (\w+) (\S+) counters? on it\.""")
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
        val THAT_CONTROLLER_CREATES = Regex("""That \w+'s controller creates (.+ tokens?)\.""")
        val GAIN_LIFE_FOR_EACH_SPELL_CAST = Regex("""(.+) gains? (\d+) life for each spell you've cast this turn\.""")
        val OPPONENT_GAINS_CONTROL = Regex("""An opponent gains control of (.+)\.""")
        val YOU_MAY = Regex("""You may (.+)\.""")
        // chunk 30
        val WHERE_X = Regex(""", where X is ([^.]+)""")
        val HAS_KEYWORDS_AS_LONG_AS = Regex("""(.+) has ([a-z ,]+) as long as (.+)\.""")
        val ESCAPES_WITH = Regex("""(.+) escapes with (a|an|one|two|three) (\S+) counters? on it\.""")
        val EXILE_FROM_GRAVEYARD_COST = Regex("""Exile (.+) from your graveyard""")
        val MILL = Regex("""Mill (\w+) cards?\.""")
        val LOSE_LIFE_AND_YOU_GAIN = Regex("""(Each opponent|Each player) loses (\d+) life(?: and you gain (\d+) life)?\.""")
        val PLAYERS_DISCARD = Regex("""(Each opponent|Each player) discards (\w+) cards?\.""")
        // chunk 31
        val YOU_MAY_WHEN_YOU_DO = Regex("""You may (.+?)\. When you do, (.+)""")
        val PUT_IT = Regex("""Put (?:it|that card) (onto the battlefield|into your hand)\.""")
        val LOOK_OR_REVEAL_TOP = Regex("""(Look at|Reveal) the top (\w+) cards of your library\.""")
        val MAY_FROM_AMONG_INTO_HAND = Regex("""You may (reveal|put) an? (.+?) from among them (?:and put it )?into your hand\.""")
        val PUT_THE_REST = Regex("""Put the rest (on the bottom of your library in a random order|into your graveyard)\.""")
        val LOOK_PUT_UP_TO_ONE_ON_TOP = Regex("""Look at the top (\w+) cards of your library\. Put up to one of them on top of your library and the rest on the bottom of your library in a random order\.""")
        val WIN_IF_X_AT_LEAST = Regex("""If X is greater than or equal to (.+), you win the game\.""")
        val RETURN_ALL_OR_SELF_TO_BATTLEFIELD = Regex("""Return (target |all )?(.+) from your graveyard to the battlefield( tapped)?\.""")
        val GAINS_FLASHBACK = Regex("""Target (.+) gains flashback until end of turn\. The flashback cost is equal to its mana cost\.""")
        val CHOOSE_TARGET = Regex("""Choose target (.+)\.""")
        val MAY_PUT_FROM_HAND = Regex("""You may put an? (.+) from your hand onto the battlefield( tapped)?\.""")
        val GRAVEYARD_ON_BOTTOM = Regex("""(Up to one target|Target) player puts all the cards from their graveyard on the bottom of their library in a random order\.""")
        // chunk 32
        val HAVE_KEYWORDS = Regex("""(.+) have ([a-z ,]+)\.""")
        val MAY_PAY_TO_CAST = Regex("""As long as (.+), you may pay (\{.+\}) to cast this spell\.""")
        val RETURN_YOURS_TO_HAND = Regex("""Return an? (.+ you control) to its owner's hand\.""")
        val TARGET_DEALS_DAMAGE_TO_ITSELF = Regex("""Target (.+) deals (\d+) damage to itself\.""")
        val UNTAP_ANOTHER_TARGET = Regex("""Untap (another )?target (.+)\.""")
        val GAIN_AND_GET = Regex("""(.+?) gains? (.+?) and gets? ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val CREATE_COPY_OF_TARGET = Regex("""Create a token that's a copy of (another )?target (.+?)(?:, except (.+))?\.""")
        // chunk 33
        val EACH_SACRIFICES = Regex("""Each (player|opponent) sacrifices an? (.+?)(?: of their choice)?\.""")
        val TARGET_REVEALS_HAND_YOU_CHOOSE = Regex("""Target opponent reveals their hand and you choose an? (.+) from it\.""")
        val MAY_EXILE_FROM_IT_UNTIL_LEAVES = Regex("""You may exile an? (.+) from it until (.+) leaves the battlefield\.""")
        val EXILED_TO_HAND = Regex("""(?:Return|Put) the exiled card (?:to|into) its owner's hand\.""")
        val EXILED_OWNER_CREATES = Regex("""The exiled card's owner creates (.+ tokens?)\.""")
        val UP_TO_ONE_TARGET = Regex("""(Exile|Destroy) up to one (other )?target (.+)\.""")
        val DAMAGE_EQUAL_TO_POWER = Regex("""(.+) deals damage equal to its power to any target\.""")
        val OPPONENTS_CANT_CAST = Regex("""Your opponents can't cast (.+) this turn\.""")
        val YOU_AND_TARGET_OPPONENT_DRAW = Regex("""You and target opponent each draw (\w+) cards?\.""")
        val X_CANT_BE = Regex("""X can't be (\d+)\.""")
        val GAIN_CONTROL_OF_TARGET = Regex("""Gain control of target (.+)\.""")
        val IF_THAT_IS_ATTACH_IT = Regex("""If that (\w+) is an? (.+), attach it to (.+)\.""")
        val COPY_NEXT_SPELL = Regex("""Copy the next (.+) you cast this turn when you cast it\.""")
        val COPY_TWICE_IF_KICKED = Regex("""If (.+) was kicked, copy that spell twice instead\.""")
        // chunk 40
        val ACTIVATE_ONLY_ONCE_EACH_TURN = Regex("""Activate only once each turn\.""")
        val X_IN_COST = Regex("""\bX\b""")
        val ONE_OR_MORE_ENTER = Regex("""one or more (.+) enter""")
        val GROUP_CANT_BE_COUNTERED = Regex("""(.+ spells(?: .+)?) can't be countered\.""")
        val TOKENS_PLUS_THAT_MANY = Regex("""If one or more tokens would be created under your control, those tokens plus that many (\d+/\d+ .+? creature) tokens are created instead\.""")
        val KEYWORD_COUNTERS_PLUS = Regex("""If an? (\w+) triggered ability would put one or more (\S+) counters on (an? .+?), that many plus (\w+) (\S+) counters are put on it instead\.""")
        val UNTIL_EOT_BASE_PT = Regex("""Until end of turn, (.+?) (?:has|have) base power and toughness (\d+|X)/(\d+|X)(?: and (.+))?\.""")
        val BECOMES_ADDITIONAL_TYPE = Regex("""becomes an? (\w+) in addition to its other creature types""")
        val SELF_GETS_UNTIL_EOT = Regex("""(.+) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
        val CANT_BE_BLOCKED_EXCEPT_BY_THIS_TURN = Regex("""(.+) can't be blocked this turn except by creatures with (\w+)\.""")
        val TAP_OR_UNTAP = Regex("""Tap or untap (another )?target (.+)\.""")
        val GAINS_PROTECTION_OF_CHOICE = Regex("""(Another target|Target) (.+) gains protection from (colorless or from )?the color of your choice until end of turn\.""")
        val CHANGE_A_TARGET_TO = Regex("""Change a target of target (.+) to (.+)\.""")
        // chunk 41
        val COUNTER_ON = Regex("""(\S+) counter on (.+)""")
        val CHOOSE_TARGET_AND_CARD_IN_THAT_GRAVEYARD = Regex("""Choose target (.+?) a player controls and target (.+?) in that player's graveyard\.""")
        val BOTH_LEGAL_SACRIFICE_AND_RETURN = Regex("""If both targets are still legal as this ability resolves, that player simultaneously sacrifices the \w+ and returns the [\w ]+ to the battlefield\.""")
        val IF_IT_WAS = Regex("""If it was an? (.+?), (.+)""")
        val RETURN_CHOSEN_TO_HAND = Regex("""Return an? (.+) to its owner's hand\.""")
        // chunk 42
        val SELF_GETS_AS_LONG_AS = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) as long as (.+)\.""")
        val SELF_GETS_FOR_EACH = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) for each (.+)\.""")
        val TAPPED_TOKENS = Regex("""^(\w+) tapped """)
        val TARGET_REVEALS_TOP = Regex("""Target (opponent|player) reveals the top (\w+) cards? of their library\.""")
        val YOU_MAY_PUT_FROM_AMONG_THEM = Regex("""You may put an? (.+?) from among them onto the battlefield( under your control)?\.""")
        val TARGET_PLAYERS_LOSE_AND_SACRIFICE = Regex("""Any number of target players each lose (\d+) life and sacrifice an? (.+?) of their choice\.""")
        val CREATE_AND = Regex("""Create (.+? tokens?) and (.+)\.""")
        val YOU_AND = Regex("""You (?!may )(.+?) and (.+)\.""")
        // chunk 35
        val POWER_IS_EQUAL_TO = Regex("""(.+)'s power is equal to (.+)\.""")
        val DEVOTION_TO = Regex("""your devotion to (\w+(?: and \w+)?)""")
        val CANT_BLOCK = Regex("""(.+) can't block\.""")
        val CREATE_FOR_EACH_COUNTER_ON_SELF = Regex("""Create (an? .+ token(?: with .+?)?) for each (\S+) counter on (.+)\.""")
        val DRAW_FOR_EACH_COUNTER_ON = Regex("""Draw a card for each (\S+) counter on (.+)\.""")
        val CREATE_X_WHERE_SELF_POWER = Regex("""Create X (tapped )?(.+ tokens), where X is (.+)'s power\.""")
        val DEALS_DAMAGE_EQUAL_TO_ITS_POWER = Regex("""(.+?) deals damage equal to its power to (any target|each opponent)\.""")
        val RETURN_TO_HAND_WITH_LESSER_MANA_VALUE = Regex("""Return to your hand target (.+?) in your graveyard with lesser mana value\.""")
        val EACH_PLAYER_MAY_SEARCH_UP_TO = Regex("""Each player may search their library for up to (\w+) (.+?), put them onto the battlefield, then shuffle\.""")
        const val WITH_MANA_VALUE_X = " with mana value X"
        val THE_CREATURE_HAD = Regex("""the creature had (power|toughness) (\d+) or greater""")
        val OF_THOSE_TOKENS = Regex("""create (\w+) of those tokens""")
        // chunk 36
        val YOU_DISCARD_ONE_OR_MORE = Regex("""you discard one or more (.+)""")
        val ATTACKS_EACH_COMBAT = Regex("""(.+) attacks each combat if able\.""")
        val CANT_BE_BLOCKED_EXCEPT_BY = Regex("""(.+) can't be blocked except by (.+)\.""")
        val IN_YOUR_GRAVEYARD_HAVE = Regex("""(.+) in your graveyard have (.+)\.""")
        val DURING_YOUR_TURN_STATIC = Regex("""During your turn, (.+)""")
        val PAY_MANA = Regex("""Pay (\{.+\})""")
        val EXILE_TOP_CARD = Regex("""Exile the top card of your library( face down)?\.""")
        const val EXILE_ONE_OF_THEM_FROM_GRAVEYARD = "Exile one of them from your graveyard"
        val MAY_PUT_FROM_AMONG_THEM_INTO_HAND = Regex("""You may put an? (.+?) from among them into your hand\.""")
        val IF_IT_ENTERED_UNDER_YOUR_CONTROL = Regex("""If it entered under your control, (.+)""")
        val PUT_ALL_EXILED_WITH_INTO_HANDS = Regex("""Put all cards exiled with (.+) into their owners' hands\.""")
        val SACRIFICE_A = Regex("""Sacrifice (another|an?) (.+)\.""")
        val EXILE_UP_TO_TARGET = Regex("""Exile up to (\w+) (other )?target (.+)\.""")
        val AT_BEGINNING_OF_NEXT_END_STEP = Regex("""At the beginning of the next end step, (.+)""")
        val RETURN_IT_UNDER_OWNERS_CONTROL = Regex("""Return (?:that card|it) to the battlefield under its owner's control\.""")
        val LOSE_LIFE_EQUAL_TO_THAT_CARDS_MANA_VALUE = Regex("""(.+) loses? life equal to that card's mana value\.""")
        val UNLESS_CONDITION = Regex("""(.+) unless (.+)\.""")
        // chunk 45
        val AS_LONG_AS_FIRST = Regex("""As long as (.+?), (.+)""")
        val AS_LONG_AS_LAST = Regex("""(.+) as long as (.+)\.""")
        val SUBJECT_PREDICATES = Regex("""(.+?) ((?:gets?|has|have|can't|attacks) .+)\.""")
        val PREDICATE_SPLIT = Regex(""",? and (?=(?:gets?|has|have|can't|attacks) )|, (?=(?:gets?|has|have|can't|attacks) )""")
        val GETS_PT = Regex("""gets? ([+-]\d+)/([+-]\d+)(?: for each (.+))?""")
        val HAS_KEYWORDS = Regex("""(?:has|have) (.+)""")
        val MAY_PLAY_FROM_TOP = Regex("""[Yy]ou may (play|cast) (.+?) from the top of your library\.""")
        val ONCE_EACH_TURN_CAST_FROM_GRAVEYARD = Regex("""Once during each of your turns, you may cast an? (.+) from your graveyard\.""")
        val CAST_FROM_GRAVEYARD_USING = Regex("""You may cast (.+) from your graveyard using its (\w+) ability\.""")
        val COLLECT_EVIDENCE = Regex("""Collect evidence (\d+)""")
        // chunk 46
        const val CHOSEN_TYPE = "type"
        val CANT_CAST_ADDITIONAL = Regex("""Each player who has cast an? (.+?) spell this turn can't cast additional (.+?) spells\.""")
        val EACH_OTHER_ENTERS_WITH = Regex("""Each other (.+?)( of the chosen type)? enters with (?:an additional (\S+) counter|a number of additional (\S+) counters) on it(?: equal to (.+))?\.""")
        val SELF_PROPERTY = Regex("""(.+)'s (power|toughness)""")
        val AS_ENTERS_CHOOSE_TYPE = Regex("""As (.+) enters, choose a creature type\.""")
        val IS_THE_CHOSEN_TYPE = Regex("""(.+) is the chosen type in addition to its other types\.""")
        val PT_EACH_EQUAL_TO_NUMBER = Regex("""(.+)'s power and toughness are each equal to the number of (.+)\.""")
        // dsk chunk 03: "Enchantment spells you cast cost {1} less to cast."
        val SPELLS_COST = Regex("""(.+) spells( you cast)? cost ((?:\{[^}]+\})+) (more|less) to cast\.""")
        val PREVENT_ALL_COMBAT_DAMAGE_TO = Regex("""Prevent all combat damage that would be dealt to (.+)\.""")
        val BOLSTER = Regex("""Bolster (\d+)\.""")
        val UNTIL_YOUR_NEXT_TURN_CANT_CAST = Regex("""Until your next turn, target player(?: who was dealt combat damage by (.+?) this turn)? can't cast (.+?) spells\.""")
        // chunk 47
        val NTH_SPELL_EACH_TURN = Regex("""(a player|an opponent) casts their (first|second|third) (?:(.+) )?spell each turn""")
        val CONNIVES = Regex("""(.+) connives\.""")
        val PUT_THAT_MANY_COUNTERS = Regex("""Put that many (\S+) counters on (.+)\.""")
        val YOU_LOSE_LIFE_EQUAL_TO_ITS = Regex("""You lose life equal to its (mana value|power)\.""")
        val YOU_MAY_PLAY_THAT_CARD = Regex("""You may play that card (this turn|until your next end step)\.""")
        val IF_YOU_WIN_THE_FLIP = Regex("""If you win the flip, (.+)""")
        val RETURN_THAT_TO_OWNERS_HAND = Regex("""Return that (?:spell|card|creature|permanent) to its owner's hand\.""")
        val COPY_THAT_SPELL = Regex("""Copy that spell(?:, and you may choose new targets for the copy)?\.""")
        val UNLESS_THAT_PLAYER_PAYS = Regex("""(.+?) unless that player pays (\{[^}]+\}(?:\{[^}]+\})*)(?:, where X is (.+?))?\.""")
        // chunk 50
        val NTH_SPELL_IF = Regex("""if it's the (\w+) (.+?) spell you cast this turn, (.+)""")
        val NTH_CARD_DRAWN = Regex("""you draw your (\w+) card (?:in a|each) turn""")
        val SACRIFICE_SELF = Regex("""Sacrifice (.+)\.""")
        val UNLESS_YOU = Regex("""(.+) unless you (.+)\.""")
        val MAY_CAST_SELF_FROM_GRAVEYARD_BY = Regex("""You may cast (.+) from your graveyard by (.+) rather than paying its mana cost\.""")
        val GERUNDS = mapOf("paying" to "", "sacrificing" to "Sacrifice", "discarding" to "Discard", "exiling" to "Exile")
        // chunk 51
        val ANY_PLAYER_MAY_ACTIVATE = Regex("""Any player may activate this ability\.""")
        val TARGET_GAINS_UNTIL_EOT = Regex("""Target (.+) gains (.+) until end of turn\.""")
        // chunk 52
        val HAS_ACTIVATED_ABILITIES_OF = Regex("""(.+) has all activated abilities of (.+?)( except for loyalty abilities)?\.( You may spend mana as though it were mana of any color to activate those abilities\.)?""")
        val COUNTERS_MINUS_ONE = Regex("""If one or more (\S+) counters would be put on (.+), that many (\S+) counters minus one are put on it instead\.""")
        val AS_ENTERS_CHOOSE = Regex("""As (.+) enters, (you may )?choose (.+)\.""")
        val CARD_NAME = Regex("""an? (.+) card name""")
        const val CHOSEN_NAME = "name"
        const val CHOSEN = "chosen"
        val CANT_BE_ACTIVATED = Regex("""Activated abilities of (.+) can't be activated\.""")
        val EVERY_ZONE = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)
        val PUT_FOUND_IN_HAND_REST_ON_BOTTOM = Regex("""Put that card into your hand and the rest on the bottom of your library in (a random|any) order\.""")
        val REVEAL_UNTIL = Regex("""Reveal cards from the top of your library until you reveal (.+)\.""")
        const val FOUND = "found"
        const val REST = "rest"
        val FOR_THE_FIRST_TIME_EACH_TURN = Regex("""(.+) for the first time each turn""")
        // chunk 56
        val CHAPTERS = Regex("""([IVX]+(?:, [IVX]+)*) — (.+)""")
        val ROMAN = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7)
        val CAN_BLOCK_ONLY = Regex("""(.+) can block only (.+)\.""")
        val LABEL_TWICE_DURING_YOUR_TURNS = Regex("""(.+) can (\w+) twice during each of your turns rather than once\.""")
        val YOU_CREATE_A = Regex("""you create an? (.+) token""")
        val EXILE_TOP_N = Regex("""Exile the top (\w+) cards of your library\.""")
        val TARGET_OPPONENT_MILLS = Regex("""Target opponent mills (\w+) cards?\.""")
        val UP_TO_ONE_TARGET_GETS = Regex("""Up to one target (.+) gets ([+-]\d+)/([+-]\d+) until (end of turn|your next turn)\.""")
        val TARGET_GETS_FOR_EACH_UNTIL_EOT = Regex("""Target (.+) gets ([+-]\d+)/([+-]\d+) until end of turn for each (.+)\.""")
        val MAY_CAST_TARGET_FROM_GRAVEYARD_THIS_TURN = Regex("""You may cast target (.+) from your graveyard this turn\.""")
        val EXILE_SELF_RETURN_TRANSFORMED = Regex("""Exile (.+), then return (?:it|him|her) to the battlefield transformed under (?:his owner's|her owner's|its owner's|(your)) control\.""")
        val TRANSFORM = Regex("""Transform (.+)\.""")
        val RETURN_ALL_TO_HANDS = Regex("""Return all (.+) to their owners' hands\.""")
        val BECOMES_CREATURE = Regex("""(Up to one target|Target) (.+?) becomes an? (\d+)/(\d+) (\w+) (\w+) creature(?: with (.+?))? in addition to its other types\.""")
        val BECOME_COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
        val IF_CONDITION = Regex("""If (.+?), (.+)\.""")
        val IT_HAS_NO_COUNTERS = Regex("""it has no (\S+) counters on it""")
    }
}

/** chunk 30: [x] in place of every announced X: a number the text defines (CR 107.3c). Walks the JSON form, as
 * DefinitionChecks does, to reach every nested value. */
private fun Instructions.withX(x: Value): Instructions {
    val json = kotlinx.serialization.json.Json
    val announced = json.encodeToJsonElement(Value.serializer(), Value.Announced(name = "X"))
    val replacement = json.encodeToJsonElement(Value.serializer(), x)
    fun replace(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when {
        e == announced -> replacement
        e is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.mapValues { replace(it.value) })
        e is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { replace(it) })
        else -> e
    }
    return json.decodeFromJsonElement(Instructions.serializer(), replace(json.encodeToJsonElement(Instructions.serializer(), this)))
}

/** "[steps] unless [payer] pays [cost]" (CR 118.12a): the payer may pay; if they didn't, [steps] happen (D52). The
 * outcome is named "paid", or "unless-paid" when [steps] already use that name. */
private fun unlessPays(payer: Players, cost: Cost, steps: List<Step>): List<Step> {
    val bind = if (kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Step.serializer()), steps)
            .contains("\"paid\"")) "unless-paid" else "paid"
    return listOf(Step.Pay(cost, payer = payer, bind = bind),
        Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did(bind)), then = steps))
}

internal fun String.removeArticle() = removePrefix("a ").removePrefix("an ")
