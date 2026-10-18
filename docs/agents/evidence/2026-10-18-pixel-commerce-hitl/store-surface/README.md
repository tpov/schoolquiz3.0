# Store surface device proof — Pixel 10 Pro, partial — 2026-10-18 evening

Story: epic-commerce 7.2 `store-gold-prices-and-unavailable-products` (device-proof half).
Device: Pixel 10 Pro (56271FDCH00C5S), com.tpov.schoolquiz.mvp 4.0.0-isolated
(base.apk sha256 79859f5d…, candidate debug cert 00:33:E7:FA…), demo backend via
adb reverse 9099/8080/5001 over an SSH tunnel to the VPS emulators.

## What the run SHOWS (screenshots in this directory)

- 01-shop-tab.png — the store surface renders server-owned gold prices, no invented money
  prices: Charge 20 000 ◎ («You have 5 · next 20 000»), Golden charge 2 gold («hard mode
  only»), Quiz slot 1 000 ◎; wallet chips in the header (1000 ◎ / 0 gold).
- Unconfigured / Play-dependent products are VISIBLY unavailable, never silently purchasable:
  Premium and Small/Medium/Large gold packs are locked with the explicit reason
  «Google Play is unreachable — prices …»; Quiz slot — «Waiting for quiz limit modelling…»;
  Box for watching 5 ads — «Waiting for an ad provider». (On this device GMS/Play ARE present —
  see 2026-10-18-pixel-gms-inventory — the lock reflects the store data/billing state, which is
  exactly the state the story asks to see.)
- 02/03/04/05/06 — Buy attempts on Charge. Observed repeatedly: the app surfaces
  «1 out of 2 underlying tasks failed» (the callable's AppCheck+auth whenAll error string) and
  the balance never changed — no charge was made, no partial state. NOTE: the insufficient-funds
  refusal path was NOT reached; see the environment section.

## Environment caveats (honest record)

- The functions backend itself was verified healthy through the same tunnel from the Mac:
  anonymous signUp on the auth emulator (9099) returned a real idToken, and applyShopPurchase
  answered 400 INVALID_ARGUMENT «Unsupported shop item » for a valid Bearer token — the
  callable receiver executes server logic end-to-end when the transport works.
- During this run the phone's USB link bounced (device re-enumerated ~19:45 local); after that
  adb reverse accepted TCP connections but stopped delivering DATA (verified with an
  independent Mac-side HTTP listener; adb kill-server + re-registration did not restore it).
  The app's long-lived Firestore/Auth sockets from 19:36 kept working (catalog/balance
  rendered), which is why the surface looks live while new callables failed. The Buy taps hit
  this broken window; the «1 out of 2 underlying tasks failed» toast is consistent with the
  callable transport being dead, NOT with a product defect — on the redroid rig this path was
  proven earlier (evidence 2026-10-09-hitl-rig-matrix and commerce server stories).
- A CrypticoBot accessibility service on the phone intermittently held the accessibility tree,
  breaking uiautomator dumps (driving fell back to screenshot-verified coordinates; every tap
  was verified to land on schoolquiz — the drawer/menu reacted as expected).

## Status

7.2 stays blocked. Proven here: the store surface states (server prices, visibly unavailable
products). Still needed for 7.2: the interactive insufficient-funds refusal on a healthy
transport, premium duration-normalization view (the Premium card is behind the Play lock in
this build), and fresh-consent-on-changed-terms — all ride on restoring a working
device↔backend path (adb reverse or another relay).
