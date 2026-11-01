package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What one card face does: its name and its abilities in printed order. */
@Serializable
data class FaceDefinition(val name: String, val abilities: List<Ability>)

/** One ability of a card face: a [Keyword], or [UnparsedAbility] for text the parser could not read. */
@Serializable
sealed interface Ability

/** Text the parser could not read, kept exactly as printed (reminder text included) so it can be shown and
 * handled by hand. */
@Serializable
@SerialName("unparsed")
data class UnparsedAbility(val text: String) : Ability
