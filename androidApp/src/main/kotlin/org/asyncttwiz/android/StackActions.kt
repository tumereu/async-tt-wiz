package org.asyncttwiz.android

object StackContexts {
    /** A type line's card types and supertypes (the part before "—"). */
    fun types(typeLine: String): Set<String> = typeLine.substringBefore('—').split(Regex("\\s+"))
        .filter(String::isNotEmpty).toSet()
}
