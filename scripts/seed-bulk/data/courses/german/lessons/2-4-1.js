'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Welcher bestimmte Artikel passt? ___ Haus', op4('das', 'die', 'der', 'den'), 'a', 'Слово Haus среднего рода, поэтому определённый артикль в именительном падеже — das.'),
  sc(1, 'EASY', 'Welcher Artikel passt? ___ Wohnung', op4('der', 'die', 'das', 'den'), 'b', 'Слово Wohnung женского рода, поэтому артикль — die.'),
  sc(2, 'EASY', 'Welcher Artikel passt? ___ Flur', op4('das', 'die', 'der', 'eine'), 'c', 'Слово Flur мужского рода, поэтому артикль — der.'),
  sc(3, 'EASY', 'Wie heißt «комната» auf Deutsch?', op4('die Küche', 'das Bad', 'das Zimmer', 'der Balkon'), 'c', 'Комната по-немецки — das Zimmer.'),
  sc(4, 'EASY', 'Welcher unbestimmte Artikel passt? ___ Wohnung', op4('eine', 'ein', 'einen', 'der'), 'a', 'Перед существительным женского рода в именительном падеже стоит неопределённый артикль eine.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wie lautet der Plural? das Haus → ___', op4('die Hausen', 'die Häuser', 'die Hause', 'die Häusen'), 'b', 'Множественное число от das Haus — die Häuser с умлаутом и окончанием -er.'),
  sc(6, 'HARD', 'Wie lautet der Plural? die Wohnung → ___', op4('die Wohnungen', 'die Wohnunge', 'die Wohnunger', 'die Wöhnungen'), 'a', 'Множественное число от die Wohnung образуется с окончанием -en: die Wohnungen.'),
  sc(7, 'HARD', 'Wie lautet der Plural? das Zimmer → ___', op4('die Zimmern', 'die Zimmere', 'die Zimmer', 'die Zimmers'), 'c', 'Слово das Zimmer во множественном числе не меняется: die Zimmer.'),
  sc(8, 'HARD', 'Welche Form ist richtig?', op4('ich wohnst', 'du wohne', 'er wohnt', 'wir wohnt'), 'c', 'В третьем лице единственного числа глагол wohnen спрягается как er wohnt.'),
  sc(9, 'HARD', 'Welcher unbestimmte Artikel passt? Wir haben ___ Balkon.', op4('eine', 'ein', 'einen', 'das'), 'c', 'Balkon мужского рода и стоит в винительном падеже после haben, поэтому артикль einen.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Räume in der Wohnung? (mehrere Antworten)', op5('die Küche', 'das Bad', 'der Tisch', 'das Schlafzimmer', 'die Wohnung'), ['a', 'b', 'd'], 'Кухня, ванная и спальня — это комнаты, а стол и квартира — нет.'),
  mc(11, 'EASY', 'Welche Substantive sind feminin (die)? (mehrere Antworten)', op5('die Küche', 'die Toilette', 'das Bad', 'die Wohnung', 'der Flur'), ['a', 'b', 'd'], 'Küche, Toilette и Wohnung женского рода и идут с артиклем die.'),
  mc(12, 'EASY', 'Welche Substantive haben den Artikel das? (mehrere Antworten)', op5('das Haus', 'die Küche', 'das Zimmer', 'der Balkon', 'das Bad'), ['a', 'c', 'e'], 'Haus, Zimmer и Bad среднего рода и идут с артиклем das.'),
  mc(13, 'EASY', 'Welche Plural-Artikel sind richtig? (mehrere Antworten)', op5('die Häuser', 'das Zimmer', 'die Wohnungen', 'der Flure', 'die Bäder'), ['a', 'c', 'e'], 'Во множественном числе артикль всегда die — die Häuser, die Wohnungen, die Bäder.'),
  mc(14, 'EASY', 'Welche Sätze sind richtig? (mehrere Antworten)', op5('Ich wohne hier.', 'Du wohnst hier.', 'Er wohne hier.', 'Wir wohnen hier.', 'Sie wohnst hier.'), ['a', 'b', 'd'], 'Правильно спряжены формы ich wohne, du wohnst и wir wohnen.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Pluralformen sind korrekt? (mehrere Antworten)', op6('die Häuser', 'die Wohnungen', 'die Zimmer', 'die Hausen', 'die Wohnungs', 'die Zimmern'), ['a', 'b', 'c'], 'Корректные формы множественного числа — die Häuser, die Wohnungen и die Zimmer.'),
  mc(16, 'HARD', 'Welche unbestimmten Artikel im Nominativ sind richtig? (mehrere Antworten)', op6('ein Haus', 'eine Wohnung', 'ein Zimmer', 'eine Haus', 'ein Wohnung', 'einen Zimmer'), ['a', 'b', 'c'], 'В именительном падеже верно ein Haus, eine Wohnung и ein Zimmer.'),
  mc(17, 'HARD', 'Welche Sätze sind grammatisch korrekt? (mehrere Antworten)', op6('Die Wohnung ist neu.', 'Das Haus hat vier Zimmer.', 'Die Wohnungen sind neu.', 'Der Wohnung ist neu.', 'Das Haus haben Zimmer.', 'Die Häuser ist neu.'), ['a', 'b', 'c'], 'Согласованы по роду и числу только первые три предложения.'),
  mc(18, 'HARD', 'In welchen Sätzen ist das Verb wohnen richtig konjugiert? (mehrere Antworten)', op6('Ich wohne in Berlin.', 'Du wohnst hier.', 'Er wohnt allein.', 'Wir wohnt zusammen.', 'Ich wohnt da.', 'Du wohne dort.'), ['a', 'b', 'c'], 'Верны формы ich wohne, du wohnst и er wohnt.'),
  mc(19, 'HARD', 'Welche Genus-Zuordnungen stimmen? (mehrere Antworten)', op6('der Balkon', 'die Toilette', 'das Schlafzimmer', 'die Balkon', 'das Toilette', 'der Schlafzimmer'), ['a', 'b', 'c'], 'Правильно der Balkon, die Toilette и das Schlafzimmer.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Дом большой».', items4('Das', 'Haus', 'ist', 'groß'), 'Правильный порядок слов: Das Haus ist groß.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Кухня маленькая».', items4('Die', 'Küche', 'ist', 'klein'), 'Правильный порядок слов: Die Küche ist klein.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я живу здесь».', items4('Ich', 'wohne', 'hier', '.'), 'Правильный порядок слов: Ich wohne hier .'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Квартиры новые».', items4('Die', 'Wohnungen', 'sind', 'neu'), 'Правильный порядок слов: Die Wohnungen sind neu.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Это спальня».', items4('Das', 'ist', 'das', 'Schlafzimmer'), 'Правильный порядок слов: Das ist das Schlafzimmer.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «В доме четыре комнаты».', items5('Das', 'Haus', 'hat', 'vier', 'Zimmer'), 'Правильный порядок слов: Das Haus hat vier Zimmer.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Я живу в квартире».', items5('Ich', 'wohne', 'in', 'einer', 'Wohnung'), 'Правильный порядок слов: Ich wohne in einer Wohnung.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «У нас есть балкон и ванная».', items5('Wir', 'haben', 'einen', 'Balkon', 'und ein Bad'), 'Правильный порядок слов: Wir haben einen Balkon und ein Bad.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Гостиная очень большая».', items5('Das', 'Wohnzimmer', 'ist', 'sehr', 'groß'), 'Правильный порядок слов: Das Wohnzimmer ist sehr groß.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Спальня имеет балкон».', items5('Das', 'Schlafzimmer', 'hat', 'einen', 'Balkon'), 'Правильный порядок слов: Das Schlafzimmer hat einen Balkon.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', '___ Haus ist groß.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'), 'Haus среднего рода, поэтому в именительном падеже артикль Das.'),
  fb(31, 'EASY', '___ Wohnung ist neu.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Das', 'Die', 'Der', 'Den', 'Ein'), 'Wohnung женского рода, поэтому артикль Die.'),
  fb(32, 'EASY', '___ Flur ist lang.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Der', 'Die', 'Das', 'Dem', 'Den'), 'Flur мужского рода, поэтому артикль Der.'),
  fb(33, 'EASY', 'Ich ___ in einer Wohnung.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('wohnt', 'wohnst', 'wohnen', 'wohne', 'wohnte'), 'С местоимением ich глагол wohnen имеет форму wohne.'),
  fb(34, 'EASY', 'Das ist ___ Zimmer.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('eine', 'einen', 'der', 'die', 'ein'), 'Zimmer среднего рода, поэтому неопределённый артикль ein.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Das Haus hat zwei ___ .', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Bad', 'Bäder', 'Bads', 'Baden', 'Bäden'), 'Множественное число от das Bad — die Bäder, поэтому здесь Bäder.'),
  fb(36, 'HARD', 'Die ___ sind neu.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Wohnung', 'Wohnunge', 'Wohnungen', 'Wohnungs', 'Wöhnungen'), 'Множественное число от die Wohnung — die Wohnungen.'),
  fb(37, 'HARD', 'Du ___ in Berlin.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('wohne', 'wohnt', 'wohnen', 'wohnst', 'wohnte'), 'С местоимением du глагол wohnen имеет форму wohnst.'),
  fb(38, 'HARD', 'Wir haben ___ Balkon.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('einen', 'ein', 'eine', 'der', 'das'), 'Balkon мужского рода в винительном падеже после haben, поэтому einen.'),
  fb(39, 'HARD', 'Die ___ sind klein.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Haus', 'Hause', 'Hausen', 'Häusen', 'Häuser'), 'Множественное число от das Haus — die Häuser.'),
];
