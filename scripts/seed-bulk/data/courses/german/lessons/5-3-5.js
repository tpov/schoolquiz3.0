'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wie sagt man höflich «я бы сказал, что …»?',
    op4('Ich würde sagen, dass …', 'Ich sage, dass …', 'Ich werde sagen, dass …', 'Ich sagte, dass …'),
    'a',
    'Конъюнктив II «würde + инфинитив» смягчает высказывание до вежливого «я бы сказал».'),

  sc(1, 'EASY', 'Welche Form ist der höfliche Konjunktiv II von «können» (1. Person Singular)?',
    op4('kann', 'könnte', 'konnte', 'gekonnt'),
    'b',
    'Сильная форма Konjunktiv II от «können» — «könnte» (я бы мог / не могли бы вы).'),

  sc(2, 'EASY', 'Wie schlägt man höflich einen Kompromiss vor?',
    op4('Wir finden einen Kompromiss.', 'Findet einen Kompromiss!', 'Könnten wir nicht einen Kompromiss finden?', 'Wir fanden einen Kompromiss.'),
    'c',
    'Вопрос с «Könnten wir nicht …?» — мягкое и дипломатичное предложение найти компромисс.'),

  sc(3, 'EASY', 'Welche Form ist der Konjunktiv II von «sein» (1./3. Person Singular)?',
    op4('war', 'bin', 'gewesen', 'wäre'),
    'd',
    'Konjunktiv II от «sein» — «wäre» (было бы), как в «Es wäre besser».'),

  sc(4, 'EASY', 'Wie bittet man höflich ums Wort?',
    op4('Ich hake ein.', 'Dürfte ich kurz einhaken?', 'Hak ein!', 'Ich hakte ein.'),
    'b',
    'Форма «Dürfte ich …?» (Konjunktiv II от «dürfen») — вежливая просьба коротко вклиниться.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welcher Satz drückt einen diplomatischen Rat im Konjunktiv II korrekt aus?',
    op4('An deiner Stelle spreche ich erst mit dem Chef.', 'An deine Stelle würde ich erst mit dem Chef sprechen.', 'An deiner Stelle würde ich erst mit dem Chef sprechen.', 'An deiner Stelle ich würde erst mit dem Chef sprechen.'),
    'c',
    'Оборот «An deiner Stelle würde ich …» требует Dativ (deiner) и V2-порядка с «würde» на втором месте.'),

  sc(6, 'HARD', 'Welcher Konditionalsatz ist grammatisch korrekt?',
    op4('Es wäre besser, wenn wir alle Argumente abwägen würden.', 'Es wäre besser, wenn wir alle Argumente abwägen werden.', 'Es wäre besser, wenn wir abwägen würden alle Argumente.', 'Es wäre besser, wenn wir alle Argumente abwägen.'),
    'a',
    'В придаточном с «wenn» глагол «würden» стоит в самом конце, согласуясь с «wäre» в главном.'),

  sc(7, 'HARD', 'Welche Form ist der Konjunktiv II von «müssen» (3. Person Singular)?',
    op4('musste', 'muss', 'gemusst', 'müsste'),
    'd',
    'Konjunktiv II от «müssen» — «müsste» с умлаутом, выражает смягчённую необходимость «следовало бы».'),

  sc(8, 'HARD', 'Wie formuliert man eine Kritik diplomatisch?',
    op4('Dieser Punkt ist falsch.', 'Ich würde diesen Punkt gern noch ergänzen.', 'Ergänze diesen Punkt!', 'Ich ergänze diesen Punkt sofort.'),
    'b',
    'Формула «Ich würde … gern ergänzen» подаёт возражение как дополнение, а не как резкую критику.'),

  sc(9, 'HARD', 'Welcher Satz ist die höflichste Variante eines Vorschlags?',
    op4('Man sollte vielleicht noch einmal darüber nachdenken.', 'Du musst noch einmal darüber nachdenken.', 'Denk noch einmal darüber nach!', 'Wir denken jetzt darüber nach.'),
    'a',
    'Konjunktiv II «sollte» плюс «vielleicht» и безличное «man» делают совет максимально необязывающим.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Sätze sind höfliche Vorschläge im Konjunktiv II? (mehrere richtig)',
    op5('Könnten wir nicht einen Kompromiss finden?', 'Ich würde vorschlagen, kurz Pause zu machen.', 'Mach jetzt eine Pause!', 'Wir machen jetzt Pause.', 'Findet einen Kompromiss!'),
    ['a', 'b'],
    'Вежливые предложения строятся через «Könnten wir …?» и «Ich würde vorschlagen …», а не через императив.'),

  mc(11, 'EASY', 'Welche Formen sind korrekte Konjunktiv-II-Formen? (mehrere richtig)',
    op5('wäre', 'hätte', 'war', 'hat', 'bin'),
    ['a', 'b'],
    'Konjunktiv II от «sein» — «wäre», от «haben» — «hätte»; остальные формы изъявительного наклонения.'),

  mc(12, 'EASY', 'Welche Wörter passen zum Thema «Diskussion und Kompromiss»? (mehrere richtig)',
    op5('der Vorschlag', 'der Kompromiss', 'der Konsens', 'der Bahnhof', 'die Suppe'),
    ['a', 'b', 'c'],
    'Слова der Vorschlag, der Kompromiss и der Konsens относятся к лексике дискуссии и согласия.'),

  mc(13, 'EASY', 'Welche Sätze klingen höflich? (mehrere richtig)',
    op5('Dürfte ich kurz einhaken?', 'Könnten Sie das bitte erklären?', 'Erklär das sofort!', 'Sei still!', 'Das ist Unsinn.'),
    ['a', 'b'],
    'Вопросы с «Dürfte ich …?» и «Könnten Sie …?» в Konjunktiv II звучат вежливо и дипломатично.'),

  mc(14, 'EASY', 'Welche Sätze beginnen mit einer höflichen Meinungseinleitung? (mehrere richtig)',
    op5('Ich würde sagen, dass …', 'Ich würde meinen, dass …', 'Das stimmt nicht!', 'Hör auf!', 'Du irrst dich.'),
    ['a', 'b'],
    'Обороты «Ich würde sagen/meinen, dass …» в Konjunktiv II мягко вводят собственное мнение.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze drücken eine Hypothese oder einen diplomatischen Rat korrekt aus? (mehrere richtig)',
    op6('An Ihrer Stelle würde ich noch abwägen.', 'Es wäre besser, wenn wir einlenken würden.', 'Wir sollten vielleicht einen Konsens suchen.', 'An Ihre Stelle würde ich noch abwägen.', 'Es wäre besser, wenn wir einlenken werden.', 'Wir sollen sofort einlenken!'),
    ['a', 'b', 'c'],
    'Корректны обороты с Dativ «An Ihrer Stelle», «wenn … würden» в конце и смягчающим «sollten vielleicht».'),

  mc(16, 'HARD', 'Welche Sätze sind grammatisch korrekte Konjunktiv-II-Sätze? (mehrere richtig)',
    op6('Ich hätte da einen Vorschlag.', 'Könnten wir das Thema später besprechen?', 'Es müsste eigentlich einfacher gehen.', 'Ich würde hätte einen Vorschlag.', 'Könnten wir besprechen das Thema später?', 'Es müsste eigentlich einfacher zu gehen.'),
    ['a', 'b', 'c'],
    'Корректны «hätte», «Könnten wir … besprechen?» с глаголом в конце и «müsste … gehen» без лишнего «zu».'),

  mc(17, 'HARD', 'Welche Sätze schwächen eine Kritik höflich ab? (mehrere richtig)',
    op6('Ich würde das etwas anders sehen.', 'Da würde ich gern noch etwas ergänzen.', 'Vielleicht könnte man das anders formulieren.', 'Das ist völlig falsch.', 'Du hast keine Ahnung.', 'So ein Quatsch!'),
    ['a', 'b', 'c'],
    'Konjunktiv II («würde anders sehen», «könnte man») подаёт несогласие как мягкое предложение, а не нападение.'),

  mc(18, 'HARD', 'In welchen Sätzen steht der Konjunktiv II korrekt für eine höfliche Bitte? (mehrere richtig)',
    op6('Dürfte ich Sie kurz unterbrechen?', 'Würden Sie das bitte wiederholen?', 'Könnten Sie mir kurz folgen?', 'Darf ich Sie kurz unterbrechen würde?', 'Werden Sie das bitte wiederholen?', 'Können Sie folgen müssten mir?'),
    ['a', 'b', 'c'],
    'Вежливые просьбы строятся через «Dürfte/Würden/Könnten Sie …?» с инфинитивом в конце.'),

  mc(19, 'HARD', 'Welche Sätze leiten letztendlich zu einem Konsens hin? (mehrere richtig)',
    op6('Letztendlich sollten wir alle Argumente abwägen.', 'Ich würde vorschlagen, dass wir einlenken.', 'Vielleicht könnten wir uns in der Mitte treffen.', 'Letztendlich abwägen wir sollten alle Argumente.', 'Ich würde vorschlagen, dass wir einlenken werden müssen sofort.', 'Vielleicht wir könnten uns treffen in der Mitte.'),
    ['a', 'b', 'c'],
    'Корректны V2-порядок после «Letztendlich», «Ich würde vorschlagen, dass …» и «Vielleicht könnten wir …».'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я бы сказал да».',
    items4('Ich', 'würde', 'Ja', 'sagen'),
    'В Konjunktiv II «würde» стоит на втором месте, а инфинитив «sagen» — в конце.'),

  ord(21, 'EASY', 'Соберите немецкое предложение: «Это было бы намного лучше».',
    items4('Das', 'wäre', 'viel', 'besser'),
    'Konjunktiv II «wäre» занимает второе место в утвердительном предложении.'),

  ord(22, 'EASY', 'Соберите немецкий вопрос: «Не могли бы мы немного подождать?».',
    items4('Könnten', 'wir', 'kurz', 'warten'),
    'В вопросе без вопросительного слова спрягаемый «Könnten» выходит на первое место.'),

  ord(23, 'EASY', 'Соберите немецкое предложение: «У меня было бы предложение».',
    items4('Ich', 'hätte', 'einen', 'Vorschlag'),
    'Konjunktiv II «hätte» стоит на втором месте перед прямым дополнением «einen Vorschlag».'),

  ord(24, 'EASY', 'Соберите немецкое предложение: «Нам следовало бы хорошо взвесить».',
    items4('Wir', 'sollten', 'gut', 'abwägen'),
    'Модальный глагол «sollten» в Konjunktiv II стоит на втором месте, инфинитив «abwägen» — в конце.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я бы сначала об этом подумал».',
    items5('Ich', 'würde', 'erst', 'darüber', 'nachdenken'),
    'Konjunktiv II «würde» стоит на втором месте, а инфинитив «nachdenken» — в самом конце.'),

  ord(26, 'HARD', 'Соберите немецкий вопрос: «Не могли бы мы найти компромисс?».',
    items5('Könnten', 'wir', 'einen', 'Kompromiss', 'finden'),
    'Вопрос без вопросительного слова начинается со спрягаемого «Könnten», инфинитив «finden» — в конце.'),

  ord(27, 'HARD', 'Соберите немецкое предложение: «Было бы лучше сейчас уступить».',
    items5('Es', 'wäre', 'besser', 'jetzt', 'einzulenken'),
    'Здесь «Es wäre besser» вводит совет, а инфинитив с «zu» («einzulenken») завершает конструкцию.'),

  ord(28, 'HARD', 'Соберите немецкую просьбу: «Можно мне, может быть, коротко вклиниться?».',
    items5('Dürfte', 'ich', 'vielleicht', 'kurz', 'einhaken'),
    'В вопросе «Dürfte ich …?» отделяемый глагол «einhaken» стоит в инфинитиве в самом конце.'),

  ord(29, 'HARD', 'Соберите немецкое предложение: «Я бы дополнил этот пункт».',
    items5('Ich', 'würde', 'diesen', 'Punkt', 'ergänzen'),
    'Konjunktiv II «würde» на втором месте, аккузатив «diesen Punkt» в середине, инфинитив «ergänzen» в конце.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich ___ sagen, dass das stimmt.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('würde', 'wird', 'wirst', 'werden', 'werdet'),
    'Для смягчённого «я бы сказал» нужен Konjunktiv II «würde» (1-е лицо ед.ч.); остальные формы не согласуются с «ich».'),

  fb(31, 'EASY', 'Das ___ viel besser.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('wärst', 'wäre', 'wären', 'wärt', 'warst'),
    'Konjunktiv II «wäre» (3-е лицо ед.ч.) согласуется с «das»; прочие формы — другого лица.'),

  fb(32, 'EASY', '___ wir kurz warten?',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('Könnte', 'Könntest', 'Könnten', 'Könntet', 'Kannst'),
    'Вежливый вопрос требует Konjunktiv II «Könnten» (мн.ч.), согласованного с «wir».'),

  fb(33, 'EASY', 'Ich ___ da einen Vorschlag.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('hättest', 'hätten', 'hättet', 'hätte', 'hast'),
    'Konjunktiv II «hätte» (1-е лицо ед.ч.) согласуется с «ich»; прочие формы — другого лица.'),

  fb(34, 'EASY', 'Wir ___ vielleicht einlenken.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('sollte', 'solltest', 'solltet', 'sollst', 'sollten'),
    'Konjunktiv II «sollten» (мн.ч.) согласуется с «wir» и даёт необязывающий совет уступить.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'An deiner Stelle ___ ich erst nachdenken.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('würde', 'würdest', 'würden', 'würdet', 'wurde'),
    'После «An deiner Stelle» нужен Konjunktiv II «würde» (1-е лицо ед.ч.) на втором месте; прочие формы не согласуются с «ich».'),

  fb(36, 'HARD', 'Es wäre besser, wenn wir alle Argumente ___ würden.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('abwägt', 'abwägen', 'abwog', 'abgewogen', 'abwägst'),
    'С «würden» в придаточном нужен чистый инфинитив «abwägen», стоящий перед ним.'),

  fb(37, 'HARD', '___ ich kurz einhaken?',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('Dürftest', 'Dürften', 'Dürfte', 'Dürftet', 'Darfst'),
    'Konjunktiv II «Dürfte» (1-е лицо ед.ч.) согласуется с «ich» и делает просьбу о слове особенно вежливой.'),

  fb(38, 'HARD', 'Das ___ eigentlich einfacher gehen.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('müsstest', 'müssten', 'müsste', 'müsstet', 'musst'),
    'Konjunktiv II «müsste» (3-е лицо ед.ч.) согласуется с «das» и выражает осторожное предположение «должно бы».'),

  fb(39, 'HARD', 'Vielleicht ___ man das anders formulieren.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('könntest', 'könnten', 'könntet', 'kannst', 'könnte'),
    'Безличное «man» (3-е лицо ед.ч.) плюс Konjunktiv II «könnte» мягко предлагает иную формулировку.'),
];
