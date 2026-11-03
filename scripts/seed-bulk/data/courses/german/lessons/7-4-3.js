'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Was bedeutet, dass ein Wort „mehrdeutig“ ist?', op4(
    'Es hat mehrere mögliche Bedeutungen.',
    'Es wird immer großgeschrieben.',
    'Es hat keinen Artikel.',
    'Es kommt nur in Gedichten vor.'
  ), 'a', 'Многозначное (mehrdeutig) слово допускает несколько возможных прочтений.'),

  sc(1, 'EASY', 'Welche zwei Bedeutungen kann „die Bank“ haben?', op4(
    'Fluss und Berg',
    'Geldinstitut und Sitzbank',
    'Auto und Fahrrad',
    'Tag und Nacht'
  ), 'b', 'Слово «die Bank» омонимично: это и банк, и скамья.'),

  sc(2, 'EASY', 'Welches Wort beschreibt einen Scherz, der mit zwei Bedeutungen eines Wortes spielt?', op4(
    'der Fahrplan',
    'die Rechnung',
    'das Wortspiel',
    'die Tabelle'
  ), 'c', 'Каламбур (das Wortspiel) обыгрывает сразу два значения слова.'),

  sc(3, 'EASY', 'Was bedeutet „der Flügel“ im Satz „Sie spielt am Flügel“?', op4(
    'das Klavier (der Konzertflügel)',
    'der Vogelflügel',
    'die Seite einer Armee',
    'die Tür eines Schranks'
  ), 'a', 'В контексте музыки «der Flügel» означает рояль, а не крыло или фланг.'),

  sc(4, 'EASY', 'Welcher Begriff bezeichnet die verschiedenen möglichen Deutungen eines Satzes?', op4(
    'die Rechtschreibung',
    'die Lesart',
    'die Aussprache',
    'der Buchstabe'
  ), 'b', '«Die Lesart» — это вариант прочтения, толкования высказывания.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', '„Er sah die Frau mit dem Fernglas.“ — Worin besteht die syntaktische Mehrdeutigkeit?', op4(
    'Es ist unklar, ob „Frau“ Subjekt oder Objekt ist.',
    'Es ist unklar, ob er oder die Frau das Fernglas hatte.',
    'Es ist unklar, ob er die Frau wirklich sah.',
    'Es ist unklar, welches Geschlecht „Fernglas“ hat.'
  ), 'b', 'Структурная амбигуитет: präpositionale Gruppe «mit dem Fernglas» относится либо к глаголу «sah», либо к «Frau».'),

  sc(6, 'HARD', '„Der Komplex, den die Architekten entwarfen, stürzte ein.“ — Welcher Garden-Path-Effekt liegt vor?', op4(
    '„Komplex“ wird zuerst psychologisch gelesen, meint aber das Gebäude.',
    '„entwarfen“ wird als Substantiv missverstanden.',
    'Der Relativsatz hat kein Subjekt.',
    'Das Verb „einstürzen“ ist nicht trennbar.'
  ), 'a', 'Garden-path: «Komplex» сначала читается как психический, но конец фразы навязывает значение «здание».'),

  sc(7, 'HARD', '„Peter sagte Hans, dass er gewonnen habe.“ — Worauf bezieht sich „er“?', op4(
    'eindeutig auf Hans',
    'eindeutig auf Peter',
    'auf eine dritte, nicht genannte Person',
    'es ist mehrdeutig: auf Peter oder auf Hans'
  ), 'd', 'Референция местоимения «er» неоднозначна — оно может указывать и на Peter, и на Hans.'),

  sc(8, 'HARD', 'Welche Eigenschaft macht „der Flügel“ zu einem Fall von Polysemie statt bloßer Homonymie?', op4(
    'Die Bedeutungen sind historisch und bildlich miteinander verwandt.',
    'Die Bedeutungen haben rein zufällig dieselbe Form.',
    'Das Wort hat in jeder Bedeutung einen anderen Artikel.',
    'Das Wort existiert nur im Plural.'
  ), 'a', 'При полисемии значения «Flügel» (крыло, рояль, фланг) исторически и образно связаны, а не случайно совпали.'),

  sc(9, 'HARD', 'Die Schlagzeile „Bank gibt nach“ ist bewusst doppeldeutig. Wodurch entsteht die zweite Lesart?', op4(
    'durch einen Rechtschreibfehler',
    'durch die Homonymie von „Bank“ (Institut / Sitzbank)',
    'durch die fehlende Großschreibung',
    'durch ein falsches Verb'
  ), 'b', 'Двусмысленность заголовка рождается из омонимии «Bank»: уступает либо банк, либо проседает скамья.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Begriffe gehören zum Wortfeld „Mehrdeutigkeit“?', op5(
    'die Doppeldeutigkeit',
    'das Wortspiel',
    'der Kühlschrank',
    'die Lesart',
    'der Bahnsteig'
  ), ['a', 'b', 'd'], 'К полю «многозначность» относятся «двусмысленность», «игра слов» и «прочтение».'),

  mc(11, 'EASY', 'Welche Wörter sind im Deutschen typische Beispiele für mehrere Bedeutungen?', op5(
    'die Bank',
    'das Schloss',
    'der Tisch',
    'der Flügel',
    'der Apfel'
  ), ['a', 'b', 'd'], '«Bank», «Schloss» и «Flügel» многозначны, тогда как «Tisch» и «Apfel» однозначны.'),

  mc(12, 'EASY', 'Welche zwei Bedeutungen kann „das Schloss“ tragen?', op6(
    'die Burg / der Palast',
    'der Türverschluss',
    'der Fluss',
    'das Wetter',
    'das Auto',
    'der Tag'
  ), ['a', 'b'], 'Слово «das Schloss» значит и замок-дворец, и дверной замок.'),

  mc(13, 'EASY', 'Welche Substantive sind hier korrekt großgeschrieben und mit richtigem Artikel?', op5(
    'die Mehrdeutigkeit',
    'das Wortspiel',
    'die Lesart',
    'der mehrdeutigkeit',
    'das anspielung'
  ), ['a', 'b', 'c'], 'Существительные пишутся с большой буквы и с верным родом: die Mehrdeutigkeit, das Wortspiel, die Lesart.'),

  mc(14, 'EASY', 'Welche Sätze nutzen ein Wort, das mehr als eine Lesart zulässt?', op5(
    'Ich gehe zur Bank.',
    'Sie spielt am Flügel.',
    'Der Hund schläft.',
    'Das Schloss ist alt.',
    'Die Sonne scheint.'
  ), ['a', 'b', 'd'], '«Bank», «Flügel» и «Schloss» допускают по два прочтения, а «Hund» и «Sonne» однозначны.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Aussagen über lexikalische Mehrdeutigkeit (Polysemie) treffen zu?', op5(
    'Die richtige Lesart wird durch Kontext und Kollokation festgelegt.',
    'Bei Polysemie sind die Bedeutungen sinnverwandt.',
    'Mehrdeutige Wörter haben stets unterschiedliche Artikel je Bedeutung.',
    'Werbung und Schlagzeilen nutzen Mehrdeutigkeit oft bewusst.',
    'Mehrdeutigkeit lässt sich nie durch Kontext auflösen.'
  ), ['a', 'b', 'd'], 'Полисемия задаётся контекстом и сочетаемостью, значения родственны, а реклама ею играет намеренно.'),

  mc(16, 'HARD', 'In welchen Sätzen ist die Bezugsambiguität eines Pronomens echt vorhanden?', op6(
    'Peter sagte Hans, dass er gewonnen habe.',
    'Die Lehrerin lobte die Schülerin, weil sie fleißig war.',
    'Der Hund bellte, und er lief weg.',
    'Anna traf Maria, als sie krank war.',
    'Es regnete den ganzen Tag.',
    'Der Stein fiel ins Wasser.'
  ), ['a', 'b', 'd'], 'В этих фразах «er/sie» может относиться к обоим лицам; в остальных референция однозначна или местоимения нет.'),

  mc(17, 'HARD', 'Welche Fälle sind Beispiele für eine syntaktische (strukturelle) Mehrdeutigkeit?', op5(
    'Er beobachtete den Mann mit dem Fernrohr.',
    'Alte Männer und Frauen wurden eingeladen.',
    'Die Katze trinkt Milch.',
    'Sie suchte den Schlüssel im Haus, das brannte.',
    'Der Lehrer empfahl dem Schüler das Buch des Kollegen, der krank war.'
  ), ['a', 'b', 'e'], 'Привязка präpositionalen/relativen Gruppen и охват «alte» допускают по два разбора; «Katze» и пример со «das brannte» однозначны.'),

  mc(18, 'HARD', 'Welche Mittel können eine strukturelle Mehrdeutigkeit gezielt auflösen?', op5(
    'eine umschreibende Paraphrase',
    'eine veränderte Wortstellung',
    'das Weglassen aller Artikel',
    'ein klärender Relativsatz',
    'die Großschreibung des Verbs'
  ), ['a', 'b', 'd'], 'Снять амбигуитет помогают перифраз, иной порядок слов и уточняющий придаточный, но не отказ от артиклей.'),

  mc(19, 'HARD', 'Welche Aussagen über den Garden-Path-Effekt sind korrekt?', op5(
    'Der Satz führt zunächst zu einer falschen Lesart.',
    'Erst das letzte Wort erzwingt die Neuanalyse.',
    'Der Effekt entsteht nur durch Rechtschreibfehler.',
    'Ein temporär falsch interpretiertes Wort wird nachträglich umgedeutet.',
    'Garden-Path-Sätze sind grammatisch immer falsch.'
  ), ['a', 'b', 'd'], 'Garden-path ведёт к ложному прочтению до конца фразы и требует переразбора, оставаясь при этом грамматически верным.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Слово „Bank“ имеет два значения».',
    items5('Das Wort „Bank“', 'hat', 'zwei', 'Bedeutungen', '.'),
    'Порядок V2: подлежащее, спрягаемый глагол «hat», затем дополнение «zwei Bedeutungen».'),

  ord(21, 'EASY', 'Соберите немецкое предложение: «Заголовок намеренно двусмысленный».',
    items4('Die Schlagzeile', 'ist', 'bewusst', 'doppeldeutig'),
    'Простое предложение: глагол «ist» на втором месте, предикатив «doppeldeutig» в конце.'),

  ord(22, 'EASY', 'Соберите немецкое предложение: «Контекст определяет значение».',
    items4('Der Kontext', 'bestimmt', 'die Bedeutung', '.'),
    'Переходный «bestimmen» с аккузативом «die Bedeutung» стоит после спрягаемого глагола на втором месте.'),

  ord(23, 'EASY', 'Соберите немецкое предложение: «Стихотворение играет на многозначности».',
    items5('Das Gedicht', 'spielt', 'mit', 'der Mehrdeutigkeit', '.'),
    'Предлог «mit» требует датива: «der Mehrdeutigkeit».'),

  ord(24, 'EASY', 'Соберите немецкое предложение: «Она играет на рояле».',
    items4('Sie', 'spielt', 'am Flügel', '.'),
    'Слитное «am» = «an dem» (Dativ) указывает на рояль: «am Flügel».'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Каламбур играет на том, что „Schloss“ означает две вещи».',
    items5('Das Wortspiel', 'spielt damit', 'dass', '„Schloss“ zweierlei', 'bedeutet'),
    'В придаточном с «dass» спрягаемый глагол «bedeutet» стоит в самом конце.'),

  ord(26, 'HARD', 'Соберите немецкое предложение: «Кто слышит „Flügel“, тот не сразу знает, что имеется в виду».',
    items5('Wer „Flügel“ hört', 'weiß', 'nicht sofort', 'was gemeint', 'ist'),
    'Главное на V2 («weiß»), косвенный вопрос с «was» завершается глаголом «ist» в конце.'),

  ord(27, 'HARD', 'Соберите немецкое предложение: «За двусмысленным заголовком скрываются два прочтения».',
    items5('Hinter der doppeldeutigen Schlagzeile', 'stecken', 'zwei', 'mögliche Lesarten', '.'),
    'Инверсия: при выносе обстоятельства вперёд глагол «stecken» сохраняет второе место.'),

  ord(28, 'HARD', 'Соберите немецкое предложение: «Если контекст отсутствует, остаётся открытым, что подразумевается».',
    items5('Wenn der Kontext fehlt', 'bleibt offen', 'was', 'gemeint', 'ist'),
    'После «wenn»-придаточного главное начинается с «bleibt», косвенный вопрос с «was» оканчивается на «ist».'),

  ord(29, 'HARD', 'Соберите немецкое предложение: «Структурная амбигуитет снимается, когда меняют порядок слов».',
    items5('Die Strukturambiguität', 'löst sich auf', 'wenn', 'man die Wortstellung', 'ändert'),
    'Возвратный «sich auflösen» даёт «löst sich auf»; в придаточном с «wenn» глагол «ändert» уходит в самый конец.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', '___ Wortspiel nutzt zwei Bedeutungen eines Wortes.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('Das', 'Der', 'Die', 'Den', 'Dem'),
    'Слово «Wortspiel» среднего рода, поэтому в номинативе артикль «das».'),

  fb(31, 'EASY', 'Die Bedeutung von „Bank“ hängt von ___ Kontext ab.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('der', 'dem', 'den', 'das', 'die'),
    'Глагол «abhängen von» требует датива: «von dem Kontext».'),

  fb(32, 'EASY', 'Das Wort „Schloss“ ___ zwei verschiedene Dinge.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('bedeuten', 'bedeutest', 'bedeutet', 'bedeute', 'bedeutend'),
    'Подлежащее «das Wort» в 3-м лице ед. ч. требует формы «bedeutet».'),

  fb(33, 'EASY', 'Sie versteht ___ Hintersinn der Schlagzeile sofort.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('der', 'die', 'das', 'den', 'dem'),
    'Прямое дополнение мужского рода в аккузативе: «der Hintersinn» → «den Hintersinn».'),

  fb(34, 'EASY', 'Jede Bedeutung nennt man auch eine ___.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('Schlagzeile', 'Aussprache', 'Rechtschreibung', 'Großschreibung', 'Lesart'),
    'Вариант прочтения значения называют «Lesart» — единственный подходящий по смыслу вариант.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Die Mehrdeutigkeit ___ sich auf, sobald der Kontext klar ist.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('löst', 'lösen', 'gelöst', 'lösend', 'löst sein'),
    'Возвратный глагол «sich auflösen» в 3-м лице ед. ч.: «löst sich … auf».'),

  fb(36, 'HARD', 'Der Garden-Path-Satz wird erst am Ende richtig ___.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('verstehen', 'verstanden', 'verstand', 'versteht', 'verstehend'),
    'Пассив с «wird … verstanden» требует Partizip II «verstanden».'),

  fb(37, 'HARD', 'Es bleibt offen, ___ er oder sie das Fernrohr hatte.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('weil', 'dass', 'ob', 'wenn', 'damit'),
    'Косвенный вопрос о неизвестном вводится союзом «ob» (ли).'),

  fb(38, 'HARD', 'Bei der Polysemie sind die Bedeutungen miteinander ___.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('zufällig', 'getrennt', 'fremd', 'verwandt', 'gleich'),
    'Полисемию отличает то, что значения родственны (verwandt), а не случайны.'),

  fb(39, 'HARD', 'Hinter der Schlagzeile ___ zwei mögliche Lesarten.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('steckt', 'steckst', 'gesteckt', 'stecktest', 'stecken'),
    'Подлежащее «zwei Lesarten» во множественном числе требует формы «stecken».'),
];
