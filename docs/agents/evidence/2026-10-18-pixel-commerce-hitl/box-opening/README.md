# Box opening survives force-stop mid-opening — Pixel 10 Pro — 2026-10-18 evening

Story: epic-commerce 7.5 `box-opening-loss-restart` (device-proof half).
Device/account: Pixel 10 Pro over Wi-Fi adb, fresh anonymous account UserG9FY6E
(uid VLqWYMHXqyI8pPWQdNXdkQg9FY6E), app 4.0.0-isolated APK sha256 d0c59bfc… (the
build-configurable emulator host build, see store-surface/README.md transport section).

## Fixture (honest note)

The account was granted 1 box by a direct admin write to `users/<uid>.boxCount = 1`
(firestore Admin SDK against the demo emulator from the functions container — the rules
rightly forbid client writes to users/). The first attempt wrote a wrong field name
(`boxes`) — inert, deleted; the server field is `boxCount` (see economy-handler.test.js).
The grant only seeds the balance; everything below runs the real client/server machinery.

## The run (AD-19 loss/restart case)

1. 01-box-screen.png — gift FAB (badge 1) tapped → dialog «Gift box — Opening… — The request
   has already been sent to the server»: the OPEN request left the client and reached the
   backend while the UI was still showing the spinner.
2. At that exact moment the app was force-stopped (`am force-stop`) — process death with the
   result in flight.
3. 02-after-forcestop-relaunch.png — after relaunch the SAME dialog returned with its FIXED
   result, not a re-roll: «Reward credited to your profile — 1159 nolics — Boxes left: 0».
4. 04-server-state.json — backend truth: exactly one box_operation
   (d01544cb…, kind OPEN, status OPENED, fingerprint ff4b58b9…, openedBoxes 1), exactly one
   receipt (box_ee26bb59…, BOX_OPENED, reward addNolics 1159, currency BOXES −1),
   `users/<uid>` boxCount=0, nolics=1159. One result, one receipt, balance moved once.
5. 03-after-claim.png — the Claim tap closed the flow (the device returned to the launcher
   right after, so the shot may show the home screen; the authoritative state is the JSON).

## Status

7.5: the loss/restart mechanics are PROVEN on a physical device — one result/receipt fixed
before display, replay returns it, no re-roll, box consumed exactly once. The box balance
itself was fixture-granted; the gift-source journeys (streak grant, premium daily gift) belong
to 7.4 and remain there. Note for 7.4 fixtures: the server profile fields are
`boxCount` / `boxStreakDays` / `nextBoxAtMs` / `premiumUntilMs` on `users/<uid>` (a `boxes`
field is ignored by the client sync).
