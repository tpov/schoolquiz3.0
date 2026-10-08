# TZ10B dependency audit (story 7.8)

- Result: DONE. Audit recorded for all three tracked Node packages, minimal compatible updates
  applied, clean install from the committed lockfiles, and the current check set green after the
  refresh: functions **92/92** (no emulator env) and **92/92** (against the live demo backend
  `FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`, `GCLOUD_PROJECT=demo-schoolquiz-mvp`), scripts
  `npm test` exit 0 and `npm run lint` exit 0. Dependency-only commit, separate from any
  financial/logic change (plan §3 TZ10B, AD-22).
- Date: 2026-10-08.
- Story: `epic-commerce` 7.8 `tz10b-dependency-audit` (covers: none — cross-cutting NFR-7).
- Spec: `_bmad-output/implementation-artifacts/spec-7-tz10b-dependency-audit.md`.

## Audit before → after (minimal compatible, in-range only)

| Package | Before | After | Notes |
| --- | --- | --- | --- |
| functions | 19 vulns (1 low, 9 mod, 7 high, **2 critical**) | 8 moderate | `npm update` + non-forced `npm audit fix`; firebase-admin 13.8.0→13.10.0, firebase-functions 7.2.5→7.4.0, google-auth-library 10.6.2→10.9.1; both critical websocket-driver advisories (GHSA-mp7j-qc5w-4988, GHSA-xv26-6w52-cph6) cleared in-range (websocket-driver 0.7.5) |
| scripts | 18 vulns (9 mod, 8 high, **1 critical**) | 13 (8 mod, 5 high) | manifest already at wanted versions; lockfile was stale — `npm audit fix` in-range: websocket-driver 0.7.4→0.7.5 (critical cleared), nested uuid@9 copies deduped to a single shared 9.0.1 |
| noir-design-system | no lockfile (audit ENOLOCK) | 0 vulnerabilities | `npm install --package-lock-only`; lockfile added (audit reproducibility; no runtime surface) |

## Residual vulnerabilities (recorded, NOT forced — major-only)

- `uuid < 11.1.1` (GHSA-w5hq-g745-h8pq, moderate) — clears only via firebase-admin **14**
  (functions chain: firestore/storage/gaxios/google-gax) or googleapis **185** (scripts).
- `@grpc/grpc-js <= 1.13.5` (high) — pinned by the current firebase-admin/grpc resolution range.
- Majors (firebase-admin 14, google-auth-library 11, googleapis 185, idb 8) stay out per the
  spec's Never (no breaking bumps without an owner decision).

## Gates (after clean `rm -rf node_modules && npm ci` from the committed lockfiles)

- functions baseline BEFORE any change: `gate-functions-baseline-noenv.log` — 92/92 (current
  lockfile), confirming the starting state matched 2.13's record.
- functions after refresh: `gate-functions-noenv.log` — 92/92; `gate-functions-demo.log` — 92/92
  against the demo backend.
- scripts after refresh: `gate-scripts-test.log` (exit 0), `gate-scripts-lint.log` (exit 0).
- `rules-emulator-test.js` NOT RUN here: needs `firebase emulators:exec` (firebase-tools + host
  JVM), not part of the current runner registry on this VPS; the functions suites it protects run
  in both gate modes above. Recorded per the story's «текущие … проверки» boundary.
- Demo hygiene after the backend-mode gate (2.8/2.13 check): 23 `mutation_keys` intact,
  0 probe keys, `configs/economy` absent (untouched). Node on the VPS is v24.14.0 (functions
  `engines` stays node 22 — the accepted 2.13 state, unchanged).

## Scope discipline

- The dependency commit contains ONLY: `functions/package.json`, `functions/package-lock.json`,
  `scripts/package-lock.json`, `noir-design-system/package-lock.json` (new). No rules, no
  financial code, no tests touched. Spec/evidence/statuses ride a separate docs commit.
- Wide-gate note: none of the known red families (O6 JDK-27 env, fixture pins, detekt jvm-target,
  dex-merge heap) touches this Node surface; no new red introduced.
