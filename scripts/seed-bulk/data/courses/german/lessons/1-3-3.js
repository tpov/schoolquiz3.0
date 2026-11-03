'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie fragt man «Который час?» — самый частый вариант?', op4('Wie spät ist es?', 'Wo ist die Uhr?', 'Was ist das?', 'Wer ist da?'), 'a', 'Стандартный вопрос о времени — Wie spät ist es?'),
  sc(1, 'EASY', 'Es ist ___ Uhr. (Сейчас три часа.)', op4('drei', 'rot', 'gut', 'hier'), 'a', 'После Es ist идёт число drei (три), затем Uhr.'),
  sc(2, 'EASY', 'Как сказать «Сейчас девять часов»?', op4('Es ist neun Uhr.', 'Es ist neun Jahr.', 'Ist es neun Uhr?', 'Es neun Uhr ist.'), 'a', 'Время называют через безличное Es ist plus число plus Uhr.'),
  sc(3, 'EASY', 'Какое слово значит «ровно» (например, ровно восемь)?', op4('halb', 'spät', 'Punkt', 'Morgen'), 'c', 'Punkt acht значит «ровно восемь».'),
  sc(4, 'EASY', 'Что значит «der Morgen»?', op4('вечер', 'утро', 'ночь', 'день'), 'b', 'der Morgen переводится как «утро».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Сколько времени показывает «halb neun»?', op4('9:30', '8:30', '9:00', '8:00'), 'b', 'halb neun — это половина К девяти, то есть 8:30.'),
  sc(6, 'HARD', 'Как правильно сказать «Сейчас половина седьмого» (6:30)?', op4('Es ist halb sechs.', 'Es ist halb sieben.', 'Es ist sieben halb.', 'Es ist halb Uhr sieben.'), 'b', 'Половина седьмого по-немецки — halb sieben (половина К семи = 6:30).'),
  sc(7, 'HARD', 'Es ist Punkt zwölf ___ Morgen. (ровно двенадцать утра)', op4('am', 'im', 'um', 'an'), 'a', 'Часть суток оформляется предлогом am — am Morgen.'),
  sc(8, 'HARD', 'Какой вопрос тоже значит «Который сейчас час?»', op4('Wie viel Uhr ist es jetzt?', 'Wie viele Uhren gibt es?', 'Wann kommst du jetzt?', 'Wie alt ist die Uhr?'), 'a', 'Wie viel Uhr ist es? — второй обычный вопрос о времени.'),
  sc(9, 'HARD', 'Es ist ein ___. (Сейчас один час.)', op4('Uhr', 'Uhren', 'eine Uhr', 'Uhrzeit'), 'a', 'Слово Uhr после числа не меняется: ein Uhr, zwölf Uhr.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие вопросы спрашивают «Который час?» (выберите все верные)', op5('Wie spät ist es?', 'Wie viel Uhr ist es?', 'Wo wohnst du?', 'Wie heißt du?', 'Was kostet das?'), ['a', 'b'], 'О времени спрашивают Wie spät ist es? и Wie viel Uhr ist es?'),
  mc(11, 'EASY', 'Какие ответы называют ровный час корректно? (выберите все верные)', op5('Es ist drei Uhr.', 'Es ist zehn Uhr.', 'Es ist Uhr drei.', 'Drei es ist Uhr.', 'Es Uhr drei ist.'), ['a', 'b'], 'Ровный час — Es ist plus число plus Uhr: Es ist drei/zehn Uhr.'),
  mc(12, 'EASY', 'Какие слова относятся ко времени суток? (выберите все верные)', op5('der Morgen', 'der Abend', 'das Brot', 'die Katze', 'der Tisch'), ['a', 'b'], 'der Morgen (утро) и der Abend (вечер) — это части суток.'),
  mc(13, 'EASY', 'В каких ответах слово Uhr написано правильно (с большой буквы, без изменений)? (выберите все верные)', op5('Es ist acht Uhr.', 'Es ist zwölf Uhr.', 'Es ist acht uhr.', 'Es ist acht Uhren.', 'Es ist Uhr acht.'), ['a', 'b'], 'Существительное Uhr пишется с большой буквы и после числа не меняется.'),
  mc(14, 'EASY', 'Какие слова означают «сейчас» или «поздно»? (выберите все верные)', op5('jetzt', 'spät', 'gut', 'rot', 'klein'), ['a', 'b'], 'jetzt — «сейчас», spät — «поздно».'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие записи времени верны для «8:30»? (выберите все верные)', op5('Es ist halb neun.', 'halb neun', 'Es ist halb acht.', 'Es ist acht halb.', 'Es ist halb neun Uhr dreißig.'), ['a', 'b'], 'halb neun — это половина К девяти, то есть 8:30.'),
  mc(16, 'HARD', 'Какие предложения о времени построены грамматически верно? (выберите все верные)', op5('Es ist Punkt sieben.', 'Es ist halb zehn.', 'Es Punkt sieben ist.', 'Ist es halb zehn Uhr halb.', 'Punkt es ist sieben.'), ['a', 'b'], 'В главном предложении глагол ist стоит на втором месте: Es ist Punkt sieben / halb zehn.'),
  mc(17, 'HARD', 'Какие пары «немецкое время → цифры» верны? (выберите все верные)', op5('halb sechs → 5:30', 'halb elf → 10:30', 'halb sechs → 6:30', 'halb elf → 11:30', 'halb sechs → 5:00'), ['a', 'b'], 'halb значит «половина К следующему часу», поэтому halb sechs = 5:30, halb elf = 10:30.'),
  mc(18, 'HARD', 'В каких предложениях часть суток оформлена правильно? (выберите все верные)', op5('Es ist acht Uhr am Morgen.', 'Es ist neun Uhr am Abend.', 'Es ist acht Uhr im Morgen.', 'Es ist neun Uhr an Abend.', 'Es ist acht Uhr Morgen am.'), ['a', 'b'], 'Часть суток вводится предлогом am: am Morgen, am Abend.'),
  mc(19, 'HARD', 'Какие слова из этого урока написаны верно (большая буква и умлаут/ß где надо)? (выберите все верные)', op5('die Uhr', 'spät', 'die uhr', 'spaet', 'der morgen'), ['a', 'b'], 'Существительные с большой буквы (die Uhr), а в spät нужен умлаут ä.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкий вопрос: «Который час?»', items4('Wie', 'spät', 'ist', 'es?'), 'Wie spät ist es? — Который час?'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Сейчас три часа».', items4('Es', 'ist', 'drei', 'Uhr.'), 'Es ist drei Uhr — Сейчас три часа.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Сейчас девять часов».', items4('Es', 'ist', 'neun', 'Uhr.'), 'Es ist neun Uhr — Сейчас девять часов.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Сейчас ровно двенадцать».', items4('Es', 'ist', 'Punkt', 'zwölf.'), 'Es ist Punkt zwölf — Сейчас ровно двенадцать.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Сейчас половина седьмого».', items4('Es', 'ist', 'halb', 'sieben.'), 'Es ist halb sieben — Сейчас половина седьмого (6:30).'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкий вопрос: «Который сейчас час?»', items5('Wie', 'viel', 'Uhr', 'ist', 'es?'), 'Wie viel Uhr ist es? — Который час? (второй вариант вопроса).'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Сейчас восемь часов утра».', items5('Es', 'ist', 'acht', 'Uhr', 'am Morgen.'), 'Es ist acht Uhr am Morgen — Сейчас восемь часов утра.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Сейчас ровно один час».', items4('Es', 'ist', 'Punkt', 'eins.'), 'Es ist Punkt eins — Сейчас ровно один час.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Сейчас половина десятого».', items4('Es', 'ist', 'halb', 'zehn.'), 'Es ist halb zehn — Сейчас половина десятого (9:30).'),
  ord(29, 'HARD', 'Соберите немецкий вопрос: «Который сейчас час?» (со словом jetzt)', items5('Wie', 'spät', 'ist', 'es', 'jetzt?'), 'Wie spät ist es jetzt? — Который сейчас час?'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Wie ___ ist es? (Который час?)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('spät', 'Uhr', 'jetzt', 'halb', 'Abend'), 'Wie spät ist es? — стандартный вопрос о времени.'),
  fb(31, 'EASY', 'Es ist drei ___. (Сейчас три часа.)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('halb', 'Uhr', 'spät', 'jetzt', 'Morgen'), 'После числа стоит слово Uhr: drei Uhr.'),
  fb(32, 'EASY', '___ ist neun Uhr. (Сейчас девять часов.)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Wie', 'Wo', 'Es', 'Wer', 'Was'), 'Время называют через безличное Es ist.'),
  fb(33, 'EASY', 'Es ist ___ sieben. (Сейчас половина седьмого, 6:30.)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Abend', 'Uhr', 'spät', 'halb', 'Morgen'), 'halb sieben = 6:30 (половина К семи).'),
  fb(34, 'EASY', 'Es ist Punkt acht am ___. (ровно восемь утра)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Uhr', 'halb', 'spät', 'jetzt', 'Morgen'), 'am Morgen — «утром».'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Es ist ___ neun. (8:30 — половина к девяти.)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('halb', 'Abend', 'Uhr', 'spät', 'Morgen'), 'halb neun = 8:30 (половина К девяти).'),
  fb(36, 'HARD', 'Wie viel ___ ist es jetzt? (Который сейчас час?)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('spät', 'Uhr', 'halb', 'Punkt', 'Abend'), 'Wie viel Uhr ist es? — второй обычный вопрос о времени.'),
  fb(37, 'HARD', 'Es ist neun Uhr ___ Abend. (девять часов вечера)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('im', 'an', 'am', 'um', 'in'), 'Часть суток вводится предлогом am — am Abend.'),
  fb(38, 'HARD', 'Es ist ___ zwölf. (ровно двенадцать)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Abend', 'spät', 'Morgen', 'Punkt', 'Uhr'), 'Punkt zwölf — «ровно двенадцать».'),
  fb(39, 'HARD', 'Es ist ein ___. (Сейчас один час.)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Uhren', 'halb', 'spät', 'jetzt', 'Uhr'), 'Слово Uhr после числа не меняется: ein Uhr.'),
];
