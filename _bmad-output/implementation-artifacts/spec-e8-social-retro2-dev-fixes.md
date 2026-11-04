---
title: 'E8 social retro round 2: dev action items of the 2026-10-28 re-rack'
type: 'bugfix'
created: '2026-10-28'
status: 'done'
baseline_revision: '8a370756'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['edge-case-hunter', 'verification-gap', 'blind-hunter']
review_loop_iteration: 0
followup_review_recommended: false
context: []
warnings: ['multiple-goals', 'oversized']
deferred:
  - summary: >-
      Наблюдение очереди агрегировано по ссылке серии: ждущий ряд публикации
      зажигает voteQueued на экране голосования (нет различия операций в
      OutboxEntitySync Queued) — редкий угол (автор смотрит свою серию, пока
      публикация ещё в очереди), самолечится доставкой.
    evidence: |-
      DefaultPollsComponent.applyOutboxSync ставит voteQueued по любому pending-ряду
      серии; ряды голоса и публикации делят entity_ref polls.SERIES:<id>.
      Разделение требует op-поля в наблюдении или отдельного ключа — решение за владельцем.
    location: >-
      android/feature/internet/social/presentation/.../DefaultPollsComponent.kt (applyOutboxSync)
    severity: low
  - summary: >-
      Гонка ремонта с параллельным писателем: вставка того же ключа между
      findByMutationId и rewriteMutationKey уронит проход громко на уникальном
      индексе; следующий проход строку пропустит (заживает само, без порчи данных).
      Ужесточение (ловля constraint-ошибки) — решение владельца.
    evidence: |-
      RoomOutboxStore.rewritePendingKeys — check-then-act; KDoc OutboxDao
      зафиксировал точную семантику (громкий отказ + самолечение). Запусков
      ремонта — один на синк, выборка старых форм пустеет после первого прохода.
    location: >-
      shared/core/persistence/.../RoomOutboxStore.kt (rewritePendingKeys)
    severity: low
  - summary: >-
      Пинов упорядочивания и композиции нет: порядок repair-Syncable перед
      OutboxSyncable держится на позиции в списке SyncModule; «настоящее правило +
      настоящее хранилище» вместе не прогоняются (правило — с фейком, хранилище —
      с зеркалом правила).
    evidence: |-
      SyncModule listOf(...) — конфигурация позицией; тест композиции продублировал
      бы PollVoteKeyRepairTest + OutboxKeyRepairPersistenceTest. Решение о пине —
      за владельцем.
    location: >-
      apps/android-next/.../di/SyncModule.kt (List<Syncable>)
    severity: low
---

<intent-contract>

## Intent

**Problem:** Перевёрстка ретро эпика 8 (RETROSPECTIVE.md, «Перевёрстка 2026-10-28», accepted-with-open-items) оставила шесть dev-класс пунктов: (4) queued-ряды `polls.SUBMIT_VOTE` со старым ключом до `402c5882` (без участника) отказывают foreign и карантинятся молча; (7) UI опросов не наблюдает outbox — отказ/карантин ряда оставляет замок «отправлено…» навсегда; (8) карточка обещает продолжение с первого неотвеченного, а навигация открывает вопрос 1; (5) список серий молча обрезается на 50-й, вопреки AD-8; (9) поправка §2 живёт в контракте двумя конкурирующими формулами, KDoc/осевые литералы теста и ключ journey-инструмента — старой формы; плюс два тестовых долга фикс-пака: три ветки `applyPublishPollSeries` без тестов и демонстрация атомарности публикации под контеншном.

**Approach:** Пять независимых закрытий: громкий `SOURCE_TOO_LARGE` на потолке скана серий; общее ядерное умение переписать ключи pending-рядов одной операции + опросное правило дописывания `-u-<ownerUid>` (не Room-миграция: data-only миграция запрещена гейтом `MigrationCoverageTest` по identityHash); наблюдение `OutboxStore.observeEntity` на поверхности опросов (замки/бейдж/показ отказа) + resume-индекс из `answeredCount`; гигиена формулы §2 до одной (`-u-<uid>`, как в коде и тесте) в контракте/KDoc/тесте/journey-инструменте; тесты трёх веток публикации и ретрай-семантики контеншна на `retryTransactionOnce` стаба (честная замена строгого эмулятора — остаётся открытым у владельца).

## Boundaries & Constraints

**Always:**
- Ключи и имена операций: форма новой голосовой формулы — ровно как в коде писателя и тесте: `polls-SUBMIT_VOTE-series-<id>-r<rev>-q<idx>-u-<uid>` (`PollOutboxWriters.voteSourceId`, `-u-` дефис).
- Ремонт ключей трогает только pending-ряды (`WAITING`/`WAITING_PRECONDITION`); подтверждённые/карантинные/конфликтные не трогаются; занятый целевой ключ (уникальный индекс `mutation_id`) пропускается, а не роняет проход.
- Отказ из наблюдения outbox — поимённый и локализованный (AD-15): состояние ряда, а не разбор `lastError` (у `OutboxEntitySync.Queued` его и нет).
- Серверные вью и приёмник: форма ответов `list` не меняется, меняется только громкость отказа на потолке.
- Гейты волны: `functions npm test` = `node run-tests.js`; gradle затронутых модулей через sgw (JAVA_HOME=/opt/jdks/jdk-27); ktlint файлов изменения чист, предсуществующая корзина атрибутируется.

**Never:**
- Не трогать `functions/index.js` (файл разделён с незакоммиченной работой соседей, 111 строк M) — все серверные тесты пишутся против закоммиченного поведения.
- Не трогать hitl-пункты (композитные индексы, device-пруфы, UX-решения), рефакторный пункт (DefaultQuestCreateComponent/разгрузка index.js), пуш 8a370756 и чужие staged-удаления (~6120 D в индексе — только pathspec-коммиты своих файлов).
- Не менять статусы историй в `tickets.toml` (фикс-пак, не пере-приёмка); не деплоить; не поднимать лимиты gradle.
- Не строить «строгий эмулятор» на заглушке и не выдавать его за закрытие open item 8.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Серий ровно 50 (потолок) | `listPollSeries`, любой pageSize | Громкий `SOURCE_TOO_LARGE` всем, без обрезанной двадцатки | Поимённый отказ |
| Серий 49 | `listPollSeries` | Обычный ответ, slice по pageSize | No error expected |
| Старый ключ в очереди | pending-ряд `polls-SUBMIT_VOTE-series-X-r1-q0`, owner `u1` | После ремонта ключ `…q0-u-u1`; тело/состояния/попытки не тронуты | No error expected |
| Ряд уже новой формы / чужая операция / QUARANTINED | Ремонт | Без изменений; повторный проход — 0 | No error expected |
| Голосовый ряд в QUARANTINED | Экран серии открыт | Замок снят, поимённое сообщение карантина; в списке бейдж «не отправлено» | Видимый отказ |
| Серия с answeredCount=2 из 3 | Клик по карточке | Открывается вопрос index 2 (первый неотвеченный) | No error expected |
| Повтор намерения (current = expected+1, то же содержание) | Публикация expected=0 поверх r1 | Возврат r1 без второй revision | No error expected |
| expected>0 на отсутствующей серии | Публикация | `failed-precondition` + `POLL_SERIES_REVISION_CONFLICT … current 0`, документа нет | Поимённый конфликт |
| Два издания поверх одного базиса | Второе коммитится пока первое в полёте | Ретрай второго читает свежий базис → поимённый конфликт; ровно одна новая revision | Поимённый конфликт |

</intent-contract>

## Code Map

- `functions/poll-views.js:156-195` -- `list`: скан серий `:161` `limit(LIMITS.maxSeries)`; образцы громкого отказа — `:179`, `:221` (сканы голосов). Сюда отказ на `docs.length >= LIMITS.maxSeries`.
- `functions/poll-views.test.js` -- node:test поверх настоящего `index.js` + стаб; `freshWorld()`/`publish()` — образец мира; потолочные тесты добавляются сюда.
- `functions/poll-vote-handler.test.js:64-71` -- `publishKey`/`voteKey` хелперы (voteKey — старая формула, гигиена); `:83-113` приёмник/фикс-часы; `store.retryTransactionOnce` стаба (`functions/_firestore-stub.js:114,331-340`) — управляемый ретрай транзакции для контеншн-теста; ветки публикации — `functions/index.js` (HEAD) `applyPublishPollSeries`: повтор намерения «current == expected+1», конфликт на отсутствующей серии, `sameContent` поверх `normalizePollSeriesDraft` (канонизирует ключи вопросов/вариантов).
- `scripts/demo-fixtures/poll-journey.js:133-136,187-201` -- `voteKey` старой формулы + `vote-as`/`replay-as`.
- `_bmad-output/mvp-candidate/epic-social-features/ux-o7-poll-contract.md` §2 -- буллет «ПОПРАВКА 2026-10-18» вставлен внутрь нормативного буллета — две конкурирующие формулы; первичная формула уходит в историческую сноску.
- `shared/core/outbox/.../OutboxOperations.kt:162-171` -- KDoc `SUBMIT_POLL_VOTE` со старой формулой (трипл).
- `shared/core/outbox/src/jvmTest/.../UxO7PollVoteIdentityContractTest.kt:11-22,74-97` -- KDoc «тройка» и осевые литералы `series-poll-77-r2-q2` и т.п. без оси участника; литерал ключа `:31` уже новой формы.
- `shared/core/outbox/src/commonMain/.../OutboxStore.kt` -- контракт хранилища; `observeEntity` (`:71-74`) — источник наблюдения; прецедент узкого ядерного интерфейса — `OutboxConsentRequeue.kt` (отдельный интерфейс, Room-реализация, композиция в корне).
- `shared/core/persistence/.../OutboxDao.kt` -- `due`/`requeueConflictedTerms` — образцы запросов; `mutation_id` UNIQUE (`OutboxEntity`); состояния строкой `'WAITING'`/`'WAITING_PRECONDITION'`.
- `shared/core/persistence/.../RoomOutboxStore.kt` -- `:24-38` implements-список (сюда новый интерфейс); jvm-харнесс настоящего SQLite — `OutboxConflictPersistenceTest.kt` (Room.inMemoryDatabaseBuilder + BundledSQLiteDriver).
- `shared/core/persistence/src/jvmTest/.../MigrationCoverageTest.kt:80-104` -- гейт «соседние схемы не делят identityHash» — причина, по которой data-only Room-миграция невозможна (записать в Implementation Notes).
- `shared/feature/internet/social/data/.../outbox/PollOutboxWriters.kt` -- `seriesRef`/`voteSourceId` (форма `-u-<uid>`); соц-данные уже зависят на core:outbox, koin-модуль `di/SocialDataModule.kt`.
- `android/feature/internet/social/presentation/.../component/DefaultPollsComponent.kt` -- флаги в памяти: `voteQueued`/`publishQueued` (`:128,350`), `onOpenSeriesClick` хардкод `questionIndex = 0` (`:77-90`), слепые опросы подтверждения (`:430-472`); прецедент потребления outbox презентацией — `TournamentSessionPlayComponent.kt:369` (data-интерфейс в конструкторе).
- `android/.../screen/PollsScreen.kt:103-133` -- карточка серии: надпись `polls_progress`/кнопка `polls_question_of answeredCount+1`; сюда бейдж «не отправлено».
- `android/.../presentation/src/test/.../DefaultPollsComponentTest.kt` -- fakes + TestCoroutineScheduler; сюда тесты resume-индекса и наблюдения.
- `apps/android-next/.../di/SyncModule.kt:126-131` -- биндинги `RoomOutboxStore`/`OutboxStore`/`OutboxConsentRequeue` (сюда биндинг нового ядерного интерфейса); `:316-343` `List<Syncable>` — `OutboxSyncable` стоит вторым; адаптер ремонта — перед ним (анонимный `Syncable` — прецедент `:336-341`).
- `apps/android-next/src/test/.../KoinModuleWiringTest.kt` -- поднимает модули с fake `OutboxStore` (`:641`); прогнать после новых биндингов.

## Tasks & Acceptance

**Execution:**
- [x] `functions/poll-views.js` -- в `list` после скана серий: `if (seriesRows.docs.length >= LIMITS.maxSeries) fail("SOURCE_TOO_LARGE")` с комментарием AD-8 -- находка 5 (громкое усечение, как у сканов голосов; настоящий курсор не строится — у модуля нет курсорной системы).
- [x] `functions/poll-views.test.js` -- тесты: 49 серий — список обычный; 50-я — `SOURCE_TOO_LARGE` до voice-сканов -- RED до правки `list`.
- [x] `functions/poll-vote-handler.test.js` -- тесты трёх веток: (а) повтор намерения expected=0 поверх r1 с тем же содержанием → `revisionId: 1`, без бампа; (б) expected=1 на отсутствующей серии → `failed-precondition` + `POLL_SERIES_REVISION_CONFLICT … current 0 (series does not exist)`, документа нет; (в) то же содержание с другим порядком ключей в теле → no-op (канонизация `normalizePollSeriesDraft` делает сравнение порядконезависимым — закрепить фактическое поведение с комментарием про deferred-посылку ревью) -- open item 9.
- [x] `functions/poll-vote-handler.test.js` -- контеншн-тест: публикация A(expected=0) в полёте, `retryTransactionOnce` коммитит публикацию B (r1→r2), ретрай A читает r2 → `failed-precondition` + `POLL_SERIES_REVISION_CONFLICT`; ровно одна новая revision, authorUid не перевернулся -- open item 8 (замена: ретрай-семантика SDK на стабе, не строгий эмулятор — так и звать в отчёте).
- [x] `functions/poll-vote-handler.test.js` + `scripts/demo-fixtures/poll-journey.js` -- `voteKey` обеих сторон перевести на формулу с `-u-<uid>` (journey: параметр uid уже есть в `vote-as`) -- гигиена §2 (находка 9, инструментальная половина).
- [x] `_bmad-output/mvp-candidate/epic-social-features/ux-o7-poll-contract.md` -- §2: одна нормативная формула `sourceId = "series-<seriesId>-r<revisionId>-q<questionIndex>-u-<uid>"`; первичная (трипл) — в историческую сноску с датой и причиной поправки; буллет поправки расправить в основной текст -- находка 9 (документная половина; кивок владельца — отдельный открытый вопрос, не этот).
- [x] `shared/core/outbox/.../OutboxOperations.kt` -- KDoc `SUBMIT_POLL_VOTE`: формула с осью участника -- находка 9.
- [x] `shared/core/outbox/src/jvmTest/.../UxO7PollVoteIdentityContractTest.kt` -- KDoc («четверка идентичности серия/revision/вопрос/участник»), осевые литералы осей в новой форме (`…-u-participant-N`), имена тестов без «triple» -- находка 9.
- [x] `shared/core/outbox/src/commonMain/.../OutboxKeyRepair.kt` (новый) -- узкий интерфейс `OutboxKeyRepair { suspend fun rewritePendingKeys(operation: String, repair: (ownerUid: String, mutationId: String) -> String?): Int }` с KDoc AD-2 (правило у фичи, механика у ядра; только pending; занятый ключ — пропуск) -- находка 4, ядерная механика по образцу `OutboxConsentRequeue`.
- [x] `shared/core/outbox/src/commonTest/.../FakeOutboxStore.kt` -- реализация `rewritePendingKeys` (фильтр pending, вызов правила, защищённая перезапись) -- чтобы ядерные и соседние fakes оставались совместимы.
- [x] `shared/core/persistence/.../OutboxDao.kt` -- запросы: выборка `(id, mutation_id, owner_uid)` pending-рядов операции; защищённый `UPDATE … SET mutation_id = :to WHERE id = :id AND mutation_id = :from` -- находка 4.
- [x] `shared/core/persistence/.../RoomOutboxStore.kt` -- `implements OutboxKeyRepair`: цикл по выборке, правило фичи, защищённый апдейт, счётчик -- находка 4.
- [x] `shared/core/persistence/src/jvmTest/.../OutboxKeyRepairPersistenceTest.kt` (новый) -- настоящий SQLite: старый WAITING/WAITING_PRECONDITION переписан, QUARANTINED/чужая операция/новая форма не тронуты, коллизия пропущена, повтор — 0 -- находка 4.
- [x] `shared/feature/internet/social/data/.../outbox/PollVoteKeyRepair.kt` (новый) + `commonTest/.../PollVoteKeyRepairTest.kt` -- правило: операция `SUBMIT_POLL_VOTE`, ключ без суффикса `-u-<ownerUid>` → дописать; с суффиксом → null; тесты правила на собственном fake -- находка 4, опросное правило.
- [x] `apps/android-next/.../di/SyncModule.kt` -- биндинг `single<OutboxKeyRepair> { get<RoomOutboxStore>() }`; в `List<Syncable>` перед `OutboxSyncable` — анонимный Syncable, зовущий `PollVoteKeyRepair.repairQueuedVotes()` (идемпотентный проход каждого синка) -- находка 4, включение в существующий шов.
- [x] `shared/feature/internet/social/data/.../outbox/PollOutboxRepository.kt` (новый) + koin -- `observe(ownerUid, seriesIds): Flow<List<OutboxEntitySync>>` поверх `OutboxStore.observeEntity` + `PollOutboxWriters.seriesRef` -- находки 7, клиентская половина.
- [x] `android/.../component/DefaultPollsComponent.kt` + `di/SocialPresentationModule.kt` -- новая зависимость `outboxSync: PollOutboxRepository`; наблюдение открытой серии и списка: pending → флаги «отправлено» честно, терминал (QUARANTINED/CONFLICT) → сброс флага + поимённое сообщение (ресурсы), None → сброс без ошибки; `onOpenSeriesClick` открывает `answeredCount.coerceIn(0, questionCount-1)`; job наблюдения перезапускается со сменой набора id и гасится закрытием серии -- находки 7 и 8.
- [x] `android/.../uistate/PollsUiState.kt`, `screen/PollsScreen.kt`, `res/values/strings.xml` -- состояние наблюдения в UI-state; бейдж «Не отправлено» на карточке (pending и терминал — разный цвет), строки карантина/конфликта/бейджа -- находки 7.
- [x] `android/.../presentation/src/test/.../DefaultPollsComponentTest.kt` -- тесты: resume-индекс (answeredCount=2 → вопрос 3-й); карантин ряда снимает замок и показывает отказ; pending ряда держит честный флаг; бейдж в списке -- находки 7, 8.

**Acceptance Criteria:**
- Given 50 опубликованных серий, when участник открывает список, then `SOURCE_TOO_LARGE` поимённо; при 49 — обычный ответ.
- Given queued-ряд голоса со старым ключом и owner `u1`, when синхронизация делает проход ремонта, then ряд уезжает под `…-q0-u-u1`; повторный проход рядов не находит.
- Given голосовый ряд серии в QUARANTINED, when участник открывает серию/список, then замка «отправлено» нет, назван отказ карантина, карточка помечена «Не отправлено».
- Given серия с answeredCount=2 из 3, when клик по карточке, then открыт вопрос index 2.
- Given в контракте §2 одна формула, when grep по документу/тесту/journey-инструменту, then старой формы (`…-q<idx>` без `-u-`) нет ни в одной нормативной позиции.
- Given контеншн двух изданий поверх r1 (ретрай-семантика стаба), when оба доезжают, then ровно одна новая revision и второй получает `POLL_SERIES_REVISION_CONFLICT`.

## Implementation Notes

- Субагенты воркфлоу в этом харнессе недоступны — исследование и реализация выполняются самим агентом; разделение с соседями по волне: свои файлы чисты (проверено `git status --porcelain` на VPS), `functions/index.js` — M соседа (111 строк), не трогается.
- Пункт 5 открытых items ретро (композитные индексы, hitl), пункт 6 (device-пруфы, владелец), пункт 7 (refactor, отложено), пункт 10 (процесс) — вне этой спеки.
- **Закрытие смены 2026-11-03/04 (доводящая смена):** серверная половина (находка 5 целиком, тесты веток/контеншна, гигиена journey) закоммичена соседней волной в `43f9358e` («усыновление хвоста»); клиентская половина оставалась незакоммиченной со смены 2026-10-28 — её довели до конца: верификация обеих ног, ревью тремя линзами, триаж с патчами, коммит.
- Ревью нашло и верификация подтвердила два реальных красных теста в паке (оба дефекта тестовые, правило и хранилище верны): (1) наивное правило в `OutboxKeyRepairPersistenceTest` («всегда дописать») противоречило собственным утверждениям — второй проход и строка-оккупант переписывались; заменено на идемпотентную форму настоящего правила; (2) `PollVoteKeyRepairTest` падал из-за порядка аргументов kotlin.test `assertNull(actual, message)` — строка-сообщение первой позицией превращалась в «actual», и оба assertNull валились всегда, даже при верном правиле; вызовы приведены к правильному порядку, похожий-uid случай заменён точным ожиданием дописывания своей оси.

## Spec Change Log

- 2026-11-04 (доводящая смена): frontmatter `review: thorough`, линзы; тестовое правило персистентного теста приведено к идемпотентной форме настоящего (KDoc у правила в компаньоне); `assertNull` для похожего uid заменён точным ожиданием дописывания. Frozen-блок не тронут.

## Review Triage Log

### 2026-11-04 — Review pass (линзы edge-case-hunter с deletion/claims, verification-gap, blind-hunter)

- intent_gap: 0
- bad_spec: 0
- patch: 7
- defer: 3
- reject: 7
- addressed_findings:
  - `[high]` `[patch]` `PollVoteKeyRepairTest`: оба `assertNull` вызывались в порядке (message, actual) — у kotlin.test сигнатура (actual, message), строка-сообщение становилась «actual» и валила тест всегда, при верном правиле; вызовы переведены на правильный порядок, похожий-uid случай закрыт точным ожиданием `NEW_KEY + -u-u1-u` (правило корректно дописывает ось своего владельца). Подтверждено живым падением обеих ног `:shared:feature:internet:social:data` до патча и сообщением XML («actual value is not null» с текстом сообщения в качестве actual).
  - `[medium]` `[patch]` `OutboxKeyRepairPersistenceTest`: наивное правило («всегда дописать») делало второй проход и проход с оккупантом переписывающими (2 и 1 вместо 0); правило в компаньоне заменено на идемпотентную форму `PollVoteKeyRepair.migratedKey` (null при своём суффиксе). Подтверждено живым падением `:shared:core:persistence:jvmTest` до патча.
  - `[medium]` `[patch]` `FakeOutboxStore.rewritePendingKeys`: KDoc заявлял «занято — пропуск», реализации не было (фейк мог породить два одинаковых ключа — недостижимо в Room); добавлена проверка занятости по снапшоту.
  - `[low]` `[patch]` `DefaultPollsComponent.onOpenSeriesClick`: `coerceIn(0, questionCount-1)` бросал `IllegalArgumentException` на сводке с 0 вопросов (верхняя граница отрицательная); верхняя граница теперь `coerceAtLeast(0)`.
  - `[medium]` `[patch]` `DefaultPollsComponent.restartOutboxObservation`: смена аккаунта с тем же набором серий оставляла наблюдателя прежнего владельца, выход из аккаунта (`uid == null`) не гасил job; добавлен `observedUid` в ключ перезапуска и явное гашение с очисткой `seriesSync`.
  - `[low]` `[patch]` `SyncModule` repair-Syncable: `runCatching` глотал `CancellationException`; добавлен ретроу (жанр epic-7-retro-item-9).
  - `[low]` `[patch]` `OutboxDao.rewriteMutationKey` KDoc: перевёрнутое объяснение («индекс отвалил проход, а не одну строку») заменено точной семантикой (пропуск видимой занятости; параллельная вставка роняет проход громко и заживает следующим проходом).
  - `[low]` `[patch]` `OutboxKeyRepairPersistenceTest:60`: перепутан порядок expected/actual в `assertEquals` (сообщение о расхождении печаталось инвертированным).
  - `[low]` `[patch]` `DefaultPollsComponentTest`: добавлен тест CONFLICT-ветки (терминален, поименован) и None-ветки (замок снят, отсутствие ряда доведено до карточки) — ранее терминал проверялся только карантином.
- deferred (frontmatter): агрегация наблюдения по серии без различия операций (publish-ряд зажигает voteQueued на экране голосования — редкий угол, самолечится); гонка ремонта с параллельным вставщиком (громкий отказ + самолечение, KDoc зафиксировал точную семантику); пины порядка Syncable и композиции «настоящее правило + настоящее хранилище».
- reject: строки только в default-локали — модуль однолокален (res/values один, все строки модуля русские); «вечный тупик карантинных рядов» — осознанное решение спеки (не воскрешать, отказ показывает наблюдение); «наблюдение только загруженной страницы» — Design Notes спеки (≤ maxPageSize); SQL-литералы состояний — стиль файла; UI-импорт data-типов — прецедент `TournamentSessionPlayComponent.kt:369` из Code Map; «контракт не обновлён» — документная половина §2 закоммичена в `43f9358e` (линза видела только дифф клиентской половины); resume-тест «другие значения, чем AC» — формула та же, AC покрывается + ветвь clamp теперь закрыта патчем.

## Design Notes

- Ремонт ключей — не Room-миграция: `MigrationCoverageTest` запрещает соседним схемам делить identityHash, то есть data-only версию не экспортировать. Правильный шов — механика в ядре (как `OutboxConsentRequeue`), правило во владеющей фиче, вызов — участник обычной синхронизации перед очередью: идемпотентно, после первого прохода — пустая выборка.
- AD-2 при переписывании ключа: уедет ли переписанный ключ вторым применением — нет: старый ключ либо никогда не доходил (ряд pending), либо его приёмка уже удаленила ряд; тройка §3 на сервере и permanent-receipt защищают от второго голоса при всех порядках потери квитанции. Уже карантинные ряды (чужой отказ по старому ключу) не воскресают — их показывает наблюдение поверхности.
- Наблюдение списка — combine per-entity flows по текущему набору id (≤ maxPageSize), а не отдельный запрос ядра: `OutboxStore` не знает «все ряды серии», ссылку собирает фича (AD-7).
- Контеншн-тест честно называется заменой: `retryTransactionOnce` воспроизводит документированную ретрай-семантику SDK (бросить записи конфликтного прогона, перечитать свежий базис), но не реальный параллелизм; строгий эмулятор остаётся открытым у владельца до боевого сезона.

## Verification

**Commands (2026-11-04, доводящая смена; sgw, JDK 27, node из schoolquiz-functions-emu):**
- `docker exec -w /repo/functions schoolquiz-functions-emu node run-tests.js` -- **143/143 сюит PASS** (включая серверную половину пака: потолок списка, ветки публикации, контеншн, сентинелы).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :shared:core:outbox:allTests :shared:core:persistence:jvmTest :shared:feature:internet:social:data:allTests :shared:feature:internet:social:domain:allTests` -- **BUILD SUCCESSFUL** (прогон 1d). Прогоны 1a/1b/1c честно красные до патчей: наивное правило персистентного теста (2 падения) и kotlin.test `assertNull(actual, message)` в `PollVoteKeyRepairTest` (обе ноги) — найдены ревью, починены, перегнаны.
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :android:feature:internet:social:presentation:testDebugUnitTest` -- **BUILD SUCCESSFUL** на финальном дереве (прогон после ktlint-переоформлений; suite включает resume-индекс, queued/карантин, конфликт/None-ветки).
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :apps:android-next:testDebugUnitTest` -- 41 тест, 3 красных **предсуществующие** (suite был некомпилируем с 8.4/4.22/4.25 — прививки типов `PrivateQuizRunnerComponentFactory` (8.4, 2026-10-22), `SubmitErrorReportProposalUseCase` (4.22/4.26-проводка), push-строк `quest_proposal_received/decided` (4.25, 2026-10-28) — все до базлайна пака `8a370756`; известная средовая красная «it09e» из дайджеста 2026-11-01). Попутно починены блокировавшие компиляцию тестового source-set: `ConsentChainWiringTest.read()` возвращал File вместо String (пин F17, «первый настоящий прогон» — коммит 6dc356f5 прямо предупреждал) и дрейф `KoinModuleWiringTest` за `DefaultRootComponent(authRepository)` + `openLessonEarnings` (8.6) — с фейком-гостем. Паковые тесты android-next зелёные.
- ktlint -- файлы изменения чисты (presentation: блоки терминальных веток и бейджа переоформлены по правилу multiline-expression; `PollVoteKeyRepair` — body-on-signature). Предсуществующие корзины атрибутированы, не чинились: `shared/core/persistence` `QuestDraftEntity.kt:59` (>120 строк, паком не тронут), `apps/android-next/build.gradle.kts:65-66` (известная корзина спеки).
- RED-доказательство находки 5: poll-views.js от HEAD до правки в изолированной копии functions/ → потолочный тест красный; с правкой — зелёный (серверная смена, `43f9358e`).
