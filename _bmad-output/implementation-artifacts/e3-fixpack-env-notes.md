# Environment notes — e3 retro fixpack implementation run (2026-10-25)

Operational rules for the implementer of `spec-e3-retro-fixpack.md`. The WHAT is the spec; this file is only HOW to work in this repo today.

## Two views of one storage

- Mac SMB mount: `/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0` — use the Read/Edit/Write tools on these paths for file contents only.
- VPS itself: `/workspace/projects/schoolquiz3.0` via `ssh rnsrv` (root, key auth, no password).
- NEVER run git or gradle on the Mac through the mount — SMB wedges git (hangs, leaves `index.lock`). All git, gradle, node, docker commands go through `ssh rnsrv`.
- Searching: `ssh rnsrv "cd /workspace/projects/schoolquiz3.0 && grep -rn ..."` (rg available on the VPS). The mount is slow for scans.

## Concurrent sessions — hard rules

At least three OTHER unattended agents share this checkout right now:
- epic-7 economy/monetisation retro: dirty files under `android/feature/economy/`, `platform/billing/`, `shared/feature/economy/`
- epic-5 retro fixpack: its own untracked spec in `_bmad-output/implementation-artifacts/`
- epic-1-rig F1-F3: already committed its work

Therefore:
- NEVER `git add -A`, `git add .`, bare `git commit` (always path-scoped), `git reset --hard`, `git checkout -- <path>`, `git stash`, or anything touching files outside YOUR spec's file list.
- Stage/commit ONLY the exact paths your spec tasks touched: `git add <path>... && git commit -m "..."`.
- Other sessions' dirty files and untracked specs must survive your run untouched.
- If `git status` shows strange MASS staged deletions (thousands of `D ` entries for files that exist on disk): do NOT commit, do NOT reset. Wait 2-3 minutes, re-check `ssh rnsrv "cd /workspace/projects/schoolquiz3.0 && git status --porcelain | wc -l"`, proceed only when the anomaly clears (it self-heals; happened once today).

## Builds

- All gradle via the serial wrapper on the VPS: `ssh rnsrv "cd /workspace/projects/schoolquiz3.0 && sgw ./gradlew <tasks>"`. sgw = global flock (one heavy build VPS-wide; other sessions queue too; waits up to 2h are normal).
- Gradle wrapper 9.8.0 is current — do not touch it. Never raise memory limits.

## Git conventions

- Branch: `night/e7-commerce`. One commit per retro item: `fix(arena): retro item N — <imperative summary>` + detailed body, matching recent history style.
- End of run: `git push origin night/e7-commerce`. On rejection: `git pull --rebase origin night/e7-commerce` then push (everything on the branch is committed by then; safe).
- AppleDouble junk (`._*`, `.DS_Store`) is gitignored — never commit it explicitly.

## Demo firebase emulator (spec item 4a)

- Check: `ssh rnsrv "docker ps --format '{{.Names}}'"` — expect `schoolquiz-demo-firebase-20261004` (ports 18090/19090).
- Run repair/verify node scripts from the VPS repo root with: `GCLOUD_PROJECT=demo-schoolquiz-mvp`, `FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`, `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090`, `GOOGLE_APPLICATION_CREDENTIALS` unset; firebase-admin resolves from `functions/node_modules`.
- If the container is NOT running: do NOT start it. Follow the spec fallback — commit the repair script + README command + note it in the report.

## Functions suite (spec item 5)

- Node on the VPS; the repo documents the runner (`node run-tests.js` in the functions emulator dir / `schoolquiz-functions-emu`). Keep one heavy thing at a time on the VPS.

## Foreign reds

If a gate fails in a module you did not touch AND its files are dirty from a sibling session: wait a few minutes, re-run; if still red and provably not yours, record it as environment-red with evidence and move on — YOUR modules must be green.

## Final report format

Per spec task: done/partial/blocked; commits (hash + first line); verification commands with outcomes; demo-container outcome; incomplete/risky items; and the ONLINE-O2 dev-default decision flagged for owner review.
