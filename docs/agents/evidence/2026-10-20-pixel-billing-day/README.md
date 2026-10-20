# 7.6 Pixel day: BillingClient is ALIVE; the buy path is proven up to Play; finger-test pending — 2026-10-20

Owner provided the keystore password → the first production release build with today fixes
was assembled (release needed 3 empty consumer-rules.pro stubs — tournament/exam/minigame —
release never ran before, AGP merge failed on the missing files).

## Facts established today, in order

1. **Locally signed release cannot be used for billing on-device**: `adb install -r` over the
   Play-installed app failed (INSTALL_FAILED_UPDATE_INCOMPATIBLE — the device copy is signed
   by Google Play App Signing). A fresh adb install of the upload-key build launches fine and
   the bounded-wait auth fix works, and the Buy tap DID reach Play — the system answered
   «This version of the application is not configured for billing through Google Play»
   (76-fresh-buy.png): launchBillingFlow fired, BillingClient connection works after the
   owner reboot. But an upload-key adb build is not a billing-eligible install — dead end
   for 7.6, as designed by Play.
2. **The Play-signed build was restored**: uninstalled the adb copy, installed from the Play
   page (installerPackageName=com.android.vending, versionCode 40000). Anonymous auth lands
   on this build (the EVENTS tab no longer says «Authenticated uid is required»,
   76-play-events.png — the startup race does not always lose).
3. **Injected taps on the Play build do not reach the Buy button**: repeated precise taps
   (uiautomator bounds, fresh process, immediately after entering SHOP) produce no billing
   logs and no UI change — while TAB taps in the same screen work (SHOP/LOCAL switching) and
   the Charge (server-side, nolics-priced) Buy is equally silent (76-charge.png). So the
   silence is NOT a billing failure — the click never starts the purchase flow.
4. The morning precedent stands: the ONE tap that ever got a Play answer was on the adb
   build; on the Play build every injected Buy tap is silent. This is exactly the
   «injected-tap vs finger» split the morning session asked the owner to test.

## What the owner does next (2 minutes, phone in hand)

Open School Quiz → SHOP → tap **Buy on Gold Pack Small with a FINGER**:
- purchase sheet opens → complete the license-tester purchase → the 7.6 matrix continues
  (gold credited → console refund → clawback);
- still nothing → the silent-Buy defect is real in the shipped 16.10 build, and the fix is
  the new production build (the same release APK that was built today already contains the
  auth fix; it needs Play Console upload by the owner — the upload key signs it, the
  keystore password is confirmed working: assembleRelease BUILD SUCCESSFUL).

Device state left clean: the Play-signed 40000 build installed, test app data fresh.
