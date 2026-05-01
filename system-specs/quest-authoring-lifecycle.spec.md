# Quest Authoring Lifecycle

## History
- 2026-04-30: Initial lifecycle spec based on the requested create-quiz flow.
- 2026-04-30: Revision 2 added decision questions with recommended answers.
- 2026-04-30: Revision 3 captured owner answers and replaced several recommended defaults.
- 2026-04-30: Revision 4 simplified server structure, anonymous user, arena and review-copy decisions.
- 2026-04-30: Revision 5 aligned remaining answers with ADR-0005 and owner scope notes.

## 1. Context & Problem
- В приложении уже есть вход в создание квеста: FAB "Создать квест" на экране "Мои квесты" ведет в `QuestCreateRoot`, но сейчас там заглушка.
- Существующая публичная структура контента: `Catalog -> Quest -> Section -> Theme -> Lesson -> Question`.
- Существующий `lesson-runner` уже умеет показывать вопросы типов `SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank` через `QuestionContent`, но его state machine завязана на прохождение, попытки, таймеры и scoring.
- Репозитории квестов/секций/тем/уроков/вопросов сейчас в основном read/sync-oriented: `observe/get/refresh`, без полноценного authoring CRUD.
- Firestore rules на 2026-04-30 разрешают пользователю создавать/обновлять только верхний документ `quests/{questId}` с ограниченным набором полей. `sections`, `themes`, `lessons`, `questions` имеют write только для admin/server. Значит полноценное создание квеста с вопросами нельзя делать прямой записью клиента в публичные коллекции.

### Assumptions
- "Готовый путь" означает шаблон или уже существующую структуру размещения внутри квеста: catalog/section/theme/lesson, которую можно выбрать и заполнить вопросами.
- Создание новых публичных каталогов пользователем не входит в MVP; пользователь выбирает существующий catalog, а внутри своего draft может создать новые section/theme/lesson.
- "Режим генерации" означает authoring/editing mode, не AI-генерацию.
- Сохранение локально и отправка на сервер разделяются: `Сохранить` сохраняет draft в "Мои квесты", `Отправить на проверку` переводит draft в review lifecycle. При этом синхронизация может бэкапить приватные draft-данные пользователя на сервер.

## 2. Goals & Non-Goals
### Goals
- Зафиксировать полный жизненный цикл пользовательского квеста от FAB до публикации в публичную структуру.
- Реализовать offline-first authoring: пользователь может создать и редактировать draft локально.
- Переиспользовать существующие question UI/building blocks из `lesson-runner`, не связывая authoring с логикой прохождения.
- Передавать пользовательские квесты на сервер в приватную authoring-зону пользователя.
- Сделать публикацию серверной/admin-controlled: админ проверяет, правит или возвращает изменения, сервер переносит approved-контент в публичные коллекции и обновляет sync markers.

### Non-Goals
- Прямые client writes в публичные `sections/themes/lessons/questions`.
- Автоматическая публикация без review.
- Новый admin UI как часть первого client MVP, если его можно заменить серверным/admin tooling.
- AI-генерация вопросов.
- Переработка публичного catalog sync сверх необходимых точек интеграции.

## 3. User Stories / Scenarios
- Как автор, я хочу нажать FAB в "Мои квесты" и открыть создание квеста, чтобы быстро начать новый draft.
- Как автор, я хочу выбрать готовый путь или создать новый путь, чтобы квест попал в понятную учебную структуру.
- Как автор, я хочу задать язык, сложность и типы вопросов, чтобы не повторять эти параметры вручную для каждого вопроса.
- Как автор, я хочу заполнять вопросы в интерфейсе, похожем на прохождение, чтобы сразу видеть будущий вид вопроса.
- Как автор, я хочу переключаться между вопросами стрелками и через список, чтобы быстро редактировать нужный вопрос.
- Как автор, я хочу сохранить draft локально, чтобы он появился в "Мои квесты" даже без сети.
- Как автор, я хочу отправить draft на проверку, чтобы админы могли подтвердить или вернуть правки.
- Как админ, я хочу проверять пользовательские квесты вне публичного каталога, чтобы не публиковать сырой контент.
- Как сервер, я хочу публиковать approved-квест атомарно в публичные коллекции, чтобы клиенты получили его через текущий sync pipeline.

## 4. Scope & Out of Scope
### In Scope
- Entry point: `MyQuests FAB -> QuestCreateRoot -> QuestAuthoringComponent`.
- Wizard создания: выбор пути, базовых метаданных, языка, difficulty/default question type.
- Authoring session с вопросами: add/delete/duplicate/reorder, previous/next navigation, question list.
- Валидация каждого вопроса по `QuestionContent` invariants.
- Локальное хранение draft bundle.
- Отображение draft/status в "Мои квесты".
- Outbox/sync для приватной authoring-зоны пользователя.
- Review request lifecycle: submitted, under review, changes requested, approved, rejected, published.
- Server-side publication into existing public collections and sync change records.

### Out of Scope
- Публичное редактирование опубликованного квеста напрямую с клиента.
- Создание новых публичных catalog.
- Финальная админ-панель с rich UI, если MVP использует scripts/functions.
- Медиа-загрузка изображений как обязательная часть первой вертикали; можно начать с URL/path или без изображений.

## 5. Functional Requirements
- FR-1: FAB "Создать квест" должен открывать экран создания вместо заглушки `QuestCreateRoot`.
- FR-2: Первый экран создания должен предложить: выбрать готовый путь или создать новый путь.
- FR-3: Готовый путь должен подставлять структуру section/theme/lesson из шаблона или существующей authoring/public структуры.
- FR-4: Новый путь должен позволять задать минимум: `questTitle`, `sectionTitle`, `themeTitle`, `lessonTitle`.
- FR-5: Пользователь должен выбрать базовые параметры draft: язык, default difficulty, default question type, описание/обложку квеста при наличии UI.
- FR-6: После настройки открывается authoring editor со списком вопросов и текущим вопросом.
- FR-7: Authoring editor должен поддерживать типы вопроса из `QuestionContent`: `SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank`.
- FR-8: Для каждого типа вопроса пользователь должен заполнить все обязательные поля, включая правильный ответ/порядок/blank mapping.
- FR-9: UI текущего вопроса должен максимально переиспользовать visual components из `lesson-runner`, но authoring component не должен вызывать runner use cases прохождения.
- FR-10: В authoring editor должны быть кнопки перехода влево/вправо между вопросами.
- FR-11: В authoring editor должен быть список вопросов с возможностью открыть конкретный вопрос.
- FR-12: Каждый вопрос должен иметь локальный статус: `Empty`, `Invalid`, `ValidUnsaved`, `Saved`.
- FR-13: `Save question` валидирует draft вопроса, сериализует его в `QuestionContent` JSON payload и сохраняет в локальный draft bundle.
- FR-14: `Save quest draft` сохраняет весь bundle локально и показывает его в "Мои квесты" со статусом `Draft` или `LocalOnly`.
- FR-15: Draft должен быть доступен офлайн после перезапуска приложения.
- FR-16: При синхронизации локальный draft может быть отправлен в приватную server authoring-зону пользователя, но не в публичные `quests/sections/themes/lessons/questions`.
- FR-17: Пользователь должен явно отправить draft на проверку через `Submit for review`, когда все обязательные элементы валидны.
- FR-18: После submit draft получает статус `QueuedForReview` локально и `Submitted`/`UnderReview` после успешной серверной фиксации.
- FR-19: Пока draft `UnderReview`, пользователь не может менять отправленную ревизию; новые правки создают новую локальную ревизию или запрещены до ответа админа.
- FR-20: Админ может вернуть изменения со статусом `ChangesRequested` и списком комментариев.
- FR-21: При `ChangesRequested` пользователь видит draft в "Мои квесты", может открыть его, исправить и отправить новую ревизию.
- FR-22: Админ может отклонить draft со статусом `Rejected`.
- FR-23: Админ может approve draft; после этого server-side processor публикует квест в публичную структуру.
- FR-24: Публикация должна создать/обновить публичные документы `quests`, `sections`, `themes`, `lessons`, `questions` и соответствующие `catalogs/{catalogId}/sync_changes`.
- FR-25: После успешной публикации локальный draft получает `Published` и ссылку на `publicQuestId`.
- FR-26: Редактирование опубликованного квеста пользователем должно начинать новую authoring revision/review request, а не менять публичный контент напрямую.

## 6. API / Integration
### Existing APIs
- Entry point: `MyQuestsScreen` FAB вызывает `component.onCreateQuestClick()`.
- App shell: `LocalConfig.QuestCreateRoot` сейчас рендерит заглушку "Создание квеста в разработке".
- Question schema: `QuestionContent` уже содержит сериализуемые типы и validation invariants.
- Existing public sync: публичный контент читается из flat collections и sync change records.
- Existing Firestore rules: client не может писать nested public content; это подтверждает необходимость отдельной authoring-зоны.

### New / Changed APIs
#### Client domain
```kotlin
interface QuestAuthoringRepository {
    fun observeDrafts(authorUid: String): Flow<List<QuestDraftSummary>>
    fun observeDraft(draftId: QuestDraftId): Flow<QuestDraft?>
    suspend fun createDraft(command: CreateQuestDraftCommand): Result<QuestDraftId>
    suspend fun updateDraftMetadata(command: UpdateQuestDraftMetadataCommand): Result<Unit>
    suspend fun saveQuestion(command: SaveDraftQuestionCommand): Result<Unit>
    suspend fun reorderQuestions(command: ReorderDraftQuestionsCommand): Result<Unit>
    suspend fun submitForReview(draftId: QuestDraftId): Result<ReviewRequestId>
}
```

#### Client sync/outbox
```kotlin
interface QuestAuthoringSyncRepository {
    suspend fun enqueueDraftUpload(draftId: QuestDraftId): Result<Unit>
    suspend fun enqueueReviewSubmit(draftId: QuestDraftId): Result<Unit>
    suspend fun refreshAuthoringStatuses(authorUid: String): Result<Unit>
}
```

#### Firestore authoring workspace
Recommended remote structure:
```text
quest_authoring/{uid}
quest_authoring/{uid}/drafts/{draftId}
quest_authoring/{uid}/drafts/{draftId}/sections/{sectionDraftId}
quest_authoring/{uid}/drafts/{draftId}/themes/{themeDraftId}
quest_authoring/{uid}/drafts/{draftId}/lessons/{lessonDraftId}
quest_authoring/{uid}/drafts/{draftId}/questions/{questionDraftId}
quest_review_requests/{requestId}
```

Security intent:
- User can read/write only `quest_authoring/{ownUid}/drafts/*` while draft is editable.
- User cannot write public `sections/themes/lessons/questions`.
- Admin/server can read all authoring drafts.
- Server/Admin SDK is the only writer to public collections during publication.

#### Server publication processor
Input:
```json
{
  "requestId": "review-request-id",
  "draftId": "draft-id",
  "authorUid": "firebase-uid",
  "catalogId": "catalog-id",
  "revision": 3
}
```

Output:
```json
{
  "status": "published",
  "publicQuestId": "quest-id",
  "publishedAtMs": 1777564800000
}
```

Required server operations:
- Validate authoring bundle.
- Assign or reuse public ids.
- Write public quest hierarchy.
- Bump `version`, `contentsVersion`, `lastModifiedAt`.
- Write `catalogs/{catalogId}/sync_changes`.
- Mark review request and draft as `Published`.

## 7. Data Model
### Local draft model
```kotlin
data class QuestDraft(
    val id: QuestDraftId,
    val authorUid: String,
    val catalogId: CatalogId,
    val title: String,
    val description: String?,
    val language: String,
    val defaultDifficulty: Difficulty,
    val status: QuestDraftStatus,
    val localRevision: Long,
    val serverRevision: Long?,
    val publicQuestId: QuestId?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
```

```kotlin
enum class QuestDraftStatus {
    LocalOnly,
    Draft,
    UploadQueued,
    UploadFailed,
    SyncedPrivate,
    ReviewQueued,
    Submitted,
    UnderReview,
    ChangesRequested,
    Rejected,
    Approved,
    Publishing,
    Published,
}
```

Hierarchy draft entities:
- `DraftSection(id, draftId, title, order)`
- `DraftTheme(id, draftId, sectionId, title, order)`
- `DraftLesson(id, draftId, themeId, title, order)`
- `DraftQuestion(id, draftId, lessonId, questionType, language, difficulty, order, payload, validationState, updatedAtMs)`

### Invariants
- Draft must have non-blank title before submit.
- Submit requires at least one lesson and at least one valid question.
- Each saved question payload must deserialize into `QuestionContent`.
- Question order must be stable and unique inside one lesson.
- `publicQuestId` is null until server publication.
- Client never sets public rating fields.
- Client never writes public sync markers.

## 8. UX / UI Overview
- Screen 1: route chooser.
  - Choose ready path/template.
  - Create custom path.
- Screen 2: quest setup.
  - Title, description, catalog, language, default difficulty, default question type.
- Screen 3: authoring editor.
  - Main area: current question in authoring mode.
  - Controls: previous/next buttons, save question, add question, delete/duplicate, open question list.
  - Question list: numbered questions with validity/saved state.
- Screen 4: submit/review status.
  - Local save state.
  - Sync/upload state.
  - Review state and admin comments.

Accessibility/localization:
- All icon buttons must have content descriptions.
- Error messages should point to the exact invalid field.
- Russian UI copy is MVP default; language field describes question content language, not app locale.

## 9. State & Flows
### Full lifecycle
```text
FAB
 -> Create screen
 -> Choose ready path OR create custom path
 -> Configure metadata/defaults
 -> Create local draft
 -> Author questions
 -> Save question(s)
 -> Save quest draft locally
 -> Appears in My Quests
 -> Sync uploads private draft to quest_authoring/{uid}
 -> User submits for review
 -> Server creates/updates quest_review_requests/{requestId}
 -> Admin reviews
 -> Changes requested OR rejected OR approved
 -> If changes requested: user edits local draft/revision and resubmits
 -> If approved: server publication processor writes public hierarchy
 -> Server writes public sync changes
 -> App sync receives published quest
 -> My Quests shows Published/public link
```

### Happy path
- User creates draft offline.
- User fills 5 valid questions.
- User saves draft; it appears in "Мои квесты".
- Network returns; sync uploads private draft.
- User taps "Отправить на проверку".
- Admin approves.
- Server publishes into public collections.
- Client sync sees `Published` status and public quest.

### Edge cases
- Invalid question: save is blocked for that question, draft can remain local but submit is blocked.
- Offline submit: app enqueues `ReviewQueued`; outbox retries when network is available.
- Upload conflict: if server has newer revision, app shows conflict and requires local merge/overwrite decision.
- Admin changes requested: app keeps comments and opens the exact question/path if comments reference an entity id.
- Publication failed: status `PublishingFailed` can be represented as `Approved` + server error field or a separate status if needed.

### Reuse of lesson-runner UI
- Easy part: reuse visual question-type components and `QuestionContent` schema.
- Not easy/safe: using `LessonRunnerRootComponent` itself as editor.
- Required approach: extract shared composables/state mappers for question rendering, then build a separate `QuestAuthoringComponent`.
- Reason: runner lifecycle includes timers, auto-answer on pause, attempt saving, result screen and scoring; these are incorrect for authoring.

## 10. Non-Functional Requirements
- Offline-first: local draft writes must not require network.
- Idempotent sync: duplicate upload/retry must not create duplicate public quests or duplicate review requests.
- Security: user content remains private until approved; only server/admin writes public content.
- Validation parity: client and server must validate the same schema constraints.
- Performance: authoring editor should switch between questions without blocking UI; local save should be fast enough for frequent saves.
- Reliability: outbox must survive process death.
- Observability: sync/review publication failures should be logged with draft id, revision and request id, without leaking question text into logs by default.

## 11. Dependencies & Constraints
- KMP + Decompose + Compose + Koin.
- Presentation state holder must be Decompose component, not AndroidX ViewModel.
- Compose screens render state and callbacks only; no repositories/use cases directly in composables.
- Domain/data live under `shared/{core,feature}`.
- Android presentation lives under `android/feature/*/presentation`.
- Current public Firestore nested content writes are admin-only.
- Current sync expects public catalog change markers; server publication must integrate with that contract.
- Existing `Quest.archived` / `visibleOn` semantics are currently in flux, so draft visibility should not rely on `visibleOn = emptySet()` in public `Quest`.

## 12. Migration / Rollout Plan
- Phase 1: Spec and architecture.
  - Finalize authoring lifecycle and remote paths.
  - Decide exact meaning of ready path/template.
- Phase 2: Domain walking skeleton.
  - Add authoring draft models, repository contracts, validation, fake repository tests.
- Phase 3: Local storage.
  - Add Room draft tables and outbox records.
  - Show local drafts in "Мои квесты".
- Phase 4: Presentation.
  - Replace `QuestCreateRoot` placeholder with Decompose authoring component.
  - Build wizard and question editor.
  - Extract/reuse runner question UI safely.
- Phase 5: Private sync.
  - Upload drafts to `quest_authoring/{uid}`.
  - Refresh review statuses.
- Phase 6: Review/publication backend.
  - Add rules/functions/admin processor.
  - Publish approved drafts into public structure and sync markers.
- Phase 7: Hardening.
  - Conflict handling, retries, admin comments, instrumentation tests.

Rollback:
- Feature can be hidden by keeping `QuestCreateRoot` on under-construction or feature flag.
- Private authoring data does not affect public catalog until server publication, so rollback of client UI is safe.

## 13. Testing Strategy
- Domain unit tests:
  - Draft creation defaults.
  - Question validation per type.
  - Submit blocked by invalid/missing fields.
  - Status transitions.
- Data tests:
  - Draft persisted and restored after restart.
  - Outbox retries are idempotent.
  - Conflict/newer server revision behavior.
- Presentation tests:
  - FAB opens creation flow.
  - Route chooser creates draft.
  - Previous/next/list navigation opens expected question.
  - Invalid fields show targeted errors.
  - Save question updates status.
- Server/rules tests:
  - User can write own authoring draft.
  - User cannot write another user's draft.
  - User cannot write public nested collections.
  - Admin/server can publish.
- Integration tests:
  - Local draft -> private upload -> review request -> server publication -> public sync visible.

## 14. Acceptance Criteria
- AC-1: FAB no longer opens under-construction screen; it opens quest creation.
- AC-2: User can create a local draft with title/path/language/defaults.
- AC-3: User can add at least one valid `SingleChoice` question and save it locally.
- AC-4: User can navigate questions with previous/next and open a question from the list.
- AC-5: Invalid questions cannot be submitted for review.
- AC-6: Saved draft appears in "Мои квесты" with a clear draft/review status.
- AC-7: Offline local save works and survives app restart.
- AC-8: Sync sends private draft data to the user's authoring workspace, not public collections.
- AC-9: Review submit creates or updates a server review request.
- AC-10: Approved draft is published by server into public hierarchy and becomes available through existing sync.
- AC-11: Direct client writes to public nested content remain impossible.

## 15. Risks & Open Questions
### Risks
- UI reuse from `lesson-runner` can become expensive if current composables are too coupled to answer/scoring state.
- Drafts stored as public `Quest` with `visibleOn = emptySet()` may conflict with current sync/delete semantics; use separate draft storage instead.
- Server publication must be atomic enough to avoid half-published hierarchies.
- Admin edit-in-place can overwrite user changes if review revisions are not explicit.
- Firestore rules for private authoring workspace must be written carefully to prevent cross-user reads/writes.

### Open Questions
- Что именно считается "готовым путем": template внутри приложения, существующий catalog/section/theme/lesson или отдельная методическая структура?
- Должен ли любой локально сохраненный draft автоматически уходить на сервер как private backup, или только после "Отправить на проверку"?
- Может ли админ править draft напрямую, или админ всегда создает review comments, а правит только пользователь?
- Нужна ли первая версия только для одного lesson в одном quest, или сразу поддерживаем несколько lessons/themes?
- Нужны ли изображения/аудио в вопросах в MVP, и где хранить media draft до публикации?
- Должен ли опубликованный пользовательский квест оставаться "owned by author" в `My Quests`, или после публикации он становится обычным публичным квестом с author attribution?

## Revision 2 - Decision Questions & Recommended Answers

### Product lifecycle
- Q-1: Что такое "готовый путь" в MVP?
  - Recommended answer: готовый путь = выбранный public catalog + локальный шаблон структуры `Section -> Theme -> Lesson`. В первой версии пользователь выбирает catalog, затем либо выбирает шаблон пути, либо вводит свои `sectionTitle`, `themeTitle`, `lessonTitle`.
  - Reason: public catalog уже является стабильной точкой входа sync, а новые public sections/themes/lessons должны появляться только после review/publication.

- Q-2: Должен ли пользователь создавать новые public catalog?
  - Recommended answer: нет, в MVP пользователь выбирает существующий catalog. Создание public catalog остается admin-only.
  - Reason: catalog является верхним уровнем публичной навигации и влияет на sync всех пользователей.

- Q-3: Сколько уровней структуры поддерживать в первой версии?
  - Recommended answer: data model сразу проектировать как `Quest -> Sections -> Themes -> Lessons -> Questions`, но UI MVP ограничить одним section, одним theme и одним lesson на квест.
  - Reason: так мы не ломаем будущую иерархию, но режем сложность первого релиза.

- Q-4: Сохранять draft локально или сразу на сервер?
  - Recommended answer: сначала всегда локально в отдельные draft tables; при наличии auth/network автоматически ставить private backup upload в outbox. `Отправить на проверку` остается отдельным явным действием.
  - Reason: пользователь не теряет работу офлайн, а сервер не получает review requests от каждого промежуточного сохранения.

- Q-5: Должен ли draft появляться в "Мои квесты" сразу после локального сохранения?
  - Recommended answer: да, с явным статусом `Draft`, `UploadQueued`, `SyncedPrivate`, `ReviewQueued`, `UnderReview`, `ChangesRequested`, `Published`.
  - Reason: для пользователя это его квест, даже если он еще не опубликован.

- Q-6: Можно ли создавать draft без авторизации?
  - Recommended answer: нет для MVP. FAB создания требует authenticated Firebase user; если пользователь не авторизован, показываем auth prompt.
  - Reason: authoring server path завязан на `uid`, иначе появятся orphan drafts и сложная миграция guest -> account.

### Authoring editor
- Q-7: Используем ли существующий экран прохождения как экран создания?
  - Recommended answer: нет, не используем `LessonRunnerRootComponent` целиком. Переиспользуем `QuestionContent` schema и выносим общие visual composables вопроса, но создаем отдельный `QuestAuthoringComponent`.
  - Reason: runner содержит таймеры, auto-answer, attempt save, result screen и scoring; это неправильная логика для редактора.

- Q-8: Какие типы вопросов должны быть доступны?
  - Recommended answer: архитектура editor поддерживает все четыре типа `SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank`; первый walking skeleton может начать с `SingleChoice`, но фича считается полной только после всех четырех.
  - Reason: schema и runner уже знают эти типы, а пользователь ожидает выбор типа вопроса.

- Q-9: Где хранить язык и сложность?
  - Recommended answer: язык хранить на уровне draft defaults и дублировать в `DraftQuestion.language`; difficulty хранить в `QuestionContent` и в metadata вопроса для быстрых списков.
  - Reason: default ускоряет создание, а per-question поля позволяют смешивать easy/hard и разные языки позже.

- Q-10: Какой default language?
  - Recommended answer: `ru` как стартовый default, UI должен хранить ISO 639-1 code и показывать человекочитаемое название.
  - Reason: текущий продукт русскоязычный, но model должна остаться международной.

- Q-11: Нужно ли автосохранение?
  - Recommended answer: raw edit state можно autosave локально, но `Save question` должен явно валидировать и создать canonical `QuestionContent` payload. Submit разрешен только по canonical saved payloads.
  - Reason: это защищает пользователя от потери текста и одновременно держит чистый payload для sync/review.

- Q-12: Как должна работать навигация вопросов?
  - Recommended answer: обязательны стрелки previous/next, список вопросов, add, duplicate, delete, reorder. При переходе с dirty invalid вопроса показываем предупреждение или сохраняем raw draft без canonical payload.
  - Reason: автору нужен быстрый обход, но invalid state нельзя случайно считать готовым вопросом.

- Q-13: Можно ли отправить квест на проверку с invalid вопросами?
  - Recommended answer: нет. Submit требует минимум один lesson и минимум один valid saved question; все included вопросы должны быть valid.
  - Reason: review queue не должна заполняться технически невалидными bundle.

- Q-14: Нужны ли изображения/аудио в MVP?
  - Recommended answer: image optional через `imageUrl` или draft media reference; audio не входит в MVP.
  - Reason: `QuestionContent` уже имеет `imageUrl`, а аудио потребует новую schema и storage lifecycle.

### Local data and sync
- Q-15: Использовать существующие public `Quest/Section/Theme/Lesson/Question` таблицы для draft?
  - Recommended answer: нет, создать отдельные draft tables/entities для authoring.
  - Reason: public sync сейчас может удалять/перетирать content по правилам каталога; draft не должен зависеть от `visibleOn` и public sync cursors.

- Q-16: Как синхронизировать authoring draft?
  - Recommended answer: через durable outbox, совместимый с будущим `OutgoingMutation`: `UploadDraft`, `SubmitReview`, `WithdrawReview`, `DeleteDraft`.
  - Reason: прямой write из UI ломает offline-first и retries; outbox сохраняет операции при process death.

- Q-17: Куда писать draft на сервере?
  - Recommended answer: `quest_authoring/{uid}/drafts/{draftId}` + subcollections `sections`, `themes`, `lessons`, `questions`; review requests отдельно в `quest_review_requests/{requestId}`.
  - Reason: так правила безопасности простые: владелец видит только свои drafts, public content остается закрыт для client writes.

- Q-18: Должен ли private backup создаваться до review?
  - Recommended answer: да, если пользователь авторизован и есть сеть, любой saved draft можно загрузить как private backup. Review request создается только после явного submit.
  - Reason: backup защищает работу пользователя, но не нагружает админов.

- Q-19: Как обрабатывать конфликт server revision новее local revision?
  - Recommended answer: в MVP блокировать submit и показывать `Conflict` state; resolution вручную позже. Для автоматического пути использовать last-write-wins только для private backup, не для submitted revision.
  - Reason: submitted content должен быть воспроизводимым и audit-friendly.

### Review and admin behavior
- Q-20: Админ правит пользовательский draft напрямую?
  - Recommended answer: нет, не напрямую. Админ либо оставляет `ChangesRequested` comments, либо создает server-side moderation revision/copy. User-owned draft остается audit source.
  - Reason: прямые правки чужого draft смешивают ответственность и усложняют конфликты.

- Q-21: Что именно отправляется на review?
  - Recommended answer: immutable snapshot revision: `draftId`, `authorUid`, `localRevision`, copied bundle hash, entity ids and payloads.
  - Reason: админ проверяет ровно ту версию, которую пользователь отправил, даже если пользователь потом продолжил локально что-то менять.

- Q-22: Может ли пользователь редактировать draft после submit?
  - Recommended answer: да, но это создает новую local revision, не меняя уже submitted snapshot. Resubmit создает новую review request revision или обновляет request с новым `revision`.
  - Reason: review остается стабильным, пользователь не заблокирован надолго.

- Q-23: Какие статусы review нужны?
  - Recommended answer: `Submitted`, `UnderReview`, `ChangesRequested`, `Rejected`, `Approved`, `Publishing`, `Published`, `PublicationFailed`.
  - Reason: эти статусы покрывают и ручную проверку, и server-side publication.

- Q-24: Как хранить comments от админа?
  - Recommended answer: comments должны ссылаться на optional target: `quest`, `sectionDraftId`, `themeDraftId`, `lessonDraftId`, `questionDraftId`, `field`. Свободный текст обязателен.
  - Reason: тогда UI может открыть конкретный проблемный вопрос или поле.

### Publication
- Q-25: Кто пишет в public `quests/sections/themes/lessons/questions`?
  - Recommended answer: только server/admin processor через Admin SDK или Cloud Functions. Клиент никогда не пишет public hierarchy.
  - Reason: текущие Firestore rules уже admin-only для nested content, и это правильная защита публичного каталога.

- Q-26: Как происходит publication?
  - Recommended answer: server publication processor берет approved immutable snapshot, валидирует bundle, назначает public ids, batch-writes public hierarchy, bump-ает versions/contentsVersion, пишет `catalogs/{catalogId}/sync_changes`, затем помечает draft/request as `Published`.
  - Reason: public sync клиентов уже зависит от sync changes, поэтому publication должна быть серверной и атомарной насколько позволяет Firestore.

- Q-27: Кто выбирает `visibleOn` и публичную полку?
  - Recommended answer: admin/server, не пользователь. User может предложить catalog/path, но public shelves (`home`, `arena`, etc.) выставляет review/publish side.
  - Reason: `visibleOn` управляет доступностью для всех пользователей.

- Q-28: После публикации квест остается в "Мои квесты"?
  - Recommended answer: да, как `Published` authoring item со ссылкой `publicQuestId`; параллельно публичный квест приходит через обычный public sync.
  - Reason: автору нужен ownership/status, а обычным пользователям нужен public catalog item.

- Q-29: Как редактировать опубликованный квест?
  - Recommended answer: только через новую authoring revision и review request. Public content не меняется до approval новой ревизии.
  - Reason: это сохраняет стабильность публичного контента и дает audit trail.

- Q-30: Как удалять draft и public quest?
  - Recommended answer: local/private draft до submit можно удалить пользователем; submitted review можно withdraw request; published quest удаляет или архивирует только admin/server.
  - Reason: public deletion влияет на всех пользователей и sync state.

### Architecture defaults
- Q-31: Где размещать модули?
  - Recommended answer: `shared/feature/quest-authoring/domain`, `shared/feature/quest-authoring/data`, `android/feature/quest-authoring/presentation`.
  - Reason: это отдельный feature slice, который использует существующие quest/question models, но не смешивает draft lifecycle с public quest repositories.

- Q-32: Какая первая реализационная вертикаль?
  - Recommended answer: `authenticated user -> create one-lesson draft -> add/save SingleChoice -> show in My Quests as Draft -> persist after restart`. После этого добавлять question list navigation, остальные типы, private upload, review submit.
  - Reason: это минимальный проверяемый срез без раннего захода в самые рискованные server/publication части.

- Q-33: Что считать "правильным" стратегическим решением для всей фичи?
  - Recommended answer: separate local/private authoring lifecycle + server-controlled publication. Нельзя хранить user drafts как полупубличные quests и нельзя давать клиенту писать public nested content.
  - Reason: это лучше всего совпадает с текущими Firestore rules, KMP offline-first архитектурой и будущей модерацией.

## Revision 3 - Owner Answers & Updated Decisions

### Confirmed owner decisions
- OD-1: MVP должен начинаться с готового экрана структуры, экрана создания/редактирования вопросов и проверки, что созданный локальный квест отображается в "Мои квесты".
- OD-2: Все созданные пользователем квесты сначала локальные. Локальный квест появляется в "Мои квесты" сразу после сохранения.
- OD-3: Синхронизация пользовательских локальных квестов должна идти через обычный sync lifecycle приложения, но с отдельной authoring-логикой. Не делать отдельный немедленный "backup upload" на каждое изменение.
- OD-4: При долгом нажатии на локальный квест в "Мои квесты" должна появляться menu action "Отправить на арену". В первом implementation slice действие может быть skeleton/queued marker без полной backend-публикации.
- OD-5: "Отправить на арену" создает очередь/заявку на сервере после синхронизации. Сервер помечает квест для проверки.
- OD-6: Проверка перед публикацией должна поддерживать несколько уровней/этапов, ориентировочно 3 уровня. Состояние прохождения уровней проверки должно быть видно разработчикам/admin-side tooling.
- OD-7: После прохождения проверки админами/переводчиками/тестировщиками сервер делает квест публичным.
- OD-8: Пользователь не создает новые public catalogs напрямую. Public catalog/path управляются существующей структурой и серверной публикацией.
- OD-9: UI создания должен опираться на полную существующую структуру `Quest -> Section -> Theme -> Lesson -> Question`, как в экранах квестов. Не ограничивать продуктовую модель одним lesson.
- OD-10: Пользователь выбирает в выпадающем списке, к какой структуре относится создаваемый контент. Архитектура должна не закрывать будущий сценарий "дополнить существующий квест/путь" через заявку.
- OD-11: Создание без полной авторизации допустимо, если у пользователя есть временный/анонимный id. После создания аккаунта временный id/drafts должны мигрировать в постоянный private ownership.
- OD-12: Для guest/anonymous режима нужен product warning: данные временные и могут быть потеряны, если не привязать аккаунт. Реализация warning не обязательна для первого coding slice, но архитектура должна это учитывать.
- OD-13: `LessonRunnerRootComponent` целиком не используется как редактор. Это принято: создаем отдельный authoring component, а переиспользование runner UI возможно только на уровне визуальных question components/schema.
- OD-14: В editor должны быть доступны все типы вопросов: `SingleChoice`, `MultipleChoice`, `Ordering`, `FillBlank`. Переключатель типа находится на экране вопроса.
- OD-15: Язык и сложность являются полями вопроса. Defaults выбираются на экране структуры/создания, но вопрос должен хранить собственные значения для последующей фильтрации.
- OD-16: Сервер и клиент должны иметь возможность фильтровать вопросы по language/difficulty через отдельные поля, а не только через payload.
- OD-17: Default language выбирается из пользовательских настроек/профиля. В MVP можно начать с простого списка доступных языков пользователя.
- OD-18: Автосохранение нужно: если пользователь начал создание и вышел, при повторном нажатии FAB открывается незаконченный draft.
- OD-19: Пользователь может удалить/сбросить текущий draft с подтверждением.
- OD-20: Навигация вопросов должна поддерживать previous/next. Если пользователь нажимает next на последнем вопросе, создается новый пустой вопрос.
- OD-21: Question list, duplicate и advanced reorder не обязательны для первого slice; их можно оставить как расширение, если базовая next-flow работает понятно.
- OD-22: Вопрос нельзя сохранить, пока он не проходит правила валидности.
- OD-23: Для отправки на арену нужны отдельные правила готовности. Минимальное правило: должен быть хотя бы один EASY и один HARD вопрос, плюс все отправляемые вопросы должны быть valid/saved.
- OD-24: Изображения в вопросах входят в scope. Audio не входит в MVP.
- OD-25: Изображения можно хранить по обычному media/path подходу проекта; отдельная папка нужна только если storage/rules constraints вынудят ее добавить.
- OD-26: Draft не должен публиковаться прямым client write в public hierarchy. Сервер переносит approved-контент в public structure.
- OD-27: Если server `updatedAt` новее локального `updatedAt`, нужен conflict state с пользовательским выбором. Детальная UX-логика конфликта будет отдельной задачей, но architecture должна хранить timestamps/revisions.
- OD-28: Админ не правит приватный draft пользователя напрямую. Админ может проверять submitted snapshot/request и править публичный квест после публикации или через отдельную moderation/public flow.
- OD-29: Пользователь может редактировать локальный draft после submit; для уже public quest редактирование должно идти через новую заявку на изменение.
- OD-30: Первая техническая вертикаль должна доказать: создать, редактировать, синхронизировать, войти на другом устройстве и получить тот же authoring draft после синхронизации. "Отправить на арену" в первой версии может быть skeleton-кнопкой/menu action.

### Updated answers overriding Revision 2 recommendations
- UA-1: Q-3 "Сколько уровней структуры поддерживать": Revision 2 предлагала UI MVP с одним lesson. Updated answer: UI должен быть спроектирован под полную существующую структуру. Первый coding slice может быть постепенным, но продуктовый MVP не должен закреплять single-lesson limitation.
- UA-2: Q-6 "Можно ли создавать без авторизации": Revision 2 предлагала запрет. Updated answer: anonymous/temp user creation допустим, если есть стабильный temporary id и migration path в постоянный аккаунт.
- UA-3: Q-8 "Какие типы вопросов": Revision 2 допускала первый skeleton только с `SingleChoice`. Updated answer: editor MVP должен поддерживать все текущие типы schema; внутренняя реализация может идти по типам по очереди, но acceptance фичи требует все четыре.
- UA-4: Q-10 "Default language": Revision 2 предлагала `ru`. Updated answer: default берется из user settings/profile languages; `ru` может быть fallback.
- UA-5: Q-11 "Автосохранение": Revision 2 делила raw autosave и explicit save. Updated answer: autosave текущего незаконченного draft обязателен; canonical question save по-прежнему валидирует payload.
- UA-6: Q-12 "Навигация": Revision 2 включала list/add/duplicate/delete/reorder. Updated answer: previous/next обязательны; next на последнем создает пустой вопрос; delete/reset с подтверждением нужен; list/duplicate/reorder можно отложить.
- UA-7: Q-13 "Submit invalid": Updated answer: invalid questions нельзя сохранять и нельзя отправлять. Для "Отправить на арену" требуется минимум один EASY и один HARD вопрос.
- UA-8: Q-18 "Private backup": Revision 2 называла это private backup до review. Updated answer: не вводить отдельное понятие backup; это обычная authoring sync на расписании/при подключении, с отдельной логикой и без write per edit.
- UA-9: Q-20 "Админ правит draft": Updated answer: admin не правит private draft напрямую. Review работает по submitted snapshot/request; публичный контент правится server/admin-side после approval.
- UA-10: Q-32 "Первая вертикаль": Updated answer: first vertical = local create/edit + local display in My Quests + authoring sync/cross-device restore; arena submission action can remain skeleton until backend review queue is implemented.

### Revised implementation slices
- Slice 1: local authoring foundation.
  - Add draft domain/data models with full hierarchy support.
  - Add temporary/anonymous owner id support at repository boundary.
  - Persist current unfinished draft and reopen it from FAB.
  - Show local drafts in "Мои квесты".
- Slice 2: authoring UI.
  - Replace `QuestCreateRoot` placeholder.
  - Add structure screen with full path chooser.
  - Add question editor with type switch, language, difficulty, image field, previous/next.
  - Implement next-on-last creates blank question.
- Slice 3: validation and readiness.
  - Validate all four `QuestionContent` types.
  - Block question save until valid.
  - Add readiness check for arena: at least one EASY and one HARD valid saved question.
- Slice 4: authoring sync.
  - Sync local drafts through normal sync lifecycle into private authoring server path.
  - Support cross-device restore.
  - Track `localUpdatedAt`, `serverUpdatedAt`, `localRevision`, `serverRevision`.
  - Add conflict state when server is newer.
- Slice 5: arena skeleton.
  - Long-press menu in "Мои квесты": "Отправить на арену".
  - Enqueue/send review request marker.
  - Show queued/submitted local status.
- Slice 6: backend review/publication.
  - Add three-stage review state.
  - Let server/admin tooling access submitted snapshots.
  - Publish approved snapshot into public hierarchy and sync markers.

### Remaining questions after owner answers
- RQ-1: Что конкретно входит в "полный path chooser" на первом экране: выбрать existing public `Catalog -> Quest -> Section -> Theme -> Lesson`, создать новый private `Quest -> Section -> Theme -> Lesson`, или оба режима сразу?
- RQ-2: Какой источник temporary/anonymous id уже есть в проекте: Firebase Anonymous Auth, локальный UUID, tpovId или другой user identity layer?
- RQ-3: При синхронизации draft на сервер: один документ bundle или subcollections `sections/themes/lessons/questions`? Рекомендация остается subcollections, но это надо сверить с лимитами и текущим sync style.
- RQ-4: Что именно означает "арена" технически: отдельный public shelf в `visibleOn`, review queue type, или оба понятия?
- RQ-5: Кто является тремя уровнями проверки: tester, translator, admin/developer, или другая роль/порог qualification?
- RQ-6: Должен ли reviewer видеть полный submitted snapshot с текстами вопросов, или только скачать/открыть его через отдельный admin tool? Product decision: private drafts не редактируются, но submitted snapshot must be reviewable.

## Revision 4 - Plain-Language Clarifications

### Simple meanings
- "Локальный/приватный квест" = квест пользователя, который живет у него локально и синхронизируется только в его приватную server-зону. Он не виден другим пользователям.
- "Отправить на арену" = пользователь просит проверить приватный квест и, если он пройдет проверку, сделать его публичным в нужном месте.
- "Копия на проверку" = сервер в момент отправки берет текущую версию приватного квеста и кладет ее в папку проверок. Это как фотография квеста в момент отправки: если пользователь потом продолжит редактировать свой приватный квест, проверяющие все равно смотрят ту отправленную версию.
- "Публичная структура" = существующие `quests`, `sections`, `themes`, `lessons`, `questions`. Клиент создания квестов ее не трогает.

### Updated answers
- A-1: При нажатии FAB пользователь всегда создает или продолжает свой приватный квест. Он не добавляет вопросы напрямую в публичный квест.
- A-2: Позже в меню приватного квеста можно добавить действия типа "Предложить дополнение" или "Отправить на арену/в архив". Тогда пользователь выбирает, к какому публичному квесту/пути относится предложение.
- A-3: Для курсов аналог "арены" может называться "архив" или другим target type. Технически это не публикация сразу, а заявка на проверку с типом назначения.
- A-4: Создание без обычной регистрации можно поддерживать через Firebase Anonymous Auth. В проекте уже есть автоматический anonymous sign-in на старте приложения, поэтому пользователь получает временный Firebase UID без экрана входа.
- A-5: Если пользователь потом привязывает настоящий аккаунт к этому anonymous user, его приватные квесты должны остаться у него. Если пользователь удалит приложение/данные до привязки, такие квесты могут потеряться.
- A-6: На сервере нужны три понятные зоны:
  - public content: текущие публичные коллекции `quests/sections/themes/lessons/questions`;
  - private authoring: приватные квесты по пользователям, например `quest_authoring/{uid}/drafts/{draftId}/...`;
  - review queue: заявки/копии на проверку, например `quest_review_requests/{requestId}`.
- A-7: В private authoring лучше хранить не один огромный документ, а структуру, похожую на публичную: draft quest + sections + themes + lessons + questions. Это сложнее на вид, но надежнее: нет лимита одного большого документа, проще синхронизировать изменения и проще проверять отдельные вопросы.
- A-8: Public content остается "папкой публичного"; private authoring остается "папкой пользователей"; review queue остается "папкой проверки". Так разработчикам и серверу проще понимать, где что лежит.
- A-9: Три уровня проверки должны быть параллельными. То есть заявка может иметь три независимые проверки, например `tester`, `translator`, `admin/developer`. Публикация разрешается, когда все нужные проверки approved.
- A-10: Проверяющий может видеть отправленную копию квеста целиком, включая тексты вопросов, потому что иначе он не сможет проверить. Но он не меняет приватный квест пользователя.
- A-11: Если проверяющий нашел проблему, он оставляет комментарий/статус. Пользователь исправляет свой приватный квест и отправляет новую копию на проверку.
- A-12: Если нужно, чтобы проверяющий сам правил текст, это отдельный moderation/public-edit flow после базовой схемы. В первом дизайне не смешивать это с private draft.
- A-13: `visibleOn` не является пользовательским вопросом UI. Это внутреннее поле публичного квеста, которое сервер выставит после успешной проверки, если нужно показать квест на home/arena/archive/etc.

### Revised remaining questions in plain language
- PRQ-1: На первом экране пользователь создает только новый приватный квест или сразу нужен режим "предложить дополнение к существующему публичному квесту"?
- PRQ-2: Названия трех параллельных проверок такие: тест, перевод, финальное админ-одобрение?
- PRQ-3: Для "арены" и "архива" делаем одно действие "Отправить на проверку", а внутри выбираем цель: arena/archive/course/etc?
- PRQ-4: Проверяющий в первой версии только approve/reject/comment, без редактирования текста?

## Revision 5 - Final Answers For Current Scope

### Resolved answers
- RA-1: В первом scope пользователь только создает новый приватный квест. Режим "предложить дополнение к существующему публичному квесту" добавляется позже через отдельное menu action.
- RA-2: Проверки не надо переименовывать заново. Использовать ADR-0005 `QuestChecks`:
  - `content` — автомодерация/запрещенный контент/слова;
  - `translation` — переводческая проверка;
  - `logic` — ручная проверка логики вопроса/квеста.
- RA-3: Проверки идут параллельно, как в ADR-0005. Это не scope первого client slice; сейчас только закладываем поля/status так, чтобы потом не переделывать.
- RA-4: Пользователь при создании выбирает catalog. Для курса `COURSE` сам catalog/тип определяет, что публикация идет в архив, а не на арену.
- RA-5: Для обычного квеста `REGULAR` после проверки сервер может отправить его на arena/home/etc по правилам ADR-0005. Клиент создания не выбирает `visibleOn`.
- RA-6: Не делать в UI отдельный выбор `arena/archive/course` как пользовательскую цель в первом scope. Цель публикации выводится из выбранного catalog/типа квеста и серверной логики.
- RA-7: В будущем catalog/domain может получить явное поле назначения публикации, например `questType` или `publicationTarget`, если серверу и UI нужно отличать course/archive от regular/arena без эвристик по имени.
- RA-8: Проверяющие не просто approve/reject. Они оценивают по шкале 1..3, а сервер по своей логике решает, прошел квест проверку или нет.
- RA-9: Логика вычисления "прошел/не прошел" по оценкам проверяющих является backend/review feature, не частью текущего client authoring scope.
- RA-10: Для текущей фичи достаточно хранить и показывать локальные/синхронизированные статусы: draft, sync pending/synced, queued for review/sent skeleton. Детальная review scoring model будет отдельной задачей.

### ADR-0005 grounding
- ADR-0005 уже разделяет:
  - `QuestType`: `REGULAR` или `COURSE`;
  - `QuestPhase`: `DRAFT`, `IN_REVIEW`, `PUBLISHED`, `RETIRED`;
  - public shelves through `visibleOn`: `home`, `arena`, `tournament`, `tournamentFinal`, `archive`.
- ADR-0005 уже говорит, что course идет в archive, а regular quest идет через arena/tournament/home.
- Поэтому authoring feature не должна придумывать отдельную модель проверки. Она должна быть совместима с ADR-0005 и только подготовить локальный/private квест к будущей отправке в review.

### Current implementation boundary
- In current scope implement:
  - create private local quest;
  - autosave unfinished draft;
  - edit full hierarchy/questions;
  - show in "Мои квесты";
  - sync private draft through normal sync lifecycle;
  - keep long-press action placeholder/skeleton for review submission.
- Defer:
  - adding proposal-to-existing-public-quest flow;
  - reviewer UI;
  - 1..3 scoring logic;
  - server decision rules;
  - final server publication into public hierarchy.
