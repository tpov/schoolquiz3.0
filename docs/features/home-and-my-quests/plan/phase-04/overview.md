---
phase: 04
name: AppShell + AuthRepository Integration + Navigation
complexity: simple
date: 2026-04-23
---

# Phase-04: AppShell + AuthRepository Integration + Navigation

## Goal

Завершить AppShell интеграцию — `AuthRepositoryImpl` в Koin + `Destination.OpenQuestCreate` routing + `Labels.kt` exhaustive when:
- `AppShellDataModule` уже содержит `AuthRepositoryImpl` binding (VERIFIED в коде)
- `LocalConfig.QuestCreateRoot` уже существует в Walking Skeleton (VERIFIED)
- `Destination.OpenQuestCreate` уже существует в Walking Skeleton (VERIFIED)
- Эта фаза проверяет что routing в `DefaultRootComponent` / `AppShellTransitions` + `Labels.kt` полностью реализован
- `AppShellTransitions` обработчик для `OpenQuestCreate` + guard (Decision #47)

## Scope

`shared/feature/app-shell/domain` (AppShellTransitions — verify/implement OpenQuestCreate handler), `android/feature/app-shell/presentation` (DefaultRootComponent.onDestination update, Labels.kt), `shared/feature/app-shell/data/di/AppShellDataModule.kt` (verify AuthRepository binding).

## Layer

presentation + domain (navigation)

## Role Inputs

- `backend.md` — backend-dev
- `tests.md` — test-dev

> **No `frontend.md`** — phase-04 не затрагивает UI/Compose screens. `DefaultRootComponent.kt` — navigation delegation via `AppShellTransitions`, не Composable UI. `Labels.kt` — pure Kotlin `when` expression returning String, no Compose/UI code. Обе задачи попадают под backend-dev domain (navigation logic + pure Kotlin expression). Если в ходе реализации DefaultRootComponent потребует Compose-специфичную логику → backend-dev эскалирует lead'у для создания frontend.md.

## Review Tags

- `concurrency-review` (AppShellTransitions — existing FSM state machine; verify OpenQuestCreate does not break existing state transitions)
- `security-review` (AuthRepository binding — ensure currentUidFlow shared hot flow, no multiple AuthStateListener registrations)

---

## Traceability

| Problem (from 2-grounding.md) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|-------------------------------|-----------|-------------|-----------------|-------------|-----------|
| P3: MyQuestsScreen navigation + FAB — Destination.OpenQuestCreate не работает | `AppShellTransitions.kt`, `DefaultRootComponent.kt`, `Labels.kt` | `AppShellScreen.LocalTabContent when-блок`, `DefaultRootComponent.onDestination()`, `Labels.kt:85-95` | guard: если active==QuestCreateRoot → no-op (Decision #47) | Implement OpenQuestCreate case in navigate(); Labels exhaustive when update | `./gradlew :shared:feature:app-shell:domain:jvmTest` + `DefaultRootComponentTest` |
| P3: AuthRepository not in Koin (blocks CascadingSyncOrchestrator) | `AppShellDataModule.kt` | `SyncModule.kt CascadingSyncOrchestrator(authRepo=get())` | `AuthRepositoryImpl` wraps `currentUidFlow` (hot shared flow) | Verify `appShellDataModule` includes `single<AuthRepository> { AuthRepositoryImpl(currentUidFlow) }` — ALREADY EXISTS in code (VERIFIED) | `./gradlew assemble` (Koin graph) |

---

## State Matrix Coverage (navigation)

Spec `02-behavior.md` DFD 3 — navigation guard for OpenQuestCreate (Decision #47):
- `active == MyQuestsRoot + OpenQuestCreate → push QuestCreateRoot`
- `active == QuestCreateRoot + OpenQuestCreate → no-op (guard)`

These are navigation invariants, не State Matrix 1-4. Тесты: `AppShellTransitionsTest.kt` (Walking Skeleton).

---

## New Files

None (все файлы уже существуют в Walking Skeleton или предыдущих фазах).

## Modified Files

| File | Change |
|------|--------|
| `shared/feature/app-shell/domain/src/.../logic/AppShellTransitions.kt` | add `OpenQuestCreate` case in `navigate()` function + guard (Decision #47) |
| `android/feature/app-shell/presentation/src/.../component/DefaultRootComponent.kt` | verify/implement `Destination.OpenQuestCreate` in `onDestination()` handler |
| `android/feature/app-shell/presentation/src/.../ui/labels/Labels.kt` | add `QuestCreateRoot → "Создание квеста"` to exhaustive when |

## Deleted Files

None.

---

## Dependencies

- Walking Skeleton: `LocalConfig.QuestCreateRoot`, `Destination.OpenQuestCreate`, `AuthRepository` — all VERIFIED as existing
- `AuthRepositoryImpl` in `shared/feature/app-shell/data` — VERIFIED as existing
- Phase-01 (quiz cleanup) — must be complete (no dead quiz references)
- Phase-02 (SyncModule SyncStateRepository) — needed for full Koin graph

---

## Acceptance Criteria (phase-04 scope)

- AC#29 (FAB click → OpenQuestCreate navigation): Domain AC from `AppShellTransitionsTest` — scenarios 41a-41e green
- `Labels.kt` exhaustive when: `QuestCreateRoot` case → `"Создание квеста"` (compile check)
- `AppShellDataModule` contains `single<AuthRepository>` — VERIFY (already exists per code read)
- `DefaultRootComponent` handles `Destination.OpenQuestCreate` without crash

---

## Tests Required

```
AppShellTransitionsTest (update/verify existing scenarios 41a-41e in Walking Skeleton):
  - scenario 41a: given LOCAL tab active, MyQuestsRoot in stack, when OpenQuestCreate, then QuestCreateRoot pushed (AC#29)
  - scenario 41b: given QuestCreateRoot already active, when OpenQuestCreate again, then no-op / no duplicate (Decision #47 guard)
  - scenario 41c: given Back from QuestCreateRoot, then MyQuestsRoot restored
  - scenario 41d: when OpenQuestCreate from non-LOCAL tab, then handle per spec
  - scenario 41e: Labels.kt exhaustive when compiles without warning

DefaultRootComponentTest (existing):
  - verify onDestination(OpenQuestCreate) does not crash
  - verify Labels.displayName for QuestCreateRoot returns non-blank string
```

---

## Pattern Invariants

- `AppShellTransitions.navigate()` ДОЛЖЕН обрабатывать все `Destination` sealed subtypes — exhaustive when (compile enforced)
- `AuthRepositoryImpl` ДОЛЖЕН использовать shared hot `currentUidFlow` (не cold per-collect) — уже реализован корректно (VERIFIED: `AuthRepositoryImpl.kt` получает `() -> Flow<String?>` lambda)
- `Labels.kt` ДОЛЖЕН иметь exhaustive when для всех `LocalConfig` subtypes — добавить QuestCreateRoot case

---

## Validation

```bash
./gradlew :shared:feature:app-shell:domain:jvmTest
./gradlew :android:feature:app-shell:presentation:test
./gradlew assemble
```

---

## Handoff Notes

- `AuthRepositoryImpl` уже существует и правильно реализован (shared hot flow, null для guest). `AppShellDataModule` — нужно верифицировать что `single<AuthRepository>` binding там есть. Если нет — backend-dev добавляет.
- Phase-03 depends on AuthRepository in Koin — phase-04 обеспечивает это binding. Порядок деплоя: phase-04 должна быть завершена перед integration test на device.
- `AppShellTransitionsTest` scenarios 41a-41e — если они уже написаны в Walking Skeleton и green → эта фаза = verify only. Если нет → test-dev пишет.
