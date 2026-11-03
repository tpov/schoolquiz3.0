---
description: Сплошной содержательный аудит квеста или отдельной темы (200 вопросов на тему) с починкой дефектов и гейтом до чистого.
argument-hint: <subject> [<s>-<t> | --all]
skills: quest-pipeline, quest-audit
---
Репозиторий проекта: /Users/tpov/schoolquiz3.0 — сначала выполни `cd /Users/tpov/schoolquiz3.0/scripts/seed-bulk` и загрузи скилл quest-pipeline.

Аудит: $ARGUMENTS (первый аргумент — предмет, второй — тема `<s>-<t>` или `--all`; по умолчанию `--all`).

Загрузи скилл quest-audit и действуй строго по нему. Для `--all` — 28 тем параллельными субагентами волнами (из терминала то же делает `./run-wave.sh audit <subject> -j 6 --all`), каждому: предмет, тема, пути к файлам, требование прочитать скилл quest-audit целиком, список дефектов из уже проверенных тем. После каждой темы — `node gate-lesson.js <subject> <s>-<t>` → `GATE: CLEAN`. В конце: `node format-for-firebase.js data/school/quest-<subject>.js` → ALL INVARIANTS OK, сводная таблица дефектов по категориям и по темам.
