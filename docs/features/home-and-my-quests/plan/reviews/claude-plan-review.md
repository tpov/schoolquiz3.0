Все необходимые данные собраны. Пишу финальный отчёт.

---

## Adversarial Plan Review — home-and-my-quests

### Проверка previously "fixed" blockers

| Blocker | Статус |
|---------|--------|
| **F-01** AC#50 в phase-03, не out-of-scope | ✅ FIXED — README строка 91 явно указывает "AC#50 is IN-SCOPE"; phase-03 AC-таблица содержит его |
| **F-02** SyncModule без `named("cascading")` | ⚠️ PARTIAL — backend.md исправлен, но overview.md содержит 3 неисправленных вхождения (см. BLOCKER-02) |
| **F-03** FakeQuestRepository `refreshFromRemote` 4 params | ✅ FIXED — phase-05/tests.md строка 34 совпадает с api-contract §2.2:181-186 |
| **F-04** `MyQuestsUiState` без `error` поля | ✅ FIXED — api-contract §6.1:408-414 подтверждён, PartialFailRetryTest использует только `isLoading`/`quests` |

---

## Findings

---

### BLOCKER-01 · Plan-as-ТЗ

**Location:** `plan/phase-05/tests.md:216, 223, 226, 236`

**Problem:** PartialFailRetryTest четыре раза вызывает `component.onRefresh()`:

```
WHEN: component.onRefresh() called AND refresh completes      (line 216)
AND: component.onRefresh() called (fails)                     (line 223)
WHEN: component.onRefresh() called again (succeeds)          (line 226)
WHEN: component.onRefresh() called AND refresh fails         (line 236)
```

Метод `onRefresh()` **отсутствует** в `MyQuestsComponent` interface — ни в api-contract §6.1:402-406 (проверено), ни в phase-05/frontend.md task #6. Интерфейс содержит только `onCatalogSelected`, `onCreateQuestClick`, `state`. Test-dev не сможет скомпилировать эти тесты.

**Required fix:** Выбрать один из вариантов:
- (A) Добавить `fun onRefresh()` в `MyQuestsComponent` interface в api-contract §6.1 + frontend.md task #6 + FakeQuestRepository call tracking в phase-05/tests.md fakes blueprint
- (B) Переписать PartialFailRetryTest — тестировать через изменение FakeQuestRepository.store и реактивный flatMapLatest (без явного onRefresh trigger, компонент не имеет pull-to-refresh по spec)

---

### BLOCKER-02 · Plan-as-ТЗ + Sequencing

**Location:** `plan/phase-03/overview.md:15, 99, 157`

**Problem:** F-02 fix был неполным. backend.md исправлен, но overview.md содержит три противоречивых вхождения старого (неверного) паттерна:

- **Line 15** (Goal): `"заменить get<CatalogRepository>() as Syncable на get<Syncable>(named("cascading"))"`
- **Line 99** (Modified Files table): `"Replace ... with get<Syncable>(named("cascading"))"`
- **Line 157** (Pattern Invariants): `"CascadingSyncOrchestrator ДОЛЖЕН регистрироваться через single<Syncable>(named("cascading")) — НЕ как single<CascadingSyncOrchestrator>"`

При этом в **той же phase-03/overview.md строка 161** написано правильно:
> `"SyncModule ДОЛЖЕН содержать single<CascadingSyncOrchestrator> { ... } (без named qualifier)"`

Api-contract SSoT `06-api-contract.md:787-797` подтверждает: `single<CascadingSyncOrchestrator>` без qualifier. backend.md тоже верен.

Агент, читающий overview.md, видит **прямое противоречие** между строкой 157 (неверной) и строкой 161 (верной) в одном файле, плюс неверные строки 15 и 99.

**Required fix:** В phase-03/overview.md исправить строки 15, 99, 157:
- Line 15: `"...заменить get<CatalogRepository>() as Syncable на get<CascadingSyncOrchestrator>()"`
- Line 99: `"Replace ... with get<CascadingSyncOrchestrator>()"`
- Line 157: Удалить или заменить на `"CascadingSyncOrchestrator ДОЛЖЕН регистрироваться через single<CascadingSyncOrchestrator> { ... } (без named qualifier); получается через get<CascadingSyncOrchestrator>() в List<Syncable>"`

---

### HIGH-01 · Sequencing

**Location:** `plan/README.md:43` + `plan/phase-01/tests.md` (отсутствие)

**Problem:** README File Map для phase-01 заявляет **16 новых файлов**, включая `InMemorySyncStateRepositoryTest`. Но:

1. `InMemorySyncStateRepositoryTest` **уже существует** в Walking Skeleton: `shared/core/sync/src/commonTest/kotlin/.../InMemorySyncStateRepositoryTest.kt` (проверено — файл и .class артефакты присутствуют)
2. Phase-01/overview.md New Files table содержит **13 файлов** (не 16)
3. Phase-01/tests.md не содержит сценариев для InMemorySyncStateRepositoryTest

AC#41-44 покрыты (тест pre-existing и green), но README неверно классифицирует файл как "новый" для phase-01, а phase-01 New Files count завышен. Если реализующий агент доверяет README (16 файлов), он будет искать то, что уже написано, и либо дублирует, либо тратит время на поиск.

**Required fix:**
- README строка 43: убрать `InMemorySyncStateRepositoryTest` из phase-01 file map; скорректировать count 16→15
- AC#41-44 перенести в "Pre-existing (Walking Skeleton)" строку в AC Coverage Map (или добавить явную пометку `(pre-existing)` рядом с тестом в phase-01 AC table)

---

### MEDIUM-01 · Sequencing

**Location:** `plan/phase-02/tests.md:93-94`

**Problem:** QuestRepositoryImplTest в phase-02 требует `FakeSyncStateRepository`:

> `"FakeSyncStateRepository — берём из shared/core/sync/commonTest/fake/ (или дублируем в quest/data)"`

Но `FakeSyncStateRepository` **создаётся только в phase-03/tests.md** (task #1). В phase-02 этого файла нет — ни в Walking Skeleton (проверено: glob по FakeSyncStateRepository дал 0 файлов), ни в phase-01/phase-02.

`"или дублируем"` — неопределённое решение. Test-dev не знает: дублировать (и как убирать дубль в phase-03?) или ждать (но тогда phase-02 тесты не пишутся параллельно).

**Required fix:** Явно указать в phase-02/tests.md: `"FakeSyncStateRepository создаётся в phase-02/fake/ по blueprint из phase-03/tests.md §1; phase-03/tests.md повторно использует тот же файл (общий или задублированный — определить в plan)"`. Или создать `FakeSyncStateRepository` Signature Card прямо в phase-02/tests.md.

---

### MEDIUM-02 · Plan-as-ТЗ (SSoT contradiction)

**Location:** `docs/features/home-and-my-quests/06-api-contract.md:178` vs `api-contract:185`

**Problem:** KDoc комментарий к `QuestRepository.refreshFromRemote`:

> **Line 178:** `"Reads cursor from SyncStateRepository internally."`

Но сигнатура функции (line 185): `cursor: Long` — явный параметр. Plan правильно разрешает это в phase-03/backend.md (RESOLUTION note), но сам SSoT самопротиворечив. Будущие maintainers, читающие только api-contract, получат неверное представление.

**Required fix:** Исправить комментарий на:
> `"cursor: Long — передаётся orchestrator-ом (из SyncStateRepository.getCursor("quests")); QuestRepositoryImpl не читает cursor внутри."`

---

### MEDIUM-03 · Sequencing (AC attribution)

**Location:** `plan/README.md:155-157`

**Problem:** AC#48 и AC#49 в phase-05 AC Detailed Distribution table имеют пометку `(phase-02 DAO test)`:

```
| AC#48 | Quest visibleOn=emptySet + authorUid=me → deleted | (phase-02 DAO test) |
| AC#49 | Quest visibleOn=emptySet + authorUid=other → deleted | (phase-02 DAO test) |
```

Но в README Summary фаза phase-02 содержит только `AC#7, AC#8, AC#9, AC#19, AC#20` (5 ACs). AC#48 и AC#49 туда не попали. Это означает что при gate-проверке phase-02 агент не знает, что AC#48/49 там покрыты; при gate phase-05 агент видит эти ACs, но тест уже должен существовать (phase-02).

**Required fix:** Либо добавить AC#48,49 явно в phase-02 AC Coverage Map, либо добавить пометку в phase-02/tests.md что эти ACs покрываются `QuestRepositoryImplTest` (Edge 1.9 сценарии там уже есть).

---

### LOW-01 · Sequencing

**Location:** `plan/README.md:86`

**Problem:** README говорит `"Total in-scope ACs across phases: 44 ACs"`. Подсчёт: phase-01(9) + phase-02(5) + phase-03(9) + phase-04(1) + phase-05(15) + Walking Skeleton(5) = 44, но AC#29 посчитан дважды (phase-04 domain + phase-05 UI). Уникальных in-scope ACs = 43 + 5 Walking Skeleton = 48... Нет, 44 верно если Walking Skeleton (5) включён в число. Тогда 9+5+9+1+15+5 = 44 с двойным AC#29 = математически верно.

Технически OK, но двойной счёт AC#29 не задокументирован явно.

**Required fix:** Добавить сноску `"* AC#29 включён в phase-04 (domain) и phase-05 (UI) — одна уникальная AC, два gate события"` или уточнить: `"44 ACs (включая 5 Walking Skeleton; AC#29 разделена на два gate)"`.

---

## README Sync Issues

| Поле | Найдена проблема |
|------|-----------------|
| File Map count phase-01 | README: 16, overview.md New Files: 13, фактически новых: 15 (InMemorySyncStateRepositoryTest pre-existing) |
| AC coverage map phase-02 | Отсутствуют AC#48, AC#49 (есть в phase-05 таблице, тест в phase-02) |
| AC coverage "pre-existing" | Только AC#1,2,4,5,6 — должны добавиться AC#41-44 (InMemorySyncStateRepositoryTest pre-existing) |
| phase-03 Modified Files | Line 99: неверный паттерн `get<Syncable>(named("cascading"))` |

---

## Residual Risks

1. **Walking Skeleton integration mode нарушений нет**: ни одна фаза не ставит Walking Skeleton файлы в "New Files" (кроме ошибочного InMemorySyncStateRepositoryTest в README). Phase-01 overview явно говорит "adapter-only integration". ✓

2. **Scaffold ownership корректен**: build.gradle.kts, settings.gradle.kts, AppApplication.kt — только в backend.md для каждой фазы. frontend.md и tests.md не трогают scaffold. ✓

3. **```kotlin/```kt/```java blocks** в plan файлах: отсутствуют — все code-блоки либо plain ```, либо ```bash. ✓

4. **Options Considered** (complex phases 01-03, 05): присутствуют, 2+ варианта, recommended + rejected с trade-offs. Phase-04 — simple, не требуется. ✓

5. **Риск onRefresh() расширения**: если team выберет вариант (A) добавить `fun onRefresh()` в интерфейс — это изменение api-contract §6.1, требует также обновить SSoT, FakeQuestRepository call tracking и `DefaultMyQuestsComponent` реализацию. Вариант (B) переписать тесты проще.

---

## Verdict: **CONTESTED**

**2 BLOCKER** блокируют приёмку:

- **BLOCKER-01** (`onRefresh()` undefined): test-dev не сможет написать PartialFailRetryTest — метод не существует в интерфейсе.
- **BLOCKER-02** (F-02 incomplete): phase-03/overview.md содержит 3 строки с неверным `named("cascading")` паттерном, которые прямо противоречат backend.md и api-contract SSoT на том же уровне.

---

## Сводка для lead (≤200 слов)

Два блокера, один из которых — неполное исправление ранее закрытого F-02.

**BLOCKER-01:** phase-05/tests.md четырежды вызывает `component.onRefresh()` (строки 216, 223, 226, 236) — этого метода нет в `MyQuestsComponent` интерфейсе ни в api-contract §6.1, ни в frontend.md. Тесты не скомпилируются. Нужно либо добавить метод в интерфейс (с каскадом изменений в SSoT), либо переписать тесты через реактивный Flow.

**BLOCKER-02 (F-02 residual):** backend.md исправлен верно (`single<CascadingSyncOrchestrator>` без qualifier), но phase-03/overview.md содержит три старых вхождения `named("cascading")` в строках 15, 99, 157 — агент, читающий overview первым, получит неверные инструкции. Строка 157 прямо противоречит строке 161 того же файла.

Дополнительно HIGH: README File Map считает `InMemorySyncStateRepositoryTest` новым файлом phase-01, но он pre-existing (Walking Skeleton); AC#41-44 фактически уже покрыты.

После исправления 2 blockers + HIGH — план готов к PASS.
