---
phase: phase-02
feature: app-shell-menu
date: 2026-04-18
---

# Phase-02: Design System Foundation

## Goal

Создать `SchoolQuizTheme` с брендовой палитрой Material3 (dark-only), типографикой и shapes в модуле `android/core/designsystem`. После этой фазы модуль компилируется, тема применима как `SchoolQuizTheme { content }`.

## Scope

- `android/core/designsystem/build.gradle.kts` — применить convention plugin `schoolquiz.android.compose.library`, добавить Compose BOM + Material Icons Extended
- `SchoolQuizTheme.kt` — MaterialTheme wrapper с darkColorScheme
- `Color.kt` — брендовые цвета per ADR-0010
- `Shape.kt` — MaterialTheme.shapes (4/8/12/16/24 dp per spec)
- `Type.kt` — Material3 типографика (defaults)

## Layer

ui / designsystem

## Role Inputs

- `frontend.md`
- `tests.md`

## Review Tags

(нет concurrency/Flow/async — pure Compose theme)

## State Matrix Coverage

FSM не затрагиваются (pure design layer). Phase-02 создаёт visual foundation, на которую опираются phase-03, phase-05, phase-06.

## Traceability

| Problem (from grounding) | Code Owner | Entry Points | Contract Limits | Fix Approach | Validation |
|--------------------------|-----------|--------------|-----------------|-------------|------------|
| Problem 2: Compose compiler gap | frontend-dev | `buildSrc/AndroidLibraryConventionPlugin.kt:38-40`; `android/core/designsystem/build.gradle.kts:9` | Kotlin 1.9.22 → compose compiler 1.5.10 (fixed pair); plugin создан в phase-01 | Apply `schoolquiz.android.compose.library` (создан phase-01); add Compose BOM в designsystem | `./gradlew :android:core:designsystem:compileDebugKotlin` |
| Problem 6: Feature module dependencies missing | frontend-dev | `android/core/designsystem/build.gradle.kts:9` | только Compose BOM, без domain deps | Заменить `bundles.androidx.ui.base` на Compose BOM + Material3 | `./gradlew :android:core:designsystem:compileDebugKotlin` |

## New Files

```
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Color.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Shape.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/Type.kt
android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/SchoolQuizTheme.kt
```

## Modified Files

```
android/core/designsystem/build.gradle.kts
```

## Deleted Files

none

## Dependencies

- Phase-01 MUST complete (convention plugin `schoolquiz.android.compose.library` создаётся в phase-01)

## Acceptance Criteria

1. `./gradlew :android:core:designsystem:compileDebugKotlin` — BUILD SUCCESSFUL
2. `SchoolQuizTheme.kt` содержит `darkColorScheme(background = Color(0xFF000000), primary = Color(0xFF4285F4), secondary = Color(0xFFFFD700))` — ADR-0010
3. Light colorScheme не существует в коде (dark-only per ADR-0010 + spec NFR #4)
4. `SchoolQuizTheme` принимает `darkTheme: Boolean = true` + `content: @Composable () -> Unit`
5. `MaterialTheme.shapes` per `06-api-contract.md:392-394`: `extraSmall`=4dp, `small`=8dp, `medium`=12dp, `large`=16dp, `extraLarge`=24dp (H1 fix — corrected from previous wrong values)

## Tests Required

```
theme_dark_colors_match_adr_0010:
  given SchoolQuizTheme applied (instrumented test или preview-snapshot)
  when MaterialTheme.colorScheme.background accessed
  then == Color(0xFF000000)

theme_colors_primary_secondary:
  given SchoolQuizTheme applied
  when MaterialTheme.colorScheme.primary / .secondary accessed
  then primary == Color(0xFF4285F4), secondary == Color(0xFFFFD700)

theme_shapes_small_medium_large:
  given SchoolQuizTheme applied
  when MaterialTheme.shapes.small / .medium / .large accessed
  then CornerBasedShape with correct dp values

no_light_theme_exported:
  given source file Color.kt
  when searching for lightColorScheme function
  then absent (grep returns empty)
```

Note: Compose theme тесты — instrumented (`androidTest`). Для JVM — smoke проверка что `SchoolQuizTheme` компилируется; functional тесты цветов — в `androidTest` (optional для phase-02, required для AC 30 detekt/ktlint).

## Validation

```bash
./gradlew :android:core:designsystem:compileDebugKotlin --no-configuration-cache
./gradlew :android:core:designsystem:lint --no-configuration-cache
```

## Handoff Notes

- Phase-03 зависит от phase-02 (импортирует `SchoolQuizTheme` в `DesignCatalogScreen`)
- Phase-05 зависит от phase-02 (`AppShellScreen` использует `SchoolQuizTheme` wrapper — хотя wrapper вызывается в MainActivity, Phase-07)
