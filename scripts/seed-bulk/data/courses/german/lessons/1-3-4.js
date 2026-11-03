'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie heißt «понедельник» auf Deutsch?', op4('Montag', 'Freitag', 'Sonntag', 'Mittwoch'), 'a', 'Понедельник по-немецки — der Montag.'),
  sc(1, 'EASY', 'Welches Wort bedeutet «неделя»?', op4('der Tag', 'die Woche', 'der Montag', 'heute'), 'b', '«Неделя» — это die Woche.'),
  sc(2, 'EASY', 'Wie heißt «суббота» auf Deutsch?', op4('Dienstag', 'Donnerstag', 'Samstag', 'Sonntag'), 'c', 'Суббота по-немецки — der Samstag.'),
  sc(3, 'EASY', 'Welcher Artikel passt: ___ Montag?', op4('die', 'das', 'dem', 'der'), 'd', 'Все дни недели мужского рода, поэтому der Montag.'),
  sc(4, 'EASY', 'Wie heißt «воскресенье» auf Deutsch?', op4('Samstag', 'Sonntag', 'Freitag', 'Montag'), 'b', 'Воскресенье по-немецки — der Sonntag.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wie sagt man «в понедельник»?', op4('an Montag', 'am Montag', 'in Montag', 'auf Montag'), 'b', 'Предлог an + dem сливается в am, поэтому am Montag.'),
  sc(6, 'HARD', 'Welcher Tag ist heute? — Heute ist ___.', op4('am Mittwoch', 'der Mittwoch', 'Mittwoch', 'die Mittwoch'), 'c', 'В ответе на вопрос о дне артикль и предлог не нужны: Heute ist Mittwoch.'),
  sc(7, 'HARD', 'Wie beantwortet man «Wann hast du Deutsch?»', op4('Am Montag.', 'Der Montag.', 'Heute Montag.', 'In Montag.'), 'a', 'На вопрос Wann? отвечаем «am + день»: Am Montag.'),
  sc(8, 'HARD', 'Welcher Satz ist richtig?', op4('Morgen ist der Freitag.', 'Morgen ist am Freitag.', 'Morgen ist Freitag.', 'Morgen ist die Freitag.'), 'c', 'После «ist» имя дня стоит без артикля: Morgen ist Freitag.'),
  sc(9, 'HARD', 'Wie heißt der Tag zwischen Dienstag und Donnerstag?', op4('Montag', 'Mittwoch', 'Freitag', 'Sonntag'), 'b', 'Между вторником и четвергом стоит среда — der Mittwoch.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Wochentage?', op5('Montag', 'Woche', 'Freitag', 'heute', 'Sonntag'), ['a', 'c', 'e'], 'Понедельник, пятница и воскресенье — это дни недели.'),
  mc(11, 'EASY', 'Welche Tage haben den Artikel der?', op5('Dienstag', 'Mittwoch', 'Woche', 'Donnerstag', 'morgen'), ['a', 'b', 'd'], 'Все дни недели мужского рода, поэтому der Dienstag, der Mittwoch, der Donnerstag.'),
  mc(12, 'EASY', 'Welche Wörter bedeuten Zeitangaben (heute/morgen/wann)?', op5('heute', 'Samstag', 'morgen', 'wann', 'Tag'), ['a', 'c', 'd'], 'heute — сегодня, morgen — завтра, wann — когда.'),
  mc(13, 'EASY', 'Welche Wörter schreibt man im Deutschen groß?', op5('Montag', 'heute', 'Woche', 'wann', 'Samstag'), ['a', 'c', 'e'], 'Существительные Montag, Woche, Samstag пишутся с большой буквы.'),
  mc(14, 'EASY', 'Welche zwei Tage gehören zum Wochenende?', op5('Samstag', 'Montag', 'Sonntag', 'Mittwoch', 'Freitag'), ['a', 'c'], 'К выходным относятся суббота (Samstag) и воскресенье (Sonntag).'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze sind grammatisch richtig?', op5('Am Montag arbeite ich.', 'Heute ist Dienstag.', 'In Montag arbeite ich.', 'Heute ist der Dienstag.', 'Morgen ist Freitag.'), ['a', 'b', 'e'], 'Верно: am + день, а после «ist» имя дня без артикля.'),
  mc(16, 'HARD', 'Welche Wörter passen in «___ habe ich Deutsch»?', op6('Am Montag', 'Am Freitag', 'Der Montag', 'Heute', 'In Freitag', 'Die Woche'), ['a', 'b', 'd'], 'Подходят «Am Montag», «Am Freitag» и «Heute».'),
  mc(17, 'HARD', 'Welche Antworten auf «Wann?» sind korrekt?', op5('Am Sonntag', 'Heute', 'Der Sonntag', 'Morgen', 'Die Woche'), ['a', 'b', 'd'], 'На вопрос Wann? подходят am + день, heute и morgen.'),
  mc(18, 'HARD', 'Welche Aussagen über die Woche sind richtig?', op5('Eine Woche hat sieben Tage.', 'Montag ist ein Tag.', 'Eine Woche hat fünf Tage.', 'Sonntag ist ein Tag.', 'Heute ist eine Woche.'), ['a', 'b', 'd'], 'В неделе семь дней, а Montag и Sonntag — это дни.'),
  mc(19, 'HARD', 'Welche Tage liegen zwischen Montag und Freitag?', op5('Dienstag', 'Mittwoch', 'Sonntag', 'Donnerstag', 'Samstag'), ['a', 'b', 'd'], 'Между понедельником и пятницей лежат Dienstag, Mittwoch и Donnerstag.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Сегодня среда».', items4('Heute', 'ist', 'Mittwoch', '.'), 'Heute ist Mittwoch — стандартный порядок: время, глагол, день.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Завтра пятница».', items4('Morgen', 'ist', 'Freitag', '.'), 'Morgen ist Freitag — наречие времени стоит первым.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «В неделе семь дней».', items5('Eine', 'Woche', 'hat', 'sieben', 'Tage'), 'Eine Woche hat sieben Tage — подлежащее, глагол, дополнение.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Сегодня понедельник».', items4('Heute', 'ist', 'Montag', '.'), 'Heute ist Montag — день стоит без артикля после ist.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Воскресенье — это день».', items4('Sonntag', 'ist', 'ein', 'Tag'), 'Sonntag ist ein Tag — глагол ist стоит на втором месте.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «В понедельник у меня немецкий».', items4('Am', 'Montag', 'habe ich', 'Deutsch'), 'Am Montag habe ich Deutsch — после обстоятельства времени глагол стоит на втором месте.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «В воскресенье я не работаю».', items5('Am', 'Sonntag', 'arbeite', 'ich', 'nicht'), 'Am Sonntag arbeite ich nicht — глагол на втором месте, nicht в конце.'),
  ord(27, 'HARD', 'Соберите немецкий вопрос: «Какой сегодня день?».', items4('Welcher', 'Tag', 'ist', 'heute?'), 'Welcher Tag ist heute? — вопросительное слово, существительное, глагол.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Завтра я учу немецкий».', items5('Morgen', 'lerne', 'ich', 'Deutsch', '.'), 'Morgen lerne ich Deutsch — глагол на втором месте после Morgen.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «В среду я учу немецкий».', items5('Am', 'Mittwoch', 'lerne', 'ich', 'Deutsch'), 'Am Mittwoch lerne ich Deutsch — глагол на втором месте после обстоятельства времени.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', '___ Montag (артикль дня недели).', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('die', 'der', 'das', 'den', 'dem'), 'Дни недели мужского рода, поэтому der Montag.'),
  fb(31, 'EASY', 'Heute ist ___. (среда)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Montag', 'Freitag', 'Mittwoch', 'Sonntag', 'Samstag'), 'Среда по-немецки — Mittwoch.'),
  fb(32, 'EASY', 'Eine ___ hat sieben Tage. (неделя)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Tag', 'Montag', 'Sonntag', 'Woche', 'Welcher'), 'В неделе семь дней — eine Woche.'),
  fb(33, 'EASY', '___ ist Freitag. (завтра)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Morgen', 'Tage', 'hat', 'sieben', 'Woche'), 'Завтра по-немецки — morgen, поэтому Morgen ist Freitag.'),
  fb(34, 'EASY', 'Wie heißt «вторник»? — Der ___.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Montag', 'Mittwoch', 'Samstag', 'Sonntag', 'Dienstag'), 'Вторник по-немецки — der Dienstag.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', '___ Montag habe ich Deutsch. (в понедельник)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('In', 'An', 'Am', 'Der', 'Die'), 'Предлог an + dem сливается в am: am Montag.'),
  fb(36, 'HARD', 'Am Sonntag ___ ich nicht. (работать)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('arbeiten', 'arbeite', 'arbeitest', 'arbeitet', 'gearbeitet'), 'С ich глагол arbeiten имеет форму arbeite.'),
  fb(37, 'HARD', '___ Tag ist heute? (какой)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Wann', 'Wie', 'Was', 'Welcher', 'Wer'), 'Вопрос «какой день?» — Welcher Tag?'),
  fb(38, 'HARD', '___ hast du Deutsch? — Am Montag. (когда)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Wann', 'Welcher', 'Wie', 'Heute', 'Morgen'), 'Вопрос о времени «когда?» — wann.'),
  fb(39, 'HARD', 'Der Tag nach Freitag ist der ___. (суббота)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Montag', 'Sonntag', 'Mittwoch', 'Dienstag', 'Samstag'), 'День после пятницы — суббота, der Samstag.'),
];
