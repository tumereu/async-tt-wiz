#!/usr/bin/env bash
# Fast parserExplain: same output as `gradle-slot.sh :arcanum-tools:parserExplain --args='"<text>" ...'`, but runs the compiled
# tool with plain java in a light slot (two machine-wide, separate from the heavy Gradle slots; 1 GB heap). It goes through
# Gradle (a heavy slot) only to recompile when parser, API, engine or tools sources changed since the last run.
#   parser-explain.sh "<oracle text>" ["<more text>" ...]     (run from the worktree root)
# Java: the project's Gradle toolchain (recorded when it compiles), else $JAVA_HOME/bin/java, else `java` on PATH.
set -u
[ $# -ge 1 ] || { echo "Usage: parser-explain.sh '\"<oracle text>\"' ['\"<more text>\"' ...]" >&2; exit 2; }
T=$(cd "$(dirname "$0")" && pwd); L=$HOME/projects/atw-cache/locks
mkdir -p "$L"
if [ ! -x ./scripts/gradle.sh ] || [ ! -d arcanum-tools ]; then
  echo "parser-explain.sh: run it from the repository root (no ./scripts/gradle.sh in $PWD)" >&2
  exit 2
fi
cpf=arcanum-tools/build/explain-classpath.txt; jf=arcanum-tools/build/explain-java.txt
stale=0
if [ ! -s "$cpf" ] || [ ! -s "$jf" ]; then stale=1
elif [ -n "$(find arcanum-api/src/commonMain arcanum-parser/src/commonMain arcanum-engine/src/commonMain arcanum-tools/src/main \
        arcanum-tools/build.gradle.kts -newer "$cpf" -print -quit 2>/dev/null)" ]; then stale=1; fi
if [ $stale = 1 ]; then
  blog=$(mktemp); trap 'rm -f "$blog"' EXIT
  if ! "$T/gradle-slot.sh" --init-script "$T/explain-classpath.init.gradle.kts" :arcanum-tools:explainClasspath -q >"$blog" 2>&1 &&
     ! { grep -q "FqNames can't be derived\|Could not perform incremental compilation" "$blog" &&
         "$T/gradle-slot.sh" -Pkotlin.incremental=false -Pkotlin.incremental.multiplatform=false --init-script "$T/explain-classpath.init.gradle.kts" \
           :arcanum-tools:explainClasspath -q >"$blog" 2>&1; }; then
    grep -E "^e: |error:|What went wrong" -A3 "$blog" | head -30; exit 1
  fi
fi
if [ -s "$jf" ] && [ -x "$(cat "$jf")" ]; then JAVA=$(cat "$jf")
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA=$JAVA_HOME/bin/java
elif command -v java >/dev/null; then JAVA=$(command -v java)
else echo "parser-explain.sh: no java (set JAVA_HOME or put java on PATH)" >&2; exit 2; fi
# Like Gradle's --args, a pair of double quotes around an argument only delimits it: drop it, so '"<text>"' reads <text>.
# Each argument is explained as a line of its own.
text=
for a in "$@"; do [[ ${#a} -ge 2 && $a == \"*\" ]] && a=${a:1:${#a}-2}; text+=${text:+$'\n'}$a; done
args=(-Xmx1g -cp "$(cat "$cpf")" -Darcanum.parserSources="$PWD/arcanum-parser/src/commonMain/kotlin" -Darcanum.pin="$PWD/catalog/pin.properties"
  arcanum.tools.ParserExplainKt "$text")
end=$((SECONDS + 3600))
while :; do
  for n in 1 2; do
    exec 8>"$L/light-$n.lock"
    if flock -n 8; then "$JAVA" "${args[@]}"; rc=$?; flock -u 8; exit $rc; fi
  done
  ((SECONDS > end)) && { echo "No light slot free for an hour" >&2; exit 75; }
  sleep 2
done
