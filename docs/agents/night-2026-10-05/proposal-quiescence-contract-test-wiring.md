# Proposal: подключение 17-кейсового контракт-теста quiescence в тестовую цепочку

schemaVersion: 1, capturedAtUtc: 2026-10-05T17:40:00Z, дерево: night-e1f @ b8bc23ff (ветка night/e1-followups).
Автор: ночной трек E. Это PROPOSAL — `scripts/package.json` ночью не менялся; применяется владельцем.

## 1. Что подключаем

Незакоммиченная правка владельца
`.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` (459 строк, дерево
mvp-candidate; читать можно, менять/коммитить нельзя — RED-оракул владельца). Против
закоммиченной версии на b8bc23ff (428 строк) она добавляет (проверено `diff`):

- хук `FAKE_PREFLIGHT_QUIESCENCE_JSON` в fake preparation-preflight reader (строки 159–161
  версии владельца), позволяющий подать произвольный quiescence-объект;
- тест `testInvalidQuiescenceStopsBeforePreparationAndTransportCut` (строки 389–407):
  некорректный quiescence должен остановить прогон с exit 2 ДО подготовки и обрезки
  транспорта (`PRE_CUT_CHILD_FAILED`, `cut.attempted=false`, wifi/data не тронуты,
  mappings целы, Maestro не вызывался, prepared-ticket.json не создан);
- матрицу 17 некорректных кейсов (строки 446–455): `{}`, `null`, `[]`, `[0,0,0,0,0]`,
  `""`, лишний ключ, строковое `"0"`, для каждого из 5 счётчиков — отсутствие и `=1`
  (7 + 5×2 = 17).

Зависимости теста — только node builtins (`node:assert/strict`, `node:crypto`,
`node:child_process`, `node:fs`, `node:os`, `node:path`) и
`../../../scripts/arena-e2e-pending-settlement-reader` → `functions/arena-tickets`
(тоже только builtins) — `npm install` НЕ нужен, тест зелёный при отсутствии
`scripts/node_modules` (как и вся текущая цепочка, см. комментарий в `build.gradle.kts:56-59`).

## 2. Почему именно scripts/package.json

`scriptsTest` (`build.gradle.kts:60-66`) запускает `npm test` в `scripts/` и входит в
`ciCheck` (`build.gradle.kts:76`). Один добавленный узел в npm-цепочку автоматически
включает контракт-тест в канонический локальный гейт — Gradle-скрипты менять не нужно.

## 3. Обязательное предусловие: сначала закоммитить тест владельца

Тест должен быть закоммичен ВЛАДЕЛЬЦЕМ (это его незакоммиченный RED-оракул, ночь его
не коммитит) ДО применения диффа. Иначе `npm test` в чистом checkout молча запустит
старую 428-строчную версию (она существует в git) — цепочка зелёная, но 17-кейсовая
матрица не исполняется: ложное покрытие. Порядок: (1) коммит теста владельцем,
(2) применение диффа ниже.

## 4. Дифф подключения

```diff
--- a/scripts/package.json
+++ b/scripts/package.json
@@ -9,8 +9,8 @@
   "scripts": {
     "test:arena-wallet:emulator": "node mvp-arena-wallet-read-emulator-test.js",
     "test:arena-eligible-universe:emulator": "node arena-eligible-universe-emulator-test.js",
-    "test": "node sync-change-id.test.js && node content-catalog-index.test.js && node redact-existing-questions.test.js && node backfill-sync-change-form.test.js && node seed-sync-list-test-data.test.js && node mvp-arena-catalog-ui-oracle.test.js && node arena-e2e-tools.test.js && node arena-e2e-ticket-discovery.test.js && node arena-e2e-rating-id-reader.test.js && node arena-e2e-attempt-progress-reader.test.js && node arena-e2e-pending-settlement-reader.test.js && node arena-e2e-preparation-preflight-reader.test.js && node arena-e2e-legacy-settlement.test.js && node ../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js",
-    "lint": "node --check backfill-sync-change-form.js && node --check backfill-sync-change-form.test.js && node --check seed-sync-list-test-data.js && node --check seed-sync-list-test-data.test.js && node --check sync-change-id.js && node --check seed-hierarchy.js && node --check seed-lesson-runner-samples.js && node --check seed-lesson-runner-extra.js && node --check seed-criptico-course.js && node --check bump-all-content.js && node --check seed-bulk/shared.js && node --check seed-bulk/seed-bulk-quests.js && node --check seed-bulk/retire-quest.js && node --check seed-bulk/verify-seeded-quest.js && node --check mvp-arena-catalog-ui-oracle.js && node --check mvp-arena-catalog-ui-oracle.test.js && node --check arena-e2e-evidence.js && node --check arena-e2e-context-reader.js && node --check arena-e2e-receipt-oracle.js && node --check arena-e2e-tools.test.js && node --check arena-e2e-ticket-discovery.js && node --check arena-e2e-ticket-discovery.test.js && node --check arena-e2e-rating-id-reader.js && node --check arena-e2e-rating-id-reader.test.js && node --check arena-e2e-attempt-progress-reader.js && node --check arena-e2e-attempt-progress-reader.test.js && node --check arena-e2e-pending-settlement-reader.js && node --check arena-e2e-pending-settlement-reader.test.js && node --check arena-e2e-preparation-preflight-reader.js && node --check arena-e2e-preparation-preflight-reader.test.js && node --check arena-eligible-universe-emulator-test.js && node --check arena-placement-private-rules.test.js && node --check arena-e2e-legacy-settlement.js && node --check arena-e2e-legacy-settlement.test.js && node --check mvp-qualification-calculation-firestore-test.js && node --check ../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.js && node --check ../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js"
+    "test": "node sync-change-id.test.js && node content-catalog-index.test.js && node redact-existing-questions.test.js && node backfill-sync-change-form.test.js && node seed-sync-list-test-data.test.js && node mvp-arena-catalog-ui-oracle.test.js && node arena-e2e-tools.test.js && node arena-e2e-ticket-discovery.test.js && node arena-e2e-rating-id-reader.test.js && node arena-e2e-attempt-progress-reader.test.js && node arena-e2e-pending-settlement-reader.test.js && node arena-e2e-preparation-preflight-reader.test.js && node arena-e2e-legacy-settlement.test.js && node ../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js && node ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js",
+    "lint": "node --check backfill-sync-change-form.js && node --check backfill-sync-change-form.test.js && node --check seed-sync-list-test-data.js && node --check seed-sync-list-test-data.test.js && node --check sync-change-id.js && node --check seed-hierarchy.js && node --check seed-lesson-runner-samples.js && node --check seed-lesson-runner-extra.js && node --check seed-criptico-course.js && node --check bump-all-content.js && node --check seed-bulk/shared.js && node --check seed-bulk/seed-bulk-quests.js && node --check seed-bulk/retire-quest.js && node --check seed-bulk/verify-seeded-quest.js && node --check mvp-arena-catalog-ui-oracle.js && node --check mvp-arena-catalog-ui-oracle.test.js && node --check arena-e2e-evidence.js && node --check arena-e2e-context-reader.js && node --check arena-e2e-receipt-oracle.js && node --check arena-e2e-tools.test.js && node --check arena-e2e-ticket-discovery.js && node --check arena-e2e-ticket-discovery.test.js && node --check arena-e2e-rating-id-reader.js && node --check arena-e2e-rating-id-reader.test.js && node --check arena-e2e-attempt-progress-reader.js && node --check arena-e2e-attempt-progress-reader.test.js && node --check arena-e2e-pending-settlement-reader.js && node --check arena-e2e-pending-settlement-reader.test.js && node --check arena-e2e-preparation-preflight-reader.js && node --check arena-e2e-preparation-preflight-reader.test.js && node --check arena-eligible-universe-emulator-test.js && node --check arena-placement-private-rules.test.js && node --check arena-e2e-legacy-settlement.js && node --check arena-e2e-legacy-settlement.test.js && node --check mvp-qualification-calculation-firestore-test.js && node --check ../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.js && node --check ../.maestro/mvp/arena-e2b/run-arena-legacy-recovery.test.js && node --check ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js"
   },
```

Суть: в `test` добавляется ` && node ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js`
(последним узлом, после arena-e2b), в `lint` — ` && node --check ../.maestro/mvp/arena-matrix/offline-settlement-contract.test.js`.
Больше ничего не меняется; `npm run test:arena-wallet:emulator` и пр. не затрагиваются.

## 5. Проверено этой ночью (все команды исполнены)

| Проверка | Команда | Результат |
|---|---|---|
| node на VPS | `node --version` | `v24.14.0` (`/workspace/infrastructure/zcode/3.14.3/node24/…/bin/node`) |
| npm на VPS | `npm --version` | `11.9.0` |
| Тест владельца дословно, контейнер node:22, repo :ro, без сети | `docker run --rm --network none -v …/mvp-candidate:/repo:ro node:22-bookworm-slim node /repo/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` | `offline settlement composition tests: PASS`, exit 0, 2m34.9s |
| Тест владельца дословно, хостовый node v24 | `node …/mvp-candidate/.maestro/mvp/arena-matrix/offline-settlement-contract.test.js` | `offline settlement composition tests: PASS`, exit 0, 2m30.6s |
| Baseline цепочки до диффа | `cd …/night-e1f/scripts && npm test` | exit 0, 2.588s (14 файлов, последний — run-arena-legacy-recovery.test.js, 16/16) |
| Дифф применён in-memory к копии package.json | node-скрипт с `JSON.parse` результата | merged JSON valid: true; хвосты `test`/`lint` заканчиваются на `offline-settlement-contract.test.js` |
| Зависимости идентичны b8bc23ff ↔ рабочее дерево владельца | `diff` leaf/guard/runner/reader и 5 flows | все IDENTICAL; единственные незакоммиченные правки владельца — сам тест и `local.properties` (git status mvp-candidate) |

Режимы в git: runner и leaf — `100755` (исполнимые), тест — `100644` (запускается через
`node`, бит не нужен). Тест пишет фикстуры в `os.tmpdir()` и исполняет фейковые
исполнимые с mode 0o700 — POSIX (Mac владельца и VPS подходят; Windows не поддерживается
и текущей цепочкой тоже).

## 6. Влияние на время и что проверка НЕ добавляет

- Тест добавляет ~2,5 минуты к `npm test`/`scriptsTest` (baseline 2,6s → ~2,6 min).
  Для ciCheck (полный Gradle-гейт, десятки минут) это приемлемо; на быстрый локальный
  `npm test` заметно. Вариант при нежелании — отдельный npm-скрипт
  `test:offline-settlement-contract` вне цепочки `test` (НЕ рекомендуется: выпадет из
  канонического гейта, повторив судьбу теста до 04.10).
- Подключение автоматизирует только этот композиционный тест (фейковые adb/maestro,
  без сети и устройств). Оно НЕ добавляет: покрытие реального adb/Maestro, проверку
  мультиплатформенности (Windows), и не заменяет истории 1.4 (лист-фикс уже закоммичен
  в 8f8356ac и подтверждён revert-check'ом 04.10).

## 7. Чего ночь НЕ делала

- НЕ меняла `scripts/package.json` (реальный файл) — только in-memory проверка диффа.
- НЕ коммитила и не правила тест владельца (дословное чтение + запуск через docker/node).
- НЕ запускала полный `npm run lint` (только подтвердила синтаксис теста фактическим
  исполнением; `node --check` узел из диффа не прогонялся отдельно).
