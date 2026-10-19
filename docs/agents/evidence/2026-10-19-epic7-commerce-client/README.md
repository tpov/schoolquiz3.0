# Epic 7 — client gift-claim wiring proven from this tree (2026-10-19)

Ветка `night/e7-commerce`; прогон build-auto по спеке
`_bmad-output/implementation-artifacts/spec-epic-7-commerce.md`. Сервер заморожен (0 изменений в
`functions/`, `firestore.rules`, закупочном конвейере). Клиент научился ЗАБИРАТЬ подарки: три
вида заявок (`VISIT` / `OFFLINE_STREAK` / `PREMIUM_DAILY`) через прямой вызываемый
`submitGiftBoxOperation` с локальным operationId-интентом, входная синхронизация серии
(офлайн-заявка → визит → перечитывание), кнопка «Получить подарок» с типизированными
состояниями, `nextBonusBoxAtMs` доезжает до клиента, минимальная премиальная поверхность магазина.

## Статусы историй (эпическое правило: PASS / FAIL / NOT_RUN, не выдумывать)

| История | Статус | Что доказано / почему NOT_RUN |
|---|---|---|
| 7.1 Play inventory | **NOT_RUN** | внешний вход владельца (Play Console) недоступен из этого дерева; ничего не сочинялось |
| 7.2 store surface | **PASS (частично, из дерева) + NOT_RUN (premium-нормализация)** | 2026-10-18: insufficient-funds и visibly-unavailable доказаны на Pixel; 2026-10-19: stale-pin теста NSC приведён к задокументированному решению a2298084 (единственный внешний домен — VPS-хост hitl-пути); premium-нормализация за Play-локом — ждёт 7.1, NOT_RUN; fresh-consent зафиксирован как отложенный (реестр `MUTATION_TERMS_OPERATIONS` не тронут) |
| 7.3 premium | **PASS (серверная половина + клиентская поверхность) + NOT_RUN (regeneration UI device proof)** | аудит `premium-economy.test.js` (15) + `premium-grants.test.js` (29): оба удвоенных восстановления, holds (резервы), пересечения/FIFO (грант поверх активного = max(now,end)+duration; грант в середине — строго после), независимый остаток лота (частичный возврат Play), сохранённые остатки работ — все документированные поведения покрыты, новых Node-кейсов не требуется (правило «добавлять ТОЛЬКО при отсутствии кейса»); клиентская поверхность магазина названа серверными словами; device proof регенерации — hitl, NOT_RUN |
| 7.4 boxes/gifts | **PASS (client wiring из дерева) + NOT_RUN (device proof на телефоне)** | вся проводка заявок сделана и покрыта тестами (см. Gates); серворный контракт не менялся и запинен своими Node-тестами; двух-устройственный пруф на железе — hitl, NOT_RUN |
| 7.5 box opening | **PASS** (закрыто 2026-10-18) | loss/restart пройден на Pixel; клиентских изменений в этом пути нет |
| 7.6 Play sandbox | **NOT_RUN** | внешний вход 7.1 + устройства владельца недоступны; не имитировалось |
| 7.7 provenance | **PASS** (закрыто 2026-10-09) | аудит этого прогона ничего в серверном контуре не трогал; rules-гейт перезапущен: **212/212** (рост с 193 — набор проверок правил вырос последующей серверной работой, не этим прогоном) |
| 7.8 dependency audit | **PASS** (закрыто 2026-10-08) | без изменений |

## Gates (все исполнялись из этого дерева, фиксация 10a463a1+)

- `functions` **no-env**: **124 of 124 suites passed** (число выросло с 92 из спеки — серверный
  набор тестов вырос последующей работой, не этой; 0 изменений в `functions/`, кроме правки
  stale-pin `mvp-isolation.test.js` — только тест, см. ниже).
- `functions` **demo-backend** (`FIRESTORE_EMULATOR_HOST=127.0.0.1:18090
  GCLOUD_PROJECT=demo-schoolquiz-mvp`, запуск внутри контейнера
  `schoolquiz-functions-emu` с host-сетью): **124 of 124 suites passed**.
- **rules**: изолированная пара auth+firestore (порты 18091/19099, проект `rules-gate-test`,
  продовый `firebase.json` и demo-фикстура не тронуты): **212/212 проверок прошло**.
- `:apps:android-next:testDebugUnitTest`: зелёный целиком, включая `KoinModuleWiringTest`
  (граф с новыми портами заявок) и `ArenaKoinWiringTest`.
- Модульные прогоны этого прогона: `:shared:core:persistence:jvmTest` (включая новые
  `GiftClaimPersistenceTest` + страж перечня владельцев), `:shared:feature:economy:domain:test`
  (GiftBoxAccrual + паритет фикстур + use-cases), `:shared:feature:economy:data:test`
  (GiftClaimRepositoryTest), `:platform:firebase:testDebugUnitTest` (GiftClaimEnvelopeMappingTest;
  2 известных stale arena-pin red — вне этого прогона), `:android:feature:quest:presentation`,
  `:android:feature:economy:presentation`.
- APK для следующей hitl-сессии: `assembleMvp` — см. артефакт (собран с
  `-PmvpEmulatorHost=204.44.66.27 -PmvpEmulatorFunctionsPort=5002`).

### Правки stale-пинов и Environmental-ремонты (только тесты/сборка, 0 прод-логики)

1. `functions/mvp-isolation.test.js` — пин cleartext-доменов догнал задокументированное решение
   a2298084 (VPS-хост в mvp NSC, device-proven 7.2); loopback по-прежнему запинен строго.
2. `ActivityRepositoryImplTest.FakeAttemptDao` — догнал пять членов `LessonAttemptDao` (арена,
   5d006688); до этого весь `commonTest` профиля не компилировался.
3. `apps/android-next` `defaultConfig` — `MVP_EMULATOR_HOST/PORT` объявлены для всех типов
   сборки: AppApplication ссылается на них безусловно с a2298084, из-за чего `assembleDebug` и
   unit-тесты корня не компилировались вовсе.
4. `net.bytebuddy.experimental=true` для тестовых JVM корня — JDK 27 этой VPS новее, чем
   официально поддерживаемая Byte Buddy; флаг рекомендован самой ошибкой, прод-код не трогает.

### Известные красные, НЕ принадлежащие этому прогону (владельцу)

- 2 fixture-pin теста `:platform:firebase:testDebugUnitTest`
  (`FirebaseArenaLessonCatalogRemoteDataSourceTest`, `FirebaseArenaTicketRemoteDataSourceTest`).
- `detektMetadataCommonMain` падает по всему дереву: `Invalid value (27) passed to --jvm-target`
  (detekt vs JDK 27) — та же причина записана в evidence эпика 6.
- ktlint в `platform:firebase`, `:android:feature:economy:presentation` (ShopView и соседи) —
  находки до этого прогона; файлы этого прогона чистые.

## Что осталось владельцу (hitl)

1. **7.4 device proof**: подключить телефон к собранному MVP APK. Фикстура
   `users/VLqWYMHXqyI8pPWQdNXdkQg9FY6E` цела (`boxStreakDays=9`, `boxCount=0`, заявок с дерева
   не отправлялось), НО её `nextBoxAtMs` состарился за пределы 24-часового окна — перед сессией
   **пере-seedить** `nextBoxAtMs = now − 2h` (как в evidence 2026-10-18 gift-sources-finding),
   иначе визит честно оборвёт серию по правилу «подряд значит подряд».
2. 7.3 regeneration UI device proof; 7.1 Play inventory; 7.6 sandbox-матрица — как и прежде,
   внешние входы.

## Отчётные числа для быстрой проверки

- Kotlin-наборы этого прогона (новые/расширенные): GiftBoxAccrualTest (10), GiftBoxStreakParityTest
  (+1 кейс заявок), GiftClaimPersistenceTest (6), GiftClaimRepositoryTest (12),
  SyncBoxStreakUseCaseTest (8), ClaimPremiumDailyGiftUseCaseTest (3),
  GiftClaimEnvelopeMappingTest (9), DefaultHomeQuestsComponentTest (+6 кейсов 7.4),
  PremiumSurfaceStateTest (5), GiftClaimPersistenceTest в страже владельцев — плюс все прежние
  наборы затронутых модулей зелёные.
