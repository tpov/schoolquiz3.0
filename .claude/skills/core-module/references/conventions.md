# Core Module — Conventions & Contracts

Package: бери `core_package` из `PROJECT-CONTEXT.md`
Path: бери `core_path` из `PROJECT-CONTEXT.md`
Потребители: project-specific footprint должен быть либо задокументирован в `PROJECT-CONTEXT.md`, либо посчитан через code search в текущем репозитории.

## Структура пакетов

| Пакет | Назначение | Key files |
|-------|-----------|-----------|
| `constants/` | Глобальные константы | `Constants.kt`, `SettingsKeys.kt` |
| `constants/call/` | Call-related strings, IDs | `CallConstants.kt`, `CallIdConstants.kt` |
| `constants/chat/` | Chat limits, validation | `ChatConstants.kt` |
| `constants/database/` | Table/column names, cache limits | `DatabaseConstants.kt` |
| `constants/network/` | HTTP codes, headers, retry | `HttpConstants.kt`, `NetworkConstants.kt`, `NetworkRetryConstants.kt` |
| `constants/websocket/` | Channel names, events | `WebSocketChannelConstants.kt` |
| `error/` | Error hierarchy, safe call | `AppError.kt`, `ErrorHandler.kt`, `SafeCall.kt` |
| `logging/` | Remote logging | `RemoteAndroidLogger.kt` |
| `network/` | API paths, connectivity | `ApiEndpoints.kt`, `ApiDefaults.kt`, `NetworkStatusMonitor.kt`, `NetworkStatusProvider.kt` |
| `call/` | Call signal TTL, UI tracking | `CallSignalRegistry.kt`, `CallUiVisibilityTracker.kt` |
| `theme/` | Chat themes | `ChatTheme.kt`, `ChatThemeRepository.kt`, `AccentColorRepository.kt` |
| `search/` | User search/dedup | `UserSearchCoordinator.kt` |
| `permissions/` | Permission tracking | `PermissionRequestTracker.kt` |
| `update/` | APK install | `ApkInstaller.kt` |
| `utils/` | 17 utility files | URL fixers, emoji, files, sound, UI |
| `websocket/` | Base WS handler | `BaseWebSocketHandler.kt`, `WebSocketEventFilter.kt` |

## Паттерны

### 1. Sealed class hierarchy (errors)
```kotlin
// AppError.kt:9 — 7 sealed subtypes
sealed class AppError : Exception() {
    sealed class Network : AppError() { ... }
    sealed class Auth : AppError() { ... }
    sealed class Call : AppError() { ... }
    // ...
}
```
**Convention**: Новые error-типы — вложенные sealed class внутри `AppError`.

### 2. Singleton с double-checked locking
```kotlin
// ErrorHandler.kt:19-29
@Volatile private var instance: ErrorHandler? = null
fun getInstance(): ErrorHandler = instance ?: synchronized(this) { ... }
```
**Convention**: Stateful singletons используют `@Volatile + synchronized`. Stateless — `object`.

### 3. Process-wide bridge object
```kotlin
// NetworkReachabilityReporter.kt:6
object NetworkReachabilityReporter {
    private var provider: NetworkStatusProvider? = null
    fun initialize(provider: NetworkStatusProvider) { ... }
}
```
**Convention**: Глобальный bridge для cross-layer доступа без DI. Lazy `initialize()` в application startup.

### 4. Safe call с retry
```kotlin
// SafeCall.kt:9
suspend fun <T> safeCall(block: suspend () -> T): UiResult<T>
suspend fun <T> safeCallWithRetry(config: RetryConfig, block: ...): UiResult<T>
```
**Convention**: Все network/DB операции через `safeCall`. Retry через `RetryConfig(factor=2.0)`.

### 5. Constants — nested objects
```kotlin
// CallConstants.kt
object CallConstants {
    object StatusMessages { const val CONNECTING = "connecting" }
    object ErrorMessages { const val NETWORK = "network_error" }
    object CallEndReasons { const val NORMAL = "normal" }
}
```
**Convention**: Группировка через вложенные `object`. Доступ: `CallConstants.StatusMessages.CONNECTING`.

### 6. Interface + implementation (connectivity)
```kotlin
// NetworkStatusProvider.kt:8 — interface
// NetworkStatusMonitor.kt:29 — implementation
```
**Convention**: Interface в core, implementation в core (не в data). Exposed через `StateFlow`.

### 7. TTL registry
```kotlin
// CallSignalRegistry.kt:5
// ConcurrentHashMap, TTL=60s, MAX_ENTRIES=256, eviction по oldest
```
**Convention**: Thread-safe через `ConcurrentHashMap`. Constants для TTL и limits.

### 8. Top-level internal functions (search)
```kotlin
// SearchCoordinator.kt — internal fun matchesQuery(), filterByQuery()
```
**Convention**: Stateless utility functions — top-level `internal fun`, без class wrapper.

### 9. Extension functions (utils)
```kotlin
// AvatarUrlExtensions.kt — String.appendAvatarCacheBuster()
```
**Convention**: Extensions в отдельных файлах `*Extensions.kt`.

### 10. Mappers
Два подхода: `object Mapper` с explicit функциями или extension `.toDomain()` / `.toEntity()` на source type. Mappers живут в data layer. Ref: [Kotlin Extension Functions & Clean Architecture](https://dev.to/myougatheaxo/kotlin-extension-functions-clean-architecture-android-best-practices-1965)

### 11. State holders
ViewModel = screen-level state holder. State = immutable data class, exposed через StateFlow. Plain class state holder — для non-ViewModel случаев. Ref: [Android Developers: State holders](https://developer.android.com/topic/architecture/ui-layer/stateholders)

## Naming conventions

| Тип | Naming | Пример |
|-----|--------|--------|
| Stateless utility | `object` | `ApiEndpoints`, `PathParams`, `CallConstants` |
| Stateful singleton | `class` + `getInstance()` | `ErrorHandler`, `RemoteLogger` |
| Process bridge | `object` + `initialize()` | `NetworkReachabilityReporter`, `UiVisibilityTracker` |
| Constants group | Nested `object` | `CallConstants.StatusMessages` |
| Pure data | `data class` | `ChatTheme`, `AppConnectivityState` |
| State enum | `enum class` | `BackendReachabilityState`, `EffectiveConnectivityMode` |
| Search/filter | Top-level `internal fun` | `matchesUserQuery()` |
| Extensions | `*Extensions.kt` | `AvatarUrlExtensions.kt` |

## API paths (ApiEndpoints)

`ApiEndpoints.kt` — единый source of truth для всех API paths.
- Группировка через вложенные `object` (Auth, Calls, Chats, Users, etc.)
- Path = relative (без base URL, без `/v1/` prefix — Retrofit добавляет из base URL)

## Зависимости core

**Core зависит от** (допустимые):
- `android.*`, `androidx.*` — Android framework
- `kotlinx.coroutines.*` — Flow, StateFlow
- `okhttp3.*` — backend probe
- `retrofit2.HttpException` — error mapping

**Core зависит от** (legacy, нежелательные):
- app-level composition root / factory — в `RemoteLogger`
- `data.api` logging API — в `RemoteLogger`
- `data.network` API provider — в `RemoteLogger`
- `data.websocket` event model — в `BaseWebSocketHandler`, `WebSocketEventFilter`
- `domain.models` search entity — в `SearchCoordinator`

## Правила для нового кода в core

1. **Перед созданием** — проверь таблицу пакетов. Если аналог существует — расширь, не дублируй
2. **Constants** — добавляй в существующий `*Constants.kt` по области. Новый файл — только для новой области
3. **Errors** — новый тип ошибки = sealed subclass внутри `AppError`
4. **Utility** — stateless → `object` или top-level `internal fun`. Stateful → `class` + singleton pattern
5. **Thread safety** — shared mutable state через `@Volatile`, `ConcurrentHashMap`, или `synchronized`
6. **Naming** — следуй таблице naming conventions выше
7. **Dependencies** — core НЕ должен зависеть от `data/*`, `presentation/*`, `ui/*`. Legacy зависимости (см. выше) — не расширять
