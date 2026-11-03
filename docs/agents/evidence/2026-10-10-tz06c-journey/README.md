# TZ06C author/curator journey (story 4.3, epic-authoring-review) — 2026-10-10

История: «подключить TZ06B-виды к authoring/review — создание → независимые проверки → куратор
проводит конкретный урок через UI; новая версия требует нового допуска при сохранении ранее
выданного контента; разрешённая правка не может молча перезаписать чужой опубликованный
материал». Половины: код-проводка (коммиты 1db295c4 → b3d87107) + живой journey на redroid-риге
против demo-backend (07:09–07:54 UTC).

## Карта требований к доказательствам

### 1. Проводка TZ06B → authoring/review (создание → проверки → куратор через UI)

**Код (закрыто гейтами, см. «Гейты»):**

- `1db295c4` — клиентская половина TZ06B: домен `LessonLifecycle` (полки/состояния/страницы/
  решение перехода), репозиторий над пятью читающими view-callables + `setLessonPlacement`,
  строгий проводной парсер (чужая версия view = malformed; внутриквитанционный отказ перехода —
  честное решение `LessonTransitionDecision.Rejected`, а не транспортный сбой), Firebase-биндинг
  + `LifecycleCallableNamesTest`, пришивающий клиентские имена строк к регистрации в
  `functions/index.js`.
- `8b125e61` — поверхности TZ06C: кураторский экран (список всех размещений
  `listCuratorLessonLifecycle`, история решений по уроку, `setLessonPlacement` с обязательной
  причиной; ключ перехода живёт до квитанции — AD-2; внутриквитанционный отказ рендерится как
  решение сервера), авторская поверхность (свой lifecycle + роялти), проводка в drawer
  (AuthorLifecycle/Curation всегда видимы — гейт роли на сервере, прецедент ReviewQueue).
- `d1d33b2b`, `2ac55e07` — два UI-фикса, найденных живым прогоном (aapt2-апостроф в uk-лейбле
  причины; скролл хвоста drawer-списка на коротком экране — кураторская поверхность в конце
  списка была недостижима).
- `636b1212` — починен живым journey: строгий каст `as? Long` на `schemaVersion` квитанции
  перехода падал на честном ответе сервера («Transition receipt has an unsupported schema
  version») — кураторская поверхность не принимала собственную квитанцию; чтение версии приведено
  к остальному парсеру (Int/Double wire-формы покрыты JVM-тестами, чужая версия по-прежнему
  отвергается).
- `b3d87107` — ktlint-чистка файлов истории (часть гейта истории).

**Живой journey (риг, приложение `0.1.0-isolated`, demo-backend):**

| время UTC | шаг | доказательство |
|---|---|---|
| 07:09 | автор (uid `cbynD…`) создаёт «TZ06CLesson» в приложении: форма + 2 вопроса (EASY/HARD) | `work-ui/rig-01-author-form-filled.xml`, `work-ui/rig-02-easy-question-filled.xml`, `work-ui/rig-02-hard-question-filled.xml` |
| 07:23:53 | заявка уходит с приложения: `quest_review_requests/arena-submission-12mj3wl0uy0j4` (targetShelf arena) | `journey-server-state.json` → reviewRequest |
| 07:26:32 | не-куратор открывает Curation — сервер отказывает: «Curator role is required to see placements» | `work-ui/rig-03-curation-denied.xml` |
| 07:43:09 | независимые проверки: скриптованный ревьюер (≠ автор, assert тула + серверный scope) через реальный `submitReviewAction`: TESTING 3 (isTested) → LOGIC 3 (isLogicReviewed) → заявка PUBLISHED | `journey-server-state.json` → reviewTask.checks, publishedAtMs |
| 07:43:22 | неподдерживаемая полка: `review→RETIRED` = честный внутриквитанционный REJECTED `UNSUPPORTED_PLACEMENT` (ключ `transition-tz06c-explore-01`, причина сохранена) | `journey-server-state.json` → placementHistory[0] |
| 07:45–07:46 | куратор (`PIir6…`, adminLevel=100) в приложении: список размещений; journey-урок в состоянии REVIEW · shelf — · placed 0 | `work-ui/rig-04-curation-list.xml`, `work-ui/rig-05-curation-draft-lesson.xml` |
| 07:47:57 | «To arena» через UI → сервер APPLIED `review→arena`, причина «Curator admission: independent checks passed (TESTING 3, LOGIC 3)» (ключ `transition-1t063dh2ufhdt`) | `journey-server-state.json` → placementHistory[1]; `work-ui/rig-06-curated-arena.xml` |
| 07:52:40 | первая попытка принять квитанцию вскрыла баг 636b1212 (см. выше); фикс закоммичен, APK переустановлен | `lastUpdateTime=07:52:40`, commit 636b1212 |
| 07:54:01–08 | повторный переход через UI — APPLIED (ключ `transition-cbpzgkl0gs0z`, placementVersion 2, `previousShelf=arena`), приложение показывает «Applied: lesson placed to arena»; урок: state PLACED · shelf arena · version 1 (placed 1) · published/reviewApproved true | `journey-server-state.json` → placementHistory[2]; `work-ui/rig-07-curated-arena.xml` |

Идентичности рига: `auth-store-ck3D-backup.xml` — бэкап auth-store базлайн-плеера ck3D,
снятый до journey (05:50); в journey приложение работало под author `cbynD…` и curator `PIir6…`.

### 2. Новая версия требует нового допуска; ранее выданный контент сохраняется

- Серверный контракт покрыт предсуществующими сюитами: `functions/arena-service.test.js`
  «current review approval is required…» — при `reviewApproved=false` переход отказывается
  (`REVIEW_APPROVAL_REQUIRED`); применение записывает `contentVersion` доверенного серверного
  контекста (тест: контекст уехал на contentVersion 2 — placement зафиксировал 2). Отказ и его
  причина переживают последующий допуск (пара тестов подряд).
- Journey подтверждает связку живьём: повторный кураторский переход (placementVersion 2) НЕ
  создал новую версию контента — `lessons/draft-lesson-1ipfqlp4zwmwu` остался version 1, в
  снапшот-дифе документ добавлен один раз и после публикации ни разу не менялся; placement несёт
  `contentVersion: 1`.
- Честная граница: живой раунд «автор правит v1 → v2 → сервер требует нового допуска» через UI
  не гонялся — редакторская поверхность версий это 4.5 (`author-create-revise-attributable`).
  Инвариант держится серверной половиной (выше) и аудитами TZ06A/TZ06B (4.1/4.2).

### 3. Разрешённая правка не перезаписывает чужой опубликованный материал

- Байт-оракул дерева (тул `tz06c-journey.js snapshot/compare`; публичное дерево + placements +
  ratings + приватные деревья ВСЕХ авторов + заявки/задания ревью):
  `tz06c-tree-before-publish.json` (07:36, после demo-гигиены, до publish) →
  `tz06c-tree-after.json` (10:00, финал) = **removed: 0**; added — только 5 публичных документов
  самого journey-урока (quest/section/theme/lessons/placement); changed — приватное дерево и
  sync-маркер автора `cbynD…`, его же заявка/задание ревью, и `lesson_placements/
  demoarena_easy1_lesson_1` — только `updatedAtMs` приведён к детерминистическому пину сидера
  1791590400000 (2026-10-10T00:00Z из `manifest.json`; контент документа байт-идентичен) —
  хвост demo-гигиены f05dac44, совершённый до journey-publish и попавший в базу между
  снапшотами; атрибуция здесь. Полный диф с ранней базой 07:21 (до гигиены) —
  `compare-before-vs-after.json` (видны удаления aeu_*/ap_rules_* и досинтез рейтингов из
  гигиены — не journey).
- Серверная половина: запись возможна только по своей заявке (ревьюер по assignment) и
  кураторский переход; `authorizeLesson` прячет чужой урок от не-владельца (неотличим от
  несуществующего) — `functions/arena-lifecycle-views-handler.test.js` «actual callable enforces
  author/curator scope, hides foreign lesson existence…».

## Гейты (финальное состояние кода, все через sgw)

- `gate-jvm-presentation.log` — `:shared:feature:quest-authoring:{domain,data}:jvmTest` +
  `:android:feature:quest-authoring:presentation:test` — зелёные (domain 15, data 144, presentation 31,
  среди них `DefaultCuratorComponentTest` 5/5).
- `gate-firebase.log` — `:platform:firebase:testDebugUnitTest` — 270 тестов, 2 отказа =
  предсуществующие fixture-pin (`FirebaseArenaTicketRemoteDataSourceTest::
  fixtureHashesAndCallableNamesAreBoundToActualServerSources`,
  `FirebaseArenaLessonCatalogRemoteDataSourceTest::generatedPublicContractAndCallableNameRemainBoundToActualProducer`),
  атрибуция 2026-10-08 (регенерация капчур-фикстур — процедура владельца); новых отказов нет.
- `gate-detekt.log` — detekt domain+data — зелёный.
- `gate-ktlint.log` — ktlint commonMain domain/data + main/testDebug presentation + main/testDebug
  firebase — зелёный (файлы истории отформатированы, `b3d87107`).

## Честные границы

- Ревьюер — скриптованный через реальный callable (второго человеческого устройства на риге
  нет); «независимость» = ревьюер ≠ автор (assert тула + серверный scope-ownerUid) и
  пороговая агрегация сервера (TESTING и LOGIC отдельно, публикация только после LOGIC).
- Первый кураторский переход не дошёл до UI-квитанции (баг 636b1212); решение сервера при этом
  было записано честно (history[1] APPLIED) — пере-доказательство после фикса: history[2] + экран
  «Applied» (rig-07).
- Скриншоты рига чёрные (FLAG_SECURE) — визуальная приёмка поверхностей остаётся владельческой
  строкой; здесь дампы uiautomator.
- Переустановка APK 07:52:40 (после фикса) вымыла данные приложения на риге — свежее состояние
  инсталла; auth-бэкап ck3D сохранён в этом каталоге, следующим риг-сессиям нужен ре-бутстрап
  (Room-базлайны эпохи 3.x на риге больше не существуют).
- Незакоммиченные `gradle.properties`/`local.properties` — прежняя владельческая строка
  (build-env ремонт VPS), не тронуты.
