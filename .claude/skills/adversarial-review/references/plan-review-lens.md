# Plan Review Lens — "Plan as ТЗ, не implementation"

Этот lens используется при adversarial-review шаге `feature-plan.md:Шаг 2` (Plan Review через Codex CLI). Ловит класс проблем, которые обычный plan review (sequencing / dependencies / validation commands) пропускает: **plan превращается в готовый код вместо task descriptions**.

Добавлен после retrospective `docs/features/app-shell-menu/retrospective.md` (2026-04-19), где `plan/phase-01/backend.md` содержал 22 fenced `\`\`\`kotlin` блока с полными классами — прямое нарушение `feature-plan.md:188` ("Только phase files, никакого кода"), не пойманное 3 раундами Codex CLI plan review.

---

## Когда применять

Вызывается как часть `adversarial-review` для каждого plan-файла в `docs/features/<slug>/plan/phase-NN/*.md`:
- `overview.md`
- `backend.md`
- `frontend.md`
- `tests.md`

Запуск: Codex CLI получает prompt с ссылкой на этот lens + чеклист ниже + plan-файлы.

---

## Чеклист проверок

### 1. No fenced Kotlin/Java/Groovy blocks

```bash
grep -nE '^[[:space:]]*```(kotlin|kt|java|groovy)\b' docs/features/<slug>/plan/phase-*/*.md
```

**Expected**: 0 matches.

**Если matches есть** — blocker. Требует replace полного класса на Signature Card (формат см. `.claude/agents/planner.md`).

**Исключение**: `\`\`\`bash` validation commands разрешены, `\`\`\`markdown` templates разрешены.

### 2. Каждый New File имеет Signature Card

Для каждой строки в секции `### New Files` или подобной секции в `backend.md`/`frontend.md`:

- [ ] Путь к файлу указан
- [ ] Тип объявления (class / interface / object / fun / sealed / data class)
- [ ] Inline-сигнатура в backticks (одна строка) — `\`class Foo : Bar\``
- [ ] Вход (параметры / dependencies)
- [ ] Поведение / выход (bullet points)
- [ ] Edge cases
- [ ] Canonical reference — ссылка на `06-api-contract.md:NN` ИЛИ пометка "internal (no api-contract entry)"
- [ ] Rationale (зачем так)

**Если какое-то поле отсутствует или `backend.md` содержит полный класс вместо Signature Card** — blocker.

### 3. Canonical references у публичных типов

Для каждого типа (interface / data class / sealed class / use case), который упоминается в plan и:
- Описан в `06-api-contract.md` → plan ДОЛЖЕН ссылаться: "Canonical reference: `06-api-contract.md:NN`"
- НЕ описан в `06-api-contract.md`, но публичный (экспортируется между модулями) → blocker: "добавить в api-contract перед plan"
- Internal (convention plugin, helper) → допустима краткая inline-сигнатура в Signature Card

### 4. Tests Required — scenarios, не код

Секция `### Tests Required` в `overview.md` и `tests.md` должна содержать:
- [ ] Имя теста (backtick Kotlin-style строка для test function)
- [ ] Формат: `given X, when Y, then Z`
- [ ] НЕ содержать готовый JUnit `@Test fun` код, `assertEquals(...)`, `coEvery { ... }` и прочую test-implementation

**Если tests.md содержит `\`\`\`kotlin` блоки с JUnit кодом** — blocker.

### 5. Options для complex фаз (conditional)

Если `overview.md` содержит tag `complex` (фаза затрагивает 3+ модулей / реализует FSM / новый архитектурный паттерн):

- [ ] Есть секция `### Options Considered`
- [ ] Минимум 2 варианта (recommended + как минимум один rejected)
- [ ] Таблица critere × options
- [ ] Recommended вариант с rationale
- [ ] Для каждого rejected — причина отказа (trade-off)

**Если фаза отмечена complex, но Options Considered отсутствует** — blocker.

**Если фаза НЕ отмечена complex** — проверка пропускается.

### 6. Pattern Invariants — ссылки на существующие паттерны

Секция `### Pattern Invariants` (из `planner.md:99`) должна ссылаться на существующие паттерны в кодовой базе:
- [ ] Каждый invariant содержит `file:line` reference на canonical пример
- [ ] Invariant описан прозой, не блоком кода

### 7. Redundancy check

- [ ] Ни один тип не описан полной сигнатурой в >1 design/plan документе
- [ ] Plan-файлы ссылаются (`see 06-api-contract.md:NN`), не копируют definitions

**Проверка**:
```bash
# Найти все типы в 06-api-contract.md
grep -oE '\b(class|interface|data class|sealed class|object|fun)\s+\w+' docs/features/<slug>/06-api-contract.md | sort -u

# Для каждого типа проверить, что он не дублируется в plan/
for type in <types>; do
  echo "=== $type ==="
  grep -l "$type" docs/features/<slug>/plan/phase-*/*.md
done
```

Если тип встречается в нескольких plan файлах с полной сигнатурой — blocker (должен быть "canonical reference").

---

## Verdict формат

Codex CLI возвращает:

- `PASS` — все чеки прошли
- `CONTESTED` — medium findings, но plan применим с trade-off документацией
- `REJECT` — один или больше blocker findings (items из чеклиста помеченные "blocker")

Для `REJECT` — список findings с:
- Файл + строка
- Цитата нарушения
- Ссылка на нужный пункт чеклиста
- Suggested fix (replace с Signature Card / добавить canonical reference / etc.)

---

## История

- **2026-04-19**: lens создан по retrospective `app-shell-menu` (Bug #1: plan = 67% готовый код, 22 kotlin блоков пропущены 3 раундами Codex)
- **Rationale**: `feature-plan.md:188` legal правило ("Только phase files, никакого кода") не enforced — text rules LLM рационализирует. Deterministic enforcement через hook (`check-plan-no-code.sh`) + review lens (этот файл) + Signature Card шаблон (`planner.md`) = defence-in-depth
