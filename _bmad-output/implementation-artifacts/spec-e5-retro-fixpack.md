---
title: 'Epic 5 retro fix pack: ретро-пункты 1–11 (L1–L9, rig-дыра, бухгалтерия)'
type: 'bugfix'
created: '2026-10-25'
status: 'in-progress'
baseline_revision: '4e2ca56c0963289baf7ea67116b02a1cd914eebf'
route: 'full'
route_source: 'pinned'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '_bmad-output/mvp-candidate/epic-competitions/RETROSPECTIVE.md'
warnings:
  - 'multiple-goals'
deferred: []
---

<intent-contract>

## Intent

**Problem:** Ретро эпика 5 (verdict: rejected, 2026-10-19) верифицировала 9 кодовых дефектов (L1–L9), живущих на швах историй, плюс rig-дыру доказательства (item 10) и дрейф бухгалтерии (item 11). До их закрытия эпик не принимаем: READY_ACK недостижим из приложения (L1), честные отказы допуска гибнут на границе сервиса (L2), легальный призовой список 16 строк не читается клиентом (L3), ARENA_LESSON-событие нельзя присоединить из приложения (L4), скачивание не доказывает курс (L5), re-read петля по/device-часам (L6), деградировавший призовой объект валит страницы discovery (L7), binding-тест меряет не ту поверхность (L8), attestedDownload бросает вопреки fail-closed доке (L9).

**Approach:** Один фикс-пакет на ветке `night/e5-retro-fixpack`: каждый пункт правится ровно там, где ретро назвало файл:строку, с регрессионным тестом на каждый дефис; контракты (prize-list D1, ONLINE-O1 D3/D4, ONLINE-O2 §4/A8) не пересматриваются.

## Boundaries & Constraints

**Always:**
- Каждый фикс сопровождается тестом, ловящим именно исходный дефект (L1/L2 — тесты потребляют вывод парсера над записанными проводными формами, P2).
- Все причины отказов сервера, названные по имени, классифицированы клиентом (охрана L8 расширяется на эмиттеров).
- Новые пользовательские строки — с RU/UK/EN паритетом.
- Тяжёлые сборки — через `sgw`; node-тесты — `/root/.zcode/server/node` на VPS.

**Never:**
- Не трогать семантику контрактов, `functions/lesson-question-window.js` (O1 A2), имена турнир/чемпионат, хранимые документы старых событий.
- Не закрывать owner-решения (F10 окно-семантика, L12 finished-events, F9 CAP-11) и deferred-гигиену (F5/F6/F8/F13) — только пункты 1–11.
- L10 решается ре-документацией Advance как poll-семантики (без close-now); F12 — только правка комментариев статусов.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 16-строчный призовой список (место 16) | server-legal v1 wire на чтении | декодируется без MalformedWire | 17-я строка / место 17 — MalformedWire |
| START без допуска на приватный курс | admission.reason = NO_ADMISSION_BASIS / OUTSIDE_QUALIFICATION_WINDOW | wire reason = имя причины; клиент показывает именованный отказ | не generic PERMISSION_DENIED |
| Квитанция ANSWER с session.window DELIVERED | op receipt (окно вложено в session) | READY_ACK уезжает из очереди, квитанция ACK применяется | окно OPEN (недоставленное) — ACK не отправляется |
| Край окна прошёл, sweep backlog | expiresAtMs < server now | re-read перевзводится по серверным часам с нижней границей и backoff | нет петли zero-delay reads |
| recordDownload не-COURSE квеста | quests/{id} не элигибельный курс | отказ SOURCE_NOT_ELIGIBLE, аттестация не пишется | существует — прежний путь |
| Деградировавший prizes: {} в хранилище | safeEvent fallback | парсируемый {listVersion:0, rows:[], maximumLiabilityBoxes:0} | страница discovery жива |
| downloadRecord schemaVersion ≠ 1 | attestedDownload(record) | возвращает false («нет записи») | не бросает RangeError |

</intent-contract>

## Code Map

- `functions/tournament-policy.js` — :15 MAX_QUALIFIERS=16 (клиентский кап обязан сойтись); :294-298 attestedDownload бросает RangeError (L9 → return false); экспорт eligibility-предиката источника для L5 (см. `tournament-source-bank.js`).
- `functions/tournament-service.js` — :277 `fail("PERMISSION_DENIED", admission.reason)` (L2 → `fail(admission.reason, admission.reason)`; строки проходят грамматику `/^[A-Z][A-Z_]{0,63}$/`, `courseTournamentFailure` ушлёт их в failed-precondition — клиент уже классифицирует оба имени); :226-231 CREATE кладёт `admissionLink:{token}` в ответ организатора (источник L4); :87-101 `sessionView` без окна — окно операции живёт в `responseFor` вложенным в `session`.
- `functions/tournament-session.js` — :398-407 мёртвая ветка `if (!opened)` (L10/F3 — `openTournamentQuestionWindow` всегда возвращает объект; удалить ветку, оставить прямой путь `session.window = opened; if (!serverDriven) deliver…`); READY_ACK-гейт сервера требует boundary.state=WINDOW_DELIVERED и совпадения windowId.
- `functions/course-tournament-controller.js` — :95-97 fallback prizes `→ {}` (L7 → парсируемый пустой v0); :361-362 recordDownload проверяет только существование `quests/{id}` (L5 → элигибельность COURSE тем же предикатом, что source-bank; отказ SOURCE_NOT_ELIGIBLE).
- `shared/feature/tournament/data/.../remote/TournamentEventWireParser.kt` — :254 MAX_PRIZE_ROWS=15 (L3 → 16; :141 место проверяется тем же капом).
- `.../jvmTest/.../TournamentEventWireParserTest.kt` + `resources/tournament-client-fixtures.json` — :275-281 hash-pin список (добавить `tournament-policy.js`), фикстура 16-строчного граничного провода.
- `.../data/outbox/TournamentSessionOutboxCodec.kt` — KDoc Advance «Закрытие текущего вопроса сейчас» ложь (L10 → re-doc: poll-семантика, сервер никогда не закрывает досрочно по клиентскому запросу).
- `.../data/outbox/TournamentSessionReceiptParser.kt` — :137/:140 ANSWER/ADVANCE статусные множества (пин семантики в `TournamentSessionOutboxContractTest.kt`).
- `shared/feature/tournament/domain/.../TournamentSessionModels.kt` — TournamentOperationView: дополнить `admissionLink` (L4), дефолт null.
- `.../data/remote/TournamentSessionWireParser.kt` — :73 `root.obj("admissionLink")?.exact("token")` отбрасывает значение (L4 → типизировать token как nonBlank).
- `android/.../presentation/model/TournamentSessionStates.kt` — только WINDOW_OPEN (добавить WINDOW_DELIVERED="DELIVERED").
- `android/.../component/TournamentSessionPlayComponent.kt` — :216-233 ACK-гейт читает top-level `view.window` (L1 → читать `view.window ?: view.session?.window`, гейт по WINDOW_DELIVERED); :303-315 re-read `wait = expiresAtMs − nowMs()` по device-часам (L6 → offset от последнего serverNowMs + floor 1s + backoff ×2 на повторные перевзводы того же windowId, reset при смене состояния).
- `android/.../component/DefaultTournamentOrganizerComponent.kt` + `screen/TournamentOrganizerScreen.kt` + `res/values{,-ru,-uk}/strings.xml` — состояние `created` без токена (L4 → хранить токен, показать на экране созданного события с testTag).
- `platform/firebase/src/test/.../tournament/TournamentRefusalsBindingTest.kt` — :33-60 grep только тела courseTournamentFailure (L8 → добавить grep fail-литералов по шести модулям: tournament-{service,session,policy,source-bank,bank,worker}.js; все 26 имён уже классифицированы — проверено).
- `android/...test/.../DefaultTournamentQualificationComponentTest.kt` — хелпер `deliver` (:172) строит top-level window (не серверная форма); :468-474 ожидает недостижимый wire; + `OperationViews.kt` (фабрика вида операции — дополнить admissionLink-дефолтом не требуется при named args); `platform/firebase/.../FirebaseTournamentRemoteDataSourceTest.kt` :152-153 — кодировать живой путь.
- `scripts/course-tournament-lifecycle-demo.test.js` — 5.12 lifecycle (item 10 → фаза: sink-driven сессия через submitMutation drain, READY_ACK, окно воркера, оставленное истекать → sweep → FORCED_CUTOFF по захваченному краю).
- `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` — :40,:52 «КОНТРАКТ ОЖИДАЕТ УТВЕРЖДЕНИЯ ВЛАДЕЛЬЦЕМ» (F12 → принятие 2026-10-16, решения 1/2).

## Tasks & Acceptance

**Execution:**
- [ ] `functions/tournament-policy.js` -- L9: attestedDownload при schemaVersion≠1 возвращает false -- честный fail-closed; тест в `tournament-policy.test.js`.
- [ ] `functions/tournament-service.js` -- L2: гейт допуска :277 несёт reason именем и кодом -- клиентские ветки оживают; тест: отказ допуска отдаёт NO_ADMISSION_BASIS в details.reason.
- [ ] `functions/course-tournament-controller.js` -- L5: recordDownload требует элигибельность COURSE; L7: fallback prizes = {listVersion:0, rows:[], maximumLiabilityBoxes:0} -- авторитет callable и живой discovery; node-тесты обоих.
- [ ] `functions/tournament-session.js` -- F3: удалить мёртвую ветку `if (!opened)` -- вводный комментарий не лжёт; существующие тесты зелёные нетронутыми.
- [ ] `TournamentEventWireParser.kt` + `TournamentEventWireParserTest.kt` + `tournament-client-fixtures.json` -- L3: MAX_PRIZE_ROWS=16, hash-pin tournament-policy.js, граничная фикстура 16 строк/место 16 -- контракт D1 читаем целиком.
- [ ] `TournamentSessionStates.kt` -- WINDOW_DELIVERED -- серверное состояние delivery именуется.
- [ ] `TournamentSessionOutboxCodec.kt` + `TournamentSessionOutboxContractTest.kt` -- L10: KDoc Advance = poll-семантика, пин статусного множества -- словарь не обещает close-now.
- [ ] `TournamentSessionPlayComponent.kt` -- L1: ACK по DELIVERED-окну из session-вложения квитанции; L6: re-read по серверным часам (offset+floor+backoff) -- O2 §4 оживёт, петля исчезает.
- [ ] `TournamentRefusalsBindingTest.kt` -- L8: grep эмиттеров шести модулей -- переименование причины не проходит молча.
- [ ] `TournamentSessionModels.kt` + `TournamentSessionWireParser.kt` + `DefaultTournamentOrganizerComponent.kt` + `TournamentOrganizerScreen.kt` + строки RU/UK/EN -- L4: токен типизирован и показан на созданном событии -- UX-O4/access получает возможный путь.
- [ ] `DefaultTournamentQualificationComponentTest.kt` + `FirebaseTournamentRemoteDataSourceTest.kt` -- P2: проводные формы хелпера = серверные (окно вложено в session), недостижимые ожидания заменены живыми -- зелёное врёт о проводе.
- [ ] `scripts/course-tournament-lifecycle-demo.test.js` -- item 10: sink-driven сессия с READY_ACK + истёкшее окно воркера → sweep → FORCED_CUTOFF по захваченному краю -- композиция очередь/ACK/worker получает rig-доказательство.
- [ ] `tickets.toml` -- F12: статусы 5.1/5.2 = принято 2026-10-16 -- следующий читатель не введён в заблуждение.

**Acceptance Criteria:**
- Given server-legal 16-строчный список (место 16), when любое клиентское чтение парсит его, then декод без MalformedWire; 17-я строка/место 17 — отказ.
- Given START без основы допуска, when сервер отказывает, then wire reason NO_ADMISSION_BASIS и клиент рендерит именованный отказ.
- Given квитанция ANSWER с session.window DELIVERED, when компонент её применяет, then READY_ACK уезжает и его квитанция применяется; given окно OPEN — ACK нет.
- Given край окна прошёл при backlog свипа, then re-read перевзводится по серверным часам с floor и backoff, без zero-delay петли.
- Given CREATE-квитанция с токеном, then экран созданного события показывает токен; второй аккаунт стартует по нему.
- Given расширенный lifecycle demo, then sink-сессия доезжает ACK, а истёкшее окно воркера завершается FORCED_CUTOFF по захваченному краю.
- Given все fail-литералы шести серверных модулей, then клиентская таблица классифицирует каждый (binding-тест зелёный).

## Implementation Notes

## Auto Run Result

Status: blocked
Blocking condition: no subagents — запуск write-сабагента (`general-purpose`) падает ошибкой провайдера «Network connection failed for the provider request» (11 попыток с паузами до 5 мин за ~80 минут); тип `Explore` при этом запускается штатно, но read-only и реализовать спеку не может. Реализация не начата; ни одного коммита поверх baseline нет.

Контекст возобновления:

- Ветка `night/e5-retro-fixpack` @ `4e2ca56c0963289baf7ea67116b02a1cd914eebf` (= baseline) чекаутена в worktree `/workspace/projects/schoolquiz3.0/.local/worktrees/e5-fixpack` на VPS — работать там, НЕ в main worktree (он занят активной sibling-сессией `night/e7-commerce`).
- Все 15 якорей Code Map перепроверены read-only пробой сабагента на живом коде baseline — все подтверждены (включая уточнения: предикат элигибельности для L5 в `functions/tournament-source-bank.js:99-103`, нужен новый экспорт; pin-тест статусов в `TournamentSessionOutboxContractTest.kt:179-217`; второй фабрикующий тест-сайт `DefaultTournamentQualificationComponentTest.kt:313-330`; строки 145/145/145 — новые ключи во все три локали).
- Тяжёлые сборки через `sgw`, node — `/root/.zcode/server/node`; доказательство — `docs/agents/evidence/<дата>-e5-retro-fixpack/`.

### 2026-10-26 (повторный автозапуск)

Status: blocked
Blocking condition: dirty working tree — поверх baseline лежит незакоммиченный WIP, оставленный предыдущим запуском; гард чистого дерева воркфлоу запрещает принимать его без решения владельца.

- Спека была `blocked` (2026-10-25: write-сабагент недоступен). Запись «реализация не начата» в предыдущем результате неточна: файлы `functions/` правились 2026-10-25 17:48–17:59 UTC, ДО коммита спеки 73b411c9 (18:37 UTC), и остались незакоммиченными (7 файлов: tournament-policy.js, tournament-service.js, tournament-session.js, course-tournament-controller.js + tournament-policy.test.js, tournament-service.test.js, course-tournament-handler.test.js).
- WIP покрывает задачи 1–3 (L9 fail-closed, L2 именованный отказ допуска, L5 eligibleCourseSource + L7 parseable fallback, F3 удаление мёртвой ветки `if (!opened)`) и выглядит добротным, но не закончен и красный на собственных тестах: `tournament-policy.test.js` — 1 детерминированный отказ (тест eligibleCourseSource :170 «catalog: {} → true» противоречит предикату и собственному комментарию «default is REGULAR»); `course-tournament-controller.test.js` — :106 всё ещё ждёт PERMISSION_DENIED (переименование L2 не проведено в этом тесте); `course-tournament-handler.test.js` — красный; `tournament-session.test.js` — зелёный изолированно, 3 сбоя только под параллельной нагрузкой (timing-тесты). `tournament-service.test.js` — зелёный (57 тестов).
- Этот запуск не менял код: ни коммитов, ни откатов. Задачи 4–13 (Kotlin: L3 MAX_PRIZE_ROWS, WINDOW_DELIVERED, L10 KDoc, L1 ACK-гейт, L6 re-read, L8 binding, L4 токен, P2 фиксаторные тесты; demo item 10; tickets F12) не начинались — ни один Kotlin-файл не тронут.

### 2026-10-27 (ручное возобновление — реализация)

Status: implemented (гейты зелёные; живой полный lifecycle-PASS не достигнут — деградация общего рига; пункт в openItems)

- WIP задач 1–3 ревизирован против `tournament-source-bank.js` collect (предикат L5 зеркалирует его гейт; авторизация вне предиката — как в KDoc), доведён до зелёного: policy-тест :170-171 переписан под фактическую цепочку разрешения questType (catalog → course → REGULAR), предикату добавлена проверка пустых-по-пробелу catalogId (fidelity к `id()`), контроллер-тест :458-459 переведён на именованный отказ L2, дописаны тесты L5 (SOURCE_NOT_ELIGIBLE + прежний путь) и L7 (деградировавший fallback в readEvent и listEvents).
- Задачи 4–13 реализованы с нуля: L3 (cap 16, hash-pin `tournament-policy.js`, граничная фикстура `eventReadPrizeBoundary16`, снятая с реального контроллера над stub: 16 строк, места 1..16, ответственность 22); WINDOW_DELIVERED; L10 re-doc + pin; L1 (ACK по DELIVERED из session-вложения квитанции, OPEN-негатив); L6 (re-arm по серверным часам: offset от serverNowMs, floor 1s, backoff ×2 до 60s, тест на виртуальном времени); L8 (grep fail-литералов шести модулей + behavior-проверка причин допуска); L4 (типизированный токен: models → wire-parser → organizer component/state/screen → строки 145/145/145 → тесты парсера и компонента); P2 (deliver-хелперы qualification И match компонентных тестов переведены на серверную форму — окно вложено в session, DELIVERED; match-сайт не был назван в Code Map — найден красным на гейте); item 10 (sink-фаза: CREATE → link-token START → ANSWER/READY_ACK через `submitMutation.run` под логическими часами → окно воркера истекает → FORCED_CUTOFF по захваченному краю → отмена события с полным возвратом); F12 (tickets.toml 5.1/5.2 — принятие 2026-10-16, решения 1/2).
- Гейты: `run-tests.js` 128/129 (единственный красный — предсуществующий mvp-isolation, атрибутирован); `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:feature:tournament:data:jvmTest :android:feature:tournament:presentation:test :platform:firebase:test --no-configuration-cache` → BUILD SUCCESSFUL (JAVA_HOME нужен: buildSrc jvmToolchain(27) не автодетектится — та же toolchain-особость, что в 4.6–4.9); `node --check` lifecycle-скрипта → OK. Матрица I/O покрыта прогнанными тестами по всем 7 строкам (аудит в evidence README).
- Живой риг: механика sink доказана пробником (START по токену → ANSWER через submitMutation → DELIVERED-окно в session → READY_ACK с квитанцией; `e5-sink-probe.js` в evidence-папке). Полный lifecycle-PASS не достигнут за 4 попытки: 2 прогонных бага фазы найдены и исправлены (синтетический игрок без Auth-токена; HTTP-sink на реальных часах эмулятора против логических часов фазы — дренируется в процессе), попытки 3–4 упали ДО sink-фазы на деградировавшем общем риге (флейм `2 UNKNOWN` транзакций firestore-эмулятора; DEADLINE_EXCEEDED на preflight при ~125-155% CPU functions-emu — hot-reload от правок соседа e7 в монтируемом worktree). Рестарт общего эмулятора — решение владельца; пункт вынесен в openItems.
- ktlint: module-wide ktlintFormat зацепил чужие main-файлы (economy/exam/arena/network) — весь коллатерал откачен; единственный известный красный форматирования — предсуществующая некорректируемая строка `FirebaseGiftClaimRemoteDataSource.kt:104` (файл вне ветки).
- Процесс: копия спеки в main worktree была ошибочно переведена в in-progress в начале сессии — откачена (`git checkout --`), правки только в этой, worktree-копии.

Разблокировка (решение владельца): ревизия WIP в `functions/` (завершить или откатить), коммит, вернуть spec status (например `ready-for-dev`) и перезапустить build-auto. Рабочее дерево этого worktree после коммита WIP станет чистым.

## Spec Change Log

## Review Triage Log

## Design Notes

- L1: сервер (`responseFor`) кладёт окно операции вложенным в `session`; чтение (`readTournamentSession`) — top-level. Клиентский парсер читает оба; компонент обязан мержить как `readSessionInto` уже делает (`view.window ?: view.session?.window`), а ACK гейтить по DELIVERED — ровно серверная предусловие READY_ACK (boundary.state=WINDOW_DELIVERED).
- L6: offset = nowMs() − serverNowMs последнего чтения; wait = expiresAtMs − (nowMs() − offset), floor 1000ms; повторный перевзвод того же windowId удваивает интервал (cap 60s), любое изменение состояния сбрасывает. Device-часы остаются только источником offset, не краем.
- L2: admission reasons удовлетворяют грамматике причины, `courseTournamentFailure` отображает незнакомые имена в failed-precondition, клиент уже классифицирует NO_ADMISSION_BASIS/OUTSIDE_QUALIFICATION_WINDOW → таблицы сходятся без правок маппера.
- Стартовый гейт :244 (`fail("FAILED_PRECONDITION", decision.reason)`) не трогается: там живут REPLAY/ROUND_ALREADY_USED, которых клиент по имени не знает — расширение области L2 ограничено гейтом допуска.

## Verification

**Commands:**
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0/functions && /root/.zcode/server/node --test tournament-policy.test.js tournament-service.test.js tournament-session.test.js'` -- все зелёные.
- `ssh rnsrv 'cd /workspace/projects/schoolquiz3.0/functions && /root/.zcode/server/node run-tests.js'` -- полный набор зелёный.
- `sgw ./gradlew :shared:feature:tournament:data:jvmTest :android:feature:tournament:presentation:test :platform:firebase:test --no-configuration-cache` (на VPS) -- все зелёные.
- `node --check scripts/course-tournament-lifecycle-demo.test.js`; live-прогон по рецепту `docs/agents/evidence/2026-10-17-competitions-demo-e2e/README.md`, если rig поднят -- PASS, exit 0, новая фаза в чек-листе.

**Manual checks (if no CLI):**
- README evidence-папки прогонов дополнен фикс-пакетом (доказательство: коммиты + прогоны).
