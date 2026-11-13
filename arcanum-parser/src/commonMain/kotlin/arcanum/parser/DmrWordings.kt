package arcanum.parser

/** Wordings first met in Dominaria Remastered (DMR). */
internal object Dmr {
    // dmr chunk 01: Veiled Serpent
    val CANT_ATTACK_UNLESS_DEFENDER_CONTROLS = Regex("""(This \w+) can't attack unless defending player controls an? (.+)\.""")
    val BECOMES_PT_CREATURE_WITH_QUOTED = Regex("""(.+?) becomes an? (\d+)/(\d+) ([A-Z]\w*(?: [A-Z]\w*)*) creature with "(.+)"\.?""")
    val SELF_IS_A = Regex("""(this \w+) is an? (\w+)""")
    // dmr chunk 02: Lightning Reflexes (a Mirage-era flash wording, the same on every card that has it)
    const val FLASH_SACRIFICE_AT_NEXT_CLEANUP = "You may cast this spell as though it had flash. If you cast it any time a sorcery couldn't have been " +
        "cast, the controller of the permanent it becomes sacrifices it at the beginning of the next cleanup step."
    // dmr chunk 03: Divine Sacrament ("get an additional +1/+1": one more modification, CR 613.4c)
    val GET_AN_ADDITIONAL = Regex("""\b(gets?) an additional """)
    val ONCE_EACH_TURN_AND_ONLY_IF = Regex("""Activate only once each turn and only if (.+?)\.""")
    // dmr chunk 03: Battlefield Scrounger
    val PUT_FROM_YOUR_GRAVEYARD_ON_BOTTOM_COST = Regex("""Put (\w+) cards? from your graveyard on the bottom of your library""")
    // dmr chunk 01: Wild Dogs
    val PLAYER_WITH_MOST_LIFE_GAINS_CONTROL = Regex("""[Tt]he player with the most life gains control of (.+)\.""")
    // dmr chunk 04: Orim's Thunder
    val THAT_PERMANENTS_PROPERTY = Regex("""that permanent's (mana value|power|toughness)""")
    // dmr chunk 04: Spiritmonger
    val BECOMES_COLOR_OF_CHOICE = Regex("""(.+?) becomes the color of your choice until end of turn\.""")
    // dmr chunk 04: Dragon Whelp
    val ACTIVATED_N_OR_MORE_TIMES = Regex("""this ability has been activated (\w+) or more times this turn""")
    // dmr chunk 04: Gemstone Mine
    val IF_NO_COUNTERS_SACRIFICE_IT = Regex("""If there are no (\S+) counters on (.+?), sacrifice it\.""")
    // dmr chunk 05: Sulfuric Vortex (and fin's "you gain twice that much life")
    val WOULD_GAIN_LIFE_INSTEAD = Regex("""If (you|a player) would gain life, (?:you gain|that player gains) (twice that much life|no life) instead\.""")
    // dmr chunk 05: Overmaster (and fra's "The next spell you cast this turn can't be countered.")
    val NEXT_SPELL_CANT_BE_COUNTERED = Regex("""The next (?:(.+?) )?spell you cast this turn can't be countered\.""")
    // dmr chunk 05: Remedy
    val PREVENT_NEXT_DIVIDED = Regex("""Prevent the next (\d+) damage that would be dealt this turn to any number of targets, divided as you choose\.""")
    // dmr chunk 05: Forgotten Ancient
    val MOVE_ANY_NUMBER_ONTO_OTHERS = Regex("""Move any number of (\S+) counters from (.+?) onto other (\w+?)s\.""")
    // dmr chunk 05: Arcades Sabboth
    val EACH_GETS_AS_LONG_AS_NOT_ATTACKING = Regex("""Each (.+?) gets \+?(-?\d+)/\+?(-?\d+) as long as it's not attacking\.""")
    // dmr chunk 05: Valduk, Keeper of the Flame
    val FOR_EACH_ATTACHED_CREATE = Regex("""For each (Aura|Equipment) and (Aura|Equipment) attached to (.+?), create (an? .+)""")
    // dmr chunk 05: Spinal Embrace
    val SACRIFICE_IT_IF_YOU_DO = Regex("""(Sacrifice (?:it|that creature))\. If you do, (.+)""")
    val AND_GAIN_CONTROL_OF_IT = Regex("""(.+?) and gain control of it\.""")
    fun property(word: String): arcanum.api.NumericProperty = when (word) {
        "mana value" -> arcanum.api.NumericProperty.ManaValue
        "power" -> arcanum.api.NumericProperty.Power
        else -> arcanum.api.NumericProperty.Toughness
    }

    /** "During combat": any step of the combat phase (CR 506.1). */
    val duringCombat: arcanum.api.Condition = arcanum.api.Condition.Or(listOf(arcanum.api.TurnStep.BeginningOfCombat, arcanum.api.TurnStep.DeclareAttackers,
        arcanum.api.TurnStep.DeclareBlockers, arcanum.api.TurnStep.CombatDamage, arcanum.api.TurnStep.EndOfCombat).map { arcanum.api.Condition.StepIs(it) })

    /** "The player with the most life": each player whose life total equals the greatest life total (CR 119.1). */
    val playersWithMostLife: arcanum.api.Players = arcanum.api.Players.Each(arcanum.api.PlayerFilter.Where(arcanum.api.Condition.Compare(
        arcanum.api.Value.LifeOf(arcanum.api.Players.Tested), arcanum.api.Comparison.Equal,
        arcanum.api.Value.GreatestFor(arcanum.api.Players.Each(arcanum.api.PlayerFilter.Any), arcanum.api.Value.LifeOf(arcanum.api.Players.Tested)))))
}
