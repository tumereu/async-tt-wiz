# Resolved qualification diagnostics

- The initial build found a package-private ownership failure constructor at the
  new runtime boundary; exposing the existing safe reason-only constructor resolved
  compilation. No raw error cause/message crosses that boundary.
- Spring's BOM initially selected jOOQ 3.21.7 instead of the already-qualified
  3.20.20 generator/runtime line. A strict runtime constraint and deliberate lock
  refresh restore the unchanged qualified line. A failed build does not persist
  Gradle locks; the separate successful lock-only command is retained. Ordinary
  clean/repeated builds and the artifact validator now enforce the locks/version.
- Minimal JDK NIO reproduced `SocketException` on wildcard port reservation followed
  by explicit loopback rebind. Standalone Tomcat at port 0 worked; fixed ports from
  matching-loopback reservations work in both minimal NIO and real Spring. This is
  a test reservation repair, not a runtime random-port fallback. Positive HTTP at
  `127.0.0.2` timed out on this WSL topology; ordinary `127.0.0.1` HTTP succeeds with
  configured distinct health/public ports. No claim is made that every host alias
  is reachable. The host relay also sometimes accepts TCP after JVM exit; exact
  listener rebinding proves release without relying on that misleading TCP probe.
- One capture-wrapper invocation of the focused process suite exceeded the shell
  tool's 120,000 ms ceiling. The tool returned: `Command exceeded timeout of 120000
  ms. Retry with a larger timeout if the command is expected to take longer.Timed
  out before completion` and `(no output)`. No child exit code/full captured stdout
  was obtained, so none is invented as a passing or failed Gradle result. The next
  Docker inventory was empty. Subsequent invocations use no shell timeout, finish
  normally and retain their actual exit/output. The interrupted command was:
  `python3 scripts/capture-check.py docs/agent/implementation-runs/20261021T192847Z-0d517a87/process-tests.json -- ./scripts/gradle.sh --no-daemon --console=plain :server:test --tests org.asyncttwiz.server.ServiceProcessTest --tests org.asyncttwiz.server.SpringStartupTest verifyModuleBoundaries`.
- The first license metadata inventory did not recognize Logback's literal SPDX
  `LGPL-2.1-only` spelling. The recognizer now retains/recognizes the actual dual
  EPL-2.0/LGPL-2.1-only POM metadata. This does not waive redistribution notices,
  legal clearance or any external release gate.
- The first final handoff helper looked for `jcmd` on PATH, but this environment
  supplies JDK21 via `JAVA_HOME`. Its failed output is preserved; the helper now
  invokes the real `JAVA_HOME/bin/jcmd` for class-specific process cleanup inventory.

All original available command captures are retained unchanged as diagnostics.
The final clean and repeated suites pass (34 service tests; no skipped tests),
including actual SIGTERM during a PostgreSQL blackhole (about 10 seconds in the
repeated run), the fencing regressions and exact owned resource cleanup. Lower-layer
tests do not claim complete product acceptance, image execution or independent review.
