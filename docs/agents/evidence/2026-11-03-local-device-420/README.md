# Локальная кампания · джорни 4.20 (автор → Send to arena → ревью живой моделью → исходы автора) — PASSED: полный цикл до исходов автора доказан живьём на своём A14; по пути найден и починен КОРНЕВОЙ баг review-queue-worker (claimable затирал прогресс конвейера — этим же багом объясняется «TRANSLATE так и не атемптится» всех DF-волн v5–v7) (03.11)

## Прогон

- Устройство владельца Samsung Galaxy A14 (RF8W80MBR3Z, Android 15, USB с Mac), APK
  сборки 02.11 со всеми фиксами (sha256 mvp `c33d3e3f…`, mvpb `e65c5a09…` — сверены
  Mac ↔ VPS). Per-app локаль `en-US` обоим пакетам через `cmd locale` (системная
  локаль устройства украинская — при первом запуске UI был «ДОМАШНІ КВЕСТИ», скрин
  `screens/first-launch-uk-before-locale.png`; системные настройки не тронуты).
  Анимации 0 на время кампании. Maestro 2.0.10 локально.
- Вотчер `run/device-farm/watch-420-local/watch.sh` (VPS): автоотчёт эмулятора →
  аккаунт `D1MVX03HHHrdvHo5Jqgv257qBsHu` → topup 3000/3000 (оба поля) ДО первого
  синка; затем терпеливый свип очереди с TRANSLATE-фазой (b259d275), бюджеты подняты
  (patient 20 мин, translate-фаза 7 мин).
- Флоу `.maestro/mvp/420-author-submit-review.yaml` (v7b + правки локальной кампании:
  хвост переделан на polling исходов автора — «Fix the questions» — вместо
  DELAYED-аssert 90 с, который при живой модели не наступает).

## Что доказано живьём (хронология)

1. **Связность и смок** — `.maestro/mvp/02-request-sync.yaml` PASSED до джорни (свой
   прогон, «Last sync» на свежем аккаунте).
2. **Авторинг → подача** — 67+ шагов COMPLETED: структура квест/раздел/тема/урок,
   2 ordering-вопроса, промежуточный синк ради кошелька, scroll до «Send to arena»,
   economy-consent «Accept and send».
3. **DRIVER-глюк maestro (не приложения)**: на тапе «Accept and send» драйвер умер
   `DEADLINE_EXCEEDED` в `isWindowUpdating` (120 с, grpc к UIAutomator) — при этом тап
   дошёл: заявка `quest_review_requests/arena-submission-1h6z8n0ttorop` создана,
   **цикл-фи 1000 списан: кошелёк 3000→2000 в обоих полях** (скрин шторки с 2000 —
   `screens/420-drawer-wallet-2000.png`), дренажный синк 10:30 ушёл. Хвост джорни
   дошёл отдельным resume-флоу (`.maestro/mvp/420-resume-outcomes.yaml`).
4. **Живые вердикты glm-4.7**: WORDS DONE + FACTS DONE (`review_verdicts/…_WORDS_1`:
   FAIL обоим вопросам — маркер «DF420» как сленг + плейсхолдер, rationale ~760 байт;
   `…_FACTS_1`: FAIL — «проверять факты не по чему»). «Checking: “Words” · “Facts”»
   на карточке — после синка (скрин `screens/420-card-checking-before-advance.png`).
5. **Корневой баг воркера — найден и починен (fix в этой кампании)**: 20-минутный
   свип вотчера НЕ довёл конвейер до терминала: TRANSLATE PENDING attempts=0 при
   DONE-проверках, все свипы IDLE/TIME_BUDGET. Трассировка патченной копии воркера
   показала: `claimable()` при истёкшей лизе **пересоздавал конвейер через
   createPipelineState**, затирая прогресс; следующий шаг шёл по WORDS/FACTS,
   crisis-repeat мгновенно возвращал их в DONE по уже стоящим вердиктам, бюджет
   свипа (2 вызова) сгорал — TRANSLATE не доставался никогда. **Этим же механизмом
   объясняется симптом всех DF-волн v5–v7 «TRANSLATE PENDING, attempts=0 при живых
   WORDS/FACTS»** (transport-timeout был вторичныйLayer). Фикс: существующий конвейер
   адоптируется (`pipeline = existing`), сироты записей (вердикт стоит, чекпойнта
   нет) пришиваются обратно scanning attempt≤attempts+1. Юнит-тесты воркера 21/21
   зелёные (до фикса падали 2 crash-resumption-теста — контракт «письмо без
   чекпойнта не переигрывается» сохранён). Полный functions-набор: 143/143 сюит.
6. **Карантин мёртвых заявок**: очередь демо-рига была отравлена 4 брошенными
   UNDER_REVIEW-заявками DF-волн (31.10–02.11) с FACTS:PENDING — кандидаты идут
   oldest-first, их минутные FACTS-вызовы живой модели съедали бюджет каждого свипа.
   Переведены в `ABANDONED_DF_WAVE` (поле-маркер с причиной; владелец может
   удалить). Для будущих волн: вотчеры должны карантинить предыдущие волны или
   свиповать только свои submissionId.
7. **Терминал за ОДИН свип после фикса**: pipeline `QUEST_REVIEWED`,
   TRANSLATE **SKIPPED** (переводится только прошедшее — любой FAIL → skip,
   контракт 4.15), `reviewOutcome` записан в заявку.
8. **Исходы автора на карточке** (скрин `screens/420-card-outcomes-full.png`):
   «Returned: …» (reason-код `REVIEW_FAILED_QUESTIONS` клиент сводит к фразе
   delivery — UX-кандидат: код не читается автору, содержание несут построчные
   причины), **«Failed: 4 question(s)»**, по-вопросные причины Words/Facts с живыми
   rationale glm-4.7, кнопка **«Fix the questions»** → редактор черновика
   (`screens/420-fix-quest-editor.png`, «Quest queued for arena» + Easy/Hard
   questions). Авторский цикл подачи замкнут.
9. **Сеттл отказа клиентом**: серверный advance (advanceQuestReviewOutcome) из
   клиентского outcome-sync не сработал сам (синк-циклы флоу не дотащили) — advance
   сделан сервер-сайд от имени uid (custom-token), после чего следующий синк клиента
   осел REJECTED в локальную базу и карточка показала исходы. Кандидат в клиентские
   баги: outcome-sync с readyIds → advanceOutcome не дотащил advance до сервера за
   ~18 мин синк-циклов (проверить порядок Syncable-ов в конвейере синка).

## Артефакты

- Скрины: `screens/` (5 файлов, кадры выше).
- Флоу: `.maestro/mvp/420-author-submit-review.yaml` (правка хвоста),
  `.maestro/mvp/420-resume-outcomes.yaml` (новый),
  `.maestro/mvp/420-outcomes-finalize.yaml` (новый, PASSED 5/5 шагов).
- Вотчер: `run/device-farm/watch-420-local/` (VPS, вне Git).
- Фиксы в Git: `functions/review-queue-worker.js` (claimable-адоптация + сироты),
  `scripts/devicefarm-rig-prep.js` (сид 4.26: lesson.authorUid + question.type —
  из джорни 4.26, см. соседний каталог).

## Вердикт

**PASSED** — джорни 4.20 закрыта целиком на реальном устройстве: подача с платой,
живые вердикты модели, терминал конвейера, исходы автора, действие «Fix the
questions». Попутно закрыт многоволновый вопрос TRANSLATE-голода (корень —
claimable-rebuild), подтверждён контракт TRANSLATE-SKIPPED при FAIL-проверках.
