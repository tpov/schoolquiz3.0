# UX-O6 annotation/redaction serialization contract (story 4.4, epic-authoring-review) — 2026-10-09

Story 4.4 («ux-o6-annotation-serialization»): общая схема/единицы/идентичность аннотаций и
redaction-сериализация зафиксированы **до** любых editor-адаптеров (гейт contract-map,
«Remaining gates» → UX-O6). Никакого продукта этой историей не строится — только контракт и
закрепляющие тесты с этого дерева.

## Deliverables

- **Контракт (полная редакция):** `_bmad-output/mvp-candidate/epic-authoring-review/ux-o6-annotation-contract.md`
  — 8 разделов по чек-листу гейта: §2 схема FillBlank/единицы (маркер `***…***`, позиционная
  связка маркер↔blank; blank ≠ correctness ≠ защита); §3 идентичность аннотации
  `(questionId, revisionId, field, occurrence)` (AD-27: вхождение не распространяется на другие
  одинаковые слова); §4 правки/undo/redo/слоты (текст и метки согласованно; пустой новый слот ≠
  стёртый существующий; неполный невалидный черновик сохраняется); §5 переводная защита — общий
  этап после генерации урока, вне wire-payload, элемент `{questionId, field, occurrence, length}`;
  §6 redaction-сериализация и миграция wire/Room (фикстурная фиксация обеих сторон — единственный
  путь изменения; Room v15→v16+ только добавлением, payload байт-в-байт); §7 player/exam redaction
  по-прежнему снимает все ключи ответов; §8 границы контракта.
- **Закрепляющие тесты:** `shared/core/question-schema/src/jvmTest/…/UxO6AnnotationSerializationContractTest.kt`
  — 5 кейсов: (1) единицы схемы — границы 1–3 blanks / 5-10 кандидатов, дубликаты id, ссылка мимо
  пула, защита без пустых строк; (2) redacted blanks — голые id в порядке маркеров, без
  `blankToCandidate`/`correctCandidateId`; (3) корпус фикстур не несёт ни одного forbidden-ключа и
  не публикует выписанный ответ рядом с перемешанным пулом; (4) переводная защита без ответа
  переживает redaction, отсутствующие сегменты = отсутствие ключа (не пустой массив); (5)
  типизированные авторские id (`BlankId`/`CandidateId`) не склеиваются со строками redaction
  (`rc-…` не доходят до авторского парсера).

## Gates (оба зелёные)

- `sgw ./gradlew :shared:core:question-schema:jvmTest --no-configuration-cache` — BUILD
  SUCCESSFUL; новый класс 5/5 (`tests="5" skipped="0" failures="0" errors="0"`), suite модуля
  целиком зелёный (включая `RedactedQuestionWireTest` — байтовая фиксация не задета).
  Лог: `gate-question-schema-jvmTest.log`.
- `functions`: `node run-tests.js` — **92 of 92 suites passed**; кросс-языковые фикстуры
  (`redacted-question-fixtures.json`, читаются Kotlin- и Node-стороной) целы.
  Лог: `gate-functions-92.log`.

## Scope notes

- Тесты закрепляют **правила поверх существующих фикстур**; байты формы уже держат
  `RedactedQuestionWireTest` + `functions/question-redaction-wire.test.js` (не дублируются).
- Утверждение редакции владельцем — не deliverable этой истории (в эпике-4 нет adopt-истории,
  цикл как у 2.9: решение владельца фиксируется отдельным батчем); до утверждения редакция
  нормативна для подготовительных работ (заголовок контракта).
- Источники контракта (первичные, прочитаны 2026-10-09) перечислены в шапке документа: AD-27
  хребта 2026-09-29, UX-18, схема `shared/core/question-schema`, эмиттер
  `functions/question-redaction.js`, `ArenaHardQuestionShape.kt`, SPEC Constraints, инцепция
  эпика 4.
