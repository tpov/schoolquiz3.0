# ТЗ-05. Подключить настоящее прохождение Арены

Зависимости: ТЗ-03/04. Результат: подготовленный ticket проходит онлайн/офлайн, переживает перезапуск, получает ровно один серверный расчёт. Обычные Home/курс-прохождения не меняют своего поведения.

Источник: `/Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/arena-runner-integration-proposal.md`. Его старое имя cancellation `requestCancel` заменить фактическими `recordCancelIntent`/`dispatchCancel` из ТЗ-01/04; предложение не является готовым кодом.

## Разделить на три проверяемых среза

### A. Сохраняющий состояние controller/reducer

- Отдельный ticket-mode/coordinator поверх текущих domain checkpoint и RunRepository. Не запускать обычный StartLesson/SaveResult/CompleteAttempt/AbortAttempt в Arena-режиме.
- Route различает explicit Fresh и ResumeOnly. Fresh только из допустимого подготовленного ticket; восстановленный route всегда ResumeOnly. `Again`/`Next` не переиспользуют старое разрешение.
- Owner + epoch + ticketId захватываются при создании. Late callback/response старой сессии отбрасывается, даже если UID снова тот же.
- Только строгий `Continuable` порождает Question. `Completing`, Pending, Cancelled, NeedsRecovery — отдельные состояния, не index=N в обычной Question.
- Переходы записываются последовательно через CAS **до** раскрытия нового вопроса/подтверждения действия. Ошибка сохранения удерживает последнее подтверждённое состояние/Attention, не продвигает локально вперёд.
- Пока CAS выполняется, повторные taps/timeout/lifecycle callbacks не порождают второй ответ. UI mutex не заменяет Room guards.

### B. Встроить в существующий runner и навигацию

- Существующие `DefaultLessonRunnerRootComponent`, `RunnerStateHolder`, `RunnerUiState`, `LessonRunnerScreen` менять точечно; доменная логика не в Compose.
- Добавить отдельную Arena factory/root-owned DI, не второй abstract method в существующий четырёхаргументный SAM и не direct Koin lookup из экрана.
- Frozen question order/version/difficulty/attemptId берутся из ticket. Нет повторного чтения live lesson bank, shuffle/subset или нового attemptId при rotation/restart.
- ANSWERING восстанавливает прежние startedAt/deadline/seed/order. Истёкший вопрос проходит существующий timeout один раз; не «отвечать» автоматически за все ещё не показанные вопросы.
- FEEDBACK восстанавливает locked answer и Continue; PAUSED сохраняет паузу. Background не заменяет уже locked ответ другим timeout-ответом. Rotation не запускает новый урок.
- Сохранить существующие правила ordinary runner для времени ответа/Continue и фоновой паузы. Arena offline runner не турнир: запрет паузы турнирных серверных часов нельзя механически переносить сюда.
- Проверить sessionMode при pause/continue и расчёте timer. Не менять семантику startedAt/момента фиксации answer time скрытой оптимизацией и не обещать устойчивость к переводу системных часов без доказательства.
- Exit/Back/cross: явная отмена → durable Entry cancel decision → navigation. Пока cancel локально не записан, не заявлять отмену. Сеть отправляется отдельно, pending не равно возврат.

### C. Результат, квитанция и оценка

- Final checkpoint → freeze → экран PendingCompletion. UI не начисляет награды сам и не считает FrozenLocally серверным успехом.
- Использовать текущий `ServerAttemptSettlementRepository` и owner-bound Result-поля. Зелёный результат/процент/награда и возможность оценить требуют настоящую квитанцию `SETTLED`, `completed=true` и правильного аккаунта.
- Unknown HARD — null/ожидание, не 0%. Legacy не выдавать за новый подтверждённый проход.
- При CANCELLED/конкурентном completion — reconciliation существующей доставки. Никаких двух sender-ов/нового результата для того же ticket.

## Тесты

Unit/reducer: claim до первого вопроса; fresh retry; двойной tap; CAS failure до reveal; timeout+tap; background/rotation во время IO; все виды вопросов; FEEDBACK/PAUSED/COMPLETING restart; CANCEL/SETTLE race; same-UID new epoch; immutable final body; Again/Next с новым разрешением.

Android: сохранить и повторить существующие проверки доступности Continue/Info и задержки 250 ms; проверять тексты/роли через semantics, не координаты. Не ухудшить ordinary Home-smoke.

Maestro + сервер:

1. SELECTED 1 нолик → подготовка → offline start → restart в FEEDBACK → завершение offline → сеть → один расчёт/80% автору с сохранённой долей.
2. Цена 100, изменение рейтинга после подготовки — frozen price сохраняется.
3. RANDOM без ноликов, обычные charges удерживаются/рассчитываются по контракту.
4. Нет ticket / исчерпан запас — новый offline-start недоступен.
5. Один ticket на двух устройствах; два разных ticket; CANCEL и SETTLE одновременно — не больше одного авторитетного исхода каждого ticket.
6. Потеря ответа после серверного расчёта, restart, повтор — прежняя receipt и неизменные денежные суммы.

Приёмка каждого среза отдельная. Финал ТЗ-05 требует реального сценария приложения и server ledger; только unit-«зелёный» не закрывает его.
