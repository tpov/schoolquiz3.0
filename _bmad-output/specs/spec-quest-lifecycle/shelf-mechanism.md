# Decision: how a published quest moves between publication shelves

Written for the product owner and for the engineer who will implement this. All code claims below were re-verified in the working tree at `/Users/tpov/schoolquiz3.0` on 2026-08-31.

---

# 1. How legacy did it

**The shelf was not a field. It was a folder name.** A quest lived in `structures/structureData/{EVENT_NAME}`, where `EVENT_NAME` was one of 8 `EventQuiz` enum values (`legacy/common/src/main/java/com/tpov/common/domain/model/EventQuiz.kt:4-12`, read at `legacy/common/src/main/java/com/tpov/common/data/RepositoryStructureImpl.kt:133-135`). The synced quest document had no shelf field at all (`legacy/common/src/main/java/com/tpov/common/data/model/remote/StructureDataRemote.kt:8-29`).

**A move was therefore a transfer, and an expensive one.** Copy the collection, then delete the source (`legacy/archive/functions/src/index.ts:38-86`, helpers at `:516-537`). The shelf name was also baked into four other Firestore roots, into every Firebase Storage image path (`legacy/common/src/main/java/com/tpov/common/data/RepositoryQuestionImpl.kt:95`), and into the local Room key on every device (`legacy/common/src/main/java/com/tpov/common/data/model/entity/QuestionEntity.kt:37-38`). Player progress was shelf-scoped too (`legacy/common/src/main/java/com/tpov/common/data/RepositoryQuestionDetailImpl.kt:45-49`), so a move would have orphaned every player's results.

**It was designed as manual.** One menu item, one paid confirmation dialog for 200 nolics (`legacy/app/src/main/res/menu/popup_menu.xml:9-11`, `legacy/common/src/main/java/com/tpov/common/presentation/quiz/QuizActivityAdapter.kt:107-112`).

**Automatic promotion never existed.** The threshold constant `RATING_QUIZ_ARENA_IN_TOP = 250` is declared once at `legacy/common/src/main/java/com/tpov/common/Consts.kt:13` and never read anywhere. Legacy collected no quest ratings at all: the rating UI (`legacy/common/src/main/java/com/tpov/common/presentation/custom/CustomProgressBar.kt:61-113`) and the rating upload (`RepositoryStructureImpl.kt:51-86`) both have zero call sites.

**Nothing worked end to end.** The popup menu was never shown, the callback body was empty (`legacy/common/src/main/java/com/tpov/common/presentation/quiz/QuizFragment.kt:174-176`), the move-builder had no callers (`legacy/common/src/main/java/com/tpov/common/domain/utils/StructureDataUtils.kt:146-163`), and the server mover was later repurposed into a gift-box reward generator to avoid billing a new function (`legacy/archive/functions/src/index.v1.ts:17-19`). The only shelf that ever worked was `QUIZ_HOME`, filled by hand with an admin script (`legacy/archive/upload-data.js:22-28`).

**Conclusion: legacy agrees with the current code and with the product owner. It disagrees with ADR-0005.** There is nothing to "restore".

---

# 2. How the industry does it

**Pattern 1 — promotion is additive, not a transfer (6 of 8 platforms studied).** Roblox home sorts (https://create.roblox.com/docs/discovery), Mario Maker Course World, Steam Workshop plus official inclusion (https://wiki.teamfortress.com/wiki/Custom_maps), Kahoot verification (https://kahoot.com/community/verified-creator/), Wordwall Community, GeoGuessr. The item stays in the general pool and gains a surface.

**Pattern 2 — where a curated surface exists, the gate is on the person, and the author can never grant it to themselves.** Valve moderation queue (https://help.steampowered.com/en/wizard/HelpWithUGCSubmission/); Minecraft Partner Program — business entity, portfolio, 2-4 week review (https://www.minecraft.net/en-us/partner); Kahoot verified creator — 5 kahoots + 300 participants + editorial review.

**Pattern 3 — human curation appears where money changes hands. Free content gets algorithmic sorts and cheap automated gates.** Mario Maker's whole pre-publish gate is "clear your own level first" (https://attackofthefanboy.com/guides/super-mario-maker-2-how-to-upload-levels/). No free-content platform in this set pays for per-item human review.

**Pattern 4 — every public metric that drives placement gets attacked.** Roblox retroactively rolls back botted votes (https://devforum.roblox.com/t/actioning-against-bot-accounts-on-roblox/4820656). Nintendo banned "★" in course titles. Valve killed Steam Greenlight in June 2017 specifically because up-vote-driven publishing was farmed (https://kotaku.com/valve-kills-steam-greenlight-1792225494) and replaced it with identity verification and a fee.

**Pattern 5 — the punishment is reduced visibility, not deletion.** Meta's "remove, reduce, inform" (https://transparency.meta.com/enforcement/reducing-content-instead-of-removing/). The one platform that chose automatic deletion — Nintendo deleting low-star courses on an unpublished rule — spent a year answering angry creators (https://www.dualshockers.com/nintendo-explains-why-super-mario-maker-courses-are-disappearing/).

**Pattern 6 — approval is pinned to a version, not to the object.** Wikipedia pending changes serves the last accepted revision (https://en.wikipedia.org/wiki/Wikipedia:Pending_changes). Steam re-reviews every update and keeps serving the previously approved version meanwhile. Chrome Web Store re-reviews every update (https://developer.chrome.com/docs/webstore/update).

**Pattern 7 — the closest analogue to us, Duolingo Incubator, promoted on a complaint rate (fewer than 3 error reports per 100 users), never on an average rating** (https://duolingo.fandom.com/wiki/Frequently_asked_questions/Incubator). It was shut down in March 2021 once courses started making money (https://blog.duolingo.com/ending-honoring-our-volunteer-contributor-program-2/).

**Which pattern is the default at our stage:** a human picks, by hand, from a small pool, with an audit record. That is Kahoot before verification, Steam before Greenlight, Mario Maker's Course World before it had content. No small product starts with automation.

**Which pattern is the default at scale:** algorithmic sorts over one pool (Roblox, Course World), with human curation reserved for one row, and with a full anti-manipulation apparatus (anomaly detection, human investigation, retroactive rollback) as a permanent cost.

---

# 3. What our code can and cannot do today

**The mechanism exists and works end to end.**
- Publication: regular quest → `["arena"]`, course → `["archive"]` (`functions/index.js:2286`).
- Move: `setPublicQuestShelf` replaces `visibleOn` with exactly `[targetShelf]` in a transaction and writes one sync marker (`functions/index.js:1344-1389`, write at `:1370-1382`).
- Clients learn about it through `catalogs/{catalogId}/sync_changes` — this is the only sync path that actually runs.
- UI already exists: shelf menu (`android/feature/quizzes-screen/.../screen/QuestListScreen.kt:85-91, :233-240`), gate (`android/feature/app-shell/.../ui/AppShellScreen.kt:204`), callable wiring (`platform/firebase/.../quest/FirebaseQuestRemoteDataSource.kt:52-68`).

**The brief was wrong on one point, and it changes the decision. `developerLevel` IS granted automatically, by quest ratings.**
- `QUEST_RATING_QUALIFICATION_PROFILE_FIELD = "developerLevel"` (`functions/index.js:89`), user field `"developer"` (`:88`).
- Delta per rating = `(rating - 2) * 10` (`functions/index.js:1968-1970`, constant `:90`); ratings are 1..3.
- The aggregator writes that total into `users/{uid}` and `profiles/{uid}` (`functions/index.js:2040-2053`).
- The gate is `developerLevel <= 100 → throw` (`functions/index.js:1347`, constant `:76`).
- **Eleven 3-star ratings from eleven other accounts make an author a curator of everyone's quests.** Rating needs only auth plus an existing profile document (`functions/index.js:440-441`), and the app starts every player anonymous, so those accounts are free.

**That same number opens other users' personal data.**
- `isVerifier()` and `isReviewer()` both accept `developerLevel > 100` (`firestore.rules:186-193`, `:195-204`).
- `isVerifier` opens `verification_requests/{anyUser}` — real name, birthday, city, Telegram handle (`functions/index.js:601-607`). `isReviewer` opens `private/{anyUser}/**` and `admin/**`.
- Good news: `isAdmin()` does **not** read `developerLevel` (`firestore.rules:171-183`), so a farmed account cannot rewrite other people's questions.
- Bad news: `canSubmit` does — `if (profile.developerLevel > DEVELOPER_ALL_ACCESS_LEVEL) return isStageOpenForSubmit(...)` (`functions/index.js:2427-2429`). A farmed account can approve review stages on anyone's lesson.

**The client can write the shelf directly — and can also create a Home-shelf quest from nothing.**
- Update allowlist includes `visibleOn`, `archived`, `catalogId` (`firestore.rules:112-116`). Only key names are checked, never values.
- **Create allowlist also includes `visibleOn`, `title`, `description`, `picturePath`, `lastModifiedAt` (`firestore.rules:99-109`).** Any signed-in user can create `quests/<new-id>` with `visibleOn: ["home"]` and any text and image. The client then pulls it in through `whereArrayContainsAny("visibleOn", …)` + `lastModifiedAt` range (`platform/firebase/.../quest/FirebaseQuestRemoteDataSource.kt:43-45`). **None of the three proposals noticed this.** Nested questions cannot be written this way (`firestore.rules:137-140` is admin-only), so the payload is the card text and picture — still on a children's home screen.
- Verified safe to close both: the app never writes `/quests`. `FirebaseQuestRemoteDataSource.kt` only reads (`:17, :27, :43`) and calls the callable (`:52`). Authored content goes to `private/{uid}/catalogs/{c}/quests/{q}` (`platform/firebase/.../quest_authoring/FirebaseQuestPrivateRemoteDataSource.kt:42-47`).

**Correcting a false claim that two of the three proposals put in front of the owner.** A promoted quest does **not** stop collecting ratings. The rating key is `hashHex(scope, ownerUid, catalogId, questId)` and deliberately excludes the shelf (`functions/index.js:4021-4023`), and the rating prompt has no shelf condition (`shared/feature/lesson-runner/domain/.../CompleteAttemptUseCase.kt:51-52`). Do not trade anything away to "keep collecting ratings".

**But ratings are scarce for a different reason.** The prompt only appears after a **perfect** run: `state.codeAnswer.allShownAnswersAre9` (`CompleteAttemptUseCase.kt:51`). And the server stores one rating document per user per quest (`functions/index.js:459-464`). "20 ratings" means 20 distinct users who perfect-scored that quest.

**The rating numbers themselves are weak.**
- No minimum sample: one 3-star rating gives `averageRating = 3.0` (`functions/index.js:1854`). The Arena list sorts on that raw mean (`android/feature/quizzes-screen/.../DefaultQuestListComponent.kt:213-219`).
- The author's own star is skipped when computing the author's points (`functions/index.js:1961-1962`) but counted in the public average 100 lines earlier (`functions/index.js:1846-1854`). This asymmetry is clearly unintentional.
- Nothing checks that a rater played anything.

**Other facts that constrain the decision.**
- The move is one-way out of archive: `PUBLIC_QUEST_SHELVES = {"home","arena","tournament","tournamentFinal"}` (`functions/index.js:117`) and `publicQuestShelfValue` rejects anything else (`:4065`). A course moved off the archive can never go back.
- An author's republish silently resets the shelf: `visibleOn: [request.targetShelf]` (`functions/index.js:2286`), and `targetShelfValue` can only return `"arena"` or `"archive"` (`:4060-4063`).
- No audit at all: `setPublicQuestShelf` writes only `visibleOn`, `version`, `lastModifiedAt`.
- **No composite index in the repository.** `firebase.json` declares only `"rules"` under `firestore` — there is no `"indexes"` key and no `firestore.indexes.json`. The indexes the public-shelf query needs exist only because someone once ran `scripts/create-indexes.js`, which hardcodes a service-account path at `/home/tpov/Downloads/...` (`scripts/create-indexes.js:3`).
- Every Cloud Function shares `maxInstances: 1` (`functions/index.js:70-74`).
- Additive shelves would break tournament scoring: the lesson result picks one shelf by priority arena > home > archive (`shared/feature/lesson-runner/data/.../outbox/LessonResultOutboxWriter.kt:113-120`), and a tournament group only forms when that value is a tournament shelf (`functions/tournament-ranking.js:288-296`). A quest on `{arena, tournament}` would score in no tournament.
- No report and no block anywhere in `functions/index.js` or `firestore.rules`. Google Play requires both for an app hosting user-generated content (https://support.google.com/googleplay/android-developer/answer/9876937). This is a separate release blocker.

---

# 4. The three options

| Option | Mechanism in one line | Cost (days) | Who is the bottleneck | What it fixes | What it leaves broken |
|---|---|---|---|---|---|
| **A — Locked Transfer** | Keep today's manual transfer; make `developerLevel` grantable only by an admin, make the server the only writer of `visibleOn`, record every move | 5 (proposed) — **8 with the required additions below** | The owner (one person taps for every move) | The accidental rating→curator grant; the author-writable `visibleOn`; no audit; the one-way archive trap; keeps the safest edit-after-approval rule | Composite index still untracked; `isVerifier`/`isReviewer` still keyed on `developerLevel`, so every new curator can read other users' identity documents; a curator can promote their own quest; create-rule hole |
| **B — Placement Ledger** | Same as A, plus a typed `QuestShelf` enum across 5 modules, an append-only move ledger, retire/restore, and "hold the placement when the author republishes" | 12 (optimistic) | The owner, same as A | Everything A fixes, plus: separates the curator role from identity-document access, refuses self-promotion, commits `firestore.indexes.json`, stops the Home shelf emptying itself on every author edit | Adds a client crash vector (`require(visibleOn.size <= 1)` on data received from the network); keeps a republished, unreviewed quest visible on Home; the enum refactor touches 35 Kotlin files during release weeks; its farmed-account repair needs a judgement nobody can make |
| **C — Nomination Queue** | Nightly job scores arena quests, marks the best "nominated"; a curator confirms with one tap; after 14 days the best nominee goes to Home automatically | 15 (claimed) — realistically a month | Nobody, by design — and that is the problem | Correctly diagnoses the rating substrate: no minimum sample, self-rating counted, anonymous raters | Cannot fire at our scale (needs 20 distinct **perfect-score** users per quest in 90 days); its "you must have played it" defence never binds the `lessonId` to the `questId` (`functions/index.js:4021-4023` hashes questId and ignores lessonId), so one completed lesson licenses an account to rate everything; ships an unattended path to the children's home screen in a build with no report/block; adds a Room v3→v4 migration for two columns nobody will read |

---

# 5. Recommendation

## Ship option A, with five additions taken from B and C, plus two holes none of the three proposals found. Budget 8 working days.

**Why A.** The mechanism the owner described already exists and already works. Every day spent on B's enum refactor or C's scoring engine is a day not spent making the existing path safe. A is also the only option whose failure mode is boring: if the founder does not tap, the Home shelf does not change. B and C both have failure modes where unreviewed content is visible on the home screen of a children's app.

**Why not C, stated plainly.** Automatic promotion has never existed in this product in either generation, the ADR that describes it was written before anyone checked the data, and the data cannot support it: a 1..3 scale rounded to one decimal has 21 possible values, ratings only appear after a perfect run, and one rating already produces a perfect 3.0. Valve deleted exactly this mechanism from Steam in 2017 after it was farmed (https://kotaku.com/valve-kills-steam-greenlight-1792225494).

### The work, in order. Steps 1-4 are release blockers.

**1. Close the client's write access to `/quests` completely.** (~0.5 day. Nobody proposed the create half.)
- `firestore.rules:112-116` — remove `'visibleOn'`, `'archived'`, `'catalogId'` from the update allowlist. Leave `title`, `description`, `picturePath`.
- `firestore.rules:99-109` — set `allow create: if false`. The app never creates documents in `/quests`; verified against `FirebaseQuestRemoteDataSource.kt` (read-only) and `FirebaseQuestPrivateRemoteDataSource.kt:42-47` (writes only under `private/{uid}/...`). Without this, any signed-in user can create a Home-shelf card with arbitrary text and picture, and every one of the three designs is decorative.
- Add the deny cases to `scripts/rules-emulator-test.js` — the harness already has actors and quest fixtures.

**2. Cut the automatic path from ratings to power.** (~2 days including the repair script.)
- `functions/index.js:88-89` — change both constants to `"authorRatingScore"`. Both the write (`:2040-2053`) and the read-back (`:2057-2065`) are driven by them, so this is a two-line edit.
- **Steal from B:** `firestore.rules:186-204` — remove `developerLevel > 100` from `isVerifier()` and `isReviewer()`. Without this, every curator you appoint from now on also gets to read every user's real name, birthday, city and Telegram handle. A's author explicitly left this alone; that is A's one real mistake.
- `functions/index.js:2427-2429` — `canSubmit` also grants blanket review power on `developerLevel`. Decide whether a curator should have it. Default: yes, keep it, since curators are now hand-picked.
- One-off script: zero `developer` / `developerLevel` on every `users/` and `profiles/` document (they were all farmed), set `adminLevel` for the first admin by hand, and **reset `qualificationAppliedScore` to 0 on every `quest_rating_aggregates` document** so the score re-accumulates into the new field. This last step is the easiest one to forget and the whole repair is wrong without it.

**3. Add the grant path.** (~1 day.) New callable `setUserQualification({uid, level})`, copied structurally from `decideVerification` (`functions/index.js:616-661`): admin-gated (not `requireVerifier`, which also accepts developers — a developer must not be able to mint developers), refuses to act on yourself (`:621-624`), writes `users/{uid}` and `profiles/{uid}` in one transaction using the field shape the client mapper expects (`platform/firebase/.../FirebaseUserStatsDataSource.kt:88`). The first admin is set once by hand in the Firestore console — that is normal.

**4. Commit the Firestore indexes.** (~1 day. **Steal from B and C.**) Add `firestore.indexes.json`, reference it from `firebase.json`, and carry the six indexes currently created only by `scripts/create-indexes.js`. Without this the curator taps, the server writes, and no phone ever shows the quest on Home — which is exactly the legacy failure mode where every layer existed and no two were connected.

**5. Audit and reversibility.** (~1.5 days.)
- Inside the existing transaction in `setPublicQuestShelf`, capture `visibleOn` before overwriting it and store `previousVisibleOn`, `shelfSetByUid`, `shelfSetAtMs`, `shelfReason` on the quest, plus one document under `admin/shelf_moves/{questId}_{ms}`. `admin/**` is already reviewer-read and client-unwritable.
- Write the same record when a republish resets the shelf (`functions/index.js:2280-2290`), with actor = author, reason = `"republish"`, so the one silent demotion in the system stops being silent.
- **Steal from B:** refuse a curator moving their own quest, copying the self-decision refusal at `functions/index.js:621-624`. Self-promotion by an appointed curator is the most likely insider abuse in a small team, and it costs three lines.
- Add `"archive"` to `PUBLIC_QUEST_SHELVES` (`functions/index.js:117`). One word, and every mistake becomes undoable through the menu that made it.

**6. Two cheap rating fixes.** (~1.5 days. **Steal from C.**)
- Apply the self-rating skip from `functions/index.js:1961-1962` to the public average loop at `:1846-1854`. One line, provably unintentional, and today an author can raise the stars players see on their own quest.
- Replace the Arena sort's raw mean (`android/feature/quizzes-screen/.../DefaultQuestListComponent.kt:213-219`) with a Bayesian weighted rating: `WR = (v/(v+m))*R + (m/(v+m))*C`, `m ≈ 20` (https://help.imdb.com/article/imdb/track-movies-tv/ratings-faq/G67Y87TFYYP6TWAV). Arena is the shelf that will actually have content at launch, and this is also how the curator finds the quests worth promoting. Without it the top of the Arena list is whichever quest got one 3-star rating — a useless shortlist.

**7. End-to-end test before the release build.** One real granted account, one real move, one real device that sees the quest appear on Home. Legacy's exact failure was six layers that each looked finished in isolation.

### Deliberately NOT doing

- **Not** making shelves additive. `LessonResultOutboxWriter.kt:113-120` plus `functions/tournament-ranking.js:288-296` mean a quest on `{arena, tournament}` scores in no tournament. This is a scoring bug, not a documentation change. (If the owner later wants "stays in Arena too" for the **home** shelf only, that is safe — the priority chain picks `arena` and no tournament is involved — and costs about 1 day. Never do it for tournament.)
- **Not** adopting B's `placementStale`. Keeping a republished, unconfirmed quest on Home is more dangerous here than elsewhere, because `publishSubmissionIfReady` only checks the lessons the client named in `targetLessonIds` (`functions/index.js:2085-2110`) and falls back to the client's own unreviewed questions for the rest (`:2117-2135`). Today's behaviour sends unreviewed content to Arena; B's would send it to Home. Keep the reset, and make it loud.
- **Not** building automatic promotion.

### Separate release blocker, not part of this decision

Google Play requires an in-app way to report and block both content and users before an app hosting user-generated content can ship (https://support.google.com/googleplay/android-developer/answer/9876937). A repository-wide search finds no report, block or abuse handler in `functions/index.js` or `firestore.rules`. This must be planned on its own track.

### When to revisit automation (the trigger for moving toward C)

Do not open this question again until **all four** are true:

1. At least 200 quests are published on `arena`, and the median published quest has **20 or more distinct raters within 90 days**. Measure it — do not estimate it. Remember that today only a perfect run produces a rating (`CompleteAttemptUseCase.kt:51`), so this probably requires changing when the rating prompt appears.
2. The rating scale question is settled. Widening 1..3 to something finer invalidates every stored rating, so it must happen **before** ratings drive anything, never after.
3. Report and block are shipped and someone is actually reading the reports.
4. A rating can be trusted: a non-anonymous account, and a **completed lesson that provably belongs to the quest being rated** — the server must load the lesson and check its `questId`, because the current payload lets the client send any `lessonId` with any `questId` (`functions/index.js:4021-4023`).

Even then, the first automation step should be **nomination only, never automatic publication**: mark the candidates, keep the human tap. Duolingo's threshold was a complaint rate, not an average rating (https://duolingo.fandom.com/wiki/Frequently_asked_questions/Incubator) — that is the better metric to copy when the time comes.

---

# 6. What the owner must decide — in plain language

**1. Who can move quests on launch day?**
*Default if you say nothing:* only you. Nobody else gets the power, and you add people later one by one.
*What changes:* if you name people now, we grant them in the same release. If the list stays empty and you forget to grant yourself, nobody can move anything and the feature looks broken.

**2. When a quest goes to Home, should it disappear from the Arena?**
*Default:* yes, it disappears. That is how it works today.
*What changes:* keeping it in both places is possible for Home and costs about one extra day. Note: an earlier draft told you that a quest leaving the Arena stops collecting ratings — **that is not true**, ratings keep working on every shelf. So there is no hidden cost to letting it disappear. Never do this for the Tournament shelf: a quest in two places would stop counting in the tournament results.

**3. An author edits a quest that you already put on Home. What should happen?**
*Default:* it drops back to the Arena, and you must choose it again. That is today's behaviour and it is the safest one.
*What changes:* the alternative is that it stays on Home with the new, unchecked text visible to everyone. We cannot show players the old approved version — the edit overwrites the questions. If you pick the default, expect the Home shelf to slowly empty as authors edit, and expect to re-place quests from time to time.

**4. Should a course that you moved out of the Archive be allowed back into the Archive?**
*Default:* yes. Today the move is one-way and your first mistake would be permanent.
*What changes:* nothing else. This is a one-word change.

**5. Do you need a "hide it from everywhere" button at launch, or is "send it back to the Arena" enough?**
*Default:* "send back to the Arena" only. Full hiding stays an operator script that a developer runs for you.
*What changes:* turning hiding into a real button costs about 2 extra days, because the quest's lessons and questions must be hidden too.

**6. Good ratings currently turn an author into a moderator by accident — and also let them read other users' real names and birthdays. We are cutting that link. Should a well-rated author still see something?**
*Default:* yes — the same number keeps growing on their profile, but it gives no powers at all.
*What changes:* if you say nothing, everyone's current "developer" number is reset to zero in this release, and testers will report that as a bug. Tell your testers before the deploy. If you want to name a few people who keep the power on purpose, give us the list now.

---

# 7. What ADR-0005 must say afterwards

File: `docs/architecture/0005-quest-lifecycle.md`. Change the status line to `Superseded in part — YYYY-MM-DD` and add a new amendment section at the end named **"Amendment — shelf is a single value, moved only by a hand-appointed curator"**. Then:

**DELETE outright**

- **Lines 78-91** — the whole section "Переходы между полками — автоматические по серверным константам", including the promotion table at `:85-88` (`ARENA → TOURNAMENT: топ-3 по средней оценке сезона`, `TOURNAMENT → TOURNAMENT_LEADER`, `TOURNAMENT_LEADER → HOME`) and the sentence at `:91` about server config values. None of this exists, none of it ever existed, and there is no season concept anywhere in the repository — the only time bucketing is a one-hour window for grouping tournament attempts (`functions/tournament-ranking.js:7`), and `seasonBoxes` in the economy feature is a referral counter.
- **Lines 246-255** — the whole subsection "Server rules автопромо", which restates the same automatic promotion as *additions* to `visibleOn`. Both halves are wrong: the promotion does not exist, and additive placement would break tournament scoring (`LessonResultOutboxWriter.kt:113-120` + `functions/tournament-ranking.js:288-296`).
- **Line 168** — the bullet "Промо между полками — на сервере. Клиент всегда тонкий." Replace as described below; as written it implies an automatic promo worker.
- **Line 300 (Notes)** — the references to `server/workers/review-collisions` and "отдельный worker автопромо (будет добавлен)". Neither exists. The server logic is `functions/index.js`.

**REWRITE**

- **Lines 198-229** ("Amendment 2026-04-21 — PublicationShelf как Set"). Keep the storage shape — `visibleOn` stays a Firestore `Array<String>`, because it is the read gate (`firestore.rules:93`) and the sync query key (`FirebaseQuestRemoteDataSource.kt:43-45`). Change the semantics: **a published quest holds exactly one element**; an empty array means retired and every client deletes it locally. Delete the three justifications at lines 206-210 ("виден и на HOME и в ARENA", promotion campaigns, tester mode) — they are wishes, not implemented behaviour, and record next to them the two reasons the additive set was rejected, with file references, so nobody re-derives it later as a free change.
- **Line 237** — the invariant `QuestType = COURSE ⇒ visibleOn ⊆ {"home", "archive"}`. The code publishes a course to `["archive"]` (`functions/index.js:2286`) and, after the recommended one-word change, allows a curator to move it to any of `home / arena / tournament / tournamentFinal / archive`. State the real allowed set and say which destinations are legitimate for a course (owner question 6 in the proposals; if unanswered, write `{"archive", "home"}` and make the callable enforce it).
- **Line 178, rule 1** ("Клиент не вычисляет правила промо между полками. Только сервер."). Strengthen it: *the client neither computes nor writes placement. `visibleOn` is server-written only. Any future feature that wants to unpublish or re-categorise from the app must go through a callable, not through a rules allowlist.* Add the reason: the author-writable allowlist at `firestore.rules:112-116` and the create allowlist at `:99-109` were both open before this change.
- **Line 168** — replace with: *the server is the only writer of the shelf; the client is thin because it only reads placement, not because promotion is automatic.*

**ADD (new content the ADR does not have)**

- **The transition table as it really is.** Publication: REGULAR → `["arena"]`, COURSE → `["archive"]` (`functions/index.js:2286`). Move: any single public shelf → any other, including back to `archive`, by `setPublicQuestShelf` only. Retire: `visibleOn = []`, today only through `scripts/seed-bulk/retire-quest.js`.
- **Who may move.** A `developerLevel > 100` that only an admin can grant, through `setUserQualification`. State explicitly that quest ratings no longer write this field — they write `authorRatingScore`, which grants nothing — and record the date of the repair script, because that is the fact a future reader will most need.
- **A curator may not move their own quest** (mirrors `decideVerification` at `functions/index.js:621-624`).
- **Audit contract.** Every move writes `previousVisibleOn`, `shelfSetByUid`, `shelfSetAtMs`, `shelfReason` on the quest and one record under `admin/shelf_moves/`, including the author-triggered republish reset.
- **Edit after publication.** State the rule chosen in owner question 3: a republish resets a curated quest to `arena` and it must be chosen again. Name the two fields that would allow a Wikipedia-style "approved version" model later — `version` and `contentsVersion`, both already written at `functions/index.js:2289-2290` — and note that the strongest form is not available while `writePublicHierarchyToBatch` (`functions/index.js:2269-2273`) overwrites nested content in place.
- **Why not automatic promotion**, in three lines with evidence, so this does not get re-proposed every quarter: legacy declared `RATING_QUIZ_ARENA_IN_TOP = 250` and never read it (`legacy/common/src/main/java/com/tpov/common/Consts.kt:13`); the current rating data has no minimum sample and counts the author's own star (`functions/index.js:1846-1854`); and the four trigger conditions in section 5 above must all be true before the question is reopened.

**SURVIVES UNCHANGED**

- The three-axis decision (`QuestType` / `QuestPhase` / `PublicationShelf`), lines 19-36 — this is the ADR's real contribution and it is correct.
- The parallel-review model, lines 38-64 — implemented, matching `functions/index.js` review records.
- The phase transitions at lines 66-76.
- Revisions, `QuizSessionMode` and `CompletionEffect`, lines 93-151.
- Invariants 5, 6, 7 (lines 159-161); invariants 1-4 (lines 155-158) survive only after the wording fix at line 237.
- The legacy mapping table, lines 184-196 — accurate, and worth keeping as the record of where the shelf names came from.