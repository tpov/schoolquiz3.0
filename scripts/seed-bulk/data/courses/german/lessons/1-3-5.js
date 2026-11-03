'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Welches Wort fehlt: «Ich stehe ___ sieben Uhr auf»?', op4('um', 'am', 'in', 'an'), 'a', 'Предлог «um» используется для указания точного времени.'),
  sc(1, 'EASY', 'Wie sagt man «в восемь часов» auf Deutsch?', op4('am acht Uhr', 'um acht Uhr', 'in acht Uhr', 'an acht Uhr'), 'b', 'Точное время вводится предлогом «um»: um acht Uhr.'),
  sc(2, 'EASY', 'Welches Wort fehlt: «___ Montag gehe ich zur Arbeit»?', op4('Um', 'In', 'Am', 'An'), 'c', 'С днями недели используется «am»: am Montag (в понедельник).'),
  sc(3, 'EASY', 'Was bedeutet «das Frühstück»?', op4('обед', 'ужин', 'завтрак', 'полдень'), 'c', 'Слово «das Frühstück» означает завтрак.'),
  sc(4, 'EASY', 'Wie heißt das Verb «вставать» auf Deutsch?', op4('essen', 'aufstehen', 'anfangen', 'arbeiten'), 'b', 'Глагол «aufstehen» означает вставать.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz ist korrekt?', op4('Ich aufstehe um sieben Uhr.', 'Ich stehe um sieben Uhr auf.', 'Ich stehe auf um sieben Uhr.', 'Auf ich stehe um sieben Uhr.'), 'b', 'У глагола «aufstehen» отделяемая приставка «auf» уходит в конец предложения.'),
  sc(6, 'HARD', 'Wie heißt es richtig: «В понедельник в девять часов начинается занятие»?', op4('Am Montag um neun Uhr beginnt der Unterricht.', 'Am Montag um neun Uhr der Unterricht beginnt.', 'Um Montag am neun Uhr beginnt der Unterricht.', 'Am Montag beginnt um neun Uhr der Unterricht den.'), 'a', 'После обстоятельства времени глагол стоит на 2-м месте, потому верно «beginnt der Unterricht».'),
  sc(7, 'HARD', 'Welches Wort fehlt: «Ich arbeite ___ neun bis fünf»?', op4('um', 'am', 'von', 'an'), 'c', 'Конструкция «von ... bis ...» (с ... до ...) начинается с «von».'),
  sc(8, 'HARD', 'Welcher Satz mit Inversion ist korrekt?', op4('Am Freitag ich gehe ins Kino.', 'Am Freitag gehe ich ins Kino.', 'Am Freitag gehen ich ins Kino.', 'Ins Kino am Freitag ich gehe.'), 'b', 'При выносе времени в начало глагол остаётся на 2-м месте: «gehe ich».'),
  sc(9, 'HARD', 'Wie fragt man «Во сколько мы едим?» korrekt?', op4('Wann viel Uhr essen wir?', 'Um wie viel Uhr essen wir?', 'Um wie viel Uhr wir essen?', 'Wie viel Uhr um essen wir?'), 'b', 'Вопрос о времени: «Um wie viel Uhr ...?» с глаголом на 2-м месте.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter bezeichnen Mahlzeiten oder Zeiten am Tag?', op5('das Frühstück', 'der Mittag', 'die Arbeit', 'aufstehen', 'der Termin'), ['a', 'b'], 'К приёму пищи и времени суток относятся «das Frühstück» и «der Mittag».'),
  mc(11, 'EASY', 'Welche Präpositionen passen zur Zeit (um/am)?', op5('um acht Uhr', 'am Montag', 'in acht Uhr', 'an Montag', 'um Frühstück'), ['a', 'b'], 'Верны «um acht Uhr» (точное время) и «am Montag» (день недели).'),
  mc(12, 'EASY', 'Welche Wörter sind Wochentage?', op6('Montag', 'Dienstag', 'Frühstück', 'Freitag', 'Arbeit', 'Termin'), ['a', 'b', 'd'], 'Дни недели здесь — Montag, Dienstag и Freitag.'),
  mc(13, 'EASY', 'Welche Verben passen zum Tagesablauf?', op5('aufstehen', 'anfangen', 'essen', 'das Haus', 'der Mittag'), ['a', 'b', 'c'], 'Глаголы распорядка дня: aufstehen, anfangen, essen.'),
  mc(14, 'EASY', 'Welche Übersetzungen sind richtig?', op5('um — в (о времени)', 'der Termin — встреча', 'essen — спать', 'die Arbeit — работа', 'der Mittag — утро'), ['a', 'b', 'd'], 'Верны um (в, о времени), der Termin (встреча) и die Arbeit (работа).'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Ich stehe um sieben Uhr auf.', 'Am Freitag gehe ich ins Kino.', 'Ich aufstehe um sieben.', 'Am Montag ich arbeite.', 'Der Unterricht fängt um neun an.'), ['a', 'b', 'e'], 'Корректны предложения с глаголом на 2-м месте и отделяемой приставкой в конце.'),
  mc(16, 'HARD', 'In welchen Sätzen steht das Verb richtig auf Position 2?', op5('Am Montag beginnt der Unterricht.', 'Um acht Uhr ich esse Frühstück.', 'Am Dienstag habe ich einen Termin.', 'Am Mittag wir essen.', 'Von neun bis fünf arbeite ich.'), ['a', 'c', 'e'], 'Глагол на 2-м месте: beginnt, habe и arbeite после обстоятельства времени.'),
  mc(17, 'HARD', 'Welche Zeitangaben sind korrekt gebildet?', op5('um halb elf', 'am neun Uhr', 'von neun bis fünf', 'um Dienstag', 'am Montag'), ['a', 'c', 'e'], 'Корректны um halb elf, von neun bis fünf и am Montag.'),
  mc(18, 'HARD', 'Welche Verben haben eine trennbare Vorsilbe?', op5('aufstehen', 'anfangen', 'essen', 'arbeiten', 'wohnen'), ['a', 'b'], 'Отделяемые приставки имеют «aufstehen» (auf) и «anfangen» (an).'),
  mc(19, 'HARD', 'Welche Fragen nach der Zeit sind korrekt?', op5('Um wie viel Uhr stehst du auf?', 'Wann hast du einen Termin?', 'Wie viel Uhr du isst?', 'An welchem Tag arbeitest du?', 'Um Tag gehst du ins Kino?'), ['a', 'b', 'd'], 'Корректны вопросы про время и день с глаголом на 2-м месте.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я встаю в семь часов».', items4('Ich', 'stehe', 'um sieben Uhr', 'auf'), 'Отделяемая приставка «auf» уходит в конец, время — um sieben Uhr: Ich stehe um sieben Uhr auf.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Завтрак в восемь часов».', items4('Das Frühstück', 'ist', 'um acht', 'Uhr'), 'Глагол «ist» на 2-м месте, время вводится предлогом um: Das Frühstück ist um acht Uhr.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «В понедельник я работаю».', items4('Am', 'Montag', 'arbeite', 'ich'), 'После обстоятельства времени глагол на 2-м месте: Am Montag arbeite ich.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Урок начинается в девять».', items4('Der', 'Unterricht', 'beginnt', 'um neun'), 'Глагол на 2-м месте: Der Unterricht beginnt um neun.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Мы едим в полдень».', items4('Wir', 'essen', 'am', 'Mittag'), 'С временем суток используется am: Wir essen am Mittag.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «В понедельник в девять часов начинается занятие».', items5('Am Montag', 'um neun Uhr', 'beginnt', 'der', 'Unterricht'), 'Сначала день и время, затем глагол на 2-м месте: ... beginnt der Unterricht.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Встреча во вторник в половине одиннадцатого».', items5('Der Termin', 'ist', 'am Dienstag', 'um halb', 'elf'), 'Глагол «ist» на 2-м месте, затем время: ... ist am Dienstag um halb elf.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Я работаю с девяти до пяти».', items5('Ich', 'arbeite', 'von neun', 'bis', 'fünf'), 'Конструкция von ... bis ...: Ich arbeite von neun bis fünf.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «В пятницу я иду в кино».', items5('Am Freitag', 'gehe', 'ich', 'ins', 'Kino'), 'При инверсии глагол на 2-м месте: Am Freitag gehe ich ins Kino.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Во вторник в восемь у меня встреча».', items5('Am Dienstag', 'um acht', 'habe', 'ich', 'einen Termin'), 'После обстоятельства времени глагол на 2-м месте: ... habe ich einen Termin.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich stehe ___ sieben Uhr auf.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('um', 'am', 'in', 'an', 'von'), 'Точное время вводится предлогом «um».'),
  fb(31, 'EASY', '___ Montag gehe ich zur Arbeit.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Um', 'Am', 'In', 'An', 'Von'), 'С днями недели используется «am»: am Montag.'),
  fb(32, 'EASY', 'Wir essen am ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Termin', 'Arbeit', 'Mittag', 'Unterricht', 'Frühstück'), 'Время суток «am Mittag» означает в полдень.'),
  fb(33, 'EASY', 'Ich esse das ___ um acht Uhr.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Mittag', 'Arbeit', 'Termin', 'Frühstück', 'Unterricht'), 'Утренний приём пищи — «das Frühstück» (завтрак).'),
  fb(34, 'EASY', 'Ich arbeite von neun ___ fünf.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('um', 'am', 'an', 'in', 'bis'), 'Конструкция «von ... bis ...» закрывается словом «bis».'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Am Montag um neun Uhr ___ der Unterricht.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('beginne', 'beginnt', 'beginnen', 'beginnst', 'begonnen'), 'Подлежащее «der Unterricht» требует формы 3-го лица «beginnt».'),
  fb(36, 'HARD', 'Um wie viel Uhr ___ du auf?', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('stehe', 'steht', 'stehst', 'stehen', 'steh'), 'С местоимением «du» глагол «aufstehen» спрягается как «stehst ... auf».'),
  fb(37, 'HARD', 'Am Freitag ___ ich ins Kino.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('gehst', 'gehen', 'geht', 'gehe', 'gegangen'), 'При инверсии глагол на 2-м месте, с «ich» форма «gehe».'),
  fb(38, 'HARD', 'Der Termin ist am ___ um halb elf.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Uhr', 'Termin', 'Arbeit', 'Frühstück', 'Dienstag'), 'День недели «Dienstag» (вторник) ставится после «am».'),
  fb(39, 'HARD', 'Ich ___ das Frühstück am Mittag.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('esse', 'isst', 'essen', 'esst', 'gegessen'), 'С местоимением «ich» глагол «essen» имеет форму «esse».'),
];
