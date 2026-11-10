package org.asyncttwiz.catalog

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Catalog files on the JVM and Android: gzip-compressed lines ([CardCatalogFormat]) whose version is the SHA-256 of the
 * uncompressed lines, kept as `<checksum>.jsonl.gz`. */
object CardCatalogFiles {
    const val SUFFIX = ".jsonl.gz"
    // How a recipe record's line starts: the class discriminator comes first ([CardCatalogFormat.encode])
    private const val RECIPE_LINE = "{\"t\":\"recipe\""
    private val checksumPattern = Regex("[0-9a-f]{64}")

    fun valid(checksum: String): Boolean = checksumPattern.matches(checksum)

    /** Writes [catalog] to [output] (closed after) and returns its checksum. */
    fun write(catalog: CardCatalog, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        GZIPOutputStream(output, 1 shl 16).use { gzip ->
            DigestOutputStream(gzip, digest).bufferedWriter(Charsets.UTF_8).use { writer ->
                catalog.records().forEach { writer.write(CardCatalogFormat.encode(it)); writer.write("\n") }
            }
        }
        return hex(digest.digest())
    }

    /** Writes [catalog] into [directory] as `<checksum>.jsonl.gz` (atomically) and returns that file. */
    fun write(catalog: CardCatalog, directory: File): File {
        check(directory.isDirectory || directory.mkdirs()) { "Can't make $directory" }
        val part = File.createTempFile("catalog-", ".part", directory)
        try {
            val checksum = part.outputStream().use { write(catalog, it) }
            val file = File(directory, checksum + SUFFIX)
            check(part.renameTo(file) || (file.isFile && part.delete())) { "Can't write $file" }
            return file
        } finally {
            part.delete()
        }
    }

    /** The catalog in [input] (gzip) and its checksum, worked out while reading. */
    fun read(input: InputStream): Pair<CardCatalog, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val catalog = GZIPInputStream(input, 1 shl 16).use { gzip ->
            java.security.DigestInputStream(gzip, digest).bufferedReader(Charsets.UTF_8).use { reader ->
                CardCatalogFormat.read(reader.lineSequence())
            }
        }
        return catalog to hex(digest.digest())
    }

    /** Reads the catalog in [input] (gzip) one record at a time into [visit], in file order (the app indexes it so, never
     * holding it whole), and returns its checksum, worked out while reading. [recipes]: false leaves out the booster
     * recipes undecoded (the app has no use for them; the checksum still covers them). */
    fun stream(input: InputStream, recipes: Boolean = true, visit: (CatalogRecord) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        GZIPInputStream(input, 1 shl 16).use { gzip ->
            java.security.DigestInputStream(gzip, digest).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.lineSequence().filter { it.isNotEmpty() && (recipes || !it.startsWith(RECIPE_LINE)) }
                    .forEach { visit(CardCatalogFormat.decode(it)) }
            }
        }
        return hex(digest.digest())
    }

    /** The catalog in [file], checked against the checksum its name gives. */
    fun read(file: File): CardCatalog {
        val expected = file.name.removeSuffix(SUFFIX)
        check(valid(expected)) { "Not a catalog file name: ${file.name}" }
        val (catalog, checksum) = file.inputStream().use(::read)
        check(checksum == expected) { "Catalog ${file.name} has checksum $checksum" }
        return catalog
    }

    /** The SHA-256 of [file]'s bytes, as hexadecimal. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
