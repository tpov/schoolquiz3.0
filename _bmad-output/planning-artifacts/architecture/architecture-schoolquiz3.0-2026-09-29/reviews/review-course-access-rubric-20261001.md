# Course tournament access — independent rubric gate

**Verdict: PASS. Findings: none.**

Проверен только diff `ARCHITECTURE-SPINE.md` относительно `/tmp/schoolquiz-course-access-20261001/ARCHITECTURE-SPINE.md`: AD-17 и строки UX-O4 / UX-O4/access. Применён ранее прочитанный good-spine checklist; предыдущие gates и неизменённая архитектура не проверялись повторно.

- **Owner intent:** два основания допуска отражены прямо: скачанный соответствующий курс **либо** действительная ссылка на турнир. Organizer whitelist не вводится; решённая аудитория больше не числится открытым продуктовым выбором.
- **Enforceable Rule:** решение принимает сервер, при обязательном аккаунте и сохранении регистрации/отбора/сроков. Доступ не раскрывает private forms и не разрешает обход этапов.
- **Deferred boundary:** UX-O4/access называет владельцев Tournament/course и требует единый серверный proof-контракт с account/courseId/tournamentId до адаптера допуска. Локальный флаг скачивания или показанная ссылка не подменяют разрешение. Формат proof, новые роли и произвольные сроки ссылки не выбраны.
- **Scope preservation:** прежняя часть AD-17, включая фонд, reserve/payout и временные правила, не изменена. UX-O4 теперь явно отличает индивидуальный private quiz от приватного турнира курса; no-charge и single-sitting правила индивидуального прохождения сохранены. AD-29 не изменён этим delta.

Это gate согласованности документации. Целевые файлы reviewer не менял; app code, builds и runtime не проверялись.
