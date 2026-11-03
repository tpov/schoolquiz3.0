'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wir suchen einen erfahrenen, ___ Mitarbeiter.', op4('zuverlässigen', 'zuverlässige', 'zuverlässiger', 'zuverlässiges'), 'a',
    'Несколько прилагательных перед существительным склоняются параллельно: после "einen" в Akkusativ оба получают -en — "einen erfahrenen, zuverlässigen Mitarbeiter".'),
  sc(1, 'EASY', 'In dem großen, ___ Büro arbeiten viele Leute.', op4('hellem', 'hellen', 'helle', 'heller'), 'b',
    'В Dativ после "dem" оба прилагательных идут по слабому образцу на -en: "in dem großen, hellen Büro".'),
  sc(2, 'EASY', 'Das ist ein großer, ___ Raum.', op4('hellen', 'hellem', 'heller', 'helles'), 'c',
    'Без определителя рода (после "ein" в Nominativ муж. р.) оба прилагательных получают сильное окончание -er: "ein großer, heller Raum".'),
  sc(3, 'EASY', 'Wir haben ein neues, ___ Auto gekauft.', op4('schnellen', 'schnellem', 'schneller', 'schnelles'), 'd',
    'После "ein" в Nominativ среднего рода оба прилагательных получают сильное -es: "ein neues, schnelles Auto".'),
  sc(4, 'EASY', 'Die jungen, ___ Mitarbeiter beginnen heute.', op4('motivierten', 'motivierte', 'motivierter', 'motiviertes'), 'a',
    'Во множественном числе после "die" оба прилагательных идут на -en: "die jungen, motivierten Mitarbeiter".'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Sie traf sich mit alten, ___ Freunden aus der Schulzeit.', op4('gute', 'guter', 'guten', 'gutes'), 'c',
    'В Dativ Plural без артикля и существительное, и оба прилагательных оканчиваются на -en: "mit alten, guten Freunden".'),
  sc(6, 'HARD', 'Mit zwei ___ Kindern ist das Leben anstrengend.', op4('kleinen', 'kleine', 'kleiner', 'kleines'), 'a',
    'Числительное zwei не влияет на склонение, а в Dativ Plural без артикля прилагательное даёт сильное -en: "mit zwei kleinen Kindern".'),
  sc(7, 'HARD', 'Beide ___ Kollegen haben sich schnell eingearbeitet.', op4('neue', 'neuer', 'neuen', 'neues'), 'c',
    'После "beide" прилагательное идёт по слабому образцу на -en: "beide neuen Kollegen".'),
  sc(8, 'HARD', 'Sämtliche ___ Dokumente liegen schon auf dem Tisch.', op4('wichtige', 'wichtigen', 'wichtiger', 'wichtiges'), 'b',
    'После "sämtliche" прилагательное обычно следует слабому образцу на -en: "sämtliche wichtigen Dokumente".'),
  sc(9, 'HARD', 'Wegen des ___, anstrengenden Tages war sie müde.', op4('lange', 'langer', 'langes', 'langen'), 'd',
    'В Genitiv муж. р. после "des" оба прилагательных идут по слабому образцу на -en: "des langen, anstrengenden Tages".'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'In welchen Wortgruppen ist die parallele Adjektivendung richtig?', op5('ein großer, heller Raum', 'die jungen, motivierten Leute', 'ein großer, hellen Raum', 'die jungen, motivierte Leute', 'das neue, schnellen Auto'), ['a', 'b'],
    'Несколько прилагательных подряд получают одинаковое окончание: "großer, heller" и "jungen, motivierten".'),
  mc(11, 'EASY', 'Welche Akkusativ-Wortgruppen sind korrekt dekliniert?', op5('einen erfahrenen, zuverlässigen Mitarbeiter', 'eine nette, hilfsbereite Kollegin', 'einen erfahrenen, zuverlässige Mitarbeiter', 'eine nette, hilfsbereiten Kollegin', 'ein erfahrenen, zuverlässigen Mitarbeiter'), ['a', 'b'],
    'В Akkusativ оба прилагательных склоняются одинаково: "einen erfahrenen, zuverlässigen" и "eine nette, hilfsbereite".'),
  mc(12, 'EASY', 'In welchen Sätzen sind beide Adjektive richtig dekliniert?', op5('Das große, helle Zimmer gefällt mir.', 'Die alten, guten Freunde kommen heute.', 'Das große, hellen Zimmer gefällt mir.', 'Die alten, gute Freunde kommen heute.', 'Der nette, freundlichen Mann hilft.'), ['a', 'b'],
    'Прилагательные в цепочке получают одно окончание: "große, helle" в Nominativ ед. ч. и "alten, guten" во мн. ч.'),
  mc(13, 'EASY', 'Welche Wortgruppen mit zwei Adjektiven sind grammatisch richtig?', op5('eine lange, schwierige Aufgabe', 'ein kleiner, runder Tisch', 'eine lange, schwierigen Aufgabe', 'ein kleiner, runden Tisch', 'das alte, großen Haus'), ['a', 'b'],
    'Оба прилагательных склоняются параллельно: "lange, schwierige" (жен. р.) и "kleiner, runder" (муж. р.).'),
  mc(14, 'EASY', 'In welchen Phrasen ist die Pluralendung -en bei beiden Adjektiven richtig?', op5('die neuen, motivierten Kollegen', 'die kleinen, neugierigen Kinder', 'die neuen, motivierte Kollegen', 'die kleine, neugierigen Kinder', 'die neuer, motivierten Kollegen'), ['a', 'b'],
    'Во множественном числе после "die" оба прилагательных оканчиваются на -en: "neuen, motivierten" и "kleinen, neugierigen".'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'In welchen Wortgruppen mit beide/sämtliche ist die Endung richtig?', op6('beide neuen Mitarbeiter', 'sämtliche wichtigen Dokumente', 'beide neue Mitarbeiter', 'sämtliche wichtige Dokumente', 'beide neuer Mitarbeiter', 'sämtliche wichtiges Dokumente'), ['a', 'b'],
    'После "beide" и "sämtliche" прилагательное обычно идёт по слабому образцу на -en: "beide neuen", "sämtliche wichtigen".'),
  mc(16, 'HARD', 'Welche Dativ-Plural-Wortgruppen ohne Artikel sind richtig?', op6('mit alten, guten Freunden', 'mit zwei kleinen Kindern', 'mit alten, gute Freunden', 'mit zwei kleine Kindern', 'mit alter, guten Freunden', 'mit zwei kleiner Kindern'), ['a', 'b'],
    'В Dativ Plural без артикля и прилагательное, и существительное оканчиваются на -en: "mit alten, guten Freunden", "mit zwei kleinen Kindern".'),
  mc(17, 'HARD', 'In welchen Sätzen mit erweiterten Partizipgruppen ist die Endung korrekt?', op6('Die gestern gelieferte Ware war teuer.', 'Ein klar formulierter Satz hilft.', 'Die gestern gelieferten Ware war teuer.', 'Ein klar formuliertes Satz hilft.', 'Die gestern geliefertem Ware war teuer.', 'Ein klar formulierte Satz hilft.'), ['a', 'b'],
    'Причастие в атрибутивной группе склоняется как прилагательное: "die gelieferte Ware", "ein formulierter Satz".'),
  mc(18, 'HARD', 'Welche Genitiv- und Dativ-Wortgruppen mit zwei Adjektiven sind richtig?', op6('trotz des langen, schwierigen Tages', 'mit dem neuen, schnellen Auto', 'trotz des langen, schwierige Tages', 'mit dem neuen, schnelle Auto', 'trotz des lange, schwierigen Tages', 'mit dem neue, schnellen Auto'), ['a', 'b'],
    'В Genitiv и Dativ после артикля оба прилагательных идут по слабому образцу на -en: "des langen, schwierigen", "dem neuen, schnellen".'),
  mc(19, 'HARD', 'Welche Aussagen über die Adjektivketten sind richtig?', op5('Mehrere Adjektive vor einem Nomen bekommen dieselbe Endung.', 'Zahlwörter wie zwei beeinflussen die Adjektivendung nicht.', 'Nach beide steht das Adjektiv immer in der starken Form.', 'Im Dativ Plural ohne Artikel enden die Adjektive auf -e.', 'Partizipien werden in Wortgruppen nicht dekliniert.'), ['a', 'b'],
    'Прилагательные в цепочке склоняются параллельно, а числительные (zwei, drei) на их окончание не влияют.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Мы ищем опытного сотрудника».', items5('Wir', 'suchen', 'einen', 'erfahrenen', 'Mitarbeiter'),
    'В Akkusativ после "einen" прилагательное получает -en, глагол стоит на втором месте (V2): "Wir suchen einen erfahrenen Mitarbeiter".'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Молодые мотивированные сотрудники начинают».', items5('Die', 'jungen', 'motivierten', 'Mitarbeiter', 'beginnen'),
    'Во множественном числе после "die" оба прилагательных в цепочке получают -en: "die jungen, motivierten Mitarbeiter beginnen".'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Маленькие любопытные дети спят».', items5('Die', 'kleinen', 'neugierigen', 'Kinder', 'schlafen'),
    'Во множественном числе после "die" оба прилагательных в цепочке получают -en, глагол стоит на втором месте (V2): "die kleinen, neugierigen Kinder schlafen".'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Это быстрая машина».', items5('Das', 'ist', 'ein', 'schnelles', 'Auto'),
    'После "ein" в Nominativ среднего рода прилагательное получает сильное -es: "ein schnelles Auto".'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Любопытные дети задают вопросы».', items4('Die', 'neugierigen', 'Kinder', 'fragen'),
    'Во множественном числе прилагательное оканчивается на -en: "die neugierigen Kinder", глагол на втором месте.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Оба новых коллеги работают быстро».', items5('Beide', 'neuen', 'Kollegen', 'arbeiten', 'schnell'),
    'После "beide" прилагательное идёт по слабому образцу на -en: "beide neuen Kollegen arbeiten schnell".'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «С маленькими детьми она путешествует».', items5('Mit', 'kleinen', 'Kindern', 'reist', 'sie'),
    'В Dativ Plural без артикля прилагательное оканчивается на -en; если группа стоит на первом месте, глагол идёт на втором (V2), а подлежащее — после него: "Mit kleinen Kindern reist sie".'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Доставленный товар был дорогим».', items5('Die', 'gelieferte', 'Ware', 'war', 'teuer'),
    'Причастие "geliefert" склоняется как прилагательное на -e в Nominativ жен. р.: "die gelieferte Ware war teuer".'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Все важные документы лежат здесь».', items5('Sämtliche', 'wichtigen', 'Dokumente', 'liegen', 'hier'),
    'После "sämtliche" прилагательное обычно идёт по слабому образцу на -en: "sämtliche wichtigen Dokumente".'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Она встретила старых хороших друзей».', items5('Sie', 'traf', 'alte', 'gute', 'Freunde'),
    'В Akkusativ Plural без артикля оба прилагательных получают сильное -e: "Sie traf alte, gute Freunde".'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'ein großer, ___ Raum', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('heller', 'hellen', 'helle', 'hellem', 'helles'),
    'Без определителя рода оба прилагательных получают сильное -er: "ein großer, heller Raum".'),
  fb(31, 'EASY', 'die jungen, ___ Mitarbeiter', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('motivierte', 'motivierten', 'motivierter', 'motiviertes', 'motiviertem'),
    'Во множественном числе после "die" оба прилагательных оканчиваются на -en: "die jungen, motivierten Mitarbeiter".'),
  fb(32, 'EASY', 'In dem großen, ___ Büro arbeiten viele Leute.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('helle', 'heller', 'hellen', 'hellem', 'helles'),
    'В Dativ после "dem" оба прилагательных идут по слабому образцу на -en: "in dem großen, hellen Büro".'),
  fb(33, 'EASY', 'ein neues, ___ Auto', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('schnellen', 'schnelle', 'schneller', 'schnelles', 'schnellem'),
    'После "ein" в Nominativ среднего рода оба прилагательных получают сильное -es: "ein neues, schnelles Auto".'),
  fb(34, 'EASY', 'eine nette, ___ Kollegin', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('hilfsbereiten', 'hilfsbereiter', 'hilfsbereites', 'hilfsbereitem', 'hilfsbereite'),
    'В Nominativ жен. р. после "eine" оба прилагательных получают -e: "eine nette, hilfsbereite Kollegin".'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'mit alten, ___ Freunden', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('guten', 'gute', 'guter', 'gutes', 'gutem'),
    'В Dativ Plural без артикля оба прилагательных оканчиваются на -en: "mit alten, guten Freunden".'),
  fb(36, 'HARD', 'beide ___ Kollegen', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('neue', 'neuen', 'neuer', 'neues', 'neuem'),
    'После "beide" прилагательное идёт по слабому образцу на -en: "beide neuen Kollegen".'),
  fb(37, 'HARD', 'mit zwei ___ Kindern', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('kleine', 'kleiner', 'kleinen', 'kleines', 'kleinem'),
    'Числительное zwei не влияет на склонение, а Dativ Plural без артикля даёт -en: "mit zwei kleinen Kindern".'),
  fb(38, 'HARD', 'die gestern ___ Ware', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('gelieferten', 'gelieferter', 'geliefertes', 'gelieferte', 'geliefertem'),
    'Причастие в атрибутивной группе склоняется как прилагательное на -e в Nominativ жен. р.: "die gelieferte Ware".'),
  fb(39, 'HARD', 'sämtliche ___ Dokumente', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('wichtige', 'wichtiger', 'wichtiges', 'wichtigem', 'wichtigen'),
    'После "sämtliche" прилагательное обычно идёт по слабому образцу на -en: "sämtliche wichtigen Dokumente".'),
];
