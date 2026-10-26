---
title: 'Epic 7 retro fix package — wire-contract pin and the claim loop''s exits (e7 commerce follow-up)'
type: 'feature'
created: '2026-10-25'
status: 'ready'
baseline_revision: '1e79312219f7ae1406b302ef21614d97484bc625' # HEAD at spec time (post retro 231db859; 5.13/monetisation/epic-4 commits landed after)
route: 'full'
route_source: 'owner-direct'
review: 'standard'
review_source: 'owner-direct'
followup_review_recommended: false
context:
  - '_bmad-output/implementation-artifacts/epic-7-retro-2026-10-25.md'
  - '_bmad-output/implementation-artifacts/spec-epic-7-commerce.md'
  - '_bmad-output/mvp-candidate/epic-commerce/tickets.toml'
  - 'docs/agents/evidence/2026-10-20-premium-wire-repair/README.md'
warnings: ['sibling-dirty-tree-arena']
---

<intent-contract>

## Intent

**Problem:** ретроспектива epic-commerce (2026-10-25, вердикт accepted-with-open-items, 12 action items) оставила древовую пачку: контракт конверта `{replayed, result}` не запинен целиком ни на одной стороне — тот самый шов, чей дрейф уже стоил одной device-смены; у цикла заявок несколько путей «намерение остаётся лежать» без запиненного выхода (застрявшая кнопка, потерянные офлайн-дни, вечный ретрай); пин `mvp-isolation` отстал от закоммиченного NSC и functions-гейт красный на HEAD (128/129); спека эпика, tickets-статусы и сентябрьский трекер разошлись с зафиксированной реальностью. Владелец 2026-10-25 приказал записать пачку в спеку на выполнение.

**Approach:** семь находок ретро (F1–F7) закрываются фиксами и пинами строго в пределах клиентского кода, тестов сервера и документов; прод-код `functions/` не меняется вовсе. Каждый путь «намерение лежит» получает запиненный выход, при этом денежная инварианта неприкосновенна: отказ разбора ОТВЕТА (решение сервера могло быть принято) намерение сохраняет — карантинится только отказ разбора ТЕЛА ДО вызова, когда решения не существует в принципе.

## Boundaries & Constraints

**Always:**
- Денежная инварианта читается так: intent держится, пока существует вероятность, что сервер принял решение. ENVELOPE_INVALID на ответе — держит; REQUEST-shape отказ до сетевого вызова — решений не было, намерение отпускается. Перепутать направления — регрессия денег, не стиля.
- `functions/` меняется ТОЛЬКО тестами (пины в `economy-handler.test.js`, `mvp-isolation.test.js`); ни строчки прод-кода сервера, ни `firestore.rules`, ни `MUTATION_HANDLERS`.
- Тест-пины выводить из реального прод-кода: набор полей ответа — из конструкции ответа в `planBoxOperation` (`functions/box-service.js`), а не из воображения; SHA-пин — по арена-прецеденту (`sourceSha256` фиксжурнал).
- Гейты через `sgw` с `JAVA_HOME=/opt/jdks/jdk-27`; затронутые модульные сюиты зелёные целиком; functions no-env 129/129 (_suite-каунт может вырасти только новыми файлами, не падением).
- В дереве на момент спеки лежит чужая in-flight работа (арена: `functions/_firestore-stub.js`, `generate-arena-client-fixtures.js`, lesson-runner тесты, ArenaRunnerContent.kt). Файлы этой пачки с ней не пересекаются; чужие файлы не трогать, не коммитить, не чинить.
- D-задачи (документы) применять ровно в предписанной формулировке: слова владельца от 2026-10-25 уже записаны в ретро, в спеке они не перефразируются.

**Never:**
- Никаких изменений на биллинг-пути: UX silent-Buy ждёт своей истории (ретро item 8, внешняя), не этой пачкой.
- Никаких изменений `premiumRegenDivisor` / экономики-константов: x2 — версионированное решение владельца (ретро item 9), вне охвата.
- Никаких Play-действий, собранных сюда «за компанию»: AAB 40001 заливает владелец (ретро item 8).
- Никакой правки семантики `sprint-status.yaml` руками: D3 — только `git mv` сентябрьского файла в архивное имя; живой трекер создаёт `bmad-sprint-planning`, не этот прогон.

## I/O & Edge-Case Matrix

| # | Сценарий | Вход / состояние | Ожидаемое поведение | Отказ/граница |
|---|----------|------------------|---------------------|---------------|
| F1a | Сервер роняет любое поле ответа | Из `planBoxOperation` удалён/переименован любой из ~12 ключей `result` (boxCount, boxStreakDays, earnedBoxes, kind, nextBonusBoxAtMs, nextBoxAtMs, openedBoxes, operationId, ownerUid, premiumUntilMs, schemaVersion, status) | `economy-handler.test.js` красный по пину полного набора ключей | `typeof daily.replayed === "boolean"` тоже запинен |
| F1b | Клиент правит читатель без перечитывания сервера | Правка `giftClaimOutcomeFrom`/`box-service.js` | SHA-пин `functions/box-service.js` в platform/firebase фиксжурнале краснеет, заставляя re-pin | Прецедент: `FirebaseArenaLessonCatalogRemoteDataSourceTest` |
| F2 | Офлайн-неудача премь-кнопки, потом связь | resume упал (NoNetwork) → pending=true, эмиссий больше нет → сеть вернулась / re-entry | resume переигрывается (от `observeOnline().filter{it}` и/или doOnResume), намерение решается, кнопка выходит из Claiming | Двойной запуск не минтует второй operationId (одно активное намерение) |
| F3 | Автогрант дня 10 сдвинул только boxCount | Свежее слово профиля: boxCount+1, streakDays/nextBoxAtMs те же; местный счёт с офлайн-днями | База считается совпавшей (сравнение по streakDays+nextBoxAtMs), офлайн-счёт НЕ пересеивается, заявка уезжает | boxCount в базу сравнения не входит; wire-тело OFFLINE_STREAK не меняется |
| F4 | Fence отказал: operationId скреплён с другим телом/владельцем | OFFLINE_STREAK отвечает CONFLICTING_REPLAY | Репозиторий отпускает намерение (терминально), use-case продолжает проход, VISIT уходит | Решение для ЭТОГО тела не существует никогда; тест `conflictingReplayKeepsTheIntent…` инвертируется и переименовывается |
| F5a | Кнопка отвечает DAILY_LIMIT / PREMIUM_REQUIRED | Терминальный статус ≠ CLAIMED/STALE_BASE | Профиль перечитан, кнопка перерисовывается из свежего слова | Каждый тап больше не минтует новый operationId |
| F5b | Испорченное тело в Room | `giftClaimRequestFrom` отказывает ДО вызова | Отказ по имени, намерение отпускается (карантин), входной проход и кнопка живут | ENVELOPE_INVALID на ОТВЕТЕ — намерение по-прежнему живёт (запинить обе стороны направления) |
| F6 | Старая completion-запись OPEN_GIFT_BOX | mutation_keys хранит пре-конвертный plan-nested результат | `openingFromReceipt` принимает обе формы (plan-nested `response` и плоскую), доставка решается | Новые записи — плоские; dual-shape read, не свип |
| F7 | Гейт на HEAD | `npm test` (no-env) | 129/129: пин `mvp-isolation` совпадает с закоммиченным NSC ровно (loopback строго, внешние ровно `204.44.66.27` + `192.168.31.218`) | Излишний домен по-прежнему красный |

</intent-contract>

## Code Map

Базаль `1e793122` на `night/e7-commerce`; работа поверх, без отдельной ветки (ночная линия).

- `functions/economy-handler.test.js:73-98` — тест «profile refresh preserves offline anchor…»: добавить пин полного набора ключей `daily.result` (deepEqual отсортированных ключей, список вывести из `planBoxOperation`) и `assert.equal(typeof daily.replayed, "boolean")`. *(F1a)*
- `functions/box-service.js:205-208` — источник истины по форме ответа; ТОЛЬКО ЧТЕНИЕ для вывода пина, не менять. *(F1a)*
- `platform/firebase/src/test/kotlin/.../arena/FirebaseArenaLessonCatalogRemoteDataSourceTest.kt:646-654` — образец SHA-пина источника; новый фиксжурнал-тест (например `BoxServiceSourcePinTest`) рядом с `GiftClaimEnvelopeMappingTest` пинит sha256 `functions/box-service.js`. *(F1b)*
- `android/feature/quest/presentation/.../DefaultHomeQuestsComponent.kt:241-253` (коллектор auto-resume) и `:418-432` (doOnResume-синк) — re-arm: возобновлять `claimPremiumDailyGift()` при появлении сети (`NetworkMonitor.observeOnline`) и/или на resume; рядом тест в `DefaultHomeQuestsComponentTest` (офлайн-неудача → reconnect → settle). *(F2)*
- `shared/feature/economy/domain/.../use_case/SyncBoxStreakUseCase.kt:79-80` — сравнение `it.base == base` заменить на пару (streakDays, nextBoxAtMs); кейс в `SyncBoxStreakUseCaseTest` (слово сдвинулось только boxCount'ом — счёт выжил). *(F3)*
- `shared/feature/economy/domain/.../use_case/SyncBoxStreakUseCase.kt:100-107` — при CONFLICTING_REPLAY от офлайн-заявки проход продолжается визитом. *(F4)*
- `shared/feature/economy/data/.../GiftClaimRepositoryImpl.kt:89-112` — (F4) отпускание намерения при CONFLICTING_REPLAY (fence-отказ терминален для этого тела — репозиторий резолвит строку и перебрасывает типизированный отказ); (F5a) `ensureCurrentProfile()` на всех терминальных статусах; (F5b) REQUEST-shape отказ от транспорта отличён от ответного: typed отказ до вызова отпускает намерение, ответный ENVELOPE_INVALID — держит. Тесты: `GiftClaimRepositoryTest` — `conflictingReplayKeepsTheIntentAndTouchesNothingElse` инвертируется/переименовывается, +кейсы «corrupted body releases», «response ENVELOPE_INVALID keeps». *(F4, F5a, F5b)*
- `platform/firebase/.../FirebaseGiftClaimRemoteDataSource.kt:74-79` (место вызова `giftClaimRequestFrom`) и `:1438-1462` (сам парсер) — отказ разбора тела поднять как типизированный pre-call отказ (не bare `error()`); кейсы в `GiftClaimEnvelopeMappingTest`/repository-тесте на оба направления. *(F5b)*
- `functions/index.js:1895-1899` — чтение для понимания формы; прод-код не менять. *(F6)*
- `shared/feature/economy/data/remote/GiftBoxRemoteDataSource.kt:7` (`openingFromReceipt`) + `GiftBoxRepositoryImpl.kt:159-160` — dual-shape read completion-записи OPEN_GIFT_BOX (plan-nested `response` | плоская), тесты на обе формы. *(F6)*
- `functions/mvp-isolation.test.js:39-51` — ожидаемый внешний набор ровно `["204.44.66.27", "192.168.31.218"]` с комментарием-провенансом (a2299284/7d8b91bd/ed16fc81: VPS hitl-хост + LAN-хост overlay 5.13); loopback-пин не смягчать. Снятие LAN-домена — отдельное решение владельца, не этой пачкой. *(F7)*
- Документы: `spec-epic-7-commerce.md` (D1: снять «no server changes»/«Server untouched», deferred premiumUntilMs-wipe → resolved с рецептом `wire/premium-grant-fixture.js`), `epic-7-context.md:56` (D1: ADR-заметка direct-callables-vs-outbox с обоснованием из Design Notes спеки), `_bmad-output/mvp-candidate/epic-commerce/tickets.toml:34,47,60,100` (D2: дописать в комментарии статусов слова владельца от 25.10 — «принимаю с открытыми хвостами», инвентарь 7.1 получен, 7.6 явно NOT_RUN до возобновления), `_bmad-output/implementation-artifacts/sprint-status.yaml` (D3: `git mv` → `sprint-status-legacy-sync-2026-09.yaml`, без семантических правок).

## Tasks & Acceptance

**Execution:**
- [ ] `functions/economy-handler.test.js` — F1a: полный пин ключей `result` + `replayed:boolean`; набор ключей выведен из `box-service.js`, не придуман.
- [ ] `platform/firebase/src/test/.../economy/BoxServiceSourcePinTest.kt` — F1b: SHA-пин `functions/box-service.js` по арена-паттерну (фиксжурнал с датой и провенансом).
- [ ] `DefaultHomeQuestsComponent.kt` + `DefaultHomeQuestsComponentTest` — F2: re-arm лежащего премь-намерения по сети/resume; тест офлайн-неудача → reconnect → settle; двойной запуск не создаёт второй operationId.
- [ ] `SyncBoxStreakUseCase.kt` + тест — F3: база сравнения без boxCount; кейс «автогрант дня 10 не стирает офлайн-счёт».
- [ ] `GiftClaimRepositoryImpl.kt` + `FirebaseGiftClaimRemoteDataSource.kt` + тесты — F4/F5: CONFLICTING_REPLAY отпускает намерение (старый тест инвертирован), терминальные статусы перечитывают профиль, REQUEST-shape отказ карантинит при живой денежной инварианте ответного ENVELOPE_INVALID (оба направления запинены).
- [ ] `GiftBoxRemoteDataSource.openingFromReceipt` + тесты — F6: dual-shape read старых/новых completion-записей OPEN_GIFT_BOX.
- [ ] `functions/mvp-isolation.test.js` — F7: пин догоняет закоммиченный NSC (внешние ровно два документированных хоста), гейт 129/129 зелёный на HEAD.
- [ ] D1: правки `spec-epic-7-commerce.md` + ADR-заметка в `epic-7-context.md` ровно в формулировках ретро (action item 11).
- [ ] D2: комментарии статусов 7.1/7.2/7.3/7.6 в `tickets.toml` дополнены словами владельца от 2026-10-25 (action item 10); сами `status =` поля не менять (это решает владелец отдельно при закрытии хвостов).
- [ ] D3: `git mv sprint-status.yaml sprint-status-legacy-sync-2026-09.yaml` + строка-объяснение в новом месте; живой трекер не создаётся (action item 12).
- [ ] Evidence: `docs/agents/evidence/2026-10-<day>-e7-retro-fixes/README.md` — что доказано, гейты, что сознательно NOT_RUN (silent-Buy, x2, Play).

**Acceptance Criteria:**
- Удаление/переименование любого поля ответа `planBoxOperation` краснит `economy-handler.test.js`; правка `box-service.js` без re-pin краснит клиентский SHA-пин — дрейф шва больше не бывает молчаливым ни в одну сторону.
- Офлайн-неудача кнопки перестаёт быть пожизненной: reconnect/resume доводит намерение до исхода, Claiming конечен.
- Автогрант дня 10 при чтении профиля не стирает честно насчитанные офлайн-дни (счёт выживает, заявка уезжает).
- CONFLICTING_REPLAY и испорченное тело больше не блокируют входной проход навсегда; при этом ответный ENVELOPE_INVALID по-прежнему держит намерение — денежная инварианта запинена с обеих сторон направления.
- DAILY_LIMIT/PREMIUM_REQUIRED приводят кнопку к свежему слову сервера без новых operationId.
- Старая (plan-nested) и новая (плоская) completion-записи OPEN_GIFT_BOX обе доводят доставку.
- `cd functions && npm test` = 129/129 no-env на HEAD; demo-backend 129/129; затронутые JVM-сюиты зелёные; файлы чужой in-flight арены-работы не тронуты.
- Спека/трекеры приведены к записанной реальности без единой семантической правки yaml.

## Implementation Notes

- (appended during implementation)
- Порядок: F7 первым (гейт зеленеет на HEAD сразу), затем F1 (пины поверх живого контракта), затем F2–F6 (поведение), D1–D3 в конце одним docs-коммитом.
- F5b — самый дизайновый пункт: направление «до вызова / после вызова» обязано остаться явным в типах (например, distinct typed exception для pre-call отказа), чтобы будущий читатель не мог их перепутать.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :platform:firebase:testDebugUnitTest :shared:feature:economy:domain:test :shared:feature:economy:data:test :android:feature:quest:presentation:test :shared:core:persistence:jvmTest --no-configuration-cache` — зелено.
- `cd functions && npm test` — 129/129 no-env (и demo-backend в контейнере `schoolquiz-functions-emu`).
- `git status` — только файлы этой пачки; арена-файлы sibling-сессии не тронуты.

**Manual checks (no CLI):**
- F5b: в диффе видно, что ответный ENVELOPE_INVALID держит намерение (тест), pre-call отказ — отпускает (тест), и это разные ветки кода.
- D1–D3: диффы документов соответствуют формулировкам ретро дословно, без перефраза слов владельца.
