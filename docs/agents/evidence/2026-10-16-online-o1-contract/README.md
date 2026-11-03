# 5.1 online-o1-timing-contract — 2026-10-16

Story: «ONLINE-O1 reconciliation contract: the shared AD-31 60s next-question window versus the
tournament's fixed endsAt=start+form.duration and the championship stage/match cutoffs (C=stage
end−1h−form duration, READY in [slot, slot+1h), 3-day stages, 7-day qualification window) —
explicit time budget and cutoff priority written as a versioned contract document
(online-o1-timing-contract.md), no silent truncation of the 60s window and no silent
stage/endsAt extension; owner adoption recorded before dependent adapters are enabled».
Story-запись: `_bmad-output/mvp-candidate/epic-competitions/tickets.toml` (id 5.1); статуса в
tickets оркестратор не касался — перевод статуса не входит в эту историю.

## Статус: ПРИНЯТО владельцем 2026-10-16 (издание 1 без правок)

Решение 1 в `_bmad-output/mvp-candidate/owner-decisions-2026-10-16.md` («принимаю»), sha256
принятой редакции `fa9ca506…`; разблокированы 5.5/5.6/5.7. Историческая запись: договор
написан как draft — утверждение — акт владельца (схема 2.5/2.9). утверждение — акт владельца (схема 2.5/2.9 истории
epic-ordinary-delivery). До записи утверждения зависимые истории гейтятся: 5.5 (`qualification-loop-close`,
«affects which late attempts are in cohort scope at cutoff»), 5.6 (`sessions-online-o2`,
исполнитель дельты Приложения A), 5.7 (`meeting-restore-uxo3`, C/READY границы) — все с
`after = [{ story = "5.1", needs = "adopted ONLINE-O1 timing reconciliation" }]` в `tickets.toml`.

## Входы (все прочитаны 2026-10-16)

| Вход | Где | Что взято |
| --- | --- | --- |
| Спека истории | `_bmad-output/implementation-artifacts/spec-5-1-online-o1-timing-contract.md` (baseline `280593004e154f7ef333a512af6c77bb48352eca`, route full/review thorough) | таблица напряжений (7 строк), границы Always/Never, карта кода, критерии приёмки |
| Гейт ONLINE-O1 | `_bmad-output/specs/spec-schoolquiz3.0/contract-map.md` («Remaining gates», строка ONLINE-O1) + хребет, таблица OQ-6 | дословные оговорки гейта для карты покрытия (§6 договора) |
| AD-31 | `ARCHITECTURE-SPINE.md` хребта 2026-09-29 (`_bmad-output/planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/`) | окно 60 с по умолчанию, старт от принятого своевременного ответа/исходного expiry, запрет молчаливого урезания окна/продления этапа, non-negotiables |
| AD-17 | тот же хребет | C = конец этапа − 1 ч − длительность формы; слот/подтверждение до C; READY в [slot, slot+1h); сервер стартует матч; неизменяемый слот |
| Принятый O2 | `_bmad-output/mvp-candidate/epic-ordinary-delivery/online-o2-contract.md` (утверждён владельцем 2026-10-08, blob `e142341e0baaa76c59a0e918bf7a89e567f3dc32`) | механика §2/§3/§4/§5/§7, с которыми компонуется договор; сам файл не отредактирован |
| Политика времени | `functions/tournament-session.js`, `functions/tournament-service.js`, `functions/tournament-policy.js`, `functions/lesson-question-window.js` на baseline | построчные факты §2 договора (клэмпы, min-правила, sweep FORCED_CUTOFF, клэмп создания квалификации, C/READY-математика, константа 60 с) |

## Отклонение от протокола (честная запись, закрыто)

Диспетчерское сообщение ссылалось на обязательную запись `## Spec Change Log` в спеке (entry
«2026-10-16, review pass 1 (bad_spec loopback)»: KEEP-инструкции и пункты (a)–(i)). На момент
старта ре-деривации секции `## Spec Change Log` и `## Review Triage Log` в файле спеки были
пусты (md5 среза `26c853e990330a33edffa70fdbaf8be8`, mtime 17:43:59Z): технический сбой
применения правок у координатора (замены без валидации совпадения) — записи были
восстановлены им на диск сразу после старта этой истории. Ре-деривация шла по телу спеки:
intent-contract, границы, таблица напряжений и абзац Design Notes «KNOW-BAD (review pass 1)» —
плотность конверта квалификации и клэмп на cutoff (`tournament-service.js:219-220`,
намеренный) как стартовая точка каждого решения, каждая дивергенция — в Приложении A.

**Сверка состоялась** (review pass 2, 2026-10-16): все пункты (a)–(i) проверены по тексту
договора; (a) клэмп сохранён (D2), (b) когорта — «как сегодня, отвеченной частью» (D2/A6),
(d) «два пути, одно состояние» (D4), (g) кап в A2, (i) формулировка здесь выправлена.
Недостающие уточнения внесены правками review pass 2 в издание 1 (журнал изменений договора):
(c) персистентность `openedAtMs`/`expiresAtMs` и `closedAtMs` sweep'а по захваченному краю;
(e) ready-ACK не применим к вопросу 0 (D6/A8); (f) метки терминала — серверные факты,
проекция за 5.9/5.10 (D4); (h) переверка указателей при утверждении/старте 5.6 (§8.4); плюс
явный PLAYED-исход для среза конверта матча (§4) и соответствие строк таблицы напряжений
решениям (§3).

## Выходы

| Файл | Что несёт |
| --- | --- |
| `_bmad-output/mvp-candidate/epic-competitions/online-o1-timing-contract.md` | издание 1: карта покрытия оговорок гейта (§6), решения D1–D9 по всем строкам таблицы напряжений (§3), таблица бюджет/приоритет по поверхностям (§4), композиция с O2 §2–§5/§7 (§5), Приложение A — дельта реализации с построчными указателями (A1–A9, включая «NO change»-охраны A5/A6/A7/A9), завершающий шаг — утверждение владельцем (§8, финальная секция) |
| `docs/agents/evidence/2026-10-16-online-o1-contract/README.md` | эта заметка |

Ключевые решения (кратко): конверт сессии фиксирован и никогда не продлевается — исключений нет
даже явных (D1); cutoff когорты абсолютен, клэмп создания остаётся (D2); окно капится только
краем конверта, кап захвачен в документе окна и объявлен здесь — молчаливого урезания нет (D3);
выдача стартует часы, expiry окна завершает попытку существующим скорингом (D4); C/READY и
скоринг нетронуты (D7/D8).

## Проверка

- `node --test functions/tournament-policy.test.js functions/tournament-session.test.js` —
  17/17 зелёные, без изменений кода (прогон этой истории; node v22.16.0).
- `git status --porcelain -- functions/ shared/ android/` — пусто: история трогает только
  `_bmad-output/` и `docs/agents/evidence/`.
- Ручные проверки спеки: издание в шапке есть; карта покрытия цитирует каждую оговорку гейта
  (contract-map + хребет OQ-6) и сопоставляет каждой решение; каждая строка таблицы напряжений
  решена явно, соответствие строк решениям зафиксировано в §3 договора, «выбор исполнителя»
  нигде не остался; Приложение A содержит построчные указатели; договор заканчивается шагом
  утверждения владельцем с именами зависимых историй (5.5/5.6/5.7).

## Осталось на владельца / риски

1. **Утверждение издания 1** — гейт для 5.5/5.6/5.7; без записи владельца адаптеры не включаются.
2. Сознательные дивергенции, которые 5.6 обязан внести после утверждения: окно между вопросами
   (A1), кап окна краем конверта (A2), терминация по expiry окна с kind `FORCED_CUTOFF` и
   `closedAtMs` = захваченный край окна — сегодня константа `endsAtMs` (A3), видимость через
   факт выдачи (A4), ready-ACK (A8, кроме вопроса 0). Всё остальное — NO change, охранено
   строками A5/A6/A7/A9.
