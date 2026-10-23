package org.asyncttwiz.protocol

import kotlin.test.*

class RandomV1Test {
    @Test fun independentLiteralBlocksWordsOrdersAndStarterDomains() {
        for (vector in RandomGolden.vectors) {
            val bytes = RandomV1.decodeSeed(vector.seed)
            assertEquals(vector.seed, RandomV1.encodeSeed(bytes))
            val words = RandomV1.Words(vector.seed)
            assertEquals(vector.words, List(16) { words.next() }) // Crosses the uint64 counter block boundary.
            assertEquals(vector.blockHex, vector.words.take(8).joinToString("") { it.toString(16).padStart(8, '0') })
            val starter = RandomV1.Words(vector.seed, "starter-v1")
            assertEquals(vector.starterBlock, List(8) { starter.next() }.joinToString("") { it.toString(16).padStart(8, '0') })
            for ((count, expected) in vector.orders) {
                val ids = (1..count).map { "00000000-0000-0000-0000-${it.toString(16).padStart(12, '0')}" }
                assertEquals(expected, RandomV1.shuffle(ids, vector.seed), "count=$count seed=${vector.seed}")
                assertEquals(expected, RandomV1.shuffle(ids.reversed(), vector.seed))
            }
            val participants = listOf("ffffffff-ffff-ffff-ffff-ffffffffffff", "00000000-0000-0000-0000-000000000001")
            assertEquals(vector.starter, RandomV1.starter(participants, vector.seed))
            assertEquals(vector.starter, RandomV1.starter(participants.reversed(), vector.seed))
        }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".encodeToByteArray()))
        assertEquals("7d0ea874-3655-53e6-abea-5cca5d8433dc", CommandIds.uuidV5("00000000-0000-4000-8000-000000000001", "board-node"))
    }

    @Test fun rejectionBoundariesAreUnsignedAndConsumeTheNextWord() {
        // Literal independent boundary vectors from random-v1-vectors.md, not a second
        // copy of the implementation's limit calculation as the expected answer.
        val vectors = mapOf(
            1 to listOf(Triple(0L, 0, 1), Triple(4294967295L, 0, 1)),
            2 to listOf(Triple(0L, 0, 1), Triple(4294967295L, 1, 1)),
            3 to listOf(Triple(0L, 0, 1), Triple(4294967294L, 2, 1), Triple(4294967295L, 0, 2)),
            7 to listOf(Triple(0L, 0, 1), Triple(4294967291L, 6, 1), Triple(4294967292L, 0, 2), Triple(4294967295L, 0, 2)),
            Int.MAX_VALUE to listOf(Triple(0L, 0, 1), Triple(4294967293L, 2147483646, 1),
                Triple(4294967294L, 0, 2), Triple(4294967295L, 0, 2)))
        for ((bound, cases) in vectors) {
            for ((word, expected, expectedConsumption) in cases) {
                var consumed = 0
                val result = RandomV1.sample(bound) { consumed++; if (consumed == 1) word else 0L }
                assertEquals(expected, result)
                assertEquals(expectedConsumption, consumed)
            }
        }
        assertFails { RandomV1.sample(0) { 0 } }
        assertFails { RandomV1.sample(2) { -1 } }
        assertFails { RandomV1.sample(2) { 4294967296L } }
    }

    @Test fun invalidNoncanonicalSeedAlgorithmAndMembershipAreRefused() {
        val zero = "A".repeat(43)
        for (seed in listOf("", zero + "=", zero.dropLast(1), zero.dropLast(1) + "B", "+" + zero.drop(1), "/" + zero.drop(1)))
            assertFails { RandomV1.decodeSeed(seed) }
        assertFails { RandomV1.encodeSeed(ByteArray(31)) }
        assertFails { RandomV1.Words(zero, "shuffle-v2") }
        assertFails { RandomV1.shuffle(emptyList(), zero, "starter-v1") }
        assertFails { RandomV1.shuffle(listOf("not a uuid"), zero) }
        val uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        assertFails { RandomV1.shuffle(listOf(uuid, uuid), zero) }
        assertEquals(listOf("7fffffff-ffff-ffff-ffff-ffffffffffff", "80000000-0000-0000-0000-000000000000", uuid),
            RandomV1.canonical(listOf(uuid, "80000000-0000-0000-0000-000000000000", "7fffffff-ffff-ffff-ffff-ffffffffffff")))
    }
}
