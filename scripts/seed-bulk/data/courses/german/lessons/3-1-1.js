'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ---- 5 sc EASY (idx 0..4) ----
  sc(0, 'EASY', 'Wähle den richtigen Artikel: Ich kaufe ___ Mantel.', op4('den', 'der', 'das', 'die'), 'a', 'Мужской род der Mantel в Akkusativ меняется на den.'),
  sc(1, 'EASY', 'Wähle die richtige Form: Er braucht ___ Tisch.', op4('ein', 'einen', 'eine', 'einem'), 'b', 'Неопределённый артикль мужского рода в Akkusativ — einen.'),
  sc(2, 'EASY', 'Auf welche Frage antwortet der Akkusativ?', op4('wer?/was?', 'wem?', 'wessen?', 'wen?/was?'), 'd', 'Akkusativ отвечает на вопрос «кого?/что?» — wen?/was?.'),
  sc(3, 'EASY', 'Wähle den richtigen Artikel: Ich sehe ___ Buch.', op4('den', 'dem', 'das', 'des'), 'c', 'Средний род das Buch в Akkusativ не меняется и остаётся das.'),
  sc(4, 'EASY', 'Wähle die richtige Form: Ich suche ___ Tasche.', op4('einen', 'einem', 'ein', 'eine'), 'd', 'Женский род eine Tasche в Akkusativ совпадает с Nominativ — eine.'),

  // ---- 5 sc HARD (idx 5..9) ----
  sc(5, 'HARD', 'Wähle die richtige Form: Wir haben ___ Apfel mehr.', op4('keinen', 'kein', 'keine', 'keinem'), 'a', 'Отрицание мужского рода в Akkusativ — keinen (как einen).'),
  sc(6, 'HARD', 'Wähle das richtige Pronomen: Siehst du das Buch? — Ja, ich sehe ___.', op4('ihn', 'sie', 'es', 'ihm'), 'c', 'Средний род das Buch заменяется местоимением es и в Akkusativ.'),
  sc(7, 'HARD', 'Wähle das richtige Pronomen: Wo ist der Schlüssel? Ich suche ___.', op4('ihn', 'es', 'sie', 'ihm'), 'a', 'Мужской род der Schlüssel в Akkusativ заменяется на ihn.'),
  sc(8, 'HARD', 'Wähle die richtige Form: Brauchst du ___ Mantel?', op4('einem', 'ein', 'einen', 'eine'), 'c', 'Мужской род der Mantel требует einen в Akkusativ.'),
  sc(9, 'HARD', 'Wähle das richtige Pronomen: Ich liebe ___ (= тебя).', op4('dir', 'du', 'dich', 'dein'), 'c', 'Личное местоимение du в Akkusativ — dich.'),

  // ---- 5 mc EASY (idx 10..14) ----
  mc(10, 'EASY', 'Welche Formen sind korrekter Akkusativ für der Tisch?', op5('den Tisch', 'einen Tisch', 'der Tisch', 'dem Tisch', 'ein Tisch'), ['a', 'b'], 'В Akkusativ мужской род даёт den Tisch и einen Tisch.'),
  mc(11, 'EASY', 'Welche Sätze brauchen den Akkusativ?', op5('Ich kaufe das Buch.', 'Er sucht die Tasche.', 'Das Haus ist groß.', 'Wir brauchen den Tisch.', 'Der Mantel ist neu.'), ['a', 'b', 'd'], 'Глаголы kaufen, suchen, brauchen требуют прямого дополнения в Akkusativ.'),
  mc(12, 'EASY', 'Welche Wörter bleiben im Akkusativ gleich wie im Nominativ?', op5('die Tasche', 'das Buch', 'der Mantel', 'eine Tasche', 'ein Mantel'), ['a', 'b', 'd'], 'Женский и средний род в Akkusativ не меняются, а мужской меняется.'),
  mc(13, 'EASY', 'Welche Pronomen sind richtiger Akkusativ?', op5('mich (ich)', 'dich (du)', 'er (er)', 'uns (wir)', 'ihm (er)'), ['a', 'b', 'd'], 'В Akkusativ: ich→mich, du→dich, wir→uns.'),
  mc(14, 'EASY', 'Welche Verben verlangen ein Akkusativobjekt?', op5('kaufen', 'sehen', 'sein', 'brauchen', 'bleiben'), ['a', 'b', 'd'], 'Глаголы kaufen, sehen, brauchen имеют прямое дополнение в Akkusativ.'),

  // ---- 5 mc HARD (idx 15..19) ----
  mc(15, 'HARD', 'Welche Sätze sind im Akkusativ korrekt?', op6('Ich kaufe den Mantel.', 'Er braucht einen Tisch.', 'Wir haben kein Apfel.', 'Ich suche eine Tasche.', 'Sie sieht der Schlüssel.', 'Ich sehe das Buch.'), ['a', 'b', 'd', 'f'], 'Мужской род должен быть einen/keinen/den, поэтому «kein Apfel» и «der Schlüssel» неверны.'),
  mc(16, 'HARD', 'Welche Pronomen-Ersetzungen im Akkusativ sind richtig?', op6('den Mantel → ihn', 'die Tasche → sie', 'das Buch → es', 'den Schlüssel → ihm', 'die Tasche → ihn', 'das Buch → ihn'), ['a', 'b', 'c'], 'der→ihn, die→sie, das→es в Akkusativ; ihm — это Dativ.'),
  mc(17, 'HARD', 'Welche Negationsformen im Akkusativ sind korrekt?', op6('keinen Mantel', 'keinen Tisch', 'kein Tasche', 'keine Tasche', 'kein Buch', 'keinem Apfel'), ['a', 'b', 'd', 'e'], 'Мужской род даёт keinen, женский — keine, средний — kein.'),
  mc(18, 'HARD', 'In welchen Sätzen steht der Artikel im Akkusativ richtig?', op6('Ich brauche einen Schlüssel.', 'Er kauft einen Mantel.', 'Wir suchen ein Buch.', 'Sie hat einen Tasche.', 'Ich sehe einen Apfel.', 'Du brauchst eine Tasche.'), ['a', 'b', 'c', 'e', 'f'], 'Tasche женского рода требует eine, поэтому «einen Tasche» неверно.'),
  mc(19, 'HARD', 'Welche Aussagen über den Akkusativ stimmen?', op6('der wird zu den', 'ein wird zu einen', 'mein wird zu meinen', 'die wird zu den (Singular)', 'das wird zu dem', 'kein wird zu keinen'), ['a', 'b', 'c', 'f'], 'В мужском роде меняются der/ein/mein/kein, а die/das остаются.'),

  // ---- 5 ord EASY (idx 20..24) ----
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я покупаю пальто».', items4('Ich', 'kaufe', 'den', 'Mantel'), 'Порядок V2: глагол kaufe на втором месте, den Mantel — Akkusativ.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ему нужен стол».', items4('Er', 'braucht', 'einen', 'Tisch'), 'Прямое дополнение einen Tisch стоит в Akkusativ после braucht.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я ищу сумку».', items4('Ich', 'suche', 'eine', 'Tasche'), 'eine Tasche — Akkusativ женского рода, форма совпадает с Nominativ.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Я вижу книгу».', items4('Ich', 'sehe', 'das', 'Buch'), 'das Buch — Akkusativ среднего рода, артикль das не меняется.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Он покупает яблоко».', items4('Er', 'kauft', 'einen', 'Apfel'), 'einen Apfel — Akkusativ мужского рода после глагола kauft.'),

  // ---- 5 ord HARD (idx 25..29) ----
  ord(25, 'HARD', 'Соберите немецкое предложение: «У нас больше нет яблока».', items5('Wir', 'haben', 'keinen', 'Apfel', 'mehr'), 'keinen Apfel — отрицание мужского рода в Akkusativ.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Я ищу свою сумку».', items4('Ich', 'suche', 'meine', 'Tasche'), 'meine Tasche — Akkusativ женского рода, притяжательный остаётся meine.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Тебе нужен ключ?».', items5('Brauchst', 'du', 'einen', 'Schlüssel', '?'), 'В вопросе глагол на первом месте, einen Schlüssel — Akkusativ.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Я вижу его там» (про ключ).', items4('Ich', 'sehe', 'ihn', 'dort'), 'den Schlüssel заменяется на ihn — местоимение Akkusativ мужского рода; dort — обстоятельство места.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Я сегодня не покупаю пальто».', items5('Ich', 'kaufe', 'heute', 'keinen', 'Mantel'), 'keinen Mantel — отрицание мужского рода в Akkusativ; heute (время) стоит перед дополнением.'),

  // ---- 5 fb EASY (idx 30..34) ----
  fb(30, 'EASY', 'Ich kaufe ___ Mantel.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('den', 'der', 'das', 'die', 'dem'), 'der Mantel в Akkusativ становится den.'),
  fb(31, 'EASY', 'Er braucht ___ Tisch.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('ein', 'einen', 'eine', 'einem', 'der'), 'Неопределённый артикль мужского рода в Akkusativ — einen.'),
  fb(32, 'EASY', 'Ich sehe ___ Buch.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('den', 'dem', 'das', 'des', 'die'), 'das Buch (средний род) в Akkusativ не меняется.'),
  fb(33, 'EASY', 'Ich suche ___ Tasche.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('einen', 'einem', 'der', 'eine', 'ein'), 'eine Tasche (женский род) в Akkusativ совпадает с Nominativ.'),
  fb(34, 'EASY', 'Er kauft ___ Apfel.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('ein', 'eine', 'einem', 'der', 'einen'), 'der Apfel требует einen в Akkusativ.'),

  // ---- 5 fb HARD (idx 35..39) ----
  fb(35, 'HARD', 'Wir haben ___ Apfel mehr.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('keinen', 'kein', 'keine', 'keinem', 'keiner'), 'Отрицание мужского рода в Akkusativ — keinen.'),
  fb(36, 'HARD', 'Siehst du das Buch? — Ja, ich sehe ___.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('ihn', 'es', 'sie', 'ihm', 'ihr'), 'das Buch заменяется местоимением es в Akkusativ.'),
  fb(37, 'HARD', 'Wo ist der Schlüssel? Ich suche ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('es', 'sie', 'ihn', 'ihm', 'er'), 'der Schlüssel в Akkusativ заменяется на ihn.'),
  fb(38, 'HARD', 'Ich suche ___ Tasche.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('mein', 'meinen', 'meinem', 'meine', 'meiner'), 'meine Tasche — притяжательный женского рода в Akkusativ остаётся meine.'),
  fb(39, 'HARD', 'Brauchst du ___ Mantel?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('ein', 'eine', 'einem', 'der', 'einen'), 'der Mantel требует einen в Akkusativ.'),
];
