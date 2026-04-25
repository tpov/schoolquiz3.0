---
phase: 03
role: test-dev
---

# Phase 03 — Test Tasks

## Pattern Invariants

- Обновлять существующие тесты atomically с production changes — canonical pattern: `shared/feature/app-shell/domain/src/commonTest/.../VisibilityTest.kt:32` (class declaration; тесты `visibleSections`/`isVisible` обновляются вместе с `Visibility.kt` extension в Phase 03)
- `DrawerFooterActionTest.kt:18` — критически важен: существующий тест проверяет размер sealed set `assertEquals(2, DrawerFooterAction::class.sealedSubclasses.size)` — обновляется до 3 (после добавления `SyncNow`)
- Test framework: `kotlin.test` (commonTest), no Turbine (`.claude/rules/testing.md:53`); naming backtick Kotlin-style — existing pattern: `VisibilityTest.kt:32` + `RegisterTapTest.kt:21` (class declarations)
- Тест-dev НЕ модифицирует production code (rule per `.claude/rules/testing.md:68`)

---

## 1. UPDATE VisibilityTest.kt — superqualification scenarios

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../VisibilityTest.kt`
**Source:** `04-testing.md §3.2.3`, scenarios DM-17..DM-27

### Superqualification visibility matrix (DM-17..DM-23, 7 сценариев):

- DM-17: given `requiredRoles={D=100,T=100,M=100,A=100}`, `developer=100`, when `isVisible`, then `true` (superqualification bypass)
- DM-18: given `requiredRoles={D=100,T=100,M=100,A=100}`, `developer=0`, `tester=100, moderator=100, admin=100`, when `isVisible`, then `true` (all roles satisfied)
- DM-19: given `requiredRoles={D=100,T=100,M=100,A=100}`, `developer=0`, `tester=100, moderator=0, admin=100`, when `isVisible`, then `false` (partial fail)
- DM-20: given `requiredRoles={T=100}` (no D), `developer=100`, `tester=0`, when `isVisible`, then `true` (superqual bypasses even sections without D requirement)
- DM-21: given `requiredRoles={T=100}`, `developer=0`, `tester=100`, when `isVisible`, then `true`
- DM-22: given `requiredRoles={T=100}`, `developer=0`, `tester=50`, when `isVisible`, then `false`
- DM-23: given `requiredRoles={}` (empty), `developer=0`, when `isVisible`, then `true` (always visible)

### Footer action visibility matrix (DM-24..DM-27, 4 сценария):

- DM-24: given `isDebugBuild=false, developer=0`, when `visibleFooterActions`, then `[About]` only
- DM-25: given `isDebugBuild=false, developer=100`, when `visibleFooterActions`, then `[DesignCatalog, SyncNow, About]`
- DM-26: given `isDebugBuild=true, developer=0`, when `visibleFooterActions`, then `[DesignCatalog, SyncNow, About]` (debug bypass)
- DM-27: given `isDebugBuild=false, developer=100`, when `visibleFooterActions`, then strict order `[DesignCatalog, SyncNow, About]` (index check)

**Note на fakes:** тесты `VisibilityTest.kt` — чистые функции, не нужны fakes. Только `UserStats` с разными значениями qualification.

---

## 2. UPDATE DrawerFooterActionTest.kt

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../DrawerFooterActionTest.kt`

Обновить тест проверяющий количество членов sealed set:
- Старый: `assertEquals(2, DrawerFooterAction::class.sealedSubclasses.size)` или `assertEquals(2, all.size)`
- Новый: `assertEquals(3, all.size)` + проверить что `SyncNow` присутствует в списке

**Сценарий:** given `DrawerFooterAction` sealed set, when enumerate all, then size == 3 и contains DesignCatalog, SyncNow, About.

---

## 3. UPDATE DrawerFooterMapperTest.kt — new signature

**Файл:** `shared/feature/app-shell/domain/src/commonTest/.../DrawerFooterMapperTest.kt` (или presentation test)

Обновить 3+ теста с 1-arg вызовами `visibleFooterActions(isDebugBuild)` → новая signature `visibleFooterActions(isDebugBuild, stats)`.

Добавить `UserStats` fake для каждого тест-кейса (developer=0 для большинства существующих тестов).

---

## Edge Cases

| Сценарий | Priority | Why |
|----------|----------|-----|
| DM-20: superqual bypass когда D нет в requiredRoles | P0 | Spec "ИЛИ разработчик 100" — любые requiredRoles |
| DM-24: developer=0 → только [About] | P0 | Нет утечки dev tools в release без dev mode |
| Строгий порядок DM-27 | P1 | UI рендерит в порядке списка |

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :shared:feature:app-shell:domain:jvmTest --no-configuration-cache` | GREEN — DM-17..27 зелёные |
| `grep -rn "assertEquals(2" shared/feature/app-shell/domain/src/commonTest/ --include="*FooterAction*"` | 0 matches (убедиться что обновили) |
