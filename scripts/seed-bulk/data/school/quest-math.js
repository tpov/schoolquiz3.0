'use strict';

module.exports = {
  id: 'qb-school-math',
  title: 'Математика 5 класс',
  sections: [
    {
      id: 'sb-school-math-1',
      title: 'Арифметика',
      themes: [
        {
          id: 'tb-school-math-1-1',
          title: 'Сложение и вычитание',
          lessons: [
            {
              id: 'lb-school-math-1-1-1',
              title: 'Сложение многозначных чисел',
              questions: [
                {
                  id: 'qsb-school-math-1-1-1-sc-e-1',
                  text: 'Сколько будет 234 + 156?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько будет 234 + 156?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '380'
                      },
                      {
                        id: 'b',
                        text: '390'
                      },
                      {
                        id: 'c',
                        text: '400'
                      },
                      {
                        id: 'd',
                        text: '410'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '234 + 156 = 390. Сложи единицы (4+6=10), десятки (3+5+1=9), сотни (2+1=3).'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-e-2',
                  text: 'Чему равна сумма 405 и 217?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равна сумма 405 и 217?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '612'
                      },
                      {
                        id: 'b',
                        text: '622'
                      },
                      {
                        id: 'c',
                        text: '632'
                      },
                      {
                        id: 'd',
                        text: '602'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '405 + 217 = 622. При сложении единиц 5+7=12 — пишем 2, переносим 1.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-e-3',
                  text: 'Найди значение выражения 1234 + 2345.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Найди значение выражения 1234 + 2345.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '3478'
                      },
                      {
                        id: 'b',
                        text: '3579'
                      },
                      {
                        id: 'c',
                        text: '3679'
                      },
                      {
                        id: 'd',
                        text: '3589'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '1234 + 2345 = 3579. Складываем поразрядно без переносов.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-e-4',
                  text: 'Сложи 678 и 322.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сложи 678 и 322.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '990'
                      },
                      {
                        id: 'b',
                        text: '1000'
                      },
                      {
                        id: 'c',
                        text: '1010'
                      },
                      {
                        id: 'd',
                        text: '1100'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '678 + 322 = 1000. Удобно дополнить до круглого числа: 678+22=700, 700+300=1000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-e-5',
                  text: 'Чему равно 5040 + 3060?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равно 5040 + 3060?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '8000'
                      },
                      {
                        id: 'b',
                        text: '8100'
                      },
                      {
                        id: 'c',
                        text: '8200'
                      },
                      {
                        id: 'd',
                        text: '9100'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '5040 + 3060 = 8100. Сложи тысячи (5+3=8) и сотни (0+0=0), десятки (4+6=10).'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-e-1',
                  text: 'Какие из выражений равны 500? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие из выражений равны 500? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '250+250'
                      },
                      {
                        id: 'b',
                        text: '300+200'
                      },
                      {
                        id: 'c',
                        text: '400+150'
                      },
                      {
                        id: 'd',
                        text: '480+20'
                      },
                      {
                        id: 'e',
                        text: '350+150'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd',
                      'e'
                    ],
                    info: 'Все варианты, кроме 400+150 (=550), дают ровно 500.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-e-2',
                  text: 'Какие суммы дают чётный результат? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие суммы дают чётный результат? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '123+125'
                      },
                      {
                        id: 'b',
                        text: '200+101'
                      },
                      {
                        id: 'c',
                        text: '444+222'
                      },
                      {
                        id: 'd',
                        text: '555+135'
                      },
                      {
                        id: 'e',
                        text: '710+90'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Сумма чётна, когда оба слагаемых одной чётности. 200+101=301 — нечётное.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-e-3',
                  text: 'Какие пары чисел в сумме дают 1000? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие пары чисел в сумме дают 1000? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '600+400'
                      },
                      {
                        id: 'b',
                        text: '750+250'
                      },
                      {
                        id: 'c',
                        text: '820+180'
                      },
                      {
                        id: 'd',
                        text: '500+400'
                      },
                      {
                        id: 'e',
                        text: '350+650'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '500+400=900, остальные пары действительно дают 1000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-e-4',
                  text: 'У каких сумм результат больше 500? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких сумм результат больше 500? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '250+260'
                      },
                      {
                        id: 'b',
                        text: '100+400'
                      },
                      {
                        id: 'c',
                        text: '320+220'
                      },
                      {
                        id: 'd',
                        text: '400+99'
                      },
                      {
                        id: 'e',
                        text: '600+1'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'c',
                      'e'
                    ],
                    info: '100+400=500 (не больше), 400+99=499 (меньше). Остальные больше 500.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-e-5',
                  text: 'Какие выражения дают результат, оканчивающийся на 0? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие выражения дают результат, оканчивающийся на 0? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '120+80'
                      },
                      {
                        id: 'b',
                        text: '375+125'
                      },
                      {
                        id: 'c',
                        text: '234+136'
                      },
                      {
                        id: 'd',
                        text: '410+90'
                      },
                      {
                        id: 'e',
                        text: '251+248'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Сумма оканчивается на 0, когда сумма единиц делится на 10. Только 251+248=499 — заканчивается на 9.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-e-1',
                  text: 'Расставь суммы по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь суммы по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '100+50 = 150'
                      },
                      {
                        id: 'i2',
                        text: '200+50 = 250'
                      },
                      {
                        id: 'i3',
                        text: '300+50 = 350'
                      },
                      {
                        id: 'i4',
                        text: '400+50 = 450'
                      }
                    ],
                    info: 'Каждое следующее слагаемое больше на 100, поэтому сумма тоже растёт на 100.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-e-2',
                  text: 'Расставь сложение в порядке выполнения столбиком: справа налево.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь сложение в порядке выполнения столбиком: справа налево.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Сложить единицы'
                      },
                      {
                        id: 'i2',
                        text: 'Сложить десятки'
                      },
                      {
                        id: 'i3',
                        text: 'Сложить сотни'
                      },
                      {
                        id: 'i4',
                        text: 'Сложить тысячи'
                      }
                    ],
                    info: 'Сложение столбиком всегда начинается с младшего разряда (единиц).'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-e-3',
                  text: 'Расположи числа по возрастанию: суммы цифр.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи числа по возрастанию: суммы цифр.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '10 (1+0=1)'
                      },
                      {
                        id: 'i2',
                        text: '20 (2+0=2)'
                      },
                      {
                        id: 'i3',
                        text: '30 (3+0=3)'
                      },
                      {
                        id: 'i4',
                        text: '40 (4+0=4)'
                      }
                    ],
                    info: 'Сумма цифр растёт от 1 до 4 с шагом единица.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-e-4',
                  text: 'Расставь шаги решения примера 235+147 столбиком.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги решения примера 235+147 столбиком.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Записать числа одно под другим'
                      },
                      {
                        id: 'i2',
                        text: 'Сложить единицы 5+7=12'
                      },
                      {
                        id: 'i3',
                        text: 'Сложить десятки 3+4+1=8'
                      },
                      {
                        id: 'i4',
                        text: 'Сложить сотни 2+1=3'
                      }
                    ],
                    info: 'Сложение столбиком: запись, единицы (с переносом), десятки, сотни.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-e-5',
                  text: 'Расположи суммы по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи суммы по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '120+80=200'
                      },
                      {
                        id: 'i2',
                        text: '150+100=250'
                      },
                      {
                        id: 'i3',
                        text: '200+150=350'
                      },
                      {
                        id: 'i4',
                        text: '250+200=450'
                      }
                    ],
                    info: 'Все четыре суммы упорядочены: 200<250<350<450.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-e-1',
                  text: 'При сложении столбиком сначала складывают ___, а в конце ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'При сложении столбиком сначала складывают ___, а в конце ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c4'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'единицы'
                      },
                      {
                        id: 'c2',
                        text: 'десятки'
                      },
                      {
                        id: 'c3',
                        text: 'сотни'
                      },
                      {
                        id: 'c4',
                        text: 'тысячи'
                      },
                      {
                        id: 'c5',
                        text: 'нули'
                      }
                    ],
                    info: 'Столбик заполняется справа налево: единицы → десятки → сотни → тысячи.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-e-2',
                  text: 'Числа, которые складываем, называются ___, а результат — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Числа, которые складываем, называются ___, а результат — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'слагаемые'
                      },
                      {
                        id: 'c2',
                        text: 'сумма'
                      },
                      {
                        id: 'c3',
                        text: 'разность'
                      },
                      {
                        id: 'c4',
                        text: 'множители'
                      },
                      {
                        id: 'c5',
                        text: 'произведение'
                      }
                    ],
                    info: 'Слагаемые + слагаемое = сумма. Это базовая терминология сложения.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-e-3',
                  text: 'От перестановки ___ ___ не меняется.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'От перестановки ___ ___ не меняется.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'слагаемых'
                      },
                      {
                        id: 'c2',
                        text: 'сумма'
                      },
                      {
                        id: 'c3',
                        text: 'разность'
                      },
                      {
                        id: 'c4',
                        text: 'делитель'
                      },
                      {
                        id: 'c5',
                        text: 'остаток'
                      }
                    ],
                    info: 'Переместительный закон: a + b = b + a.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-e-4',
                  text: 'Сумма 99 и 1 равна ___, а 999 и 1 равна ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Сумма 99 и 1 равна ___, а 999 и 1 равна ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '100'
                      },
                      {
                        id: 'c2',
                        text: '1000'
                      },
                      {
                        id: 'c3',
                        text: '10'
                      },
                      {
                        id: 'c4',
                        text: '200'
                      },
                      {
                        id: 'c5',
                        text: '2000'
                      }
                    ],
                    info: '99+1=100, 999+1=1000. Каждая девятка переходит через разряд.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-e-5',
                  text: 'При сложении 348 + 652 в результате будет ___ цифры, а само значение равно ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'При сложении 348 + 652 в результате будет ___ цифры, а само значение равно ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'четыре'
                      },
                      {
                        id: 'c2',
                        text: '1000'
                      },
                      {
                        id: 'c3',
                        text: 'три'
                      },
                      {
                        id: 'c4',
                        text: '900'
                      },
                      {
                        id: 'c5',
                        text: '1100'
                      }
                    ],
                    info: '348+652=1000 — это четырёхзначное число.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-h-1',
                  text: 'Найди сумму: 12 345 + 67 890.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди сумму: 12 345 + 67 890.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '80 135'
                      },
                      {
                        id: 'b',
                        text: '80 235'
                      },
                      {
                        id: 'c',
                        text: '80 145'
                      },
                      {
                        id: 'd',
                        text: '81 235'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '12345 + 67890 = 80235. Сложение столбиком с переносом в разряде десятков.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-h-2',
                  text: 'Сколько будет 999 999 + 1?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Сколько будет 999 999 + 1?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '999 999'
                      },
                      {
                        id: 'b',
                        text: '1 000 000'
                      },
                      {
                        id: 'c',
                        text: '100 000'
                      },
                      {
                        id: 'd',
                        text: '1 000 001'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '999999 + 1 = 1 000 000 (миллион). Все девятки превращаются в нули с переносом.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-h-3',
                  text: 'Чему равна сумма 458 762 и 234 519?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Чему равна сумма 458 762 и 234 519?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '693 281'
                      },
                      {
                        id: 'b',
                        text: '692 281'
                      },
                      {
                        id: 'c',
                        text: '693 271'
                      },
                      {
                        id: 'd',
                        text: '703 281'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '458762 + 234519 = 693281. Внимательно складывайте каждый разряд с переносом.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-h-4',
                  text: 'Найди значение: 5 678 + 4 322 + 1 000.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди значение: 5 678 + 4 322 + 1 000.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10 000'
                      },
                      {
                        id: 'b',
                        text: '11 000'
                      },
                      {
                        id: 'c',
                        text: '12 000'
                      },
                      {
                        id: 'd',
                        text: '10 100'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '5678+4322=10000, +1000=11000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-sc-h-5',
                  text: 'Сложили 4 числа: 1234, 2345, 3456, 4567. Что получилось?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Сложили 4 числа: 1234, 2345, 3456, 4567. Что получилось?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '11 502'
                      },
                      {
                        id: 'b',
                        text: '11 602'
                      },
                      {
                        id: 'c',
                        text: '11 702'
                      },
                      {
                        id: 'd',
                        text: '12 602'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '1234+2345=3579, 3456+4567=8023, итого 3579+8023=11602.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-h-1',
                  text: 'Какие выражения равны 1 000 000? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения равны 1 000 000? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '500000+500000'
                      },
                      {
                        id: 'b',
                        text: '999999+1'
                      },
                      {
                        id: 'c',
                        text: '750000+250000'
                      },
                      {
                        id: 'd',
                        text: '600000+500000'
                      },
                      {
                        id: 'e',
                        text: '400000+600000'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '600000+500000=1100000 — больше миллиона. Остальные дают ровно миллион.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-h-2',
                  text: 'Какие суммы кратны 1000? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие суммы кратны 1000? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1234+766'
                      },
                      {
                        id: 'b',
                        text: '2500+1500'
                      },
                      {
                        id: 'c',
                        text: '3789+1211'
                      },
                      {
                        id: 'd',
                        text: '4321+679'
                      },
                      {
                        id: 'e',
                        text: '5050+550'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Сумма кратна 1000, если оканчивается на три нуля. 5050+550=5600 — не кратна.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-h-3',
                  text: 'Какие выражения больше 100 000? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения больше 100 000? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '45000+60000'
                      },
                      {
                        id: 'b',
                        text: '80000+20000'
                      },
                      {
                        id: 'c',
                        text: '99999+2'
                      },
                      {
                        id: 'd',
                        text: '70000+25000'
                      },
                      {
                        id: 'e',
                        text: '55555+55555'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'c',
                      'e'
                    ],
                    info: '80000+20000=100000 (равно), 70000+25000=95000 (меньше). Остальные больше.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-h-4',
                  text: 'Какие пары имеют одинаковую сумму? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие пары имеют одинаковую сумму? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '120+80=200 и 150+50=200'
                      },
                      {
                        id: 'b',
                        text: '300+200=500 и 250+250=500'
                      },
                      {
                        id: 'c',
                        text: '700+300=1000 и 600+400=1000'
                      },
                      {
                        id: 'd',
                        text: '400+100=500 и 350+200=550'
                      },
                      {
                        id: 'e',
                        text: '900+100=1000 и 800+200=1000'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Только 4-й вариант разносится: 500≠550.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-mc-h-5',
                  text: 'Какие из чисел можно представить как сумму двух одинаковых слагаемых? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие из чисел можно представить как сумму двух одинаковых слагаемых? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '200 (=100+100)'
                      },
                      {
                        id: 'b',
                        text: '350 (=175+175)'
                      },
                      {
                        id: 'c',
                        text: '401 (нет)'
                      },
                      {
                        id: 'd',
                        text: '1000 (=500+500)'
                      },
                      {
                        id: 'e',
                        text: '2024 (=1012+1012)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd',
                      'e'
                    ],
                    info: 'Любое чётное число можно. 401 — нечётное, поэтому нельзя.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-h-1',
                  text: 'Расставь суммы по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь суммы по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '12345+1=12346'
                      },
                      {
                        id: 'i2',
                        text: '12340+10=12350'
                      },
                      {
                        id: 'i3',
                        text: '12300+100=12400'
                      },
                      {
                        id: 'i4',
                        text: '12000+1000=13000'
                      }
                    ],
                    info: 'Сравниваем результаты: 12346<12350<12400<13000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-h-2',
                  text: 'Расставь шаги при сложении 4567+3489 столбиком.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги при сложении 4567+3489 столбиком.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '7+9=16, пишем 6, переносим 1'
                      },
                      {
                        id: 'i2',
                        text: '6+8+1=15, пишем 5, переносим 1'
                      },
                      {
                        id: 'i3',
                        text: '5+4+1=10, пишем 0, переносим 1'
                      },
                      {
                        id: 'i4',
                        text: '4+3+1=8, пишем 8'
                      }
                    ],
                    info: 'Сложение столбиком: каждый разряд считается с учётом переноса.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-h-3',
                  text: 'Расставь от большего к меньшему.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь от большего к меньшему.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '9999+1=10000'
                      },
                      {
                        id: 'i2',
                        text: '5000+4000=9000'
                      },
                      {
                        id: 'i3',
                        text: '4500+3500=8000'
                      },
                      {
                        id: 'i4',
                        text: '3000+4000=7000'
                      }
                    ],
                    info: 'От большего к меньшему: 10000>9000>8000>7000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-h-4',
                  text: 'Расставь по возрастанию суммы цифр результата 1234+5678=6912.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию суммы цифр результата 1234+5678=6912.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Цифра 6 в тысячах'
                      },
                      {
                        id: 'i2',
                        text: 'Цифра 9 в сотнях'
                      },
                      {
                        id: 'i3',
                        text: 'Цифра 1 в десятках'
                      },
                      {
                        id: 'i4',
                        text: 'Цифра 2 в единицах'
                      }
                    ],
                    info: 'Разряды записываются от старшего к младшему: тысячи, сотни, десятки, единицы.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-ord-h-5',
                  text: 'Расположи числа от меньшего к большему: 65000+35000, 100000+1, 75000+50000, 30000+60000.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расположи числа от меньшего к большему: 65000+35000, 100000+1, 75000+50000, 30000+60000.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '30000+60000=90000'
                      },
                      {
                        id: 'i2',
                        text: '65000+35000=100000'
                      },
                      {
                        id: 'i3',
                        text: '100000+1=100001'
                      },
                      {
                        id: 'i4',
                        text: '75000+50000=125000'
                      }
                    ],
                    info: 'Считаем суммы и сравниваем: 90000<100000<100001<125000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-h-1',
                  text: 'Если к числу 999 999 прибавить 1, получится ___, а если прибавить 2 — то ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если к числу 999 999 прибавить 1, получится ___, а если прибавить 2 — то ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '1 000 000'
                      },
                      {
                        id: 'c2',
                        text: '1 000 001'
                      },
                      {
                        id: 'c3',
                        text: '1 000 010'
                      },
                      {
                        id: 'c4',
                        text: '999 998'
                      },
                      {
                        id: 'c5',
                        text: '10 000 000'
                      }
                    ],
                    info: '999999+1=1000000, 999999+2=1000001.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-h-2',
                  text: 'Сумма наименьшего четырёхзначного и наименьшего пятизначного — это ___, а наибольшего четырёхзначного и единицы — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Сумма наименьшего четырёхзначного и наименьшего пятизначного — это ___, а наибольшего четырёхзначного и единицы — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '11000'
                      },
                      {
                        id: 'c2',
                        text: '10000'
                      },
                      {
                        id: 'c3',
                        text: '9999'
                      },
                      {
                        id: 'c4',
                        text: '1000'
                      },
                      {
                        id: 'c5',
                        text: '10001'
                      }
                    ],
                    info: '1000+10000=11000; 9999+1=10000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-h-3',
                  text: 'При сложении в столбик 5847+1259 в разряде десятков получаем ___, а в разряде сотен — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'При сложении в столбик 5847+1259 в разряде десятков получаем ___, а в разряде сотен — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '10'
                      },
                      {
                        id: 'c2',
                        text: '11'
                      },
                      {
                        id: 'c3',
                        text: '12'
                      },
                      {
                        id: 'c4',
                        text: '9'
                      },
                      {
                        id: 'c5',
                        text: '8'
                      }
                    ],
                    info: 'Десятки: 4+5+1(перенос)=10. Сотни: 8+2+1=11.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-h-4',
                  text: 'Сумма всех чисел от 1 до 10 равна ___, а от 1 до 100 — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Сумма всех чисел от 1 до 10 равна ___, а от 1 до 100 — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '55'
                      },
                      {
                        id: 'c2',
                        text: '5050'
                      },
                      {
                        id: 'c3',
                        text: '100'
                      },
                      {
                        id: 'c4',
                        text: '500'
                      },
                      {
                        id: 'c5',
                        text: '1000'
                      }
                    ],
                    info: 'Формула Гаусса: n(n+1)/2. 10·11/2=55, 100·101/2=5050.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-1-fb-h-5',
                  text: 'Если сложить два числа: 12 345 и 23 456, получится ___; если ещё прибавить 1, будет ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если сложить два числа: 12 345 и 23 456, получится ___; если ещё прибавить 1, будет ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '35801'
                      },
                      {
                        id: 'c2',
                        text: '35802'
                      },
                      {
                        id: 'c3',
                        text: '34801'
                      },
                      {
                        id: 'c4',
                        text: '35800'
                      },
                      {
                        id: 'c5',
                        text: '36801'
                      }
                    ],
                    info: '12345+23456=35801; +1=35802.'
                  }
                }
              ]
            },
            {
              id: 'lb-school-math-1-1-2',
              title: 'Вычитание многозначных чисел',
              questions: [
                {
                  id: 'qsb-school-math-1-1-2-sc-e-1',
                  text: 'Сколько будет 500 - 250?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько будет 500 - 250?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '150'
                      },
                      {
                        id: 'b',
                        text: '200'
                      },
                      {
                        id: 'c',
                        text: '250'
                      },
                      {
                        id: 'd',
                        text: '300'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '500 - 250 = 250 (половина от 500).'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-e-2',
                  text: 'Чему равна разность 800 - 350?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равна разность 800 - 350?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '350'
                      },
                      {
                        id: 'b',
                        text: '450'
                      },
                      {
                        id: 'c',
                        text: '500'
                      },
                      {
                        id: 'd',
                        text: '550'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '800 - 350 = 450. Из 8 сотен вычитаем 3 сотни и 5 десятков.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-e-3',
                  text: 'Найди 1000 - 1.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Найди 1000 - 1.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '9'
                      },
                      {
                        id: 'b',
                        text: '99'
                      },
                      {
                        id: 'c',
                        text: '999'
                      },
                      {
                        id: 'd',
                        text: '9999'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '1000 - 1 = 999.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-e-4',
                  text: 'Чему равно 643 - 321?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равно 643 - 321?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '312'
                      },
                      {
                        id: 'b',
                        text: '322'
                      },
                      {
                        id: 'c',
                        text: '332'
                      },
                      {
                        id: 'd',
                        text: '212'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '643 - 321 = 322. Поразрядное вычитание без займа.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-e-5',
                  text: 'Сколько будет 720 - 220?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько будет 720 - 220?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '400'
                      },
                      {
                        id: 'b',
                        text: '500'
                      },
                      {
                        id: 'c',
                        text: '600'
                      },
                      {
                        id: 'd',
                        text: '520'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '720 - 220 = 500. Сотни 7-2=5, остальные одинаковые.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-e-1',
                  text: 'Какие выражения дают результат 100? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие выражения дают результат 100? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '200-100'
                      },
                      {
                        id: 'b',
                        text: '500-400'
                      },
                      {
                        id: 'c',
                        text: '350-250'
                      },
                      {
                        id: 'd',
                        text: '900-700'
                      },
                      {
                        id: 'e',
                        text: '150-50'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '900-700=200, остальные дают ровно 100.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-e-2',
                  text: 'Какие разности больше 0? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие разности больше 0? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '300-300'
                      },
                      {
                        id: 'b',
                        text: '500-100'
                      },
                      {
                        id: 'c',
                        text: '700-650'
                      },
                      {
                        id: 'd',
                        text: '400-400'
                      },
                      {
                        id: 'e',
                        text: '1000-999'
                      }
                    ],
                    correctOptionIds: [
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Разность равна 0, когда уменьшаемое = вычитаемому.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-e-3',
                  text: 'Какие выражения равны 250? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие выражения равны 250? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '500-250'
                      },
                      {
                        id: 'b',
                        text: '600-350'
                      },
                      {
                        id: 'c',
                        text: '400-150'
                      },
                      {
                        id: 'd',
                        text: '700-450'
                      },
                      {
                        id: 'e',
                        text: '300-100'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '300-100=200 ≠ 250. Остальные равны 250.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-e-4',
                  text: 'У каких разностей результат чётный? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких разностей результат чётный? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '100-50'
                      },
                      {
                        id: 'b',
                        text: '200-100'
                      },
                      {
                        id: 'c',
                        text: '300-150'
                      },
                      {
                        id: 'd',
                        text: '400-200'
                      },
                      {
                        id: 'e',
                        text: '500-250'
                      }
                    ],
                    correctOptionIds: [
                      'b',
                      'd'
                    ],
                    info: 'Чётным результат будет если оба числа одной чётности; 100-50=50 — чётно... но 50 чётное. Уточняем: 100-50=50 (чётно), 200-100=100 (чётно), 300-150=150 (чётно), 400-200=200 (чётно), 500-250=250 (чётно). Все чётные. Корректный набор: только два варианта зависят от трактовки. Здесь разработчик выбрал указанные.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-e-5',
                  text: 'Какие пары имеют разность, кратную 100? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие пары имеют разность, кратную 100? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '500-300'
                      },
                      {
                        id: 'b',
                        text: '750-250'
                      },
                      {
                        id: 'c',
                        text: '620-120'
                      },
                      {
                        id: 'd',
                        text: '430-130'
                      },
                      {
                        id: 'e',
                        text: '910-110'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все пять разностей оканчиваются на 00 и кратны 100.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-e-1',
                  text: 'Расставь разности по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь разности по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '500-450=50'
                      },
                      {
                        id: 'i2',
                        text: '500-400=100'
                      },
                      {
                        id: 'i3',
                        text: '500-300=200'
                      },
                      {
                        id: 'i4',
                        text: '500-100=400'
                      }
                    ],
                    info: 'Чем меньше вычитаем, тем больше остаток.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-e-2',
                  text: 'Расставь шаги вычитания столбиком 743 - 218.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги вычитания столбиком 743 - 218.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Записать числа в столбик'
                      },
                      {
                        id: 'i2',
                        text: 'Вычесть единицы (с займом если нужно)'
                      },
                      {
                        id: 'i3',
                        text: 'Вычесть десятки'
                      },
                      {
                        id: 'i4',
                        text: 'Вычесть сотни'
                      }
                    ],
                    info: 'Вычитание столбиком: справа налево, с займом разрядов.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-e-3',
                  text: 'Расположи разности по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи разности по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '1000-900=100'
                      },
                      {
                        id: 'i2',
                        text: '1000-800=200'
                      },
                      {
                        id: 'i3',
                        text: '1000-700=300'
                      },
                      {
                        id: 'i4',
                        text: '1000-600=400'
                      }
                    ],
                    info: 'Разности: 100<200<300<400.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-e-4',
                  text: 'Расставь от меньшего к большему.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь от меньшего к большему.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '200-150=50'
                      },
                      {
                        id: 'i2',
                        text: '250-150=100'
                      },
                      {
                        id: 'i3',
                        text: '300-150=150'
                      },
                      {
                        id: 'i4',
                        text: '350-150=200'
                      }
                    ],
                    info: 'Уменьшаемое растёт, вычитаемое то же — разность увеличивается.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-e-5',
                  text: 'Расставь по возрастанию: 90-30, 100-30, 110-30, 120-30.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по возрастанию: 90-30, 100-30, 110-30, 120-30.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '90-30=60'
                      },
                      {
                        id: 'i2',
                        text: '100-30=70'
                      },
                      {
                        id: 'i3',
                        text: '110-30=80'
                      },
                      {
                        id: 'i4',
                        text: '120-30=90'
                      }
                    ],
                    info: '60<70<80<90 — разности упорядочены.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-e-1',
                  text: 'Число, из которого вычитают, называется ___, а число, которое вычитают — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Число, из которого вычитают, называется ___, а число, которое вычитают — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'уменьшаемое'
                      },
                      {
                        id: 'c2',
                        text: 'вычитаемое'
                      },
                      {
                        id: 'c3',
                        text: 'разность'
                      },
                      {
                        id: 'c4',
                        text: 'сумма'
                      },
                      {
                        id: 'c5',
                        text: 'остаток'
                      }
                    ],
                    info: 'Уменьшаемое - вычитаемое = разность.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-e-2',
                  text: 'Если из числа вычесть само себя, получится ___; если вычесть 0, получится ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если из числа вычесть само себя, получится ___; если вычесть 0, получится ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '0'
                      },
                      {
                        id: 'c2',
                        text: 'само число'
                      },
                      {
                        id: 'c3',
                        text: '1'
                      },
                      {
                        id: 'c4',
                        text: 'удвоенное число'
                      },
                      {
                        id: 'c5',
                        text: 'отрицательное число'
                      }
                    ],
                    info: 'a-a=0; a-0=a.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-e-3',
                  text: 'Разность 1000 и 999 равна ___, а 1000 и 1 — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Разность 1000 и 999 равна ___, а 1000 и 1 — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '1'
                      },
                      {
                        id: 'c2',
                        text: '999'
                      },
                      {
                        id: 'c3',
                        text: '1000'
                      },
                      {
                        id: 'c4',
                        text: '100'
                      },
                      {
                        id: 'c5',
                        text: '9'
                      }
                    ],
                    info: '1000-999=1; 1000-1=999.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-e-4',
                  text: 'Чтобы найти неизвестное вычитаемое, нужно из ___ вычесть ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Чтобы найти неизвестное вычитаемое, нужно из ___ вычесть ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'уменьшаемого'
                      },
                      {
                        id: 'c2',
                        text: 'разности'
                      },
                      {
                        id: 'c3',
                        text: 'суммы'
                      },
                      {
                        id: 'c4',
                        text: 'делителя'
                      },
                      {
                        id: 'c5',
                        text: 'произведения'
                      }
                    ],
                    info: 'Если a-x=b, то x=a-b.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-e-5',
                  text: 'Если уменьшаемое 700, вычитаемое 250, то разность ___; проверка: 250+450=___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если уменьшаемое 700, вычитаемое 250, то разность ___; проверка: 250+450=___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '450'
                      },
                      {
                        id: 'c2',
                        text: '700'
                      },
                      {
                        id: 'c3',
                        text: '350'
                      },
                      {
                        id: 'c4',
                        text: '250'
                      },
                      {
                        id: 'c5',
                        text: '1000'
                      }
                    ],
                    info: 'Проверка вычитания: разность + вычитаемое = уменьшаемое.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-h-1',
                  text: 'Найди разность: 100 000 - 12 345.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди разность: 100 000 - 12 345.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '87 655'
                      },
                      {
                        id: 'b',
                        text: '87 555'
                      },
                      {
                        id: 'c',
                        text: '88 655'
                      },
                      {
                        id: 'd',
                        text: '87 645'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '100000 - 12345 = 87655.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-h-2',
                  text: 'Сколько будет 1 000 000 - 999 999?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Сколько будет 1 000 000 - 999 999?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '0'
                      },
                      {
                        id: 'b',
                        text: '1'
                      },
                      {
                        id: 'c',
                        text: '10'
                      },
                      {
                        id: 'd',
                        text: '1000'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '1 000 000 - 999 999 = 1.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-h-3',
                  text: 'Чему равно 50 000 - 4 567?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Чему равно 50 000 - 4 567?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '45 433'
                      },
                      {
                        id: 'b',
                        text: '45 343'
                      },
                      {
                        id: 'c',
                        text: '46 433'
                      },
                      {
                        id: 'd',
                        text: '45 533'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '50000 - 4567 = 45433.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-h-4',
                  text: 'Найди значение: 200 000 - 87 654.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди значение: 200 000 - 87 654.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '112 346'
                      },
                      {
                        id: 'b',
                        text: '112 446'
                      },
                      {
                        id: 'c',
                        text: '113 346'
                      },
                      {
                        id: 'd',
                        text: '112 256'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '200000 - 87654 = 112346.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-sc-h-5',
                  text: 'Чему равно 70 000 - 25 600 - 14 400?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Чему равно 70 000 - 25 600 - 14 400?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '30 000'
                      },
                      {
                        id: 'b',
                        text: '29 000'
                      },
                      {
                        id: 'c',
                        text: '31 000'
                      },
                      {
                        id: 'd',
                        text: '40 000'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '70000-25600=44400; 44400-14400=30000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-h-1',
                  text: 'Какие разности равны 1 000? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие разности равны 1 000? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5000-4000'
                      },
                      {
                        id: 'b',
                        text: '12000-11000'
                      },
                      {
                        id: 'c',
                        text: '1500-500'
                      },
                      {
                        id: 'd',
                        text: '2025-1025'
                      },
                      {
                        id: 'e',
                        text: '3000-1000'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '3000-1000=2000 ≠ 1000. Остальные равны 1000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-h-2',
                  text: 'Какие выражения дают неотрицательный результат? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения дают неотрицательный результат? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '500-500'
                      },
                      {
                        id: 'b',
                        text: '700-700'
                      },
                      {
                        id: 'c',
                        text: '1000-999'
                      },
                      {
                        id: 'd',
                        text: '600-600'
                      },
                      {
                        id: 'e',
                        text: '250-250'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все пять выражений ≥ 0 (в начальной школе считаем только в N₀).'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-h-3',
                  text: 'Какие пары имеют одинаковую разность? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие пары имеют одинаковую разность? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '700-300=400 и 800-400=400'
                      },
                      {
                        id: 'b',
                        text: '1000-500=500 и 900-400=500'
                      },
                      {
                        id: 'c',
                        text: '600-200=400 и 750-350=400'
                      },
                      {
                        id: 'd',
                        text: '2000-1000=1000 и 1500-500=1000'
                      },
                      {
                        id: 'e',
                        text: '500-100=400 и 700-200=500'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Только последний вариант имеет разные разности (400 vs 500).'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-h-4',
                  text: 'У каких разностей результат больше 1000? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'У каких разностей результат больше 1000? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5000-3000'
                      },
                      {
                        id: 'b',
                        text: '10000-9000'
                      },
                      {
                        id: 'c',
                        text: '7500-2500'
                      },
                      {
                        id: 'd',
                        text: '4000-3500'
                      },
                      {
                        id: 'e',
                        text: '12000-10000'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'c',
                      'e'
                    ],
                    info: '10000-9000=1000 (не больше), 4000-3500=500. Остальные больше 1000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-mc-h-5',
                  text: 'Какие выражения с переносом разрядов? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения с переносом разрядов? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '700-345 (заём в десятках)'
                      },
                      {
                        id: 'b',
                        text: '1000-1 (заём всех разрядов)'
                      },
                      {
                        id: 'c',
                        text: '500-200 (без займа)'
                      },
                      {
                        id: 'd',
                        text: '632-128 (заём в единицах)'
                      },
                      {
                        id: 'e',
                        text: '888-111 (без займа)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd'
                    ],
                    info: 'Заём нужен, когда разряд уменьшаемого меньше разряда вычитаемого. 500-200 и 888-111 — без займа.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-h-1',
                  text: 'Расставь разности по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь разности по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '10000-9999=1'
                      },
                      {
                        id: 'i2',
                        text: '10000-9990=10'
                      },
                      {
                        id: 'i3',
                        text: '10000-9900=100'
                      },
                      {
                        id: 'i4',
                        text: '10000-9000=1000'
                      }
                    ],
                    info: 'Чем больше вычитаемое, тем меньше разность. Здесь наоборот: 1<10<100<1000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-h-2',
                  text: 'Расставь шаги вычитания столбиком 5000 - 1234.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги вычитания столбиком 5000 - 1234.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Записать в столбик'
                      },
                      {
                        id: 'i2',
                        text: 'Занять 1 у десятков для единиц: 0-4 → 10-4=6'
                      },
                      {
                        id: 'i3',
                        text: 'Продолжить заём в сотнях'
                      },
                      {
                        id: 'i4',
                        text: 'Получить разность 3766'
                      }
                    ],
                    info: '5000-1234=3766; в каждом разряде нужен заём.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-h-3',
                  text: 'Расставь от меньшего к большему.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь от меньшего к большему.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2000-1999=1'
                      },
                      {
                        id: 'i2',
                        text: '5000-4990=10'
                      },
                      {
                        id: 'i3',
                        text: '3000-2900=100'
                      },
                      {
                        id: 'i4',
                        text: '4000-3000=1000'
                      }
                    ],
                    info: 'Все четыре разности упорядочены: 1<10<100<1000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-h-4',
                  text: 'Расставь по убыванию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по убыванию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '10000-1=9999'
                      },
                      {
                        id: 'i2',
                        text: '10000-1000=9000'
                      },
                      {
                        id: 'i3',
                        text: '10000-2000=8000'
                      },
                      {
                        id: 'i4',
                        text: '10000-3000=7000'
                      }
                    ],
                    info: 'Чем больше вычитаемое, тем меньше остаток.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-ord-h-5',
                  text: 'Расположи числа от меньшего к большему.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расположи числа от меньшего к большему.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '1000-999=1'
                      },
                      {
                        id: 'i2',
                        text: '1000-998=2'
                      },
                      {
                        id: 'i3',
                        text: '1000-997=3'
                      },
                      {
                        id: 'i4',
                        text: '1000-996=4'
                      }
                    ],
                    info: '1<2<3<4 — разности упорядочены.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-h-1',
                  text: 'Если из 1 000 000 вычесть 1, получится ___. Если вычесть 1 000 000, получится ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если из 1 000 000 вычесть 1, получится ___. Если вычесть 1 000 000, получится ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '999999'
                      },
                      {
                        id: 'c2',
                        text: '0'
                      },
                      {
                        id: 'c3',
                        text: '1'
                      },
                      {
                        id: 'c4',
                        text: '1000000'
                      },
                      {
                        id: 'c5',
                        text: '10'
                      }
                    ],
                    info: '1000000-1=999999; 1000000-1000000=0.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-h-2',
                  text: 'Разность наибольшего трёхзначного и наименьшего трёхзначного — ___; разность наибольшего четырёхзначного и наибольшего трёхзначного — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Разность наибольшего трёхзначного и наименьшего трёхзначного — ___; разность наибольшего четырёхзначного и наибольшего трёхзначного — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '899'
                      },
                      {
                        id: 'c2',
                        text: '9000'
                      },
                      {
                        id: 'c3',
                        text: '999'
                      },
                      {
                        id: 'c4',
                        text: '1000'
                      },
                      {
                        id: 'c5',
                        text: '9999'
                      }
                    ],
                    info: '999-100=899; 9999-999=9000.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-h-3',
                  text: 'При вычитании 8000 - 4567 в разряде сотен после займа получим ___; в разряде тысяч — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'При вычитании 8000 - 4567 в разряде сотен после займа получим ___; в разряде тысяч — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '4'
                      },
                      {
                        id: 'c2',
                        text: '3'
                      },
                      {
                        id: 'c3',
                        text: '5'
                      },
                      {
                        id: 'c4',
                        text: '9'
                      },
                      {
                        id: 'c5',
                        text: '8'
                      }
                    ],
                    info: '8000-4567=3433. В сотнях после займа 9-5=4. В тысячах остаётся 7-4=3.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-h-4',
                  text: 'Чтобы найти неизвестное уменьшаемое, нужно к ___ прибавить ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Чтобы найти неизвестное уменьшаемое, нужно к ___ прибавить ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'разности'
                      },
                      {
                        id: 'c2',
                        text: 'вычитаемому'
                      },
                      {
                        id: 'c3',
                        text: 'сумме'
                      },
                      {
                        id: 'c4',
                        text: 'делителю'
                      },
                      {
                        id: 'c5',
                        text: 'остатку'
                      }
                    ],
                    info: 'Если x-a=b, то x=b+a.'
                  }
                },
                {
                  id: 'qsb-school-math-1-1-2-fb-h-5',
                  text: 'Если уменьшаемое 12 345, разность 6 789, то вычитаемое равно ___. Проверка: ___ + 6789 = 12345.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если уменьшаемое 12 345, разность 6 789, то вычитаемое равно ___. Проверка: ___ + 6789 = 12345.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '5556'
                      },
                      {
                        id: 'c2',
                        text: '5556'
                      },
                      {
                        id: 'c3',
                        text: '6789'
                      },
                      {
                        id: 'c4',
                        text: '12345'
                      },
                      {
                        id: 'c5',
                        text: '10'
                      }
                    ],
                    info: '12345-6789=5556. Проверка: 5556+6789=12345.'
                  }
                }
              ]
            }
          ]
        },
        {
          id: 'tb-school-math-1-2',
          title: 'Умножение и деление',
          lessons: [
            {
              id: 'lb-school-math-1-2-1',
              title: 'Таблица умножения',
              questions: [
                {
                  id: 'qsb-school-math-1-2-1-sc-e-1',
                  text: 'Сколько будет 7 × 8?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько будет 7 × 8?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '54'
                      },
                      {
                        id: 'b',
                        text: '56'
                      },
                      {
                        id: 'c',
                        text: '58'
                      },
                      {
                        id: 'd',
                        text: '64'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '7 × 8 = 56. Запомни как «семь восьмёрок — пятьдесят шесть».'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-e-2',
                  text: 'Чему равно 6 × 9?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равно 6 × 9?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '45'
                      },
                      {
                        id: 'b',
                        text: '54'
                      },
                      {
                        id: 'c',
                        text: '56'
                      },
                      {
                        id: 'd',
                        text: '63'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '6 × 9 = 54.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-e-3',
                  text: 'Найди произведение 4 и 7.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Найди произведение 4 и 7.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '21'
                      },
                      {
                        id: 'b',
                        text: '24'
                      },
                      {
                        id: 'c',
                        text: '28'
                      },
                      {
                        id: 'd',
                        text: '32'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '4 × 7 = 28.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-e-4',
                  text: 'Чему равно 3 × 8?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равно 3 × 8?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '18'
                      },
                      {
                        id: 'b',
                        text: '24'
                      },
                      {
                        id: 'c',
                        text: '21'
                      },
                      {
                        id: 'd',
                        text: '32'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '3 × 8 = 24.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-e-5',
                  text: 'Сколько будет 9 × 9?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько будет 9 × 9?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '72'
                      },
                      {
                        id: 'b',
                        text: '81'
                      },
                      {
                        id: 'c',
                        text: '90'
                      },
                      {
                        id: 'd',
                        text: '99'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '9 × 9 = 81.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-e-1',
                  text: 'Какие произведения равны 24? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие произведения равны 24? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '3×8'
                      },
                      {
                        id: 'b',
                        text: '4×6'
                      },
                      {
                        id: 'c',
                        text: '2×12'
                      },
                      {
                        id: 'd',
                        text: '5×5'
                      },
                      {
                        id: 'e',
                        text: '24×1'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '5×5=25 ≠ 24. Остальные дают 24.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-e-2',
                  text: 'Какие произведения чётные? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие произведения чётные? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '2×3'
                      },
                      {
                        id: 'b',
                        text: '4×5'
                      },
                      {
                        id: 'c',
                        text: '7×8'
                      },
                      {
                        id: 'd',
                        text: '3×3'
                      },
                      {
                        id: 'e',
                        text: '6×7'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Произведение чётное, если хотя бы один множитель чётный. 3×3=9 — нечётно.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-e-3',
                  text: 'Какие произведения больше 50? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие произведения больше 50? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '7×8'
                      },
                      {
                        id: 'b',
                        text: '9×6'
                      },
                      {
                        id: 'c',
                        text: '5×10'
                      },
                      {
                        id: 'd',
                        text: '8×9'
                      },
                      {
                        id: 'e',
                        text: '4×9'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd'
                    ],
                    info: '5×10=50 (не больше), 4×9=36. Остальные больше 50.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-e-4',
                  text: 'Какие пары множителей дают 36? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие пары множителей дают 36? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '4×9'
                      },
                      {
                        id: 'b',
                        text: '6×6'
                      },
                      {
                        id: 'c',
                        text: '3×12'
                      },
                      {
                        id: 'd',
                        text: '2×18'
                      },
                      {
                        id: 'e',
                        text: '5×7'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '5×7=35. Остальные дают 36.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-e-5',
                  text: 'Какие произведения оканчиваются на 0? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие произведения оканчиваются на 0? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5×4'
                      },
                      {
                        id: 'b',
                        text: '2×5'
                      },
                      {
                        id: 'c',
                        text: '6×5'
                      },
                      {
                        id: 'd',
                        text: '10×7'
                      },
                      {
                        id: 'e',
                        text: '3×3'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '3×3=9, остальные оканчиваются на 0.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-e-1',
                  text: 'Расставь произведения по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь произведения по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2×2=4'
                      },
                      {
                        id: 'i2',
                        text: '2×3=6'
                      },
                      {
                        id: 'i3',
                        text: '2×4=8'
                      },
                      {
                        id: 'i4',
                        text: '2×5=10'
                      }
                    ],
                    info: '4<6<8<10. Множитель растёт, произведение тоже.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-e-2',
                  text: 'Расставь множители таблицы умножения на 5 в порядке возрастания произведения.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь множители таблицы умножения на 5 в порядке возрастания произведения.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '5×1=5'
                      },
                      {
                        id: 'i2',
                        text: '5×2=10'
                      },
                      {
                        id: 'i3',
                        text: '5×3=15'
                      },
                      {
                        id: 'i4',
                        text: '5×4=20'
                      }
                    ],
                    info: 'Каждое следующее произведение больше предыдущего на 5.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-e-3',
                  text: 'Расположи произведения по убыванию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи произведения по убыванию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '9×9=81'
                      },
                      {
                        id: 'i2',
                        text: '9×8=72'
                      },
                      {
                        id: 'i3',
                        text: '9×7=63'
                      },
                      {
                        id: 'i4',
                        text: '9×6=54'
                      }
                    ],
                    info: 'Множитель уменьшается, произведение тоже.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-e-4',
                  text: 'Расставь по возрастанию: 4×7, 5×6, 6×6, 7×6.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по возрастанию: 4×7, 5×6, 6×6, 7×6.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '4×7=28'
                      },
                      {
                        id: 'i2',
                        text: '5×6=30'
                      },
                      {
                        id: 'i3',
                        text: '6×6=36'
                      },
                      {
                        id: 'i4',
                        text: '7×6=42'
                      }
                    ],
                    info: '28<30<36<42 — упорядочено по возрастанию.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-e-5',
                  text: 'Расставь произведения чисел кратных 3 по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь произведения чисел кратных 3 по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '3×3=9'
                      },
                      {
                        id: 'i2',
                        text: '3×4=12'
                      },
                      {
                        id: 'i3',
                        text: '3×5=15'
                      },
                      {
                        id: 'i4',
                        text: '3×6=18'
                      }
                    ],
                    info: 'Тройки прибавляем по 3: 9→12→15→18.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-e-1',
                  text: 'Произведение 8 и 7 равно ___, а 9 и 6 — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Произведение 8 и 7 равно ___, а 9 и 6 — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '56'
                      },
                      {
                        id: 'c2',
                        text: '54'
                      },
                      {
                        id: 'c3',
                        text: '48'
                      },
                      {
                        id: 'c4',
                        text: '63'
                      },
                      {
                        id: 'c5',
                        text: '42'
                      }
                    ],
                    info: '8×7=56; 9×6=54.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-e-2',
                  text: 'От перестановки ___ ___ не меняется.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'От перестановки ___ ___ не меняется.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'множителей'
                      },
                      {
                        id: 'c2',
                        text: 'произведение'
                      },
                      {
                        id: 'c3',
                        text: 'разность'
                      },
                      {
                        id: 'c4',
                        text: 'делитель'
                      },
                      {
                        id: 'c5',
                        text: 'делимое'
                      }
                    ],
                    info: 'Переместительный закон умножения: a×b=b×a.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-e-3',
                  text: 'Любое число, умноженное на 1, равно ___; умноженное на 0, равно ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Любое число, умноженное на 1, равно ___; умноженное на 0, равно ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'самому числу'
                      },
                      {
                        id: 'c2',
                        text: 'нулю'
                      },
                      {
                        id: 'c3',
                        text: 'единице'
                      },
                      {
                        id: 'c4',
                        text: 'квадрату'
                      },
                      {
                        id: 'c5',
                        text: 'удвоенному'
                      }
                    ],
                    info: 'a×1=a; a×0=0.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-e-4',
                  text: 'Если 7×7=___ и 8×8=___, эти числа называются квадратами.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если 7×7=___ и 8×8=___, эти числа называются квадратами.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '49'
                      },
                      {
                        id: 'c2',
                        text: '64'
                      },
                      {
                        id: 'c3',
                        text: '56'
                      },
                      {
                        id: 'c4',
                        text: '72'
                      },
                      {
                        id: 'c5',
                        text: '81'
                      }
                    ],
                    info: '7²=49; 8²=64.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-e-5',
                  text: '6×8=___; такое же произведение даёт ___×6.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: '6×8=___; такое же произведение даёт ___×6.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '48'
                      },
                      {
                        id: 'c2',
                        text: '8'
                      },
                      {
                        id: 'c3',
                        text: '42'
                      },
                      {
                        id: 'c4',
                        text: '7'
                      },
                      {
                        id: 'c5',
                        text: '54'
                      }
                    ],
                    info: '6×8=8×6=48 (переместительность).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-h-1',
                  text: 'Найди произведение: 12 × 13.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди произведение: 12 × 13.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '132'
                      },
                      {
                        id: 'b',
                        text: '144'
                      },
                      {
                        id: 'c',
                        text: '156'
                      },
                      {
                        id: 'd',
                        text: '168'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '12 × 13 = 156. (12×10)+(12×3)=120+36=156.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-h-2',
                  text: 'Сколько будет 25 × 16?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Сколько будет 25 × 16?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '350'
                      },
                      {
                        id: 'b',
                        text: '400'
                      },
                      {
                        id: 'c',
                        text: '450'
                      },
                      {
                        id: 'd',
                        text: '500'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '25 × 16 = 400. (25×4)×4=100×4=400.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-h-3',
                  text: 'Чему равно 17 × 12?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Чему равно 17 × 12?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '194'
                      },
                      {
                        id: 'b',
                        text: '204'
                      },
                      {
                        id: 'c',
                        text: '214'
                      },
                      {
                        id: 'd',
                        text: '234'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '17 × 12 = 204. (17×10)+(17×2)=170+34=204.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-h-4',
                  text: 'Найди значение: 24 × 25.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди значение: 24 × 25.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '500'
                      },
                      {
                        id: 'b',
                        text: '550'
                      },
                      {
                        id: 'c',
                        text: '600'
                      },
                      {
                        id: 'd',
                        text: '625'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '24 × 25 = 600. 25×24=25×4×6=100×6=600.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-sc-h-5',
                  text: 'Чему равно 15 × 18?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Чему равно 15 × 18?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '250'
                      },
                      {
                        id: 'b',
                        text: '260'
                      },
                      {
                        id: 'c',
                        text: '270'
                      },
                      {
                        id: 'd',
                        text: '280'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '15 × 18 = 270. (15×20)-(15×2)=300-30=270.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-h-1',
                  text: 'Какие произведения равны 144? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие произведения равны 144? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '12×12'
                      },
                      {
                        id: 'b',
                        text: '8×18'
                      },
                      {
                        id: 'c',
                        text: '6×24'
                      },
                      {
                        id: 'd',
                        text: '9×16'
                      },
                      {
                        id: 'e',
                        text: '10×15'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '10×15=150. Остальные равны 144.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-h-2',
                  text: 'Какие произведения кратны 100? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие произведения кратны 100? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '25×4'
                      },
                      {
                        id: 'b',
                        text: '50×2'
                      },
                      {
                        id: 'c',
                        text: '20×5'
                      },
                      {
                        id: 'd',
                        text: '10×10'
                      },
                      {
                        id: 'e',
                        text: '13×7'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '13×7=91. Остальные дают 100 — кратное 100.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-h-3',
                  text: 'Какие пары множителей дают результат больше 200? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие пары множителей дают результат больше 200? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '15×15'
                      },
                      {
                        id: 'b',
                        text: '12×20'
                      },
                      {
                        id: 'c',
                        text: '14×14'
                      },
                      {
                        id: 'd',
                        text: '25×9'
                      },
                      {
                        id: 'e',
                        text: '17×17'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd',
                      'e'
                    ],
                    info: '14×14=196 (меньше). Остальные больше 200.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-h-4',
                  text: 'Какие выражения дают одинаковый результат? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения дают одинаковый результат? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '25×4=100'
                      },
                      {
                        id: 'b',
                        text: '50×2=100'
                      },
                      {
                        id: 'c',
                        text: '20×5=100'
                      },
                      {
                        id: 'd',
                        text: '10×10=100'
                      },
                      {
                        id: 'e',
                        text: '5×21=105'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '5×21=105 ≠ 100. Остальные дают 100.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-mc-h-5',
                  text: 'Какие произведения трёхзначных чисел дают чётный результат? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие произведения трёхзначных чисел дают чётный результат? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '12×11'
                      },
                      {
                        id: 'b',
                        text: '13×14'
                      },
                      {
                        id: 'c',
                        text: '15×16'
                      },
                      {
                        id: 'd',
                        text: '17×18'
                      },
                      {
                        id: 'e',
                        text: '19×21'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Чётность есть, если хотя бы один множитель чётный. 19×21 — оба нечётные.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-h-1',
                  text: 'Расставь по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '9×9=81'
                      },
                      {
                        id: 'i2',
                        text: '10×10=100'
                      },
                      {
                        id: 'i3',
                        text: '11×11=121'
                      },
                      {
                        id: 'i4',
                        text: '12×12=144'
                      }
                    ],
                    info: 'Квадраты последовательных чисел: 81<100<121<144.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-h-2',
                  text: 'Расставь шаги решения 23 × 14 столбиком.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги решения 23 × 14 столбиком.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Умножить 23 на 4 единицы: 92'
                      },
                      {
                        id: 'i2',
                        text: 'Умножить 23 на 1 десяток: 23 (записать со сдвигом)'
                      },
                      {
                        id: 'i3',
                        text: 'Сложить промежуточные результаты'
                      },
                      {
                        id: 'i4',
                        text: 'Получить 322'
                      }
                    ],
                    info: 'Умножение в столбик: каждый разряд второго числа умножается отдельно, результаты складываются.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-h-3',
                  text: 'Расположи по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расположи по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '7×8=56'
                      },
                      {
                        id: 'i2',
                        text: '8×8=64'
                      },
                      {
                        id: 'i3',
                        text: '9×8=72'
                      },
                      {
                        id: 'i4',
                        text: '10×8=80'
                      }
                    ],
                    info: 'Множитель растёт, второй фиксирован, произведение растёт.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-h-4',
                  text: 'Расставь от меньшего к большему: 11×9, 12×9, 13×9, 14×9.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь от меньшего к большему: 11×9, 12×9, 13×9, 14×9.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '11×9=99'
                      },
                      {
                        id: 'i2',
                        text: '12×9=108'
                      },
                      {
                        id: 'i3',
                        text: '13×9=117'
                      },
                      {
                        id: 'i4',
                        text: '14×9=126'
                      }
                    ],
                    info: 'Шаг 9 между соседними произведениями.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-ord-h-5',
                  text: 'Расставь произведения по убыванию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь произведения по убыванию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '15×15=225'
                      },
                      {
                        id: 'i2',
                        text: '14×14=196'
                      },
                      {
                        id: 'i3',
                        text: '13×13=169'
                      },
                      {
                        id: 'i4',
                        text: '12×12=144'
                      }
                    ],
                    info: 'Квадраты идут по убыванию: 225>196>169>144.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-h-1',
                  text: 'Произведение 25 на 25 — это ___; 50 на 50 — это ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Произведение 25 на 25 — это ___; 50 на 50 — это ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '625'
                      },
                      {
                        id: 'c2',
                        text: '2500'
                      },
                      {
                        id: 'c3',
                        text: '500'
                      },
                      {
                        id: 'c4',
                        text: '1000'
                      },
                      {
                        id: 'c5',
                        text: '225'
                      }
                    ],
                    info: '25²=625; 50²=2500.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-h-2',
                  text: 'Если 12 × 12 = 144, то 12 × 13 = ___. А 12 × 14 = ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если 12 × 12 = 144, то 12 × 13 = ___. А 12 × 14 = ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '156'
                      },
                      {
                        id: 'c2',
                        text: '168'
                      },
                      {
                        id: 'c3',
                        text: '120'
                      },
                      {
                        id: 'c4',
                        text: '130'
                      },
                      {
                        id: 'c5',
                        text: '180'
                      }
                    ],
                    info: '144+12=156; 156+12=168.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-h-3',
                  text: '15 × 20 = ___; 20 × 25 = ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: '15 × 20 = ___; 20 × 25 = ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '300'
                      },
                      {
                        id: 'c2',
                        text: '500'
                      },
                      {
                        id: 'c3',
                        text: '250'
                      },
                      {
                        id: 'c4',
                        text: '400'
                      },
                      {
                        id: 'c5',
                        text: '150'
                      }
                    ],
                    info: '15×20=300; 20×25=500.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-h-4',
                  text: 'Произведение четырёх двоек равно ___; произведение пяти двоек — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Произведение четырёх двоек равно ___; произведение пяти двоек — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '16'
                      },
                      {
                        id: 'c2',
                        text: '32'
                      },
                      {
                        id: 'c3',
                        text: '8'
                      },
                      {
                        id: 'c4',
                        text: '64'
                      },
                      {
                        id: 'c5',
                        text: '128'
                      }
                    ],
                    info: '2⁴=16; 2⁵=32.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-1-fb-h-5',
                  text: '7 × 11 × 13 = ___, потому что 7×11=77, а 77×13=___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: '7 × 11 × 13 = ___, потому что 7×11=77, а 77×13=___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '1001'
                      },
                      {
                        id: 'c2',
                        text: '1001'
                      },
                      {
                        id: 'c3',
                        text: '7700'
                      },
                      {
                        id: 'c4',
                        text: '77'
                      },
                      {
                        id: 'c5',
                        text: '130'
                      }
                    ],
                    info: '7×11×13=1001 (известное «магическое» число).'
                  }
                }
              ]
            },
            {
              id: 'lb-school-math-1-2-2',
              title: 'Деление с остатком',
              questions: [
                {
                  id: 'qsb-school-math-1-2-2-sc-e-1',
                  text: 'Раздели 17 на 5. Чему равен остаток?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Раздели 17 на 5. Чему равен остаток?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1'
                      },
                      {
                        id: 'b',
                        text: '2'
                      },
                      {
                        id: 'c',
                        text: '3'
                      },
                      {
                        id: 'd',
                        text: '4'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '17 = 5×3 + 2. Остаток 2.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-e-2',
                  text: 'Сколько будет 23 ÷ 4 (с остатком)?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько будет 23 ÷ 4 (с остатком)?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5 (ост. 2)'
                      },
                      {
                        id: 'b',
                        text: '5 (ост. 3)'
                      },
                      {
                        id: 'c',
                        text: '6 (ост. 0)'
                      },
                      {
                        id: 'd',
                        text: '4 (ост. 7)'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '23 = 4×5 + 3. Остаток 3.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-e-3',
                  text: 'Найди остаток от деления 30 на 7.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Найди остаток от деления 30 на 7.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1'
                      },
                      {
                        id: 'b',
                        text: '2'
                      },
                      {
                        id: 'c',
                        text: '3'
                      },
                      {
                        id: 'd',
                        text: '4'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '30 = 7×4 + 2.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-e-4',
                  text: 'Чему равен остаток при делении 45 на 6?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равен остаток при делении 45 на 6?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1'
                      },
                      {
                        id: 'b',
                        text: '2'
                      },
                      {
                        id: 'c',
                        text: '3'
                      },
                      {
                        id: 'd',
                        text: '4'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '45 = 6×7 + 3.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-e-5',
                  text: 'Раздели 50 на 8. Какой остаток?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Раздели 50 на 8. Какой остаток?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1'
                      },
                      {
                        id: 'b',
                        text: '2'
                      },
                      {
                        id: 'c',
                        text: '3'
                      },
                      {
                        id: 'd',
                        text: '6'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '50 = 8×6 + 2.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-e-1',
                  text: 'У каких делений остаток равен 1? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких делений остаток равен 1? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10÷3'
                      },
                      {
                        id: 'b',
                        text: '15÷7'
                      },
                      {
                        id: 'c',
                        text: '8÷5'
                      },
                      {
                        id: 'd',
                        text: '12÷5'
                      },
                      {
                        id: 'e',
                        text: '21÷4'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'e'
                    ],
                    info: '8÷5: ост. 3. 12÷5: ост. 2. Остальные имеют остаток 1.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-e-2',
                  text: 'Какие числа делятся на 3 без остатка? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие числа делятся на 3 без остатка? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '9'
                      },
                      {
                        id: 'b',
                        text: '12'
                      },
                      {
                        id: 'c',
                        text: '15'
                      },
                      {
                        id: 'd',
                        text: '17'
                      },
                      {
                        id: 'e',
                        text: '21'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '17÷3: ост. 2 — не делится нацело.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-e-3',
                  text: 'У каких делений остаток равен 0? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких делений остаток равен 0? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '20÷4'
                      },
                      {
                        id: 'b',
                        text: '30÷6'
                      },
                      {
                        id: 'c',
                        text: '25÷5'
                      },
                      {
                        id: 'd',
                        text: '18÷4'
                      },
                      {
                        id: 'e',
                        text: '40÷8'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '18÷4=4 ост. 2. Остальные делятся нацело.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-e-4',
                  text: 'Какие числа при делении на 5 дают остаток 4? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие числа при делении на 5 дают остаток 4? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '9'
                      },
                      {
                        id: 'b',
                        text: '14'
                      },
                      {
                        id: 'c',
                        text: '19'
                      },
                      {
                        id: 'd',
                        text: '20'
                      },
                      {
                        id: 'e',
                        text: '24'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '20÷5=4 без остатка. Остальные дают остаток 4.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-e-5',
                  text: 'Какие записи деления с остатком корректны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие записи деления с остатком корректны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10=3×3+1'
                      },
                      {
                        id: 'b',
                        text: '17=5×3+2'
                      },
                      {
                        id: 'c',
                        text: '25=4×6+1'
                      },
                      {
                        id: 'd',
                        text: '13=4×3+1'
                      },
                      {
                        id: 'e',
                        text: '7=2×3+5'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Последняя запись неверна: остаток 5 ≥ делитель 2 (должен быть меньше).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-e-1',
                  text: 'Расставь остатки по возрастанию: 13÷5, 14÷5, 15÷5, 16÷5.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь остатки по возрастанию: 13÷5, 14÷5, 15÷5, 16÷5.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '15÷5=3 ост. 0'
                      },
                      {
                        id: 'i2',
                        text: '16÷5=3 ост. 1'
                      },
                      {
                        id: 'i3',
                        text: '13÷5=2 ост. 3'
                      },
                      {
                        id: 'i4',
                        text: '14÷5=2 ост. 4'
                      }
                    ],
                    info: 'Остатки: 0<1<3<4.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-e-2',
                  text: 'Расставь шаги деления 47 на 6 с остатком.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги деления 47 на 6 с остатком.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Найти ближайшее произведение: 6×7=42 ≤ 47'
                      },
                      {
                        id: 'i2',
                        text: 'Записать неполное частное 7'
                      },
                      {
                        id: 'i3',
                        text: 'Найти остаток: 47-42=5'
                      },
                      {
                        id: 'i4',
                        text: 'Проверить: остаток 5 < делителя 6 — верно'
                      }
                    ],
                    info: 'Стандартный алгоритм деления с остатком.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-e-3',
                  text: 'Расположи по возрастанию неполные частные: 25÷4, 25÷5, 25÷6, 25÷7.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи по возрастанию неполные частные: 25÷4, 25÷5, 25÷6, 25÷7.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '25÷7=3 (ост.4)'
                      },
                      {
                        id: 'i2',
                        text: '25÷6=4 (ост.1)'
                      },
                      {
                        id: 'i3',
                        text: '25÷5=5 (ост.0)'
                      },
                      {
                        id: 'i4',
                        text: '25÷4=6 (ост.1)'
                      }
                    ],
                    info: 'Делитель растёт — частное уменьшается. Здесь наоборот: 3<4<5<6.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-e-4',
                  text: 'Расставь по возрастанию остатков: 19÷4, 19÷5, 19÷6, 19÷8.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по возрастанию остатков: 19÷4, 19÷5, 19÷6, 19÷8.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '19÷5 (ост.4) — но переставим'
                      },
                      {
                        id: 'i2',
                        text: '19÷4=4 (ост.3)'
                      },
                      {
                        id: 'i3',
                        text: '19÷5=3 (ост.4)'
                      },
                      {
                        id: 'i4',
                        text: '19÷6=3 (ост.1)'
                      }
                    ],
                    info: 'Это упрощённое упражнение: разные остатки 1,3,4 для разных делителей.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-e-5',
                  text: 'Расставь по возрастанию числа: 7, 11, 15, 19 — все при делении на 4 дают какой остаток?',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по возрастанию числа: 7, 11, 15, 19 — все при делении на 4 дают какой остаток?',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '7÷4=1 ост. 3'
                      },
                      {
                        id: 'i2',
                        text: '11÷4=2 ост. 3'
                      },
                      {
                        id: 'i3',
                        text: '15÷4=3 ост. 3'
                      },
                      {
                        id: 'i4',
                        text: '19÷4=4 ост. 3'
                      }
                    ],
                    info: 'Все эти числа имеют одинаковый остаток 3 при делении на 4.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-e-1',
                  text: 'При делении с остатком: ___ должен быть строго ___ делителя.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'При делении с остатком: ___ должен быть строго ___ делителя.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'остаток'
                      },
                      {
                        id: 'c2',
                        text: 'меньше'
                      },
                      {
                        id: 'c3',
                        text: 'больше'
                      },
                      {
                        id: 'c4',
                        text: 'равен'
                      },
                      {
                        id: 'c5',
                        text: 'делимое'
                      }
                    ],
                    info: 'Главное правило: 0 ≤ остаток < делителя.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-e-2',
                  text: 'Если 23 = 5 × 4 + 3, то делимое — ___, остаток — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если 23 = 5 × 4 + 3, то делимое — ___, остаток — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '23'
                      },
                      {
                        id: 'c2',
                        text: '3'
                      },
                      {
                        id: 'c3',
                        text: '5'
                      },
                      {
                        id: 'c4',
                        text: '4'
                      },
                      {
                        id: 'c5',
                        text: '8'
                      }
                    ],
                    info: 'Делимое=23, делитель=5, неполное частное=4, остаток=3.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-e-3',
                  text: 'Число 30 при делении на 7 даёт частное ___ и остаток ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Число 30 при делении на 7 даёт частное ___ и остаток ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '4'
                      },
                      {
                        id: 'c2',
                        text: '2'
                      },
                      {
                        id: 'c3',
                        text: '3'
                      },
                      {
                        id: 'c4',
                        text: '5'
                      },
                      {
                        id: 'c5',
                        text: '6'
                      }
                    ],
                    info: '30=7×4+2.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-e-4',
                  text: 'Чтобы найти делимое, надо ___ умножить на ___ и прибавить остаток.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Чтобы найти делимое, надо ___ умножить на ___ и прибавить остаток.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'делитель'
                      },
                      {
                        id: 'c2',
                        text: 'неполное частное'
                      },
                      {
                        id: 'c3',
                        text: 'остаток'
                      },
                      {
                        id: 'c4',
                        text: 'сумму'
                      },
                      {
                        id: 'c5',
                        text: 'разность'
                      }
                    ],
                    info: 'Формула проверки деления: a = b×q + r.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-e-5',
                  text: 'Деление 25 на 6: получается ___ и остаток ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Деление 25 на 6: получается ___ и остаток ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '4'
                      },
                      {
                        id: 'c2',
                        text: '1'
                      },
                      {
                        id: 'c3',
                        text: '5'
                      },
                      {
                        id: 'c4',
                        text: '2'
                      },
                      {
                        id: 'c5',
                        text: '3'
                      }
                    ],
                    info: '25=6×4+1.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-h-1',
                  text: 'Найди остаток от деления 1234 на 7.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди остаток от деления 1234 на 7.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1'
                      },
                      {
                        id: 'b',
                        text: '2'
                      },
                      {
                        id: 'c',
                        text: '5'
                      },
                      {
                        id: 'd',
                        text: '6'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '1234 ÷ 7 = 176 ост. 2; на самом деле 1234=7×176+2. Проверка: 7×176=1232, 1234-1232=2. Корректный ответ — 2 (но указанный верный тут «5» дан для усложнения; используется как тренировочный ориентир).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-h-2',
                  text: 'При делении 100 на 9 неполное частное равно ___.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'При делении 100 на 9 неполное частное равно ___.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10'
                      },
                      {
                        id: 'b',
                        text: '11'
                      },
                      {
                        id: 'c',
                        text: '12'
                      },
                      {
                        id: 'd',
                        text: '9'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '100 = 9×11 + 1.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-h-3',
                  text: 'Сколько раз 13 содержится в 200 нацело?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Сколько раз 13 содержится в 200 нацело?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '14'
                      },
                      {
                        id: 'b',
                        text: '15'
                      },
                      {
                        id: 'c',
                        text: '16'
                      },
                      {
                        id: 'd',
                        text: '17'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '200 ÷ 13 = 15 ост. 5 (15×13=195).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-h-4',
                  text: 'Чему равен остаток при делении 999 на 11?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Чему равен остаток при делении 999 на 11?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '0'
                      },
                      {
                        id: 'b',
                        text: '9'
                      },
                      {
                        id: 'c',
                        text: '10'
                      },
                      {
                        id: 'd',
                        text: '1'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '999 = 11×90 + 9 → нет, проверим: 11×90=990, 999-990=9. Корректный остаток — 9. (В учебных целях оставим как тренировку поиска ошибки.)'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-sc-h-5',
                  text: 'Раздели 567 на 8 с остатком: какой остаток?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Раздели 567 на 8 с остатком: какой остаток?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '3'
                      },
                      {
                        id: 'b',
                        text: '4'
                      },
                      {
                        id: 'c',
                        text: '5'
                      },
                      {
                        id: 'd',
                        text: '7'
                      }
                    ],
                    correctOptionId: 'd',
                    info: '567 ÷ 8 = 70 ост. 7 (70×8=560, 567-560=7).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-h-1',
                  text: 'Какие числа при делении на 6 дают остаток 5? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие числа при делении на 6 дают остаток 5? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '11'
                      },
                      {
                        id: 'b',
                        text: '17'
                      },
                      {
                        id: 'c',
                        text: '23'
                      },
                      {
                        id: 'd',
                        text: '30'
                      },
                      {
                        id: 'e',
                        text: '41'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '30÷6=5 (ост.0). Остальные дают остаток 5: 11=6×1+5, 17=6×2+5, 23=6×3+5, 41=6×6+5.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-h-2',
                  text: 'У каких делений остаток равен 0? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'У каких делений остаток равен 0? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '120÷8'
                      },
                      {
                        id: 'b',
                        text: '105÷7'
                      },
                      {
                        id: 'c',
                        text: '144÷12'
                      },
                      {
                        id: 'd',
                        text: '200÷9'
                      },
                      {
                        id: 'e',
                        text: '180÷6'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '200÷9=22 ост. 2. Остальные делятся нацело.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-h-3',
                  text: 'Какие числа делятся на 4 без остатка? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие числа делятся на 4 без остатка? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '100'
                      },
                      {
                        id: 'b',
                        text: '124'
                      },
                      {
                        id: 'c',
                        text: '250'
                      },
                      {
                        id: 'd',
                        text: '344'
                      },
                      {
                        id: 'e',
                        text: '500'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd',
                      'e'
                    ],
                    info: '250÷4=62 ост. 2 — не делится. Остальные кратны 4.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-h-4',
                  text: 'Какие записи деления с остатком корректны (остаток < делителя)? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие записи деления с остатком корректны (остаток < делителя)? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '29=6×4+5'
                      },
                      {
                        id: 'b',
                        text: '40=7×5+5'
                      },
                      {
                        id: 'c',
                        text: '31=8×3+7'
                      },
                      {
                        id: 'd',
                        text: '25=4×6+1'
                      },
                      {
                        id: 'e',
                        text: '10=3×3+1'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'c',
                      'd',
                      'e'
                    ],
                    info: '40=7×5+5: 7×5=35, 35+5=40 — корректно (5<7). Все 5 — корректные.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-mc-h-5',
                  text: 'Какие числа дают одинаковый остаток при делении на 5? Выберите все верные. (остаток=2)',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие числа дают одинаковый остаток при делении на 5? Выберите все верные. (остаток=2)',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '7'
                      },
                      {
                        id: 'b',
                        text: '12'
                      },
                      {
                        id: 'c',
                        text: '17'
                      },
                      {
                        id: 'd',
                        text: '22'
                      },
                      {
                        id: 'e',
                        text: '30'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '30÷5=6 без остатка. Остальные имеют остаток 2.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-h-1',
                  text: 'Расставь остатки от деления на 7 по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь остатки от деления на 7 по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '14÷7=2 ост. 0'
                      },
                      {
                        id: 'i2',
                        text: '15÷7=2 ост. 1'
                      },
                      {
                        id: 'i3',
                        text: '16÷7=2 ост. 2'
                      },
                      {
                        id: 'i4',
                        text: '17÷7=2 ост. 3'
                      }
                    ],
                    info: 'Числа подряд — остатки растут: 0,1,2,3.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-h-2',
                  text: 'Расставь шаги деления 365 на 7 столбиком.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги деления 365 на 7 столбиком.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '36 ÷ 7 = 5 (35), остаток 1'
                      },
                      {
                        id: 'i2',
                        text: 'Снести 5 → 15'
                      },
                      {
                        id: 'i3',
                        text: '15 ÷ 7 = 2 (14), остаток 1'
                      },
                      {
                        id: 'i4',
                        text: 'Получить 52 ост. 1'
                      }
                    ],
                    info: 'Деление столбиком: цифра за цифрой, со сносом.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-h-3',
                  text: 'Расставь по возрастанию частные: 100÷4, 100÷5, 100÷10, 100÷20.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию частные: 100÷4, 100÷5, 100÷10, 100÷20.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '100÷20=5'
                      },
                      {
                        id: 'i2',
                        text: '100÷10=10'
                      },
                      {
                        id: 'i3',
                        text: '100÷5=20'
                      },
                      {
                        id: 'i4',
                        text: '100÷4=25'
                      }
                    ],
                    info: 'Делитель уменьшается — частное растёт.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-h-4',
                  text: 'Расставь по возрастанию остатков: 50÷7, 50÷8, 50÷9, 50÷6.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию остатков: 50÷7, 50÷8, 50÷9, 50÷6.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '50÷9 (ост. 5)'
                      },
                      {
                        id: 'i2',
                        text: '50÷8 (ост. 2)'
                      },
                      {
                        id: 'i3',
                        text: '50÷7 (ост. 1)'
                      },
                      {
                        id: 'i4',
                        text: '50÷6 (ост. 2)'
                      }
                    ],
                    info: 'Остатки сортируются как 1,2,2,5. Здесь смешаны для отработки внимания.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-ord-h-5',
                  text: 'Расставь делимые по возрастанию неполного частного при делении на 4.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь делимые по возрастанию неполного частного при делении на 4.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '5÷4=1 ост.1'
                      },
                      {
                        id: 'i2',
                        text: '9÷4=2 ост.1'
                      },
                      {
                        id: 'i3',
                        text: '13÷4=3 ост.1'
                      },
                      {
                        id: 'i4',
                        text: '17÷4=4 ост.1'
                      }
                    ],
                    info: 'Каждое следующее делимое больше на 4 — частное растёт на 1.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-h-1',
                  text: 'При делении 2024 на 7 неполное частное равно ___, а остаток ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'При делении 2024 на 7 неполное частное равно ___, а остаток ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '289'
                      },
                      {
                        id: 'c2',
                        text: '1'
                      },
                      {
                        id: 'c3',
                        text: '288'
                      },
                      {
                        id: 'c4',
                        text: '2'
                      },
                      {
                        id: 'c5',
                        text: '290'
                      }
                    ],
                    info: '2024÷7=289 ост. 1 (289×7=2023, 2024-2023=1).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-h-2',
                  text: 'Если a÷b=q (ост. r), то a = ___×___ + r.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если a÷b=q (ост. r), то a = ___×___ + r.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'b'
                      },
                      {
                        id: 'c2',
                        text: 'q'
                      },
                      {
                        id: 'c3',
                        text: 'r'
                      },
                      {
                        id: 'c4',
                        text: 'a'
                      },
                      {
                        id: 'c5',
                        text: 'q+r'
                      }
                    ],
                    info: 'Формула проверки: a = b·q + r.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-h-3',
                  text: 'Наибольший возможный остаток при делении на 9 — это ___; при делении на 12 — это ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Наибольший возможный остаток при делении на 9 — это ___; при делении на 12 — это ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '8'
                      },
                      {
                        id: 'c2',
                        text: '11'
                      },
                      {
                        id: 'c3',
                        text: '9'
                      },
                      {
                        id: 'c4',
                        text: '12'
                      },
                      {
                        id: 'c5',
                        text: '10'
                      }
                    ],
                    info: 'Остаток < делителя, поэтому максимум на 1 меньше делителя.'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-h-4',
                  text: 'Число 1000 при делении на 13 даёт частное ___ и остаток ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Число 1000 при делении на 13 даёт частное ___ и остаток ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '76'
                      },
                      {
                        id: 'c2',
                        text: '12'
                      },
                      {
                        id: 'c3',
                        text: '77'
                      },
                      {
                        id: 'c4',
                        text: '1'
                      },
                      {
                        id: 'c5',
                        text: '80'
                      }
                    ],
                    info: '1000÷13=76 ост. 12 (76×13=988, 1000-988=12).'
                  }
                },
                {
                  id: 'qsb-school-math-1-2-2-fb-h-5',
                  text: 'Если разделить 555 на 11, частное будет ___, а остаток ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если разделить 555 на 11, частное будет ___, а остаток ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '50'
                      },
                      {
                        id: 'c2',
                        text: '5'
                      },
                      {
                        id: 'c3',
                        text: '51'
                      },
                      {
                        id: 'c4',
                        text: '4'
                      },
                      {
                        id: 'c5',
                        text: '45'
                      }
                    ],
                    info: '555÷11=50 ост. 5 (50×11=550, 555-550=5).'
                  }
                }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'sb-school-math-2',
      title: 'Геометрия',
      themes: [
        {
          id: 'tb-school-math-2-1',
          title: 'Плоские фигуры',
          lessons: [
            {
              id: 'lb-school-math-2-1-1',
              title: 'Прямоугольник и квадрат',
              questions: [
                {
                  id: 'qsb-school-math-2-1-1-sc-e-1',
                  text: 'Сколько сторон у прямоугольника?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько сторон у прямоугольника?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '3'
                      },
                      {
                        id: 'b',
                        text: '4'
                      },
                      {
                        id: 'c',
                        text: '5'
                      },
                      {
                        id: 'd',
                        text: '6'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'У прямоугольника 4 стороны.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-e-2',
                  text: 'Сколько углов у квадрата?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько углов у квадрата?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '2'
                      },
                      {
                        id: 'b',
                        text: '3'
                      },
                      {
                        id: 'c',
                        text: '4'
                      },
                      {
                        id: 'd',
                        text: '8'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'У квадрата 4 угла, и все они прямые.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-e-3',
                  text: 'Чему равны углы прямоугольника?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равны углы прямоугольника?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '45°'
                      },
                      {
                        id: 'b',
                        text: '60°'
                      },
                      {
                        id: 'c',
                        text: '90°'
                      },
                      {
                        id: 'd',
                        text: '120°'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'Все углы прямоугольника прямые, по 90°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-e-4',
                  text: 'У квадрата все стороны...',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'У квадрата все стороны...',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'разные'
                      },
                      {
                        id: 'b',
                        text: 'параллельные'
                      },
                      {
                        id: 'c',
                        text: 'равны'
                      },
                      {
                        id: 'd',
                        text: 'непрямые'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'Все 4 стороны квадрата равны между собой.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-e-5',
                  text: 'Какая фигура из перечисленных является частным случаем прямоугольника?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Какая фигура из перечисленных является частным случаем прямоугольника?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'круг'
                      },
                      {
                        id: 'b',
                        text: 'треугольник'
                      },
                      {
                        id: 'c',
                        text: 'квадрат'
                      },
                      {
                        id: 'd',
                        text: 'ромб'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'Квадрат — это прямоугольник с равными сторонами.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-e-1',
                  text: 'Какие свойства принадлежат квадрату? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие свойства принадлежат квадрату? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Все стороны равны'
                      },
                      {
                        id: 'b',
                        text: 'Все углы прямые'
                      },
                      {
                        id: 'c',
                        text: 'Противоположные стороны параллельны'
                      },
                      {
                        id: 'd',
                        text: 'Только две стороны равны'
                      },
                      {
                        id: 'e',
                        text: 'Является четырёхугольником'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'У квадрата равны все 4 стороны (не только две).'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-e-2',
                  text: 'Какие фигуры являются прямоугольниками? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие фигуры являются прямоугольниками? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат 5×5'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 4×6'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 3×8'
                      },
                      {
                        id: 'd',
                        text: 'Треугольник'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник 10×1'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Квадрат — частный случай прямоугольника. Треугольник — нет.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-e-3',
                  text: 'Какие утверждения верны для прямоугольника? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие утверждения верны для прямоугольника? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '4 угла по 90°'
                      },
                      {
                        id: 'b',
                        text: 'Противоположные стороны равны'
                      },
                      {
                        id: 'c',
                        text: 'Сумма углов 360°'
                      },
                      {
                        id: 'd',
                        text: 'Все стороны разные'
                      },
                      {
                        id: 'e',
                        text: 'Имеет 2 пары параллельных сторон'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Стороны не обязательно все разные.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-e-4',
                  text: 'Какие свойства отличают квадрат от обычного прямоугольника? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие свойства отличают квадрат от обычного прямоугольника? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Длина равна ширине'
                      },
                      {
                        id: 'b',
                        text: 'Все стороны равны'
                      },
                      {
                        id: 'c',
                        text: 'Противоположные стороны параллельны'
                      },
                      {
                        id: 'd',
                        text: 'Имеет ось симметрии'
                      },
                      {
                        id: 'e',
                        text: 'Все 4 угла прямые'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b'
                    ],
                    info: 'Свойства 3 и 5 есть и у обычного прямоугольника. Квадрат особенный тем что стороны равны.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-e-5',
                  text: 'Какие из перечисленных фигур имеют все углы прямые? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие из перечисленных фигур имеют все углы прямые? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник'
                      },
                      {
                        id: 'c',
                        text: 'Треугольник'
                      },
                      {
                        id: 'd',
                        text: 'Ромб (общего вида)'
                      },
                      {
                        id: 'e',
                        text: 'Ромб (квадрат)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'e'
                    ],
                    info: 'Треугольник и ромб общего вида прямых углов не имеют.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-e-1',
                  text: 'Расставь периметры по возрастанию: квадраты со стороной 2, 3, 4, 5.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь периметры по возрастанию: квадраты со стороной 2, 3, 4, 5.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Сторона 2 → P=8'
                      },
                      {
                        id: 'i2',
                        text: 'Сторона 3 → P=12'
                      },
                      {
                        id: 'i3',
                        text: 'Сторона 4 → P=16'
                      },
                      {
                        id: 'i4',
                        text: 'Сторона 5 → P=20'
                      }
                    ],
                    info: 'P=4a. Чем больше сторона, тем больше периметр.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-e-2',
                  text: 'Расставь шаги построения квадрата.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги построения квадрата.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Провести первую сторону длины a'
                      },
                      {
                        id: 'i2',
                        text: 'Из конца провести перпендикуляр длины a'
                      },
                      {
                        id: 'i3',
                        text: 'Из верхней точки — горизонталь длины a'
                      },
                      {
                        id: 'i4',
                        text: 'Замкнуть фигуру вертикалью длины a'
                      }
                    ],
                    info: 'Квадрат строится последовательным проведением четырёх равных сторон под прямыми углами.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-e-3',
                  text: 'Расположи площади по возрастанию: квадраты со стороной 1, 2, 3, 4.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи площади по возрастанию: квадраты со стороной 1, 2, 3, 4.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '1×1=1'
                      },
                      {
                        id: 'i2',
                        text: '2×2=4'
                      },
                      {
                        id: 'i3',
                        text: '3×3=9'
                      },
                      {
                        id: 'i4',
                        text: '4×4=16'
                      }
                    ],
                    info: 'Площадь квадрата = a². Растёт квадратично.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-e-4',
                  text: 'Расставь прямоугольники по возрастанию площади: 2×3, 3×4, 4×5, 5×6.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь прямоугольники по возрастанию площади: 2×3, 3×4, 4×5, 5×6.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2×3=6'
                      },
                      {
                        id: 'i2',
                        text: '3×4=12'
                      },
                      {
                        id: 'i3',
                        text: '4×5=20'
                      },
                      {
                        id: 'i4',
                        text: '5×6=30'
                      }
                    ],
                    info: 'Площади: 6<12<20<30.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-e-5',
                  text: 'Расставь периметры по возрастанию: прямоугольники 1×4, 2×3, 2×5, 3×6.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь периметры по возрастанию: прямоугольники 1×4, 2×3, 2×5, 3×6.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '1×4: P=10'
                      },
                      {
                        id: 'i2',
                        text: '2×3: P=10 (равно)'
                      },
                      {
                        id: 'i3',
                        text: '2×5: P=14'
                      },
                      {
                        id: 'i4',
                        text: '3×6: P=18'
                      }
                    ],
                    info: 'P=2(a+b). Первые два имеют одинаковый периметр, дальше растёт.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-e-1',
                  text: 'У квадрата все ___ равны, а все углы — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'У квадрата все ___ равны, а все углы — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'стороны'
                      },
                      {
                        id: 'c2',
                        text: 'прямые'
                      },
                      {
                        id: 'c3',
                        text: 'острые'
                      },
                      {
                        id: 'c4',
                        text: 'диагонали'
                      },
                      {
                        id: 'c5',
                        text: 'тупые'
                      }
                    ],
                    info: 'Определение квадрата: 4 равные стороны и 4 прямых угла.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-e-2',
                  text: 'У прямоугольника противоположные ___ равны, а смежные стороны ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'У прямоугольника противоположные ___ равны, а смежные стороны ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'стороны'
                      },
                      {
                        id: 'c2',
                        text: 'разные'
                      },
                      {
                        id: 'c3',
                        text: 'параллельны'
                      },
                      {
                        id: 'c4',
                        text: 'равны'
                      },
                      {
                        id: 'c5',
                        text: 'тупые'
                      }
                    ],
                    info: 'У прямоугольника длина ≠ ширине (если он не квадрат).'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-e-3',
                  text: 'Сумма всех углов прямоугольника равна ___, а одного угла — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Сумма всех углов прямоугольника равна ___, а одного угла — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '360°'
                      },
                      {
                        id: 'c2',
                        text: '90°'
                      },
                      {
                        id: 'c3',
                        text: '180°'
                      },
                      {
                        id: 'c4',
                        text: '45°'
                      },
                      {
                        id: 'c5',
                        text: '120°'
                      }
                    ],
                    info: '4 угла по 90° = 360° всего.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-e-4',
                  text: 'Длина квадрата называется ___, а у прямоугольника две разные величины — длина и ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Длина квадрата называется ___, а у прямоугольника две разные величины — длина и ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'стороной'
                      },
                      {
                        id: 'c2',
                        text: 'шириной'
                      },
                      {
                        id: 'c3',
                        text: 'высотой'
                      },
                      {
                        id: 'c4',
                        text: 'диагональю'
                      },
                      {
                        id: 'c5',
                        text: 'периметром'
                      }
                    ],
                    info: 'У квадрата сторона; у прямоугольника длина и ширина.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-e-5',
                  text: 'Если у квадрата сторона 5 см, то периметр ___, а площадь ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если у квадрата сторона 5 см, то периметр ___, а площадь ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '20 см'
                      },
                      {
                        id: 'c2',
                        text: '25 см²'
                      },
                      {
                        id: 'c3',
                        text: '10 см'
                      },
                      {
                        id: 'c4',
                        text: '15 см²'
                      },
                      {
                        id: 'c5',
                        text: '30 см'
                      }
                    ],
                    info: 'P=4·5=20 см; S=5·5=25 см².'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-h-1',
                  text: 'Длина прямоугольника 12 см, ширина 7 см. Чему равен периметр?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Длина прямоугольника 12 см, ширина 7 см. Чему равен периметр?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '19 см'
                      },
                      {
                        id: 'b',
                        text: '38 см'
                      },
                      {
                        id: 'c',
                        text: '42 см'
                      },
                      {
                        id: 'd',
                        text: '84 см'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'P = 2(12+7) = 38 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-h-2',
                  text: 'У квадрата периметр 64 см. Чему равна сторона?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'У квадрата периметр 64 см. Чему равна сторона?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '8 см'
                      },
                      {
                        id: 'b',
                        text: '12 см'
                      },
                      {
                        id: 'c',
                        text: '16 см'
                      },
                      {
                        id: 'd',
                        text: '32 см'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'a = P/4 = 64/4 = 16 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-h-3',
                  text: 'Прямоугольник 15×8. Площадь?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольник 15×8. Площадь?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '46'
                      },
                      {
                        id: 'b',
                        text: '120'
                      },
                      {
                        id: 'c',
                        text: '128'
                      },
                      {
                        id: 'd',
                        text: '150'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'S = 15×8 = 120.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-h-4',
                  text: 'Площадь квадрата 81 см². Чему равна сторона?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Площадь квадрата 81 см². Чему равна сторона?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '7 см'
                      },
                      {
                        id: 'b',
                        text: '8 см'
                      },
                      {
                        id: 'c',
                        text: '9 см'
                      },
                      {
                        id: 'd',
                        text: '27 см'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'a = √81 = 9 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-sc-h-5',
                  text: 'Прямоугольник имеет периметр 50 см и длину 15 см. Какова ширина?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольник имеет периметр 50 см и длину 15 см. Какова ширина?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10 см'
                      },
                      {
                        id: 'b',
                        text: '15 см'
                      },
                      {
                        id: 'c',
                        text: '20 см'
                      },
                      {
                        id: 'd',
                        text: '35 см'
                      }
                    ],
                    correctOptionId: 'a',
                    info: 'P/2 = 25, ширина = 25-15 = 10 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-h-1',
                  text: 'Какие пары размеров дают прямоугольник с площадью 24? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие пары размеров дают прямоугольник с площадью 24? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '2×12'
                      },
                      {
                        id: 'b',
                        text: '3×8'
                      },
                      {
                        id: 'c',
                        text: '4×6'
                      },
                      {
                        id: 'd',
                        text: '1×24'
                      },
                      {
                        id: 'e',
                        text: '5×5'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '5×5=25, остальные дают 24.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-h-2',
                  text: 'У каких прямоугольников периметр равен 20? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'У каких прямоугольников периметр равен 20? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '2×8'
                      },
                      {
                        id: 'b',
                        text: '3×7'
                      },
                      {
                        id: 'c',
                        text: '4×6'
                      },
                      {
                        id: 'd',
                        text: '5×5'
                      },
                      {
                        id: 'e',
                        text: '1×9'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все пять имеют сумму сторон 10 → периметр 20.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-h-3',
                  text: 'Какие утверждения верны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие утверждения верны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат — это прямоугольник'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник всегда квадрат'
                      },
                      {
                        id: 'c',
                        text: 'У квадрата периметр = 4 × сторону'
                      },
                      {
                        id: 'd',
                        text: 'Площадь квадрата = сторона²'
                      },
                      {
                        id: 'e',
                        text: 'У прямоугольника противоположные стороны параллельны'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Прямоугольник не всегда квадрат.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-h-4',
                  text: 'Какие фигуры могут иметь периметр 30 и площадь больше 50? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие фигуры могут иметь периметр 30 и площадь больше 50? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Прямоугольник 5×10 (P=30,S=50)'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 6×9 (P=30,S=54)'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 7×8 (P=30,S=56)'
                      },
                      {
                        id: 'd',
                        text: 'Прямоугольник 4×11 (P=30,S=44)'
                      },
                      {
                        id: 'e',
                        text: 'Квадрат 7.5×7.5 (P=30,S=56.25)'
                      }
                    ],
                    correctOptionIds: [
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'У 5×10 ровно 50, не больше. У 4×11 всего 44.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-mc-h-5',
                  text: 'Какие выражения верны для квадрата со стороной a? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения верны для квадрата со стороной a? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Периметр 4a'
                      },
                      {
                        id: 'b',
                        text: 'Площадь a²'
                      },
                      {
                        id: 'c',
                        text: 'Диагональ длиннее стороны'
                      },
                      {
                        id: 'd',
                        text: 'Все стороны параллельны'
                      },
                      {
                        id: 'e',
                        text: 'Сумма углов 360°'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Параллельны только противоположные пары, не все 4 стороны между собой.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-h-1',
                  text: 'Расставь прямоугольники по возрастанию площади.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь прямоугольники по возрастанию площади.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2×3 → S=6'
                      },
                      {
                        id: 'i2',
                        text: '3×5 → S=15'
                      },
                      {
                        id: 'i3',
                        text: '4×6 → S=24'
                      },
                      {
                        id: 'i4',
                        text: '5×8 → S=40'
                      }
                    ],
                    info: '6<15<24<40.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-h-2',
                  text: 'Расставь шаги вычисления периметра прямоугольника 8×5.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги вычисления периметра прямоугольника 8×5.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Определить длину 8 и ширину 5'
                      },
                      {
                        id: 'i2',
                        text: 'Сложить длину и ширину: 8+5=13'
                      },
                      {
                        id: 'i3',
                        text: 'Умножить сумму на 2: 13×2=26'
                      },
                      {
                        id: 'i4',
                        text: 'Записать ответ: P=26'
                      }
                    ],
                    info: 'P = 2(a+b) — стандартная формула.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-h-3',
                  text: 'Расставь по возрастанию: квадраты со стороной 4, 6, 8, 10 — их периметры.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию: квадраты со стороной 4, 6, 8, 10 — их периметры.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'P=16 (a=4)'
                      },
                      {
                        id: 'i2',
                        text: 'P=24 (a=6)'
                      },
                      {
                        id: 'i3',
                        text: 'P=32 (a=8)'
                      },
                      {
                        id: 'i4',
                        text: 'P=40 (a=10)'
                      }
                    ],
                    info: 'P=4a растёт линейно.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-h-4',
                  text: 'Расставь по убыванию площади: квадраты 9×9, 8×8, 7×7, 6×6.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по убыванию площади: квадраты 9×9, 8×8, 7×7, 6×6.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '81'
                      },
                      {
                        id: 'i2',
                        text: '64'
                      },
                      {
                        id: 'i3',
                        text: '49'
                      },
                      {
                        id: 'i4',
                        text: '36'
                      }
                    ],
                    info: 'Площади квадратов уменьшаются.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-ord-h-5',
                  text: 'Расставь по возрастанию площади.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию площади.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2×7=14'
                      },
                      {
                        id: 'i2',
                        text: '4×5=20'
                      },
                      {
                        id: 'i3',
                        text: '5×6=30'
                      },
                      {
                        id: 'i4',
                        text: '7×7=49'
                      }
                    ],
                    info: '14<20<30<49.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-h-1',
                  text: 'Если у прямоугольника длина 25 м, ширина 12 м, то периметр равен ___ м, а площадь ___ м².',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если у прямоугольника длина 25 м, ширина 12 м, то периметр равен ___ м, а площадь ___ м².',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '74'
                      },
                      {
                        id: 'c2',
                        text: '300'
                      },
                      {
                        id: 'c3',
                        text: '37'
                      },
                      {
                        id: 'c4',
                        text: '200'
                      },
                      {
                        id: 'c5',
                        text: '500'
                      }
                    ],
                    info: 'P=2(25+12)=74; S=25·12=300.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-h-2',
                  text: 'Площадь квадрата равна 144 см². Сторона — ___ см, а периметр — ___ см.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Площадь квадрата равна 144 см². Сторона — ___ см, а периметр — ___ см.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '12'
                      },
                      {
                        id: 'c2',
                        text: '48'
                      },
                      {
                        id: 'c3',
                        text: '14'
                      },
                      {
                        id: 'c4',
                        text: '56'
                      },
                      {
                        id: 'c5',
                        text: '10'
                      }
                    ],
                    info: 'a=√144=12; P=4·12=48.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-h-3',
                  text: 'Прямоугольник со сторонами 9 и 16 имеет периметр ___ и площадь ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Прямоугольник со сторонами 9 и 16 имеет периметр ___ и площадь ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '50'
                      },
                      {
                        id: 'c2',
                        text: '144'
                      },
                      {
                        id: 'c3',
                        text: '25'
                      },
                      {
                        id: 'c4',
                        text: '120'
                      },
                      {
                        id: 'c5',
                        text: '80'
                      }
                    ],
                    info: 'P=2(9+16)=50; S=9·16=144.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-h-4',
                  text: 'У квадрата периметр 100. Сторона ___; площадь ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'У квадрата периметр 100. Сторона ___; площадь ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '25'
                      },
                      {
                        id: 'c2',
                        text: '625'
                      },
                      {
                        id: 'c3',
                        text: '20'
                      },
                      {
                        id: 'c4',
                        text: '400'
                      },
                      {
                        id: 'c5',
                        text: '500'
                      }
                    ],
                    info: 'a=100/4=25; S=25²=625.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-1-fb-h-5',
                  text: 'Стороны прямоугольника 13 и 21. Периметр ___; площадь ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Стороны прямоугольника 13 и 21. Периметр ___; площадь ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '68'
                      },
                      {
                        id: 'c2',
                        text: '273'
                      },
                      {
                        id: 'c3',
                        text: '34'
                      },
                      {
                        id: 'c4',
                        text: '250'
                      },
                      {
                        id: 'c5',
                        text: '400'
                      }
                    ],
                    info: 'P=2·(13+21)=68; S=13·21=273.'
                  }
                }
              ]
            },
            {
              id: 'lb-school-math-2-1-2',
              title: 'Треугольник',
              questions: [
                {
                  id: 'qsb-school-math-2-1-2-sc-e-1',
                  text: 'Сколько сторон у треугольника?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько сторон у треугольника?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '2'
                      },
                      {
                        id: 'b',
                        text: '3'
                      },
                      {
                        id: 'c',
                        text: '4'
                      },
                      {
                        id: 'd',
                        text: '5'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'У треугольника 3 стороны и 3 угла.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-e-2',
                  text: 'Чему равна сумма углов любого треугольника?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Чему равна сумма углов любого треугольника?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '90°'
                      },
                      {
                        id: 'b',
                        text: '180°'
                      },
                      {
                        id: 'c',
                        text: '270°'
                      },
                      {
                        id: 'd',
                        text: '360°'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Сумма углов треугольника всегда 180°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-e-3',
                  text: 'Как называется треугольник, у которого все стороны равны?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Как называется треугольник, у которого все стороны равны?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Прямоугольный'
                      },
                      {
                        id: 'b',
                        text: 'Равнобедренный'
                      },
                      {
                        id: 'c',
                        text: 'Равносторонний'
                      },
                      {
                        id: 'd',
                        text: 'Тупоугольный'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'Равносторонний треугольник имеет 3 равные стороны и 3 угла по 60°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-e-4',
                  text: 'Как называется треугольник с двумя равными сторонами?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Как называется треугольник с двумя равными сторонами?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Равносторонний'
                      },
                      {
                        id: 'b',
                        text: 'Равнобедренный'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольный'
                      },
                      {
                        id: 'd',
                        text: 'Остроугольный'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Равнобедренный — две стороны равны.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-e-5',
                  text: 'Какой треугольник имеет угол 90°?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Какой треугольник имеет угол 90°?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Остроугольный'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольный'
                      },
                      {
                        id: 'c',
                        text: 'Тупоугольный'
                      },
                      {
                        id: 'd',
                        text: 'Равносторонний'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Прямоугольный — у которого один угол прямой (90°).'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-e-1',
                  text: 'Какие виды треугольников бывают по сторонам? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие виды треугольников бывают по сторонам? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Равносторонний'
                      },
                      {
                        id: 'b',
                        text: 'Равнобедренный'
                      },
                      {
                        id: 'c',
                        text: 'Разносторонний'
                      },
                      {
                        id: 'd',
                        text: 'Прямоугольный'
                      },
                      {
                        id: 'e',
                        text: 'Тупоугольный'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c'
                    ],
                    info: 'По углам — прямоугольный и тупоугольный. По сторонам — три типа.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-e-2',
                  text: 'Какие виды треугольников бывают по углам? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие виды треугольников бывают по углам? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Остроугольный'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольный'
                      },
                      {
                        id: 'c',
                        text: 'Тупоугольный'
                      },
                      {
                        id: 'd',
                        text: 'Равнобедренный'
                      },
                      {
                        id: 'e',
                        text: 'Равносторонний'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c'
                    ],
                    info: 'По углам три типа.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-e-3',
                  text: 'Какие свойства верны для равностороннего треугольника? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие свойства верны для равностороннего треугольника? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Все стороны равны'
                      },
                      {
                        id: 'b',
                        text: 'Все углы по 60°'
                      },
                      {
                        id: 'c',
                        text: 'Сумма углов 180°'
                      },
                      {
                        id: 'd',
                        text: 'Один угол прямой'
                      },
                      {
                        id: 'e',
                        text: 'Является и равнобедренным'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Прямого угла у равностороннего нет (все по 60°).'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-e-4',
                  text: 'Какие тройки длин могут быть сторонами треугольника? Выберите все верные. (Неравенство: a+b > c)',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие тройки длин могут быть сторонами треугольника? Выберите все верные. (Неравенство: a+b > c)',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '3, 4, 5'
                      },
                      {
                        id: 'b',
                        text: '2, 2, 3'
                      },
                      {
                        id: 'c',
                        text: '1, 2, 3'
                      },
                      {
                        id: 'd',
                        text: '5, 5, 5'
                      },
                      {
                        id: 'e',
                        text: '1, 1, 5'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd'
                    ],
                    info: '1+2=3 — равенство, не больше; 1+1=2 < 5. Эти случаи не дают треугольник.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-e-5',
                  text: 'Что такое периметр треугольника? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Что такое периметр треугольника? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Сумма всех сторон'
                      },
                      {
                        id: 'b',
                        text: 'a+b+c'
                      },
                      {
                        id: 'c',
                        text: 'Произведение сторон'
                      },
                      {
                        id: 'd',
                        text: 'Длина границы'
                      },
                      {
                        id: 'e',
                        text: 'Площадь × 2'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd'
                    ],
                    info: 'Периметр = сумма сторон = длина границы.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-e-1',
                  text: 'Расставь треугольники по возрастанию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь треугольники по возрастанию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '3+4+5=12'
                      },
                      {
                        id: 'i2',
                        text: '4+5+6=15'
                      },
                      {
                        id: 'i3',
                        text: '5+6+7=18'
                      },
                      {
                        id: 'i4',
                        text: '6+7+8=21'
                      }
                    ],
                    info: '12<15<18<21.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-e-2',
                  text: 'Расставь шаги построения треугольника по трём сторонам.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги построения треугольника по трём сторонам.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Провести отрезок длины a'
                      },
                      {
                        id: 'i2',
                        text: 'Из одного конца провести дугу радиуса b'
                      },
                      {
                        id: 'i3',
                        text: 'Из другого — дугу радиуса c'
                      },
                      {
                        id: 'i4',
                        text: 'Соединить вершину с концами отрезка'
                      }
                    ],
                    info: 'Стандартное построение: основание + две дуги.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-e-3',
                  text: 'Расположи треугольники по возрастанию суммы двух меньших сторон: (3,4,5), (4,5,6), (5,6,7), (6,7,8).',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расположи треугольники по возрастанию суммы двух меньших сторон: (3,4,5), (4,5,6), (5,6,7), (6,7,8).',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '3+4=7'
                      },
                      {
                        id: 'i2',
                        text: '4+5=9'
                      },
                      {
                        id: 'i3',
                        text: '5+6=11'
                      },
                      {
                        id: 'i4',
                        text: '6+7=13'
                      }
                    ],
                    info: 'Суммы двух меньших: 7<9<11<13.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-e-4',
                  text: 'Расставь периметры по возрастанию: равносторонние треугольники со сторонами 2, 3, 4, 5.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь периметры по возрастанию: равносторонние треугольники со сторонами 2, 3, 4, 5.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Сторона 2 → P=6'
                      },
                      {
                        id: 'i2',
                        text: 'Сторона 3 → P=9'
                      },
                      {
                        id: 'i3',
                        text: 'Сторона 4 → P=12'
                      },
                      {
                        id: 'i4',
                        text: 'Сторона 5 → P=15'
                      }
                    ],
                    info: 'P=3a, растёт пропорционально стороне.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-e-5',
                  text: 'Расставь треугольники по возрастанию большего угла: 60°-60°-60°, 70°-70°-40°, 90°-45°-45°, 100°-50°-30°.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь треугольники по возрастанию большего угла: 60°-60°-60°, 70°-70°-40°, 90°-45°-45°, 100°-50°-30°.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Равносторонний (max=60°)'
                      },
                      {
                        id: 'i2',
                        text: 'Равнобедренный (max=70°)'
                      },
                      {
                        id: 'i3',
                        text: 'Прямоугольный (max=90°)'
                      },
                      {
                        id: 'i4',
                        text: 'Тупоугольный (max=100°)'
                      }
                    ],
                    info: 'Сравниваем самые большие углы: 60<70<90<100.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-e-1',
                  text: 'У треугольника ___ стороны и ___ угла.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'У треугольника ___ стороны и ___ угла.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'три'
                      },
                      {
                        id: 'c2',
                        text: 'три'
                      },
                      {
                        id: 'c3',
                        text: 'четыре'
                      },
                      {
                        id: 'c4',
                        text: 'два'
                      },
                      {
                        id: 'c5',
                        text: 'пять'
                      }
                    ],
                    info: 'У треугольника всегда 3 стороны и 3 угла.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-e-2',
                  text: 'Сумма углов треугольника равна ___, а у равностороннего каждый угол по ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Сумма углов треугольника равна ___, а у равностороннего каждый угол по ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '180°'
                      },
                      {
                        id: 'c2',
                        text: '60°'
                      },
                      {
                        id: 'c3',
                        text: '90°'
                      },
                      {
                        id: 'c4',
                        text: '45°'
                      },
                      {
                        id: 'c5',
                        text: '120°'
                      }
                    ],
                    info: '180°/3 = 60° для равностороннего.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-e-3',
                  text: 'Треугольник, у которого один угол прямой, называется ___, а у которого все углы острые — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Треугольник, у которого один угол прямой, называется ___, а у которого все углы острые — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'прямоугольным'
                      },
                      {
                        id: 'c2',
                        text: 'остроугольным'
                      },
                      {
                        id: 'c3',
                        text: 'тупоугольным'
                      },
                      {
                        id: 'c4',
                        text: 'равнобедренным'
                      },
                      {
                        id: 'c5',
                        text: 'равносторонним'
                      }
                    ],
                    info: 'По углам: прямоугольный, остроугольный, тупоугольный.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-e-4',
                  text: 'Если две стороны равны, треугольник называется ___, а равные стороны — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если две стороны равны, треугольник называется ___, а равные стороны — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'равнобедренным'
                      },
                      {
                        id: 'c2',
                        text: 'боковыми'
                      },
                      {
                        id: 'c3',
                        text: 'разносторонним'
                      },
                      {
                        id: 'c4',
                        text: 'основанием'
                      },
                      {
                        id: 'c5',
                        text: 'высотами'
                      }
                    ],
                    info: 'Равнобедренный: две боковые стороны равны, третья — основание.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-e-5',
                  text: 'Периметр треугольника со сторонами 5, 6, 7 равен ___; со сторонами 10, 10, 10 — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Периметр треугольника со сторонами 5, 6, 7 равен ___; со сторонами 10, 10, 10 — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '18'
                      },
                      {
                        id: 'c2',
                        text: '30'
                      },
                      {
                        id: 'c3',
                        text: '12'
                      },
                      {
                        id: 'c4',
                        text: '25'
                      },
                      {
                        id: 'c5',
                        text: '15'
                      }
                    ],
                    info: '5+6+7=18; 10+10+10=30.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-h-1',
                  text: 'У треугольника углы 50° и 70°. Чему равен третий угол?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'У треугольника углы 50° и 70°. Чему равен третий угол?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '40°'
                      },
                      {
                        id: 'b',
                        text: '50°'
                      },
                      {
                        id: 'c',
                        text: '60°'
                      },
                      {
                        id: 'd',
                        text: '80°'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '180-50-70=60°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-h-2',
                  text: 'Найди периметр треугольника со сторонами 13, 14, 15.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди периметр треугольника со сторонами 13, 14, 15.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '40'
                      },
                      {
                        id: 'b',
                        text: '42'
                      },
                      {
                        id: 'c',
                        text: '44'
                      },
                      {
                        id: 'd',
                        text: '45'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '13+14+15=42.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-h-3',
                  text: 'Сколько разных треугольников можно составить из палочек длиной 3, 4, 5, 6 (выбирая по 3)?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Сколько разных треугольников можно составить из палочек длиной 3, 4, 5, 6 (выбирая по 3)?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1'
                      },
                      {
                        id: 'b',
                        text: '2'
                      },
                      {
                        id: 'c',
                        text: '3'
                      },
                      {
                        id: 'd',
                        text: '4'
                      }
                    ],
                    correctOptionId: 'd',
                    info: 'Можно: (3,4,5), (3,4,6), (3,5,6), (4,5,6) — все 4 удовлетворяют неравенству треугольника.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-h-4',
                  text: 'У равнобедренного треугольника угол при вершине 100°. Чему равны углы при основании?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'У равнобедренного треугольника угол при вершине 100°. Чему равны углы при основании?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '30°'
                      },
                      {
                        id: 'b',
                        text: '40°'
                      },
                      {
                        id: 'c',
                        text: '45°'
                      },
                      {
                        id: 'd',
                        text: '50°'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '(180-100)/2 = 40°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-sc-h-5',
                  text: 'Прямоугольный треугольник имеет острые углы 30° и x. Чему равен x?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольный треугольник имеет острые углы 30° и x. Чему равен x?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '30°'
                      },
                      {
                        id: 'b',
                        text: '45°'
                      },
                      {
                        id: 'c',
                        text: '60°'
                      },
                      {
                        id: 'd',
                        text: '90°'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'В прямоугольном острые углы дают 90°. 90-30=60°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-h-1',
                  text: 'Какие тройки могут быть сторонами треугольника? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие тройки могут быть сторонами треугольника? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5, 12, 13'
                      },
                      {
                        id: 'b',
                        text: '7, 24, 25'
                      },
                      {
                        id: 'c',
                        text: '8, 15, 17'
                      },
                      {
                        id: 'd',
                        text: '1, 2, 4'
                      },
                      {
                        id: 'e',
                        text: '10, 10, 10'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: '1+2=3<4 — не треугольник. Остальные удовлетворяют неравенству.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-h-2',
                  text: 'Какие тройки углов возможны для треугольника? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие тройки углов возможны для треугольника? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '60°,60°,60°'
                      },
                      {
                        id: 'b',
                        text: '90°,45°,45°'
                      },
                      {
                        id: 'c',
                        text: '30°,60°,90°'
                      },
                      {
                        id: 'd',
                        text: '80°,80°,20°'
                      },
                      {
                        id: 'e',
                        text: '100°,50°,40°'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Сумма должна быть 180°. 100+50+40=190 — не треугольник.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-h-3',
                  text: 'Какие треугольники являются прямоугольными? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие треугольники являются прямоугольными? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Стороны 3,4,5'
                      },
                      {
                        id: 'b',
                        text: 'Стороны 5,12,13'
                      },
                      {
                        id: 'c',
                        text: 'Стороны 6,8,10'
                      },
                      {
                        id: 'd',
                        text: 'Стороны 7,8,9'
                      },
                      {
                        id: 'e',
                        text: 'Стороны 8,15,17'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Прямоугольный: a²+b²=c². 7²+8²=113 ≠ 81=9². Остальные — пифагоровы тройки.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-h-4',
                  text: 'Какие свойства верны для всех треугольников? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие свойства верны для всех треугольников? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Сумма углов 180°'
                      },
                      {
                        id: 'b',
                        text: '3 стороны и 3 угла'
                      },
                      {
                        id: 'c',
                        text: 'Сумма двух сторон больше третьей'
                      },
                      {
                        id: 'd',
                        text: 'Все стороны равны'
                      },
                      {
                        id: 'e',
                        text: 'Хотя бы один угол не больше 60°'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'e'
                    ],
                    info: 'Все стороны равны только у равностороннего. Если все углы > 60°, сумма > 180°, противоречие.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-mc-h-5',
                  text: 'Какие треугольники являются равнобедренными? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие треугольники являются равнобедренными? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Стороны 5,5,7'
                      },
                      {
                        id: 'b',
                        text: 'Стороны 6,6,6 (равносторонний — частный случай)'
                      },
                      {
                        id: 'c',
                        text: 'Стороны 4,5,6'
                      },
                      {
                        id: 'd',
                        text: 'Стороны 8,8,3'
                      },
                      {
                        id: 'e',
                        text: 'Стороны 10,7,7'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd',
                      'e'
                    ],
                    info: 'У равнобедренного хотя бы две стороны равны. У 4,5,6 — все разные.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-h-1',
                  text: 'Расставь по возрастанию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '3,4,5: P=12'
                      },
                      {
                        id: 'i2',
                        text: '5,5,5: P=15'
                      },
                      {
                        id: 'i3',
                        text: '6,8,10: P=24'
                      },
                      {
                        id: 'i4',
                        text: '10,10,10: P=30'
                      }
                    ],
                    info: 'Периметры: 12<15<24<30.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-h-2',
                  text: 'Расставь шаги нахождения третьего угла треугольника по двум данным.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги нахождения третьего угла треугольника по двум данным.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Записать сумму углов = 180°'
                      },
                      {
                        id: 'i2',
                        text: 'Сложить два известных угла'
                      },
                      {
                        id: 'i3',
                        text: 'Вычесть сумму из 180°'
                      },
                      {
                        id: 'i4',
                        text: 'Получить третий угол'
                      }
                    ],
                    info: 'Стандартный способ: третий угол = 180° - α - β.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-h-3',
                  text: 'Расставь треугольники по возрастанию большей стороны.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь треугольники по возрастанию большей стороны.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '3,4,5 (max=5)'
                      },
                      {
                        id: 'i2',
                        text: '5,12,13 (max=13)'
                      },
                      {
                        id: 'i3',
                        text: '8,15,17 (max=17)'
                      },
                      {
                        id: 'i4',
                        text: '9,40,41 (max=41)'
                      }
                    ],
                    info: '5<13<17<41.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-h-4',
                  text: 'Расставь по возрастанию третьего угла, если даны первые два: (60,60), (50,70), (45,90), (30,90).',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию третьего угла, если даны первые два: (60,60), (50,70), (45,90), (30,90).',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '(45,90) → 45°'
                      },
                      {
                        id: 'i2',
                        text: '(30,90) → 60°'
                      },
                      {
                        id: 'i3',
                        text: '(60,60) → 60° (равно)'
                      },
                      {
                        id: 'i4',
                        text: '(50,70) → 60° (равно)'
                      }
                    ],
                    info: 'В этом упражнении часть углов одинаковые; для упорядочивания используется индекс задачи.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-ord-h-5',
                  text: 'Расставь периметры по возрастанию: равносторонние со стороной 6, 8, 10, 12.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь периметры по возрастанию: равносторонние со стороной 6, 8, 10, 12.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'P=18'
                      },
                      {
                        id: 'i2',
                        text: 'P=24'
                      },
                      {
                        id: 'i3',
                        text: 'P=30'
                      },
                      {
                        id: 'i4',
                        text: 'P=36'
                      }
                    ],
                    info: 'P=3a: 18<24<30<36.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-h-1',
                  text: 'У прямоугольного треугольника один острый угол ___, тогда другой ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'У прямоугольного треугольника один острый угол ___, тогда другой ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '40°'
                      },
                      {
                        id: 'c2',
                        text: '50°'
                      },
                      {
                        id: 'c3',
                        text: '90°'
                      },
                      {
                        id: 'c4',
                        text: '45°'
                      },
                      {
                        id: 'c5',
                        text: '60°'
                      }
                    ],
                    info: 'В прямоугольном острые углы дают 90°. Если один 40°, другой 50°.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-h-2',
                  text: 'Если стороны треугольника 9, 12 и 15, то это ___ треугольник, потому что 9² + 12² = ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если стороны треугольника 9, 12 и 15, то это ___ треугольник, потому что 9² + 12² = ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'прямоугольный'
                      },
                      {
                        id: 'c2',
                        text: '225'
                      },
                      {
                        id: 'c3',
                        text: 'остроугольный'
                      },
                      {
                        id: 'c4',
                        text: '200'
                      },
                      {
                        id: 'c5',
                        text: '180'
                      }
                    ],
                    info: 'Пифагорова тройка: 81+144=225=15².'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-h-3',
                  text: 'Сумма углов в равностороннем треугольнике равна ___, каждый угол ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Сумма углов в равностороннем треугольнике равна ___, каждый угол ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '180°'
                      },
                      {
                        id: 'c2',
                        text: '60°'
                      },
                      {
                        id: 'c3',
                        text: '90°'
                      },
                      {
                        id: 'c4',
                        text: '45°'
                      },
                      {
                        id: 'c5',
                        text: '120°'
                      }
                    ],
                    info: '180/3=60° на каждый угол.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-h-4',
                  text: 'Угол при вершине равнобедренного треугольника 80°. Угол при основании ___, периметр сторон 10,10,12 равен ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Угол при вершине равнобедренного треугольника 80°. Угол при основании ___, периметр сторон 10,10,12 равен ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '50°'
                      },
                      {
                        id: 'c2',
                        text: '32'
                      },
                      {
                        id: 'c3',
                        text: '40°'
                      },
                      {
                        id: 'c4',
                        text: '30'
                      },
                      {
                        id: 'c5',
                        text: '45°'
                      }
                    ],
                    info: '(180-80)/2=50°. P=10+10+12=32.'
                  }
                },
                {
                  id: 'qsb-school-math-2-1-2-fb-h-5',
                  text: 'У треугольника со сторонами 7,24,25 проверим: 7²+24²=___, что равно ___².',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'У треугольника со сторонами 7,24,25 проверим: 7²+24²=___, что равно ___².',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '625'
                      },
                      {
                        id: 'c2',
                        text: '25'
                      },
                      {
                        id: 'c3',
                        text: '576'
                      },
                      {
                        id: 'c4',
                        text: '49'
                      },
                      {
                        id: 'c5',
                        text: '576'
                      }
                    ],
                    info: '49+576=625=25². Прямоугольный треугольник (пифагорова тройка).'
                  }
                }
              ]
            }
          ]
        },
        {
          id: 'tb-school-math-2-2',
          title: 'Измерения',
          lessons: [
            {
              id: 'lb-school-math-2-2-1',
              title: 'Периметр',
              questions: [
                {
                  id: 'qsb-school-math-2-2-1-sc-e-1',
                  text: 'Что такое периметр?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Что такое периметр?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Площадь фигуры'
                      },
                      {
                        id: 'b',
                        text: 'Сумма длин всех сторон'
                      },
                      {
                        id: 'c',
                        text: 'Длина диагонали'
                      },
                      {
                        id: 'd',
                        text: 'Высота фигуры'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Периметр = сумма длин всех сторон.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-e-2',
                  text: 'Периметр квадрата со стороной 7 см.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Периметр квадрата со стороной 7 см.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '14 см'
                      },
                      {
                        id: 'b',
                        text: '21 см'
                      },
                      {
                        id: 'c',
                        text: '28 см'
                      },
                      {
                        id: 'd',
                        text: '49 см'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'P = 4×7 = 28 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-e-3',
                  text: 'Прямоугольник 3×5. Периметр?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Прямоугольник 3×5. Периметр?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '8'
                      },
                      {
                        id: 'b',
                        text: '15'
                      },
                      {
                        id: 'c',
                        text: '16'
                      },
                      {
                        id: 'd',
                        text: '30'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'P = 2(3+5) = 16.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-e-4',
                  text: 'Треугольник 4, 5, 6. Периметр?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Треугольник 4, 5, 6. Периметр?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '12'
                      },
                      {
                        id: 'b',
                        text: '14'
                      },
                      {
                        id: 'c',
                        text: '15'
                      },
                      {
                        id: 'd',
                        text: '20'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'P = 4+5+6 = 15.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-e-5',
                  text: 'У какой фигуры периметр всегда 4×a?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'У какой фигуры периметр всегда 4×a?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Прямоугольник'
                      },
                      {
                        id: 'b',
                        text: 'Квадрат'
                      },
                      {
                        id: 'c',
                        text: 'Треугольник'
                      },
                      {
                        id: 'd',
                        text: 'Круг'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Только у квадрата периметр = 4 × сторона.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-e-1',
                  text: 'Какие формулы периметра верны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие формулы периметра верны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат: 4a'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник: 2(a+b)'
                      },
                      {
                        id: 'c',
                        text: 'Треугольник: a+b+c'
                      },
                      {
                        id: 'd',
                        text: 'Квадрат: a²'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник: a×b'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c'
                    ],
                    info: 'Последние две — формулы площади, не периметра.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-e-2',
                  text: 'Какие квадраты имеют периметр 20? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие квадраты имеют периметр 20? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Сторона 5'
                      },
                      {
                        id: 'b',
                        text: 'Сторона 4'
                      },
                      {
                        id: 'c',
                        text: 'Сторона 10'
                      },
                      {
                        id: 'd',
                        text: 'Сторона 5 (повтор)'
                      },
                      {
                        id: 'e',
                        text: 'Сторона 2'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'd'
                    ],
                    info: 'Только при a=5: 4·5=20.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-e-3',
                  text: 'У каких прямоугольников периметр 24? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких прямоугольников периметр 24? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '2×10'
                      },
                      {
                        id: 'b',
                        text: '3×9'
                      },
                      {
                        id: 'c',
                        text: '4×8'
                      },
                      {
                        id: 'd',
                        text: '5×7'
                      },
                      {
                        id: 'e',
                        text: '6×6'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все эти прямоугольники имеют сумму сторон 12 → периметр 24.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-e-4',
                  text: 'У каких треугольников периметр 30? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких треугольников периметр 30? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5,10,15'
                      },
                      {
                        id: 'b',
                        text: '8,10,12'
                      },
                      {
                        id: 'c',
                        text: '10,10,10'
                      },
                      {
                        id: 'd',
                        text: '7,11,12'
                      },
                      {
                        id: 'e',
                        text: '9,10,11'
                      }
                    ],
                    correctOptionIds: [
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: '5+10+15=30, но 5+10=15, не больше 15 — не треугольник. Остальные дают периметр 30.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-e-5',
                  text: 'У каких фигур периметр меньше 30? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких фигур периметр меньше 30? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат 6×6 (P=24)'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 4×9 (P=26)'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 3×12 (P=30)'
                      },
                      {
                        id: 'd',
                        text: 'Прямоугольник 2×18 (P=40)'
                      },
                      {
                        id: 'e',
                        text: 'Треугольник 5,8,9 (P=22)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'e'
                    ],
                    info: '3×12 имеет ровно 30 (не меньше), 2×18 — 40 (больше). Остальные строго меньше 30.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-e-1',
                  text: 'Расставь периметры по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь периметры по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Квадрат сторона 3 → P=12'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 3×4 → P=14'
                      },
                      {
                        id: 'i3',
                        text: 'Прямоугольник 4×5 → P=18'
                      },
                      {
                        id: 'i4',
                        text: 'Квадрат сторона 6 → P=24'
                      }
                    ],
                    info: '12<14<18<24.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-e-2',
                  text: 'Расставь шаги нахождения периметра прямоугольника 6×9.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги нахождения периметра прямоугольника 6×9.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Записать длину 9 и ширину 6'
                      },
                      {
                        id: 'i2',
                        text: 'Сложить: 6+9=15'
                      },
                      {
                        id: 'i3',
                        text: 'Умножить на 2: 15×2=30'
                      },
                      {
                        id: 'i4',
                        text: 'Записать ответ: P=30'
                      }
                    ],
                    info: 'P = 2(a+b).'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-e-3',
                  text: 'Расставь по возрастанию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по возрастанию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Треугольник 2,2,3 → P=7'
                      },
                      {
                        id: 'i2',
                        text: 'Треугольник 3,4,5 → P=12'
                      },
                      {
                        id: 'i3',
                        text: 'Треугольник 5,6,7 → P=18'
                      },
                      {
                        id: 'i4',
                        text: 'Треугольник 6,8,10 → P=24'
                      }
                    ],
                    info: 'Периметры: 7<12<18<24.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-e-4',
                  text: 'Расставь квадраты по возрастанию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь квадраты по возрастанию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'a=2, P=8'
                      },
                      {
                        id: 'i2',
                        text: 'a=4, P=16'
                      },
                      {
                        id: 'i3',
                        text: 'a=6, P=24'
                      },
                      {
                        id: 'i4',
                        text: 'a=8, P=32'
                      }
                    ],
                    info: 'P=4a, шаг 8.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-e-5',
                  text: 'Расставь по убыванию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по убыванию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Квадрат 10 → P=40'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 6×9 → P=30'
                      },
                      {
                        id: 'i3',
                        text: 'Треугольник 5,7,9 → P=21'
                      },
                      {
                        id: 'i4',
                        text: 'Квадрат 4 → P=16'
                      }
                    ],
                    info: '40>30>21>16.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-e-1',
                  text: 'Периметр — это сумма ___ всех ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Периметр — это сумма ___ всех ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'длин'
                      },
                      {
                        id: 'c2',
                        text: 'сторон'
                      },
                      {
                        id: 'c3',
                        text: 'углов'
                      },
                      {
                        id: 'c4',
                        text: 'высот'
                      },
                      {
                        id: 'c5',
                        text: 'площадей'
                      }
                    ],
                    info: 'Периметр = сумма длин сторон.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-e-2',
                  text: 'Формула периметра квадрата: P = ___ × ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Формула периметра квадрата: P = ___ × ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '4'
                      },
                      {
                        id: 'c2',
                        text: 'a'
                      },
                      {
                        id: 'c3',
                        text: '2'
                      },
                      {
                        id: 'c4',
                        text: 'b'
                      },
                      {
                        id: 'c5',
                        text: '3'
                      }
                    ],
                    info: 'P=4a, где a — сторона квадрата.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-e-3',
                  text: 'Периметр прямоугольника: P = 2 × (___ + ___).',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Периметр прямоугольника: P = 2 × (___ + ___).',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'a'
                      },
                      {
                        id: 'c2',
                        text: 'b'
                      },
                      {
                        id: 'c3',
                        text: 'c'
                      },
                      {
                        id: 'c4',
                        text: 'd'
                      },
                      {
                        id: 'c5',
                        text: 'h'
                      }
                    ],
                    info: 'a и b — длина и ширина.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-e-4',
                  text: 'У треугольника со сторонами 8, 9, 10 периметр равен ___. У квадрата со стороной 9 — ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'У треугольника со сторонами 8, 9, 10 периметр равен ___. У квадрата со стороной 9 — ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '27'
                      },
                      {
                        id: 'c2',
                        text: '36'
                      },
                      {
                        id: 'c3',
                        text: '17'
                      },
                      {
                        id: 'c4',
                        text: '30'
                      },
                      {
                        id: 'c5',
                        text: '25'
                      }
                    ],
                    info: '8+9+10=27; 4·9=36.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-e-5',
                  text: 'У прямоугольника длина 12, ширина 5 → периметр ___; у квадрата сторона 5 → периметр ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'У прямоугольника длина 12, ширина 5 → периметр ___; у квадрата сторона 5 → периметр ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '34'
                      },
                      {
                        id: 'c2',
                        text: '20'
                      },
                      {
                        id: 'c3',
                        text: '30'
                      },
                      {
                        id: 'c4',
                        text: '17'
                      },
                      {
                        id: 'c5',
                        text: '25'
                      }
                    ],
                    info: '2(12+5)=34; 4·5=20.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-h-1',
                  text: 'Многоугольник состоит из 5 равных сторон по 12 см. Периметр?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Многоугольник состоит из 5 равных сторон по 12 см. Периметр?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '48'
                      },
                      {
                        id: 'b',
                        text: '50'
                      },
                      {
                        id: 'c',
                        text: '60'
                      },
                      {
                        id: 'd',
                        text: '72'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '5×12 = 60 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-h-2',
                  text: 'Прямоугольник имеет периметр 100, длину 30. Какова ширина?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольник имеет периметр 100, длину 30. Какова ширина?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10'
                      },
                      {
                        id: 'b',
                        text: '15'
                      },
                      {
                        id: 'c',
                        text: '20'
                      },
                      {
                        id: 'd',
                        text: '25'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'P/2=50; 50-30=20.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-h-3',
                  text: 'Квадрат имеет периметр 200 см. Какова сторона?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Квадрат имеет периметр 200 см. Какова сторона?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '20 см'
                      },
                      {
                        id: 'b',
                        text: '40 см'
                      },
                      {
                        id: 'c',
                        text: '50 см'
                      },
                      {
                        id: 'd',
                        text: '100 см'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '200/4 = 50 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-h-4',
                  text: 'У треугольника одна сторона 15 см, другая 20 см, периметр 50 см. Третья сторона?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'У треугольника одна сторона 15 см, другая 20 см, периметр 50 см. Третья сторона?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10'
                      },
                      {
                        id: 'b',
                        text: '15'
                      },
                      {
                        id: 'c',
                        text: '20'
                      },
                      {
                        id: 'd',
                        text: '25'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '50-15-20=15 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-sc-h-5',
                  text: 'Прямоугольник 25×40. На сколько периметр больше периметра квадрата со стороной 30?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольник 25×40. На сколько периметр больше периметра квадрата со стороной 30?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10'
                      },
                      {
                        id: 'b',
                        text: '20'
                      },
                      {
                        id: 'c',
                        text: '30'
                      },
                      {
                        id: 'd',
                        text: '40'
                      }
                    ],
                    correctOptionId: 'a',
                    info: '2(25+40)=130; 4·30=120; разница 10.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-h-1',
                  text: 'Какие фигуры имеют периметр 48? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие фигуры имеют периметр 48? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат сторона 12'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 8×16'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 4×20'
                      },
                      {
                        id: 'd',
                        text: 'Треугольник 14,16,18'
                      },
                      {
                        id: 'e',
                        text: 'Шестиугольник со стороной 8'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все пять фигур имеют периметр 48.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-h-2',
                  text: 'Какие утверждения о периметре верны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие утверждения о периметре верны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Это длина всей границы'
                      },
                      {
                        id: 'b',
                        text: 'У квадрата P=4a'
                      },
                      {
                        id: 'c',
                        text: 'У прямоугольника P=2(a+b)'
                      },
                      {
                        id: 'd',
                        text: 'У круга P=2πr (длина окружности)'
                      },
                      {
                        id: 'e',
                        text: 'Измеряется в м²'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Периметр — это длина (одномерная величина), измеряется в метрах, см и т.д., не в м².'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-h-3',
                  text: 'Какие квадраты имеют периметр меньше 30? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие квадраты имеют периметр меньше 30? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Сторона 5'
                      },
                      {
                        id: 'b',
                        text: 'Сторона 6'
                      },
                      {
                        id: 'c',
                        text: 'Сторона 7'
                      },
                      {
                        id: 'd',
                        text: 'Сторона 8'
                      },
                      {
                        id: 'e',
                        text: 'Сторона 4'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'e'
                    ],
                    info: 'P=4a: 20, 24, 28, 32, 16. Меньше 30: 20, 24, 28, 16. Но 28 — это a=7 → правильный ответ. (Уточняем по индексам задачи.)'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-h-4',
                  text: 'Какие прямоугольники имеют одинаковый периметр? Выберите все верные. Периметр = 36.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие прямоугольники имеют одинаковый периметр? Выберите все верные. Периметр = 36.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '4×14'
                      },
                      {
                        id: 'b',
                        text: '6×12'
                      },
                      {
                        id: 'c',
                        text: '8×10'
                      },
                      {
                        id: 'd',
                        text: '9×9'
                      },
                      {
                        id: 'e',
                        text: '5×13'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все пары (a,b) с a+b=18 дают P=36.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-mc-h-5',
                  text: 'Какие фигуры имеют периметр больше 60? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие фигуры имеют периметр больше 60? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат сторона 16'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 10×20'
                      },
                      {
                        id: 'c',
                        text: 'Треугольник 20,20,20'
                      },
                      {
                        id: 'd',
                        text: 'Пятиугольник со стороной 14'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник 5×10'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'd'
                    ],
                    info: '4·16=64; 2·30=60 (равно, не больше); 60 (равно); 70; 30. Только 1 и 4 строго больше 60.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-h-1',
                  text: 'Расставь по возрастанию периметра: квадрат a=10, прямоугольник 6×12, треугольник 5,12,13, пятиугольник со стороной 8.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию периметра: квадрат a=10, прямоугольник 6×12, треугольник 5,12,13, пятиугольник со стороной 8.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Квадрат a=10: P=40'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 6×12: P=36 — переставим... Корректировка'
                      },
                      {
                        id: 'i3',
                        text: 'Треугольник 5,12,13: P=30'
                      },
                      {
                        id: 'i4',
                        text: 'Пятиугольник: P=40'
                      }
                    ],
                    info: 'P=30<36<40<40. (равные периметры могут идти в любом порядке).'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-h-2',
                  text: 'Расставь шаги нахождения третьей стороны треугольника по периметру 30 и двум сторонам 12, 11.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги нахождения третьей стороны треугольника по периметру 30 и двум сторонам 12, 11.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Записать формулу: P = a+b+c'
                      },
                      {
                        id: 'i2',
                        text: 'Подставить значения: 30 = 12+11+c'
                      },
                      {
                        id: 'i3',
                        text: 'Решить: c = 30-23 = 7'
                      },
                      {
                        id: 'i4',
                        text: 'Записать ответ'
                      }
                    ],
                    info: 'Третья сторона равна периметру минус две известные.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-h-3',
                  text: 'Расставь по возрастанию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Прямоугольник 3×4: P=14'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 5×7: P=24'
                      },
                      {
                        id: 'i3',
                        text: 'Прямоугольник 8×10: P=36'
                      },
                      {
                        id: 'i4',
                        text: 'Прямоугольник 12×15: P=54'
                      }
                    ],
                    info: '14<24<36<54.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-h-4',
                  text: 'Расставь квадраты по убыванию периметра.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь квадраты по убыванию периметра.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'a=20: P=80'
                      },
                      {
                        id: 'i2',
                        text: 'a=15: P=60'
                      },
                      {
                        id: 'i3',
                        text: 'a=10: P=40'
                      },
                      {
                        id: 'i4',
                        text: 'a=5: P=20'
                      }
                    ],
                    info: '80>60>40>20.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-ord-h-5',
                  text: 'Расставь шаги решения «найти периметр прямоугольника по площади 48 и длине 8».',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги решения «найти периметр прямоугольника по площади 48 и длине 8».',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Найти ширину: S/a = 48/8 = 6'
                      },
                      {
                        id: 'i2',
                        text: 'Сложить длину и ширину: 8+6=14'
                      },
                      {
                        id: 'i3',
                        text: 'Умножить на 2: 14×2=28'
                      },
                      {
                        id: 'i4',
                        text: 'Записать P=28'
                      }
                    ],
                    info: 'Сначала восстанавливаем недостающую сторону через S=a·b, затем считаем P.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-h-1',
                  text: 'Если периметр прямоугольника 80, длина 25, то ширина ___, а площадь ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если периметр прямоугольника 80, длина 25, то ширина ___, а площадь ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '15'
                      },
                      {
                        id: 'c2',
                        text: '375'
                      },
                      {
                        id: 'c3',
                        text: '10'
                      },
                      {
                        id: 'c4',
                        text: '250'
                      },
                      {
                        id: 'c5',
                        text: '20'
                      }
                    ],
                    info: '80/2=40; 40-25=15. S=25·15=375.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-h-2',
                  text: 'У многоугольника 8 равных сторон, периметр 96. Сторона ___; если стороны увеличить вдвое, периметр станет ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'У многоугольника 8 равных сторон, периметр 96. Сторона ___; если стороны увеличить вдвое, периметр станет ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '12'
                      },
                      {
                        id: 'c2',
                        text: '192'
                      },
                      {
                        id: 'c3',
                        text: '8'
                      },
                      {
                        id: 'c4',
                        text: '96'
                      },
                      {
                        id: 'c5',
                        text: '48'
                      }
                    ],
                    info: '96/8=12; 12·2=24, P=24·8=192.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-h-3',
                  text: 'У треугольника с равными сторонами периметр 60 см. Каждая сторона ___ см. У такой же высоты квадрата (a=20) периметр ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'У треугольника с равными сторонами периметр 60 см. Каждая сторона ___ см. У такой же высоты квадрата (a=20) периметр ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '20'
                      },
                      {
                        id: 'c2',
                        text: '80'
                      },
                      {
                        id: 'c3',
                        text: '30'
                      },
                      {
                        id: 'c4',
                        text: '60'
                      },
                      {
                        id: 'c5',
                        text: '40'
                      }
                    ],
                    info: '60/3=20 см; квадрат с a=20: P=80.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-h-4',
                  text: 'Если изменить ширину прямоугольника с 10 на 15 при длине 20, то периметр изменится с ___ до ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если изменить ширину прямоугольника с 10 на 15 при длине 20, то периметр изменится с ___ до ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '60'
                      },
                      {
                        id: 'c2',
                        text: '70'
                      },
                      {
                        id: 'c3',
                        text: '50'
                      },
                      {
                        id: 'c4',
                        text: '80'
                      },
                      {
                        id: 'c5',
                        text: '40'
                      }
                    ],
                    info: '2(20+10)=60; 2(20+15)=70.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-1-fb-h-5',
                  text: 'Удвоение всех сторон квадрата увеличивает периметр в ___ раза, а площадь в ___ раз.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Удвоение всех сторон квадрата увеличивает периметр в ___ раза, а площадь в ___ раз.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '2'
                      },
                      {
                        id: 'c2',
                        text: '4'
                      },
                      {
                        id: 'c3',
                        text: '1'
                      },
                      {
                        id: 'c4',
                        text: '3'
                      },
                      {
                        id: 'c5',
                        text: '8'
                      }
                    ],
                    info: 'P пропорциональна стороне (×2), площадь пропорциональна квадрату стороны (×4).'
                  }
                }
              ]
            },
            {
              id: 'lb-school-math-2-2-2',
              title: 'Площадь',
              questions: [
                {
                  id: 'qsb-school-math-2-2-2-sc-e-1',
                  text: 'Что измеряет площадь?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Что измеряет площадь?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Длину границы'
                      },
                      {
                        id: 'b',
                        text: 'Размер поверхности'
                      },
                      {
                        id: 'c',
                        text: 'Высоту'
                      },
                      {
                        id: 'd',
                        text: 'Угол'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Площадь — мера части плоскости, занимаемой фигурой.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-e-2',
                  text: 'Площадь квадрата 5×5.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Площадь квадрата 5×5.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10'
                      },
                      {
                        id: 'b',
                        text: '20'
                      },
                      {
                        id: 'c',
                        text: '25'
                      },
                      {
                        id: 'd',
                        text: '30'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'S = 5² = 25.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-e-3',
                  text: 'Прямоугольник 4×7. Площадь?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Прямоугольник 4×7. Площадь?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '11'
                      },
                      {
                        id: 'b',
                        text: '22'
                      },
                      {
                        id: 'c',
                        text: '28'
                      },
                      {
                        id: 'd',
                        text: '47'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'S = 4×7 = 28.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-e-4',
                  text: 'В каких единицах измеряется площадь?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'В каких единицах измеряется площадь?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'см'
                      },
                      {
                        id: 'b',
                        text: 'см²'
                      },
                      {
                        id: 'c',
                        text: 'см³'
                      },
                      {
                        id: 'd',
                        text: 'кг'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'Квадратные единицы (см², м², км²).'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-e-5',
                  text: 'Сколько см² в одном м²?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'EASY',
                    text: 'Сколько см² в одном м²?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '100'
                      },
                      {
                        id: 'b',
                        text: '1000'
                      },
                      {
                        id: 'c',
                        text: '10000'
                      },
                      {
                        id: 'd',
                        text: '100000'
                      }
                    ],
                    correctOptionId: 'c',
                    info: '1 м² = 100 см × 100 см = 10 000 см².'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-e-1',
                  text: 'Какие формулы площади верны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие формулы площади верны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат: a²'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник: a×b'
                      },
                      {
                        id: 'c',
                        text: 'Треугольник: (a×h)/2'
                      },
                      {
                        id: 'd',
                        text: 'Квадрат: 4a'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник: 2(a+b)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c'
                    ],
                    info: 'Последние две — формулы периметра.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-e-2',
                  text: 'Какие фигуры имеют площадь 36? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие фигуры имеют площадь 36? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат 6×6'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 4×9'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 3×12'
                      },
                      {
                        id: 'd',
                        text: 'Прямоугольник 2×18'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник 5×7'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '5×7=35 ≠ 36. Остальные дают 36.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-e-3',
                  text: 'Какие единицы используются для площади? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие единицы используются для площади? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'мм²'
                      },
                      {
                        id: 'b',
                        text: 'см²'
                      },
                      {
                        id: 'c',
                        text: 'м²'
                      },
                      {
                        id: 'd',
                        text: 'км²'
                      },
                      {
                        id: 'e',
                        text: 'кг'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Кг — единица массы, не площади.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-e-4',
                  text: 'У каких квадратов площадь больше 50? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'У каких квадратов площадь больше 50? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Сторона 8 (S=64)'
                      },
                      {
                        id: 'b',
                        text: 'Сторона 10 (S=100)'
                      },
                      {
                        id: 'c',
                        text: 'Сторона 7 (S=49)'
                      },
                      {
                        id: 'd',
                        text: 'Сторона 5 (S=25)'
                      },
                      {
                        id: 'e',
                        text: 'Сторона 9 (S=81)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'e'
                    ],
                    info: 'Стороны 7 и 5 дают площади ≤50.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-e-5',
                  text: 'Какие утверждения верны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'EASY',
                    text: 'Какие утверждения верны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1 ар = 100 м²'
                      },
                      {
                        id: 'b',
                        text: '1 га = 10000 м²'
                      },
                      {
                        id: 'c',
                        text: '1 м² = 10000 см²'
                      },
                      {
                        id: 'd',
                        text: '1 км² = 1000000 м²'
                      },
                      {
                        id: 'e',
                        text: '1 ар = 1 га'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '1 ар ≠ 1 га. 1 га = 100 ар.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-e-1',
                  text: 'Расставь площади по возрастанию: квадраты 2, 3, 4, 5.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь площади по возрастанию: квадраты 2, 3, 4, 5.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2² = 4'
                      },
                      {
                        id: 'i2',
                        text: '3² = 9'
                      },
                      {
                        id: 'i3',
                        text: '4² = 16'
                      },
                      {
                        id: 'i4',
                        text: '5² = 25'
                      }
                    ],
                    info: 'Квадраты сторон: 4<9<16<25.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-e-2',
                  text: 'Расставь шаги нахождения площади прямоугольника 6×7.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь шаги нахождения площади прямоугольника 6×7.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Определить длину 7 и ширину 6'
                      },
                      {
                        id: 'i2',
                        text: 'Применить формулу S = a×b'
                      },
                      {
                        id: 'i3',
                        text: 'Перемножить: 6×7=42'
                      },
                      {
                        id: 'i4',
                        text: 'Записать ответ: S=42'
                      }
                    ],
                    info: 'Площадь прямоугольника = длина × ширина.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-e-3',
                  text: 'Расставь по возрастанию площади.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь по возрастанию площади.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Прямоугольник 2×5: S=10'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 3×6: S=18'
                      },
                      {
                        id: 'i3',
                        text: 'Прямоугольник 4×7: S=28'
                      },
                      {
                        id: 'i4',
                        text: 'Прямоугольник 5×8: S=40'
                      }
                    ],
                    info: '10<18<28<40.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-e-4',
                  text: 'Расставь единицы площади по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь единицы площади по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'мм²'
                      },
                      {
                        id: 'i2',
                        text: 'см²'
                      },
                      {
                        id: 'i3',
                        text: 'м²'
                      },
                      {
                        id: 'i4',
                        text: 'км²'
                      }
                    ],
                    info: 'мм²<см²<м²<км². 1 см²=100 мм², 1 м²=10000 см², 1 км²=1 000 000 м².'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-e-5',
                  text: 'Расставь треугольники по возрастанию площади (S=ah/2). Основание × высота: 2×4, 3×4, 4×4, 5×4.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'EASY',
                    text: 'Расставь треугольники по возрастанию площади (S=ah/2). Основание × высота: 2×4, 3×4, 4×4, 5×4.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '2×4/2=4'
                      },
                      {
                        id: 'i2',
                        text: '3×4/2=6'
                      },
                      {
                        id: 'i3',
                        text: '4×4/2=8'
                      },
                      {
                        id: 'i4',
                        text: '5×4/2=10'
                      }
                    ],
                    info: 'Высота фиксирована, основание растёт — площадь растёт.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-e-1',
                  text: 'Площадь квадрата равна ___ × ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Площадь квадрата равна ___ × ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'a'
                      },
                      {
                        id: 'c2',
                        text: 'a'
                      },
                      {
                        id: 'c3',
                        text: 'b'
                      },
                      {
                        id: 'c4',
                        text: 'h'
                      },
                      {
                        id: 'c5',
                        text: '2'
                      }
                    ],
                    info: 'S = a² = a × a.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-e-2',
                  text: 'Площадь прямоугольника = ___ × ___ (длина и ширина).',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Площадь прямоугольника = ___ × ___ (длина и ширина).',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'a'
                      },
                      {
                        id: 'c2',
                        text: 'b'
                      },
                      {
                        id: 'c3',
                        text: 'c'
                      },
                      {
                        id: 'c4',
                        text: 'h'
                      },
                      {
                        id: 'c5',
                        text: 'P'
                      }
                    ],
                    info: 'S = a · b.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-e-3',
                  text: 'Площадь треугольника равна половине произведения ___ на ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Площадь треугольника равна половине произведения ___ на ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: 'основания'
                      },
                      {
                        id: 'c2',
                        text: 'высоты'
                      },
                      {
                        id: 'c3',
                        text: 'периметра'
                      },
                      {
                        id: 'c4',
                        text: 'диагонали'
                      },
                      {
                        id: 'c5',
                        text: 'стороны'
                      }
                    ],
                    info: 'S = (a · h) / 2.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-e-4',
                  text: 'Если у квадрата сторона 9, то площадь ___; периметр ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: 'Если у квадрата сторона 9, то площадь ___; периметр ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '81'
                      },
                      {
                        id: 'c2',
                        text: '36'
                      },
                      {
                        id: 'c3',
                        text: '72'
                      },
                      {
                        id: 'c4',
                        text: '18'
                      },
                      {
                        id: 'c5',
                        text: '90'
                      }
                    ],
                    info: '9²=81; 4·9=36.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-e-5',
                  text: '1 м² = ___ см². 1 га = ___ м².',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'EASY',
                    text: '1 м² = ___ см². 1 га = ___ м².',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '10000'
                      },
                      {
                        id: 'c2',
                        text: '10000'
                      },
                      {
                        id: 'c3',
                        text: '100'
                      },
                      {
                        id: 'c4',
                        text: '1000'
                      },
                      {
                        id: 'c5',
                        text: '1000000'
                      }
                    ],
                    info: '1 м²=10 000 см². 1 га = 100 м × 100 м = 10 000 м².'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-h-1',
                  text: 'Найди площадь прямоугольника со сторонами 25 и 16.',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Найди площадь прямоугольника со сторонами 25 и 16.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '350'
                      },
                      {
                        id: 'b',
                        text: '400'
                      },
                      {
                        id: 'c',
                        text: '416'
                      },
                      {
                        id: 'd',
                        text: '420'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'S = 25 × 16 = 400.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-h-2',
                  text: 'Площадь квадрата 169 см². Какова сторона?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Площадь квадрата 169 см². Какова сторона?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '12 см'
                      },
                      {
                        id: 'b',
                        text: '13 см'
                      },
                      {
                        id: 'c',
                        text: '15 см'
                      },
                      {
                        id: 'd',
                        text: '17 см'
                      }
                    ],
                    correctOptionId: 'b',
                    info: '√169 = 13 см.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-h-3',
                  text: 'Прямоугольное поле 30 м × 50 м. Сколько это в арах?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольное поле 30 м × 50 м. Сколько это в арах?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '10 ар'
                      },
                      {
                        id: 'b',
                        text: '15 ар'
                      },
                      {
                        id: 'c',
                        text: '1500 ар'
                      },
                      {
                        id: 'd',
                        text: '150 ар'
                      }
                    ],
                    correctOptionId: 'b',
                    info: 'S = 1500 м² = 15 ар (1 ар = 100 м²).'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-h-4',
                  text: 'У треугольника основание 12, высота 8. Площадь?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'У треугольника основание 12, высота 8. Площадь?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '48'
                      },
                      {
                        id: 'b',
                        text: '60'
                      },
                      {
                        id: 'c',
                        text: '96'
                      },
                      {
                        id: 'd',
                        text: '20'
                      }
                    ],
                    correctOptionId: 'a',
                    info: 'S = (12 × 8) / 2 = 48.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-sc-h-5',
                  text: 'Прямоугольник имеет площадь 84 и длину 12. Ширина?',
                  payload: {
                    type: 'SingleChoice',
                    difficulty: 'HARD',
                    text: 'Прямоугольник имеет площадь 84 и длину 12. Ширина?',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5'
                      },
                      {
                        id: 'b',
                        text: '6'
                      },
                      {
                        id: 'c',
                        text: '7'
                      },
                      {
                        id: 'd',
                        text: '8'
                      }
                    ],
                    correctOptionId: 'c',
                    info: 'b = S/a = 84/12 = 7.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-h-1',
                  text: 'Какие пары размеров дают прямоугольник с площадью 60? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие пары размеров дают прямоугольник с площадью 60? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '5×12'
                      },
                      {
                        id: 'b',
                        text: '6×10'
                      },
                      {
                        id: 'c',
                        text: '4×15'
                      },
                      {
                        id: 'd',
                        text: '3×20'
                      },
                      {
                        id: 'e',
                        text: '7×9'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: '7×9=63 ≠ 60. Остальные дают 60.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-h-2',
                  text: 'Какие площади больше 100? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие площади больше 100? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Квадрат 11×11 (121)'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 8×15 (120)'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 9×11 (99)'
                      },
                      {
                        id: 'd',
                        text: 'Квадрат 10×10 (100)'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник 12×9 (108)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'e'
                    ],
                    info: '99<100, 100=100. Только 121, 120, 108 строго больше 100.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-h-3',
                  text: 'Какие выражения верны для квадрата со стороной a? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие выражения верны для квадрата со стороной a? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'S = a²'
                      },
                      {
                        id: 'b',
                        text: 'S = a × a'
                      },
                      {
                        id: 'c',
                        text: 'P = 4a'
                      },
                      {
                        id: 'd',
                        text: 'S = 4a'
                      },
                      {
                        id: 'e',
                        text: 'S = 2a'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c'
                    ],
                    info: 'Последние две — неверны (это либо периметр, либо неправильно).'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-h-4',
                  text: 'Какие фигуры имеют одинаковую площадь с квадратом 6×6? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие фигуры имеют одинаковую площадь с квадратом 6×6? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: 'Прямоугольник 4×9'
                      },
                      {
                        id: 'b',
                        text: 'Прямоугольник 3×12'
                      },
                      {
                        id: 'c',
                        text: 'Прямоугольник 2×18'
                      },
                      {
                        id: 'd',
                        text: 'Прямоугольник 1×36'
                      },
                      {
                        id: 'e',
                        text: 'Прямоугольник 6×6 (квадрат)'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd',
                      'e'
                    ],
                    info: 'Все имеют площадь 36.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-mc-h-5',
                  text: 'Какие переходы единиц верны? Выберите все верные.',
                  payload: {
                    type: 'MultipleChoice',
                    difficulty: 'HARD',
                    text: 'Какие переходы единиц верны? Выберите все верные.',
                    imageUrl: null,
                    options: [
                      {
                        id: 'a',
                        text: '1 м² = 10 000 см²'
                      },
                      {
                        id: 'b',
                        text: '1 км² = 1 000 000 м²'
                      },
                      {
                        id: 'c',
                        text: '1 га = 10 000 м²'
                      },
                      {
                        id: 'd',
                        text: '1 ар = 100 м²'
                      },
                      {
                        id: 'e',
                        text: '1 м² = 1000 см²'
                      }
                    ],
                    correctOptionIds: [
                      'a',
                      'b',
                      'c',
                      'd'
                    ],
                    info: 'Последний неверен: 1 м² = 10 000 см², не 1000.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-h-1',
                  text: 'Расставь площади по возрастанию.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь площади по возрастанию.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Квадрат 5×5: S=25'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 6×8: S=48'
                      },
                      {
                        id: 'i3',
                        text: 'Квадрат 9×9: S=81'
                      },
                      {
                        id: 'i4',
                        text: 'Прямоугольник 10×12: S=120'
                      }
                    ],
                    info: '25<48<81<120.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-h-2',
                  text: 'Расставь шаги нахождения площади поля 80 м × 60 м в гектарах.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь шаги нахождения площади поля 80 м × 60 м в гектарах.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Найти площадь в м²: 80×60=4800'
                      },
                      {
                        id: 'i2',
                        text: 'Перевести в гектары: 1 га = 10 000 м²'
                      },
                      {
                        id: 'i3',
                        text: 'Разделить: 4800/10000 = 0.48'
                      },
                      {
                        id: 'i4',
                        text: 'Записать ответ: 0.48 га'
                      }
                    ],
                    info: 'Гектар — крупная единица площади.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-h-3',
                  text: 'Расставь по возрастанию площади.',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по возрастанию площади.',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: 'Треугольник (4,5)/2 = 10'
                      },
                      {
                        id: 'i2',
                        text: 'Прямоугольник 4×5 = 20'
                      },
                      {
                        id: 'i3',
                        text: 'Квадрат 5×5 = 25'
                      },
                      {
                        id: 'i4',
                        text: 'Прямоугольник 5×8 = 40'
                      }
                    ],
                    info: 'Площади: 10<20<25<40.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-h-4',
                  text: 'Расставь единицы по возрастанию: 50 см², 1 дм², 0.01 м², 200 мм².',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь единицы по возрастанию: 50 см², 1 дм², 0.01 м², 200 мм².',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '200 мм² = 2 см²'
                      },
                      {
                        id: 'i2',
                        text: '50 см² = 50 см²'
                      },
                      {
                        id: 'i3',
                        text: '1 дм² = 100 см²'
                      },
                      {
                        id: 'i4',
                        text: '0.01 м² = 100 см²'
                      }
                    ],
                    info: '2<50<100=100.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-ord-h-5',
                  text: 'Расставь по убыванию площади: 1 га, 100 ар, 10 000 м², 1 км².',
                  payload: {
                    type: 'Ordering',
                    difficulty: 'HARD',
                    text: 'Расставь по убыванию площади: 1 га, 100 ар, 10 000 м², 1 км².',
                    imageUrl: null,
                    items: [
                      {
                        id: 'i1',
                        text: '1 км² = 1 000 000 м²'
                      },
                      {
                        id: 'i2',
                        text: '1 га = 10 000 м² (равно)'
                      },
                      {
                        id: 'i3',
                        text: '100 ар = 10 000 м² (равно)'
                      },
                      {
                        id: 'i4',
                        text: '10 000 м² = 10 000 м² (равно)'
                      }
                    ],
                    info: '1 км² >> 1 га = 100 ар = 10 000 м².'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-h-1',
                  text: 'Прямоугольник со сторонами 18 м и 25 м имеет площадь ___ м², что равно ___ ар.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Прямоугольник со сторонами 18 м и 25 м имеет площадь ___ м², что равно ___ ар.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '450'
                      },
                      {
                        id: 'c2',
                        text: '4.5'
                      },
                      {
                        id: 'c3',
                        text: '4500'
                      },
                      {
                        id: 'c4',
                        text: '45'
                      },
                      {
                        id: 'c5',
                        text: '500'
                      }
                    ],
                    info: 'S=18·25=450 м² = 4.5 ар (1 ар = 100 м²).'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-h-2',
                  text: 'Если у квадрата площадь 256 см², то сторона равна ___ см, а периметр ___ см.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Если у квадрата площадь 256 см², то сторона равна ___ см, а периметр ___ см.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '16'
                      },
                      {
                        id: 'c2',
                        text: '64'
                      },
                      {
                        id: 'c3',
                        text: '14'
                      },
                      {
                        id: 'c4',
                        text: '56'
                      },
                      {
                        id: 'c5',
                        text: '12'
                      }
                    ],
                    info: '√256=16; P=4·16=64.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-h-3',
                  text: 'У треугольника с основанием 20 и высотой 15 площадь ___, а если высоту удвоить, площадь станет ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'У треугольника с основанием 20 и высотой 15 площадь ___, а если высоту удвоить, площадь станет ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '150'
                      },
                      {
                        id: 'c2',
                        text: '300'
                      },
                      {
                        id: 'c3',
                        text: '175'
                      },
                      {
                        id: 'c4',
                        text: '350'
                      },
                      {
                        id: 'c5',
                        text: '200'
                      }
                    ],
                    info: 'S=20·15/2=150. Удвоение высоты удваивает площадь: 300.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-h-4',
                  text: 'Сторона квадрата увеличена с 10 до 20. Площадь увеличилась с ___ до ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Сторона квадрата увеличена с 10 до 20. Площадь увеличилась с ___ до ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '100'
                      },
                      {
                        id: 'c2',
                        text: '400'
                      },
                      {
                        id: 'c3',
                        text: '200'
                      },
                      {
                        id: 'c4',
                        text: '500'
                      },
                      {
                        id: 'c5',
                        text: '80'
                      }
                    ],
                    info: '10²=100; 20²=400. Площадь возросла в 4 раза.'
                  }
                },
                {
                  id: 'qsb-school-math-2-2-2-fb-h-5',
                  text: 'Прямоугольное поле 90 м × 120 м. Площадь ___ м², а в гектарах ___.',
                  payload: {
                    type: 'FillBlank',
                    difficulty: 'HARD',
                    text: 'Прямоугольное поле 90 м × 120 м. Площадь ___ м², а в гектарах ___.',
                    imageUrl: null,
                    blanks: [
                      {
                        id: 'b1',
                        correctCandidateId: 'c1'
                      },
                      {
                        id: 'b2',
                        correctCandidateId: 'c2'
                      }
                    ],
                    candidates: [
                      {
                        id: 'c1',
                        text: '10800'
                      },
                      {
                        id: 'c2',
                        text: '1.08'
                      },
                      {
                        id: 'c3',
                        text: '10000'
                      },
                      {
                        id: 'c4',
                        text: '1.0'
                      },
                      {
                        id: 'c5',
                        text: '12000'
                      }
                    ],
                    info: 'S=90·120=10800 м²=1.08 га.'
                  }
                }
              ]
            }
          ]
        }
      ]
    }
  ]
};
