'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Was bedeutet „zwischen den Zeilen lesen“?', op4(
    'das Gemeinte erfassen, das nicht ausdrücklich gesagt wird',
    'jeden Satz laut vorlesen',
    'nur die Überschriften überfliegen',
    'den Text wörtlich abschreiben'
  ), 'a', 'Идиома «читать между строк» означает улавливать подразумеваемое, прямо не высказанное.'),

  sc(1, 'EASY', 'Welcher Begriff bezeichnet das, was ein Sprecher nahelegt, ohne es zu sagen?', op4(
    'die Worttrennung',
    'die Implikatur',
    'die Rechtschreibung',
    'die Aussprache'
  ), 'b', 'Импликатура — это то, что говорящий подразумевает, не произнося прямо.'),

  sc(2, 'EASY', '„Einige Studenten haben bestanden.“ — Was wird hier nahegelegt?', op4(
    'Alle Studenten haben bestanden.',
    'Kein Student hat bestanden.',
    'Nicht alle Studenten haben bestanden.',
    'Genau ein Student hat bestanden.'
  ), 'c', 'Скалярная импликатура «einige» наводит на мысль «не все», хотя логически не исключает всех.'),

  sc(3, 'EASY', 'Wie nennt man eine vorsichtige, indirekte Bemerkung, die etwas nur nahelegt?', op4(
    'die Andeutung',
    'die Vorlesung',
    'die Rechnung',
    'die Bestellung'
  ), 'a', 'Намёк (die Andeutung) — это косвенная реплика, которая лишь наводит на смысл.'),

  sc(4, 'EASY', 'Welches Wort meint den verborgenen, tieferen Sinn einer Äußerung?', op4(
    'der Vordergrund',
    'der Wortlaut',
    'der Hintersinn',
    'der Buchstabe'
  ), 'c', '«Der Hintersinn» обозначает скрытый, потаённый смысл сказанного.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', '„Er hat aufgehört zu rauchen.“ — Was wird hier vorausgesetzt (Voraussetzung), nicht bloß impliziert?', op4(
    'Er raucht jetzt mehr als früher.',
    'Er hat früher geraucht.',
    'Er wird bald wieder anfangen.',
    'Er hat nie geraucht.'
  ), 'b', 'Пресуппозиция глагола «aufhören» — что он раньше курил, и это нельзя отрицать без противоречия.'),

  sc(6, 'HARD', 'Welche Antwort verletzt am deutlichsten Grices Maxime der Quantität und legt damit etwas Negatives nahe?', op4(
    'Auf die Frage nach dem Aufsatz sagte er ausführlich, was gut und was schwach war.',
    'Auf die Frage nach dem Aufsatz sagte sie nur: „Nun ja, die Handschrift ist ordentlich.“',
    'Auf die Frage nach dem Aufsatz lobte sie die klare Argumentation in jedem Absatz.',
    'Auf die Frage nach dem Aufsatz nannte er konkret drei überzeugende Belege.'
  ), 'b', 'Похвалив лишь почерк вместо содержания, говорящий нарушает максиму количества и тем намекает, что работа слабая.'),

  sc(7, 'HARD', 'Worin liegt der Unterschied zwischen Implikatur und Voraussetzung?', op4(
    'Beide bleiben unter Verneinung unverändert bestehen.',
    'Die Implikatur ist tilgbar, die Voraussetzung bleibt auch unter Verneinung bestehen.',
    'Die Voraussetzung lässt sich beliebig aufheben, die Implikatur nie.',
    'Es gibt zwischen ihnen keinen Unterschied.'
  ), 'b', 'Импликатуру можно снять без противоречия, а пресуппозиция сохраняется даже при отрицании.'),

  sc(8, 'HARD', 'Welcher Satz zeigt, dass die Skalar-Implikatur „nicht alle“ tatsächlich aufhebbar ist?', op4(
    'Einige, ja sogar alle, waren einverstanden.',
    'Einige waren einverstanden, also nicht alle.',
    'Alle waren einverstanden, niemand fehlte.',
    'Niemand war einverstanden, alle widersprachen.'
  ), 'a', 'Добавление «ja sogar alle» снимает импликатуру «не все» без противоречия — значит это импликатура, а не часть значения.'),

  sc(9, 'HARD', '„Das hättest du mir ruhig früher sagen können.“ — Was steckt hinter dem höflichen Konjunktiv?', op4(
    'ein aufrichtiges Lob',
    'eine sachliche Terminfrage',
    'ein deutlicher Vorwurf',
    'eine gleichgültige Feststellung'
  ), 'c', 'За вежливым конъюнктивом скрывается явный упрёк, что не сказали раньше.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Ausdrücke gehören zum Wortfeld „Indirektheit / Implikatur“?', op5(
    'die Andeutung',
    'der Hintersinn',
    'der Fahrplan',
    'die Schlussfolgerung',
    'der Kühlschrank'
  ), ['a', 'b', 'd'], 'К полю «косвенность/импликатура» относятся «намёк», «скрытый смысл» и «вывод».'),

  mc(11, 'EASY', 'Welche Sätze legen nahe, dass NICHT alle gemeint sind?', op5(
    'Einige Gäste sind schon gegangen.',
    'Manche Antworten waren richtig.',
    'Alle Gäste sind schon gegangen.',
    'Ein paar Kollegen haben zugestimmt.',
    'Sämtliche Antworten waren richtig.'
  ), ['a', 'b', 'd'], '«Einige», «manche» и «ein paar» наводят на мысль «не все», тогда как «alle» и «sämtliche» означают всех.'),

  mc(12, 'EASY', 'Welche Wendungen bedeuten, etwas nur anzudeuten statt direkt zu sagen?', op5(
    'zwischen den Zeilen lesen lassen',
    'etwas durch die Blume sagen',
    'etwas beim Namen nennen',
    'etwas nahelegen',
    'Klartext reden'
  ), ['a', 'b', 'd'], 'Намекают, когда говорят «между строк», «durch die Blume» или «nahelegen», а не прямо.'),

  mc(13, 'EASY', 'Welche Substantive sind hier korrekt mit großem Anfangsbuchstaben und passendem Artikel genannt?', op5(
    'die Implikatur',
    'die Voraussetzung',
    'der Hintersinn',
    'die andeutung',
    'das schlussfolgerung'
  ), ['a', 'b', 'c'], 'Существительные пишутся с большой буквы и с верным родом: die Implikatur, die Voraussetzung, der Hintersinn.'),

  mc(14, 'EASY', 'Welche Reaktionen deuten höflich Unzufriedenheit an, statt sie offen zu nennen?', op6(
    'Nun ja, er bemüht sich.',
    'Sagen wir, es ist ein Anfang.',
    'Das war hervorragend, danke!',
    'Es geht so.',
    'Eine glänzende Leistung!',
    'Ich bin restlos begeistert.'
  ), ['a', 'b', 'd'], 'Сдержанные «он старается», «это начало» и «так себе» вежливо намекают на недовольство.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Aussagen über konversationelle Implikaturen treffen zu?', op5(
    'Sie sind ohne Widerspruch tilgbar (aufhebbar).',
    'Sie entstehen aus dem Kontext und dem Kooperationsprinzip.',
    'Sie sind Teil der wörtlichen Wahrheitsbedingungen des Satzes.',
    'Sie lassen sich durch einen Zusatz verstärken oder zurücknehmen.',
    'Sie bleiben unter Verneinung zwingend erhalten.'
  ), ['a', 'b', 'd'], 'Конверсационная импликатура снимаема, рождается из контекста и принципа кооперации и не входит в буквальные истинностные условия.'),

  mc(16, 'HARD', 'Welche Sätze enthalten eine Voraussetzung (Präsupposition), die auch unter Verneinung bestehen bleibt?', op5(
    'Es ärgert ihn, dass der Zug verspätet ist.',
    'Sie hat ihr Rauchen endlich aufgegeben.',
    'Vielleicht kommt morgen jemand vorbei.',
    'Auch der König von Frankreich war anwesend.',
    'Möglicherweise hat er recht.'
  ), ['a', 'b', 'd'], 'Фактивное «ärgert», глагол «aufgeben» и определённая дескрипция несут пресуппозицию, сохраняющуюся при отрицании.'),

  mc(17, 'HARD', 'In welchen Fällen signalisiert der Konjunktiv II eine Implikatur der Distanzierung oder des Vorwurfs?', op6(
    'Das wäre ja schön gewesen.',
    'Du hättest ruhig fragen können.',
    'Ich wünsche dir einen schönen Tag.',
    'Das hätte ich dir gleich sagen können.',
    'Er kommt morgen um acht.',
    'Sie liest gerade die Zeitung.'
  ), ['a', 'b', 'd'], 'Konjunktiv II в «wäre schön gewesen», «hättest fragen können» и «hätte sagen können» намекает на нереализованность или упрёк.'),

  mc(18, 'HARD', 'Welche Fortsetzungen heben die Skalar-Implikatur „einige → nicht alle“ widerspruchsfrei auf?', op5(
    'Einige Abgeordnete, genauer gesagt alle, stimmten zu.',
    'Einige, wenn nicht sogar alle, waren dafür.',
    'Einige Abgeordnete, also keiner, stimmten zu.',
    'Einige – und in der Tat ausnahmslos alle – waren dafür.',
    'Einige Abgeordnete, das heißt höchstens zwei, stimmten zu.'
  ), ['a', 'b', 'd'], 'Снимают импликатуру лишь усиления к «alle», тогда как «keiner» и «höchstens zwei» её не отменяют.'),

  mc(19, 'HARD', 'Welche Antworten verletzen eine Grice-Maxime und erzeugen so eine Implikatur?', op5(
    'Auf „Wie war das Konzert?“ folgt nur: „Die Stühle waren bequem.“',
    'Auf „Hat er geholfen?“ folgt: „Sagen wir, er war anwesend.“',
    'Auf „Wie spät ist es?“ folgt: „Es ist Viertel nach drei.“',
    'Auf „Kommst du mit?“ folgt langes Schweigen und ein Räuspern.',
    'Auf „Wo wohnst du?“ folgt: „In der Bahnhofstraße zwölf.“'
  ), ['a', 'b', 'd'], 'Уход от темы, недосказанность и красноречивое молчание нарушают максимы и порождают импликатуру, а прямые ответы — нет.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Она читает между строк».',
    items4('Sie', 'liest', 'zwischen', 'den Zeilen'),
    'Порядок V2: подлежащее, спрягаемый глагол, затем обстоятельство «zwischen den Zeilen».'),

  ord(21, 'EASY', 'Соберите немецкое предложение: «Намёк был совершенно ясен».',
    items4('Die Andeutung', 'war', 'völlig', 'klar'),
    'Простое предложение с глаголом «war» на втором месте: «Намёк был совершенно ясен».'),

  ord(22, 'EASY', 'Соберите немецкое предложение: «Слова наводят на мысль о выводе».',
    items5('Die Worte', 'legen', 'eine Schlussfolgerung', 'nahe', '.'),
    'Отделяемая приставка «nahe» от «nahelegen» уходит в конец предложения.'),

  ord(23, 'EASY', 'Соберите немецкое предложение: «Некоторые коллеги прочитали отчёт».',
    items5('Einige Kollegen', 'haben', 'den Bericht', 'gelesen', '.'),
    'Перфект: «haben» на втором месте, причастие «gelesen» в конце.'),

  ord(24, 'EASY', 'Соберите немецкое предложение: «Он понимает скрытый смысл».',
    items4('Er', 'versteht', 'den Hintersinn', '.'),
    'Аккузатив «den Hintersinn» (der → den) стоит после спрягаемого глагола «versteht».'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Тем самым было ясно, что она недовольна».',
    items5('Damit', 'war klar', 'dass', 'sie nicht zufrieden', 'war'),
    'В придаточном с «dass» спрягаемый глагол «war» стоит в самом конце.'),

  ord(26, 'HARD', 'Соберите немецкое предложение: «Кто говорит „некоторые“, тот подразумевает, что не все».',
    items5('Wer „einige“ sagt', 'legt nahe', 'dass', 'nicht alle gemeint', 'sind'),
    'Главное с отделяемым «legt … nahe», затем «dass»-придаточное с глаголом «sind» в конце.'),

  ord(27, 'HARD', 'Соберите немецкое предложение: «За вежливым конъюнктивом скрывается явный упрёк».',
    items5('Hinter dem höflichen Konjunktiv', 'steckt', 'ein deutlicher', 'Vorwurf', '.'),
    'Инверсия: при выносе обстоятельства вперёд глагол «steckt» сохраняет второе место.'),

  ord(28, 'HARD', 'Соберите немецкое предложение: «Если кто-то умолкает, он часто намекает на нечто неприятное».',
    items5('Wenn jemand schweigt', 'deutet er', 'oft', 'etwas Unangenehmes', 'an'),
    'После «wenn»-придаточного главное начинается с глагола «deutet», а приставка «an» уходит в конец.'),

  ord(29, 'HARD', 'Соберите немецкое предложение: «Молчаливо предполагалось, что деньги вообще пропали».',
    items5('Stillschweigend', 'wurde vorausgesetzt', 'dass', 'überhaupt Geld', 'abhandengekommen war'),
    'Пассив «wurde vorausgesetzt» на V2, в «dass»-придаточном глагольная группа «abhandengekommen war» в конце.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', '___ Implikatur ist das Gemeinte, nicht das Gesagte.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('Die', 'Der', 'Das', 'Den', 'Dem'),
    'Слово «Implikatur» женского рода, поэтому в номинативе артикль «die».'),

  fb(31, 'EASY', 'Sie liest gern zwischen ___ Zeilen.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('die', 'den', 'der', 'das', 'dem'),
    'Предлог «zwischen» с дативом множественного числа даёт «den Zeilen».'),

  fb(32, 'EASY', 'Das Wort „einige“ ___ nahe, dass nicht alle gemeint sind.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('legen', 'legst', 'legt', 'lege', 'gelegt'),
    'Подлежащее «das Wort» (3-е лицо ед. ч.) требует формы «legt … nahe».'),

  fb(33, 'EASY', 'Er versteht ___ Hintersinn ihrer Worte sofort.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('der', 'die', 'das', 'den', 'dem'),
    'Прямое дополнение мужского рода в аккузативе: «der Hintersinn» → «den Hintersinn».'),

  fb(34, 'EASY', 'Aus ihren Worten zog er eine wichtige ___.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('Hintersinn', 'Hinweis', 'Wort', 'Schluss', 'Schlussfolgerung'),
    'После «eine wichtige» нужно существительное женского рода: только «die Schlussfolgerung» подходит и по роду, и по смыслу («Schlussfolgerung ziehen»).'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Wer sagt „er hat aufgehört zu rauchen“, ___ voraus, dass er früher rauchte.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('setzt', 'setzen', 'gesetzt', 'setzte', 'setzst'),
    'Подлежащее «wer» (3-е лицо ед.) даёт презенс «setzt … voraus».'),

  fb(36, 'HARD', 'Das wäre ja schön ___, aber es ist leider nie passiert.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('sein', 'gewesen', 'geworden', 'worden', 'wäre'),
    'Konjunktiv II прошедшего: «wäre … gewesen» сигналит, что этого не случилось.'),

  fb(37, 'HARD', 'Im Nebensatz steht der Vorwurf, ___ er das verschwiegen habe.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('weil', 'wenn', 'dass', 'ob', 'damit'),
    'После существительного «Vorwurf» содержание раскрывает изъяснительный союз «dass».'),

  fb(38, 'HARD', 'Einige, ja eigentlich ___, waren dafür — so wird die Implikatur aufgehoben.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('keiner', 'wenige', 'manche', 'alle', 'einige'),
    'Снять импликатуру «не все» можно только усилением к «alle».'),

  fb(39, 'HARD', 'Hinter dem höflichen Konjunktiv ___ oft ein deutlicher Vorwurf.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('stecken', 'steckst', 'steckte', 'gesteckt', 'steckt'),
    'При инверсии подлежащее «ein Vorwurf» (ед. ч.) сохраняет форму «steckt».'),
];
