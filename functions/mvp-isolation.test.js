"use strict";

const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {join} = require("node:path");
const test = require("node:test");
const root = join(__dirname, "..");
const read = (path) => readFileSync(join(root, path), "utf8");
const app = "apps/android-next";

test("MVP fixture is a demo-only separate application, not a real Firebase project credential", () => {
  const config = JSON.parse(read(`${app}/src/mvp/google-services.json`));
  assert.equal(config.project_info.project_id, "demo-schoolquiz-mvp");
  assert.equal(config.client[0].client_info.android_client_info.package_name, "com.tpov.schoolquiz.mvp");
  assert.match(config.client[0].api_key[0].current_key, /DemoOnly/);
  const gradle = read(`${app}/build.gradle.kts`);
  assert.match(gradle, /buildConfigField\("boolean", "MVP_EMULATORS", "false"\)/);
  assert.match(gradle, /create\("mvp"\)[\s\S]*applicationIdSuffix = "\.mvp"/);
});

test("demo configuration is checked before remote initialization and live telemetry/store starters are gated", () => {
  const initializer = read("platform/firebase/src/main/kotlin/com/tpov/schoolquiz/platform/firebase/FirebaseInitializer.kt");
  const guard = initializer.indexOf('require(firebaseApp.options.projectId == "demo-schoolquiz-mvp")');
  assert.ok(guard > 0 && guard < initializer.indexOf("FirebaseAuth.getInstance(firebaseApp).useEmulator"));
  for (const service of ["FirebaseAuth", "FirebaseFirestore", "FirebaseFunctions", "FirebaseDatabase", "FirebaseStorage"]) {
    assert.match(initializer, new RegExp(`${service}\\.getInstance\\(firebaseApp\\)\\.useEmulator\\(host,`));
  }
  const source = read(`${app}/src/main/java/com/tpov/schoolquiz/apps/android_next/AppApplication.kt`);
  assert.ok(source.indexOf("initializeFirebaseSecurity(") < source.indexOf("auth.signInAnonymously()"));
  assert.match(source, /useEmulators = BuildConfig.MVP_EMULATORS/);
  assert.match(source, /if \(!BuildConfig.MVP_EMULATORS\) \{\s*startMeasurement\(\)\s*registerDeviceToken\(\)\s*startPurchaseSettlement\(\)/);
  assert.match(source, /if \(BuildConfig.MVP_EMULATORS\) isolatedMvpBillingModule else billingModule/);
  assert.match(source, /if \(BuildConfig.MVP_EMULATORS\) isolatedAnalyticsModule else analyticsModule/);
});

test("cleartext is restricted to local emulator loopback and automatic telemetry is disabled in MVP manifest", () => {
  const network = read(`${app}/src/mvp/res/xml/mvp_network_security_config.xml`);
  assert.match(network, /base-config cleartextTrafficPermitted="false"/);
  const domains = [...network.matchAll(/<domain[^>]*>([^<]+)<\/domain>/g)].map((m) => m[1]);
  assert.deepEqual(domains, ["127.0.0.1", "localhost"]);
  const manifest = read(`${app}/src/mvp/AndroidManifest.xml`);
  assert.match(manifest, /firebase_analytics_collection_deactivated" android:value="true"/);
  assert.match(manifest, /firebase_crashlytics_collection_enabled" android:value="false"/);
  assert.match(manifest, /firebase_messaging_auto_init_enabled" android:value="false"/);
});
