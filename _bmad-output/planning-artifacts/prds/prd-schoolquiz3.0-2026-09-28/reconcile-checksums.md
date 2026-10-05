# Reconciliation: CHECKSUMS.sha256

Статус: независимая входная сверка перед quality gate, не Finalize/runtime acceptance. Дата: 2026-09-28.

Источник: /workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/CHECKSUMS.sha256

SHA-256 входа: 82c4dd19cf87fa17988152f2dbbecce52ce848de491b924dec87c53612271a55.

Сопоставлено с prd.md, addendum.md, prd-consolidated-addendum.md, evidence-inventory.md. Проверенный срез и SHA этих четырёх файлов записаны в memlog-audit.md; приведённые строки относятся к нему. «Companion» означает prd-consolidated-addendum.md. Приоритет: актуальные явные решения владельца → PRODUCT-CONTRACT → непротиворечащая история. Основные файлы и код этой сверкой не менялись.

## Результат: no gaps; целостность пакета подтверждена в узком смысле

Прочитаны все17 записей CHECKSUMS.sha256; все17 именованных файлов существуют в переданном VPS-пакете, SHA-256 каждого совпал. Это read-only сверка документов, не тесты приложения.

- CHECKSUMS.sha256:1–12 перечисляет ТЗ00–11; :13 CONTEXT-AND-STATE, :14 PRODUCT-CONTRACT, :15 README, :16 SOURCE-MAP, :17 STATE-MANIFEST.
- Этот внутренний checksum-файл не содержит самого себя и добавочного10A-QUANTITY-REFUND-EVIDENCE.md. Это не противоречит19-file transfer claim из HANDOFF.md:35: речь там о внешней сверке всех19 файлов оригинального пакета. Нельзя писать «CHECKSUMS подтверждает19» — он подтверждает17.
- Source-inventory.json отдельно содержит все22 входа (19package+HANDOFF+plan+report), включая hash CHECKSUMS и10A. Evidence-inventory.md:9 именно различает инвентарь22 и сообщённый transfer19; не приписывает19 строкам CHECKSUMS.
- Addendum.md:29–30, prd.md:471,494–496,515 и evidence-inventory.md:11–20 корректно отделяют hash/inventory от runtime, installable backup и текущего acceptance.
- Внутренние SHA исходников STATE-MANIFEST исторические; сегодняшний22same/18different — отдельная сверка в reconcile-state-manifest.md, а не ошибка checksum переданных документов.

Минимальных исправлений не требуется. CHECKSUMS не содержит самостоятельного продуктового scope, не исполнялся как installer и не служит разрешением restore/import/deploy.
