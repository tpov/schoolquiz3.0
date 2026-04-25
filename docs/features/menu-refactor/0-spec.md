---
date: 2026-04-19
feature: menu-refactor
type: new-feature
commit: 7c52c200
---

# Feature Specification: Menu Refactor (Master)

Master-spec объединяет четыре связанные sub-feature, которые вместе рефакторят навигационное меню (drawer), модель квалификаций и каталоги:

| Sub-spec | Описание | Зависит от |
|---|---|---|
| [`0-spec-qualification-levels.md`](./0-spec-qualification-levels.md) | Вынести пороги квалификаций (100/200/300) как отдельную доменную сущность `QualificationLevel` в `shared/feature/qualification/domain/` | — |
| [`0-spec-dev-mode.md`](./0-spec-dev-mode.md) | Dev Mode: 10 тапов на версию в drawer → локально `developer = LEVEL_1 points` + superqualification override в `isVisible`. DesignCatalog + SyncNow в release при dev mode | qualification-levels |
| [`0-spec-home-quests.md`](./0-spec-home-quests.md) | Rename `DrawerSection.LocalSection.MyCourses → HomeQuests` + `LocalConfig.MyCoursesRoot → HomeQuestsRoot`, поменять порядок: `HomeQuests, MyQuests, Settings` | — |
| [`0-spec-catalog-foundation.md`](./0-spec-catalog-foundation.md) | Catalog foundation: плоская сущность каталогов (`shared/core/catalog/`), Firebase integration, `DrawerFooterAction.SyncNow`, Quest.catalogId delta, UI компоненты (Spinner + Grid) в designsystem | qualification-levels + dev-mode (Footer Contract) |

## Source

- Description: пользовательский запрос
  > "что делаем дальше? 1. сделать режим разработчика, 10 раз по нажатию на версию приложения - становится 100 очков квалификации разработчика. 2. начал с экрана мои квесты, посмотри на легаси там есть разные каталоги, курсы, опросы и т д, есть что-то об этом?"
  - Дополнения в диалоге:
    > "устанавливается поле разработчика на 100, а там уже все функции которые к нему привязаны они сами разблокируются, на данный момент это пункты в меню"
    > "каталог, каталоги будут встречаться на многих экранах, на данном этапе в пункте домашние квесты и мои квесты, оба пункта в закладке локально. курсы не пункт меню, убрать"
    > "режим разработчика временно и только локально повышает очки разработчика"
    > "он сам отключется при следующей синхронизации"
    > "добавь не (и разработчик 100) а (или разработчик 100)"
    > "очки квалификации вынеси отдельно, например разработчик 1 уровня = 100, и так для каждой квалификации, типа сделай 3 уровня"

- Type: new-feature (для `qualification-levels` и `dev-mode`) + enhancement (для `home-quests` rename)

## Execution order

Pipeline для трёх sub-spec:

```
1. qualification-levels  (fundamental — добавляет domain enum)
         ↓
2. dev-mode              (использует QualificationLevel.LEVEL_1.points)
         ↓
3. home-quests           (independent)   ← может идти параллельно с 1 и 2
```

Обсуждавшиеся альтернативы:
- **Одна большая фича** — отвергнуто: три независимых scope с разными invariants impact
- **Три отдельных feature directories** — отвергнуто пользователем: хочет один master spec + sub-specs

## Scope

### In Scope (across all sub-specs)

- Domain enum `QualificationLevel { LEVEL_1(100), LEVEL_2(200), LEVEL_3(300) }` в `shared/feature/qualification/domain/`
- Замена magic numbers `100` в `DrawerSection.*.requiredRoles` на `QualificationLevel.LEVEL_1.points`
- Dev Mode toggle через 10-tap на `v$versionName` в `DrawerFooter`
- Локальная запись `UserQualifications.developer = QualificationLevel.LEVEL_1.points` в локальную БД без отправки на сервер
- Superqualification: `DEVELOPER >= LEVEL_1.points` удовлетворяет любые `requiredRoles` (OR-bypass в `isVisible`)
- `visibleFooterActions(isDebugBuild, stats)` учитывает DEVELOPER>=LEVEL_1 → DesignCatalog видим в release
- Rename `DrawerSection.LocalSection.MyCourses → HomeQuests` + label "Мои курсы" → "Домашние квесты"
- Rename `LocalConfig.MyCoursesRoot → HomeQuestsRoot` + navigation targets
- Порядок `visibleSections(Tab.LOCAL, ...)`: `HomeQuests, MyQuests, Settings`
- Обновление ADR-0006 (добавить QualificationLevel + superqualification rule + local override rule)
- Обновление `docs/invariants.md` (добавить invariant про superqualification и про local-only qualification override)

### Explicitly Out of Scope

- **Каталог как сущность** — "каталог, каталоги будут встречаться на многих экранах, я думаю просто сделать спиннер, но это не относится к этой задаче" — [USER DECIDED]
- **Семантика LEVEL_2/LEVEL_3** — "Не решать сейчас, определить только пороги" — [USER DECIDED]. LEVEL_2/3 определены в enum без привязки к конкретным функциям
- **Серверный конфиг порогов** — "одинаковые 100/200/300 для всех 6" — [USER DECIDED]. Runtime override с сервера — future work
- **Перенос legacy dev-пунктов меню** (MENU_CHAT_BANNED, MENU_USERS, MENU_NEWS, MENU_FRIEND, MENU_REPORT, MENU_CONTACT) — "не раздувает фичу" — отдельные spec когда эти фичи будут переноситься
- **UI наполнение HomeQuests/MyQuests root screens** — пока placeholder, как сейчас у MyCoursesRoot
- **Отключение Dev Mode через UI** — "никак, 100 очков и есть дев режим... он сам отключется при следующей синхронизации" — [USER DECIDED]

## User Decisions (across sub-specs)

| # | Question | Answer | Impact |
|---|----------|--------|--------|
| 1 | Что значит "режим разработчика"? | Локальный dev-mode (НЕ серверная квалификация) | Dev mode spec без backend-работ |
| 2 | Как повышается developer локально? | "просто локально ставишь 100" — запись в локальную БД | Локальный write без server sync |
| 3 | Как отключается? | "никак, сам отключится при синхронизации" | Нет UI toggle; reset через server sync |
| 4 | Что разблокирует 100 очков разработчика? | "обновится UI, все пункты меню. разработчик = и тестировщик и т д. ИЛИ разработчик 100" | Superqualification: DEVELOPER>=100 bypasses всё |
| 5 | "Мои квесты" в меню — что делать? | `MyCourses → HomeQuests`, порядок `HomeQuests, MyQuests, Settings` | Rename + reorder |
| 6 | Каталог = отдельный пункт? | "не относится к этой задаче, это будет спиннер внутри многих экранов" | Out of scope |
| 7 | "Курсы" = пункт меню? | "курсы не пункт меню, убрать" | Удалить MyCourses section |
| 8 | Очки квалификации — где хранить пороги? | "вынеси отдельно, 3 уровня" | Новая sub-spec `qualification-levels` |
| 9 | Пороги одинаковые для всех квалификаций? | 100/200/300 для всех 6 | Simple enum |
| 10 | Reset timeout между тапами? | 500 мс (быстрый) | Tight tap pattern |
| 11 | Feedback до 10-го тапа? | Только на 10-м (secret mode) | Нет progress indication |
| 12 | Если уже developer>=100? | 10-tap no-op + toast "Уже в режиме разработчика" | Domain rule |
| 13 | DesignCatalog в release при dev mode? | Да: `isDebugBuild || developer>=LEVEL_1.points` | Extension of visibleFooterActions |
| 14 | ADR-0006 расширение? | Да, фиксируем superqualification + local override | Update ADR-0006 + invariants.md |
| 15 | `QualificationLevel` location? [ADDED IN RESEARCH 2026-04-20] | `shared/core/foundation/` (не `shared/feature/qualification/domain/`) | Avoid cross-feature import app-shell→qualification. Walking Skeleton переезжает в core/foundation |
| 16 | Dev mode model — overlay или прямая запись? [ADDED IN RESEARCH 2026-04-20] | Прямая запись `developer=100` в local UserStats Room. Без отдельной `LocalDeveloperOverride` entity. | **Revert codex fix #2**. Simpler model. Требует переписать sub-spec-dev-mode и Walking Skeleton dev_mode package |
| 17 | Dev mode persistence между рестартами? [ADDED IN RESEARCH 2026-04-20] | Да, `developer=100` сохраняется в Room (как все quals). Sync сбросит на server value. | Room persistence для UserStats |
| 18 | Trigger SyncNow button (не navigation, side-effect)? [ADDED IN RESEARCH 2026-04-20] | `RootComponent.onSyncNow()` метод + `RootEvent.SyncStarted` event для Snackbar | Симметрично existing `onActiveTabRetap` pattern |
| 19 | Deactivation event (нет `refreshProfile()` в новом app)? [ADDED IN RESEARCH 2026-04-20] | Создать SyncWorker + `refreshProfile()` инфраструктуру | Полноценный sync pipeline как в legacy |
| 20 | Sync periodic frequency? [ADDED IN RESEARCH 2026-04-20] | Legacy values 1/2/3/4/7/14/30 дней (user-configurable). MVP default = 1 день | Settings screen для выбора — follow-up фича |
| 21 | Quest placeholder location (TEMP в catalog vs quiz/domain)? [ADDED IN RESEARCH 2026-04-20] | Оставить в `shared/core/catalog/domain/` как TEMPORARY до quiz-lifecycle фичи | Минимальный scope, migration в quiz-feature |
| 22 | Image loader choice? [ADDED IN RESEARCH 2026-04-20] | Coil 3.4.0 (`io.coil-kt.coil3`) | Recommended для Compose KMP 2026 |

## Cross-cutting Invariants

Эти инварианты применимы ко всем трём sub-spec:

### New invariant: Superqualification DEVELOPER (Bug #13)
- `DEVELOPER >= QualificationLevel.LEVEL_1.points` — суперквалификация. Любые `DrawerSection.requiredRoles` считаются удовлетворёнными
- Реализация в `shared/.../domain/logic/Visibility.kt::isVisible` + `visibleFooterActions`
- Owner: architect-reviewer (grep/test check), backend-dev
- Added to `docs/invariants.md`

### New invariant: Local-only qualification override (Bug #14) — [UPDATED IN RESEARCH 2026-04-20: revert codex fix #2]
- `UserStats.qualification.developer` в **локальной** Room таблице может временно иметь value 100 (записано клиентом для Dev Mode). НЕ отправляется на сервер
- **Нет отдельной `LocalDeveloperOverride` entity** — revert codex fix #2. Модель проще: клиент пишет напрямую в local UserStats cache; sync перезаписывает со server state
- При получении серверного значения через sync — локальная БД перезаписывается → dev mode "сам отключится"
- Это **узкоспецифическое расширение** для DEVELOPER поля. Для остальных полей (tester/moderator/...) по-прежнему работает ADR-0006 инвариант "клиент не пишет"
- Добавляется как explicit exception в ADR-0006
- Owner: architect-reviewer, backend-dev, domain-designer

## ADR Updates Required

### ADR-0006 (roles-and-qualifications)
Добавить секцию:

> **Quality Level Thresholds**
> Пороги получения квалификаций определены в `enum class QualificationLevel`:
> - `LEVEL_1 = 100` (базовый)
> - `LEVEL_2 = 200` (средний — семантика TBD)
> - `LEVEL_3 = 300` (высокий — семантика TBD)
>
> **Wording clarification (Codex fix #10)**: предыдущий черновик ADR-0006 упоминал `value class QualificationLevel` как возможный вариант. Финальное решение: `enum class` (closed set of 3 tiers), так как это fit для fixed thresholds. Для одиночной валидации числовой точки — отдельный `value class QualificationPoints` может быть добавлен позже.
>
> **Superqualification DEVELOPER**
> `effectiveDeveloperLevel >= LEVEL_1.points` — суперквалификация; юзер с этим уровнем автоматически удовлетворяет любые `requiredRoles` для любой фичи. Реализация в `shared/feature/app-shell/domain/logic/Visibility.kt::isVisible` через OR-bypass перед `requiredRoles.all { }`.
>
> **Local-only DEVELOPER override (Dev Mode)** — [UPDATED IN RESEARCH 2026-04-20: revert codex fix #2, simpler model]
> Dev Mode (см. `menu-refactor/0-spec-dev-mode.md`) — **прямая запись** `developer=100` в local Room copy `UserStats` (`Qualification.developer` поле). Без отдельной overlay entity:
> - Local Room cache хранит копию `UserStats` целиком (включая все 6 `qualification.*` полей)
> - 10-tap → `ActivateDevModeUseCase` вызывает `UserStatsRepository.setDeveloper(100)` — пишет в local Room
> - Sync pipeline (refreshProfile) → читает с Firestore → перезаписывает local Room со server values
> - При этом `developer=100` (set client-side) будет перезатёрто server value (обычно 0) → dev mode "сам отключится"
> - Исключение от ADR-0006 "клиент не пишет в profile": **только** поле `UserStats.qualification.developer` в **local cache** может временно иметь client-written value. Все остальные поля — строго server-synced
> - Ни одно другое поле (tester/moderator/translator/sponsor/admin) не подвержено такому паттерну
> - Sync интервал (periodic) — configurable, default 1 день (legacy values 1/2/3/4/7/14/30)

### ADR-0004 (sync-contract) — related [UPDATED IN RESEARCH 2026-04-20]

Revert codex fix #2. Новый текст:

> `UserStats.qualification.developer` поле в local Room cache — **единственное** поле, которому разрешено иметь client-written value (для Dev Mode). Sync pipeline игнорирует этот fact: при `refreshProfile()` читает с Firestore и полностью перезаписывает local Row `UserStatsEntity`. Client-written `developer=100` — временный артефакт, стирается при следующей успешной sync.
>
> Все остальные `qualification.*` поля (tester/moderator/translator/sponsor/admin) — client never writes. Invariant "local cache = last successful server fetch + optional dev mode developer override" is enforced by test (`SyncWriteBoundaryTest`).

### Shared Quest delta contract (Codex fix #9)

`catalog-foundation/0-spec.md` добавляет non-null `Quest.catalogId: CatalogId` поле + `CreateQuestUseCase` с validation. Это **owned** catalog-foundation spec (не menu-refactor). Остальные Quest concerns (QuestType, QuestPhase per ADR-0005) — отдельные quiz-lifecycle фичи.

### Shared Footer Contract (Codex fix #7)

`DrawerFooterAction` sealed set и `visibleFooterActions` result order — **owned** by `menu-refactor/0-spec-dev-mode.md` раздел "Footer Contract". catalog-foundation расширяет sealed set (добавляет `SyncNow`), но visibility rules и ordering — в dev-mode spec.

### ADR-0008 (navigation) — minor update
Обновить mapping `LocalConfig.MyCoursesRoot → HomeQuestsRoot`.

## Constraints (from PROJECT_STRUCTURE.md)

- Domain типы живут в `shared/feature/<name>/domain/src/commonMain/kotlin/`
- Presentation (Compose) — в `android/feature/<name>/presentation/`
- DI — Koin, per-feature модули (ADR-0009)
- KMP: androidTarget + jvm (можно добавить iOS позже)

## Acceptance Criteria (summary — detailed в sub-specs)

Cross-cutting ACs:

1. [ ] GIVEN новый юзер (developer=0) WHEN тапает 10 раз по v$versionName в drawer footer THEN локальное значение `UserQualifications.developer = 100` + Snackbar + UI обновляется (все пункты меню с requiredRoles видимы)
2. [ ] GIVEN юзер developer=100 (local, после dev mode) WHEN происходит sync с сервером, возвращающим developer=0 THEN локальное значение = 0 + UI возвращается к изначальному
3. [ ] GIVEN юзер developer=200 (server — реальный разработчик) WHEN тапает 10 раз по версии THEN no-op + toast "Уже в режиме разработчика". Локальное developer остаётся 200
4. [ ] GIVEN release build, developer<100 WHEN DrawerFooter рендерится THEN DesignCatalog НЕ виден
5. [ ] GIVEN release build, developer=100 (local dev mode) WHEN DrawerFooter рендерится THEN DesignCatalog виден
6. [ ] GIVEN Tab.LOCAL WHEN drawer открыт THEN пункты в порядке: `HomeQuests, MyQuests, Settings` (displayName "Домашние квесты", "Мои квесты", "Настройки")
7. [ ] GIVEN любой места в коде, где был magic number `100` для quality threshold WHEN читаем код THEN используется `QualificationLevel.LEVEL_1.points` вместо `100`

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | Новый `QualificationLevel` enum — чистый Kotlin без Android/SDK | preserve |
| 2. Activity/Fragment calls only ViewModel | Dev mode счётчик tap живёт в ViewModel/Composable state (не Activity) | preserve |
| 3. No bidirectional coupling | `app-shell` будет depend от `qualification:domain` (был бы + stays — one-way) | preserve |
| 4. onDestroy is not for business cleanup | Нет изменений в Activity lifecycle | preserve |
| 5. DI exclusive binding | Если добавим `DevModeRepository` — использовать один подход | preserve (следовать ADR-0009 Koin) |
| 6. Walking Skeleton ownership | Создадим domain в `qualification:domain` (если не существует) + `app-shell:domain` (расширение). НЕ перезаписывать существующие файлы | preserve |
| 7. Scaffold file ownership | Изменений в build.gradle.kts/libs.versions.toml/AndroidManifest — только через backend-dev | preserve |
| NEW. Superqualification DEVELOPER | См. "New invariant" выше | add |
| NEW. Local-only qualification override | См. "New invariant" выше | add |

## Sub-spec Delegation Summary

Детальные Requirements / Acceptance Criteria / State Matrix / Domain Contract — в файлах:
- [`0-spec-qualification-levels.md`](./0-spec-qualification-levels.md)
- [`0-spec-dev-mode.md`](./0-spec-dev-mode.md)
- [`0-spec-home-quests.md`](./0-spec-home-quests.md)

Master-spec — index + cross-cutting decisions + ADR updates. Sub-spec-ы self-contained по своим deliverables.
