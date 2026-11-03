'use strict';
// Аудит-скрипт темы history 4-4 (уникальное имя). Снимок + поиск дефектов.
const fs = require('fs');
const path = require('path');

const LESSONS = ['4-4-1', '4-4-2', '4-4-3', '4-4-4', '4-4-5'];
const dir = path.join(__dirname, 'data', 'school', 'history', 'lessons');
const snapPath = path.join(__dirname, 'snap-history-4-4.json');

function load(les) {
  const m = require(path.join(dir, les + '.js'));
  return m.map((q, i) => ({ les, idx: i, q }));
}

// --- нормализация: русские основы (обрубаем слово до префикса 5/6 букв) ---
function stems(text) {
  const words = String(text).toLowerCase()
    .replace(/[^а-яёa-z0-9\s-]/gi, ' ')
    .split(/\s+/)
    .filter(Boolean);
  return new Set(words.map(w => (w.length >= 6 ? w.slice(0, 6) : w)).filter(w => !STOP.has(w)));
}
const STOP = new Set(['какой', 'какая', 'какие', 'кого', 'кто', 'что', 'где', 'когда', 'вчем', 'каким', 'каком', 'который', 'назывался', 'называли', 'называется', 'произошла', 'произошло', 'произошли', 'стал', 'стала']);

function jac(a, b) {
  if (!a.size || !b.size) return 0;
  let inter = 0;
  for (const x of a) if (b.has(x)) inter++;
  return inter / (a.size + b.size - inter);
}
function sim(t1, t2) { return jac(stems(t1), stems(t2)); }

const all = LESSONS.flatMap(load);

// --- снимок ---
fs.writeFileSync(snapPath, JSON.stringify(all.map(({ les, idx, q }) => ({
  les, idx, type: q.type, diff: q.difficulty, text: q.text,
  options: q.options ? q.options.map(o => o.text) : undefined,
  correct: q.correctOptionId || (q.correctOptionIds || undefined),
  blanks: q.blanks ? q.blanks.map(b => b.correctCandidateId) : undefined,
  candidates: q.candidates ? q.candidates.map(c => c.text) : undefined,
  items: q.items ? q.items.map(i => i.text) : undefined,
  info: q.info,
})), null, 1));
console.log('snapshot ->', snapPath);

const out = [];

// --- (а) fb vs sc/mc: схожий стем + тот же ключ ---
for (const f of all.filter(x => x.q.type === 'FillBlank')) {
  const fText = f.q.text.replace(/_{2,}/g, ' ');
  const fCorrect = f.q.candidates.find(c => c.id === f.q.blanks[0].correctCandidateId).text;
  for (const o of all.filter(x => (x.q.type === 'SingleChoice' || x.q.type === 'MultipleChoice'))) {
    const stemSim = sim(fText, o.q.text);
    const opts = o.q.options.map(x => x.text);
    const correctTexts = (o.q.type === 'SingleChoice'
      ? [opts.find((t, i) => 'abcd'[i] === o.q.correctOptionId)]
      : opts.filter((t, i) => o.q.correctOptionIds.includes('abcdef'[i])));
    const keyHit = correctTexts.some(t => sim(t, fCorrect) >= 0.6 || t.toLowerCase().includes(fCorrect.toLowerCase()) || fCorrect.toLowerCase().includes(t.toLowerCase()));
    if (stemSim >= 0.4 && keyHit) {
      out.push(`FB-DUP ${f.les}#${f.idx} fb«${fCorrect}» vs ${o.q.type === 'SingleChoice' ? 'sc' : 'mc'} ${o.les}#${o.idx} stem=${stemSim.toFixed(2)} | fb: ${f.q.text.slice(0, 70)} | sc/mc: ${o.q.text.slice(0, 70)}`);
    }
  }
}

// --- ord: пересечение пунктов (внутри урока) ---
for (const les of LESSONS) {
  const ords = all.filter(x => x.les === les && x.q.type === 'Ordering');
  for (let i = 0; i < ords.length; i++) for (let j = i + 1; j < ords.length; j++) {
    const a = ords[i], b = ords[j];
    const sa = a.q.items.map(t => stems(t.text)), sb = b.q.items.map(t => stems(t.text));
    let hits = 0;
    for (const x of sa) if (sb.some(y => jac(x, y) >= 0.55)) hits++;
    const rel = hits / Math.min(sa.length, sb.length);
    if (rel >= 0.5) out.push(`ORD-OVERLAP ${les} #${a.idx} vs #${b.idx}: ${hits}/${Math.min(sa.length, sb.length)} = ${(rel * 100) | 0}%`);
  }
}

// --- ord: годы монотонны ---
for (const { les, idx, q } of all.filter(x => x.q.type === 'Ordering')) {
  const years = q.items.map(t => {
    const m = t.text.match(/(\d{3,4})/g);
    return m ? m.map(Number) : null;
  });
  years.forEach((ys, k) => { if (!ys) out.push(`ORD-NODATE ${les}#${idx} item${k + 1}: ${q.items[k].text}`); });
  const firsts = years.map(y => y ? y[0] : null);
  for (let k = 1; k < firsts.length; k++) if (firsts[k] != null && firsts[k - 1] != null && firsts[k] < firsts[k - 1]) {
    out.push(`ORD-CHRONO ${les}#${idx}: год ${firsts[k]} стоит после ${firsts[k - 1]}`);
  }
}

// --- позиционные ссылки (кириллица-aware) ---
const POS = new RegExp('(?:первы|перв[ао]|втор|трет|четв|пят|шест|седьм|последн|послед|остальн|предпоследн|средн)[а-яё]*\\s+(?:из\\s+)?[а-яё]*(?:вариант|пункт|утверждени|шаг|строк|пропуск|позиций|поле)', 'gi');
const POS2 = /соответственн[а-яё]*/gi;
const POS3 = /(?:вариант|пункт)\s*[абвгде][\s).,]/gi;
for (const { les, idx, q } of all) {
  const targets = [['info', q.info]];
  if (q.options) q.options.forEach((o, i) => targets.push(['opt' + i, o.text]));
  if (q.items) q.items.forEach((o, i) => targets.push(['item' + i, o.text]));
  for (const [where, t] of targets) {
    const m1 = t && t.match(POS); const m2 = t && t.match(POS2); const m3 = t && t.match(POS3);
    if (m1) out.push(`POSREF ${les}#${idx} (${where}): ${m1.join('; ')} | ${t.slice(0, 80)}`);
    if (m2) out.push(`POSREF2 ${les}#${idx} (${where}): ${m2.join('; ')}`);
    if (m3) out.push(`POSREF3 ${les}#${idx} (${where}): ${m3.join('; ')}`);
  }
}

// --- mc: близкие варианты (означают одно и то же) ---
for (const { les, idx, q } of all.filter(x => x.q.type === 'MultipleChoice')) {
  const opts = q.options;
  for (let i = 0; i < opts.length; i++) for (let j = i + 1; j < opts.length; j++) {
    const s = sim(opts[i].text, opts[j].text);
    if (s >= 0.75) out.push(`MC-OPT-SIM ${les}#${idx} ${opts[i].id}≈${opts[j].id} (${s.toFixed(2)}): «${opts[i].text}» vs «${opts[j].text}»`);
  }
}

// --- оформление: ключ в 1.5+ раза длиннее дистракторов или со скобками-пояснением ---
for (const { les, idx, q } of all.filter(x => x.q.type === 'SingleChoice')) {
  const opts = q.options;
  const key = opts.find((o, i) => o.id === q.correctOptionId);
  const others = opts.filter(o => o !== key);
  const kLen = key.text.replace(/\([^)]*\)/g, '').trim().length;
  const avg = others.reduce((s, o) => s + o.text.replace(/\([^)]*\)/g, '').trim().length, 0) / others.length;
  if (kLen >= 20 && kLen >= avg * 1.6) out.push(`SC-LONGKEY ${les}#${idx}: ключ ${kLen} vs avg ${avg.toFixed(0)} «${key.text}»`);
  const keyParens = /\([^)]{5,}\)/.test(key.text) && !others.some(o => /\([^)]{5,}\)/.test(o.text));
  if (keyParens) out.push(`SC-PARENS ${les}#${idx}: у ключа пояснение в скобках, у дистракторов нет: «${key.text}»`);
}
for (const { les, idx, q } of all.filter(x => x.q.type === 'FillBlank')) {
  const key = q.candidates.find(c => c.id === q.blanks[0].correctCandidateId);
  const others = q.candidates.filter(c => c !== key);
  const kLen = key.text.replace(/\([^)]*\)/g, '').trim().length;
  const avg = others.reduce((s, o) => s + o.text.replace(/\([^)]*\)/g, '').trim().length, 0) / others.length;
  if (kLen >= 15 && kLen >= avg * 1.6) out.push(`FB-LONGKEY ${les}#${idx}: ключ ${kLen} vs avg ${avg.toFixed(0)} «${key.text}»`);
}

// --- fb: близкие кандидаты между собой (второй верный в fb) ---
for (const { les, idx, q } of all.filter(x => x.q.type === 'FillBlank')) {
  const cs = q.candidates;
  for (let i = 0; i < cs.length; i++) for (let j = i + 1; j < cs.length; j++) {
    const s = sim(cs[i].text, cs[j].text);
    if (s >= 0.8 && cs[i].text.toLowerCase() !== cs[j].text.toLowerCase()) {
      out.push(`FB-CAND-SIM ${les}#${idx} ${cs[i].id}≈${cs[j].id} (${s.toFixed(2)}): «${cs[i].text}» vs «${cs[j].text}»`);
    }
  }
}

// --- fb: полный дубликат вопросов между уроками/внутри ---
const seen = new Map();
for (const { les, idx, q } of all) {
  const sig = q.type + '|' + q.text + '|' + (q.options ? q.options.map(o => o.text).join('§') : q.items ? q.items.map(i => i.text).join('§') : q.candidates.map(c => c.text).join('§'));
  if (seen.has(sig)) out.push(`EXACT-DUP ${les}#${idx} == ${seen.get(sig)}`);
  else seen.set(sig, `${les}#${idx}`);
}

console.log(out.length ? out.join('\n') : 'CLEAN: подозрительных мест не найдено');
