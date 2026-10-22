package org.asyncttwiz.server.accounts

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UProperty
import com.ibm.icu.text.IDNA
import com.ibm.icu.text.Normalizer2

class InvalidAccountField(val field: String) : RuntimeException("Invalid account field")

/** Semantic-v1 Unicode data stays at 15.1 independently of the host JDK/Android. */
object AccountFields {
    private val nfc = Normalizer2.getNFCInstance()
    private val idna = IDNA.getUTS46Instance(IDNA.USE_STD3_RULES or IDNA.CHECK_BIDI or
        IDNA.CHECK_CONTEXTJ or IDNA.NONTRANSITIONAL_TO_ASCII)
    init { check(UCharacter.getUnicodeVersion().toString() == "15.1.0.0") }

    data class Name(val display: String, val key: String)
    data class Email(val delivery: String, val key: String) {
        override fun toString() = "Email[redacted]"
    }

    private fun scalars(value: String, field: String): Int {
        var i = 0
        var count = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) {
                if (i == value.length || !value[i++].isLowSurrogate()) throw InvalidAccountField(field)
            } else if (c.isLowSurrogate()) throw InvalidAccountField(field)
            count++
        }
        return count
    }

    private fun trim(value: String) = value.trim { UCharacter.hasBinaryProperty(it.code, UProperty.WHITE_SPACE) }

    fun username(value: String): Name {
        scalars(value, "username")
        val display = nfc.normalize(trim(value))
        if (scalars(display, "username") !in 1..64 || display.codePoints().anyMatch {
            UCharacter.isISOControl(it) || UCharacter.hasBinaryProperty(it, UProperty.BIDI_CONTROL)
        }) throw InvalidAccountField("username")
        return Name(display, UCharacter.foldCase(display, true))
    }

    fun email(value: String): Email {
        scalars(value, "email")
        val delivery = trim(value)
        val split = delivery.lastIndexOf('@')
        if (split < 1 || split != delivery.indexOf('@')) throw InvalidAccountField("email")
        val local = delivery.substring(0, split)
        if (local.length > 64 || !local.matches(Regex("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*")))
            throw InvalidAccountField("email")
        val info = IDNA.Info()
        val domain = idna.nameToASCII(delivery.substring(split + 1), StringBuilder(), info).toString().lowercase()
        if (info.hasErrors() || domain.isEmpty() || domain.endsWith('.') ||
            delivery.toByteArray(Charsets.UTF_8).size > 254 || local.length + domain.length + 1 > 254)
            throw InvalidAccountField("email")
        return Email(delivery, local.lowercase() + "@" + domain)
    }

    fun password(value: String): String {
        if (scalars(value, "password") !in 8..256 || value.toByteArray(Charsets.UTF_8).size > 1024)
            throw InvalidAccountField("password")
        return value // Never trim, fold, normalize, truncate or replace secrets.
    }
}
