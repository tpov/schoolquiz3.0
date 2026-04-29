# Cross-Feature Architectural Invariants

Архитектурные инварианты, применимые для **всех** фич проекта. Нарушение — blocker для любой phase.

Читается `/feature-spec` (invariant check), `/feature-research` (invariant impact analysis), `architect-reviewer` (по каждому review).

---

## 1. Domain layer purity

- **Invariant**: Файлы domain-слоя в `shared/**/domain/src/commonMain/**` не содержат Android framework типов (Context, Uri, Bundle, Intent, View, Activity, Fragment), SDK типов (Firebase, Retrofit, Room, OkHttp, Moshi), DI аннотаций (@Inject, @Provides, @Module) ни как полей, ни как параметров функций, ни как generic параметров, ни как return types.
- **Constraint**: В KMP domain нет исключения для `Parcelable`; navigation/platform mapping живёт вне domain.
- **Owner**: `architect-reviewer` (grep check), `domain-designer` (generation), `backend-dev` (implementation).
- **Rule source**: `.claude/rules/domain-models.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #8).

## 2. Presentation does not bypass domain

- **Invariant**: Android presentation (`android/**/presentation`) не вызывает DAO, Entity, RemoteDataSource, Firebase/Room adapters или data-layer mappers напрямую. Decompose `Component` работает через use cases или domain repository interfaces; Compose `Screen` получает state/callbacks от component и не резолвит Koin напрямую.
- **Constraint**: AndroidX `ViewModel` не является каноничным state holder для новых экранов; использовать Decompose `Component`, если фаза/ADR явно не требует другого.
- **Owner**: `architect-reviewer` (grep check), `frontend-dev` (implementation).
- **Rule source**: `.claude/rules/clean-architecture.md`, `.claude/rules/navigation.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #9).

## 3. No bidirectional coupling between feature modules

- **Invariant**: Если feature-A импортирует feature-B напрямую — feature-B **не может** импортировать feature-A напрямую. Bidirectional coupling допускается только через shared interface в `core/` или через reflection (ADR-обоснованно).
- **Constraint**: Прямой cross-feature import (А → Б) должен быть задокументирован в `docs/features/<A>/03-decisions.md` или в том же ADR что и обратная reflection-связь.
- **Owner**: `architect-reviewer` (grep check), cross-feature scanner в `/feature-research` Step 0.8.
- **Rule source**: `.claude/rules/clean-architecture.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #10).

## 4. onDestroy is not for business cleanup

- **Invariant**: Activity `onDestroy()` не содержит kill-like actions (`endCall`, `stopService`, `ACTION_END_*`, `disconnect`, `cancelJob`) без проверки `if (isFinishing && !isChangingConfigurations)`. В Decompose components `doOnDestroy` подходит для отмены component scope/listeners, но не для бизнес-операций, которые должны жить дольше UI.
- **Constraint**: Если нужно завершать operation "когда user действительно ушёл" — явно моделировать это в navigation/component lifecycle или platform service, а не полагаться на Android `onDestroy()`.
- **Owner**: `architect-reviewer` (grep check), `frontend-dev`.
- **Rule source**: `.claude/rules/lifecycle.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #11).

## 5. Koin binding uniqueness

- **Invariant**: Для одного exposed типа в Koin graph должен быть один production binding, если duplicate не задокументирован как named/qualified binding. Composition root — `apps/android-next/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`.
- **Constraint**: Project does NOT use Hilt/Dagger. Hilt/Dagger exclusive binding rule не применяется; для Koin проверять duplicate `single`/`factory`, missing module registration и неверные `parametersOf`.
- **Owner**: `architect-reviewer` (grep check), `backend-dev`.
- **Rule source**: `.claude/rules/di-patterns.md`
- **Added**: 2026-04-16, pipeline-retrospective 2026-04-16 (Bug #12).

## 6. Walking Skeleton ownership

- **Invariant**: Если `docs/features/<slug>/0-spec.md` содержит `Feature Domain Contract` ≠ N/A — на spec-этапе сгенерирован domain код в проектном layout из `.claude/PROJECT-CONTEXT.md`; для KMP feature это `shared/feature/<slug>/domain/src/commonMain/` + `src/commonTest/`. Domain код **не переписывается** в downstream фазах — только оборачивается infrastructure в phase-01.
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
