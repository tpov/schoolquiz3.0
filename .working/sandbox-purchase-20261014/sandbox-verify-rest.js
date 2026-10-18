#!/usr/bin/env node
// Вариант scripts/devices/sandbox-verify.js: те же срезы Firestore, но доступ через
// refresh-токен firebase-tools владельца (ключ firebase-adminsdk-h5hhr отозван на стороне
// Google после 12.10 — JWT даёт invalid_grant; см. evidence/sa-key-revoked.txt).
// Бежит на Mac (у владельца), артефакты пишет в каталог прогона на VPS-шаре.
//   node sandbox-verify-rest.js <uid> [uid2 ...]
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');

const PROJECT = 'school-quiz-89336951';
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)`;
const CFG = path.join(os.homedir(), '.config/configstore/firebase-tools.json');
// Публичные OAuth-константы firebase-cli (lib/api.js): constant, не секрет владельца.
const CLIENT_ID = '563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com';
const CLIENT_SECRET = 'j9iVZfS8kkCEFUPaAeJV0sAi';

if (process.argv.length < 3) {
  console.error('использование: node sandbox-verify-rest.js <uid> [uid2 ...]');
  process.exit(2);
}

let accessToken = null;
async function token() {
  if (accessToken) return accessToken;
  const cfg = JSON.parse(fs.readFileSync(CFG, 'utf8'));
  const refresh = cfg.tokens && cfg.tokens.refresh_token;
  if (!refresh) throw new Error(`нет refresh_token в ${CFG}`);
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      refresh_token: refresh,
      grant_type: 'refresh_token',
    }).toString(),
  });
  const data = await res.json();
  if (!data.access_token) throw new Error('token endpoint: ' + JSON.stringify(data));
  accessToken = data.access_token;
  return accessToken;
}

async function api(p, init) {
  const res = await fetch(BASE + p, {
    ...init,
    headers: { Authorization: `Bearer ${await token()}`, 'Content-Type': 'application/json' },
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`${res.status}: ${JSON.stringify(body).slice(0, 300)}`);
  return body;
}

const fields = (doc) => {
  const out = {};
  for (const [k, v] of Object.entries(doc.fields || {})) {
    if (k in v === false) continue;
    if ('timestampValue' in v) out[k] = v.timestampValue;
    else if ('integerValue' in v) out[k] = Number(v.integerValue);
    else if ('doubleValue' in v) out[k] = v.doubleValue;
    else if ('booleanValue' in v) out[k] = v.booleanValue;
    else if ('stringValue' in v) out[k] = v.stringValue;
  }
  return out;
};

async function query(collection, uid) {
  const body = await api('/documents:runQuery', {
    method: 'POST',
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: collection }],
        where: { fieldFilter: { field: { fieldPath: 'uid' }, op: 'EQUAL', value: { stringValue: uid } } },
        limit: 20,
      },
    }),
  });
  return (Array.isArray(body) ? body : []).filter((x) => x.document).map((x) => x.document);
}

function iso(msNum) { return new Date(msNum).toISOString(); }

(async () => {
  for (const uid of process.argv.slice(2)) {
    console.log(`\n================= ${uid} =================`);

    console.log('\n-- users/{uid} (экономика) --');
    let u = null;
    try { u = await api(`/documents/users/${uid}`); }
    catch (e) { console.log('  ДОКУМЕНТА НЕТ — пользователь ещё не читал экономику'); }
    if (u) {
      const d = fields(u);
      for (const k of ['gold', 'goldHearts', 'hasPremium', 'premiumUntilMs', 'premiumGrantId', 'shortfall', 'boxCount', 'updatedAtMs']) {
        if (k in d) console.log(`  ${k}: ${typeof d[k] === 'number' && k.endsWith('Ms') ? `${d[k]} (${iso(d[k])})` : d[k]}`);
      }
      if (d.premiumUntilMs) {
        const left = Math.round((d.premiumUntilMs - Date.now()) / 86400000);
        console.log(`  премия до: ${iso(d.premiumUntilMs)} (осталось ~${left} дн.)`);
      }
    }

    console.log('\n-- purchase_settlements (расчёты покупок) --');
    const s = await query('purchase_settlements', uid);
    if (!s.length) console.log('  пусто');
    s.forEach((doc) => console.log(`  [${doc.name.split('/').pop().slice(0, 12)}…]`, JSON.stringify(fields(doc))));

    console.log('\n-- users/{uid}/receipts (чеки в приложении, story 5.4) --');
    let r = [];
    try {
      const rl = await api(`/documents/users/${uid}/receipts?pageSize=20`);
      r = rl.documents || [];
    } catch (e) { /* 404 = пусто */ }
    if (!r.length) console.log('  пусто');
    r.forEach((doc) => console.log(`  [${doc.name.split('/').pop().slice(0, 16)}…]`, JSON.stringify(fields(doc))));

    console.log('\n-- purchase_notifications (RTDN-поток) --');
    const n = await query('purchase_notifications', uid);
    if (!n.length) console.log('  для этого uid пусто (testNotification uid не несёт — см. полный дамп ниже)');
    n.forEach((doc) => console.log(`  [${doc.name.split('/').pop().slice(0, 16)}…]`, JSON.stringify(fields(doc))));

    console.log('\n-- audit/currency_ledger/entries (золотой журнал) --');
    const lbody = await api('/documents/audit/currency_ledger:runQuery', {
      method: 'POST',
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: 'entries' }],
          where: { fieldFilter: { field: { fieldPath: 'uid' }, op: 'EQUAL', value: { stringValue: uid } } },
          limit: 20,
        },
      }),
    });
    const l = (Array.isArray(lbody) ? lbody : []).filter((x) => x.document).map((x) => x.document);
    if (!l.length) console.log('  пусто');
    l.forEach((doc) => console.log(`  [${doc.name.split('/').pop().slice(0, 16)}…]`, JSON.stringify(fields(doc))));
  }
  console.log('');
})().catch((e) => { console.error('ОШИБКА:', e.message); process.exit(1); });
