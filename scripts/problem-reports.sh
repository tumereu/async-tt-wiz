#!/usr/bin/env bash
# Lists recent player problem reports, or prints one in full by its code.
# Usage: scripts/problem-reports.sh [--dev] [CODE]
#   Default: the test env database (container atw-test-db); --dev: the dev server database (atw-dev-db).
#   Production or any other database: PSQL="ssh pi docker exec -i <db-container> psql -U <user> -d <db>" scripts/problem-reports.sh [CODE]
#   (or PSQL="psql <database-url>").
set -euo pipefail
psql_command=(docker exec -i atw-test-db psql -U atw_test -d atw_test)
if [[ ${1:-} == --dev ]]; then psql_command=(docker exec -i atw-dev-db psql -U atw_dev -d atw_dev); shift; fi
if [[ -n ${PSQL:-} ]]; then read -r -a psql_command <<< "$PSQL"; fi
if [[ $# -eq 0 ]]; then
    "${psql_command[@]}" -X -q -P pager=off <<'SQL'
SELECT code, to_char(received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI') AS received_utc, app_version, version_code AS code_nr,
    build_type, device, android, account_id IS NOT NULL AS signed_in,
    left(regexp_replace(coalesce(nullif(description, ''), problem), '\s+', ' ', 'g'), 60) AS summary
FROM problem_report ORDER BY id DESC LIMIT 50;
SQL
else
    code=${1^^}
    [[ $code =~ ^[A-Z0-9]{6}$ ]] || { echo "A report code is 6 letters/digits." >&2; exit 2; }
    "${psql_command[@]}" -X -q -A -t -v code="$code" <<'SQL'
SELECT 'Code: ' || code || E'\nReceived: ' || received_at || E'\nReported: ' || reported_at ||
    E'\nApp: ' || app_version || ' (' || version_code || ') ' || build_type || E'\nDevice: ' || device || '; ' || android ||
    E'\nAccount: ' || coalesce(account_id::text, '-') || E'\n\nDescription:\n' || description ||
    E'\n\nUpdate problem:\n' || problem || E'\n\n--- App log ---\n' || log || E'\n\n--- Logcat ---\n' || logcat
FROM problem_report WHERE code = :'code';
SQL
fi
