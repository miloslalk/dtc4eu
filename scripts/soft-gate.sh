#!/usr/bin/env bash
# soft-gate.sh <YYYY-MM-DD> <command...> — a check that warns until the date, then blocks
# (CONSTRAINTS.md: "warn for the first two weeks"). Exit 2 (the check could not run)
# is never softened: a check that cannot run must not read as a pass.
until="$1"; shift
"$@"; rc=$?
if [ "$rc" -eq 1 ] && [[ "$(date +%F)" < "$until" ]]; then
  echo "WARN (soft until $until, then blocking): $*" >&2
  exit 0
fi
exit "$rc"
