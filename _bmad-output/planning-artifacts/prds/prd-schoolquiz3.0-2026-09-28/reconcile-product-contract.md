# Reconciliation: PRODUCT-CONTRACT.md

Статус: независимая входная сверка перед quality gate, не Finalize/runtime acceptance. Дата: 2026-09-28.

Источник: /workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/PRODUCT-CONTRACT.md

SHA-256 входа: ec00cf4635b0f7f1b3607e8bac6228ff4ff7e73617a287cac450b2bb698aae43.

Сопоставлено с prd.md, addendum.md, prd-consolidated-addendum.md, evidence-inventory.md. Проверенный срез и SHA этих четырёх файлов записаны в memlog-audit.md; приведённые строки относятся к нему. «Companion» означает prd-consolidated-addendum.md. Приоритет: актуальные явные решения владельца → PRODUCT-CONTRACT → непротиворечащая история. Основные файлы и код этой сверкой не менялись.

## Покрытие договора

- PRODUCT-CONTRACT.md:3,7–10 → prd.md:16,20,59–61,109–157: приоритет; lesson lifecycle; куратор; собственные placement/rating/price/history; библиотека и отдельное событие; authorship/review/жалобы/причины.
- :14–19 → prd.md:203–223,269–295: RANDOM0/SELECTED1–100, charges, 80/20 с дробным остатком, milestone15/20/season10, одна заменяемая1–3 без self-rating, (sum+40)/(count+20), linear1–100 и fallback1, UTCquarter/20distinct/tie-order/no eligible=no winner.
- :23–29 → prd.md:213–257,435–445: frozen owner/version/difficulty/price/attempt, consent+reserves,20/day/5active, cancel не возвращает quota, reserve до settle/cancel без нового TTL, valid ticket offline, same-ID/body retry, unknown не success/no-debit, local claim не global lock.
- :33–41 → prd.md:303–349: допустимый immutable bank без polls/new text midmatch;3EASY×20/week/1score, top16;3day knockout/HARD20;общая встреча/час/серверные часы/reconnect;played ties,2third/no technical prizes;fund first+second+2third;byes,<2cancel/refund.
- :45–49 → prd.md:357–409: один тип boxes,ordinary1/day/offline accounting/online opening,premium отдельныйdaily/noads/nick/обе×2, gold10/60/150,premium1/7/30/365,server gold/Play money,preciseexpiry,specific unusedgrantrefund,persistent money identity.
- :53,55,57 → prd.md:24,459,479–481,483–515: exclusions,честные offline/anti-cheat пределы, единый кандидат/owner acceptance до KMP/public release, сохранность устройств. Companion:68–75 сохраняет способ доказательства.

## Найдены 2 рассогласования сопровождающего статуса

1. **Заменяемая оценка: closed owner interaction ещё open в evidence.** Договор :17 требует replacement, владелец уточнил способ в .memlog.md:20; prd.md:269–271 отражает его. Evidence-inventory.md:54 всё ещё говорит, что место изменения уточняется. Минимум: заменить эту фразу на ссылку FR-28/owner decision, отдельно оставить inspected-code limitation. Не утверждать готовую реализацию.
2. **Gold prices: принятое решение ещё external unresolved.** Договор :47 оставляет gold prices серверными, без выдумывания; владелец выбрал1/5/15/100 (.memlog.md:34–38). Prd.md:359–367,530 и addendum.md:43 корректны; evidence-inventory.md:78 перечисляет четыре gold prices как требующие уточнения. Минимум: убрать повторное продуктовое согласование и сохранить проверку server config/Play setup как недоказанную зависимость.

Это актуализация evidence, не потеря функционального объёма и не вопрос владельцу. Поздние review fee/question publication/online-review решения дополняют договор; прежние варианты не действуют. Приоритет C над старым no-reserve/perfect-lifetime-rating сохранён (addendum.md:37,45).

## Статус после исправлений root

2026-09-28T13:16:37.416406+00:00

Оба gap закрыты: evidence-inventory.md:54 отражает выбранную замену из прежнего результата без replay; :78 фиксирует gold prices1/5/15/100 как принятые, отдельно от Play setup. No remaining gaps этого входа; runtime не проверялся.

Срез SHA зафиксирован в позднем дополнении memlog-audit.md.
