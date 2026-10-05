# ТЗ-07. Завершаемый серверный турнир и большой отбор

Зависимость: ТЗ-00. Высокий риск; выполнять тремя отдельными срезами. Не писать заново весь tournament backend.

## Существующая основа

`tournament-source-bank`, `tournament-bank`, `tournament-policy`, `tournament-service`, `tournament-session`, `tournament-session-results`, `tournament-ranking`, `tournament-qualification*`, `course-tournament-controller`, `tournament-worker` уже подключены к `index.js`.

Есть шесть Auth-callables и scheduled-handler. Приватные формы/ключи/worker-state закрыты от прямых клиентов. Реальные HTTP/Firebase-проверки проходили; worker **не развёрнут как работающий scheduler** на этом стенде.

Отбор сохраняет прогресс чтения/проверок. Проверен случай 501 **попытка**, например 167×3, а не ограничение в 501 участника. Выше 501 accepted runs controller сейчас остаётся `CALCULATION_PENDING/RESOURCE_ENVELOPE`, сохраняя фонд, но бесконечно ожидая отсутствующий альтернативный расчёт. Это незавершённый сценарий.

## Срез A — вычисление отбора без зависания

- Изучить фактическую относительную LS-модель и fixture-контракты, не заменять рейтинг суммой процентов/временем/средним по своему выбору.
- Есть готовый **прототип**, не drop-in: `arena`-независимый пакет `qualification-compact-spike` в реестре. 33/33 pure Node22 tests прошли. Он использует компактное точное вычисление; input-adapter/durable публикации у него нет.
- Обнаружена значимая разница на допустимой 32-player one-form ничьей: старый floating результат отправляет `u31` на место 32, prototype — на место 2; top16 меняется. Нельзя молча заменить алгоритм и пересчитать опубликованные результаты.
- Для нового расчёта задать явную версию, canonical frozen input fingerprint и deterministic rounding/tie rules. Подготовить сравнительный отчёт на старых fixtures/контрпримере; изменение численной погрешности требует отдельного review до включения. Существующие published ranking/bracket immutable.
- Подключить расчёт только к **полностью** проверенному frozen manifest: missing/corrupt/непроверенная строка не пропускается. Не использовать только последние 501, первые страницы или случайный sample.
- Durable scan/reduction/calculation checkpoints, lease/fencing, проверка input hash при каждом возобновлении. Нельзя накопить значение дважды при retry Firestore transaction.
- Явно ограничить память, операции/время на invocation, число/размер чисел. Prototype 4096-runs/4096-bit guards — технические границы эксперимента, не утверждённые лимиты участников. Не превратить их в новую продуктовую квоту.
- Для случая выше текущего бюджета нужен реальный путь к завершению/контролируемому продолжению, а не переименование вечного Pending или автоматическая отмена/возврат. Если доказанного алгоритма/ресурса ещё нет, ТЗ не закрыто; лимит допуска требует решения владельца.

## Срез B — работа worker на тестовом стенде

- Сохранить server-only controller reconcile; не выставлять scheduler-команды через generic клиентский callable.
- Подключить управляемый bounded запуск на demo-стенде без production, без внешнего Scheduler/PubSub по умолчанию. Запуски должны быть воспроизводимы с тестовыми часами в тестах, но клиент не выбирает serverNow.
- Отбор закрывается в deadline, поздние результаты не дописываются в frozen cohort. Worker после crash/lease expiry продолжает те же события, не застревает на первом и не теряет cursor.
- `CLOSE_QUALIFICATION`, match progress, technical outcomes и выплаты вызываются сервером. Повторный reconcile безопасен.
- Проверить периоды: неделя отбора, три дня/этап, крайний срок оставляет час+match duration. Один без предложенного времени → он out; нет общего согласованного → оба out. Не подменять случай отсутствующей договорённости random winner.

## Срез C — complete event/fund proof

- Банк автоматически собирается из допущенных вопросов; все 3 EASY и HARD-формы валидны по 20 вопросов, polls исключены, недостаток банка блокирует создание до резерва фонда.
- Оба игрока пары получают тот же HARD form/fingerprint. Online session не раскрывает будущие вопросы/ключи; клиент не задаёт результат/elapsed/server time.
- Потеря сети не приостанавливает матч; readiness-window начинается от согласованного времени. Повтор ответа/возврат — та же session/question.
- Призы first/second/два third; technically-out не получают; bye не создаёт вымышленного призёра. Меньше двух допущенных — отмена с одним возвратом. `reserved = paid + returned + remaining` на каждом подтверждённом состоянии, remaining в конце 0.
- Фонд резервируется средствами организатора единожды; concurrent другие траты не дают отрицательный остаток. Деньги/award marker/event transition — согласованы атомарно или через доказанную сохраняемую идемпотентную цепочку.

## Проверки и приёмка

Node gates всех tournament/qualification/worker/currency наборов + реальные Firestore/HTTP/rules. Матрица: 0/1/2/15/16/17 участников, три distinct rounds, same-form ties, missing rounds по текущему контракту, 501/502 и объём выше выбранного подтверждённого бюджета; unknown/malformed данные; конкурентные workers; crash после partial scan/lease/commit; CLOCK boundaries; обе стороны absent; одна absent; ничья played; два third; повтор payout.

Измерить время/RSS/read-write-count на закреплённом Node22 и стенде. Привести input/output hash, calc version, final event, bracket, ledger и escrow. Не суммировать pure prototype tests с production доказательством. Не снимать лимит 501, пока замена не выполняет весь тестовый путь. Публикация scheduler в production отдельно не разрешена.
