# 5.7 meeting-restore-uxo3 — 2026-10-16

Story: разрешённая (permitted) read/ack-проекция встречи чемпионата по гейту UX-O3 —
предложения/подтверждения/READY по участникам, состояние согласованного слота, границы
C/слот/READY-окно для честных отказов, восстановление при повторном входе (owner+epoch) и
типированные граничные отказы по AD-17. Спека:
`_bmad-output/implementation-artifacts/spec-5-7-meeting-restore-uxo3.md` (baseline
`f0f5b336e6a77f3bc9a29d678a7f1a74391cd78f`, совпал с HEAD). Закон тайминга: принятый договор
ONLINE-O1, D7 — «C и READY математика не меняется» (C = конец этапа − 1 ч − длительность формы;
предложение до C, слот в [now, C], подтверждение до C и не позже слота, READY в
[слот, слот+1 час)); гейт UX-O3 из `contract-map.md`: «permitted proposal/confirmation/READY
projections and restoration before real championship actions»; AD-17 хребта: «PROPOSE не
CONFIRM; предложения и подтверждения отдельных слотов сохраняются, выбор radio их не отменяет.
Согласованный слот неизменяем; разрешённая owner/epoch read/ack-проекция восстанавливает
CONFIRM и READY каждого участника».

## Что сделано

| Файл | Изменение |
| --- | --- |
| `functions/course-tournament-controller.js` | `readMeeting` расширен (только projection, без новых callable и без новых фактов): (1) страничная выдача подтверждений — подколлекция `meetings/{id}/confirmations`, та же дисциплина курсора, что у offers (`afterConfirmationId`, `pageSize`, `hasMoreConfirmations`, `nextConfirmationId`, проверка привязки `doc.id === digest([slot, uid])` и принадлежности участнику, иначе UNAVAILABLE); (2) `ready` — READY-сигналы каждого участника из единственного хранилища (`meeting.readySignals`), allowlist `{uid, acceptedAtMs}`, чужой uid → UNAVAILABLE; (3) `participants` — `{uid, seed}` из pending-входа сетки (uid'ы уже открыты readEvent через `progress.pending`); (4) `stageStartsAtMs`/`stageEndsAtMs` рядом с существующим `schedulingDeadlineMs` (C) — границы для честных отказов; `serverNowMs` уже был. Все новые поля — явный allowlist (`pick`), редакция не ослаблена: ни форм, ни ключей, ни fingerprint'ов, ни чужих встреч |
| `functions/tournament-service.js` | Приёмник операций: граничные отказы PROPOSE (`planMeetingProposal`: «proposal is outside the scheduling window») и CONFIRM (`evaluateMeeting` в ветке подтверждения: «confirmation needs an existing timely proposal» — нет пары предложений, подтверждение позже C, подтверждение позже слота) конвертируются из голого `RangeError` в типированный `FAILED_PRECONDITION` с текстом самой политики; на отказе ничего не staged. READY уже отказывал типированно с именованием окна; «Agreed slot is immutable» уже был типированным |
| `functions/tournament-policy.js` | НЕ тронут (D7/A7: C/READY математика — единственный источник фактов встречи) |
| `functions/index.js`, callable, storage | НЕ тронуты: новых путей/коллекций нет (offers/confirmations/readySignals уже существовали), `fetchCourseTournamentMeeting` отдаёт расширенный ответ того же callable |

## Гейт UX-O3 / матрица спеки → тесты

| Строка матрицы | Тесты |
| --- | --- |
| Proposals persist (обе заявки с авторами) | controller: «a radio selection cancels neither…» (offers `[p0,X],[p0,Y]` с uid авторов); существующий paging-тест offers зелёный |
| PROPOSE ≠ CONFIRM (radio отменяет ни proposals, ни confirmations; typed refusal без пары предложений) | controller: «a radio selection cancels neither proposals nor confirmations…» (confirmations `[p1,X]` стоят, agreements нет, CONFIRM слота без offers → FAILED_PRECONDITION «confirmation needs an existing timely proposal», store байт-в-байт до/после); service: «meeting proposal/confirmation boundaries refuse typed…» |
| Agreed slot immutable (+ stale page cannot confirm) | controller: «agreed slot is immutable against moved confirmations and a stale page…» (CONFIRM второго слота со устаревшей страницы и новый PROPOSE → FAILED_PRECONDITION, повторный CONFIRM согласованного слота идемпотентен, третьего confirmation-документа нет); service: существующий «old offers remain confirmable…» зелёный |
| Restore on re-entry (reload; потерянный ответ → повторный fetch идентичен) | controller: «readMeeting restores confirmations, ready states, participants and stage boundaries identically…» (deepEqual трёх прочтений; курсор confirmations; курсор `afterConfirmationId:"zzz"` → INVALID_ARGUMENT) |
| READY window edges ([слот, слот+1ч), до слота отказ, на границе окна отказ, типированно с именованием окна) | controller: «READY window edges are typed refusals…» (сообщение «Ready is accepted only inside the agreed one-hour window»); policy: «confirmation and readiness boundaries are exact…» (999/1000/windowEnd−1); существующие «one-hour agreed window is half-open» и сервисный no-show зелёные |
| Confirmation boundary (≤ C, ≤ slot; на C ровно и после C отказ; слот за C отказ) | service: «meeting proposal/confirmation boundaries refuse typed and stage nothing» (at C, at C+1, слот C+1, слот в прошлом — всё FAILED_PRECONDITION без staging; at C−1 — последний легальный момент); policy: те же границы в чистой политике |
| Match start authority (только сервер стартует; проекция STARTED; клиент не форсирует) | controller: «only the server starts the match…» (ADVANCE_MATCH игроком/организатором → PERMISSION_DENIED, статус WAITING_FOR_READY без раннего старта, после обоих READY — STARTED с серверными startedAtMs/endsAtMs); существующие клиентские запреты worker-capabilities зелёные |
| Account switch (A→B→A; B не принимает черновики A) | controller: тот же restore-тест — A/B/A чтения deepEqual, каждое подтверждение/предложение атрибутировано своему uid; owner-охрана — существующая дисциплина (см. ниже) |

## Owner+epoch: переиспользованная (не изобретённая) дисциплина

Дисциплина AD-6 уже живёт в приёмнике и на чтениях, новых механизмов не добавлено:

- операции жёстко привязаны к владельцу: `payload.ownerUid !== uid` → `PERMISSION_DENIED`
  («Operation belongs to another account»), чек незатронут;
- квитанции операций принадлежат своему uid (`receipt.uid !== uid` → `PERMISSION_DENIED`),
  потерянный ответ перепроигрывается в ту же квитанцию — незатронуто, покрытие существующими
  replay-тестами;
- чтения авторизованы вызывающим: `readMeeting` пускает только участников матча и организатора
  (запинено (тест «organizer reads the same meeting projection…»)), факты в ответе атрибутированы авторам — поэтому смена аккаунта A→B→A даёт
  байт-идентичные серверные факты, а чужие черновики adopt'ить нечему: B пишет только свои
  документы (offers/confirmations/readySignals ключуются по аутентифицированному uid);
- «stale page cannot confirm»: приёмник CONFIRM перевалидирует каждую попытку по текущим фактам
  (evaluateMeeting по документам слота), ответ устаревшей страницы ничего не разрешает — запинено
  тестом «agreed slot is immutable against … a stale page».

## Что проекция открывает / что удерживает

Открывает (readMeeting): `meeting` (статус evaluateMeeting: WAITING_*/STARTED/FORFEIT/DOUBLE_OUT,
согласованный слот `slotStartsAtMs`/`agreedAtMs`, `windowEndMs`, `startedAtMs`/`endsAtMs`,
`resultDue`, технические исходы — как раньше), `offers[]`/`confirmations[]`
(`offerId`/`confirmationId`, `uid`, `slotStartsAtMs`, `acceptedAtMs`; страницы с курсорами),
`ready[]` (`uid`, `acceptedAtMs`), `participants[]` (`uid`, `seed`), границы: `schedulingDeadlineMs`
(C), `stageStartsAtMs`, `stageEndsAtMs`, `serverNowMs`. Границы READY-окна выводятся клиентом из
серверных фактов (`slotStartsAtMs` + `windowEndMs` при согласии) — 5.11 рендерит честные
expiry-состояния без запроса политики у сервера.

Удерживает: приватные формы/ключи/fingerprint'ы (assertSafe на каждом ответе), чужие события и
встречи (match привязан к pending-входу сетки события), незапятнанные внутренности
evaluateMeeting (агрументы политики не покидают сервер), token ссылки допуска (не в этом ответе),
client-claims времени/результатов (поля только для чтения; мутации — только через операционный
приёмник). readEvent намеренно НЕ расширен состояниями встреч: per-match восстановление —
поверхность `readMeeting` (чтение 15 встреч в event-view было бы неограниченным обогащением);
границы слотов события уже открыты readEvent'ом.

## Честные заметки

1. **Типирование ошибок** — единственное изменение поведения мутаций: голые `RangeError`
   политики на границах PROPOSE/CONFIRM теперь доходят клиенту как `FAILED_PRECONDITION` с
   исходным текстом. Ранее они дошли бы как INTERNAL (untyped). Существующие тесты, ждущие
   `FAILED_PRECONDITION` на соседних ветках (immutable slot, READY вне окна), не изменялись.
2. **Две страницы, один `pageSize`**: offers и confirmations страничат независимо с собственными
   курсорами; `pageSize` применяется к каждой странице отдельно. Число confirmations структурно
   не ограничено (по одному документу на (слот, участник) с существующим предложением), поэтому
   курсор обязателен, а не необязателен.
3. **`ready` берётся из `meeting.readySignals`** — единственного хранилища READY (документ
   встречи, перезаписывается атомарно операцией READY). Отдельных документов READY нет — новых
   форм фактов не появилось (запрет спеки соблюдён). Поведение идемпотентно: повторный READY того
   же участника не дублирует сигнал (существующая охрана `some(row => row.uid === uid)`).
4. **«Each account sees only its own …» в строке account-switch** читается как серверное
   наблюдаемое: факты атрибутированы и пишутся только от аутентифицированного автора; B не может
   ни прочитать, ни записать черновики A (их не существует как отдельные документы — только общие
   факты встречи, видимые обоим участникам одинаково). Локальные UI-черновики — вне скоупа
   (no client code в этой истории).
5. **Организатор** видит ту же проекцию встречи (уже был авторизован в readMeeting); это нужно
   для админ-диагностики и не раскрывает ничего сверх участнического вида.

## Верификация

- `cd functions && /root/.zcode/server/node run-tests.js` → все сюиты зелёные (см. финальный
  прогон истории; до него точечно: `tournament-policy` 28 pass / 0 fail (27→28),
  `tournament-service` 51 behavioral tests (50→51), `course-tournament-controller` 55 behavioral
  cases (49→55)).
- `git status --porcelain -- shared/ android/` → пусто (проверено).
- Изменённые файлы: `functions/course-tournament-controller.js`,
  `functions/tournament-service.js`, `functions/tournament-policy.test.js`,
  `functions/tournament-service.test.js`, `functions/course-tournament-controller.test.js`,
  этот README. НЕ тронуты: `tournament-policy.js`, `tournament-session*.js`,
  `lesson-question-window.js`, `index.js`, callable-обвязка, правила Firestore/индексы
  (новых путей нет — `tournament-storage-contract` и `course-tournament-private-rules` зелёные
  без правок), клиентский код (`shared/`, `android/`).

Правки review pass 1 (2026-10-16, оркестратор):
- «Байт-идентичные факты» уточнено: проекция идентична по фактам, КРОМЕ `serverNowMs` —
  клиентскому restore-сравнению поле подлежит исключению; тест идёт под замороженными часами.
- Organizer-read запинен тестом «organizer reads the same meeting projection…»; ремонтные ветки
  (чужой uid / нечисловой acceptedAtMs → типовой UNAVAILABLE), комбинированный пейджинг (оба
  курсора одним запросом) и empty-shape свежей координации запинены.
- Конвертация RangeError → FAILED_PRECONDITION сужена до сообщений границ (scheduling window /
  timely proposal) общим хелпером; прочие RangeError остаются серверными гэпами.
- Границы READY: policy молча отбрасывает ранний READY, типовой отказ даёт receiver —
  двухслойность зафиксирована.
- Воспроизведение: `node` из PATH или `/root/.zcode/server/node` (v22); финальный прогон —
  `cd functions && node run-tests.js` → 109/109.
