'use strict';
// Ревизор темы history 3-3: снимок + попарные сверки (уникальные имена файлов).
// Запуск: node audit-history-3-3.js [--snapshot-only]

const fs = require('fs');
const path = require('path');

const LESSONS = ['3-3-1', '3-3-2', '3-3-3', '3-3-4', '3-3-5'];
const SNAP = path.join(__dirname, 'snap-history-3-3.json');

const norm = (s) => String(s)
  .toLowerCase()
  .replace(/ё/g, 'е')
  .replace(/\(.*?\)/g, ' ')
  .replace(/[^a-zа-я0-9\s]/g, ' ')
  .replace(/\b(г|гг|в|на|из|за|по|и|с|к|о|об|от|до|что|какой|какие|каких|кем|чем|где|когда|это|или|для|при|уже|еще|был|была|были|стал|стала|стало|называли|называется|называлась|называли|принял|стал|все|год|года|году)\b/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const stemTok = (t) => {
  if (/^\d+[-–]?\d*$/.test(t)) return t;
  return t.length <= 5 ? t : t.slice(0, 5);
};
const tokens = (s) => norm(s).split(' ').filter(Boolean);
const stems = (s) => tokens(s).map(stemTok);
const jacc = (a, b) => {
  const A = new Set(stems(a)), B = new Set(stems(b));
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
};

const load = {};
for (const l of LESSONS) load[l] = require(path.join(__dirname, 'data/school/history/lessons', `${l}.js`));

// --- снимок (состояние до правок) ---
if (!fs.existsSync(SNAP)) {
  const snap = {};
  for (const l of LESSONS) {
    snap[l] = load[l].map((q, i) => ({ i, type: q.type, difficulty: q.difficulty, text: q.text,
      options: q.options ? q.options.map(o => o.text) : undefined,
      items: q.items ? q.items.map(o => o.text) : undefined,
      candidates: q.candidates ? q.candidates.map(o => o.text) : undefined,
      key: q.correctOptionId || q.correctOptionIds || (q.blanks ? q.blanks.map(b => b.correctCandidateId) : undefined),
      info: q.info }));
  }
  fs.writeFileSync(SNAP, JSON.stringify(snap, null, 1));
  console.log(`SNAPSHOT -> ${SNAP}`);
}

const issues = [];
const allQ = [];
for (const l of LESSONS) load[l].forEach((q, i) => allQ.push({ l, i, q }));

// 1) fb <-> sc/mc внутри урока: тот же стем-смысл + тот же ключ (или почти те же кандидаты)
for (const l of LESSONS) {
  const qs = load[l];
  const fbs = qs.map((q, i) => ({ q, i })).filter(x => x.q.type === 'FillBlank');
  const scmcs = qs.map((q, i) => ({ q, i })).filter(x => x.q.type === 'SingleChoice' || x.q.type === 'MultipleChoice');
  for (const { q: fbq, i: fi } of fbs) {
    const candTexts = fbq.candidates.map(c => norm(c.text));
    const fbKeyTexts = fbq.blanks.map(b => norm(fbq.candidates.find(c => c.id === b.correctCandidateId).text));
    const fbStem = fbq.text.replace(/_{1,}/g, ' ');
    for (const { q: sq, i: si } of scmcs) {
      const stemJ = jacc(fbStem, sq.text);
      const optTexts = sq.options.map(o => norm(o.text));
      const keys = Array.isArray(sq.correctOptionIds) ? sq.correctOptionIds : [sq.correctOptionId];
      const keyTexts = sq.options.filter(o => keys.includes(o.id)).map(o => norm(o.text));
      // ключ fb совпадает с ключом sc/mc
      const keyHit = fbKeyTexts.some(k => keyTexts.some(kk => {
        const A = new Set(stems(k)), B = new Set(stems(kk));
        const sub = [...A].every(x => B.has(x)) || [...B].every(x => A.has(x));
        return sub || jacc(k, kk) >= 0.5;
      }));
      const candJ = jacc(candTexts.join(' '), optTexts.join(' '));
      if (stemJ >= 0.25 && keyHit) issues.push(`FB-SC-DUP(key): ${l}#${fi} fb "${fbq.text.slice(0, 70)}" ~ ${si} "${sq.text.slice(0, 70)}" stem=${stemJ.toFixed(2)} candJ=${candJ.toFixed(2)}`);
      else if (stemJ >= 0.55 && candJ >= 0.5) issues.push(`FB-SC-DUP(cands): ${l}#${fi} fb "${fbq.text.slice(0, 70)}" ~ ${si} "${sq.text.slice(0, 70)}" stem=${stemJ.toFixed(2)} candJ=${candJ.toFixed(2)}`);
    }
  }
}

// 2) ord-пары по всей теме: доля совпадающих пунктов (по меньшему из наборов)
const yearOf = (t) => {
  const nums = String(t).match(/\d{3,4}/g);
  return nums ? parseInt(nums[nums.length - 1], 10) : null;
};
const itemMatch = (a, b) => {
  const j = jacc(a, b);
  if (j >= 0.45) return true;
  const ya = yearOf(a), yb = yearOf(b);
  return ya !== null && ya === yb && j >= 0.2;
};
const ords = allQ.filter(x => x.q.type === 'Ordering');
for (let x = 0; x < ords.length; x++) {
  for (let y = x + 1; y < ords.length; y++) {
    const A = ords[x], B = ords[y];
    const matched = new Set();
    for (const ia of A.q.items) {
      let best = null;
      for (const ib of B.q.items) {
        if (itemMatch(ia.text, ib.text)) { best = ib; break; }
      }
      if (best) matched.add(best.id + '|' + B.l + '#' + B.i);
    }
    const frac = matched.size / Math.min(A.q.items.length, B.q.items.length);
    if (frac >= 0.75) issues.push(`ORD-OVERLAP ${Math.round(frac * 100)}%: ${A.l}#${A.i} ~ ${B.l}#${B.i} [${[...matched].length}]`);
  }
}

// 3) Позиционные ссылки в info/вариантах (кириллица, свой регэксп)
const POS_RE = new RegExp('(перв|втор|трет|четв|пят|последн|остальн|заключительн|предпоследн|предыдущ|следующ)[а-яё]*\\s+([а-яё]+\\s+){0,2}(вариант|пункт|утвержден|шаг|строк|столбц|ответ|дат|запис)');
const RESP_RE = /соответственно|из перечисленных выше|вышеперечисленн|как указано выше|вариант\s+[абвгд]\b|пункт\s+[а-яё]\)|под\s+буквой/;
for (const { l, i, q } of allQ) {
  const texts = [q.info || ''];
  if (q.options) texts.push(...q.options.map(o => o.text));
  if (q.items) texts.push(...q.items.map(o => o.text));
  for (const t of texts) {
    if (POS_RE.test(t) || RESP_RE.test(t)) issues.push(`POS-REF: ${l}#${i}: "${t.slice(0, 90)}"`);
  }
}

// 4) Оформление: ключ sc заметно длиннее дистракторов / с пояснением в скобках
for (const { l, i, q } of allQ) {
  if (q.type === 'SingleChoice' || q.type === 'MultipleChoice') {
    const keys = Array.isArray(q.correctOptionIds) ? q.correctOptionIds : [q.correctOptionId];
    for (const kid of keys) {
      const key = q.options.find(o => o.id === kid);
      const dis = q.options.filter(o => o.id !== kid);
      const avgDis = dis.reduce((s, o) => s + o.text.length, 0) / dis.length;
      if (key.text.length >= avgDis * 1.6 && key.text.length - Math.max(...dis.map(o => o.text.length)) > 20)
        issues.push(`KEY-LONG: ${l}#${i}: ключ ${key.text.length} симв. (avg дистр. ${Math.round(avgDis)}): "${key.text.slice(0, 70)}"`);
      if (/\((?!г\.|гг\.|год|годы|г\b)[^)]{6,}\)/.test(key.text) && !dis.some(o => /\([^)]{6,}\)/.test(o.text)))
        issues.push(`KEY-PAREN: ${l}#${i}: ключ с пояснением в скобках, у дистракторов нет: "${key.text.slice(0, 80)}"`);
    }
  }
}

// 5) ord: хронология (годы в пунктах должны не убывать) и недатированные пункты
for (const { l, i, q } of ords) {
  let prev = null;
  for (const it of q.items) {
    const y = yearOf(it.text);
    if (y === null) issues.push(`ORD-NODATE: ${l}#${i}: "${it.text.slice(0, 60)}"`);
    else {
      if (prev !== null && y < prev) issues.push(`ORD-CHRONO: ${l}#${i}: ${y} после ${prev}`);
      prev = y;
    }
  }
}

// 6) mc: полностью совпадающие варианты (норм.)
for (const { l, i, q } of allQ) {
  if (q.type !== 'MultipleChoice' && q.type !== 'SingleChoice') continue;
  const seen = new Map();
  for (const o of q.options) {
    const k = norm(o.text);
    if (seen.has(k)) issues.push(`OPT-DUP: ${l}#${i}: "${o.text.slice(0, 50)}" == "${seen.get(k).slice(0, 50)}"`);
    else seen.set(k, o.text);
  }
}

// 7) Бакеты и порядок блоков
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
