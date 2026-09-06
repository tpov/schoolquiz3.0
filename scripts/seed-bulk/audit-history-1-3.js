'use strict';
// Аудит темы history 1-3: попарная сверка 200 вопросов (дубли), позиционные ссылки, длина ключей.
const path = require('path');
const fs = require('fs');
const base = path.join(__dirname, 'data', 'school', 'history');

const STOP = new Set(['до', 'н', 'э', 'г', 'гг', 'год', 'года', 'лет', 'век', 'века', 'вв', 'ок', 'примерно', 'около', 'и', 'в', 'на', 'с', 'по', 'the']);
const norm = (x) => String(x || '').toLowerCase().replace(/[«»"'(){}\[\]—–-]/g, ' ').replace(/\s+/g, ' ').trim();
const tokens = (x) => norm(x).split(/[^a-zа-яё0-9]+/i).filter((w) => w.length > 2 && !STOP.has(w));

function simTokens(a, b) {
  const A = new Set(tokens(a)), B = new Set(tokens(b));
  if (!A.size || !B.size) return 0;
  let inter = 0; for (const w of A) if (B.has(w)) inter++;
  return inter / Math.min(A.size, B.size); // overlap coefficient
}
function arrTexts(q, arr) { return (q[arr] || []).map((e) => String(e.text || '')); }

// Грубое сопоставление элементов двух списков (жадное, порог по overlap)
function matchCount(listA, listB, thr = 0.55) {
  const used = new Set(); let matched = 0; const pairs = [];
  for (const a of listA) {
    let best = -1, bestJ = -1, bestIdx = -1;
    listB.forEach((b, i) => {
      if (used.has(i)) return;
      const s = simTokens(a, b);
      if (s > bestJ) { bestJ = s; bestIdx = i; best = b; }
    });
    if (bestJ >= thr) { matched++; used.add(bestIdx); pairs.push([a, best, bestJ.toFixed(2)]); }
  }
  return { matched, pairs };
}

const POS_RE = /(перв|втор|трет|четв|пят|шест|седьм|последн|предпоследн|остальн|оставш)[а-яё]*\s+(вариант|пункт|шаг|утверждени|запис|случа|элемент|позици|половин|часть|строк|колонк|столбц|пункт)/i;
const SOOTV = /соответственно/i;

const lessons = {};
for (let l = 1; l <= 5; l++) lessons[l] = require(path.join(base, 'lessons', `1-3-${l}.js`));

const all = [];
for (let l = 1; l <= 5; l++) lessons[l].forEach((q, i) => all.push({ l, i, q, label: `1-3-${l} q${i}` }));

fs.writeFileSync(path.join(__dirname, 'snap-history-1-3.json'), JSON.stringify(all, null, 1), 'utf8');
console.log('snapshot written: snap-history-1-3.json, questions:', all.length);

// --- 1. Позиционные ссылки (info + варианты/пункты)
console.log('\n=== ПОЗИЦИОННЫЕ ССЫЛКИ ===');
for (const { label, q } of all) {
  const info = String(q.info || '');
  if (POS_RE.test(info) || SOOTV.test(info)) console.log(`${label} INFO: ${info.slice(0, 120)}`);
  for (const arr of ['options', 'items', 'candidates']) {
    for (const t of arrTexts(q, arr)) if (POS_RE.test(String(t))) console.log(`${label} ${arr}: ${t.slice(0, 120)}`);
  }
}

// --- 2. Попарные дубли (внутри типа; текст + совпадение вариантов)
console.log('\n=== ПОПАРНЫЕ СВЕРКИ (ratio>=0.5) ===');
const TYPE = { SingleChoice: 'sc', MultipleChoice: 'mc', Ordering: 'ord', FillBlank: 'fb' };
for (let x = 0; x < all.length; x++) {
  for (let y = x + 1; y < all.length; y++) {
    const A = all[x], B = all[y];
    if (A.q.type !== B.q.type) continue;
    const arr = A.q.type === 'SingleChoice' || A.q.type === 'MultipleChoice' ? 'options'
      : A.q.type === 'Ordering' ? 'items' : 'candidates';
    const la = arrTexts(A.q, arr), lb = arrTexts(B.q, arr);
    const { matched, pairs } = matchCount(la, lb);
    const ratio = matched / Math.min(la.length, lb.length);
    const textSim = simTokens(A.q.text, B.q.text);
    if (ratio >= 0.5) {
      const flag = (ratio >= 0.7 || (ratio >= 0.6 && textSim >= 0.35)) ? 'FLAG' : '    ';
      console.log(`${flag} ${A.label} ~ ${B.label} [${TYPE[A.q.type]}] ratio=${ratio.toFixed(2)} textSim=${textSim.toFixed(2)}`);
      if (flag === 'FLAG') for (const [a, b, s] of pairs) console.log(`      (${s}) «${a.slice(0, 60)}» ↔ «${b.slice(0, 60)}»`);
    }
  }
}

// --- 3. Оформление: ключ sc заметно длиннее дистракторов или с пояснением, которого нет у дистракторов
console.log('\n=== ОФОРМЛЕНИЕ sc (ключ длиннее дистракторов x1.5) ===');
for (const { label, q } of all) {
  if (q.type !== 'SingleChoice') continue;
  const correct = (q.options || []).find((o) => o.id === q.correctOptionId);
  const distr = (q.options || []).filter((o) => o.id !== q.correctOptionId).map((o) => String(o.text));
  const maxD = Math.max(...distr.map((d) => d.length));
  if (correct && correct.text.length / maxD > 1.5) console.log(`${label}: ключ(${correct.text.length}) «${correct.text.slice(0, 70)}» vs maxDistr(${maxD}) «${distr.sort((a, b) => b.length - a.length)[0].slice(0, 50)}»`);
}
// mc: пояснение через тире только у ключевых вариантов
console.log('\n=== ОФОРМЛЕНИЕ mc (пояснение-тире у ключевых, нет у дистракторов) ===');
for (const { label, q } of all) {
  if (q.type !== 'MultipleChoice') continue;
  const dash = (o) => /\s—\s/.test(String(o.text));
  const keyDash = (q.options || []).filter((o) => q.correctOptionIds.includes(o.id) && dash(o));
  const disDash = (q.options || []).filter((o) => !q.correctOptionIds.includes(o.id) && dash(o));
  if (keyDash.length && !disDash.length) console.log(`${label}: key с тире: ${keyDash.map((o) => o.text).join(' | ')}; дистракторы без тире: ${(q.options || []).filter((o) => !q.correctOptionIds.includes(o.id)).map((o) => o.text).join(' | ')}`);
}
console.log('\nDONE');
