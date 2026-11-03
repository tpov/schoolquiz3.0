'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ---- 5 sc EASY (idx 0..4) ----
  sc(0, 'EASY', 'Ich ___ gestern Deutsch gelernt.', op4('habe', 'hast', 'hat', 'haben'), 'a', 'С местоимением ich вспомогательный глагол haben имеет форму habe.'),
  sc(1, 'EASY', 'Wie heißt das Partizip II vom Verb „machen“?', op4('gemacht', 'gemachen', 'machte', 'gemach'), 'a', 'Partizip II слабого глагола machen образуется как ge- + mach + -t = gemacht.'),
  sc(2, 'EASY', 'Du ___ Fußball gespielt.', op4('habe', 'hast', 'hat', 'habt'), 'b', 'С местоимением du форма haben — hast.'),
  sc(3, 'EASY', 'Welcher Satz steht im Perfekt?', op4('Ich habe gespielt.', 'Ich spiele.', 'Ich werde spielen.', 'Ich spielte.'), 'a', 'Perfekt состоит из спрягаемого haben и Partizip II в конце: habe gespielt.'),
  sc(4, 'EASY', 'Was hast du ___?', op4('machen', 'gemacht', 'macht', 'machte'), 'b', 'После haben в Perfekt нужна форма Partizip II — gemacht.'),

  // ---- 5 sc HARD (idx 5..9) ----
  sc(5, 'HARD', 'Welches Partizip II ist richtig: „arbeiten“?', op4('gearbeitet', 'gearbeitt', 'gearbeit', 'arbeitet'), 'a', 'Основа на -t требует вставного -e-: ge- + arbeit + -e- + -t = gearbeitet.'),
  sc(6, 'HARD', 'Wir ___ den ganzen Tag gearbeitet.', op4('habt', 'hast', 'haben', 'hat'), 'c', 'С местоимением wir вспомогательный глагол haben имеет форму haben.'),
  sc(7, 'HARD', 'An welcher Stelle steht das Partizip II im Hauptsatz?', op4('am Satzende', 'auf Position 2', 'am Satzanfang', 'vor dem Subjekt'), 'a', 'В рамочной конструкции Partizip II стоит в самом конце предложения.'),
  sc(8, 'HARD', 'Sie (она) ___ ein neues Auto gekauft.', op4('habe', 'hast', 'hat', 'haben'), 'c', 'С местоимением sie (она) форма haben — hat.'),
  sc(9, 'HARD', 'Welches Partizip II ist korrekt: „warten“?', op4('gewartet', 'gewartt', 'gewart', 'gewartete'), 'a', 'Основа на -t даёт вставное -e-: ge- + wart + -e- + -t = gewartet.'),

  // ---- 5 mc EASY (idx 10..14) ----
  mc(10, 'EASY', 'Welche Formen von „haben“ sind richtig?', op5('ich habe', 'du hast', 'du habe', 'er hast', 'ich hast'), ['a', 'b'], 'Правильные формы — ich habe и du hast; остальные перепутаны по лицам.'),
  mc(11, 'EASY', 'Welche Wörter sind ein Partizip II?', op5('gemacht', 'gelernt', 'machen', 'lernen', 'spielt'), ['a', 'b'], 'Partizip II здесь — gemacht и gelernt (ge-…-t); остальное инфинитивы или другие формы.'),
  mc(12, 'EASY', 'In welchen Sätzen steht das Perfekt?', op5('Ich habe gespielt.', 'Du hast gelernt.', 'Ich spiele.', 'Du lernst.', 'Wir machen.'), ['a', 'b'], 'Perfekt — это haben + Partizip II: habe gespielt и hast gelernt.'),
  mc(13, 'EASY', 'Welche Partizipien gehören zu „spielen“ und „kaufen“?', op5('gespielt', 'gekauft', 'spielen', 'kaufen', 'gespielen'), ['a', 'b'], 'Корректные Partizip II — gespielt и gekauft.'),
  mc(14, 'EASY', 'Welche Sätze sind richtig gebildet?', op5('Ich habe gewohnt.', 'Du hast gemacht.', 'Ich hast gemacht.', 'Du habe gewohnt.', 'Ich habe machen.'), ['a', 'b'], 'Верно согласованы habe (ich) и hast (du) с Partizip II.'),

  // ---- 5 mc HARD (idx 15..19) ----
  mc(15, 'HARD', 'Welche Partizipien brauchen ein eingeschobenes -e-?', op6('gearbeitet', 'gewartet', 'gemacht', 'gelernt', 'gespielt', 'gekauft'), ['a', 'b'], 'Вставное -e- нужно у основ на -t/-d: gearbeitet и gewartet.'),
  mc(16, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Wir haben gearbeitet.', 'Sie hat gekauft.', 'Wir habt gearbeitet.', 'Sie haben gekauft hat.', 'Wir hat gearbeitet.'), ['a', 'b'], 'Корректны wir haben gearbeitet и sie hat gekauft.'),
  mc(17, 'HARD', 'Welche Aussagen über das Perfekt stimmen?', op5('Es braucht ein Hilfsverb.', 'Das Partizip II steht am Ende.', 'Das Partizip II steht auf Position 2.', 'Man braucht kein Hilfsverb.', 'Das Hilfsverb wird nicht konjugiert.'), ['a', 'b'], 'Perfekt требует спрягаемого haben и Partizip II в конце.'),
  mc(18, 'HARD', 'Welche Partizip-II-Formen sind korrekt gebildet?', op6('gewohnt', 'gekauft', 'gewohn', 'gekaufen', 'gemacht', 'gemachen'), ['a', 'b', 'e'], 'Правильно: gewohnt, gekauft и gemacht; формы на -en/-n тут ошибочны.'),
  mc(19, 'HARD', 'Welche Sätze sind im Perfekt korrekt?', op5('Ihr habt gelernt.', 'Sie haben gewartet.', 'Ihr habe gelernt.', 'Sie hat gewartet hat.', 'Ihr hast gelernt.'), ['a', 'b'], 'Верны ihr habt gelernt и sie haben gewartet.'),

  // ---- 5 ord EASY (idx 20..24) ----
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я учил немецкий».', items4('Ich', 'habe', 'Deutsch', 'gelernt'), 'Спрягаемое habe на 2-м месте, Partizip II gelernt — в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ты играл в футбол».', items4('Du', 'hast', 'Fußball', 'gespielt'), 'Hast на 2-м месте, Partizip II gespielt — в конце предложения.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Она купила машину».', items4('Sie', 'hat', 'ein Auto', 'gekauft'), 'Hat на 2-м месте, Partizip II gekauft — в конце.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мы много работали».', items4('Wir', 'haben', 'viel', 'gearbeitet'), 'Haben на 2-м месте, обстоятельство viel, Partizip II gearbeitet — в конце.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Что ты делал?».', items4('Was', 'hast', 'du', 'gemacht'), 'В вопросе hast на 2-м месте, Partizip II gemacht — в конце.'),

  // ---- 5 ord HARD (idx 25..29) ----
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я учил немецкий вчера».', items5('Ich', 'habe', 'gestern', 'Deutsch', 'gelernt'), 'Habe на 2-м месте, обстоятельства в середине, Partizip II gelernt — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Дети играли в парке».', items5('Die', 'Kinder', 'haben', 'im Park', 'gespielt'), 'Подлежащее die Kinder, haben на 2-м месте, gespielt — в конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Мы работали целый день».', items5('Wir', 'haben', 'den', 'ganzen Tag', 'gearbeitet'), 'Haben на 2-м месте, обстоятельство времени, gearbeitet — в конце.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Она купила новую машину».', items5('Sie', 'hat', 'ein', 'neues Auto', 'gekauft'), 'Hat на 2-м месте, Akkusativ ein neues Auto, gekauft — в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Что ты вчера делал?».', items5('Was', 'hast', 'du', 'gestern', 'gemacht'), 'В вопросе hast на 2-м месте, обстоятельство gestern, gemacht — в конце.'),

  // ---- 5 fb EASY (idx 30..34) ----
  fb(30, 'EASY', 'Ich ___ Deutsch gelernt.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С местоимением ich вспомогательный глагол haben имеет форму habe.'),
  fb(31, 'EASY', 'Du ___ Fußball gespielt.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С местоимением du форма haben — hast.'),
  fb(32, 'EASY', 'Meine Schwester ___ ein Auto gekauft.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'Подлежащее в 3-м лице ед. числа (meine Schwester) — форма haben это hat.'),
  fb(33, 'EASY', 'Wir ___ gearbeitet.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С местоимением wir форма haben — haben.'),
  fb(34, 'EASY', 'Ihr ___ gemacht.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('habe', 'hast', 'hat', 'haben', 'habt'), 'С местоимением ihr форма haben — habt.'),

  // ---- 5 fb HARD (idx 35..39) ----
  fb(35, 'HARD', 'Ich habe gestern ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('gearbeitet', 'arbeiten', 'gearbeit', 'gearbeitt', 'arbeitete'), 'Partizip II от arbeiten с вставным -e- — gearbeitet.'),
  fb(36, 'HARD', 'Wir haben den ganzen Tag ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('gewartet', 'warten', 'gewartt', 'gewart', 'wartete'), 'Грамматически верен только Partizip II с вставным -e- — gewartet.'),
  fb(37, 'HARD', 'Die Kinder haben im Park ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('gespielt', 'spielen', 'gespielen', 'spielte', 'gespiel'), 'После haben нужен Partizip II — gespielt (ge- + spiel + -t).'),
  fb(38, 'HARD', 'Sie hat ein neues Auto ___.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('gekauft', 'kaufen', 'gekaufen', 'gekauf', 'kaufte'), 'После haben нужен Partizip II — gekauft (ge- + kauf + -t).'),
  fb(39, 'HARD', 'Was ___ du gestern gemacht?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('habe', 'haben', 'hat', 'habt', 'hast'), 'В вопросе с du вспомогательный глагол — hast.'),
];
