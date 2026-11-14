package arcanum.engine.state

/**
 * Secret choices ("secretly choose Human, Merfolk, or Goblin", [arcanum.api.Step.Choose.secret]): the choice is kept as the
 * source's card memory under its name like any other (CR 607.2d), and beside it, under [markerKey], the players who know it,
 * until it is revealed ([arcanum.api.CostPart.RevealChoice]). A host shows a secret memory only to those players (I-D27: the
 * client may hold it, never show it).
 */
object Secrets {
    /** The memory key that marks the choice kept under [key] as secret. */
    fun markerKey(key: String): String = "$key#secret"

    /** Whether [o]'s memory under [key] is a secret not yet revealed. */
    fun isSecret(o: GameObject, key: String): Boolean = o.linked[markerKey(key)] != null

    /** The players who know [o]'s secret memory under [key]; null when it isn't secret. */
    fun knownBy(o: GameObject, key: String): List<PlayerId>? = (o.linked[markerKey(key)] as? NamedValue.Players)?.ids
}
