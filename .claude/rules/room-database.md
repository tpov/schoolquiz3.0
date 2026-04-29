---
paths:
  - "shared/core/persistence/src/commonMain/**/*.kt"
  - "shared/**/data/src/commonMain/**/*.kt"
  - "shared/**/data/src/androidMain/**/*.kt"
  - "shared/**/src/androidTest/**/*.kt"
  - "shared/**/src/androidInstrumentedTest/**/*.kt"
  - "android/**/src/androidTest/**/*.kt"
---

# Room Database — Android

## Structure

```
data/
  db/
    AppDatabase.kt          — Room DB class (@Database)
    dao/                     — DAO interfaces
    entities/                — Entity classes (@Entity)
    mappers/                 — Entity <-> Domain mappers
  migrations/                — Migration classes (optional location)
```

Consult PROJECT-CONTEXT.md for project-specific database name, location, and entities.

## DAO patterns

```kotlin
@Dao
interface SomeDao {
    // Reactive observation
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: Long): Flow<ItemEntity?>

    // List with ordering
    @Query("SELECT * FROM items ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    // Suspend for one-shot operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ItemEntity): Long

    // Transaction for multi-table operations
    @Transaction
    @Query("UPDATE items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}
```

## Rules

- Prefer `Flow<T>` in DAO for reactive observation.
- Entity classes stay in data layer — never expose to domain or presentation.
- Always map: `Entity.toDomain()` and `DomainModel.toEntity()`.
- Use `@Transaction` for operations that read/write multiple tables.
- Index frequently queried columns via `@Index`.
- When changing schema: check impact on ALL existing DAO queries, mappers, and repositories that consume affected tables.
- Provide migration paths — don't rely on destructive migration in production.
- Test migrations with `MigrationTestHelper`.

## Migration checklist

When adding/removing/changing columns:
1. Create `Migration(oldVersion, newVersion)` with ALTER TABLE statements.
2. Update entity class with new fields.
3. Update mapper if domain model changes.
4. **Impact scan**: check ALL DAO queries that touch the table — do they assume value ranges, NOT NULL, or specific data types that the migration changes?
5. Test migration preserves existing data.

## Avoid

- No raw SQL outside data layer.
- No entities or DTOs in UI state — map to domain first.
- No business logic inside DAO — keep queries pure data access.
- No skipping migration testing when adding/removing columns.
- No assumptions about ID ranges (e.g., `id > 0`) without explicit validation — temp IDs, offline-generated IDs may use negative ranges.
