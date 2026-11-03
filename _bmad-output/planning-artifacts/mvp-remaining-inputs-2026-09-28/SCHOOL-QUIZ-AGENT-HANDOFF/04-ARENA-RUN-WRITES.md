# ТЗ-04. Безопасный write-adapter прохождения Арены

Зависимость: оба этапа ТЗ-01. Результат: typed API для единственного запуска, сохранения и завершения заранее подготовленного ticket. **Никаких UI, DI-расширений и нового отправителя в этом задании.**

Основа: `/Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/arena-run-write-proposal.md`. Предложение полностью просмотрено ранее; adapter ещё не написан. Предварительное усиление шести файлов уже находится в отдельном draft ТЗ-01 — не реализовывать его повторно.

## Минимальный scope

Изменить `ArenaRunRepository`, `ArenaRunRepositoryImpl`, узкий внутренний seam `ArenaTicketJournal`; добавить `ArenaRunWriteRepositoryTest` в существующем data JVM test-наборе. Полные корни — карта исходников. Сохранить исходный read API и результат ТЗ-01. Расширение вне четырёх целей сначала обосновать.

## Контракт

Имена допускают согласование с существующим стилем, семантика обязательна:

```kotlin
freshClaim(stamp, ticketId, sessionMode): Result<ArenaRunClaimOutcome>
resumeOnly(stamp, ticketId): Result<ArenaRunRead>
checkpointCAS(stamp, ticketId, expectedRevision, candidate): Result<ArenaRunCasOutcome>
finalize(stamp, completing): Result<ArenaRunFinalizeOutcome>
```

- `Claimed(current)` / `NotFresh(current)` различают локальный выигранный claim и последующее наблюдение. Даже Claimed не разрешает показать вопрос, если current уже terminal/pending/recovery.
- `resumeOnly` только читает: не claim, не новый seed/deadline, не prepare.
- CAS: `Saved(current)` / `AlreadyCurrent(current)` / `Conflict(current)`. AlreadyCurrent допускается только при **побайтно равном canonical checkpoint с тем же binding**. Не придумывать revision и не повторять blind overwrite после false.
- `finalize` принимает существующий typed `Completing`, не raw произвольное тело. Результат — только `FrozenLocally(ticketId, attemptId)`, не score/receipt/reward/ACK.

## Fresh claim

В Journal добавить внутренний synchronous factory-overload, вызываемый только после строгой проверки genuinely-fresh строки под существующим original-session gate. Factory получает frozen snapshot и создаёт `ArenaInitialCheckpoint`; прежний overload с готовым initial сохранить через общий private core.

Clock и seed provider инъецируются, фиксируются один раз на explicit fresh invocation до await. Initial использует текущий `computeTimer` с frozen первым вопросом, difficulty и sessionMode. Не считывать живую иерархию, не выбирать/перетасовывать новый набор, не создавать attemptId, не списывать ресурсы: они уже связаны с ticket.

Сохранение LAUNCHED + initial — одна Room TX до возврата. Проигравший/повторный claim не обнуляет checkpoint/таймер. Потеря ответа не основание повторить fresh другим намерением.

## CAS и завершение

- Получить строгий one-row RunRead; проверить owner/ticket/attempt/hash candidate; canonical encode через обязательный codec. expectedRevision передаётся без изменения.
- Текущая TX повторно проверяет полное immutable binding из ТЗ-01. При конфликте — новое чтение, не новый initial и не подгонка ответа.
- FEEDBACK prefix/locked answer, mode, seed и order сохраняются. COMPLETING неизменяем; один endedAt и final body фиксируются первым допустимым переходом.
- Последний CAS → durable COMPLETING → `arenaCheckpointCompletion` → существующий `freezeCompletion`. В одной existing TX сохраняются retained SETTLE, original SUBMIT_ATTEMPT и first-intent marker.
- Повтор freeze того же Completing после response loss/terminal eviction использует existing bankless retained-intent replay, без новой очереди и сброса backoff.
- После restart без checkpoint не реконструировать Completing из header. Pending/terminal ведёт в Entry reconciliation/восстановление существующей доставки.
- ABORTED не выдавать за financial completion. HARD unknown score остаётся null.

## Сессии и отмена

Исходный `ArenaSessionStamp` не переснимать по текущему UID; проверки до/после await и dispatcher return, CancellationException наружу. Не оборачивать Journal вторым тем же gate. Уже committed запись не откатывать при cancellation; клиент делает read/reconcile, не новый запрос.

Не добавлять Run.cancel. Выход позже использует Entry `recordCancelIntent` и отдельный `dispatchCancel`. SETTLE/CANCEL first-decision wins; отсутствие банка не блокирует отмену.

## Обязательные тесты

1. Genuine fresh вызывает factory ровно один раз; duplicate/lost-response claim не переинициализирует; resume не вызывает factory/clock-write/start.
2. Wrong owner/ticket/attempt/hash/revision, повреждённые canonical terms и текущая terminal/decision-строка отвергаются без изменений.
3. Потеря CAS-ответа: точное уже сохранённое тело → AlreadyCurrent; другое конкурирующее тело → Conflict. Никаких автоматических повторных записей.
4. Все четыре фазы, сохранение FEEDBACK/prefix/claims, запрет изменения final и unknown HARD.
5. Crash после final CAS до freeze; потеря freeze-response; replay после eviction — одна исходная операция, никакого повторного enqueue/сброса retry.
6. A→B→A до dispatch/внутри local work/на выходе; отмена корутины после commit не создаёт rollback или новый ID.

Тесты через реальный Journal/strict codec и canonical fakes, не полностью замоканный «успешный repository». Приёмка: данные/persistence/domain JVM gates, static и сборка. UI пока не запускается; следующая задача — ТЗ-05.
