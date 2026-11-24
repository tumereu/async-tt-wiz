package arcanum.parser

import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Zone

/** Regular expressions of Kamigawa: Neon Dynasty (neo) wordings read in [Sentences]. */
internal object Neo {
    // neo chunk 04: "Equipped creature has base power and toughness 5/5, has menace, and is a black Demon in addition to its other colors and types."
    val IS_COLOR_SUBTYPES_IN_ADDITION_PREDICATE = Regex("""is an? (white|blue|black|red|green) ((?:[A-Z]\w+ )*[A-Z]\w+) in addition to its other colors and types""")
    // neo chunk 03: "If you cast a spell this way, pay life equal to its mana value rather than paying its mana cost." (after a permission to play cards)
    val PAY_LIFE_RATHER_THAN_MANA_COST = Regex("""If you cast a spell this way, pay life equal to its mana value rather than pay(?:ing)? its mana cost\.""")
    // neo chunk 02: "You may discard up to two cards. If you do, draw that many cards." (the number discarded, CR 608.2c)
    val MAY_DISCARD_UP_TO_IF_YOU_DO_DRAW_THAT_MANY = Regex("""You may discard up to (\w+) cards\. If you do, draw that many cards\.""")
    // neo chunk 02: "your opponents can't cast spells with the same mana value as that spell until your next turn."
    val OPPONENTS_CANT_CAST_SAME_MANA_VALUE_UNTIL_NEXT_TURN = Regex("""[Yy]our opponents can't cast (.+?) with the same mana value as that spell until your next turn\.""")
    // neo chunk 02: "Each player gains control of all permanents they own." (CR 613.1b)
    val EACH_PLAYER_GAINS_CONTROL_OF_ALL_THEY_OWN = Regex("""Each player gains control of all (.+?) they own\.""")
    // neo chunk 02: "defending player chooses a nonland card in your graveyard. Return that card to your hand. This creature gets +X/+0 until end of
    // turn, where X is the mana value of that card."
    val DEFENDING_CHOOSES_IN_YOUR_GRAVEYARD_RETURN_GETS_X = Regex("""Defending player chooses an? (.+?) in your graveyard\. Return that card to your hand\. (.+?) gets \+X/\+0 until end of turn(?:, where X is the mana value of that card)?\.""")
    // neo chunk 05: "Create a Treasure token and a 1/1 colorless Pilot creature token with "…"" (two token kinds, the last with a quoted ability)
    // neo chunk 05: Mechtitan Core
    val EXILE_SELF_AND_OTHERS_YOU_CONTROL = Regex("""Exile (.+?) and (\w+) other (.+?) you control""")
    val CREATE_NAMED_LEGENDARY_ALL_COLORS = Regex("""Create ([A-Z][\w' -]*?), a legendary (\d+)/(\d+) ([A-Z][A-Za-z ]+?) creature token(?: with (.+?))? that's all colors\.""")
    val CREATE_WHEN_THAT_TOKEN_LEAVES = Regex("""(Create [^.]+? token[^.]*?\.) When that token leaves the battlefield, (.+)""")
    val RETURN_ALL_EXILED_WITH_EXCEPT_TAPPED_UNDER_OWNERS = Regex("""Return all cards exiled with (.+?) except (.+?) to the battlefield tapped under their owners' control\.""")
    val UNTIL_EOT_BECOMES_COPY_OF_TARGET_EXCEPT = Regex("""[Uu]ntil end of turn, (.+?) becomes a copy of target (.+?), except (.+)\.""")
    val CREATE_TOKEN_AND_TOKEN = Regex("""Create (an? [^"]+? tokens?) and (an? .+ tokens? with ".+"\.?)""")
    // neo chunk 06: "Enchanted land is a 3/3 red Spirit creature with haste. It's still a land." (for as long as it's attached)
    val ATTACHED_IS_CREATURE = Regex("""((?:Enchanted|Equipped) (?:creature|land|permanent|artifact)) is (an? [^.]+? creature(?: with [^.]+)?)\.(?: It's still an? [a-z]+\.)?""")
    // neo chunk 06: "If enchanted land would be destroyed, instead sacrifice this Aura and that land gains indestructible until end of turn."
    val AND_THAT_NOUN = Regex(""" and that (?:creature|land|permanent|artifact) """)
    // neo chunk 07: "As long as a card exiled with Eater of Virtue has flying, equipped creature has flying. The same is true for first strike, …"
    val AS_LONG_AS_EXILED_WITH_HAS_SAME_FOR = Regex("""As long as an? (.+?) exiled with (.+?) has ([a-z ]+), (.+?) has \3\. The same is true for (.+)\.""")
    // neo chunk 07: "Return any number of target nonland permanents with total mana value 6 or less to their owners' hands."
    val TARGETS_WITH_TOTAL_AT_MOST = Regex("""(.+?\btarget [^.]+?) with total (mana value|power) (\d+) or less( [^.]+)?\.""")
    // neo chunk 07: "If it has mana value less than the sacrificed permanent's mana value, scry 2."
    val IT_HAS_NUMBER_THAN = Regex("""it has (mana value|power|toughness) (less|greater) than (.+)""")
    // neo chunk 07: "the sacrificed permanent's mana value"
    val SACRIFICED_NUMBER = Regex("""the sacrificed (?:creature|permanent|artifact)'s (mana value|power|toughness)""")
    // neo chunk 07: "When you exile a nonland card this way, Hidetsugu deals damage equal to the exiled card's mana value to any target."
    val WHEN_YOU_EXILE_THIS_WAY = Regex("""When you exile an? (.+?) this way, (.+)""")
    // neo chunk 07: "Equipped creature gets +X/+0 until end of turn, where X is …" (the X cut off before)
    val ATTACHED_GETS_UNTIL_EOT = Regex("""((?:Enchanted|Equipped) creature) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
    val IF_WOULD_BE_DESTROYED_INSTEAD = Regex("""If ((?:enchanted|equipped) (?:creature|land|permanent|artifact)) would be destroyed, instead (.+)\.""")
    // neo chunk 08
    val COSTS_LESS_IF_AND_LESS_IF = Regex("""This spell costs (\{\d+\}) less to cast if (.+?) and (\{\d+\}) less to cast if (.+)\.""")
    val ITS_A_SUBTYPES_WITH_BASE_PT = Regex("""It's an? ((?:[A-Z]\w+ )*[A-Z]\w+) with base power and toughness (\d+)/(\d+)(?: and (.+))?\.""")
    val AS_LONG_AS_ENTERED_THIS_TURN_LOYALTY_INSTANT = Regex("""As long as (.+?) entered this turn, you may activate (?:her|his|its|their) loyalty abilities any time you could cast an instant\.""")
    val AS_LONG_AS_HAS_QUOTED = Regex("""As long as (.+?), (it|.+?) has "(.+)"\.?""")
    val SACRIFICES_IF_THEY_CANT_REPEAT_FOR = Regex("""(Target opponent|Target player|Each opponent|Each player) sacrifices (an? \w+) of their choice\. If they can't, (.+?)\. Then repeat this process for (an? \w+(?:, an? \w+)*,? and an? \w+)\.""")
    val CAST_UP_TO_TOTAL_MV_FROM_ZONES_FREE = Regex("""You may cast up to (\w+) (.+?) spells with total mana value (\d+) or less from your (graveyard|hand)(?: and/or (graveyard|hand))? without paying their mana costs\.""")
    val SPELLS_COST_FOR_EACH_TARGET = Regex("""Spells (you|your opponents) cast cost (\{\d+\}) (less|more) to cast for each target\.""")
    val WITH_MANA_VALUE_AT_MOST_THAT = Regex("""(.+?) with mana value less than or equal to that (?:[A-Z]\w+|creature|artifact|enchantment|permanent|card)(?: and (with .+))?""")
    val NEXT_SPELL_COSTS_LESS = Regex("""[Tt]he next (?:(.+?) )?spell you cast this turn costs ((?:\{[^}]+\})+) less to cast\.""")
}

/** neo: nouns and conditions of Kamigawa: Neon Dynasty wordings, tried by [Filters] and [Conditions]. */
internal object NeoPhrases {
    // neo chunk 02: "creature dealt damage this turn by a source you controlled" (the creature was dealt it, CR 120.1; the source's controller then)
    private val DEALT_DAMAGE_THIS_TURN_BY = Regex("""(.+?) dealt damage this turn by an? (.+?) you controlled""")
    // neo chunk 02: "creature cards in defending player's graveyard" (in an attack trigger, CR 506.2)
    private val IN_DEFENDING_PLAYERS_GRAVEYARD = Regex("""(.+?) in defending player's graveyard""")
    // neo chunk 02: "it has dealt 10 or more damage to that player this turn" (this object's damage to the trigger's player, added up)
    private val IT_HAS_DEALT_DAMAGE_TO_THAT_PLAYER = Regex("""it has dealt (\w+) or more damage to that player this turn""")

    private val SUBTYPE_AND_SUBTYPE = Regex("""([A-Z][\w-]*) and ([A-Z][\w-]*) ((?:creatures|permanents)\b.*)""")
    private val SELF_IS_MODIFIED = Regex("""(.+?) is modified""")
    private val SELF_IS_ATTACHED_TO_A_CREATURE = Regex("""(.+?) is attached to a creature""")
    // neo chunk 06: "creature with the greatest power among creatures on the battlefield" (ties count, CR 208.1)
    private val WITH_THE_GREATEST_AMONG = Regex("""(.+?) with the greatest (power|toughness|mana value) among (.+)""")
    private val CONTROL_A_AND_A = Regex("""you control an? ([a-z]+) and an? ([a-z]+)""")
    private val WITHOUT_A_COUNTER = Regex("""(.+?) that (?:doesn't|don't) have an? (\S+) counters? on (?:it|them)""")

    fun noun(phrase: String): ObjectFilter? {
        // neo chunk 03: "Ninja and Rogue creatures you control": creatures of either subtype, as "Ninja or Rogue creatures" (CR 205.3)
        SUBTYPE_AND_SUBTYPE.matchEntire(phrase)?.takeIf { it.groupValues[1] in RulesWords.subtypes && it.groupValues[2] in RulesWords.subtypes }
            ?.let { m -> return Filters.noun("${m.groupValues[1]} or ${m.groupValues[2]} ${m.groupValues[3]}") }
        // neo chunk 03: "creature you control that doesn't have a +1/+1 counter on it" (CR 122.1)
        WITHOUT_A_COUNTER.matchEntire(phrase)?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.HasCounter(kind, atLeast = 1)))
        }
        // (only "among ... on the battlefield": "among creatures they control" depends on the sentence's player, and a sentence rule that cuts
        // "they control" off reads the rest itself)
        WITH_THE_GREATEST_AMONG.matchEntire(phrase)?.takeIf { it.groupValues[3].endsWith(" on the battlefield") }?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val among = Filters.objects(m.groupValues[3]) ?: return null
            fun greatest(p: arcanum.api.NumericProperty) = arcanum.api.Value.Of(p, among, arcanum.api.Combine.Greatest)
            val at = arcanum.api.Comparison.AtLeast
            return Filters.all(noun, when (m.groupValues[2]) {
                "power" -> ObjectFilter.Power(at, greatest(arcanum.api.NumericProperty.Power))
                "toughness" -> ObjectFilter.Toughness(at, greatest(arcanum.api.NumericProperty.Toughness))
                else -> ObjectFilter.ManaValue(at, greatest(arcanum.api.NumericProperty.ManaValue))
            })
        }
        DEALT_DAMAGE_THIS_TURN_BY.matchEntire(phrase)?.let { m ->
            val dealtTo = Filters.noun(m.groupValues[1]) ?: return null
            val by = if (m.groupValues[2] == "source") ObjectFilter.ControlledBy(PlayerFilter.You) else Filters.noun("${m.groupValues[2]} you control") ?: return null
            return Filters.all(dealtTo, ObjectFilter.ThisTurn(EventPattern.Damage(by, RecipientFilter()), role = EventRole.Object))
        }
        IN_DEFENDING_PLAYERS_GRAVEYARD.matchEntire(phrase)?.let { m ->
            val cards = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))
        }
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        // neo chunk 08: "you control an artifact and an enchantment" (one of each, both at once; an artifact enchantment is both, CR 205.2b)
        CONTROL_A_AND_A.matchEntire(text)?.let { m ->
            val first = Filters.noun(m.groupValues[1] + " you control") ?: return@let
            val second = Filters.noun(m.groupValues[2] + " you control") ?: return@let
            return Condition.And(listOf(Condition.Exists(first, atLeast = 1), Condition.Exists(second, atLeast = 1)))
        }
        // neo chunk 04: "The Reality Chip is attached to a creature" (an Equipment with reconfigure, CR 702.151b; false while unattached)
        SELF_IS_ATTACHED_TO_A_CREATURE.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.AttachedTo(of = Objects.Self), ObjectFilter.Type(arcanum.api.CardType.Creature))
        }
        // neo chunk 08: "this creature is modified" (it has an Equipment or an Aura its controller controls attached, or a counter, CR 700.9)
        SELF_IS_MODIFIED.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Modified)
        }
        IT_HAS_DEALT_DAMAGE_TO_THAT_PLAYER.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: m.groupValues[1].toIntOrNull() ?: return null
            return Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Is(Players.EventPlayer))),
                atLeast = n, measure = Measure.Amount)
        }
        return null
    }
}
