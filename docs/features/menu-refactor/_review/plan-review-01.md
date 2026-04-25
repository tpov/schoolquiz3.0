## Verdict: REJECT

## Lens 1: Sequencing findings

- [BLOCKER] Finding #1: Phase 03 deliberately breaks compile, but phase validation still requires green app build
  File: docs/features/menu-refactor/plan/phase-03/backend.md:26
  Цитата: "`Breaks exhaustive `when` в `DrawerFooter.kt` и `Labels.kt` — они обновляются в Phase 07`"
  Проблема: Phase 03 backend явно откладывает compile-fix на Phase 07, а `phase-03/overview.md:106` одновременно требует `./gradlew assembleDebug --no-configuration-cache` GREEN. Это не теоретический риск: сейчас exhaustive `when`/interface impl уже есть в `MainActivity.kt:31`, `DrawerFooter.kt:49`, `Labels.kt:116`, `DefaultRootComponent.kt:62`, поэтому фаза не может быть independently green в заявленных границах.
  Suggested fix: либо перенести breaking contract changes целиком в Phase 07, либо расширить Phase 03 на все compile-fix call sites/stubs и держать фазу зелёной до handoff.

- [BLOCKER] Finding #2: Phase 05 задаёт запрещённое и неразрешённое направление зависимостей
  File: docs/features/menu-refactor/plan/phase-05/overview.md:62
  Цитата: "`shared/core/catalog/data/build.gradle.kts` — добавить `platform:firebase` dep ... или определить interface в catalog:data и impl в firebase"
  Проблема: План оставляет архитектурный выбор открытым и одновременно допускает `shared/core/catalog:data -> platform:firebase`, хотя та же фаза декларирует, что platform dependency не должна утекать в KMP (`phase-05/overview.md:89-92`). Дополнительно backend-task 7 уже wiring’ит `FirebaseCatalogRemoteDataSource` и `FirebaseStorage` прямо внутри `shared/core/catalog/data/di/CatalogDataModule.kt` (`phase-05/backend.md:135-145`). Это ломает clean architecture и делает phase dependency graph неисполняемым без нарушения правил.
  Suggested fix: зафиксировать один вариант: `core:catalog:data` остаётся pure KMP, remote interface использует нейтральный shared type, а Firebase-specific DI/storage URL resolver живут в `platform/firebase` или app composition root.

- [HIGH] Finding #3: README ADR Coverage table рассинхронизирован с `03-decisions.md`
  File: docs/features/menu-refactor/plan/README.md:152
  Цитата: "`| ADR-HLA-06 | `CatalogRepository` в `core:catalog:domain` (не feature) | Walking Skeleton (green) |`"
  Проблема: В `03-decisions.md` `ADR-HLA-06` — это Coil 3.4.0 (`03-decisions.md:146-159`), а `ADR-L3-04` — это `CatalogDao.replaceAll()` как `@Transaction` (`03-decisions.md:306-312`). README мапит оба ID на другие решения, поэтому требуемая трассировка всех 11 ADR по фазам сейчас недостоверна.
  Suggested fix: пересобрать ADR Coverage table напрямую из `03-decisions.md` и заново привязать HLA-06 и L3-04 к реальным phase owners.

- [HIGH] Finding #4: Phase 01 не перечисляет весь 8-file Walking Skeleton delete set
  File: docs/features/menu-refactor/plan/phase-01/tests.md:24
  Цитата: "`| `...dev_mode/.../LocalDeveloperOverrideTest.kt` (если есть) | ...`" и "`| `...dev_mode/.../EffectiveDeveloperLevelTest.kt` (если есть) | ...`"
  Проблема: В phase files явно названы только 2 overlay test files, но `04-testing.md:36-38` требует удалить ещё и `FakeLocalDeveloperOverrideRepositoryTest.kt`. Значит проверка "все 8 delete files в Phase 01" не выполняется: README говорит "3 файла", а Phase 01 tasks перечисляют только два.
  Suggested fix: добавить `FakeLocalDeveloperOverrideRepositoryTest.kt` в `phase-01/overview.md` и `phase-01/tests.md` как explicit delete target, а не оставлять его скрытым за общим описанием.

- [HIGH] Finding #5: Validation commands не опираются на project-canonical build rules
  File: docs/features/menu-refactor/plan/README.md:43
  Цитата: "`| 01 | ... | complex | `./gradlew assembleDebug` GREEN + QL-01..14 + DM-01..16 green |`"
  Проблема: План системно использует bare `assembleDebug` как quality gate, но `.claude/rules/testing.md:26` прямо запрещает считать bare `assembleDebug` valid final proof, если это не зафиксировано в `PROJECT-CONTEXT.md`. `PROJECT-CONTEXT.md` в репозитории отсутствует, значит validation section сейчас не grounded in project rules.
  Suggested fix: заменить bare `assembleDebug` на конкретные app/module tasks + tests, либо сначала завести `PROJECT-CONTEXT.md` с canonical build commands и сослаться на него из README/phase files.

## Lens 2: Plan-as-ТЗ findings

- [BLOCKER] Finding #1: `catalogDomainModule` не оформлен как валидная Signature Card
  File: docs/features/menu-refactor/plan/phase-05/backend.md:154
  Цитата: "`**Файл:** (в том же или отдельном файле — `shared/core/catalog/data/di/CatalogDomainModule.kt`)`"
  Проблема: Для New File card путь оставлен неоднозначным, а обязательный блок `Edge cases` отсутствует совсем. По lens-чеклисту это blocker: у каждого New File должны быть concrete path + type + inline signature + input + behavior + edge cases + canonical reference + rationale.
  Suggested fix: выбрать один конкретный файл, добавить `Edge cases`, и синхронно внести этот new file в `phase-05/overview.md` New Files.

- [MEDIUM] Finding #2: `tests.md` уходит в test-implementation code вместо scenario-only requirements
  File: docs/features/menu-refactor/plan/phase-07/tests.md:57
  Цитата: "`val mockWorkManager = mockk<WorkManager>(relaxed = true)`"
  Проблема: Lens требует сценарии в формате `given/when/then`, а не готовые MockK/JUnit рецепты. Здесь tests plan начинает диктовать конкретную реализацию теста (`mockk`, `every`, `verify`), что делает plan менее ТЗ и более полукодом.
  Suggested fix: переписать эти фрагменты в observable-scenario form; если мок нужен, оставить это краткой fixture-note без executable snippet.

- [MEDIUM] Finding #3: Pattern Invariants не ссылаются на canonical `file:line` examples
  File: docs/features/menu-refactor/plan/phase-07/frontend.md:10
  Цитата: "`LaunchedEffect(rootComponent) — ключ `rootComponent`, не `Unit`: предотвращает дублирование collectors при recomposition`"
  Проблема: По lens’у каждый invariant должен иметь ссылку на существующий canonical pattern через `file:line`. Здесь и в других phase files invariants описаны прозой, но не anchored to real repo examples.
  Suggested fix: добавить к каждому invariant concrete example refs, например `some/Screen.kt:NN`, `some/Component.kt:NN`, чтобы implementer видел canonical source pattern.

## Summary

- 3 blockers
- 3 highs
- 2 mediums

## Recommendation

fix and re-review — grep-check на ` ```kotlin|java|groovy ` прошёл, но текущий план не является phase-safe и не дотягивает до deterministic ТЗ по catalog stack, ADR traceability и Signature Card completeness.