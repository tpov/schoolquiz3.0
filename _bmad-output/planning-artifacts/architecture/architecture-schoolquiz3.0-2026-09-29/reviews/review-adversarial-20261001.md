---
type: architecture-review
date: '2026-10-01'
lens: adversarial
scope: 'Initiative → independently implemented features; UX synchronization'
verdict: PASS
open_findings: 0
resolved_findings: 1
---

# Adversarial gate — 2026-10-01

**PASS после проверки исправления R-ADV-UX-1.** Открытых critical/high/medium findings нет. Незакрытые предметные и технические решения остаются prerequisites конкретных адаптеров; это не готовность реализации всего MVP.

Проверены [общий spine AD-1–30](../ARCHITECTURE-SPINE.md), [EXAM AD-18 и Deferred](../../architecture-schoolquiz3.0-2026-08-29/ARCHITECTURE-SPINE.md), [поправка ADR-0011](../../../../../docs/architecture/0011-noir-designsystem.md) и сохранение общего исходного spine из `/tmp/schoolquiz-architecture-ux-20261001/ARCHITECTURE-SPINE.md`. Применён тест двух независимых feature units, каждая из которых пытается соблюсти все AD и всё же получить несовместимые ownership/data/mutation contracts. Это архитектурная проверка, не runtime/test PASS.

## R-ADV-UX-1 — Контракт призовых мест был отложен дальше первого финансового обязательства

Severity: **medium**. Disposition: **autofix → resolved**. Исправление выполнено родительским агентом; этот reviewer целевые документы не редактировал.

Исходная строка UX-O2 требовала единый CREATE/read/reserve/rank/payout contract только «до выплаты по новому списку», а исходное дополнение AD-17 — «до исполнения произвольных мест». При этом новый CREATE уже принимал динамический список и резервировал его frozen фонд.

Два независимо построенных unit могли разойтись так:

1. Creation/economy принимает произвольные призовые строки, резервирует весь фонд и фиксирует событие. Полный список и сумма сохранены, двойного третьего места нет: денежные требования AD-17 выполнены.
2. Ranking/payout сохраняет существующий tournament lifecycle и откладывает mapping ties/technical outcomes к новым местам до своей предвыплатной границы O2.
3. Событие уже создало обязательство, а для допустимого исхода пары ещё нет согласованного получателя конкретного призового места. Frozen список и conservation не выбирают недостающее правило. Самовольный mapping нарушил бы product ownership; indefinite pending противоречит требованию завершить фонд.

Это пробел **момента gate**, а не запрос выбрать новый tie-breaker или изменить принятый prize UI.

### Повторная проверка исправления

Текущий [AD-17](../ARCHITECTURE-SPINE.md), строка 168, требует единый rank/tie/technical-outcome contract **до серверного CREATE/резерва** нового списка и явно разрешает лишь сохранение черновика без финансового обязательства. Текущий UX-O2, строка 315, ставит общий CREATE/read/reserve/rank/payout contract перед тем же первым эффектом, затем перед расчётом/выплатой.

Контрпример теперь нарушает прямой gate ещё на шаге 1. Старые события сохраняют свою версию/совместимость, новый продуктовый mapping не придуман. Finding закрыт.

## Другие попытки разойтись

| Независимые units / попытка несовместимости | Обязательная граница | Результат |
| --- | --- | --- |
| Learning передаёт процент или случайные 20 EASY как допуск; exam считает это полным покрытием | AD-14 + C3: exact revision manifest, confirmed tokens/generation, before-charge barrier | Отклонено: счётчики/звёзды/ACK не доказательство; новый paid start не проходит |
| Exam start выбирает ordinary wallet, economy выбирает plasma из той же цены | Main AD-14/UX-O1 и EXAM AD-18/Deferred | Отклонено до реального debit; 100/200 согласованы, источник/split открыт одинаково в обоих spines |
| Exam correction опережает signing и отнимает уже заработанный сертификат; profile выдаёт свой | AD-1/16, EXAM AD-19, C4 | Отклонено: immutable issuance fact в исходной terminal TX, exam owner, profile read-only, pending fulfilment без нового score check |
| Review считает текст перевода одобрением, publication принимает assignment score вместо question evidence | AD-3/4/26 и O5 | Отклонено: immutable revision/generation, явное tri-state решение по сегменту; полнота проверяется сервером; aggregation/cycle closure нужны до publication/settlement |
| Editor и translation выбирают UTF-16/code-point offsets независимо | AD-27 и O6 | Не разрешённая свобода: единый schema/index/serialization contract требуется до независимых адаптеров; конкретная единица намеренно не выбрана |
| Poll runner отправляет новый ID после lost response; stats считает второй набор новым голосом | AD-6/28 и O7 | Отклонено: respondent/question uniqueness сверх operationId; revision/series identity и counts snapshot фиксируются до API записи |
| Private entry открывает вторую сессию по неизвестному лимиту; statistics возвращает скрытый результат | AD-29 и O4 | Отклонено: server authority/atomic start-limit, resume той же попытки, unknown не permission; server-side show-result projection отдельно от author scope |
| Meeting UI хранит галочку локально, другое устройство восстанавливает только agreed slot | AD-17 и O3 | Отклонено: индивидуальные CONFIRM/READY в permitted read/ack projection необходимы до реальных галочек; radio не CONFIRM |
| Общая история пересчитывает balance или смешивает units, а author history применяет другие записи того же receipt | AD-1/8/9/30 и O10 | Отклонено: одна economy read-проекция, одинаковая identity/amount/resource/status между scopes, pending не income, права проверяет сервер |
| Login автоматически делает Premium claim, ad screen создаёт вторую коробку | AD-6/9/19 и O12 | Отклонено: ordinary auto и Premium explicit — разные operation keys/receipts, Premium claim не запускается на login, общего двойного эффекта нет |
| Inbox даёт curator доступ ко всему приватному тексту просто по роли | AD-20, строка 186 | Отклонено: общий inbox не расширяет исходную readership; приватный lesson report адресован разрешённому автору |
| Новая фича добавляет свой NOIR provider/token map и использует старую типографику ADR вместо final UX | AD-25 и ADR-0011:9–11 | Отклонено: общая designsystem authority, явный приоритет final DESIGN/EXPERIENCE, исторические таблицы и universal mono-button rule не альтернативный канон |
| Оболочка запускает старый first-open tutorial, считая FR-56 обязательным для текущей версии | AD-21/O9 | Отклонено: текущая версия явно без guide/пустых заглушек, будущий experience trigger и prerequisites перед отдельным включением |
| Generic sender удаляет retained Arena settlement или async response пересекает A→B→A | Неизменённые AD-6/7/11/12 | Отклонено: immutable body/owner+epoch, authoritative proof и feature exact-resolution обязательны |

## Проверка оставшихся вопросов

Это реальные choices или технические работы, а не скрытые holes, которые требуется произвольно закрыть в общей архитектуре:

- **Продуктовые:** источник/split exam debit; remaining private naming/default/access/exit/timeout/settings policy; TESTING aggregation и full-cycle closure; окончательное прекращение незавершённого review cycle; будущий userguide; опциональные course stars/placement. Их значения не выведены из HTML, старого backend или чужого режима.
- **Межкомандные технические:** O2 mapping/версия призов теперь до CREATE/reserve; O3 projection до реальных статусов; O6 schema до независимых adapters; O7 identity/counts до записи; O10 fields/access/order до списков. Фичи могут делать читаемый черновик и общие компоненты, но не объявлять зависимый адаптер завершённым независимо.
- **Среда/приёмка:** C4 уже binding, конкретные signing/runtime adapters и runbook до разрешённой активации; Android a11y и актуальный candidate gate не заменены HTML. Public release и KMP rollout не разрешены этим документом.

`status: final` описан как завершённая синхронизация договора с явными открытыми вопросами (строка 298), а не обещание готового backend. Утверждения build/runtime/Play readiness отсутствуют. Кодирования полного serializer/endpoint schema в initiative spine не требуется, поскольку владельцы и моменты согласования названы.

## Сохранение принятого baseline

Механически сопоставлены тела исходных AD-1–24 с текущими AD. AD IDs стабильны. Все исходные тела, кроме явно актуализированного AD-21, либо совпадают, либо полностью сохранены с добавлением требований final UX. В AD-21 сохранены typed states, mode readiness, Continue/Info/Back, RU/UK/EN, rotation/restore и запрет UI-owned delivery; прежнее UX discovery заменено финальными источниками, FR-56 уточнён поздним разрешением версии без guide. AD-25–30 добавлены без перенумерации.

Не ослаблены sole terminal writer, correction/economic generation barriers, exact reward arithmetic/receipts, immutable latest-earned certificate, retained Arena intents, Play/refund provenance, single outbox, cursor ties, non-destructive migrations и отдельные rollout gates. EXAM AD-18 добавляет только согласованный debit-source prerequisite, сохраняя 100/200, атомарность, cooldown, recovery и обычный provisional unlock. ADR-0011 сохраняет NOIR/SchoolQuizTheme nesting и designsystem ownership; переопределяет только исторические визуальные подробности через уже принятый final UX и снимает ложную свежую интерпретацию старого contrast PASS.

## Evidence и предел проверки

SHA-256 текущего повторно проверенного состояния:

| Документ | SHA-256 |
| --- | --- |
| Initiative spine | `b4a886ab4a7072ef77d1a9120eed3f49b8f9285b0788fc4b13c8751ef414f6af` |
| EXAM spine | `fa547f6b1e50b8357f9ad051d1346143d27763e3b1fbdf93b28b20a8bda437af` |
| ADR-0011 | `2470128e35ef5dfef99caac9d372dc1c451176752cb0e70a81774059fd543acb` |
| Исходный initiative snapshot | `db0d6c852074a631ffc61e47d823f5f854782e50b95d2043bafd973a75fc9b9e` |

Reviewer выполнял только чтение и запись этого отчёта; целевые spines/ADR не менял. Сервисы, сборки, финансовые операции и runtime-проверки не запускались. Нет новых выводов о текущих версиях технологий.
