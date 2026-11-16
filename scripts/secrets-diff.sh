#!/usr/bin/env bash
# Diff-scoped secrets check (CONSTRAINTS.md Secrets row, edit loop): scans the
# uncommitted diff plus untracked files. Always --redact: report rule + location, never the value.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
{
  git diff HEAD || exit 2
  git ls-files -z --others --exclude-standard | while IFS= read -r -d '' f; do
    git diff --no-index -- /dev/null "$f"
    [ $? -le 1 ] || exit 2  # exit 1 just means "differs", the normal case for a new file
  done
} | gitleaks stdin -c .gitleaks.toml --redact --no-banner -v
