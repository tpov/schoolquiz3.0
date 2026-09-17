'use strict';
// Аудит-скрипт-b темы history 3-1 (5 уроков, 200 вопросов). Уникальное имя (audit-history-3-1.js занят прошлой попыткой).
// Отличие от -а: стем-сравнение на префиксах по 4 буквы (ловит русские падежные окончания: «Колумбов обмен» vs «Колумбовым обменом»).
const path = require('path');

const BASE = path.join(__dirname, 'data', 'school', 'history');
const lessons = [1, 2, 3, 4, 5].map((l) => {
  delete require.cache[require.resolve(path.join(BASE, 'lessons', `3-1-${l}.js`))];
  return { l, qs: require(path.join(BASE, 'lessons', `3-1-${l}.js`)) };
});

const STOP = new Set(('и в во не что он на я с со как а то все она так его но да ты к у же вы за бы по только ее мне было вот от меня еще нет о из ему теперь когда даже ну вдруг ли если уже или ни быть был него до вас нибудь опять уж вам ведь там потом себя ничего ей может они тут где есть надо ней для мы тебя их чем была сам чтоб без будто человек чего раз тоже себе под жизнь будет ж тогда кто этот говорил того потому этого какой совсем ним здесь этом один почти мой тем чтобы нее кажется сейчас были куда зачем сказать всех никогда сегодня можно при наконец два об другой хоть после над больше тот через эти нас про всего них какая много разве должна почему называют какой какая какое какие кто где когда что верно относится').split(' '));

function norm(s) {
  return String(s || '').toLowerCase().replace(/ё/g, 'е').replace(/[^а-яa-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}
function stemTok(w) {
  return w.length >= 5 ? w.slice(0, 4) : w;
}
function toks(s) {
  return norm(s).split(' ').filter((w) => w.length > 2 && !STOP.has(w)).map(stemTok);
}
function toksRaw(s) {
  return norm(s).split(' ').filter((w) => w.length > 2 && !STOP.has(w));
}
function jacc(a, b) {
  const A = new Set(a), B = new Set(b);
  let inter = 0; for (const x of A) if (B.has(x)) inter++;
  const uni = new Set([...A, ...B]);
  return uni.size ? inter / uni.size : 0;
}
function yearOf(s) {
  const m = /(\d{3,4})/.exec(String(s || '')); return m ? +m[1] : null;
}

// ---- 1. Попарные дубли sc/mc/fb ----
const all = [];
lessons.forEach(({ l, qs }) => qs.forEach((q, i) => all.push({ l, i, q })));

function optsOf(q) { return (q.options || q.candidates || []).map((o) => o.text); }
function keysOf(q) {
  if (q.type === 'SingleChoice') return [(q.options.find((o) => o.id === q.correctOptionId) || {}).text];
  if (q.type === 'MultipleChoice') return q.correctOptionIds.map((id) => (q.options.find((o) => o.id === id) || {}).text);
  if (q.type === 'FillBlank') return q.candidates.filter((c) => q.blanks.some((b) => b.correctCandidateId === c.id)).map((c) => c.text);
  return [];
}
function keySim(a, b) {
  const A = keysOf(a), B = keysOf(b);
  let best = 0;
  for (const x of A) for (const y of B) {
    const nx = norm(x), ny = norm(y);
    if (nx === ny) { best = 1; continue; }
    const s = jacc(toks(x), toks(y));
    if (s > best) best = s;
  }
  return best;
}
function bodySim(a, b) { return jacc(optsOf(a).map(norm), optsOf(b).map(norm)); }
function stemSim(a, b) { return jacc(toks(a.text), toks(b.text)); }

console.log('=== 1. ПОДОЗРИТЕЛЬНЫЕ ПАРЫ (sc/mc/fb): стем + ключ + варианты ===');
const seen = new Set();
for (let i = 0; i < all.length; i++) {
  for (let j = i + 1; j < all.length; j++) {
    const A = all[i], B = all[j];
    const ta = A.q.type, tb = B.q.type;
    if (ta === 'Ordering' || tb === 'Ordering') continue;
    const ks = keySim(A.q, B.q), ss = stemSim(A.q, B.q), bs = bodySim(A.q, B.q);
    const crossFb = (ta === 'FillBlank') !== (tb === 'FillBlank');
    const flagKey = ks >= 0.999 && (ss >= 0.42 || bs >= 0.34);
    const flagKeyStem = ks >= 0.74 && ks < 0.999 && ss >= 0.5; // падежные варианты ключа + близкий стем
    const flagBody = bs >= 0.5 && ks >= 0.5 && (crossFb || ss >= 0.3);
    if (flagKey || flagKeyStem || flagBody) {
      const id = `${A.l}-${A.i}|${B.l}-${B.i}`;
      if (seen.has(id)) continue; seen.add(id);
      console.log(`L${A.l}q${A.i}(${ta[0]},key:${keysOf(A.q).join('/')})  <->  L${B.l}q${B.i}(${tb[0]},key:${keysOf(B.q).join('/')}): stem=${ss.toFixed(2)} body=${bs.toFixed(2)} key=${ks.toFixed(2)}${crossFb ? ' [fb-cross]' : ''}`);
      console.log(`   A: ${A.q.text.slice(0, 110)}`);
      console.log(`   B: ${B.q.text.slice(0, 110)}`);
    }
  }
}

// ---- 2. ord: попарные совпадения пунктов ----
console.log('\n=== 2. ORD: пересечение пунктов (>=75% = дефект) ===');
const ords = all.filter((x) => x.q.type === 'Ordering');
function itemsMatch(a, b) {
  const na = norm(a), nb = norm(b);
  if (na === nb) return 1;
  const ya = yearOf(a), yb = yearOf(b);
  const js = jacc(toks(a), toks(b));
  if (ya && ya === yb && js >= 0.3) return Math.max(js, 0.8);
  return js >= 0.55 ? js : 0;
}
const ordPairFlags = [];
for (let i = 0; i < ords.length; i++) {
  for (let j = i + 1; j < ords.length; j++) {
    const A = ords[i], B = ords[j];
    const Ait = A.q.items.map((x) => x.text), Bit = B.q.items.map((x) => x.text);
    let matched = [];
    for (const a of Ait) for (const b of Bit) { const s = itemsMatch(a, b); if (s > 0) matched.push([a, b, s]); }
    const ratio = matched.length / Math.min(Ait.length, Bit.length);
    if (ratio >= 0.75) {
      ordPairFlags.push(1);
      console.log(`L${A.l}q${A.i} x L${B.l}q${B.i}: ${matched.length}/${Math.min(Ait.length, Bit.length)} = ${(ratio * 100).toFixed(0)}%`);
      matched.forEach(([a, b, s]) => console.log(`   (${s.toFixed(2)}) «${a}» ~ «${b}»`));
    }
  }
}
if (!ordPairFlags.length) console.log('(нет пар >= 75%)');
console.log('--- ord: хронология годов (раньший год после позднего) и недатированные ---');
let ordChronBad = 0;
ords.forEach(({ l, i, q }) => {
  let prev = -Infinity;
  q.items.forEach((it, k) => {
    const y = yearOf(it.text);
    if (y == null) { ordChronBad++; console.log(`L${l}q${i} п.${k + 1} без года: ${it.text}`); }
    else { if (y < prev) { ordChronBad++; console.log(`L${l}q${i} п.${k + 1}: год ${y} после ${prev}!`); } prev = Math.max(prev, y); }
  });
});
if (!ordChronBad) console.log('(хронология в items корректна, недатированных нет)');

// ---- 3. Позиционные ссылки (кириллица) ----
console.log('\n=== 3. ПОЗИЦИОННЫЕ ССЫЛКИ ===');
const POS = [
  /[А-Яа-яЁё]{0,}(перв|втор|трет|четв|пят|шест|седьм|последн|предпоследн|остальн|оставш)[а-яё]*\s+(вариант|пункт|шаг|ответ|утверждени|запис|строк|случа|объект|элемент|позици|услови|половин|часть|кандидат)[а-яё]*/gi,
  /[А-Яа-яЁё]{0,}(вариант|пункт|утверждени|кандидат)[а-яё]*\s+[«"']?[абвгде]/gi,
  /соответственно/gi,
  /(вариант|пункт|шаг|утверждение)\s*[абвгд][)\s.]/gi,
];
let posHits = 0;
all.forEach(({ l, i, q }) => {
  const fields = [['info', q.info], ['text', q.text]];
  (q.options || q.candidates || q.items || []).forEach((o, k) => fields.push([`opt${k}`, o.text]));
  fields.forEach(([f, v]) => {
    const s = String(v || '');
    POS.forEach((re) => {
      const m = s.match(re);
      if (m) { posHits++; console.log(`L${l}q${i} [${f}]: ${m.join(' | ')} :: ${s.slice(0, 90)}`); }
    });
  });
});
if (!posHits) console.log('(позссылок нет)');

// ---- 4. Оформление ключа ----
console.log('\n=== 4. ОФОРМЛЕНИЕ КЛЮЧА (sc/mc) ===');
all.forEach(({ l, i, q }) => {
  if (q.type !== 'SingleChoice' && q.type !== 'MultipleChoice') return;
  const correct = new Set(q.type === 'SingleChoice' ? [q.correctOptionId] : q.correctOptionIds);
  const co = q.options.filter((o) => correct.has(o.id));
  const di = q.options.filter((o) => !correct.has(o.id));
  if (!di.length) return;
  const maxD = Math.max(...di.map((o) => o.text.length));
  co.forEach((o) => {
    const r = o.text.length / Math.max(1, maxD);
    const hasParen = /\(/.test(o.text);
    const diParen = di.filter((d) => /\(/.test(d.text)).length;
    if (r >= 1.6 && o.text.length - maxD > 25) console.log(`L${l}q${i}: ключ в ${r.toFixed(1)}x длиннее дистракторов: «${o.text.slice(0, 80)}» (max дистр ${maxD})`);
    if (hasParen && diParen === 0) console.log(`L${l}q${i}: у ключа скобки, у дистракторов нет: «${o.text.slice(0, 90)}»`);
  });
});

// ---- 5. Дубли опций/кандидатов внутри вопроса ----
console.log('\n=== 5. ДУБЛИ ОПЦИЙ/КАНДИДАТОВ ВНУТРИ ВОПРОСА ===');
let optDup = 0;
all.forEach(({ l, i, q }) => {
  const os = optsOf(q).map(norm);
  for (let a = 0; a < os.length; a++) for (let b = a + 1; b < os.length; b++) {
    if (os[a] && os[a] === os[b]) { optDup++; console.log(`L${l}q${i}: точный дубль «${os[a]}»`); }
    else if (os[a] && os[b] && jacc(toks(optsOf(q)[a]), toks(optsOf(q)[b])) > 0.8) { optDup++; console.log(`L${l}q${i}: почти-дубль «${optsOf(q)[a]}» ~ «${optsOf(q)[b]}»`); }
  }
});
if (!optDup) console.log('(дублей опций нет)');

// ---- 6. Тон (оценочные суждения) ----
console.log('\n=== 6. ТОН: маркеры оценочности ===');
const TONE = /(варварск|коварн|кровожадн|жесток|беспощадн|славн|героическ|подл|труслив|безумн|жажда золота|дик(?!т)|отстал|цивилизованн|просвещенн|мрачн|ужасн|чудовищн|позорн|трагичн|злодеян)/gi;
let toneHits = 0;
all.forEach(({ l, i, q }) => {
  const fields = [q.text, q.info, ...(q.options || q.candidates || q.items || []).map((o) => o.text)];
  fields.forEach((v, k) => {
    const m = String(v || '').match(TONE);
    if (m) { toneHits++; console.log(`L${l}q${i} [поле ${k}]: ${m.join(', ')} | ${String(v).slice(0, 80)}`); }
  });
});
if (!toneHits) console.log('(оценочных маркеров нет)');

// ---- 7. fb: структурная вменяемость ----
console.log('\n=== 7. FB: кандидаты ===');
all.forEach(({ l, i, q }) => {
  if (q.type !== 'FillBlank') return;
  const texts = q.candidates.map((c) => norm(c.text));
  if (new Set(texts).size !== texts.length) console.log(`L${l}q${i}: повтор кандидатов`);
  if (q.candidates.length !== 5 && q.candidates.length !== 10) console.log(`L${l}q${i}: кандидатов ${q.candidates.length}`);
  if (q.blanks.length !== 1) console.log(`L${l}q${i}: пропусков ${q.blanks.length}`);
  const correctIds = new Set(q.blanks.map((b) => b.correctCandidateId));
  q.candidates.forEach((c) => {
    if (!correctIds.has(c.id) && toksRaw(c.text).length && jacc(toks(q.text), toks(c.text)) >= 0.55) {
      console.log(`L${l}q${i}: дистрактор «${c.text}» близок к тексту вопроса (проверь вручную)`);
    }
  });
});
console.log('\nOK');
