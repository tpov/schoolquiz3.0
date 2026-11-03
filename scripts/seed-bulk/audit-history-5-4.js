'use strict';
// Audit helper for theme history 5-4 (unique name to avoid clashing with parallel agents).
// Modes: dump -> snap-history-5-4.json; check -> dup/ord/pos/length report.

const fs = require('fs');
const path = require('path');

const LESSONS = [1, 2, 3, 4, 5].map((l) => {
  const arr = require(path.join(__dirname, 'data/school/history/lessons', `5-4-${l}.js`));
  return { l, qs: arr.map((q, i) => ({ ...q, _i: i, _l: l })) };
});

const ALL = LESSONS.flatMap((L) => L.qs.map((q) => ({ ...q, _l: L.l })));

const norm = (s) => String(s || '')
  .toLowerCase()
  .replace(/ё/g, 'е')
  .replace(/«|»|“|”|"|'|\(|\)|,|\.|;|:|—|–|-|!|\?/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const RU_SUFFIX = /(ами|ями|ого|его|ому|ему|ыми|ими|ого|ая|яя|ое|ее|ые|ие|ую|юю|ой|ей|ах|ях|ам|ям|ов|ев|ут|ют|ат|ят|ит|ет|ла|ло|ли|лся|лась|сь|ся|а|я|ы|и|у|ю|е|ь)$/;
function stem(s) {
  return norm(s).split(' ').map((w) => (w.length > 5 ? w.slice(0, Math.max(5, w.length - 3)) : w));
}
function tokenSet(s) { return new Set(stem(s)); }
function jac(a, b) {
  const A = tokenSet(a), B = tokenSet(b);
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  const uni = new Set([...A, ...B]).size;
  return uni ? inter / uni : 0;
}

function optsOf(q) {
  if (q.options) return q.options.map((o) => o.text);
  if (q.candidates) return q.candidates.map((o) => o.text);
  return [];
}
function keyTextsOf(q) {
  if (q.type === 'SingleChoice') {
    const k = q.options.find((o) => o.id === q.correctOptionId);
    return k ? [k.text] : [];
  }
  if (q.type === 'MultipleChoice') return q.options.filter((o) => q.correctOptionIds.includes(o.id)).map((o) => o.text);
  if (q.type === 'FillBlank') {
    const byId = new Map(q.candidates.map((c) => [c.id, c.text]));
    return q.blanks.map((b) => byId.get(b.correctCandidateId)).filter(Boolean);
  }
  return [];
}

const label = (q) => `L${q._l}#${String(q._i).padStart(2, '0')}${q.type === 'SingleChoice' ? 'sc' : q.type === 'MultipleChoice' ? 'mc' : q.type === 'Ordering' ? 'ord' : 'fb'}`;

const mode = process.argv[2] || 'check';

if (mode === 'dump') {
  fs.writeFileSync(path.join(__dirname, 'snap-history-5-4.json'), JSON.stringify(LESSONS, null, 1));
  console.log('snapshot written: snap-history-5-4.json, questions:', ALL.length);
  process.exit(0);
}

// ---------- 1. dup candidates (stems + options), sc/fb/mc pairs ----------
console.log('== DUP CANDIDATES (same lesson first) ==');
const TEXTQ = ALL.filter((q) => q.type !== 'Ordering');
for (let i = 0; i < TEXTQ.length; i++) {
  for (let j = i + 1; j < TEXTQ.length; j++) {
    const a = TEXTQ[i], b = TEXTQ[j];
    const sameLesson = a._l === b._l;
    const ss = jac(a.text, b.text);
    if (ss < 0.42) continue;
    const oa = optsOf(a).join(' | '), ob = optsOf(b).join(' | ');
    const os = jac(oa, ob);
    const ka = keyTextsOf(a).join(' '), kb = keyTextsOf(b).join(' ');
    const ks = jac(ka, kb);
    const flag = sameLesson && ((ss >= 0.5 && (ks >= 0.5 || os >= 0.6)) || ss >= 0.72 || (ss >= 0.45 && ks >= 0.7));
    if (flag) {
      console.log(`${flag ? 'DUP?' : '   '} ${label(a)} ~ ${label(b)} stem=${ss.toFixed(2)} opts=${os.toFixed(2)} key=${ks.toFixed(2)} sameLesson=${sameLesson}`);
      console.log(`      A: ${a.text}`);
      console.log(`      B: ${b.text}`);
    }
  }
}

// exact-ish duplicate text anywhere in theme
console.log('== EXACT TEXT DUP ACROSS THEME ==');
const seen = new Map();
for (const q of ALL) {
  const k = norm(q.text) + '||' + optsOf(q).map(norm).sort().join('|');
  if (seen.has(k)) console.log('EXACT-DUP', label(seen.get(k)), '~', label(q));
  else seen.set(k, q);
}

// ---------- 2. ord overlap matrix ----------
console.log('== ORD PAIR OVERLAP (>=50% of smaller set) ==');
const ORDS = ALL.filter((q) => q.type === 'Ordering');
const normItem = (s) => norm(s).replace(/\s+/g, ' ');
for (let i = 0; i < ORDS.length; i++) {
  for (let j = i + 1; j < ORDS.length; j++) {
    const A = ORDS[i], B = ORDS[j];
    const ia = A.items.map((x) => normItem(x.text));
    const ib = B.items.map((x) => normItem(x.text));
    let shared = [];
    for (const x of ia) {
      for (const y of ib) {
        if (x === y || (x.includes(y) && y.length >= 18) || (y.includes(x) && x.length >= 18) || jac(x, y) >= 0.75) { shared.push(x); break; }
      }
    }
    const minLen = Math.min(ia.length, ib.length);
    const frac = shared.length / minLen;
    if (frac >= 0.5) {
      console.log(`${frac >= 0.75 ? 'BAD ' : 'warn'} ${label(A)} ~ ${label(B)} shared ${shared.length}/${minLen} (${(frac * 100) | 0}%)`);
      shared.forEach((s) => console.log('       = ' + s));
    }
  }
}

// ---------- 3. positional references (Cyrillic-safe) ----------
console.log('== POSITIONAL REFERENCES ==');
const POS_RE = new RegExp('(перв|втор|трет|четв|пят|последн|остальн|предпоследн|последующ|начальн|заключит)[а-яё]*\\s*[а-яё]*\\s*(вариант|пункт|утверждени|шаг|строк|позиции|колонк|событи|из перечисленн|из названн|списке)|соответственно|(вариант|пункт|шаг)\\s+[абвгде]\\b', 'i');
for (const q of ALL) {
  const fields = [q.info, ...optsOf(q)];
  fields.forEach((f, k) => {
    if (f && POS_RE.test(f)) console.log(`POS? ${label(q)} field=${k === 0 ? 'info' : 'option' + k}: ${f.slice(0, 120)}`);
  });
}

// ---------- 4. key length vs distractors (sc/fb) ----------
console.log('== KEY LENGTH / BRACKET HINTS (sc, ratio>=1.5) ==');
for (const q of ALL) {
  if (q.type !== 'SingleChoice' && q.type !== 'FillBlank') continue;
  const opts = optsOf(q);
  let keys, dist;
  if (q.type === 'SingleChoice') {
    keys = opts.filter((o) => o.id === q.correctOptionId);
    dist = opts.filter((o) => o.id !== q.correctOptionId);
  } else {
    const keyIds = new Set(q.blanks.map((b) => b.correctCandidateId));
    keys = q.candidates.filter((c) => keyIds.has(c.id));
    dist = q.candidates.filter((c) => !keyIds.has(c.id));
  }
  const klen = Math.max(...keys.map((k) => k.length));
  const dmax = Math.max(...dist.map((d) => d.length));
  const ratio = dmax ? klen / dmax : 99;
  const hint = keys.some((k) => /[(（]\s*[а-яёa-z]/i.test(k)) && !dist.every((d) => /[(（]/.test(d));
  if (ratio >= 1.5 || hint) {
    console.log(`KEYLEN ${label(q)} ratio=${ratio.toFixed(2)} hint=${hint}`);
    keys.forEach((k) => console.log(`   KEY ${k.length}: ${k}`));
    dist.forEach((d) => console.log(`   dis ${d.length}: ${d}`));
  }
}

// ---------- 5. mc suspicious duplicate options ----------
console.log('== MC OPTION SELF-SIMILARITY ==');
for (const q of ALL) {
  if (q.type !== 'MultipleChoice') continue;
  const os = q.options;
  for (let i = 0; i < os.length; i++) for (let j = i + 1; j < os.length; j++) {
    const s = jac(os[i].text, os[j].text);
    if (s >= 0.6) console.log(`MCOPT ${label(q)} sim=${s.toFixed(2)} "${os[i].text}" ~ "${os[j].text}"`);
  }
}

// ---------- 6. ord chronology sanity: years must be non-decreasing as written ----------
console.log('== ORD YEARS AS WRITTEN (stored order) ==');
for (const q of ORDS) {
  const years = q.items.map((it) => {
    const m = String(it.text).match(/(\d{3,4})\s*(?:г[г]?\.|\))?/g);
    const nums = (String(it.text).match(/\d{3,4}/g) || []).map(Number).filter((n) => n > 1000 && n < 2000);
    return nums.length ? nums[0] : null;
  });
  const yearsAll = q.items.map((it) => (String(it.text).match(/\d{4}/g) || []).map(Number));
  let ok = true;
  for (let i = 1; i < q.items.length; i++) {
    const prev = Math.max(...(yearsAll[i - 1].length ? yearsAll[i - 1] : [0]));
    const cur = Math.max(...(yearsAll[i].length ? yearsAll[i] : [0]));
    if (prev > cur) { ok = false; console.log(`ORDSEQ? ${label(q)} item ${i - 1}->${i}: ${q.items[i - 1].text} || ${q.items[i].text}`); }
  }
}
console.log('== DONE ==');
