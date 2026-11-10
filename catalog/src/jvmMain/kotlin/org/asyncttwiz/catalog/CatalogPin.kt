package org.asyncttwiz.catalog

import java.io.File

/**
 * The catalog's pin (docs/specs/web-client.md section 3.1), `catalog/pin.properties`: the MTGJSON build every real-card
 * tool, the release, the server and both clients read, and the SHA-256 of its raw `AllPrintings.json.gz`. Holds no card
 * data; the raw file is kept only on the developer's machine. The trimmed catalog's checksum is not pinned: the release
 * works it out when it builds the catalog and builds it into the app (section 3.4).
 */
data class CatalogPin(val mtgjsonVersion: String, val mtgjsonDate: String, val allPrintingsSha256: String) {
    fun write(file: File) = file.writeText(
        """
        |# The card catalog's pin (docs/specs/web-client.md section 3.1): the MTGJSON AllPrintings build the Arcanum tools, the
        |# release, the server and both clients read. No card data here. The raw file is kept outside the repository. Check
        |# it with scripts/catalog-pin.sh verify; move the pin (a task of its own: setSupport and parserDiff against the new
        |# file first) with scripts/catalog-pin.sh move.
        |mtgjson.version=$mtgjsonVersion
        |mtgjson.date=$mtgjsonDate
        |allprintings.sha256=$allPrintingsSha256
        |""".trimMargin())

    /** The plain message for a missing or different raw file at [path]: put the pinned file there, or move the pin. */
    fun missing(path: File, actual: String? = null): String =
        (if (actual == null) "The pinned MTGJSON file isn't at $path." else "$path isn't the pinned MTGJSON file (its SHA-256 is $actual).") +
            " Put AllPrintings.json.gz of MTGJSON $mtgjsonVersion (SHA-256 $allPrintingsSha256) there, or move the pin: scripts/catalog-pin.sh move"

    companion object {
        fun read(file: File): CatalogPin {
            check(file.isFile) { "No catalog pin at $file" }
            val values = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            fun value(key: String) = checkNotNull(values[key]?.takeIf { it.isNotEmpty() }) { "$file has no $key" }
            return CatalogPin(value("mtgjson.version"), value("mtgjson.date"), value("allprintings.sha256"))
        }
    }
}
