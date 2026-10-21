# Client storage boundary

Android adapter module reserved for I01d's real SQLDelight schema, drivers and
forward migrations. The empty initial library does not persist data or implement
an outgoing journal. Generated database types must remain in this adapter, never
in `:core` or `:protocol`. `:androidApp` already depends on this module.
