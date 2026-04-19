# Cross-Feature Architectural Invariants

Архитектурные инварианты, применимые для **всех** фич проекта. Нарушение — blocker для любой phase.

Читается `/feature-spec` (invariant check), `/feature-research` (invariant impact analysis), `architect-reviewer` (по каждому review).

---

## 1. Domain layer purity

- **Invariant**: Файлы в `app/src/main/kotlin/<base_package>/domain/**/` не содержат Android framework типов (Context, Uri, Bundle, Intent, View, Activity, Fragment), SDK типов (LiveKit, Firebase, Retrofit, Room, Moshi), DI аннотаций (@Inject, @Provides, @Module) ни как полей, ни как параметров функций, ни как generic параметров, ни как return types.
- **Constraint**: Исключение — `android.os.Parcelable` в domain models если явно требуется для navigation.
- **Owner**: `architect-reviewer` (grep check), `domain-designer` (generation), `backend-dev` (implementation).
- **Rule source**: `.claude/rules/domain-models.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #8).

## 2. Activity/Fragment calls only ViewModel

- **Invariant**: Activity и Fragment не вызывают Repository, Provider, Manager, Service, Store, UseCase, DAO или API напрямую. Только через ViewModel (или navigation/system платформенные компоненты).
- **Constraint**: Нет DI инъекции Repository/UseCase в Activity/Fragment как `@Inject lateinit var`.
- **Owner**: `architect-reviewer` (grep check), `frontend-dev` (implementation).
- **Rule source**: `.claude/rules/use-cases.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #9).

## 3. No bidirectional coupling between feature modules

- **Invariant**: Если feature-A импортирует feature-B напрямую — feature-B **не может** импортировать feature-A напрямую. Bidirectional coupling допускается только через shared interface в `core/` или через reflection (ADR-обоснованно).
- **Constraint**: Прямой cross-feature import (А → Б) должен быть задокументирован в `docs/features/<A>/03-decisions.md` или в том же ADR что и обратная reflection-связь.
- **Owner**: `architect-reviewer` (grep check), cross-feature scanner в `/feature-research` Step 0.8.
- **Rule source**: `.claude/rules/clean-architecture.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #10).

## 4. onDestroy is not for business cleanup

- **Invariant**: Activity `onDestroy()` не содержит kill-like actions (`endCall`, `stopService`, `ACTION_END_*`, `disconnect`, `cancelJob`) без проверки `if (isFinishing && !isChangingConfigurations)`. Business operations, которые должны жить в background, принадлежат Foreground Service, не Activity.
- **Constraint**: Если нужно завершать operation "когда user действительно ушёл" — использовать `ViewModel.onCleared()`, который вызывается только при реальном destruction (не на config change).
- **Owner**: `architect-reviewer` (grep check), `frontend-dev`.
- **Rule source**: `.claude/rules/lifecycle.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #11).

## 5. DI exclusive binding

- **Invariant**: Для одного класса — либо `@Inject constructor`, либо `@Provides`/`@Binds` в module. Не оба одновременно. Иначе возможен molчаливое duplicate singleton.
- **Constraint**: Если класс имеет `@Inject constructor` — используй `@Binds` для interface → impl связи. Если класс требует специфичной construction логики (`@Provides`) — убери `@Inject constructor`.
- **Owner**: `architect-reviewer` (grep check), `backend-dev`.
- **Rule source**: `.claude/rules/di-patterns.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #12).

## 6. Walking Skeleton ownership

- **Invariant**: Если `docs/features/<slug>/0-spec.md` содержит `Feature Domain Contract` ≠ N/A — на spec-этапе сгенерирован domain код в `app/src/main/kotlin/<base_package>/domain/<feature_slug>/` + JVM тесты в `app/src/test/kotlin/<base_package>/domain/<feature_slug>/`. Domain код **не переписывается** в downstream фазах — только оборачивается infrastructure в phase-01.
- **Constraint**: Renaming классов в design phase допустимо. Изменение business rules в domain после spec approval — architectural mismatch, эскалация пользователю.
- **Owner**: `domain-designer` (generation), `backend-dev` (integration), `architect-reviewer` (check).
- **Rule source**: `.claude/skills/domain-modeling/SKILL.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #1).

## 7. Scaffold file ownership

- **Invariant**: Файлы `build.gradle.kts` (root + per-module), `libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `AndroidManifest.xml` (root) меняет **только** `backend-dev`. Другие teammates (test-dev, frontend-dev, firebase-dev) запрашивают изменения через SendMessage lead-у.
- **Constraint**: Параллельное редактирование = merge conflict.
- **Owner**: `backend-dev` (ownership), все остальные teammates (запрос через lead).
- **Rule source**: `.claude/rules/agent-communication.md`, `.claude/commands/feature-implement.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #6).

## Как использовать этот файл

- `/feature-spec` Phase 2 — прочитать invariants, добавить секцию "Invariant Check" в `0-spec.md` для затронутых
- `/feature-research` Step 0.7 — для каждого инварианта, который фича затрагивает, добавить в промт research-агенту проверку "как текущая реализация обеспечивает invariant, не нарушит ли фича"
- `architect-reviewer` — проверить, что PR не нарушает ни один invariant. Grep-checklist в `.claude/agents/architect-reviewer.md`
