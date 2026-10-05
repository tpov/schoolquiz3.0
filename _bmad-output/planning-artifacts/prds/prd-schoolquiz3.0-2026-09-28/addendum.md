# Addendum — технические договорённости и границы доказательств

Приложение к завершённому PRD, 2026-09-28. Сохраняет принятые ограничения, технические детали и передачу в UX/архитектуру. Приёмка реализации выполняется отдельно после возобновления работ.

## Рабочий контекст

Авторитетный checkout расположен на VPS: `/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate`, ветка `continuation/mvp-candidate-20260926`, HEAD `1e5961263a4c1ac85ad03eb7894a51bf44228a4a`. Mac служит интерфейсом. Изменять исходники, запускать сборки, стенды, устройства и финансовые сценарии в этой PRD-задаче нельзя по поручению владельца. Существующие незавершённые изменения сохранены. Диск и кэши не входят в объём.

Будущие запуски выполняются только в согласованном demo-окружении. Production Google Application Credentials, публичный ADB и публичное открытие портов ради тестирования не допускаются. Правило сохраняется из исходного CONTEXT:54,60; действительные адреса, имена и сеть берутся из реестра среды перед будущим запуском. Доступы и окружение не меняются этой PRD-задачей.

## Архитектурные ограничения и базовые проверки

Из применимых `AGENTS.md` и `.claude/PROJECT-CONTEXT.md`:

- KMP, Decompose, Compose и Koin — действующая основа.
- Domain/data находятся под `shared/{core,feature}`, Android presentation — под `android/feature/*/presentation`.
- Новые держатели состояния — Decompose Components, если применимое ADR не говорит иначе.
- Compose-экраны отображают состояние и передают callbacks; не получают зависимости Koin и не вызывают repositories/use cases напрямую.
- Root/factory передаёт зависимости. Lazy DI не выполняет I/O. Не вводить Hilt/ViewModel/второй sender/второй mutex по общей Android-заготовке.
- После успешного применения sync сохраняет лексикографический cursor `(changedAtMs, docId)` во всех четырёх change journals (SYNC AD-31); одного максимального timestamp недостаточно для изменений с одинаковым временем. Старый CascadingSyncOrchestrator/contentsVersion остаётся историческим контекстом и не восстанавливается.
- Миграции добавочные, текущая схема базы определяется по фактическому устройству, не по исторической schema13. Сохранить непустые tables/data/auth/pending и совместимость старых receipts; destructive fallback запрещён.

Канонический gate репозитория — `./gradlew ciCheck --no-configuration-cache`; KMP `allTests` и Android/app JVM `test` по отдельности не заменяют gate. Выбор source sets и свежесть результатов подтверждаются фактическим выполнением. Fakes и поведенческие проверки предпочтительны; source-string совпадение не доказывает поведение. Команды сохранены как ограничения будущей проверки и здесь не запускались.

## Известная незавершённая регрессия

HANDOFF описывает ожидаемый RED в `.maestro/mvp/arena-matrix/offline-settlement-contract.test.js`: preflight должен требовать ровно пять известных quiescence counters и numeric zero у каждого; пустой/неполный объект проходит текущую проверку `Object.values(q).some(...)` в launcher. Production fix отсутствует. Это незавершённая работа, не закрытая story, и не предмет исправления в PRD-задаче.

## Правила наследования фактов

1. Приоритетный продуктовый договор — `PRODUCT-CONTRACT.md`; старый противоречащий текст не переопределяет его.
2. Commit, существующий в Git, ещё не означает наличие изменения в кандидате: требуется принадлежность истории HEAD.
3. Исторический PASS сохраняется с идентичностью HEAD/APK/среды и не объединяется с нынешней приёмкой без подтверждения.
4. Manifest и checksum фиксируют состав пакета; это не установщик, резервная копия или свидетельство исполнения.

## Сохраняемые механизмы по функциональным группам

Подробные ссылки и полный технический материал находятся в [извлечении договора](source-extract-contract.md), [извлечении Арены](source-extract-arena.md), [извлечении турниров и монетизации](source-extract-commerce.md). Ниже — опорные договорённости для последующих UX/architecture/stories, не новые решения этого чата.

- Ticket закрепляет account, lesson/questions version, difficulty, price и attemptId. RANDOM выбирает сервер. Исходные ID/body и account epoch сохраняются на retry/restart. Available/reserved различаются. Save-before-reveal и frozen intent не допускают повторного списания после неопределённого исхода.
- Резерв живёт до settle или явной отмены; произвольный автоматический TTL не вводится. Старый `spec-charges/settlement-protocol.md:64–68` с Arena без серверного резерва заменён поздним PRODUCT-CONTRACT.
- Доли автора сохраняют дробный остаток: пять оплат по 1 дают автору 4. Награды за публикационные переходы и сезон имеют устойчивую идентичность и не повторяются после редактирования/снятия/повторной публикации.
- В TZ07A сохраняются LS-модель и tie-breakers; расчёт использует точную рациональную систему до 3×3 и округление до преобразования в Number. Resumable phases/cursors/revision/hash, server-fixed versions и неизменяемые опубликованные ranking/bracket — уже принятые технические решения. 4096/4097 — размеры проверок, не продуктовый cap.
- UTC передаётся между клиентом и сервером; локальные timezone/DST учитываются в интерфейсе. Будущие ключи ответов не должны становиться доступны клиенту.
- Для покупок сохраняются account binding, durable purchase identities, receipts, permanent token fence, V2/V1 quantity binding, monotonic fence, cumulative partial refunds/schema2, bounded legacy repair и восстановление ранее оплаченного независимо от разрешения новых продаж.
- Gold SKU в коде: `gold_pack_small`=10, `gold_pack_medium`=60, `gold_pack_large`=150 (`functions/config/economy-constants.json:36–40`, `StoreProduct.kt:11–18`). Настройки настоящего Play каталога этими файлами не подтверждены. Multi-quantity gold поддерживается; premium quantity>1 явно отклоняется.
- Четыре цены premium за золото в проверенном коде конфигурации не найдены. Для будущей реализации владелец уже утвердил 1/5/15/100 золота за 1/7/30/365 дней соответственно; это новое продуктовое решение, а не свидетельство обновлённого сервера. `functions/economy-constants.js:147–170` требует положительную настроенную цену; иначе предложение недоступно. Цены зарядов 1/2/3 не являются premium prices.

Старое правило оценки «perfect и один раз за жизнь» из lesson-runner spec конфликтует с заменяемой оценкой по позднему договору и не является вариантом для новой PRD. Число `<100ms` из того же spec относится к вводу/ответу, а не к загрузке первого вопроса.


## UX-направление, заданное владельцем: premium

Отдельный экран покупки premium, золотое оформление, визуально привлекательная подача. Показать разницу между сроками через нормализованную стоимость за 30 дней при видимой полной цене срока. Конкретные композиция, оттенки, типографика и иллюстрации ещё не выбраны; не выдавать эти детали за утверждённый макет. Нормализация — вычисление для сравнения, не период фактического списания.

## Граница нового состояния загрузки

Согласованная заглушка до готовности вопроса относится к отображению состояния. Она не отменяет ранее принятые обязательства: локальный путь при готовом контенте, явное согласие на платную подготовку, фиксированные выдачи и непрерывный серверный таймер в турнирном сценарии. Глобальную остановку таймеров на время ожидания этой правкой не вводить. Сохранять предусмотренные отдельными сценариями состояния ошибок и восстановления.

## Указание владельца о конфигурации

Продуктовые тарифы и прочие изменяемые константы должны иметь централизованный серверный источник. Это касается в том числе первоначальной полной подачи за 1000 ноликов и частичной повторной проверки за 100 ноликов на каждый проверяемый вопрос. Для каждого вопроса перевод и все необходимые этапы включены в одну цену; дополнительные языковые или поэтапные платежи не вводятся. Техническая организация конфигурации ещё не проектировалась в этой беседе; не трактовать указание как автоматическое изменение всех констант исходников. Сохранить действующие договорённости о зафиксированной цене и версии уже начатых операций.

## Передача в последующую UX-проработку

Владелец включил переработку всего дизайна экранов MVP и планирует отдельно запустить UX workflow. Передать согласованные пользовательские сценарии и ограничения, исходные визуальные материалы при наличии, gold-направление premium и новые экраны/состояния. Не объявлять существующие source-extract документы готовыми UX-макетами; не менять принятую функциональную модель ради нового оформления без обсуждения.

## Закрепление работы проверяющего

Серверное назначение относится к конкретной отправленной версии, направлению и языку; намеренно независимые проверки имеют отдельные назначения. Текущая identity задания submission + lesson не доказывает единственного исполнителя. Идемпотентность результата не заменяет защиту от случайной выдачи одной работы нескольким людям.

Архитектура должна определить возврат/переназначение и частоту серверного сохранения, сохранив принятые права и подтверждённый прогресс. Срок закрепления и число одновременных назначений пока не заданы.

## Принятое уточнение: онлайн-работа проверяющего

Владелец согласовал серверное сохранение прогресса, паузу при потере связи и продолжение с последнего подтверждённого сервером состояния после восстановления связи. Это заменяет обсуждавшееся разрешение продолжать саму проверку офлайн. Существующий offline-first sync-контракт в части reviewer action требуется согласовать с новым продуктовым решением при проектировании; исходный код и ADR в текущей planning-задаче не меняются.

Повтор ранее сформированного сохранения/результата остаётся устойчивым и идемпотентным. Это не новая оплачиваемая попытка и не разрешение продолжать проверку при потере сети. Офлайн-режимы обычного прохождения и Арены сохраняются. Сроки закрепления, возврат/переназначение работы и техническая частота сохранения не выбираются этим уточнением.


## UX: обучение игрока по опыту

Владелец описал существующую, но незавершённую механику приветствий и обучающих диалогов по опыту игрока (FR-56). В UX-проработку всего MVP входят содержание сообщений, опыт и действия для показа, места появления, закрытие и поведение после просмотра. Сохранение просмотров уже есть в legacy-механизме, но конкретные правила повторения и новые пороги ещё не утверждены.

Вход по приглашению автора курса, друга или после знакомства с описанием продукта — обсуждавшийся контекст первого визита. Из него нельзя вывести поддержку прямой ссылки на конкретный вопрос, обязательный мини-курс либо заданный первый квест. Эти варианты не являются согласованными требованиями. Конкретный срок UX-работ не назначен.


## Арена: Entry, wallet, journal и runner

- Отдельные opaque cursors tickets/preparations, typed owner-bound observation кошелька, original owner/session epoch. Epoch проверяется до dispatch/write и после ответа; CancellationException не поглощается как обычный отказ/успех.
- Consent/requestId сохраняются до первого suspension; double tap блокируется синхронно. PREPARE record/dispatch раздельны; cancel intent сохраняется отдельно от доставки и может быть записан офлайн. Unknown prepare продолжает исходную команду, не создаёт замену.
- Wallet read не создаёт/регенерирует профиль. Stored life, receive time и projected recovery различаются. Проверяются UID/version/целочисленность/диапазоны/NaN/Infinity/резервы. Persistent cache не обязателен; его добавление требует миграции и Unknown для legacy, не нуля.
- Header/terms/bank/checkpoint читаются согласованно. Binding: ownerUid, ticketId, attemptId, snapshotSha256, requestId, точные termsJson/snapshotJson. Внутри транзакции проверяются актуальные immutable bytes/state, не один старый pre-read hash.
- Fresh claim только UNUSED/NONE/RESERVED с пустым checkpoint/revision0. Clock/seed/order фиксируются атомарно с LAUNCHED; resume/read-only и повтор claim не вызывают fresh factory.
- CAS: Saved/AlreadyCurrent/Conflict; AlreadyCurrent требует тех же canonical bytes и binding. Конфликт другого тела не допускает blind overwrite. Final CAS фиксирует COMPLETING/endedAt/body, freeze сохраняет retained SETTLE + original SUBMIT_ATTEMPT + first intent атомарно.
- FrozenLocally не квитанция. Replay после response loss/eviction не создаёт вторую очередь и не сбрасывает backoff. CANCELLED+retained SETTLE согласуется существующим proof-путём; отсутствие банка не мешает разрешённой отмене и не разрешает выдумать completion.
- Только Continuable открывает вопрос; восстановленный route — ResumeOnly. StateKeeper не хранит raw bank/body вместо journal. Отдельный Arena mode не вызывает ordinary StartLesson/SaveResult/CompleteAttempt/AbortAttempt в обход Arena contracts.
- Preserve ANSWERING/FEEDBACK/PAUSED/COMPLETING и режимные правила времени. Существующая задержка runner 250 ms и criterion ввода/ответа <100 ms относятся к своим исходным сценариям, а не искусственной выдержке загрузки. Новый loading-state не останавливает глобально часы турнира.
- Catalogue/quote/prepare используют одну eligible universe. Лимиты 1000 raw/12000 reads — ограничения существующей реализации, не продуктовая квота. Глобальные цена/сезон не основываются на усечённой странице.
- Server settlement/rating receipt привязаны к владельцу/попытке. Новое изменение оценки из прежнего результата требует корректного нового rating intent, а не повтора старого принятого значения; конкретная реализация здесь не выбрана.

## Жизненный цикл, конфигурация и приватные входящие

Три направления проверки: запрещённое содержание/слова, перевод, логическая корректность. Это не ровно три человека. Администраторы для логики были предположением, нейросеть — обсуждаемой возможностью; назначение исполнителей и AI-only схема не выбраны. Эти направления не создают поэтапные доплаты автора либо выплаты проверяющему.

- Bounded typed owner/curator/detail/history/payout views и server role validation; raw private placement/history закрыты. Review/submission replay сам по себе не является реализацией нового денежного журнала review fee.
- Immutable submitted content/revisions, independently auditable review results, новое admission и сохранённые frozen banks. Механизм определения зависимостей должен объяснять, что перепроверено и почему остальные результаты сохранены. Не сводить его к равенству изменённых полей; не добавлять исчерпывающий алгоритм в PRD без проектирования.
- Отдельная config sync должна определить согласованные server version/projection/cache/freshness и поведение при недоступности, используя существующие контракты. Не вводятся новый TTL, обязательная сеть для каждого действия или переоценка начатой операции.
- Приватное обращение автору — не существующая public-comment запись с новым названием. Unified inbox должен сохранять доступ к каждому исходному виду, lesson context и account isolation. Ответы/уведомления/вложения/автоперевод/общие DMs не добавляются техническим удобством.

## Турнир

- Переиспользуются шесть callables: prepareCourseTournamentBank, submitCourseTournamentOperation, submitCourseTournamentSession, fetchCourseTournamentEvent, fetchCourseTournamentSession, fetchCourseTournamentMeeting. Discovery при отсутствии контракта — ограниченное безопасное постраничное server view, не прямое чтение private collections.
- Client mutations CREATE/START_QUALIFICATION/PROPOSE/CONFIRM/READY; worker operations/time/result — server-only. Original operationId/body/owner+epoch и strict DTO versions/enums/IDs/integers/time, authoritative response до ACK.
- Сохраняется LS-модель и порядок tie-breakers. Exact rational system ≤3×3, округление до Number. Версии calculation/rounding фиксируются сервером до работы; опубликованный ranking/bracket не переписывается.
- Полный frozen manifest/input hash; сохраняемые фазы VALIDATE→REDUCE→SOLVE→PROJECT→PUBLISH→PUBLISHED, cursor/revision/lease/fencing, целостность при resume. Старый publisher не перезаписывает новую версию job; незаконченный job сохраняет нужный runtime/ICU contract. Invocation budget не ограничение entrants.
- Идентичные формы пары и неизменяемый fingerprint; клиенту только текущий разрешённый вопрос. UTC/server time на проводе, локальный timezone/DST в UI.
- Prize reserve/award marker/event transitions согласованы атомарно либо доказанной устойчивой идемпотентной цепочкой. Worker после crash/lease expiry продолжает событие и не застревает на первом элементе.

## Покупки, возвраты, зависимости

- Persistent token fence обоих денежных маршрутов; RTDN — сигнал проверки, не готовая инструкция начисления. V2 single-line binding с V1/SKU/order/account/original quantity, monotonic refundableQuantity, cumulative immutable receipt/audit/ledger.
- Purchase→grant→used/remaining provenance; schema1→2 добавочно, legacy full key сохраняется. Ambiguous verification не снимает fence одновременно с выдачей; unknown consumed token требует authoritative proof/bounded visible resumable repair. Никакой неразрешённой mass migration.
- Multi-quantity gold support присутствует в коде, premium quantity>1 отвергается; реальная Play policy ещё проверяется. `gold_pack_small/medium/large` соответствуют 10/60/150 в коде, но не доказывают Play Console readiness.
- ACK/consume по подтверждённому состоянию/типу товара; recovery/query/refunds не отключаются sales-флагом. Isolated Billing, который отключает и recovery, не закрывает этот сценарий.
- Dependency-only изменения отдельно: package/lock/runtime image hashes, актуальный audit/runtime/dev/transitive exposure, воспроизводимый clean install, syntax/import, полный current Node и real Auth/Firestore/functions HTTP/rules. Без массового latest/audit fix --force; major adaptation требует compatibility. Откат lock/image не удаляет данные.
- Актуальная официальная совместимость Billing/Play проверяется при разрешённом исполнении. Старые target API/версии не являются текущим store proof, публичный release здесь не разрешён.

## Достоверная будущая приёмка

- Один final candidate: source/backend/APK/schema/config/flags совпадают. Каждый run сохраняет versioned context и PASS/FAIL/NOT RUN/INTERRUPTED, уникальный каталог/логи, hashes и redacted identities. Старые квитанции не подтверждают новый run.
- Е2В становится PASS только при matching SETTLE и RATE_LESSON для одной попытки; промежуточная фаза остаётся NOT_RUN. Исторический PASS13 сентября не подтверждает нынешний Samsung/APK.
- Настоящий offline — доказанный полный network cut с сохранённым управлением, не только Wi-Fi off при adb reverse. Response-loss proxy обрывает ответ после подтверждённого upstream. Local Room proof не server settlement.
- Текущие readers допускают точные пары Room v14/v15, legacy recovery v14-only. Сохранён RED quiescence regression: ровно пять известных counters numeric zero должны проверяться до preparation/Maestro/network change. Production fix отсутствует; эта planning-задача его не выполняет.
- Одна тяжёлая сборка одновременно; физические телефоны вместо Android-эмулятора; semantic selectors после UI inspection, bounded waits, clearState:false. Server test clocks вместо изменения часов телефона. Старые launchers/логи привязаны к своим HEAD и не перезаписываются.
- Demo fixtures создают условия, приложение делает проверяемые переходы. Никаких production credentials или реальных финансовых покупок вместо sandbox. Секреты, токены, order IDs и приватные answer payloads не публикуются.


## Уточнения технической приёмки по ТЗ-00…06

Эти условия восстанавливают детали исходных контрактов, выявленные input reconciliation. Они не меняют FR-1…55, принятые тарифы/выплаты, замену оценки из прежнего результата или online review. Проверяющий работает онлайн с серверным сохранением и паузой при обрыве; обычные уроки и подготовленные прохождения Арены сохраняют предусмотренный offline. Описанные действия выполняются только в последующей разрешённой работе; новый PASS этим текстом не создаётся.

- **Каталог — незакрытый финансовый oracle.** Исторический R2 остановился на users-документе, before/after не получены; предположение о null не доказано. Отдельное актуальное завершение этой проверки не установлено, поэтому её нельзя считать закрытой по UI или общему gate.

  В будущей приёмке сохранить оригинальную regression `AppShellScreenTest#arenaCanReopenAfterBackWithoutLoadingOrStartingALesson` и путь через настоящую оболочку; не обходить его тестовым Activity.

  Финансовая проверка: ограниченный read-only before → только просмотр/ручные страницы/Back → after по одной и той же области/снимку и владельцу. Сопоставить резервы, tickets, очереди серверных операций, выплаты, покупки и призы; изменение профиля из-за иной активности/регенерации объяснить отдельно.

  Сохранить строгую область чтения, owner-checks, SHA-самопроверку и редактирование вывода; не публиковать значения профиля, токены или transaction handle. Новые логи не затирают R1/R2.

  Изменение parser допускается по подтверждённому wire-формату с отрицательными проверками, не по старой гипотезе. Исторические APK, четыре карточки и fixture-title сверяются с актуальным состоянием; assertions не ослабляются ради прохождения. Источник: [ТЗ-00:19–40](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/00-BASELINE-AND-CATALOG.md:19).

- **Восстанавливаемая scoped-копия исходников.** До будущего собственного изменения сверить HEAD/ветку/dirty и отсутствие конкурирующей сборки или редактора того же worktree; проверить пригодность существующей копии либо отдельно сохранить index, tracked diff и точные новые scoped-файлы так, чтобы восстановление не затронуло unrelated paths. Хеши/state manifest не заменяют эту копию. Не очищать дерево и не повторять исторический импорт/commit40 автоматически. Источник: [ТЗ-00:7–9,41](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/00-BASELINE-AND-CATALOG.md:7).

- **Lazy DI и обязательные зависимости.** Ленивая регистрация DI и создание графа не обращаются к сети, базе или часам и не дают разрешения на запуск. Отсутствующая обязательная зависимость не заменяется permissive fallback. Приёмка отдельно проверяет эти условия вместе с отсутствием нового sender/повторного gate. Источник: [ТЗ-01:26–32,49](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/01-READY-DRAFTS.md:26).

- **Строгая проверка Journal guards.** Использовать общие canonical `ArenaJournalFakes`, настоящий Journal со strict codec и реальные Room-проверки для checkpoint guards; полностью замоканный успешный repository или второй permissive fake их не заменяет. Проверить canonical terms, повреждённые bank/terms bytes при сохранившемся старом hash-поле, owner/ticket/attempt/revision/session mismatch и текущую terminal/decision-строку: отказ не меняет состояние. Fresh, CAS и retained terminal replay проверяются в своих допустимых состояниях; replay после eviction не заставляют снова проходить active RESERVED guard. Проверки сохраняют реальную текущую схему, а не возвращают историческую schema13. Источники: [ТЗ-01:38–49](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/01-READY-DRAFTS.md:38); [ТЗ-04:53–60](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/04-ARENA-RUN-WRITES.md:53).

- **Ограниченное обновление wallet.** Сетевой read вызывается явным событием либо согласованной ограниченной refresh-политикой; бесконтрольный polling и новый фоновый sender не вводятся. Сам повтор чтения не создаёт финансовую mutation. Новые период/TTL и обязательная сеть для готового локального вопроса этим не назначаются. Источник: [ТЗ-02:26](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/02-ARENA-BALANCE-AND-RECOVERY.md:26).

- **Typed recovery без queue-body в UI.** Entry передаёт presentation минимальные typed сведения о сохранённом settlement/cancel intent отдельно от observed server terminal, без raw JSON/queue-body. CANCELLED с retained SETTLE остаётся обнаруживаемым и предлагает существующую reconciliation; только strict proof и exact-resolution TX закрывают доставку. Отображение terminal не разрешает UI самостоятельно ACK/удалить очередь. Источник: [ТЗ-02:30–34](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/02-ARENA-BALANCE-AND-RECOVERY.md:30).

- **Fresh time/seed и вход finalize.** На explicit fresh invocation clock/seed фиксируются один раз до await; initial использует действующий `computeTimer` с frozen первым вопросом, difficulty и sessionMode. Resume и проигравший/повторный claim не создают новый initial и не переопределяют время. Finalize принимает существующий typed Completing, не произвольное raw тело; возвращаемый FrozenLocally не является score/receipt/reward/ACK. Это не новая формула времени и не численный SLA загрузки. Источник: [ТЗ-04:22–33](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/04-ARENA-RUN-WRITES.md:22).

- **Cancellation после локального commit.** Отмена coroutine после committed claim/CAS/freeze не откатывает запись, не создаёт новый ID или initial. При возвращении выполняется read/reconcile исходного действия; CancellationException сохраняется как отмена. Приёмка включает cancellation после commit и A→B→A до dispatch, внутри локальной работы и на выходе. Источник: [ТЗ-04:47,58](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/04-ARENA-RUN-WRITES.md:47).

- **Отдельный review pricing/season перед включением.** Изменения цены и сезонного расчёта проходят отдельный review до включения. При изменении eligibility catalogue/quote и prepare/server rank inputs сверяются вместе; выданные tickets не переоцениваются. Это исходный инженерный gate без нового органа согласования или rollout-процента. Источник: [ТЗ-06:29–32,42](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/06-LESSON-LIFECYCLE.md:29).

## Уточнения технической приёмки по ТЗ-07…11

Эти условия восстанавливают детали исходных входов по результатам input reconciliation и дополняют текущий `prd.md`. Они не разрешают реализацию, runtime, финансовые операции или выпуск сейчас и не создают новый PASS. Поздние отдельно разрешаемые KMP/public этапы не становятся условием завершения нынешней Android MVP-приёмки.

- **Турнир: bounded demo worker и ресурсы.** Worker запускается управляемо и ограниченно на demo, без внешнего Scheduler/PubSub по умолчанию и без production deployment; reconcile остаётся server-only. Тестовые часы контролируются серверной оснасткой, клиент не выбирает serverNow. Явно фиксируются и проверяются границы памяти, операций/времени invocation, числа и размера числовых представлений. Исчерпание бюджета сохраняет путь к завершению/контролируемому продолжению; технические guards не становятся квотой участников, вечным Pending или автоматической отменой. Источник: [ТЗ-07:21–29,44](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/07-TOURNAMENT-SERVER.md:21).

- **Турнир: полная матрица отбора и worker.** В дополнение к размерам и complete event/fund proof в PRD проверить same-form ties отбора отдельно от сыгранной ничьей матча, missing rounds по действующему контракту, unknown/malformed вход, конкурентные workers, crash после partial scan/lease/commit и CLOCK boundaries. После resume сохраняются полный frozen cohort, input hash, единственность вклада/публикации/выплат и завершение события. Полные tournament/qualification/worker/currency Node-наборы и реальные Firestore/HTTP/rules фиксируются отдельно от pure prototype: закреплённые Node22/стенд, время/RSS/read-write-count, input/output hashes, calculation version, final event, bracket, ledger и escrow. Уже согласованная коррекция численной погрешности не требует повторного продуктового решения; опубликованные результаты остаются immutable. Источник: [ТЗ-07:15–22,37–44](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/07-TOURNAMENT-SERVER.md:15).

- **Турнир: ограниченные чтения.** Fetch event/session/meeting использует ограниченные запросы с различимыми Unknown/Pending/Forbidden/NotFound, без бесконечного polling. Это относится к трём ресурсам, не только discovery list. Новый числовой лимит запросов, SLA или срок принудительного завершения события этим не назначается. Источник: [ТЗ-08:20–24](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/08-TOURNAMENT-CLIENT.md:20).

- **Турнир: границы асинхронной операции.** Исходные owner+epoch проверяются до SDK-call и после ответа; CancellationException передаётся наружу. Сохранённые original operationId и immutable body переживают restart/потерю ответа, ACK следует только за сохранением authoritative ответа. Ответы отправляются serial/idempotent; unknown outcome требует fetch текущей session, а не локального перехода/второго ответа. Unit/Firebase wire/DI матрица сохраняет malformed DTO, forbidden worker action, user switch, duplicate CREATE/ANSWER, process restore, lost response, late question/time и отсутствие key leakage, включая DTO/Room/log/analytics/exception. Источник: [ТЗ-08:20–24,39,44](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/08-TOURNAMENT-CLIENT.md:20).

- **Покупки: карта переходов до изменений.** Перед изменением purchase-revocations, purchase-verification, purchase-notifications, purchase-settlement, purchase-clawback, premium-grants, Play adapter и ledger составляется таблица состояний и переходов с фактическим подтверждением каждого перехода из авторитетного источника. Неоднозначный ответ не подменяется final success/rejection; новые provider proof и состояния не изобретаются из отсутствия ответа. Источник: [ТЗ-10:20–25](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/10-PAYMENT-AND-DEPENDENCY-HARDENING.md:20).

- **Покупки: точная failure-matrix.** Помимо grant→refund/refund→grant/concurrent, проверить потерю ответа после каждой финансовой TX, повтор RTDN и purchase/refund race с двумя устройствами; это отдельная проверка от одного Arena ticket на двух устройствах. Сохранить все отрицательные regressions постоянного token fence, wrong package/product/order/quantity/UID, временный Play failure, spent/part-used/unspent premium и multiple grants. Требуются неотрицательные balances/remaining, conservation и один ledger effect на исходную операцию. Полные purchase/premium/currency Node-наборы и реальные HTTP/Firebase/rules дополняются отдельно настоящим Play sandbox; исторические totals/fake Play их не заменяют. Источник: [ТЗ-10:9,29–31](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/10-PAYMENT-AND-DEPENDENCY-HARDENING.md:29).

- **Зависимости: база кандидата и несовместимые изменения.** До использования ранее сохранённого dependency-only кандидата сверить его базу с текущей; несовпадение требует нового отдельного среза, не слепого копирования lock. Security-исправление, требующее несовместимого изменения, оформляется отдельным согласованным ТЗ; оно не становится скрытой переработкой backend. Конкретная новая версия Admin SDK этим не выбирается. Источник: [ТЗ-10:38–43](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/10-PAYMENT-AND-DEPENDENCY-HARDENING.md:38).

- **Зависимости: runtime/privacy.** Проверить GAC/legacy messaging/Firestore/gRPC runtime paths при запуске без production credentials. Обновление lock не включает телеметрию или внешние запросы из приватного demo-стенда. Это условие dependency compatibility среза не подменяет отдельно разрешаемую проверку настоящего Play sandbox. Источник: [ТЗ-10:41](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/10-PAYMENT-AND-DEPENDENCY-HARDENING.md:41).

- **Совместимый rollout и финансовый rollback.** Сохраняется порядок сервер → миграции/новый клиент → feature flags → сценарии → владелец → отдельно разрешённый KMP → отдельно разрешённый public. Отключение новых продаж оставляет обработку ранее оплаченных операций/возвратов. Rollback не удаляет новую финансовую историю и не восстанавливает старую БД поверх подтверждённых денег. Источник: [ТЗ-11:68](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/11-END-TO-END-ACCEPTANCE.md:68).

- **Условия поздних KMP/public этапов.** Только после явного согласия на перенос проверить актуальное состояние KMP и новые пользовательские изменения, сохранить их, подготовить точечный merge проверенного состояния и повторить gates; историческая база не считается текущей. Public release требует другого отдельного разрешения. Тогда проверить актуальные официальные target API требования, фактически опубликованные versionCode, Billing/SKU/подпись, privacy, возрастную аудиторию, UGC/Families. Упомянутый в старом плане API36 остаётся историческим ориентиром, не текущим store/legal proof. Эти проверки не выполнены и выпуск не разрешён настоящим документом. Источник: [ТЗ-11:64–66](/Users/tpov/Servers/vps/workspace/projects/schoolquiz3.0/.local/worktrees/mvp-candidate/_bmad-output/planning-artifacts/mvp-remaining-inputs-2026-09-28/SCHOOL-QUIZ-AGENT-HANDOFF/11-END-TO-END-ACCEPTANCE.md:64).


## Проверка моделей и полнота запуска тестов

Изменения моделей проверяются по всем полям mapper с ненулевыми значениями, особенно по резервам; новые имена wire-полей проверяются клиентскими и серверными контрактными тестами. Новые серверные тесты регистрируются в действующем Node runner, чтобы обязательный запуск действительно включал их. Наличие тестового файла само по себе не является выполненной проверкой. Источник: CONTEXT-AND-STATE.md:58.

## Уточнение объёма: экзамены и сертификат, 2026-09-29

По явному решению владельца экзамены входят в текущую задачу MVP. Контрольные, финальный экзамен и сертификат больше не являются исключением; см. [уточнение PRD](prd.md#уточнение-объёма-от-2026-09-29). Предметная архитектура экзаменов и поздние решения её журнала — входы для согласования, а не доказательство реализации. Новая [общая архитектура MVP](../../architecture/architecture-schoolquiz3.0-2026-09-29/ARCHITECTURE-SPINE.md) прорабатывает связь с публикацией вопросов, версиями банка, результатами и общей приёмкой. Planning не возобновляет реализацию, сборки или стенды.

## Экзаменационные вопросы: обычный допуск проверенной редакции

Решение владельца 2026-09-29 отменяет обязательный pretest как условие попадания вопроса в оценку. Независимые проверки и утверждение конкретной редакции — единый вход в экзаменационный банк; статистика и жалобы направляют на повторную проверку. Не добавлять скрытые незачётные вопросы к двадцати зачётным и не требовать набора ответов для первого запуска курса. Это заменяет pretest-положения AD-13/15/22/23 старого exam spine; стабильные AD IDs будут согласованы при его обновлении. Исторические журналы сохраняют отвергнутый вариант как историю. Автоматический пересмотр действительности сертификата этим решением не утверждён.

## Неизменность выданного сертификата при ошибке задания

Владелец подтвердил 2026-09-29: «да сохраняем как есть». Понижающий пересчёт после подтверждённой ошибки в нашем задании не отзывает и не изменяет уже выданный сертификат. Его идентичность, подписанный снимок результата и контекста, дата и исходный срок действия сохраняются. Исправление записывается отдельной версией результата с причиной, без перезаписи сертификата. Это уточняет совместное применение exam AD-19 и AD-24; новые правила отзыва по иным основаниям не вводятся.

## Прогресс тем при обновлении курса

Сданные темы сохраняются по (courseId, stable themeId) при изменении вопросов; нужны только новые добавленные темы. Это явно выбранная владельцем политика, а не перенос оценки на произвольный новый ID. Новый final start проверяет актуальный утверждённый состав и фиксирует courseVersion/bank/config; активная сессия их не меняет. Подтверждённые ошибки оценки исправляются отдельной версией результата, не переписывая первоначальную попытку или выданный сертификат.
