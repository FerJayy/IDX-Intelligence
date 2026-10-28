#!/usr/bin/env bash
# Runs one import, or with JOB=due every import that may still be due, against $BASE with $TOKEN.
# A failing step doesn't stop the next one; the run fails at the end if any step failed.
set -u
failed=0

call() {
  echo "== $1"
  if ! curl -fsS --max-time 8000 -X POST -H "X-Admin-Token: $TOKEN" "$BASE/api/admin/import/$2"; then
    echo "(failed)"
    failed=1
  fi
  echo
}

case "$JOB" in
  due)
    call "prices (skipped if today's import already succeeded)" "prices?ifDue=true"
    call "screener picks" "screener"
    call "broker summaries" "brokers"
    ;;
  *)
    call "$JOB" "$JOB"
    ;;
esac
exit $failed
