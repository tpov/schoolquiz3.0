# ТЗ-03. Подготовка и управление разрешениями Арены

Зависимости: ТЗ-01/02. Результат: пользователь осознанно подготавливает RANDOM/SELECTED, видит сохранённые заявки/разрешения и может восстановить/отменить их. **Само прохождение — ТЗ-05.**

## Точка интеграции

Сохранить существующий `QuizzesConfig.ArenaLessons`, `ArenaLessonCatalogComponent`, `DefaultArenaLessonCatalogComponent` и stateless `ArenaLessonCatalogScreen`. `DefaultQuizzesComponent`/composition root передают зависимости; Compose не вызывает Koin/repository. Каталог остаётся доступен независимо от баланса и состояния Entry.

Подробное исходное предложение с 28 ссылками: `/Users/tpov/Documents/Codex/2026-09-06/school-quiz/work/arena-entry-ui-proposal.md`. Не переносить из предложения имена API, которых нет после ТЗ-01/02, без проверки.

## Интерфейс

1. Каталог с ручной загрузкой, автором, рейтингом/отсутствием оценок и ценой. Не подменять урок курсом и не отрисовывать unknown rating нулём.
2. Отдельная кнопка RANDOM: «0 ноликов», с честным предупреждением о резерве обычного заряда. Не бесплатный fallback при отказе SELECTED и не автозапрос 20 tickets на входе.
3. Диалог SELECTED: урок, сложность, согласие «не больше N ноликов», где N — показанная цена 1–100. По умолчанию не ceiling=100. Замороженная копия согласия не меняется при обновлении каталога.
4. «Мои разрешения и заявки» доступен даже при пустом/ошибочном каталоге и неизвестном/нулевом балансе. Раздельные страницы tickets и preparations, стабильные IDs и opaque cursors.
5. Баланс: доступно и зарезервировано раздельно, со свежестью/Unknown из ТЗ-02. Для ticket — его подтверждённые frozen price/lifeCost. Никакой суммы страниц под видом общего резерва.
6. Явные действия recovery, отмена с подтверждением, повтор той же операции; понятные состояния Offline/Pending/Attention/NeedsRecovery.

## Соответствие действий портам

- Confirm → один requestId, зафиксированный до первого suspension → `recordPrepareConsent` → одна `dispatchPrepare`. Блокировка двойного tap устанавливается синхронно. Записанная заявка ещё не означает выданное разрешение.
- Retry той же заявки → только `dispatchPrepare(originalRequestId)`. Не переписывать её параметры и не создавать новую при неизвестном исходе.
- PRICE_CHANGED → Attention; обновление каталога отдельно; новый потолок/ID только после нового согласия. Автоматически повышать сумму запрещено.
- «Найти на сервере» → `recoverPage`; «скачать сохранённые вопросы» → `recoverTicket`, не live lesson hierarchy. Отсутствие в странице не отменяет билет.
- Confirm cancel → один исходный cancel requestId → `recordCancelIntent` → отдельно `dispatchCancel`. Retry отмены использует уже сохранённую команду, не новое решение.
- Локальное CANCEL_PENDING → «ожидает подтверждения», не «ресурсы возвращены». Cross-device CANCELLED с retained SETTLE → явная reconciliation из ТЗ-02, не удаление очереди UI.
- Локальные списки — `localTickets` и `pendingPreparations` с собственными курсорами; не смешивать remote recovery cursor.

## Сессия и восстановление

Все intents и callbacks привязаны к исходному stamp и стабильному ID; A→B→A сбрасывает диалоги/страницы старой сессии. Callback старой карточки не может оплатить новую. Отмена Job не означает отмену уже совершённой серверной операции.

Состояние процесса восстанавливается из journal, не из сериализованного raw bank/body в StateKeeper. После restart не отправлять pending prepare автоматически без согласованной политики; явный retry предупреждает, что он может впервые успешно зарезервировать средства. PREPARE сейчас требует онлайн для сохранения согласия; CANCEL можно сохранить офлайн.

До ТЗ-05 не добавлять работающую кнопку fresh-start. Prepared summary — не authority запуска. LAUNCHED предлагает продолжить/восстановить, а не начать заново; terminal/recovery не превращаются в playable.

## Тесты и приёмка

Unit/reducer: double tap, PRICE_CHANGED, timeout после успешного hold, отмена coroutine, offline, два вида cursor, пустой каталог, exhausted quota, 5 selected, A→B→A до/после ответа. Проверить ноль неожиданных mutation при создании графа, открытии экрана, refresh и смене вкладки.

Compose/Android: диалог показывает исходную сумму, кнопки доступны по состоянию, ошибки и Unknown читаемы, EN/RU/UK, Back/rotation/process restore, tickets доступны при нулевом балансе. Существующие accessibility Continue/Info и навигационные тесты не ослаблять.

Maestro на стенде: явный selected prepare → pending/confirmed ticket → restart → тот же ticket → explicit cancel → серверный release один раз. Отдельно RANDOM, лимиты и offline cancel. Каждый резерв/возврат подтверждать журналом сервера; экран сам по себе не доказательство.
