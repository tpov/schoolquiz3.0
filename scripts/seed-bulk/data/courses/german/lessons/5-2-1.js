'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Der ___ Kollege spricht sehr gut Deutsch.', op4('neue', 'neuen', 'neuer', 'neues'), 'a',
    'После определённого артикля в Nominativ ед. ч. прилагательное получает окончание -e: "der neue Kollege".'),
  sc(1, 'EASY', 'Ich kenne den ___ Kollegen noch nicht.', op4('neue', 'neuen', 'neuer', 'neues'), 'b',
    'В Akkusativ мужского рода после "den" окончание прилагательного — -en: "den neuen Kollegen".'),
  sc(2, 'EASY', 'Wir helfen der ___ Nachbarin.', op4('freundliche', 'freundlicher', 'freundlichen', 'freundliches'), 'c',
    'Глагол helfen требует Dativ, а весь Dativ при слабом склонении даёт окончание -en: "der freundlichen Nachbarin".'),
  sc(3, 'EASY', 'Diese ___ Kinder stellen viele Fragen.', op4('neugierige', 'neugieriger', 'neugieriges', 'neugierigen'), 'd',
    'Во множественном числе при слабом склонении прилагательное всегда получает окончание -en: "diese neugierigen Kinder".'),
  sc(4, 'EASY', 'Das ___ Hotel der Stadt ist sehr bekannt.', op4('teuren', 'teure', 'teurer', 'teures'), 'b',
    'В Nominativ среднего рода после "das" прилагательное получает окончание -e: "das teure Hotel".'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Trotz des ___ Wetters gingen wir spazieren.', op4('schlechte', 'schlechter', 'schlechten', 'schlechtes'), 'c',
    'Предлог trotz требует Genitiv, а весь Genitiv при слабом склонении даёт окончание -en: "des schlechten Wetters".'),
  sc(6, 'HARD', 'Welcher ___ Mann hat angerufen?', op4('neugierige', 'neugieriger', 'neugieriges', 'neugierigen'), 'a',
    'После welcher (как определённый артикль) в Nominativ мужского рода окончание прилагательного — -e: "welcher neugierige Mann".'),
  sc(7, 'HARD', 'Wir sprechen mit dem ___ Nachbarn über das Wetter.', op4('freundliche', 'freundlichen', 'freundlicher', 'freundliches'), 'b',
    'Предлог mit требует Dativ, поэтому после "dem" прилагательное получает -en: "dem freundlichen Nachbarn".'),
  sc(8, 'HARD', 'Jeder ___ Schüler bekommt eine Aufgabe.', op4('fleißigen', 'fleißiges', 'fleißige', 'fleißiger'), 'c',
    'После jeder в Nominativ мужского рода прилагательное получает окончание -e: "jeder fleißige Schüler".'),
  sc(9, 'HARD', 'Die Farbe der ___ Tür gefällt mir.', op4('alte', 'alter', 'altes', 'alten'), 'd',
    'Genitiv женского рода даёт окончание -en при слабом склонении: "der alten Tür".'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'In welchen Sätzen ist die Adjektivendung -e richtig?', op5('Der gute Freund kommt.', 'Die nette Frau lacht.', 'Den guten Freund sehe ich.', 'Mit dem guten Freund rede ich.', 'Wegen des guten Freundes warte ich.'), ['a', 'b'],
    'Окончание -e стоит в Nominativ ед. ч.: "der gute Freund", "die nette Frau"; остальные позиции дают -en.'),
  mc(11, 'EASY', 'In welchen Phrasen steht korrekt die Endung -en?', op5('den guten Mann', 'der freundlichen Frau', 'das neue Auto', 'der gute Mann', 'die nette Frau'), ['a', 'b'],
    'Akkusativ мужского рода ("den guten Mann") и Dativ женского рода ("der freundlichen Frau") требуют окончания -en.'),
  mc(12, 'EASY', 'Welche Nominativ-Singular-Phrasen sind richtig?', op5('das kleine Kind', 'die junge Lehrerin', 'der kleinen Hund', 'das großen Haus', 'die alten Frau'), ['a', 'b'],
    'В Nominativ ед. ч. прилагательное получает -e: "das kleine Kind", "die junge Lehrerin".'),
  mc(13, 'EASY', 'In welchen Sätzen ist die Pluralendung richtig?', op5('Die neuen Bücher sind teuer.', 'Die kleinen Kinder spielen.', 'Die neue Bücher sind teuer.', 'Die klein Kinder spielen.', 'Die neuer Bücher sind teuer.'), ['a', 'b'],
    'Во множественном числе при слабом склонении прилагательное всегда оканчивается на -en: "die neuen Bücher", "die kleinen Kinder".'),
  mc(14, 'EASY', 'Welche Phrasen sind grammatisch korrekt?', op5('dieser nette Mann', 'jenes alte Haus', 'dieser netten Mann', 'jenes alten Haus', 'jeder kleinen Hund'), ['a', 'b'],
    'После dieser/jenes в Nominativ ед. ч. окончание -e: "dieser nette Mann", "jenes alte Haus".'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'In welchen Sätzen ist die Adjektivendung im Kasus korrekt?', op6('Ich helfe dem alten Mann.', 'Trotz des starken Regens kommt er.', 'Ich helfe dem alte Mann.', 'Trotz des starke Regens kommt er.', 'Ich sehe der alten Mann.', 'Wegen des starker Regens bleibt er.'), ['a', 'b'],
    'Dativ ("dem alten Mann") и Genitiv ("des starken Regens") при слабом склонении дают окончание -en.'),
  mc(16, 'HARD', 'Welche Phrasen mit dieser/jeder/welcher sind richtig dekliniert?', op6('jeder neue Student', 'welche kluge Frau', 'jeder neuen Student', 'welche klugen Frau', 'jenem neue Lehrer', 'dieser neuen Lehrer'), ['a', 'b'],
    'После jeder/welche в Nominativ ед. ч. прилагательное получает -e: "jeder neue Student", "welche kluge Frau".'),
  mc(17, 'HARD', 'In welchen Sätzen steht die Endung -en zu Recht?', op6('Ich gebe dem freundlichen Kind ein Buch.', 'Das ist das Haus des reichen Mannes.', 'Ich gebe dem freundliche Kind ein Buch.', 'Das ist das Haus des reiche Mannes.', 'Der nette Mann liegt im Dativ.', 'Die kluge Frau steht im Genitiv.'), ['a', 'b'],
    'Dativ ("dem freundlichen Kind") и Genitiv ("des reichen Mannes") требуют окончания -en во всех родах.'),
  mc(18, 'HARD', 'Welche Akkusativ-Phrasen sind korrekt?', op6('Ich sehe den neuen Lehrer.', 'Ich kaufe die teure Uhr.', 'Ich sehe den neue Lehrer.', 'Ich kaufe die teuren Uhr.', 'Ich sehe das großen Haus.', 'Ich kaufe der teure Uhr.'), ['a', 'b'],
    'В Akkusativ мужского рода окончание -en ("den neuen Lehrer"), а в женском роде -e ("die teure Uhr").'),
  mc(19, 'HARD', 'Welche Aussagen über die schwache Deklination sind richtig?', op5('Nach dem bestimmten Artikel gibt es nur -e oder -en.', 'Der ganze Plural bekommt die Endung -en.', 'Im Genitiv steht immer die Endung -e.', 'Im Akkusativ Maskulinum steht die Endung -e.', 'dieser und jeder verlangen die starke Deklination.'), ['a', 'b'],
    'При слабом склонении возможны только -e или -en, а весь Plural и весь Genitiv/Dativ дают -en.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Новый коллега говорит».', items4('Der', 'neue', 'Kollege', 'spricht'),
    'В Nominativ мужского рода прилагательное получает -e: "der neue Kollege", глагол на втором месте (V2).'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Любопытные дети задают вопросы».', items5('Die', 'neugierigen', 'Kinder', 'stellen', 'Fragen'),
    'Множественное число даёт окончание -en: "die neugierigen Kinder", глагол "stellen" стоит на втором месте: "stellen Fragen" — задают вопросы.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Дорогая гостиница закрывается».', items4('Das', 'teure', 'Hotel', 'schließt'),
    'В Nominativ среднего рода прилагательное получает -e: "das teure Hotel", глагол на втором месте (V2).'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Приветливая соседка помогает».', items4('Die', 'freundliche', 'Nachbarin', 'hilft'),
    'В Nominativ женского рода окончание -e: "die freundliche Nachbarin".'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Этот любопытный мужчина спрашивает».', items4('Dieser', 'neugierige', 'Mann', 'fragt'),
    'После dieser в Nominativ мужского рода прилагательное оканчивается на -e: "dieser neugierige Mann".'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я знаю нового коллегу».', items5('Ich', 'kenne', 'den', 'neuen', 'Kollegen'),
    'Akkusativ мужского рода требует окончания -en: "den neuen Kollegen".'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Мы помогаем приветливой соседке».', items5('Wir', 'helfen', 'der', 'freundlichen', 'Nachbarin'),
    'Глагол helfen требует Dativ, поэтому женский род даёт окончание -en: "der freundlichen Nachbarin".'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Мы благодарим приветливого учителя».', items5('Wir', 'danken', 'dem', 'freundlichen', 'Lehrer'),
    'Глагол danken требует Dativ, поэтому мужской род даёт окончание -en: "dem freundlichen Lehrer".'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Я помогаю приветливому соседу».', items5('Ich', 'helfe', 'dem', 'freundlichen', 'Nachbarn'),
    'Глагол helfen требует Dativ, поэтому мужской род даёт окончание -en: "dem freundlichen Nachbarn".'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Каждый прилежный ученик получает задания».', items5('Jeder', 'fleißige', 'Schüler', 'bekommt', 'Aufgaben'),
    'После jeder в Nominativ мужского рода окончание -e: "jeder fleißige Schüler".'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Der ___ Kollege spricht gut Deutsch.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('neue', 'neuen', 'neuer', 'neues', 'neuem'),
    'В Nominativ мужского рода после "der" окончание прилагательного — -e: "der neue Kollege".'),
  fb(31, 'EASY', 'Ich kenne den ___ Kollegen.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('neue', 'neuen', 'neuer', 'neues', 'neuem'),
    'В Akkusativ мужского рода прилагательное получает окончание -en: "den neuen Kollegen".'),
  fb(32, 'EASY', 'Wir helfen der ___ Nachbarin.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('freundliche', 'freundlicher', 'freundlichen', 'freundliches', 'freundlichem'),
    'Dativ женского рода даёт окончание -en: "der freundlichen Nachbarin".'),
  fb(33, 'EASY', 'Die ___ Kinder stellen viele Fragen.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('neugierige', 'neugieriger', 'neugieriges', 'neugierigen', 'neugierigem'),
    'Во множественном числе прилагательное всегда оканчивается на -en: "die neugierigen Kinder".'),
  fb(34, 'EASY', 'Das ___ Hotel ist sehr bekannt.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('teuren', 'teurer', 'teures', 'teurem', 'teure'),
    'В Nominativ среднего рода прилагательное получает окончание -e: "das teure Hotel".'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Trotz des ___ Wetters gingen wir spazieren.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('schlechten', 'schlechte', 'schlechter', 'schlechtes', 'schlechtem'),
    'Genitiv среднего рода при слабом склонении даёт окончание -en: "des schlechten Wetters".'),
  fb(36, 'HARD', 'Ich spreche mit dem ___ Nachbarn.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('freundliche', 'freundlichen', 'freundlicher', 'freundliches', 'freundlichem'),
    'Dativ мужского рода после "dem" даёт окончание -en: "dem freundlichen Nachbarn".'),
  fb(37, 'HARD', 'Jeder ___ Schüler bekommt eine Aufgabe.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('fleißigen', 'fleißiger', 'fleißige', 'fleißiges', 'fleißigem'),
    'После jeder в Nominativ мужского рода прилагательное получает -e: "jeder fleißige Schüler".'),
  fb(38, 'HARD', 'Die Farbe der ___ Tür gefällt mir.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('alte', 'alter', 'altes', 'alten', 'altem'),
    'Genitiv женского рода даёт окончание -en: "der alten Tür".'),
  fb(39, 'HARD', 'Welcher ___ Mann hat angerufen?', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('neugierigen', 'neugieriger', 'neugieriges', 'neugierigem', 'neugierige'),
    'После welcher в Nominativ мужского рода прилагательное получает окончание -e: "welcher neugierige Mann".'),
];
