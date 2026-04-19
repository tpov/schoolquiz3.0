# Lessons Learned — Pipeline Retrospectives Archive

Этот файл — архив обобщённых уроков из ретроспектив фич. Pipeline leads читают `docs/invariants.md` (cross-feature инварианты) и свежие `docs/features/<slug>/retrospective.md` для текущей фичи. Этот файл — для людей, которые хотят понять эволюцию методологии.

Формат одной записи:

```
### <date> — <feature-slug>: <one-line lesson>
- **Pattern**: <имя failure pattern>
- **Lesson**: <что future research/design должны проверять>
- **Example**: <конкретный пример из этой фичи>
```

---

### 2026-04-19 — app-shell-menu: Plan = ТЗ, не implementation

- **Pattern**: Plan Faithfulness + Model Default Behavior + Missing Deterministic Enforcement
- **Lesson**: planner LLM по умолчанию копирует готовый код из design docs (`06-api-contract.md`) в plan-файлы, даже когда text rule запрещает. Решение — 3-слойная защита: (1) Signature Card format в `planner.md`, (2) hook `check-plan-no-code.sh` на файловой системе, (3) Plan Review Lens для Codex CLI
- **Example**: `plan/phase-01/backend.md` = 783 строки, 22 fenced `\`\`\`kotlin` блока (72% содержания), прошло 3 раунда Codex CLI plan review без замечаний. Text rule `feature-plan.md:188` игнорировался 7 фаз подряд

### 2026-04-19 — app-shell-menu: DRY для design docs — canonical single source

- **Pattern**: Document Duplication
- **Lesson**: каждый публичный domain type имеет ровно ОДИН canonical source — в `06-api-contract.md`. Другие docs (01-architecture, 02-behavior, plan/*.md) ссылаются, не дублируют полные сигнатуры. Drift неизбежен, когда одно изменение требует правок в 4 файлах
- **Example**: Navigator описан 5 раз — в 0-spec.md, 01-architecture.md, 06-api-contract.md, plan/phase-01/backend.md, domain/Navigator.kt. Добавлена Document Responsibility Matrix в `feature-design.md` с grep-check командой

### 2026-04-19 — app-shell-menu: Alternatives — first-class artifact для complex фаз

- **Pattern**: Methodology Gap
- **Lesson**: ADR best practice (Nygard 2011, "The most valuable part of ADR is the rejected alternatives") применяется к phase-level planning. Для complex фаз (3+ модулей / FSM / новый pattern) plan содержит Options Considered таблицу с recommended + rejected + trade-offs. Без альтернатив — переписать фазу в случае проблемы в коде дорого
- **Example**: все 7 фаз `app-shell-menu` содержали ровно один вариант реализации. User feedback: "несколько вариантов, а потом с каждым ревью он убирал или приоритизировал пункты — что-бы можно было легко переписывать если проблема реально проявится"

### 2026-04-19 — app-shell-menu: Shared-model review blind spot

- **Pattern**: Review Blind Spot
- **Lesson**: Codex CLI эффективен для correctness bugs (поймал Firebase wiring gap, InstanceKeeper leak, firestore.rules wrong fields — 3 critical issues). НО: Codex делит те же pipeline-assumptions с автором plan → не ловит methodology drift (plan = код, document duplication). Нужен **explicit review lens** с checklist, не general-purpose review
- **Example**: 3 round'а Codex plan review пропустили 22 `\`\`\`kotlin` блока в plan-файлах. После retrospective создан `.claude/skills/adversarial-review/references/plan-review-lens.md` с явным чеклистом

### 2026-04-19 — app-shell-menu: Text rules → Deterministic enforcement

- **Pattern**: Missing Deterministic Enforcement
- **Lesson**: правила в markdown (CLAUDE.md, feature-*.md) LLM рационализирует в обход. Для правил, которые МОГУТ быть автоматизированы — используй hooks (`.claude/hooks/*.sh`) + grep + exit 2. Это 100% enforcement vs 0% для text rules в реальности
- **Example**: `feature-plan.md:188` "Только phase files, никакого кода" legal 6+ месяцев, но planner его игнорировал. После retrospective добавлен hook `check-plan-no-code.sh` (PostToolUse, exit 2 с объяснением)
