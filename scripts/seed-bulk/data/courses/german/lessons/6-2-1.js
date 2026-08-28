'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY',
    'Welcher Satz ist im Nominalstil formuliert?',
    op4(
      'Wegen des starken Anstiegs der Preise müssen wir sparen.',
      'Weil die Preise stark gestiegen sind, müssen wir sparen.',
      'Da die Preise steigen, sparen wir.',
      'Wir sparen, weil die Preise steigen.'
    ),
    'a',
    'Номинальный стиль выражает причину предлогом «wegen» с отглагольным существительным «der Anstieg», а не придаточным с «weil».'),

  sc(1, 'EASY',
    'Welches Substantiv gehört zum Verb «untersuchen»?',
    op4(
      'der Untersuch',
      'die Untersuchung',
      'das Untersuchen-Heit',
      'die Untersuchtion'
    ),
    'b',
    'От глагола «untersuchen» образуется существительное «die Untersuchung» с типичным для номинального стиля суффиксом -ung.'),

  sc(2, 'EASY',
    'Welcher Artikel passt zu «Nominalstil»?',
    op4(
      'die Nominalstil',
      'das Nominalstil',
      'der Nominalstil',
      'den Nominalstil'
    ),
    'c',
    'Слово «Stil» мужского рода, поэтому правильно «der Nominalstil».'),

  sc(3, 'EASY',
    'Welche Endung ist typisch für viele verbale Substantive im Nominalstil?',
    op4(
      'die Endung -bar',
      'die Endung -los',
      'die Endung -sam',
      'die Endung -ung'
    ),
    'd',
    'Суффикс -ung типичен для отглагольных существительных и является ярким признаком номинального стиля.'),

  sc(4, 'EASY',
    'Welcher Satz ist im Verbalstil formuliert?',
    op4(
      'Nach Prüfung der Lage entschied die Behörde.',
      'Nachdem die Lage geprüft worden war, entschied die Behörde.',
      'Wegen der Prüfung der Lage entschied die Behörde.',
      'Bei Prüfung der Lage entschied die Behörde.'
    ),
    'b',
    'Вербальный стиль использует придаточное предложение с союзом «nachdem» и глаголом, а не предлог с существительным.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD',
    'Welche Nominalisierung von «Weil die Kosten steigen, …» ist korrekt?',
    op4(
      'Wegen das Steigen der Kosten …',
      'Wegen des Anstiegs der Kosten …',
      'Wegen der Anstieg der Kosten …',
      'Wegen dem Anstieg die Kosten …'
    ),
    'b',
    'После предлога «wegen» нужен родительный падеж: «des Anstiegs der Kosten» с правильными формами артиклей и существительных.'),

  sc(6, 'HARD',
    'Welche Genitivkette ist grammatisch korrekt?',
    op4(
      'die Erhöhung die Kosten der Produktion',
      'die Erhöhung der Kosten der Produktion',
      'die Erhöhung den Kosten der Produktion',
      'die Erhöhung der Kosten den Produktion'
    ),
    'b',
    'Цепочка родительного падежа требует артикль «der» в обоих звеньях: «der Kosten der Produktion».'),

  sc(7, 'HARD',
    'Welcher Satz nominalisiert «… , weil das Projekt scheiterte» korrekt mit «infolge»?',
    op4(
      'infolge des Scheiterns des Projekts',
      'infolge dem Scheitern des Projekts',
      'infolge das Scheitern des Projekts',
      'infolge des Scheitern dem Projekt'
    ),
    'a',
    'Предлог «infolge» управляет родительным падежом, а субстантивированный инфинитив «das Scheitern» в Genitiv даёт «des Scheiterns des Projekts».'),

  sc(8, 'HARD',
    'Welches Substantiv zu «produzieren» ist korrekt gebildet?',
    op4(
      'die Produzierung',
      'das Produzier',
      'die Produktion',
      'der Produktionung'
    ),
    'c',
    'Латинская основа даёт существительное на -tion: «die Produktion», что характерно для номинального стиля.'),

  sc(9, 'HARD',
    'In welchem Satz ersetzt eine Präposition korrekt den Nebensatz «obwohl es regnete»?',
    op4(
      'wegen des Regens fand das Spiel statt',
      'trotz des Regens fand das Spiel statt',
      'infolge des Regens fand das Spiel statt',
      'dank des Regens fand das Spiel statt'
    ),
    'b',
    'Уступительное «obwohl» в номинальном стиле передаётся предлогом «trotz» с родительным падежом «des Regens».'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY',
    'Welche Merkmale sind typisch für den Nominalstil? (Mehrere richtig)',
    op5(
      'viele verbale Substantive',
      'Genitivketten',
      'kurze Hauptsätze mit vielen Verben',
      'Umgangssprache und Dialoge',
      'Präpositionen statt Nebensätze'
    ),
    ['a', 'b', 'e'],
    'Для номинального стиля характерны отглагольные существительные, цепочки родительного падежа и предлоги вместо придаточных.'),

  mc(11, 'EASY',
    'Welche Wörter sind Substantive auf «-ung»? (Mehrere richtig)',
    op5(
      'die Untersuchung',
      'die Erhöhung',
      'abstrakt',
      'die Entscheidung',
      'steigen'
    ),
    ['a', 'b', 'd'],
    'Существительные с суффиксом -ung — это «Untersuchung», «Erhöhung» и «Entscheidung», а «abstrakt» и «steigen» ими не являются.'),

  mc(12, 'EASY',
    'Welche Präpositionen leiten typisch einen Nominalstil ein? (Mehrere richtig)',
    op5(
      'wegen',
      'und',
      'trotz',
      'aber',
      'infolge'
    ),
    ['a', 'c', 'e'],
    'Причинно-уступительные предлоги «wegen», «trotz», «infolge» заменяют союзы и вводят именные конструкции.'),

  mc(13, 'EASY',
    'In welchen Bereichen wird der Nominalstil bevorzugt? (Mehrere richtig)',
    op6(
      'in wissenschaftlichen Texten',
      'in der Verwaltungssprache',
      'in einem lockeren Gespräch',
      'in juristischen Texten',
      'in einer SMS an Freunde',
      'in einem privaten Tagebuch'
    ),
    ['a', 'b', 'd'],
    'Номинальный стиль предпочитается в научном, административном и юридическом регистрах, а не в неформальном общении.'),

  mc(14, 'EASY',
    'Welche Sätze sind im Nominalstil? (Mehrere richtig)',
    op5(
      'Nach der Untersuchung des Falls handelte die Behörde.',
      'Weil der Fall geprüft wurde, handelte die Behörde.',
      'Wegen der Erhöhung der Kosten sparen wir.',
      'Wir sparen, weil die Kosten steigen.',
      'Bei Ankunft des Zuges öffnen sich die Türen.'
    ),
    ['a', 'c', 'e'],
    'Именными являются предложения с предлогом и отглагольным существительным, а не с союзом и глаголом.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD',
    'Welche Nominalisierungen sind grammatisch korrekt? (Mehrere richtig)',
    op5(
      'wegen des Anstiegs der Preise',
      'trotz des schlechten Wetters',
      'wegen dem Anstieg die Preise',
      'infolge des Scheiterns des Projekts',
      'trotz dem schlechtes Wetter'
    ),
    ['a', 'b', 'd'],
    'Предлоги «wegen», «trotz», «infolge» требуют родительного падежа с правильными окончаниями артиклей и прилагательных.'),

  mc(16, 'HARD',
    'Welche Verb-Substantiv-Paare sind richtig zugeordnet? (Mehrere richtig)',
    op5(
      'entscheiden → die Entscheidung',
      'produzieren → die Produktion',
      'untersuchen → die Untersuchtion',
      'steigen → der Anstieg',
      'ankommen → die Ankommung'
    ),
    ['a', 'b', 'd'],
    'Корректны пары «Entscheidung», «Produktion» и «Anstieg», тогда как «Untersuchtion» и «Ankommung» — несуществующие формы.'),

  mc(17, 'HARD',
    'Welche Sätze drücken denselben Inhalt wie «Weil die Preise stiegen, sparten wir» im Nominalstil aus? (Mehrere richtig)',
    op5(
      'Wegen des Anstiegs der Preise sparten wir.',
      'Aufgrund des Preisanstiegs sparten wir.',
      'Trotz des Anstiegs der Preise sparten wir.',
      'Infolge des Preisanstiegs sparten wir.',
      'Obwohl die Preise stiegen, sparten wir.'
    ),
    ['a', 'b', 'd'],
    'Причинную связь сохраняют «wegen», «aufgrund» и «infolge», а «trotz» и «obwohl» меняют смысл на уступительный.'),

  mc(18, 'HARD',
    'Welche Aussagen über Nominal- und Verbalstil sind richtig? (Mehrere richtig)',
    op5(
      'Der Verbalstil stützt sich vor allem auf Verben und Nebensätze.',
      'Der Nominalstil verdichtet Inhalte durch Substantive.',
      'Der Nominalstil ist typisch für lockere Alltagsgespräche.',
      'Genitivketten sind ein Merkmal des Nominalstils.',
      'Der Verbalstil ist in der Wissenschaft strikt verboten.'
    ),
    ['a', 'b', 'd'],
    'Вербальный стиль опирается на глаголы и придаточные, а номинальный сжимает содержание существительными и цепочками генитива.'),

  mc(19, 'HARD',
    'Welche Genitivketten sind korrekt gebildet? (Mehrere richtig)',
    op5(
      'die Untersuchung des Falls der Behörde',
      'die Erhöhung der Kosten der Produktion',
      'die Erhöhung den Kosten die Produktion',
      'der Rückgang des Gewinns des Unternehmens',
      'der Rückgang der Gewinn dem Unternehmen'
    ),
    ['a', 'b', 'd'],
    'Каждое звено цепочки родительного падежа должно стоять в Genitiv с правильным артиклем и окончанием существительного.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY',
    'Соберите немецкое предложение: «Из-за роста цен мы экономим».',
    items4('Wegen', 'des Anstiegs', 'der Preise', 'sparen wir'),
    'Предлог «wegen» с родительным падежом стоит в начале, затем глагол на втором месте по правилу V2.'),

  ord(21, 'EASY',
    'Соберите немецкое предложение: «Исследование дела длилось долго».',
    items4('Die Untersuchung', 'des Falls', 'dauerte', 'lange'),
    'Именная группа в роли подлежащего стоит первой, спрягаемый глагол «dauerte» — на втором месте.'),

  ord(22, 'EASY',
    'Соберите немецкое предложение: «После проверки ведомство принимает решение».',
    items4('Nach der Prüfung', 'trifft', 'die Behörde', 'eine Entscheidung'),
    'Предложная группа открывает предложение, поэтому глагол «trifft» занимает второе место перед подлежащим.'),

  ord(23, 'EASY',
    'Соберите немецкое предложение: «Номинальный стиль использует существительные».',
    items4('Der Nominalstil', 'verwendet', 'viele', 'Substantive'),
    'Подлежащее «der Nominalstil» стоит первым, а спрягаемый глагол «verwendet» — на втором месте.'),

  ord(24, 'EASY',
    'Соберите немецкое предложение: «Повышение издержек снижает прибыль».',
    items4('Die Erhöhung', 'der Kosten', 'senkt', 'den Gewinn'),
    'Именная группа в Genitiv образует подлежащее, а прямое дополнение «den Gewinn» стоит в Akkusativ после глагола.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD',
    'Соберите немецкое предложение: «Из-за сильного роста цен мы должны экономить».',
    items5('Wegen', 'des starken Anstiegs', 'der Preise', 'müssen', 'wir sparen'),
    'Прилагательное «starken» получает слабое окончание после артикля в Genitiv, а модальный глагол «müssen» стоит на втором месте.'),

  ord(26, 'HARD',
    'Соберите немецкое предложение: «Повышение производственных издержек привело к снижению прибыли».',
    items5('Die Erhöhung der Produktionskosten', 'führte', 'zu', 'einem Rückgang', 'des Gewinns'),
    'Предлог «zu» требует Dativ «einem Rückgang», к которому примыкает определение в Genitiv «des Gewinns».'),

  ord(27, 'HARD',
    'Соберите немецкое предложение: «После рассмотрения дела ведомство приняло решение».',
    items5('Nach der Untersuchung', 'des Falls', 'traf', 'die Behörde', 'eine Entscheidung'),
    'Предложная группа в начале сдвигает глагол «traf» на второе место перед подлежащим «die Behörde».'),

  ord(28, 'HARD',
    'Соберите немецкое предложение: «Частое употребление существительных является признаком научных текстов».',
    items5('Der häufige Gebrauch von Substantiven', 'ist', 'ein Merkmal', 'wissenschaftlicher', 'Texte'),
    'Прилагательное «wissenschaftlicher» в Genitiv множественного числа без артикля имеет сильное окончание -er.'),

  ord(29, 'HARD',
    'Соберите немецкое предложение: «Несмотря на рост издержек, прибыль выросла».',
    items5('Trotz', 'des Anstiegs', 'der Kosten', 'stieg', 'der Gewinn'),
    'Уступительный предлог «trotz» управляет родительным падежом, а глагол «stieg» стоит на втором месте после вынесенной группы.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY',
    'Wegen ___ Anstiegs der Preise sparen wir.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('der', 'des', 'dem', 'den', 'das'),
    'После предлога «wegen» существительное мужского рода «Anstieg» стоит в Genitiv с артиклем «des».'),

  fb(31, 'EASY',
    '___ Untersuchung des Falls dauerte lange.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('Der', 'Den', 'Die', 'Das', 'Dem'),
    'Существительное «Untersuchung» женского рода, поэтому в именительном падеже артикль «Die».'),

  fb(32, 'EASY',
    'Nach ___ Prüfung trifft die Behörde eine Entscheidung.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('die', 'das', 'den', 'der', 'dem'),
    'Предлог «nach» требует Dativ, а слово «Prüfung» женского рода даёт артикль «der».'),

  fb(33, 'EASY',
    'Der Nominalstil verwendet viele ___ .',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('Substantive', 'substantive', 'Substantiven', 'Substantiv', 'substantiv'),
    'Существительные в немецком пишутся с большой буквы, а множественное число к «Substantiv» — «Substantive».'),

  fb(34, 'EASY',
    'Die Erhöhung ___ Kosten senkt den Gewinn.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('die', 'das', 'dem', 'den', 'der'),
    'Определение в родительном падеже множественного числа «der Kosten» образует цепочку генитива.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD',
    'Infolge des ___ des Projekts wurde der Plan geändert.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('Scheitern', 'Scheiterns Plan', 'Scheiterns', 'scheitern', 'Scheiterung'),
    'Субстантивированный инфинитив «das Scheitern» в родительном падеже получает окончание -s: «des Scheiterns».'),

  fb(36, 'HARD',
    'Wegen des starken ___ der Preise müssen wir sparen.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('Anstieg', 'Anstiege', 'Anstiegen', 'Anstiegs', 'Anstiegung'),
    'В родительном падеже мужского рода существительное «Anstieg» получает окончание -s: «des Anstiegs».'),

  fb(37, 'HARD',
    'Der Gebrauch ___ Substantive ist ein Merkmal wissenschaftlicher Texte.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('den', 'der', 'die', 'dem', 'das'),
    'Определение в родительном падеже множественного числа «der Substantive» примыкает к «der Gebrauch».'),

  fb(38, 'HARD',
    'Trotz ___ schlechten Wetters fand das Spiel statt.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('des', 'dem', 'der', 'das', 'den'),
    'Предлог «trotz» управляет родительным падежом, а «Wetter» среднего рода даёт артикль «des».'),

  fb(39, 'HARD',
    'Die Erhöhung der ___ führte zu einem Rückgang des Gewinns.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('Produktionskost', 'Produktionskoste', 'Produktionskostens', 'produktionskosten', 'Produktionskosten'),
    'Слово «Kosten» употребляется только во множественном числе, поэтому в Genitiv форма «Produktionskosten» с большой буквы.'),
];
