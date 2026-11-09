package arcanum.parser.cube

import arcanum.api.Ability

/** Notes [abilities] of a card a test checked in full, for `:arcanum-tools:coveredShapes`; does nothing in a normal test run. */
internal expect fun recordCoveredAbilities(abilities: List<Ability>)
