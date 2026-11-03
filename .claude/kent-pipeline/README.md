# Quiz Feature Pipeline → Kent (миграция)

Claude-Code-пайплайн (7 команд `feature-*`, 21 субагент, 8 скилов, 11 rules, 6 hooks)
перенесён в Kent-воркфлоу.

- Kent workflow: **Quiz Feature Pipeline** — UUID `a6a6f80c-1295-449a-bb5d-81f28f5e6f0d`
- Kent project: **schoolquiz3.0** — `project-8ef35b4b-0bae-489a-9be1-bad97138eb65` → /Volumes/EXTERNAL/schoolquiz3.0 (default workflow)
- Граф: 80 нод (1 start `backlog`, 69 agent, 9 join, 1 terminal `done`), 132 ребра, 6 approval-рёбер, 5 continue_session judge-рёбер. Валиден в draft/task_creation/execution.
- Новая роль субагента `domain-designer` добавлена в ~/.kent/config.toml.
- `quiz-pipeline-kent-design.md` — полный build-spec (спайн, все ноды и рёбра с промптами, ограничения переноса §6, CLI §7).

Запуск задачи: `kent task create --title "<фича>" --body "<описание>" --project project-8ef35b4b-0bae-489a-9be1-bad97138eb65`
(баг-репорт на готовой фиче интейк-нода сама уводит в debug-ветку).
