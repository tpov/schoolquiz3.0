'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wo ist die Lampe? — Sie steht ___ dem Tisch.', op4('auf', 'in', 'zu', 'mit'), 'a', 'Предлог auf (на) показывает, что лампа стоит на поверхности стола.'),
  sc(1, 'EASY', 'Was bedeutet «unter»?', op4('над', 'под', 'рядом', 'за'), 'b', 'Немецкий предлог unter переводится как «под».'),
  sc(2, 'EASY', 'Welcher Artikel im Dativ? Das Buch liegt auf ___ Tisch. (der Tisch)', op4('der', 'die', 'dem', 'den'), 'c', 'Существительное мужского рода der Tisch в дативе получает артикль dem.'),
  sc(3, 'EASY', 'Wie sagt man «рядом с кроватью»?', op4('vor dem Bett', 'neben dem Bett', 'unter dem Bett', 'hinter dem Bett'), 'b', 'Предлог neben означает «рядом», поэтому «рядом с кроватью» — neben dem Bett.'),
  sc(4, 'EASY', 'Welche Verschmelzung ist richtig? in dem Wohnzimmer = ___', op4('im Wohnzimmer', 'am Wohnzimmer', 'in Wohnzimmer', 'dem Wohnzimmer'), 'a', 'Слияние in + dem даёт форму im, поэтому правильно im Wohnzimmer.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Dativartikel passt? Die Katze liegt zwischen ___ Kissen. (die Kissen, Plural)', op4('der', 'die', 'dem', 'den'), 'd', 'Во множественном числе в дативе артикль становится den, а существительное получает -n, но Kissen уже оканчивается на -n.'),
  sc(6, 'HARD', 'Welche Form ist im Dativ korrekt? Das Bild hängt an ___ Wand. (die Wand)', op4('der Wand', 'die Wand', 'dem Wand', 'den Wand'), 'a', 'Существительное женского рода die Wand в дативе получает артикль der.'),
  sc(7, 'HARD', 'Welcher Satz ist grammatisch korrekt?', op4('Der Schrank steht neben das Bett.', 'Der Schrank steht neben dem Bett.', 'Der Schrank steht neben der Bett.', 'Der Schrank steht neben den Bett.'), 'b', 'После предлога места neben на вопрос wo? нужен датив, поэтому neben dem Bett.'),
  sc(8, 'HARD', 'Welche Verschmelzung ist richtig? an dem Fenster = ___', op4('im Fenster', 'an Fenster', 'am Fenster', 'dem Fenster'), 'c', 'Слияние an + dem даёт форму am, поэтому правильно am Fenster.'),
  sc(9, 'HARD', 'Welches Verb passt? Das Bild ___ an der Wand.', op4('steht', 'liegt', 'hängt', 'sitzt'), 'c', 'Картина на стене «висит», поэтому используется глагол hängen — das Bild hängt.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Ortspräpositionen mit Dativ? (mehrere Antworten)', op5('auf', 'unter', 'gehen', 'neben', 'Tisch'), ['a', 'b', 'd'], 'auf, unter и neben — предлоги места, а gehen это глагол и Tisch это существительное.'),
  mc(11, 'EASY', 'Welche Antworten auf «Wo?» sind im Dativ korrekt? (mehrere Antworten)', op5('auf dem Tisch', 'in der Küche', 'auf den Tisch', 'unter dem Stuhl', 'in die Küche'), ['a', 'b', 'd'], 'На вопрос wo? нужен датив: auf dem Tisch, in der Küche, unter dem Stuhl, а варианты с винительным падежом неверны.'),
  mc(12, 'EASY', 'Welche Verben antworten auf die Frage «Wo?»? (mehrere Antworten)', op5('stehen', 'liegen', 'kaufen', 'hängen', 'essen'), ['a', 'b', 'd'], 'Глаголы положения stehen, liegen и hängen отвечают на вопрос wo?, а kaufen и essen — нет.'),
  mc(13, 'EASY', 'Welche Verschmelzungen sind richtig? (mehrere Antworten)', op5('in dem = im', 'an dem = am', 'auf dem = aum', 'in dem = in', 'an dem = an'), ['a', 'b'], 'Корректные слияния — in dem = im и an dem = am, остальные формы выдуманы.'),
  mc(14, 'EASY', 'Welche Sätze beschreiben einen Ort korrekt? (mehrere Antworten)', op5('Die Lampe steht auf dem Tisch.', 'Das Buch liegt unter dem Stuhl.', 'Der Schrank steht neben Bett.', 'Das Bild hängt an der Wand.', 'Die Katze liegt in dem.'), ['a', 'b', 'd'], 'Корректны фразы с предлогом и дативным артиклем, а варианты без артикля и без существительного ошибочны.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Dativformen des bestimmten Artikels sind richtig? (mehrere Antworten)', op6('der → dem', 'das → dem', 'die → der', 'die (Pl.) → den', 'der → der', 'das → das'), ['a', 'b', 'c', 'd'], 'В дативе der и das дают dem, die даёт der, а множественное die даёт den.'),
  mc(16, 'HARD', 'Welche Sätze sind im Dativ grammatisch korrekt? (mehrere Antworten)', op5('Es liegt unter dem Stuhl.', 'Es liegt unter den Stuhl.', 'Es hängt an der Wand.', 'Es steht neben dem Schrank.', 'Es steht neben der Schrank.'), ['a', 'c', 'd'], 'Верны фразы с дативом: unter dem Stuhl, an der Wand, neben dem Schrank.'),
  mc(17, 'HARD', 'Welche Sätze nutzen das passende Positionsverb? (mehrere Antworten)', op5('Das Bild hängt an der Wand.', 'Das Buch liegt auf dem Tisch.', 'Die Lampe liegt auf dem Tisch.', 'Der Stuhl steht neben dem Tisch.', 'Das Bild steht an der Wand.'), ['a', 'b', 'd'], 'Картина висит, книга лежит, стул стоит — каждый глагол положения подобран правильно.'),
  mc(18, 'HARD', 'Wo ist «im» die korrekte Verschmelzung? (mehrere Antworten)', op5('in dem Bad → im Bad', 'in dem Flur → im Flur', 'in der Küche → im Küche', 'in dem Zimmer → im Zimmer', 'in den Zimmern → im Zimmern'), ['a', 'b', 'd'], 'Слияние im возникает только из in + dem, поэтому варианты с der и den неверны.'),
  mc(19, 'HARD', 'Welche Plural-Dativformen sind korrekt? (mehrere Antworten)', op5('zwischen den Kissen', 'zwischen dem Kissen', 'neben den Stühlen', 'neben den Stuhl', 'unter den Büchern'), ['a', 'c', 'e'], 'В дативе множественного числа артикль den и существительное с -n: den Kissen, den Stühlen, den Büchern.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Лампа стоит на столе».', items4('Die Lampe', 'steht', 'auf dem', 'Tisch'), 'Правильный порядок: Die Lampe steht auf dem Tisch — подлежащее, глагол на втором месте, затем обстоятельство места.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Книга лежит под стулом».', items4('Das Buch', 'liegt', 'unter dem', 'Stuhl'), 'Правильный порядок: Das Buch liegt unter dem Stuhl с предлогом unter и дативом.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Шкаф стоит рядом с кроватью».', items4('Der Schrank', 'steht', 'neben dem', 'Bett'), 'Правильный порядок: Der Schrank steht neben dem Bett с предлогом neben и дативом.'),
  ord(23, 'EASY', 'Соберите немецкий вопрос: «Где лампа?».', items4('Wo', 'ist', 'die', 'Lampe?'), 'Вопрос со словом wo начинается с вопросительного слова, затем глагол: Wo ist die Lampe?'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Картина висит на стене».', items4('Das Bild', 'hängt', 'an der', 'Wand'), 'Правильный порядок: Das Bild hängt an der Wand с глаголом hängen и дативом der Wand.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «В гостиной на стене висит картина».', items5('Im Wohnzimmer', 'hängt', 'ein Bild', 'an der', 'Wand'), 'При вынесенном вперёд обстоятельстве глагол остаётся на втором месте: Im Wohnzimmer hängt ein Bild an der Wand.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Кошка лежит между подушками».', items5('Die Katze', 'liegt', 'zwischen', 'den', 'Kissen'), 'Предлог zwischen с дативом множественного числа: Die Katze liegt zwischen den Kissen.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Лампа стоит на столе у окна».', items5('Die Lampe', 'steht', 'auf dem Tisch', 'am', 'Fenster'), 'Слияние an + dem = am: Die Lampe steht auf dem Tisch am Fenster.'),
  ord(28, 'HARD', 'Соберите немецкий вопрос: «Где стоит шкаф?».', items4('Wo', 'steht', 'der', 'Schrank?'), 'Вопрос с wo: вопросительное слово, затем глагол на втором месте: Wo steht der Schrank?'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «В кухне стоит стол перед окном».', items5('In der Küche', 'steht', 'ein Tisch', 'vor dem', 'Fenster'), 'Обстоятельство впереди, глагол вторым: In der Küche steht ein Tisch vor dem Fenster.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Die Lampe steht auf ___ Tisch. (der Tisch, Dativ)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('der', 'die', 'dem', 'den', 'das'), 'Мужской род der Tisch в дативе получает артикль dem.'),
  fb(31, 'EASY', 'Das Bild hängt an ___ Wand. (die Wand, Dativ)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('der', 'die', 'dem', 'den', 'das'), 'Женский род die Wand в дативе получает артикль der.'),
  fb(32, 'EASY', 'Wir sind ___ Wohnzimmer. (in + dem)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('am', 'im', 'in', 'an', 'auf'), 'Слияние in + dem даёт форму im, поэтому im Wohnzimmer.'),
  fb(33, 'EASY', 'Das Buch liegt unter ___ Stuhl. (der Stuhl, Dativ)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('der', 'die', 'dem', 'den', 'das'), 'Мужской род der Stuhl в дативе получает артикль dem.'),
  fb(34, 'EASY', 'Was bedeutet «между»? ___', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('neben', 'vor', 'hinter', 'unter', 'zwischen'), 'Предлог «между» по-немецки — zwischen.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Die Katze liegt zwischen ___ Kissen. (Plural, Dativ)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('der', 'die', 'dem', 'den', 'das'), 'Во множественном числе в дативе артикль становится den.'),
  fb(36, 'HARD', 'Die Bücher stehen neben ___ Stühlen. (Plural, Dativ)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('die', 'den', 'der', 'dem', 'das'), 'Предлог neben на вопрос wo? требует датива, во множественном числе — den.'),
  fb(37, 'HARD', 'Die Lampe steht ___ Fenster. (an + dem)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('im', 'in', 'am', 'an', 'auf'), 'Слияние an + dem даёт форму am, поэтому am Fenster.'),
  fb(38, 'HARD', 'Das Bild ___ an der Wand. (висит)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('steht', 'liegt', 'sitzt', 'geht', 'hängt'), 'Картина на стене «висит», поэтому нужен глагол hängt.'),
  fb(39, 'HARD', 'Der Schrank steht neben ___ Bett. (das Bett, Dativ)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('dem', 'der', 'die', 'den', 'das'), 'Средний род das Bett в дативе получает артикль dem.'),
];
