package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.game.EngineJson
import java.io.File

private const val WARMUP_GAMES = 2
private const val GAMES_PER_PAIR = 1
private const val GAMES_RESOURCE = "/engine-benchmark-games.json"

/**
 * The engine spike's workloads ([EngineOnServerSpikeTest]) as a standalone program, to measure the engine on another
 * machine (the Pi) without Gradle or a database. `./scripts/gradle.sh :server:engineBenchmarkJar` records the seeded
 * games' inputs (`record <file>`) into the jar; `java -Xmx512m -jar server/build/benchmark/engine-benchmark.jar
 * [heavyRuns checkpointRuns]` (defaults 20, 11) replays them (a cold game, 2 warm-up games, one measured game per deck
 * pair), then runs the checkpoints and heavy cases: about a minute on the Pi. Prints median, p95 and max per case.
 */
fun main(args: Array<String>) {
    val spike = EngineOnServerSpikeTest().apply { warmupGames = WARMUP_GAMES; gamesPerPair = GAMES_PER_PAIR }
    if (args.firstOrNull() == "record") {
        File(args[1]).apply { parentFile.mkdirs() }.writeText(spike.record())
        return
    }
    val started = System.nanoTime()
    val recorded = requireNotNull(EngineOnServerSpikeTest::class.java.getResource(GAMES_RESOURCE)) { "no recorded games" }.readText()
    spike.apply {
        scripts = EngineJson.json.decodeFromString(SCRIPTS, recorded)
        heavyRuns = args.getOrNull(0)?.toInt() ?: 20
        checkpointRuns = args.getOrNull(1)?.toInt() ?: 11
    }.run()
    println("SPIKE wall time %.1f s".format((System.nanoTime() - started) / 1e9))
}
