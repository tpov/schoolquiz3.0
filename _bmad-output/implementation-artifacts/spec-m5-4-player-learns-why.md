---
title: 'M5.4 — Игрок узнаёт, за что получил'
type: 'feature'
created: '2026-10-12'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
baseline_commit: '58b3baf8198de3c3cfffd5fc5fca4c680b5ca1a1'
story_key: '5-4-игрок-узнаёт-за-что-получил'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m5-2-leaderboard-pays-boxes.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m5-3-referral-pays-for-six-and-percent.md'
warnings: []
deferred: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** сервер начисляет коробки, золото и премиум, и каждая причина уже лежит в квитанциях
(`users/{uid}/receipts`, ключи + параметры, правила читают их владельцу) — но клиент их не читает
и не показывает: награда выглядит случайной.

**Approach:** клиентская половина поверх готовой серверной: источник данных платформы слушает
подколлекцию квитанций (свежие первыми, ограниченный список), домен отдаёт их потоком, витрина
магазина показывает раздел «за что начислено» — каждая строка собирает текст из ключа и
параметров на языке устройства (строки в трёх локалях; незнакомый ключ — общая строка с суммой).
Серверная половина (ключи, параметры, delivery) уже построена попутно: квитанции пишут все
начисляющие пути (покупки, коробки, турниры, сезоны, рефералка) в тех же транзакциях, что и
балансы, и каждая — отдельный документ, так что два начисления подряд дают две квитанции, а
повторное чтение (потерянное гашение) показывает их снова и не двигает баланс.

## Boundaries & Constraints

**Always:** текст приходит ключом с параметрами — серверные строки в квитанциях запрещены
(NFR8: украинский интерфейс обязателен, язык знает устройство); незнакомый ключ не ломает
раздел, а показывает общую строку с суммой и валютой. Чтение — владельцем своих квитанций
(правила уже так закрывают); гость видит пустой раздел. Испорченная квитанция сервера — громкий
отказ потока (писатель сервер, кривой документ — серверный баг, прецедент inbox). Раздел живёт
в витрине магазина (прецедент поверхности), не строит второй экран.

**Ask First:** ограничение списка (последние 30); форма строки (дата, текст, сумма с валютой).

**Never:** никаких серверных изменений (серверная половина готова и закреплена тестами 5.2/5.3
и прежних историй); никаких готовых строк с сервера; никаких ручных «отметок прочитанности»,
меняющих баланс.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Квитанции есть | владелец открыл магазин | раздел показывает свежие: дата, текст по ключу, сумма с валютой | N/A |
| Два начисления подряд | два документа | две строки, ни одна не затёрта | N/A |
| Потерянное гашение | экран перечитал те же документы | те же строки снова, баланс не менялся | N/A |
| Незнакомый ключ | messageKey вне словаря | общая строка с суммой и валютой | fallback |
| Гость | нет auth uid | пустой раздел | N/A |
| Испорченный документ | поле не той формы | громкий отказ потока | named error |
| Язык устройства | uk/ru/en | текст на языке устройства из локалей | N/A |

</frozen-after-approval>

## Code Map

- `shared/feature/economy/domain/src/commonMain/kotlin/.../domain/model/EconomyReceipt.kt` --
  модель квитанции (id, kind, messageKey, params, amount, currency, createdAtMs).
- `shared/feature/economy/data/src/commonMain/kotlin/.../data/remote/EconomyReceiptsRemoteDataSource.kt` --
  интерфейс чтения (наблюдение списка).
- `platform/firebase/src/main/kotlin/.../economy/FirebaseEconomyReceiptsDataSource.kt` --
  Firestore-слушатель `users/{uid}/receipts` (orderBy createdAtMs desc, limit 30), отображение
  снимка в модель; гость — пусто.
- `shared/feature/economy/domain/src/commonMain/kotlin/.../domain/repository/EconomyRepository.kt`
  + `data/EconomyRepositoryImpl.kt` -- `observeReceipts()`: гость — пустой поток.
- `shared/feature/economy/domain/src/commonMain/kotlin/.../domain/use_case/ObserveEconomyReceiptsUseCase.kt` --
  сценарий.
- `shared/feature/economy/data/src/commonMain/kotlin/.../data/di/EconomyDataModule.kt`,
  `platform/firebase/src/main/kotlin/.../di/FirebaseModule.kt`,
  `shared/feature/economy/domain/src/commonMain/kotlin/.../domain/di/EconomyDomainModule.kt`,
  `android/feature/economy/presentation/src/main/kotlin/.../di/EconomyPresentationModule.kt` --
  проводка источника, репозитория и сценария.
- `android/feature/economy/presentation/.../component/ShopViewState.kt`,
  `component/DefaultShopComponent.kt` -- состояние и сбор; `view/ShopView.kt` -- раздел
  «за что начислено» на вкладке магазина; `view/EconomyReceiptText.kt` -- словарь ключ → строка
  ресурса с порядком параметров, общий fallback.
- `android/feature/economy/presentation/src/main/res/values{,-ru,-uk}/strings.xml` -- строки
  раздела и квитанционных ключей (английский fallback / русский / украинский).
- Tests: `platform/firebase/src/test/.../economy/EconomyReceiptMappingTest.kt` (отображение
  документа, испорченное — громко), `android/feature/economy/presentation/src/test/.../EconomyReceiptTextTest.kt`
  (словарь, порядок параметров, fallback), `shared/feature/economy/data/src/commonTest/.../EconomyRepositoryReceiptsTest.kt`
  (гость/делегирование).
- Read-only: `functions/` (серверная половина готова: box-service.js, purchase-verification.js,
  purchase-notifications.js, tournament-service.js, economy-season.js, firestore.rules).

## Tasks & Acceptance

**Execution:**
- [ ] shared domain/data + platform firebase -- модель, интерфейс, Firestore-источник,
      репозиторий, сценарий, DI -- по матрице.
- [ ] android/feature/economy presentation -- состояние, сбор, раздел витрины, словарь текстов,
      строки трёх локалей.
- [ ] Тесты трёх слоёв -- закрепить.
- [ ] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- 5-4 →
      in-progress → review → done с комментарием.

**Acceptance Criteria:**
- Given сервер начислил что-либо, когда клиент синхронизируется, то он показывает квитанцию с
  суммой и человеческим объяснением.
- Given два начисления подряд, когда клиент читает квитанции, то он показывает две квитанции, а
  не одну затёртую.
- Given потерянное гашение, когда квитанции читаются повторно, то они показываются снова и
  баланс не двигается.
- Given текст квитанции, когда он отображается, то он приходит ключом с параметрами и
  собирается на языке устройства.

## Implementation Notes

- Route `full`; реализация и линзы — в этой же сессии (платформа без субагентов, как в
  3.1–5.3). Серверная половина истории готова попутно (квитанции всех начисляющих путей несут
  ключи и параметры и читаются владельцу по правилам) — новые серверные изменения не требуются,
  что закрепляют существующие сьюты box/purchase/season/referral.

## Spec Change Log

## Review Triage Log

### 2026-10-12 — Review pass (thorough: blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

Линзы исполнены в сессии (платформа без субагентов — как в 3.1–5.3); дифф перечитан на каждую линзу.

- verdicts: 5 findings — high 0, medium 1, low 0, false 4, maybe-false 0
- findings:
  - `[medium]` `[patch]` Поток квитанций не был заперт на текущий аккаунт: подписка живёт один
    раз, и после смены аккаунта магазин показывал бы ушедшему игроку квитанции его
    предшественника — исправлено: репозиторий плоско переключается по потоку uid (форма
    observeBalance), гость получает пустое, не доходя до источника; тест гостя и лимита добавлен
    (EconomyRepositoryReceiptsTest).
  - `[false]` Firestore Double в параметрах не распознаётся — опровергнуто: сервер пишет
    целые числа, Firestore хранит их Long'ами; дробь в квитанции — серверная аномалия, и громкий
    отказ ровно то, что выбрано для кривого серверного документа (форма inbox).
  - `[false]` Дубликат id уронит LazyColumn — опровергнуто: id — имя документа Firestore,
    уникально по построению; дубликат означает два документа с одним именем.
  - `[false]` Отсутствующий DI-тест проводки (KoinModuleWiringTest красный и до истории) —
    опровергнуто как дефект: это документированный красный владельца (it09e); проводка проходит
    сборкой assembleMvp и композицией тестов компонента.
  - `[false]` limit ≤ 0 уронит слушатель — опровергнуто: лимит приходит из константы интерфейса
    (30), публичных callers с другим числом нет; Firestore-отказ на неподдерживаемый лимит —
    громкая ошибка программиста, а не пользовательская ветка.

## Auto Run Result

Status: done
Blocking condition: none

**Summary:** клиентская половина поверх готовой серверной. Источник платформы
(FirebaseEconomyReceiptsDataSource) слушает `users/{uid}/receipts` владельца — свежие первыми,
последние тридцать, живое пополнение, пока экран открыт; гость получает пустое. Репозиторий
плоско переключается по потоку uid (смена аккаунта пересобирает подписку). Модель
EconomyReceipt (id, kind, messageKey, params, amount, currency, createdAtMs) едет в витрину
магазина: раздел «За что начислено» на вкладке магазина — дата, текст по ключу с параметрами на
языке устройства (три локали: en fallback / ru / uk), сумма с валютой и знаком (премиум —
днями). Словарь ключ → ресурс замкнут; незнакомый ключ деградирует до общей строки с суммой,
турнирные ключи разбираются префиксом. Две квитанции — две строки (id = имя документа);
потерянное гашение показывается повторно и баланс не двигает (квитанции — иммутабельные
серверные документы, дисплей их не гасит). Серверных изменений нет: ключи, параметры и доставка
уже построены начисляющими путями и правилами и закреплены их сьютами.

**Files changed:**
- `shared/feature/economy/domain` — модель EconomyReceipt, observeReceipts в репозитории,
  ObserveEconomyReceiptsUseCase, DI сценария.
- `shared/feature/economy/data` — интерфейс EconomyReceiptsRemoteDataSource, делегирование с
  гейтом по uid, DI.
- `platform/firebase` — FirebaseEconomyReceiptsDataSource (слушатель + строгое отображение).
- `android/feature/economy/presentation` — receipts в состоянии, сбор в компоненте, раздел
  витрины (NoirShopStore), словарь EconomyReceiptText, строки трёх локалей, DI презентации.
- Тесты: EconomyReceiptMappingTest (платформа), EconomyReceiptTextTest (презентация),
  EconomyRepositoryReceiptsTest (shared).

**Review findings breakdown:** 1 патч (гейт по uid с гостем и тестом), 4 опровергнуты с
опровержениями. followup_review_recommended: false — один средний патч закрыт тестом,
неназванных рисков нет.

**Verification performed:** `node functions/run-tests.js` — 109/109 (сервер не менялся; известный
флак arena-lifecycle-views сошёл на повторе); `sgw ./gradlew
:shared:feature:economy:data:allTests :android:feature:economy:presentation:test
:platform:firebase:test` — зелёные (платформа: минус два документированных арена-пина
владельца); `sgw ./gradlew :apps:android-next:assembleMvp` — зелёная; ktlint моих модулей —
зелёный (economy presentation/data). Средовые красные не из историй, подтверждены на HEAD
сташем: `test`/`assembleDebug` (google-services.json локальный содержит только mvp-клиент —
 debug-вариант непригоден средово), detekt Android-модулей (jvm-target 27), ktlint
platform:firebase / app-shell / apps:android-next / designsystem / sync / lesson-runner /
quest-authoring (все красные до диффа), it09e KoinModuleWiringTest, compileTestKotlin
shared:feature:internet:profile:data (красный до диффа).

**Residual risks:** живой показ — после деплоя владельца (квитанции копятся с момента выкатки
5.2/5.3; до того раздел пуст — честно). Отметки «прочитано» и пуш-уведомления о начислениях —
за пределами истории (AC их не требует).

## Design Notes

- Delivery — прямое чтение владельцем: правила уже разрешают `users/{uid}/receipts` хозяину
  (и запрещают писать), серверный вью был бы вторым ключом к той же двери. Слушатель Firestore
  даёт живое пополнение: начисление приезжает строкой, пока магазин открыт.
- Словарь ключ → ресурс живёт в презентации и замкнут: незнакомый ключ — общая строка с суммой,
  поэтому новый серверный ключ деградирует до честного «начислено N», а не до падения раздела.
- Параметры едут строками и подставляются по зафиксированному порядку каждого ключа — порядок
  слов в локали знает только перевод.

## Verification

**Commands:**
- `node functions/run-tests.js` -- expected: 109/109 (сервер не менялся, регрессия исключена).
- `sgw ./gradlew test --no-configuration-cache` -- expected: зелёный (JVM-тесты приложения и
  модулей).
- `sgw ./gradlew allTests --no-configuration-cache` -- expected: зелёный (KMP).
- `sgw ./gradlew detekt ktlintCheck --no-configuration-cache` -- expected: зелёный (минус
  известные красные владельца: detekt Android-модулей jvm-target 27, ktlint platform:firebase и
  app-shell presentation).
- `sgw ./gradlew :apps:android-next:assembleMvp --no-configuration-cache` -- expected: зелёная
  сборка (assembleDebug средово невозможен).
