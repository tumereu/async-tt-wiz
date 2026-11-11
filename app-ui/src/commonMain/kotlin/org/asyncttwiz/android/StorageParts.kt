package org.asyncttwiz.android

import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogNamedPart
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.ParsedDeck

// The card records' readings the screens use that the platform's storage works out (docs/specs/web-client.md §1 item 2).
// Android: client-storage's own functions (they read the records' JSON with Gson), called as before.

/** [record]'s picture at [size] (Android: client-storage's ArtworkReference.from). */
expect fun ArtworkReference.Companion.from(record: CatalogPrinting, size: String = "normal"): ArtworkReference?

/** The card's named parts, one per face (Android: client-storage's CatalogCard.namedParts). */
expect fun CatalogCard.namedParts(): List<CatalogNamedPart>

/** The first Cube Cobra cube link in [text], as written, or null (Android: CubeCobraImport.link). */
expect fun cubeCobraLink(text: String?): String?

/** The cube id of a Cube Cobra link or id [value]; throws when it is neither (Android: CubeCobraImport.cubeId). */
expect fun cubeCobraId(value: String): String

/** A Cube Cobra cube's JSON as a card list; throws when it can't be read (Android: CubeCobraImport.parse). */
expect fun parseCubeCobra(json: String): ParsedDeck
