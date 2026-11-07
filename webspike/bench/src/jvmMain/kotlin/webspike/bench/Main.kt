package webspike.bench

/** Web spike: the engine benchmark on the desktop JVM (`./scripts/gradle.sh :webspike-bench:runJvmBench`). */
fun main(args: Array<String>) {
    val runs = args.getOrNull(0)?.toIntOrNull() ?: 15
    val result = Bench.run(runs = runs) { System.err.println(it) }
    println(result.report("jvm"))
    println("BENCHJSON jvm " + result.json())
}
