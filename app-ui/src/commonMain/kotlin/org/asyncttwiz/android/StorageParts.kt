package org.asyncttwiz.android

import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogNamedPart
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.CubeCobraImport
import org.asyncttwiz.storage.ParsedDeck
import org.asyncttwiz.storage.from as storageFrom
import org.asyncttwiz.storage.namedParts as storageNamedParts

// The card records' readings the screens use, worked out by client-storage's common code (kotlinx.serialization) on both
// platforms (docs/specs/web-client.md §1 item 2).

/** [record]'s picture at [size] (client-storage's ArtworkReference.from). */
fun ArtworkReference.Companion.from(record: CatalogPrinting, size: String = "normal"): ArtworkReference? = storageFrom(record, size)

/** The card's named parts, one per face (client-storage's CatalogCard.namedParts). */
fun CatalogCard.namedParts(): List<CatalogNamedPart> = storageNamedParts()

/** The first Cube Cobra cube link in [text], as written, or null. */
fun cubeCobraLink(text: String?): String? = CubeCobraImport.link(text)

/** The cube id of a Cube Cobra link or id [value]; throws when it is neither. */
fun cubeCobraId(value: String): String = CubeCobraImport.cubeId(value)

/** A Cube Cobra cube's JSON as a card list; throws when it can't be read. */
fun parseCubeCobra(json: String): ParsedDeck = CubeCobraImport.parse(json)
