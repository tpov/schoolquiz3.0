# 5.6 sessions-online-o2 — 2026-10-16

Story: турнирные (QUALIFICATION и MATCH) сессии принимают ONLINE-O2/AD-31 по принятому договору
ONLINE-O1 — окно следующего вопроса с захваченной конфигурацией, часы от выдачи, ready-ACK,
завершение по истёкшему окну, видимость, требующая выдачи. Спека:
`_bmad-output/implementation-artifacts/spec-5-6-sessions-online-o2.md` (baseline
`0d0cec590113cddc4645e3d7a8c7eb5e3a9ef5f4`, совпал с HEAD). Закон:
`_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` (издание 1, принято
владельцем 2026-10-16, sha256 `fa9ca506…`), Приложение A1–A9 и D1–D9; механика O2:
`_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` §2/§3/§4/§5/§7.

## §8.4 — переверка указателей на старте истории (baseline договора `28059300` → HEAD истории `0d0cec59`)

| Указатель договора | На `28059300` | На `0d0cec59` (проверено grep'ом до правок) | Вывод |
| --- | --- | --- | --- |
| `tournament-session.js:121` (структурная охрана конверта) | :121 | :121 | без сдвига |
| `tournament-session.js:142` (видимость текущего вопроса) | :142 | :142 | без сдвига |
| `tournament-session.js:210/214` (своевременность) | :210/:214 | :210/:214 | без сдвига |
| `tournament-session.js:227-229` ( kinds закрытий ANSWER/TIMEOUT/FORCED_CUTOFF) | :227-229 | :227-229 | без сдвига |
| `tournament-session.js:247-250` (clock seeding той же операцией) | :247-250 | :247-249 (строка уже была 247-249 в контракте; закрывающая скобка на 250 — граница диапазона, решений не сдвигала) | сдвига решений нет |
| `tournament-session.js:186-192` (settled-путь) | :186-192 | :186-192 | без сдвига |
| `tournament-session.js:299-307` (worker advance) | :299-307 | :299-307 | без сдвига |
| `tournament-session.js:105-135` / `:233` (scoreForm / неизменяемость префикса) | :105-135 / :233 | :105-135 / :233 | без сдвига |
| `tournament-service.js:219-220` (клэмп создания квалификации + намеренный комментарий) | :219-220 | :280-282 | сдвиг, смысл тот же |
| `tournament-service.js:93` (клэмп вопроса 0 при создании) | :93 | :119 (внутри `makeSession`) | сдвиг, смысл тот же |
| `tournament-service.js:117` (freeze привязан к точному cutoff) | :117 | :143 | сдвиг, смысл тот же |
| `tournament-service.js:437-455` / `:451-454` (reconnect / read-проекция) | :437-455 / :451-454 | :502-517 / :516 | сдвиг, смысл тот же |
| `tournament-policy.js:158-160` (OUTSIDE_QUALIFICATION_WINDOW) | :158-160 | :243-245 (вход), :270-272 (admission) | сдвиг, смысл тот же |
| `tournament-policy.js:205-209` (form-fit + длительность формы) | :205-209 | :208-209 (form()), :340-342 (form-fit throw) | сдвиг, смысл тот же |
| `tournament-policy.js:228-230` (C = конец этапа − 1 ч − форма) | :228-230 | :344 (`coordinationDeadlineMs`), :363 (`meetingContext`) | сдвиг, смысл тот же |
| `tournament-policy.js:233-241` (предложение до C) | :233-241 | :368-376 | сдвиг, смысл тот же |
| `tournament-policy.js:281-295` (READY/STARTED, `startedAtMs = max`, `endsAtMs = start+длительность`) | :281-295 | :385-435, факт старта :427-428 | сдвиг, смысл тот же |
| `lesson-question-window.js:43 / :68-75 / :89-110 / :104 / :140-161 / :181-206` | те же | те же | без сдвига; файл не редактировался (A2) |

Все указатели найдены по смыслу на актуальном baseline; решения D1–D9 не пересматривались
(§8.4: «указатели обновляются — решения при этом не пересматриваются»).

## Приложение A1–A9 → что сделано

| # | Что требовалось | Что сделано | Где |
| --- | --- | --- | --- |
| A1 | Окно в операции, закрывающей вопрос; клэмп сохранён, `now` = момент выдачи; конверт не двигать | Закрытие внутри конверта открывает окно (`openTournamentQuestionWindow`) вместо seed'а часов; выдача якорит часы той же формулой `min(deliveredAt + maxDuration, endsAtMs)` (`deliverTournamentQuestionWindow`). Клиент-дрiven закрытие доставляет следующее question в том же ответе (матрица: «delivered in the same response»); worker-закрытие оставляет окно OPEN — иначе недоставленные окна (и строки матрицы про них) были бы недостижимы (см. честные заметки). Конверт не пишет никто | `tournament-session.js`: план, сайт бывшего seeding; `deliverTournamentQuestionWindow` |
| A2 | Захват `windowSeconds = min(60, endsAt − закрытие)` на стороне турнирного адаптера; `lesson-question-window.js` не редактировать | `openTournamentQuestionWindow` компонует константу `NEXT_QUESTION_WINDOW_SECONDS`, состояния и `mintWindowId` из неизменённого O2-модуля (импорт read-only); захваченные `windowSeconds`, `expiresAtMs = min(openedAt + windowSeconds·1000, endsAtMs)`, identity, следующий вопрос/длительность, fingerprint, край конверта пишутся в документ сессии при открытии и не меняются | `tournament-session.js` (`openTournamentQuestionWindow`); git: `lesson-question-window.js` не в diff |
| A3 | Истёкшее без выдачи окно закрывает остаток без ответов, kind `FORCED_CUTOFF`, `closedAtMs` = захваченный край; driver — worker/controlled tick | Вердикт края замкнут с двух сторон: (1) worker-проход (`advanceTournamentSession`, `advanceSession` — `serverDriven: true`) и (2) любая клиентская операция, разрешающая окно после края. Оба пути пишут одно состояние: `FORCED_CUTOFF` по всем оставшимся слотам с `closedAtMs = expiresAtMs` окна (не момент наблюдения), settlement тем же `scoreForm`, `completedAtMs` = захваченный край; идемпотентно (повтор — существующий settled-путь) | `tournament-session.js` (блок разрешения окна в плане), `course-tournament-controller.js` (`advanceSession`), `tournament-session.js` (`advanceTournamentSession`) |
| A4 | Путь «следующий вопрос»/read сначала разрешает окно; до выдачи активного вопроса нет | `responseFor` требует выдачи явно (`state !== OPEN`); `readTournamentSession` разрешает живое окно — выдача с якорением часов ровно один раз (`deliveredNowMs`), истёкшее окно не показывает вопрос и не пишет ничего (терминация — за mutation/sweep-driver'ами, читает никогда не закрывают вопросы — см. заметку 2); worker-проход при живом окне не меняет ничего (`writes: []`) и возвращает `CURRENT_QUESTION` с проекцией окна | `tournament-session.js` (`responseFor`, план), `tournament-service.js` (`readTournamentSession`), `course-tournament-controller.js` (`advanceSession` nextDue = край окна) |
| A5 | NO change — клэмп создания и охрана конверта остаются | Не тронуты; тест структурной охраны зелёный, новые тесты подтверждают (фикстуры с конвертом длиннее суммы времён вопросов отказываются той же охраной — это и есть A5 в действии) | `tournament-service.js:280-282`, `tournament-session.js:121` (до правок) |
| A6 | NO change — cutoff когорты абсолютен | Не тронут; новый пин «a window opened near the cohort cutoff…» — окно длится ровно остаток конверта, sweep оседает ровно на cutoff | `tournament-service.test.js` |
| A7 | NO change — C/READY, слот ≤ C, form-fit | `tournament-policy.js` не в diff; все meeting-тесты зелёные нетронутыми | git |
| A8 | Ready-ACK `(identity сессии, questionIndex, windowId)`, идемпотентный, через единый приёмник; никогда не двигает deadline; на вопрос 0 ACK нет | Новый kind `READY_ACK` сессийной операции (тот же приёмник: `controller.session` → `applyTournamentSessionOperation`; отдельного транспорта нет, AD-7). Квитанция хранится на сессии (`readyAcks[questionIndex]`), повтор того же ключа+тела возвращает ту же квитанцию, другое тело под тем же ключом — `CONFLICTING_REPLAY`, ACK без доставки/с чужим windowId — `FAILED_PRECONDITION`, вопрос 0 и индексы вне 1..19 — `INVALID_ARGUMENT` ещё в нормализации. Часы ACK не трогает (ассерты в тестах) | `tournament-session.js` (`normalizeSessionRequest`, ветка плана), `course-tournament-controller.js` (`session`, `safeResponse.readyAck`) |
| A9 | NO change — скоринг нетронут, незакрытые слоты null-ответами через существующий scorer | `scoreForm` и проверка префикса не менялись; завершение по окну идёт через тот же `scoreForm` с null-ответами | git; тесты процента 0/5 при завершении |

## Матрица I/O → тесты

| Строка матрицы | Кейсы |
| --- | --- |
| Window opens (capture + delivery in response + retry) | session: «closing a question opens a captured 60s window…», «retry of the closing operation replays…» |
| Window near envelope edge / zero remainder | session: «window near envelope edge captures the remainder…» (seconds=1, край = конверт), «zero envelope remainder opens no window…»; service: «a window opened near the cohort cutoff…» (D2/D3 пин) |
| Delivery anchors clocks (+ never re-anchor) | session: «delivery inside the window anchors the clocks exactly once…»; service: «mid-window reconnect delivers from the read path…»; controller: «worker close leaves the window open; readSession delivers…» |
| Undelivered window expires (sweep + delivery attempt, one state, idempotent) | session: «expired undelivered window settles via the sweep…» (+ идемпотентность), «a late delivery attempt after the edge settles to the same state…», «a late answer after the edge gets the settled response and is never attributed»; service: «an expired window read shows no question and writes nothing…»; controller: «the sweep settles a match session whose window expired…» |
| Ready-ACK (idempotence, conflict, no-ACK-0, no delivery) | session: «ready-ACK acknowledges…», «same ready-ACK key with a different body conflicts…», «ready-ACK without a delivery is refused…», «an ACK receipt survives its window being superseded…»; controller: «ready-ACK through the public session callable…» |
| Question 0 | session: «question 0 lives by the start contract…» (+ тесты старта сервиса без изменений зелёные) |
| Last question | session: «no window opens after the last question…» |
| Match symmetry | session: «MATCH sessions share the form but never the clocks or windows» |
| Reconnect | service/controller read-кейсы выше + существующие reconnect-тесты зелёные нетронутыми |

## Хонест-заметки (что решил исполнитель; не решения договора, а их механика)

1. **Кто выдаёт.** A1/матрица требуют доставку в ответе закрывающей операции И одновременно
   предполагают недоставленные окна (строки «Undelivered window expires», «Reconnect mid-window»).
   Совместимо ровно одно разбиение: клиент-дрiven закрытие (ANSWER/ADVANCE игрока) доставляет в
   том же ответе (часы якорятся в `at`, численно = прежнему поведению), а worker-закрытие
   (`serverDriven: true` — `advanceTournamentSession` и `advanceSession` контроллера, AD-23)
   оставляет окно OPEN: иначе недоставленное окно недостижимо, и офлайн-игрок матча терял бы время
   вопроса k+1 до переподключения — ровно та проблема AD-31, которую окно чинит. Различение —
   доверенный вход плана, как `now`; клиентские callable его выставить не могут.
2. **Read-путь разрешает только живое окно.** `readTournamentSession` доставляет (якорит часы,
   пишет в транзакции — ответ чтения и есть доставка по O2 §3), но истёкшее окно НЕ Terminирует:
   терминация требует `scoreForm` (ключи/слоты, которых чтение не читает), а существующее свойство
   «read-only reconnect cannot pause/restart expired question» (тест закреплён) запрещает чтениям
   двигать состояние. Край окна на чтении виден как `window.state: "OPEN"` + `question: null`;
   терминальный вердикт остаётся за мутациями и sweep'ем (оба bounded, AD-23).
3. **Поздний ANSWER после края** получает settled `COMPLETED`-ответ (существующий settled-путь),
   ответ нигде не атрибутируется (ни ingress, ни payload слота), попытка оседает на захваченном
   крае — «late client gets the settled response».
4. **`wasShown` честность:** вопрос, закрытый `FORCED_CUTOFF` по краю окна, помечается
   `wasShown: false` (он не выдавался), в отличие от sweep-закрытия показанного вопроса.
5. **Суб-секундный остаток:** `windowSeconds = min(60, ceil(остаток/1000))` — целые секунды ≥ 1;
   при остатке < 1 с захваченное значение 1 с, а персистированный `expiresAtMs` зажат краем
   конверта (край всегда точный и ≤ `endsAtMs`; отрицательных/нулевых окон нет — D3).
6. **Хранение:** окно и ready-ACK-квитанции живут на документе сессии (замена при каждой
   границе ≤ 19 раз, ≤ 19 квитанций) — НОВОЙ КОЛЛЕКЦИИ/ПОДКОЛЛЕКЦИИ НЕТ, поэтому
   `tournament-storage-contract.test.js` и `scripts/course-tournament-private-rules.test.js`
   расширяются только если появляются новые пути — их не появилось, deny-листы остаются полными.
7. **Цикл модулей:** `tournament-service` получает `deliverTournamentQuestionWindow` ленивым
   require внутри функции (обратное ребро к `tournament-session`, который уже требует
   `TOURNAMENT_PATHS`); на порядок загрузки не зависит.
8. **60-секундные окна достижимы только при достаточном конверте:** структурная охрана A5
   (`конверт ≤ сумма времён вопросов`) означает, что полный 60-секундный захват требуют формы с
   достаточной суммой (в тестах duration 4000×20); при малых длительностях окно честно
   капится остатком конверта — та же формула, то же наблюдаемое поле `windowSeconds`.

## Верификация

- `cd functions && /root/.zcode/server/node run-tests.js` → **109 of 109 suites passed**
  (tournament-session 41 → **58**, tournament-service 47 → **50**, course-tournament-controller
  46 → **49**; все прочие — включая `tournament-policy`, `lesson-question-window`,
  `tournament-storage-contract`, freeze/scan/worker — зелёные нетронутыми или без правок).
- `git status --porcelain -- shared/ android/` → пусто (проверено).
- Изменённые файлы: `functions/tournament-session.js`, `functions/tournament-service.js`,
  `functions/course-tournament-controller.js` + их три тест-сьюты. НЕ тронуты:
  `lesson-question-window.js`, `tournament-policy.js`, оба договора, экзамен/Arena/ordinary пути,
  клиентский код.

Правки review pass 1 (2026-10-16, оркестратор):
- `wasShown` починен для гонки «доставка+ответ одной операцией»: доставка внутри операции
  снимает флаг недоставленности — отвеченный слот больше не помечается «не показанным».
- `READY_ACK` после дедлайна ответа (до закрытия вопроса) отказывается типовым
  FAILED_PRECONDITION «Ready-ACK after the answer deadline» и ничего не терминирует; кейс
  запинен. Идемпотентный replay того же ключа по-прежнему возвращает сохранённую квитанцию.
- Отклик клиентского закрытия несёт `session.window` (windowId/state/expiresAtMs) — источник
  ключа ACK; запинено тестом. nextDueAtMs sweep'а запинен на краю окна в обеих ветках.
- Состояния окна в валидаторе — через импортированные константы WINDOW_* (без строковых
  литералов).
- `windowSeconds` — ceil (окно живо для любого положительного остатка), НО авторитетный край —
  `expiresAtMs`, зажатый точно в край конверта; windowSeconds — грубая захваченная константа и
  может завышать меньше чем на секунду. Клиенту 5.9/5.10 считать по `expiresAtMs`.
- Документ сессии с полной лестницей из 18 ACK остаётся в бюджете хранения (тест).
- Предсуществующие сессии без `window`/`readyAcks` (деплой поверх идущей попытки) толерантны:
  оба поля опциональны, старое поведение сохраняется до первого закрытия вопроса.
- Интерпретации исполнителя, вынесенные владельцу с контрактом (не новые решения): (а)
  разделение serverDriven — клиентское закрытие доставляет в ответе, worker-закрытие оставляет
  окно открытым; (б) чтения никогда не терминируют истёкшее окно (только мутации и sweep).
