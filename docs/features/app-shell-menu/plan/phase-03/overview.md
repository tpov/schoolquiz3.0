---
phase: phase-03
feature: app-shell-menu
date: 2026-04-18
---

# Phase-03: DS Wrappers + DesignCatalogScreen

## Goal

Создать все Brand Component wrappers (`BrandCard`, `BrandPrimaryButton`, `BrandSecondaryButton`, `BrandProgressBar`, `BrandCircleIconButton`, `CategoryIcon`) и runtime `DesignCatalogScreen` в `android/core/designsystem`. После фазы — все wrappers компилируются и отображаются в IDE preview.

## Scope

- 6 Brand Component wrappers в `android/core/designsystem`
- `DesignCatalogScreen.kt` — runtime Composable отображающий все wrappers (debug-only screen)

## Layer

ui / designsystem

## Role Inputs

- `frontend.md`
- `tests.md`

## Review Tags

(нет concurrency — pure Compose composables)

## State Matrix Coverage

FSM не затрагиваются. Phase-03 создаёт component library, используемую в phase-05/06 drawer.

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 2: Compose compiler gap | frontend-dev | `android/core/designsystem/build.gradle.kts` | Phase-02 уже включила Compose в модуле | Wrappers используют готовую theme из phase-02 | compile check |
| Problem 1: Walking Skeleton integration | frontend-dev | `DesignCatalogScreen.kt` открывается через `Destination.OpenDesignCatalog` → `LocalConfig.DesignCatalogRoot` | Debug-only guard per spec FR #17 | `DesignCatalogScreen` — Composable, route подключается в phase-05 `AppShellScreen` | compile check |

## New Files

```
android/core/designsystem/src/main/kotlin/.../designsystem/components/BrandCard.kt
android/core/designsystem/src/main/kotlin/.../designsystem/components/BrandPrimaryButton.kt
android/core/designsystem/src/main/kotlin/.../designsystem/components/BrandSecondaryButton.kt
android/core/designsystem/src/main/kotlin/.../designsystem/components/BrandProgressBar.kt
android/core/designsystem/src/main/kotlin/.../designsystem/components/BrandCircleIconButton.kt
android/core/designsystem/src/main/kotlin/.../designsystem/components/CategoryIcon.kt
android/core/designsystem/src/main/kotlin/.../designsystem/catalog/DesignCatalogScreen.kt
```

## Modified Files

none (build.gradle.kts уже обновлён в phase-02)

## Deleted Files

none

## Dependencies

- Phase-02 MUST complete (SchoolQuizTheme, Color.kt, Shape.kt)

## Acceptance Criteria

1. `./gradlew :android:core:designsystem:compileDebugKotlin` — BUILD SUCCESSFUL
2. Все 6 wrappers имеют `@Preview` аннотацию — отображаются в IDE
3. `DesignCatalogScreen` является `@Composable` и демонстрирует все 6 wrappers
4. `DesignCatalogScreen` НЕ является `DrawerSection` — это `LocalConfig.DesignCatalogRoot` экран (spec FR #17)
5. Все wrappers используют только `MaterialTheme.*` токены из `SchoolQuizTheme` — нет hardcoded colors

## Tests Required

```
brand_card_renders_without_crash:
  given BrandCard { Text("test") }
  when composed in SchoolQuizTheme
  then no exception, content visible

brand_primary_button_click:
  given BrandPrimaryButton(onClick = { clicked = true }, text = "Test")
  when button clicked
  then clicked == true

brand_progress_bar_value_range:
  given BrandProgressBar(progress = 0.5f)
  when composed
  then renders without crash, progress = 0.5f

design_catalog_screen_renders:
  given DesignCatalogScreen() in SchoolQuizTheme
  when composed
  then no exception
```

## Validation

```bash
./gradlew :android:core:designsystem:compileDebugKotlin --no-configuration-cache
./gradlew :android:core:designsystem:assembleDebug --no-configuration-cache
```

## Handoff Notes

- Phase-05 импортирует `DesignCatalogScreen` из этого модуля для route binding в `AppShellScreen`
- Phase-06 импортирует все Brand Components для drawer header (`BrandProgressBar` для streak bar, `BrandCard` для stats row)
