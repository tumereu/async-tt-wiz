package org.asyncttwiz.storage

/** Opaque versioned wire data, not Java serialization or an authoritative local game. */
data class StoredPayload(val version: Long, val json: String) {
    init { require(version > 0) }
}

data class RecordKey(val kind: String, val id: String) {
    init { require(kind.isNotEmpty() && id.isNotEmpty()) }
}

data class AcceptedRecord(val key: RecordKey, val payload: StoredPayload)
data class AcceptedSnapshot(val revision: Long, val records: List<AcceptedRecord>)

enum class StorageOpenFailure { OPEN_FAILED, UNSUPPORTED_VERSION, INVALID_SCHEMA, MIGRATION_FAILED }
