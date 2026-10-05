# Input reconciliation — baseline, privacy, runtime и settled UX

Дата: 2026-09-29. Этап: step 2, reconciliation входов; это не reviewer gate/reality-check и не runtime-приёмка.

**Вердикт: FAIL — одно существенное расхождение и одна неоднозначная граница наследования.**

Проверенный документ: `../ARCHITECTURE-SPINE.md`, draft, SHA-256 `5b6b2de6d2b8c3d1c2cfc810805b53de09ca399b0fb88802ddfbd115dd292ab8`. Строки ниже относятся к этому снимку.

Все чтения и этот отчёт выполнены на VPS в `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`. Исходники, spine, входы и журналы не изменялись; сборки, устройства, службы, финансовые сценарии и production credentials не использовались. Корневого README в checkout нет; прочитаны AGENTS.md и PROJECT-CONTEXT.md.

## Находки

### B-1 — HIGH: скалярный cursor теряет принятый tie-breaker

- Target: общий AD-8, строка 108; он прямо задаёт `cursor=max(changedAtMs)`, одновременно объявляя SYNC:AD-31 входом в строке 106.
- Источник: [SYNC spine AD-31](../../architecture-schoolquiz3.0-2026-08-31/ARCHITECTURE-SPINE.md), строка 269, требует персистентную лексикографическую пару `(changedAtMs, docId)` для всех четырёх журналов. [SYNC memlog](../../architecture-schoolquiz3.0-2026-08-31/.memlog.md), строка 113, фиксирует доведение пары до всех журналов и миграцию `sync_state.doc_id`; legacy cursor получает пустой docId и перечитывает граничную миллисекунду.
- Последствие: при нескольких записях одной миллисекунды и границе страницы следующий запрос по строгому `changedAtMs > cursor` пропускает непрочитанные записи. «Ограниченные выборки продолжаемы» само по себе этого не предотвращает.
- PRD addendum действительно повторяет старое сокращение `setCursor(max(changedAtMs))`. Это конфликт между поздно скопированным кратким описанием и явно принятым detailed invariant, а не явное решение владельца отменить пару. Более новая дата документа не делает scalar cursor осознанным override.
- Исправление: сформулировать в общем AD-8 именно pair cursor и продвижение только после успешного применения страницы; `max(changedAtMs)` оставить лишь описанием временной компоненты. Зафиксировать reconciliation сокращения addendum с SYNC:AD-31, не возвращать окно перекрытия как новую альтернативу.

### B-2 — MEDIUM: ADM-16 импортирован без явной отмены общего потолка коробок

- Target: строка 38 включает MON:ADM-16 в принимаемый набор; AD-19, строка 172, снова связывает его с текущими коробками. В тексте указаны daily/premium/прочие источники, но нет границы, какая часть ADM-16 продолжает действовать.
- Источник: [MON spine ADM-16](../../architecture-schoolquiz3.0-2026-09-01/ARCHITECTURE-SPINE.md), строки 222–229, объединяет единую таблицу дропа с общим потолком «две коробки в день», из которого вычитается всякий новый источник, и путём пяти реклам. [PRD](../../../prds/prd-schoolquiz3.0-2026-09-28/prd.md) FR-44, строка 397, оставляет максимум одну ordinary daily и отдельную premium daily, прямо разрешает другие источники и не добавляет пять реклам. FR-31/32 закрепляют отдельные награды 15/20/10 коробок, а турниры — самостоятельный фонд и призы.
- Последствие: добросовестный исполнитель может прочесть «MON:ADM-16 в границе MVP» как действующий глобальный daily ceiling и ограничить продвижение/призы/прочие законные выдачи. Общая иерархия поздних решений позволяет разрешить конфликт, но текущая точечная ссылка его вновь импортирует.
- Исправление: явно сузить наследование ADM-16 до единого server-owned открытия/дропа/идемпотентного grant в применимой части; глобальный two-per-day ceiling и пять реклам не переносить. Daily-лимиты относятся к своим источникам, не к общей сумме всех выдач. Это уточнение принятого FR-44, не расширение scope и не новый продуктовый выбор.

## Что сохранено и согласовано

| Вход / несущая граница | Результат сверки |
| --- | --- |
| KMP/Clean, Decompose Components, Compose render/callbacks, Koin через root/factory, pure domain | PASS: Design Paradigm и AD-1/2 соответствуют AGENTS и PRD addendum; не подменены Hilt/ViewModel или UI→repository. |
| Одна outbox, один sender/mutex, feature handlers, core без знания payload/feature state | PASS: AD-7 сохраняет границу. Direct Firestore writes из очереди запрещены. Online financial recovery не становится офлайн-покупкой. |
| Stable ID/body, async owner+epoch, потерянный ответ и cancellation | PASS: AD-6/7/11/12 сохраняют исходное намерение, не создают replacement operation, не принимают terminal/sent за proof. |
| Новые Arena retained intents против старого DELETE-on-sent/общего quarantine | PASS: override назван в таблице источников и AD-7/12; retained SETTLE/CANCEL не уничтожаются старым общим правилом. |
| Онлайн review против старого offline reviewer action | PASS: AD-4 явно приостанавливает работу на unknown/offline, требует authoritative ACK/generation; не отменяет offline ordinary/Arena. |
| Server-owned entity version, account-scoped проекции, отсутствие не равно zero/deletion | PASS с B-1: AD-8 сохраняет server ownership/unknown/scopes/resync, но cursor требует исправления. Предметные SYNC правила version только от содержимого и одинаковой private/public версии не переопределены. |
| Неотрицательные monetary balances, atomic effect/idempotency/receipt, собственный carry автора | PASS: AD-9/10/18. MON:ADM-19 остаётся предметным источником shortfall вместо отрицательного золота; новая архитектура не вводит отрицательный баланс или клиентское начисление по receipt. |
| Premium grant provenance и cumulative quantity refunds | PASS: AD-18/19 используют поздний PRD вместо старого общего expiry-only/full-refund прочтения. Sales-off сохраняет recovery/refunds; RTDN только запускает проверку. |
| Общий каталог всех старых MON возможностей | PASS: весь старый MON scope не импортирован. Нет обязанности заново включить рекламу, аукцион, рефералку или отдельные старые сезонные выплаты. B-2 касается только ложного наследования внутри уже включённых коробок. |
| Неизменяемые revisions/manifests, независимый исход вопроса, provenance незатронутых проверок | PASS: AD-3/4 сохраняют границы. Принятая новая редакция не следует за draft; старый банк frozen попытки не переписывается. |
| Роли/права, private errors и public comments, общий inbox | PASS: AD-1/3/4/20 сохраняют серверную проверку и readership исходной записи. Нет новых DMs/AI-only/Telegram/автоперевода. |
| Ключи/формы/токены/order IDs и диагностика | PASS: AD-15/17/20 запрещают утечки в клиентские проекции/Room/logs; PRD NFR-2 остаётся нормой для разрешённого acceptance package. |
| Runtime boundary | PASS: AD-22–24 не дают разрешения запускать службы, сборки, устройства или финансовые сценарии. Demo ticks используют server capability без автоматического Scheduler/PubSub; production adapter и public/KMP этапы остаются отдельными. |
| Миграции и rollback | PASS: AD-22 отменяет destructive reset, сохраняет pending/history/auth и исходный export; не откатывает старую БД поверх новых financial facts. |
| Исторические доказательства и новая приёмка | PASS: AD-24 различает candidate identity/статусы, реальные gates и отдельный Play sandbox; старый PASS не принимается за новый. RED quiescence defect не скрыт. |
| Поздние решения экзаменов | PASS: экзамены текущие; mandatory pretest отменён; отдельные lifecycle exam/tournament сохранены; content-fault correction не меняет issued certificate; passed theme переносится по stable ID, новые темы досдаются, final использует актуальный курс. |

## UX: settled границы и незавершённые части

Прочитаны [DESIGN.md](../../../ux-designs/ux-schoolquiz3.0-2026-09-28/DESIGN.md), [EXPERIENCE.md](../../../ux-designs/ux-schoolquiz3.0-2026-09-28/EXPERIENCE.md) и полный [UX memlog](../../../ux-designs/ux-schoolquiz3.0-2026-09-28/.memlog.md). Первые два пока discovery/frontmatter. Это не отменяет принятых отдельных пользовательских решений.

AD-21 в сочетании с constraint общего .memlog («принятые решения UX memlog остаются действующими») не противоречит settled UX. Проверено, что архитектура не требует отвергнутого профиля без двух серий радара, общего atlas вместо поэкранного обсуждения или новых серверных «технических карточек» вместо принятого результата.

Сохраняемые settled опоры для дальнейших UI stories:

- Основной профиль на одном экране: ник, главный процент, обе статистики паутиной, отдельные плитки трофеев; рейтинг при наличии места (UX memlog:25–28). Конкретные demo числа не превращаются в доменные данные.
- Приняты home-r5, уроки с условной золотой карточкой экзамена, общий тёмный navigation chrome #08080A и archive-r1 (UX memlog:46–50). Условная карточка не назначает собственную цену или допуск экзамена.
- My Quests: FAB создаёт категорию; до утверждения полоски стадий, после — горизонтальные publication tags вместо полосок, подробности доступны отдельно (UX memlog:50–55). Стадии проверок не означают прохождения игроков.
- Принят result-r1 по исходному Compose, без утверждения точного pixel match (UX memlog:58). Его композиция не отменяет архитектурную обязанность правдиво различать pending/confirmed.
- Quest list только reviewed-move-on, arena-r1 остаётся опубликованным макетом на оценке; демонстрационная FAB-лупа не доказывает server RANDOM/prepare и не отменяет reserve/recovery contract (UX memlog:56–59).

Полезная негейтирующая правка: добавить прямую ссылку на UX memlog в «Источники», чтобы downstream не искал принятые границы через общий .memlog. Не требуется копировать цвета, размеры или все макеты в архитектуру.

## Пределы вывода и условие PASS

B-1 необходимо исправить; B-2 необходимо снять явным scope override. После этого данная линза может дать PASS без повторного продуктового Coaching.

Не требуется переписывать все базовые AD в общий spine: предметные ссылки достаточны там, где локальный текст не противоречит им. Согласованные ранее обычные outbox числа/retention и injected clock не считаются новыми открытыми решениями только из-за общего Deferred; исключения retained financial/Arena intents должны оставаться явно feature-owned. Полная UX финализация, новый MON scope, runtime proof и внешние Play/legal проверки не являются условиями этой документальной сверки.

Этот отчёт не меняет verdict отдельного будущего reality-check reviewer gate.


## Повторная сверка после исправлений, 2026-09-29

**PASS. B-1 и B-2 закрыты.** В общем AD-8 и PRD addendum теперь явно задан persistent лексикографический cursor (changedAtMs,docId) для четырёх журналов. MON:ADM-16 ограничен common drop/opening; AD-19 явно исключает общий cap 2 и five-ad box и сохраняет независимые награды. Новых противоречий от этих поправок не обнаружено.

Проверенный SHA-256 spine: 9f67638c305c44c4eebbb740e2d46b1f3d517be60e624573e2810d7fdb6675b7. Runtime не выполнялся. Отдельный technology/code reality gate оформляется в review-reality.md.
