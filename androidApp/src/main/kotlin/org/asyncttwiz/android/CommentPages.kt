package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Worker-owned cursor. No conversion of the full annotation map, even on the first page. */
internal class CommentPages(annotations: EventAnnotationState) {
    private val iterator = annotations.commentsById.values.iterator()
    var inspected = 0; private set
    fun next(): List<RecordedEventComment> = buildList {
        repeat(50) { if (iterator.hasNext()) { add(iterator.next()); inspected++ } }
    }
    fun hasMore() = iterator.hasNext()
}
