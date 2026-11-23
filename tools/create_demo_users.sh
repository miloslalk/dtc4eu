#!/usr/bin/env bash
# Creates one demo account per role ({role}_dtc4eu) on a running platform via
# the admin API (POST /api/v1/users), all sharing one password. Safe to re-run:
# existing usernames are reported and skipped. Passwords are read silently and
# never printed or passed on the command line.
#
# SUPER_ADMIN/ADMIN demo accounts are opt-in (--with-admins): otherwise every
# holder of the shared demo password would also hold an admin login.
#
#   PLATFORM_URL=https://platform.dtc4.eu bash tools/create_demo_users.sh
#   PLATFORM_URL=https://platform.dtc4.eu bash tools/create_demo_users.sh --with-admins
set -euo pipefail

PLATFORM_URL="${PLATFORM_URL:?set PLATFORM_URL, e.g. PLATFORM_URL=https://platform.dtc4.eu}"
API="$PLATFORM_URL/api/v1"

WITH_ADMINS=false
case "${1:-}" in
  "") ;;
  --with-admins) WITH_ADMINS=true ;;
  *) echo "Usage: PLATFORM_URL=... $0 [--with-admins]" >&2; exit 2 ;;
esac

ROLES=(
  "RESEARCH_TEAM:Research Team"
  "COORDINATOR:Terna Coordinator"
  "UPPER_COORDINATOR:Upper Coordinator"
  "AUDIT_COORDINATOR:Audit Coordinator"
  "INTERNAL_FACILITATOR:Internal Facilitator"
  "EVALUATOR:Evaluator"
  "RESPONDENT:Respondent"
  "TRAINER:Trainer"
  "DTC_REPRESENTATIVE:DTC Representative"
  "POLICYMAKER:Policymaker"
)
if [ "$WITH_ADMINS" = true ]; then
  ROLES=("SUPER_ADMIN:Super Administrator" "ADMIN:Administrator" "${ROLES[@]}")
fi

echo "Platform: $PLATFORM_URL"
[ "$WITH_ADMINS" = true ] && echo "Including SUPER_ADMIN and ADMIN demo accounts."
read -r -p "Admin username: " ADMIN_USER
read -r -s -p "Admin password: " ADMIN_PASS; echo
read -r -s -p "Shared demo password (min 8 chars): " DEMO_PASS; echo
read -r -s -p "Repeat demo password: " DEMO_PASS2; echo

if [ "$DEMO_PASS" != "$DEMO_PASS2" ]; then echo "Demo passwords do not match." >&2; exit 1; fi
if [ "${#DEMO_PASS}" -lt 8 ]; then echo "Demo password must be at least 8 characters." >&2; exit 1; fi

export ADMIN_USER ADMIN_PASS DEMO_PASS

LOGIN_JSON=$(python3 -c 'import json,os; print(json.dumps({"username": os.environ["ADMIN_USER"], "password": os.environ["ADMIN_PASS"]}))')
LOGIN_RESP=$(printf '%s' "$LOGIN_JSON" | curl -sS -w '\n%{http_code}' -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' -d @-)
LOGIN_CODE=$(printf '%s' "$LOGIN_RESP" | tail -n1)
if [ "$LOGIN_CODE" != "200" ]; then echo "Admin login failed (HTTP $LOGIN_CODE)." >&2; exit 1; fi
TOKEN=$(printf '%s' "$LOGIN_RESP" | sed '$d' | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

for entry in "${ROLES[@]}"; do
  code="${entry%%:*}"
  name="${entry#*:}"
  username="$(printf '%s' "$code" | tr '[:upper:]' '[:lower:]')_dtc4eu"
  body=$(ROLE_CODE="$code" ROLE_NAME="$name" DEMO_USERNAME="$username" python3 -c 'import json,os
print(json.dumps({"username": os.environ["DEMO_USERNAME"], "fullName": "Demo " + os.environ["ROLE_NAME"],
                  "password": os.environ["DEMO_PASS"], "roles": [os.environ["ROLE_CODE"]], "preferredLang": "en"}))')
  resp=$(printf '%s' "$body" | curl -sS -w '\n%{http_code}' -X POST "$API/users" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d @-)
  status=$(printf '%s' "$resp" | tail -n1)
  case "$status" in
    201) printf '%-30s created\n' "$username" ;;
    409) printf '%-30s already exists (skipped)\n' "$username" ;;
    *)   printf '%-30s FAILED (HTTP %s): %s\n' "$username" "$status" "$(printf '%s' "$resp" | sed '$d')" ;;
  esac
done
