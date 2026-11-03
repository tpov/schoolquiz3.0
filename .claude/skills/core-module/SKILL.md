---
name: core-module
description: "Загружай когда фича затрагивает пакет core/ — для проверки существующей логики (research, duplicate scan), написания нового кода (implementation), или понимания shared infrastructure (design). Triggers: 'core module', 'shared types', 'constants', 'error handling', 'ApiEndpoints', 'safeCall', 'AppError', 'NetworkStatusProvider'."
disable-model-invocation: true
---

# Core Module Conventions

Skill для работы с пакетом `core/` — shared infrastructure layer проекта.

## Зачем существует core/

Пакет `core/` — это shared infrastructure, используемый 98+ файлами проекта. Он содержит:
- **Contracts** (interfaces, error types) — единый vocabulary для всех слоёв
- **Constants** — source of truth для API paths, DB schema names, WS channels
- **Utilities** — safeCall, retry, error mapping — переиспользуемые примитивы
- **Infrastructure** — network status, logging, permissions — cross-cutting concerns

Core организован по принципу **один concept = один файл**. Это reference-паттерн для всего проекта.

## Связь с архитектурой

- Clean architecture rules: `.claude/rules/clean-architecture.md`
- Kotlin conventions: `.claude/rules/kotlin-conventions.md`
- Domain models: `.claude/rules/domain-models.md`

Core находится между domain и data — он содержит infrastructure contracts (не бизнес-логику). Core НЕ должен зависеть от `data/*`, `presentation/*`, `ui/*` (legacy зависимости задокументированы в conventions.md — не расширять).

## Когда загружать

- **Research**: проверка дубликатов с core (Duplicate Logic Scan)
- **Design**: решение куда поместить shared логику (core vs domain vs data)
- **Implementation**: написание/изменение кода — следуй паттернам из core как reference
- **Review**: проверка корректности использования core contracts

## Как использовать

1. Загрузи `references/conventions.md` — структура, паттерны, naming, contracts
2. Перед созданием нового класса — проверь что аналога нет в conventions
3. **При написании любого кода** — организуй как в core: один concept = один файл, extensions в отдельных `*Extensions.kt`, helpers как top-level `internal fun`
4. При использовании core API — проверь сигнатуру и contract в conventions

## Gotchas

- core/ — это пакет внутри `:app`, НЕ отдельный Gradle-модуль. Нет `include(":core")` в settings.gradle.kts
- Package declaration и filesystem path для `core/` бери из `PROJECT-CONTEXT.md`. Если path и package name не совпадают — фиксируй этот mismatch только там.
- core зависит от Android framework (`Context`, `DataStore`, `ConnectivityManager`) — это НЕ pure Kotlin layer
- core зависит от `data.api`, `data.network`, `data.websocket` — нарушает clean architecture (infrastructure layer, не domain)
- 98 файлов импортируют core — любое breaking change требует широкого impact scan
