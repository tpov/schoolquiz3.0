# Reconciliation: CONTEXT-AND-STATE.md

Статус: независимая входная сверка перед quality gate, не Finalize/runtime acceptance. Дата: 2026-09-28.

Источник: /workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/CONTEXT-AND-STATE.md

SHA-256 входа: 9d1eb166bbb3fbd2a6eac3acd4389ebdb8ae13b3c4effd6039e585189568c259.

Сопоставлено с prd.md, addendum.md, prd-consolidated-addendum.md, evidence-inventory.md. Проверенный срез и SHA этих четырёх файлов записаны в memlog-audit.md; приведённые строки относятся к нему. «Companion» означает prd-consolidated-addendum.md. Приоритет: актуальные явные решения владельца → PRODUCT-CONTRACT → непротиворечащая история. Основные файлы и код этой сверкой не менялись.

## Покрытие состояния и границ

- CONTEXT-AND-STATE.md:5–18: baseline550a640, thinmirror40, draft/merge/schema13 — исторический срез. Prd.md:539, evidence-inventory.md:7,24–40 и addendum.md:7,27–30 используют актуальный candidate, не повторяют merge/import и не реставрируют прежние байты.
- :20,48–49,61–62,66–70: чужая среда не тестовая по умолчанию, protected retained data/credentials, matchingcert update без wipe, dirty/log preservation/additive migrations → prd.md:447–451,459,471,494–495,513,531; companion:13,25,70,74–75. Старые APK/cert/device/container addresses :41–49 не являются current identities.
- :24–37: 340Node,1151+skip Kotlin с reused,5catalog,failedshell/retestpending,oldAPK,migration/Homepass,financialoracle/Playnotrun,ciCheckunproven — сохраняются только историей источника. Ни одна сумма не превратилась в сегодняшнее принятие; evidence-inventory.md:11–20,24–40,92–94.
- :53–59: архитектура/AGENTS, однаheavybuild, preparedtoolchain, фактическиеsource sets/behavior gates → addendum.md:11–19; companion:21–25,74. Старые wrappers/флаги/точные порты не приказывают запустить старое окружение.
- :72: recovery/refunds не выключаются new-sales flag, production/KMP/publication отдельны → prd.md:393–409,481; companion:64,66.

## Найдены 2 неполно перенесённые технические guardrails

1. **Конкретные ограничения будущего demo-стенда укорочены.** Source:54,60 запрещает production/GAC, открытые публичные сервисные/ADB порты и потерю данных, ограничивая работу demo. Companion:75 запрещает production credentials/real purchases и prd.md:451 сохраняет данные, но запрет production/GAC/публичной экспозиции стенда не сформулирован целиком. Минимум в addendum: сохранить demo-only и запрет production/GAC/публичного ADB/сервисных портов; действительные имена/адреса/сеть устанавливать по будущему registry, не копировать snapshot. Не менять конфигурацию и не объявлять текущий стенд проверенным.
2. **Специфические правила контрактных тестов сведены к общему behavioral требованию.** Source:58 требует mapper checks каждого поля с ненулевыми значениями (особенно reserves), client/server test для новых wire names и регистрацию новых Node tests в настоящем runner. Companion:23 и prd.md:496 сохраняют fakes/behavior/fresh gates, но эти три условия не названы. Минимум: одна техническая строка в addendum с точным source reference; без новых продуктовых FR или запуска тестов.

Это сохранение уже заданных инженерных ограничений, не новые продуктовые blockers. Исторические команды не оперативны; pause и поздний выбор физических телефонов имеют приоритет.

## Статус после исправлений root

2026-09-28T13:16:37.416406+00:00

Оба технических carry-forward внесены root в canonical addendum.md:192–194: demo-only, запрет production Google Application Credentials/public ADB/public testing ports; все mapper fields и wire-contract, обязательная регистрация новых server tests в настоящем Node runner. No remaining gaps этого входа; это инженерные ограничения, не новые продуктовые правила и не выполненные тесты.

Срез SHA зафиксирован в позднем дополнении memlog-audit.md.
