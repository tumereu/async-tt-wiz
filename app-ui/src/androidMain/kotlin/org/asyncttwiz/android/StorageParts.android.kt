package org.asyncttwiz.android

import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogNamedPart
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.CubeCobraImport
import org.asyncttwiz.storage.ParsedDeck
import org.asyncttwiz.storage.from as storageFrom
import org.asyncttwiz.storage.namedParts as storageNamedParts

actual fun ArtworkReference.Companion.from(record: CatalogPrinting, size: String): ArtworkReference? = storageFrom(record, size)

actual fun CatalogCard.namedParts(): List<CatalogNamedPart> = storageNamedParts()

actual fun cubeCobraLink(text: String?): String? = CubeCobraImport.link(text)

actual fun cubeCobraId(value: String): String = CubeCobraImport.cubeId(value)

actual fun parseCubeCobra(json: String): ParsedDeck = CubeCobraImport.parse(json)
