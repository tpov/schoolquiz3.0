---
phase: 01
role: backend-dev
---

# Phase 01 — Backend Tasks

## Pattern Invariants

- `core:foundation`, `core:sync`, `core:persistence` — только KMP commonMain. Нет Android imports в этих модулях (кроме Room annotations которые KMP 2.7 поддерживает в commonMain).
- `core:sync` — только `Syncable` interface, нет реализаций.
- `ActivateDevModeUseCase` после rewrite — НЕ импортирует `app-shell:domain`. Только lambda params.
- Scaffold files (`settings.gradle.kts`, `libs.versions.toml`, `build.gradle.kts`) — только backend-dev.
- `core:foundation` не зависит ни от каких других project modules.
- Walking Skeleton KEPT files (`TapProgress.kt`, `TapResult.kt`, `RegisterTap.kt`) — не трогать business logic, только param rename.

---

## 1. settings.gradle.kts — добавить catalog:data модуль

**Файл:** `settings.gradle.kts`
- **Тип:** config file
- **Сигнатура:** добавить строку `include(":shared:core:catalog:data")` в блок `// shared-core` после строки `:shared:core:catalog:domain`
- **Вход:** текущий `settings.gradle.kts` (уже содержит `:shared:core:catalog:domain`)
- **Поведение / Выход:**
  - Gradle теперь видит модуль `:shared:core:catalog:data` как project module
  - Другие модули могут добавить `implementation(project(":shared:core:catalog:data"))` в свои build.gradle.kts
- **Edge cases:**
  - Директория `shared/core/catalog/data/` должна существовать с `build.gradle.kts` — создать одновременно
- **Depends on:** ничего
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Grounding Problem 4 — модуль не включён в settings. Без этого шага catalog data layer не компилируется.

---

## 2. libs.versions.toml — добавить coil3

**Файл:** `gradle/libs.versions.toml`
- **Тип:** config file
- **Сигнатура:** добавить в `[versions]` блок: `coil3 = "3.4.0"`; в `[libraries]` блок: `coil3-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil3" }`, `coil3-core = { group = "io.coil-kt.coil3", name = "coil", version.ref = "coil3" }`
- **Вход:** текущий `libs.versions.toml` (уже содержит `room` и `work` версии)
- **Поведение / Выход:**
  - `libs.coil3.compose` и `libs.coil3.core` доступны в build.gradle.kts
  - Позволяет Phase 07 (designsystem) подключить Coil в `android:core:designsystem`
- **Edge cases:**
  - Проверить нет ли конфликта с существующими coil2 aliases если таковые есть
  - `ksp` plugin должен быть задекларирован — проверить наличие в `[plugins]` блоке, добавить если отсутствует
- **Depends on:** ничего
- **Canonical reference:** ADR-HLA-06 (`03-decisions.md`)
- **Rationale:** ADR-HLA-06 (Coil 3.4.0) + кsp plugin нужен для Room code generation.

---

## 3. CREATE QualificationLevel — MOVE в core:foundation

**Файл:** `shared/core/foundation/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/foundation/QualificationLevel.kt`
- **Тип:** `enum class`
- **Сигнатура:** `enum class QualificationLevel(val points: Int) { LEVEL_1(100), LEVEL_2(200), LEVEL_3(300) }` + extension `fun QualificationLevel.isReachedBy(points: Int): Boolean`
- **Вход:** параметр `points: Int` в конструкторе каждого члена enum
- **Поведение / Выход:**
  - `LEVEL_1.points == 100`, `LEVEL_2.points == 200`, `LEVEL_3.points == 300`
  - `isReachedBy(x)` возвращает `x >= this.points`
  - `entries.size == 3` (Kotlin 1.9+ API)
- **Edge cases:**
  - Отрицательные значения `isReachedBy(-1)` → `false`
  - Граничные: `isReachedBy(100)` → `true`, `isReachedBy(99)` → `false`
- **Depends on:** ничего (pure Kotlin enum)
- **Canonical reference:** `06-api-contract.md §1.1`
- **Rationale:** ADR-HLA-01 — перенос из `qualification:domain` в `core:foundation` устраняет cross-feature import BLOCKER.

Action: MOVE файл из `shared/feature/qualification/domain/.../model/QualificationLevel.kt` в новое местоположение. Обновить package declaration: `package com.tpov.schoolquiz.shared.core.foundation`.

---

## 4. UPDATE core:foundation/build.gradle.kts — KMP setup

**Файл:** `shared/core/foundation/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** Убедиться что модуль настроен как KMP (kotlinMultiplatform plugin), targets: `androidTarget()` + `jvm()`. Нет внешних dependencies — только pure Kotlin.
- **Вход:** текущее содержимое (возможно пустой или минимальный)
- **Поведение / Выход:**
  - `./gradlew :shared:core:foundation:jvmTest` компилируется и запускает тесты
- **Edge cases:** если уже настроен — проверить и не дублировать
- **Depends on:** ничего
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** QualificationLevel.kt должна быть KMP-compatible для использования в commonMain других модулей.

---

## 5. DELETE — Walking Skeleton overlay files

Удалить следующие файлы из `shared/feature/qualification/domain/`:

- `src/commonMain/.../dev_mode/model/LocalDeveloperOverride.kt`
- `src/commonMain/.../dev_mode/model/DeveloperLevelStats.kt`
- `src/commonMain/.../dev_mode/logic/EffectiveDeveloperLevel.kt`
- `src/commonMain/.../dev_mode/repository/LocalDeveloperOverrideRepository.kt`
- `src/commonTest/.../dev_mode/fake/FakeLocalDeveloperOverrideRepository.kt`
- Тест файлы к этим классам (если существуют: `LocalDeveloperOverrideTest.kt`, `EffectiveDeveloperLevelTest.kt`)

**Depends on:** ничего (только удаление)
**Canonical reference:** `06-api-contract.md §2` (DELETED files list), ADR-HLA-02
**Rationale:** User Decision #2 — revert codex fix #2. Overlay модель заменяется прямой записью в Room.

---

## 6. UPDATE RegisterTap — param rename

**Файл:** `shared/feature/qualification/domain/src/commonMain/.../dev_mode/logic/RegisterTap.kt`
- **Тип:** function (pure)
- **Сигнатура:** `fun registerTap(progress: TapProgress, nowMillis: Long, currentDeveloperLevel: Int, required: QualificationLevel = QualificationLevel.LEVEL_1, resetThresholdMillis: Long = 500L, targetCount: Int = 10): TapResult`
- **Вход:** параметр `currentDeveloperLevel: Int` (переименован с `currentEffectiveDeveloperLevel`)
- **Поведение / Выход:** бизнес-логика не меняется — только rename параметра
- **Edge cases:** все DM-01..DM-10 scenarios должны остаться зелёными
- **Depends on:** `QualificationLevel` (теперь из `core:foundation`), `TapProgress`, `TapResult`
- **Canonical reference:** `06-api-contract.md §2.1`
- **Rationale:** Согласование терминологии — "effective" убрано т.к. нет overlay, просто current developer level из UserStats.

---

## 7. UPDATE qualification:domain/build.gradle.kts — добавить deps

**Файл:** `shared/feature/qualification/domain/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** добавить в `commonMain.dependencies`: `implementation(libs.kotlinx.coroutines.core)` + `implementation(project(":shared:core:foundation"))`
- **Вход:** текущий файл (подтверждено в grounding: нет deps вообще)
- **Поведение / Выход:**
  - `ActivateDevModeUseCase` может использовать `suspend` keyword
  - `RegisterTap.kt` может импортировать `QualificationLevel` из `core:foundation`
- **Edge cases:** не добавлять дублирующих зависимостей
- **Depends on:** `core:foundation` должен существовать (шаг 3)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Grounding Problem 1 fix + coroutines нужны для suspend onDevModeActivated lambda.

---

## 8. REWRITE ActivateDevModeUseCase — lambda injection

**Файл:** `shared/feature/qualification/domain/src/commonMain/.../dev_mode/use_case/ActivateDevModeUseCase.kt`
- **Тип:** `class`
- **Сигнатура:** `class ActivateDevModeUseCase(private val readCurrentDeveloperLevel: () -> Int, private val onDevModeActivated: suspend () -> Unit)`
- **Вход:**
  - `readCurrentDeveloperLevel: () -> Int` — sync lambda, читает текущий `developer` из state
  - `onDevModeActivated: suspend () -> Unit` — suspend side-effect при activation
- **Поведение / Выход:**
  - `suspend operator fun invoke(progress: TapProgress, nowMillis: Long): TapResult`
  - Вызывает `registerTap(progress, nowMillis, readCurrentDeveloperLevel())`
  - Если `result is TapResult.Activated` → вызывает `onDevModeActivated()`
  - Возвращает `TapResult` (тот же что из `registerTap`)
- **Edge cases:**
  - `AlreadyDev` — `onDevModeActivated` НЕ вызывается
  - `NoChange/Reset` — `onDevModeActivated` НЕ вызывается
  - Suspend lambda не блокирует FSM (вызывается `suspend` в контексте)
- **Depends on:** `RegisterTap`, `TapProgress`, `TapResult`, `QualificationLevel` (transitively)
- **Canonical reference:** `06-api-contract.md §2.2`
- **Rationale:** ADR-L3-01 — lambda injection избегает `qualification:domain → app-shell:domain` cross-feature import BLOCKER.

---

## 9. CREATE Syncable interface

**Файл:** `shared/core/sync/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/sync/Syncable.kt`
- **Тип:** `interface`
- **Сигнатура:** `interface Syncable { suspend fun sync(): Result<Unit> }`
- **Вход:** N/A
- **Поведение / Выход:**
  - Pure KMP interface, нет Android imports
  - `sync()` возвращает `Result.success(Unit)` при успехе, `Result.failure(e)` при ошибке
- **Edge cases:** N/A (только interface)
- **Depends on:** `kotlinx.coroutines.core` (для `suspend`)
- **Canonical reference:** `06-api-contract.md §14`
- **Rationale:** ADR-HLA-04 — `Syncable` позволяет `SyncWorker` работать с любыми data sources без feature coupling.

---

## 10. UPDATE core:sync/build.gradle.kts

**Файл:** `shared/core/sync/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** KMP plugin + `commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }`
- **Вход:** текущий файл (пустой stub)
- **Поведение / Выход:** `Syncable.kt` компилируется
- **Depends on:** ничего
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Syncable использует `suspend` — нужен coroutines.

---

## 11. CREATE AppDatabase

**Файл:** `shared/core/persistence/src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt`
- **Тип:** `abstract class` (Room)
- **Сигнатура:** `@Database(entities = [UserStatsEntity::class, CatalogEntity::class], version = 1, exportSchema = true) abstract class AppDatabase : RoomDatabase()`
- **Вход:** Room entities как параметры аннотации
- **Поведение / Выход:**
  - `abstract fun userStatsDao(): UserStatsDao`
  - `abstract fun catalogDao(): CatalogDao`
  - Room ksp генерирует имплементацию
- **Edge cases:**
  - `exportSchema = true` — экспортировать JSON схему для MigrationTestHelper
  - `version = 1` — первая версия, нет prior schema, нет migrations
- **Depends on:** `UserStatsEntity`, `CatalogEntity`, `UserStatsDao`, `CatalogDao`
- **Canonical reference:** `06-api-contract.md §12` (persistence module в Koin wiring)
- **Rationale:** ADR-HLA-03 — central AppDatabase, один instance.

---

## 12. CREATE UserStatsEntity

**Файл:** `shared/core/persistence/src/commonMain/kotlin/.../UserStatsEntity.kt`
- **Тип:** `data class` (Room entity)
- **Сигнатура:** `@Entity(tableName = "user_stats") data class UserStatsEntity(@PrimaryKey val uid: String, val nickname: String, val avatarUrl: String?, val hasPremium: Boolean, val streakDays: Int, val stars: Long, val nolics: Long, val standardHearts: Int, val goldHearts: Int, val gold: Long, val currentSkill: Int, val testerLevel: Int, val moderatorLevel: Int, val sponsorLevel: Int, val translatorLevel: Int, val adminLevel: Int, val developerLevel: Int)`
- **Вход:** все 17 полей (uid + 10 stat fields + 6 qualification levels)
- **Поведение / Выход:**
  - Flat structure — qualification fields flat (не nested)
  - `developerLevel` — единственное поле с client-side write path
  - `uid` = Firebase Auth currentUser?.uid
- **Edge cases:**
  - Все Int поля квалификаций >= 0 (доменный инвариант, в entity нет ограничений — validated in domain)
  - `avatarUrl` может быть null
- **Depends on:** Room ksp
- **Canonical reference:** `06-api-contract.md §5`, `08-storage-model.md §2`
- **Rationale:** 16 полей как в `RawUserStats` (verified in grounding). Flat structure соответствует `RawUserStats.kt`.

---

## 13. CREATE UserStatsDao

**Файл:** `shared/core/persistence/src/commonMain/kotlin/.../UserStatsDao.kt`
- **Тип:** `interface` (Room DAO)
- **Сигнатура:** `@Dao interface UserStatsDao`
- **Вход:** Room queries + entity parameters
- **Поведение / Выход:**
  - `fun observeByUid(uid: String): Flow<UserStatsEntity?>` — reactive query
  - `suspend fun findByUid(uid: String): UserStatsEntity?` — one-shot
  - `suspend fun upsert(entity: UserStatsEntity)` — INSERT OR REPLACE
  - `suspend fun updateDeveloperLevel(uid: String, value: Int)` — targeted UPDATE, только `developerLevel` поле
- **Edge cases:**
  - `observeByUid` на unknown uid → эмитит `null`
  - `updateDeveloperLevel` на несуществующем uid — нет строки → noop (Room UPDATE WHERE uid=? → 0 rows affected)
- **Depends on:** `UserStatsEntity`, kotlinx.coroutines.core
- **Canonical reference:** `06-api-contract.md §5`, `08-storage-model.md §3`
- **Rationale:** `updateDeveloperLevel` как отдельный метод реализует ADR-HLA-02 (targeted UPDATE, не full REPLACE для dev mode).

---

## 14. CREATE CatalogEntity

**Файл:** `shared/core/persistence/src/commonMain/kotlin/.../CatalogEntity.kt`
- **Тип:** `data class` (Room entity)
- **Сигнатура:** `@Entity(tableName = "catalogs") data class CatalogEntity(@PrimaryKey val id: String, val name: String, val picturePath: String?, val pictureUrl: String?)`
- **Вход:** 4 поля
- **Поведение / Выход:**
  - `picturePath` — relative Firebase Storage path (nullable)
  - `pictureUrl` — pre-resolved HTTPS URL, кэшируется при `refreshFromRemote()` (ADR-HLA-07)
  - Sorted by `id ASC` в DAO query (не в entity, но в CatalogDao.observeAll())
- **Edge cases:**
  - `picturePath=null` → `pictureUrl=null` (нет URL resolver вызова)
  - `name` non-blank (enforced at domain level before insert, entity не валидирует)
- **Depends on:** Room ksp
- **Canonical reference:** `06-api-contract.md §9`, `08-storage-model.md §4`
- **Rationale:** ADR-HLA-07 — `pictureUrl` в entity = кэшированный HTTPS URL для UI.

---

## 15. CREATE CatalogDao

**Файл:** `shared/core/persistence/src/commonMain/kotlin/.../CatalogDao.kt`
- **Тип:** `interface` (Room DAO)
- **Сигнатура:** `@Dao interface CatalogDao`
- **Вход:** Room queries + entity parameters
- **Поведение / Выход:**
  - `fun observeAll(): Flow<List<CatalogEntity>>` — с ORDER BY id ASC
  - `suspend fun findById(id: String): CatalogEntity?`
  - `suspend fun insertAll(entities: List<CatalogEntity>)`
  - `suspend fun deleteAll()`
  - `@Transaction suspend fun replaceAll(entities: List<CatalogEntity>)` — вызывает `deleteAll()` + `insertAll()` атомарно
- **Edge cases:**
  - `replaceAll` — транзакция, Flow не должен эмитить пустой список между delete и insert
  - `observeAll` на пустой таблице — эмитит `emptyList()`
- **Depends on:** `CatalogEntity`, kotlinx.coroutines.core
- **Canonical reference:** `06-api-contract.md §9`, `08-storage-model.md §5`
- **Rationale:** ADR-L3-04 — `replaceAll` как `@Transaction` для предотвращения UI flash.

---

## 16. UPDATE core:persistence/build.gradle.kts

**Файл:** `shared/core/persistence/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** KMP plugin + ksp plugin + Room 2.7 deps + coroutines; targets: `androidTarget()` + `jvm()`
- **Вход:** текущий файл (пустой stub)
- **Поведение / Выход:**
  - Room annotations (`@Entity`, `@Dao`, `@Database`) доступны в commonMain
  - ksp обрабатывает Room code generation
  - Модуль компилируется
- **Edge cases:**
  - Room KMP 2.7+ требует `ksp` plugin применённый к KMP targets
  - Нужен `room.gradle` plugin или `androidx.room` ksp coordinates
- **Depends on:** `ksp` plugin в libs.versions.toml (шаг 2)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Room infrastructure — основная задача Phase 01.

---

## 17. CREATE shared/core/catalog/data module structure + build.gradle.kts

**Файл:** `shared/core/catalog/data/build.gradle.kts`
- **Тип:** build config (new module)
- **Сигнатура:** KMP plugin + coroutines; dependencies на `:shared:core:catalog:domain` + `:shared:core:persistence` + `:shared:core:sync`
- **Вход:** N/A (новый модуль)
- **Поведение / Выход:**
  - Модуль компилируется
  - Может импортировать `CatalogRepository` из `catalog:domain`, `CatalogDao` из `persistence`, `Syncable` из `sync`
  - `platform:firebase` dependency добавляется в Phase 05 когда `FirebaseCatalogRemoteDataSource` создаётся
- **Edge cases:**
  - Директория `shared/core/catalog/data/src/commonMain/kotlin/...` должна существовать
- **Depends on:** шаг 1 (settings.gradle.kts), шаги 9, 11-15 (sync + persistence)
- **Canonical reference:** internal (no api-contract entry)
- **Rationale:** Grounding Problem 4 — модуль не существует. Создаётся в Phase 01 как пустой (с build.gradle.kts), реализуется в Phase 05.

---

## 18. UPDATE app-shell:domain/build.gradle.kts — добавить core:foundation dep

**Файл:** `shared/feature/app-shell/domain/build.gradle.kts`
- **Тип:** build config
- **Сигнатура:** добавить `implementation(project(":shared:core:foundation"))` в `commonMain.dependencies`
- **Вход:** текущий файл (содержит только kotlinx.coroutines.core + test deps)
- **Поведение / Выход:**
  - `Visibility.kt` и `DrawerSection.kt` могут импортировать `QualificationLevel` из `core:foundation`
  - Не добавляет зависимости на `qualification:domain` — избегает cross-feature BLOCKER
- **Edge cases:** проверить нет ли уже этой зависимости
- **Depends on:** шаг 3 (core:foundation exists)
- **Canonical reference:** ADR-HLA-01 (`03-decisions.md`)
- **Rationale:** Problem 1 fix — позволяет заменить magic `100` на `QualificationLevel.LEVEL_1.points` в Phase 03.
