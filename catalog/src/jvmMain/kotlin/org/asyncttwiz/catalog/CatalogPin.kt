package org.asyncttwiz.catalog

import java.io.File

/**
 * The catalog's pin, `catalog/pin.properties`: the MTGJSON build every real-card
 * tool, the release, the server and both clients read, and the SHA-256 of its raw `AllPrintings.json.gz`; and the draft
 * bots' card ratings file ([CatalogRatings], from 17Lands' public data) the catalog build bakes in: the day it was
 * fetched ([ratingsDate]) and its SHA-256 (null: no ratings pinned). Holds no card data; the raw files are kept only on the
 * developer's machine. The trimmed catalog's checksum is not pinned: the release works it out when it builds the catalog
 * and builds it into the app (SYS-081).
 */
data class CatalogPin(val mtgjsonVersion: String, val mtgjsonDate: String, val allPrintingsSha256: String,
    val ratingsDate: String? = null, val ratingsSha256: String? = null) {
    fun write(file: File) = file.writeText(
        """
        |# The card catalog's pin: the MTGJSON AllPrintings build the Arcanum tools, the
        |# release, the server and both clients read. No card data here. The raw file is kept outside the repository. Check
        |# it with scripts/catalog-pin.sh verify; move the pin (a task of its own: setSupport and parserDiff against the new
        |# file first) with scripts/catalog-pin.sh move.
        |mtgjson.version=$mtgjsonVersion
        |mtgjson.date=$mtgjsonDate
        |allprintings.sha256=$allPrintingsSha256
        |""".trimMargin() + if (ratingsSha256 == null) "" else
        """
        |# The draft bots' card ratings (17Lands' public game data, CC BY 4.0), kept outside the repository: fetch and pin
        |# them again with scripts/catalog-pin.sh ratings-move.
        |ratings.date=$ratingsDate
        |ratings.sha256=$ratingsSha256
        |""".trimMargin())

    /** The plain message for a missing or different raw file at [path]: put the pinned file there, or move the pin. */
    fun missing(path: File, actual: String? = null): String =
        (if (actual == null) "The pinned MTGJSON file isn't at $path." else "$path isn't the pinned MTGJSON file (its SHA-256 is $actual).") +
            " Put AllPrintings.json.gz of MTGJSON $mtgjsonVersion (SHA-256 $allPrintingsSha256) there, or move the pin: scripts/catalog-pin.sh move"

    /** The plain message for a missing or different ratings file at [path]: put the pinned file there, or fetch and pin again. */
    fun missingRatings(path: File, actual: String? = null): String =
        (if (actual == null) "The pinned card ratings file isn't at $path." else "$path isn't the pinned card ratings file (its SHA-256 is $actual).") +
            " Put the ratings of $ratingsDate (SHA-256 $ratingsSha256) there, or fetch and pin them again: scripts/catalog-pin.sh ratings-move"

    companion object {
        fun read(file: File): CatalogPin {
            check(file.isFile) { "No catalog pin at $file" }
            val values = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            fun value(key: String) = checkNotNull(values[key]?.takeIf { it.isNotEmpty() }) { "$file has no $key" }
            val ratings = values["ratings.sha256"]?.takeIf { it.isNotEmpty() }
            return CatalogPin(value("mtgjson.version"), value("mtgjson.date"), value("allprintings.sha256"),
                ratings?.let { value("ratings.date") }, ratings)
        }
    }
}
