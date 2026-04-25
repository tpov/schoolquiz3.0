---
date: 2026-04-22
feature: home-and-my-quests
author: architect-high-level
single-source-of-truth: true
---

# API Contract: Home Quests & My Quests + Cascading Catalog Sync

**CANONICAL SIGNATURES — единственный источник правды для публичных интерфейсов.**

Другие документы (`01-architecture.md`, `02-behavior.md`, `03-decisions.md`, phase plan files) ссылаются на этот файл (`см. 06-api-contract.md:NN`), не дублируют сигнатуры.

---

## 1. Domain Models (расширение + новые)

### 1.1 Catalog (расширение существующего)

```kotlin
// shared/core/catalog/domain/model/Catalog.kt
data class Catalog(
    val id: CatalogId,
    val name: String,
    val picturePath: String?,      // относительный Firebase Storage path
    val pictureUrl: String?,       // resolved HTTPS URL + ?v=version suffix
    val version: Long = 1L,        // NEW: инкрементируется сервером при любом write
    val contentsVersion: Long = 0L, // NEW: инкрементируется при изменении descendants
    val lastModifiedAt: Long = 0L,  // NEW: Unix millis (FieldValue.serverTimestamp())
    val archived: Boolean = false,  // NEW: soft-delete flag
) {
    init {
        require(name.isNotBlank())
        require(picturePath == null || (picturePath.isNotBlank()
            && !picturePath.startsWith("https://")
            && !picturePath.startsWith("http://")
            && !picturePath.startsWith("gs://")))
        require(pictureUrl == null || pictureUrl.startsWith("https://"))
        require(version >= 1)
        require(contentsVersion >= 0)
        require(lastModifiedAt >= 0)
    }
}

// Existing — no change
@JvmInline value class CatalogId(val value: String) {
    init { require(value.isNotBlank()) }
}
```

### 1.2 Quest (новый domain model)

```kotlin
// shared/feature/quest/domain/model/Quest.kt  [CANONICAL — Walking Skeleton ✓]
data class Quest(
    val id: QuestId,
    val catalogId: CatalogId,
    val authorUid: String,             // Firebase Auth UID (not tpovId)
    val title: String,
    val picturePath: String?,
    val pictureUrl: String?,
    val visibleOn: Set<String>,        // values: {"home","arena","tournament","tournamentFinal","archive"}
    val averageRating: Float?,         // null = no ratings (DRAFT); 0.0..3.0 step 0.1
    val averageRatingCount: Int,       // server-managed; 0 for new quests
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
) {
    companion object {
        val VALID_SHELVES = setOf("home", "arena", "tournament", "tournamentFinal", "archive")
    }
    init {
        require(title.isNotBlank())
        require(authorUid.isNotBlank())
        require(picturePath == null || (picturePath.isNotBlank()
            && !picturePath.startsWith("https://")
            && !picturePath.startsWith("http://")))
        require(pictureUrl == null || pictureUrl.startsWith("https://"))
        require(visibleOn.all { it in VALID_SHELVES })
        require(averageRating == null || averageRating in 0.0f..3.0f)
        require(averageRatingCount >= 0)
        require(version >= 1)
        require(contentsVersion >= 0)
        require(lastModifiedAt >= 0)
    }
}

@JvmInline value class QuestId(val value: String) { init { require(value.isNotBlank()) } }
```

### 1.3 Section, Theme, Lesson (одинаковая структура)

```kotlin
// shared/feature/{section,theme,lesson}/domain/model/{Entity}.kt
data class Section(
    val id: SectionId,
    val questId: QuestId,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
) {
    init {
        require(title.isNotBlank()); require(order >= 0)
        require(version >= 1); require(contentsVersion >= 0); require(lastModifiedAt >= 0)
    }
}
@JvmInline value class SectionId(val value: String) { init { require(value.isNotBlank()) } }

// Theme: идентично, parentId = sectionId: SectionId
// Lesson: идентично, parentId = themeId: ThemeId
```

### 1.4 Question (leaf — нет contentsVersion)

```kotlin
// shared/feature/question/domain/model/Question.kt
data class Question(
    val id: QuestionId,
    val lessonId: LessonId,
    val text: String,
    val payload: String,       // JSON string, ADR-0003 schema
    val language: String,      // ISO 639-1 (no filtering logic in this feature)
    val order: Int,
    val version: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
) {
    init {
        require(text.isNotBlank())
        require(payload.isNotBlank())
        require(language.isNotBlank())
        require(order >= 0)
        require(version >= 1)
        require(lastModifiedAt >= 0)
    }
}
@JvmInline value class QuestionId(val value: String) { init { require(value.isNotBlank()) } }
```

---

## 2. Repository Interfaces

### 2.1 CatalogRepository (расширение)

```kotlin
// shared/core/catalog/domain/repository/CatalogRepository.kt
// Updated phase-03: removed Syncable (CascadingSyncOrchestrator handles sync now)
interface CatalogRepository {
    fun observeAll(): Flow<List<Catalog>>          // Persistence: see 08-storage-model.md §CatalogDao
    suspend fun getById(id: CatalogId): Catalog?
    // Returns Set<CatalogId> of non-archived catalogs processed on this refresh.
    // Cursor managed internally by CatalogRepositoryImpl via SyncStateRepository.
    suspend fun refreshFromRemote(): Result<Set<CatalogId>>
}
```

### 2.2 QuestRepository (новый)

```kotlin
// shared/feature/quest/domain/repository/QuestRepository.kt  [CANONICAL — Walking Skeleton ✓]
interface QuestRepository {
    /** Observe quests owned by given uid, optionally filtered by catalog. Persistence: see 08-storage-model.md §QuestDao */
    fun observeMyQuests(authorUid: String, catalogId: CatalogId? = null): Flow<List<Quest>>

    /** Observe quests visible on given shelf. Persistence: see 08-storage-model.md §QuestDao */
    fun observeByShelf(shelf: String): Flow<List<Quest>>

    suspend fun getById(id: QuestId): Quest?

    /**
     * Refresh quests from Firestore for changed catalogs.
     * Executes dual Query A (own quests by authorUid+catalogId) + Query B (public by visibleOn).
     * cursor: Long — передаётся orchestrator-ом (из SyncStateRepository.getCursor("quests"));
     *   QuestRepositoryImpl НЕ читает cursor внутри — только принимает как параметр.
     * cursor advancement: orchestrator вызывает SyncStateRepository.setCursor("quests", ...) после
     *   успешного результата (QuestRepositoryImpl возвращает changed ids, cursor management = orchestrator).
     */
    suspend fun refreshFromRemote(
        currentUserUid: String?,            // null → skip Query A
        availableShelves: Set<String>,      // {"home","arena"} baseline
        catalogIdsToSync: Set<CatalogId>,   // batch ≤30 per Firestore in-filter limit
        cursor: Long,                       // from SyncStateRepository.getCursor("quests") — passed by orchestrator
    ): Result<Set<QuestId>>                 // returns ids of changed quests (for cascade trigger)
}
```

### 2.3 SectionRepository, ThemeRepository, LessonRepository (одинаковая структура)

```kotlin
// shared/feature/section/domain/repository/SectionRepository.kt
interface SectionRepository {
    fun observeByQuest(questId: QuestId): Flow<List<Section>>   // Persistence: see 08-storage-model.md §SectionDao
    suspend fun getById(id: SectionId): Section?

    /**
     * Refresh sections for changed quests. Batch ≤30 questIds.
     * Returns SectionIds where contentsVersion grew (for cascade trigger to themes).
     */
    suspend fun refreshByParents(
        questIds: Set<QuestId>,
        cursor: Long,
    ): Result<Set<SectionId>>

    suspend fun getLocalContentsVersion(id: SectionId): Long?   // for cascade predicate
}

// ThemeRepository: parentIds: Set<SectionId>, cursor, returns Set<ThemeId>
// LessonRepository: parentIds: Set<ThemeId>, cursor, returns Set<LessonId>
```

### 2.4 QuestionRepository (leaf — нет contentsVersion, нет cascade trigger)

```kotlin
// shared/feature/question/domain/repository/QuestionRepository.kt
interface QuestionRepository {
    fun observeByLesson(lessonId: LessonId): Flow<List<Question>>  // Persistence: see 08-storage-model.md §QuestionDao
    suspend fun getById(id: QuestionId): Question?

    /** Refresh questions for changed lessons. Batch ≤30 lessonIds. Returns count for logging. */
    suspend fun refreshByParents(
        lessonIds: Set<LessonId>,
        cursor: Long,
    ): Result<Unit>
}
```

### 2.5 AuthRepository (новый — Decision #42)

```kotlin
// shared/feature/app-shell/domain/repository/AuthRepository.kt  [Walking Skeleton ✓]
interface AuthRepository {
    /** One-shot: current Firebase Auth UID. Returns null for guest/unauthenticated. */
    suspend fun currentUid(): String?

    /** Continuous: emits new value on sign-in / sign-out. First emission is immediate. */
    fun observeUid(): Flow<String?>
}
```

### 2.6 SyncStateRepository (существующий — не меняется)

```kotlin
// shared/core/sync/domain/SyncStateRepository.kt  [уже создан, нужно добавить Koin binding]
interface SyncStateRepository {
    suspend fun getCursor(collectionId: String): Long         // 0L if not set
    suspend fun setCursor(collectionId: String, value: Long)
    suspend fun markCascadeInProgress(parentId: String, parentType: String, pendingChildIds: Set<String>)
    suspend fun markCascadeCompleted(parentId: String, parentType: String)
    suspend fun getPendingCascades(): List<PendingCascade>
}

data class PendingCascade(
    val parentId: String,
    val parentType: String,
    val pendingChildIds: Set<String>,
)
```

---

## 3. Use Cases

### 3.1 ObserveMyQuestsUseCase

```kotlin
// shared/feature/quest/domain/use_case/ObserveMyQuestsUseCase.kt  [Walking Skeleton ✓]
class ObserveMyQuestsUseCase(private val questRepository: QuestRepository) {
    operator fun invoke(authorUid: String, catalogId: CatalogId? = null): Flow<List<Quest>> =
        questRepository.observeMyQuests(authorUid, catalogId)
}
```

### 3.2 ObserveCatalogsUseCase

```kotlin
// shared/core/catalog/domain/use_case/ObserveCatalogsUseCase.kt  [existing pattern]
class ObserveCatalogsUseCase(private val catalogRepository: CatalogRepository) {
    operator fun invoke(): Flow<List<Catalog>> = catalogRepository.observeAll()
}
```

### 3.3 SyncQuestsUseCase

```kotlin
// shared/feature/quest/domain/use_case/SyncQuestsUseCase.kt  [Walking Skeleton ✓]
class SyncQuestsUseCase(private val questRepository: QuestRepository) {
    suspend operator fun invoke(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> =
        questRepository.refreshFromRemote(currentUserUid, availableShelves, catalogIdsToSync, cursor)
}
```

### 3.4 SyncSectionsUseCase, SyncThemesUseCase, SyncLessonsUseCase, SyncQuestionsUseCase

```kotlin
// shared/feature/section/domain/use_case/SyncSectionsUseCase.kt  [pattern — Walking Skeleton ✓]
class SyncSectionsUseCase(private val sectionRepository: SectionRepository) {
    suspend operator fun invoke(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> =
        sectionRepository.refreshByParents(questIds, cursor)
}
// SyncThemesUseCase, SyncLessonsUseCase, SyncQuestionsUseCase — same pattern with respective types
```

---

## 4. CascadingSyncOrchestrator + SyncLevel

```kotlin
// shared/core/sync/src/commonMain/kotlin/.../CascadingSyncOrchestrator.kt
// см. 03-decisions.md ADR-CMP-49 для полной сигнатуры

enum class SyncLevel {
    Catalog, Quest, Section, Theme, Lesson, Question;

    val next: SyncLevel? get() = when (this) {
        Catalog  -> Quest
        Quest    -> Section
        Section  -> Theme
        Theme    -> Lesson
        Lesson   -> Question
        Question -> null
    }

    val collectionId: String get() = when (this) {
        Catalog  -> "catalogs"
        Quest    -> "quests"
        Section  -> "sections"
        Theme    -> "themes"
        Lesson   -> "lessons"
        Question -> "questions"
    }
}

class CascadingSyncOrchestrator(
    private val catalogRepo: CatalogRepository,
    private val questRepo: QuestRepository,
    private val sectionRepo: SectionRepository,
    private val themeRepo: ThemeRepository,
    private val lessonRepo: LessonRepository,
    private val questionRepo: QuestionRepository,
    private val syncStateRepo: SyncStateRepository,
    private val authRepo: AuthRepository,
    private val userStatsRepo: UserStatsRepository,
) : Syncable {
    override suspend fun sync(): Result<Unit> = syncCascade(SyncLevel.Catalog, emptySet())

    // Internal: see 03-decisions.md ADR-CMP-49 for full recursive implementation
    internal suspend fun syncCascade(level: SyncLevel, parentIds: Set<String>): Result<Unit>
}
```

---

## 5. Navigation Contracts (Decisions #41, #45)

### 5.1 LocalConfig (sealed extension)

```kotlin
// shared/feature/app-shell/domain/model/TabConfig.kt — добавить
sealed interface LocalConfig : TabConfig {
    data object MyQuestsRoot : LocalConfig        // existing
    data object HomeQuestsRoot : LocalConfig      // existing
    data object SettingsRoot : LocalConfig        // existing
    data object DesignCatalogRoot : LocalConfig   // existing
    data object EmptyRoot : LocalConfig            // existing
    data object QuestCreateRoot : LocalConfig      // NEW (Decision #41)
}
```

### 5.2 Destination (sealed extension)

```kotlin
// shared/feature/app-shell/domain/model/Destination.kt — добавить
sealed interface Destination {
    data object Back : Destination                  // existing
    data class SwitchTab(val tab: TabSection) : Destination  // existing
    data class SelectSection(val section: DrawerSection) : Destination  // existing
    data object OpenDrawer : Destination             // existing
    data object CloseDrawer : Destination            // existing
    data object OpenDesignCatalog : Destination      // existing
    data object OpenQuestCreate : Destination        // NEW (Decision #41)
    // Semantics: push LocalConfig.QuestCreateRoot on top of current local stack
    // Guard: if active == QuestCreateRoot → no-op (Decision #47)
}
```

---

## 6. Decompose Components

### 6.1 MyQuestsComponent

```kotlin
// android/feature/quest/presentation/MyQuestsComponent.kt
interface MyQuestsComponent {
    val state: StateFlow<MyQuestsUiState>
    fun onCatalogSelected(id: CatalogId?)  // null = "Все категории"
    fun onCreateQuestClick()               // → Navigator.goTo(Destination.OpenQuestCreate)
}

data class MyQuestsUiState(
    val quests: List<QuestDisplayItem> = emptyList(),
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val selectedCatalogId: CatalogId? = null,
    val isGuest: Boolean = false,
    val isLoading: Boolean = false,
)
```

### 6.2 HomeQuestsComponent

```kotlin
// android/feature/quest/presentation/HomeQuestsComponent.kt
interface HomeQuestsComponent {
    val state: StateFlow<HomeQuestsUiState>
    fun onCatalogClick(id: CatalogId)  // TODO placeholder — future catalog detail
}

data class HomeQuestsUiState(
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
)
```

---

## 7. UI Models

```kotlin
// android/core/designsystem/model/QuestDisplayItem.kt
data class QuestDisplayItem(
    val id: QuestId,
    val title: String,
    val pictureUrl: String?,         // HTTPS URL + ?v=version; null → placeholder icon
    val averageRating: Float?,       // null → outline stars; 0.0..3.0
    val averageRatingCount: Int = 0, // total vote count; 0 → hide count label
)

// Existing — no change
// android/core/designsystem/model/CatalogDisplayItem.kt
data class CatalogDisplayItem(val id: CatalogId, val name: String, val pictureUrl: String?)
```

### Extension functions

```kotlin
// android/feature/quest/presentation/mapper/QuestToDisplayItem.kt
fun Quest.toDisplayItem(): QuestDisplayItem = QuestDisplayItem(
    id = id,
    title = title,
    pictureUrl = pictureUrl,
    averageRating = averageRating,
    averageRatingCount = averageRatingCount,
)

// Existing pattern — reuse
// fun Catalog.toDisplayItem(): CatalogDisplayItem  (already exists in designsystem)
```

---

## 8. Firestore DTOs

### 8.1 CatalogDto (расширение)

```kotlin
// shared/core/catalog/data/src/commonMain/kotlin/.../dto/CatalogDto.kt
data class CatalogDto(
    val id: String,
    val name: String,
    val picturePath: String?,
    val version: Long,          // NEW
    val contentsVersion: Long,  // NEW
    val lastModifiedAt: Long,   // NEW (converted from Firestore Timestamp → Long millis)
    val archived: Boolean,      // NEW
)
```

### 8.2 QuestDto (новый)

```kotlin
// shared/feature/quest/data/src/commonMain/kotlin/.../dto/QuestDto.kt
data class QuestDto(
    val id: String,
    val catalogId: String,
    val authorUid: String,
    val title: String,
    val picturePath: String?,
    val visibleOn: List<String>,    // List в DTO (Firestore Array), Set в domain
    val averageRating: Double?,     // Double в Firestore, Float? в domain
    val averageRatingCount: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
```

### 8.3 SectionDto (новый)

```kotlin
// shared/feature/section/data/src/commonMain/kotlin/.../dto/SectionDto.kt
data class SectionDto(
    val id: String,
    val questId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
// ThemeDto: parentField = sectionId; LessonDto: parentField = themeId — same structure
```

### 8.4 QuestionDto (новый, leaf — нет contentsVersion)

```kotlin
// shared/feature/question/data/src/commonMain/kotlin/.../dto/QuestionDto.kt
data class QuestionDto(
    val id: String,
    val lessonId: String,
    val text: String,
    val payload: String,
    val language: String,
    val order: Int,
    val version: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
```

---

## 9. Remote Data Source Interfaces

```kotlin
// shared/core/catalog/data/src/commonMain/.../CatalogRemoteDataSource.kt
interface CatalogRemoteDataSource {
    suspend fun fetchChangedSince(cursor: Long): List<CatalogDto>
    // Replaces: suspend fun fetchAll(): List<CatalogDto>
}

// shared/feature/quest/data/src/commonMain/.../QuestRemoteDataSource.kt
interface QuestRemoteDataSource {
    /** Query A: own quests by authorUid + catalogIds */
    suspend fun fetchOwnChanged(
        authorUid: String,
        catalogIds: Set<String>,    // ≤30 per call (Firestore in-filter limit)
        cursor: Long,
    ): List<QuestDto>

    /** Query B: public quests by visibleOn shelves */
    suspend fun fetchPublicChanged(
        shelves: Set<String>,
        cursor: Long,
    ): List<QuestDto>
}

// shared/feature/section/data/.../SectionRemoteDataSource.kt
interface SectionRemoteDataSource {
    suspend fun fetchChangedByParents(questIds: Set<String>, cursor: Long): List<SectionDto>
}
// ThemeRemoteDataSource, LessonRemoteDataSource, QuestionRemoteDataSource — same pattern
```

---

## 10. Firestore Collection Schema + Composite Indexes

### Composite Indexes (ручная регистрация в Firebase Console / firestore.indexes.json)

| # | Collection | Fields | Exact query shape |
|---|-----------|--------|-------------------|
| 1 | `quests` | `authorUid ASC, catalogId ASC, lastModifiedAt ASC` | `quests.where('authorUid','==',uid).where('catalogId','in',catalogIds).where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |
| 2 | `quests` | `visibleOn ARRAY, lastModifiedAt ASC` | `quests.where('visibleOn','array-contains-any',shelves).where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |
| 3 | `sections` | `questId ASC, lastModifiedAt ASC` | `sections.where('questId','in',questIds).where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |
| 4 | `themes` | `sectionId ASC, lastModifiedAt ASC` | `themes.where('sectionId','in',sectionIds).where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |
| 5 | `lessons` | `themeId ASC, lastModifiedAt ASC` | `lessons.where('themeId','in',themeIds).where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |
| 6 | `questions` | `lessonId ASC, lastModifiedAt ASC` | `questions.where('lessonId','in',lessonIds).where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |

**Note**: batch ≤30 ids per `in` filter (Firestore SDK limit). Orchestrator splits into chunks if `parentIds.size > 30`.

### Single-field Auto Indexes (Firebase creates automatically — no manual registration needed)

| Collection | Field | Exact query shape |
|-----------|-------|-------------------|
| `catalogs` | `lastModifiedAt ASC` | `catalogs.where('lastModifiedAt','>',cursor).orderBy('lastModifiedAt')` |

**Note**: Firestore auto-indexes every single field in descending and ascending order. The `catalogs` delta-pull only needs this auto index — no manual composite index required.

### Firestore Document Schemas (canonical)

```json
// catalogs/{catalogId}
{
  "name": "Опросы",
  "picturePath": "catalog-pictures/surveys.jpg",
  "version": 3,
  "contentsVersion": 17,
  "archived": false,
  "lastModifiedAt": 1714000000000
}

// quests/{questId}
{
  "catalogId": "surveys",
  "authorUid": "abc123xyzFirebaseUID",
  "title": "Мой квест о котах",
  "picturePath": "quest-pictures/q-uuid.jpg",
  "visibleOn": ["home", "arena"],
  "averageRating": 2.7,
  "averageRatingCount": 15,
  "version": 5,
  "contentsVersion": 3,
  "archived": false,
  "lastModifiedAt": 1714000000000
}

// sections/{sectionId}
{
  "questId": "q-uuid-1",
  "title": "Введение",
  "order": 0,
  "version": 1,
  "contentsVersion": 2,
  "archived": false,
  "lastModifiedAt": 1714000000000
}

// themes/{themeId} — sectionId instead of questId, otherwise same
// lessons/{lessonId} — themeId instead of sectionId, otherwise same

// questions/{questionId} — leaf, no contentsVersion
{
  "lessonId": "l-uuid-1",
  "text": "Когда котов одомашнили?",
  "payload": "{\"type\":\"SingleChoice\",\"options\":[...],\"correctIndex\":0}",
  "language": "ru",
  "order": 0,
  "version": 1,
  "archived": false,
  "lastModifiedAt": 1714000000000
}
```

**Note**: `lastModifiedAt` хранится как Firestore `Timestamp` (native type), ставится через `FieldValue.serverTimestamp()`. При mapping `DocumentSnapshot → Dto`: `snapshot.getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L`.

---

## 11. Firebase Security Rules (canonical block)

```javascript
// firestore.rules — добавить к существующему catalogs rule
match /catalogs/{catalogId} {
  allow read: if true;       // public — no auth required (existing)
  allow write: if isAdmin(); // admin-only (existing)
}

match /quests/{questId} {
  allow read: if request.auth != null
    && (resource.data.authorUid == request.auth.uid
        || resource.data.visibleOn.hasAny(['home', 'arena', 'tournament', 'tournamentFinal']));
  allow create: if request.auth != null
    && request.resource.data.authorUid == request.auth.uid;
  allow update, delete: if request.auth != null
    && resource.data.authorUid == request.auth.uid;
}

// Nested content — MVP: admin-only write; any-auth read
match /sections/{sectionId}   { allow read: if request.auth != null; allow write: if isAdmin(); }
match /themes/{themeId}       { allow read: if request.auth != null; allow write: if isAdmin(); }
match /lessons/{lessonId}     { allow read: if request.auth != null; allow write: if isAdmin(); }
match /questions/{questionId} { allow read: if request.auth != null; allow write: if isAdmin(); }

function isAdmin() {
  return request.auth != null
    && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.qualifications.admin >= 100;
}
```

---

## 12. Backend Hard Dependencies (Decision #54)

**DOCUMENTED HARD DEPENDENCY — out-of-scope for client phase-01.**

Cascading sync functions correctly **only if** server implements:

### Invariant A — Upward Propagation (on any write)

При любом write к документу (admin или Cloud Function) сервер **обязан** поднять:
- `lastModifiedAt + version` изменённого документа
- `lastModifiedAt + version + contentsVersion` всех **предков** вверх по иерархии до catalog

```
question write → bump lesson(lastMod+version+cv) → theme(lastMod+version+cv)
               → section(lastMod+version+cv) → quest(lastMod+version+cv)
               → catalog(lastMod+version+cv)
```

**Без Invariant A**: клиент видит только top-level catalog изменения, nested quests/sections/etc. не подтягиваются через cursor.

### Invariant B — Downward Cascade (on visibility/archived change)

При изменении `parent.visibleOn` или `parent.archived` сервер **обязан** поднять `lastModifiedAt` **всех потомков** вниз по иерархии.

```
quest.visibleOn changed → bump sections/*.lastModifiedAt → themes/*.lastModifiedAt
                        → lessons/*.lastModifiedAt → questions/*.lastModifiedAt
```

**Без Invariant B**: при первом доступе к quest клиент видит quest, но не его содержимое (sections/lessons/questions — старые lastModifiedAt, не pulled через cursor).

### Implementation options (backend team)

1. **Cloud Function trigger** на Firestore write (рекомендуется для production)
2. **Admin-tool**: при каждом write через admin UI — вручную обновлять предков/потомков
3. **Pre-population script** для seed data (acceptable для pre-production)

**Trade-off**: Invariant B trigger при изменении quest.visibleOn → N серверных writes (N = число потомков). Для большого quest (1000 вопросов) — заметная нагрузка. Acceptable MVP; future optimization через batch writes или per-subtree version.

### Build-Level Hard Dependencies (web-researcher findings — 2026-04-22)

#### BD-1: `kspJvm` required for KMP Room module

**Scope**: `shared/core/persistence/build.gradle.kts` — scaffold file, owner `backend-dev`.

`shared/core/persistence/build.gradle.kts` currently has only `kspAndroid`. If any `shared/*` module declares `jvm()` target (all Walking Skeleton modules do — ADR-0002), Room KSP **must** also be configured for JVM:

```kotlin
// shared/core/persistence/build.gradle.kts — REQUIRED addition by backend-dev:
add("kspJvm", libs.room.compiler)   // alongside existing kspAndroid
```

Without this, build fails with `Configuration with name 'kspJvm' not found` when jvm() target is present.

**Action**: backend-dev adds `kspJvm` in Phase-01 build setup. This is a blocker before any KMP Room entity compilation.

#### BD-2: Coil 3.4.0 requires Kotlin ≥ 2.1.0

**Scope**: `libs.versions.toml` — scaffold file, owner `backend-dev`. Decision #43 (Coil bump).

Coil 3.4.0 (Kotlin Native dependency) requires Kotlin **≥ 2.1.0** at minimum; Kotlin 2.2.0 recommended. Before bumping `coil` version in `libs.versions.toml`:

1. Check current `kotlin` version in `libs.versions.toml`
2. If `kotlin < 2.1.0` — bump Kotlin first (separate commit, validate build)
3. Then bump Coil

**Risk**: Kotlin bump may require AGP compatibility check. Do not batch Kotlin + Coil + AGP bumps in one commit.

---

## 13. Koin Module Structure (canonical registration)

```kotlin
// Кто добавляет в apps/android-next/AppApplication.kt:
startKoin {
    modules(
        // existing
        persistenceModule,         // ← bump version, add entities
        syncModule,                // ← add SyncStateRepository binding
        appShellDataModule(authUidFlow),  // ← add AuthRepository binding
        catalogDataModule,         // ← update refreshFromRemote
        firebaseCatalogModule,     // ← update fetchChangedSince
        // new
        questDomainModule,
        questDataModule,
        questPresentationModule,
        sectionDomainModule, sectionDataModule,
        themeDomainModule,   themeDataModule,
        lessonDomainModule,  lessonDataModule,
        questionDomainModule, questionDataModule,
        firebaseQuestModule,
        firebaseSectionModule, firebaseThemeModule,
        firebaseLessonModule, firebaseQuestionModule,
    )
}

// syncModule.kt — обновить:
val syncModule = module {
    single<SyncStateRepository> { InMemorySyncStateRepository() }  // NEW binding
    single<CascadingSyncOrchestrator> { CascadingSyncOrchestrator(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<List<Syncable>> {
        listOf(
            get<UserStatsRepository>() as Syncable,
            get<CascadingSyncOrchestrator>(),  // replaces individual catalog Syncable
        )
    }
}

// questPresentationModule (android/feature/quest/presentation/di/QuestPresentationModule.kt)
val questPresentationModule = module {
    factory<MyQuestsComponent> { (ctx: ComponentContext) ->
        DefaultMyQuestsComponent(ctx, get(), get(), get(), get())
    }
    factory<HomeQuestsComponent> { (ctx: ComponentContext) ->
        DefaultHomeQuestsComponent(ctx, get())
    }
}

// appShellDataModule — обновить (shared/feature/app-shell/data/di/AppShellDataModule.kt)
fun appShellDataModule(currentUidFlow: () -> Flow<String?>): Module = module {
    single<UserStatsRepository> { UserStatsRepositoryImpl(currentUidFlow) }  // existing
    single<AuthRepository> { AuthRepositoryImpl(currentUidFlow) }             // NEW
}
```

## 16. Security Rules — Nested Content Visibility (MVP Trade-off)

### Decision: Option C — any-auth read for nested content

**Status**: ACCEPTED MVP (cross-fix-pass #2, 2026-04-24)

Firestore rules for `sections`, `themes`, `lessons`, `questions` allow `read` for any
authenticated user (`request.auth != null`). Quest-level visibility (`authorUid` /
`visibleOn`) is **not** enforced at the Firestore rules level for nested content.

**Rationale**: enforcing parent visibility server-side requires either:
- **Option A**: 1-2 `get()` calls per document read (expensive; each `get()` counts as a
  read operation and adds latency cascading through subtree fetches)
- **Option B**: denormalization of `questAuthorUid` + `questIsPublic` into every nested
  document + server-side fan-out on quest write (significant Cloud Function complexity)

For MVP, accepted trade-off: **access control is enforced client-side** by
`CascadingSyncOrchestrator`, which only fetches nested content for quests the current user
has access to (via quest-level `authorUid` / `visibleOn` Firestore rules, which ARE enforced
server-side). A user who knows a `sectionId` directly could read it without owning the parent
quest — but discovery of valid IDs requires a prior quest read which is already gated.

**Post-MVP path**: Option B — denormalize `questIsPublic: Boolean` into every nested document
and update rules to `resource.data.questIsPublic == true || resource.data.questAuthorUid == request.auth.uid`.
Requires Cloud Function trigger on `quest.visibleOn` / `quest.archived` change (same trigger
as Invariant B downward cascade — natural extension).

**Referenced in**: `firestore.rules:32-40` (comment), `03-decisions.md §Nested Content Visibility MVP`.
