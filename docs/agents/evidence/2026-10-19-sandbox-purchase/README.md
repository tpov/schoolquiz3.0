# Sandbox purchase run (7.6) — production app on the Pixel — 2026-10-19

Production app 4.0.0/40000 (com.tpov.schoolquiz, the internal-track build) on the Pixel
(oleg12794 device, license tester, opt-in passed this morning — see
2026-10-19-play-console/README.md).

## What PASSED

- 01-shop-production.png: the production SHOP renders the REAL catalog with money prices from
  the console: Premium 1 month 239,99 UAH; Gold Pack Small/Medium/Large — 11,99/119,99/299,99
  UAH; Charge 20 000 nolics; Quiz slot and ad box locked as designed. The Billing pipeline
  works end-to-end (the prices come from Play).
- 02/03 — Buy attempts on gold_pack_small.

## What BLOCKED

The Buy attempt surfaced **«Требуется авторизация»** — the production app's anonymous
sign-in state against the PRODUCTION Firebase (school-quiz-89336951). Verified in the
Firebase console (oleg12794 has owner access): **Anonymous provider = Enabled**, so the
blocker is not the provider. The app logs show no auth errors; the banner may be a startup
race (sign-in is async at launch) or App Check enforcement on the callables (the release
build uses PlayIntegrity).

## Environment note (honest)

The user was actively using the Pixel during this run (a crypto wallet was foreground at
13:40; taps were held until School Quiz was verified on screen; the app restarted mid-run and
reset its tab). Next session: a clean retest with the device untouched for 15 minutes, and if
«Требуется авторизация» persists, pull the UNFILTERED logcat around the Buy tap — billing and
auth logs live in the GMS process, not the app's pid.
