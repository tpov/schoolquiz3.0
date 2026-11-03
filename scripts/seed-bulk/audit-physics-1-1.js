'use strict';
// Аудит темы physics 1-1: механическая верификация арифметики, ord, fb, дублей, позиционных ссылок.
const path = require('path');
const lessons = [];
for (let l = 1; l <= 5; l++) {
  const file = path.join(__dirname, 'data/school/physics/lessons', `1-1-${l}.js`);
  const arr = require(file);
  lessons.push({ l, qs: arr.map((q, i) => ({ i, ...q })) });
}

const out = { arith: [], ordChecks: [], fbChecks: [], dups: [], posRefs: [], optDups: [] };
const approx = (a, b, eps = 0.06) => Math.abs(a - b) <= eps;

// ---------- 1. Арифметика: пересчёт каждой числовой задачи ----------
function check(name, actual, expect, eps) {
  const ok = eps !== undefined ? approx(actual, expect, eps) : actual === expect;
  out.arith.push(`${ok ? 'OK ' : 'FAIL'} ${name}: расчёт=${actual} заявлено=${expect}`);
}

// 1-1-1
check('1-1-1 q3 36км/ч', 36 / 3.6, 10);
check('1-1-1 q5 20+2', 20 + 2, 22);
check('1-1-1 q6 72/3.6-1', 72 / 3.6 - 1, 19);
check('1-1-1 q7 (15+1)*20', (15 + 1) * 20, 320);
check('1-1-1 q9 0.02км/с', 0.02 * 1000, 20);
check('1-1-1 q9 50км/ч', 50 / 3.6, 13.9, 0.06);
check('1-1-1 q9 800м/мин', 800 / 60, 13.3, 0.06);
check('1-1-1 q9 90км/ч(mc14)', 90 / 3.6, 25);
check('1-1-1 q9 18км/ч(mc14)', 18 / 3.6, 5);
check('1-1-1 q9 5м/с->км/ч(mc14)', 5 * 3.6, 18);
check('1-1-1 q9 54км/ч(mc14)', 54 / 3.6, 15);
check('1-1-1 mc15 sвагон', 1 * 20, 20);
check('1-1-1 mc15 vземля', 1 + 15, 16);
check('1-1-1 mc15 sземля', 16 * 20, 320);
check('1-1-1 mc19 v', 0.5 + 0.75, 1.25);
check('1-1-1 mc19 t', 60 / 1.25, 48);
check('1-1-1 mc19 sст', 0.5 * 48, 24);
check('1-1-1 mc19 tстоя', 60 / 0.75, 80);
check('1-1-1 ord25 18км/ч', 18 / 3.6, 5);
check('1-1-1 ord25 50км/ч', 50 / 3.6, 13.9, 0.06);
check('1-1-1 ord27 пешеход', 1 * 60, 60);
check('1-1-1 ord27 вел', (18 / 3.6) * 60, 300);
check('1-1-1 ord27 авто', 15 * 60, 900);
check('1-1-1 ord27 поезд', (144 / 3.6) * 60, 2400);
check('1-1-1 ord27 самолёт', 200 * 60, 12000);
check('1-1-1 ord29 20+1', 20 + 1, 21);
check('1-1-1 ord29 20+20', 20 + 20, 40);
check('1-1-1 fb35', (1 + 15) * 20, 320);
check('1-1-1 fb36', 72 / 3.6, 20);
check('1-1-1 fb39', 0.5 + 0.75, 1.25);

// 1-1-2
check('1-1-2 q5 путь', 20 + 10, 30);
check('1-1-2 q5 перемещ', 20 - 10, 10);
check('1-1-2 q6 полуокр', Math.PI * 10, 31.4, 0.06);
check('1-1-2 q6 диаметр', 2 * 10, 20);
check('1-1-2 q7 пифагор', Math.hypot(3, 4), 5);
check('1-1-2 q8 полная', 2 * Math.PI * 10, 62.8, 0.06);
check('1-1-2 q9 пифагор км', Math.hypot(6, 8), 10);
check('1-1-2 mc15 четверть путь', (2 * Math.PI * 10) / 4, 15.7, 0.06);
check('1-1-2 mc15 хорда', 10 * Math.SQRT2, 14.1, 0.06);
check('1-1-2 mc18 3/4 путь', 0.75 * 2 * Math.PI * 10, 47.1, 0.06);
check('1-1-2 mc18 отношение', 47.1 / 14.1, 3.3, 0.06);
check('1-1-2 ord22 треть хорда', 2 * 10 * Math.sin(Math.PI / 3), 17.3, 0.06);
check('1-1-2 ord28 половина отн', 31.4 / 20, 1.6, 0.06);
check('1-1-2 ord28 четверть отн', 15.7 / 14.1, 1.1, 0.06);
check('1-1-2 fb35', 2 * 10, 20);
check('1-1-2 fb36', Math.hypot(8, 6), 10);
check('1-1-2 fb37', 20 + 10, 30);
check('1-1-2 fb38', (2 * Math.PI * 10) / 4, 15.7, 0.06);

// 1-1-3
check('1-1-3 q3', 2 - 5, -3);
check('1-1-3 q4 cos60', 10 * Math.cos(Math.PI / 3), 5);
check('1-1-3 q5 AB', Math.abs(-1 - 3), 4);
check('1-1-3 q5 BC', Math.abs(1 - 4), 3);
check('1-1-3 q6', Math.hypot(-4, -3), 5);
check('1-1-3 q8 vx', 20 * Math.cos(Math.PI / 6), 17.3, 0.06);
check('1-1-3 q8 vy', 20 * Math.sin(Math.PI / 6), 10);
check('1-1-3 q9', -6 + 2 * 5, 4);
check('1-1-3 mc12 sin45', Math.sin(Math.PI / 4), 0.71, 0.01);
check('1-1-3 mc15 dx', 10 * Math.cos(Math.PI / 6), 8.7, 0.06);
check('1-1-3 mc15 dy', 10 * Math.sin(Math.PI / 6), 5);
check('1-1-3 mc17 x3', -6 + 2 * 3, 0);
check('1-1-3 mc18 сумм', Math.hypot(3, 4), 5);
check('1-1-3 ord27 5', Math.hypot(3, 4), 5);
check('1-1-3 ord27 10', Math.hypot(-6, 8), 10);
check('1-1-3 ord27 13', Math.hypot(5, 12), 13);
check('1-1-3 ord27 17', Math.hypot(8, -15), 17);
check('1-1-3 ord27 29', Math.hypot(-20, 21), 29);
check('1-1-3 ord28 45', 10 * Math.cos(Math.PI / 4), 7.1, 0.06);
check('1-1-3 ord28 30', 10 * Math.cos(Math.PI / 6), 8.7, 0.06);
check('1-1-3 fb35', 1 - 4, -3);
check('1-1-3 fb36', Math.hypot(-4, -3), 5);
check('1-1-3 fb37', 20 * Math.sin(Math.PI / 6), 10);
check('1-1-3 fb38', -6 + 2 * 5, 4);

// 1-1-4
check('1-1-4 q1', 5 + 2, 7);
check('1-1-4 q2', 5 - 2, 3);
check('1-1-4 q3', 15 + 20, 35);
check('1-1-4 q5', Math.hypot(3, 4), 5);
check('1-1-4 q6', 60 / 3, 20);
check('1-1-4 q7', 4 * 20, 80);
check('1-1-4 q8', 5 * 20, 100);
check('1-1-4 q8 проверка', Math.hypot(60, 80), 100);
check('1-1-4 q9', 100 / (25 - 20), 20);
check('1-1-4 mc15 t', 60 / 3, 20);
check('1-1-4 mc15 снос', 4 * 20, 80);
check('1-1-4 mc15 v', Math.hypot(3, 4), 5);
check('1-1-4 mc15 путь', 5 * 20, 100);
check('1-1-4 mc17', 20 + 30, 50);
check('1-1-4 mc17 догон', 30 - 20, 10);
check('1-1-4 mc19 по', 120 / (6 + 2), 15);
check('1-1-4 mc19 против', 120 / (6 - 2), 30);
check('1-1-4 mc19 сумма', 15 + 30, 45);
check('1-1-4 ord21 поперёк', Math.hypot(5, 2), 5.4, 0.06);
check('1-1-4 ord23 прям угол', Math.hypot(12, 8), 14.4, 0.06);
check('1-1-4 ord26 плот', 120 / 2, 60);
check('1-1-4 ord26 1м/с', 120 / 1, 120);
check('1-1-4 ord28 догон30/12', 30 - 12, 18);
check('1-1-4 fb35', 60 / 3, 20);
check('1-1-4 fb36', 4 * 20, 80);
check('1-1-4 fb37', Math.hypot(3, 4), 5);
check('1-1-4 fb38', 15 + 20, 35);

// 1-1-5
check('1-1-5 q2', 120 / 2, 60);
check('1-1-5 q2 в СИ', 60 / 3.6, 16.7, 0.06);
check('1-1-5 q5 гарм', (2 * 60 * 40) / (60 + 40), 48);
check('1-1-5 q6 арифм', (60 + 40) / 2, 50);
check('1-1-5 q7', 100 / (20 + 30), 2);
check('1-1-5 q8 t1', (1 / 3) / 30, 1 / 90, 1e-9);
check('1-1-5 q8 t2', (2 / 3) / 60, 1 / 90, 1e-9);
check('1-1-5 q8 vср', 45);
check('1-1-5 q9', (90 * 2 + 60 * 3) / (2 + 3), 72);
check('1-1-5 mc16 путь', 100 + 0 + 150, 250);
check('1-1-5 mc16 время', 20 + 30 + 50, 100);
check('1-1-5 mc16 vср', 250 / 100, 2.5);
check('1-1-5 mc16 уч1', 100 / 20, 5);
check('1-1-5 mc17', 400 / 50, 8);
check('1-1-5 mc19', 45);
check('1-1-5 ord21', [100 / 50, 100 / 25, 100 / 10, 100 / 5].join(','), '2,4,10,20');
check('1-1-5 ord22', [120 / 4, 120 / 3, 120 / 2, 120 / 1].join(','), '30,40,60,120');
check('1-1-5 ord25 сложение', 1 / 120 + 1 / 80, 1 / 48, 1e-9);
check('1-1-5 ord26 24', (2 * 20 * 30) / (20 + 30), 24);
check('1-1-5 ord26 40', (2 * 30 * 60) / (30 + 60), 40);
check('1-1-5 ord26 48', (2 * 40 * 60) / (40 + 60), 48);
check('1-1-5 ord27', [100 / 100, 100 / 50, 100 / 25, 100 / 20, 100 / 10].join(','), '1,2,4,5,10');
check('1-1-5 fb32', 120 / 2, 60);
check('1-1-5 fb35', (2 * 60 * 40) / 100, 48);
check('1-1-5 fb37 при v1=v2', (2 * 50 * 50) / (50 + 50), 50);
check('1-1-5 fb38', 100 / 50, 2);
check('1-1-5 fb39', 360 / 5, 72);

// ---------- 2. ord: извлекаемые числа — строгая монотонность ----------
const numRe = /-?\d+(?:,\d+)?/;
lessons.forEach(({ l, qs }) => {
  qs.forEach(({ i, type, items, text }) => {
    if (type !== 'Ordering') return;
    const nums = items.map((it) => {
      const m = it.text.match(/—\s*(-?\d+(?:[.,]\d+)?)\s*(м|м\/с|с|км\/ч|м²)?\s*$/);
      return m ? parseFloat(m[1].replace(',', '.')) : null;
    });
    if (nums.every((n) => n !== null)) {
      const strict = nums.every((n, k) => k === 0 || n > nums[k - 1]);
      out.ordChecks.push(`${strict ? 'OK ' : 'FAIL'} 1-1-${l} q${i}: [${nums.join(', ')}] strict=${strict} :: ${text.slice(0, 60)}`);
    } else {
      out.ordChecks.push(`NOTE 1-1-${l} q${i}: нечисловой ord (проверен вручную) :: ${text.slice(0, 60)}`);
    }
  });
});

// ---------- 3. fb: уникальность текстов кандидатов ----------
lessons.forEach(({ l, qs }) => {
  qs.forEach(({ i, type, candidates }) => {
    if (type !== 'FillBlank') return;
    const texts = candidates.map((c) => c.text);
    const dup = texts.some((t, k) => texts.indexOf(t) !== k);
    if (dup) out.fbChecks.push(`FAIL 1-1-${l} q${i}: дубликаты кандидатов`);
    const blanks = candidates.length;
    if (blanks !== 5 && blanks !== 10) out.fbChecks.push(`FAIL 1-1-${l} q${i}: кандидатов ${blanks}`);
  });
});

// ---------- 4. Дубли по сути: (тип, нормализованный текст) и (тип, текст+варианты) ----------
const seen = new Map();
lessons.forEach(({ l, qs }) => {
  qs.forEach(({ i, type, text, options, items, candidates }) => {
    const norm = text.toLowerCase().replace(/[^a-zа-яё0-9]/gi, '');
    const variantKey = [options && options.map((o) => o.text).join('|'), items && items.map((o) => o.text).join('|'), candidates && candidates.map((o) => o.text).join('|')].filter(Boolean).join('#');
    const k1 = `${type}:${norm}`;
    const k2 = `${type}:${norm}:${variantKey.toLowerCase().replace(/[^a-zа-яё0-9|]/gi, '')}`;
    for (const k of [k1, k2]) {
      if (seen.has(k)) {
        const prev = seen.get(k);
        out.dups.push(`совпадение ${k.startsWith(k1) ? 'ТЕКСТА' : 'ТЕКСТ+ВАРИАНТЫ'}: 1-1-${prev.l} q${prev.i} == 1-1-${l} q${i} (${type})`);
      } else {
        seen.set(k, { l, i });
      }
    }
  });
});

// ---------- 5. Позиционные ссылки в info/вариантах/пунктах ----------
const posRe = /(первые\s+(?:два|три|четыре|пять)|последн\w+\s+(?:вариант|пункт|утверждение|строка)|в\s+(?:первом|втором|третьем|четвёртом|последнем)\s+(?:варианте|пункте|случае выше)|вариант[аы]?\s+[абвгд]\b|пункта?х?\s+[1-5]\b|(?:выше|ниже)\s*(?:перечисленн|указанн|названн)|из\s+(?:них|перечисленных)\s+(?:последние|первые)|предыдущ\w+\s+(?:вариант|пункт|шаг)|следующ\w+\s+(?:вариант|пункт)\b)/i;
lessons.forEach(({ l, qs }) => {
  qs.forEach(({ i, text, info, options, items }) => {
    const fields = { info, opt: options && options.map((o) => o.text).join(' / '), items: items && items.map((o) => o.text).join(' / ') };
    for (const [fname, val] of Object.entries(fields)) {
      if (val && posRe.test(val)) out.posRefs.push(`1-1-${l} q${i} [${fname}]: ${val.match(posRe)[0]} :: ${val.slice(0, 90)}`);
    }
  });
});

// ---------- 6. Дубликаты вариантов внутри одного вопроса ----------
lessons.forEach(({ l, qs }) => {
  qs.forEach(({ i, options, items, candidates }) => {
    const lists = [options, items, candidates].filter(Boolean);
    lists.forEach((list) => {
      const t = list.map((o) => o.text.trim());
      t.forEach((x, k) => { if (t.indexOf(x) !== k) out.optDups.push(`1-1-${l} q${i}: повтор варианта «${x}»`); });
    });
  });
});

console.log('=== АРИФМЕТИКА ===');
console.log(out.arith.filter((s) => s.startsWith('FAIL')).join('\n') || `все ${out.arith.length} проверок OK`);
console.log('=== ORD ===');
console.log(out.ordChecks.filter((s) => s.startsWith('FAIL')).join('\n') || 'все числовые ord строго монотонны');
console.log(out.ordChecks.filter((s) => s.startsWith('NOTE')).join('\n'));
console.log('=== FB ===');
console.log(out.fbChecks.join('\n') || 'fb: структура в норме');
console.log('=== ДУБЛИ ===');
console.log(out.dups.join('\n') || 'дублей не найдено');
console.log('=== ПОЗИЦИОННЫЕ ССЫЛКИ ===');
console.log(out.posRefs.join('\n') || 'позиционных ссылок не найдено');
console.log('=== ПОВТОРЫ ВАРИАНТОВ ===');
console.log(out.optDups.join('\n') || 'повторов вариантов нет');
