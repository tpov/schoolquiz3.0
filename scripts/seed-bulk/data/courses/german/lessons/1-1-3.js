'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Как читается «sch» в слове «die Schule»?', op4('[ш]', '[с]', '[сх]', '[щ]'), 'a', 'Сочетание sch всегда читается как русское [ш].'),
  sc(1, 'EASY', 'Как читается «z» в слове «die Zeit»?', op4('[з]', '[ц]', '[с]', '[дз]'), 'b', 'Буква z в немецком читается как [ц], а не как [з].'),
  sc(2, 'EASY', 'Как читается «w» в слове «das Wasser»?', op4('[у]', '[ф]', '[в]', '[уэ]'), 'c', 'Буква w читается как русское [в].'),
  sc(3, 'EASY', 'Как читается «v» в слове «der Vater»?', op4('[в]', '[у]', '[б]', '[ф]'), 'd', 'Буква v обычно читается как [ф], особенно в исконно немецких словах.'),
  sc(4, 'EASY', 'Как читается «ei» в слове «drei»?', op4('[ай]', '[эй]', '[ей]', '[и]'), 'a', 'Сочетание ei всегда читается как [ай].'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Как читается «st» в начале слова «die Straße»?', op4('[ст]', '[шт]', '[щт]', '[зт]'), 'b', 'В начале слова или корня st читается как [шт].'),
  sc(6, 'HARD', 'Как читается «sp» в начале слова «spielen»?', op4('[сп]', '[зп]', '[шп]', '[сб]'), 'c', 'В начале слова или корня sp читается как [шп].'),
  sc(7, 'HARD', 'Как читается «ch» в слове «das Buch»?', op4('[ч]', '[ш]', '[к]', '[х]'), 'd', 'После a, o, u, au сочетание ch даёт твёрдый звук [х].'),
  sc(8, 'HARD', 'Как читается «ch» в слове «ich»?', op4('[хь]', '[х]', '[ш]', '[к]'), 'a', 'После i, e и согласных ch даёт мягкий звук [хь].'),
  sc(9, 'HARD', 'Как читается «eu» в слове «neun»?', op4('[эу]', '[ой]', '[ай]', '[ю]'), 'b', 'Сочетания eu и äu читаются как [ой].'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'В каких словах «sch» читается как [ш]?', op5('die Schule', 'schön', 'das Buch', 'der Vater', 'die Zeit'), ['a', 'b'], 'Sch как [ш] есть только в Schule и schön.'),
  mc(11, 'EASY', 'В каких словах есть звук [ц]?', op5('zehn', 'die Zeit', 'die Schule', 'das Wasser', 'spielen'), ['a', 'b'], 'Буква z даёт [ц] в zehn и die Zeit.'),
  mc(12, 'EASY', 'В каких словах буква «w» читается как [в]?', op5('das Wasser', 'wie', 'der Vater', 'das Buch', 'drei'), ['a', 'b'], 'W как [в] есть в Wasser и wie.'),
  mc(13, 'EASY', 'В каких словах есть сочетание «ei» со звуком [ай]?', op5('drei', 'mein', 'neun', 'die Schule', 'zehn'), ['a', 'b'], 'Звук [ай] из ei есть в drei и mein.'),
  mc(14, 'EASY', 'В каких словах буква «v» читается как [ф]?', op5('der Vater', 'vier', 'das Wasser', 'die Zeit', 'spielen'), ['a', 'b'], 'V как [ф] есть в Vater и vier.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'В каких словах «st» или «sp» в начале даёт [шт]/[шп]?', op6('die Straße', 'spielen', 'der Stuhl', 'das Wasser', 'die Zeit', 'der Vater'), ['a', 'b', 'c'], 'В начале корня st даёт [шт] (Straße, Stuhl), sp даёт [шп] (spielen).'),
  mc(16, 'HARD', 'В каких словах «ch» читается твёрдо как [х]?', op6('das Buch', 'die Nacht', 'das Tuch', 'ich', 'das Mädchen', 'schön'), ['a', 'b', 'c'], 'После a, o, u ch твёрдое [х]: Buch, Nacht, Tuch.'),
  mc(17, 'HARD', 'В каких словах «ch» читается мягко как [хь]?', op6('ich', 'das Mädchen', 'die Milch', 'das Buch', 'die Nacht', 'das Tuch'), ['a', 'b', 'c'], 'После i, e и согласных ch мягкое [хь]: ich, Mädchen, Milch.'),
  mc(18, 'HARD', 'В каких словах есть звук [ой] (eu или äu)?', op6('neun', 'das Häuschen', 'die Leute', 'drei', 'mein', 'die Zeit'), ['a', 'b', 'c'], 'Звук [ой] дают eu (neun, Leute) и äu (Häuschen).'),
  mc(19, 'HARD', 'В каких словах буква «z» даёт звук [ц]?', op6('zehn', 'die Zeit', 'der Zug', 'das Wasser', 'die Schule', 'der Vater'), ['a', 'b', 'c'], 'Z как [ц] есть в zehn, die Zeit и der Zug.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Школа большая.»', items4('Die', 'Schule', 'ist', 'groß'), 'Die Schule ist groß — школа большая, sch читается как [ш].'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Вода холодная.»', items4('Das', 'Wasser', 'ist', 'kalt'), 'Das Wasser ist kalt — вода холодная, w читается как [в].'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Книга маленькая.»', items4('Das', 'Buch', 'ist', 'klein'), 'Das Buch ist klein — книга маленькая, ch после u даёт твёрдый [х].'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Отец читает охотно.»', items4('Der', 'Vater', 'liest', 'gern'), 'Der Vater liest gern — отец читает охотно, v читается как [ф].'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Улица длинная.»', items4('Die', 'Straße', 'ist', 'lang'), 'Die Straße ist lang — улица длинная, st в начале даёт [шт].'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Дети играют на улице.»', items5('Die', 'Kinder', 'spielen', 'auf', 'der Straße'), 'Die Kinder spielen auf der Straße — sp в spielen даёт [шп], после auf нужен Dativ der Straße.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Мой отец читает книгу.»', items5('Mein', 'Vater', 'liest', 'ein', 'Buch'), 'Mein Vater liest ein Buch — мой отец читает книгу, ch в Buch это твёрдый [х].'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Сегодня у меня нет времени.»', items5('Ich', 'habe', 'heute', 'keine', 'Zeit'), 'Ich habe heute keine Zeit — z в Zeit даёт [ц], ch в ich мягкий [хь].'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Вода очень холодная.»', items5('Das', 'Wasser', 'ist', 'sehr', 'kalt'), 'Das Wasser ist sehr kalt — вода очень холодная, w в Wasser это [в].'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Девочка играет на улице.»', items5('Das', 'Mädchen', 'spielt', 'auf', 'der Straße'), 'Das Mädchen spielt auf der Straße — ch в Mädchen мягкий [хь], Mädchen среднего рода (das).'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', '___ Schule ist groß. (школа — женский род)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Die', 'Der', 'Das', 'Den', 'Dem'), 'Schule женского рода, поэтому артикль die.'),
  fb(31, 'EASY', '___ Wasser ist kalt. (вода — средний род)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Das', 'Die', 'Den', 'Dem'), 'Wasser среднего рода, поэтому артикль das.'),
  fb(32, 'EASY', '___ Vater liest ein Buch. (отец — мужской род)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Die', 'Das', 'Der', 'Den', 'Dem'), 'Vater мужского рода, поэтому артикль der.'),
  fb(33, 'EASY', '___ Buch ist klein. (книга — средний род)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Der', 'Die', 'Den', 'Das', 'Dem'), 'Buch среднего рода, поэтому артикль das.'),
  fb(34, 'EASY', '___ Straße ist lang. (улица — женский род)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Der', 'Das', 'Den', 'Dem', 'Die'), 'Straße женского рода, поэтому артикль die.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Die Kinder spielen auf ___ Straße. (Dativ, ж. род)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('der', 'die', 'das', 'den', 'dem'), 'Предлог auf тут с Dativ, женский род в Dativ даёт артикль der.'),
  fb(36, 'HARD', 'Ich habe keine ___. (время — z читается [ц])', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Buch', 'Zeit', 'Schule', 'Wasser', 'Straße'), 'У меня нет времени — die Zeit, z даёт звук [ц].'),
  fb(37, 'HARD', 'Das ___ spielt im Garten. (девочка — мягкое ch)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Vater', 'Buch', 'Mädchen', 'Wasser', 'Straße'), 'Das Mädchen среднего рода, ch в нём мягкий [хь].'),
  fb(38, 'HARD', 'Der ___ fährt schnell. (поезд — z читается [ц])', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Schule', 'Wasser', 'Straße', 'Zug', 'Buch'), 'Der Zug — поезд, z даёт звук [ц].'),
  fb(39, 'HARD', 'Mein Vater liest ein ___. (книга — твёрдое ch)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Schule', 'Straße', 'Wasser', 'Zeit', 'Buch'), 'Das Buch — книга, ch после u даёт твёрдый [х].'),
];
