package org.asyncttwiz.server.diagnostics

/** How many reports the server keeps, so the Pi never fills up (LOB-090). Every kind keeps only its newest
 * reports per account, everything together keeps only the newest [total], and nothing older than [maxAgeDays]
 * stays. Each value can be set from the environment; missing, invalid or non-positive values use the default. */
data class ReportLimits(
    val crashPerAccount: Int = 20,
    val latencyPerAccount: Int = 60,
    val stutterPerAccount: Int = 20,
    val total: Int = 500,
    val maxAgeDays: Int = 30,
) {
    fun perAccount(kind: String): Int = when (kind) {
        "crash" -> crashPerAccount
        "latency" -> latencyPerAccount
        else -> stutterPerAccount
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ReportLimits {
            fun read(name: String, default: Int) = environment[name]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: default
            val defaults = ReportLimits()
            return ReportLimits(
                read("CRASH_REPORT_LIMIT_PER_ACCOUNT", defaults.crashPerAccount),
                read("LATENCY_REPORT_LIMIT_PER_ACCOUNT", defaults.latencyPerAccount),
                read("STUTTER_REPORT_LIMIT_PER_ACCOUNT", defaults.stutterPerAccount),
                read("DIAGNOSTIC_REPORT_LIMIT", defaults.total),
                read("DIAGNOSTIC_REPORT_MAX_AGE_DAYS", defaults.maxAgeDays),
            )
        }
    }
}

/** Player-sent problem reports (LOB-089): newest [perAccount] per signed-in account and newest [total] overall.
 * Reports sent before sign-in have no account and are bounded by [total] alone. */
data class ProblemReportLimits(val perAccount: Int = 20, val total: Int = 200) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ProblemReportLimits {
            fun read(name: String, default: Int) = environment[name]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: default
            val defaults = ProblemReportLimits()
            return ProblemReportLimits(read("PROBLEM_REPORT_LIMIT_PER_ACCOUNT", defaults.perAccount),
                read("PROBLEM_REPORT_LIMIT", defaults.total))
        }
    }
}
