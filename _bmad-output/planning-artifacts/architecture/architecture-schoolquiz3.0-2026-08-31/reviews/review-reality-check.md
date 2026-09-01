# Ревью спайна синхронизации — линза ПРОВЕРКА РЕАЛЬНОСТИ

Документ: `_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-08-31/ARCHITECTURE-SPINE.md`
Дата проверки: 2026-08-31. Всё сверено по файлам рабочего дерева.

**Вердикт: ПРОХОДИТ С ЗАМЕЧАНИЯМИ.**

Таблица Stack сверена построчно — расхождений нет ни одного. Из ~25 утверждений спайна о текущем состоянии кода подтверждено 22, опровергнуто одно (AD-2), неточны два (AD-15, AD-19). Одно решение (AD-18) опирается на путь, который действующие `firestore.rules` запрещают. Одно правило (AD-1) в буквальном прочтении противоречит собственному списку.

---

## 1. Таблица Stack — сверка по каталогу версий

Источники: `gradle/libs.versions.toml`, `functions/package.json`.

| Строка спайна | В репозитории | Итог |
| --- | --- | --- |
| Kotlin 2.3.10 | `kotlin = "2.3.10"` | совпадает |
| AGP 8.11.0 | `agp = "8.11.0"` | совпадает |
| KSP 2.3.7 | `ksp = "2.3.7"` | совпадает |
| kotlinx-coroutines 1.10.2 | `kotlinx-coroutines = "1.10.2"` | совпадает |
| kotlinx-serialization 1.7.3 | `kotlinx-serialization = "1.7.3"` | совпадает |
| kotlinx-datetime 0.5.0 | `kotlinx-datetime = "0.5.0"` | совпадает |
| Decompose 3.1.0 | `decompose = "3.1.0"` | совпадает |
| Essenty 2.1.0 | `essenty = "2.1.0"` | совпадает |
| Koin 3.5.6 | `koin = "3.5.6"` | совпадает |
| androidx.room 2.7.0 | `androidx-room = "2.7.0"` | совпадает |
| androidx.sqlite 2.5.0 | `androidx-sqlite = "2.5.0"` | совпадает |
| androidx.work 2.9.1 | `androidx-work = "2.9.1"` | совпадает |
| Compose BOM 2024.09.02 | `compose-bom = "2024.09.02"` | совпадает |
| Firebase BOM (Android) 33.2.0 | `firebase-bom = "33.2.0"` | совпадает |
| firebase-admin (Node) ^13.8.0 | `functions/package.json` deps | совпадает |
| firebase-functions (Node) ^7.2.5 | `functions/package.json` deps | совпадает |
| Node (Cloud Functions) 22 | `engines.node: "22"`, `firebase.json` → `runtime: nodejs22` | совпадает |

Отдельно проверено утверждение под таблицей: **`firebase-messaging-ktx` действительно уже объявлен** — `gradle/libs.versions.toml:162`, без `version.ref`, то есть версию берёт из BOM. Поиск по всем `build.gradle.kts` даёт ноль подключений, то есть «добавлять не нужно, нужно подключить» — верно дословно.

Замечание о неоднозначности (low): в каталоге есть ещё `firebase-admin = "9.8.0"` и `firebase-functions = "21.0.0"` — это JVM/Android-артефакты, не Node. Спайн разводит их пометками «(Node)», так что ошибки нет, но при чтении таблицы рядом с каталогом два разных `firebase-admin` путают. Стоит пометить node-строки как `functions/package.json`.

---

## 2. Утверждения о текущем состоянии кода

### Подтверждено

| Утверждение спайна | Подтверждение |
| --- | --- |
| AD-3: прохождения через callable, заявки арены — прямой записью | `FirebaseLessonResultRemoteDataSource.kt:16` (`getHttpsCallable`) против `FirebaseQuestArenaSubmissionRemoteDataSource.kt:20-22` (`firestore.collection("quest_review_requests").document(...).set(...)`) |
| AD-5: две несовместимые формы «отправлено» | надгробие — `LessonResultSyncOutboxDao.kt` (`SET sent_at_ms = :sentAtMs`); удаление — `QuestArenaSubmissionDao.kt:35` (`DELETE FROM quest_arena_submission_outbox`) |
| AD-5: «три существующие очереди» | `lesson_result_attempt_outbox`, `quest_rating_outbox`, `quest_arena_submission_outbox` — ровно три |
| AD-6: падение прохождений глушит оценки в том же прогоне | `LessonResultSync.kt:44` — `syncAttempts()` делает `throw e`, поэтому `syncRatings()` строкой ниже не выполняется |
| AD-7: выборка не фильтрует по владельцу | `LessonResultSyncOutboxDao.kt` — `WHERE sent_at_ms IS NULL` без `owner_uid`, хотя колонка `owner_uid` в обеих таблицах уже есть |
| AD-8: `signInWithCredential` возвращает `SWITCHED` | `FirebaseGoogleSignInRepository.kt:77-79` — `signInAs()` делает `auth.signInWithCredential(...).await()` и возвращает `GoogleLinkOutcome.SWITCHED` |
| AD-8: обработчик — всплывающая надпись | `DefaultProfileComponent.kt:208` — `SWITCHED -> ProfileMessage.GoogleSwitchedToExisting`, ничего кроме сообщения |
| AD-9: `LessonRatingRepositoryImpl` делает два независимых вызова | `LessonRatingRepositoryImpl.kt:17-20` — `ratingLocalDao.upsert(...)` и `outboxWriter.enqueueRating(...)` без `@Transaction` |
| AD-11: четыре журнала, схемы id | `functions/index.js:3943,3947,3951,3987` — `private/{ownerUid}/sync_changes/{catalogId}_{questId}`, `catalogs/{catalogId}/sync_changes/{type}_{id}`, `lesson_content/{lessonId}/sync_changes/{questionId}`, `admin/review/sync_changes/{changeId}`; `changeId` — это `assignmentId` (`index.js:2784-2785`) |
| AD-11: сид-скрипты формируют id из таймстемпа | `scripts/seed-hierarchy.js:100,108`, `scripts/seed-criptico-course.js:318,327`, `scripts/seed-lesson-runner-extra.js:280,289` — все вида `${change.changedAtMs}-${change.type}-${change.id}` |
| AD-12: три сосуществующих способа записи `version` | `functions/index.js:2310` (`version: request.localRevision` — клиентский), `index.js:1388` (`numberValue(quest.version, 0) + 1` — read-modify-write), `index.js:1938` (`FieldValue.increment(1)`) |
| AD-13: правила требуют `contentsVersion == 0` при создании | `firestore.rules:108` — `&& request.resource.data.contentsVersion == 0` |
| AD-14: схема `1.json` совпадает с `2.json` вплоть до `identityHash` | обе `7f980aefc2cf4f08dd06c57aa7a3ed6f`, побайтовый diff после вычёркивания поля `version` пуст, размер идентичен (75560 байт) |
| AD-16: спиннер держится ~70 секунд | ни один из 18 вызовов `getHttpsCallable` в `platform/firebase/src/main` не задаёт таймаут — работает дефолт SDK |
| AD-17: наружу не выходит ничего, кроме `Log.w` | единственная точка — `SyncWorker.kt:38`, `Log.w(TAG, "Syncable failed: ...")` |
| AD-20: три места регистрации | `apps/android-next/.../di/SyncModule.kt` (app), `shared/feature/quest-authoring/data/src/commonMain/.../di/QuestAuthoringDataModule.kt` (commonMain фичи), `shared/feature/lesson-runner/data/src/androidMain/.../di/LessonRunnerDataModule.kt` (androidMain фичи) |
| AD-21: `index.js` без единого `module.exports` | `grep -c "module.exports" functions/index.js` → 0; при этом 38 `exports.<name>` |
| AD-23: способов удаления ровно три, поля `deleted` нет | `refreshByIds` — `CatalogRepositoryImpl.kt:45`; `visibleOn.size() > 0` — `firestore.rules:88`; `archived` — `CatalogRepositoryImpl.kt:85`. Поиск `"deleted"`/`isDeleted` по `shared` и `functions` — ноль совпадений |
| Deferred: `maxInstances` даёт один инстанс | `functions/index.js:74` — `maxInstances: 1` в общем `FUNCTION_OPTIONS` |
| Деплой-контур: эмулятор объявлен, никем не поднимается | `firebase.json` содержит блок `emulators` (auth 9099, firestore 8080, functions 5001); ни один скрипт в `functions/package.json` и `scripts/` его не стартует |
| Deferred: две точечные подписки Firestore | `FirebaseUserStatsDataSource.kt:46` и `FirebaseLessonCommentRepository.kt:23` — ровно два `addSnapshotListener` |
| Deferred: pull только на `onCreate` при `ON_LAUNCH` плюс воркер | `MainActivity.kt:44,70` — `SyncFrequency.ON_LAUNCH` внутри `onCreate`; возврата из фона нет |
| Deferred: `result_events_*` write-only и растут | `functions/index.js:317` — `scopedCollection("result_events", event.scope)`, чистки нет |

### Опровергнуто

**AD-2, строка Prevents.** Спайн: «двойное списание при повторной доставке — **сегодня на сервере нет ни одной проверки повтора**». Это неверно, причём дважды:

- `functions/index.js:364` — `const isNew = !existingSnapshots[index].exists;`, и списание жизненных очков (`spendLifePoints`) выполняется только при `isNew`. Это полноценная проверка повтора, не только запись через `set(..., {merge: true})`.
- `functions/index.js:1291` — `if (owned.includes(key)) { response = {..., charged: 0, ...}; return; }` в `unlockLesson`. Комментарий в коде прямо об этом: «Charging twice for the same door is the one thing a retry must not do».

Собственное правило AD-2 ниже это признаёт («Существующая дедупликация по doc-id (`attemptId`, `ratingId`) — частный случай»), то есть строка Prevents противоречит строке Rule внутри одного AD. Журнал решений при этом был точен — `.memlog.md:16` пишет «Единственный дедуп — doc-id по клиентскому attemptId/ratingId… безопасна для ретрая одна (unlockLesson, естественная идемпотентность)». Точность потеряна при рендере спайна.

Решение AD-2 остаётся верным — существующая дедупликация держится на doc-id, который выбирает клиент, и покрывает две мутации из десяти. Но обоснование в текущем виде разработчик проверит за минуту и перестанет доверять остальным «сегодня» в документе.

### Неточно

**AD-15.** «Три легальных способа получить „сейчас“, уже сосуществующих **в sync-коде**». В `shared/core/sync/src/commonMain` нет ни одного обращения ко времени вообще — ни `Clock`, ни `Instant`, ни `now`. Три способа реально существуют, но живут вне этого модуля: `apps/android-next/.../di/SyncModule.kt:92` (`System.currentTimeMillis()`), дефолтные лямбды `Clock.System.now().toEpochMilliseconds()` в `ProfileRepositoryImpl.kt:23` / `ActivityRepositoryImpl.kt:34` / `DefaultQuestAuthoringTimestampProvider.kt:7`, и `FieldValue.serverTimestamp()` в `FirebaseQuestArenaSubmissionRemoteDataSource.kt:41`. Правило («запрет прямых вызовов в `core/sync` и `core/outbox`») от этого не страдает — но в `core/sync` оно сегодня превентивное, а не исправляющее, и стоит сказать это прямо.

**AD-19.** «Его оркестратор тянет domain-репозитории **шести фич**». Репозиториев в `CatalogSyncListOrchestrator.kt:29-34` действительно шесть, но `CatalogRepository` приходит из `shared/core/catalog/domain` — это core-модуль, не фича (`shared/core/sync/build.gradle.kts` перечисляет `:shared:core:catalog:domain` и пять `:shared:feature:*:domain`). Точная формулировка — «пять feature-domain плюс `core/catalog/domain`». Мелочь, но AD-19 именно о границе core↔feature, и там точность стоит дороже обычного.

**Счёт синхронных мутаций.** AD-1 перечисляет шесть синхронных действий; `.memlog.md:17` говорит «пять мутаций нельзя отложить». Расхождение не объяснено — либо шестое добавлено сознательно, либо счёт съехал.

**«4142 строки».** `wc -l functions/index.js` → **4166**. Отклонение 24 строки. Довод AD-21 держится не на числе, а на нуле `module.exports`, который подтверждён, так что решение не страдает — но число в документе, помеченном «не по памяти», должно совпадать.

---

## 3. Названные пути

| Путь | Состояние | Соответствие спайну |
| --- | --- | --- |
| `shared/core/outbox` | не существует | верно, помечен НОВЫЙ |
| `functions/mutation-queue.js` | не существует | верно, помечен НОВЫЙ |
| `shared/core/sync`, `shared/core/persistence`, `platform/firebase`, `platform/android-services`, `apps/android-next/di` | существуют | верно, помечены существующими |
| `firestore.rules` с `contentsVersion == 0` | существует, строка 108 | верно |
| `users/{uid}/devices` | **не существует и запрещён правилами** | см. ниже |

**`users/{uid}/devices/{token}` — правила его запрещают.** В `firestore.rules:4` объявлен `match /users/{userId}` без `{document=**}`, то есть правило покрывает только сам документ, но не подколлекции; ни `match` для `devices`, ни catch-all в файле нет (`grep -n "device\|fcm\|token" firestore.rules` — пусто). По умолчанию Firestore запрещает всё несопоставленное, поэтому клиентская запись токена будет отклонена.

Сверх того, это идёт против устоявшейся позиции файла: у `users`, `profiles`, `nickname_claims`, `configs`, `private`, `admin`, `verification_requests` стоит `allow write: if false`, и всякая клиентская запись идёт через callable. Единственное исключение — `quest_review_requests` с `allow create` и жёстким allowlist полей. AD-18 вводит второе исключение молча: строка CAP-10 в карте перечисляет `platform/firebase` и `functions/`, но не `firestore.rules`, а AD-13 (порядок выкатки «правила → функции → клиент») из-за этого на AD-18 не наводится.

---

## 4. Устаревшие технологии и подходы

Отдельной находки нет: всё названное живо и поддерживается. Наблюдения:

- Firebase BOM 33.2.0 и Compose BOM 2024.09.02 — сборки середины 2024 года. Для спайна это факт каталога, а не ошибка, и обновление вне его темы. Но AD-18 (FCM) — единственное место, где возраст Firebase BOM может выстрелить: подключать `firebase-messaging-ktx` придётся из BOM двухлетней давности.
- `compileSdk`/`targetSdk` = 34 (`libs.versions.toml:11,13`). Для магазина это уже ниже порога, но спайн честно фиксирует, что живых установок и Play-дистрибуции нет («только локальные сборки»), так что для этой темы это не долг.
- `kotlinx-datetime 0.5.0` — на нём `Clock.System` ещё в `kotlinx.datetime`, а не в `kotlin.time`. AD-15 всё равно запрещает прямые вызовы, так что будущая миграция ничего в спайне не ломает.
- `turbine = "1.1.0"` объявлен в каталоге (`libs.versions.toml:78`), хотя и спайн, и `.claude/rules/testing.md` требуют «без Turbine». Объявление без использования — не нарушение, но конвенция и каталог смотрят в разные стороны.

---

## 5. Находки, ранжированные

### HIGH-1 — AD-2, строка Prevents, опровергнута кодом

Утверждение «сегодня на сервере нет ни одной проверки повтора» неверно: `functions/index.js:364` и `functions/index.js:1291`. Оно же противоречит правилу того же AD. Журнал (`.memlog.md:16`) был точен.

**Правка:** заменить Prevents на «проверка повтора есть у двух мутаций из десяти и держится на doc-id, который выбирает клиент (`attemptId`, `ratingId`), плюс естественная идемпотентность `unlockLesson` по `lessonUnlocks`; остальным мутациям очереди опереться не на что, и doc-id не годится для действий, у которых нет естественного уникального ключа».

### HIGH-2 — AD-1: критерий в буквальном прочтении противоречит собственному списку

AD-1 объявляет синхронной мутацию, у которой «цена, награда или доступность рождаются на сервере и показываются на экране». Разблокировка урока подходит под это дословно: цена считается сервером в момент вызова из содержимого урока (`functions/index.js:1269-1274` → `functions/lesson-unlocks.js:35` `unlockPrice`), а у клиента локальной цены сегодня нет — `LessonItemUi.kt:23` объявляет `unlockPriceNolics: Int? = null`, присваивания нет нигде, и `LessonItemCard.kt:88` рендерит пустую строку. Тем не менее AD-1 относит разблокировку к отложенным.

Различие, которое спайн держит в уме, но не выговаривает: цена сердца зависит от живого баланса и реестра, а цена разблокировки — детерминированная функция синхронизированного контента. Пока это не сказано, два разработчика классифицируют новую мутацию по-разному, а тот, кто возьмётся за отложенную разблокировку, упрётся в отсутствие локальной цены.

**Правка:** переформулировать критерий по источнику входных данных, а не по месту вычисления: «синхронно — если цена или результат зависят от состояния, которого у клиента нет (баланс, реестр ников, чужая покупка лота); отложенно — если результат детерминированно выводится из синхронизированного контента». И добавить следствием, что формула цены разблокировки обязана жить в общем `shared/core`, иначе очередь не может показать цену до отправки.

### HIGH-3 — AD-18: `users/{uid}/devices/{token}` запрещён действующими правилами, и CAP-10 их не называет

`firestore.rules:4` — `match /users/{userId}` без `{document=**}`; подколлекции не сопоставлены, catch-all отсутствует, значит запись клиента отклоняется. Кроме того, AD-18 вводит второе в файле исключение из позиции «клиент в `users` не пишет» (`firestore.rules:16` — `allow write: if false`), не отмечая этого. Строка CAP-10 в карте перечисляет `platform/firebase` и `functions/`, но не `firestore.rules`, поэтому порядок выкатки из AD-13 на этот путь не распространяется.

**Правка:** добавить `firestore.rules` в строку CAP-10 и одним предложением в AD-18 зафиксировать, что подколлекция `devices` — единственная клиентски записываемая ветка под `users/{uid}` и что она подчиняется порядку AD-13. Либо, если исключение нежелательно, изменить AD-18: токен регистрируется callable-вызовом, как всё остальное под `users`.

### Более мелкие (7)

1. **MEDIUM — AD-15 указывает не то место.** «Три способа получить „сейчас“ в sync-коде» — в `shared/core/sync` обращений ко времени нет вовсе; три способа живут в `SyncModule.kt:92`, дефолтных лямбдах data-слоёв и `FieldValue.serverTimestamp()`. Правило верное, обоснование указывает мимо. Правка: назвать реальные места и сказать, что для `core/sync` запрет превентивный.
2. **LOW — «4142 строки»** против фактических 4166 (`wc -l functions/index.js`). Правка: 4166 или «более четырёх тысяч».
3. **LOW — AD-19 «шести фич».** Шесть репозиториев, но `CatalogRepository` — из `shared/core/catalog/domain`. Правка: «пять feature-domain и `core/catalog/domain`».
4. **LOW — AD-1 перечисляет шесть синхронных мутаций**, `.memlog.md:17` — пять. Расхождение не объяснено; либо выровнять, либо сказать, что шестая добавлена.
5. **LOW — AD-21 и текущий `lint`.** `functions/package.json:8` перечисляет проверяемые файлы вручную и уже пропускает `logos.js`, `logo-images.js`, `tournament-ranking.js`; `firebase.json` в `predeploy` вызывает только `lint`, не `test`. Требование AD-21 достижимо, но опирается на список, который уже разъехался. Стоит зафиксировать инвариантом, что список — не ручной.
6. **LOW — AD-14 и третья схема.** Схем сейчас три: `3.json` (`identityHash` `3f3f10a5...`, добавлена колонка `lessonUnlocks`), и `PersistenceModule.kt:38` уже регистрирует `Migration1to2, Migration2to3`. Утверждение спайна про `1.json`≡`2.json` верно, но «пересоздать одной схемой» теперь выбрасывает две написанные миграции — решение держится (живых установок нет), просто цена его выросла с момента разведки. `runMigrationsAndValidate` по репозиторию не встречается ни разу, так что требование AD-14 полностью новое.
7. **LOW — таблица Stack и одноимённые артефакты.** В каталоге есть `firebase-admin = "9.8.0"` и `firebase-functions = "21.0.0"` (JVM), рядом со строками спайна `^13.8.0` / `^7.2.5` (Node). Пометки «(Node)» есть, но стоит указать источник — `functions/package.json`.

---

## Итог

Спайн стоит на проверяемых фактах, и подавляющее большинство их подтверждается. Три находки требуют правки: одна ложная строка «сегодня» (AD-2), один критерий, который расходится со своим же списком (AD-1), и один путь, который правила запрещают (AD-18). Все три чинятся формулировкой и одной строкой в карте capability, без перестройки документа.
