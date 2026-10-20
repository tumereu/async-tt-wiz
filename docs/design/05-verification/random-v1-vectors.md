# Random v1 literal reference vectors

Specification fixtures for [runtime random v1](../04-technical/runtime-and-verification-profile.md#deterministic-random-input-v1)
and T-655, not application tests or a claim of executed shared Kotlin code.
Calculated independently with Python standard-library SHA-256/integer arithmetic
while writing the specification, 2026-10-20. Implementation must consume these
literal expected values, not regenerate its expected answer with its own shuffle.

All seven input IDs are UUIDs `00000000-0000-0000-0000-000000000001` through
`00000000-0000-0000-0000-000000000007`, sorted by unsigned 16-byte order. Numbers
in the output column denote those literal last-byte identities, not array indices.
Sampler choices are zero-based j for bounds 7,6,5,4,3,2, in that order. These vectors
consume the first six words; separate boundary fixtures below exercise rejection.

| Seed bytes (32 bytes) | First SHA-256 block, lowercase hex | First six unsigned big-endian words | Sampled j | Final ID order |
| --- | --- | --- | --- | --- |
| all 00 | `0e6fdb1be498a0fdcad2682eae46ad35f55a5f534f5c8981b841c4a1c8db7d8f` | 242211611,3835207933,3402786862,2923867445,4116340563,1331464577 | 5,1,2,1,0,1 | 5,4,1,7,3,2,6 |
| 00,01,…,1f | `844b6e79f6313e0ec2e6488b662e3805a9e5496d497c90e04b31190a4a3a883b` | 2219535993,4130422286,3269871755,1714305029,2850376045,1232900320 | 3,2,0,1,1,0 | 6,5,7,2,1,3,4 |
| all ff | `bc3dc85c8912aea204bf822ea4864871a0918d7b138e2ba20ed8a97fd272c7eb` | 3158165596,2299702946,79659566,2760263793,2693893499,328084386 | 5,2,1,1,2,0 | 4,1,7,5,2,3,6 |

Empty/singleton input is unchanged for every seed, with no sample consumed. Reversed
or otherwise permuted enumeration of the same input set gives the same output
because canonical sorting precedes Fisher–Yates.

| Bound b | limit | Boundary behavior |
| --- | --- | --- |
| 1 | 4294967296 | Every uint32 accepted; output 0. |
| 2 | 4294967296 | 4294967295 accepted; output 1. |
| 3 | 4294967295 | 4294967294 accepted → 2; 4294967295 rejected, consume next word. |
| 7 | 4294967292 | 4294967291 accepted → 6; 4294967292..4294967295 rejected. |
| 2147483647 | 4294967294 | 4294967293 accepted → 2147483646; 4294967294/4294967295 rejected. |

Protocol output-identity fixtures using namespace/request UUID
`00000000-0000-4000-8000-000000000001` and UTF-8 names:

- `board-node` → `7d0ea874-3655-53e6-abea-5cca5d8433dc`
- `token/0` → `1a6709d5-311d-54a2-a2dc-625218e2adeb`

UUIDv5 uses the RFC namespace-byte/name algorithm; do not hash the namespace's
display string or apply native-endian UUID memory layout. Accepted outcome IDs
and concrete shuffled orders remain recorded server output; vectors are lower-
layer fixtures, never a hidden E2E API for choosing game results.

## Stack checksum v1 reference vectors — T-668

For protocol v1's Stack guard, use play ID
`00000000-0000-4000-8000-000000000001`. R is a `realCard` with `id` and `sourceId`
both `00000000-0000-0000-0000-000000000001`; E is an `effect` with `id`
`00000000-0000-0000-0000-000000000002` and the same source ID as R. Hash only the
specified descriptor, never rendered source fields. Independently calculated with
Python stdlib SHA-256 and sorted compact JSON (all keys/values here are ASCII, so
this has RFC 8785's canonical bytes); these are fixtures, not executed Kotlin tests.

| Ordered entries | Expected checksum |
| --- | --- |
| `[]` | `sha256:a7c8c625bee1013522c0943d76f51eabdc731857ab80a8f7ed3da39140664f9d` |
| `[R,E]` | `sha256:05138a821c801b6a01e30e1fbe7dfb832b6b5109cb6113847123d099f7795516` |
| `[E,R]` | `sha256:f65c95a8e499649d9be008c3d4c182830692ed7541d39b4e4ae90296ee6e9860` |

Literal empty-Stack canonical bytes, UTF-8 with no newline:

```json
{"entries":[],"kind":"stack-guard","playId":"00000000-0000-4000-8000-000000000001","version":1}
```
