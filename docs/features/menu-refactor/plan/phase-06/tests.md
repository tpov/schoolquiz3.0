---
phase: 06
role: test-dev
---

# Phase 06 — Test Tasks

## Pattern Invariants

- WorkManager testing с `TestListenableWorkerBuilder` требует Android context — instrumented test
- Для JVM: тест SyncWorker через fake `List<Syncable>`
- No Turbine — только direct invocations

---

## SyncWorker tests

**Файл:** `platform/android-services/src/test/.../SyncWorkerTest.kt`

**Fake:**
```
class FakeSyncable(private val result: Result<Unit> = Result.success(Unit)) : Syncable {
    var syncCalls: Int = 0
    override suspend fun sync(): Result<Unit> { syncCalls++; return result }
}
```

**Сценарии:**

- given `List<Syncable>` with 2 successful fakes, when `doWork()`, then `Result.success()` + each `syncCalls == 1`
- given first Syncable fails `Result.failure(IOException())`, when `doWork()`, then `Result.retry()` + second Syncable NOT called
- given empty `List<Syncable>`, when `doWork()`, then `Result.success()`

**Note:** Для JVM-только теста нужен способ создать `CoroutineWorker` без Android context. Альтернатива: тестировать delegation pattern через pure unit test на `doWork` logic abstracted.

---

## Validation

| Команда | Ожидаемый результат |
|---------|---------------------|
| `./gradlew :platform:android-services:test --no-configuration-cache` | GREEN |
