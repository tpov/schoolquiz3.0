---
type: sprint-change-proposal
date: 2026-10-26
trigger: owner decision 2026-10-25/26 — replace human review and moderation with a model pipeline in the MVP; add proposals to another author's material
scope: major
status: approved by the owner 2026-10-26
mode: incremental (each edit approved in chat 2026-10-25/26)
visual-source: private Design artifact https://claude.ai/artifact/S8c5LWgvZh5Xnb7tpnp8a1 (version 8)
---

# Sprint Change Proposal — model review and proposals to authors

## 1. Issue summary

**Trigger.** On 2026-10-25 the owner decided that human review and moderation of submitted quests is replaced by a neural-network pipeline **in the MVP**, not at the alpha test. On 2026-10-26 the owner added a second part: users can propose changes to another author's material in three ways, and the author approves or rejects them.

**Category.** Strategic pivot / new stakeholder requirement. No single story failed; the closest story is 4.10 (review resume and timed TESTING question), which is blocked on a rig with human reviewers.

**Why now.**
- Cold start: peer review needs a pool of qualified reviewers, which the MVP will not have; submissions would wait in the queue. A model works from the first user.
- The alpha test should exercise what ships; testing the human flow there would debug a system planned for removal.
- Precedent in the repo: `.zcode/skills/quest-audit` already performs model-driven content audit of seeded school quests, with `scripts/seed-bulk/gate-lesson.js` as a deterministic gate.

**What this reverses (recorded decisions).**
- SPEC Non-goals: «an AI-only reviewer model» and «general editing of someone else's published text, or a public proposed-edit workflow».
- PRD FR-8: «нейросеть — как обсуждаемая возможность… AI-only схема не приняты»; PRD «Не входит»: «общий редактор чужого опубликованного текста и отдельный workflow предложенных правок всем игрокам… не означает AI-only исполнителя».
- Architecture AD-4: «новые роли, AI-only… не назначаются».
- Epic 4 Boundaries: «no GLM in this version».

The owner is the decision authority for all four; this proposal records the reversal explicitly.

## 2. Impact analysis

### Epics (initiative `mvp-candidate`)

| Epic | Impact |
| --- | --- |
| 4 authoring-review | **Reopened.** Done-when 2, 3, 6 rewritten; boundary on GLM lifted; CAP-27 added; 4.10 cancelled; stories 4.11–4.26 added. Retrospective 2026-10-25 closed «accepted-with-open-items»; its fee-integrity action items (L4, item 2) and the draft-selection finding (item 9a) are carried into new stories. |
| 5 competitions, 6 exams | Contract unchanged: they consume published approved banks. «Approved» now means the model checks passed; curator placement unchanged. |
| 7 commerce | Not affected (ad review stays manual; out of this change). |
| 9 UX NOIR | Reviewer screens are no longer restyled; new surfaces: Decisions tab, question preview, proposal entries. |
| 10 final acceptance | Matrix row «Автор и вопросы» changes (section 4.2). |

No epic becomes obsolete; no new epic is created — the work lands in epic 4.

### Artifacts

| Artifact | Change |
| --- | --- |
| `specs/spec-schoolquiz3.0/SPEC.md` | CAP-3, CAP-4, CAP-6 rewritten; CAP-27 added; Constraints review paragraph; two Non-goals; Success signal; OQ-10 added. |
| `specs/spec-schoolquiz3.0/contract-map.md` | CAP-3/4/6 rows; CAP-27 row. |
| `specs/spec-schoolquiz3.0/ux-constraints.md` | UX-6, UX-17 rewritten; UX-22, UX-23 added (UX-21 is the existing online-timing rule). |
| PRD `prd.md` | Dated amendment section «Уточнение объёма от 2026-10-26» plus pointer lines in UJ-3, UJ-4, FR-5, FR-6, FR-8, FR-10. |
| `ARCHITECTURE-SPINE.md` (2026-09-29) | New AD-32, AD-33; AD-3, AD-4, AD-10, AD-26 amended; capability map rows. |
| Epic 4 `epic-authoring-review.md`, `tickets.toml` | Description, Done-when, Boundaries, Notes; 4.10 cancelled; 16 new stories. |
| Initiative `tickets.toml`, `initiative-mvp-candidate.md` | Epic 4 and initiative `covers` gain CAP-27. |
| `DESIGN.md` / `EXPERIENCE.md` (ux-schoolquiz3.0-2026-09-28) | **Not on this branch** — they exist only in worktree `mvp-candidate`. StatusStrip row and F9 need the same edits where the files live (follow-up, section 5). |

### Technical impact

- New VPS worker process for the review queue (light: API calls only; no Gradle; fits the VPS resource rules).
- Model API key stored on the VPS outside Git.
- Removed code: reviewer screens, star rating and threshold handling, segment translation review, human reviewer assignment/generation, reviewer reward distribution inputs. Removal follows `testing.md`: every deleted file is listed explicitly in the removal story.
- Kept code: submission queue, per-question outcomes (4.6 server half adapted to pass/fail), fee cycle with burn (4.7), curator transitions (4.8), inbox (4.9), views (4.2), push infrastructure.
- New shared names between client and server (stage names, proposal kinds, push event type) each get a sentinel test reading the other side (`testing.md`, «Общие имена клиента и сервера»).

## 3. Recommended approach

**Hybrid: Direct Adjustment + narrow MVP amendment.**

- Direct adjustment: the change fits inside epic 4 as new stories on top of contracts that already exist (queue, outcomes, fee cycle, curator, inbox, push, revisions).
- MVP amendment: two Non-goals are lifted and one role disappears. MVP stays achievable; acceptance becomes lighter (no human-reviewer device journeys).
- Rollback: not needed; superseded code is removed by a dedicated story, not reverted.

| Aspect | Assessment |
| --- | --- |
| Effort | Medium: 16 stories, no rework of accepted contracts beyond the review actor. |
| Risk | Medium: model quality and web search reliability (mitigated by linter, delayed-review state, admin Decisions tab); prompt injection (lesson text treated as data); children's content published without a human gate («Слова» rejects on doubt). |
| Timeline | Epic 4 reopens before epics 5/6 consume new banks; proposals block follows the pipeline. |

## 4. Detailed change proposals (approved in chat)

Decisions taken during the session:

| # | Decision | Source |
| --- | --- | --- |
| D1 | Model is the reviewer; humans only audit. | 2026-10-25 |
| D2 | Checks: «Слова» (forbidden words/phrases, children's audience) and «Факты» (correctness with web sources). | 2026-10-25 |
| D3 | Translation is not a check: automatic after checks; source language detected, target = other app languages; author never specifies language. | artifact comment 2026-10-25 |
| D4 | Per-question verdict pass/fail with reason; no stars, no threshold. | 2026-10-25 |
| D5 | Linter after each stage; ≤3 retries; then «Проверка задерживается». | rec. 3, accepted |
| D6 | Passing lessons publish; author sees tag «Не прошёл» and the list of failed questions with stage and reason. | rec. 1 + mockup |
| D7 | Fee 1000/100 unchanged; no reviewer rewards; whole fee burns. | default, not objected |
| D8 | Decisions tab for **admins**: verdicts, ✓/✗ with comment, logs, question preview; marks change nothing published. | 2026-10-26 |
| D9 | Reviewer screens and human assignment code are **deleted**, not frozen. | 2026-10-25 |
| D10 | Ad review (monetisation 4.3) out of this change. | default, not objected |
| D11 | Exam questions: passing checks suffices; curator places. | 2026-10-25 |
| D12 | Model: GLM family; concrete model and per-quest budget after trial runs; no budget limit until then. | 2026-10-26 |
| D13 | 4.11: draft submitted with a button on the draft card. | 2026-10-26 |
| D14 | Proposals: three entries (error report, edited copy, own lesson with a target path), all through checks, author approves in Decisions → «Предложения»; copy without secret keys; stale detection; fees 0/100 per changed question/1000. | 2026-10-26 |
| D15 | Proposals block is in this MVP, after the pipeline. | recommended 2026-10-26, not objected |

### 4.1 SPEC (`specs/spec-schoolquiz3.0/SPEC.md`)

Frontmatter: `updated: '2026-10-26'`.

**CAP-3** — replace intent and success with:

> - **intent:** A queued model review assesses submitted content; authors see which questions failed and why, and admins can audit every model decision.
> - **success:** A submitted quest enters a server queue. A model pipeline checks one lesson at a time, with the quest title and full category path as context, in two stages — forbidden words and phrases for a children's audience (doubt rejects) and factual correctness with recorded web sources — and gives each question a pass/fail verdict with a reason; there is no rating scale or threshold. A format linter validates every stage output; an invalid output returns a named error and is retried at most three times, after which the lesson shows a delayed review, never a guessed verdict. The model judges and never edits the author's text. Each verdict binds the concrete revision, stage, model and prompt version. Passing lessons publish independently; a quest with failed questions shows «Не прошёл», and its expansion lists each failed question with lesson, stage and reason. Affected dependencies are rechecked and unaffected valid evidence is retained. After the checks the pipeline detects the source language and translates the content into the other app languages; translation is a processing step, not a check, and its structure linter preserves option count, answer key and protected FillBlank ranges. Admins see a Decisions tab with each verdict, ✓/✗ marks with an optional comment, the model's log and the question as the player sees it; a mark records agreement for debugging and changes no published state.

**CAP-4** — replace intent and success with:

> - **intent:** Authors submit material at an agreed fee; this version pays no reviewer reward.
> - **success:** The accepted tariff is shown and charged once; retry and retained prior evidence create no extra paid work. A completed cycle, including justified rejection, has zero reward entitlements and burns the whole fee, so fee = entitlements + burned remainder still holds under the exact-arithmetic contract. Proposal fees follow CAP-27. An unfinished cycle is not finally settled.

**CAP-6** — replace success with:

> - **success:** Both post-lesson actions exist. A private error report (free text about the whole lesson, optionally with the reporter's own variant) is a CAP-27 proposal: it passes the checks and reaches the lesson's author in the Proposals section without re-revealing questions or answers to anyone else. The shared inbox keeps comments and preserves each item's original readers and lesson link; reviewer or curator roles alone grant no access to a private report. Reports delivered before this change stay readable.

**CAP-27** — add after CAP-26:

> - **CAP-27 — Propose changes to another author's material**
>   - **intent:** A signed-in user can propose a change to another author's lesson or course, and the author decides.
>   - **success:** Three entries end in «Предложить автору»: an error report (text and an optional own variant), an edited copy of a lesson, and an own new lesson created with a target path in another author's course. Every proposal passes the «Слова» and «Факты» checks before the author sees it in the Proposals section of the Decisions tab, with a push notification; the author approves or rejects with an optional comment and the proposer sees the outcome. An approved report marks the lesson for correction; an approved copy becomes a new revision of the author's lesson exactly as checked, with the proposer recorded as contributor; an approved new lesson is placed in the course with the proposer as its author. A copy never contains secret answer keys (HARD, Arena, exam); the proposer marks the correct answer and «Факты» checks it. A proposal based on an older revision becomes stale and cannot be applied. Error reports are free, an edited copy costs 100 nolics per changed question and a new lesson 1000; deciding costs the author nothing. Issued attempts keep their frozen banks.

**Constraints**, review paragraph — OLD: «Independent checks approve a concrete immutable revision; … TESTING is per question with one independent reviewer and a server threshold defaulting to 2 of 3; other directions and explicit translation completeness remain mandatory. Online evidence writes pause on connection loss, but an active timed question continues under AD-31. …» — NEW:

> Model review approves a concrete immutable revision; preserve valid unaffected evidence and frozen issued banks. Every question gets a pass/fail verdict with a reason from the «Слова» and «Факты» stages; there is no rating threshold. Lesson text is data to the model, never instructions; a verdict records model, prompt version, stage, sources and rationale. Translation follows the checks automatically from the detected source language and must pass the structure linter. Approved ordinary/Arena offline paths remain; no pretest or statistical admission gate is added.

**Non-goals** — two items:

- OLD «Telegram/group delivery, arbitrary participant DMs, general editing of someone else's published text, or a public proposed-edit workflow.» → NEW «Telegram/group delivery, arbitrary participant DMs, or direct editing of someone else's published text; changes by others arrive only as proposals the author approves (CAP-27).»
- OLD «Unapproved message auto-translation, developer broadcast announcements or an AI-only reviewer model; existing content translation remains part of review.» → NEW «Unapproved message auto-translation or developer broadcast announcements. Human reviewer pools, reviewer assignment, reviewer rewards and manual translation review are out of this version, and their code is removed.»

**Success signal** — OLD «Prove one-reviewer TESTING and translation completeness;» → NEW «Prove the model review on a real submission (pass, failed questions with reasons, linter retry and delayed review, automatic translation with preserved structure), the admin Decisions tab and all three proposal entries through author approval;»

**Open questions** — add:

> - **OQ-10:** Choose the concrete GLM model and the per-quest budget after trial runs of the pipeline; until then no budget limit is assumed.

### 4.2 contract-map.md

| Row | NEW |
| --- | --- |
| CAP-3 | `| CAP-3 | FR-6–8; PRD amendment 2026-10-26; UX-6/17/22 | AD-3/20/31/32 |` |
| CAP-4 | `| CAP-4 | FR-9–10; PRD amendment 2026-10-26 | AD-5/6/9/10/32 |` |
| CAP-6 | `| CAP-6 | FR-13–15; PRD amendment 2026-10-26 | AD-20/21/33 |` |
| CAP-27 (new) | `| CAP-27 | PRD amendment 2026-10-26; UX-22/23 | AD-3/9/20/32/33 |` |

### 4.3 PRD (`prds/prd-schoolquiz3.0-2026-09-28/prd.md`)

Insert before «## Полный объём и исключения»:

```markdown
## Уточнение объёма от 2026-10-26: проверка нейросетью и предложения автору

По решению владельца от 2026-10-25/26 независимую проверку людьми в текущем MVP заменяет проверка нейросетью, а пользователи получают способ предлагать изменения чужим урокам и курсам. Уточнение заменяет перечисленные ниже части; остальные требования сохраняют смысл и идентификаторы. Основание — [предложение об изменении](../../sprint-change-proposal-2026-10-26.md).

### Проверка нейросетью

- Поданный квест встаёт в серверную очередь. Нейросеть проверяет уроки по одному; в контексте — название квеста и полный путь категории.
- Два этапа: «Слова» — запрещённые слова и фразы с учётом детской аудитории, при сомнении отказ; «Факты» — корректность с поиском и записью источников.
- По каждому вопросу — «прошёл» или «не прошёл» с причиной. Шкалы и порога нет.
- После каждого этапа линтер проверяет формат ответа модели; неверный формат возвращается модели с понятной ошибкой, не более трёх повторов, затем урок получает состояние «Проверка задерживается». Сбой модели — не отказ по качеству и не угаданный вердикт.
- Нейросеть не правит текст автора. Решение хранится неизменным: редакция, этап, модель, версия промпта, источники, комментарий.
- Прошедшие уроки публикуются независимо. Квест с непрошедшими вопросами показывает метку «Не прошёл»; раскрытие перечисляет урок, вопрос, этап и причину.
- Перевод — не проверка. После проверок нейросеть сама определяет язык исходника и переводит содержание на остальные языки приложения (RU/UK/EN). Автор язык не указывает и переводы не сдаёт. Линтер сохраняет число вариантов, ключ и защищённые фрагменты FillBlank.
- Вопрос годен для экзамена после прохождения проверок; размещение по-прежнему делает куратор.
- Модель — семейство GLM; конкретная модель и бюджет на квест выбираются после пробных прогонов.

### Роли и вкладка «Решения»

- Роли «Независимый проверяющий» в текущем MVP нет. Экраны проверяющего, назначение проверяющих и ручная проверка перевода удаляются.
- Админ видит вкладку «Решения»: каждый вердикт, отметки ✓/✗ с комментарием для отладки, логи модели и вопрос в виде экрана прохождения. Отметка не меняет опубликованное состояние.
- Автор видит во вкладке «Решения» только раздел «Предложения» — предложения к своим урокам и курсам.

### Плата

- Тарифы FR-9 не меняются. Вознаграждения проверяющим нет: вся плата за цикл сгорает, равенство «плата = вознаграждения + сгоревший остаток» сохраняется. Возврата платы, как и прежде, нет.

### Предложения автору

- Три входа заканчиваются кнопкой «Предложить автору»: «Сообщить об ошибке» (текст и по желанию свой вариант), «Предложить правку» — редактируемая копия урока, и собственный новый урок с путём в чужом курсе.
- Каждое предложение проходит «Слова» и «Факты» до показа автору. Автор получает push и элемент в «Предложениях», одобряет или отклоняет с комментарием; предложивший видит итог.
- Одобренное сообщение об ошибке помечает урок к исправлению. Одобренная копия становится новой редакцией урока ровно в проверенном виде; автор урока прежний, предложивший записывается соавтором правки. Одобренный новый урок встаёт в курс, его автор — предложивший.
- Копия не содержит секретных ключей HARD, Арены и экзаменов; верный ответ отмечает предложивший, «Факты» его проверяет.
- Предложение по устаревшей редакции помечается «устарело» и не применяется.
- Сообщение об ошибке бесплатно, правка копии — 100 ноликов за изменённый вопрос, новый урок — 1000. Автор за решение не платит. Выданные попытки сохраняют прежний банк.

### Что заменяется

| Место | Было | Стало |
| --- | --- | --- |
| Участники | «Независимый проверяющий» | Роли нет; проверяет нейросеть; админ ведёт «Решения» |
| Словарь: «Проверка», «Уровень проверяющего» | Независимое рассмотрение по направлениям; личный уровень в формуле вознаграждения | Этап нейросети «Слова» или «Факты»; уровень проверяющего не применяется |
| UJ-3 | Независимые проверяющие и куратор выполняют свои действия | Нейросеть проверяет, куратор размещает |
| UJ-4 | «Сообщить об ошибке» приватно отправляет автору текст | Сообщение — предложение: проходит проверки и попадает в «Предложения» автора |
| FR-5 | Языки вопросов и переводческая проверка остаются в объёме | Язык определяется сам, перевод автоматический |
| FR-6 | Роль проверяющего, онлайн-сохранение и возобновление его работы | Вердикты нейросети; абзац о работе проверяющего не применяется |
| FR-8 | Три направления; AI-only схема не принята | Два этапа и автоматический перевод; схема с нейросетью принята |
| FR-10 | Вознаграждение «личный уровень / 10» | Вознаграждения нет, плата сгорает |
| Уточнение 2026-09-29 (экзамены) | Пригодность после всех независимых проверок и утверждения | Пригодность после проверок нейросети; размещение — куратор |
| Не входит | Общий редактор чужого текста и workflow предложенных правок; «не означает AI-only исполнителя» | Чужой текст меняется только через одобренные автором предложения; фраза про AI-only снята |
| Матрица «Автор и вопросы» | Три направления, вознаграждение уровень / 10 | Два этапа, линтер и задержка, перевод, «Решения», сгорание платы, три входа предложений |
| Открытое решение «Назначения проверяющим» | Требования к назначению | Закрыто: назначать некого |
```

Pointer line added at the end of UJ-3, UJ-4, FR-5, FR-6, FR-8 and FR-10:

```markdown
> Уточнено 2026-10-26 — см. «Уточнение объёма от 2026-10-26».
```

### 4.4 Architecture (`architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md`)

Add after AD-31:

```markdown
### AD-32 — Проверку выполняет конвейер нейросети `[ADOPTED 2026-10-26]`

- **Binds:** FR-5–10 и уточнение PRD 2026-10-26; CAP-3/4/27.
- **Prevents:** угаданный вердикт при сбое модели, исполнение инструкций из текста урока, правку авторского текста моделью, двойную обработку одного урока.
- **Rule:** подача → серверная очередь → отдельный обработчик на VPS по образцу `arena-season-worker` (AD-23), не триггер функции: вызов модели с поиском длится минутами. Один квест за раз, уроки по одному; контекст — название квеста и путь категории. Этапы «Слова» → «Факты», у каждого свой скилл (промпт) с версией. Ответ модели строго по схеме; линтер (код) проверяет схему и при ошибке возвращает модели именованную ошибку, не более трёх повторов, затем состояние REVIEW_DELAYED — не REJECTED (AD-8). Вердикт по вопросу PASS/FAIL с причиной — неизменяемая запись (revision, stage, model, promptVersion, sources, rationale, attempts). Текст урока передаётся как данные внутри разграничителей; модель не возвращает правок текста. Задание имеет идентичность (submission, lesson, stage, attempt); повтор не создаёт второго вердикта. После прохождения — шаг перевода: определение языка источника, перевод на остальные языки приложения, линтер структуры (число вариантов, ключ, защищённые диапазоны AD-27). Ключ API хранится на VPS вне Git; модель и бюджет — после пробных прогонов (SPEC OQ-10). Вкладка «Решения» читает записи серверной проекцией с проверкой роли admin; отметки ✓/✗ хранятся отдельно и не меняют вердикт или публикацию.

### AD-33 — Предложение автору — одна запись для трёх входов `[ADOPTED 2026-10-26]`

- **Binds:** CAP-6, CAP-27; FR-5, FR-11–15.
- **Prevents:** утечку секретных ключей через копию, применение непроверенной или устаревшей правки, изменение чужого текста без решения автора.
- **Rule:** proposal = (kind REPORT | COPY_EDIT | NEW_LESSON, целевой урок или узел курса, base revision, proposer, payload revision). Все виды проходят этапы AD-32 до показа автору. Маршрут — владельцу целевого урока или курса; push-тип с общим именем и сторожем (testing.md). Копию собирает сервер из проекции игрока: тексты и варианты без секретных ключей. Одобрение атомарно применяет ровно проверенную редакцию: COPY_EDIT → новая редакция урока (автор прежний, предложивший — соавтор в истории); NEW_LESSON → урок в курсе с автором-предложившим; REPORT → отметка «к исправлению». Если base revision не равна текущей — STALE, применение отказывает. Плата по виду: 0 / 100 × изменённые вопросы / 1000 по AD-9/10 без выплат; решение автора бесплатно. Выданные попытки сохраняют банки (AD-3). Права на раздел «Предложения» проверяет сервер (AD-20).
```

Amendments:

| AD | Change |
| --- | --- |
| AD-3 | «evidence связывает revision, направление, язык, independent slot и reviewer» → «evidence связывает revision, этап, модель и версию промпта (AD-32)». |
| AD-4 | Append: «В текущем MVP не применяется: проверяющих-людей нет, проверку ведёт AD-32; код назначения удаляется.» |
| AD-10 | Append: «В текущем MVP единиц вознаграждения нет: ΣLi = 0, burn = 10P (AD-32); формула и инварианты сохраняются.» |
| AD-26 | Append: «В текущем MVP TESTING-оценка 1–3, порог и проверка перевода по сегментам не применяются: вердикт PASS/FAIL с причиной даёт AD-32, перевод — автоматический шаг с линтером.» |
| Capability map | Rows for FR-5–8 and EXPERIENCE F9 gain AD-32; new row «CAP-27: proposals» → AD-3/9/20/32/33. |

### 4.5 UX (`specs/spec-schoolquiz3.0/ux-constraints.md`)

- **UX-6** NEW: «My Quests retains category selection and the category-creation FAB. Before approval a quest row shows two check strokes («Слова», «Факты»). A quest with failed questions shows the tag «Не прошёл» with the count; its expansion lists each failed question with lesson, stage and the model's reason, then «Исправить вопросы». A delayed review reads «Проверка задерживается». After approval, publication-place tags replace the strokes. The detected language appears as a detail row. Visual source: Design artifact 2026-10-26.»
- **UX-17** NEW: «Review is done by the model pipeline (AD-32): there are no reviewer screens, star ratings, threshold or segment translation review, and their code is removed. Translation is automatic after the checks.»
- **UX-22** NEW: «Decisions tab. Admins see model verdicts in the review-queue family (64px rows, wash-review, radius-6 tags), filters «Без отметки / Верно / Неверно», ✓/✗ with an optional comment, «Логи» (model comment, sources, attempts, model/prompt/revision) and the question in the runner preview without timer or charges. Mark states read «Сохранено» / «Отправляется…». Authors see only the «Предложения» section for their own lessons and courses. Sources: AD-32/33; CAP-3, CAP-27.»
- **UX-23** NEW: «Proposal entries: «Сообщить об ошибке» after a lesson, «Предложить правку» on a lesson (editable copy without secret keys) and a target path in another author's course during quest creation. Each ends with «Предложить автору» and shows its fee before sending; the proposer sees the outcome. Sources: AD-33; CAP-27.»

### 4.6 Epic 4 (`mvp-candidate/epic-authoring-review/`)

`epic-authoring-review.md`:

- frontmatter `covers: [CAP-2, CAP-3, CAP-4, CAP-5, CAP-6, CAP-27]`.
- Description: «independent per-question review with a configurable threshold admits concrete revisions (CAP-3), the paid review cycle conserves fee = rewards + burned remainder (CAP-4)» → «a queued model review gives each question a pass/fail verdict with a reason and translates passed content automatically (CAP-3), the paid review cycle charges once and burns the whole fee (CAP-4)»; append «, and users propose changes to other authors' material, which the author approves (CAP-27)».
- Done-when 2 → «A submitted quest passes the model pipeline («Слова», «Факты», linter with at most three retries, automatic translation); each question gets pass/fail with a reason; failed questions return to the author; admins audit verdicts in Decisions (CAP-3).»
- Done-when 3 → «A paid review cycle, including justified rejection, charges once and burns the whole fee; retry and retained evidence create no extra paid work (CAP-4).»
- Done-when 6 → «All three proposal entries reach the target author after checks; approval applies exactly the checked revision, a stale proposal is refused, a copy never carries secret keys (CAP-27).»
- Boundaries: «not GLM parsing (out of MVP scope — no GLM in this version)» → «GLM is used only by the review pipeline (AD-32); GLM parsing of other content stays out of scope».
- Notes: add «Change 2026-10-26 (sprint-change-proposal-2026-10-26): human review replaced by the model pipeline; 4.10 cancelled; stories 4.11–4.26 added.»

`tickets.toml`:

- 4.10 → `status = "cancelled"` with comment «2026-10-26: superseded by the model pipeline (sprint-change-proposal-2026-10-26); draft-selection finding moves to 4.11». If the orchestrator rejects `cancelled`, use `done` with the same comment — confirm at inception.
- New stories, build order:

| id | slug | title (summary) | covers | after |
| --- | --- | --- | --- | --- |
| 4.11 | draft-submit-from-card | A local draft is submitted for review with «Отправить на проверку» on the draft card (retro item 9a) | CAP-2/3 | — |
| 4.12 | review-queue-worker | VPS worker drains the queue: one quest at a time, lessons one by one with title and path context, immutable verdict record, linter with ≤3 retries, REVIEW_DELAYED, idempotent jobs, lesson text as data | CAP-3 | 4.11 |
| 4.13 | stage-words | «Слова» stage skill + schema + linter; doubt rejects | CAP-3 | 4.12 |
| 4.14 | stage-facts | «Факты» stage from the quest-audit checklist in judge mode, web sources recorded | CAP-3 | 4.12 |
| 4.15 | auto-translation | Source language detection, translation to the other app languages, structure linter (options, key, protected FillBlank ranges) | CAP-3 | 4.13, 4.14 |
| 4.16 | author-review-outcome | Passing lessons publish; My Quests tag «Не прошёл», failed-question list with reasons, «Проверка задерживается» (UX-6) | CAP-3 | 4.14 |
| 4.17 | admin-decisions-tab | Decisions tab for admins: verdicts, ✓/✗ with comment, Логи, question preview; server view with role check (UX-22) | CAP-3 | 4.14 |
| 4.18 | review-fee-burn-and-integrity | Tariff unchanged, zero reward units, whole fee burns (conservation test); retro L4/item 2: validate draft ownership in `planReviewFeeCharge`, dual-write the `nolics` mirror | CAP-4 | 4.12 |
| 4.19 | remove-human-review | Remove reviewer screens, stars/threshold, segment translation review, reviewer assignment; explicit deleted-files list | CAP-3 | 4.16, 4.17 |
| 4.20 | review-pipeline-journey | Live journey on the rig: real submission → pass/fail → author screen → Decisions; trial runs fix model choice and budget input (OQ-10) | CAP-3/4 | 4.15–4.19 |
| 4.21 | proposal-core | Proposal record (REPORT/COPY_EDIT/NEW_LESSON), base revision, routing to the target owner, checks via the pipeline, fees, STALE (AD-33) | CAP-27 | 4.20 |
| 4.22 | proposal-error-report | «Сообщить об ошибке» becomes a proposal (text + optional own variant); existing reports stay readable | CAP-6/27 | 4.21 |
| 4.23 | proposal-copy-edit | «Предложить правку»: server-built copy without secret keys → editor → «Предложить автору»; approval applies the checked revision, proposer recorded as contributor | CAP-27 | 4.21 |
| 4.24 | proposal-new-lesson | Quest creation with a target path in another author's course → «Предложить автору»; approval places the lesson with the proposer as author | CAP-27 | 4.21 |
| 4.25 | proposals-author-side | «Предложения» section in Decisions (rights-scoped), push type with sentinel, ✓/✗ with comment, proposer sees outcome | CAP-27 | 4.21 |
| 4.26 | proposals-journey | Live journey of all three entries through author approval, including stale refusal and absence of secret keys in the copy | CAP-27 | 4.22–4.25 |

All new stories start `ready` except where the orchestrator's inception rule requires a device (`hitl`): 4.20 and 4.26.

Initiative: `mvp-candidate/tickets.toml` epic 4 `covers` gains `"CAP-27"`; `initiative-mvp-candidate.md` frontmatter `covers` gains `CAP-27`.

## 5. Implementation handoff

**Scope classification: Major** — a recorded Non-goal is lifted, a role disappears and two architecture decisions are added.

| Step | Owner | Deliverable |
| --- | --- | --- |
| 1 | Owner | Approve this proposal. |
| 2 | Developer agent (this session) | Apply sections 4.1–4.6 in one commit naming only those files (parallel sessions commit to the same branch). |
| 3 | Architect review | Review AD-32/33 against AD-3/8/9/20/23/27 before story inception; `architect-reviewer` grep checks apply to the new worker and views. |
| 4 | Follow-up (owner decides where) | Bring `ux-designs/ux-schoolquiz3.0-2026-09-28/` to this branch or edit it in worktree `mvp-candidate`: DESIGN StatusStrip row (two checks, reason shown) and EXPERIENCE F9 (model review, Decisions, proposals). |
| 5 | Orchestrator (`bmad-build` / night cycle) | Re-inception of epic 4: 4.11 → 4.26 in build order. |

**Success criteria.** Epic 4 closes when Done-when 1–6 (as amended) hold with per-criterion evidence; no human-reviewer code remains; the fee conservation test passes with zero reward units; the three proposal journeys pass on the rig; OQ-10 is answered from trial runs.

**Open items carried.** OQ-2 (abandoned-cycle settlement) stays parked. Retrospective action items unrelated to review stay with their owners.
