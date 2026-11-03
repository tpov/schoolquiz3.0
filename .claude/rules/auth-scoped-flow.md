# Auth-Scoped Flow — re-subscribe rule

User-specific Flows (отдающие данные текущего пользователя — profile, stats, sync state, orders, messages, achievements) ОБЯЗАНЫ re-subscribe на auth state changes. Прямое `dao.observeByUid(currentUid())` без auth trigger = stale data после logout / account-switch.

**Recurrence count**: 2 фичи (menu-refactor Bug #1, home-and-my-quests Bug #6). Этот pattern enforced как rule, потому что text-only mention в spec checklist (`feature-spec.md:203`) был ignored на impl phase.

## Pattern

```kotlin
// ❌ WRONG — uid захвачен один раз, after logout/switch остаётся stale
class UserStatsRepositoryImpl(
    private val dao: UserStatsDao,
    private val authRepository: AuthRepository,
) : UserStatsRepository {
    override fun observeStats(): Flow<UserStats?> {
        val uid = authRepository.currentUid() // SNAPSHOT
        return uid?.let { dao.observeByUid(it) } ?: emptyFlow()
    }
}
```

```kotlin
// ✅ CORRECT — Flow re-subscribes на uid change через flatMapLatest
class UserStatsRepositoryImpl(
    private val dao: UserStatsDao,
    private val authRepository: AuthRepository,
) : UserStatsRepository {
    override fun observeStats(): Flow<UserStats?> {
        return authRepository.currentUidFlow()
            .flatMapLatest { uid ->
                if (uid != null) {
                    dao.observeByUid(uid)
                } else {
                    flowOf(UserStats.guest())  // или guest defaults
                }
            }
            .distinctUntilChanged()
    }
}
```

## Rules

1. **AuthRepository должен предоставлять `currentUidFlow(): Flow<String?>`** (не только `currentUid(): String?`). Без Flow API нет re-subscribe возможности.

2. **User-specific Repository implementations** обязаны принимать `currentUidFlow()` Flow в конструктор и применять `.flatMapLatest { uid -> uid?.let { dao.observeByUid(it) } ?: flowOf(guestDefaults) }`.

3. **Logout side-effect**: при `currentUidFlow.emit(null)` — все user-scoped Flows должны emit guest defaults (либо `null`, либо `UserStats.guest()`, в зависимости от domain). НЕ emptyFlow() — это даёт stale collection в downstream.

4. **AppShell / privilege-gated UI**: при logout все privilege-зависимые состояния (admin menu visibility, qualification levels, dev-mode UI) должны immediately reset через guest emit. UI должен collect Flow с `.collectAsState(initial = guestDefaults)` для immediate reaction.

## Avoid

- Не вызывай `currentUid()` once-only внутри Repository implementation для observable Flow.
- Не возвращай `emptyFlow()` для null uid — это invisible stale (downstream collector думает "no data" вместо "guest").
- Не store uid как class field; используй Flow directly.
- Не делай auth state polling — используй reactive Flow API из AuthRepository.

## Review check (grep-паттерны для architect-reviewer)

```bash
# 1. Repository implementations с user-specific data, которые НЕ принимают currentUidFlow в конструктор
rg -n "class \w+RepositoryImpl\b" shared/**/data/src/commonMain platform -g "*.kt" | while read line; do
    file=$(echo "$line" | cut -d: -f1)
    name=$(echo "$line" | grep -oE 'class \w+')
    # Если class содержит observe* method, проверь что есть AuthRepository / currentUidFlow в constructor
    if grep -qE "fun observe\w*\(.*\):\s*Flow" "$file" && ! grep -qE "currentUidFlow|authStateFlow" "$file"; then
        echo "VIOLATION: $file — $name has observe* but no auth flow"
    fi
done

# 2. Прямой currentUid() outside flatMap — likely violation
rg -n "currentUid\(\)" shared/**/data/src/commonMain platform -g "*.kt" | grep -v "flatMapLatest\|flatMap\|currentUidFlow"

# 3. emptyFlow() для null uid handling — likely violation (should emit guest defaults)
rg -n "emptyFlow\(\)" shared/**/data/src/commonMain platform -g "*.kt" -B 2 | grep -A 2 "uid\s*==\s*null\|uid\s*!=\s*null"
```

Любой match в changed файлах = blocker (если фича работает с user-specific data); existing matches outside phase scope — report как existing debt.

## Связанные правила

- `.claude/rules/clean-architecture.md` — repository в data layer
- `.claude/rules/use-cases.md` — orchestration через UseCase
- `.claude/rules/kotlin-conventions.md` — `Flow.flatMapLatest` для async cancellation
- `docs/invariants.md` — глобальный auth-scoped Flow invariant
