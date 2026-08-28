'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Как неформально (на «ты») спросить «Как дела?»', op4('Wie geht es dir?', 'Wie geht es Ihnen?', 'Wer ist das?', 'Was kostet das?'), 'a', 'На «ты» спрашивают Wie geht es dir? с местоимением dir.'),
  sc(1, 'EASY', 'Как ответить «Спасибо, хорошо!»?', op4('Bitte schön.', 'Danke, gut!', 'Guten Morgen!', 'Bis bald!'), 'b', 'Краткий ответ на «как дела» — Danke, gut!'),
  sc(2, 'EASY', 'Что значит «danke»?', op4('пожалуйста', 'привет', 'спасибо', 'пока'), 'c', 'Danke переводится как «спасибо».'),
  sc(3, 'EASY', 'Вежливое местоимение «Вы» по-немецки — это…', op4('du', 'ihr', 'wir', 'Sie'), 'd', 'Вежливое «Вы» — это Sie, всегда с большой буквы.'),
  sc(4, 'EASY', 'Как сказать «очень хорошо»?', op4('sehr gut', 'nicht gut', 'gut bitte', 'gut danke'), 'a', 'Усилитель sehr ставится перед gut: sehr gut.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Вежливо (на «Вы») «Как Ваши дела?» — это…', op4('Wie geht es dir?', 'Wie geht es Ihnen?', 'Wie heißt du?', 'Woher kommst du?'), 'b', 'Вежливая форма — Wie geht es Ihnen? с местоимением Ihnen.'),
  sc(6, 'HARD', 'Закончите вежливо: «Wie heißen ___?»', op4('du', 'ihr', 'Sie', 'er'), 'c', 'С вежливым Sie глагол имеет окончание -en: Wie heißen Sie?'),
  sc(7, 'HARD', 'Как обратиться к госпоже Мюллер?', op4('Herr Müller', 'Frau Müller', 'Frau Herr', 'die Müller'), 'b', 'К женщине обращаются Frau + фамилия: Frau Müller.'),
  sc(8, 'HARD', 'Какое окончание у глагола при вежливом Sie: «Woher kommen ___?»', op4('Sie', 'du', 'ihr', 'es'), 'a', 'При Sie глагол оканчивается на -en: Woher kommen Sie?'),
  sc(9, 'HARD', 'Как ответить нейтрально «Так себе / Нормально»?', op4('Sehr gut, danke.', 'Es geht.', 'Danke schön.', 'Guten Tag.'), 'b', 'Нейтральный ответ на «как дела» — Es geht.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Какие слова означают вежливость (спасибо/пожалуйста)?', op5('danke', 'bitte', 'woher', 'wer', 'was'), ['a', 'b'], 'Danke — спасибо, bitte — пожалуйста.'),
  mc(11, 'EASY', 'Какие ответы подходят на «Wie geht es dir?»', op5('Danke, gut!', 'Es geht.', 'Was kostet das?', 'Wer ist das?', 'Wie viel Uhr?'), ['a', 'b'], 'На «как дела» отвечают Danke, gut! или Es geht.'),
  mc(12, 'EASY', 'Какие формы обращения существуют в немецком?', op5('du', 'ihr', 'Sie', 'der', 'das'), ['a', 'b', 'c'], 'Обращения: du (ты), ihr (вы — друзья), Sie (Вы — вежливо).'),
  mc(13, 'EASY', 'Какие слова значат «хорошо» и «очень»?', op5('gut', 'sehr', 'und', 'Frau', 'Herr'), ['a', 'b'], 'Gut — хорошо, sehr — очень.'),
  mc(14, 'EASY', 'Какие обращения вежливые (к мужчине/женщине)?', op5('der Herr', 'die Frau', 'du', 'ihr', 'es'), ['a', 'b'], 'Вежливые обращения — der Herr (господин) и die Frau (госпожа).'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Какие фразы — неформальные (на «ты»)?', op6('Wie geht es dir?', 'Und dir?', 'Wie geht es Ihnen?', 'Wie heißen Sie?', 'Woher kommen Sie?', 'Wer sind Sie?'), ['a', 'b'], 'Неформальны фразы с dir: Wie geht es dir? и Und dir?'),
  mc(16, 'HARD', 'Какие фразы — вежливые (на «Вы», с Sie)?', op6('Wie geht es Ihnen?', 'Wie heißen Sie?', 'Woher kommen Sie?', 'Wie geht es dir?', 'Und dir?', 'Wie heißt du?'), ['a', 'b', 'c'], 'Вежливы фразы с Sie/Ihnen: Wie geht es Ihnen?, Wie heißen Sie?, Woher kommen Sie?'),
  mc(17, 'HARD', 'В каких фразах глагол стоит в форме на -en (для Sie)?', op5('heißen Sie', 'kommen Sie', 'heißt du', 'kommst du', 'bist du'), ['a', 'b'], 'При Sie глагол на -en: heißen Sie, kommen Sie.'),
  mc(18, 'HARD', 'Какие ответы означают «всё хорошо / очень хорошо»?', op6('Danke, gut!', 'Mir geht es sehr gut.', 'Nicht so gut.', 'Es geht.', 'Wer ist das?', 'Wie spät?'), ['a', 'b'], 'Положительные ответы: Danke, gut! и Mir geht es sehr gut.'),
  mc(19, 'HARD', 'Какие слова пишутся с большой буквы по правилам?', op6('Sie (вежливое)', 'Frau', 'Herr', 'gut', 'danke', 'bitte'), ['a', 'b', 'c'], 'С большой буквы: вежливое Sie и существительные Frau, Herr.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Как у тебя дела?».', items4('Wie', 'geht', 'es', 'dir'), 'Порядок: Wie geht es dir? — неформальный вопрос «как дела».'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Как Ваши дела?».', items4('Wie', 'geht', 'es', 'Ihnen'), 'Порядок: Wie geht es Ihnen? — вежливый вопрос «как дела».'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Спасибо, хорошо, а у тебя?».', items4('Danke', 'gut', 'und', 'dir'), 'Порядок: Danke, gut, und dir? — ответ и встречный вопрос.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Как Вас зовут, пожалуйста?».', items4('Wie', 'heißen', 'Sie', 'bitte'), 'Порядок: Wie heißen Sie, bitte? — вежливый вопрос об имени.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Откуда же Вы?».', items4('Woher', 'kommen', 'Sie', 'denn'), 'Порядок: Woher kommen Sie denn? — вежливый вопрос «откуда» (denn ≈ «же»).'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «У меня всё очень хорошо.».', items5('Mir', 'geht', 'es', 'sehr', 'gut'), 'Порядок: Mir geht es sehr gut — «у меня всё очень хорошо».'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Как Ваши дела, госпожа Мюллер?».', items5('Wie', 'geht', 'es', 'Ihnen', 'Frau Müller'), 'Порядок: Wie geht es Ihnen, Frau Müller? — вежливое обращение по фамилии.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Меня зовут Томас Беккер, спасибо.».', items5('Ich', 'heiße', 'Thomas', 'Becker', 'danke'), 'Порядок: Ich heiße Thomas Becker, danke — вежливое представление по имени.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «У меня всё хорошо, спасибо.».', items5('Mir', 'geht', 'es', 'gut', 'danke'), 'Порядок: Mir geht es gut, danke — ответ «у меня всё хорошо, спасибо».'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Очень хорошо, спасибо, господин Беккер!».', items5('Sehr', 'gut', 'danke', 'Herr', 'Becker'), 'Порядок: Sehr gut, danke, Herr Becker — вежливый ответ с обращением по фамилии.'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Wie geht es ___? (неформально, на «ты»)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('dir', 'du', 'Sie', 'was', 'wir'), 'В дательном падеже от du — dir: Wie geht es dir?'),
  fb(31, 'EASY', 'Wie geht es ___? (вежливо, на «Вы»)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Ihnen', 'Sie', 'du', 'wir', 'es'), 'Вежливая форма в дательном падеже — Ihnen: Wie geht es Ihnen?'),
  fb(32, 'EASY', 'Mir geht es sehr ___. (очень хорошо)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('danke', 'bitte', 'gut', 'und', 'Sie'), 'Sehr gut — очень хорошо.'),
  fb(33, 'EASY', '___, gut! (ответ на «как дела»)', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('Bitte', 'Sehr', 'Und', 'Danke', 'Wie'), 'Ответ — Danke, gut!'),
  fb(34, 'EASY', 'Wie heißen ___? (вежливо, на «Вы»)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('du', 'ihr', 'dir', 'es', 'Sie'), 'Вежливо — Wie heißen Sie?'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Woher kommen ___? (вежливый вопрос «откуда Вы»)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('Sie', 'du', 'ihr', 'dir', 'es'), 'Вежливо — Woher kommen Sie?'),
  fb(36, 'HARD', 'Woher ___ Sie denn? (вежливый вопрос «откуда Вы»)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('kommen', 'kommst', 'kommt', 'komme', 'kam'), 'При вежливом Sie глагол оканчивается на -en: Woher kommen Sie?'),
  fb(37, 'HARD', 'Es geht, ___! (спасибо)', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('bitte', 'gut', 'danke', 'sehr', 'und'), 'Danke — спасибо в конце ответа.'),
  fb(38, 'HARD', 'Mir geht es gut, danke. Und wie geht es ___? (а тебе? на «ты»)', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('dir', 'du', 'Sie', 'was', 'wir'), 'Встречный вопрос на «ты» в дательном падеже — Und wie geht es dir?'),
  fb(39, 'HARD', 'Wie heißen Sie, ___? (вежливо «пожалуйста»)', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('danke', 'sehr', 'gut', 'und', 'bitte'), 'Bitte — «пожалуйста» в вежливом вопросе.'),
];
