'use strict';
// Аудит темы history 5-3: попарная сверка 200 вопросов.
// 1) fb-дубли sc/mc того же урока (стем-сходство + сходство ключей/вариантов)
// 2) ord-перекрытия (>=75% пунктов, подмножества) в пределах темы
// 3) позиционные ссылки в info/вариантах (кириллический регэксп)
// 4) sc-ключ заметно длиннее дистракторов или с уникальным пояснением в скобках
const FS = require('fs');
const PATH = require('path');

const LESSONS = [1, 2, 3, 4, 5].map(l => PATH.join(__dirname, 'data/school/history/lessons/5-3-' + l + '.js'));
const theme = LESSONS.map((p, i) => ({ l: i + 1, qs: require(p) }));

// ---------- нормализация ----------
function norm(s) {
  return String(s).toLowerCase().replace(/ё/g, 'е')
    .replace(/[«»"'(){}\[\]—–\-.,;:!?]/g, ' ')
    .replace(/\s+/g, ' ').trim();
}
function stem(s) {
  const STOP = new Set(['и','в','во','не','что','он','на','я','с','со','как','а','то','все','она','так','его','но','да','ты','к','у','же','вы','за','бы','по','только','ее','мне','было','вот','от','меня','еще','нет','о','из','ему','теперь','когда','даже','ну','вдруг','ли','если','уже','или','ни','быть','был','него','до','вас','нибудь','опять','уж','вам','ведь','там','потом','себя','ничего','ей','они','тут','где','есть','надо','ней','для','мы','тебя','их','чем','была','сам','чтоб','без','будто','чего','раз','тоже','себе','под','будет','ж','кто','этот','того','потому','этого','какой','совсем','ним','здесь','этом','один','почти','мой','тем','чтобы','нее','сейчас','были','куда','зачем','всех','никогда','можно','при','наконец','два','об','другой','хоть','после','над','больше','тот','через','эти','нас','про','всего','них','какая','много','разве','три','эту','моя','впрочем','хорошо','свою','этой','перед','иногда','лучше','чуть','том','нельзя','такой','им','более','всегда','конечно','всю','между']);
  return norm(s).split(' ')
    .filter(w => w && !STOP.has(w))
    .map(w => (w.length > 5 ? w.slice(0, 5) : w))
    .join(' ');
}
function tok(s) { return new Set(stem(s).split(' ').filter(Boolean)); }
function jac(a, b) {
  const A = tok(a), B = tok(b);
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  return inter / (A.size + B.size - inter);
}
// грубое «ядро» пункта ord: текст без дат/годов/месяцев
function ordCore(s) {
  return norm(s)
    .replace(/\d+\s*(октября|ноября|марта|мая|июня|июля|августа|сентября|января|февраля|апреля|декабря)?/g, ' ')
    .replace(/\d{4}/g, ' ')
    .replace(/\d+\s*г\b/g, ' ')
    .replace(/\s+/g, ' ').trim();
}
function ordSim(a, b) {
  const A = new Set(stem(ordCore(a)).split(' ').filter(Boolean));
  const B = new Set(stem(ordCore(b)).split(' ').filter(Boolean));
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const x of A) if (B.has(x)) inter++;
  // порог: доля более короткого набора токенов
  return Math.max(inter / A.size, inter / B.size);
}
function ordItemSim(a, b) { return ordSim(a, b) >= 0.72 ? 1 : 0; }

const rows = [];
for (const { l, qs } of theme) {
  qs.forEach((q, idx) => rows.push({ l, idx, q, type: q.type, diff: q.difficulty }));
}

// ---------- 1) fb vs sc/mc в одном уроке ----------
console.log('=== 1. FB vs SC/MC в одном уроке (совпадение стема и ключа) ===');
for (const { l } of [1, 2, 3, 4, 5]) {
  const les = rows.filter(r => r.l === l);
  const fbs = les.filter(r => r.type === 'FillBlank');
  const sm = les.filter(r => r.type === 'SingleChoice' || r.type === 'MultipleChoice');
  for (const f of fbs) {
    const fAns = f.q.candidates.filter(c => f.q.blanks.some(b => b.correctCandidateId === c.id)).map(c => c.text).join(' ');
    for (const s of sm) {
      const stemSim = jac(f.q.text.replace(/_{2,}/g, ' '), s.q.text);
      const keyOpts = s.q.type === 'SingleChoice'
        ? s.q.options.filter(o => o.id === s.q.correctOptionId).map(o => o.text).join(' ')
        : s.q.options.filter(o => s.q.correctOptionIds.includes(o.id)).map(o => o.text).join(' ');
      const keySim = jac(fAns, keyOpts);
      const optPool = s.q.options.map(o => o.text).join(' ');
      const candSim = jac(f.q.candidates.map(c => c.text).join(' '), optPool);
      const score = stemSim * 0.5 + Math.max(keySim, candSim) * 0.5;
      if (stemSim >= 0.45 && (keySim >= 0.5 || candSim >= 0.45)) {
        console.log(`L${l} fb[${f.idx}] <-> ${s.type === 'SingleChoice' ? 'sc' : 'mc'}[${s.idx}] stem=${stemSim.toFixed(2)} key=${keySim.toFixed(2)} cand=${candSim.toFixed(2)}`);
        console.log(`   FB: ${f.q.text}`);
        console.log(`   SM: ${s.q.text}  || KEY: ${keyOpts}`);
      }
    }
  }
}

// ---------- 2) ord-перекрытия внутри темы ----------
console.log('\n=== 2. ORD-перекрытия (пары и подмножества), вся тема ===');
const ords = rows.filter(r => r.type === 'Ordering');
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    const A = ords[i], B = ords[j];
    const itemsA = A.q.items.map(x => x.text), itemsB = B.q.items.map(x => x.text);
    let inter = 0;
    const matched = [];
    for (const a of itemsA) {
      const best = Math.max(...itemsB.map(b => ordItemSim(a, b)));
      if (best) { inter++; matched.push(a); }
    }
    const fracShort = inter / Math.min(itemsA.length, itemsB.length);
    const fracA = inter / itemsA.length, fracB = inter / itemsB.length;
    if (fracShort >= 0.6) {
      const sameLesson = A.l === B.l ? 'L' + A.l : `L${A.l}-L${B.l}`;
      console.log(`${sameLesson} ord[${A.idx}](${itemsA.length}) <-> ord[${B.idx}](${itemsB.length}) inter=${inter} fracShort=${fracShort.toFixed(2)} (${(fracA * 100).toFixed(0)}%/${(fracB * 100).toFixed(0)}%) ${fracA === 1 || fracB === 1 ? 'SUBSET!' : ''}`);
      if (fracShort >= 0.75 || fracA === 1 || fracB === 1) matched.forEach(m => console.log('    • ' + m));
    }
  }
}

// ---------- 3) позиционные ссылки ----------
console.log('\n=== 3. Позиционные ссылки в info/вариантах/пунктах ===');
const POS = /(перв|втор|трет|четв|пят|последн|предпоследн|остальн)[а-яё]*\s+(вариант|пункт|утверждени|шаг|строк|пози|столб|колонк|случа|ответ)/gi;
const POS2 = /вариант\s+[абвгд]\b|под\s+буквой\s+[абвгд]|букв[а-яё]*\s+[абвгд]\b|ответ\s+[абвгд]\b/gi;
const POS3 = /соответственно/gi;
for (const r of rows) {
  const texts = [r.q.info || ''];
  if (r.q.options) texts.push(...r.q.options.map(o => o.text));
  if (r.q.items) texts.push(...r.q.items.map(o => o.text));
  for (const t of texts) {
    for (const RE of [POS, POS2, POS3]) {
      RE.lastIndex = 0;
      const m = t.match(RE);
      if (m) console.log(`L${r.l} ${r.type}[${r.idx}]: ${m.join(', ')}  || ${t.slice(0, 160)}`);
    }
  }
}

// ---------- 4) длина sc-ключа ----------
console.log('\n=== 4. SC-ключ длиннее дистракторов (или с пояснением в скобках) ===');
for (const r of rows.filter(r => r.type === 'SingleChoice')) {
  const key = r.q.options.find(o => o.id === r.q.correctOptionId);
  const dis = r.q.options.filter(o => o.id !== r.q.correctOptionId);
  const avgDis = dis.reduce((s, o) => s + o.text.length, 0) / dis.length;
  const maxDis = Math.max(...dis.map(o => o.text.length));
  const ratio = key.text.length / avgDis;
  const keyParen = /\([^)]{10,}\)/.test(key.text);
  const disParen = dis.some(o => /\([^)]{10,}\)/.test(o.text));
  if (ratio >= 1.5 && key.text.length > maxDis + 25) {
    console.log(`L${r.l} sc[${r.idx}] len=${key.text.length} avgDis=${avgDis.toFixed(0)} maxDis=${maxDis} ratio=${ratio.toFixed(2)}${keyParen && !disParen ? ' KEY-PAREN!' : ''}`);
    console.log(`   KEY: ${key.text}`);
    console.log(`   DIS: ${dis.map(o => o.text.length + ': ' + o.text).join(' | ')}`);
  }
}

// ---------- 5) дубли вопросов по (текст+варианты) в теме, как их ловит гейт ----------
console.log('\n=== 5. Точные дубли вопросов в теме (text+options) ===');
const seen = new Map();
for (const r of rows) {
  const sig = norm(r.q.text) + '|' + (r.q.options ? r.q.options.map(o => norm(o.text)).sort().join('~') : (r.q.items ? r.q.items.map(o => norm(o.text)).sort().join('~') : (r.q.candidates ? r.q.candidates.map(o => norm(o.text)).sort().join('~') : '')));
  if (seen.has(sig)) console.log(`DUP: L${seen.get(sig).l} ${seen.get(sig).type}[${seen.get(sig).idx}] == L${r.l} ${r.type}[${r.idx}]`);
  else seen.set(sig, r);
}

// снимок
FS.writeFileSync(PATH.join(__dirname, 'snap-history-5-3.json'), JSON.stringify(theme.map(({ l, qs }) => ({ l, qs })), null, 1));
console.log('\nsnap-history-5-3.json записан, вопросов: ' + rows.length);
