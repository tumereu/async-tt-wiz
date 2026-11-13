package arcanum.parser

/** Wordings first met in Dominaria Remastered (DMR). */
internal object Dmr {
    // dmr chunk 01: Veiled Serpent
    val CANT_ATTACK_UNLESS_DEFENDER_CONTROLS = Regex("""(This \w+) can't attack unless defending player controls an? (.+)\.""")
    val BECOMES_PT_CREATURE_WITH_QUOTED = Regex("""(.+?) becomes an? (\d+)/(\d+) ([A-Z]\w*(?: [A-Z]\w*)*) creature with "(.+)"\.?""")
    val SELF_IS_A = Regex("""(this \w+) is an? (\w+)""")
    // dmr chunk 01: Wild Dogs
    val PLAYER_WITH_MOST_LIFE_GAINS_CONTROL = Regex("""[Tt]he player with the most life gains control of (.+)\.""")

    /** "The player with the most life": each player whose life total equals the greatest life total (CR 119.1). */
    val playersWithMostLife: arcanum.api.Players = arcanum.api.Players.Each(arcanum.api.PlayerFilter.Where(arcanum.api.Condition.Compare(
        arcanum.api.Value.LifeOf(arcanum.api.Players.Tested), arcanum.api.Comparison.Equal,
        arcanum.api.Value.GreatestFor(arcanum.api.Players.Each(arcanum.api.PlayerFilter.Any), arcanum.api.Value.LifeOf(arcanum.api.Players.Tested)))))
}
