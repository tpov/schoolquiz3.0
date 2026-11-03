---
phase: 05
name: Catalog Data Stack
complexity: complex
---

# Phase 05: Catalog Data Stack

## Goal

Реализовать полный catalog data stack: `CatalogRepositoryImpl` + `CatalogLocalDataSourceImpl` + `FirebaseCatalogRemoteDataSource` + `CatalogMapper`, создать `CatalogDto` и маппер `DocumentSnapshot → CatalogDto → CatalogEntity`. Обновить Koin `catalogDataModule`.

## Scope

- CREATE `CatalogLocalDataSourceImpl` в `core:catalog:data`
- CREATE `CatalogRemoteDataSource` interface + `FirebaseCatalogRemoteDataSource` impl в `platform:firebase`
- CREATE `CatalogRepositoryImpl` с `CatalogLocalDataSource` + `CatalogRemoteDataSource` + `storageUrlResolver` lambda
- CREATE `CatalogMapper`: `CatalogEntity.toDomain()` + `Catalog.toEntity()`
- CREATE `CatalogDto` (pure Kotlin, `core:catalog:data/commonMain`) + `CatalogDto.toEntity()` mapper (pure Kotlin, `core:catalog:data/commonMain`)
- CREATE `DocumentSnapshot.toCatalogDto()` Firebase adapter (`platform:firebase`)
- CREATE `catalogDataModule` + `catalogDomainModule` Koin modules
- UPDATE `platform:firebase/build.gradle.kts` — добавить `core:catalog:data` dep (для `CatalogRemoteDataSource` interface + `CatalogDto`); опционально `core:catalog:domain` dep если нужны domain types в `firebaseCatalogModule` binding для `CatalogRepository` composition
- UPDATE `AppApplication.kt` — добавить новые Koin modules в `startKoin`
- UPDATE `platform:android-services/build.gradle.kts` — добавить `core:sync` dep

## Layer

data (catalog persistence + Firestore)

## Role Inputs

- `backend.md`
- `frontend.md` — none (нет UI в этой фазе)
- `tests.md`

## Dependencies

phases_ref: [phase-01, phase-03]
- Phase 01: `CatalogDao`, `CatalogEntity` в `core:persistence`; `:shared:core:catalog:data` module создан
- Phase 03: `CatalogRepository` interface (Walking Skeleton, уже существует, не меняется)

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|------------|--------------|-----------------|--------------|------------|
| Problem 4: нет CatalogRepositoryImpl | backend-dev | `shared/core/catalog/data/` (пустой stub) | ADR-L3-04 (replaceAll @Transaction) | CREATE реализацию в catalog:data | CF-11..18 green |
| Problem 4: нет FirebaseCatalogRemoteDataSource | firebase-dev / backend-dev | `platform/firebase/` | ADR-HLA-07 (URL pre-resolve) | CREATE FirebaseCatalogRemoteDataSource | CF-22, CF-23 green |
| Problem 4: нет catalog Koin modules | backend-dev | `AppApplication.kt` | ADR-HLA-03 (persistence module) | CREATE catalogDataModule + register | app стартует без DI error |
| Problem 4: URL resolution ADR-HLA-07 | backend-dev | `CatalogRepositoryImpl.refreshFromRemote()` | storageUrlResolver lambda | resolve при `refreshFromRemote`, сохранить в CatalogEntity.pictureUrl | CF-14, CF-15 green |

## New Files

- `shared/core/catalog/data/src/commonMain/.../CatalogLocalDataSource.kt` (interface + impl)
- `shared/core/catalog/data/src/commonMain/.../CatalogRemoteDataSource.kt` (interface — pure KMP, no Firebase)
- `shared/core/catalog/data/src/commonMain/.../CatalogDto.kt` (pure Kotlin DTO — per ADR clean-architecture split; ⇄ `08-storage-model.md §7.3`)
- `shared/core/catalog/data/src/commonMain/.../mapper/CatalogDtoMapper.kt` (pure `CatalogDto.toEntity()` — no Firestore types)
- `shared/core/catalog/data/src/commonMain/.../CatalogRepositoryImpl.kt`
- `shared/core/catalog/data/src/commonMain/.../mapper/CatalogMapper.kt` (CatalogEntity ↔ Catalog domain)
- `shared/core/catalog/data/src/commonMain/.../di/CatalogDataModule.kt` (pure KMP bindings — `CatalogLocalDataSource`, `CatalogRepository`; does NOT bind Firebase types)
- `shared/core/catalog/domain/src/commonMain/.../di/CatalogDomainModule.kt` (domain owns use case Koin bindings)
- `platform/firebase/src/main/.../catalog/FirebaseCatalogRemoteDataSource.kt`
- `platform/firebase/src/main/.../catalog/FirestoreCatalogDtoMapper.kt` (Firebase adapter — `DocumentSnapshot.toCatalogDto()` extension only; imports `CatalogDto` from `core:catalog:data`)
- `platform/firebase/src/main/.../di/FirebaseCatalogModule.kt` (Koin — Firebase-specific bindings: `CatalogRemoteDataSource` → `FirebaseCatalogRemoteDataSource`, `storageUrlResolver` lambda named qualifier)

## Modified Files

- `shared/core/catalog/data/build.gradle.kts` — добавить `implementation(project(":shared:core:persistence"))` + `implementation(project(":shared:core:sync"))` + `implementation(project(":shared:core:catalog:domain"))`. НЕ добавлять `platform:firebase` dep — это нарушение clean architecture (KMP module НЕ зависит от Android-only platform module)
- `platform/firebase/build.gradle.kts` — добавить `implementation(project(":shared:core:catalog:data"))` (для `CatalogRemoteDataSource` interface) + `implementation(project(":shared:core:catalog:domain"))` (для domain types в mapper)
- `apps/android-next/src/main/.../AppApplication.kt` — добавить `catalogDataModule`, `catalogDomainModule`, `firebaseCatalogModule` в `startKoin`

## Deleted Files

none

## Acceptance Criteria

- [ ] `CatalogRepositoryImpl.observeAll()` читает из Room (не Firebase), сортирует по id ASC
- [ ] `CatalogRepositoryImpl.refreshFromRemote()` вызывает Firebase.fetchAll() → mapper → Room.replaceAll()
- [ ] При `picturePath=null` — `storageUrlResolver` не вызывается, `pictureUrl=null` (CF-14)
- [ ] При `picturePath` с prefix `catalog-pictures/` — `storageUrlResolver` вызывается с path, результат в entity.pictureUrl (CF-15). **User decision 2026-04-20**: defence-in-depth — только paths в `catalog-pictures/` folder triggers resolver, другие prefixes игнорируются для защиты от Firestore write injection (admin-only/private paths)
- [ ] При `picturePath` без prefix `catalog-pictures/` — `storageUrlResolver` НЕ вызывается, `pictureUrl=null` (CF-15b)
- [ ] `CatalogDao.replaceAll()` атомарный (@Transaction) — Room Flow не эмитит пустой список (CF-07)
- [ ] `DocumentSnapshot.toCatalogDto()` возвращает null при blank name (CF-22)
- [ ] CF-11..CF-23 зелёные
- [ ] `./gradlew :shared:core:catalog:data:jvmTest` GREEN

## State Matrix Coverage

Flow 4 (First-Launch Catalog Pull) из `02-behavior.md`:
- Empty Room → refreshFromRemote() → replaceAll() → observeAll() emits list
- CF-11..CF-18 repository scenarios покрывают весь flow

## Pattern Invariants

- `CatalogRepositoryImpl` НЕ зависит от Firebase SDK напрямую — зависит от `CatalogRemoteDataSource` interface (dependency inversion)
- `storageUrlResolver: suspend (String) -> String` — non-null лямбда (бросает exception при fail; caller `CatalogRepositoryImpl.refreshFromRemote` wraps в `runCatching { ... }.getOrNull()` для graceful degradation), инжектируется из Koin `named("storageUrlResolver")` (платформенная зависимость не утекает в KMP модуль)
- `CatalogRepositoryImpl.observeAll()` сортирует по `id.value ASC` (ADR client-sort invariant)
- `CatalogLocalDataSource` и `CatalogRemoteDataSource` — interfaces в `core:catalog:data` (не в `platform`)
- `FirebaseCatalogRemoteDataSource` реализует `CatalogRemoteDataSource` interface
- Новые stateful fields в `CatalogRepositoryImpl`: нет MutableState — stateless impl

### Options Considered

| Критерий | Option A (recommended): interface в catalog:data, impl в firebase | Option B: обе в firebase | Option C: прямая зависимость catalog:data → firebase |
|----------|------------------------------------------------------------------|--------------------------|------------------------------------------------------|
| Coupling | data layer не зависит от Firebase SDK | data и firebase coupled | нарушает правило data → platform запрещён |
| Testability | FakeCatalogRemoteDataSource легко заменяет | сложнее mock Firebase | нет dependency injection |
| KMP compatible | catalog:data = KMP-compatible | firebase = Android-only | нет |
| Modular | firebase impl можно заменить другим provider | нет | нет |

**Recommended: Option A**

**Rationale:** dependency inversion — catalog:data определяет контракт, platform:firebase реализует. Согласуется с существующим паттерном `UserStatsDataSource` → `FirebaseUserStatsDataSource`.

**Rejected Option B:** coupling data layer с Firebase SDK.

**Rejected Option C:** нарушает clean-architecture (data → platform — нет).

## Tests Required

Параллельно:

- CF-11: `observeAll()` — `FakeCatalogRemoteDataSource.fetchAllCalls == 0` (читает только из Room)
- CF-12: `refreshFromRemote()` — `FakeCatalogDao.replaceAllCalls == 1` с правильными entities
- CF-13: Firebase error → `Result.failure`
- CF-14: `picturePath=null` → `storageUrlResolver` NOT called, entity.pictureUrl=null
- CF-15: `picturePath="some/path"` → `storageUrlResolver` called with "some/path"
- CF-16: `sync()` == `refreshFromRemote()` (delegates)
- CF-17: `observeAll()` сортирует по id ASC — `[CatalogId("a"), CatalogId("b")]`
- CF-18: `getById(id)` — returns Catalog for existing id
- CF-19..CF-23: mapper round-trip scenarios

**Fakes:** `FakeCatalogDao`, `FakeCatalogRemoteDataSource`, `FakeCatalogUrlResolver` из `04-testing.md §4.2-4.3b`

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:core:catalog:data:jvmTest --no-configuration-cache` | GREEN — CF-11..23 |
| `./gradlew :apps:android-next:assembleDebug --no-configuration-cache` | GREEN — нет DI errors (все Koin modules зарегистрированы) |

## Handoff Notes

После Phase 05 разблокирована Phase 06 (Sync Infrastructure) — `CatalogRepositoryImpl.sync()` добавлен как Syncable, SyncWorker может получить `List<Syncable>`.
