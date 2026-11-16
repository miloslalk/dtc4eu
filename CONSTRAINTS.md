# Constraints

Last reviewed: 2026-11-16 by @miloslalk

This is the project's quality bar. Agents read it before writing code and never
weaken it to make a change pass: fix the code, or add a row to Exceptions with an
owner and an expiry date and say so in the change description. Tightening is
silent; loosening is loud (`scripts/floor-guard.mjs` flags it).

## Floor (always enforced, blocking)

- No new suppression comments: `@ts-ignore`, `@ts-expect-error`, `eslint-disable`, `NOSONAR`, `nosemgrep`, `gitleaks:allow`, `@Generated` to dodge coverage, coverage `<exclude>`s
- No unimplemented stubs: `throw new UnsupportedOperationException("not implemented")`, empty `catch {}`, `TODO`/`FIXME` standing in for an implementation
- No skipped or deleted tests: no `@Disabled`, `assume…()`, `.skip`, `-DskipTests`, deleted test files, or assertions removed from tests that stay
- No growth of the frozen architecture baseline (`backend/src/test/resources/archunit_store`)
- No secrets in source
- This file does not get weakened to make a change pass

Checked by `node scripts/floor-guard.mjs` (diff-scoped; exit 0 clean, 1 violation, 2 could not run).

## Enforced with numbers

`Runs at`: **edit** = `npm run check:fast` (< 5 s), **task** = `npm run check:task`
(≈ 70 s; run before handing work back), **CI** = `npm run check:full`.
**Soft until 2026-11-30**: the check warns and passes until that date, then blocks
(`scripts/soft-gate.sh`). A check that cannot run (exit 2) always fails.

| Dimension | Rule | Checked by | Runs at | Why this number |
|-----------|------|-----------|---------|-----------------|
| Floor | Zero violations in the diff | `node scripts/floor-guard.mjs --base HEAD` (edit), `--base origin/main` (CI) | edit, CI | Agents take the cheapest road to green; these are those roads |
| Types | Zero errors (TS + javac) | `npm --prefix frontend run typecheck`, `npm --prefix website/frontend run typecheck`; javac via `check:backend` | edit, task | A type error is a bug the compiler already found |
| Tests | All backend unit tests + ITs pass | `npm run check:backend` (`mvn clean verify`, Testcontainers — Docker must run) | task | The ITs are the platform's safety net for scope rules and scoring |
| Secrets | No findings | `bash scripts/secrets-diff.sh` (diff, edit); `gitleaks git . -c .gitleaks.toml --redact` (history, CI) | edit, CI | A leaked key is unrecoverable once pushed; always `--redact` |
| Coverage | Changed backend lines ≥ 80% covered | `npm run check:diff-coverage` (diff-cover on JaCoCo XML) | task (soft until 2026-11-30) | High enough to force a test, low enough to allow a config line |
| Architecture | No controller→repository, repository→service/controller/dto, entity→service/controller/dto; no *new* service→controller dependency or domain cycle | `npm run check:arch` (ArchUnit, `backend/src/test/java/eu/dtc4eu/ArchitectureTest.java`) | task (soft until 2026-11-30) | CLAUDE.md's per-domain layering: services own scope checks and auditing |
| Security: code | Nothing at high or above (incl. Semgrep `ERROR`) | `npm run check:security` (Semgrep `p/default` + `scripts/quality-gate.mjs`) | task (soft until 2026-11-30) | External rules; below high is mostly noise |
| Security: deps | Nothing at high or above (CVSS ≥ 7.0, unscored counts as high) beyond the E3 baseline | `npm run check:deps` (osv-scanner + `scripts/quality-gate.mjs osv`) | CI | Public advisory database; the app holds clinical data |
| Bundle size | No bundle grows > 0.5% over the Measured value | `npm run check:bundles` (size-limit + `scripts/ratchet.mjs`) | CI (soft until 2026-11-30) | Builds take minutes here, so not in the task loop |

At least three of these are external opinions the agent cannot argue with:
gitleaks, Semgrep and osv-scanner.

Not enforced, by decision: accessibility and Lighthouse performance (they need a
running URL; revisit with a preview deploy) and ESLint (neither frontend has a
linter configured yet).

## Measured, not yet enforced

Ratchets: today's value is the floor (or ceiling); tolerance 0.5 (percentage points
for coverage, percent growth for bytes). Checked by `node scripts/ratchet.mjs
<backend|frontend|website>`, which reads this table. When a value improves, record
the new value here in the same change.

| Metric | Today | Direction | Source |
|--------|-------|-----------|--------|
| Backend line coverage | 78.04% | must not fall | `backend/target/site/jacoco/jacoco.xml` |
| Backend branch coverage | 61.86% | must not fall | same |
| Bundle: platform JS (gzip) | 163556 bytes | must not grow | `frontend/.size-limit.json` |
| Bundle: public site JS (gzip) | 137238 bytes | must not grow | `website/frontend/.size-limit.json` |
| Bundle: CMS admin JS (gzip) | 118183 bytes | must not grow | `website/frontend/.size-limit.json` |
| Known high/critical dependency advisories | 86 | must not grow | `osv-scanner.toml` (E3) |

## Exceptions

| ID | Rule | Path | Reason | Owner | Expires |
|----|------|------|--------|-------|---------|
| E1 | Floor: skipped tests | `deploy/*.Dockerfile` (`.constraintsignore`) | Image builds use `-DskipTests`; the suite runs in `check:full` before deploy | @miloslalk | 2027-02-14 |
| E2 | Floor: suppressions | `scripts/floor-guard.mjs` (`.constraintsignore`) | The guard's pattern lists contain every token it looks for | @miloslalk | 2027-02-14 |
| E3 | Security: deps | `osv-scanner.toml` (86 advisories, 13 critical) | Adoption baseline. Fix: Spring Boot 3.5.3 → 3.5.16 in both backends, then axios/vite/zod/react-router/markdown-it/… in both frontends — one dependency per change | @miloslalk | 2026-12-16 |
| E4 | Security: code | `backend/…/V2__seed_roles.sql`, `website/backend/…/V2__seed_placeholder_content.sql` | bcrypt hashes of the documented dev admin passwords; Flyway checksums forbid editing applied migrations. Mitigation: first-boot password change (DEPLOY.md §4) — re-verify on production before expiry | @miloslalk | 2027-02-14 |
| E5 | Architecture | `archunit_store` (2 service→controller, 10 domain cycles) | Frozen at adoption. Quick win: move `UserAdminController.CoordinatorOption` to `user/dto` | @miloslalk | 2027-02-14 |
| E6 | Secrets | `.gitleaks.toml` allowlist | Profile-schema field keys (`"key": "a6_…"`) match generic-api-key; allowlist targets only that match shape | @miloslalk | 2027-11-16 |
