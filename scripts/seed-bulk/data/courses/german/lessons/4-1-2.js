'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (0..4) =====
  sc(0, 'EASY', 'Wie lautet das Präteritum von "machen" (ich)?', op4('machte', 'machst', 'gemacht', 'mache'), 'a', 'Слабые глаголы образуют Präteritum по схеме основа + -te: machen → ich machte.'),
  sc(1, 'EASY', 'Setze ein: Früher ___ meine Familie in Berlin.', op4('wohnt', 'wohnte', 'gewohnt', 'wohnen'), 'b', 'Präteritum от wohnen в 3-м лице ед. ч. — wohnte (основа wohn- + -te).'),
  sc(2, 'EASY', 'Wie heißt das Präteritum von "spielen" (wir)?', op4('spielst', 'spielen', 'spielten', 'spielte'), 'c', 'Во множественном числе wir добавляется окончание -en: wir spielten.'),
  sc(3, 'EASY', 'Welche Form ist das Präteritum von "lernen" (sie, Plural)?', op4('lernten', 'lernte', 'lernst', 'gelernt'), 'a', 'Для sie (они) к -te добавляется -n: sie lernten.'),
  sc(4, 'EASY', 'Setze das Präteritum ein: Am Wochenende ___ die Kinder im Park (spielen).', op4('spielt', 'spielte', 'gespielt', 'spielten'), 'd', 'Подлежащее die Kinder (множ.), поэтому в Präteritum форма spielten с окончанием -ten.'),

  // ===== 5 sc HARD (5..9) =====
  sc(5, 'HARD', 'Wie lautet das Präteritum von "arbeiten" (er)?', op4('arbeitte', 'arbeitete', 'arbeitet', 'arbeite'), 'b', 'После основы на -t вставляется -e-: arbeiten → er arbeitete.'),
  sc(6, 'HARD', 'Welche Form ist korrekt: Du ___ gestern fleißig (lernen)?', op4('lerntest', 'lerntst', 'lerntet', 'lernte'), 'a', 'Во 2-м лице ед. ч. (du) окончание -test: du lerntest.'),
  sc(7, 'HARD', 'Setze ein: Ich ___ gestern Brot ein (einkaufen).', op4('einkaufte', 'kaufe ein', 'kaufte ein', 'kaufte'), 'd', 'Приставка ein уже стоит в конце рамки, поэтому в пропуск идёт только спрягаемая основа kaufte (иначе двойная приставка): ich kaufte ... ein.'),
  sc(8, 'HARD', 'Wie lautet das Präteritum von "öffnen" (sie, Singular)?', op4('öffnte', 'öffente', 'öffnete', 'offnete'), 'c', 'После сочетания -fn- вставляется -e-: öffnen → sie öffnete.'),
  sc(9, 'HARD', 'Welche Präteritum-Form passt: Ihr ___ am Wochenende viel (arbeiten)?', op4('arbeittet', 'arbeitetet', 'arbeitete', 'arbeiteten'), 'b', 'Для ihr в Präteritum после вставного -e- окончание -tet: ihr arbeitetet.'),

  // ===== 5 mc EASY (10..14) =====
  mc(10, 'EASY', 'Welche Formen sind Präteritum von "machen"? (Wähle alle richtigen.)', op5('ich machte', 'er machte', 'ich mache', 'du machst', 'wir machen'), ['a', 'b'], 'Präteritum machen: ich machte и er machte (1-е и 3-е лицо совпадают).'),
  mc(11, 'EASY', 'Welche Sätze stehen im Präteritum? (Wähle alle richtigen.)', op5('Sie lernte Deutsch.', 'Er spielte Fußball.', 'Wir wohnen hier.', 'Ich kaufe Brot.', 'Du spielst gern.'), ['a', 'b'], 'В Präteritum стоят lernte и spielte; остальные — в Präsens.'),
  mc(12, 'EASY', 'Welche Verben sind schwach (regelmäßig)? (Wähle alle richtigen.)', op6('machen', 'spielen', 'gehen', 'wohnen', 'sein', 'fahren'), ['a', 'b', 'd'], 'Слабые глаголы machen, spielen, wohnen образуют Präteritum с -te.'),
  mc(13, 'EASY', 'Welche Formen passen zu "wir" im Präteritum? (Wähle alle richtigen.)', op5('wir wohnten', 'wir spielten', 'wir wohnte', 'wir spielte', 'wir machte'), ['a', 'b'], 'Для wir окончание -ten: wohnten и spielten.'),
  mc(14, 'EASY', 'Welche Wörter sind Substantive (groß geschrieben)? (Wähle alle richtigen.)', op5('die Geschichte', 'das Wochenende', 'spielen', 'damals', 'machen'), ['a', 'b'], 'Существительные пишутся с большой буквы: Geschichte и Wochenende.'),

  // ===== 5 mc HARD (15..19) =====
  mc(15, 'HARD', 'In welchen Sätzen ist das Präteritum korrekt gebildet? (Wähle alle richtigen.)', op5('Er arbeitete drei Jahre.', 'Sie öffnete die Tür.', 'Ich arbeitte lange.', 'Du machtst das.', 'Wir öffnten das Fenster.'), ['a', 'b'], 'Корректны arbeitete и öffnete (с вставным -e-); остальные образованы неверно.'),
  mc(16, 'HARD', 'Welche Formen verlangen ein eingeschobenes -e-? (Wähle alle richtigen.)', op5('arbeiten → arbeitete', 'öffnen → öffnete', 'spielen → spielte', 'machen → machte', 'wohnen → wohnte'), ['a', 'b'], 'Вставное -e- нужно после основы на -t (arbeiten) и -fn (öffnen).'),
  mc(17, 'HARD', 'Welche Sätze mit trennbarem Verb sind richtig? (Wähle alle richtigen.)', op5('Ich kaufte Brot ein.', 'Er räumte das Zimmer auf.', 'Ich einkaufte Brot.', 'Er aufräumte das Zimmer.', 'Sie kaufte ein Milch.'), ['a', 'b'], 'Отделяемая приставка стоит в конце: kaufte ... ein, räumte ... auf.'),
  mc(18, 'HARD', 'Welche Aussagen über das Präteritum sind richtig? (Wähle alle richtigen.)', op5('1. und 3. Person Singular sind gleich.', 'Es ist vor allem schriftlich.', 'Schwache Verben ändern den Stammvokal.', 'Man bildet es mit "haben" + Partizip.', 'Die Endung ist immer -en.'), ['a', 'b'], 'У Präteritum 1-е и 3-е лицо ед. ч. совпадают, и оно используется прежде всего письменно.'),
  mc(19, 'HARD', 'Welche Verbformen passen zu "du" im Präteritum? (Wähle alle richtigen.)', op5('du machtest', 'du arbeitetest', 'du machtst', 'du arbeittest', 'du machtet'), ['a', 'b'], 'Для du окончание -test: machtest и (с вставным -e-) arbeitetest.'),

  // ===== 5 ord EASY (20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Раньше моя семья жила в Берлине.»', items5('Früher', 'wohnte', 'meine Familie', 'in', 'Berlin'), 'При начале с обстоятельства глагол стоит на 2-м месте (V2): Früher wohnte meine Familie in Berlin.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Дети играли в парке.»', items4('Die Kinder', 'spielten', 'im', 'Park'), 'Подлежащее на 1-м месте, спрягаемый глагол на 2-м: Die Kinder spielten im Park.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Он работал в фирме.»', items4('Er', 'arbeitete', 'in der', 'Firma'), 'Глагол arbeitete на 2-м месте: Er arbeitete in der Firma.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Она усердно учила немецкий.»', items4('Sie', 'lernte', 'fleißig', 'Deutsch'), 'Глагол на 2-м месте, затем обстоятельство и дополнение: Sie lernte fleißig Deutsch.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Мы играли на выходных.»', items4('Wir', 'spielten', 'am', 'Wochenende'), 'Глагол spielten на 2-м месте: Wir spielten am Wochenende.'),

  // ===== 5 ord HARD (25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я вчера купил хлеб.»', items5('Ich', 'kaufte', 'gestern', 'Brot', 'ein'), 'Отделяемая приставка ein уходит в конец: Ich kaufte gestern Brot ein.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «На выходных дети играли в парке.»', items5('Am Wochenende', 'spielten', 'die Kinder', 'im', 'Park'), 'После обстоятельства глагол на 2-м месте, подлежащее после него: Am Wochenende spielten die Kinder im Park.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Он три года работал в этой фирме.»', items5('Er', 'arbeitete', 'drei Jahre lang', 'in dieser', 'Firma'), 'Глагол arbeitete на 2-м месте, далее время и место: Er arbeitete drei Jahre lang in dieser Firma.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Тогда она жила в маленьком городе.»', items5('Damals', 'wohnte', 'sie', 'in einer kleinen', 'Stadt'), 'После damals глагол на 2-м месте: Damals wohnte sie in einer kleinen Stadt.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Учитель спрашивал, а ученики отвечали.»', items5('Der Lehrer', 'fragte', 'und die', 'Schüler', 'antworteten'), 'Два главных предложения, в каждом глагол на 2-м месте: Der Lehrer fragte, und die Schüler antworteten.'),

  // ===== 5 fb EASY (30..34) =====
  fb(30, 'EASY', 'Früher ___ ich in München. (wohnen)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('wohnte', 'wohnt', 'wohnen', 'gewohnt', 'wohne'), 'Präteritum от wohnen в 1-м лице ед. ч. — wohnte.'),
  fb(31, 'EASY', 'Er ___ gestern Fußball. (spielen)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('spielt', 'spielte', 'spielen', 'spielst', 'gespielt'), 'Präteritum от spielen в 3-м лице ед. ч. — spielte.'),
  fb(32, 'EASY', 'Wir ___ zu Hause. (lernen)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('lernte', 'lernt', 'lernten', 'lernst', 'gelernt'), 'Для wir в Präteritum окончание -ten: lernten.'),
  fb(33, 'EASY', 'Die Frau ___ Brot und Milch. (kaufen)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('kauft', 'kaufen', 'kaufst', 'kaufte', 'gekauft'), 'Präteritum от kaufen в 3-м лице ед. ч. — kaufte.'),
  fb(34, 'EASY', 'Sie ___ eine Geschichte. (machen)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('macht', 'machen', 'machst', 'gemacht', 'machte'), 'Präteritum от machen в 3-м лице ед. ч. — machte.'),

  // ===== 5 fb HARD (35..39) =====
  fb(35, 'HARD', 'Er ___ drei Jahre in der Firma. (arbeiten)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('arbeitete', 'arbeitet', 'arbeitte', 'gearbeitet', 'arbeiten'), 'После основы на -t вставляется -e-: er arbeitete.'),
  fb(36, 'HARD', 'Du ___ gestern fleißig. (lernen)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('lerntst', 'lerntest', 'lernte', 'lerntet', 'gelernt'), 'Для du в Präteritum окончание -test: lerntest.'),
  fb(37, 'HARD', 'Ich ___ gestern Brot ein. (einkaufen)', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('einkaufte', 'kaufte', 'kaufe', 'gekauft', 'kauften'), 'У отделяемого глагола спрягается основа kaufte, а приставка ein остаётся в конце.'),
  fb(38, 'HARD', 'Ihr ___ am Morgen. (arbeiten)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('arbeitet', 'arbeitete', 'arbeitetet', 'arbeiteten', 'gearbeitet'), 'Для ihr после вставного -e- окончание -tet: arbeitetet.'),
  fb(39, 'HARD', 'Sie ___ das Fenster. (öffnen)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('öffnt', 'offnete', 'öffnen', 'geöffnet', 'öffnete'), 'После сочетания -fn вставляется -e-: sie öffnete.'),
];
