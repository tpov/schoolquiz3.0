'use strict';
// Аудит темы history 4-2 (5 уроков × 40 вопросов): дубли fb↔sc/mc, ord-перенасыщение,
// ord-подмножества, позиционные ссылки, выдающее ответ оформление, хронология ord.
const fs = require('fs');
const path = require('path');

const LESSONS = [1, 2, 3, 4, 5].map(l => path.join(__dirname, 'data', 'school', 'history', 'lessons', `4-2-${l}.js`));
const SNAP = path.join(__dirname, 'snap-history-4-2.json');

// ---------- нормализация ----------
const STOP = new Set(['и', 'в', 'во', 'не', 'на', 'с', 'со', 'из', 'за', 'по', 'у', 'о', 'об', 'от', 'до', 'для', 'что', 'как', 'это', 'при', 'которые', 'который', 'которая', 'было', 'был', 'была', 'были', 'его', 'их', 'она', 'они', 'он', 'the', 'гг', 'г']);

function stemWord(w) {
  if (w.length <= 4) return w;
  const endings = ['иями', 'ями', 'ами', 'иях', 'ях', 'ах', 'ов', 'ев', 'ей', 'ой', 'ий', 'ый', 'ая', 'яя', 'ое', 'ее', 'ые', 'ие', 'ем', 'ом', 'ам', 'ым', 'им', 'ую', 'ешь', 'ение', 'ание', 'ость', 'а', 'я', 'ы', 'и', 'у', 'ю', 'е', 'ь'];
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
  if (!Array.isArray(arr) || arr.length !== 40) throw new Error(`lesson 4-2-${i + 1}: ${Array.isArray(arr) ? arr.length : 'not array'} questions`);
  return { l: i + 1, qs: arr };
});

// снимок для контроля правок
const snap = {};
for (const { l, qs } of data) snap[`4-2-${l}`] = qs.map((q, idx) => ({
  idx, type: q.type, difficulty: q.difficulty, text: q.text,
  options: optTexts(q), key: keyTexts(q), info: q.info,
}));
fs.writeFileSync(SNAP, JSON.stringify(snap, null, 1));
console.log(`SNAPSHOT written: ${SNAP} (${Object.keys(snap).length} lessons, ${Object.values(snap).reduce((s, a) => s + a.length, 0)} questions)`);

// ---------- 1. Дубли fb ↔ sc/mc и вообще все пары внутри урока ----------
console.log('\n=== 1. ПОХОЖИЕ ПАРЫ ВНУТРИ УРОКА (стем-Jaccard стема + Jaccard вариантов/кандидатов) ===');
for (const { l, qs } of data) {
  for (let i = 0; i < qs.length; i++) {
    for (let j = i + 1; j < qs.length; j++) {
      const A = qs[i], B = qs[j];
      const isFB = q => q.type === 'FillBlank';
      const crossType = isFB(A) !== isFB(B);
      const sA = stems(A.text), sB = stems(B.text);
      const sj = jac(sA, sB);
      const oj = jac(optTexts(A).map(norm), optTexts(B).map(norm));
      const keyOverlap = jac(keyTexts(A).map(norm), keyTexts(B).map(norm));
      const interesting = crossType ? (sj >= 0.30 || oj >= 0.4) : (sj >= 0.45 && oj >= 0.35);
      if (interesting) {
        console.log(`L${l} [${i}]${A.type[0]}${A.difficulty[0]} "${norm(A.text).slice(0, 75)}"`);
        console.log(`   [${j}]${B.type[0]}${B.difficulty[0]} "${norm(B.text).slice(0, 75)}"`);
        console.log(`   stemJ=${sj.toFixed(2)} optJ=${oj.toFixed(2)} keyJ=${keyOverlap.toFixed(2)}`);
      }
    }
  }
}

// ---------- 2. ord: перенасыщение и подмножества (внутри темы, нормированные пункты) ----------
console.log('\n=== 2. ORD: ПЕРЕКРЫТИЯ ПУНКТОВ (порог отчёта 50%, дефект 75%) ===');
const ords = [];
data.forEach(({ l, qs }) => qs.forEach((q, idx) => { if (q.type === 'Ordering') ords.push({ l, idx, q }); }));
function ordItemSet(o) {
  return o.q.items.map(it => {
    const n = norm(it.text).replace(/около|начало|первая половина|середина|конец|гг?/g, ' ').replace(/\s+/g, ' ').trim();
    return n.split(' ').map(stemWord).join(' ');
  });
}
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    const O = ords[i], T = ords[j];
    const sO = ordItemSet(O), sT = ordItemSet(T);
    const inter = sO.filter(x => sT.includes(x));
    const frac = Math.max(inter.length / sO.length, inter.length / sT.length);
    if (frac >= 0.5) {
      console.log(`L${O.l}[${O.idx}] vs L${T.l}[${T.idx}] overlap=${frac.toFixed(2)} (${inter.length}/${Math.min(sO.length, sT.length)}) общие: ${inter.length ? inter.join(' | ').slice(0, 220) : '—'}`);
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

// ---------- 3. Позиционные ссылки (свой регэксп под кириллицу) ----------
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
console.log('\n=== 5. ХРОНОЛОГИЯ ORD ===');
for (const { l, qs } of data) {
  qs.forEach((q, idx) => {
    if (q.type !== 'Ordering') return;
    let prev = null;
    q.items.forEach(it => {
      const m = it.text.match(/(\d{3,4})/);
      let y = m ? parseInt(m[1], 10) : null;
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
  });
}

// ---------- 7. mc: точные дубли опций ----------
console.log('\n=== 7. MC: ДУБЛИ ОПЦИЙ (точные) ===');
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

// ---------- 8. Похожие пары МЕЖДУ уроками (все 200 вопросов темы попарно) ----------
console.log('\n=== 8. ПОХОЖИЕ ПАРЫ МЕЖДУ УРОКАМИ ===');
const all = [];
data.forEach(({ l, qs }) => qs.forEach((q, idx) => all.push({ l, idx, q })));
let crossHits = 0;
for (let i = 0; i < all.length; i++) {
  for (let j = i + 1; j < all.length; j++) {
    const A = all[i], B = all[j];
    if (A.l === B.l) continue;
    const sj = jac(stems(A.q.text), stems(B.q.text));
    const oj = jac(optTexts(A.q).map(norm), optTexts(B.q).map(norm));
    const kj = jac(keyTexts(A.q).map(norm), keyTexts(B.q).map(norm));
    if ((sj >= 0.45 && oj >= 0.45) || (kj >= 0.9 && sj >= 0.3)) {
      crossHits++;
      console.log(`L${A.l}[${A.idx}]${A.q.type[0]} vs L${B.l}[${B.idx}]${B.q.type[0]} stemJ=${sj.toFixed(2)} optJ=${oj.toFixed(2)} keyJ=${kj.toFixed(2)}`);
      console.log(`   "${norm(A.q.text).slice(0, 80)}"`);
      console.log(`   "${norm(B.q.text).slice(0, 80)}"`);
    }
  }
}
console.log(`cross-lesson candidate pairs: ${crossHits}`);

// ---------- 8b. ORD-перекрытия между уроками ----------
console.log('\n=== 8b. ORD-ПЕРЕКРЫТИЯ МЕЖДУ УРОКАМИ (порог 50%) ===');
let crossOrd = 0;
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    if (ords[i].l === ords[j].l) continue;
    const sO = ordItemSet(ords[i]), sT = ordItemSet(ords[j]);
    const inter = sO.filter(x => sT.includes(x));
    const frac = Math.max(inter.length / sO.length, inter.length / sT.length);
    if (frac >= 0.5) {
      crossOrd++;
      console.log(`L${ords[i].l}[${ords[i].idx}] vs L${ords[j].l}[${ords[j].idx}] overlap=${frac.toFixed(2)}: ${inter.join(' | ').slice(0, 160)}`);
    }
  }
}
console.log(`cross-lesson ord overlaps >= 0.5: ${crossOrd}`);
