# GMS/Play supplement to the device inventory (story 1.5) — physical Pixel 10 Pro, 2026-10-18

The 2026-10-09 inventory (inventory-15.txt) recorded the GMS/Play columns as ABSENT on the
redroid rig. On 2026-10-18 the owner connected a physical device; this supplement records those
columns for real. Everything was captured over adb; the APK was hashed and its certificate
printed on the VPS — no project copies were stored on the Mac (owner rule).

- device: Pixel 10 Pro (blazer), serial 56271FDCH00C5S, 1280x2856 @480dpi —
  inventory-pixel.txt [REVISED 2026-10-28: the level line in this README said
  "Android 17 (SDK 36 …)"; the recorded measurement is authoritative and reads
  `ro.build.version.sdk=37` (inventory-pixel.txt:8), fingerprint
  CP3A.260905.009]
- room schema: the room-schema section of inventory-pixel.txt (:31-32) is a
  POINTER TO THE SOURCE ("see shared/core/persistence AppDatabase.kt of HEAD
  bf4b32a7"), not a device-side measurement — the schema was never pulled from
  the device [disclaimer added 2026-10-28, retro F6].
- app: com.tpov.schoolquiz.mvp 4.0.0-isolated (vc40000), installed 2026-10-18 14:23, updated
  17:37; base.apk sha256 79859f5d665f73046810731f1d237b32e69a28411d06934bc164bf9286e83cda
- signature: Signer #1 SHA-256 0033e7fa2c7e...6c47d3 (signing-cert.txt) — the same candidate
  debug certificate as recorded in story 1.2 (00:33:E7:FA:...:47:D3). The build is NEWER than
  the identified candidate (post epic-5/6 merge HEAD bf4b32a7); the candidate identification
  re-run remains a separate rig story — this supplement does not claim it.
- GMS: com.google.android.gms 26.39.33 (260400-995470952), GSF 17, Play Store
  com.android.vending 53.5.48-34; 9 google accounts registered on the device, incl.
  oleg12794@gmail.com and tpovdevelopers@gmail.com
- consequence: Play Integrity App Check and Play Billing are physically reachable on this
  device; concrete SKUs / money prices / license-tester configuration remain the owner's
  external input (story 7.1 stays blocked on Play Console data, not on the device).
- role: device C (ordinary player) for the multi-device runbook rows; demo backend only,
  reached via adb reverse 9099/8080/5001 over an SSH tunnel to the VPS containers.
