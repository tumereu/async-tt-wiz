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
    // neo chunk 02: "You may discard up to two cards. If you do, draw that many cards." (the number discarded, CR 608.2c)
    val MAY_DISCARD_UP_TO_IF_YOU_DO_DRAW_THAT_MANY = Regex("""You may discard up to (\w+) cards\. If you do, draw that many cards\.""")
    // neo chunk 02: "your opponents can't cast spells with the same mana value as that spell until your next turn."
    val OPPONENTS_CANT_CAST_SAME_MANA_VALUE_UNTIL_NEXT_TURN = Regex("""[Yy]our opponents can't cast (.+?) with the same mana value as that spell until your next turn\.""")
    // neo chunk 02: "Each player gains control of all permanents they own." (CR 613.1b)
    val EACH_PLAYER_GAINS_CONTROL_OF_ALL_THEY_OWN = Regex("""Each player gains control of all (.+?) they own\.""")
    // neo chunk 02: "defending player chooses a nonland card in your graveyard. Return that card to your hand. This creature gets +X/+0 until end of
    // turn, where X is the mana value of that card."
    val DEFENDING_CHOOSES_IN_YOUR_GRAVEYARD_RETURN_GETS_X = Regex("""Defending player chooses an? (.+?) in your graveyard\. Return that card to your hand\. (.+?) gets \+X/\+0 until end of turn(?:, where X is the mana value of that card)?\.""")
}

/** neo: nouns and conditions of Kamigawa: Neon Dynasty wordings, tried by [Filters] and [Conditions]. */
internal object NeoPhrases {
    // neo chunk 02: "creature dealt damage this turn by a source you controlled" (the creature was dealt it, CR 120.1; the source's controller then)
    private val DEALT_DAMAGE_THIS_TURN_BY = Regex("""(.+?) dealt damage this turn by an? (.+?) you controlled""")
    // neo chunk 02: "creature cards in defending player's graveyard" (in an attack trigger, CR 506.2)
    private val IN_DEFENDING_PLAYERS_GRAVEYARD = Regex("""(.+?) in defending player's graveyard""")
    // neo chunk 02: "it has dealt 10 or more damage to that player this turn" (this object's damage to the trigger's player, added up)
    private val IT_HAS_DEALT_DAMAGE_TO_THAT_PLAYER = Regex("""it has dealt (\w+) or more damage to that player this turn""")

    fun noun(phrase: String): ObjectFilter? {
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

    fun condition(text: String): Condition? {
        IT_HAS_DEALT_DAMAGE_TO_THAT_PLAYER.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: m.groupValues[1].toIntOrNull() ?: return null
            return Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Is(Players.EventPlayer))),
                atLeast = n, measure = Measure.Amount)
        }
        return null
    }
}
