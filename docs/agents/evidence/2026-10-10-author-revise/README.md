# Author create/revise attributable (story 4.5, epic-authoring-review) — 2026-10-10

История: «автор создаёт урок и подаёт ревизии с атрибутируемой историей и protected-диапазонами
по контракту UX-O6; существующие заголовки неизменны, нет поля description, нет платы за
metadata-only (SPEC Constraints)». Половины: код редактора (коммиты смены 9) + живой hitl-раунд
(edit/undo/redo на устройстве — требование `waits` тикета) и живая подача ревизии в arena против
demo-backend. Риг: redroid 12 (`schoolquiz-emu`), приложение `com.tpov.schoolquiz.mvp`
(0.1.0-isolated), игрок UserKPWX87 (uid `GxeLc2…KpWx87`, ANONYMOUS, бутстрапнут приложением в
смене 9 до journey).

Прогон — два отрезка одной смены: 12:43–13:50 UTC (первая нога, оборвана сменой-лимитом; дампы
rig-00…45) и продолжение 13:55–14:35 (rig-46…52, серверный съём, финальный слепок базы).

## Карта требований к доказательствам

### 1. Код-половина (гейты, см. «Гейты»)

- `11a194ff` — undo/redo редактора: `QuestionEditorHistory` держит целые снимки редактора на
  сессию вопроса (UX-O6 §4, UX-18, AD-27): правка контента толкает прежний снимок через
  `updateEditable`, undo/redo меняют снимки местами, служебные переходы (сохранение, ошибки,
  превью) в историю не попадают; смена/переоткрытие вопроса сеанс истории очищают;
  canUndo/canRedo едут в ui-state, кнопки — в верхней панели редактора. Тесты: текст + метка +
  привязка ответа едут одним куском; история не переживает границу сохранения.
- `bfcc1906` — снятие метки сохраняет слово: `removeFillBlankAnswerMarker` понижает blank в
  обычный текст (метка — разметка над текстом, unmark не текстовая правка — AD-27, UX-O6 §4).
  Пины: позиционное соответствие метка-ответ переживает правку одной записи; повтор слова
  забирается один раз (identity вхождения); пустой новый слот не пишет запись (отсутствие ≠
  очищено); очищенный сохранённый вопрос не перезаписывает свою запись; частично невалидный
  ввод остаётся в редакторе (валидация на сохранении, не на каждом нажатии).
- `f28b056c` — неизменность заголовка урока на приёмнике заявок: `applyArenaSubmission`
  отвергает заявку, чей заголовок урока расходится с опубликованным `lessons/{id}`
  (`failed-precondition LESSON_TITLE_IMMUTABLE`), до всякой записи. Node-тесты на реальном
  приёмнике: переименование отвергнуто при нуле записей; неизменённый заголовок принят, кошелёк
  автора не тронут (заявки денег не читают); первая подача нового урока не требует сравнения;
  заявка без вопросов отпускает ключ мутации.
- `3d0cafed` — ktlint-чистка файлов истории; `c8e98463` — фикс, найденный живым ригом (ниже);
  `d9672918`, `065bf9d1` — гейты.

### 2. Живой hitl: edit/undo/redo round-trip на устройстве (требование waits тикета)

| время UTC | шаг | доказательство |
|---|---|---|
| 12:43–13:00 | форма создания урока: Quest/Section/Topic/Lesson/RU — поля description в форме нет вовсе | `work-ui/rig-01-creation-form.xml`, `rig-01b…01e` |
| 13:01–13:28 | раунды редактора fill-blank; приложение падало на непустом тексте превью | `rig-10b-recover`, `rig-19b`, `rig-22b` |
| 13:29–13:33 | краш атрибутирован: NoSuchMethodError FlowRow в `FillBlankTransformedPreview` (модуль против compose-foundation 1.7, в APK 1.9.x, бинарная сигнатура FlowRow другая) → фикс `c8e98463` (Column вместо FlowRow), APK переустановлен с сохранением данных | `build-apk.log`; `rig-26-after-reinstall` |
| 13:34–13:37 | round-trip: ввод текста → метка **blank** → метка *text* (protected) → обе метки (30a) → **undo** возвращает текст и метки вместе (31) → **redo** (32) → финальное состояние (33) → структура сохранена (34) | `rig-28a…rig-34` |
| 13:38–13:48 | дистракторы Groovy/Scala/Java/KotlinScript (1 ответ + 4 = ровно 5 кандидатов по валидатору), сохранение EASY в 13:48:33 (localRevision 2) | `rig-40…44`; `client-db-final.json` |

### 3. Продолжение: protected-диапазоны и подача ревизии в arena (живой demo-backend)

| время UTC | шаг | доказательство |
|---|---|---|
| 14:07:17 | HARD-вопрос собран через UI: текст «Kotlin compiles to JVM bytecode», кнопки **blank** и *text* → разметка «… **blank** \*text\*», 4 дистрактора; сохранён: payload `protectedTextSegments:["text"]`, кандидаты 5; localRevision 3 | `rig-47-hard-protected-markup.xml`, `rig-48-hard-wrong-fills.xml`; `client-db-final.json` (draft-question-11fiq63acfk04, HARD, SAVED) |
| 14:08:01 | «Send to arena» → «Quest queued for arena»; в очереди `quest_authoring.SUBMIT_ARENA` / `arena-submission-zq8lz01icnm0`, тело собрано в момент намерения автора (AD-2) | `rig-46-queued-for-arena.xml`; outbox-строка в `client-db-final.json` |
| 14:09:36 | SYNC NOW → очередь слилась ровно один раз: квитанция `replayed:false`; сервер: `quest_review_requests/arena-submission-zq8lz01icnm0` status `UNDER_REVIEW`, processedAt 14:09:36 | `server-state-first-submission.json`; `mutation_receipts` в `client-db-final.json` |
| — | серверное тело заявки: ownerUid = uid автора; урок `TZ06ELesson` (заголовок без изменений); draftDescription `null`; оба вопроса с payload'ами, у HARD — `protectedTextSegments:["text"]`; private-дерево автора записано; admin review-задание создано; ключ мутации completed с requestFingerprint | `server-state-first-submission.json` |
| — | кошелёк автора не тронут подачей: в профиле нет ни одного изменённого балансного поля (заявки денег не читают — совпадает с Node-тестом f28b056c) | `server-state-first-submission.json` → authorProfile.walletFields = [] |
| 14:20–14:28 | блокировка после подачи: черновик `REVIEW_QUEUED`; строка «TZ06Equest (Draft, 2 вопроса)» видна в списке, клик делает её активной, но редактор открывается пустым — `activeDraftStatuses` = DRAFT/SAVED/SYNC_PENDING/SYNCED/CONFLICT (`QuestAuthoringLocalDataSource`), поданный черновик заморожен до вердикта; повторная подача до вердикта структурно невозможна | `rig-51-my-quests-list.xml`, `rig-52-review-queued-editor-locked.xml`; `client-db-final.json` (status REVIEW_QUEUED, isActive 1) |

### 4. Отказ по валидности до подачи (жизненный цикл, попутно)

13:49:40 — первая «Send to arena» при одном сохранённом EASY-вопросе: именованный отказ
«Submission needs saved easy and hard questions in every lesson» (`LessonNotReady` →
`qa_error_arena_missing_questions`; правило `validateArenaReadiness`: ≥1 сохранённый EASY и
≥1 сохранённый HARD, ноль испорченных). Доказывает, что валидация готовности — на домене, а
подписи отказов не сливаются в одну (комментарий `arenaSubmissionErrorMessage`).
Доказательство: `rig-45-sent.xml`.

## Соответствие строке истории

- «создаёт урок и подаёт ревизии с атрибутируемой историей» — localRevision 1→2→3, у каждого
  вопроса свой updatedAtMs (13:48:33 EASY / 14:07:17 HARD); поданное тело — снимок на момент
  намерения (AD-2); серверная атрибуция: ключ мутации с uid автора + requestFingerprint,
  квитанция ровно-один-раз (`replayed:false`).
- «protected-диапазоны по контракту UX-O6» — маркер *text* из UI-кнопки доехал
  редактор → Room → outbox → серверный документ (`protectedTextSegments:["text"]`).
- «существующие заголовки неизменны» — клиентского пути переименования нет; приёмник
  отказывает до записи (f28b056c + Node-тесты); в живой подаче заголовок урока не менялся.
- «нет поля description» — в форме создания поля нет (rig-01b/01c); клиент кладёт
  `description = null` (`CreateQuestDraftCommand`); по проводу `draftDescription: null`.
- «нет платы за metadata-only» — приёмник заявок денег не читает вовсе (Node-тест f28b056c:
  «author's wallet untouched»); живая подача не изменила ни одного балансного поля профиля.

## Гейты (только sgw)

`d9672918` (12:37): quest-authoring domain 30 + data 144 + presentation 49 — зелёные; functions
93/93 (без эмулятора и против demo-backend); firebase 270 тестов — ровно 2 предсуществующих
fixture-pin отказа (атрибуция 2026-10-08, список владельца); ktlint зелёный; detekt scoped —
presentation:detekt красный предсуществующе (jvm-target 27, список владельца). Логи:
`gate-jvm.log`, `gate-firebase.log`, `gate-functions-node.log`, `gate-ktlint.log`,
`gate-detekt.log`. После фикса FlowRow (`065bf9d1`, 13:32): presentation-тесты + ktlint
повторно зелёные — `gate-jvm-fix.log`; сборка APK — `build-apk.log`.

## Границы (честно)

- Живой v2-раунд (правка после вердикта → повторная подача) структурно закрыт до вердикта:
  REVIEW_QUEUED не открывается в редакторе (by design, см. §3) — вердикт и повторная подача
  оживают в 4.6 (независимое TESTING-ревью) / 4.7 (платный цикл); скриптованный ревьюер из
  тула 4.3 (`scripts/demo-fixtures/tz06c-journey.js review`) готов это обеспечить в 4.6.
- FLAG_SECURE: скриншоты приложения чёрные — доказательства с экрана = дампы uiautomator
  (`work-ui/rig-*.xml`), как в 4.3.
- GMS на риге нет: UI-путь Google sign-in непроверяем (не входит в историю).
- Риг нестабилен под 1g: в ходе сессии SystemUI перезапускался («Phone is starting…» ≈14:25),
  затем устройство самопроизвольно перезагрузилось (~14:30, FallbackHome в logcat); данные
  приложения пережили оба случая, дампы сняты заново.
- Drawer: «My quests»/«Curation» живут ниже сгиба списка и требуют жеста скролла внутри списка
  (список секций прокручивается — ремонт TZ06C d55ab3d3); developer-гейт (версия-пасхалка,
  developerLevel 101) был уже включён сменой 9.
- На аккаунте рига остался попутный пустой черновик «Новый квест» (draft-1nhsv533ldxva, 0
  вопросов) от навигации смены 9 — не влияет на доказательства.

## Состояние после прогона

- Риг (schoolquiz-emu) остановлен после съёма доказательств; demo-backend
  (`schoolquiz-demo-firebase-20261004`) оставлен работать по постоянному решению владельца,
  после прогонов чист (все записи journey — собственные артефакты автора рига: заявка,
  private-дерево, admin-задание, ключ мутации).
- Файлы владельца не тронуты: незакоммиченные `gradle.properties`/`local.properties` остались
  как были (build-env ремонт VPS, список владельца).
