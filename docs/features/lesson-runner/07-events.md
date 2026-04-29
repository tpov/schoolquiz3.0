---
date: 2026-04-27
authors: architect-component, architect-high-level
feature: lesson-runner
---

# 07 Events: Lesson Runner

## Overview

`RunnerEvent` — односторонний поток событий: error-feedback (Snackbar) + navigation signal. Паттерн: `Channel<RunnerEvent>` in-process. Идентичен `AppShellScreen.kt:104,131-147` (precedent в проекте, включая `RootEvent.SystemBack` как navigation action в том же channel).

**Navigation contract (A2 hybrid, 2026-04-27)**: `NavigateBack` — сигнал component→screen о готовности к выходу. `LessonRunnerScreen` получает `onNavigateBack: () -> Unit` callback от `QuizzesScreen` и вызывает его при получении `NavigateBack` в `LaunchedEffect`. Component не знает о `StackNavigation`.

---

## Event Types

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/.../event/RunnerEvent.kt`

```kotlin
sealed interface RunnerEvent {
    data class SaveAttemptFailed(val error: SaveError) : RunnerEvent
    data object SaveRatingFailed : RunnerEvent
    data object NavigateBack : RunnerEvent   // terminal states signal: onFinish/onExit/onBack
}
```

**`SaveAttemptFailed`**: эмитируется когда `LessonAttemptRepository.save()` → `Result.failure`. State переходит в `RunnerState.SaveFailed` И event отправляется параллельно. `saveWarning=true` в `RunnerUiState.Result` — основной индикатор; event — дополнительный Snackbar.

**`SaveRatingFailed`**: эмитируется когда `LessonRatingRepository.submit()` → `Result.failure`. State остаётся `Completed`. UI показывает Snackbar через event (нет отдельного fail state для rating).

---

## Channel Declaration

**File**: `android/feature/lesson-runner/presentation/src/main/kotlin/.../component/DefaultLessonRunnerRootComponent.kt`

```kotlin
private val _events = Channel<RunnerEvent>(capacity = Channel.BUFFERED)
override val events: Flow<RunnerEvent> = _events.receiveAsFlow()  // C5: Flow, not ReceiveChannel

init {
    lifecycle.doOnDestroy { _events.close() }
}

private fun emitEvent(event: RunnerEvent) {
    _events.trySend(event)  // non-blocking; BUFFERED handles brief UI pause
}
```

Pattern per `DefaultRootComponent.kt:113-114`: `receiveAsFlow()` вместо прямого `ReceiveChannel` exposure.

---

## Screen Consumption

```kotlin
// LessonRunnerScreen.kt — onNavigateBack передаётся QuizzesScreen-ом
LaunchedEffect(component) {
    component.events.collect { event ->
        when (event) {
            is RunnerEvent.SaveAttemptFailed ->
                snackbarHostState.showSnackbar("Не удалось сохранить результат")
            RunnerEvent.SaveRatingFailed ->
                snackbarHostState.showSnackbar("Не удалось отправить оценку")
            RunnerEvent.NavigateBack ->
                onNavigateBack()   // callback из QuizzesScreen → navigation.pop()
        }
    }
}
```

**Key**: `onNavigateBack` — `() -> Unit` Compose callback из `QuizzesScreen`. Вызывает `component.popCurrentChild()` → `StackNavigation.pop()`. Компонент не владеет `StackNavigation`.

---

## AC Coverage

| AC | Event |
|----|-------|
| AC-52a | `SaveAttemptFailed` + `RunnerUiState.Result.saveWarning=true` |
| AC-52b | `SaveRatingFailed` → Snackbar |
| AC-5, AC-34 | `NavigateBack` → `onNavigateBack()` → `StackNavigation.pop()` (terminal states: Completed, Aborted, InitFailed via `onBack()`) |
