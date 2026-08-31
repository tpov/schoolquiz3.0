---
id: SPEC-quest-lifecycle
companions:
  - brownfield.md
  - shelf-mechanism.md
  - ../../../docs/architecture/0005-quest-lifecycle.md
  - ../../../docs/architecture/0006-roles-and-qualifications.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate.

# The quest lifecycle, made safe for release

## Why

The circle the product owner describes is built and it works: an author writes a quest, reviewers score it, it publishes to the arena, players play and rate it, and a developer moves it onto the home shelf or into a tournament, where it leaves the arena behind. Every step of that was traced to a live call site (`brownfield.md` §1).

What is missing is not the mechanism. It is the guard around it. Three doors stand open at once, and they compose:

1. Privilege is **earned by ratings**, not granted. Eleven 3-star ratings from eleven accounts write `profiles/{uid}.developerLevel = 110` (`functions/index.js:1968-1970`, `:2040-2053`), crossing every `> 100` gate in the product. Anonymous sign-in makes those accounts free (`AppApplication.kt:102`).
2. Review is **attendance, not judgement**. `lessonPassed` asks only whether a score exists, never what it is (`functions/index.js:2569-2583`, `:3468-3474`); a 1-out-of-3 publishes, and no reject path exists at all (`:2565-2567`).
3. Nobody is stopped from reviewing **their own** quest — `canSubmit` never compares the reviewer's uid to `task.ownerUid` (`functions/index.js:2427-2455`).

Chained, they form an unattended path from a free account to the home screen of an app used by schoolchildren, with no second human anywhere in it. A fourth door — `firestore.rules` letting any signed-in user create `quests/{id}` with `visibleOn: ["home"]` (`firestore.rules:99-109`) — reaches the same place in one write.

This is a mandate, not an opportunity: the app cannot ship in this state, and Google Play separately refuses user-generated content without report and block. The work is small and additive. Nothing here asks for a rewrite.

## Capabilities

- **CAP-1 — One publication target per quest type.**
  - **intent:** Publication puts a REGULAR or SURVEY quest on the arena and a COURSE in the archive, and nowhere else; no other shelf is reachable at publication.
  - **success:** A published REGULAR quest holds `visibleOn == ["arena"]`; a published COURSE holds `["archive"]` and `archived == true`. No submission path can name any other destination.

- **CAP-2 — Review is a judgement, with a threshold.**
  - **intent:** A quest publishes only when its testing and logic scores clear a pass mark; a score below it does not publish anything.
  - **success:** A lesson scored at the lowest value on either axis stays unpublished indefinitely, including across the nightly reconcile job. Raising that score to the pass mark publishes it without any other action.

- **CAP-3 — Review can reject, and the author is told why.**
  - **intent:** A reviewer can refuse a quest with a reason, which returns it to the author as an editable draft carrying that reason.
  - **success:** A rejected quest appears in the author's drafts with the reviewer's reason visible, is editable, and can be resubmitted. It is absent from every public shelf throughout.

- **CAP-4 — Nobody approves their own quest, except a developer.**
  - **intent:** An author is refused every review action on their own quest; a user holding the developer qualification is exempt and may approve their own.
  - **success:** An ordinary author sees no review task for their own quest and is refused server-side if they call the action directly. The same call from a developer on their own quest succeeds.

- **CAP-5 — A curator moves a published quest, and it leaves the shelf it came from.**
  - **intent:** A user holding the developer qualification moves a published quest to another shelf; the quest appears on the destination and disappears from the origin, on every device.
  - **success:** After a move from arena to home, a second device that had the quest in its arena list no longer shows it there and shows it on home, without reinstalling.

- **CAP-6 — The shelf is server-owned.**
  - **intent:** Only the server writes a quest's placement; no client can create a quest document or alter where an existing one appears.
  - **success:** A direct SDK write that creates `quests/{id}` with any shelf is refused, and so is an update that changes `visibleOn`, `archived` or `catalogId` on a quest the caller authored. A rules test asserts both.

- **CAP-7 — Privilege is granted by a person, never earned by ratings.**
  - **intent:** An admin grants a qualification to a named person with a written reason, and the recipient is told; ratings feed a separate author-reputation number that confers nothing.
  - **success:** No volume of ratings from any number of accounts changes any privilege gate. Granting works from inside the app for every role the review pipeline needs — tester, admin, translator, developer — refuses to act on the caller themselves, and records the reason. Existing farmed levels are zeroed in the same release.
  - **note:** This is the only way a reviewer can come to exist. Today `testerLevel`, `adminLevel`, `translatorLevel`, `moderatorLevel` and `sponsorLevel` have no award path anywhere in either generation — the aggregator is hardcoded to `developerLevel` (`functions/index.js:91`) and none of the 38 exported functions grants a level. Without CAP-7 the review workforce cannot be created except by hand in the Firestore console.

- **CAP-8 — Reputation stops opening other people's identity.**
  - **intent:** Reading another user's verification data or private documents requires an explicitly granted role, never the author-reputation number.
  - **success:** An account with any author-reputation value and no granted role is refused `verification_requests/{otherUser}` and `private/{otherUser}/**`.

- **CAP-9 — Content can be taken down from inside the app.**
  - **intent:** A curator removes a published quest from every shelf without a laptop, a script, or a service-account key, and the removal reaches players.
  - **success:** A retired quest vanishes from every device's lists after sync and from a fresh install, in any catalog — not only `courses`.

- **CAP-10 — A course can go back to the archive.**
  - **intent:** Any shelf move a curator can make is reversible, including returning a course to the archive.
  - **success:** A course moved from the archive to the arena can be moved back, and lands with the same placement and archived flag it had at publication.

- **CAP-11 — Every placement change is attributable.**
  - **intent:** Each move records who changed the placement, when, from what, and why — including the placement reset that an author's republish causes.
  - **success:** For any quest on any shelf, the previous placement and the actor who set it can be read back, and a republish that demotes a curated quest is visible in that record as an author-triggered event.

- **CAP-12 — Players can report content and block authors.**
  - **intent:** Any player can report a quest, a comment or a user from inside the app, and block an author so their content stops appearing.
  - **success:** A report is filed from the app and lands where a moderator can read it; a blocked author's content stops appearing in the blocking player's lists. Both are reachable within the store-review walkthrough.

- **CAP-13 — User-generated text can be removed.**
  - **intent:** A moderator can take down a comment, and the takedown reaches every device.
  - **success:** A comment removed by a moderator is gone for every reader after sync and on a fresh install. Today this is impossible for anyone: `lessonComments` is `allow update, delete: if false` and no server function touches the collection (`firestore.rules:142-157`).

- **CAP-14 — Every piece of user text has an owner.**
  - **intent:** Anything a user posts records who posted it, so it can be attributed, reported and blocked.
  - **success:** A comment carries its author's uid; blocking that author hides their comments; reporting one identifies a person a moderator can act on. Today a comment stores only a nickname and an avatar URL, so there is nobody to ban.

- **CAP-15 — A moderator decides a report, and the reporter is paid or charged for it.**
  - **intent:** A moderator rules on a report; an upheld report earns the reporter reputation, a rejected one costs them.
  - **success:** Filing a report changes nothing until it is decided. After an upheld decision the reporter's reputation is higher and after a rejected one it is lower, by the same amounts in both directions across repeated cases.

- **CAP-16 — A moderator's decisions are graded by those above them.**
  - **intent:** Moderators are themselves scored on how far their verdicts sat from the verdict of the senior moderators reviewing the same case.
  - **success:** A junior moderator matching the senior verdict gains, missing by one changes nothing, and missing by two loses — the same consensus rule already used for quest review, not a second implementation of it.

- **CAP-17 — Banning a moderator takes seniority.**
  - **intent:** Any moderator can ban an ordinary user; banning a moderator requires a moderator at least 100 levels above them, or a developer.
  - **success:** A moderator attempting to ban a peer at or near their own level is refused server-side; the same call from someone 100 levels above, or from a developer, succeeds. A banned user cannot post.

## Constraints

- A published quest holds **exactly one** shelf. Additive placement is refused, not deferred: `LessonResultOutboxWriter.kt:113-120` picks a single shelf by priority and `functions/tournament-ranking.js:288-296` forms a tournament group only from a tournament shelf, so a quest on `{arena, tournament}` would score in no tournament at all.
- Anonymous sign-in is free and unbounded, so **no privilege may depend on a count of accounts, ratings, or votes**.
- `profiles/{uid}` and `users/{uid}` stay client-unwritable (`firestore.rules:17`, `:50`); every level change goes through a callable.
- The app never writes `/quests` — verified across `FirebaseQuestRemoteDataSource.kt` (reads and one callable) and `FirebaseQuestPrivateRemoteDataSource.kt:42-47` (writes only under `private/{uid}/…`). Closing the collection to clients therefore costs no feature.
- Google Play refuses an app hosting user-generated content without in-app reporting and blocking. This gates submission independently of everything else here.
- The rating scale is 1..3 with no minimum sample, and one rating already yields a perfect average (`functions/index.js:1845-1854`). Nothing may be built on top of that number until it is widened, because widening invalidates every stored rating.
- Fixes ship before the device matrix runs. The owner chose this order.
- Moderation is designed against a chat shaped like the legacy one — `tpovId, time, user, msg, importance, icon, rating, reaction` (`legacy/…/ChatEntity.kt:8-19`) — but the chat itself stays a stub this release. Moderation therefore has to work on lesson comments today and attach to the chat unchanged when it lands; nothing in it may assume a message type that only the chat has.
- The consensus arithmetic is written once. `scoreAggregate` (`functions/index.js:2593-2610`) already implements exactly the senior-band rule the owner described — the reviewers within `ACTIVE_LEVEL_WINDOW = 100` of the top level set the verdict, and everyone below is paid +3 / 0 / −3 by distance from it. Moderation reuses that function; a second consensus implementation is refused.
- `moderatorLevel` becomes the right to decide reports and ban. It is inert today — no gate in `functions/` or `firestore.rules` reads it, and `requireProfile` does not even load it.

## Non-goals

- **Automatic promotion between shelves.** It never existed in either generation — legacy declared `RATING_QUIZ_ARENA_IN_TOP = 250` and never read it (`legacy/…/Consts.kt:13`) — and the rating substrate cannot support it. ADR-0005's promotion table is deleted, not implemented.
- **Typed `QuestPhase` / `PublicationShelf` enums across the modules.** The string literals are already consistent in all six places that name shelves; the refactor touches ~35 files and buys nothing this release.
- **Exam mode, certificates, and qualification offers.** All three are absent from production code and all three have their own specs. Out of scope here.
- **The chat itself.** Its data shape is fixed here so moderation can be built against it, and nothing more. No chat screen, no message delivery, no chat sync ships in this spec.
- **Widening the rating scale, and any Bayesian or minimum-sample ranking.** Deferred until ratings drive a decision.

## Success signal

On six real devices, one account authors a quest, two different accounts review it, it appears on the arena for a third, a developer moves it to the home shelf and it disappears from the arena on every device — and the four-step abuse chain is attempted and fails at every step: ratings no longer move any gate, a low score no longer publishes, a self-review is refused, and a direct SDK write to `quests` is denied.

## Assumptions

- "Домашние" in the owner's request means the HOME shelf, not school homework — consistent with ADR-0005 and the menu-refactor spec.
- Existing `developerLevel` values above 100 in the live project were all farmed by ratings rather than granted deliberately, so zeroing them is a repair and not a loss. To be confirmed by counting them before the migration runs.

## Open Questions

- Do report reputation and review reputation share one number, or are they two? Default: one `reviewReputation`, since both are earned the same way and the owner described them as one idea.
- Is a ban permanent, or does it expire? Default: permanent until lifted by someone who could have issued it, since nothing in the codebase has a scheduled expiry today.
- Who holds the developer qualification on launch day? Default: the owner alone, granted by hand once, then everyone else through CAP-7.
- What happens to comments already posted, which carry no author uid? Default: they stay readable and are reportable but not blockable, because there is nobody to attribute them to.
- When an author edits a quest a curator placed on the home shelf, does it drop back to the arena (today's behaviour, safest) or stay on home with unreviewed text? Default: drop back.
- Is "send back to the arena" enough at launch, or is a full hide-from-everywhere button needed? Default: CAP-9 covers the takedown; no separate hide.
