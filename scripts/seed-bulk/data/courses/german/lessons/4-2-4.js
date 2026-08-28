'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich ___ jeden Morgen um acht Uhr an. (anfangen)', op4('fange', 'fangen', 'anfange', 'fängst'), 'a', 'У отделяемых глаголов приставка an- уходит в конец, а спрягается основа: ich fange.'),
  sc(1, 'EASY', 'Wann beginnt die Arbeit? — ___ neun Uhr.', op4('Um', 'Am', 'Von', 'In'), 'a', 'С точным часом по-немецки используется предлог um: um neun Uhr.'),
  sc(2, 'EASY', 'Ich rufe ___ Kunden an.', op4('den', 'dem', 'der', 'des'), 'a', 'anrufen требует Akkusativ, поэтому der Kunde превращается в den Kunden.'),
  sc(3, 'EASY', 'Ich helfe meinem ___ bei der Aufgabe.', op4('Kollegen', 'Kollege', 'Kollegin', 'Kollegens'), 'a', 'helfen требует Dativ, и слабое существительное der Kollege в дательном даёт Kollegen.'),
  sc(4, 'EASY', 'Wir arbeiten ___ acht bis siebzehn Uhr.', op4('von', 'um', 'am', 'ab'), 'a', 'Интервал времени выражается конструкцией von … bis: von acht bis siebzehn Uhr.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Am Montag ___ ich an der Besprechung teil. (teilnehmen)', op4('nehme', 'teilnehme', 'nehmen', 'nimmst'), 'a', 'teilnehmen отделяет teil-, а основа nehmen спрягается: ich nehme … teil.'),
  sc(6, 'HARD', 'Nach Feierabend schicke ich ___ die E-Mail.', op4('dem Chef', 'den Chef', 'der Chef', 'des Chefs'), 'a', 'При двух существительных Dativ стоит перед Akkusativ, поэтому получателю — Dativ: dem Chef.'),
  sc(7, 'HARD', '___ Montag arbeite ich im Homeoffice.', op4('Ab', 'Um', 'Von', 'In'), 'a', 'ab + Dativ означает «начиная с»: ab Montag — начиная с понедельника.'),
  sc(8, 'HARD', 'Ich danke ___ für seine Hilfe.', op4('dem Kollegen', 'den Kollege', 'der Kollege', 'des Kollegen'), 'a', 'danken требует Dativ, поэтому der Kollege становится dem Kollegen.'),
  sc(9, 'HARD', 'Können Sie ___ bei dieser Aufgabe helfen?', op4('mir', 'mich', 'ich', 'mein'), 'a', 'helfen управляет дательным падежом, поэтому правильное местоимение — mir.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие слова обозначают рабочее СОБРАНИЕ или встречу?', op5('die Besprechung', 'die Sitzung', 'die Pause', 'der Feierabend', 'der Termin'), ['a', 'b', 'e'], 'Besprechung, Sitzung и Termin относятся к встречам, а Pause и Feierabend — к отдыху и концу дня.'),
  mc(11, 'EASY', 'Какие предлоги уместны при указании ВРЕМЕНИ графика?', op5('um', 'von', 'ab', 'unter', 'neben'), ['a', 'b', 'c'], 'um, von … bis и ab задают время, а unter и neben — пространственные предлоги.'),
  mc(12, 'EASY', 'Какие глаголы имеют ОТДЕЛЯЕМУЮ приставку?', op5('anfangen', 'anrufen', 'teilnehmen', 'erledigen', 'helfen'), ['a', 'b', 'c'], 'anfangen, anrufen и teilnehmen отделяют приставку, а erledigen и helfen — нет.'),
  mc(13, 'EASY', 'Какие слова связаны с РАБОТОЙ в офисе?', op5('der Kollege', 'die Aufgabe', 'der Kunde', 'der Apfel', 'der Garten'), ['a', 'b', 'c'], 'Kollege, Aufgabe и Kunde — офисная лексика, а Apfel и Garten к работе не относятся.'),
  mc(14, 'EASY', 'В каких предложениях приставка стоит ПРАВИЛЬНО в конце?', op5('Ich fange um acht an.', 'Ich rufe den Kunden an.', 'Ich anfange um acht.', 'Ich anrufe den Kunden.', 'Ich teilnehme heute.'), ['a', 'b'], 'Отделяемая приставка идёт в конец, поэтому корректны только первые два предложения.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие глаголы требуют дополнения в ДАТЕЛЬНОМ падеже?', op5('helfen', 'antworten', 'danken', 'anrufen', 'erledigen'), ['a', 'b', 'c'], 'helfen, antworten и danken управляют Dativ, тогда как anrufen и erledigen — Akkusativ.'),
  mc(16, 'HARD', 'В каких предложениях падежи стоят ПРАВИЛЬНО?', op6('Ich helfe meinem Kollegen.', 'Ich schicke dem Chef die E-Mail.', 'Ich danke der Kundin.', 'Ich helfe meinen Kollege.', 'Ich danke den Chef.', 'Ich antworte der Kunde auf Englisch.'), ['a', 'b', 'c'], 'helfen, danken и antworten берут Dativ; meinen Kollege, den Chef и der Kunde — ошибочные падежи.'),
  mc(17, 'HARD', 'Какие фразы корректно описывают РАБОЧИЙ ГРАФИК?', op5('Ich arbeite von 8 bis 17 Uhr.', 'Ich fange um 9 Uhr an.', 'Ab Montag mache ich Überstunden.', 'Ich arbeite am 8 bis 17.', 'Ich fange an 9 Uhr an.'), ['a', 'b', 'c'], 'von … bis, um и ab употреблены верно, а «am 8 bis» и «an 9 Uhr» грамматически ошибочны.'),
  mc(18, 'HARD', 'Какие предложения с отделяемыми глаголами построены ВЕРНО?', op6('Ich nehme an der Sitzung teil.', 'Wir fangen um acht an.', 'Sie ruft den Kunden an.', 'Ich teilnehme an der Sitzung.', 'Wir anfangen um acht.', 'Sie anruft den Kunden.'), ['a', 'b', 'c'], 'Спрягается основа, а приставка teil/an идёт в конец — поэтому верны только первые три.'),
  mc(19, 'HARD', 'В каких случаях порядок «Dativ перед Akkusativ» соблюдён?', op5('Ich schicke dem Chef die E-Mail.', 'Ich gebe dem Kollegen die Aufgabe.', 'Ich zeige der Kundin den Termin.', 'Ich schicke die E-Mail dem Chef gestern.', 'Ich gebe die Aufgabe dem Kollegen falsch.'), ['a', 'b', 'c'], 'При двух существительных Dativ-получатель стоит перед Akkusativ-предметом, как в первых трёх вариантах.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я начинаю в восемь часов».', items4('Ich', 'fange', 'um acht Uhr', 'an'), 'V2-порядок: спрягаемый глагол на втором месте, отделяемая приставка an — в конце.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Я звоню клиенту».', items4('Ich', 'rufe', 'den Kunden', 'an'), 'anrufen отделяет приставку an, которая встаёт в самый конец предложения.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я помогаю коллеге».', items4('Ich', 'helfe', 'meinem', 'Kollegen'), 'helfen требует Dativ, поэтому объект — meinem Kollegen.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Мы работаем до семнадцати часов».', items4('Wir', 'arbeiten', 'bis', 'siebzehn Uhr'), 'Глагол на втором месте, обстоятельство времени bis siebzehn Uhr — в конце.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я делаю перерыв».', items4('Ich', 'mache', 'eine', 'Pause'), 'eine Pause machen — устойчивое выражение «делать перерыв» с глаголом на втором месте.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «В понедельник я участвую в совещании».', items5('Am Montag', 'nehme', 'ich', 'an der Besprechung', 'teil'), 'После обстоятельства глагол стоит на 2-м месте (инверсия), приставка teil — в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «После конца дня я отправляю шефу письмо».', items5('Nach Feierabend', 'schicke', 'ich', 'dem Chef', 'die E-Mail'), 'Инверсия после обстоятельства, затем Dativ dem Chef перед Akkusativ die E-Mail.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «С понедельника я часто делаю сверхурочные».', items5('Ab Montag', 'mache', 'ich', 'oft', 'Überstunden'), 'ab Montag в начале даёт инверсию, глагол mache — на втором месте.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Я благодарю коллегу за помощь».', items5('Ich', 'danke', 'meinem', 'Kollegen', 'für die Hilfe'), 'danken управляет Dativ, поэтому объект — meinem Kollegen, благодарность вводится через für die Hilfe.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Можете ли вы мне, пожалуйста, помочь?».', items5('Können', 'Sie', 'mir', 'bitte', 'helfen'), 'В вопросе модальный глагол на 1-м месте, инфинитив helfen — в конце, объект mir в Dativ, bitte — «пожалуйста».'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ um acht Uhr an.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('fange', 'fängst', 'fangen', 'fängt', 'fingen'), 'Для ich у anfangen спрягается основа: ich fange … an.'),
  fb(31, 'EASY', 'Wir arbeiten ___ acht bis siebzehn Uhr.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('um', 'von', 'ab', 'am', 'in'), 'Интервал начинается с предлога von: von acht bis siebzehn Uhr.'),
  fb(32, 'EASY', 'Ich rufe den ___ an.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Kunde', 'Kundin', 'Kunden', 'Kundes', 'Kund'), 'anrufen требует Akkusativ, и слабое слово der Kunde даёт den Kunden.'),
  fb(33, 'EASY', 'Ich helfe ___ Kollegen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('mein', 'meine', 'meiner', 'meinem', 'meines'), 'helfen требует Dativ единственного числа, поэтому притяжательное местоимение — meinem.'),
  fb(34, 'EASY', 'Die Besprechung beginnt ___ neun Uhr.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('von', 'bei', 'am', 'in', 'um'), 'С точным временем используется предлог um: um neun Uhr.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Am Montag ___ ich an der Sitzung teil.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('nehme', 'nehmen', 'nimmst', 'teilnehme', 'genommen'), 'teilnehmen отделяет teil, основа спрягается для ich как nehme.'),
  fb(36, 'HARD', 'Ich schicke ___ Chef die E-Mail.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('den', 'dem', 'der', 'des', 'das'), 'Получатель стоит в Dativ перед Akkusativ, поэтому der Chef становится dem Chef.'),
  fb(37, 'HARD', '___ Montag arbeite ich im Homeoffice.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Um', 'Für', 'Ab', 'In', 'Bei'), 'ab + Dativ означает «начиная с»: ab Montag.'),
  fb(38, 'HARD', 'Ich danke ___ Kollegen für die Hilfe.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('das', 'der', 'des', 'dem', 'die'), 'danken требует Dativ единственного числа, поэтому артикль — dem Kollegen.'),
  fb(39, 'HARD', 'Können Sie ___ bei der Aufgabe helfen?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('mich', 'ich', 'mein', 'meiner', 'mir'), 'helfen управляет дательным падежом, поэтому правильное местоимение — mir.'),
];
