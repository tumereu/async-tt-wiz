# I01c3 — local application image

Starting commit: `83e83a8dfc87df4ed5ed24e99b0449c5256a9283`; clean master/index.
D-304 authorizes implementation; I01c1/I01c2 are implemented, findings are empty,
and B1 independent review follows I01–I04, so I01c3 is the next eligible unit.

Sources: I01c3's unchanged outcome/checks; runtime-and-verification-profile.md
“Application container interface” and JVM21/PostgreSQL17 baseline;
storage-mechanics.md writer ownership; server/README.md implemented interfaces.
Coverage: foundation portions of P-001, T-630 and T-662 only. No whole product
scenario, UI feedback, future durable worker or independent review is completed.
T-015/T-551, Compose/device work and I35 full amd64/arm64 qualification remain.

Owned scope: a digest-pinned JVM21 runtime image containing the real locked Gradle
distribution and both explicit serve/migrate commands; local build interface;
isolated Docker image lifecycle tests, source/configuration documentation, this
run's evidence/report and selected task status. No deployment, publication,
production orchestration, paid resources, new sessions, runner/permission/model/
authorization or external gate changes.

Checks: clean locked compilation and real generated schema; affected service/DB
regressions; actual built-image identity/base/runtime inventory and no build tools
or credentials in image layers; fresh/retained explicit migration and invalid-schema
refusal; live/ready port separation; file secrets/redaction; competing writer,
lock takeover, DB outage/recovery; <=30s actual SIGTERM and retained epoch restart;
failure evidence before exact owned resource cleanup; documentation/ledger/whitespace
and report/state boundary validation. arm64 execution is not claimed.
