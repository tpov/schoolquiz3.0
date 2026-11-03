# 5.12 demo-e2e-server — 2026-10-18

Story: «restricted server-only demo run of the full competition lifecycle» — спека
`_bmad-output/implementation-artifacts/spec-5-12-demo-e2e-server.md` (route full/pinned,
`context: []`), baseline `d9cd05b0bee898c0f33d94681d81d6aca28e3d78` (HEAD совпал с
`baseline_revision` спеки на момент начала; рабочее дерево чистое, кроме самой спеки).
Все прогоны — против живого demo-рига (`demo-schoolquiz-mvp`), без прод-credential'ов;
env-ассерты харнесов не тронуты.

Обзорный раунд правок (review, тот же день) укрепил харнесы (пин отпечатков, robustness
lifecycle-склейки, честность scopeWrite, identity-префлайт) и ПЕРЕПРОГНАЛ все семьи;
номера в таблице ниже — свежие прогоны после правок. См. «Обзорный раунд правок» ниже.

## Прогонный риг и его проводка (env alignment, точно как исполнено)

Контейнеры: `schoolquiz-demo-firebase-20261004` (bridge `172.17.0.3`; auth 9099 и firestore 8080
внутри, на хост опубликованы как `127.0.0.1:19090/18090`; внутри же — эмулятор-hub на 4400,
отдающий auth+firestore) и `schoolquiz-functions-emu` (host-network; functions 0.0.0.0:5001,
свой hub на 127.0.0.1:4400; `FIRESTORE_EMULATOR_HOST=127.0.0.1:18090`). Оба монтируют этот
ворк три как `/repo` (ro); functions-эмулятор грузит код вызываемых из `/repo/functions`.

Харнессы ассертят ОДИН хост `schoolquiz-mvp-firebase` на портах 8080/9099/4400/5001. Прогноз
"риг той эпохи" (запись в `/etc/hosts` → мёртвый `172.17.0.4`) не отвечал вовсе. Выравнивание
(уровень хоста, эмуляторы не переконфигурировались, данные не тронуты):

1. `/etc/hosts`: `172.17.0.4 schoolquiz-mvp-firebase` → `172.17.0.3 schoolquiz-mvp-firebase`
   (sed-замена одной строки).
2. Форвардер :5001 внутри firebase-контейнера (внутри него нет своего 5001; functions слушает
   на хосте): `docker exec -i schoolquiz-demo-firebase-20261004 sh -c 'cat > /tmp/tcp-fwd-5001.js'`
   (node/net, `0.0.0.0:5001 → 172.17.0.1:5001`; скрипт лежит в контейнере `/tmp`) и запуск
   `docker exec -d schoolquiz-demo-firebase-20261004 node /tmp/tcp-fwd-5001.js`. Умирает с
   контейнером; пересоздание — той же командой.
3. Симлинк `/repo → /workspace/projects/schoolquiz3.0` на хосте (пути `/repo/...` в харнесах).

После этого все четыре ассерта отвечают на `schoolquiz-mvp-firebase`: 8080/9099/4400 (hub
auth+firestore) и 5001 (форвардер). Прогонная команда для всех трёх харнесов (общая шапка):

```
cd /repo && env -u GOOGLE_APPLICATION_CREDENTIALS -u FIREBASE_CONFIG \
  GCLOUD_PROJECT=demo-schoolquiz-mvp \
  FIRESTORE_EMULATOR_HOST=schoolquiz-mvp-firebase:8080 \
  FIREBASE_AUTH_EMULATOR_HOST=schoolquiz-mvp-firebase:9099 \
  /root/.zcode/server/node scripts/<harness>.js [args]
```
(+ `QS_APPROVED_CASE=small QS_SOURCE_MANIFEST=/workspace/infrastructure/android-toolchain/firebase-state/qs-source-manifest.json`
для обоих bulk-ригов; Node хоста v22.16.0.)

## Адаптации харнесов (каждая называет отражённое поведение; env-ассерты не тронуты)

Разраб. раунд:

- `scripts/course-tournament-http.test.js` (префлайт, 3 строки): hub на `HOST:4400` — это hub
  auth+firestore-процесса демо-рестора; functions в этом риге — отдельный процесс со своим hub,
  поэтому его готовность проверяется на самом ассертированном порту. Отражает топологию рига из
  двух процессов; cross-process регистрация в hub в firebase-tools отсутствует (проверено по
  исходникам hub.js: только GET /, GET /emulators, POST /export).
- `scripts/mvp-qualification-firestore-test.js`, `scripts/mvp-qualification-calculation-firestore-test.js`
  (по одной строке в `scopeWrite`): CREATE с 5.3 минтит документ Competitive-link
  `course_tournament_links/{eventId}` — allowlist скоупа записей 5.4-эпохи его отказывал
  («Out-of-fixture write refused», честный отказ рига). Путь события добавлен в fixture-scope.
- `scripts/mvp-qualification-firestore-test.js` (перепин + ожидание публикации): (а) inline-SHA
  `course-tournament-controller.js` `f1ce23d0…` → `ed9692fc…` — контроллер менялся в 5.5–5.8
  (worker CLOSE/ADVANCE_MATCH, settlement), пин страж «смонтирован просмотренный источник»,
  HEAD при перепине = baseline эпика, дерево чистое; (б) блок ожидания ранжирования: с 5.5 CLOSE
  публикует замороженную ВЕРСИОНИРОВАННУЮ калькуляцию (schemaVersion 2) — LS-поверхности
  (rows/counts/algorithmVersion) остались deep-equal к перестроенному снапшоту, а входные
  отпечатки теперь базы player-stream-chain-v1: в ЭТОМ риге они только формат-проверяются
  (regex) — его когорт неймспейсом привязан к своему прогону, кросс-риговый пин невозможен;
  точные значения пиняет calculation-риг для СОБСТВЕННОГО события (см. обзорный раунд).
- `scripts/package.json`: `lint` дополнен `node --check course-tournament-lifecycle-demo.test.js`
  (конвенция: все rig-скрипты в гейте синтаксиса).

Обзорный раунд правок (review, 2026-10-18):

- `scripts/mvp-qualification-calculation-firestore-test.js` — НАСТОЯЩИЙ пин отпечатков
  публикации: `published.formsFingerprint === chosen.contract.formsFingerprint` и независимая
  перестройка player-stream-chain дайджеста из СОБСТВЕННЫХ замороженных входов прогона
  (VERIFIED_RUNS-документы + `manifestDigest` scan-head + формы события) чистым compact-
  конвейером (`createReduction → reducePage → solve → createTop16 → mergeTop16 → finishTop16`),
  deep-equal к `published.verifiedRunsFingerprint` — schema-2 аналог идиомы перестройки
  `buildQualificationRankingSnapshot` из `functions/tournament-qualification-scan.test.js`.
  Стабильность двух исполнений и раньше доказывалась самим ригом (replayed-вызов + deep-equal
  опубликованного документа) — сохранено. Прод-изменения не потребовались: перестройка
  воспроизвела отпечаток производства на живом риге.
- `scripts/mvp-qualification-firestore-test.js` — только честная правка комментария (:284-287):
  прежняя формулировка «точные значения пиняет calculation-риг» была ложной для ЭТОГО рига
  (calculation-риг пинил своё событие); теперь сказано точно: здесь regex, пин — в
  calculation-риге и только для его собственного события.
- `scripts/course-tournament-lifecycle-demo.test.js` — robustness: (а) `typeof
  view.serverNowMs === 'number'` до цикла ожидания старта квалификации; (b) ассерты нахождения
  lookup-результатов до разыменования (матч сетки, finalPending размера 2, verified run
  сессии); (c) мёртвый guard `RESOURCE_ENVELOPE` заменён: `reconcileEvent` сигнализирует
  исчерпание бюджета причиной `INVOCATION_TIME` (также `SCAN_PENDING`/`CHECKPOINT_SAVED`/
  `WORKER_BUSY`) — ожидание логируется диагностикой, последний сырой результат включён в
  сообщение провала ограниченного цикла; (d) истина сева сетки: entrants/seeds/паринги
  сравниваются с опубликованным замороженным рейтингом (seed k = место k; размер 4 — пары
  1v4/2v3; размер 2 — 1v2), а не только консервация множества; (e) финансовый дайджест между
  КАЖДОЙ фазой (после сева сетки, на входе в каждый матч, после согласований, через
  абортированный settlement, после каждого settlement, после event2-CREATE); (f) оба
  вырожденных события ассертят `cancelReason === 'FEWER_THAN_TWO_QUALIFIERS'` (случаи 0 и 1);
  (g) второй crash-зонд: `HARNESS_ABORT` в транзакции settlement-квитанции ADVANCE_MATCH
  (`ctw_match_*`) — после аборта нет исхода и движения денег, повторный sweep доигрывает ровно
  один раз (уникальность matchId в outcomes ассертится на FINISHED); (h) `synthetic.d0`
  объявлен и используется как stray-credit-canary (никогда не участник/организатор — нулевой
  баланс до терминальной проверки). Плюс honesty scopeWrite: коллекции квитанций
  (`course_tournament_operations`, `course_tournament_session_operations`,
  `course_tournament_answer_ingress`) ассертят синтетического владельца записи (прецедент
  bulk-ригов), funding/downloads — владельца и событие прогона, `bank_preparations`/снапшот
  банка — владельца; глобальный `course_tournament_worker_state` исключён из allowlist
  (через обёртку не пишется — отказ fail-closed); префлайт :5001 теперь identity-проверка
  (не «любой 404»): probe неизвестной функции перечисляет развернутые callables ровно по
  шести scoped-именам, а смонтированный код отвечает собственным структурированным
  UNAUTHENTICATED; `mountedSourceShas` дополнен `tournament-source-bank.js`.
- `scripts/course-tournament-http.test.js` — префлайт :5001 усилен той же identity-проверкой
  (перечисление callables; собственный UNAUTHENTICATED каждого callables и так ассертирован
  первой проверкой — он и доказывает identity смонтированного кода). Остаточный риск
  задокументирован в комментарии (подделка обеих форм ответа иностранным сервером).
- `scripts/package.json` — в `lint` добавлены три старых rig-скрипта:
  `course-tournament-http.test.js`, `course-tournament-private-rules.test.js`,
  `mvp-qualification-firestore-test.js`.
- `_bmad-output/implementation-artifacts/spec-5-12-demo-e2e-server.md` — убраны дублирующиеся
  пустые ключи frontmatter (`review: ''` / `review_source: ''`).

Ни одно производственное изменение в этой истории не потребовалось: оба красных следа,
которые риг вскрыл (links-scope, база отпечатков публикации), — устаревшие ожидания харнесов,
каждое названо выше; прод-код не тронут (см. «Проверка»).

## Прогоны (команда → исход; все — ПОСЛЕ обзорного раунда правок)

| # | Харнес / прогон | Результат | Артифакт |
| --- | --- | --- | --- |
| 1 | `course-tournament-private-rules.test.js` (пишет 0 документов) | **PASS**: 18/18 проб 403 — 14 коллекций `course_tournament_*` (включая 5.3 `links`/`downloads`), 3 пути qualification_calculations (checkpoint/player/session-dedup), admission-подколлекция; mountedRulesSha `d8cbdd82…`; elapsed ≈1.1 s (префлайт+пробы, без сидов) | `runs/rules-probe.json` |
| 2 | `course-tournament-http.test.js` | **PASS** 7/7 за 63.1 s (из них ≈60 s — реальное ожидание окна квалификации): отказ всех 6 callables без auth (+ identity-префлайт :5001); create-only сид 92 документов (80 вопросов); prepare-replay ровно 60 EASY+20 HARD; реальный CREATE с резервом 12 боксов, неизменяемым funding/ledger и идентичным replay; отказы worker-капабилити/owner-spoof/чужой сессии; прямые чтения приватных документов под клиентским токеном — 403; admission download → START → ANSWER с replay и одноразовой записью ответа | `runs/http-journey.json` |
| 3 | `mvp-qualification-firestore-test.js --case=small --execute` | **PASS** 7/7 за 8.9 s, peakRss 142 MB: injected-аборты CHUNK/PROOF/PUBLISH атомарны, курсор pending-очереди сохраняется, полный когортный LS-ранг совпадает, повторы операций и контроллера не двигают деньги (financialDigest `3f9a13bd…`); отпечатки публикации — только формат (см. адаптации) | `runs/bulk-qualification-small.json` |
| 4 | `mvp-qualification-calculation-firestore-test.js --case=small --execute` | **PASS** 11/11 за 6.9 s, peakRss 137 MB: аборты CHUNK/PROOF/CALC_REDUCE/CALC_SOLVE/CALC_PROJECT/CALC_PUBLISH/PUBLISH атомарны; опубликован schemaVersion 2 с пином: `formsFingerprint` = контрактному, `verifiedRunsFingerprint` = перестроенному из frozen-входов player-stream-chain; независимый тай-оракул равных счётов; replay сохраняет ранг и деньги | `runs/bulk-calculation-small.json` |
| 5 | `course-tournament-lifecycle-demo.test.js` | **PASS** 9/9 за 120 s (310 HTTP-вызовов, 548 транзакций, 1824 записи под скоупом, 53 query-read, 12 sweeps, 2 injected aborts — CLOSE и ADVANCE_MATCH; peakRss 165 MB) | `runs/lifecycle-full.json` |
| 6 | Тот же скрипт, повторяемость | **PASS** 9/9 за 117 s — второй прогон с СВЕЖИМ namespaced runId (`lc_b7ddb58e…` после `lc_0f920429…`) воспроизводит; повторяемость достигается уникальными неймспейсами прогона, а не переигрыванием тех же имён | `runs/lifecycle-repeat.json` |
| 7 | `cd functions && node run-tests.js` | **111/111** сюит зелёные (гейт спеки: ≥110) | `runs/gate-functions-node.tail` |

## Новый скрипт-склейка непрерывности: `scripts/course-tournament-lifecycle-demo.test.js`

Закрывает ровно те дыры, которые принятые харнесы не покрывают: один непрерывный прогон полного
жизненного цикла + крэш-рекавери на границах фаз + вырожденные размеры 0/1/2 (план §4). Реальное
HTTP покрывает create/admission/квалификацию (4 игрока × 3 попытки EASY по HTTP, 240 ответов с
`{type:"single-choice", selected:"b"}`, 12 SETTLED-прогонов); всё после фиксированного каттаффа
исполняется теми же точками входа контроллера, что и callables, под доверенным логическим
таймером рига (тот же шов, что и в принятых bulk-ригах — реальное время не может ждать 7 суток;
HTTP-приём остаётся за `course-tournament-http.test.js`). Записи под fail-closed скоупом
(события/сессии/владельцы прогона; квитанции — по синтетическому владельцу; ledger — по
владельцу и префиксу), финансовый дайджест снимается между КАЖДОЙ фазой. Проверки внутри:

- CREATE replay идентичен и не двигает деньги; START/ANSWER/PROPOSE replay идентичны; reconnect
  READ не перезапускает и не двигает часы.
- Крэш-рекавери на ДВУХ границах: injected `HARNESS_ABORT` (1) в транзакции, пишущей `ctw_close_*`
  квитанцию → событие остаётся QUALIFICATION, опубликованный замороженный ранг цел, деньги не
  тронуты; (2) в транзакции settlement-квитанции `ctw_match_*` (ADVANCE_MATCH) → нет исхода
  матча и движения денег; в обоих случаях повторный sweep переиспользует ту же перманентную
  операцию и коммитит РОВНО ОДИН раз (уникальность matchId в outcomes ассертится).
- Истина сева: entrants/seeds/паринги сетки — это опубликованный замороженный рейтинг
  (top16 по местам; размер 4: 1v4 и 2v3; размер 2: 1v2), не просто консервация множества.
- Основное событие: BRACKET (сетка 4: 2 полуфинала + финал), согласование слотов (PROPOSE×2 →
  CONFIRM×2 → READY×2 → STARTED), сыгранные матчи через swept ADVANCE_MATCH, **FINISHED**,
  эскроу `{reserved:12, paid:10, returned:2, remaining:0}` (консервация), плейсменты
  champion/runner-up/third-upper/third-lower оплачены ровно по строкам v1 (5/3/1/1), балансы
  точны.
- Вырожденные: 0 участников и 1 квалифаер → **CANCELLED** `FEWER_THAN_TWO_QUALIFIERS`; эскроу
  кортежем (reserved,paid,returned,remaining) = `{12,0,12,0}` — полный возврат 12; автору
  возвращается ровно его баланс до CREATE; 2 участника → полный финал до **FINISHED**, эскроу
  `{12,8,4,0}` в том же порядке. Все четыре события терминальны с `remaining = 0`; организатор
  200 → 182 (ровно оплаченные призы двух FINISHED-событий); `synthetic.d0` — stray-credit-canary,
  нулевой баланс подтверждён.

## Диагностика неудачных прогонов разраб. раунда (команда одна — общая шапка выше)

Разраб. раунд зафиксировал 2 неудачных прогона bulk-qualification и 9 неудачных исполнений
lifecycle-скрипта (каждый — баг самого скрипта, не прод-кода); сырые FAIL-выводы не
сохранялись, поэтому ниже — диагнозы как упорядоченный список исправлений, а не построчная
атрибуция к конкретным retained-фикстурам (десять осиротевших `lc_…` — следы этих итераций,
все перечислены в «Границах»).

| Прогон | Исход | Причина (диагноз разраб. раунда) → правка |
| --- | --- | --- |
| bulk-qualification №1 | FAIL | CREATE (5.3) минтит `course_tournament_links/{eventId}`; allowlist скоупа 5.4-эпохи отказывал → путь ссылки добавлен в fixture-scope обоих bulk-ригов |
| bulk-qualification №2 | FAIL | с 5.5 CLOSE публикует ВЕРСИОНИРОВАННУЮ калькуляцию (schemaVersion 2), харнес перестраивал legacy-LS отпечатки → deep-equal LS-поверхностей + формат-проверка версионных; перепин устаревшего inline-SHA контроллера (`f1ce23d0…` → `ed9692fc…`) |
| lifecycle №1–9 | FAIL ×9 | только баги скрипта: отсутствие endpoint в allowlist; `outcomes: []` vs undefined; operation-id из matchId ломал чарсет идентификаторов (реальные matchId — JSON-ключи); первый READY возвращает WAITING_FOR_READY; id сессий должны быть зарегистрированы в скоупе ДО создающей транзакции; ledger — вложенный путь; вырожденные CREATE должны идти от автора-владельца банка |
| review-раунд (все семьи) | FAIL ×0 | все прогоны таблицы выше зелёные с первого исполнения после правок |

## Границы (bounds) и мусор

- Bounds в каждом PASS-выводе (`resource` + `bounds`): календарное время, heap/RSS/peakRss,
  число HTTP-вызовов/транзакций/записей/запросов/sweeps. Сводка: rules-probe ≈1.1 s;
  http-journey 63.1 s (≈60 s — реальное ожидание окна); bulk small 6.9–8.9 s / 137–142 MB;
  полный цикл 120 s / 165 MB, повтор 117 s (310 HTTP, 548 транзакций, 1824 записи, 12 sweeps,
  2 injected aborts). Устаревшие числа разраб. раунда (96/103 s, 11 sweeps, 1 abort) заменены
  свежими после обзорного раунда.
- Повторяемость — свежими namespaced runId каждого прогона (`qs_/qcalc_/ct_/lc_` + UUID),
  НЕ переигрыванием тех же имён; префлайт каждого харнеса отказывается сеять при коллизии
  неймспейса. Повтор lifecycle-прогона №6 — отдельный runId, полный PASS.
- Синтетические фикстуры НЕ вычищаются — retained by design, неймспейс каждого прогона
  уникален, прод-данным пересечение невозможно; это СОЗНАТЕЛЬНО заменяет формулировку спеки
  «teardown/restore … so a second run reproduces»: воспроизводимость обеспечена неймспейсами,
  teardown отменён владельцем в пользу retained-фикстур (диагностика). Осиротевшие фикстуры
  неудачных прогонов разраб. раунда (диагностика, не мусор прод-данных): bulk `qs_b2965393…`,
  `qs_c33e59e2…`; lifecycle `lc_ed6bbb8b…, lc_0054c72a…, lc_e0caed3d…, lc_dded38f6…,
  lc_aacfec81…, lc_531a7f69…, lc_99490caf…, lc_36563953…, lc_9b468152…, lc_07815bde…`
  (десять), плюс retained-фикстуры всех успешных прогонов обоих раундов.
- Проводка рига (п.1–2) переживает сессию только пока живы контейнеры; команды пересоздания
  выше; риг оставлен работающим.

## Deferred-followups: закрыто / честно передано владельцу

1. **5.3 rules-probe defer — ЗАКРЫТО**: живая 403-проба прошла на поднятом риге (18/18, прогон №1
   обоих раундов).
2. **QS_SOURCE_MANIFEST — ЗАКРЫТО**: манифест перегенерирован для текущих источников
   (`/workspace/infrastructure/android-toolchain/firebase-state/qs-source-manifest.json`,
   0600, вне репозитория). Процедуры-генератора в репозитории нет — формат определён самим
   ассертом харнеса (плоский JSON {файл: sha256} ровно по 7 файлам); команда регенерации:
   sha256 по списку `["course-tournament-controller.js","tournament-qualification-scan.js",
   "tournament-qualification-calculation.js","tournament-qualification-compact.js",
   "tournament-qualification-version.js","tournament-qualification-freeze.js","tournament-service.js"]`
   от `/repo/functions/<файл>`. Оба bulk-рига прошли против него в обоих раундах. Сопутствующий
   stale-пин `course-tournament-controller.js` в qualification-риге перепинен (см. адаптации).
3. **Arena fixture drift (2 красных platform-теста) — владельцу, с точными командами**:
   `FirebaseArenaTicketRemoteDataSourceTest.fixtureHashesAndCallableNamesAreBoundToActualServerSources`
   и `FirebaseArenaLessonCatalogRemoteDataSourceTest.generatedPublicContractAndCallableNameRemainBoundToActualProducer`
   (`sgw ./gradlew :platform:firebase:test` для воспроизведения). Фиксация 2026-10-08 в силе:
   JSON-фикстуры пинят SHA-256 JS-продюсеров; тулза захвата payload'ов в репозитории ОТСУТСТВУЕТ
   (сессия-локальная 2026-09-07), поэтому перезапись хешей без перезахвата вымыла бы реальную
   смену продюсера — фиксация требует процедуры захвата; подтверждено красным в 5.9
   (2026-10-17, коммит ee50a40c). Тяжёлый Gradle-прогон в этой смене повторно не запускался —
   свежее подтверждение 5.9 на почти этом HEAD.
4. **Large-кейсы bulk-ригов (167/502 игроков) — отдельное владельческое одобрение** по их
   собственному плану («Large requires separate approval after small PASS»); small PASS
   получен в обоих раундах, large не запрашивался.

## Осталось / риски (честно)

1. Смоук-гейт `:platform:firebase` в этой смене не гонялся (тяжёлая Gradle-задача; server-story
   без Kotlin-правок; свежий базлайн 5.9 от 2026-10-17 зафиксировал те же 2 известных красных).
2. Форвардер :5001 и запись `/etc/hosts` — хостовая проводка этой смены: если firebase-контейнер
   пересоздастся с другим IP, нужно обновить строку hosts и (при перезапуске) форвардер (команды
   выше). Эмуляторы и их данные не переконфигурировались и оставлены поднятыми.
3. `scripts/course-tournament-lifecycle-demo.test.js` — склейка непрерывности, не замена
   принятых харнесов: post-cutoff фазы идут под доверенным логическим таймером через те же точки
   входа контроллера (шов принятых bulk-ригов); реальное HTTP-покрытие — прогон №2.
