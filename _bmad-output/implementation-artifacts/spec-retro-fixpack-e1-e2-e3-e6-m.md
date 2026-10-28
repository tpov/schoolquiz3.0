---
title: 'Сквозной фикс-пак: action items ретро mvp-эпиков 1/2/3/6 и монетизации (e1..e6, m1..m5)'
type: 'bugfix'
created: '2026-10-28'
status: 'done'
baseline_revision: 75193bfa97038eeb7ec3f5665bc274c91b03774e
route: 'full'
review: 'thorough'
review_source: 'pinned'
review_loop_iteration: 0
followup_pass: true
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
followup_review_recommended: true
deferred:
  - summary: >-
      Фильтрованный android-next юнит-гейт (ConsentChainWiringTest, ReadyAckQuarantineWiringTest)
      неисполним в этом дереве: mergeDebugResources падает на чужих mid-flight правках ресурсов —
      первая чистая смена обязана его прогнать.
    evidence: |-
      `sgw ./gradlew :apps:android-next:testDebugUnitTest --tests …ConsentChainWiringTest --tests …ReadyAckQuarantineWiringTest`
      падает на `mergeDebugResources` («Can not extract resource from ParsedResource…values.xml»);
      среди грязных носителей resources.xml — strings.xml в android/feature/internet/social,
      lesson-runner, quest-authoring (MM, эпик-8/4.23/4.25 соседи); ни один файл этого фикс-пака
      ресурсов не трогает. Компиляция только Kotlin (`compileDebugUnitTestKotlin`) запускалась
      отдельно как stand-in — исход в Auto Run Result.
    location: >-
      apps/android-next
    severity: medium
context: []
warnings: ['multiple-goals']
---

# Сквозной фикс-пак по action items проведённых ретро (цикл владельца «после ретро билд»)

Intent: исполнить dev-доли action items уже проведённых ретро — эпик 1 mvp-candidate
(candidate-rig, re-verdict 26.10), эпик 2 (ordinary-delivery, 22.10), эпик 3 (arena-completion,
22.10), эпик 6 (exams, 17.10) и монетизационных ретро m1–m5 (26.10, носитель
sprint-status-monetisation.yaml). Фикс-паки e5 (spec-e5-retro-fixpack, 7057c540/b543a741) и e7
(spec-e7-retro-fixes, c415d09d→db8d11a4) закрыты раньше — их пункты в коде, не дублируются.
Границы: owner-пункты («tpov (владелец)», Play Console, деплой, продуктовые решения) не
трогаются; прод-данные Firebase не пишутся;Pathspec-коммиты только (в индексе ~6296 чужих
staged-удалений).

## Триаж-таблица

### Эпик 1 — candidate-rig (перевёрстка 26.10, action items 1–10)

| # | Пункт | Владелец по ретро | Триаж | Что делает фикс-пак |
|---|---|---|---|---|
| 1 | Решение по CAP-20 ((а)+(б) или candidate-верный install-over) | tpov (владелец) | **owner** | пропуск — продуктовое решение |
| 2 | Debug google-services.json → снять RED ciCheck, закрыть 1.7 | tpov | **owner** | пропуск — владелец по дизайну (owner-локальный файл) |
| 3 | F4: происхождение APK B + дисклеймер «B — не кандидат 1.2» | test-dev | **dev** | делаю: README 1.3 дополняется происхождением (ветка night/e3-e8-inception, worktree .local/worktrees/night-impl, tip окна сборки 1940b0dd — log HEAD не пишет, пометка честная), sha256 A «не снят, сертификат сочтён достаточным», дисклеймер суррогата |
| 4 | F5: решение 21:55 дословно в owner-decisions | tpov | **owner** | пропуск — дословная запись устного решения владельца недоступна dev |
| 5 | F6: проход сверки (28/30 таблиц, 23:10:48/23:15:49, SDK 36/37, schema-дисклеймер пикселя, REVISED) | test-dev | **dev** | делаю: документационный проход по трём README рига |
| 6 | F7: server-mutation-records.json → .txt | test-dev | **dev** | делаю: git mv в .txt + правка ссылки в drain/README.md:63 |
| 7 | F8: решение по персональным данным (gmail-адреса, token-XML) | tpov | **owner** | пропуск — редактура vs толерантность = решение владельца (git-история при редактуре) |
| 8 | F10: процедура пересева после рестарта demo-бэкенда | tpov/ops | **owner** | пропуск — «базлайн = staged экспорт, реестр посева» = решение владельца (open question ретро) |
| 9 | F10: погасить stale-запись deferred-work:509-510 | test-dev | **dev** | делаю: запись «exactly the five zero-valued counters» гасится с указанием fc71bfc1+024675ea/fa72bd73 |
| 10 | control-save: history-doc + чистый контейнер | defer | **defer** | пропуск — defer, нужен живой demo-контейнер |

### Эпик 2 — ordinary-delivery (22.10, action items 1–13)

| # | Пункт | Владелец по ретро | Триаж | Что делает фикс-пак |
|---|---|---|---|---|
| 1 | F7: consent-ветка вместо invalid-argument + пин ActivityKind↔ACTIVITY_KINDS | next functions shift | **dev (половина)** | server-semantics половина = owner (Open question 1 «Owner call»); делаю пин-тест (JVM, скрейп functions/economy-constants.js) — дрейф детектируется при любой диспозиции |
| 2 | F8: verified-снимок economy в обработчик (single read) | functions | **dev** | делаю: readEconomyConstantsDocument читается один раз в terms-блоке и передаётся в applyLessonResultEvents |
| 3 | F10/F11/F12: ownerUid, pool-probe в транзакцию, finite-guard | functions (small batch) | **dev** | делаю: ownerUid пишется и проверяется на begin/fetch/ack; pool-probe после existing-window ветки внутри транзакции; finite-guard ISSUE-ветки |
| 4 | F13+F9: premiumRegenDivisor === undefined ‖ < 1; release-on-throw вокруг terms- и version-блоков | functions | **dev** | делаю: guard + общий release-on-try (read-rejections больше не сжигают резерв) |
| 5 | F15: READY_ACK → NoLocalEffect в router | app DI | **dev** | делаю: SyncModule карта + wiring-пин в стиле LessonUnlockQuarantineWiringTest |
| 6 | F16: кросс-половинная фикстура capturedTerms | outbox/functions | **dev** | делаю: литерал PayableTermsCodec.toJson коммитится; mutation-terms.test.js принимает его дословно; Kotlin-тест зажимает кодек ровно этим литералом |
| 7 | F17: Koin-тест consent-цепочки | app | **dev (замена)** | поднять syncModule в JVM нечем (прецедент LessonUnlockQuarantineWiringTest) — делаю source-wiring тест на 4 несущих привязки; замена записана в носителе |
| 8 | F22: мёртвые константы FirebaseMutationTransport | trivial | **dev** | делаю |
| 9 | F1: 2.10 в tickets.toml; сверка контракта §8/§10.3 через owner-адопшн | owner + orchestrator | **dev (половина)** | делаю append 2.10 (done, e7b610b1); сверка контракта = owner-адопшн-цикл — пропуск |
| 10 | F2: пометить post-epic consent-закрытие; adoption-пин | owner | **owner** | пропуск |
| 11 | F3: поправить «adopted by the owner 2026-10-06» в README 2.6 | orchestrator | **dev** | делаю: 06.10 — только clock-rule; полная редакция 08.10 (owner-decisions, blob e142341e) |
| 12 | F18: перегенерация arena fixture-пинов | owner | **закрыто раньше** | spec-e3-retro-fixpack item 5 (19f8c255): генератор + регенерация; :platform:firebase зелёный — статус в носителе |
| 13 | F19/F21: полный functions-гейт (процесс — живёт); loud-skip + happy-path | process | **закрыто частично** | loud-skip уже в файле (кто — по git); happy-path осознанно отклонён в шапке файла («сеять урок целиком»); статус фиксируется в носителе как partial |

### Эпик 3 — arena-completion (22.10, action items 1–9)

| # | Пункт | Владелец | Триаж |
|---|---|---|---|
| 1–7 | Settle-replay ACK; consent/recovery UI; sync-trace read; demo-fixtures; fixture-генератор; ONLINE-O2 wiring; SETTLEMENT_REUSE const | dev | **закрыто раньше** — spec-e3-retro-fixpack `status: done`: 455f19ee, c305427a(+73c54cfa), 6521e5df, ba915081, 19f8c255, 98539e88, 919be5a5 + патчи c42d0c9c (наличие коммитов в истории проверено этой сменой) |
| 8 | Registry/spec reconciliation, mvpb, 3.12 season rows | owner | **owner** — пропуск |
| 9 | Process lessons | — | запись, не действие |

### Эпик 6 — exams (17.10, action items 1–8)

| # | Пункт | Владелец | Триаж |
|---|---|---|---|
| 1 | Деплой + reconcileExamSittings + индексы + Q-EX-5 | owner | **owner** — пропуск (деплой = go владельца) |
| 2 | Навигационная проводка exam/certificate + локализация | owner-session | **owner** — пропуск |
| 3 | Механическая сверка патчей (урок «фантомного фикса») | оркестратор build-auto | **process** — правило живёт в ретро/спеках; артефакта фикс-пака не требует |
| 4 | Обёрточный тест против строгого стаба для новых транзакционных модулей | dev-практика | **process** — практика, применяется в этой же смене (новые тесты m3/m4 — поверх стабов) |
| 5 | canonicalJson-консолидация | defer | **defer** — пропуск |
| 6 | exam-sitting.js разрезание | defer | **defer** — пропуск |
| 7 | Реанимировать/архивировать sprint-status.yaml | owner | **закрыто раньше** — 19393f2f: архив sprint-status-legacy-sync-2026-09.yaml + поясняющая строка; статус в носителе |
| 8 | Ревью pass-3 патчей + судьба опциональной награды | owner decision | **owner** — пропуск |

### Монетизация m1–m5 (sprint-status-monetisation.yaml, 34 пункта)

| Пункт | Владелец | Триаж | Что делает фикс-пак |
|---|---|---|---|
| m1-1 GMS-покупка, m1-2 отказанный токен, m1-4 configs/economy, m1-6 PAUSED/ON_HOLD, m1-7 RTDN-прод, m1-8 правило done-после-ревью, m1-9 AppleDouble, m2-1 golden-таблица+документ, m2-3 GMS-sandbox, m2-4 RTDN-триггер, m2-6 процесс приёмки, m3-3 AdMob/Play, m3-5 квитанция рекламной коробки, m4-5 деплой индексов, m4-6 операторская очередь, m4-7 витрина аукциона, m4-8 тренд index.js, m5-2 deep-link история, m5-3 живой прогон сезонов, m5-4 полные gradle-гейты, m5-5 свойство снимка в spec-m5-1 | tpov (владелец) / владелец+ops / планирование | **owner** | пропуск — деплой, Play/AdMob, прод-конфиги, продуктовые решения, наблюдение прода |
| m1-3 четырёхлинзовый ревью 1.1+1.2 | агент | **dev, вне фикс-пака** | отдельная ревью-смена (анализ, не код-фикс; результаты = новый бэклог) — остаётся open |
| m1-5 / m3-6 / m5-1 mvp-isolation красный | трекер/агент/треки | **закрыто раньше** | c1f07487 (e7 fixpack F7): пин догнал NSC (loopback strict, externals 204.44.66.27 + 192.168.31.218 с provenance), functions-гейт зелёный на HEAD — статусы open→done в yaml |
| m2-2 exactly-one тест shipped-дефолтов | dev | **dev — блокирован** | DEFAULTS = require(config/economy-constants.json) (economy-constants.js:22), в таблице 4 salesEnabled:true — тест «ровно одно» красный против HEAD; красный гейт недопустим → остаётся open до owner-пункта m2-1; проверено этой сменой |
| m2-5 tracked defers эпика 2 | dev | **defer-реестр** | пункт сам говорит «остаются открытыми» — действия нет, остаётся open |
| m3-1 инвариант пары часов магазинного слота | dev-цикл | **dev** | делаю: инвариант-тест «после PREMIUM_DAILY и после ad-выдачи оба часа равны» (box-service.test.js + ad-reward-handler.test.js) |
| m3-2 единый стиль отказа storedBoxCount | dev-цикл | **dev** | делаю: coded INVALID_ARGUMENT + перевод в verifyPurchase-обвязке + тест |
| m3-4 сверка замороженных текстов спек | владелец/спек-владелец | **dev (документально)** | делаю: Spec Change Log в spec-m3-2 (planAdRewardBox без boxCount — код ad-reward-progress.js:70) и spec-m3-1 (потолок query 8192 — код ad-reward-verification.js:88, в спеке отсутствовал) |
| m4-1 reviewStatus: REJECTED в stale-возврате + тест | dev-цикл (по go владельца) | **dev** | делаю: offer-auction-settlement.js stale-ветка + тест; деплой остаётся owner-гейтом |
| m4-2 проводные тесты decideOfferAdReview NOT_FOUND/FAILED_PRECONDITION + fetchOffersScreen unauth | dev-цикл | **dev** | делаю: economy-handler.test.js |
| m4-3 гейт decideOfferAdReview к isModerator/isDeveloper | dev-цикл (косметика) | **dev** | делаю: образец index.js:5013 (decideReport) |
| m4-4 вторая копия storedGold | dev-цикл (косметика) | **dev** | делаю: экспорт из offer-auction.js, импорт в settlement |

Итог триажа: **dev к исполнению — 18 позиций** (e1: 4, e2: 9 (из них 2 половинки), m: 8 с учётом m3-4; пересчёт без дублей в разделе «Работы»), **owner — 31**, **закрыто более ранними пакетами — 6** (m1-5/m3-6/m5-1 c1f07487; e2-12 и e3-1..7 через spec-e3-retro-fixpack; e6-7 через 19393f2f), **blocked — 1** (m2-2 ждёт m2-1), **defer/process — 4**.

## Работы

Functions (`functions/`):
- index.js: F8 (terms-блок читает документ один раз, передаёт в handler пятым аргументом;
  applyLessonResultEvents принимает verifiedEconomy), F9 (version+terms блоки под общим
  try/release-on-throw), F10 (ownerUid в окна: begin пишет, существующее окно чужого —
  permission-denied; fetch и ready-ack проверяют), F11 (pool-probe внутри транзакции после
  existing-window ветки), m4-3 (гейт decideOfferAdReview к именованной форме), m3-2 (перевод
  coded INVALID_ARGUMENT в verifyPurchase).
- lesson-question-window.js: F12 (finite-guard в ISSUE/DELIVERED ветках; испорченный
  allocatedSeconds → WINDOW_EXPIRED), openQuestionWindow пишет ownerUid.
- mutation-terms.js: F13 (`premiumRegenDivisor === undefined || premiumRegenDivisor < 1`).
- purchase-settlement.js: m3-2 (storedBoxCount — coded Error).
- offer-auction-settlement.js: m4-1 (stale-возврат флипает reviewStatus: REJECTED), m4-4
  (storedGold импортируется из offer-auction.js).
- Тесты: mutation-terms.test.js (F13-кейс + F16-литерал), lesson-question-window.test.js
  (F12-кейсы), lesson-ready-ack-handler.test.js (F10 ownerUid-кейсы, F11 repeat-after-drift),
  box-service.test.js (m3-1 premium-нога), ad-reward-handler.test.js (m3-1 ad-нога),
  economy-handler.test.js (m4-2: NOT_FOUND/FAILED_PRECONDITION + fetchOffersScreen unauth),
  offer-auction-settlement.test.js (m4-1), purchase-settlement.test.js (m3-2).

Kotlin:
- platform/firebase: F22 (мёртвые companion-константы FirebaseMutationTransport).
- shared/feature/economy/domain (jvmTest): F7-пин ActivityKind.entries ↔ ACTIVITY_KINDS
  (скрейп functions/economy-constants.js, стиль OutboxOperationsRegistryTest).
- apps/android-next (test): F15-пин (READY_ACK → NoLocalEffect в карте маршрутизатора),
  F17 (ConsentChainWiringTest — 4 несущие привязки).
- shared/feature/economy/data (commonTest): F16 — литерал-пин кодека (двусторонний).

Docs/статусы:
- evidence 1.3: README 08.10 — происхождение B + дисклеймеры (F4); README 08/09 + pixel README —
  сверка чисел, REVISED-пометки (F6); drain/server-mutation-records.json → .txt (F7).
- deferred-work.md — гасится stale-запись quiescence-гейта.
- README 2.6 (F3) — дата адопшна.
- tickets.toml эпика 2 — append 2.10.
- Носители статусов: RETROSPECTIVE.md ×3 (e1-перевёрстка-таблица, e2-таблица, e3),
  epic-6-retro-2026-10-17.md (п.7), sprint-status-monetisation.yaml (m1-5/m3-6/m5-1 → done,
  канонический патч), spec-m3-1/spec-m3-2 Spec Change Log.

Гейты: `cd functions && npm test` (полный), gradle через sgw (JDK 27):
:platform:firebase (F22), :shared:feature:economy:domain jvmTest (F7-пин),
:shared:feature:economy:data jvmTest (F16), :apps:android-next test (F15/F17 —
фильтром классов, модуль несёт известные env-красные), ktlint по затронутым файлам.

## Auto Run Result

Status: done
Baseline: 75193bfa (night/e7-commerce)

### Реализация (дельты к триаж-таблице)

- m3-1 (инвариант пары часов) — **закрыт без нового кода**: обе ноги инварианта запины
  существующими тестами ещё в 7de873f2 (решение владельца 12.10): premium-нога
  `box-service.test.js` («забрав магазинную коробку кнопкой, закрываем слот и рекламе»),
  ad-нога `ad-reward-progress.test.js` («магазинная коробка, взятая за просмотры, закрыта
  и премиальной кнопке»). Ретро-файлинг оказался избыточен; в yaml — open→done.
- m3-2 — кодированный INVALID_ARGUMENT на `storedBoxCount` + юнит-пин; перевод на провод в
  verifyPurchase НЕ добавлен: catch `settlePurchase` классифицирует любой сбой транзакции как
  UNAVAILABLE раньше обвязки — перевод был бы мёртвым кодом. Внешний контракт (UNAVAILABLE,
  retry later) не менялся.
- deferred-actions.test.js: перепинован литерал вызова обработчика (5-й аргумент
  `termsEconomy`); смысл пина (release в catch приёмника) сохранён.

### Гейты (исполнено 2026-10-28, VPS)

- `functions npm test` — **138/139**; единственный красный `arena-lifecycle-views-handler.test.js`
  — чужой mid-flight: история 4.21 (сиблинг) добавила в ENDPOINTS `quoteLessonProposalFee`/
  `decideLessonProposal` в незакоммиченной правке теста при отсутствии прод-половины в дереве
  (git diff теста — только их правка). review-queue-worker и deferred-actions — зелёные
  (первый падал в смешанном прогоне, вторая — на моём перепинованном литерале, исправлено).
- `sgw ./gradlew :shared:feature:economy:data:jvmTest :shared:feature:economy:domain:jvmTest
  :platform:firebase:testDebugUnitTest` (JDK 27) — зелёные (F16-литерал, F7-пин, F22).
- `:apps:android-next:testDebugUnitTest` (фильтр трёх wiring-тестов) — **не выполнен**:
  компиляция модуля заблокирована чужим незакоммиченным брейком
  `:android:feature:lesson-runner:presentation` (`ArenaRunnerContent.kt:93`,
  CircularProgressIndicator — чужая mid-flight правка Noir-диалогов в общем дереве). Замена —
  stand-in проверка тех же File+string-ассертов скриптом (PASS); задача остаётся за первой
  сменой после приземления 4.21-сиблинга.
- ktlint: `economy:data`/`economy:domain` (source sets моих пинов) — зелёные;
  `apps:android-next` main+test source sets — зелёные; `platform:firebase` — предсуществующая
  корзина чужих файлов + строка 47 Transport-а (выше моей правки, на HEAD та же) — атрибуция
  без починки.

### Коммиты

- `d111fb47` — e2 functions pack (F8/F9/F10/F11/F12/F13/F16 + re-pin deferred-actions).
- `6c38a53c` — ремонт дерева: functions/lesson-proposals.js приземлена, потому что общий
  index.js унёс в d111fb47 чужой require истории 4.21 (сиблинг правил тот же файл в worktree);
  без модуля чистый checkout не грузил приёмник. Инцидент записан честно: часть чужих
  4.21-правок index.js едет в d111fb47; остальное (тесты, витрины) — за сменой 4.21.
- `e48ac699` — e2 Kotlin pack (F7-пин, F15+пин, F17 wiring, F22, F16-литерал).
- `05ae0873` — m-pack (m3-2, m4-1, m4-2, m4-3, m4-4).
- `117524c6` — e1 evidence (F4/F6/F7 + переименование drain-записи).
- этот коммит — носители статусов (ретро-доки e1/e2/e3/e6, tickets 2.10, F3 README 2.6,
  yaml m1-5/m3-1/m3-6/m5-1 → done, Spec Change Log m3-1/m3-2) и сама спека.

## Review Triage Log

Отклонение формы: линзы исполнены самой сессией, не сабагентами — в этом харнессе нет
инструмента запуска сабагентов; метод каждой линзы (blind-hunter N=10 при 152 kB диффа,
edge-case с path-tracing и deletion/claims-проходами, verification-gap с прогоном проверок,
intent-alignment по исходному намерению) исполнен дословно, но одним исполнителем.

### 2026-10-28 — Review pass (follow-up на done-спеке)

- verdicts: 14 findings — high 3, medium 1, low 5, false 5, maybe-false 0
- findings:
  - `[high]` `[patch]` ConsentChainWiringTest читает межмодульные исходники путями без `../../`
    (резолвятся внутрь apps/android-next) — тест F17 упал бы на собственном `assertTrue(file.isFile)`
    при первом же прогоне; сегодня замаскировано чужим resource-брейком модуля — проверено:
    все прочие File-тесты модуля используют module-relative `src/main/...`
    (grep по apps/android-next/src/test), workingDir в gradle не переопределён; строка 39
    (148 символов) сверх max-line-length. Фикс применён: префиксы `../../` + разбиение строки;
    резолв путей доказан ls-ом из каталога модуля, строки ≤120.
  - `[high]` `[patch]` (edge-case lens, тот же дефект независимой линзой) — тот же фикс.
  - `[high]` `[patch]` (verification-gap) broken-verification: за F17-пином нет ни одного
    зелёного прогона — гейт модуля заблокирован чужим mergeDebugResources; после патча пути
    доказуемо резолвятся, сам прогон остаётся за defer-пунктом ниже.
  - `[medium]` `[defer]` фильтрованный android-next юнит-гейт (F15/F17-пины) неисполним в этом
    дереве: mergeDebugResources падает на чужих mid-flight strings.xml (social/lesson-runner/
    quest-authoring, MM соседей); ни один файл фикс-пака ресурсов не трогает — записано в
    frontmatter `deferred`, первая чистая смена обязана прогнать гейт.
  - `[low]` `[patch]` sprint-status-monetisation.yaml `last_updated` потерял часы-минуты
    формата («10-28-2026» против «10-26-2026 21:57» у соседних записей) — восстановлено
    «10-28-2026 00:43» с пином на момент носителя (f90396db = 00:43:59 UTC).
  - `[low]` `[reject]` повтор begin-window с потерянным в теле nextQuestionId отказывает как
    «different transition», а не поимённой формой: честный клиент повторяет то же тело
    (F11-тест именно это и делает), отказ остаётся invalid-argument; защитная ветка на угол,
    куда честные клиенты не попадают, — сложность без повседневного вреда.
  - `[low]` `[reject]` pool-проба теперь сканирует questions внутри транзакции (дольше холд):
    скан существовал и до пакета на каждом begin; перенос внутрь — именно то, что делает
    F11-повтор корректным, цена присуща самому фиксу.
  - `[low]` `[reject]` m4-2-тест штампует moderatorLevel в общий world()-фикстуру: тестовая
    внутренность, сюита зелёная в смешанном прогоне.
  - `[low]` `[reject]` (edge-case lens, дубль повтора с потерянным полем) — см. выше.
  - `[false]` DELIVERED-окно с `answerDeadlineMs === undefined` дало бы NaN remainderMs:
    опровергнуто — deliveryUpdate пишет числовой deadline в каждом пути кода; недостижимо
    без прямой порчи хранилища, поведение не менялось этим пакетом.
  - `[false]` OPEN-окно с нечисловым expiresAtMs не истекает бы: опровергнуто — ISSUE-перевыдача
    перезаписывает delivery целиком свежим конечным дедлайном от allocatedSeconds
    (functions/lesson-question-window.js, ветка ISSUE).
  - `[false]` дублирование F16-литерала в functions- и Kotlin-тестах: это и есть кросс-половинный
    пин — дрейф любой половины ломает одну из двух проверок; свойство задумано.
  - `[false]` 4.21-обработчики предложений «едут» в коммитах фикс-пака: на HEAD опровергнуто —
    полный мир 4.21 приземлён (d074873e, 7dd3ed61), lesson-proposals.test.js зелёный standalone
    (exit 0 этой сменой), заезд записан в списке коммитов спеки.
  - `[false]` экранирование `\|\|` в ячейке таблицы статусов ретро эпика 2: корректный GFM,
    рендерится как `||`.
- deletion check (edge-case): lessonQuestionPoolSize — ссылок не осталось (grep); storedGold —
  импорт добавлен, модуль зелёный; мёртвые константы Transport — компиляция platform:firebase
  зелёная. Чисто.
- intent-alignment: расхождений нет — дифф исполняет dev-доли перечисленных ретро, owner-пункты
  не тронуты, носители обновлены; все четыре отклонения (замена F17, закрытие m3-1 без кода,
  отказ от провода m3-2, заезд 4.21) записаны в самой спеке.

## Auto Run Result — follow-up pass (2026-10-28, смена приёмки)

Status: done (спека принята от остановленного соседа и доведена ревью-проходом)

- Проверка наследства: все шесть коммитов пакета в истории (d111fb47, 6c38a53c, e48ac699,
  05ae0873, 117524c6, f90396db), спека закоммичена и идентична диску, носители в f90396db.
- Гейты перезапущены на HEAD (дерево уже несёт приземлённые 4.21/4.22/4.25/9.x и чужие
  mid-flight правки):
  - `functions npm test` (PATH=/workspace/infrastructure/zcode/3.14.3/node24/…/bin) — **137/140**;
    три красных — arena-lifecycle-views-handler, entity-version, lesson-proposals: каждый
    зелёный standalone (exit 0, эта смена), прод-половины чужие и грязные (index.js MM,
    review-queue-worker MM, quest-proposals-view D+?? — 4.23/4.25 соседи) → environment-red
    с доказательством; сюиты пакета (mutation-terms, lesson-question-window,
    lesson-ready-ack-handler, deferred-actions, economy-handler, offer-auction-settlement,
    purchase-settlement) зелёные в смешанном прогоне; mvp-isolation зелёный standalone.
  - `sgw ./gradlew :shared:feature:economy:data:jvmTest :shared:feature:economy:domain:jvmTest
    :platform:firebase:testDebugUnitTest` (JDK 27) — BUILD SUCCESSFUL (F16-литерал, F7-пин,
    F22-компиляция).
  - `:apps:android-next:testDebugUnitTest` (фильтр F15/F17) — чужой resource-брейк, см. defer;
    stand-in-попытка `compileDebugUnitTestKotlin` заперта тем же mergeDebugResources (AGP
    ведёт R-генерацию через слияние ресурсов) — компиляция патча не исполнена; доказательства
    патча: резолв обоих путей ls-ом из каталога модуля, длины строк ≤120 (awk), правка
    строковая и повторяет дословный паттерн read() третьего теста того же файла.
  - ktlint: красные source sets атрибутированы — economy:data commonMain = OwnHistoryWireParser
    (45f7e9c8, до базлайна пакета), economy:domain commonMain = SyncBoxStreakUseCase
    (d9c6d28c, до базлайна), android-next main = SyncModule.kt:338 — строка чужой dirty-правки
    эпика-8 (не F15-ханк) — и build.gradle.kts:65-66 (чужая закоммиченная инфраструктура),
    platform:firebase = известная корзина (включая Transport:47 — выше F22-правки);
    собственных ktlint-нарушений файлы пакета не имеют; test-source-set задачи в этой настройке
    NO-SOURCE (тесты пакета ktlint-ом не обходятся).
- Review-патчи этой смены: ConsentChainWiringTest (пути `../../` + разбиение 148-символьной
  строки), yaml last_updated (формат восстановлен).
- Residual risk (именованный, причина followup_review_recommended: true): F15/F17-пины так и не
  исполнялись gradle-ом — «чистый» android-next гейт (первая смена без чужого resource-брейка)
  обязан их прогнать; ревью-патч проверен stand-in-ами (резолв путей, длины строк), не прогоном.
