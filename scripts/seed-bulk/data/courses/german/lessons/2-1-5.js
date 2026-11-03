'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Welches Wort bedeutet «откуда»?', op4('woher', 'wann', 'warum', 'wer'), 'a', 'Вопросительное слово «woher» означает «откуда».'),
  sc(1, 'EASY', 'Welches Wort bedeutet «где»?', op4('wann', 'wo', 'wie', 'was'), 'b', 'Вопросительное слово «wo» означает «где».'),
  sc(2, 'EASY', 'Wie heißt die richtige Ja-Nein-Frage?', op4('Du wohnst in Berlin.', 'Wo wohnst du?', 'Wohnst du in Berlin?', 'In Berlin du wohnst.'), 'c', 'В общем вопросе глагол стоит на первом месте: «Wohnst du …?».'),
  sc(3, 'EASY', 'Was bedeutet «wann»?', op4('как', 'когда', 'кто', 'что'), 'b', 'Вопросительное слово «wann» означает «когда».'),
  sc(4, 'EASY', '___ heißt du? – Ich heiße Anna.', op4('Wie', 'Wo', 'Wann', 'Warum'), 'a', 'Вопрос об имени задаётся словом «wie»: «Wie heißt du?».'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Wähle die korrekte W-Frage.', op4('Wo du wohnst?', 'Wohnst wo du?', 'Wo wohnst du?', 'Du wo wohnst?'), 'c', 'В W-Frage порядок: вопросительное слово, потом глагол, потом подлежащее.'),
  sc(6, 'HARD', 'Welche Frage passt zur Antwort «Ich komme aus Italien.»?', op4('Wo kommst du?', 'Woher kommst du?', 'Wann kommst du?', 'Wer kommst du?'), 'b', 'На вопрос об происхождении «откуда» отвечает «woher».'),
  sc(7, 'HARD', 'Wähle die grammatisch korrekte Frage mit «Sie».', op4('Wo wohnen Sie?', 'Wo wohnst Sie?', 'Wo wohne Sie?', 'Wo wohnt Sie?'), 'a', 'С вежливым «Sie» глагол имеет окончание -en: «wohnen Sie».'),
  sc(8, 'HARD', 'Welche Frage passt zur Antwort «Er heißt Thomas.»?', op4('Was heißt er?', 'Wo heißt er?', 'Wie heißt er?', 'Wann heißt er?'), 'c', 'Имя спрашивают вопросом «wie»: «Wie heißt er?».'),
  sc(9, 'HARD', 'Welches Wort bedeutet «почему»?', op4('woher', 'wer', 'warum', 'was'), 'c', 'Вопросительное слово «warum» означает «почему».'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Wörter sind Fragewörter (вопросительные слова)?', op5('wo', 'und', 'wer', 'aus', 'in'), ['a', 'c'], 'Вопросительные слова здесь — «wo» (где) и «wer» (кто).'),
  mc(11, 'EASY', 'Welche Sätze sind richtige Ja-Nein-Fragen?', op5('Wohnst du hier?', 'Du wohnst hier.', 'Lernst du Deutsch?', 'Wo wohnst du?', 'Hier du wohnst?'), ['a', 'c'], 'Общий вопрос начинается с глагола: «Wohnst du …?», «Lernst du …?».'),
  mc(12, 'EASY', 'Welche Wörter bedeuten «когда» oder «как»?', op5('wann', 'wer', 'wie', 'wo', 'warum'), ['a', 'c'], '«wann» — когда, «wie» — как.'),
  mc(13, 'EASY', 'Welche Fragen beginnen mit einem Fragewort (W-Frage)?', op5('Was machst du?', 'Kommst du?', 'Wer ist das?', 'Hast du Zeit?', 'Trinkst du Tee?'), ['a', 'c'], 'W-Frage начинается с вопросительного слова: «Was …?», «Wer …?».'),
  mc(14, 'EASY', 'Welche Antworten passen zu einer Ja-Nein-Frage?', op5('Ja', 'Berlin', 'Nein', 'aus Italien', 'um acht'), ['a', 'c'], 'На общий вопрос отвечают «Ja» или «Nein».'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche W-Fragen haben die korrekte Wortstellung?', op6('Wo wohnst du?', 'Woher du kommst?', 'Wann kommst du?', 'Was du machst?', 'Wer ist das?', 'Wie du heißt?'), ['a', 'c', 'e'], 'Правильный порядок W-Frage: слово + глагол + подлежащее.'),
  mc(16, 'HARD', 'Welche Fragen sind korrekt mit «Sie» konjugiert?', op5('Woher kommen Sie?', 'Wo wohnen Sie?', 'Was macht Sie?', 'Wann kommst Sie?', 'Wie heißt Sie?'), ['a', 'b'], 'С «Sie» глагол оканчивается на -en: «kommen Sie», «wohnen Sie».'),
  mc(17, 'HARD', 'Welche Fragewörter passen zur Antwort «um acht Uhr»?', op5('Wann', 'Wo', 'Um wie viel Uhr', 'Wer', 'Woher'), ['a', 'c'], 'О времени спрашивают «wann» или «um wie viel Uhr».'),
  mc(18, 'HARD', 'Welche Sätze sind grammatisch korrekte Fragen?', op6('Lernt ihr Deutsch?', 'Ihr lernt Deutsch?', 'Was macht er?', 'Macht was er?', 'Trinken Sie Kaffee?', 'Sie trinken Kaffee?'), ['a', 'c', 'e'], 'Вопрос требует инверсии: глагол перед подлежащим или после вопросительного слова.'),
  mc(19, 'HARD', 'Welche Fragen passen zur Antwort «Ich heiße Lena.»?', op5('Wie heißt du?', 'Wer bist du?', 'Wo heißt du?', 'Wann heißt du?', 'Was heißt du?'), ['a', 'b'], 'Об имени спрашивают «Wie heißt du?» или «Wer bist du?».'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкий вопрос: «Где ты живёшь?».', items4('Wo', 'wohnst', 'du', '?'), 'W-Frage: вопросительное слово, глагол, подлежащее.'),
  ord(21, 'EASY', 'Соберите немецкий вопрос: «Ты учишь немецкий?».', items4('Lernst', 'du', 'Deutsch', '?'), 'В общем вопросе глагол стоит на первом месте.'),
  ord(22, 'EASY', 'Соберите немецкий вопрос: «Откуда ты родом?».', items4('Woher', 'kommst', 'du', '?'), '«Woher» спрашивает о происхождении, глагол идёт следом.'),
  ord(23, 'EASY', 'Соберите немецкий вопрос: «Как тебя зовут?».', items4('Wie', 'heißt', 'du', '?'), 'Об имени спрашивают вопросом «Wie heißt du?».'),
  ord(24, 'EASY', 'Соберите немецкий вопрос: «У тебя есть время?».', items4('Hast', 'du', 'Zeit', '?'), 'Глагол «hast» открывает общий вопрос без вопросительного слова.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкий вопрос: «Где Вы живёте?».', items4('Wo', 'wohnen', 'Sie', '?'), 'С вежливым «Sie» глагол имеет окончание -en.'),
  ord(26, 'HARD', 'Соберите немецкий вопрос: «Когда ты приходишь?».', items4('Wann', 'kommst', 'du', '?'), 'О времени спрашивают «wann», глагол стоит на втором месте.'),
  ord(27, 'HARD', 'Соберите немецкий вопрос: «Учите ли вы немецкий?».', items4('Lernt', 'ihr', 'Deutsch', '?'), 'С «ihr» глагол имеет окончание -t и стоит первым в общем вопросе.'),
  ord(28, 'HARD', 'Соберите немецкий вопрос: «Что ты делаешь сегодня?».', items5('Was', 'machst', 'du', 'heute', '?'), 'В W-Frage обстоятельство «heute» стоит после подлежащего.'),
  ord(29, 'HARD', 'Соберите немецкий вопрос: «Откуда же Вы родом?».', items5('Woher', 'kommen', 'Sie', 'denn', '?'), 'Частица «denn» в вопросе стоит после подлежащего.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', '___ wohnst du? – Ich wohne in Köln.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Wo', 'Wer', 'Wann', 'Was', 'Wie'), 'О месте жительства спрашивают вопросом «wo».'),
  fb(31, 'EASY', '___ du Deutsch? – Ja, ich lerne Deutsch.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Wo', 'Lernst', 'Wann', 'Woher', 'Warum'), 'Общий вопрос начинается с глагола «Lernst».'),
  fb(32, 'EASY', '___ kommst du? – Ich komme aus Italien.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('Wann', 'Wer', 'Woher', 'Wie', 'Was'), 'О происхождении спрашивают вопросом «woher».'),
  fb(33, 'EASY', '___ heißt du? – Ich heiße Tom.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Wo', 'Wer', 'Wann', 'Wie', 'Warum'), 'Об имени спрашивают вопросом «wie».'),
  fb(34, 'EASY', '___ ist das? – Das ist Frau Müller.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('Wo', 'Was', 'Wann', 'Wie', 'Wer'), 'О человеке спрашивают вопросом «wer».'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Wo ___ Sie? – Ich wohne in Hamburg.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('wohnen', 'wohnst', 'wohnt', 'wohne', 'wohnest'), 'С «Sie» глагол имеет окончание -en: «wohnen Sie».'),
  fb(36, 'HARD', '___ machst du heute? – Ich lerne Deutsch.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('Wer', 'Was', 'Wo', 'Woher', 'Wann'), 'О действии спрашивают вопросом «was».'),
  fb(37, 'HARD', 'Wann ___ ihr? – Wir kommen um acht.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('kommt', 'kommst', 'kommt ihr', 'kommen', 'komme'), 'С «ihr» глагол имеет окончание -t: «kommt ihr».'),
  fb(38, 'HARD', '___ lernst du Deutsch? – Weil es interessant ist.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Wann', 'Wie', 'Wo', 'Warum', 'Wer'), 'О причине спрашивают вопросом «warum».'),
  fb(39, 'HARD', 'Woher ___ du? – Ich komme aus Polen.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('kommen', 'kommt', 'komme', 'kommst du', 'kommst'), 'С «du» глагол имеет окончание -st: «kommst du».'),
];
