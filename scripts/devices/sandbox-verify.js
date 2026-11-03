#!/usr/bin/env node
// Серверная сторона sandbox-покупки: что прод записал в Firestore после
// покупки/возврата. Запускается там, где лежит ключ сервис-аккаунта
// (по умолчанию — Mac владельца), НЕ на риге: правила Firestore для
// purchase_* клиенту запрещают чтение, админский SDK их обходит.
//
//   node sandbox-verify.js <uid> [uid2 ...]
//   ADMIN_SDK_JSON=/path/to/school-quiz-89336951-firebase-adminsdk-*.json \
//     node sandbox-verify.js YMQZ9uRlqVeIFadJgGRLQQFMyMm1
//
// uid берётся из scripts/devices/uid.sh (расшифрованный Firebase-uid
// приложения с устройства) — см. sandbox-purchase.sh.
//
// Ожидания после шагов из sandbox-purchase.sh:
//   premium_30d (триал trial-3d):  users/{uid}.premiumUntilMs ≈ now+3д,
//     premiumGrantId, receipts-строка; settlement premium_30d SETTLED.
//   gold_pack_small: users/{uid}.gold = было+10; settlement SETTLED;
//     receipt; строка в audit/currency_ledger/entries.
//   refund gold-пака: settlement → REFUNDED, gold снят (долг → shortfall);
//   refund подписки: СЕЙЧАС паркуется (см. functions/purchase-clawback.js:
//     "subscription refund parked") — запись в purchase_notifications с
//     result RECORDED и pending-fence на токен, премия остаётся.
'use strict';

const path = require('path');
const fs = require('fs');

const PROJECT_ID = 'school-quiz-89336951';
const DEFAULT_KEY = '/etc/tpov/schoolquiz/sa-firebase-adminsdk-h5hhr-d6fcf09c340e.json';
const KEY = process.env.ADMIN_SDK_JSON || DEFAULT_KEY;

if (process.argv.length < 3) {
  console.error('использование: node sandbox-verify.js <uid> [uid2 ...]');
  process.exit(2);
}
if (!fs.existsSync(KEY)) {
  console.error(`ключ не найден: ${KEY}\n` +
    'положи сервис-аккаунт проекта и укажи путь: ADMIN_SDK_JSON=<файл> ' +
    '(Play Console → Настройка → Доступ к API → сервисный аккаунт Firebase)');
  process.exit(2);
}

let admin;
try {
  admin = require('firebase-admin');
} catch (e) {
  console.error('нет firebase-admin: выполни `npm install` в scripts/ этого репозитория');
  process.exit(2);
}

admin.initializeApp({
  credential: admin.credential.cert(require(path.resolve(KEY))),
  projectId: PROJECT_ID,
});
const db = admin.firestore();

const ms = (v) => (v && v.toMillis ? v.toMillis() : v);
const clean = (o) => {
  if (!o || typeof o !== 'object') return o;
  const out = {};
  for (const [k, v] of Object.entries(o)) {
    if (v && v.toMillis) out[k] = new Date(v.toMillis()).toISOString();
    else if (typeof v === 'number' || typeof v === 'string' || typeof v === 'boolean' || v == null) out[k] = v;
  }
  return out;
};

(async () => {
  for (const uid of process.argv.slice(2)) {
    console.log(`\n================= ${uid} =================`);

    const user = await db.collection('users').doc(uid).get();
    console.log('\n-- users/{uid} (экономика) --');
    if (!user.exists) console.log('  ДОКУМЕНТА НЕТ — пользователь ещё не читал экономику');
    else {
      const d = user.data();
      const picked = clean({
        gold: d.gold, goldHearts: d.goldHearts,
        hasPremium: d.hasPremium, premiumUntilMs: d.premiumUntilMs, premiumGrantId: d.premiumGrantId,
        shortfall: d.shortfall, boxCount: d.boxCount, updatedAtMs: d.updatedAtMs,
      });
      for (const [k, v] of Object.entries(picked)) console.log(`  ${k}: ${v}`);
      if (picked.premiumUntilMs) {
        const left = Math.round((ms(d.premiumUntilMs) - Date.now()) / 86400000);
        console.log(`  премия до: ${new Date(ms(d.premiumUntilMs)).toISOString()} (осталось ~${left} дн.)`);
      }
    }

    console.log('\n-- purchase_settlements (расчёты покупок) --');
    const s = await db.collection('purchase_settlements').where('uid', '==', uid).limit(20).get();
    if (s.empty) console.log('  пусто');
    s.forEach((doc) => console.log(`  [${doc.id.slice(0, 12)}…]`, JSON.stringify(clean(doc.data()))));

    console.log('\n-- users/{uid}/receipts (чеки в приложении, story 5.4) --');
    const r = await db.collection('users').doc(uid).collection('receipts').limit(20).get();
    if (r.empty) console.log('  пусто');
    r.forEach((doc) => console.log(`  [${doc.id.slice(0, 16)}…]`, JSON.stringify(clean(doc.data()))));

    console.log('\n-- purchase_notifications (RTDN-поток) --');
    const n = await db.collection('purchase_notifications').where('uid', '==', uid).limit(20).get();
    if (n.empty) console.log('  для этого uid пусто (testNotification uid не несёт — см. полный дамп ниже)');
    n.forEach((doc) => console.log(`  [${doc.id.slice(0, 16)}…]`, JSON.stringify(clean(doc.data()))));

    console.log('\n-- audit/currency_ledger/entries (золотой журнал) --');
    const l = await db.collection('audit').doc('currency_ledger')
      .collection('entries').where('uid', '==', uid).limit(20).get();
    if (l.empty) console.log('  пусто');
    l.forEach((doc) => console.log(`  [${doc.id.slice(0, 16)}…]`, JSON.stringify(clean(doc.data()))));
  }
  console.log('');
})().catch((e) => {
  console.error('ОШИБКА:', e.message);
  process.exit(1);
});
