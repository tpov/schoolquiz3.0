"use strict";

// Ручной ремонт рейтинговых пинов демо-бэкенда по манифесту (ретро item 4a/4d): та же
// проверка, что в прамбуле live-run драйверов (rating-pins.js), отдельной командой — для
// случая, когда дрейф найден вне прогона. Merge только трёх пиновых полей каждого банка;
// authorUid и прочие поля, как и чужие документы, не трогаются. Идемпотентно: совпадает —
// ничего не пишет, расходится — чинит и громко логирует.
//
// На хосте node нет: запуск в throwaway-контейнере (сеть хоста достаёт эмуляторы на
// 127.0.0.1:18090/19090, репо в /repo):
//   docker run --rm --network host -v "$PWD:/repo:rw" -w /repo \
//     -e GCLOUD_PROJECT=demo-schoolquiz-mvp \
//     -e FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 \
//     -e GOOGLE_APPLICATION_CREDENTIALS= \
//     node:22-bookworm-slim node scripts/demo-fixtures/repair-pin.js

const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const path = require("node:path");

const PROJECT = "demo-schoolquiz-mvp";
const HOST = "127.0.0.1:18090";

assert.equal(process.env.GCLOUD_PROJECT, PROJECT, "GCLOUD_PROJECT must name the demo project");
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, HOST, "firestore must be the demo backend");
assert.equal(process.env.GOOGLE_APPLICATION_CREDENTIALS, undefined, "no credentials: emulator-only tool");

const admin = require(path.join(__dirname, "..", "..", "functions", "node_modules", "firebase-admin"));
const manifest = JSON.parse(readFileSync(path.join(__dirname, "manifest.json"), "utf8"));
assert.equal(manifest.schemaVersion, 2, "unsupported manifest schema");
const {assertOrRepairRatingPins} = require(path.join(__dirname, "rating-pins.js"));

admin.initializeApp({projectId: PROJECT});
const db = admin.firestore();

(async () => {
  const repairs = await assertOrRepairRatingPins(manifest, db);
  console.log(`repair-pin: ${repairs.length} rating document(s) ` +
    `${repairs.length > 0 ? "repaired to the manifest pins" : "matched the manifest pins"}`);
})().catch((error) => {
  console.error("repair-pin failed:", error.message);
  process.exitCode = 1;
});
