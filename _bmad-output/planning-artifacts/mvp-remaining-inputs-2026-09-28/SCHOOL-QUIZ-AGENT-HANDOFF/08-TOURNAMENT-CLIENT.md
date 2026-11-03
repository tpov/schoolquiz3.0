# ТЗ-08. Турниры по курсам и урокам в приложении

Зависимость: ТЗ-07; серверный runner UI согласовать с ТЗ-05. Результат — пользователь создаёт/находит событие, проходит отбор, договаривается о времени, играет матч и видит серверные итоги. Никаких Telegram/ручного подбора/сертификатов.

## Не путать со старым UI

Существующие `TournamentEventScreen`/legacy leaderboard не являются уже подключённым новым MVP. Проверить текущие routes/DTO/ports. Серверные новые endpoints:

- `prepareCourseTournamentBank`
- `submitCourseTournamentOperation`
- `submitCourseTournamentSession`
- `fetchCourseTournamentEvent`
- `fetchCourseTournamentSession`
- `fetchCourseTournamentMeeting`

Фактические клиентские event-операции сейчас ограничены `CREATE`, `START_QUALIFICATION`, `PROPOSE`, `CONFIRM`, `READY`; сначала прочитать точные payload/roles, не угадывать по имени, кто вызывает каждую. Worker-операции клиенту не добавлять.

## Срез A — типизированный data/domain слой

- Строгие версии/enum/ID/целые числа/время и safe DTO; Auth UID не из payload. Сохранить original owner+epoch до старта SDK-call и после ответа. CancellationException наружу.
- Для mutation — durable original operationId и immutable body, повтор прежнего ID после restart/потери ответа; не новый ID на каждый callback/ручной retry. Не ACK до сохранения authoritative ответа.
- Для чтения event/session/meeting — ограниченные запросы, понятные Unknown/Pending/Forbidden/NotFound, не бесконечный polling.
- Если для discovery событий/своих sessions недостаёт endpoint, реализовать узкий paginated safe list на сервере. Не читать закрытые коллекции напрямую и не использовать старый overview как новый контракт без проверки.
- Будущие формы/ключи не попадают в публичный DTO, Room, log, analytics или exception. Игрок получает только допустимый текущий вопрос серверной сессии.

## Срез B — организатор и участник отбора

- Создание из существующей архивной страницы курса или допустимого источника уроков Арены. Показать источник, авто-генерацию и отказ при недостатке банка.
- Призы только коробками: first, second, third **для каждого** из двух третьих мест. До confirm показать полный фонд `first + second + 2*third`; сервер подтверждает reserve. Недостаточный фонд/ошибка не показывает созданное успешное событие.
- Отдельное событие с server status и датами; курс не переезжает из архива. Повтор CREATE восстанавливает то же событие.
- Отбор: три отдельные EASY-попытки в пределах недели, по одной зачётной на каждый round. Показать доступные/завершённые/ожидающие подтверждения rounds и завершение срока. Не начинать следующий round автоматически при response loss.
- Итоговый посев только по опубликованному серверному ranking, не локальному проценту и не промежуточным строкам.

## Срез C — договорённость и матч

- Показать stage deadline и отдельно крайний срок согласования с локальным отображением часового пояса; wire-time UTC/server ms. Двое должны подтвердить один slot; просто предложение ещё не договорённость.
- Явно объяснить: кто не предложил — out; без общего времени — оба out; после согласованного времени отсутствующего ждут час. Отображение countdown не принимает server decision.
- READY/reconnect продолжает одну server session. Нельзя использовать offline Arena pause/claim/checkpoint для турнирного авторитетного времени.
- Отправка ответа serial/idempotent; при неизвестном исходе fetch current session, не локальный переход/второй ответ. По окончании deadline сервер решает timeout.
- Итоги матча/bye/technical forfeit, top16 bracket и два третьих места берутся из authoritative event progress. Призы pending/paid/returned раздельно, local animation не начисляет коробки.

## Тесты и приёмка

Unit/Firebase wire/DI: malformed DTO, forbidden worker action, user switch, duplicate CREATE/ANSWER, process restore, lost response, late question/time, no key leakage. UI: разные состояния, доступность, EN/RU/UK, Back/rotation, timezone/DST display без изменения UTC deadline.

На demo через Maestro две роли участников + организатор: course → event → 3 qualifiers → seeds → propose/confirm → READY → один матч → server result/ledger. Длинные сроки в unit/integration воспроизводятся controlled server clock, а не изменением системных часов телефона/production; технические выбывания проверять отдельно.

Полный турнирный backend проверяется ТЗ-07; UI-путь должен доказать именно существующие реальные endpoints, а не полностью fake repository. Настоящий multi-device disconnect остаётся частью ТЗ-11 и не заменяется скриншотом.
