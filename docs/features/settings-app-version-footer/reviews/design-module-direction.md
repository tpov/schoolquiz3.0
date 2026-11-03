# SCH-2 Quality Gate 6 — Module Direction Audit

Feature: `settings-app-version-footer`  
Scope: design docs audit, not implementation review  
Inputs:

- `docs/features/settings-app-version-footer/01-architecture.md`
- `docs/features/settings-app-version-footer/02-behavior.md`
- `docs/features/settings-app-version-footer/03-decisions.md`
- `docs/features/settings-app-version-footer/06-api-contract.md`

## Verdict

**PASS**

No blocker/high findings in the SCH-2 design docs. The design preserves the allowed one-way flow:

`apps/android-next` → `android/feature/app-shell/presentation` → `android/feature/local/settings/presentation` → `android/core/designsystem` primitives consumed by settings.

The docs do not propose a reverse dependency from local settings to app-shell/app/root/navigation internals, do not propose `android/core/designsystem` depending on app-shell or settings feature types, and keep SCH-2 API contracts limited to internal Compose UI signatures.

## Findings

None.

## Required Module Direction Checks

### 1. Core/designsystem must not depend on app-shell or settings feature types

**PASS.**

Design evidence:

- `01-architecture.md:100` says `android/core/designsystem` has **no design-system API change** and remains provider of `SchoolQuizTheme`, MaterialTheme setup, and `SchoolQuizDesignBackground`.
- `01-architecture.md:136` says design-system has **no dependency on local settings or app-shell** and remains lower-level.
- `03-decisions.md:75` explicitly rejects creating a reusable design-system footer component for SCH-2.

Code-reference grep evidence:

```bash
grep -RInE "^import .*\.android\.feature\." android/core/designsystem --include="*.kt"
# count=0
```

Related existing debt, not introduced/proposed by SCH-2:

```bash
grep -RInE "^import .*\.shared\.feature\." android/core/designsystem --include="*.kt"
```

returned existing quest-domain imports:

- `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCard.kt:28`
- `android/core/designsystem/src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/model/QuestDisplayItem.kt:4`
- `android/core/designsystem/src/androidTest/kotlin/com/tpov/schoolquiz/android/core/designsystem/components/QuestCardLongClickTest.kt:15`

Those are pre-existing generic core→feature debt, not app-shell/settings coupling and not part of SCH-2 design scope.

### 2. local/settings must not import app-shell/app/root/navigation internals

**PASS.**

Design evidence:

- `01-architecture.md:127` says `android/feature/local/settings/presentation` must not depend on app-shell or the app module.
- `03-decisions.md:148` says not to add a reverse dependency from local settings to app-shell, app module, root implementation, navigation internals, or other feature modules.
- `03-decisions.md:161` rejects local settings importing app-shell/root implementation to fetch version values.
- `03-decisions.md:168-170` constrains implementation to the existing app → app-shell → local/settings parameter flow and forbids reverse imports.

Code-reference grep evidence:

```bash
grep -RInE "^import .*(android\.feature\.app_shell|apps\.android_next|android\.core\.navigation|DefaultRootComponent|LocalConfig|RootComponent)" \
  android/feature/local/settings/presentation --include="*.kt"
# count=0
```

Version values are specified as primitive props:

- `06-api-contract.md:47-48` — `AppShellScreen(appVersionName: String, appVersionCode: Int, ...)`
- `06-api-contract.md:85-86` — private `AppShellContent(appVersionName: String, appVersionCode: Int, ...)`
- `06-api-contract.md:113-114` — private `LocalTabContent(appVersionName: String, appVersionCode: Int, ...)`
- `06-api-contract.md:141-142` — `DesignSettingsScreen(appVersionName: String, appVersionCode: Int, ...)`

### 3. Existing app-shell → local/settings dependency must remain one-way

**PASS.**

Design evidence:

- `01-architecture.md:124` documents app-shell → local/settings as an existing dependency/import path.
- `03-decisions.md:142-170` accepts preserving the existing one-way parent-to-child render path and explicitly forbids reverse imports.

Code-reference grep evidence:

```bash
grep -RInE "^import .*\.android\.feature\.local\.settings\.presentation" \
  android/feature/app-shell/presentation --include="*.kt"
# android/feature/app-shell/presentation/src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/ui/AppShellScreen.kt:63:
# import com.tpov.schoolquiz.android.feature.local.settings.presentation.ui.DesignSettingsScreen
```

Reverse direction check:

```bash
grep -RInE "^import .*\.android\.feature\.app_shell\.presentation" \
  android/feature/local/settings/presentation --include="*.kt"
# count=0
```

### 4. `06-api-contract.md` must stay internal Compose UI only

**PASS.**

Design evidence:

- `06-api-contract.md:11-22` says external/backend/domain API contract is N/A and the document is only the signature source of truth for internal Compose UI contracts.
- `06-api-contract.md:128` says no `LocalTabComponent`, app-shell domain navigation, or Decompose child-stack contract changes are introduced.
- `06-api-contract.md:168-177` explicitly forbids an `AppVersionInfo` model/provider, repositories, use cases, policies, domain models, Koin bindings, Room, SharedPreferences, DataStore, storage, events, analytics, navigation routes, and direct app `BuildConfig` usage from library modules.

No domain/data/Koin/storage contract is proposed. The only target signatures are Compose functions in app-shell/settings presentation:

- `AppShellScreen`
- private `AppShellContent`
- private `LocalTabContent`
- `DesignSettingsScreen`

## ADR Audit

| ADR | Constraint checked | Result |
|---|---|---|
| ADR-SCH2-01 | Generated `VERSION_NAME`/`VERSION_CODE` read only in `apps/android-next`; library modules receive primitives. | PASS — `03-decisions.md:26`; `06-api-contract.md:26-33`. |
| ADR-SCH2-02 | Settings screen owns pinned footer layout; no design-system primitive or shell overlay. | PASS — `03-decisions.md:58-60`, `03-decisions.md:72-75`. |
| ADR-SCH2-03 | Settings footer is passive and separate from drawer footer behavior. | PASS — `03-decisions.md:90-111`; `02-behavior.md:83-108`. |
| ADR-SCH2-04 | No domain/data/DI/storage/events/backend work. | PASS — `03-decisions.md:119-140`; `06-api-contract.md:11-22`, `06-api-contract.md:168-177`. |
| ADR-SCH2-05 | Preserve existing one-way app-shell → local/settings dependency; add no reverse dependency. | PASS — `03-decisions.md:148-170`. |

## Grep / Check Summary

`rg` is unavailable in this environment (`command -v rg` returned no path), so all checks used `grep`.

### Architect-reviewer required grep set

| Check | Command shape | Result |
|---|---|---|
| Domain Android imports | `grep -RInE "^import (android|androidx)\." shared --include="*.kt" \| grep "/domain/src/commonMain/"` | Clean, `count=0`. |
| Domain SDK imports | `grep -RInE "^import (com\.google\.firebase\|retrofit2\|okhttp3\|androidx\.room\|com\.squareup\.moshi\|kotlinx\.serialization)" shared --include="*.kt" \| grep "/domain/src/commonMain/"` | Clean, `count=0`. |
| Domain framework types | `grep -RInE "\b(Context\|Uri\|Bundle\|Intent\|View\|Activity\|Fragment)\s*[:,)]" shared --include="*.kt" \| grep "/domain/src/commonMain/"` | Clean, `count=0`. |
| Domain DI annotations | `grep -RInE "@(Inject\|Provides\|Binds\|Module\|Singleton\|HiltAndroidApp\|AndroidEntryPoint\|HiltViewModel)" shared --include="*.kt" \| grep "/domain/src/commonMain/"` | Clean, `count=0`. |
| Presentation data/persistence imports | `grep -RInE "^import .*\.(Dao\|Entity\|DataSource\|Mapper\|Firebase\|Room)" android --include="*.kt" \| grep "/presentation/src/main/"` | Clean, `count=0`. |
| Compose UI direct Koin | `grep -RInE "getKoin\(\|koinInject\(\|inject<" android --include="*.kt" \| grep "/presentation/src/main/.*/ui/"` | Clean, `count=0`. |
| AndroidX ViewModel in presentation | `grep -RInE "androidx\.lifecycle\.(ViewModel\|viewModelScope)\|: ViewModel\(" android --include="*.kt" \| grep "/presentation/src/main/"` | Clean, `count=0`. |
| Core imports feature code | `grep -RInE "^import .*\.shared\.feature\." shared/core android/core platform --include="*.kt"` | Non-empty existing debt; no SCH-2 design proposal depends on it. |
| Android feature presentation imports another feature presentation | `grep -RInE "^import .*\.android\.feature\..*\.presentation" android/feature --include="*.kt"` | Non-empty existing composition pattern. Relevant SCH-2 path is documented app-shell → local/settings only; reverse local/settings → app-shell is clean. |
| Shared feature-to-feature imports | `grep -RInE "^import .*\.shared\.feature\." shared/feature --include="*.kt"` | Non-empty broad existing same-feature/cross-feature imports; no SCH-2 shared-layer change proposed. |
| Business cleanup in `onDestroy` | `grep -RInE -A 15 "override fun onDestroy" apps android platform ... \| grep -E "(endCall\|stopService\|sendBroadcast\|cancelJob\|disconnect\|ACTION_END\|ACTION_STOP\|ACTION_KILL)"` | Clean, `count=0`. |
| Kill-intent in `onDestroy` | `grep -RInE -B 3 -A 10 "ACTION_END\|ACTION_STOP\|ACTION_KILL" apps android platform ... \| grep -B 10 "onDestroy"` | Clean, `count=0`. |
| Koin bindings | `grep -RInE "(single\|factory)<\|module \{" apps android shared platform --include="*.kt"` | Existing Koin modules found, as expected; SCH-2 docs explicitly add no Koin binding. |
| Hilt/Dagger annotations | `grep -RInE "@(Inject\|Provides\|Binds\|Module\|HiltAndroidApp\|AndroidEntryPoint\|HiltViewModel)" apps android shared platform --include="*.kt"` | Clean, `count=0`. |

### SCH-2 module-direction specific greps

| Check | Result |
|---|---|
| `android/core/designsystem` imports Android feature presentation types | Clean, `count=0`. |
| `android/core/designsystem` imports shared feature types | Existing quest-domain debt, `count=3`; no app-shell/settings type and no SCH-2 dependency proposal. |
| local/settings reverse imports app-shell/app/root/navigation internals | Clean, `count=0`. |
| app-shell imports local/settings presentation | Existing one-way composition dependency, one relevant import: `AppShellScreen.kt:63`. |
| local/settings imports app-shell presentation | Clean, `count=0`. |
| BuildConfig reads in app-shell/settings library modules | No direct code reads. Matches are comments documenting that `BuildConfig` is passed from app layer: `AppShellScreen.kt:107-108`, `DrawerFooter.kt:36`. |

## Conclusion

SCH-2 design docs pass the module-direction quality gate. The intended implementation path is constrained to primitive version metadata threading through existing presentation call sites, with no new module dependency, no bidirectional feature coupling, no core/designsystem feature-type dependency, and no domain/data/DI/storage/API expansion.
