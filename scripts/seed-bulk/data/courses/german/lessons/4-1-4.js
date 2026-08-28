'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Wähle den richtigen Satz: Я остаюсь дома, потому что я болен.',
    op4('Ich bleibe zu Hause, weil ich krank bin.', 'Ich bleibe zu Hause, weil ich bin krank.', 'Ich bleibe zu Hause, weil bin ich krank.', 'Ich bleibe zu Hause, weil krank ich bin.'),
    'a', 'В придаточном с weil спрягаемый глагол bin стоит в самом конце.'),

  sc(1, 'EASY', 'Что значит союз «weil»?',
    op4('что', 'потому что', 'или', 'но'),
    'b', 'weil вводит придаточное причины и переводится «потому что».'),

  sc(2, 'EASY', 'Что значит союз «dass»?',
    op4('что (союз)', 'когда', 'чтобы не', 'хотя'),
    'a', 'dass вводит дополнительное придаточное и переводится «что».'),

  sc(3, 'EASY', 'Wähle den richtigen Satz: Я думаю, что он прав.',
    op4('Ich glaube, dass hat er recht.', 'Ich glaube, dass er hat recht.', 'Ich glaube, dass er recht hat.', 'Ich glaube, dass recht er hat.'),
    'c', 'В придаточном с dass спрягаемый глагол hat уходит в конец предложения.'),

  sc(4, 'EASY', 'Welches Wort fehlt? Ich komme nicht, ___ ich krank bin.',
    op4('und', 'weil', 'oder', 'aber'),
    'b', 'Причину «потому что» вводит союз weil.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wähle den korrekten Satz: Он не может уснуть, потому что выпил слишком много кофе.',
    op4('Er kann nicht schlafen, weil er hat zu viel Kaffee getrunken.', 'Er kann nicht schlafen, weil er zu viel Kaffee getrunken hat.', 'Er kann nicht schlafen, weil er zu viel Kaffee hat getrunken.', 'Er kann nicht schlafen, weil zu viel Kaffee er getrunken hat.'),
    'b', 'В Perfekt в придаточном спрягаемое hat стоит в самом конце, после Partizip getrunken.'),

  sc(6, 'HARD', 'Wähle den korrekten Satz: Я звоню, потому что мне нужно работать.',
    op4('Ich rufe an, weil ich muss arbeiten.', 'Ich rufe an, weil arbeiten ich muss.', 'Ich rufe an, weil ich arbeiten muss.', 'Ich rufe an, weil ich muss arbeiten muss.'),
    'c', 'С модальным глаголом в придаточном инфинитив arbeiten стоит перед спрягаемым muss в конце.'),

  sc(7, 'HARD', 'Придаточное стоит первым. Wähle den korrekten Satz: Так как погода была плохой, мы остались дома.',
    op4('Weil das Wetter schlecht war, wir blieben zu Hause.', 'Weil das Wetter schlecht war, blieben wir zu Hause.', 'Weil das Wetter war schlecht, blieben wir zu Hause.', 'Weil war das Wetter schlecht, wir blieben zu Hause.'),
    'b', 'После придаточного на первом месте в главном сразу идёт глагол blieben (правило V2).'),

  sc(8, 'HARD', 'Wähle den korrekten Satz mit trennbarem Verb: Я рад, потому что поезд прибывает вовремя.',
    op4('Ich freue mich, weil der Zug pünktlich an kommt.', 'Ich freue mich, weil der Zug ankommt pünktlich.', 'Ich freue mich, weil an der Zug pünktlich kommt.', 'Ich freue mich, weil der Zug pünktlich ankommt.'),
    'd', 'В придаточном отделяемая приставка снова срастается с глаголом: ankommt в конце.'),

  sc(9, 'HARD', 'Какой союз сохраняет ОБЫЧНЫЙ порядок слов (глагол на втором месте, не в конце)?',
    op4('weil', 'dass', 'denn', 'damit'),
    'c', 'denn («так как») — сочинительный союз, после него обычный порядок: Ich bleibe, denn ich bin krank.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Sätze sind korrekt? (Глагол в конце придаточного)',
    op5('Ich bleibe, weil ich müde bin.', 'Ich bleibe, weil ich bin müde.', 'Sie sagt, dass sie kommt.', 'Sie sagt, dass kommt sie.', 'Wir warten, weil es regnet.'),
    ['a', 'c', 'e'], 'В придаточных с weil/dass спрягаемый глагол всегда стоит в конце.'),

  mc(11, 'EASY', 'Welche Wörter sind Konjunktionen für Nebensätze (глагол в конец)?',
    op5('weil', 'und', 'dass', 'wenn', 'oder'),
    ['a', 'c', 'd'], 'weil, dass и wenn вводят придаточные и отправляют глагол в конец.'),

  mc(12, 'EASY', 'In welchen Sätzen steht das Verb richtig am Ende?',
    op6('Er denkt, dass es wichtig ist.', 'Er denkt, dass es ist wichtig.', 'Ich hoffe, dass du kommst.', 'Ich hoffe, dass kommst du.', 'Wir wissen, dass er recht hat.', 'Wir wissen, dass hat er recht.'),
    ['a', 'c', 'e'], 'В придаточном с dass спрягаемый глагол ist/kommst/hat стоит в самом конце.'),

  mc(13, 'EASY', 'Welche Sätze brauchen ein Komma vor weil/dass? (запятая обязательна)',
    op5('Ich glaube, dass er kommt.', 'Ich bleibe, weil ich krank bin.', 'Sie hofft, dass es klappt.', 'Ich gehe und du bleibst.', 'Er liest oder schläft.'),
    ['a', 'b', 'c'], 'Перед союзами weil и dass всегда ставится запятая.'),

  mc(14, 'EASY', 'Welche Verben passen ans Ende: «Ich denke, dass das ___.»',
    op5('wichtig ist', 'gut ist', 'ist gut', 'richtig ist', 'ist wichtig'),
    ['a', 'b', 'd'], 'В придаточном спрягаемый глагол ist стоит последним словом.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Nebensätze sind grammatisch korrekt?',
    op6('..., weil ich arbeiten muss.', '..., weil ich muss arbeiten.', '..., weil der Zug ankommt.', '..., weil der Zug an kommt.', '..., weil er Kaffee getrunken hat.', '..., weil er hat Kaffee getrunken.'),
    ['a', 'c', 'e'], 'С модальными инфинитив перед спрягаемым; приставка срастается; в Perfekt hat стоит последним.'),

  mc(16, 'HARD', 'Welche Sätze sind korrekt, wenn der Nebensatz ZUERST steht?',
    op6('Weil ich krank bin, bleibe ich zu Hause.', 'Weil ich krank bin, ich bleibe zu Hause.', 'Wenn es regnet, bleiben wir hier.', 'Wenn es regnet, wir bleiben hier.', 'Weil es spät war, gingen wir.', 'Weil es spät war, wir gingen.'),
    ['a', 'c', 'e'], 'Если придаточное первое, в главном сразу идёт глагол (V2): bleibe/bleiben/gingen.'),

  mc(17, 'HARD', 'Welche Sätze verwenden die Konjunktion korrekt (Wortstellung)?',
    op6('Ich bleibe, denn ich bin krank.', 'Ich bleibe, denn ich krank bin.', 'Ich bleibe, weil ich krank bin.', 'Ich bleibe, weil ich bin krank.', 'Er kommt, dass er Zeit hat.', 'Er sagt, dass er Zeit hat.'),
    ['a', 'c', 'f'], 'После denn обычный порядок, после weil/dass глагол в конце; dass нельзя после «er kommt».'),

  mc(18, 'HARD', 'Welche Sätze mit Modalverb im Nebensatz sind korrekt?',
    op6('Ich gehe, weil ich einkaufen muss.', 'Ich gehe, weil ich muss einkaufen.', 'Er bleibt, weil er lernen will.', 'Er bleibt, weil er will lernen.', 'Sie ruft an, weil sie fragen möchte.', 'Sie ruft an, weil sie möchte fragen.'),
    ['a', 'c', 'e'], 'В придаточном модальный глагол muss/will/möchte стоит последним, инфинитив перед ним.'),

  mc(19, 'HARD', 'Welche Sätze drücken einen Grund korrekt aus («причина»)?',
    op6('Ich komme nicht, weil ich keine Zeit habe.', 'Ich komme nicht, weil ich habe keine Zeit.', 'Ich komme nicht, denn ich habe keine Zeit.', 'Ich komme nicht, denn ich keine Zeit habe.', 'Der Grund ist, dass ich keine Zeit habe.', 'Der Grund ist, dass ich habe keine Zeit.'),
    ['a', 'c', 'e'], 'weil/dass отправляют habe в конец, а denn сохраняет обычный порядок.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я остаюсь дома, потому что я болен.» (вторая часть)',
    items4('weil', 'ich', 'krank', 'bin'),
    'В придаточном с weil спрягаемый глагол bin стоит в конце.'),

  ord(21, 'EASY', 'Соберите немецкое предложение: «Я думаю, что он прав.» (вторая часть)',
    items4('dass', 'er', 'recht', 'hat'),
    'В придаточном с dass спрягаемый глагол hat стоит последним.'),

  ord(22, 'EASY', 'Соберите немецкое предложение: «Она говорит, что она придёт.»',
    items5('Sie', 'sagt,', 'dass', 'sie', 'kommt'),
    'После dass глагол kommt уходит в самый конец придаточного.'),

  ord(23, 'EASY', 'Соберите немецкое предложение: «Я надеюсь, что ты придёшь.»',
    items5('Ich', 'hoffe,', 'dass', 'du', 'kommst'),
    'В придаточном с dass спрягаемый глагол kommst стоит в конце.'),

  ord(24, 'EASY', 'Соберите немецкое предложение: «Мы ждём, потому что идёт дождь.»',
    items5('Wir', 'warten,', 'weil', 'es', 'regnet'),
    'После weil спрягаемый глагол regnet стоит в конце придаточного.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Потому что мне нужно работать.» (модальный глагол)',
    items4('weil', 'ich', 'arbeiten', 'muss'),
    'С модальным глаголом в придаточном инфинитив arbeiten стоит перед muss в конце.'),

  ord(26, 'HARD', 'Соберите немецкое предложение: «Так как я болен, я остаюсь дома.» (придаточное первым)',
    items5('Weil', 'ich krank bin,', 'bleibe', 'ich', 'zu Hause'),
    'Когда придаточное стоит первым, в главном сразу идёт глагол bleibe (правило V2): ..., bleibe ich zu Hause.'),

  ord(27, 'HARD', 'Соберите немецкое предложение: «Я рад, что поезд прибывает.» (отделяемая приставка)',
    items5('Ich', 'freue mich,', 'dass', 'der Zug', 'ankommt'),
    'В придаточном приставка срастается с глаголом: ankommt в конце.'),

  ord(28, 'HARD', 'Соберите немецкое предложение: «Он не может уснуть, потому что выпил слишком много кофе.» (вторая часть, Perfekt)',
    items5('weil', 'er', 'zu viel Kaffee', 'getrunken', 'hat'),
    'В Perfekt спрягаемое hat стоит последним, после Partizip getrunken.'),

  ord(29, 'HARD', 'Соберите немецкое предложение: «Я знаю, что это важно.»',
    items5('Ich', 'weiß,', 'dass', 'das', 'wichtig ist'),
    'В придаточном с dass спрягаемый глагол ist стоит в самом конце.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich bleibe zu Hause, ___ ich krank bin.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('weil', 'und', 'oder', 'aber', 'dass'),
    'Причину «потому что» вводит союз weil.'),

  fb(31, 'EASY', 'Ich glaube, ___ er recht hat.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('aber', 'dass', 'und', 'oder', 'denn'),
    'Содержание мысли вводит союз dass («что»).'),

  fb(32, 'EASY', 'Sie sagt, dass sie morgen einen Termin ___.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('haben', 'habt', 'hat', 'hast', 'habe'),
    'Для sie (она) форма глагола haben — hat, и она стоит в конце придаточного.'),

  fb(33, 'EASY', 'Wir warten, ___ es regnet.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('und', 'oder', 'aber', 'weil', 'dass'),
    'Причину «потому что» вводит союз weil.'),

  fb(34, 'EASY', 'Ich hoffe, dass du morgen ___.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('kommt', 'kommen', 'komme', 'kommst gern', 'kommst'),
    'Для du форма глагола kommen — kommst, и она стоит в конце придаточного.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich rufe an, weil ich heute arbeiten ___.',
    [{ id: 'b1', correctCandidateId: 'c1' }],
    cand5('muss', 'musst', 'müssen', 'will arbeiten', 'arbeite'),
    'С модальным глаголом в придаточном muss стоит последним, после инфинитива arbeiten.'),

  fb(36, 'HARD', 'Er kann nicht schlafen, weil er zu viel Kaffee getrunken ___.',
    [{ id: 'b1', correctCandidateId: 'c2' }],
    cand5('ist', 'hat', 'haben', 'hatte getrunken', 'wird'),
    'В Perfekt спрягаемое hat стоит в самом конце придаточного, после Partizip getrunken.'),

  fb(37, 'HARD', 'Ich freue mich, weil der Zug pünktlich ___.',
    [{ id: 'b1', correctCandidateId: 'c3' }],
    cand5('an kommt', 'kommt an', 'ankommt', 'ankommen', 'an gekommen'),
    'В придаточном отделяемая приставка срастается с глаголом: ankommt в конце.'),

  fb(38, 'HARD', '___ das Wetter schlecht war, blieben wir zu Hause.',
    [{ id: 'b1', correctCandidateId: 'c4' }],
    cand5('Denn', 'Und', 'Dass', 'Weil', 'Oder'),
    'Придаточное причины в начале вводит Weil, а после него в главном сразу идёт глагол.'),

  fb(39, 'HARD', 'Ich bleibe zu Hause, ___ ich bin krank.',
    [{ id: 'b1', correctCandidateId: 'c5' }],
    cand5('weil', 'dass', 'wenn', 'damit', 'denn'),
    'Только denn сохраняет обычный порядок слов (ich bin krank), остальные союзы отправили бы глагол в конец.'),
];
