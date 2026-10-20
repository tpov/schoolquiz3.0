# 7.6 sandbox attempt on the owner-prepared Pixel — blocked on a NEW production build — 2026-10-20

The owner prepared the Pixel (reboot + Play Store cache — his «все сделал»): Play Store is
healthy and signed in (v53.5.48), the production app 4.0.0/40000 (installed 16.10) opens,
the SHOP renders real Play prices (premium 239,99 UAH; gold packs 11,99/119,99/299,99 UAH;
charges locked correctly).

## What is now precisely isolated

- **The production build is stale, and its startup auth race is CONFIRMED on-device**: the
  EVENTS tab says «Could not refresh participants — Authenticated uid is required» with
  «Гость / access open» (76-events.png). The fire-and-forget signInAnonymously (fixed in
  45c9afd1 today) lost its race in this old build — the uid never lands, so every purchase
  tap dies as NoAccountException BEFORE billing is ever touched.
- **Silent Buy is a real UX defect in the old build**: the tap handler maps failures to a
  ShopMessage.Failure(errorDetail()), but no message renders — the funnel event fires, the
  player sees nothing. Whether the new build still has this (the message pipeline exists) or
  the old one lost it needs one look on the new build; recorded as a finding either way.
- **BillingClient state is UNPROVABLE until uid lands**: with no account the use case refuses
  before connect/purchase; zero billing logs are expected and were seen.

## Why this session stops here (deliberately)

A new production build cannot be assembled in this session: release signs with
/workspace/keys/Schoolquiz3.0.jks and the keystore password is the owner (README.txt says
so explicitly; no LEGACY_* env anywhere on the VPS). The mvp build cannot substitute — it
runs the demo backend and fake billing, not Play.

## What unblocks 7.6 immediately (owner, ~10 minutes)

1. Provide the keystore password (LEGACY_STORE_PASS / LEGACY_KEY_PASS) or run
   `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :apps:android-next:assembleRelease` yourself;
2. adb install -r the APK on the Pixel (same legacy key — update installs cleanly);
3. launch → the bounded wait guarantees a uid before the first tap → tap Buy on
   Gold Pack Small → license-tester sandbox purchase continues the matrix
   (credit check → refund → clawback; the console refund step still needs the owner).
