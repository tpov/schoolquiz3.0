'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wähle die richtige Konjunktiv-II-Form: «Ich ___ gern ein Eis essen.»', op4('werde', 'würde', 'wurde', 'wäre'), 'b', 'Для 1-го лица ед. ч. в Konjunktiv II используется würde + Infinitiv.'),
  sc(1, 'EASY', 'Wähle die richtige Form: «Was ___ du an meiner Stelle machen?»', op4('würdest', 'würde', 'würden', 'würdet'), 'a', 'Для du форма würde получает окончание -st: du würdest.'),
  sc(2, 'EASY', 'Wähle die richtige Form: «Wir ___ lieber zu Hause bleiben.»', op4('würde', 'würdest', 'würden', 'würdet'), 'c', 'Для wir форма würde получает окончание -en: wir würden.'),
  sc(3, 'EASY', 'Welches Wort steht am Ende? «Ich würde gern nach Italien ___.»', op4('fahre', 'fahren', 'fuhr', 'gefahren'), 'b', 'После würde глагол стоит в инфинитиве в самом конце предложения.'),
  sc(4, 'EASY', 'Wofür benutzt man Konjunktiv II? «Ich ___ gern Kaffee trinken.»', op4('würde', 'bin', 'habe', 'wird'), 'a', 'Konjunktiv II würde выражает вежливое желание; bin/habe/wird не образуют форму «бы» с инфинитивом для ich.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wähle die richtige Form: «An deiner Stelle ___ ich den Arzt fragen.»', op4('würdest', 'würde', 'würden', 'würdet'), 'b', 'После обстоятельства глагол стоит на втором месте: würde для ich.'),
  sc(6, 'HARD', 'Wähle die richtige Form: «___ ihr mir bitte helfen?»', op4('Würde', 'Würdest', 'Würdet', 'Würden'), 'c', 'Для ihr форма würde получает окончание -et: ihr würdet.'),
  sc(7, 'HARD', 'Wähle die korrekte Wortstellung: «Sie würde so etwas nie ___.»', op4('sagen', 'sagt', 'gesagt', 'sagte'), 'a', 'Полнозначный глагол sagen остаётся инфинитивом в конце рамки.'),
  sc(8, 'HARD', 'Wähle die richtige Form: «___ Sie mir bitte das Salz reichen?»', op4('Würdest', 'Würdet', 'Würde', 'Würden'), 'd', 'Вежливое Sie требует würden: Würden Sie ... reichen?'),
  sc(9, 'HARD', 'Welcher Satz drückt eine höfliche Bitte aus?', op4('Gib mir das Buch!', 'Würdest du mir das Buch geben?', 'Du gibst mir das Buch.', 'Ich gebe dir das Buch.'), 'b', 'Konjunktiv II würde превращает приказ в вежливую просьбу.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Formen von «würde» sind korrekt konjugiert?', op5('ich würde', 'du würde', 'wir würden', 'er würdet', 'sie würden'), ['a', 'c', 'e'], 'Корректно: ich würde, wir würden, sie würden.'),
  mc(11, 'EASY', 'Welche Sätze stehen korrekt im Konjunktiv II (würde + Infinitiv)?', op5('Ich würde gern essen.', 'Ich würde gern esse.', 'Wir würden bleiben.', 'Wir würde bleiben.', 'Sie würden kommen.'), ['a', 'c', 'e'], 'После würde нужен инфинитив: essen, bleiben, kommen.'),
  mc(12, 'EASY', 'Welche Wörter passen zum Thema «Wunsch und Höflichkeit»?', op5('gern', 'niemals nie', 'der Wunsch', 'das Auto', 'eigentlich'), ['a', 'c', 'e'], 'К теме вежливости и желания относятся: gern, der Wunsch, eigentlich.'),
  mc(13, 'EASY', 'In welchen Sätzen steht der Infinitiv richtig am Ende?', op5('Ich würde gern Musik hören.', 'Ich würde gern hören Musik.', 'Er würde gern reisen.', 'Er würde reisen gern.', 'Wir würden gern tanzen.'), ['a', 'c', 'e'], 'Инфинитив (hören, reisen, tanzen) стоит в самом конце.'),
  mc(14, 'EASY', 'Welche Formen passen zu «du» und «ihr»?', op5('du würdest', 'du würden', 'ihr würdet', 'ihr würde', 'du würdest fragen'), ['a', 'c', 'e'], 'Корректно: du würdest, ihr würdet, du würdest fragen.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekter Konjunktiv II?', op6('Würdest du mir helfen?', 'Würde du mir helfen?', 'Wir würden gern bleiben.', 'Wir würdet gern bleiben.', 'Ich würde nie lügen.', 'Ich würden nie lügen.'), ['a', 'c', 'e'], 'Корректно: würdest du, wir würden, ich würde.'),
  mc(16, 'HARD', 'In welchen Sätzen ist die Endung von «würde» richtig?', op6('ihr würdet warten', 'ihr würden warten', 'sie würden kommen', 'sie würdet kommen', 'er würde gehen', 'er würden gehen'), ['a', 'c', 'e'], 'Корректные окончания: ihr würdet, sie würden, er würde.'),
  mc(17, 'HARD', 'Welche Sätze drücken eine höfliche Bitte oder einen Wunsch aus?', op6('Würden Sie bitte warten?', 'Sie warten jetzt.', 'Ich würde gern mitkommen.', 'Ich komme mit.', 'Würdest du das machen?', 'Du machst das.'), ['a', 'c', 'e'], 'Вежливость и желание передают формы с würde.'),
  mc(18, 'HARD', 'In welchen Sätzen ist die Wortstellung korrekt (Infinitiv am Satzende)?', op6('Sie würde nie so etwas sagen.', 'Sie würde sagen nie so etwas.', 'Ich würde gern ein Buch lesen.', 'Ich würde lesen gern ein Buch.', 'Wir würden gern nach Hause gehen.', 'Wir würden gehen gern nach Hause.'), ['a', 'c', 'e'], 'Инфинитив (sagen, lesen, gehen) замыкает рамочную конструкцию.'),
  mc(19, 'HARD', 'Welche Formen von «würde» sind korrekt?', op6('ich würde', 'ich würdest', 'du würdest', 'du würden', 'wir würden', 'wir würdet'), ['a', 'c', 'e'], 'Корректно: ich würde, du würdest, wir würden.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я бы с удовольствием съел мороженое».', items5('Ich', 'würde', 'gern', 'ein Eis', 'essen'), 'würde стоит на втором месте, инфинитив essen — в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Мы бы лучше остались дома».', items5('Wir', 'würden', 'lieber', 'zu Hause', 'bleiben'), 'würden на втором месте, инфинитив bleiben замыкает рамку.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Ты бы помог мне?».', items4('Würdest', 'du', 'mir', 'helfen'), 'В вопросе würdest стоит на первом месте, helfen — в конце.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Она бы никогда такого не сказала».', items5('Sie', 'würde', 'nie', 'so etwas', 'sagen'), 'würde на втором месте, инфинитив sagen — в конце предложения.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я бы охотно поехал в Италию».', items5('Ich', 'würde', 'gern', 'nach Italien', 'fahren'), 'würde на втором месте, инфинитив fahren замыкает рамку.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Что бы ты сделал на моём месте?».', items5('Was', 'würdest', 'du', 'an meiner Stelle', 'machen'), 'Вопросительное слово Was, затем würdest на втором месте, machen — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «На твоём месте я бы спросил врача».', items5('An deiner Stelle', 'würde', 'ich', 'den Arzt', 'fragen'), 'Обстоятельство стоит первым, würde на втором месте (V2).'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Не могли бы вы мне, пожалуйста, помочь?».', items5('Würden', 'Sie', 'mir', 'bitte', 'helfen'), 'Вежливое Würden Sie в начале вопроса, bitte перед инфинитивом, helfen — в конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Вообще-то я бы лучше остался дома».', items5('Eigentlich', 'würde', 'ich', 'lieber', 'zu Hause bleiben'), 'Eigentlich первым, würde на втором месте, инфинитив в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Вместо этого мы бы охотно прогулялись».', items5('Stattdessen', 'würden', 'wir', 'gern', 'spazieren gehen'), 'Stattdessen на первом месте, würden на втором, инфинитив в конце.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ gern ein Eis essen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('würde', 'würdet', 'wurde', 'wäre', 'würdest'), 'Для ich в Konjunktiv II — würde + Infinitiv.'),
  fb(31, 'EASY', 'Was ___ du an meiner Stelle machen?', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('würde', 'würdest', 'würden', 'würdet', 'wurdest'), 'Для du форма würde с окончанием -st: würdest.'),
  fb(32, 'EASY', 'Wir ___ lieber zu Hause bleiben.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('würde', 'würdest', 'würden', 'würdet', 'wurden'), 'Для wir форма würde с окончанием -en: würden.'),
  fb(33, 'EASY', 'Ich würde gern nach Italien ___.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('fahre', 'fuhr', 'gefahren', 'fahren', 'fährt'), 'После würde глагол стоит в инфинитиве: fahren.'),
  fb(34, 'EASY', '___ ihr mir bitte helfen?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Würde', 'Würden', 'Würdest', 'Wurdet', 'Würdet'), 'Для ihr форма würde с окончанием -et: Würdet.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'An deiner Stelle ___ ich den Arzt fragen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('würde', 'würdest', 'würden', 'würdet', 'wurde'), 'Для ich — würde, глагол на втором месте после обстоятельства.'),
  fb(36, 'HARD', '___ Sie mir bitte das Salz reichen?', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Würdest', 'Würden', 'Würde', 'Würdet', 'Wurden'), 'Вежливое Sie требует Würden Sie ... reichen?'),
  fb(37, 'HARD', 'Sie ___ so etwas nie sagen.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('würdest', 'würdet', 'würde', 'würden ihr', 'wurde'), 'Для sie (она) — würde, инфинитив sagen в конце.'),
  fb(38, 'HARD', 'Stattdessen ___ wir gern spazieren gehen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('würde', 'würdest', 'würdet', 'würden', 'wurden'), 'Для wir — würden, инфинитив gehen замыкает рамку.'),
  fb(39, 'HARD', 'Eigentlich ___ ich lieber zu Hause bleiben.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('würdest', 'würden', 'würdet', 'wurde', 'würde'), 'Для ich — würde, инфинитив bleiben в конце предложения.'),
];
