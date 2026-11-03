# ADR-0001: Карта модулей

## Status
Accepted — 2026-04-16

## Context

SchoolQuiz 4.0 — это переписывание legacy-проекта с нуля. Legacy (`legacy/app/`) — монолитное Android-приложение (~500 Kotlin-файлов), которое смешивало бизнес-логику, Firebase-интеграцию, UI и синхронизацию. Долгосрочный план — выйти на другие операционные системы (iOS, потенциально desktop) и развернуть собственную серверную часть.

Требования к новой структуре:

1. Разделить платформенно-независимую логику (domain + data) и платформенно-зависимый UI.
2. Изолировать внешние SDK (Firebase, Telegram, Billing, Crypto) от доменного слоя, чтобы их можно было заменить без каскадных правок.
3. Держать клиент, shared-логику и сервер в одном репозитории.
4. Оставить legacy как архивную зону, чтобы не мешал и оставался референсом при миграции.
5. Иметь возможность добавить iOS/desktop-клиент позже без реорганизации модулей.

## Decision

Репозиторий разделён на 5 зон:

| Зона | Назначение | Технология |
|------|-----------|------------|
| `apps/` | Точки входа приложений (`android-next` сейчас) | Android application |
| `shared/` | Платформенно-независимая логика: `core/*` (базовые контракты) и `feature/*/{domain,data}` (фичи) | Kotlin Multiplatform (`androidTarget + jvm`) |
| `android/` | Android-only UI-слой: `core/*` (navigation, designsystem, userguide) и `feature/*/presentation` | Android library |
| `platform/` | Адаптеры к внешним SDK (Firebase, Billing, Crypto, Telegram, Android-services) | Android library |
| `server/` | Серверные JVM-модули (functions, workers, bots, admin-tools) | JVM-only Kotlin |

Продуктовая декомпозиция — 7 фич-зон: `app-shell`, `quiz`, `local/settings`, `internet/{auth,profile,social,leaderboard}`, `qualification`, `economy`. Каждая фича в `shared/feature/` имеет модули `domain` и `data`, в `android/feature/` — только `presentation`.

Legacy вынесен в `legacy/` и **не участвует в активной сборке**.

## Consequences

### Плюсы
- Android UI-модули не видят Firebase — только через адаптер в `platform/firebase`. Замена Firebase на собственный backend (в `server/`) не потребует трогать UI.
- Вся бизнес-логика в `shared/` переиспользуема на iOS/desktop без переписывания, когда появится соответствующий target в KMP-модулях (см. ADR-0002).
- Серверный код живёт в том же репо — один источник правды для API-контрактов через `shared/core/network` и `shared/core/question-schema`.
- Plotter-тест: при поиске «где логика прохождения квеста» мы смотрим только в `shared/feature/quiz/domain`, а не в 20 местах.

### Минусы и риски
- **Overhead на старте.** 54 модуля до первой реальной строки кода — накладные расходы на Gradle и когнитивная нагрузка. Митигация: convention plugins (см. `buildSrc/`), version catalog, и факт, что пустые модули компилируются мгновенно.
- **KMP на только Android + JVM сейчас.** Пока нет iOS/Native-target, KMP «переусложнён» для чистого `shared`-модуля. Осознанный долг, см. ADR-0002.
- **Промежуточные каталоги не являются Gradle-модулями.** Только leaf-модули, перечисленные в `settings.gradle.kts`, подключены. `shared/feature/quiz/` сам по себе — не модуль, а только `shared/feature/quiz/domain` и `shared/feature/quiz/data`.
- **Legacy занимает ~2 ГБ и не покидает репозиторий.** Обдуманное решение — иметь рядом референс до окончания миграции. После миграции feature-by-feature папки будут удалены.

### Правила, которые следуют из этого решения
1. `android/*` не ссылается напрямую на `platform/*` SDK-классы — только на контракты из `shared/*`.
2. `shared/*` никогда не зависит от `android/*` или `platform/*`.
3. `platform/*` модули не содержат бизнес-логики; только мэпперы и вызовы SDK.
4. `server/*` не зависит от `android/*` и `platform/*`, но может использовать `shared/core/*` (кроме android-only частей).
5. Новая фича = добавление leaf-модулей в соответствующие зоны, не расширение существующих.

## Notes

Структура подключения в Gradle — `settings.gradle.kts`, раздел `layered-scaffold:start..end`. Не менять руками в обход этих маркеров: при добавлении фичи обновляются сразу `shared/feature/X/{domain,data}` + `android/feature/X/presentation`.
