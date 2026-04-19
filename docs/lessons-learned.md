# Pipeline Lessons Learned

Архив обобщаемых уроков из pipeline retrospectives. Новые записи — сверху.

---

### 2026-04-16 — pipeline-retrospective: AI склонность к упрощению + deterministic enforcement

- **Pattern**: Text-based rules не enforced
- **Lesson**: LLM-агенты визуально ревьюят, но пропускают паттерны которые легко грепнуть. Каждое архитектурное правило нуждается в конкретной `grep`-команде для reviewer-агента. Text rule без grep — это hope-based enforcement, не deterministic.
- **Example**: `domain-models.md:30` правило "No Context in domain models" — не поймано 4+ reviewer агентами, потому что Context был параметром функции, а правило читалось как "про поля класса". Фикс: grep `\bContext\s*[:,)]` + явное указание "как параметры, generic, return types".

### 2026-04-16 — pipeline-retrospective: Walking Skeleton ловит противоречия в rules раньше

- **Pattern**: Text-based Domain Contract discovers conflicts late
- **Lesson**: Если domain logic фиксируется только текстом в spec — противоречия между rules находятся в phase-01 implement (через 4 фазы после spec). Если spec генерирует исполняемый domain + JVM тесты — противоречия видны сразу через failing tests.
- **Example**: feature-voip domain rules противоречили в "can mute when state X" — обнаружено в реализации. С Walking Skeleton это было бы failing test на spec-фазе.

### 2026-04-16 — pipeline-retrospective: Hard limits провоцируют упрощение

- **Pattern**: AI simplification bias
- **Lesson**: Нейросети склонны упрощать сверх меры. Hard limits в инструкциях ("max 5 files", "max 5 hypotheses", "max 2 fix iterations", "5 min budget") усиливают эту склонность — агент охотно останавливается по лимиту даже когда нужно продолжать. Quality > speed; лучше escalation signals (повод спросить пользователя), чем hard stops.
- **Example**: первоначальная версия `domain-modeling/SKILL.md` имела "max 5 aggregates / 15 tests". Убрано — осталось "если объём выглядит чрезмерным для фичи, эскалация пользователю".

### 2026-04-16 — pipeline-retrospective: Peer DM — антипаттерн для delegation

- **Pattern**: Coordination overhead > work
- **Lesson**: Peer-to-peer DM между teammates оправдан только для genuine collaboration (обмен evidence, cross-verification гипотез). Для delegation-style работы subagent pattern дешевле. Team should send DM только для evidence/action, не для status/ack. Статус идёт через TaskUpdate.
- **Example**: Industry research (Anthropic blog, Addy Osmani) подтверждает: "peer DMs становятся антипаттерном, когда команды используют их для delegation вместо genuine collaboration".

### 2026-04-16 — pipeline-retrospective: Self-starting prompts обязательны

- **Pattern**: LLM default: first message = greeting
- **Lesson**: LLM-агенты по умолчанию трактуют первое сообщение как greeting и отвечают ack. Prompts в SendMessage должны явно содержать "Начни НЕМЕДЛЕННО, без ack. Это полное задание в этом сообщении." — иначе каждый assignment требует 2-3 round trips на wake-up.
- **Example**: feature-implement.md Phase 2.1 — промпты были "Прочитай ... Реализуй. Сообщи чеклист." → агенты отвечали "понял, приступаю" вместо работы.

### 2026-04-16 — pipeline-retrospective: Deterministic gates > text rules

- **Pattern**: Text rules игнорируются при compliance drift
- **Lesson**: "Build gate обязателен ДО review" как текст → агенты иногда начинают review по plan-файлам до build. `TaskCreate(..., addBlockedBy: [build_task])` технически блокирует reviewer tasks в TaskList как `blocked` — агент не может claim задачу пока build не completed. Это deterministic enforcement, не hope.
- **Example**: feature-implement.md Phase 2.3 — переведено на blockedBy pattern.

### 2026-04-16 — pipeline-retrospective: Hierarchical delegation обходит single-team limit

- **Pattern**: Platform constraint workaround
- **Lesson**: `TeamCreate` поддерживает одну team на session. Для параллельной работы над независимыми фазами — master lead спавнит phase-lead агентов (general-purpose, без team_name), каждый phase-lead создаёт свою sub-team. Context master не фрагментируется.
- **Example**: feature-implement.md "Parallel Phases via Hierarchical Delegation" section.

### 2026-04-16 — pipeline-retrospective: Ownership на scaffold-файлы

- **Pattern**: Parallel edits → merge conflicts
- **Lesson**: `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, `AndroidManifest.xml` (root) — файлы с высокой частотой параллельных правок. Без явного owner (backend-dev) test-dev и backend-dev спавнят edits одновременно. Другие teammates запрашивают изменения через SendMessage lead-у.
- **Example**: feature-implement.md Scaffold File Ownership section + дублировано в agent .md файлах.

### 2026-04-16 — pipeline-retrospective: Cross-feature dependencies требуют research-шага

- **Pattern**: Hidden coupling causes late architectural surprises
- **Lesson**: Connections между feature-модулями (direct imports, reflection calls, shared interfaces) нужно мапить на research-фазе отдельным codebase-researcher агентом. Без этого design принимает решения не зная о существующих contracts, и new coupling добавляется без awareness. Web-researcher через Context7 MCP проверяет recommended patterns для shared SDK.
- **Example**: feature-voip → feature-tcp прямой import SocketService. Ни research, ни design не знали о существующей reflection-связи в обратную сторону.

### 2026-04-16 — pipeline-retrospective: Android lifecycle — platform knowledge в rules

- **Pattern**: Generic platform rules vs feature-specific behavior
- **Lesson**: `onDestroy` vs `onStop`, foreground service поведение при сворачивании, `isFinishing && !isChangingConfigurations` — platform knowledge, применимо к ЛЮБОЙ Android фиче. Должно быть в `.claude/rules/lifecycle.md`, не в feature-specific spec. Spec описывает что "этот конкретный звонок живёт в background", rules описывают что "onDestroy не гарантирован".
- **Example**: `rules/lifecycle.md` создан с BAD/GOOD примерами + grep для architect-reviewer.
