'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY =====
  sc(0, 'EASY', 'Ich ___ in Berlin.', op4('wohne', 'wohnst', 'wohnt', 'wohnen'), 'a', 'Для ich окончание -e: ich wohne.'),
  sc(1, 'EASY', 'Du ___ Deutsch.', op4('lerne', 'lernst', 'lernt', 'lernen'), 'b', 'Для du окончание -st: du lernst.'),
  sc(2, 'EASY', 'Er ___ Fußball.', op4('spiele', 'spielst', 'spielen', 'spielt'), 'd', 'Для er окончание -t: er spielt.'),
  sc(3, 'EASY', 'Sie ___ aus München. (sie = она)', op4('komme', 'kommt', 'kommst', 'kommest'), 'b', 'Для sie (она) окончание -t: sie kommt.'),
  sc(4, 'EASY', 'Was ___ du am Wochenende?', op4('macht', 'machen', 'machst', 'mache'), 'c', 'Для du окончание -st: du machst.'),

  // ===== 5 sc HARD =====
  sc(5, 'HARD', 'Du ___ in einer Bank. (arbeiten)', op4('arbeitst', 'arbeitest', 'arbeitet', 'arbeitste'), 'b', 'После основы на -t вставляется соединительное -e-: du arbeitest.'),
  sc(6, 'HARD', 'Welcher Satz hat das Verb auf Position 2?', op4('In Berlin ich wohne.', 'Wohne ich in Berlin.', 'In Berlin wohne ich.', 'Ich in Berlin wohne.'), 'c', 'В утвердительном предложении глагол всегда на 2-м месте: In Berlin wohne ich.'),
  sc(7, 'HARD', 'Er ___ in Hamburg. (arbeiten)', op4('arbeitt', 'arbeitet', 'arbeitst', 'arbeite'), 'b', 'После основы на -t для er нужно -et: er arbeitet.'),
  sc(8, 'HARD', 'Wie ___ du? – Ich heiße Anna.', op4('heißt', 'heißst', 'heißest', 'heißen'), 'a', 'Основа heiß- уже кончается на -ß, поэтому du heißt (без второго s).'),
  sc(9, 'HARD', 'Welche Form ist richtig?', op4('ich macht', 'du mache', 'er machst', 'es macht'), 'd', 'Для es окончание -t: es macht.'),

  // ===== 5 mc EASY =====
  mc(10, 'EASY', 'Welche Formen passen zu „ich"?', op5('wohne', 'wohnst', 'lerne', 'lernt', 'spiele'), ['a', 'c', 'e'], 'Для ich у слабых глаголов всегда окончание -e.'),
  mc(11, 'EASY', 'Welche Formen passen zu „du"?', op5('machst', 'mache', 'lernst', 'spielt', 'wohnst'), ['a', 'c', 'e'], 'Для du у слабых глаголов окончание -st.'),
  mc(12, 'EASY', 'Welche Formen passen zu „er/sie/es"?', op5('spielt', 'wohnt', 'lerne', 'macht', 'kommst'), ['a', 'b', 'd'], 'Для er/sie/es у слабых глаголов окончание -t.'),
  mc(13, 'EASY', 'Welche Wörter sind Personalpronomen im Singular?', op5('ich', 'wir', 'du', 'er', 'sie (она)'), ['a', 'c', 'd', 'e'], 'ich, du, er, sie, es — местоимения единственного числа.'),
  mc(14, 'EASY', 'Welche Sätze sind richtig?', op5('Ich spiele Tennis.', 'Du lernen Deutsch.', 'Er wohnt hier.', 'Sie macht das.', 'Ich kommst aus Berlin.'), ['a', 'c', 'd'], 'Правильны те предложения, где окончание глагола совпадает с лицом.'),

  // ===== 5 mc HARD =====
  mc(15, 'HARD', 'Welche Verben brauchen ein Binde-e (du -est / er -et)?', op6('arbeiten', 'spielen', 'wohnen', 'finden', 'machen', 'baden'), ['a', 'd', 'f'], 'После основы на -t/-d вставляется соединительное -e- для удобства произношения.'),
  mc(16, 'HARD', 'Welche Sätze haben das Verb korrekt auf Position 2?', op5('Heute lerne ich Deutsch.', 'Ich heute lerne Deutsch.', 'In München wohnt er.', 'Spiele ich Tennis gern.', 'Am Montag arbeitest du.'), ['a', 'c', 'e'], 'В утвердительном предложении спрягаемый глагол стоит вторым.'),
  mc(17, 'HARD', 'Welche Formen sind korrekt?', op6('du arbeitest', 'er arbeitet', 'du findst', 'es findet', 'ich arbeite', 'er arbeitt'), ['a', 'b', 'd', 'e'], 'Основы на -t/-d требуют соединительного -e-: arbeitest, arbeitet, findet.'),
  mc(18, 'HARD', 'Welche Sätze sind grammatisch korrekt?', op5('Wie heißt du?', 'Er heißst Tom.', 'Du arbeitest viel.', 'Ich wohnst in Köln.', 'Sie lernt Englisch.'), ['a', 'c', 'e'], 'Верны предложения с правильным личным окончанием глагола.'),
  mc(19, 'HARD', 'Welche Aussagen über die Konjugation stimmen?', op5('„ich" bekommt -e', '„du" bekommt -t', '„er" bekommt -t', '„es" bekommt -st', '„du" bekommt -st'), ['a', 'c', 'e'], 'ich -e, du -st, er/sie/es -t — базовые окончания слабых глаголов.'),

  // ===== 5 ord EASY =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я живу в Берлине».', items4('Ich', 'wohne', 'in', 'Berlin'), 'Глагол wohne на 2-м месте: Ich wohne in Berlin.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Ты учишь немецкий».', items4('Du', 'lernst', 'Deutsch', '.'), 'Глагол lernst стоит вторым: Du lernst Deutsch.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Он играет в теннис».', items4('Er', 'spielt', 'Tennis', '.'), 'Глагол spielt на 2-м месте: Er spielt Tennis.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Меня зовут Анна».', items4('Ich', 'heiße', 'Anna', '.'), 'Глагол heiße стоит вторым: Ich heiße Anna.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Она приезжает из Мюнхена».', items5('Sie', 'kommt', 'aus', 'München', '.'), 'Глагол kommt на 2-м месте: Sie kommt aus München.'),

  // ===== 5 ord HARD =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Сегодня я учу немецкий».', items5('Heute', 'lerne', 'ich', 'Deutsch', '.'), 'После Heute глагол lerne всё равно вторым, подлежащее после него.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Ты работаешь в банке».', items5('Du', 'arbeitest', 'in', 'einer', 'Bank'), 'Основа на -t даёт du arbeitest; глагол на 2-м месте.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «В Гамбурге он работает».', items5('In', 'Hamburg', 'arbeitet', 'er', '.'), 'Обстоятельство In Hamburg занимает 1-е место, глагол arbeitet — 2-е.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Что ты делаешь сегодня?».', items5('Was', 'machst', 'du', 'heute', '?'), 'В W-вопросе глагол machst стоит вторым после вопросительного слова.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Она охотно играет в теннис».', items5('Sie', 'spielt', 'gern', 'Tennis', '.'), 'Глагол spielt на 2-м месте, gern и Tennis следуют далее.'),

  // ===== 5 fb EASY =====
  fb(30, 'EASY', 'Ich ___ in München.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('wohne', 'wohnst', 'wohnt', 'wohnen', 'wohnet'), 'Для ich окончание -e: ich wohne.'),
  fb(31, 'EASY', 'Du ___ Deutsch.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('lerne', 'lernst', 'lernt', 'lernen', 'lernet'), 'Для du окончание -st: du lernst.'),
  fb(32, 'EASY', 'Er ___ Fußball.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('spiele', 'spielst', 'spielt', 'spielen', 'spielet'), 'Для er окончание -t: er spielt.'),
  fb(33, 'EASY', 'Was ___ du?', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('mache', 'macht', 'machen', 'machst', 'machet'), 'Для du окончание -st: du machst.'),
  fb(34, 'EASY', 'Sie ___ aus Köln.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('komme', 'kommst', 'kommen', 'kommet', 'kommt'), 'Для sie (она) окончание -t: sie kommt.'),

  // ===== 5 fb HARD =====
  fb(35, 'HARD', 'Du ___ in einer Bank.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('arbeitest', 'arbeitst', 'arbeitet', 'arbeite', 'arbeiten'), 'Основа на -t требует -est: du arbeitest.'),
  fb(36, 'HARD', 'Er ___ in Hamburg.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('arbeitst', 'arbeitet', 'arbeite', 'arbeiten', 'arbeitt'), 'Основа на -t требует -et: er arbeitet.'),
  fb(37, 'HARD', 'Wie ___ du?', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('heißst', 'heißest', 'heißt', 'heißen', 'heiße'), 'Основа на -ß даёт du heißt (без второго s).'),
  fb(38, 'HARD', 'Heute ___ ich Deutsch.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('lernst', 'lernt', 'lernen', 'lerne', 'lernest'), 'После Heute глагол на 2-м месте, для ich окончание -e: lerne.'),
  fb(39, 'HARD', 'Es ___ keinen Spaß.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('mache', 'machst', 'machen', 'machet', 'macht'), 'Для es окончание -t: es macht keinen Spaß.'),
];
