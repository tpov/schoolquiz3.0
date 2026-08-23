---
date: 2026-07-26
feature: settings-app-version-footer
type: enhancement
commit: 9fa96700
---

# Feature Specification: Settings App Version Footer

## Source

- Description: «Показать версию приложения (versionName + versionCode) внизу экрана настроек, мелким серым текстом по центру.»
- Intake / triage: новая работа над существующим экраном настроек; это не баг-репорт на готовую фичу.
- Type: `enhancement` — добавляем локальный UI-polish к существующему `DesignSettingsScreen`.
- Pipeline tier: `Light` — reason: маленькое app-shell/settings UI wiring изменение, без новых public domain contracts, DI, storage, API, миграций или business rules.
- Server analysis: N/A — задача явно client-only: «чисто UI, без бизнес-логики, без сети и хранилища».
- Product dialogue: SCH-2 явно разрешает опираться на ТЗ первого прогона и не переспрашивать решения, уже зафиксированные здесь.

## Requirements

### Functional Requirements

1. На экране настроек у нижней границы видимой области должен отображаться текст версии приложения, состоящий из `versionName` и `versionCode` — [USER DECIDED] основание: тело тикета SCH-1/SCH-2.
2. Текст версии должен быть расположен по центру горизонтально, визуально выглядеть как footer экрана настроек и быть anchored/pinned к низу settings viewport, а не просто последним элементом короткого списка — [DELEGATED: wording пользователя «внизу экрана» означает видимый низ экрана; это закрывает UX-двусмысленность между pinned footer и scroll item].
3. Текст версии должен быть мелким и серым / low-emphasis — [USER DECIDED] основание: «мелким серым текстом».
4. Версия должна быть видна и в debug, и в release сборках; это обычная диагностическая информация, не dev-only действие — [DELEGATED: существующий drawer footer уже показывает версию независимо от build type, значит консистентно не прятать её на settings].
5. Формат отображения: `v<versionName> (<versionCode>)`, например `v0.1.0 (1)` — [DELEGATED: компактный привычный Android-формат, явно содержит оба требуемых поля и совпадает с текущей приставкой `v` в drawer footer].
6. Settings footer должен быть display-only: без tap, long-press, About dialog, developer-mode или другого side effect — [DELEGATED: пользователь попросил «показать версию», а не добавить новую точку входа; это сохраняет существующее поведение drawer footer отдельно].

### Non-Functional Requirements

1. Feature Domain Contract = N/A: фича не добавляет бизнес-логику, domain rules, repositories, use cases, storage, networking или Koin bindings — [USER DECIDED] основание: тело тикета SCH-1/SCH-2.
2. UI не должен читать `BuildConfig` напрямую из library module; app-layer должен передать `BuildConfig.VERSION_NAME` и `BuildConfig.VERSION_CODE` через параметры, как уже сделано для `appVersionName` в `AppShellScreen` — [DELEGATED: existing constraint in `AppShellScreen.kt` comments: library modules cannot access app BuildConfig directly].
3. Compose screen остаётся view-function: получает данные версии параметром и не резолвит Koin / repositories / platform APIs — [DELEGATED: соответствует project architecture и `docs/invariants.md` invariant 2].
4. Изменение не должно менять navigation state, drawer footer actions, About dialog, design-style selection или persistence выбранного дизайна — [DELEGATED: задача локальная и не должна расширять scope].

## Scope

### In Scope

- Добавить pinned/display-only footer с версией в `DesignSettingsScreen`.
- Протащить `versionName` и `versionCode` от app layer до settings screen.
- Обновить preview / тесты, если сигнатура `DesignSettingsScreen` меняется.
- Локальная валидация компиляцией релевантных Android модулей или canonical gate, если позволяет окружение.

### Explicitly Out of Scope

- About screen / About dialog redesign.
- Изменение drawer footer, drawer actions или developer-mode логики.
- Новые domain/data contracts, use cases, repositories, Koin bindings.
- Сеть, storage, Firebase, Room, миграции.
- Локализация формата версии через resources; строка диагностическая и не содержит пользовательского copy кроме префикса `v`.

## User Decisions

| # | Question | Answer | Impact on Design |
|---|----------|--------|-----------------|
| 1 | Что показать? | `versionName + versionCode` | Нужно передать оба значения из app BuildConfig в settings UI. |
| 2 | Где показать? | Внизу экрана настроек | Footer должен быть в settings viewport у нижней границы, а не drawer и не просто последний item короткого списка. |
| 3 | Как выглядит? | Мелкий серый текст по центру | Использовать small typography + low-emphasis color + center alignment. |
| 4 | Есть ли бизнес-логика / сеть / storage? | Нет, чисто UI | Feature Domain Contract = N/A; Walking Skeleton не нужен. |
| 5 | Цель тикета | Дымовой прогон pipeline v2 | Scope intentionally small; избегать расширения фичи. |

## Decision Ledger

| # | Risk | Decision | Rationale | Evidence | Rollback / Revisit Trigger |
|---|------|----------|-----------|----------|----------------------------|
| 1 | Low | Классифицировать как `enhancement`, не bug report | Добавляется новый footer к существующему экрану; нет описания некорректного поведения | Тикет SCH-1/SCH-2: «показать версию...» | Если product скажет, что версия уже должна была быть на settings и отсутствует regression, перевести в bug triage. |
| 2 | Low | Pipeline tier = `Light` | Маленький UI-only change без contracts/storage/API | Тикет SCH-1/SCH-2 прямо ограничивает scope; текущий `DesignSettingsScreen` — один Compose screen | Повысить tier, если research найдёт скрытую публичную API-миграцию за пределами app-shell/settings UI. |
| 3 | Low | Формат `v<versionName> (<versionCode>)` | Читаемо, компактно, содержит оба поля; совместимо с текущим drawer format `v$versionName` | `DrawerFooter.kt` уже использует префикс `v` для version label | Если нужен другой product copy, заменить formatter без изменения архитектуры. |
| 4 | Low | Передавать version fields параметрами из app layer | Android library modules не должны зависеть от app `BuildConfig`; текущий app-shell уже принимает `appVersionName` | `MainActivity.kt` передаёт `BuildConfig.VERSION_NAME`; `apps/android-next/build.gradle.kts` задаёт `versionCode`/`versionName` | Если появится shared app-info provider, заменить параметры на единый dependency. |
| 5 | Medium | Footer anchored/pinned к видимому низу settings viewport | Это наиболее буквальное прочтение «внизу экрана» и снижает риск, что на коротком списке version окажется посреди экрана | Тело тикета SCH-1/SCH-2; prior spec-review finding about pinned-vs-scroll ambiguity | Если product попросит footer как часть scroll content, заменить layout contract и AC #1. |
| 6 | Low | Settings footer display-only, без tap behavior | Запрос — показать диагностический текст; drawer footer уже владеет своим `onVersionTap`, settings не должен получать новый hidden action без явного scope | Тело тикета SCH-1/SCH-2; existing drawer footer pattern is separate UI | Если product попросит интерактивность, добавить отдельный requirement и journeys для tap/repeated tap. |
| 7 | Low | Показывать footer в debug и release | Version label — обычная build metadata, не debug-only инструмент | Drawer footer уже показывает версию как user-facing diagnostic label; тело тикета не ограничивает build type | Если product захочет скрыть в release, добавить build-type requirement и тесты. |
| 8 | Low | Не менять navigation, drawer, About dialog, design-style selection и persistence | Scope — локальный footer на settings; расширение существующих side effects не требуется | Тело тикета SCH-2: «чисто UI, без бизнес-логики»; out-of-scope секция spec | Если implementation вынужденно трогает эти области, вернуть в design/spec delta review. |
| 9 | Low | Settings Compose screen остаётся view-function без Koin/repository/platform lookups | Это сохраняет project invariant: presentation получает state/props и emits callbacks; build metadata приходит с app boundary | `docs/invariants.md` invariant 2; `.claude/PROJECT-CONTEXT.md` Compose screen constraint | Если понадобится общий app-info provider, оформить отдельный contract/design instead of resolving it in screen. |

## Assumption Ledger

| # | Assumption | Risk | Verification Plan | Expiry / Blocking Phase |
|---|------------|------|-------------------|-------------------------|
| 1 | `BuildConfig.VERSION_CODE` доступен в `apps/android-next` так же, как `VERSION_NAME` | Low | Проверить `apps/android-next/build.gradle.kts` и compile gate; app module уже имеет `buildFeatures.buildConfig = true` | Research / implementation до merge |
| 2 | Settings screen должен показывать версию дополнительно к drawer footer, не заменяя его | Low | Проверить `AppShellScreen` route `LocalConfig.SettingsRoot` и drawer footer usage; не удалять existing footer | Design / implementation |
| 3 | Текущий settings layout позволяет добавить pinned footer без нового navigation state и без перекрытия существующих design controls | Low | Research должен проверить структуру `DesignSettingsScreen` и предложить безопасную компоновку; compile/preview должны подтвердить отсутствие stale call sites | Design / implementation |

## Server-Side Context

N/A — фича не затрагивает API, серверные данные, Firebase, Room, синхронизацию или auth.

## Search Criteria for Research

Эту секцию читает `/feature-research`. Для Light-tier фичи достаточно research-lite:

1. Найти все call sites `DesignSettingsScreen` и подтвердить, где менять сигнатуру.
   - Current evidence: `AppShellScreen.kt` рендерит `DesignSettingsScreen` при `LocalConfig.SettingsRoot`.
2. Найти текущий путь передачи версии из app layer.
   - Current evidence: `MainActivity.kt` передаёт `BuildConfig.VERSION_NAME` в `AppShellScreen`.
   - Проверить наличие `BuildConfig.VERSION_CODE` в `apps/android-next`.
3. Найти текущий drawer footer version label и не сломать его.
   - Current evidence: `DrawerFooter.kt` принимает `versionName` и показывает `v$versionName`.
   - Подтвердить, что settings footer не подключается к `onVersionTap` и остаётся display-only.
4. Проверить текущую структуру `DesignSettingsScreen`.
   - Current evidence: экран построен через `LazyColumn`; research должен определить, как закрепить version footer у видимого низа settings viewport, не превращая его в последний item короткого списка.
5. Проверить тестовую/preview поверхность для settings screen.
   - Current evidence: есть `DesignSettingsScreenPreview`; production tests для settings module пока не найдены.

### Completeness check

- Grep `DesignSettingsScreen(` по `android apps shared` и обновить все call sites.
- Grep `appVersionName` / `versionName` / `versionCode` по `apps/android-next` и `android/feature/app-shell`.
- Проверить, что нигде в settings path не добавлен callback/tap handler для version footer; existing drawer `onVersionTap` остаётся только drawer behavior.
- После изменения compile must prove no stale call sites remain.

## Primary User Journeys

1. Happy path — user opens settings
   - Start: user is in app shell on Local tab or opens drawer.
   - Trigger: user selects «Настройки».
   - State changes: existing app-shell navigation selects `LocalConfig.SettingsRoot`; this feature adds no new state.
   - Expected result: settings screen shows existing design settings plus centered low-emphasis display-only footer text exactly like `v0.1.0 (1)` at the visible bottom of the settings viewport.
   - Decision: [USER DECIDED] from SCH-1/SCH-2 body for presence/placement; [DELEGATED] for exact format and display-only behavior.

2. Build metadata changes between releases
   - Start: developer updates `versionName` / `versionCode` in app Gradle config.
   - Trigger: app is rebuilt and user opens settings.
   - State changes: none.
   - Expected result: footer reflects the build's generated `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`, not hardcoded text.
   - Decision: [DELEGATED] use generated BuildConfig values passed from app layer.

3. Offline / fresh install / logout / process death
   - Start: any app state, including no network, first launch, account switch, process recreation.
   - Trigger: user opens settings.
   - State changes: none.
   - Expected result: same footer is shown because version is static build metadata and independent of user/session/network.
   - Decision: [N/A — pure local static UI].

4. Repeated tap / parallel actions
   - Start: settings screen is visible and footer is visible.
   - Trigger: user taps or long-presses the version footer, including repeated taps.
   - State changes: none.
   - Expected result: no navigation, dialog, developer-mode action, analytics side effect, storage write or state mutation is triggered by the settings footer.
   - Decision: [DELEGATED] display-only text is the narrowest interpretation of «показать версию» and avoids copying drawer-only hidden behavior into settings.

## Cross-Cutting ADR Impact

### ADR-0008 Navigation
- **Applies**: Partial.
- **Amendments needed**: none.
- **Reference**: `docs/architecture/0008-navigation.md`; existing route is `LocalConfig.SettingsRoot`.
- **Impact**: preserve existing app-shell navigation; no new destination or back behavior.

### ADR-0010 Design System
- **Applies**: Yes.
- **Amendments needed**: none.
- **Reference**: `docs/architecture/0010-designsystem.md`; existing settings UI uses `SchoolQuizTheme` / MaterialTheme.
- **Impact**: footer color/typography should use `MaterialTheme`, not hardcoded colors.

### Lesson-runner ADR-LR-03 auth cross-feature import
- **Applies**: No.
- **Amendments needed**: none.
- **Reference**: `docs/features/lesson-runner/03-decisions.md` mentions auth as cross-cutting concern for lesson runner.
- **Impact**: settings version footer does not read auth/user data.

## Feature Domain Contract

N/A — no business logic, no state machine, no domain rules, no repository contract, no walking skeleton.

## Delegated Decisions Summary

| # | Область | Решение агента | Обоснование | Risk |
|---|---------|----------------|-------------|------|
| 1 | Triage | Enhancement, not bug | Описание просит добавить UI, не исправить regression | Low |
| 2 | Pipeline | Light tier | Small app-shell/settings UI wiring, no contracts/API/storage | Low |
| 3 | UI format | `v<versionName> (<versionCode>)` | Компактно и явно содержит оба поля | Low |
| 4 | Architecture | Pass version fields from app layer | Existing `BuildConfig` boundary already follows this pattern | Low |
| 5 | UI placement | Anchor/pin footer to the visible bottom of settings viewport | Best matches «внизу экрана» and avoids mid-screen footer on short lists | Medium |
| 6 | Interactivity | Settings footer is display-only | User requested display, not a new tap target; drawer footer behavior remains separate | Low |
| 7 | Build types | Show in debug and release | Version is passive build metadata; no dev-only behavior requested | Low |
| 8 | Scope guard | Preserve navigation, drawer, About dialog and design persistence behavior | Request is local UI polish; avoiding hidden scope creep | Low |
| 9 | Presentation boundary | Compose screen receives version as parameter and does not resolve dependencies | Matches project invariant 2 and existing app-layer BuildConfig boundary | Low |

## State Matrix

N/A — фича не содержит ветвистую логику.

## Acceptance Criteria

1. [ ] GIVEN the user opens «Настройки» WHEN the settings screen is rendered THEN the visible bottom of the settings viewport contains centered display-only text with both `versionName` and `versionCode`, not merely a last row floating after a short list.
2. [ ] GIVEN `versionName = "0.1.0"` and `versionCode = 1` WHEN settings footer is rendered THEN it displays exactly `v0.1.0 (1)`.
3. [ ] GIVEN the settings screen is displayed WHEN the footer is visible THEN the text uses small typography and low-emphasis grey/on-surface color, centered horizontally.
4. [ ] GIVEN the app is offline, freshly installed, logged out, or restored after process death WHEN settings opens THEN the same build version footer is still shown.
5. [ ] GIVEN the settings footer is visible WHEN the user taps or long-presses it THEN no navigation, dialog, dev-mode action, storage write or other state mutation occurs.
6. [ ] GIVEN drawer footer exists WHEN this feature is implemented THEN existing drawer footer, drawer version tap behavior and About dialog behavior are unchanged.
7. [ ] GIVEN implementation is complete WHEN relevant compile/tests are run THEN no stale `DesignSettingsScreen` / `AppShellScreen` call sites remain.

## Invariant Check (from docs/invariants.md)

| Invariant | Impact | Decision |
|-----------|--------|----------|
| 1. Domain layer purity | No domain changes | N/A / preserve |
| 2. Presentation does not bypass domain | Settings Compose screen receives static display data via parameters; no Koin/repository/BuildConfig direct access | preserve |
| 3. No bidirectional coupling between feature modules | No new feature module dependency expected | preserve |
| 4. onDestroy is not for business cleanup | No lifecycle cleanup | N/A |
| 5. Koin binding uniqueness | No DI changes | N/A |
| 6. Walking Skeleton ownership | Feature Domain Contract = N/A | N/A |
| 7. Scaffold file ownership | App/build Gradle files should not need edits; if they do, backend-dev owns scaffold files | preserve |
| 8. Auth-scoped Flow re-subscribe | No user-specific Flow | N/A |

## Constraints (from PROJECT-CONTEXT.md / AGENTS.md)

- Android presentation uses Decompose Components; Compose screens render state and emit callbacks.
- Compose screens do not resolve Koin or repositories directly.
- Domain/data live under `shared/{core,feature}`; this feature should not add domain/data.
- Android presentation lives under `android/feature/*/presentation`.
- Koin is manual DI from `apps/android-next`; no new binding expected.
- Canonical local gate: `./gradlew ciCheck --no-configuration-cache`.
