'use strict';
const { sc, mc, ord, fb, op4, op5, op6, items4, items5, cand5 } = require('../_helpers');

module.exports = [
  // ===== 5 sc EASY (idx 0..4) =====
  sc(0, 'EASY', 'Ich esse ___ Apfel.', op4('einen', 'eine', 'ein', 'einem'), 'a',
    'Apfel мужского рода, и в Akkusativ ein меняется на einen.'),
  sc(1, 'EASY', 'Wir kaufen ___ Brot.', op4('einen', 'ein', 'eine', 'einem'), 'b',
    'Brot среднего рода (das), и в Akkusativ ein не меняется.'),
  sc(2, 'EASY', 'Ich trinke ___ Milch.', op4('einen', 'einem', 'eine', 'ein'), 'c',
    'Milch женского рода (die), и в Akkusativ артикль eine не меняется.'),
  sc(3, 'EASY', 'Er nimmt ___ Kaffee.', op4('ein', 'eine', 'einem', 'einen'), 'd',
    'Kaffee мужского рода (der), поэтому в Akkusativ ein превращается в einen.'),
  sc(4, 'EASY', 'Welcher Satz ist richtig?', op4('Ich kaufe einen Apfel.', 'Ich kaufe ein Apfel.', 'Ich kaufe eine Apfel.', 'Ich kaufe einem Apfel.'), 'a',
    'Apfel мужского рода, поэтому в Akkusativ нужен артикль einen.'),

  // ===== 5 sc HARD (idx 5..9) =====
  sc(5, 'HARD', 'Welche Frage passt zum Akkusativ?', op4('Wem?', 'Wer?', 'Wen oder was?', 'Wessen?'), 'c',
    'Akkusativ отвечает на вопрос «wen/was?» (кого/что?).'),
  sc(6, 'HARD', 'Ich möchte ___ Kaffee und ___ Brot, bitte.', op4('einen … ein', 'ein … einen', 'einem … ein', 'einen … einen'), 'a',
    'Kaffee мужского рода (einen в Akkusativ), а Brot среднего рода (ein без изменения).'),
  sc(7, 'HARD', 'Welche Form ist falsch in „Ich nehme ___ Apfel“?', op4('den Apfel', 'einen Apfel', 'einem Apfel', 'keinen Apfel'), 'c',
    'einem — это Dativ, а после nehmen в значении «брать» нужен Akkusativ (den/einen/keinen).'),
  sc(8, 'HARD', 'Wie heißt „хотел бы“ für „du“?', op4('möchten', 'möchte', 'möchtet', 'möchtest'), 'd',
    'Глагол möchten для du имеет форму du möchtest.'),
  sc(9, 'HARD', 'Welcher Satz hat die richtige Wortstellung?', op4('Einen Apfel ich kaufe.', 'Ich einen Apfel kaufe.', 'Ich kaufe einen Apfel.', 'Kaufe ich einen Apfel einen.'), 'c',
    'В утвердительном предложении глагол стоит на 2-м месте, а дополнение в Akkusativ — после него.'),

  // ===== 5 mc EASY (idx 10..14) =====
  mc(10, 'EASY', 'Welche Sätze sind im Akkusativ richtig?', op5('Ich esse einen Apfel.', 'Ich trinke einen Milch.', 'Wir kaufen ein Brot.', 'Ich nehme einem Apfel.', 'Ich möchte ein Wasser.'), ['a', 'c', 'e'],
    'Apfel требует einen, Brot и Wasser среднего рода (ein); einen Milch (Milch женского рода → eine) и einem Apfel (Dativ) неверны.'),
  mc(11, 'EASY', 'Welche Wörter sind Verben mit Akkusativ aus dieser Lektion?', op5('essen', 'der Apfel', 'trinken', 'kaufen', 'die Milch'), ['a', 'c', 'd'],
    'essen, trinken и kaufen — глаголы, а der Apfel и die Milch — существительные.'),
  mc(12, 'EASY', 'Bei welchen Wörtern ändert sich „ein“ im Akkusativ zu „einen“?', op5('der Apfel', 'das Brot', 'der Kaffee', 'die Milch', 'das Wasser'), ['a', 'c'],
    'Артикль меняется на einen только у мужского рода: der Apfel и der Kaffee.'),
  mc(13, 'EASY', 'Welche Formen von „möchten“ sind korrekt?', op5('ich möchte', 'du möchten', 'wir möchten', 'er möchtest', 'sie möchten'), ['a', 'c', 'e'],
    'Правильно: ich möchte, wir möchten, sie möchten; остальные формы спутаны.'),
  mc(14, 'EASY', 'Welche Sätze sind grammatisch richtig?', op5('Ich kaufe eine Milch.', 'Er isst einen Apfel.', 'Wir trinken ein Wasser.', 'Ich nehme eine Brot.', 'Sie kauft ein Apfel.'), ['a', 'b', 'c'],
    'Milch (eine), Apfel (einen) и Wasser (ein) верны, а Brot среднего рода и Apfel мужского рода в последних двух стоят с неверным артиклем.'),

  // ===== 5 mc HARD (idx 15..19) =====
  mc(15, 'HARD', 'Welche Sätze stehen korrekt im Akkusativ?', op6('Ich möchte einen Kaffee.', 'Er nimmt den Apfel.', 'Wir kaufen einen Brot.', 'Ich esse ein Apfel.', 'Sie trinkt eine Milch.', 'Ich möchte einem Wasser.'), ['a', 'b', 'e'],
    'Kaffee и Apfel мужского рода (einen/den), Milch женского (eine), а Brot/Apfel/Wasser в остальных вариантах стоят с неверным артиклем.'),
  mc(16, 'HARD', 'In welchen Sätzen ist der Artikel im Akkusativ unverändert (wie im Nominativ)?', op6('Ich kaufe die Milch.', 'Ich nehme den Apfel.', 'Ich esse das Brot.', 'Ich möchte den Kaffee.', 'Ich trinke das Wasser.', 'Ich nehme die Milch.'), ['a', 'c', 'e', 'f'],
    'Женский (die), средний (das) и множественное в Akkusativ не меняются, а мужской der становится den.'),
  mc(17, 'HARD', 'Welche Verben verlangen in dieser Lektion ein Objekt im Akkusativ?', op6('essen', 'sein', 'kaufen', 'nehmen', 'möchten', 'wohnen'), ['a', 'c', 'd', 'e'],
    'essen, kaufen, nehmen и möchten берут прямое дополнение в Akkusativ, а sein и wohnen — нет.'),
  mc(18, 'HARD', 'Welche „möchten“-Formen passen korrekt zum Pronomen?', op6('ich möchte', 'du möchtest', 'er möchten', 'ihr möchtet', 'wir möchtest', 'sie möchten'), ['a', 'b', 'd', 'f'],
    'Правильны ich möchte, du möchtest, ihr möchtet и sie möchten; формы er möchten и wir möchtest неверны.'),
  mc(19, 'HARD', 'Welche Sätze haben die richtige Wortstellung (Verb an Position 2)?', op6('Ich kaufe einen Apfel.', 'Einen Apfel kaufe ich.', 'Ich einen Apfel kaufe.', 'Heute esse ich ein Brot.', 'Esse ich heute ein Brot.', 'Wir möchten einen Kaffee.'), ['a', 'b', 'd', 'f'],
    'Глагол стоит на 2-м месте в этих утверждениях, а в неверных вариантах он сдвинут с позиции 2.'),

  // ===== 5 ord EASY (idx 20..24) =====
  ord(20, 'EASY', 'Соберите немецкое предложение: «Я ем яблоко».', items4('Ich', 'esse', 'einen', 'Apfel'), 'Подлежащее, глагол на 2-м месте и дополнение Apfel в Akkusativ с einen.'),
  ord(21, 'EASY', 'Соберите немецкое предложение: «Мы покупаем хлеб».', items4('Wir', 'kaufen', 'ein', 'Brot'), 'Brot среднего рода, поэтому ein в Akkusativ не меняется.'),
  ord(22, 'EASY', 'Соберите немецкое предложение: «Я пью молоко».', items4('Ich', 'trinke', 'die', 'Milch'), 'Milch женского рода, и артикль die в Akkusativ остаётся прежним.'),
  ord(23, 'EASY', 'Соберите немецкое предложение: «Он берёт яблоко».', items4('Er', 'nimmt', 'den', 'Apfel'), 'У мужского рода der в Akkusativ превращается в den.'),
  ord(24, 'EASY', 'Соберите немецкое предложение: «Я хотел бы кофе».', items4('Ich', 'möchte', 'einen', 'Kaffee'), 'Kaffee мужского рода, поэтому ein в Akkusativ становится einen.'),

  // ===== 5 ord HARD (idx 25..29) =====
  ord(25, 'HARD', 'Соберите немецкое предложение: «Я хотел бы кофе, пожалуйста».', items5('Ich', 'möchte', 'einen', 'Kaffee', 'bitte'), 'Глагол möchte на 2-м месте, дополнение в Akkusativ с einen, bitte в конце.'),
  ord(26, 'HARD', 'Соберите немецкое предложение: «Она хотела бы яблоко, пожалуйста».', items5('Sie', 'möchte', 'einen', 'Apfel', 'bitte'), 'Глагол möchte на 2-м месте, дополнение в Akkusativ с einen, bitte в конце.'),
  ord(27, 'HARD', 'Соберите немецкое предложение: «Сегодня я ем хлеб».', items5('Heute', 'esse', 'ich', 'ein', 'Brot'), 'При начале с Heute глагол остаётся на 2-м месте, а подлежащее идёт после него.'),
  ord(28, 'HARD', 'Соберите немецкое предложение: «Я не хотел бы кофе, спасибо».', items5('Ich', 'möchte', 'keinen', 'Kaffee', 'danke'), 'У мужского рода kein в Akkusativ становится keinen, danke стоит в конце.'),
  ord(29, 'HARD', 'Соберите немецкое предложение: «Сегодня мы покупаем хлеб».', items5('Heute', 'kaufen', 'wir', 'ein', 'Brot'), 'При начале с Heute глагол остаётся на 2-м месте, подлежащее wir идёт после него, Brot среднего рода (ein).'),

  // ===== 5 fb EASY (idx 30..34) =====
  fb(30, 'EASY', 'Ich esse ___ Apfel.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('einen', 'eine', 'ein', 'einem', 'der'), 'Apfel мужского рода, поэтому в Akkusativ нужен einen.'),
  fb(31, 'EASY', 'Wir kaufen ___ Brot.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('einen', 'ein', 'eine', 'einem', 'den'), 'Brot среднего рода, и ein в Akkusativ не меняется.'),
  fb(32, 'EASY', 'Ich trinke ___ Milch.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('einen', 'ein', 'eine', 'einem', 'einer'), 'Milch женского рода, поэтому артикль eine в Akkusativ остаётся.'),
  fb(33, 'EASY', 'Er nimmt ___ Apfel.', [{ id: 'b1', correctCandidateId: 'c4' }], cand5('die', 'das', 'dem', 'den', 'ein'), 'У мужского рода der в Akkusativ превращается в den.'),
  fb(34, 'EASY', 'Ich möchte ___ Kaffee.', [{ id: 'b1', correctCandidateId: 'c5' }], cand5('ein', 'eine', 'einem', 'einer', 'einen'), 'Kaffee мужского рода, поэтому в Akkusativ нужен einen.'),

  // ===== 5 fb HARD (idx 35..39) =====
  fb(35, 'HARD', 'Ich möchte ___ Kaffee, bitte.', [{ id: 'b1', correctCandidateId: 'c1' }], cand5('einen', 'ein', 'eine', 'einem', 'einer'), 'Kaffee мужского рода (der), и в Akkusativ ein меняется на einen.'),
  fb(36, 'HARD', 'Heute esse ich ___ Brot.', [{ id: 'b1', correctCandidateId: 'c2' }], cand5('einen', 'ein', 'eine', 'einem', 'den'), 'Brot среднего рода, поэтому артикль ein в Akkusativ не меняется.'),
  fb(37, 'HARD', 'Ich kaufe ___ Kaffee heute.', [{ id: 'b1', correctCandidateId: 'c3' }], cand5('kein', 'keine', 'keinen', 'keinem', 'keiner'), 'У мужского рода kein в Akkusativ превращается в keinen.'),
  fb(38, 'HARD', 'Sie trinkt ___ Milch und ___ Wasser.', [{ id: 'b1', correctCandidateId: 'c2' }, { id: 'b2', correctCandidateId: 'c4' }], cand5('einen', 'die', 'einem', 'das', 'den'), 'Milch женского рода (die), Wasser среднего рода (das), и оба артикля в Akkusativ не меняются.'),
  fb(39, 'HARD', 'Er nimmt ___ Apfel und ___ Brot.', [{ id: 'b1', correctCandidateId: 'c5' }, { id: 'b2', correctCandidateId: 'c3' }], cand5('die', 'dem', 'das', 'einem', 'den'), 'Apfel мужского рода (den), Brot среднего рода (das), артикль среднего рода в Akkusativ не меняется.'),
];
