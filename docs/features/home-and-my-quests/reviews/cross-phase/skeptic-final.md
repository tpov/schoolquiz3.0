## Verification
- Fix 2 [VERIFIED]: [firestore.rules](/home/Programming/Android/schoolquiz4.0/firestore.rules:34) now has `request.resource.data.keys().hasOnly([...])`; [line 40](/home/Programming/Android/schoolquiz4.0/firestore.rules:40) requires `version == 1`; [line 41](/home/Programming/Android/schoolquiz4.0/firestore.rules:41) requires `contentsVersion == 0`; [line 42](/home/Programming/Android/schoolquiz4.0/firestore.rules:42) blocks `averageRating` / `averageRatingCount`. Note: those rating fields are still listed in `hasOnly`, but the later `!hasAny` makes them effectively forbidden.
- Fix 3 [VERIFIED]: [0-spec.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/0-spec.md:64) says `Clock.System.now()` via `CascadingSyncOrchestrator`; [02-behavior.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:447) says orchestrator advances catalog cursor and [line 448](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/02-behavior.md:448) uses `freshTime` / `Clock.System.now()`; [03-decisions.md](/home/Programming/Android/schoolquiz4.0/docs/features/home-and-my-quests/03-decisions.md:687) now treats `max(lastModifiedAt)` only as the rejected hypothetical and states `cursor = Clock.System.now()`.

## Previous still OK?
Fix 1: YES  
B1: YES  
B2: YES  
B3: YES  
B4: YES  
B5: YES  
H2: YES  
H7: YES  
H8: YES

## New regressions
- none

## Overall verdict
PASS