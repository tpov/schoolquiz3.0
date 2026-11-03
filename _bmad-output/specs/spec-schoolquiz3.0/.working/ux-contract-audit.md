# UX contract audit — full MVP SPEC

Дата аудита: 2026-09-30. Read-only входы; записан только этот файл на VPS. Upstream, adopted docs, SPEC и memlog не менялись. Runtime/build/install не выполнялись.

## Итог

PRD/addendum/main spine сохраняют функциональные границы, но не все поздние принятые UX-детали. Рекомендуется один spec-authored companion ux-constraints.md типа content с приведённым ниже содержанием/provenance. Это не финализация DESIGN/EXPERIENCE. Новых неразрешённых продуктовых противоречий не найдено: поздние явные решения применяются как overrides.

## Срез и источники

Все пути относительно _bmad-output:

- A: planning-artifacts/architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md final и .memlog.md, updated 2026-09-29T20:40, 44 entries, последняя spine finalized.
- P: planning-artifacts/prds/prd-schoolquiz3.0-2026-09-28/prd.md и addendum.md с amendment экзаменов.
- U: planning-artifacts/ux-designs/ux-schoolquiz3.0-2026-09-28/.memlog.md, updated 2026-09-29T21:04, 63 entries / 69 lines. Последний принятый целый экран — leaderboard-r1, строка65; точечный world CTA — строка68. World целиком не принят.
- D/E: DESIGN.md и EXPERIENCE.md того же UX run: discovery, только frontmatter, tokens пусты. Принятые детали находятся в U.
- UF: UX .working/html-review/v4/decisions.md и STYLE-RULES.md; поздние U overrides приоритетнее её устаревшей очереди.
- R: specs/spec-result-screen/.memlog.md, updated 2026-08-29T13:50. Его SPEC имеет companions:[] и устаревшие OQ.

UX продолжается параллельно; при фиксации SPEC проверить только delta после этого cutoff.

## Предлагаемый lean content companion

| ID | Принятое ограничение для ux-constraints.md | Provenance |
| --- | --- | --- |
| UX-1 | Home/runner/Shop — визуальные ориентиры. Принятые экраны сохраняются с поздними явно названными изменениями. «Минимализм» не разрешает заменить их собственным плоским стилем. | U:7,25–28,36,38,40,42 |
| UX-2 | Главный профиль целиком на одном экране для screenshot/share: ник, главный процент, паутина с обеими сериями квалификации/ролевой активности, ряд отдельных плиток трофеев. Рейтинг желателен, если помещается. Радар не заменяется таблицей/аккордеоном. | U:25–26,36,42; UF. Восемь трофеев и 390×844 — fixture, не лимит всех данных/устройств. |
| UX-3 | Topbar/statusbar/bottom/side menu всех экранов: app-chrome=#08080A. Только навигационные поверхности; содержимое и карточки вместе с ними не затемняются. | U:48; STYLE-RULES. Применяется также к ранее принятым экранам. |
| UX-4 | Home сохраняет исходную композицию и Continue; принята белая заливка Continue 5%. Shop: балансы справа от заголовка, нолики серые, золото золотое, также цены. Runner single восстанавливается по фактическому источнику/приложению, не отвергнутому HTML. | U:40–46,42. Home-r2/r4 отвергнуты. |
| UX-5 | Уроки: принятый список без добавленных «Начать/Открыть»; при наличии экзамена золотая карточка внизу. Не придумывать цену/допуск. Архив: то же семейство карточек, справа Download/«Скачать» либо Check/«Скачано». | U:40,42,46–50. Lessons и archive-r1 приняты. |
| UX-6 | My Quests: выбор категории; FAB+ создаёт категорию. До утверждения полоски проверок и краткий статус, после — вместо полосок только теги мест публикации горизонтальным рядом. Подробности раскрываются. | U:50,52,54–55. Полоски testing/validation/translation/условной translation-review — не прохождения игроков и не draft→publication; UI этапы не назначают четырёх людей/оплаты вместо P:FR-8. HTML approved не серверная модель: привязать к разрешённой projection revision/publication. |
| UX-7 | Confirmed result сохраняет процент, личный рекорд, общую полосу наград, оценку, top, discussion и текстовые действия. Сохраняются две прямые кнопки Comments/Report error по P:FR-13/14 и typed delivery states по A:AD-21. | U:56–58, result-r1 принят; это перенос Compose, не утверждённая pixel equality. Удаление pending-макета не отменяет pending/unknown/recovery. |
| UX-8 | NEXT: следующий lesson по order текущей темы; если его нет — действие EXAM ведёт к соответствующей exam/admission surface. AGAIN — повтор; подписи соответствуют действию. Discussion показывает total comments без «новых»/last-read marker и открывает modal list. | R:16,19,21–22. EXAM не обходит eligibility/sync/bank/согласие на цену/явный paid start и не даёт автоматический сертификат после раздела. |
| UX-9 | Arena: фиолетовый фон, карточки принятых уроков, FAB-лупа случайного поиска. | U:58,60, arena-r1 принят. Demo random из трёх карточек не алгоритм: RANDOM серверный по P:FR-18; prepare/reserve/recovery сохраняются. |
| UX-10 | Участники отбора: приняты ник/аватар, статус, число попыток, последний результат, время последней попытки; фильтры Все/С попытками/Без попыток из participants-r1. Последний результат не заменять рекордом/местом; нет попыток не равно подтверждённому 0%. | U:60–62, принято «хорошо». Люди/четыре строки/значения fixture; отсутствующие UI-model поля — target work. |
| UX-11 | Рейтинг отбора: компактные строки в стиле result, золотой лидер, выделение себя/свой результат. «Средний результат» означает averagePercent, не ratingPercent; серверные place/order не пересортировываются клиентом по среднему. Порог отбора и статус показывают authoritative event rule: текущий MVP top16 по P:FR36. Top32 в HTML не новое продуктовое решение; averagePercent не критерий квалификации. | U:63–65, leaderboard-r1 принят «ок». Шесть строк/место5/85%/3попытки fixture. |
| UX-12 | Сохранить принятый внешний вид runner_single, profile-v2-new, profile-rename, nft-logos; не приписывать им новые возможности. | U:42, UF. Approval NFT-logo mock не включает весь старый market/referral scope. |
| UX-13 | World CTA как принятая Home Continue, но с зелёным success #5CC97A; не добавлять выдуманный completion counter/прогресс ещё не начатого турнира. Три карточки меню, фиолетовый фон и chrome сохраняются. | U:68–69, точечное решение world-r2. Это не approval world целиком. |

Не переносить в companion Figma/Penpot/HTML workflow, адреса инфраструктуры, аккаунты, audit totals, fixture-числа или утверждение о готовности всего дизайна. HTML остаётся evidence визуального решения, не вторым источником бизнес-правил.

Не утверждено на cutoff: world целиком (r2 принимает только CTA); ранее rejected profile details, ordering/resume, arena_attention, shop states, NFT name empty, referral-components без позднего решения. Quest_list U:56 — reviewed-move-on, не отдельное final approval. Удаление review-макета не удаляет функцию. Guide content/пороги/seen-поведение P:FR-56 остаются UX prerequisite.

## Старые feature SPEC и нужное наследование

| SPEC и frontmatter | Вывод |
| --- | --- |
| quest-lifecycle: brownfield, shelf-mechanism, chat-contract, device-run-playbook, ADR0005/0006 | Не adopt целиком. Publication/rights/history/reporting уже в P:FR5–15/NFR2 и A:AD3/4/20. Сохранять существующие reporting/blocking/takedown/author ownership как regression baseline. Старые whole-quest publication/developer self-review не переопределяют позднюю независимую проверку каждого вопроса. Старые ban/role/OQ defaults не новые задачи; при изменении moderation читать поздний memo, не ранний CAP7. Исторический device playbook не разрешает production/device run. |
| result-screen: companions:[], derived_from memo | Конкретный semantic gap — UX-8 выше. Не adopt stale SPEC: NEXT/new-comment OQ закрыты; CAP2 recommendation исключён поздним scope now. Старая заметка об exam ladder не создаёт экзамены после каждого урока/раздела. Поздний result-r1 определяет композицию, FR13 добавляет private-error action. |
| sync: brownfield, SYNC spine, epic-breakdown, ADR0004, invariants | При сохранении adopted SYNC spine redundant SPEC не нужен. Не терять его AD14 (last success/error + отдельные counts состояний), AD20/21 (push только повод pull), AD31 (pair cursor) при сокращении contract map. Reviewer-offline и one-time destructive reset superseded; ordinary offline unlock сохранён A:AD7. |
| monetisation: paid-catalogue, gold-flows, box-economy, promotion-and-offers, purchase-verification, brownfield | Selective MON adoption достаточно. Не импортировать market/ad-auction/referrals/analytics/five-ad box/global cap2. Нынешние receipt/grant/refund/common drop-opening сохранены P/A и выбранными ADM. |
| charges: economy-constants, settlement-protocol, brownfield, MON box/gold, SYNC SPEC | Не adopt весь SPEC/transitive siblings. Resource/constants provenance уже связан MON spine с economy-constants; A:AD5/9/19 и exam сохраняют актуальные границы. Не импортировать Arena no-reserve, blanket unfinished-run refund, старый box cap, общий exam/tournament lifecycle или seed цены как immutable. Если contract map сокращён до main prose, оставить эту scoped ссылку через MON. |

## Preservation boundary

- P:FR52 «макеты ещё не утверждены» — исходный момент; поздние U approvals уточняют перечисленные экраны, не требуют нового вопроса. Остальное discovery.
- A memo требует сохранить accepted UX memo, но не перечисляет UX1–13; content companion закрывает этот пробел без изменения upstream.
- AGENTS/PROJECT-CONTEXT можно adopt как conventions, явно исключив historical scalar cursor/package/stack/environment seeds. Nested-content any-auth note не blanket permission на private forms/keys/reviews/errors/чужую историю: P:NFR2/A:AD20 и режимная redaction обязательны.
- Исторические PASS, наличие кода и HTML approval не новая implementation/runtime acceptance.

## Узкая проверка Top32

P:FR36 явно требует top16. Approved HTML v12/approved/leaderboard-r1.html:269 содержит literal «Топ-32»; TournamentEventDetailScreen.kt:41,99–100 отображает model.qualificationRule. U:63–65 не содержит явного пользовательского изменения размера отбора. Approval визуального макета не меняет FR36; UX11 выше исправлен, live unresolved conflict не объявляется. Cutoff остановлен на явном CTA решении U:68 (world-r2); дальнейший UX этим аудитом не преследуется.
