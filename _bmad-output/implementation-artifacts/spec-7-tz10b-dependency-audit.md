---
title: 'Epic 7 story 7.8 TZ10B dependency audit: current audit, minimal compatible updates, clean install, current Node/HTTP/Firebase/rules checks green'
type: 'maintenance'
created: '2026-10-08'
status: 'in-progress'
baseline_revision: 'night/e3-e8-inception @ 0d25ac8c'
context: ['_bmad-output/mvp-candidate/epic-commerce/tickets.toml (7.8)', '_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/USER-CONTINUATION-PLAN.md §3 TZ10B']
warnings: ['dependency-surface']
deferred: []
---

<intent-contract>

## Intent

**Problem:** TZ10B (plan §3) — dependency hygiene of the candidate's Node surface has never been
run as its own separated commit: `npm audit` shows 19 vulnerabilities in `functions/` (2 critical:
websocket-driver GHSA-mp7j-qc5w-4988 / GHSA-xv26-6w52-cph6 chain) and 18 in `scripts/` (1
critical, same family), and the installed trees predate several in-range releases
(firebase-admin 13.8.0 installed vs 13.10.0 in-range; firebase-functions 7.2.5 vs 7.4.0;
google-auth-library 10.6.2 vs 10.9.1). Nothing proves a clean install of the current manifests
resolves green checks.

**Approach:** Audit all three tracked Node packages (`functions/`, `scripts/`,
`noir-design-system/` — the last has no lockfile: `--package-lock-only` for audit coverage).
Baseline the current checks FIRST (functions `npm test` no-env on the current lockfile), then apply
MINIMAL COMPATIBLE updates only: caret-range refresh (`npm update` — lockfile moves within
existing `^` ranges) plus non-forced `npm audit fix` where it stays in-range. No major bumps
(firebase-admin 14, google-auth-library 11, googleapis 185, idb 8 stay out); vulnerabilities that
only clear across a major are recorded as residual external dependencies, not forced.
Clean install (`npm ci` from the refreshed lockfiles), then re-run the current check set.

**Check set («текущие Node/HTTP/Firebase/rules проверки», the runner registry of this tree):**

- `functions/` — `npm test` (`run-tests.js`, discovers every `*.test.js`; covers HTTP-callable
  contract suites and the rules/concurrency suites): once without emulator env, once against the
  live demo backend (`FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`, `GCLOUD_PROJECT=demo-schoolquiz-mvp`).
- `scripts/` — `npm test` (the committed chain incl. arena e2e readers and the legacy-settlement
  suite) and `npm run lint` (`node --check` over the production scripts).
- `rules-emulator-test.js` is NOT in this run: it needs `firebase emulators:exec` (firebase-tools
  + JVM on the host), which is not part of the current runner registry on this VPS; recorded as
  NOT RUN with that reason, consistent with the story's «текущие … проверки».

## Boundaries & Constraints

**Always:**
- The commit is dependency-only: `package.json`/`package-lock.json` (and the new noir lockfile)
  and nothing else from the product tree; the story lands as its own separate commit (AC).
- Baseline checks run before any dependency change; post-change checks run after a real clean
  install (`rm -rf node_modules && npm ci`), not an incremental `npm update` in place.
- Demo backend hygiene after the backend-mode gate (probe keys/profiles check, as 2.8/2.13 did).
- Evidence under `docs/agents/evidence/2026-10-08-tz10b-audit/` (audit before/after, outdated,
  update diff summary, all check logs).
- Wide-gate red families (owner decision batch 2 item 3) are attributed, not chased.

**Never:**
- No major-version bumps, no `npm audit fix --force`, no dependency removals, no
  `engines`/Node-version changes (functions stays `node 22`; the VPS runs node 24 — the existing
  accepted state, recorded in 2.13's gate logs).
- No changes to financial logic, rules files, or any test file. If a check is red after the
  refresh, the dependency change is rolled back to the lockfile that was green at baseline and the
  residual is recorded — a red check never ships inside the dependency commit.
- No touching owner files (`.maestro/**`, `local.properties`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Baseline green | Current lockfiles (functions 92/92 per 2.13) | Baseline log recorded | If baseline red: attribute per wide-gate families, fix nothing here, story stays open |
| In-range update breaks a check | Refreshed lockfile, red suite | Roll back lockfile to baseline state, record the specific regression | Dependency commit never lands red |
| Vulnerability only clears via major | `npm audit fix` non-force leaves it | Residual recorded with advisory links | Not forced, not worked around in code |
| scripts/ has no node_modules | `npm outdated` shows MISSING current | `npm ci` (after lockfile refresh) is the clean install | Recorded as found |
| noir/ has no lockfile | audit ENOLOCK | `npm i --package-lock-only`, audit, record | Lockfile added (dependency-only file) |
| Demo backend down | docker ps shows container stopped | `docker start` per owner's standing 06.10 decision | Backend-mode gate waits for healthy 18090 |

## Done When

1. Audit + outdated recorded for all three packages, before and after.
2. Minimal compatible updates applied (functions manifest may move only within existing `^`
   ranges; scripts/noir manifests unchanged unless an in-range pin correction is required — not
   expected); clean install performed from the committed lockfiles.
3. Check set green after the refresh: functions 92/92 (no-env) + 92/92 (demo-backend),
   scripts `npm test` + `npm run lint` green; any pre-existing red attributed to the known
   families, none introduced by the refresh.
4. Evidence complete; single dependency-only commit; statuses updated (7.8 → done in
   epic-commerce/tickets.toml).

</intent-contract>
