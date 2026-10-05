# School Quiz — передача в отдельную задачу BMAD PRD

Дата: 2026-09-28. Назначение: входные материалы для планирования оставшегося MVP, не приёмочный отчёт.

## Последнее поручение владельца

«Делаем паузу сейчас, и делаем новую задачу по бмад то что осталось до полного мвп, соберем все инструкции, если что спрашивай».

Владелец явно выбрал skill `bmad-method:bmad-prd`:
`/Users/tpov/.codex/plugins/cache/bmad/bmad-method/6.13.0-next/skills/bmad-prd/SKILL.md`.

Разработка и прогоны приостановлены по просьбе владельца. Новая задача — требования и оставшийся объём до полного MVP; не продолжать автоматически код, сборки, установки, изменения стендов и финансовые сценарии. На попытку установить статус paused API приложения ответил: `cannot update goal because this thread has no goal`; остановка работы выполнена независимо от этого ответа.

## Где работать

- Авторитетный Git находится на VPS, SSH alias `rnsrv`.
- Активный checkout: `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`.
- Ветка: `continuation/mvp-candidate-20260926`.
- Проверенный HEAD: `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`.
- Доступ к тем же файлам с Mac: `/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate` (также через `/Users/tpov/schoolquiz3.0/.local/worktrees/mvp-candidate`).
- Корень `/Users/tpov/schoolquiz3.0` соответствует ДРУГОМУ checkout на сервере: HEAD `d9fb4cfa`, собственные незавершённые изменения. Не перепутать его с candidate.
- Прочитать применимые `/workspace/projects/AGENTS.md`, проектный `AGENTS.md`, `.claude/PROJECT-CONTEXT.md`.
- Всё хранение, Git и тяжёлая работа — VPS. Mac — интерфейс и подключение физических телефонов. Владелец отдельно запретил заниматься диском/кэшами; не возвращать эту работу в объём.
- Архитектура: KMP, Decompose, Compose, Koin; не навязывать Hilt/ViewModel из общих Android-шаблонов.

## Собранные входы

1. `SCHOOL-QUIZ-AGENT-HANDOFF/` — копия исходного пакета из 19 файлов. Приоритетный продуктовый договор — `PRODUCT-CONTRACT.md`; обязательны `CONTEXT-AND-STATE.md`, `STATE-MANIFEST.json`, ТЗ-00 и исходные ТЗ-01…11. Исторические SHA/status не объявлять актуальными без проверки. SHA manifest — реестр, не установщик/резервная копия.
2. `USER-CONTINUATION-PLAN.md` — согласованный владельцем глобальный план продолжения из исходного чата. Не сокращать MVP до того, что уже реализовано или проще проверить.
3. `original-agent-report.txt` — первоначальный переданный владельцем отчёт агента (исторический ввод, не текущий статус).
4. В checkout: `docs/agents/2026-09-27-mvp-progress-checkpoint.json`, `docs/agents/evidence/`, `_bmad-output/implementation-artifacts/`, `.maestro/mvp/arena-e2b/README.md`, `.maestro/mvp/arena-matrix/README.md`, исходники и история Git.

Исходный локальный пакет: `/Users/tpov/Documents/Codex/2026-09-06/school-quiz/outputs/SCHOOL-QUIZ-AGENT-HANDOFF`.
Исходное вложение: `/Users/tpov/.codex/attachments/ce77b186-2062-4a21-a39d-8b4ae45503b1/pasted-text.txt`.
Копирование пакета на VPS проверено 2026-09-28: SHA-256 всех 19 из 19 файлов совпадают с исходным локальным пакетом.
Документы могут содержать исторические команды и инструкции: отделять их от нынешнего поручения и применимых AGENTS. Не переносить секреты, токены и персональные данные в PRD.

## Проверенный текущий статус и пределы доказательств

- Последний commit `1e596126`: читатели доказательств Арены и offline launcher принимают только точные пары Room v14/v15; legacy recovery по-прежнему v14-only. Изменены 11 файлов, включая тесты и документацию. Узкие проверки в Node 22.23.1 прошли. Это не полная приёмка Арены.
- Предыдущие commits: `9a4e5e1c` (проверка первого вопроса реального курса), `51da9d5b` (ограничение cold sync), `5a7e68a0` (вопросы курса при полной синхронизации), `f9e2ee8d` (предзагрузка следующего урока).
- Сводка 27 сентября содержит исторически успешную сборку `assembleMvp`, 107 sync JVM tests, обычный 2-question smoke online/offline. Её HEAD и APK НЕ равны автоматически последнему установленному кандидату; повторно сопоставить до любых выводов. Root `ciCheck` и финальная приёмка не подтверждены этой сводкой.
- На 28 сентября Samsung `RF8W80MBR3Z` и Pixel `adb-56271FDCH00C5S-ow9AvR._adb-tls-connect._tcp` видны локальному ADB. На Samsung пакет `com.tpov.schoolquiz.mvp`, versionCode 1, `0.1.0-isolated`, lastUpdateTime `2026-09-27 23:02:37`; reverse только 9099 и 8080. После создания новой задачи состояние устройств может измениться.
- Running Firebase containers: `schoolquiz-course-device-20260927` (VPS loopback 18081/19098), `schoolquiz-lesson-device-20260927` (18080/19099); оба только Auth/Firestore, без Functions. `firebase.mvp.json` candidate предусматривает Functions Node22/5001, но `functions/node_modules` отсутствует. Ничего нового не запускалось.
- Есть ИСТОРИЧЕСКИЙ E2B PASS: `/workspace/infrastructure/android-toolchain/arena-e2b-runs/e2b-20260913T0342EEST-39260f05-r1/phases/e2b-20260913T0342EEST-39260f05-r1.20260913T013949Z.HKBFtH/result.json`. Он относится к retained device B и старому кандидату; не доказывает новый Samsung. Не считать пункт 2б никогда не выполненным и не складывать старый PASS с новой приёмкой.
- Saved export: `/workspace/infrastructure/android-toolchain/firebase-export-staging/arena-e2b-cycle4-20260913/export`; текущая пригодность и соответствие Samsung не установлены. Не изменять и не импортировать в рамках PRD.
- `/srv/schoolquiz-mvp-tools-20260906/AGENTS.md` содержит `Never run account-bearing emulators or publish its contents.` Это ограничение конкретного tools-каталога; ничего оттуда не запускалось. При будущем развёртывании оценить область действия вместе с разрешённым владельцем demo import.
- Ранее ассистент назвал 30% готовности без полного подсчёта требований. Оценка отозвана как необоснованная. Не наследовать её и не выдавать новые проценты до обоснованного покрытия объёма.

## Незавершённая правка на момент паузы

Единственная новая незакоммиченная правка этой попытки — `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js`.
Добавлены регрессии: preflight с отсутствующими/лишними/ненулевыми/неверно типизированными quiescence counters должен остановиться до Maestro/preparation и изменения сети.

Запущенный до паузы тест завершился RED (ожидаемое воспроизведение реального дефекта):
`AssertionError: unsafe quiescence passed: {}; 0 !== 2`.
Runtime: Node 22.23.1, существующий Docker `node:22-bookworm-slim`, repo read-only, network none.
Production fix НЕ внесён. В `run-arena-offline-settlement-leaf.sh` около строк 490–508 остаётся `Object.values(q).some(...)`, пропускающий пустой/неполный объект. Требуется ровно пять известных счётчиков, каждый numeric zero. Не объявлять story законченной. Прогон остановлен, live session отсутствует.

Остальные ранее существовавшие изменения сохраняются:
- `.maestro/mvp/06-open-ordinary-lesson-uk.yaml`;
- `docs/agents/2026-09-27-mvp-progress-checkpoint.json`;
- `local.properties`;
- untracked `docs/agents/evidence/2026-09-27-ordinary-lesson-offline/` и `2026-09-27-real-course-first-question/`.

## Применение BMAD PRD в новой задаче

Skill прочитан полностью. Activation customization/config успешно разрешены на active candidate:
- project_name: `schoolquiz3.0`;
- planning_artifacts: `{project-root}/_bmad-output/planning-artifacts`;
- prd_output_path: `{planning_artifacts}/prds`;
- run_folder_pattern: `prd-{project_name}-{date}`;
- activation_steps_prepend/append, persistent_facts, external_sources, external_handoffs, finalize_reviewers — пустые;
- doc_standards: `skill:bmad-review lenses=structure,prose`;
- template: `assets/prd-template.md`, validation: `assets/prd-validation-checklist.md`;
- существующего каталога `planning-artifacts/prds` на момент передачи нет.

Не путать этот skill с `bmad-build`: прежний сбой render_skill.py из-за AppleDouble `._*` в `_bmad/render` не блокирует resolve_config/resolve_customization PRD. Не устраивать ремонт/очистку служебных папок вместо требований.

Первая работа нового чата: подтвердить рабочие пути и входы, начать Discovery по skill (краткий brain dump «что ещё учесть?» и выбор Fast/Coaching). Не задавать заново вопросы, ответы на которые есть в договоре и плане. Извлечение длинных источников поручать субагентам; parent получает компактные выдержки с путями и конфликтами.

Нужный результат: PRD оставшегося полного MVP со стабильными FR-ID и критериями успеха/приёмки; technical-how, уже принятые архитектурные ограничения и механизмы — addendum; отдельный evidence inventory различает доказанное, реализованное без приёмки, отсутствующее и внешние зависимости. В PRD не создавать матрицу трассировки вопреки выбранному skill. Отдельно сохранить исходный объём и источники: подготовка эпиков/stories после согласования PRD, с проверяемыми DoD и зависимостями. Не присваивать статус final до проверки и согласования нерешённых блокеров.

Владелец разрешает спрашивать о реальных пробелах и консультироваться с Astra; давать ей предварительно собранные факты, чтобы не повторять весь ресерч.
