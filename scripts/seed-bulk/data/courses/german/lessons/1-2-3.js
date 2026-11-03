'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie heißt «откуда» auf Deutsch?', op4('woher', 'wo', 'was', 'wer'), 'a', 'Слово «woher» означает «откуда».'),
  sc(1, 'EASY', 'Ich ___ aus Russland.', op4('komme', 'kommst', 'kommt', 'kommen'), 'a', 'С «ich» глагол kommen имеет окончание -e: ich komme.'),
  sc(2, 'EASY', 'Wie heißt «где» auf Deutsch?', op4('wann', 'wer', 'wo', 'wie'), 'c', 'Слово «wo» означает «где».'),
  sc(3, 'EASY', 'Welche Präposition passt? Ich komme ___ Moskau.', op4('in', 'aus', 'an', 'zu'), 'b', 'С названием места происхождения используется предлог «aus» (из).'),
  sc(4, 'EASY', 'Du ___ in Berlin.', op4('wohne', 'wohnen', 'wohnst', 'wohnt'), 'c', 'С «du» глагол wohnen имеет окончание -st: du wohnst.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welche Frage ist korrekt?', op4('Woher du kommst?', 'Woher kommst du?', 'Du woher kommst?', 'Kommst woher du?'), 'b', 'В вопросе с вопросительным словом глагол стоит на втором месте: Woher kommst du?'),
  sc(6, 'HARD', 'Welcher Satz ist richtig?', op4('Ich wohne in Deutschland.', 'Ich wohne aus Deutschland.', 'Ich wohnst in Deutschland.', 'Ich wohne an Deutschland.'), 'a', 'Место проживания вводится предлогом «in»: ich wohne in Deutschland.'),
  sc(7, 'HARD', 'Vervollständige: Kommst du ___ Österreich?', op4('in', 'an', 'aus', 'zu'), 'c', 'Происхождение из страны передаётся предлогом «aus»: aus Österreich.'),
  sc(8, 'HARD', 'Welche Antwort ist korrekt: «Wo wohnst du?»', op4('Ich wohne aus Berlin.', 'Ich komme in Berlin.', 'Ich wohne in Berlin.', 'Ich wohnst in Berlin.'), 'c', 'На вопрос «где живёшь» отвечают «in» + город: ich wohne in Berlin.'),
  sc(9, 'HARD', 'Welcher Satz ist grammatisch korrekt?', op4('Du kommst aus Russland.', 'Du komme aus Russland.', 'Du kommen aus Russland.', 'Du kommt aus Russland.'), 'a', 'С «du» глагол kommen имеет окончание -st: du kommst.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Fragewörter?', op5('woher', 'aus', 'wo', 'in', 'kommen'), ['a', 'c'], 'Вопросительные слова здесь — «woher» (откуда) и «wo» (где).'),
  mc(11, 'EASY', 'Welche Formen passen zu «ich»?', op5('komme', 'kommst', 'wohne', 'wohnst', 'kommt'), ['a', 'c'], 'С «ich» глаголы оканчиваются на -e: ich komme, ich wohne.'),
  mc(12, 'EASY', 'Welche Wörter sind Präpositionen?', op5('aus', 'wer', 'in', 'wohnen', 'wo'), ['a', 'c'], 'Предлоги здесь — «aus» (из) и «in» (в).'),
  mc(13, 'EASY', 'Welche Formen passen zu «du»?', op5('kommst', 'komme', 'wohnst', 'wohne', 'kommt'), ['a', 'c'], 'С «du» глаголы оканчиваются на -st: du kommst, du wohnst.'),
  mc(14, 'EASY', 'Welche Wörter sind Länder?', op5('Deutschland', 'Berlin', 'Russland', 'Moskau', 'kommen'), ['a', 'c'], 'Страны здесь — Deutschland (Германия) и Russland (Россия).'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Ich komme aus Moskau.', 'Ich komme in Moskau.', 'Du wohnst in Berlin.', 'Du wohne in Berlin.', 'Ich kommst aus Berlin.'), ['a', 'c'], 'Происхождение — с «aus», а с «du» глагол на -st: aus Moskau и du wohnst.'),
  mc(16, 'HARD', 'Welche Sätze passen zu «Woher kommst du?»', op6('Ich komme aus Russland.', 'Ich komme aus Deutschland.', 'Ich wohne in Berlin.', 'Ich komme in Russland.', 'Ich heiße Anna.', 'Ich wohnst aus Berlin.'), ['a', 'b'], 'На «откуда» отвечают «aus» + страна: aus Russland, aus Deutschland.'),
  mc(17, 'HARD', 'Welche Fragen sind korrekt gebaut?', op5('Woher kommst du?', 'Wo wohnst du?', 'Woher du kommst?', 'Wo du wohnst?', 'Kommst woher du?'), ['a', 'b'], 'В обоих вопросах глагол стоит на втором месте после вопросительного слова.'),
  mc(18, 'HARD', 'Welche Verbformen sind richtig konjugiert?', op6('ich wohne', 'du kommst', 'ich kommst', 'du wohne', 'du komme', 'ich wohnst'), ['a', 'b'], 'Правильно: ich wohne (-e) и du kommst (-st).'),
  mc(19, 'HARD', 'Welche Präposition-Verbindungen sind korrekt?', op6('aus Russland', 'in Berlin', 'in Russland weg', 'aus in Berlin', 'zu aus Deutschland', 'in aus Moskau'), ['a', 'b'], 'Корректны «aus Russland» (из) и «in Berlin» (в); остальные сочетания предлогов неверны.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я из России».', items4('Ich', 'komme', 'aus', 'Russland'), 'Порядок: подлежащее, глагол, предлог aus, страна — Ich komme aus Russland.'),
  ord(21, 'EASY', 'Соберите немецкий вопрос: «Откуда ты?».', items4('Woher', 'kommst', 'du', '?'), 'Глагол на втором месте после woher: Woher kommst du?'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я живу в Берлине».', items4('Ich', 'wohne', 'in', 'Berlin'), 'Место проживания вводится предлогом in: Ich wohne in Berlin.'),
  ord(23, 'EASY', 'Соберите немецкий вопрос: «Где ты живёшь?».', items4('Wo', 'wohnst', 'du', '?'), 'Глагол на втором месте после wo: Wo wohnst du?'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Ты из Германии».', items4('Du', 'kommst', 'aus', 'Deutschland'), 'С du глагол kommst, происхождение с aus: Du kommst aus Deutschland.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я живу в Германии».', items4('Ich', 'wohne', 'in', 'Deutschland'), 'Проживание в стране — предлог in: Ich wohne in Deutschland.'),
  ord(26, 'HARD', 'Соберите немецкий вопрос: «Ты из Австрии?».', items5('Kommst', 'du', 'aus', 'Österreich', '?'), 'В вопросе без вопросительного слова глагол на первом месте: Kommst du aus Österreich?'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я сейчас живу в Берлине».', items5('Ich', 'wohne', 'jetzt', 'in', 'Berlin'), 'Глагол на 2-м месте, обстоятельство jetzt после глагола, затем предлог in: Ich wohne jetzt in Berlin.'),
  ord(28, 'HARD', 'Соберите немецкий вопрос: «Где ты живёшь сейчас?».', items5('Wo', 'wohnst', 'du', 'jetzt', '?'), 'Глагол на втором месте, обстоятельство jetzt в конце: Wo wohnst du jetzt?'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Ты сейчас живёшь в России».', items5('Du', 'wohnst', 'jetzt', 'in', 'Russland'), 'С du глагол wohnst, обстоятельство jetzt после глагола, место с in: Du wohnst jetzt in Russland.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ aus Russland.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('komme', 'kommst', 'kommt', 'kommen', 'gekommen'), 'С ich глагол kommen оканчивается на -e: ich komme.'),
  fb(31, 'EASY', 'Ich wohne ___ Berlin.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('aus', 'in', 'an', 'zu', 'von'), 'Место проживания вводится предлогом in: in Berlin.'),
  fb(32, 'EASY', 'Du ___ aus Deutschland.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('komme', 'kommt', 'kommst', 'kommen', 'kam'), 'С du глагол kommen оканчивается на -st: du kommst.'),
  fb(33, 'EASY', '___ wohnst du?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Was', 'Wer', 'Wann', 'Wo', 'Wie'), 'Вопрос о месте — «где»: Wo wohnst du?'),
  fb(34, 'EASY', 'Ich komme ___ Moskau.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('in', 'an', 'zu', 'von', 'aus'), 'Происхождение из города — предлог aus: aus Moskau.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', '___ kommst du? — Ich komme aus Russland.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Woher', 'Wo', 'Wann', 'Wer', 'Was'), 'Спрашивают о происхождении — «откуда»: Woher kommst du?'),
  fb(36, 'HARD', 'Du ___ in Berlin und ich wohne in Moskau.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('wohne', 'wohnst', 'wohnt', 'wohnen', 'wohnte'), 'С du глагол wohnen оканчивается на -st: du wohnst.'),
  fb(37, 'HARD', 'Kommst du ___ Österreich?', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('in', 'an', 'aus', 'zu', 'von'), 'Происхождение из страны — предлог aus: aus Österreich.'),
  fb(38, 'HARD', 'Ich ___ in Deutschland, aber ich komme aus Russland.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('wohnst', 'wohnt', 'wohnen', 'wohne', 'gewohnt'), 'С ich глагол wohnen оканчивается на -e: ich wohne.'),
  fb(39, 'HARD', 'Wo ___ du jetzt?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('wohne', 'wohnt', 'wohnen', 'wohnte', 'wohnst'), 'С du глагол wohnen оканчивается на -st: du wohnst.'),
];
