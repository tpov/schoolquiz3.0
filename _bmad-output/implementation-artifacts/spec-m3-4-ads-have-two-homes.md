---
title: 'M3.4 — Ads never enter the lesson, and a build without an id turns them off'
type: 'feature'
created: '2026-10-11'
status: 'done'
route: 'full'
route_source: 'pinned'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
followup_review_recommended: false
baseline_commit: '22f5077c7f4397d49927fd139fb892cb9d1aa9ed'
story_key: '3-4-реклама-не-входит-в-урок'
sprint_status_file: '_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context-monetisation.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-m3-1-network-confirms-the-reward.md'
warnings: []
deferred: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** у рекламы в приложении нет ни дома, ни выключателя. Сегодня её действительно нет
нигде — нет и SDK, и идентификатора — но и запрета нет: первый же интегрирующий lobbyists
SDK не встретит в дереве ни названных мест, ни урока, закрытого для рекламы, ни сборки, которая
без идентификатора честно выключает рекламу вместо падения.

**Approach:** у рекламы появляется один дом — модуль `shared:feature:ads:domain`: закрытый
перечень мест (`BOX_UNLOCK`, `OFFERS_SCREEN` — ровно те два, что назвал владелец), состояние
доступности и врата, читающие манифест: нет `com.google.android.gms.ads.APPLICATION_ID` —
реклама выключена, во всех местах, названным состоянием. Инварианты запираются тестами: урока
реклама не касается (скан исходников lesson-runner), SDK AdMob в дереве не появляется (скан по
всем исходникам), мест ровно два. Живой SDK-показ и поверхности-экраны — не здесь: врата пассивны,
никто в старте их не зовёт, UI-элемент без адаптера не рисуется.

## Boundaries & Constraints

**Always:** перечень мест закрытый и исчерпывающий — два значения, названные владельцем; состояние
«выключено» — именованное, а не отсутствие кода. Врата — чистая функция над метаданными манифеста:
пустая, отсутствующая или белизна-строка идентификация означает Disabled, непустая — Enabled,
одинаково для всех мест. Комментарии и стиль — по соседним domain-модулям. Сборка приложения
(mvp, без идентификатора) зелёная: ни манифеста, ни старта, ни зависимостей история не добавляет
к приложению.

**Ask First:** зависимости приложения (gradle), манифесты (main/mvp), AppApplication/MainActivity,
существующие экраны (HomeQuests и прочие) — потребители врат появятся вместе с живым SDK (defer).

**Never:** рекламные SDK в зависимостях; UI-элементы рекламы; вызовы врат из урока; второй
перечень мест где-либо ещё.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Идентификатора нет в манифесте | метаданные без ключа AdMob | Disabled для обоих мест | N/A |
| Идентификатор пустой/пробелы | `""`/`"  "` | Disabled — пустое значение не «включено» | N/A |
| Идентификатор есть | непустая строка | Enabled для обоих мест | N/A |
| Мест ровно два | значения `AdPlacement` | BOX_UNLOCK, OFFERS_SCREEN — закрытый перечень | N/A |
| Урок сканируют | исходники lesson-runner (shared+android) | ни одного упоминания рекламных типов | N/A |
| Дерево сканируют | все Kotlin-исходники | `gms.ads` встречается только в константе ключа метаданных модуля ads | N/A |

</frozen-after-approval>

## Code Map

- `shared/feature/ads/domain/build.gradle.kts` -- NEW: `id("schoolquiz.kmp.library")` + namespace,
  по образцу `shared/feature/local/settings/domain`; `settings.gradle.kts` -- include
  `:shared:feature:ads:domain` в блоке shared-feature.
- `shared/feature/ads/domain/src/commonMain/.../ads/domain/AdPlacement.kt` -- NEW enum:
  `BOX_UNLOCK`, `OFFERS_SCREEN` -- закрытый перечень мест рекламы.
- `.../AdsAvailability.kt` -- NEW sealed interface: `Disabled`, `Enabled` -- именованные состояния.
- `.../AdsGate.kt` -- NEW interface: `fun availability(placement: AdPlacement): AdsAvailability`.
- `.../ManifestAdsGate.kt` -- NEW: константа `ADMOB_APP_ID_METADATA =
  "com.google.android.gms.ads.APPLICATION_ID"` и чистая функция
  `availabilityFor(manifestMetadata: Map<String, String>, placement)`; реализация `AdsGate` поверх
  неё. Android-обёртка (Context → Map) придёт с живым SDK.
- `shared/feature/ads/domain/src/jvmTest/.../AdsDomainTest.kt` -- NEW: матрица врат; NEW
  `AdsHomePinsTest.kt` -- исходник-сканы: lesson-runner (shared+android) чист от рекламных типов,
  `gms.ads` в дереве только в константе ключа (образец скана — box-service.test.js истории 3.3 и
  EconomyConstantsParityTest, читающая файлы по относительному пути).
- Read-only: `apps/**`, `android/**`, остальной `shared/**` — ни один существующий файл не меняется.

## Tasks & Acceptance

**Execution:**
- [ ] `settings.gradle.kts` + `shared/feature/ads/domain/build.gradle.kts` -- модуль -- по конвенции.
- [ ] `AdPlacement.kt`, `AdsAvailability.kt`, `AdsGate.kt`, `ManifestAdsGate.kt` -- дом рекламы.
- [ ] `AdsDomainTest.kt` -- матрица врат -- закрепить.
- [ ] `AdsHomePinsTest.kt` -- урок чист, SDK в дереве только именем ключа -- исходник-сканы.
- [ ] `_bmad-output/implementation-artifacts/sprint-status-monetisation.yaml` -- `3-4-…` →
  `in-progress`, затем `review`.

**Acceptance Criteria:**
- Given игрок проходит урок любой сложности, когда идёт урок, то ни одной рекламной поверхности
  в lesson-runner нет — и исходник-тест это называет, а не обещает.
- Given перечень мест рекламы, когда его открывают, то в нём ровно два значения: разблокировка
  коробки и экран предложений.
- Given сборка без идентификатора AdMob, когда врата спрашивают о любом месте, то ответ —
  именованное Disabled, и сборка приложения зелёная без единого падения.

## Implementation Notes

- Route `full`; реализация и линзы — в этой же сессии (те же платформенные ограничения, что в 3.1).
- 2026-10-11: ни один существующий файл, кроме settings.gradle.kts, не тронут; приложение не
  получило ни зависимостей, ни манифестных записей — сборка без идентификатора AdMob и есть
  сегодняшняя сборка, и она зелёная.

## Spec Change Log

## Review Triage Log

### 2026-10-11 — Review pass (thorough: blind-hunter + edge-case-hunter + verification-gap + intent-alignment)

Линзы в сессии (то же платформенное отклонение, что в 3.1); пол слепого охотника: N = 6 по 26 КБ.

- verdicts: 6 findings — high 0, medium 0, low 2, false 3, maybe-false 0
- findings:
  - `[low]` `[patch]` Gradle-зависимость lesson-runner на ads-модуль просачивалась бы мимо скана:
    в kts путь пишется двоеточием («feature:ads»), а токен скана был с точкой — исправлено: в
    перечень запрещённых добавлен «feature:ads», импорты и зависимости ловятся оба.
  - `[low]` `[patch]` ktlint на новом модуле: первая строка тела override-функции уместилась в
    сигнатуру — исправлено однострочником; detekt и ktlint модуля зелёные.
  - `[low]` `[reject]` У врат нет ни одного потребителя в прод-коде — отвергнуто по намерению:
    живой SDK-показ и поверхности отложены (defer владельца), DI-обвязка для нуля потребителей —
    преждевременный код; врата пассивны сознательно (Design Notes), экран без адаптера был бы
    ложью игроку.
  - `[false]` Скан SDK попал на RewardedAdCompleted — опровергнуто как находку: это собственное
    событие аналитики, а не класс SDK; скан уточнён SDK-специфичными токенами
    (gms.ads, play-services-ads, AdView, com.google.android.ads).
  - `[false]` Параметр placement в availabilityFor не участвует в вычислении — опровергнуто:
    намеренно — место называется, ответ общий; выключатель один на все места, detekt молчит.
  - `[false]` Поиск корня репозитория в пинах хрупок к переносу каталогов — опровергнуто:
    хрупкость намеренная (kdoc), перенос громко уронит тест, и переезжающий поправит пути.

## Design Notes

- Врата пассивны и никто их не зовёт: UI-элемент «посмотреть рекламу» без адаптера был бы ложью
  игроку. Потребители (HomeQuests для BOX_UNLOCK, экран предложений эпика 4) появятся вместе с
  живым SDK — врата уже дают им именованный ответ вместо падения.
- Раздел ads в файлах констант экономики не нужен: идентификатор AdMob — не экономическое число,
  а манифестная метка сборки; выключатель читает манифест.

## Verification

**Commands:**
- `sgw ./gradlew :shared:feature:ads:domain:allTests --no-configuration-cache` -- expected: зелёные.
- `sgw ./gradlew :apps:android-next:assembleMvp --no-configuration-cache` -- expected: сборка без
  идентификатора AdMob успешна (это и есть сборка «без id»).
- `node functions/run-tests.js` -- expected: 104/104 (сервер истории не трогали — регрессия).

**Manual checks (no CLI):**
- Манифесты main/mvp не содержат AdMob meta-data — чтением.

## Auto Run Result

Status: done
Blocking condition: none

**Summary:** у рекламы появился дом и выключатель. Новый модуль `shared:feature:ads:domain`:
закрытый перечень мест (BOX_UNLOCK, OFFERS_SCREEN — ровно два, названные владельцем; урока в
перечне нет), именованные состояния Disabled/Enabled, врата `AdsGate` и реализация над метаданными
манифеста — нет `com.google.android.gms.ads.APPLICATION_ID`, рекламы нет всюду. Инварианты
заперты исходник-сканами: lesson-runner (shared+android) чист от рекламных типов и зависимостей;
SDK AdMob в дереве живёт только именем ключа метаданных в константе. Врата пассивны: ни старт,
ни экраны их не зовут — потребители приедут с живым SDK (defer владельца).

**Files changed:**
- `settings.gradle.kts` — include `:shared:feature:ads:domain`.
- `shared/feature/ads/domain/` — NEW: build file, AdPlacement, AdsAvailability, AdsGate,
  ManifestAdsGate (+ADMOB_APP_ID_METADATA), AdsDomainTest (матрица), AdsHomePinsTest (сканы).
- Спека + `sprint-status-monetisation.yaml` — 3-4: backlog → done.

**Review findings breakdown:** 2 патча (токен «feature:ads» против gradle-зависимости мимо скана;
ktlint-однострочник), 1 отвергнутый по намерению (врата без потребителей — SDK отложен), 3
отвергнутых с опровержениями. followup_review_recommended: false.

**Verification:** `sgw ./gradlew :shared:feature:ads:domain:allTests` — зелёные (5 матричных +
2 пина); `:shared:feature:ads:domain:detekt ktlintCheck` — зелёные;
`sgw ./gradlew :apps:android-next:assembleMvp` — зелёная (сборка без идентификатора AdMob);
`node functions/run-tests.js` — 104/104 (сервер не тронут). Манифесты main/mvp — чтением, без
AdMob записей.

**Residual risks:** живой показ рекламы, потребители врат и Android-обёртка Context → Map —
defer владельца до появления аккаунта AdMob; сканы хрупки к переносу каталогов — намеренно.
