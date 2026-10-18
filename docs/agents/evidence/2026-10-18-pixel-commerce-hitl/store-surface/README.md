# Store surface device proof — Pixel 10 Pro — 2026-10-18 evening (UPDATED: insufficient-funds PASS)

Story: epic-commerce 7.2 `store-gold-prices-and-unavailable-products` (device-proof half).
Device: Pixel 10 Pro (56271FDCH00C5S), Wi-Fi adb; fresh anonymous account UserG9FY6E
(uid VLqWYMHXqyI8pPWQdNXdkQg9FY6E, created 20:45 local after `pm clear`).

## Transport (what made this run possible)

`adb reverse` proved unusable for data on this setup (SYN accepted, payload dropped — verified
with an independent Mac-side listener; both USB and Wi-Fi transports; `adb kill-server` and an
adbd restart did not help). The working path instead routes the app DIRECTLY to the VPS:

- build-time change: `MVP_EMULATOR_HOST` / `MVP_EMULATOR_FUNCTIONS_PORT` BuildConfig fields
  (apps/android-next build.gradle.kts mvp block) consumed by `initializeFirebaseSecurity`;
  defaults keep the 127.0.0.1 loopback contract for the redroid rig untouched.
- VPS socat relays (nohup, logs /tmp/socat-*.log): 0.0.0.0:9099→19090 (auth),
  0.0.0.0:8080→18090 (firestore), 0.0.0.0:5002→5001 (functions; node's own 5001 accepts
  external connections but never answers them — pre-existing quirk, worked around).
- cleartext fix: 204.44.66.27 added to apps/android-next/src/mvp/res/xml/
  mvp_network_security_config.xml (mvp variant only — without it Android blocked the HTTP
  emulators: `CLEARTEXT communication to 204.44.66.27 not permitted`).

Build: `JAVA_HOME=/opt/jdks/jdk-27 sgw ./gradlew :apps:android-next:assembleMvp
-PmvpEmulatorHost=204.44.66.27 -PmvpEmulatorFunctionsPort=5002 -I /tmp/signfix.gradle`
(the init script overrides the legacy signing store with /root/.android/debug.keystore for
this build only — the LEGACY_* keystore password is owner-only per /workspace/keys/README.txt;
the resulting certificate 00:33:E7:FA… is byte-identical to the candidate debug cert, so
`install -r` keeps app data). Final APK sha256
d0c59bfcebf30709126a14b3aa8e0336deccc0a8150b3820e78e17935e7d2d77, installed 20:48 local.
NOTE: this build is newer than the identified candidate (post epic-5/6 merge + the two
transport accommodations); the candidate identification re-run remains a rig story.

## What the run SHOWS

- 07-live-shop-fresh.png — live store on the fresh account: header 0 ◎ / 0 gold, server
  catalog with gold prices (Charge 20 000 ◎, Quiz slot 1 000 ◎, Golden charge 1 gold), and
  the visibly-unavailable states: Premium + Small/Medium/Large gold packs locked with
  «Google Play is unreachable — prices …», Quiz slot «Waiting for quiz limit modelling…»,
  ad box «Waiting for an ad provider». No invented money prices anywhere.
- 08-buy-insufficient-live.png — **the insufficient-funds refusal, server-authoritative**:
  Buy on Charge (20 000 ◎ against a 0 balance) → the functions emulator executed
  `applyShopPurchase` (docker log: Beginning/Finished in 1323 ms) → the app surfaced
  **«Not enough nolics»**, balance unchanged, nothing purchased. This is the CAP-16
  server-authority check the story asks to see on a device.
- 01..06 (earlier evening) — the same surface on the pre-wipe account (1000 ◎) during the
  broken-transport window: the «1 out of 2 underlying tasks failed» toasts documented there
  were the dead adb-reverse bridge, not a product defect; superseded by this run.

## Status

7.2: the insufficient-funds refusal and the unavailable/locked product states are PROVEN on a
physical device. Remaining open in 7.2: the premium duration-normalization view (the Premium
card renders its Play-unreachable lock here; the normalized comparison sheet is reachable only
once Play pricing/SKUs exist — couples to the owner's 7.1 input) and the
fresh-consent-on-changed-terms case (needs configurable server terms to flip).
