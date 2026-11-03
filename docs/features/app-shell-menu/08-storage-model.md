---
date: 2026-04-18
feature: app-shell-menu
authors: [architect-component]
---

# Storage Model: App Shell Menu

## Summary

Фича `app-shell-menu` **не использует Room / локальную БД**. Вся навигационная state хранится in-memory в `MutableStateFlow<AppShellState>` (pure coroutines) внутри `DefaultRootComponent`. UserStats приходит из Firestore через `UserStatsRepositoryImpl` (Firebase snapshot listener) и кэшируется в `_state` как часть `AppShellState`.

## In-Memory State

| Объект | Тип | Lifetime | Где |
|--------|-----|----------|-----|
| `AppShellState` | `MutableStateFlow<AppShellState>` (kotlinx.coroutines — не Decompose `Value<>`; см. ADR-0011) | Activity lifecycle | `DefaultRootComponent._state` |
| `UserStats` | Поле внутри `AppShellState.userStats` | Activity lifecycle | обновляется из Firestore Flow |
| `TabState` per tab | Поле внутри `AppShellState.{local,internet,events,shop}State` | Activity lifecycle | обновляется через `AppShellTransitions` |

## Process Death Recovery

Process death recovery для sub-stacks отключена (`serializer = null`, ADR-COMP-02). При возврате после death:
1. `initUseCase()` вызывается заново → `UserStatsRepository.currentStats()` (Firestore single fetch).
2. `AppShellState.default(stats)` восстанавливает default navigation.
3. `observeStats()` coroutine подписывается заново — live updates возобновляются.

Навигационные позиции внутри вкладок (backStack) сбрасываются до root. Это допустимо по spec.

## Firestore (Remote Source)

Firestore document читается через `FirebaseUserStatsDataSource`:
- **Collection**: `users/{uid}` — path определяется в `platform/firebase` impl.
- **Data**: `UserStats` поля (currentSkill, qualification, premium, hearts, nolics, stars, gold, streak).
- **Access**: snapshot listener (realtime) + single fetch (`get()`) для cold start.
- **Offline**: `.catch { emit(UserStats.guest()) }` — UI не падает без сети.

## No Room Entities

Нет `@Entity`, нет `@Dao`, нет `@Database` для этой фичи.

Если в будущих фазах появится кэширование UserStats в Room — потребуется отдельный ADR и миграция схемы `MainDatabase` (backend-dev zone).
