# ADR-0006: Роли и квалификации

## Status
Accepted — 2026-04-16

## Context

В legacy у юзера был набор числовых уровней по разным «квалификациям»: TESTER, MODERATOR, TRANSLATOR, SPONSOR, ADMIN, DEVELOPER. Все они — поля типа `Int` в `ProfileEntity` и `Qualification` (например, `sponsor`, `tester`, `translater`, `moderator`, `admin`, `developer`). Базовый порог получения квалификации — 100 (константы `LVL_TRANSLATOR_1_LVL = 100` и т.д.). Уровни защищены на сервере (Firebase Functions) от изменения клиентом.

Отдельная ось — **общий игровой уровень юзера** (`EDUCATION`, `BEGINNER`, `PLAYER`, ..., `LEGEND`), вычисляемый по `pointsSkill`. Это не квалификация, а совокупная метрика.

Ранее в обсуждении возникло предложение ввести «SystemRole» отдельно от «GameQualification» (например, DEVELOPER как системная роль). Это предложение **отклонено** — в legacy уже есть единая числовая модель, и разработчик в ней — просто квалификация `DEVELOPER ≥ 100`.

## Decision

### Единая модель квалификаций

Квалификации — **числовые уровни**, не enum/bool. У юзера есть набор значений по каждой из шести квалификаций:

```kotlin
data class UserQualifications(
    val tester: Int = 0,
    val moderator: Int = 0,
    val translator: Int = 0,
    val sponsor: Int = 0,
    val admin: Int = 0,
    val developer: Int = 0,
)
```

Порог «квалификация получена» — `level ≥ 100`. Дальше уровни растут по конфигурации (200, 300 ...) и могут разблокировать дополнительные возможности.

Выражение ролей через enum возможно для маппинга и доступа к логике:

```kotlin
enum class QualificationType {
    USER,        // базовая роль, уровень = f(pointsSkill), отдельно от UserQualifications
    TESTER,
    MODERATOR,
    TRANSLATOR,
    SPONSOR,
    ADMIN,
    DEVELOPER,
}
```

`USER` — **не** поле в `UserQualifications`. Это базовая роль, которая у каждого юзера по умолчанию. Её «уровень» вычисляется по `pointsSkill` и маппится в игровой уровень (см. ниже).

### Игровой уровень игрока (отдельная ось)

Общий игровой уровень — **не квалификация**, а совокупная метрика активности:

```kotlin
enum class PlayerLevel {
    EDUCATION,  // начальный, 0+
    BEGINNER,   // 2000+ pointsSkill
    PLAYER,     // 100000+
    // ... дальше по конфигурации сервера
    LEGEND,     // 1000000+
}
```

Значения порогов — серверный конфиг. Клиент вычисляет локально на основе последних известных `pointsSkill`. Живёт как свойство профиля (`profile:domain`), **не** в `qualification:domain`.

### Правила присвоения квалификаций

| Квалификация | Как получается |
|---|---|
| `TESTER` | автоматом через квесты-собесы на тестера (CompletionEffect = OfferQualification(TESTER)) |
| `TRANSLATOR` | автоматом через квесты-собесы на переводчика + повышения от более опытных переводчиков (см. translation flow в ADR-0005) |
| `MODERATOR` | автоматом через квесты-собесы на модератора |
| `SPONSOR` | начисляется по экономическим действиям (донат / покупки) — связь с `economy:domain` |
| `ADMIN` | **вручную** админом приложения |
| `DEVELOPER` | **вручную** админом приложения |

«Вручную админом» = через админ-панель, которая защищена серверными правилами (доступна только юзерам с `ADMIN ≥ 100`). Клиент никогда не пишет эти поля напрямую.

### Серверная защита (наследуется из legacy)

**Ни одно поле `UserQualifications` не может быть записано клиентом напрямую.** Серверный handler (`server/functions`, `server/workers/rewards`) — единственный источник правды:

- Запрос на повышение квалификации = event от клиента с `type + reason`.
- Сервер проверяет право (юзер прошёл квест-собес? юзер имеет квалификацию, необходимую для повышения другого юзера? есть ли `ADMIN ≥ 100` у инициатора?).
- Сервер пишет в БД, клиент читает новое значение через sync.

### Функции, разблокируемые квалификациями

Это описывается как **требования на фичи** в `android:core:userguide` / конкретных presentation-модулях. Пример:

- Доступ к ревью переводов → требует `TRANSLATOR ≥ 100` (или вышестоящую квалификацию).
- Доступ к модерации контента → требует `MODERATOR ≥ 100`.
- Доступ к админ-панели → требует `ADMIN ≥ 100`.
- Получение аналитики о прохождениях (notifyDevs в CompletionEffect) → требует `DEVELOPER ≥ 100`.

Проверка прав — функция в `qualification:domain` (например, `canAccess(user, Feature.REVIEW_TRANSLATIONS): Boolean`). Эта функция единственное место, где хардкодятся пороги; UI просто спрашивает её.

## Consequences

### Плюсы
- **Единая модель** без искусственного разделения «system vs game». Одна таблица, одна серверная защита, одни правила sync.
- **Совместимость с legacy БД** — поля `UserQualifications` мэпятся 1:1 в поля legacy `ProfileEntity`. Миграция данных тривиальна.
- **Расширяемость по уровням** — новый порог (например, `TRANSLATOR ≥ 500 → может оценивать переводчиков 400-го уровня`) задаётся без правки типов.
- **Прозрачность прав:** все проверки доступа в одном модуле (`qualification:domain`), UI не размазан if'ами.

### Минусы
- Int-поля вместо sealed/enum — теряется типобезопасность (можно написать `user.admin = -5`). Митигация: value class `QualificationLevel` с валидацией в `init {}`.
- 6 Int-полей в одной data class'е — при добавлении новой квалификации приходится трогать ~4 места (data class, enum, сервер, sync). Это осознанный долг.

### Правила
1. Клиент не пишет поля `UserQualifications` напрямую. Всегда через серверный запрос.
2. Проверка доступа к фичам — только через функцию в `qualification:domain`, не через прямое чтение полей.
3. `PlayerLevel` (игровой уровень игрока) — не квалификация. Живёт в `profile:domain`, вычисляется из `pointsSkill`.
4. Добавление новой квалификации — через правку этого ADR + enum + data class + серверный endpoint.

## Mapping из legacy

| Legacy (`ProfileEntity`) | Новый эквивалент |
|---|---|
| `sponsor: Int` | `UserQualifications.sponsor` |
| `tester: Int` | `UserQualifications.tester` |
| `translater: Int` (sic) | `UserQualifications.translator` |
| `moderator: Int` | `UserQualifications.moderator` |
| `admin: Int` | `UserQualifications.admin` |
| `developer: Int` | `UserQualifications.developer` |
| `LVL_TRANSLATOR_1_LVL = 100` | порог `TRANSLATOR ≥ 100` |
| `pointsSkill` + `EDUCATION/BEGINNER/...` enum | `PlayerLevel` в `profile:domain` |

## Notes

Модели `UserQualifications` и `QualificationType` живут в `shared/feature/qualification/domain/src/commonMain/kotlin/`. Функция проверки доступа — там же. Серверная часть (валидация запросов на повышение квалификации) — в `server/functions` и `server/workers/rewards`. Связь с квестами-собесами — через `CompletionEffect.OfferQualification` (см. ADR-0005).
