# PRD reconciliation — step 2

Дата: 2026-09-29.
**Вердикт: FAIL — один load-bearing конфликт наследования; после локальной правки достаточно повторной сверки этого пункта.**

## Прочитано и границы

Полностью прочитаны canonical PRD (606 строк), addendum (212 строк) и новый main ARCHITECTURE-SPINE.md (252 строки, 24 AD). Рабочий чек-лист охватывает все FR-1–56, NFR-1–8 и exam amendment: [prd-checklist.md](../.working/prd-checklist.md). Дополнительно прочитаны непосредственно импортированные MON:ADM-8/16 и затронутые ссылки EXAM для проверки смысла bindings. Это input reconciliation, не кодовый аудит; исходники/стенды/устройства не запускались и не проверялись.

Полный пересказ продуктовых чисел и всей матрицы в spine не требуется: canonical PRD/addendum явно сохраняются как governing inputs (main:28,32), Binds и capability map могут ссылаться на них.

## F-PRD-1 — Импорт MON:ADM-16 сохраняет противоречащее правило источников коробок

**Severity:** blocker для согласованности источников; локальная документационная правка.

**Main:** таблица источников main:38 включает MON:ADM-16; AD-19 main:172 прямо binds MON:ADM-16.

**Действующий источник:** PRD FR-44:395–397 сохраняет один тип/общий запас с разными источниками, максимум одну ordinary daily и отдельную premium daily; источники не ограничены daily. PRD:397,522 явно исключает новую коробку за пять реклам и новый рекламный путь.

**Импортируемая норма:** MON spine 2026-09-01, ADM-16:227–229 предписывает «покупка и пять реклам» в одном серверном гранте и инвариант «две коробки в день», из которого всякий новый источник вычитается.

**Почему это потеря ограничения:** main:40 не импортирует весь каталог MON автоматически, а AD-19:174 говорит о daily/premium/прочих наградах. Однако именно ADM-16 импортирован по ID дважды без явного ограничения: его rule вводит общий cap на остальные источники и отвергнутый ad-путь. Это противоречащая архитектурная ссылка, а не просьба продублировать FR-44. Разработчик коробок получает две разные обязательные политики при корректном следовании Binds.

**Минимальная правка:** убрать MON:ADM-16 из действующих bindings либо явно ограничить заимствование единством таблицы дропа/однократным открытием; непосредственно отметить, что его «пять реклам» и общий cap2 отменены текущим FR-44 и не наследуются. Существующие daily ограничения/источники/one-box-type оставить по PRD. Изменять тарифы или вводить новые source limits не требуется.

## Остальное не потеряно

- **Полный объём и экзамены:** FR-1–56/NFR-1–8/CAP-1–21 сохранены; нет обязательного pretest/скрытых вопросов; exact approved revision, theme5E+15H/final20H, current composition со stable theme carry, отдельная correction и неизменный уже выданный certificate закреплены AD-3/14–16/24.
- **Серверная истина и старые доказательства:** immutable owner+epoch+operation/body, journal/CAS/save-before-reveal, retained intents, strict authoritative receipts, no local/terminal/queue-success; финансовые conservation/fractional carry/escrow, full-cohort resumption, old PASS≠current proof отражены AD-6–13/17–19/22–24. Runtime acceptance не объявлена выполненной.
- **Pending решения не выданы за принятые бизнес-правила:** tariff для lesson edits без affected questions и окончательное прекращение незавершённого review cycle прямо отложены. Rounding/level snapshot выбраны в разрешённой архитектурной зоне; нет нового individual cap/stage fee/developer commission. Three directions≠three people/AI-only.
- **UX/приватность и среда:** обычный offline, Arena/tournament/exam lifecycles раздельны; typed unknown/pending/stale, recovery, RU/UK/EN, gold premium, private report/public comments/inbox, опытные сообщения в UX scope. VPS-only/demo/no runtime, additive nonempty migration, separate KMP/public stages и actual Play sandbox preserved. Остальные тихие non-goals действуют через canonical PRD и main:40; дополнительных требований повторить их все в spine нет.

Spine, PRD/addendum и memlogs этой сверкой не изменены.

## Повторная сверка исправления, 2026-09-29

**Текущий вердикт reconciliation: PASS; F-PRD-1 закрыт.**

Повторно прочитаны main:38,172–174. Таблица источников и Binds явно ограничивают MON:ADM-16 общим drop/opening; rule исключает five-ad box и общий cap 2, сохраняет максимум ordinary daily 1, отдельный premium daily и независимые прочие награды без расходования этих daily limits. Противоречащие положения старого companion больше не наследуются. Исходный FAIL и описание сохранены выше как история сверки; новая строка не объявляет runtime/реализацию принятой.
