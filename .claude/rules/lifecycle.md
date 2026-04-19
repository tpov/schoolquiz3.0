# Android Lifecycle — cleanup и background behavior

Platform-level правила для любой Android фичи. Feature-specific поведение (например "этот конкретный звонок живёт в background") фиксируется в `0-spec.md` Primary User Journeys.

## Проблема: `onDestroy()` не гарантирован

`onDestroy()` вызывается:
- Когда пользователь завершает Activity (Back, `finish()`)
- При configuration change (поворот экрана, разделение)
- **НЕ гарантированно** при low-memory kill процесса
- **НЕ сразу** при Home / swipe home — Activity в stopped, onDestroy может быть через секунды/минуты/никогда

Вывод: `onDestroy()` — ненадёжное место для "user ушёл из UI" cleanup. Годится только для instance-level cleanup (unregister listeners, release bindings).

## Правила

### 1. `onDestroy` — только instance cleanup

```kotlin
override fun onDestroy() {
    super.onDestroy()
    binding = null           // ✅ release view binding
    adapter.cleanup()        // ✅ release adapter resources
    listener?.unregister()   // ✅ prevent memory leak
    // ❌ НЕ здесь: business actions (endCall, stopService, sendBroadcast для kill)
}
```

### 2. Cleanup-таблица по callback

| Что cleanup | Где | Почему |
|-------------|-----|--------|
| UI-only ресурсы (Views, animations, adapters) | `onDestroy` | Привязаны к Activity instance |
| Observers, listeners на Activity/Fragment | `onDestroy` (или `onDestroyView` для Fragment) | Memory leak prevention |
| Ресурсы foreground-only (camera preview, sensor, screen brightness) | `onStop` | User не смотрит на UI — они не нужны |
| Business operations (call, upload, playback) — если они foreground-only | `onStop` (опционально через ViewModel) | — |
| Business operations — если они должны жить в background | **НЕ в Activity вообще** | Живут в Foreground Service |
| Persistence (save draft, sync state) | `onPause` | Гарантированно вызывается перед background |

### 3. Foreground operations требуют Foreground Service

Если business operation должна жить **дольше UI** (звонок, upload, music playback, navigation):

1. Операция живёт в **Foreground Service** с ongoing notification — система не убивает такой процесс
2. Service запускается через `startForeground()` в момент старта operation (не в Activity `onCreate`)
3. Activity **только подписывается** на service state (через binder / ViewModel / Repository / Flow)
4. `onDestroy()` Activity **НЕ завершает service** — service сам решает когда завершиться (когда operation logically done)

### 4. `isFinishing` vs `isChangingConfigurations`

Если business cleanup из `onDestroy` всё же нужен (не получается перенести в ViewModel.onCleared) — защити его проверкой:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    if (isFinishing && !isChangingConfigurations) {
        // User реально ушёл, не configuration change
        viewModel.onActivityFinished()
    }
    // Иначе (config change или background) — ничего business не делаем
}
```

### 5. `ViewModel.onCleared()` — правильное "user really left"

`onCleared()` вызывается когда ViewModelStore очищается — то есть user реально завершил screen, НЕ на config change.

```kotlin
class VoipCallViewModel(
    private val endCallUseCase: EndCallUseCase,
) : ViewModel() {

    override fun onCleared() {
        super.onCleared()
        // User реально ушёл (не config change).
        // Но если звонок должен жить в background Foreground Service — НЕ завершать отсюда.
        // Решение зависит от spec Primary User Journey.
    }
}
```

## Пример нарушения (ловится этим правилом)

```kotlin
// ❌ onDestroy() убивает звонок при любом уходе из Activity — включая свертывание приложения
class VoipActiveCallActivity : AppCompatActivity() {

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(this, CallService::class.java).apply {
            action = ACTION_END_CALL
        }
        startService(intent)  // ❌ service завершит звонок даже если user просто ушёл в background
    }
}
```

**Почему баг**: user нажал Home → Android держит процесс живым с Foreground Service (звонок должен продолжаться) → НО Activity всё равно в какой-то момент получит `onDestroy()` → звонок убивается, хотя user его не завершал.

```kotlin
// ✅ Activity делегирует ViewModel, ViewModel различает finishing vs background
class VoipActiveCallActivity : AppCompatActivity() {

    private val viewModel: VoipCallViewModel by viewModels { factory }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onActivityDestroyed(
            isFinishing = isFinishing,
            isChangingConfigurations = isChangingConfigurations
        )
    }
}

class VoipCallViewModel(
    private val endCallUseCase: EndCallUseCase,
) : ViewModel() {

    fun onActivityDestroyed(isFinishing: Boolean, isChangingConfigurations: Boolean) {
        // НЕ завершаем звонок здесь — он живёт в Foreground Service.
        // Service сам решит (по business logic из spec) когда завершиться.
    }

    override fun onCleared() {
        super.onCleared()
        // User реально ушёл — если spec говорит "звонок привязан к screen", завершить.
        // Если spec говорит "звонок живёт в background" — НЕ завершать.
    }
}
```

## Review check (grep-паттерны для architect-reviewer)

```bash
# Нарушение: business actions в onDestroy без isFinishing check
# Ищем onDestroy blocks содержащие kill-like actions
grep -rn -A 15 "override fun onDestroy" <ui_path>/ --include="*Activity.kt" --include="*Fragment.kt" | \
    grep -E "(endCall|stopService|sendBroadcast|cancelJob|disconnect|ACTION_END|ACTION_STOP|ACTION_KILL)"

# Нарушение: Activity запускает kill-intent в onDestroy без проверок
grep -rn -B 3 -A 10 "ACTION_END\|ACTION_STOP\|ACTION_KILL" \
    <ui_path>/ --include="*Activity.kt" --include="*Fragment.kt" | \
    grep -B 10 "onDestroy"
```

Если найден kill-like action в `onDestroy` без `if (isFinishing && !isChangingConfigurations)` wrap — blocker; перенести cleanup в `ViewModel.onCleared()` или в Foreground Service lifecycle.

## Связанные правила

- `.claude/rules/use-cases.md` — Activity не вызывает UseCase напрямую, только через ViewModel
- `.claude/rules/clean-architecture.md` — layer boundaries
