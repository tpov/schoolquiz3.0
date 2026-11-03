# Initiative SPEC — PRD preservation notes

2026-09-29. Только notes; SPEC и memlogs не изменялись. Полностью прочитаны текущие canonical PRD (606 строк), addendum (212 строк), main architecture 09-29 (252 строки, final). Продуктовые факты не выведены из кода. Runtime/build/device/Play не запускались; работа и файл на VPS.

## Способ сохранения без раздувания kernel

Новый SPEC — общий initiative kernel принятого MVP, не новый PRD и не сокращение до «ещё не написанного». Можно дать 14 продуктовых capabilities + 1 capability единичной приёмки. ID ниже **предложены**, не назначены root как canonical; исходные FR-1–56/NFR-1–8 и qualified EXAM:CAP-1–21 остаются неизменны.

В самом kernel явно принять canonical PRD **и addendum как нормативные companions целиком**, включая каждое acceptance condition/число/failure matrix/non-goal, а не оставить их только в provenance `sources`. Технические решения берутся из final main architecture; exam SPEC/protocol — полноценный нормативный companion текущего scope. Предлагаемые относительные пути от будущего SPEC.md:

- `../../planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd.md`;
- `../../planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/addendum.md`;
- `../../planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md`;
- `../spec-theme-exams/SPEC.md`;
- `../spec-theme-exams/exam-protocol.md`.

Формулировка inheritance: «Этот kernel группирует полный accepted scope. Нормативные детали и обязательная матрица приняты из перечисленных companions; краткие Success не отменяют не повторённые в них условия. Явные актуальные решения владельца приоритетны; final main architecture применяет согласованные технические уточнения. Старые отозванные варианты и исторические evidence не действующий продуктовый контракт».

## Claim → предлагаемая capability-группа

| Группа | Claims / полный coverage | Ядро пользовательского результата и success, который нельзя потерять |
|---|---|---|
| G1 Обучение и помощь по опыту | FR-1–4,56; UJ-1 | Home/Archive→course→section→theme→lesson и полноценный сеанс; готовый local вопрос сразу/offline, реальная причина подготовки local/network/sync, конечная ошибка не spinner. Сохраняются типы, Continue/Info, ordinary time/pause/lifecycle/accessibility. Existing experience guide завершается; конкретные copy/thresholds/seen rules задаёт UX, не придуманный обязательный tutorial quest. |
| G2 Авторство, независимая проверка, публикация и куратор | FR-5–8,11–12; UJ-3 | Immutable submitted revision, provenance и независимость, три направления content/translation/logic. Каждый successful question публикуется независимо; failed new revision оставляет old approved. Confirmed defect curator снимает из новых выдач, frozen existing attempts/events сохраняются. Перепроверка всех affected dependencies/translations, unchanged checks retained. Online reviewer/server save/pause/resume и single assigned slot. Author/curator views bounded/authorized; placement per lesson и явное curator Arena→Tournament→Home, no automatic rating/season promotion. |
| G3 Оплата подачи и награда за проверку | FR-9–10; UJ-3 | Accepted new submission charged once; retry продолжает её. First full1000; partial100×N affected questions, no base addition/cap/language/stage surcharge. No review-fee refund in MVP, включая service failure/rejection. Reward personal level/10 per lesson, не question/stage; completed justified rejection paid. Completed cycle: fee=rewards+burn, nonnegative, excess proportional reduction, leftover burns; old retained evidence/replay не новая work. Pending OQ-2 и termination policy не заполняются defaults. |
| G4 Обратная связь и входящие | FR-13–15; UJ-4 | Две прямые кнопки Comments/Report error после урока/Арены. Existing discussion unchanged; private single free text по whole lesson без replay questions/answers или editing. Home «Личка» объединяет own incoming lesson context/types, сохраняя ACL исходного вида; не arbitrary DMs. |
| G5 Arena discovery, wallet, preparation и cancel | FR-16–23; UJ-2 | Настоящая shell навигация и catalog независимо от wallet/recovery; browsing zero financial effect. Global authoritative available/reserved/staleness, read no mutation. RANDOM server0 ноликов плюс charges; SELECTED explicit N=shown1–100, renewed consent при change. One durable request before dispatch, online prepare, no implicit retry replacement/auto stock. Frozen ticket owner/version/difficulty/price/attempt, quota20 RANDOM/day и5 prepared paid, no cancel quota return/TTL. Separate paginated lists; recover/cancel despite catalog/wallet failure; cancel saved before exit/offline, settle/cancel races at most1 terminal effect. |
| G6 Arena прохождение, offline и подтверждённый результат | FR-24–27; UJ-2 | One fresh start and save-before-reveal; restore same attempt/order/clock/answer/paused/feedback/completing. Expired current shown question one timeout, no fabricated future answers. Frozen completion retry; only same owner/attempt strict settlement receipt unlocks confirmed result/reward/rating. Local save/terminal/queue absence не proof, unknown HARD не0, no bank не fake result. Actual prepared offline→restart→delivery one effect; no instant global-offline-revocation promise. |
| G7 Оценки, цена, доход автора и сезон | FR-28–32; UJ-2/3/4 | Replaceable rating1–3 from past confirmed result without replay; no self/foreign/unconfirmed, one distinct rater. Full eligible universe drives price/random/prepare/season; rating(sum+40)/(count+20), linear equal-price1–100, empty/single tier1, issued ticket fixed. Author80% with fractional carry (5×1→4), burn20%; first Tournament15/Home20 boxes once. UTC quarter one winner10 boxes,≥20 confirmed raters, ties count→early publication→stableID, no candidate no award, never auto role/publish. |
| G8 Турнир как полное событие | FR-33–40; UJ-5/6 | Approved course/Arena bank frozen pre-fund, insufficient fails before reserve, course stays Archive. Full own-box fund first+second+2×third, idempotent creation. Three independent weekly EASY20, one attempt/run; full validated cohort server top16, immutable published ranking/bracket, no truncation/cap4096. Three-day stage, common confirmed meeting, one-hour wait and explicit technical outcomes. Same20HARD pair, current permitted question/no future keys, continuous server time. Played tie score→time→seed;2 bronzes/no bronze match/no technical prize. <2 qualify refund once; reserved=paid+returned+remaining, terminal remaining0, worker recovery to completion. |
| G9 Магазин, premium и коробки | FR-41–45; UJ-7 | Gold packs10/60/150; premium1/7/30/365 days gold1/5/15/100 and Play prices/source separated. Dedicated gold screen shows full price,30-day comparison and savings vs daily1, not subscription fiction. Premium nickname/daily no-ad/normal+plasma regen×2 with exact expiry/overlap/holds/grant provenance. One box type/global stock, max ordinary daily1 plus separate premium daily and other independent awards, no global cap2/5-ad flow. Offline observations not absolute attendance proof; opening online once with durable same result after loss/restart. |
| G10 Покупки, восстановление и возвраты | FR-46–49; UJ-8 | Authoritative Play verification/account binding only; Pending/callback no grant. Original purchase identity, ACK/consume appropriate confirmed product; sales-off preserves restore/query/paid delivery/refunds. Premium refund only unused part of matching grant, preserves used time/other grants. Cumulative quantity refund, permanent token fence, RTDN verify signal, wrong binding/ambiguous/legacy no invented new money. Actual Play sandbox separate fake, gold quantity support/premium>1 reject under current contract. |
| G11 Серверная конфигурация и её синхронизация | FR-50–51 | Central server changing rules/prices/limits/rewards with separate coherent config sync. UI/enforcement agree; current/stale/unknown distinct, failure not zero config, accepted operations keep snapshots. Monetary Play prices remain Play-owned. No new TTL/network-before-every-local-action. Main AD-5 supplies version/projection/cache/freshness design; do not reopen it as undefined. |
| G12 Дизайн всего MVP | FR-52; NFR-5 cross-cutting | Whole existing+new MVP including exams gets agreed UX and implementation, not only functioning old screens. Applicable waiting/empty/error/offline/no funds/no rights/unconfirmed/recovery; truthful loading, gold premium, two buttons/inbox/privacy/review progress/guidance; RU/UK/EN and semantic accessibility. Current DESIGN/EXPERIENCE discovery is not accepted finished layout. |
| G13 Устойчивые действия, аккаунты и обновление установки | FR-53–55 | Queue120 completes without repeated manual sync; immutable original identity/body, valid authority before ACK/deletion. A→B→A/sameUID new epoch/late replies/page scopes cannot mutate/read foreign state. One ticket2 devices≤1 settlement,2 different tickets each lawful. Additive update nonempty install preserves data/auth/history/pending/receipts, no uninstall/reset/destructive fallback; original export kept, invalid restore input no blank-env workaround. Ordinary offline unlock/provisional debit/outbox/quarantine rollback retained (mainAD-7), does not authorize offline exam. |
| G14 Контрольные, final и сертификат | PRD scope amendment:501–516, matrix559–563; EXAM:CAP-1–21 wholly adopted | Full EASY confirmed progress gates online theme5E+15H; all required stable themes gate current-course final20H. Approved exact revisions enter without pretest, stats only review signal; insufficient bank refusal pre-charge, snapshots/deadlines/answer immutable and abandoned exam server-finalized. Aggregate results without keys/item verdicts; correction appends history. Annual server certificate/public signed-out verification only from successful final; immutable on our-question error even downward rescore; stable themes carry, new themes must pass. Detailed pass/cost/cooldown/certification/draw/correction/renewal in adopted exam companion, not lost into mere source citation. |
| G15 Единый принятый Android-кандидат | NFR-7/8; SM-1/2/3/SM-C1; PRD:526–570; all FR and exam matrix | Code/backend/APK/schema/config/flags/environment/time identity agree; current/reused/not-run/skipped/interrupted/FAIL evidence honest, old PASS/Room/fakePlay не proof. ciCheck+applicable real HTTP/Firestore/rules/Room/UI, physical preserved installs,2-device races, actual Play sandbox, full courses/real paths, all required matrices incl exams. Final package state registry/ACCEPTANCE/MANUAL-CHECK/APK hash/signature/logs; owner manual result mandatory. Open monetary/data-loss/permission defect or mandatory not-run blocks readiness. This planning task runs none of them. |

**Coverage check:** G1=1–4,56; G2=5–8,11–12; G3=9–10; G4=13–15; G5=16–23; G6=24–27; G7=28–32; G8=33–40; G9=41–45; G10=46–49; G11=50–51; G12=52; G13=53–55 → every FR-1–56 exactly covered once in primary grouping. G14 imports all21 EXAM caps qualified; G15 binds acceptance across all. UI/private/common invariants remain cross-cutting, not optional because assigned one primary group.

## NFR preservation as kernel invariants

- **NFR-1 → G3/G5–10/G13/G14:** one stable financial effect with ledger/receipt; nonnegative concurrency, author fractions, review budget/burn, tournament conservation, grant-specific reversal; both orders/race/loss/restart. No-refund review fee does not cancel Play/refund/reserve/fund guarantees.
- **NFR-2 → G2/G4–10/G13/G14:** server authorization and bounded permitted views for direct reads/writes, account binding, private report ACL, keys/future forms/tokens/order IDs/PII excluded from general logs/evidence. Offline content inspectable, no unsupported attendance/clock tamper guarantees, no ban on one metric.
- **NFR-3 → G5–10/G13/G14:** retained original operations and mode progress through restart/offline/response loss/update; no local animation/terminal/queue-deletion proof; full cohort/resumable work and120 deliveries.
- **NFR-4 → G1/G5–10/G14:** distinct ordinary pause, prepared Arena offline and continuous tournament time; serverUTC/timezoneDST; pending/unknown/stale/rejected/confirmed differ; ready question no background wait.
- **NFR-5 → G1/G4/G12 and all UI:** RU/UK/EN, semantic actions, Continue/Info, Back/rotation/background/restore, recovery despite zero wallet/catalog error, explicit price.
- **NFR-6 → G1/G6/G7/G8:** input/answer<100ms retains original scope, runner250ms retained only original scenario; no invented first-load SLA/overall participants quota. Mandatory cohort sizes501/502 attempts,502 players,1503 attempts,4096/4097 with time/RSS/read-write/recovery; no truncation for PASS.
- **NFR-7 → G13/G15:** reproducible current dependency/toolchain compatibility/security, fresh runtime/dev/transitive audit; separate dependency-only slice; additive actual-schema migration and financial-history-safe rollback.
- **NFR-8 → G15:** versioned candidate/environment/scenario/time proof, retained failures, real financial ledger/server state/UI path, manual owner result; no historical proof relabel.

The above are invariant summaries. All detailed criteria in adopted PRD/addendum remain normative: real network cut, response-loss-after-upstream, full Node suite registration, no permissive mocks replacing journal codec/Room proof, catalogue oracle still unclosed, exact5 quiescence counters numeric0, map all nonzero fields, bounded worker resources, dependency transition map and independent Play evidence.

## Explicit amendments and already resolved choices

Do not reopen these as OPEN:
1. Exams are current MVP, not later scope; single mixed theme5E+15H/final20H.
2. Every exact revision independent checks→approval→eligible, initial and later banks; no mandatory pretest/statistics gate/hidden unscored extras.
3. Already-issued certificate unchanged on our-content downward correction; original attempt preserved, correction separate.
4. Theme passes carry by (courseId,stable themeId); only new required themes must pass, archived extras ignored, current final and frozen active session distinct.
5. Question-independent publication, dependency-aware re-review and online reviewer/server save/resume.
6. Fee1000/100×N, personal level/10 per lesson, no refund, proportional budget bound and burn; gold premium1/5/15/100 settled.
7. MainAD-4 fixes assignment generation/return/reassign and completion-segment save semantics without new lease TTL; AD-5 coherent config snapshots; AD-10 integer tenth entitlement/carry/escrow plus first-completed-unit level freeze; these fulfill prior architectural delegations in PRD, not live unanswered product gaps.
8. One outbox, ordinary offline unlock provisional Room TX+visible rollback; Arena retained intent+strict proof; lexicographic change cursor pair in4 journals. Current addendum:20 now agrees with mainAD-8.
9. MainAD-16 initial final success+immutable logical issuance+durable signing work atomic; signing outside TX/pending/retry same claims/key/date. It closes delayed signing vs correction race without choosing renewal policy.
10. MainMON override explicitly removes old global boxes cap2/five-ad; independent award sources remain.
11. Main KMP/public transfer separate later authorization; Android current acceptance includes neither as hidden completion prerequisite.

## Live OPEN product questions and precise wording

These gaps should remain explicit with owner/when, not guessed assumptions. No need to block this initiative SPEC from capturing all settled scope.

| Open | Precise question for the proper next stage | Owner / gate |
|---|---|---|
| OQ-2 tariff lesson-level edits without affected questions | «Какую плату берём за принятую повторную подачу изменения уровня урока, когда ни один вопрос и его зависимости не требуют перепроверки?» Do not infer0/100/1000; editing remains in scope. | Product owner, before implementing/accepting that paid submission path. |
| Final termination of unfinished paid review cycle, conditional | «Нужен ли отдельный окончательный исход незавершённого цикла; если да, что происходит с уже выполненной работой, выплатами и остатком оплаты?» No final burn/payout until whole cycle under current rule. Do not silently introduce this outcome. | Product owner only if that outcome is added; current completed-cycle behavior already settled. |
| Full UX and experience guide semantics | «Какие сообщения, пороги опыта, места показа и правила повторения/seen нужны в согласованном дизайне?» Not a required miniquest/deeplink/onboarding script. | UX workflow + owner before affected UI stories. |

Exam-specific **implementation prerequisites**, to preserve by adoption of exam SPEC rather than treat as new broad Discovery:
- mixed-theme star mapping: one server projection contract before result UI; default60% pass is independent and already settled;
- correction exclusion/denominator/rounding/version fixtures before rescore; preserve raw original evidence;
- full-required-EASY coverage record/version definition before exam admission; owner learning ingestion, no stars/subset surrogate;
- annual renewal issuance identity/history before renewal; one-year validity and new-final re-earning already settled; voluntary pre-expiry improvement must not silently replace current certificate;
- permitted exam sweeper adapter/cadence/resource evidence before unattended validation; one logical server terminator already settled.

External dependencies (not unanswered user policy): actual Play SKU/catalog/currency/quantity/tester/sandbox, final env/APK/config/physical preserved installs2devices/GMS, finished UX realization/manual result, fresh dependency gate and existing quiescence RED. Do not label unqueried env unavailable.

## Non-goals and quiet boundaries to keep visible in kernel/companion adoption

Telegram/groups; arbitrary DMs; shared editing of another published text or public suggestion workflow; manual tournament question selection; new box type/five-ad box/new ad path; unapproved developer messages/message auto-translation/attachments/notifications/replies via inbox convenience. Content translation review remains in scope and is not AI-only.

No compulsory iOS/web client; no new personas/market/KPI/availability%/launch SLA/throughput/overall learner cap. No reserve TTL, forced network for every ready local action, global stop of tournament timers during loading, random fallback or silent max-price consent. No exact3 reviewers/admin-only logic reviewer/new anti-cheat ban by one signal. No hidden first-visit quest/direct question deeplink requirement.

No runtime/build/deploy/financial session now; VPS storage, Mac interface only, demo future runs/no production credentials/public ADB/public ports. No disk/cache cleanup. No data reset/uninstall/destructive schema fallback/restore older DB over committed money. No bulk dependency latest/force or imported old schema/module creation plan.

## Contradiction status

**Нет обнаруженного live product contradiction** между прочитанными canonical PRD/addendum и final main architecture. Старые MON cap2/five-ad, timestamp-only cursor, reviewer-offline, pretest/rungs, ordinary offline unlock ambiguity и certificate ownership/issuance race согласованы поздними правилами и не требуют нового владельческого решения.

Сохраняются **исторические state-of-document phrases**, не противоречия поведения: addendum:62 «техническая организация конфигурации ещё не проектировалась» и :72/108 архитектурные делегирования описывают стадию PRD, а mainAD-4/5/10 уже дают решения; addendum:204 «AD IDs будут согласованы» — историческая передача, exam spine уже обновлён. Новый kernel не должен копировать эти фразы как текущие OPEN. Аналогично финальный архитектурный статус не означает реализованный код/пройденный runtime.

## Pass2 checklist для будущего kernel

Проверить: everyFR coverage; NFR1–8 named/bound; adopted companions normative not sources-only; all EXAM21 in scope; exact non-goals; final Android+manual acceptance identity; preserved original-open fees/conditional review termination and implementation prereqs; no newly invented defaults, no stale historical items as active tasks; no percentages of readiness/currentPASS; binding references resolve on VPS; PRD/addendum/source state untouched.

## Alignment with root's 23-capability kernel (English artifact language)

The root's grouping is complete and replaces the larger G1–G15 proposal as the intended kernel structure. Every FR-1–56 has exactly one primary capability; cross-cutting NFR and final acceptance remain explicit invariants/success conditions.

| Proposed initiative CAP | Primary claims | Quiet preservation check |
|---|---|---|
| CAP-1 Ordinary learning | FR-1–4 | Preserve offline/ready-immediately, true loading work, ordinary pause/time/Continue/Info and full real-course session. |
| CAP-2 Authoring | FR-5 | Author/private draft vs immutable submitted revision, language, provenance and history. |
| CAP-3 Review and publication | FR-6–8 | Independent question outcomes, old approved revision survives failed edit, dependency-aware re-review, online review saves and unique assignment; three directions do not define three people/AI-only. |
| CAP-4 Review fee/rewards | FR-9–10 |1000/100×N, no surcharge/cap/refund, per-lesson personal-level reward, proportional budget+burn, completed-cycle gate, two OPEN policies. |
| CAP-5 Curation/status | FR-11–12 | Explicit per-lesson promotion/removal, authorized private views, no automatic publication/role from rating or seasonal win. |
| CAP-6 Feedback/inbox | FR-13–15 | Two direct buttons, unchanged public comments, whole-lesson private free text/no answer replay, unified inbox retains source ACL. |
| CAP-7 Arena catalog/wallet/preparation | FR-16–22 | Name wallet explicitly: global available/reserved/freshness; browsing/read has no financial mutation. Explicit frozen consent/request/ticket, quotas and independent recovery lists. |
| CAP-8 Arena cancel/run/recovery | FR-23–27 | Offline retained cancel, cancel/settle proof, fresh once/save-before-reveal/restore, actual offline, strict same-attempt receipt rather than local/terminal/queue status. |
| CAP-9 Rating/price/royalty | FR-28–30 | Replaceable rating from old result, full eligible universe, fixed terms and fractional80/20 effect. |
| CAP-10 Promotion/season awards | FR-31–32 | Once per lesson transition, single UTC-quarter winner and full cohort/tie rules, no implicit curator role. |
| CAP-11 Tournament creation/fund/qualification | FR-33–36 | Bank sufficient before reserve, course stays Archive, complete own fund, three independent EASY runs and untruncated immutable top16. |
| CAP-12 Tournament match/end/fund | FR-37–40 | Explicit time/no-show, continuous server clocks, identical20HARD/current question only, technical outcomes and complete escrow conservation. |
| CAP-13 Store/premium | FR-41–43 | Fixed gold durations/prices + separate Play source, full actual price/30-day comparison/daily savings, exact grant intervals and both regens×2. |
| CAP-14 Boxes | FR-44–45 | Ordinary daily1 + separate premium + independent other awards; one type, no global cap2 or five-ad; opening online once. |
| CAP-15 Purchases/refunds | FR-46–49 | Provider proof/account binding, pending not grant, sales-off restore, grant-specific remainder, cumulative quantity and legacy fence, actual Play sandbox. |
| CAP-16 Config | FR-50–51 | Separate coherent sync/freshness, current display and enforcement, frozen accepted terms, no network-every-local-action/TTL invention. |
| CAP-17 Design/guidance | FR-52,56 | All screens including exams; RU/UK/EN/semantic accessibility; UX guide copy/thresholds/seen remain explicit later detail, no invented onboarding quest. |
| CAP-18 Delivery | FR-53 |120 queue actions, same identity/body across retry, valid authority before ACK/deletion, no repeated manual sync requirement. |
| CAP-19 Account isolation | FR-54 | Owner+session epoch/A→B→A/sameUID new session/late responses/pages; one ticket2 devices≤1settlement, two different tickets retain lawful effects. |
| CAP-20 Migration | FR-55 | Nonempty install/auth/data/pending/history/receipt preservation; original export kept, no reset/blank-stand substitution. |
| CAP-21 Theme tests | EXAM theme/admission/runtime capabilities | Full confirmed EASY coverage, one5E+15H online sitting, applied config/charges/cooldown, private answers, best/pass/first-pass identity, abandoned session server outcome. Ordinary offline access purchase is not passed progress. |
| CAP-22 Final/certificate | EXAM final/certificate capabilities | All current required stable themes and certification bank, separate20HARD final, successful terminal+initial issuance fact atomic; annual signed certificate/public signed-out verification; new-final re-earning and immutable old attestation. |
| CAP-23 Versioned content correction | EXAM quality/history/correction capabilities | Exact revision approval without pretest, signals only trigger review, quarantine only new draws, original/frozen evidence, versioned correction can lower admission but cannot change issued certificate due our question error. |

Suggested explicit qualified EXAM coverage (overlap is intentional):
- CAP-21 primarily binds EXAM:CAP-1–7,9–15,17,19–21.
- CAP-22 primarily binds EXAM:CAP-8,12–13,16,18–21, using shared session guarantees EXAM:CAP-2–6,10.
- CAP-23 primarily binds EXAM:CAP-14,17–19,21 plus publication guarantees from EXAM:CAP-3.
This covers every EXAM:CAP-1–21; do not interpret shared items as separate competing implementations.

Retain a kernel-wide success condition for the single Android candidate, normative full acceptance matrix and owner manual result; it need not be a separate capability. Keep NFR-1–8 explicitly adopted/bound even though the23 CAP table is functional. A source-only citation to the PRD is insufficient for preservation.
