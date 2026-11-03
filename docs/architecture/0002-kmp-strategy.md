# ADR-0002: KMP-стратегия

## Status
Accepted — 2026-04-16

## Context

Долгосрочный план SchoolQuiz — выход на iOS и, потенциально, desktop. В legacy-версии проект был Android-only, и при попытке добавить iOS пришлось бы переписывать весь domain+data слой на Swift.

Альтернативы, которые рассматривались:

1. **Kotlin/JVM без KMP.** Самый простой путь, но переиспользование логики на iOS невозможно — только через KMP или переписывание на Swift.
2. **KMP сейчас, полный набор таргетов (Android, JVM, iOS, JS, native).** Максимальная гибкость, но ни одного iOS/desktop-клиента ещё нет, и каждый лишний таргет тормозит сборку и усложняет конфиг.
3. **KMP-каркас с минимальным набором таргетов (Android + JVM), с возможностью быстро добавить iOS.** Компромисс.

## Decision

Выбран вариант 3: **все модули `shared/*` используют `org.jetbrains.kotlin.multiplatform`** с таргетами `androidTarget()` и `jvm()`. iOS и остальные таргеты не включены **до** появления конкретного iOS-клиента.

Причины именно такой пары таргетов сейчас:
- `androidTarget()` обязателен для использования из `android/*` и `apps/android-next`.
- `jvm()` обязателен для использования из `server/*` и для запуска unit-тестов без Android-эмулятора.
- `iosArm64 + iosSimulatorArm64 + iosX64` добавляются **одной строкой** в `KmpLibraryConventionPlugin` когда появится iOS-клиент.

Source set'ы используются по canonical-схеме:
- `commonMain` — вся платформенно-независимая логика
- `androidMain` — Android-specific (`android.content.Context`, Room, и т.п.)
- `jvmMain` — server-specific (только JVM-зависимости)
- `commonTest` / `androidUnitTest` / `jvmTest` — тесты

**Запрет:** в `commonMain` нельзя добавлять зависимости, которые привязаны к одной платформе (например, AndroidX). Если такая зависимость появилась — она живёт в `androidMain` и абстрагируется через `expect`/`actual`.

## Consequences

### Плюсы
- Добавление iOS — правка одного convention plugin, не всех 27 KMP-модулей.
- Server-модули из `server/*` могут напрямую зависеть от `shared/core/model` и `shared/feature/quiz/domain` — единый источник правды для API.
- Unit-тесты domain-логики запускаются на JVM, без Android-эмулятора.

### Минусы
- **KMP сейчас «переусложнён».** Для Android + JVM без iOS достаточно было бы двух обычных Kotlin-модулей. Это осознанный долг, который окупается при появлении первого iOS-клиента.
- **Время сборки выше**, чем у чистого `kotlin-android` модуля — KMP Gradle plugin тяжелее.
- **Некоторые библиотеки Android-only** (Room, AndroidX) не переносятся в `commonMain`. Для кросс-платформенного хранения придётся использовать `SQLDelight` или писать `expect`/`actual`-адаптер.

### Правила
1. В `commonMain` только Kotlin-stdlib, `kotlinx-coroutines`, `kotlinx-serialization`, `kotlinx-datetime` и аналогичные multiplatform-библиотеки.
2. Room и SQLDelight — выбор для persistence откладывается до реализации `shared/core/persistence` (отдельный ADR).
3. Все `shared/*` модули — KMP. Если фича нужна только на Android, она живёт в `android/feature/` или `platform/`.
4. Добавление `iosXxx()` таргета — только через правку `KmpLibraryConventionPlugin.kt` + обновление этого ADR.

## Notes

Текущий `KmpLibraryConventionPlugin` в `buildSrc/src/main/kotlin/KmpLibraryConventionPlugin.kt` задаёт `androidTarget() + jvm() + jvmToolchain(17)`. При добавлении iOS — добавить туда `listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { ... }` и создать новый source set `iosMain`.
