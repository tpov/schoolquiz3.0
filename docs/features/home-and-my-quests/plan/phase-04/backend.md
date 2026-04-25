---
phase: 04
role: backend-dev
---

# Phase-04 Backend Tasks

Большинство работы этой фазы уже существует в Walking Skeleton. Задачи = verify + implement missing navigation handler + Labels update.

---

## Pattern Invariants

- `AppShellTransitions.navigate()` — exhaustive when по Destination sealed interface (compile enforced)
- `DefaultRootComponent` — не вызывает бизнес-логику напрямую, только делегирует через transitions
- `Labels.kt` — exhaustive when по LocalConfig sealed interface

---

## 1. Verify AppShellDataModule — AuthRepository binding

- **Файл:** `shared/feature/app-shell/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/data/di/AppShellDataModule.kt`
- **Тип:** Koin module — verify/update
- **Сигнатура:** `fun appShellDataModule(currentUidFlow: () -> Flow<String?>): Module = module { ... }`
- **Вход:** существующий module
- **Поведение / Выход:**
  - Если `single<AuthRepository> { AuthRepositoryImpl(currentUidFlow) }` уже есть → NO CHANGE (verify)
  - Если отсутствует → добавить binding
- **Edge cases:**
  - AuthRepositoryImpl принимает `currentUidFlow: () -> Flow<String?>` lambda — НЕ сам Flow (lazy evaluation для shared hot flow из AppApplication)
- **Canonical reference:** `06-api-contract.md` §13 appShellDataModule

---

## 2. Implement OpenQuestCreate in AppShellTransitions.navigate()

- **Файл:** `shared/feature/app-shell/domain/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/logic/AppShellTransitions.kt`
- **Тип:** class — update navigate() function
- **Сигнатура:** добавить case для `Destination.OpenQuestCreate` в `when(destination)` блок
- **Вход:** `state: AppShellState`, `destination: Destination.OpenQuestCreate`
- **Поведение / Выход:**
  - Guard: `if (state.localState.stack.active == LocalConfig.QuestCreateRoot) return TransitionResult.NoOp` (Decision #47)
  - Иначе: push `LocalConfig.QuestCreateRoot` на LOCAL tab stack, preserving `MyQuestsRoot` в backStack
  - Result: активный экран = QuestCreateRoot; back вернёт на MyQuestsRoot
  - Если активная tab не LOCAL → spec нужно определить поведение. Рекомендация: switch to LOCAL tab first, then push QuestCreateRoot. Per spec `02-behavior.md` DFD 3 FAB flow: FAB только на MyQuestsScreen → всегда LOCAL tab active при click.
- **Edge cases:**
  - Если `active == QuestCreateRoot` → no-op (guard)
  - Configuration must ensure backStack is not empty after push
- **Depends on:** existing NavStack.push() method, LocalConfig.QuestCreateRoot (Walking Skeleton)
- **Canonical reference:** `06-api-contract.md` §5 Destination.OpenQuestCreate; Decision #41, #47

---

## 3. Update DefaultRootComponent.onDestination()

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/component/DefaultRootComponent.kt`
- **Тип:** class — update onDestination handler
- **Сигнатура:** добавить `Destination.OpenQuestCreate` case в `when(destination)` блок в `onDestination()`
- **Вход:** `destination: Destination.OpenQuestCreate`
- **Поведение / Выход:**
  - Delegate to `appShellTransitions.navigate(state, destination)` (same pattern as `OpenDesignCatalog`)
  - Apply resulting state mutation
- **Edge cases:**
  - Exhaustive when — добавление `OpenQuestCreate` case делает when exhaustive для нового sealed subtype
- **Depends on:** AppShellTransitions (task #2)
- **Canonical reference:** `2-grounding.md` Problem 3 Fix Shape; Decision #41

---

## 4. Update Labels.kt — QuestCreateRoot case

- **Файл:** `android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/labels/Labels.kt`
- **Тип:** function update
- **Сигнатура:** добавить `LocalConfig.QuestCreateRoot -> "Создание квеста"` в exhaustive when для displayName
- **Вход:** exhaustive when по LocalConfig sealed interface
- **Поведение / Выход:**
  - `QuestCreateRoot → "Создание квеста"` (или аналогичный string resource)
  - Compile error если case отсутствует при exhaustive when
- **Edge cases:**
  - String локализация — для MVP hardcoded string допустима (decision per project convention)
- **Canonical reference:** `2-grounding.md` Problem 3 Entry Points (Labels.kt:85-95)
