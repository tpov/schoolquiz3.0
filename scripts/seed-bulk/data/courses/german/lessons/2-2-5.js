'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Meine Mutter ist ___. (моя мама дружелюбная)', op4('freundlich', 'der Hund', 'kochen', 'gern'), 'a', 'После sein характер называют прилагательным без окончания.'),
  sc(1, 'EASY', 'Wie sagt man «весёлый»?', op4('ruhig', 'lustig', 'streng', 'klug'), 'b', '«lustig» означает «весёлый».'),
  sc(2, 'EASY', 'Mein Bruder ___ Hunde. (мой брат любит собак)', op4('ist', 'und', 'mag', 'gern'), 'c', 'Глагол «mögen» в 3-м лице — «mag».'),
  sc(3, 'EASY', 'Meine Oma kocht ___. (бабушка любит готовить)', op4('nett', 'der Charakter', 'mögen', 'gern'), 'd', '«gern» с глаголом показывает, что человек делает это с удовольствием.'),
  sc(4, 'EASY', 'Was bedeutet «klug»?', op4('умный', 'строгий', 'спокойный', 'милый'), 'a', '«klug» переводится как «умный».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Mein Vater ist ruhig, ___ mein Bruder ist laut. (а брат шумный)', op4('ist', 'aber', 'mag', 'gern'), 'b', 'Союз «aber» противопоставляет две фразы (контраст «спокойный — шумный»).'),
  sc(6, 'HARD', 'Welcher Satz ist korrekt?', op4('Sie mag Hunde.', 'Sie mögt Hunde.', 'Sie mag Hunden.', 'Sie mag der Hund.'), 'a', 'В 3-м лице — «mag», а дополнение «Hunde» стоит в Akkusativ.'),
  sc(7, 'HARD', 'Meine Schwester ist nett ___ klug. (милая и умная)', op4('aber', 'mag', 'und', 'gern'), 'c', 'Союз «und» соединяет два прилагательных характера.'),
  sc(8, 'HARD', 'Welche Form ist richtig: «Er ___ Musik»?', op4('magst', 'mögen', 'mögt', 'mag'), 'd', 'Для «er» правильная форма глагола «mögen» — «mag».'),
  sc(9, 'HARD', 'Welcher Satz beschreibt den Charakter korrekt?', op4('Meine Oma ist freundlich und nett.', 'Meine Oma ist freundliche.', 'Meine Oma freundlich ist.', 'Meine Oma ist die freundlich.'), 'a', 'После sein прилагательное идёт без окончания, а глагол стоит на втором месте.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter beschreiben den Charakter? (выберите все)', op5('freundlich', 'lustig', 'der Hund', 'kochen', 'ruhig'), ['a', 'b', 'e'], 'Прилагательные характера: freundlich, lustig, ruhig.'),
  mc(11, 'EASY', 'Welche Sätze sind korrekt?', op5('Sie ist nett.', 'Er ist lustig.', 'Sie ist der nett.', 'Er ist lustige.', 'Sie ist freundlich.'), ['a', 'b', 'e'], 'После sein прилагательное стоит без окончания и без артикля.'),
  mc(12, 'EASY', 'Welche Wörter bedeuten eine positive Eigenschaft?', op5('nett', 'klug', 'streng', 'freundlich', 'laut'), ['a', 'b', 'd'], 'Положительные черты — nett, klug, freundlich.'),
  mc(13, 'EASY', 'In welchen Sätzen wird «gern» richtig benutzt?', op5('Sie kocht gern.', 'Er liest gern.', 'Sie gern kocht.', 'Er ist gern.', 'Wir spielen gern.'), ['a', 'b', 'e'], '«gern» ставят после глагола, чтобы показать любимое занятие.'),
  mc(14, 'EASY', 'Welche Wörter sind Verben?', op5('mögen', 'kochen', 'ruhig', 'lesen', 'nett'), ['a', 'b', 'd'], 'Глаголы здесь — mögen, kochen, lesen.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze mit «mögen» sind grammatisch korrekt?', op6('Er mag Musik.', 'Sie mag Hunde.', 'Er mögt Musik.', 'Wir mögen Tiere.', 'Sie mag den Musik.', 'Du magst Katzen.'), ['a', 'b', 'd', 'f'], 'Формы mögen: er/sie mag, wir mögen, du magst, а дополнение — в Akkusativ.'),
  mc(16, 'HARD', 'Welche Sätze verbinden zwei Aussagen korrekt?', op5('Er ist ruhig, aber sie ist laut.', 'Sie ist nett und klug.', 'Er ist ruhig aber sie.', 'Sie ist nett und.', 'Wir sind freundlich und lustig.'), ['a', 'b', 'e'], 'Союзы «und» и «aber» соединяют две полные фразы.'),
  mc(17, 'HARD', 'Welche Sätze beschreiben eine Person korrekt?', op6('Meine Mutter ist freundlich.', 'Mein Vater ist streng.', 'Meine Oma ist die nett.', 'Mein Bruder ist sehr lustig.', 'Meine Schwester ist kluge.', 'Mein Opa ist ruhig.'), ['a', 'b', 'd', 'f'], 'После sein нет ни артикля, ни окончания у прилагательного.'),
  mc(18, 'HARD', 'In welchen Sätzen steht das Verb richtig auf Position zwei?', op5('Meine Mutter kocht gern.', 'Mein Bruder mag Musik.', 'Meine Oma gern kocht.', 'Mein Vater Hunde mag.', 'Wir lesen gern Bücher.'), ['a', 'b', 'e'], 'В главном предложении спрягаемый глагол стоит на втором месте (V2).'),
  mc(19, 'HARD', 'Welche Sätze passen zu einem Mini-Porträt der Familie?', op6('Das ist meine Mutter.', 'Sie ist freundlich und klug.', 'Sie ist die freundlich.', 'Sie mag Musik.', 'Sie mögt Musik.', 'Ich liebe sie.'), ['a', 'b', 'd', 'f'], 'Корректный портрет: представление, характер без окончаний и mögen с Akkusativ.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Моя мама дружелюбная».', items4('Meine', 'Mutter', 'ist', 'freundlich'), 'Порядок: подлежащее, глагол на втором месте, затем прилагательное.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Мой брат любит собак».', items4('Mein', 'Bruder', 'mag', 'Hunde'), 'Глагол «mag» стоит на втором месте, дополнение «Hunde» в Akkusativ.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Моя бабушка милая».', items4('Meine', 'Oma', 'ist', 'nett'), 'После «ist» прилагательное идёт без окончания.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мой папа спокойный».', items4('Mein', 'Vater', 'ist', 'ruhig'), 'Глагол sein («ist») стоит на втором месте.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Моя сестра любит готовить».', items4('Meine', 'Schwester', 'kocht', 'gern'), '«gern» ставится после глагола «kocht».'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Моя мама дружелюбная и любит готовить».', items5('Meine Mutter', 'ist', 'freundlich', 'und', 'kocht gern'), 'Союз «und» соединяет две фразы об одном человеке.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Мой папа спокойный, а брат весёлый».', items5('Mein Vater', 'ist ruhig,', 'aber', 'mein Bruder', 'ist lustig'), 'Союз «aber» противопоставляет две части.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Мой брат любит собак и музыку».', items5('Mein Bruder', 'mag', 'Hunde', 'und', 'Musik'), '«und» соединяет два дополнения в Akkusativ.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Моя бабушка милая и умная».', items5('Meine Oma', 'ist', 'nett', 'und', 'klug'), 'Оба прилагательных стоят после sein без окончаний.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Это моя семья, и я её люблю».', items5('Das ist', 'meine Familie,', 'und', 'ich', 'liebe sie'), 'Союз «und» соединяет два самостоятельных предложения.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Meine Mutter ist ___. (дружелюбная)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('freundlich', 'der Hund', 'kochen', 'gern', 'mag'), 'После sein используют прилагательное характера без окончания.'),
  fb(31, 'EASY', 'Mein Bruder ___ Hunde. (любит)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('ist', 'mag', 'und', 'gern', 'nett'), 'Форма «mögen» для «er» — «mag».'),
  fb(32, 'EASY', 'Meine Oma kocht ___. (с удовольствием)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('ist', 'mag', 'gern', 'der Hund', 'und'), '«gern» после глагола означает любимое занятие.'),
  fb(33, 'EASY', 'Mein Vater ist ___. (спокойный)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('mag', 'gern', 'kochen', 'ruhig', 'der Hund'), '«ruhig» — прилагательное «спокойный».'),
  fb(34, 'EASY', 'Meine Schwester ist nett ___ klug. (и)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('aber', 'mag', 'gern', 'ist', 'und'), 'Союз «und» соединяет два прилагательных.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Mein Vater ist ruhig, ___ mein Bruder ist laut. (а)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('aber', 'und', 'mag', 'gern', 'ist'), 'Союз «aber» противопоставляет два характера.'),
  fb(36, 'HARD', 'Meine Mutter ist freundlich ___ kocht gern. (и)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('aber', 'und', 'mag', 'gern', 'ist'), 'Союз «und» соединяет две фразы об одном человеке.'),
  fb(37, 'HARD', 'Sie ___ Hunde und Musik. (любит)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('ist', 'und', 'mag', 'gern', 'aber'), 'Для «sie» (она) форма «mögen» — «mag».'),
  fb(38, 'HARD', 'Meine Oma ist nett ___ klug. (и)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('aber', 'mag', 'gern', 'und', 'ist'), 'Два прилагательных характера соединяются союзом «und».'),
  fb(39, 'HARD', 'Mein Bruder ist ___ und mag Musik. (весёлый)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('mag', 'gern', 'und', 'ist', 'lustig'), '«lustig» — прилагательное «весёлый», после sein без окончания.'),
];
