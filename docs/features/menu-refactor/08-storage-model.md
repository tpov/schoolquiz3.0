---
date: 2026-04-20
feature: menu-refactor
author: architect-component
status: CANONICAL
---

# Storage Model: Menu Refactor

Canonical Room entity/DAO definitions, Firestore schema, и mapper functions. Ссылается на 06-api-contract.md для методов repository; этот файл = shape + constraints + migration strategy.

---

## 1. AppDatabase

**Location**: `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt`
**Version**: 1 (initial — no prior schema)
**Module**: `shared:core:persistence` (KMP, androidTarget)

```kotlin
@Database(
    entities = [UserStatsEntity::class, CatalogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userStatsDao(): UserStatsDao
    abstract fun catalogDao(): CatalogDao
}
```

**ksp plugin** required in `shared/core/persistence/build.gradle.kts` (backend-dev task).

---

## 2. UserStatsEntity

**Table**: `user_stats`
**Location**: `shared/core/persistence/src/commonMain/kotlin/.../UserStatsEntity.kt`

```kotlin
@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey
    val uid: String,
    val nickname: String,
    val avatarUrl: String?,
    val hasPremium: Boolean,
    val streakDays: Int,
    val stars: Long,
    val nolics: Long,
    val standardHearts: Int,
    val goldHearts: Int,
    val gold: Long,
    val currentSkill: Int,
    // Qualification fields (all Int >= 0):
    val testerLevel: Int,
    val moderatorLevel: Int,
    val sponsorLevel: Int,
    val translatorLevel: Int,
    val adminLevel: Int,
    val developerLevel: Int,   // ONLY field with client-side write path (dev mode)
)
```

**Constraints**:
- `developerLevel` is writable by `setLocalDeveloperLevel()`. All other fields exclusively written by `refreshProfile()` (UPSERT replaces entire row).
- `uid` maps to Firebase Auth `currentUser?.uid` — row is per-user.
- On logout/re-login: old row orphaned (uid changes). Future: add cleanup on auth change.

**✓ VERIFIED 2026-04-20** (prior verification by lead judge via direct Read):

**Non-qualification fields** (`nickname, avatarUrl, hasPremium, streakDays, stars, nolics, standardHearts, goldHearts, gold, currentSkill`) — 1:1 между `RawUserStats` (`shared/core/stats/src/commonMain/kotlin/.../RawUserStats.kt:3-20`), `UserStats` domain (`shared/feature/app-shell/domain/.../model/UserStats.kt:11-23`) и `UserStatsEntity` — идентичные names и types (Long для balance fields, Int для hearts/currentSkill).

**Qualification fields** — flat в `RawUserStats` и `UserStatsEntity` (`testerLevel, moderatorLevel, sponsorLevel, translatorLevel, adminLevel, developerLevel: Int`), **consolidated** в domain `UserStats.qualification: Qualification`. Mapper конвертирует flat entity → nested domain через `Qualification(tester = testerLevel, ...)` constructor (см. §7.1).

Spec `0-spec-dev-mode.md:44-59` ошибочно описывал consolidated contract (`premium, heartsBalance, starsBalance, nolicsBalance, goldBalance`) — это simplification wording, не отражает реальный code. Storage model следует реальному domain/RawUserStats, не spec wording.

**Field mapping table (UserStatsEntity ↔ UserStats domain — 1:1 names):**

| UserStatsEntity field | UserStats domain field | Mapper |
|-----------------------|------------------------|--------|
| `hasPremium: Boolean` | `hasPremium: Boolean` | match |
| `stars: Long` | `stars: Long` | match |
| `nolics: Long` | `nolics: Long` | match |
| `standardHearts: Int` | `standardHearts: Int` | match (0..5 invariant in domain) |
| `goldHearts: Int` | `goldHearts: Int` | match (0..1 invariant in domain) |
| `gold: Long` | `gold: Long` | match |
| `currentSkill: Int` | `currentSkill: Int` | match (>= 0 invariant in domain) |
| `testerLevel..developerLevel: Int` | `qualification.{role}: Int` | flat entity → nested domain via `Qualification(...)` constructor |

**Note for `RawUserStats.toEntity()` mapper**: `RawUserStats` имеет *flat* qualification fields (`testerLevel, moderatorLevel, ..., developerLevel`) — не nested `qualifications.tester`. Mapper копирует flat→flat.

---

## 3. UserStatsDao

**Location**: `shared/core/persistence/src/commonMain/kotlin/.../UserStatsDao.kt`

```kotlin
@Dao
interface UserStatsDao {
    @Query("SELECT * FROM user_stats WHERE uid = :uid")
    fun observeByUid(uid: String): Flow<UserStatsEntity?>

    @Query("SELECT * FROM user_stats WHERE uid = :uid LIMIT 1")
    suspend fun findByUid(uid: String): UserStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserStatsEntity)

    @Query("UPDATE user_stats SET developerLevel = :value WHERE uid = :uid")
    suspend fun updateDeveloperLevel(uid: String, value: Int)
}
```

---

## 4. CatalogEntity

**Table**: `catalogs`
**Location**: `shared/core/persistence/src/commonMain/kotlin/.../CatalogEntity.kt`

```kotlin
@Entity(tableName = "catalogs")
data class CatalogEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val picturePath: String?,   // relative Firebase Storage path or null (domain source of truth)
    val pictureUrl: String?,    // resolved HTTPS URL, cached on refreshFromRemote() — ⇄ ADR-HLA-07, ADR-L3-03
)
```

**Constraints**:
- `name` non-blank (enforced by domain invariant before insert).
- `picturePath` either `null` or non-blank relative path without `https://` / `gs://` prefix.
- `pictureUrl` either `null` (if `picturePath` null or resolver failed) or valid HTTPS URL.
- `picturePath` сохраняется для audit/debug; `pictureUrl` — для runtime UI (передаётся в `CatalogDisplayItem`).
- Sorted by `id` ASC in `observeAll()` DAO query — deterministic order for tests.

**⇄ ADR-HLA-07** — URL resolution in `CatalogRepositoryImpl.refreshFromRemote()`:
```kotlin
val pictureUrl = entity.picturePath?.let { path ->
    runCatching { storageUrlResolver(path) }.getOrNull()
}
// Сохраняется в CatalogEntity.pictureUrl при replaceAll()
```

---

## 5. CatalogDao

**Location**: `shared/core/persistence/src/commonMain/kotlin/.../CatalogDao.kt`

```kotlin
@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalogs ORDER BY id ASC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM catalogs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CatalogEntity>)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<CatalogEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
```

---

## 6. Room Migration Strategy

**Version 1** — initial version. No prior schema exists in new-stack; no migrations required.

Future migration policy: каждая новая фича, добавляющая таблицу или колонку, инкрементирует `version` в `AppDatabase` и добавляет `Migration(N, N+1)`.
Instrumented test: `AppDatabaseMigrationTest` (using `MigrationTestHelper`) — создаётся в phase-01.

---

## 7. Mapper Functions

### 7.1 UserStatsEntity ↔ UserStats

**Location**: `shared/feature/app-shell/data/src/commonMain/kotlin/.../mapper/UserStatsMapper.kt`

```kotlin
fun UserStatsEntity.toDomain(): UserStats = UserStats(
    nickname = nickname,
    avatarUrl = avatarUrl,
    hasPremium = hasPremium,
    streakDays = streakDays,
    stars = stars,
    nolics = nolics,
    standardHearts = standardHearts,
    goldHearts = goldHearts,
    gold = gold,
    currentSkill = currentSkill,
    qualification = Qualification(
        tester = testerLevel,
        moderator = moderatorLevel,
        sponsor = sponsorLevel,
        translator = translatorLevel,
        admin = adminLevel,
        developer = developerLevel,
    ),
)
```

```kotlin
fun RawUserStats.toEntity(uid: String): UserStatsEntity = UserStatsEntity(
    uid = uid,
    nickname = nickname,
    avatarUrl = avatarUrl,
    hasPremium = hasPremium,
    streakDays = streakDays,
    stars = stars,
    nolics = nolics,
    standardHearts = standardHearts,
    goldHearts = goldHearts,
    gold = gold,
    currentSkill = currentSkill,
    testerLevel = testerLevel,
    moderatorLevel = moderatorLevel,
    sponsorLevel = sponsorLevel,
    translatorLevel = translatorLevel,
    adminLevel = adminLevel,
    developerLevel = developerLevel,
)
```

**✓ VERIFIED 2026-04-20**: field names match `RawUserStats.kt` (`shared/core/stats/src/commonMain/kotlin/.../RawUserStats.kt:3-20`). `RawUserStats.nickname` is non-nullable `String` with default `""`, `hasPremium` is boolean, qualification fields are flat (`testerLevel`, `moderatorLevel`, etc.) — no nested `qualifications` object.

### 7.2 CatalogEntity ↔ Catalog

**Location**: `shared/core/catalog/data/src/commonMain/kotlin/.../mapper/CatalogMapper.kt`

```kotlin
fun CatalogEntity.toDomain(): Catalog = Catalog(
    id = CatalogId(id),
    name = name,
    picturePath = picturePath,
)

fun Catalog.toEntity(): CatalogEntity = CatalogEntity(
    id = id.value,
    name = name,
    picturePath = picturePath,
)
```

### 7.3 DocumentSnapshot → CatalogDto → CatalogEntity

**Locations** (split to satisfy clean-architecture `core/* → platform/*` invariant):

- `CatalogDto` (pure Kotlin data class) — `shared/core/catalog/data/src/commonMain/kotlin/.../CatalogDto.kt`
- `CatalogDto.toEntity()` mapper (pure Kotlin) — `shared/core/catalog/data/src/commonMain/kotlin/.../mapper/CatalogDtoMapper.kt`
- `DocumentSnapshot.toCatalogDto()` Firebase-specific mapper — `platform/firebase/src/main/kotlin/.../catalog/FirestoreCatalogDtoMapper.kt`

```kotlin
// shared/core/catalog/data/.../CatalogDto.kt — pure Kotlin DTO, no Firebase types
data class CatalogDto(
    val id: String,
    val name: String,
    val picturePath: String?,
)

// shared/core/catalog/data/.../mapper/CatalogDtoMapper.kt — pure Kotlin mapping
fun CatalogDto.toEntity(): CatalogEntity = CatalogEntity(
    id = id,
    name = name,
    picturePath = picturePath,
    pictureUrl = null,            // resolved separately in CatalogRepositoryImpl (⇄ ADR-HLA-07)
)

// platform/firebase/.../catalog/FirestoreCatalogDtoMapper.kt — Firebase adapter
fun DocumentSnapshot.toCatalogDto(): CatalogDto? {
    val name = getString("name") ?: return null
    if (name.isBlank()) return null
    return CatalogDto(
        id = id,                               // Firestore document ID
        name = name,
        picturePath = getString("picturePath"),
    )
}
```

**Clean architecture rationale**: `CatalogRemoteDataSource` interface (in `shared/core/catalog/data`) returns `List<CatalogDto>`. If `CatalogDto` lived in `platform/firebase`, `core/catalog/data` would depend on `platform/firebase` — forbidden. Splitting DTO (pure commonMain) from Firestore-specific extension (platform/firebase) keeps `core → platform` dependency direction clean.

---

## 8. Firestore Schema

### 8.1 Collection: catalogs

```
catalogs/{catalogId}

Fields:
  name:        String    — non-empty, display name
  picturePath: String?   — relative Storage path (e.g. "catalog-pictures/surveys.jpg")
  order:       Int       — optional, MVP unused (reserved for future ordering)
  createdAt:   Timestamp — optional (future conflict resolution per ADR-0004)
  updatedAt:   Timestamp — optional
```

**Sample seed documents** (admin → Firebase Console):
```
catalogs/surveys  → { name: "Опросы",  picturePath: "catalog-pictures/surveys.jpg" }
catalogs/courses  → { name: "Курсы",   picturePath: "catalog-pictures/courses.jpg" }
catalogs/games    → { name: "Игры",    picturePath: "catalog-pictures/games.jpg"   }
catalogs/school   → { name: "Школа",   picturePath: "catalog-pictures/school.jpg"  }
```

**Client observeAll() sort**: client sorts by `id.value` ASC after fetch → `[courses, games, school, surveys]`.

### 8.2 Security Rules Addition

```js
// In firestore.rules — add after existing 'users' block:
match /catalogs/{catalogId} {
  allow read: if true;                    // public — all clients can read
  allow write: if request.auth != null
    && get(/databases/$(database)/documents/users/$(request.auth.uid))
         .data.qualifications.admin >= 100;
}
```

---

## 9. Firebase Storage

**Bucket**: default project Firebase Storage bucket
**Path convention**: `catalog-pictures/{catalogId}.{ext}` (jpg, png, webp)
**ACL**: public read (Storage rules: `allow read: if true;`)

**URL Resolution** (infrastructure concern — NOT in domain model):
```
Catalog.picturePath = "catalog-pictures/surveys.jpg"
  → FirebaseStorage.getInstance().reference.child(picturePath).downloadUrl.await()
  → "https://firebasestorage.googleapis.com/...?token=..."
  → Coil AsyncImage loads HTTPS URL
```

Domain field `picturePath` = relative path (String?). `pictureUrl` (HTTPS URL) is NOT a domain field.
Resolver location: see ADR-HLA-07 + ADR-L3-03 in 03-decisions.md.

---

## 10. Sync Contract Summary

| Operation | Trigger | Effect on `user_stats` table |
|---|---|---|
| `refreshProfile()` | SyncWorker (periodic/manual) | Full UPSERT — overwrites all columns including `developerLevel` |
| `setLocalDeveloperLevel(value)` | 10-tap dev mode | UPDATE `developerLevel` only (UID-targeted) |
| Firestore snapshot listener | Auth + Firestore realtime | Full UPSERT via `refreshProfile()` analog |

**Dev mode deactivation flow**: `refreshProfile()` called → server returns `developer=0` →
`toEntity()` maps `developer=0` → `upsert()` replaces row → Room Flow emits → `observeStats()` emits →
`AppShellState.userStats.qualification.developer = 0` → `isVisible` returns to server-value behavior.
