package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Persistent visual relationships, not source relationships or event-scoped pings. */
@Serializable enum class LinkEndpointKind { CARD, PLAYER }
@Serializable data class LinkEndpoint(val kind: LinkEndpointKind, val id: String)
@Serializable data class PlayLink(val id: String, val first: LinkEndpoint, val second: LinkEndpoint)
/** The blank token: a token with no card data of its own. */
object TokenCatalog {
    val blank: SubmittedDefinition = CardDefinition("Blank", listOf(DefinitionFace(
        "548c71f3-20cd-53f0-b9b0-737030a14f51", "Blank")), provider = "async-tt-wiz",
        sourceVersion = "blank-v1", token = true).let { SubmittedDefinition(DeckJson.digest(it), it) }
}

/** Where a card starts on the Battlefield: the first free cell of its normal row. */
object TokenStacks {
    /** The normal Battlefield row of a card showing [face]: row 0 (lands) when its card types (before "—")
     * include Land, else row 1, the main row (as for a resolved spell or a manifested card). */
    fun row(face: DefinitionFace?): Int =
        if (face != null && "Land" in AttackTargets.types(face.typeLine)) 0 else 1
}

/** A token's card data, checked as the catalog keeps it. */
object TokenLinkRules {
    fun validDefinition(value: SubmittedDefinition) = DeckValidation.definition(value.definition) &&
        value.definition.token && value.digest == DeckJson.digest(value.definition)
}
