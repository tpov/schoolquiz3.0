## Fix verification
- B1 [VERIFIED FIXED]: `firestore.rules:22-29` enforces quest-level read via `authorUid` / `visibleOn`; `firestore.rules:32-43` documents Option C and allows any-auth nested reads. ADR/API docs also record it at `03-decisions.md:821-848` and `06-api-contract.md:818-845`.
- B2 [PARTIAL]: code is fixed: `CatalogRepositoryImpl.kt:25-49` only reads cursor at `:27`, while `CascadingSyncOrchestrator.kt:92-103` advances catalog cursor after empty catalog delta or successful quest subtree. But docs still say old behavior: `02-behavior.md:33` and `0-spec.md:1100` still put `setCursor("catalogs", max(...))` in `CatalogRepositoryImpl`.
- B3 [PARTIAL]: implementation uses one `freshTime = Clock.System.now()` at `CascadingSyncOrchestrator.kt:68`, and all six `setCursor` calls use `freshTime` at `:99`, `:103`, `:122`, `:126`, `:143`, `:147`, `:164`, `:168`, `:185`, `:189`, `:202`. Requested lines are updated at `0-spec.md:86`, `0-spec.md:1171`, `02-behavior.md:60`. But stale contradictions remain: `03-decisions.md:47`, `02-behavior.md:149`, `02-behavior.md:418-431`, `0-spec.md:761`, `0-spec.md:997-998`, `0-spec.md:1100`.
- H8 [VERIFIED FIXED]: `StarRating.kt:64` clips a narrow `Box`, and the filled `Icon` inside uses `Modifier.requiredSize(size)` at `StarRating.kt:69`.

## New regressions
- Security regression: `firestore.rules:10-13` blocks only top-level privilege fields, but `isAdmin()` trusts `users/{uid}.qualifications.admin` at `firestore.rules:45-47`; an owner write to the `qualifications` map appears unblocked, enabling admin self-escalation for `catalogs`/nested writes.
- Quest write rules are too broad: `firestore.rules:26-29` lets authors create/update arbitrary quest fields, including server-managed rating/cursor/version fields and even `authorUid` on update. This contradicts `0-spec.md:887`, `0-spec.md:891`, and `06-api-contract.md:64`.

## Overall verdict
REJECT