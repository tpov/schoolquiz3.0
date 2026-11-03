---
description: Состояние конвейера квестов — какие предметы/уроки написаны, что подключено в каталог, что проходит инварианты.
skills: quest-pipeline
allowed-tools: Read, Bash
---
Репозиторий проекта: /Users/tpov/schoolquiz3.0 — сначала выполни `cd /Users/tpov/schoolquiz3.0/scripts/seed-bulk` и загрузи скилл quest-pipeline.

Из `scripts/seed-bulk/` выполни `node progress.js --all`, прочитай `data/school.js`, и для каждого подключённого квеста — `node format-for-firebase.js data/school/quest-<subj>.js`. Сведи в таблицу: предмет | уроков/140 | спек/140 | в каталоге | инварианты. Правок не делай.
