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

    /** "The player with the most life": each player whose life total equals the greatest life total (CR 119.1). */
    val playersWithMostLife: arcanum.api.Players = arcanum.api.Players.Each(arcanum.api.PlayerFilter.Where(arcanum.api.Condition.Compare(
        arcanum.api.Value.LifeOf(arcanum.api.Players.Tested), arcanum.api.Comparison.Equal,
        arcanum.api.Value.GreatestFor(arcanum.api.Players.Each(arcanum.api.PlayerFilter.Any), arcanum.api.Value.LifeOf(arcanum.api.Players.Tested)))))
}
