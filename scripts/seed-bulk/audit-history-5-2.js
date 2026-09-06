'use strict';
// Аудит темы history 5-2 (5 уроков × 40 вопросов): дубли fb↔sc/mc и fb↔fb, ord-перенасыщение,
// ord-подмножества, позиционные ссылки, выдающее ответ оформление, хронология ord, mc-дубли опций.
const fs = require('fs');
const path = require('path');

const THEME = '5-2';
const LESSONS = [1, 2, 3, 4, 5].map(l => path.join(__dirname, 'data', 'school', 'history', 'lessons', `${THEME}-${l}.js`));
const SNAP = path.join(__dirname, `snap-history-${THEME}.json`);
const MODE = process.argv[2] || 'full'; // full | check (check — без перезаписи снимка)

// ---------- нормализация ----------
const STOP = new Set(['и', 'в', 'во', 'не', 'на', 'с', 'со', 'из', 'за', 'по', 'у', 'о', 'об', 'от', 'до', 'для', 'что', 'как', 'это', 'при', 'которые', 'который', 'которая', 'было', 'был', 'была', 'были', 'его', 'их', 'она', 'они', 'он', 'the', 'гг', 'г']);

function stemWord(w) {
  if (w.length <= 4) return w;
  const endings = ['иями', 'ями', 'ами', 'иях', 'ях', 'ах', 'ов', 'ев', 'ей', 'ой', 'ий', 'ый', 'ая', 'яя', 'ое', 'ее', 'ые', 'ие', 'ого', 'его', 'ому', 'ему', 'ыми', 'ими', 'ем', 'ом', 'ам', 'ым', 'им', 'ую', 'ешь', 'ение', 'ание', 'ость', 'а', 'я', 'ы', 'и', 'у', 'ю', 'е', 'ь'];
  for (const e of endings) {
    if (w.length - e.length >= 3 && w.endsWith(e)) return w.slice(0, w.length - e.length);
  }
  return w;
}

function norm(s) {
  return String(s)
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/«|»|["'(),.;:!?—–\-]/g, ' ')
    .replace(/\d{3,4}\s*г(г)?\.?/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
function stems(s) {
  return norm(s).split(' ').filter(w => w.length > 1 && !STOP.has(w)).map(stemWord);
}
function jac(a, b) {
  const A = new Set(a), B = new Set(b);
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
}
function optTexts(q) {
  if (q.type === 'Ordering') return q.items.map(o => o.text);
  if (q.type === 'FillBlank') return q.candidates.map(o => o.text);
  return q.options.map(o => o.text);
}
function keyTexts(q) {
  if (q.type === 'SingleChoice') return q.options.filter(o => o.id === q.correctOptionId).map(o => o.text);
  if (q.type === 'MultipleChoice') return q.options.filter(o => q.correctOptionIds.includes(o.id)).map(o => o.text);
  if (q.type === 'FillBlank') {
    const idset = new Set(q.blanks.map(b => b.correctCandidateId));
    return q.candidates.filter(o => idset.has(o.id)).map(o => o.text);
  }
  return [];
}

// ---------- загрузка ----------
const data = LESSONS.map((p, i) => {
  delete require.cache[require.resolve(p)];
  const arr = require(p);
  if (!Array.isArray(arr) || arr.length !== 40) throw new Error(`lesson ${THEME}-${i + 1}: ${Array.isArray(arr) ? arr.length : 'not array'} questions`);
  return { l: i + 1, qs: arr };
});

// снимок для контроля правок
if (MODE === 'full') {
  const snap = {};
  for (const { l, qs } of data) snap[`${THEME}-${l}`] = qs.map((q, idx) => ({
    idx, type: q.type, difficulty: q.difficulty, text: q.text,
    options: optTexts(q), key: keyTexts(q), info: q.info,
  }));
  fs.writeFileSync(SNAP, JSON.stringify(snap, null, 1));
  console.log(`SNAPSHOT written: ${SNAP} (${Object.values(snap).reduce((s, a) => s + a.length, 0)} questions)`);
}

// ---------- 1. Похожие пары внутри урока (акцент: fb ↔ sc/mc и fb ↔ fb) ----------
console.log('\n=== 1. ПОХОЖИЕ ПАРЫ ВНУТРИ УРОКА ===');
for (const { l, qs } of data) {
  for (let i = 0; i < qs.length; i++) {
    for (let j = i + 1; j < qs.length; j++) {
      const A = qs[i], B = qs[j];
      const isFB = q => q.type === 'FillBlank';
      const crossType = isFB(A) !== isFB(B);
      const sj = jac(stems(A.text), stems(B.text));
      const oj = jac(optTexts(A).map(norm), optTexts(B).map(norm));
      const kj = jac(keyTexts(A).map(norm), keyTexts(B).map(norm));
      // fb-пары: низкий порог по стему — ловим «Когда X» vs «X … ___ года»
      const interesting = crossType ? (sj >= 0.28 || oj >= 0.45) : (sj >= 0.42 && (oj >= 0.3 || kj >= 0.5));
      if (interesting) {
        console.log(`L${l} [${i}]${A.type[0]}${A.difficulty[0]} "${norm(A.text).slice(0, 78)}"`);
        console.log(`   [${j}]${B.type[0]}${B.difficulty[0]} "${norm(B.text).slice(0, 78)}"`);
        console.log(`   stemJ=${sj.toFixed(2)} optJ=${oj.toFixed(2)} keyJ=${kj.toFixed(2)}`);
      }
    }
  }
}

// ---------- 2. ord: перенасыщение и подмножества (внутри темы, нормированные пункты) ----------
console.log('\n=== 2. ORD: ПЕРЕКРЫТИЯ ПУНКТОВ (отчёт 50%, дефект 75%) ===');
const ords = [];
data.forEach(({ l, qs }) => qs.forEach((q, idx) => { if (q.type === 'Ordering') ords.push({ l, idx, q }); }));
function ordItemSet(o) {
  return o.q.items.map(it => {
    const n = norm(it.text).replace(/около|начало|первая половина|середина|конец|открытие|подписание|провозглашение|вступление в силу|gg?/g, ' ').replace(/\s+/g, ' ').trim();
    return n.split(' ').map(stemWord).join(' ');
  });
}
const ordPairs = [];
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    const O = ords[i], T = ords[j];
    const sO = ordItemSet(O), sT = ordItemSet(T);
    const inter = sO.filter(x => sT.includes(x));
    const frac = Math.max(inter.length / sO.length, inter.length / sT.length);
    if (frac >= 0.5) {
      ordPairs.push({ a: O, b: T, frac });
      console.log(`L${O.l}[${O.idx}] vs L${T.l}[${T.idx}] overlap=${frac.toFixed(2)} (${inter.length}/${Math.min(sO.length, sT.length)}) общие: ${inter.join(' | ').slice(0, 200) || '—'}`);
    }
  }
}

console.log('\n=== 2b. ORD-ПОДМНОЖЕСТВА ===');
for (let i = 0; i < ords.length; i++) {
  for (let j = 0; j < ords.length; j++) {
    if (i === j) continue;
    const sO = ordItemSet(ords[i]), sT = ordItemSet(ords[j]);
    if (sO.length < sT.length && sO.every(x => sT.includes(x))) {
      console.log(`L${ords[i].l}[${ords[i].idx}] целиком входит в L${ords[j].l}[${ords[j].idx}]`);
    }
  }
}

// ---------- 3. Позиционные ссылки (кириллический регэксп) ----------
console.log('\n=== 3. ПОЗИЦИОННЫЕ ССЫЛКИ ===');
const POS = [
  /[А-Яа-яЁё]*(перв|втор|трет|четв|пят|последн|остальн|предпоследн|начальн|заключит)[А-Яа-яЁё]*\s+(вариант|пункт|утвержден|строк|пози|шаг|запис|предложен|абзац|столбец|перечисл|способ|средство)[А-Яа-яЁё]*/gi,
  /(?:вариант|пункт|утвержден|шаг|запис)[А-Яа-яЁё]*\s+[«"']?[абвгдеж]\b/gi,
  /соответственн[А-Яа-яЁё]*/gi,
];
for (const { l, qs } of data) {
  qs.forEach((q, idx) => {
    const fields = [['info', q.info]];
    optTexts(q).forEach((t, k) => fields.push([`opt${k}`, t]));
    fields.forEach(([fld, txt]) => {
      if (!txt) return;
      for (const re of POS) {
        re.lastIndex = 0;
        const m = txt.match(re);
        if (m) console.log(`L${l}[${idx}] ${q.type} ${q.difficulty} ${fld}: ${m.join('; ')} || ${txt.slice(0, 120)}`);
      }
    });
  });
}

// ---------- 4. Оформление: ключ длиннее/подробнее дистракторов ----------
console.log('\n=== 4. КЛЮЧ ВЫДЕЛЯЕТСЯ ДЛИНОЙ (sc) ===');
for (const { l, qs } of data) {
  qs.forEach((q, idx) => {
    if (q.type !== 'SingleChoice') return;
    const key = q.options.find(o => o.id === q.correctOptionId);
    const dis = q.options.filter(o => o.id !== q.correctOptionId);
    const avgD = dis.reduce((s, o) => s + o.text.length, 0) / dis.length;
    const maxD = Math.max(...dis.map(o => o.text.length));
    const kParen = /\([^)]{6,}\)/.test(key.text);
    const dParen = dis.some(o => /\([^)]{6,}\)/.test(o.text));
    if (key.text.length > 1.5 * avgD && key.text.length > maxD * 1.25) {
      console.log(`L${l}[${idx}] sc key=${key.text.length} avgD=${avgD.toFixed(0)} maxD=${maxD} :: ${key.text.slice(0, 90)}`);
    }
    if (kParen && !dParen) {
      console.log(`L${l}[${idx}] sc ключ со скобкой-пояснением, у дистракторов нет :: ${key.text.slice(0, 90)}`);
    }
  });
}

// ---------- 5. Хронология ord ----------
console.log('\n=== 5. ХРОНОЛОЛОГИЯ ORD ===');
for (const { l, qs } of data) {
  qs.forEach((q, idx) => {
    if (q.type !== 'Ordering') return;
    let prev = null;
    q.items.forEach(it => {
      // год в скобках с «г.» надёжнее: «(1921 г.)», иначе любое число (избегаем «132 млрд»)
      const mp = it.text.match(/\(([^)]*?)(\d{3,4})\s*г(г)?\.?\)/) || it.text.match(/(\d{3,4})\s*г(г)?\.?/);
      let y = mp ? parseInt(mp[2] || mp[1], 10) : null;
      if (y == null) {
        const cen = it.text.match(/(X{1,3}I{0,3})\s*в/);
        const mapC = { 'XVIII': 1701, 'XIX': 1801, 'XX': 1901 };
        y = cen ? mapC[cen[1]] : null;
      }
      if (y == null) { console.log(`L${l}[${idx}] ord: не распознан год в пункте "${it.text.slice(0, 60)}"`); return; }
      if (prev != null && y < prev) console.log(`L${l}[${idx}] ord: НАРУШЕНИЕ ${prev} -> ${y} в пункте "${it.text.slice(0, 60)}"`);
      prev = Math.max(prev, y);
    });
  });
}

// ---------- 6. fb структура ----------
console.log('\n=== 6. FB СТРУКТУРА ===');
for (const { l, qs } of data) {
  qs.forEach((q, idx) => {
    if (q.type !== 'FillBlank') return;
    const blanks = (q.text.match(/___/g) || []).length;
    if (blanks !== q.blanks.length) console.log(`L${l}[${idx}] fb: ___=${blanks}, blanks=${q.blanks.length}`);
    if (![5, 10].includes(q.candidates.length)) console.log(`L${l}[${idx}] fb: кандидатов ${q.candidates.length}`);
    // одинаковость кандидатов: близкие синонимы не ловятся здесь, только точные
    const seen = {};
    q.candidates.forEach(c => {
      const n = norm(c.text);
      if (seen[n]) console.log(`L${l}[${idx}] fb: кандидаты ${seen[n]} и ${c.id} совпадают: "${c.text}"`);
      seen[n] = c.id;
    });
  });
}

// ---------- 7. mc: дубли опций ----------
console.log('\n=== 7. MC: ДУБЛИ ОПЦИЙ ===');
for (const { l, qs } of data) {
  qs.forEach((q, idx) => {
    if (q.type !== 'MultipleChoice') return;
    const seen = {};
    q.options.forEach(o => {
      const n = norm(o.text);
      if (seen[n]) console.log(`L${l}[${idx}] mc: опции ${seen[n]} и ${o.id} совпадают: "${o.text.slice(0, 70)}"`);
      seen[n] = o.id;
    });
  });
}

console.log('\nAUDIT SCRIPT DONE');
