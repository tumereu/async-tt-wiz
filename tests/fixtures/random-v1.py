"""Independent stdlib reference; generates literal shared Kotlin fixtures, never used at runtime.

Run: python3 tests/fixtures/random-v1.py > protocol/src/commonTest/kotlin/org/asyncttwiz/protocol/RandomGolden.kt
The first blocks/seven-member orders are also checked against the preexisting specification.
"""
import base64
import hashlib
import json
import struct
import uuid


def stream(seed, domain):
    counter = 0
    while True:
        block = hashlib.sha256(domain + seed + counter.to_bytes(8, "big")).digest()
        yield from struct.unpack(">8I", block)
        counter += 1


def bounded(words, bound):
    ceiling = (2**32 // bound) * bound
    while True:
        value = next(words)
        if value < ceiling:
            return value % bound


seeds = [bytes(32), bytes(range(32)), bytes([255]) * 32]
blocks = ["0e6fdb1be498a0fdcad2682eae46ad35f55a5f534f5c8981b841c4a1c8db7d8f",
          "844b6e79f6313e0ec2e6488b662e3805a9e5496d497c90e04b31190a4a3a883b",
          "bc3dc85c8912aea204bf822ea4864871a0918d7b138e2ba20ed8a97fd272c7eb"]
orders = [[5, 4, 1, 7, 3, 2, 6], [6, 5, 7, 2, 1, 3, 4], [4, 1, 7, 5, 2, 3, 6]]
print('// Generated literal fixtures by tests/fixtures/random-v1.py (Python hashlib/struct/uuid).')
print('// Expected values are not computed by the Kotlin implementation under test.')
print('package org.asyncttwiz.protocol\n')
print('internal object RandomGolden {')
print('    data class Vector(val seed: String, val blockHex: String, val words: List<Long>,')
print('        val orders: Map<Int, List<String>>, val starterBlock: String, val starter: String)')
print('    val vectors = listOf(')
participants = [str(uuid.UUID(int=1)), str(uuid.UUID(int=2**128-1))]
for index, seed in enumerate(seeds):
    domain = b"async-tt-wiz/shuffle/v1\0"
    block = hashlib.sha256(domain + seed + bytes(8)).hexdigest()
    assert block == blocks[index]
    words = stream(seed, domain)
    print('        Vector(' + json.dumps(base64.urlsafe_b64encode(seed).decode().rstrip('=')) + ',')
    print('            ' + json.dumps(block) + ',')
    print('            listOf(' + ', '.join(str(next(words)) + 'L' for _ in range(16)) + '),')
    print('            mapOf(')
    for count in [0, 1, 2, 7, 60, 257]:
        ids = sorted((uuid.UUID(int=i) for i in range(1, count + 1)), key=lambda u: u.bytes)
        words = stream(seed, domain)
        for i in range(count - 1, 0, -1):
            j = bounded(words, i + 1)
            ids[i], ids[j] = ids[j], ids[i]
        if count == 7:
            assert [u.int for u in ids] == orders[index]
        print(f'                {count} to listOf(')
        for start in range(0, len(ids), 3):
            print('                    ' + ', '.join(json.dumps(str(u)) for u in ids[start:start+3]) + ',')
        print('                ),')
    starter_domain = b"async-tt-wiz/starter/v1\0"
    starter_block = hashlib.sha256(starter_domain + seed + bytes(8)).hexdigest()
    winner = participants[bounded(stream(seed, starter_domain), 2)]
    print('            ), ' + json.dumps(starter_block) + ', ' + json.dumps(winner) + '),')
print('    )\n}')
