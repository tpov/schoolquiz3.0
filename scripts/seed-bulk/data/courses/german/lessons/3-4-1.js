'use strict';

const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie heißt «вокзал» auf Deutsch?', op4('der Bahnhof', 'die Post', 'der Park', 'das Museum'), 'a', 'Вокзал по-немецки — der Bahnhof.'),
  sc(1, 'EASY', 'Wie heißt «аптека» auf Deutsch?', op4('die Bank', 'die Apotheke', 'die Kirche', 'die Straße'), 'b', 'Аптека по-немецки — die Apotheke.'),
  sc(2, 'EASY', 'Welcher Artikel passt: ___ Rathaus?', op4('der', 'die', 'das', 'den'), 'c', 'Слово Rathaus среднего рода, поэтому артикль das.'),
  sc(3, 'EASY', 'Wo ist der Park? — Der Park ist ___ Zentrum.', op4('im', 'am', 'auf', 'an'), 'a', 'In + dem сливается в im, поэтому im Zentrum.'),
  sc(4, 'EASY', 'Welches Wort bedeutet «город»?', op4('die Stadt', 'der Platz', 'das Zentrum', 'die Kirche'), 'a', 'Город по-немецки — die Stadt.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wo ist die Post? — Die Post ist ___ Markt.', op4('im', 'in', 'am', 'an'), 'c', 'An + dem сливается в am, поэтому am Markt.'),
  sc(6, 'HARD', 'Welcher Satz hat die richtige Wortstellung?', op4('Im Zentrum das Rathaus liegt.', 'Das Rathaus liegt im Zentrum.', 'Das Rathaus im Zentrum liegt.', 'Liegt das Rathaus im Zentrum nicht.'), 'b', 'В утверждении глагол стоит на втором месте — Das Rathaus liegt im Zentrum.'),
  sc(7, 'HARD', 'Die Apotheke ___ neben dem Supermarkt.', op4('liegen', 'liegt', 'liege', 'liegst'), 'b', 'С подлежащим die Apotheke (она) глагол liegen в форме liegt.'),
  sc(8, 'HARD', 'Welche Präposition passt: Die Kirche ist ___ Bahnhof (рядом с)?', op4('in dem', 'neben dem', 'auf dem', 'an der'), 'b', 'Рядом с чем-то — neben dem, с существительным мужского рода Bahnhof в Dativ.'),
  sc(9, 'HARD', 'Wo steht das Museum? — Es steht ___ Marktplatz.', op4('im', 'am', 'in', 'auf'), 'b', 'An + dem = am, поэтому музей стоит am Marktplatz.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Orte in der Stadt? (несколько)', op5('die Post', 'der Bahnhof', 'die Milch', 'das Museum', 'der Apfel'), ['a', 'b', 'd'], 'Местами в городе являются die Post, der Bahnhof и das Museum.'),
  mc(11, 'EASY', 'Welche Substantive haben den Artikel «die»? (несколько)', op5('die Bank', 'der Park', 'die Apotheke', 'das Zentrum', 'die Kirche'), ['a', 'c', 'e'], 'Артикль die имеют существительные Bank, Apotheke и Kirche.'),
  mc(12, 'EASY', 'Welche Wörter bedeuten «больница» oder «супермаркет»? (несколько)', op5('das Krankenhaus', 'die Straße', 'der Supermarkt', 'der Platz', 'das Museum'), ['a', 'c'], 'Больница — das Krankenhaus, супермаркет — der Supermarkt.'),
  mc(13, 'EASY', 'In welchen Sätzen geht es um einen Ort? (несколько)', op5('Hier ist die Post.', 'Ich esse einen Apfel.', 'Dort ist die Bank.', 'Das Wetter ist schön.', 'Der Park liegt im Zentrum.'), ['a', 'c', 'e'], 'О месте говорят предложения про die Post, die Bank и den Park.'),
  mc(14, 'EASY', 'Welche Wörter sind Gebäude? (несколько)', op5('das Rathaus', 'die Kirche', 'der Apfel', 'das Krankenhaus', 'die Milch'), ['a', 'b', 'd'], 'Зданиями являются das Rathaus, die Kirche и das Krankenhaus.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'In welchen Sätzen ist die Verschmelzung von Präposition und Artikel richtig? (несколько)', op5('Der Park ist im Zentrum.', 'Die Post ist an dem Markt.', 'Das Museum ist am Marktplatz.', 'Die Bank ist in dem Stadt.', 'Der Bahnhof ist im Zentrum.'), ['a', 'c', 'e'], 'Слияние предлога с артиклем верно: im Zentrum (in+dem) и am Marktplatz (an+dem); вариант in dem Stadt неверен — Stadt женского рода, нужно in der Stadt.'),
  mc(16, 'HARD', 'Welche Sätze haben die richtige Wortstellung? (несколько)', op5('Das Rathaus liegt im Zentrum.', 'Im Zentrum liegt das Rathaus.', 'Das Rathaus im Zentrum liegt.', 'Die Post steht am Markt.', 'Am Markt die Post steht.'), ['a', 'b', 'd'], 'Глагол на втором месте только в этих трёх предложениях.'),
  mc(17, 'HARD', 'Welche Verben beschreiben die Lage eines Gebäudes? (несколько)', op5('liegen', 'essen', 'stehen', 'sein', 'trinken'), ['a', 'c', 'd'], 'Расположение здания описывают глаголы liegen, stehen и sein.'),
  mc(18, 'HARD', 'Welche Antworten auf «Wo ist die Apotheke?» sind grammatisch richtig? (несколько)', op5('Sie ist im Zentrum.', 'Sie ist neben dem Supermarkt.', 'Sie ist neben der Supermarkt.', 'Sie ist am Markt.', 'Sie ist in das Zentrum.'), ['a', 'b', 'd'], 'Ответ на Wo? требует Dativ — верны im Zentrum, neben dem Supermarkt и am Markt.'),
  mc(19, 'HARD', 'In welchen Sätzen ist der Artikel im Dativ korrekt? (несколько)', op5('Die Bank ist neben dem Bahnhof.', 'Die Bank ist neben der Bahnhof.', 'Das Café ist in der Straße.', 'Der Park ist hinter dem Museum.', 'Der Park ist hinter das Museum.'), ['a', 'c', 'd'], 'Dativ верен в neben dem Bahnhof, in der Straße и hinter dem Museum.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Вокзал в центре».', items4('Der', 'Bahnhof', 'ist', 'im Zentrum'), 'Порядок: подлежащее, глагол на втором месте, затем обстоятельство места.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Вот почта».', items4('Hier', 'ist', 'die', 'Post'), 'После hier на втором месте стоит глагол ist.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Парк в центре».', items4('Der', 'Park', 'liegt', 'im Zentrum'), 'Глагол liegt стоит на втором месте.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Там банк».', items4('Dort', 'ist', 'die', 'Bank'), 'После dort глагол ist занимает второе место.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Музей на площади».', items4('Das', 'Museum', 'steht', 'am Marktplatz'), 'Глагол steht на втором месте, затем am Marktplatz.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Аптека рядом с супермаркетом».', items5('Die', 'Apotheke', 'liegt', 'neben dem', 'Supermarkt'), 'Предлог neben требует Dativ: neben dem Supermarkt.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Где находится ратуша?».', items4('Wo', 'liegt', 'das', 'Rathaus'), 'В вопросе с Wo глагол стоит сразу после вопросительного слова.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Церковь стоит на площади».', items5('Die', 'Kirche', 'steht', 'auf dem', 'Platz'), 'Auf + dem даёт Dativ: auf dem Platz.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Банк находится рядом с вокзалом».', items5('Die', 'Bank', 'liegt', 'neben dem', 'Bahnhof'), 'Neben dem Bahnhof — мужской род в Dativ.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «В центре находится большая больница».', items5('Im Zentrum', 'liegt', 'das', 'große', 'Krankenhaus'), 'Обстоятельство впереди, но глагол всё равно на втором месте; после das окончание -e: das große Krankenhaus.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Der Bahnhof ist ___ Zentrum.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('im', 'in', 'an', 'auf', 'bei'), 'In + dem сливается в im, поэтому im Zentrum; остальные предлоги здесь требовали бы артикля.'),
  fb(31, 'EASY', '___ Apotheke liegt neben dem Supermarkt.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Der', 'Die', 'Das', 'Den', 'Dem'), 'Слово Apotheke женского рода, поэтому артикль die.'),
  fb(32, 'EASY', 'Das Museum ___ am Marktplatz.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('stehe', 'stehst', 'steht', 'stehen', 'standen'), 'С подлежащим das Museum (оно) глагол stehen в форме steht.'),
  fb(33, 'EASY', 'Hier ist die Post, und ___ ist die Bank.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('im', 'am', 'zu', 'dort', 'mit'), 'Противоположность hier (здесь) — это dort (там); предлоги im/am/zu/mit это место занять не могут.'),
  fb(34, 'EASY', '___ ist der Park? — Im Zentrum.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Wer', 'Was', 'Wie', 'Wann', 'Wo'), 'О месте спрашивают словом Wo? (где?).'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Die Post ist ___ Markt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('am', 'ans', 'ins', 'aufs', 'um'), 'An + dem сливается в am (Dativ), поэтому am Markt; остальные формы (ans, ins, aufs — это an/in/auf + das) не подходят к мужскому Markt.'),
  fb(36, 'HARD', 'Die Apotheke liegt neben ___ Supermarkt.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('der', 'dem', 'das', 'den', 'die'), 'Neben требует Dativ, мужской род — dem Supermarkt.'),
  fb(37, 'HARD', 'Das Rathaus ___ im Zentrum der Stadt.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('liege', 'liegst', 'liegt', 'liegen', 'lagst'), 'С подлежащим das Rathaus глагол liegen в форме liegt.'),
  fb(38, 'HARD', 'Die Bank steht neben ___ Bahnhof.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('die', 'das', 'der', 'dem', 'den'), 'Neben + Dativ, мужской род Bahnhof — dem Bahnhof.'),
  fb(39, 'HARD', 'Die Kirche steht auf ___ Platz.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('der', 'dem', 'das', 'den', 'die'), 'Вопрос Wo? + auf требует Dativ; мужской род Platz — auf dem Platz.'),
];
