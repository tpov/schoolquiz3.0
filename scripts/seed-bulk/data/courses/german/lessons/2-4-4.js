'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ----- 5 sc EASY (idx 0..4) -----
  sc(0, 'EASY', 'Wie spät ist es? — Es ist ___ Uhr (7:00).', op4('sieben', 'sieben Stunde', 'sieben Morgen', 'sieben spät'), 'a', 'Целый час называют числом + Uhr: «Es ist sieben Uhr».'),
  sc(1, 'EASY', 'Welche Präposition passt? Ich stehe ___ 7 Uhr auf.', op4('am', 'in', 'um', 'an'), 'c', 'Точное время вводится предлогом um: «um 7 Uhr».'),
  sc(2, 'EASY', 'Welche Präposition passt? ___ Morgen trinke ich Kaffee.', op4('Am', 'Um', 'In', 'Vor'), 'a', 'С частями суток используют предлог am: «am Morgen».'),
  sc(3, 'EASY', 'Wie heißt «вечер» auf Deutsch?', op4('der Mittag', 'der Morgen', 'der Abend', 'die Nacht'), 'c', 'Слово «вечер» по-немецки — der Abend.'),
  sc(4, 'EASY', 'Wie fragt man «Который час?» auf Deutsch?', op4('Wie alt bist du?', 'Wie spät ist es?', 'Wie geht es?', 'Wie viel kostet es?'), 'b', 'Вопрос о времени звучит как «Wie spät ist es?».'),

  // ----- 5 sc HARD (idx 5..9) -----
  sc(5, 'HARD', 'Wie sagt man 8:30 auf Deutsch?', op4('Es ist halb acht.', 'Es ist halb neun.', 'Es ist Viertel nach acht.', 'Es ist Viertel vor acht.'), 'b', 'Немцы считают половину к следующему часу, поэтому 8:30 — «halb neun».'),
  sc(6, 'HARD', 'Wie sagt man 8:45 auf Deutsch?', op4('Es ist Viertel nach acht.', 'Es ist halb neun.', 'Es ist Viertel vor neun.', 'Es ist Viertel vor acht.'), 'c', '8:45 — это «без четверти девять», то есть «Viertel vor neun».'),
  sc(7, 'HARD', 'Welche Präposition fehlt? ___ der Nacht schlafe ich.', op4('Am', 'Um', 'In', 'An'), 'c', 'С Nacht употребляют исключение «in der Nacht», а не am.'),
  sc(8, 'HARD', 'Welcher Satz hat die richtige Wortstellung?', op4('Am Abend ich bin zu Hause.', 'Am Abend bin ich zu Hause.', 'Am Abend zu Hause ich bin.', 'Ich am Abend bin zu Hause.'), 'b', 'После обстоятельства в начале глагол стоит на втором месте: «Am Abend bin ich…».'),
  sc(9, 'HARD', 'Wie sagt man 8:15 auf Deutsch?', op4('Es ist Viertel vor acht.', 'Es ist halb acht.', 'Es ist Viertel nach acht.', 'Es ist Viertel nach neun.'), 'c', '8:15 — это «четверть после восьми», то есть «Viertel nach acht».'),

  // ----- 5 mc EASY (idx 10..14) -----
  mc(10, 'EASY', 'Welche Wörter bezeichnen eine Tageszeit (часть суток)?', op5('der Morgen', 'die Uhr', 'der Abend', 'die Stunde', 'früh'), ['a', 'c'], 'der Morgen и der Abend — это части суток (утро и вечер).'),
  mc(11, 'EASY', 'Welche Sätze verwenden die Präposition um richtig?', op5('Ich komme um 8 Uhr.', 'Ich komme um Morgen.', 'Der Film beginnt um 9 Uhr.', 'Ich schlafe um der Nacht.', 'Wir essen um Abend.'), ['a', 'c'], 'Предлог um ставят только перед точным временем с Uhr.'),
  mc(12, 'EASY', 'Welche Antworten auf «Wie spät ist es?» sind korrekt?', op5('Es ist drei Uhr.', 'Es ist halb vier.', 'Es ist am Morgen.', 'Es ist um vier.', 'Es ist Montag.'), ['a', 'b'], 'На вопрос о времени отвечают «Es ist … Uhr» или «Es ist halb …».'),
  mc(13, 'EASY', 'Welche Sätze verwenden am richtig?', op5('Am Morgen trinke ich Tee.', 'Am 7 Uhr stehe ich auf.', 'Am Abend lese ich.', 'Am der Nacht schlafe ich.', 'Am halb acht komme ich.'), ['a', 'c'], 'Предлог am используют с частями суток: am Morgen, am Abend.'),
  mc(14, 'EASY', 'Welche Wörter passen zum Thema «время»?', op6('die Uhr', 'die Stunde', 'der Tisch', 'der Mittag', 'das Buch', 'die Tür'), ['a', 'b', 'd'], 'die Uhr, die Stunde и der Mittag связаны с темой времени.'),

  // ----- 5 mc HARD (idx 15..19) -----
  mc(15, 'HARD', 'Welche Uhrzeiten sind korrekt formuliert?', op5('Es ist halb neun. (8:30)', 'Es ist Viertel nach acht. (8:15)', 'Es ist halb acht. (8:30)', 'Es ist Viertel vor acht. (8:45)', 'Es ist neun Uhr. (8:00)'), ['a', 'b'], 'Верны только halb neun для 8:30 и Viertel nach acht для 8:15.'),
  mc(16, 'HARD', 'Welche Sätze haben die richtige Wortstellung mit Zeitangabe am Anfang?', op5('Am Vormittag arbeite ich.', 'Am Vormittag ich arbeite.', 'Um 7 Uhr stehe ich auf.', 'Um 7 Uhr ich stehe auf.', 'Am Mittag wir essen.'), ['a', 'c'], 'После обстоятельства времени глагол стоит на втором месте, перед подлежащим.'),
  mc(17, 'HARD', 'Welche Präpositionen sind hier richtig eingesetzt?', op5('in der Nacht', 'am Montag', 'um Morgen', 'am 8 Uhr', 'um die Nacht'), ['a', 'b'], 'Правильно: «in der Nacht» и «am Montag» (с днями недели — am).'),
  mc(18, 'HARD', 'Welche Aussagen über die deutsche Uhrzeit stimmen?', op5('«halb acht» bedeutet 7:30.', '«Viertel vor neun» bedeutet 8:45.', '«halb acht» bedeutet 8:30.', '«Viertel nach acht» bedeutet 8:45.', '«Viertel vor neun» bedeutet 9:15.'), ['a', 'b'], 'halb acht = 7:30 (половина к восьми), а Viertel vor neun = 8:45.'),
  mc(19, 'HARD', 'Welche Fragen passen, um nach der Zeit oder dem Zeitpunkt zu fragen?', op5('Wie spät ist es?', 'Wann beginnt der Unterricht?', 'Wie alt ist die Uhr?', 'Wie viel kostet der Morgen?', 'Wer ist es?'), ['a', 'b'], '«Wie spät ist es?» спрашивает время, а «Wann?» — когда что-то происходит.'),

  // ----- 5 ord EASY (idx 20..24) -----
  ord(20, 'EASY', 'Соберите немецкое предложение: «Сейчас семь часов».', items4('Es', 'ist', 'sieben', 'Uhr'), 'Целый час: «Es ist sieben Uhr».'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Я встаю в семь».', items5('Ich', 'stehe', 'um', 'sieben', 'auf'), 'С отделяемой приставкой: «Ich stehe um sieben auf»; приставка auf уходит в конец.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Утром я пью кофе».', items5('Am', 'Morgen', 'trinke', 'ich', 'Kaffee'), 'После «Am Morgen» глагол на втором месте: «Am Morgen trinke ich Kaffee».'),
  ord(23, 'EASY', 'Соберите немецкий вопрос: «Который час?».', items4('Wie', 'spät', 'ist', 'es'), 'Вопрос о времени: «Wie spät ist es?».'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Вечером я дома».', items5('Am', 'Abend', 'bin', 'ich', 'zu Hause'), 'После «Am Abend» глагол bin стоит на втором месте.'),

  // ----- 5 ord HARD (idx 25..29) -----
  ord(25, 'HARD', 'Соберите немецкое предложение: «Урок начинается в четверть девятого».', items5('Der Unterricht', 'beginnt', 'um', 'Viertel', 'nach acht'), 'Точное время с um: «um Viertel nach acht» (8:15).'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Ночью я охотно сплю».', items4('In der Nacht', 'schlafe', 'ich', 'gern'), 'Исключение «In der Nacht», далее глагол на втором месте: «In der Nacht schlafe ich gern» (gern — охотно).'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Сейчас половина девятого».', items5('Es', 'ist', 'jetzt', 'halb', 'neun'), '8:30 по-немецки — «halb neun» (половина к девяти).'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «В полдень мы вместе едим суп».', items5('Am Mittag', 'essen', 'wir', 'zusammen', 'Suppe'), 'После «Am Mittag» глагол essen стоит на втором месте: «Am Mittag essen wir zusammen Suppe».'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «В понедельник урок начинается очень рано».', items5('Am Montag', 'beginnt', 'der Unterricht', 'sehr', 'früh'), 'С днями недели — am: «Am Montag beginnt der Unterricht sehr früh».'),

  // ----- 5 fb EASY (idx 30..34) -----
  fb(30, 'EASY', 'Ich stehe ___ 7 Uhr auf.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('am', 'um', 'in', 'an', 'vor'), 'Точное время вводят предлогом um.'),
  fb(31, 'EASY', '___ Morgen trinke ich Kaffee.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Am', 'Um', 'In', 'An', 'Vor'), 'С частями суток используют предлог am.'),
  fb(32, 'EASY', 'Wie spät ist es? — Es ist sieben ___.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Morgen', 'Stunde', 'Uhr', 'Nacht', 'Abend'), 'Целый час называют со словом Uhr.'),
  fb(33, 'EASY', '___ Abend bin ich zu Hause.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Um', 'In', 'Vor', 'Am', 'An'), 'С частью суток Abend — предлог am.'),
  fb(34, 'EASY', '«Который час?» — «Wie ___ ist es?»', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('alt', 'viel', 'lange', 'oft', 'spät'), 'Вопрос о времени: «Wie spät ist es?».'),

  // ----- 5 fb HARD (idx 35..39) -----
  fb(35, 'HARD', '8:30 — Es ist halb ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('neun', 'acht', 'sieben', 'zehn', 'sechs'), 'В немецком 8:30 — это «halb neun», половина к девяти.'),
  fb(36, 'HARD', '8:45 — Es ist Viertel ___ neun.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('um', 'nach', 'vor', 'am', 'in'), '8:45 — «без четверти девять», то есть «Viertel vor neun».'),
  fb(37, 'HARD', '8:15 — Es ist Viertel ___ acht.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('vor', 'um', 'am', 'nach', 'in'), '8:15 — «четверть после восьми», то есть «Viertel nach acht».'),
  fb(38, 'HARD', '___ der Nacht schlafe ich.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Am', 'Um', 'An', 'Vor', 'In'), 'С Nacht — исключение «in der Nacht».'),
  fb(39, 'HARD', '___ Montag beginnt der Unterricht früh.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Um', 'Am', 'In', 'Vor', 'An'), 'С днями недели используют предлог am: «am Montag».'),
];
