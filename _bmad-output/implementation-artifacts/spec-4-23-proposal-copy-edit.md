---
title: '4.23 proposal-copy-edit — «Предложить правку»: копия без секретных ключей и применение одобренной правки (CAP-27, AD-33, UX-23)'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'full'
route_source: 'auto'
review: 'quick'
review_source: 'auto'
lenses_ran: []
review_loop_iteration: 0
baseline_commit: 'd074873e'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless renegotiated by the owner">

## Intent

**Problem:** Второй вход CAP-27 — «Предложить правку» на чужом уроке — не существует. Сервер должен сам собрать редактируемую копию из проекции игрока без секретных ключей (HARD, Арена, экзамены): предложивший отмечает верный ответ сам, «Факты» его проверяет, а одобрение автора атомарно применяет ровно проверенную редакцию как новую редакцию урока — автор прежний, предложивший записан соавтором правки, замороженные банки попыток не переписываются.

**Approach:** Серверная половина в этой смене: callable `buildLessonEditCopy` (копия из опубликованных вопросов: для вопросов, которые redact() умеет разделить — публичная половина `SingleChoiceRedacted`/…/`FillBlankRedacted` без ключа; Survey и легаси-диалекты едут запертыми (`editable: false`) в том виде, в котором уже открыты миру; документ `question_keys` копией не читается вовсе) и применение одобренной правки в `decideLessonProposal` (ветка COPY_EDIT вместо промежуточного `PROPOSAL_APPLY_PENDING` из 4.21): транзакция пишет вопросы новой редакцией (версии из entity-version), пересобирает документ ключей урока из новых payload, поднимает версию урока и зеркалит её в приватный документ автора (правило writeQuestVersionMirror), пишет журнал sync-changes по узлам публикации, предложившего — arrayUnion в `contributorUids` урока; запертые вопросы проверяются байт-в-байт (чужая правка запертого — именованный отказ). Клиентская половина (редактор копии, отметка ответов, подача через `ProposalOutboxWriter` соседней истории 4.22, цитата платы 100×изменённые) ставится на инфраструктуру 4.22 после её лендинга.

**Зависимости:** 4.21 done (d074873e — приёмка COPY_EDIT с платой, решение автора, STALE). 4.22 — соседняя смена в той же волне (spec-4-22 in-progress): её outbox-писатель и транспорт цитаты — фундамент клиентской половины; файлы 4.22 не трогаются до её коммита.

## Boundaries & Constraints

**Always:**
- Копию собирает сервер из документов вопросов, которые уже публичны: redact() отдаёт публичную половину (ключ отбрасывается, не хранится в копии), документ ключей урока (`question_keys`) не читается — секретные ключи HARD/Арены/экзаменов в копию не попадают по построению.
- Граница честности (записывается в спеку): публичный payload вопросов не-redactable-диалектов (легаси, Survey) и сегодня несёт ключ всему миру (красакция публикации ещё не включена — publicDocuments пишет payload как есть); копия не добавляет к этому ничего: такие вопросы едут запертыми и байт-в-байт неизменными.
- Применение — ровно проверенная редакция: база сверяется STALE-правилом 4.21 (baseRevision против версии урока), запертые вопросы — байт-в-байт с базой, идентификаторы вопросов предложения обязаны существовать в уроке (чужой id — именованный отказ, инъекция в дерево закрыта).
- Автор урока не меняется; предложивший — `contributorUids` (arrayUnion) + запись предложения как атрибуция; версия урока поднимается и зеркалится в приватный документ автора (AD-11: где назначается версия, там же и зеркалится).
- Выданные попытки сохраняют банки: применяются только документы дерева (questions/lessons/keys) — замороженные попытки и issued-банки не читаются и не пишутся (AD-3).
- Плата 100×изменённые взята при подаче (4.21); решение автора бесплатно; повтор применения невозможен (статус APPROVED одноразовый, PROPOSAL_NOT_ACTIONABLE).

**Never:**
- Никаких правок приёмки/воркера/решения сверх ветки COPY_EDIT в `decideLessonProposal`; никаких новых правил безопасности; никаких деплоев.
- Никаких изменений запертых вопросов (байт-в-байт); никакой правки мира 4.22 до её коммита; никаких поверхностей «Предложений» (4.25) и целевых путей (4.24).
- Никакой правки текста модели: конвейер уже отработал к моменту применения; apply не вызывает модель.

</frozen-after-approval>

## Code Map

- **New `functions/lesson-copy-edit.js`** — чистый модуль: `buildEditCopy({lesson, questions})` (redact-половины, `editable`/`locked` разметка, view `lesson-edit-copy-v1`), `parseEditCopyRequest` (тело callable), `validateEditProposalAgainstBase({baseQuestions, proposalQuestions})` (ids ⊆ база; запертые — байт-в-байт; возвращает именованные отказы), `mergedQuestionsForApply` (пропозал поверх базы).
- **`functions/index.js`** — `exports.buildLessonEditCopy` (auth, профиль, чужой урок, вопросы `where lessonId`, копия; своё = `PROPOSAL_TARGET_OWN`, неопубликовано = not-found); ветка COPY_EDIT в `decideLessonProposal` → `applyCopyEditProposal` (транзакция: вопросы новой редакцией с версиями, ключи урока `questionKeyDocuments`, версия урока + `contributorUids`, зеркало версии автору, sync-changes quest/lesson/question, предложение → APPROVED).
- **New `functions/lesson-copy-edit.test.js`** — копия (редактируемые без ключа, запертые как опубликованы, keys-doc не читается), валидация против базы (инъекция чужого id, правка запертого, байт-в-байт), применение (версии, ключи, зеркало, sync-changes, STALE уже прикрыт 4.21, чужое = not-found).
- Сентинелы: `proposal-names-sentinel.test.js` дополняется именем `buildLessonEditCopy` и view-строкой копии; экспорт-пин перепиновывается (новый callable).
- **Клиентская половина (после лендинга 4.22, отдельным коммитом той же истории):** редактор копии на `RedactedQuestionContent` (парсер question-schema уже есть), отметка верного ответа, счёт изменённых вопросов → цитата 100×N, подача `ProposalOutboxWriter` (payload COPY_EDIT), транспорт копии + строгий парсер `lesson-edit-copy-v1`, строки ×3.

## Tasks & Acceptance

**Execution:**
- [x] Модуль lesson-copy-edit.js + сюита (копия, валидация, apply-набор).
- [x] index.js: callable копии + применение в решении автора; экспорт-пин; сентинел.
- [ ] Клиентская половина после 4.22 (редактор, отметка, подача, цитата).
- [ ] Гейты: functions npm test; gradle затронутых модулей (клиентская половина); ktlint; сентинелы.

**Acceptance Criteria:**
- Given опубликованный урок с современными и легаси-вопросами, когда чужой игрок зовёт `buildLessonEditCopy`, то копия содержит публичные половины без ключей для redactable-вопросов и запертые легаси-вопросы байт-в-байт, а документ `question_keys` не прочитан; автору копию не отдают (`PROPOSAL_TARGET_OWN`).
- Given одобряемая правка чужим id вопроса, когда apply валидирует, то именованный отказ без записей; правка запертого вопроса — отказ `PROPOSAL_LOCKED_QUESTION_EDITED`.
- Given проверенное предложение по актуальной базе, когда автор одобряет, то вопросы записаны новой редакцией с версиями, ключи урока пересобраны, версия урока поднята и зеркалится автору, sync-changes названы, предложивший в `contributorUids`, предложение APPROVED.
- Given повторное решение того же предложения, когда callable зовётся снова, то `PROPOSAL_NOT_ACTIONABLE`, записей нет.

## Implementation Notes

- Baseline d074873e (4.21 done). Волна: файлы 4.22 (сосед) не трогаются до их коммита; клиентская половина встанет на их `ProposalOutboxWriter`/цитатный транспорт.
- Redact-вызов копии отбрасывает ключ: копия — не хранилище ключей, а поверхность редактора; отмечать верный ответ предлагает автор копии, «Факты» проверяет отметку (CAP-27).
- Запертые вопросы в копии — не уязвимость: их байты уже публичны; уязвимость была бы выдать документ ключей — он не читается.

## Spec Change Log

- 2026-10-28, спека создана, status in-progress; серверная половина в работе, клиентская ждёт инфраструктуру 4.22 (соседняя смена той же волны).
- 2026-10-28, server landing (takeover-смена, 2e31293a): диспетчер решения подключает applyCopyEditProposal; callable buildLessonEditCopy (view lesson-edit-copy-v1); сюита lesson-copy-edit.test.js 5/5; сентинел контракта копии; пин 161fc07c.
- 2026-10-28, client landing (эта смена, takeover хвоста): редактор копии на экране результата урока — вход «Предложить правку» рядом с «Сообщить об ошибке» (UX-23, «на уроке»); копия грузится callable buildLessonEditCopy строгим парсером lesson-edit-copy-v1; предложивший отмечает верный ответ в публичных половинах (SingleChoice/MultipleChoice радиочек-боксы, Ordering стрелками, FillBlank кандидатами по пропускам), запертые строки показываются как есть и не едут; счёт изменённых → цитата 100×N (QuoteLessonProposalFeeUseCase, kind COPY_EDIT); подача — ProposalOutboxWriter.recordCopyEdit (тело строго по белому списку 4.21: proposalId/kind/target.lessonId/lessonTitle/questions[авторская форма]/editedQuestionIds — только изменённые), отмеченные строки собираются EditCopyMarking (публичная половина + отметка = полный QuestionContent тем же kotlinx-движком); строки ×3; сентинелы: ProposalReportWireSentinelTest дополнен COPY_EDIT-кейсом (ключи payload против парсера 4.21), LessonEditCopyCallableNamesTest читает functions/index.js. Починен сломанный ещё лендингом 4.24 фейк репозитория в ErrorReportProposalPresentationTest (нет queueNewLessonProposal/queueCopyEditProposal/fetchLessonEditCopy — сюита не компилировалась). Новые сюиты: CopyEditProposalPresentationTest 5/5 (загрузка копии, перезапрос платы по отметке, подача только отмеченных в авторской форме, ноль-п подача, хост без ручек), LessonEditCopyWireParserTest 4/4.

## Verification

**Commands (все выполнены в этой смене, sgw/JDK 27):**
- `functions`: `node run-tests.js` — 141/141 (дважды: на HEAD 50e6a187 и на финальном дереве; сентинелы копий внутри).
- `sgw ./gradlew :shared:feature:quest-authoring:domain:jvmTest :shared:feature:quest-authoring:data:jvmTest :android:feature:quest-authoring:presentation:testDebugUnitTest :android:feature:lesson-runner:presentation:testDebugUnitTest :platform:firebase:testDebugUnitTest` — зелёные (data jvmTest 98 тестов включая LessonEditCopyWireParserTest 4/4 и COPY_EDIT-кейс сентинела; lesson-runner — CopyEditProposalPresentationTest 5/5; firebase — LessonEditCopyCallableNamesTest).
- ktlint: `ktlintDebugSourceSetCheck` (quest-authoring, lesson-runner) и `ktlintJvmMainCheck` (domain, data) — SUCCESS; `platform:firebase:ktlintMainSourceSetCheck` — предсуществующая корзина (PushEvents, FirebaseMutationTransport, GiftClaim, QuestPrivate, ReviewDecisions, Tournament, Arena… и чужая инверсия QuestProposals/QuestArena в FirebaseModule — до моей правки); мои файлы и вставка импорта — чисто.

**Manual checks (if no CLI):** None beyond the suites; живой прогон — 4.26 (hitl, риг владельца).
