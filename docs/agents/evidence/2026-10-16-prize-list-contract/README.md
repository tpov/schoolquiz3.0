# 5.2 prize-list-contract — 2026-10-16

Story: «UX-O2 contract: one versioned dynamic ordered prize-list contract (CREATE/read/
reserve/rank/payout over the same list version; frozen fund = sum of each row exactly once)
plus the definitive rank/tie/technical-outcome→place mapping (absences, ties, byes,
FORFEIT/DOUBLE_OUT, shared third place from the bracket matrix, 0/1/2/15/16/17-participant
degenerate cases) with fund conservation reserved=paid+returned+remaining and terminal
remaining=0; legacy {first,second,third} events stay readable and immutable; written as a
versioned contract document with the compatibility table; evidence
docs/agents/evidence/<date>-prize-list-contract/».
Story-запись: `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` (id 5.2); статус в
tickets оркестратор не касался — перевод статуса не входит в эту историю.

## Статус: ПРИНЯТО владельцем 2026-10-16 (издание 1 без правок)

Решение 2 в `_bmad-output/mvp-candidate/owner-decisions-2026-10-16.md` («принимаю»), sha256
принятой редакции `366860da…`; разблокирована 5.4. Правило «два третьих места» подтверждено
без изменений. Историческая запись: договор написан как draft — утверждение — акт владельца
(схема 2.5/2.9). утверждение — акт владельца (схема 2.5/2.9 и 5.1). До записи
утверждения гейтится история 5.4 (`server-dynamic-prize-list`, исполнитель дельты Приложения A)
— `after = [{ story = "5.2", needs = "adopted prize-list and mapping contract" }]` в
`tickets.toml`; за 5.4 косвенно стоят 5.8/5.9/5.10–5.12. До утверждения финансовое
обязательство по новому списку невозможно (AD-17); сегодняшний CREATE остаётся legacy-формой.

## Входы (все прочитаны 2026-10-16 на baseline `e7129ba6ba9197c9246825b210b195b79f0461b0`)

| Вход | Где | Что взято |
| --- | --- | --- |
| Спека истории | `_bmad-output/implementation-artifacts/spec-5-2-prize-list-contract.md` (route full/pinned; `context: []` в frontmatter — карта кода спеки прочитана как список входов) | intent, границы Always/Never, таблица напряжений (7 строк), карта кода, критерии приёмки |
| Гейт UX-O2 | `contract-map.md` («Remaining gates» строка UX-O2/O3; «Superseded statements» строка про AD-17; NFR-1) + хребет, таблица UX-O2 | дословные оговорки гейта для карты покрытия (§7 договора) |
| AD-17 (и AD-9/AD-2) | `ARCHITECTURE-SPINE.md` хребта 2026-09-29 | динамический упорядоченный список, frozen фонд = Σ строк один раз, без второго третьего, одна версия для reserve/mapping/payout, conservation `reserved=paid+returned+remaining`, terminal remaining=0 |
| План продолжения | `USER-CONTINUATION-PLAN.md` §3 (ТЗ07B/C «два третьих места, сохранение фонда при выплатах/возврате») и §4 (0/1/2/15/16/17 участников; сохранение LS tie-breakers) | обязательные случаи консервации и запертость ничьих выше призовой матрицы |
| tickets.toml | `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` (5.2, 5.4, 5.7, 5.8) | границы: 5.4 — исполнитель дельты, UX-O3 (5.7) — вне договора |
| Код baseline | `functions/tournament-policy.js` (:84-98, :100-109, :167-179, :187-219, :243-247, :303-321, :324-389, :392-414), `functions/tournament-service.js` (:14-15, :49, :59-60, :82-87, :95-101, :111-127, :148-155, :159-164, :165-177, :183-196, :225-247, :332-336, :350-359, :376-382, :383-392), `functions/course-tournament-controller.js` (:79-94, :169-178) | факты §2 договора и построчные указатели Приложения A |
| Формат | принятый `online-o2-contract.md` и родственный `online-o1-timing-contract.md` (издание, журнал изменений, карта покрытия, A-приложение, шаг утверждения) | документная дисциплина «одна семья гейт-договоров» |

## Выходы

| Файл | Что несёт |
| --- | --- |
| `_bmad-output/mvp-candidate/epic-competitions/prize-list-contract.md` | издание 1: схема v1 и каноническая заморозка (D1), liability = Σ строк с возвратом излишка (D2), одна версия на CREATE/reserve/read/mapping/payout (D3), ничьи сняты выше — без сплитов (D4), два третьих места организаторскими строками с детерминированным назначением, без авто-дублирования и сплита (D5), матрица технических исходов, проверенная исполнением (D6), byes/невоспроизводимые места → возврат (D7), дегенераты 0/1 — полный возврат (D8), идемпотентность на существующих ключах (D9, §5), legacy-проекция с выплатной эквивалентностью (D10, §6); conservation-таблица всех случаев (§4); карта покрытия оговорок гейта (§7); Приложение A — дельта реализации A1–A11 с построчными указателями, включая NO-change-охраны (A3/A4/A6/A7/A9/A11); завершающий шаг — утверждение владельцем (§9) |
| `docs/agents/evidence/2026-10-16-prize-list-contract/README.md` | эта заметка |

Ключевые решения (кратко): liability нового CREATE — сумма строк ровно один раз, депозит ≥
liability, излишек возвращается (D2); список заморожен при CREATE в `event.prizes`, ни один
путь не пишет его после (D3); ничьи невозможны структурно (LS tie-breakers, percent→elapsed→seed)
— правил сплита нет (D4); обоих третьих платят только две строки места 3, при одной строке
оплачивается `third-upper`, второй третий остаётся без приза — молчаливой чеканки нет (D5);
матрица FORFEIT/DOUBLE_OUT сохранена как есть и зафиксирована исполнением (D6); 0/1
квалификантов → CANCELLED → полный возврат, remaining=0 (D8); legacy события неизменяемы,
проекция `{listVersion, rows, maximumLiabilityBoxes}` выплатно эквивалентна (D10).

## Проверка

- `cd functions && /root/.zcode/server/node --test tournament-policy.test.js
  tournament-service.test.js` — 17/17 зелёные, без изменений кода (прогон этой истории до и
  после написания договора; node v22.16.0).
- `git status --porcelain -- functions/ shared/ android/` — пусто: история трогает только
  `_bmad-output/` и `docs/agents/evidence/`.
- Дополнительно, матрица мест D6 и арифметика §4 проверены исполнением чистой политики на
  baseline (временный `node -e`-прогон против `functions/tournament-policy.js`, без записи
  файлов): 4P all PLAYED → champion/runner-up/third-upper/third-lower; полуфинал FORFEIT →
  forfeit-проигравший без места, одна треть; полуфинал DOUBLE_OUT → финал разрешается BYE,
  champion = выживший; оба DOUBLE_OUT → placements пусты, FINISHED; финал DOUBLE_OUT → только
  третьи; 15P → size 16 и один bye (пары `[[1,16],…]`); 2P → один круг, третьих нет;
  legacy `planPrizePayouts` → paid 210 при 100/50/30 и обеих третях, технический placement
  пропускается и возвращается; отказы: «prizes support first, second and two thirds; no fourth
  place», «insufficient prize fund», «duplicate placement slot/player», «only first, second
  and two third-place slots are allowed», «unknown match outcome».
- Ручные проверки спеки: издание и журнал изменений в шапке есть; карта покрытия (§7) цитирует
  каждую оговорку гейта (contract-map UX-O2/O3 + хребет UX-O2 + superseded-строка AD-17) и
  сопоставляет каждой решение, включая «три строки — пример, не лимит» и явный вынос половины
  O3 в 5.7; каждая строка таблицы напряжений решена явно (соответствие строк → D1–D10
  зафиксировано в интро §3); conservation-арифметика для 0/1/2/15 квалификантов и излишка
  депозита сведена в §4; Приложение A содержит построчные указатели baseline и NO-change
  охраны; договор заканчивается шагом утверждения владельцем с именем гейтимой истории 5.4;
  ни один раздел не обещает изменений кода в этой истории.

## Осталось на владельца / риски

1. **Утверждение издания 1** — гейт для 5.4; без записи владельца CREATE/reserve по новому
   списку не включаются, сегодняшний legacy-путь остаётся единственным.
2. Сознательные дивергенции, которые 5.4 обязан внести после утверждения: валидатор v1 вместо
   фиксированного DTO (A1), liability = Σ строк (A2), заморозка `{listVersion, rows}` в
   событии (A3), назначение строк и `rowKey` в `planPrizePayouts` (A5), проекция чтения в
   `safeEvent`/`eventView` (A8), граница принятия legacy-DTO (A10), двухформенная ревалидация
   замороженных призов в `planPrizePayouts` (`:394`, A12). Чистые NO-change-охраны — A4, A6,
   A9; смешанные строки (изменение + охрана) — A7, A11; полный состав — Приложение A договора.
3. Осознанное следствие D5, которое стоит прочесть владельцу до утверждения: при одной строке
   места 3 оплачивается только `third-upper`; оплата обоих третьих требует двух строк —
   авто-дублирование запрещено AD-17.
4. Место 16 как верхняя граница (`place ≤ 16`, строк ≤ 16) следует из `MAX_QUALIFIERS`; места
   вне воспроизводимого множества {1,2,3} легальны, но при текущем формате сетки никогда не
   платятся и возвращаются — если владелец захочет отвергать такие строки на CREATE, это новая
   редакция издания, а не молчаливая правка.
