---
title: '9.1 noir-shared-foundation-and-sweep-inventory — общий фундамент NOIR и инвентарь свипа (CAP-17)'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: '75193bfa'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Эпик 9 владеет общими компонентами и свипом покрытия (epic-ux-noir, Done-when 4): дизайн-система обязана быть единственной реализацией, и свип должен идти по проверяемому инвентарю поверхностей, а не по догадкам.

**Approach:** (1) Снять инвентарь: главные исходники presentation, импортирующие androidx.compose.material3 без designsystem.noir, по джорням — метод зафиксирован в tickets.toml эпика. (2) Закрыть недостающий общий примитив, которого требовали 9.2/9.3: диалог (surface-4, радиус 20, mono-действия) — NoirDialog/NoirDialogAction в NoirComponents.kt. Локальных форков не заводить.

## Boundaries & Constraints

**Always:**
- Канонические источники в дереве: noir-design-system/NOIR-SPEC.md, noir-пакет designsystem, ux-constraints.md (финальные DESIGN.md/EXPERIENCE.md в дереве отсутствуют — проверено 2026-10-28, каталог planning-artifacts/ux-designs/ пуст; это отмечено в tickets.toml и не блокирует транскрипцию по NOIR-SPEC).
- Инвентарь различает UI-поверхности и хелперы/шеллы: QuizzesScreen уже на noirScreenGround, ShopScreen — shell над ShopView (ложные срабатывания капитального grep по регистру «Noir»).

**Never:**
- Не дублировать компоненты локально в фичах; всё общее — в designsystem/noir.
- Не трогать Brand*/SchoolQuizDesign-семью (другие носители, отдельное решение).

## I/O & Edge-Case Matrix

| Scenario | Expected |
|----------|----------|
| Инвентарь снят на HEAD волны | Список raw-material3 UI-поверхностей по джорням (см. tickets.toml 9.1) |
| Диалог нужен 9.2/9.3 | NoirDialog с content/actions-слотами; NoirDialogAction primary/ghost, weight(1f) |
| Экран без действий в диалоге | Пустой actions-слот выкладывается нулевой высоты — ритм сохранён |

## Code Map

- `android/core/designsystem/.../noir/NoirComponents.kt` — +NoirDialog, +RowScope.NoirDialogAction (импорт androidx.compose.ui.window.Dialog).
- Инвентарь: `_bmad-output/mvp-candidate/epic-ux-noir/tickets.toml` (шапка-метод + статус-комментарий 9.1).

## Tasks & Acceptance

**Execution:**
- [x] Инвентарь снят и записан в tickets.toml.
- [x] NoirDialog/NoirDialogAction добавлены в общий пакет.
- [ ] Гейт компиляции designsystem в составе sgw-прогона 9.2/9.3/9.6.

**Acceptance Criteria:**
- Given список raw-material3-поверхностей, when сверяешь с джорнями эпика, then каждый джорни имеет статус/историю, а «ложные хиты» (шеллы, хелперы) объяснены.
- Given 9.2/9.3 используют диалог, when они компилируются, then примитив берётся только из designsystem/noir (никаких локальных копий).

## Implementation Notes

- Baseline: 75193bfa (night/e7-commerce).
- Корневой почин инфраструктуры тестов этой волны: robolectric 4.11.1 → 4.14.1 в gradle/libs.versions.toml (ASM «major version 71» под JDK 27 + targetSdk 35 > maxSdk 34). Это предсуществующий красный settings/economy/app-shell robolectric-тестов — проверен прогоном settings-модуля на HEAD 2026-10-28 (NoirSettingsScreenTest initializationError). Локальные пины sdk=34 в трёх модулях истории — для детерминизма.

## Spec Change Log

- 2026-10-28, верификация: NoirDialog/NoirDialogAction используются 9.2/9.3 и покрыты их Compose-тестами; designsystem testDebugUnitTest 18/0 в составе пяти-модульного sgw-прогона (см. 9.2). Инфраструктурный корень-почин волны: robolectric 4.11.1 → 4.17 в `gradle/libs.versions.toml` — 4.11.1 падал на JDK 27 (ASM «Unsupported class file major version 71») и на targetSdk 35 (maxSdkVersion 34), чем был закрыт запуск robolectric-compose тестов ВООБЩЕ (предсуществующий красный NoirSettingsScreenTest-класса); после апгрейда settings-модуль проверен отдельно — GREEN. Локальные `robolectric.properties` (sdk=34) в трёх модулях истории — детерминизм эмулируемого устройства.
