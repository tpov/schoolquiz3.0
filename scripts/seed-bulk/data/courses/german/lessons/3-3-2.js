'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Ich ___ nach Hause gegangen.', op4('bin', 'habe', 'hat', 'ist'), 'a', 'Глагол gehen (движение) образует Perfekt с sein, в 1-м лице — ich bin.'),
  sc(1, 'EASY', 'Wann ___ du gekommen?', op4('hast', 'bist', 'habe', 'ist'), 'b', 'Kommen — глагол движения, поэтому du bist gekommen.'),
  sc(2, 'EASY', 'Wir ___ mit dem Zug nach Berlin gefahren.', op4('haben', 'seid', 'sind', 'sein'), 'c', 'Fahren — глагол движения, форма wir sind.'),
  sc(3, 'EASY', 'Sie (она) ___ nach Spanien geflogen.', op4('hat', 'haben', 'bist', 'ist'), 'd', 'Fliegen — глагол движения, в 3-м лице ед. ч. она ist geflogen.'),
  sc(4, 'EASY', 'Am Sonntag ___ ich zu Hause geblieben.', op4('bin', 'habe', 'ist', 'sind'), 'a', 'Bleiben образует Perfekt с sein, в 1-м лице — ich bin.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Welcher Satz ist korrekt?', op4('Ich habe nach Hause gegangen.', 'Ich bin nach Hause gegangen.', 'Ich bin nach Hause gegeht.', 'Ich habe nach Hause gegeht.'), 'b', 'Gehen — движение (sein) с неправильным Partizip II gegangen.'),
  sc(6, 'HARD', 'Was ist das Partizip II von "fliegen"?', op4('gefliegt', 'fliegte', 'geflogen', 'gefliegen'), 'c', 'Fliegen — сильный глагол, его Partizip II — geflogen.'),
  sc(7, 'HARD', 'Welches Hilfsverb passt: "Das Kind ___ schnell eingeschlafen."', op4('hat', 'ist', 'habt', 'seid'), 'b', 'Einschlafen — изменение состояния, поэтому das Kind ist.'),
  sc(8, 'HARD', 'Ihr ___ gestern lange im Park geblieben.', op4('habt', 'sind', 'seid', 'bin'), 'c', 'Bleiben идёт с sein, форма 2-го лица мн. ч. — ihr seid.'),
  sc(9, 'HARD', 'Was ist das Partizip II von "sein"?', op4('geseint', 'gewesen', 'geseen', 'gesein'), 'b', 'Partizip II глагола sein — особая форма gewesen.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Verben bilden das Perfekt mit "sein"? (mehrere Antworten)', op5('gehen', 'kaufen', 'kommen', 'essen', 'fahren'), ['a', 'c', 'e'], 'Gehen, kommen и fahren — глаголы движения, поэтому идут с sein.'),
  mc(11, 'EASY', 'In welchen Sätzen steht das richtige Hilfsverb?', op5('Ich bin gekommen.', 'Du bist gegangen.', 'Wir haben gefahren.', 'Sie ist geflogen.', 'Er hat geblieben.'), ['a', 'b', 'd'], 'Глаголы движения kommen, gehen, fliegen требуют sein.'),
  mc(12, 'EASY', 'Welche Partizipien gehören zu Bewegungsverben?', op5('gegangen', 'gekauft', 'gekommen', 'gemacht', 'gefahren'), ['a', 'c', 'e'], 'Gegangen, gekommen, gefahren — это Partizip II глаголов движения.'),
  mc(13, 'EASY', 'Welche Formen von "sein" sind richtig?', op6('ich bin', 'du bist', 'er hat', 'wir sind', 'ihr habt', 'sie sind'), ['a', 'b', 'd', 'f'], 'Спряжение sein: ich bin, du bist, wir sind, sie sind.'),
  mc(14, 'EASY', 'Welche Sätze beschreiben eine Bewegung mit "sein"?', op5('Sie ist nach Hause gegangen.', 'Er hat ein Buch gelesen.', 'Wir sind zum Bahnhof gefahren.', 'Ich habe gegessen.', 'Du bist gekommen.'), ['a', 'c', 'e'], 'Движение к месту (gehen, fahren, kommen) выражается через sein.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Verben bilden das Perfekt mit "sein"?', op6('aufstehen', 'lesen', 'einschlafen', 'sterben', 'arbeiten', 'werden'), ['a', 'c', 'd', 'f'], 'Изменение состояния (aufstehen, einschlafen, sterben) и werden идут с sein.'),
  mc(16, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Wir sind nach Berlin gefahren.', 'Ich bin müde geworden.', 'Er hat nach Hause gegangen.', 'Sie ist geblieben.', 'Du hast geflogen.'), ['a', 'b', 'd'], 'Fahren, werden и bleiben образуют Perfekt с sein.'),
  mc(17, 'HARD', 'Welche Partizip-II-Formen sind richtig?', op6('gegangen', 'gekommt', 'gefahren', 'gebleiben', 'geflogen', 'geblieben'), ['a', 'c', 'e', 'f'], 'Правильные сильные формы: gegangen, gefahren, geflogen, geblieben.'),
  mc(18, 'HARD', 'In welchen Sätzen passt "sein" als Hilfsverb?', op5('Das Kind ___ eingeschlafen.', 'Ich ___ ein Auto gekauft.', 'Er ___ um sieben aufgestanden.', 'Wir ___ Pizza gegessen.', 'Sie ___ alt geworden.'), ['a', 'c', 'e'], 'Einschlafen, aufstehen и werden — изменение состояния, поэтому с sein.'),
  mc(19, 'HARD', 'Welche Aussagen über das Perfekt mit "sein" stimmen?', op5('Bewegungsverben mit Ortswechsel nutzen sein.', 'Alle Verben nutzen sein.', 'sein, bleiben, werden nutzen sein.', 'Zustandsänderung nutzt sein.', 'Das Partizip steht am Anfang.'), ['a', 'c', 'd'], 'Sein используют глаголы движения, изменения состояния и sein/bleiben/werden.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я вчера пошёл домой».', items5('Ich', 'bin', 'gestern', 'nach Hause', 'gegangen'), 'Рамка: sein на 2-м месте (bin), Partizip II gegangen — в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Мы поехали в Берлин».', items4('Wir', 'sind', 'nach Berlin', 'gefahren'), 'Sein (sind) на 2-м месте, Partizip II gefahren — в конце.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Она улетела в Испанию».', items5('Sie', 'ist', 'nach', 'Spanien', 'geflogen'), 'Ist на 2-м месте, Partizip II geflogen завершает предложение.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Ты пришёл вовремя».', items4('Du', 'bist', 'pünktlich', 'angekommen'), 'Bist на 2-м месте, Partizip II angekommen — в конце.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я остался дома».', items4('Ich', 'bin', 'zu Hause', 'geblieben'), 'Bin на 2-м месте, Partizip II geblieben — в конце.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Когда ты пришёл вчера?».', items5('Wann', 'bist', 'du', 'gestern', 'gekommen'), 'В вопросе с вопросительным словом sein (bist) на 2-м месте, Partizip II — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Дети вчера рано легли спать».', items5('Die Kinder', 'sind', 'gestern', 'früh', 'eingeschlafen'), 'Sind на 2-м месте, Partizip II eingeschlafen — в самом конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Мы на поезде поехали на вокзал».', items5('Wir', 'sind', 'mit dem Zug', 'zum Bahnhof', 'gefahren'), 'Рамка: sind на 2-м месте, Partizip II gefahren — в конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «В воскресенье я остался дома».', items5('Am Sonntag', 'bin', 'ich', 'zu Hause', 'geblieben'), 'После обстоятельства времени sein (bin) на 2-м месте, подлежащее за ним, Partizip II — в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Она позже стала врачом».', items5('Sie', 'ist', 'später', 'Ärztin', 'geworden'), 'Werden идёт с sein: ist на 2-м месте, Partizip II geworden — в конце.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Ich ___ nach Hause gegangen.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('bin', 'habe', 'hat', 'ist', 'sind'), 'Gehen — движение, в 1-м лице ich bin.'),
  fb(31, 'EASY', 'Du ___ pünktlich gekommen.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('hast', 'bist', 'habe', 'ist', 'bin'), 'Kommen — движение, du bist gekommen.'),
  fb(32, 'EASY', 'Wir ___ nach Berlin gefahren.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('haben', 'seid', 'sind', 'bin', 'ist'), 'Fahren — движение, wir sind gefahren.'),
  fb(33, 'EASY', 'Sie (она) ___ nach Spanien geflogen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('hat', 'habe', 'sind', 'ist', 'bist'), 'Fliegen — движение, она ist geflogen.'),
  fb(34, 'EASY', 'Ich ___ zu Hause geblieben.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('habe', 'hat', 'ist', 'sind', 'bin'), 'Bleiben идёт с sein, ich bin geblieben.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Das Partizip II von "gehen" ist "___".', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('gegangen', 'gegeht', 'gegehen', 'gehte', 'gegangt'), 'Gehen — сильный глагол, Partizip II — gegangen.'),
  fb(36, 'HARD', 'Ihr ___ lange im Park geblieben.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('habt', 'seid', 'sind', 'bin', 'ist'), 'Bleiben с sein, во 2-м лице мн. ч. — ihr seid.'),
  fb(37, 'HARD', 'Das Kind ___ schnell eingeschlafen.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('hat', 'habt', 'ist', 'bin', 'seid'), 'Einschlafen — изменение состояния, das Kind ist.'),
  fb(38, 'HARD', 'Er ___ Lehrer geworden.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('hat', 'habt', 'sind', 'ist', 'bin'), 'Werden идёт с sein, er ist geworden.'),
  fb(39, 'HARD', 'Das Partizip II von "sein" ist "___".', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('geseint', 'gesein', 'geseen', 'geseinen', 'gewesen'), 'Особая форма Partizip II глагола sein — gewesen.'),
];
