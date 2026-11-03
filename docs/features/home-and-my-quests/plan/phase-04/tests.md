---
phase: 04
role: test-dev
---

# Phase-04 Test Tasks

---

## Pattern Invariants

- `AppShellTransitionsTest` — чистый JVM тест, использует TestNavStack/fake state
- test-dev проверяет Walking Skeleton scenario 41a-41e: если уже green → только verify; если нет → пишет
- Все тесты в `shared/feature/app-shell/domain/commonTest`

---

## 1. AppShellTransitionsTest — verify/write scenarios 41a-41e

- **Файл:** `shared/feature/app-shell/domain/src/commonTest/kotlin/com/tpov/schoolquiz/shared/feature/app_shell/domain/AppShellTransitionsTest.kt` (найти существующий файл)

**Сценарии (per 04-testing.md §3 Domain Test Scenarios 41a-41e):**

```
scenario_41a_when_MyQuestsRoot_active_then_QuestCreateRoot_pushed:
  GIVEN: state with LOCAL tab active, stack=[MyQuestsRoot]
  WHEN: navigate(OpenQuestCreate)
  THEN: result.localState.stack.active == QuestCreateRoot
  AND: result.localState.stack.backStack contains MyQuestsRoot
  [AC#29 / scenario 41a]

scenario_41b_when_QuestCreateRoot_already_active_then_no_op:
  GIVEN: state with LOCAL tab active, stack=[QuestCreateRoot], backStack=[MyQuestsRoot]
  WHEN: navigate(OpenQuestCreate)
  THEN: TransitionResult.NoOp OR state unchanged
  [Decision #47 guard / scenario 41b]

scenario_41c_back_from_QuestCreateRoot_restores_MyQuestsRoot:
  GIVEN: state with stack.active=QuestCreateRoot, backStack=[MyQuestsRoot]
  WHEN: navigate(Back)
  THEN: result.localState.stack.active == MyQuestsRoot
  AND: result.localState.stack.backStack is empty
  [scenario 41c]

scenario_41d_when_OpenQuestCreate_from_non_LOCAL_tab_then_handled:
  GIVEN: state with GLOBAL tab active (not LOCAL)
  WHEN: navigate(OpenQuestCreate)
  THEN: state transitions per spec — either switch to LOCAL+push QuestCreateRoot, or define behavior in AppShellTransitions (see 02-behavior.md DFD3)
  [scenario 41d]

scenario_41e_Labels_QuestCreateRoot_displayName_non_blank:
  GIVEN: LocalConfig.QuestCreateRoot
  WHEN: Labels.displayName(QuestCreateRoot)  [or whatever Labels function]
  THEN: result is non-blank string (= "Создание квеста")
  [scenario 41e / Labels.kt exhaustive when — per phase-04/overview.md:100]
```

---

## 2. DefaultRootComponentTest — verify OpenQuestCreate (existing test file)

- **Файл:** `android/feature/app-shell/presentation/src/test/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/DefaultRootComponentTest.kt`

**Добавить/verify:**

```
when_onDestination_OpenQuestCreate_then_no_crash:
  GIVEN: component in initial state (LOCAL tab, MyQuestsRoot)
  WHEN: navigator.goTo(Destination.OpenQuestCreate)
  THEN: no IllegalStateException / unhandled destination exception
  AND: component.state reflects QuestCreateRoot active

when_onDestination_OpenQuestCreate_twice_then_guard_applied:
  GIVEN: component after first OpenQuestCreate
  WHEN: navigator.goTo(Destination.OpenQuestCreate) again
  THEN: backStack still has exactly one MyQuestsRoot entry (no duplicate)
```
