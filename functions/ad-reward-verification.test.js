"use strict";

const assert = require("assert/strict");
const crypto = require("node:crypto");
const {
  AD_REWARD_READY,
  AD_REWARD_SIGNATURE_INVALID,
  REASON_BAD_KEY_ID,
  REASON_BAD_TRANSACTION_ID,
  REASON_BAD_USER_ID,
  REASON_DUPLICATE_PARAMETER,
  REASON_MISSING_KEY_ID,
  REASON_MISSING_TRANSACTION_ID,
  REASON_MISSING_USER_ID,
  adRewardCallbackId,
  adRewardCallbackRecord,
  adRewardConfirmationId,
  adRewardConfirmationRecord,
  isValidRewardUid,
  readAdRewardCallback,
  selectVerifierKey,
  verifyAdRewardSignature,
} = require("./ad-reward-verification");

const {privateKey, publicKey} = crypto.generateKeyPairSync("ec", {namedCurve: "prime256v1"});
const PUBLIC_PEM = publicKey.export({type: "spki", format: "pem"});

/** Подписанный callback в форме SSV: параметры в порядке, подпись и key_id — всегда последними. */
function signedCallback({content, key = privateKey, keyId = 101}) {
  const signature = crypto.sign("sha256", Buffer.from(content, "utf8"), key).toString("base64");
  return `${content}&signature=${encodeURIComponent(signature)}&key_id=${keyId}`;
}

const CONTENT = "ad_network=1953547073528090325&ad_unit=ca-app-pub-1/unit&custom_data=perm%20one%2Bplus" +
  "&reward_amount=1&reward_item=coin&timestamp=1700000000000&transaction_id=0123456789abcdef&user_id=box-player";
const TESTS = [];
function test(name, body) { TESTS.push({name, body}); }

test("parsed callback keeps the signed bytes byte for byte and decodes the fields", () => {
  const rawQuery = signedCallback({content: CONTENT});
  const callback = readAdRewardCallback(rawQuery);
  assert.equal(callback.unusableReason, null);
  assert.equal(callback.content, CONTENT);
  const {signature, ...params} = callback.params;
  // Подпись ECDSA рандомизирована каждым вызовом, поэтому сверяется верификацией, а не байтами.
  assert.equal(verifyAdRewardSignature({content: callback.content, signatureBase64: signature, publicKeyPem: PUBLIC_PEM}), true);
  assert.deepEqual(params, {
    adNetwork: "1953547073528090325",
    adUnit: "ca-app-pub-1/unit",
    customData: "perm one+plus",
    keyId: "101",
    rewardAmount: "1",
    rewardItem: "coin",
    timestamp: "1700000000000",
    transactionId: "0123456789abcdef",
    userId: "box-player",
  });
});

test("honest signature verifies against the matching public key", () => {
  const callback = readAdRewardCallback(signedCallback({content: CONTENT}));
  assert.equal(verifyAdRewardSignature({content: callback.content, signatureBase64: callback.params.signature, publicKeyPem: PUBLIC_PEM}), true);
});

test("a tampered reward amount breaks the signature", () => {
  // Подпись сделана над исходным контентом; подмена суммы после подписи её ломает.
  const signature = encodeURIComponent(crypto.sign("sha256", Buffer.from(CONTENT, "utf8"), privateKey).toString("base64"));
  const tampered = (CONTENT.replace("reward_amount=1", "reward_amount=999") +
    `&signature=${signature}&key_id=101`);
  const callback = readAdRewardCallback(tampered);
  assert.equal(callback.unusableReason, null);
  assert.equal(callback.content, CONTENT.replace("reward_amount=1", "reward_amount=999"));
  assert.equal(verifyAdRewardSignature({content: callback.content, signatureBase64: callback.params.signature, publicKeyPem: PUBLIC_PEM}), false);
});

test("wrong key, garbage signature and a missing key are all «not verified»", () => {
  const other = crypto.generateKeyPairSync("ec", {namedCurve: "prime256v1"});
  const callback = readAdRewardCallback(signedCallback({content: CONTENT, key: other.privateKey}));
  assert.equal(verifyAdRewardSignature({content: callback.content, signatureBase64: callback.params.signature, publicKeyPem: PUBLIC_PEM}), false);
  assert.equal(verifyAdRewardSignature({content: CONTENT, signatureBase64: "!!!not base64!!!", publicKeyPem: PUBLIC_PEM}), false);
  assert.equal(verifyAdRewardSignature({content: CONTENT, signatureBase64: "AAAA", publicKeyPem: null}), false);
});

test("a query without a signature has no verifiable content at all", () => {
  assert.equal(readAdRewardCallback(""), null);
  assert.equal(readAdRewardCallback(null), null);
  assert.equal(readAdRewardCallback("a=1&b=2"), null);
  assert.equal(readAdRewardCallback("&signature=QUFB&key_id=1"), null);
  assert.equal(readAdRewardCallback(`${"a=1&".repeat(2100)}signature=QUFB&key_id=1`), null);
});

test("a repeated parameter name makes the callback unparseable", () => {
  const callback = readAdRewardCallback(`${CONTENT}&reward_item=coin&signature=QUFB&key_id=101`);
  assert.equal(callback.unusableReason, REASON_DUPLICATE_PARAMETER);
  assert.equal(callback.params, null);
});

test("broken percent-escaping is an unreadable query, not a crash", () => {
  // decodeURIComponent на «%ZZ» кидает исключение; разбор обязан назвать это нечитаемым.
  assert.doesNotThrow(() => readAdRewardCallback(`${CONTENT}%ZZ&signature=QUFB&key_id=101`));
  assert.equal(readAdRewardCallback(`${CONTENT}%ZZ&signature=QUFB&key_id=101`), null);
  assert.doesNotThrow(() => readAdRewardCallback("x=%E0%A4%A&signature=QUFB&key_id=101"));
});

test("unusable reasons name the first field the decision cannot use", () => {
  const without = (name) => {
    const pairs = CONTENT.split("&").filter((pair) => !pair.startsWith(`${name}=`));
    return signedCallback({content: pairs.join("&")});
  };
  assert.equal(readAdRewardCallback(without("transaction_id")).unusableReason, REASON_MISSING_TRANSACTION_ID);
  assert.equal(readAdRewardCallback(signedCallback({content: CONTENT.replace("transaction_id=0123456789abcdef", "transaction_id=sp<aced!") })).unusableReason, REASON_BAD_TRANSACTION_ID);
  assert.equal(readAdRewardCallback(without("user_id")).unusableReason, REASON_MISSING_USER_ID);
  assert.equal(readAdRewardCallback(signedCallback({content: CONTENT.replace("user_id=box-player", "user_id=a/b/c")})).unusableReason, REASON_BAD_USER_ID);
  // key_id дописывается подписывающим помощником; без него callback не доходит до ключа.
  const withoutKeyId = `${CONTENT}&signature=${encodeURIComponent(crypto.sign("sha256", Buffer.from(CONTENT, "utf8"), privateKey).toString("base64"))}`;
  assert.equal(readAdRewardCallback(withoutKeyId).unusableReason, REASON_MISSING_KEY_ID);
  assert.equal(readAdRewardCallback(signedCallback({content: CONTENT, keyId: "not-a-number"})).unusableReason, REASON_BAD_KEY_ID);
});

test("the parsed callback without a usable signature still parses for the record", () => {
  const callback = readAdRewardCallback(signedCallback({content: CONTENT, keyId: "not-a-number"}));
  assert.equal(callback.unusableReason, REASON_BAD_KEY_ID);
  assert.equal(callback.params.transactionId, "0123456789abcdef");
});

test("verifier key selection picks by key id and refuses a corrupt document", () => {
  const document = {keys: [{keyId: 7, pem: "corrupt"}, {keyId: 101, pem: PUBLIC_PEM}, {keyId: 202, pem: null}]};
  assert.equal(selectVerifierKey(document, 101), PUBLIC_PEM);
  assert.equal(selectVerifierKey(document, 202), null);
  assert.equal(selectVerifierKey(document, 303), null);
  assert.equal(selectVerifierKey(null, 101), null);
  assert.equal(selectVerifierKey({}, 101), null);
});

test("record ids are stable: the same delivery lands on the same row, a different message does not", () => {
  const delivery = signedCallback({content: CONTENT});
  const first = readAdRewardCallback(delivery);
  const second = readAdRewardCallback(delivery);
  const third = readAdRewardCallback(signedCallback({content: CONTENT.replace("reward_item=coin", "reward_item=gems")}));
  assert.equal(adRewardCallbackId(first), adRewardCallbackId(second));
  assert.notEqual(adRewardCallbackId(first), adRewardCallbackId(third));
});

test("callback record keeps the evidence and the verdict", () => {
  const callback = readAdRewardCallback(signedCallback({content: CONTENT}));
  const record = adRewardCallbackRecord({callback, verdict: "FORGED", nowMs: 1700000009999});
  assert.deepEqual(record, {
    schemaVersion: 1,
    id: adRewardCallbackId(callback),
    verdict: "FORGED",
    reason: null,
    transactionId: "0123456789abcdef",
    userId: "box-player",
    keyId: 101,
    adNetwork: "1953547073528090325",
    adUnit: "ca-app-pub-1/unit",
    rewardAmount: "1",
    rewardItem: "coin",
    rewardTimestampMs: 1700000000000,
    receivedAtMs: 1700000009999,
  });
});

test("confirmation record and its document id are both derived from the transaction id", () => {
  const callback = readAdRewardCallback(signedCallback({content: CONTENT}));
  const record = adRewardConfirmationRecord({callback, nowMs: 1700000009999});
  assert.equal(record.transactionId, "0123456789abcdef");
  assert.equal(record.userId, "box-player");
  assert.equal(record.confirmedAtMs, 1700000009999);
  assert.equal(adRewardConfirmationId("0123456789abcdef"),
    crypto.createHash("sha256").update("0123456789abcdef").digest("hex"));
});

test("uid form is the server-wide one: trimmed, no slash, bounded", () => {
  assert.equal(isValidRewardUid("box-player"), true);
  assert.equal(isValidRewardUid(" a "), false);
  assert.equal(isValidRewardUid("a/b"), false);
  assert.equal(isValidRewardUid(""), false);
  assert.equal(isValidRewardUid("x".repeat(129)), false);
});

test("verdict constants are a closed list the wiring answers for", () => {
  assert.equal(AD_REWARD_READY, "CONFIRMED_READY");
  assert.equal(AD_REWARD_SIGNATURE_INVALID, "SIGNATURE_INVALID");
});

async function main() {
  for (const {name, body} of TESTS) { await body(); console.log(`ok - ${name}`); }
  console.log(`ad-reward-verification: ${TESTS.length} behavioral tests passed`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
