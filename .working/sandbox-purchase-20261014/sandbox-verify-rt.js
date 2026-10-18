#!/usr/bin/env node
// Вариант sandbox-verify.js: тот же срез, но credential — refresh-токен firebase-tools
// (ключ firebase-adminsdk-h5hhr отозван на стороне Google после 12.10, JWT -> invalid_grant).
'use strict';
const fs = require('fs');

const FT_API = '/root/.cache/firebase/tools/lib/node_modules/firebase-tools/lib/api.js';
const CFG = '/root/.config/configstore/firebase-tools.json';
const PROJECT_ID = 'school-quiz-89336951';

if (process.argv.length < 3) {
  console.error('использование: node sandbox-verify-rt.js <uid> [uid2 ...]');
  process.exit(2);
}
let admin;
try { admin = require('firebase-admin'); }
catch (e) { console.error('нет firebase-admin: нужен NODE_PATH на functions/node_modules'); process.exit(2); }

const cfg = JSON.parse(fs.readFileSync(CFG, 'utf8'));
const refresh = cfg.tokens && cfg.tokens.refresh_token;
if (!refresh) { console.error('нет refresh_token в ' + CFG); process.exit(2); }
const api = require(FT_API);
admin.initializeApp({
  credential: admin.credential.refreshToken({
    clientId: api.clientId,
    clientSecret: api.clientSecret(),
    refreshToken: refresh,
  }),
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
      const picked = clean({ gold: d.gold, goldHearts: d.goldHearts, hasPremium: d.hasPremium,
        premiumUntilMs: d.premiumUntilMs, premiumGrantId: d.premiumGrantId,
        shortfall: d.shortfall, boxCount: d.boxCount, updatedAtMs: d.updatedAtMs });
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
})().catch((e) => { console.error('ОШИБКА:', e.message); process.exit(1); });
