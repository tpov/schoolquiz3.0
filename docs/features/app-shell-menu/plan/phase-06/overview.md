---
phase: phase-06
feature: app-shell-menu
date: 2026-04-18
---

# Phase-06: Drawer Content + UnderConstruction Integration

## Goal

Реализовать полное содержимое ModalNavigationDrawer: `DrawerHeader` (UserStats stats row + streak bar), `DrawerSectionList` (progressive unlock per `visibleSections`), `DrawerFooter` (debug/release filter). Подключить `DrawerContent` в `AppShellScreen` из phase-05 (заменить placeholder).

## Scope

- `DrawerHeader.kt` — avatar + nickname + premium badge + streak bar + stats row (hearts/gold/stars/nolics)
- `DrawerSectionList.kt` — `visibleSections(activeTab, userStats)` → `NavigationDrawerItem` list
- `DrawerFooter.kt` — `visibleFooterActions(BuildConfig.DEBUG)` + version label
- `DrawerContent.kt` — composition header + sections + footer
- Обновить `AppShellScreen.kt` из phase-05 — заменить drawer placeholder на `DrawerContent`

## Layer

ui / presentation (drawer composables)

## Role Inputs

- `frontend.md`
- `tests.md`

## Review Tags

(нет прямых coroutines в Drawer Composables — state flows из AppShellState через recomposition)

## State Matrix Coverage

| FSM | Строки | Coverage |
|-----|--------|----------|
| SectionVisibility (progressive unlock) | все строки: `visibleSections(tab, stats)` | `DrawerSectionList` через `visibleSections` domain function |
| DrawerHeader stats | userStats fields → UI: streakDays 0..10 → BrandProgressBar | `DrawerHeaderTest.kt` stats rendering |

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 1: Walking Skeleton integration | frontend-dev | `DrawerSectionList.kt` вызывает `visibleSections(tab, stats)` из domain/Visibility.kt | domain функции не переписываются | Вызвать `visibleSections` на каждой recomposition | instrumented test |
| Problem 7: Firebase integration | frontend-dev (indirect) | `UserStats` поля в `DrawerHeader.kt` — `state.userStats.streakDays`, etc. | UserStats уже через `AppShellState` flow | Читать из `state.userStats` — никакого прямого Firebase | compile check |

## New Files

```
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/drawer/DrawerHeader.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/drawer/DrawerSectionList.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/drawer/DrawerFooter.kt
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/drawer/DrawerContent.kt
android/feature/app-shell/presentation/src/androidTest/kotlin/.../DrawerHeaderTest.kt
android/feature/app-shell/presentation/src/test/kotlin/.../DrawerFooterMapperTest.kt
```

## Modified Files

```
android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/AppShellScreen.kt
  (replace drawer placeholder with DrawerContent(state, navigator))
```

## Deleted Files

none

## Dependencies

- Phase-05 MUST complete (AppShellScreen, SchoolQuizTheme)
- Phase-03 MUST complete (BrandProgressBar for streak bar, BrandCard for stats row)
- Phase-02 MUST complete (MaterialTheme tokens)

## Acceptance Criteria

1. `./gradlew :android:feature:app-shell:presentation:compileDebugKotlin` — BUILD SUCCESSFUL
2. `DrawerHeader` отображает все 7 UserStats fields: nickname, avatarUrl (or avatar placeholder), hasPremium badge, streakDays (10-segment BrandProgressBar), stars, nolics, standardHearts+goldHearts, gold
3. `DrawerSectionList` использует `visibleSections(tab, stats)` — hidden sections не отрисовываются (spec FR #20, AC 23a-g)
4. `DrawerFooter` — debug: «Design catalog» + «О приложении» + version; release: «О приложении» + version (spec FR scope item 4)
5. Tap по footer «Design catalog» → `navigator.goTo(Destination.OpenDesignCatalog)` (spec FR #17)
6. Drawer content меняется при смене вкладки (per-tab drawer per spec FR #2)
7. `DrawerFooter` использует параметр `versionName: String` (НЕ `BuildConfig.VERSION_NAME`) — H8 fix; значение приходит из `appVersionName` параметра `AppShellScreen`
8. **H3/AC 26**: Tap по footer «О приложении» → локальный `AlertDialog` с версией; НЕ изменяет domain state (spec `0-spec.md:426-430`)
9. **AC 20**: `BrandDrawerItem` wrapper существует в `DrawerFooter.kt` с `badge: BadgeContent? = null` param. `DrawerSectionList` использует `BrandDrawerItem` для секций.

## Tests Required

```
drawer_footer_mapper_debug_shows_design_catalog:
  given visibleFooterActions(isDebugBuild = true)
  when mapped
  then contains DrawerFooterAction.DesignCatalog

drawer_footer_mapper_release_hides_design_catalog:
  given visibleFooterActions(isDebugBuild = false)
  when mapped
  then does NOT contain DrawerFooterAction.DesignCatalog

drawer_header_renders_nickname:
  given DrawerHeader(userStats = UserStats.guest().copy(nickname = "Alice"))
  when composed
  then Text("Alice") visible

drawer_header_streak_bar_progress:
  given DrawerHeader(userStats = UserStats.guest().copy(streakDays = 5))
  when BrandProgressBar progress calculated (5/10 = 0.5f)
  then correct fraction passed to BrandProgressBar

drawer_section_list_hidden_section_not_rendered:
  given DrawerSectionList(tab = LOCAL, stats = UserStats.guest())
  when visibleSections(LOCAL, guest) returns [MyQuests, MyQuests only - Settings requires unlock]
  then Settings section not displayed (AC 23a progressive unlock)
```

## Validation

```bash
./gradlew :android:feature:app-shell:presentation:compileDebugKotlin --no-configuration-cache
./gradlew :android:feature:app-shell:presentation:test --no-configuration-cache
```

## Handoff Notes

- После phase-06 + phase-07 (MainActivity wiring) весь shell flow завершён. AC 1-30 должны быть выполнимы через manual smoke test на device/emulator.
