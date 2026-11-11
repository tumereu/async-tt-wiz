package arcanum.engine.scenario

import arcanum.api.Ability

/** Coverage is recorded only on the JVM (`recordCoveredAbilities`). */
internal actual fun coveredAbilitySink(): ((Coverage, Ability) -> Unit)? = null
