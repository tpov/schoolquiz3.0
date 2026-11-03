'use strict';
// Ревизор темы history 1-4. Уникальные имена: audit-history-1-4.js / snap-history-1-4.json
const path = require('path');
const fs = require('fs');

const base = path.join(__dirname, 'data', 'school', 'history', 'lessons');
const lessons = [1, 2, 3, 4, 5].map((l) => {
  const arr = require(path.join(base, `1-4-${l}.js`));
  return arr.map((q, i) => ({ ...q, _lesson: l, _idx: i, _tag: `1-4-${l} q${i}` }));
});
const all = [].concat(...lessons);

// ---------- нормализация: русские основы без дат и стоп-слов ----------
const STOP = new Set(['какой','какая','какие','какого','какую','что','где','кто','когда','почему','зачем',
  'это','этот','эта','эти','весь','вся','все','всех','всем','свой','своя','свои','такой','такие',
  'для','что','или','него','нее','них','них','быть','был','была','были','будет','него','им','их','его',
  'год','года','годы','гг','г','нэ','н','э','до','наж','век','века','веков','ок','около','примерно',
  'было','быть','между','также','называли','называется','называют','называлась','назывался','считается',
  'стал','стала','стало','стали','произошло','произошла','произошли','относится','относятся','верно','верны']);

function stemRu(w) {
  // грубая русская основа: срезаем окончания
  return w
    .replace(/(иями|ями|ами|иях|ях|ах|ов|ев|ей|ой|ая|ое|ые|ый|ий|ья|ье|ью|ю|я|а|о|е|ы|и|у|ь)$/,'')
    .replace(/(ова|ева|ировать|ирова)$/,'');
}
function tokenize(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/[«»"'(),.;:!?—–-]/g, ' ')
    .replace(/\d+/g, ' ')
    .replace(/\s*(до н|н э|гг?|г)\s*/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length >= 4 && !STOP.has(w))
    .map(stemRu)
    .filter((w) => w.length >= 3);
}
function jac(a, b) {
  const A = new Set(a), B = new Set(b);
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
}
const normText = (x) => String(x || '').toLowerCase().replace(/\s+/g, ' ').replace(/[«»".,();:!?]/g, '').trim();

// ---------- 1. fb vs sc/mc: дубли стема + ключа ----------
const REPORT = { fbDup: [], ordOverlap: [], positional: [], keyLength: [], mcSecondTrue: [] };
const scmc = all.filter((q) => q.type === 'SingleChoice' || q.type === 'MultipleChoice');
const fbs = all.filter((q) => q.type === 'FillBlank');
for (const f of fbs) {
  const fStem = tokenize(f.text.replace(/___/g, ' '));
  const fOpts = (f.candidates || []).map((c) => normText(c.text));
  const fKeys = (f.blanks || []).map((b) => (f.candidates || []).find((c) => c.id === b.correctCandidateId)).filter(Boolean).map((c) => normText(c.text));
  for (const s of scmc) {
    if (s._lesson !== f._lesson) continue; // в рамках урока (координатор: того же урока)
    const sStem = tokenize(s.text);
    const stemJ = jac(fStem, sStem);
    const sOpts = (s.options || []).map((o) => normText(o.text));
    // совпадение ключей (подстрока/токены)
    let keyHit = false;
    for (const k of fKeys) {
      if (sOpts.some((o) => o === k)) keyHit = true;
      const kt = tokenize(k);
      for (const o of sOpts) { const ot = tokenize(o); if (kt.length && ot.length && jac(kt, ot) >= 0.7) keyHit = true; }
    }
    const optJ = jac(fOpts, sOpts);
    if ((stemJ >= 0.5 && keyHit) || (stemJ >= 0.34 && keyHit && optJ >= 0.25) || optJ >= 0.5) {
      REPORT.fbDup.push({ fb: f._tag, fbText: f.text, fbKey: fKeys.join(','), sc: s._tag, scText: s.text, stemJ: +stemJ.toFixed(2), optJ: +optJ.toFixed(2), keyHit });
    }
  }
}

// ---------- 2. ord: попарные пересечения пунктов ----------
const ords = all.filter((q) => q.type === 'Ordering');
const itemKey = (t) => {
  const n = normText(t).replace(/\d+/g, '').replace(/\s+/g, ' ').trim();
  return n;
};
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    const A = ords[i], B = ords[j];
    if (A._lesson !== B._lesson) continue;
    const a = A.items.map((x) => itemKey(x.text)), b = B.items.map((x) => itemKey(x.text));
    const aSet = new Set(a);
    let shared = 0; const sharedList = [];
    for (const x of b) if (aSet.has(x)) { shared++; sharedList.push(x); }
    const minLen = Math.min(a.length, b.length);
    const verbatimShare = shared / minLen;
    // смысловая доля: по токенам
    let meanShared = 0;
    for (const x of b) {
      const xt = new Set(tokenize(x));
      if (!xt.size) continue;
      let best = 0;
      for (const y of a) { const j = jac([...xt], tokenize(y)); if (j > best) best = j; }
      if (best >= 0.6) meanShared++;
    }
    const meanShare = meanShared / minLen;
    if (verbatimShare >= 0.5 || meanShare >= 0.5) {
      REPORT.ordOverlap.push({ a: A._tag, b: B._tag, verbatim: +verbatimShare.toFixed(2), mean: +meanShare.toFixed(2), sharedList });
    }
  }
}

// ---------- 3. позиционные ссылки (кириллица, без \b) ----------
const POS = new RegExp('(?:перв|втор|трет|четвёрт|четверт|пят|шест|седьм|последн|предпоследн|остальн|оставш)[а-яё]*\\s+(?:ваши|вариант|пункт|шаг|утверждени|запис|строк|случа|объект|элемент|позици|ответ|формул|уравнени|список|половин|часть|строчк)[а-яё]*', 'i');
const POS2 = /соответственно|вышеуказанн|нижеперечисленн|вариант[аеу]?\s*[«"?']?[абвгдеabcdef][»"?']?(?![а-яё])|пункт[аеу]?\s*[«"?']?[абвгдеabcdef][»"?']?(?![а-яё])/i;
const POS3 = /(?:первые|последние|два последних|три первых|два первых|три последних)\s+(?:пункт|вариант|утверждени|запис|шаг|элемент|позиции|строк)/i;
for (const q of all) {
  const fields = { info: q.info };
  for (const [name, val] of Object.entries(fields)) {
    const s = String(val || '');
    if (POS.test(s) || POS2.test(s) || POS3.test(s)) REPORT.positional.push({ tag: q._tag, field: name, snippet: s.slice(0, 160) });
  }
  // варианты ссылающиеся на соседние
  for (const arr of ['options', 'items', 'candidates']) {
    for (const e of (q[arr] || [])) {
      const t = String(e.text || '');
      if (/предыдущ|последующ|вышестоящ|нижестоящ|соседн|остальн[а-яё]*\s+(вариант|пункт)/i.test(t))
        REPORT.positional.push({ tag: q._tag, field: arr, snippet: t.slice(0, 120) });
    }
  }
}

// ---------- 4. оформление: ключ длиннее дистракторов ----------
// Из helpers: sc(id, diff, text, options, correctOptionId, info) — ключ это буква; варианты в q.options.
const LETTERS = ['a','b','c','d','e','f'];
for (const q of all) {
  if (q.type === 'SingleChoice' && q.options && q.options.length) {
    const keyIdx = LETTERS.indexOf(q.correctOptionId);
    if (keyIdx < 0) continue;
    const keyLen = q.options[keyIdx].text.length;
    const dist = q.options.filter((_, i) => i !== keyIdx).map((o) => o.text.length);
    const avg = dist.reduce((s, x) => s + x, 0) / dist.length;
    const maxD = Math.max(...dist);
    if (keyLen >= 1.5 * avg || (avg > 0 && keyLen >= 1.5 * maxD)) {
      REPORT.keyLength.push({ tag: q._tag, keyLen, avg: Math.round(avg), maxD, key: q.options[keyIdx].text.slice(0, 90) });
    }
  }
  // mc: правильные с пояснением в скобках, которых нет у неверных
  if (q.type === 'MultipleChoice' && q.options) {
    const corr = Array.isArray(q.correctOptionIds) ? q.correctOptionIds : null;
    if (corr) {
      const keyIdxs = corr.map((l) => LETTERS.indexOf(l));
      const wrong = q.options.filter((_, i) => !keyIdxs.includes(i));
      for (const ki of keyIdxs) {
        const t = q.options[ki].text;
        if (/\([^)]+\)/.test(t) && wrong.every((w) => !/\([^)]+\)/.test(w.text))) {
          REPORT.keyLength.push({ tag: q._tag, kind: 'mc-gloss', key: t.slice(0, 90) });
        }
      }
    }
  }
}

fs.writeFileSync(path.join(__dirname, 'snap-history-1-4.json'), JSON.stringify(REPORT, null, 2));
console.log('fbDup candidates:', REPORT.fbDup.length);
REPORT.fbDup.forEach((d) => console.log(`  ${d.fb} ↔ ${d.sc} stem=${d.stemJ} opt=${d.optJ} key=${d.keyHit}\n    FB: ${d.fbText}\n    SC: ${d.scText} [key ${d.fbKey}]`));
console.log('ordOverlap:', REPORT.ordOverlap.length);
REPORT.ordOverlap.forEach((d) => console.log(`  ${d.a} ↔ ${d.b} verbatim=${d.verbatim} mean=${d.mean}${d.sharedList.length ? ' | ' + d.sharedList.slice(0, 5).join(' ; ') : ''}`));
console.log('positional:', REPORT.positional.length);
REPORT.positional.forEach((d) => console.log(`  ${d.tag} [${d.field}] ${d.snippet}`));
console.log('keyLength:', REPORT.keyLength.length);
REPORT.keyLength.forEach((d) => console.log(`  ${d.tag} keyLen=${d.keyLen} avg=${d.avg} maxD=${d.maxD} :: ${d.key || ''}${d.kind || ''}`));
