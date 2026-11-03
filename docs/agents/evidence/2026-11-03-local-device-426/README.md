# Локальная кампания · джорни 4.26 (три входа предложений + авторская половина) — PARTIAL: подача входов 1 (REPORT) и 3 (NEW_LESSON с платой 1000) доказана живьём до серверных терминалов (CHECK_PASSED / CHECK_FAILED), вход 2 (COPY_EDIT) и одобрение автором заблокированы НАЙДЕННЫМИ клиентскими багами; серверный fetchQuestProposals был сломан совсем (ReferenceError) — починен и доказан 200 (03.11–04.11)

## Прогон

- Два клиента на A14: mvp = предлагающий (`1CNreZZm69PL6V4khErkHCvwuNpt`, topup
  3000/3000 по автоотчёту ДО первого синка), mvpb = автор (`UY5Taioj0taVhat7M5akXhanLBe9`,
  сид авторского курса по его автоотчёту). Вотчер `run/device-farm/watch-426-local/watch.sh`.
  Свежий сид Timestamp-версии; «DF author catalog» приехал на полку предлагающего
  (корень полки из v7 стабилен), спуск каталог→курс→секция→тема→урок, «val» ×2,
  «Completion confirmed by the server» — без единого фола.
- Вход 1 (REPORT, бесплатно): point-tap «50%,47%» в поле листа (плейсхолдер вне
  семантики Compose — фикс v7b подтверждён на своём устройстве; локальному maestro
  2.0.10 дробные проценты не давать — `NumberFormatException`, в committed флоу
  поправлено на целые), текст отчёта введён, «Propose to author» → заявка в outbox →
  синком на сервер: `lesson_proposals/…u52jfhtyxfz1` REPORT QUEUED.
- Вход 3 (NEW_LESSON, Fee 1000): авторинг «DF426 proposer quest/section/topic/lesson»
  + ordering-вопрос, диалог «Propose your lesson to another author» (заголовок в две
  строки — литеральный ассерт не матчится; плейсхолдеры дропдаунов вне семантики —
  тапы полей по точкам, пункты меню по тексту), цель DF author catalog → course →
  section → theme, «Proposal queued» COMPLETED; сервер: `…x12jiuxc6sau` NEW_LESSON
  QUEUED, **фи 1000 списан (кошелёк 3003→2003)**, скрин
  `screens/426-entry3-proposal-queued.png`.
- Свипы предложений (починенный воркер, claimable-адоптация из 420): REPORT →
  **CHECK_PASSED**, NEW_LESSON → **CHECK_FAILED** (модель честно завернула
  плейсхолдер-урок), конвейеры QUEST_REVIEWED. Авторская дверь серверно готова:
  `fetchQuestProposals {box:"INCOMING"}` → 200 со строкой REPORT CHECK_PASSED,
  `stale:false`, полным `proposalText` устройства.

## Найденные баги (блокеры джорни — с точными корнями)

1. **[server, ИСПРАВЛЕНО в этой кампании] `fetchQuestProposals` падал 500 на КАЖДОМ
   вызове**: `ReferenceError: parseQuestProposalsRequest is not defined`
   (index.js:7029 — функция из quest-proposals-view.js не импортирована). Единственная
   дверь авторской половины была мертва; ни одна DF-волна до неё не доходила, поэтому
   не срабатывала. Фикс: импорт добавлен; 200 доказан живым вызовом с CHECK_PASSED-строкой.
2. **[client] COPY_EDIT (вход 2) не строит копию**: сервер `buildLessonEditCopy`
   отвечает 200 с валидной копией (проверено прямым вызовом), лист навечно висит
   «Assembling the copy…» — успех не засеттливается (при этом ОТКАЗ до фикса сида
   рендерился честно). Сид приборки был части причины и починен: `lessons/df426-lesson`
   без `authorUid` (NOT_FOUND) и вопросы без `type` (пустой тип в копии) — фикс в
   `scripts/devicefarm-rig-prep.js`, пересеян. Остаток — клиентский settle/hang.
3. **[client] Экран Решений зависает для не-verifier**: `fetchReviewDecisions` →
   403 «Admin or developer level is required»; клиент не засеттливает отказ —
   поверхность Verdicts вечно «грузится», переключение на вкладку Proposals
   заблокировано (текст- и point-тапы чипа не переключают). Блокирует и «My
   proposals» предлагающего, и «For my lessons» автора (mvpb — тоже не-verifier).
   Свежий перезапуск приложения не помогает — hang в init-фете поверхности.
4. **[UX-мелочи]**: Fee-лейбл листа оставался «—» при активной кнопке (квота не
   подтянулась в окно 45–60 с); rejectionReason сервера — код
   `REVIEW_FAILED_QUESTIONS`, клиент сводит коды к «could not be sent to the server,
   try again» (содержание несут построчные причины).

## Вердикт

**PARTIAL**: серверная половина джорни доказана глубже всех волн (две заявки с
терминальными статусами живой модели, плата, 200 авторской двери), но одобрение
автором в UI недостижимо до починки клиентских зависаний (п.2–3). Рекомендация
владельцу: клиентские фиксы п.2 (settle ответа копии) и п.3 (рендер 403 как пустой
Verdicts + разблокировка вкладок), после чего джорни закрывается без приборки.
