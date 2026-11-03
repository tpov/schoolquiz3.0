'use strict';
// Ревизор темы history 2-4: снимок + попарные сверки (уникальные имена файлов).
// Запуск: node audit-history-2-4.js [--snapshot]  (по умолчанию снимок + анализ)

const fs = require('fs');
const path = require('path');

const LESSONS = ['2-4-1', '2-4-2', '2-4-3', '2-4-4', '2-4-5'];
const SNAP = path.join(__dirname, 'snap-history-2-4.json');

const norm = (s) => String(s)
  .toLowerCase()
  .replace(/ё/g, 'е')
  .replace(/\(.*?\)/g, ' ')   // скобки (годы, пояснения)
  .replace(/[^a-zа-я0-9\s]/g, ' ')
  .replace(/\b(г|гг|в|на|из|за|по|и|с|к|о|об|от|до|что|какой|какие|каких|кем|чем|где|когда)\b/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const tokens = (s) => norm(s).split(' ').filter(Boolean);
const jacc = (a, b) => {
  const A = new Set(tokens(a)), B = new Set(tokens(b));
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
};

const load = {};
for (const l of LESSONS) load[l] = require(path.join(__dirname, 'data/school/history/lessons', `${l}.js`));

// --- снимок ---
const snap = {};
for (const l of LESSONS) {
  snap[l] = load[l].map((q, i) => ({ i, type: q.type, diff: q.difficulty, text: q.text,
    options: q.options ? q.options.map(o => o.text) : undefined,
    items: q.items ? q.items.map(o => o.text) : undefined,
    candidates: q.candidates ? q.candidates.map(o => o.text) : undefined,
    key: q.correctOptionId || q.correctOptionIds || (q.blanks ? q.blanks.map(b => b.correctCandidateId) : undefined),
    info: q.info }));
}
fs.writeFileSync(SNAP, JSON.stringify(snap, null, 1));
console.log(`SNAPSHOT -> ${SNAP}`);

const issues = [];

// 1) Точное дублирование текстов вопросов внутри темы (гейт-стиль)
const allQ = [];
for (const l of LESSONS) load[l].forEach((q, i) => allQ.push({ l, i, q }));
const seenText = new Map();
for (const { l, i, q } of allQ) {
  const k = norm(q.text) + '|' + (q.options ? q.options.map(o => norm(o.text)).sort().join('#')
    : q.items ? q.items.map(o => norm(o.text)).join('#')
    : q.candidates ? q.candidates.map(o => norm(o.text)).join('#') : '');
  if (seenText.has(k)) issues.push(`DUP-TEXT: ${l}#${i} == ${seenText.get(k)}`);
  else seenText.set(k, `${l}#${i}`);
}

// 2) fb <-> sc/mc внутри урока: тот же стем-смысл + пересечение ключей/кандидатов
for (const l of LESSONS) {
  const qs = load[l];
  const fbs = qs.map((q, i) => ({ q, i })).filter(x => x.q.type === 'FillBlank');
  const scmcs = qs.map((q, i) => ({ q, i })).filter(x => x.q.type === 'SingleChoice' || x.q.type === 'MultipleChoice');
  for (const { q: fbq, i: fi } of fbs) {
    const candTexts = fbq.candidates.map(c => norm(c.text));
    const fbKeyTexts = fbq.blanks.map(b => norm(fbq.candidates.find(c => c.id === b.correctCandidateId).text));
    for (const { q: sq, i: si } of scmcs) {
      const stemJ = jacc(fbq.text, sq.text);
      const optTexts = sq.options.map(o => norm(o.text));
      const keys = Array.isArray(sq.correctOptionIds) ? sq.correctOptionIds : [sq.correctOptionId];
      const keyTexts = sq.options.filter(o => keys.includes(o.id)).map(o => norm(o.text));
      // кандидат fb совпадает с ключом sc/mc (или наоборот) и стемы похожи
      const keyHit = fbKeyTexts.some(k => keyTexts.some(kk =>
        kk.includes(k) || k.includes(kk) || jacc(k, kk) >= 0.6));
      const candJ = jacc(candTexts.join(' '), optTexts.join(' '));
      if (stemJ >= 0.45 && keyHit) issues.push(`FB-SC-DUP(strong): ${l}#${fi} fb "${fbq.text.slice(0, 60)}" ~ ${si} sc/mc "${sq.text.slice(0, 60)}" stem=${stemJ.toFixed(2)} candJ=${candJ.toFixed(2)}`);
      else if (stemJ >= 0.45 && candJ >= 0.45) issues.push(`FB-SC-DUP(cands): ${l}#${fi} fb "${fbq.text.slice(0, 60)}" ~ ${si} sc/mc "${sq.text.slice(0, 60)}" stem=${stemJ.toFixed(2)} candJ=${candJ.toFixed(2)}`);
    }
  }
}

// 3) ord-пары внутри темы: совпадение пунктов (событий)
const ords = allQ.filter(x => x.q.type === 'Ordering');
function itemsSim(a, b) { return jacc(a, b); }
for (let x = 0; x < ords.length; x++) {
  for (let y = x + 1; y < ords.length; y++) {
    const A = ords[x], B = ords[y];
    const matched = new Set();
    for (const ia of A.q.items) {
      let best = null;
      for (const ib of B.q.items) {
        const s = itemsSim(ia.text, ib.text);
        if (!best || s > best.s) best = { s, ib };
      }
      if (best && best.s >= 0.55) matched.add(best.ib.id + B.l + B.i);
    }
    const frac = matched.size / Math.min(A.q.items.length, B.q.items.length);
    if (frac >= 0.7) issues.push(`ORD-OVERLAP ${Math.round(frac * 100)}%: ${A.l}#${A.i} ~ ${B.l}#${B.i}`);
  }
}

// 4) Позиционные ссылки в info/вариантах (кириллица, свой регэксп)
const POS_RE = new RegExp('(перв|втор|трет|четв|пят|последн|остальн|заключительн)[а-яё]*\\s+([а-яё]+\\s+){0,2}(вариант|пункт|утвержден|шаг|строк|столбц|ответ|дат)');
const RESP_RE = /соответственно|из перечисленных выше|вышеперечисленн/;
for (const { l, i, q } of allQ) {
  const texts = [q.info || ''];
  if (q.options) texts.push(...q.options.map(o => o.text));
  if (q.items) texts.push(...q.items.map(o => o.text));
  for (const t of texts) {
    if (POS_RE.test(t) || RESP_RE.test(t)) issues.push(`POS-REF: ${l}#${i}: "${t.slice(0, 80)}"`);
  }
}

// 5) Оформление: ключ sc заметно длиннее дистракторов
for (const { l, i, q } of allQ) {
  if (q.type !== 'SingleChoice') continue;
  const key = q.options.find(o => o.id === q.correctOptionId);
  const dis = q.options.filter(o => o.id !== q.correctOptionId);
  const avgDis = dis.reduce((s, o) => s + o.text.length, 0) / dis.length;
  if (key.text.length >= avgDis * 1.5 && key.text.length - Math.max(...dis.map(o => o.text.length)) > 15) {
    issues.push(`KEY-LONG: ${l}#${i}: ключ ${key.text.length} симв., дистракторы avg ${Math.round(avgDis)}: "${key.text.slice(0, 60)}"`);
  }
}

// 6) Бакеты и порядок блоков
for (const l of LESSONS) {
  const qs = load[l];
  const expect = [];
  for (const t of ['SingleChoice', 'MultipleChoice', 'Ordering', 'FillBlank'])
    for (const d of ['EASY', 'HARD']) for (let k = 0; k < 5; k++) expect.push([t, d]);
  qs.forEach((q, i) => {
    if (q.type !== expect[i][0] || q.difficulty !== expect[i][1])
      issues.push(`BLOCK-ORDER: ${l}#${i} ${q.type}/${q.difficulty}, ожидалось ${expect[i].join('/')}`);
  });
}

console.log(issues.length ? issues.join('\n') : 'NO ISSUES FOUND');
