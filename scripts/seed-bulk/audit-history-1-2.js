'use strict';
// Уникальный временный скрипт аудита темы history 1-2 (дубли, позиционные ссылки, оформление, тон)
const path = require('path');
const subj = 'history';
const files = [1, 2, 3, 4, 5].map(l => path.join(__dirname, 'data', 'school', subj, 'lessons', `1-2-${l}.js`));

let qs = [];
for (let i = 0; i < files.length; i++) {
  require(files[i]).forEach((q, j) => qs.push({ lesson: i + 1, lidx: j, q }));
}

function normalize(s) {
  return (s || '').toLowerCase().replace(/ё/g, 'е').replace(/[«»"'`()]/g, ' ').replace(/[^a-zа-я0-9]+/g, ' ').trim();
}
function toks(s) { return normalize(s).split(' ').filter(w => w.length > 2); }
function stemTok(s) { return toks(s).map(w => w.slice(0, 5)); }
function stemKey(s) { return stemTok(s).sort().join(' '); }
function jac(a, b) {
  const A = new Set(a), B = new Set(b);
  const inter = [...A].filter(x => B.has(x)).length;
  const uni = new Set([...a, ...b]).size;
  return uni ? inter / uni : 0;
}
function optStems(list) { return list.map(o => stemTok(o).sort().join(' ')); }
function texts(q) { return (q.options ? q.options.map(o => o.text) : q.items ? q.items.map(o => o.text) : q.candidates ? q.candidates.map(o => o.text) : []); }
function correctIds(q) {
  if (q.type === 'SingleChoice') return [q.correctOptionId];
  if (q.type === 'MultipleChoice') return q.correctOptionIds;
  if (q.type === 'FillBlank') return q.blanks.map(b => b.correctCandidateId);
  return [];
}

console.log('=== 1. ПОЗИЦИОННЫЕ ССЫЛКИ ===');
const posRe = /(перв[а-яё]*\s+(вариант|пункт|утверждени|шаг|строк|запис|положени|столбец|столбц)|втор[а-яё]*\s+(вариант|пункт|утверждени|шаг|строк|запис)|последн[а-яё]*\s+(вариант|пункт|утверждени|вариантов|пунктов|утверждений|два|три|четы|двух|трёх|четырёх|строк|запис)|остальн[а-ыяо]*\s+(вариант|пункт|утверждени|записи|два|три)|вариант\s+[абвгде]\b|пункт[а-ы]*\s+[1-9]|шаг[а-яё]*\s+[1-9]|соответственно|в\s+(первом|втором|третьем|четвёртом|пятом|последнем)\s+(варианте|пункте|утверждении|шаге|случае|столбце|списке)|из\s+пунктов|из\s+вариантов|в\s+вариантах\s+[абвгде])/i;
for (const { lesson, lidx, q } of qs) {
  const parts = [[q.text, 'text'], [q.info, 'info'], [texts(q).join(' | '), 'options']];
  for (const [s, src] of parts) {
    const m = (s || '').match(posRe);
    if (m) console.log(`L${lesson}#${lidx} [${src}] "${m[0]}" :: ${(s || '').slice(0, 130)}`);
  }
}

console.log('\n=== 2. ПОЧТИ-ДУБЛИ (стем с основами + общие варианты) ===');
const metas = qs.map(x => ({ ...x, stem: stemKey(x.q.text), toks: stemTok(x.q.text), os: optStems(texts(x.q)), type: x.q.type }));
for (let i = 0; i < metas.length; i++) {
  for (let j = i + 1; j < metas.length; j++) {
    const A = metas[i], B = metas[j];
    if (A.type !== B.type) continue;
    const sj = jac(A.toks, B.toks);
    const sameStem = A.stem === B.stem;
    const common = A.os.filter(o => B.os.includes(o)).length;
    const minN = Math.min(A.os.length, B.os.length);
    const sameAns = jac(correctIds(A.q), correctIds(B.q)).length > 0 || A.q.type === 'Ordering';
    if ((sameStem || sj >= 0.6) && common >= Math.max(2, Math.ceil(minN / 2))) {
      console.log(`L${A.lesson}#${A.lidx} vs L${B.lesson}#${B.lidx} ${A.type} stemJ=${sj.toFixed(2)} sameStem=${sameStem} common=${common}/${minN} sameAns=${sameAns}`);
      console.log(`   A: ${A.q.text.slice(0, 110)}`);
      console.log(`   B: ${B.q.text.slice(0, 110)}`);
    } else if (sj >= 0.75 && common >= 1 && A.type === 'Ordering') {
      console.log(`L${A.lesson}#${A.lidx} vs L${B.lesson}#${B.lidx} ORD stemJ=${sj.toFixed(2)} common=${common}`);
      console.log(`   A: ${A.q.text.slice(0, 100)}`);
      console.log(`   B: ${B.q.text.slice(0, 100)}`);
    }
  }
}

console.log('\n=== 3. ОФОРМЛЕНИЕ: ключ длиннее дистракторов ===');
for (const { lesson, lidx, q } of qs) {
  if (!q.options) continue;
  const cids = new Set(correctIds(q));
  const textsO = q.options.map(o => o.text);
  q.options.forEach((o, k) => {
    if (!cids.has(o.id)) return;
    const distr = textsO.filter((_, m) => !cids.has(q.options[m].id));
    const avg = distr.reduce((s, d) => s + d.length, 0) / distr.length;
    const maxD = Math.max(...distr.map(d => d.length));
    if (o.text.length > avg * 1.5 && o.text.length - maxD > 12) {
      console.log(`L${lesson}#${lidx} key="${o.text}" (${o.text.length}) avg=${avg.toFixed(0)} max=${maxD}`);
    }
  });
}

console.log('\n=== 4. ТОН ===');
const toneRe = /(велики[ймх]|велик[аяое]\s|мудр[а-яё]+|прогрессивн[а-яё]+|кровав[а-яё]+|жесток[а-яё]+|ужасн[а-яё]+|трагичн[а-яё]+|славн[а-яё]+|чудесн[а-яё]+|прекрасн[а-яё]+|варвар[а-яё]*|мрачн[а-яё]+|отстал[а-яё]+|передов[а-яё]+|культурн[а-яё]+\s+геро|зло[йе]\b|добр[а-яё]+\s)/gi;
for (const { lesson, lidx, q } of qs) {
  for (const [s, src] of [[q.text, 'text'], [q.info, 'info'], [texts(q).join(' | '), 'options']]) {
    let m; const re = new RegExp(toneRe.source, 'gi');
    while ((m = re.exec(s || ''))) {
      console.log(`L${lesson}#${lidx} [${src}] "${m[0]}" :: ...${s.slice(Math.max(0, m.index - 45), m.index + 45).replace(/\n/g, ' ')}...`);
    }
  }
}

console.log('\n=== 5. FB: структ. проверки ===');
for (const { lesson, lidx, q } of qs) {
  if (q.type !== 'FillBlank') continue;
  const blanks = (q.text.match(/_{2,}/g) || []).length;
  if (blanks !== 1 || q.blanks.length !== 1) console.log(`L${lesson}#${lidx} blanksInText=${blanks} blanksArr=${q.blanks.length}`);
  if (q.candidates.length !== 5) console.log(`L${lesson}#${lidx} candidates=${q.candidates.length}`);
  const cids = q.candidates.map(c => c.id);
  for (const b of q.blanks) if (!cids.includes(b.correctCandidateId)) console.log(`L${lesson}#${lidx} bad correctCandidateId`);
}

console.log('\n=== 6. MC с 4+ верными ===');
for (const { lesson, lidx, q } of qs) {
  if (q.type === 'MultipleChoice' && q.correctOptionIds.length >= 4) console.log(`L${lesson}#${lidx} ${q.correctOptionIds.length}/${q.options.length} верных :: ${q.text.slice(0, 80)}`);
}
console.log('\nDONE. Вопросов:', qs.length);

console.log('\n=== 7. КРОСС-ТИПОВЫЕ sc/fb ПОЧТИ-ДУБЛИ ===');
const fbq = qs.filter(x => x.q.type === 'FillBlank').map(x => ({ ...x, os: optStems(texts(x.q)), toks: stemTok(x.q.text) }));
const scq = qs.filter(x => x.q.type === 'SingleChoice').map(x => ({ ...x, os: optStems(texts(x.q)), toks: stemTok(x.q.text) }));
for (const A of scq) for (const B of fbq) {
  const common = A.os.filter(o => B.os.includes(o)).length;
  const sj = jac(A.toks, B.toks);
  if (common >= 2 && sj >= 0.3) {
    const aAns = A.q.correctOptionId, bAns = B.q.blanks.map(b => b.correctCandidateId);
    const aAnsT = A.q.options.find(o => o.id === aAns).text;
    const bAnsT = B.q.candidates.find(c => c.id === bAns[0]).text;
    const aSt = stemTok(aAnsT).sort().join(''), bSt = stemTok(bAnsT).sort().join('');
    console.log(`L${A.lesson}#${A.lidx}(sc) vs L${B.lesson}#${B.lidx}(fb) common=${common} stemJ=${sj.toFixed(2)} sameAns=${aSt === bSt || aSt.includes(bSt) || bSt.includes(aSt)}`);
    console.log(`   sc: ${A.q.text.slice(0, 100)}`);
    console.log(`   fb: ${B.q.text.slice(0, 100)}`);
  }
}
