'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie heißt es richtig? «Это красный стол.»', op4('Das ist ein roter Tisch.', 'Das ist ein rote Tisch.', 'Das ist ein rotes Tisch.', 'Das ist eine roter Tisch.'), 'a', 'У существительного мужского рода с ein прилагательное-цвет в именительном падеже получает окончание -er: ein roter Tisch.'),
  sc(1, 'EASY', 'Wie heißt es richtig? «Это синяя дверь.»', op4('Das ist ein blaue Tür.', 'Das ist eine blaue Tür.', 'Das ist eine blauer Tür.', 'Das ist eine blaues Tür.'), 'b', 'Существительное Tür женского рода, поэтому eine и окончание -e: eine blaue Tür.'),
  sc(2, 'EASY', 'Wie heißt es richtig? «Это зелёная книга.»', op4('Das ist ein grüner Buch.', 'Das ist ein grüne Buch.', 'Das ist ein grünes Buch.', 'Das ist eine grünes Buch.'), 'c', 'Buch среднего рода, с ein прилагательное получает окончание -es: ein grünes Buch.'),
  sc(3, 'EASY', 'Welches Wort bedeutet «коричневый»?', op4('grau', 'braun', 'rosa', 'lila'), 'b', 'Слово braun по-русски значит «коричневый».'),
  sc(4, 'EASY', 'Wie heißt es richtig? «У меня розовая лампа.»', op4('Ich habe eine rosa Lampe.', 'Ich habe eine rosae Lampe.', 'Ich habe eine roser Lampe.', 'Ich habe ein rosa Lampe.'), 'a', 'Цвет rosa не склоняется, поэтому остаётся без окончания: eine rosa Lampe.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wie heißt es richtig? «Это новый коричневый стол.»', op4('Das ist ein neuer brauner Tisch.', 'Das ist ein neue braune Tisch.', 'Das ist ein neues braunes Tisch.', 'Das ist eine neuer brauner Tisch.'), 'a', 'Оба прилагательных перед мужским Tisch с ein получают окончание -er: ein neuer brauner Tisch.'),
  sc(6, 'HARD', 'Welcher Satz ist richtig?', op4('Der Tisch ist roter.', 'Der Tisch ist rot.', 'Der Tisch ist rotes.', 'Der Tisch ist rote.'), 'b', 'После глагола ist прилагательное стоит без окончания: Der Tisch ist rot.'),
  sc(7, 'HARD', 'Wie heißt es richtig? «Это фиолетовая вещь.»', op4('Das ist eine lilae Sache.', 'Das ist eine lilar Sache.', 'Das ist eine lila Sache.', 'Das ist ein lila Sache.'), 'c', 'Цвет lila не склоняется, а Sache женского рода требует eine: eine lila Sache.'),
  sc(8, 'HARD', 'Wie heißt es richtig? «Это серая дверь.»', op4('Das ist ein graue Tür.', 'Das ist eine grauer Tür.', 'Das ist eine graue Tür.', 'Das ist eine graues Tür.'), 'c', 'Tür женского рода, поэтому eine и окончание -e: eine graue Tür.'),
  sc(9, 'HARD', 'Wie heißt es richtig? «Это оранжевая книга.»', op4('Das ist ein orange Buch.', 'Das ist ein oranger Buch.', 'Das ist ein oranges Buch.', 'Das ist eine oranges Buch.'), 'c', 'Buch среднего рода, с ein окончание -es: ein oranges Buch.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Sätze sind richtig?', op5('Das ist ein roter Stuhl.', 'Das ist ein rote Stuhl.', 'Das ist eine blaue Tür.', 'Das ist eine blauer Tür.', 'Das ist ein grünes Buch.'), ['a', 'c', 'e'], 'Верны фразы с правильными окончаниями: -er для мужского, -e для женского и -es для среднего рода.'),
  mc(11, 'EASY', 'Welche Wörter sind Farben (цвета)?', op5('braun', 'grau', 'die Sache', 'neu', 'lila'), ['a', 'b', 'e'], 'Цвета здесь — braun, grau и lila, а Sache и neu цветами не являются.'),
  mc(12, 'EASY', 'Welche Farben werden NICHT verändert (не склоняются)?', op6('rosa', 'rot', 'lila', 'blau', 'grün', 'braun'), ['a', 'c'], 'Цвета rosa и lila не склоняются и всегда стоят без окончания.'),
  mc(13, 'EASY', 'Welche Sätze passen zu «среднему роду» (das Buch)?', op5('Das ist ein grünes Buch.', 'Das ist ein grüner Buch.', 'Das ist ein blaues Buch.', 'Das ist ein blaue Buch.', 'Das ist ein rotes Buch.'), ['a', 'c', 'e'], 'У среднего рода Buch с ein прилагательное получает окончание -es.'),
  mc(14, 'EASY', 'Welche Sätze sind richtig?', op5('Ich habe eine rosa Lampe.', 'Ich habe eine rosae Lampe.', 'Das ist eine lila Sache.', 'Das ist eine lilae Sache.', 'Das ist ein neuer Tisch.'), ['a', 'c', 'e'], 'Цвета rosa и lila остаются без окончания, а neuer перед мужским Tisch верно.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op6('Das ist ein roter Tisch.', 'Das ist ein rotes Tisch.', 'Das ist eine graue Tür.', 'Das ist eine grauer Tür.', 'Das ist ein blaues Buch.', 'Das ist ein blauer Buch.'), ['a', 'c', 'e'], 'Окончания зависят от рода: -er (m), -e (f), -es (n) после ein/eine.'),
  mc(16, 'HARD', 'Welche Sätze passen zu «женскому роду» (die Tür)?', op5('Das ist eine neue Tür.', 'Das ist eine neuer Tür.', 'Das ist eine braune Tür.', 'Das ist eine braunes Tür.', 'Das ist eine graue Tür.'), ['a', 'c', 'e'], 'У женского рода Tür с eine прилагательное получает окончание -e.'),
  mc(17, 'HARD', 'In welchen Sätzen steht die Farbe OHNE Endung richtig?', op5('Der Stuhl ist blau.', 'Der Stuhl ist blaue.', 'Die Lampe ist rosa.', 'Das Buch ist grünes.', 'Die Tür ist grau.'), ['a', 'c', 'e'], 'После ist прилагательное-цвет стоит без окончания.'),
  mc(18, 'HARD', 'Welche Sätze sind richtig?', op6('Das ist ein neuer brauner Tisch.', 'Das ist ein neue braune Tisch.', 'Das ist eine neue blaue Tür.', 'Das ist eine neuer blauer Tür.', 'Das ist ein neues grünes Buch.', 'Das ist ein neuer grünes Buch.'), ['a', 'c', 'e'], 'Оба прилагательных согласуются с родом: -er (m), -e (f), -es (n).'),
  mc(19, 'HARD', 'Welche Aussagen über Endungen sind richtig?', op5('ein roter Tisch — мужской род', 'ein rotes Tür — женский род', 'eine rote Tür — женский род', 'ein roter Buch — средний род', 'ein rotes Buch — средний род'), ['a', 'c', 'e'], 'Правильно: -er у мужского, -e у женского, -es у среднего рода.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Это красный стул.»', items4('Das', 'ist', 'ein', 'roter Stuhl.'), 'В именительном падеже мужского рода с ein цвет получает окончание -er: ein roter Stuhl.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Это синяя дверь.»', items4('Das', 'ist', 'eine', 'blaue Tür.'), 'У женского рода Tür артикль eine и окончание -e: eine blaue Tür.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Это зелёная книга.»', items4('Das', 'ist', 'ein', 'grünes Buch.'), 'У среднего рода Buch с ein окончание -es: ein grünes Buch.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «У меня розовая лампа.»', items4('Ich', 'habe', 'eine', 'rosa Lampe.'), 'Цвет rosa не склоняется и стоит без окончания: eine rosa Lampe.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Стол красный.»', items4('Der', 'Tisch', 'ist', 'rot.'), 'После ist цвет стоит без окончания: Der Tisch ist rot.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Это новый коричневый стол.»', items5('Das', 'ist', 'ein', 'neuer', 'brauner Tisch.'), 'Оба прилагательных перед мужским Tisch получают окончание -er: ein neuer brauner Tisch.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Это новая серая дверь.»', items5('Das', 'ist', 'eine', 'neue', 'graue Tür.'), 'Оба прилагательных перед женским Tür получают окончание -e: eine neue graue Tür.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Это новая фиолетовая вещь.»', items5('Das', 'ist', 'eine', 'neue', 'lila Sache.'), 'Цвет lila не склоняется, а neue согласуется с женским Sache: eine neue lila Sache.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Это новая зелёная книга.»', items5('Das', 'ist', 'ein', 'neues', 'grünes Buch.'), 'Оба прилагательных перед средним Buch получают окончание -es: ein neues grünes Buch.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Это новый оранжевый стул.»', items5('Das', 'ist', 'ein', 'neuer', 'oranger Stuhl.'), 'Оба прилагательных перед мужским Stuhl получают окончание -er: ein neuer oranger Stuhl.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Das ist ein ___ Tisch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('roter', 'rote', 'rotes', 'rot', 'roten'), 'У мужского рода Tisch с ein цвет получает окончание -er: ein roter Tisch.'),
  fb(31, 'EASY', 'Das ist eine ___ Tür.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('blauer', 'blaue', 'blaues', 'blau', 'blauen'), 'У женского рода Tür с eine цвет получает окончание -e: eine blaue Tür.'),
  fb(32, 'EASY', 'Das ist ein ___ Buch.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('grüner', 'grüne', 'grünes', 'grün', 'grünen'), 'У среднего рода Buch с ein цвет получает окончание -es: ein grünes Buch.'),
  fb(33, 'EASY', 'Ich habe eine ___ Lampe.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('rosaer', 'rosae', 'rosar', 'rosa', 'rosaes'), 'Цвет rosa не склоняется и остаётся без окончания: eine rosa Lampe.'),
  fb(34, 'EASY', 'Der Tisch ist ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('roter', 'rote', 'rotes', 'roten', 'rot'), 'После ist цвет стоит без окончания: Der Tisch ist rot.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Das ist ein neuer ___ Tisch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('brauner', 'braune', 'braunes', 'braun', 'braunen'), 'Перед мужским Tisch оба прилагательных получают окончание -er: ein neuer brauner Tisch.'),
  fb(36, 'HARD', 'Das ist eine neue ___ Tür.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('grauer', 'graue', 'graues', 'grau', 'grauen'), 'Перед женским Tür оба прилагательных получают окончание -e: eine neue graue Tür.'),
  fb(37, 'HARD', 'Das ist ein neues ___ Buch.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('oranger', 'orange', 'oranges', 'orangen', 'orang'), 'Перед средним Buch оба прилагательных получают окончание -es: ein neues oranges Buch.'),
  fb(38, 'HARD', 'Das ist eine neue ___ Sache.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('lilaer', 'lilar', 'lilaes', 'lila', 'lilae'), 'Цвет lila не склоняется, поэтому стоит без окончания: eine neue lila Sache.'),
  fb(39, 'HARD', 'Das ist ein neuer ___ Stuhl.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('brauner', 'braune', 'braunes', 'braun', 'braunen'), 'Перед мужским Stuhl оба прилагательных получают окончание -er: ein neuer brauner Stuhl.'),
];
