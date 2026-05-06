'use strict';

module.exports = {
  "id": "qb-courses-programming",
  "title": "Основы программирования",
  "sections": [
    {
      "id": "sb-courses-programming-1",
      "title": "Базовые конструкции",
      "themes": [
        {
          "id": "tb-courses-programming-1-1",
          "title": "Переменные и типы данных",
          "lessons": [
            {
              "id": "lb-courses-programming-1-1-1",
              "title": "Числовые типы (Int, Float, Long)",
              "questions": [
                {
                  "id": "qsb-courses-programming-1-1-1-sc-e-1",
                  "text": "Какой тип данных используется для целых чисел в большинстве языков?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой тип данных используется для целых чисел в большинстве языков?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "String"
                      },
                      {
                        "id": "c",
                        "text": "Boolean"
                      },
                      {
                        "id": "d",
                        "text": "Char"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Int — стандартный тип для целых чисел."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-e-2",
                  "text": "Какой тип данных подходит для дробных чисел?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой тип данных подходит для дробных чисел?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "Float"
                      },
                      {
                        "id": "c",
                        "text": "Boolean"
                      },
                      {
                        "id": "d",
                        "text": "Long"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Float хранит числа с плавающей точкой."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-e-3",
                  "text": "Сколько бит занимает Int в Kotlin/Java?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Сколько бит занимает Int в Kotlin/Java?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "8"
                      },
                      {
                        "id": "b",
                        "text": "16"
                      },
                      {
                        "id": "c",
                        "text": "32"
                      },
                      {
                        "id": "d",
                        "text": "64"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Int — 32-битное знаковое целое."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-e-4",
                  "text": "Какой тип используют для очень больших целых чисел?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой тип используют для очень больших целых чисел?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "Short"
                      },
                      {
                        "id": "c",
                        "text": "Long"
                      },
                      {
                        "id": "d",
                        "text": "Byte"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Long — 64-битное целое для больших значений."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-e-5",
                  "text": "Какой литерал создаёт Long в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой литерал создаёт Long в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "100"
                      },
                      {
                        "id": "b",
                        "text": "100L"
                      },
                      {
                        "id": "c",
                        "text": "100f"
                      },
                      {
                        "id": "d",
                        "text": "100.0"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Суффикс L превращает целое в Long."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-e-1",
                  "text": "Какие из перечисленных типов являются числовыми?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие из перечисленных типов являются числовыми?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "Float"
                      },
                      {
                        "id": "c",
                        "text": "String"
                      },
                      {
                        "id": "d",
                        "text": "Long"
                      },
                      {
                        "id": "e",
                        "text": "Boolean"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "Int, Float, Long — числовые. String и Boolean — нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-e-2",
                  "text": "Какие из этих значений валидны для Int?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие из этих значений валидны для Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0"
                      },
                      {
                        "id": "b",
                        "text": "-1"
                      },
                      {
                        "id": "c",
                        "text": "3.14"
                      },
                      {
                        "id": "d",
                        "text": "100"
                      },
                      {
                        "id": "e",
                        "text": "true"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "Int принимает целые без дробной части."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-e-3",
                  "text": "Какие типы относятся к числам с плавающей точкой?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие типы относятся к числам с плавающей точкой?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Float"
                      },
                      {
                        "id": "b",
                        "text": "Double"
                      },
                      {
                        "id": "c",
                        "text": "Int"
                      },
                      {
                        "id": "d",
                        "text": "Long"
                      },
                      {
                        "id": "e",
                        "text": "Short"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b"
                    ],
                    "info": "Float и Double — типы с плавающей точкой."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-e-4",
                  "text": "Какие операции допустимы для двух Int?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операции допустимы для двух Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Сложение"
                      },
                      {
                        "id": "b",
                        "text": "Деление"
                      },
                      {
                        "id": "c",
                        "text": "Конкатенация строк"
                      },
                      {
                        "id": "d",
                        "text": "Умножение"
                      },
                      {
                        "id": "e",
                        "text": "Логическое И"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "Арифметические операции работают с числами."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-e-5",
                  "text": "Какие литералы создают целое число?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие литералы создают целое число?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "42"
                      },
                      {
                        "id": "b",
                        "text": "0xFF"
                      },
                      {
                        "id": "c",
                        "text": "0b1010"
                      },
                      {
                        "id": "d",
                        "text": "3.14"
                      },
                      {
                        "id": "e",
                        "text": "\"42\""
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "Hex и binary литералы тоже целые."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-e-1",
                  "text": "Расположите типы по размеру (от меньшего к большему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите типы по размеру (от меньшего к большему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Byte"
                      },
                      {
                        "id": "i2",
                        "text": "Short"
                      },
                      {
                        "id": "i3",
                        "text": "Int"
                      },
                      {
                        "id": "i4",
                        "text": "Long"
                      }
                    ],
                    "info": "Byte=8, Short=16, Int=32, Long=64 бит."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-e-2",
                  "text": "Расположите шаги объявления переменной.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги объявления переменной.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Выбрать тип"
                      },
                      {
                        "id": "i2",
                        "text": "Дать имя"
                      },
                      {
                        "id": "i3",
                        "text": "Присвоить значение"
                      },
                      {
                        "id": "i4",
                        "text": "Использовать в коде"
                      }
                    ],
                    "info": "Стандартный порядок работы с переменной."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-e-3",
                  "text": "Расположите числа по возрастанию.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите числа по возрастанию.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "-100"
                      },
                      {
                        "id": "i2",
                        "text": "0"
                      },
                      {
                        "id": "i3",
                        "text": "50"
                      },
                      {
                        "id": "i4",
                        "text": "1000"
                      }
                    ],
                    "info": "Сортировка целых чисел по возрастанию."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-e-4",
                  "text": "Расположите типы по точности (от меньшей к большей).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите типы по точности (от меньшей к большей).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Float (32 бит)"
                      },
                      {
                        "id": "i2",
                        "text": "Double (64 бит)"
                      },
                      {
                        "id": "i3",
                        "text": "BigDecimal (произвольная)"
                      },
                      {
                        "id": "i4",
                        "text": "BigInteger (целочисленная произвольная)"
                      }
                    ],
                    "info": "Точность растёт с размером."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-e-5",
                  "text": "Расположите этапы вычисления выражения val x: Int = 2 + 3.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите этапы вычисления выражения val x: Int = 2 + 3.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Лексический разбор"
                      },
                      {
                        "id": "i2",
                        "text": "Парсинг"
                      },
                      {
                        "id": "i3",
                        "text": "Вычисление 2+3"
                      },
                      {
                        "id": "i4",
                        "text": "Присвоение x"
                      }
                    ],
                    "info": "Стандартная последовательность вычисления."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-e-1",
                  "text": "Тип ___ хранит целые 32-битные числа.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Тип ___ хранит целые 32-битные числа.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Int"
                      },
                      {
                        "id": "c2",
                        "text": "Float"
                      },
                      {
                        "id": "c3",
                        "text": "String"
                      },
                      {
                        "id": "c4",
                        "text": "Boolean"
                      },
                      {
                        "id": "c5",
                        "text": "Long"
                      }
                    ],
                    "info": "Int — 32-битное целое."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-e-2",
                  "text": "Суффикс ___ превращает литерал в Long.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Суффикс ___ превращает литерал в Long.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "L"
                      },
                      {
                        "id": "c2",
                        "text": "F"
                      },
                      {
                        "id": "c3",
                        "text": "D"
                      },
                      {
                        "id": "c4",
                        "text": "B"
                      },
                      {
                        "id": "c5",
                        "text": "S"
                      }
                    ],
                    "info": "Long литералы помечаются L."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-e-3",
                  "text": "Тип ___ используют для дробных значений.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Тип ___ используют для дробных значений.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Float"
                      },
                      {
                        "id": "c2",
                        "text": "Int"
                      },
                      {
                        "id": "c3",
                        "text": "String"
                      },
                      {
                        "id": "c4",
                        "text": "Char"
                      },
                      {
                        "id": "c5",
                        "text": "Long"
                      }
                    ],
                    "info": "Float поддерживает дробные числа."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-e-4",
                  "text": "64-битное целое — это ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "64-битное целое — это ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Long"
                      },
                      {
                        "id": "c2",
                        "text": "Int"
                      },
                      {
                        "id": "c3",
                        "text": "Short"
                      },
                      {
                        "id": "c4",
                        "text": "Byte"
                      },
                      {
                        "id": "c5",
                        "text": "Float"
                      }
                    ],
                    "info": "Long занимает 64 бита."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-e-5",
                  "text": "Int может хранить значения от ___ до плюс 2^31-1.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Int может хранить значения от ___ до плюс 2^31-1.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "-2^31"
                      },
                      {
                        "id": "c2",
                        "text": "-2^15"
                      },
                      {
                        "id": "c3",
                        "text": "-128"
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "-2^63"
                      }
                    ],
                    "info": "Диапазон Int симметричен."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-h-1",
                  "text": "Что напечатает println(Int.MAX_VALUE + 1) в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает println(Int.MAX_VALUE + 1) в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0"
                      },
                      {
                        "id": "b",
                        "text": "Int.MAX_VALUE"
                      },
                      {
                        "id": "c",
                        "text": "Int.MIN_VALUE"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка компиляции"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Переполнение Int приводит к MIN_VALUE."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-h-2",
                  "text": "Какое значение даёт 0.1 + 0.2 == 0.3 для Double?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Какое значение даёт 0.1 + 0.2 == 0.3 для Double?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "NaN"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Из-за бинарного представления равенство false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-h-3",
                  "text": "Что вернёт 1.0 / 0.0 в Kotlin/Java для Double?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт 1.0 / 0.0 в Kotlin/Java для Double?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0.0"
                      },
                      {
                        "id": "b",
                        "text": "Infinity"
                      },
                      {
                        "id": "c",
                        "text": "NaN"
                      },
                      {
                        "id": "d",
                        "text": "ArithmeticException"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Деление на 0.0 даёт Infinity для Double."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-h-4",
                  "text": "Что вернёт 1 / 0 для Int в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт 1 / 0 для Int в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0"
                      },
                      {
                        "id": "b",
                        "text": "Infinity"
                      },
                      {
                        "id": "c",
                        "text": "NaN"
                      },
                      {
                        "id": "d",
                        "text": "ArithmeticException"
                      }
                    ],
                    "correctOptionId": "d",
                    "info": "Целочисленное деление на ноль бросает исключение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-sc-h-5",
                  "text": "Какой результат у Int.MIN_VALUE.absoluteValue в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Какой результат у Int.MIN_VALUE.absoluteValue в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int.MAX_VALUE"
                      },
                      {
                        "id": "b",
                        "text": "Int.MIN_VALUE"
                      },
                      {
                        "id": "c",
                        "text": "0"
                      },
                      {
                        "id": "d",
                        "text": "Long.MAX_VALUE"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "|MIN_VALUE| не помещается в Int — остаётся MIN_VALUE."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-h-1",
                  "text": "Какие утверждения верны про Float и Double?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие утверждения верны про Float и Double?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Double точнее Float"
                      },
                      {
                        "id": "b",
                        "text": "Float занимает 32 бита"
                      },
                      {
                        "id": "c",
                        "text": "Double занимает 32 бита"
                      },
                      {
                        "id": "d",
                        "text": "Float точнее Double"
                      },
                      {
                        "id": "e",
                        "text": "Double занимает 64 бита"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "e"
                    ],
                    "info": "Double — 64 бита, Float — 32 бита."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-h-2",
                  "text": "Какие проблемы характерны для арифметики чисел с плавающей точкой?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие проблемы характерны для арифметики чисел с плавающей точкой?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Потеря точности"
                      },
                      {
                        "id": "b",
                        "text": "Невозможность сложения"
                      },
                      {
                        "id": "c",
                        "text": "NaN при делении 0/0"
                      },
                      {
                        "id": "d",
                        "text": "Округление 0.1+0.2"
                      },
                      {
                        "id": "e",
                        "text": "Целочисленное переполнение"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d"
                    ],
                    "info": "Это типичные ловушки IEEE 754."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-h-3",
                  "text": "Какие операции могут вызвать переполнение для Int?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие операции могут вызвать переполнение для Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "MAX_VALUE + 1"
                      },
                      {
                        "id": "b",
                        "text": "MIN_VALUE - 1"
                      },
                      {
                        "id": "c",
                        "text": "0 * 0"
                      },
                      {
                        "id": "d",
                        "text": "MAX_VALUE * 2"
                      },
                      {
                        "id": "e",
                        "text": "10 + 5"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "Любой выход за границы — переполнение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-h-4",
                  "text": "Какие методы корректно конвертируют Long в Int в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие методы корректно конвертируют Long в Int в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "toInt()"
                      },
                      {
                        "id": "b",
                        "text": "as Int"
                      },
                      {
                        "id": "c",
                        "text": ".toIntOrNull()"
                      },
                      {
                        "id": "d",
                        "text": ".intValue()"
                      },
                      {
                        "id": "e",
                        "text": "String(it)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d"
                    ],
                    "info": "toInt и intValue работают; as Int — невозможно для Long."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-mc-h-5",
                  "text": "Какие литералы создают Double?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие литералы создают Double?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1.0"
                      },
                      {
                        "id": "b",
                        "text": "1e2"
                      },
                      {
                        "id": "c",
                        "text": "1.5f"
                      },
                      {
                        "id": "d",
                        "text": "1.0d"
                      },
                      {
                        "id": "e",
                        "text": "1L"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "d — суффикс Double; 1.5f — Float; 1L — Long."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-h-1",
                  "text": "Расположите типы по диапазону MAX_VALUE (от меньшего к большему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите типы по диапазону MAX_VALUE (от меньшего к большему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Byte (127)"
                      },
                      {
                        "id": "i2",
                        "text": "Short (32767)"
                      },
                      {
                        "id": "i3",
                        "text": "Int (~2 млрд)"
                      },
                      {
                        "id": "i4",
                        "text": "Long (~9*10^18)"
                      }
                    ],
                    "info": "Каждый следующий тип в 2x больше бит."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-h-2",
                  "text": "Расположите этапы переполнения Int.MAX_VALUE+1.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите этапы переполнения Int.MAX_VALUE+1.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Чтение операндов"
                      },
                      {
                        "id": "i2",
                        "text": "Сложение в 32 битах"
                      },
                      {
                        "id": "i3",
                        "text": "Отбрасывание переноса"
                      },
                      {
                        "id": "i4",
                        "text": "Получение Int.MIN_VALUE"
                      }
                    ],
                    "info": "Two-complement переполнение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-h-3",
                  "text": "Расположите шаги вычисления 0.1 + 0.2 в Double.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги вычисления 0.1 + 0.2 в Double.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Округление 0.1 в IEEE 754"
                      },
                      {
                        "id": "i2",
                        "text": "Округление 0.2 в IEEE 754"
                      },
                      {
                        "id": "i3",
                        "text": "Сложение мантисс"
                      },
                      {
                        "id": "i4",
                        "text": "Получение 0.30000000000000004"
                      }
                    ],
                    "info": "Бинарное представление вызывает погрешность."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-h-4",
                  "text": "Расположите типы по приоритету в смешанной арифметике (Kotlin).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите типы по приоритету в смешанной арифметике (Kotlin).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Byte"
                      },
                      {
                        "id": "i2",
                        "text": "Short"
                      },
                      {
                        "id": "i3",
                        "text": "Int"
                      },
                      {
                        "id": "i4",
                        "text": "Long"
                      }
                    ],
                    "info": "В Kotlin меньшие типы продвигаются к Int перед операцией."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-ord-h-5",
                  "text": "Расположите шаги конвертации Long в Int через toInt().",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги конвертации Long в Int через toInt().",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Прочитать 64 бита"
                      },
                      {
                        "id": "i2",
                        "text": "Взять младшие 32 бита"
                      },
                      {
                        "id": "i3",
                        "text": "Отбросить старшие 32 бита"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть результат как Int"
                      }
                    ],
                    "info": "toInt() — это truncation младших битов."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-h-1",
                  "text": "IEEE ___ — стандарт чисел с плавающей точкой.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "IEEE ___ — стандарт чисел с плавающей точкой.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "754"
                      },
                      {
                        "id": "c2",
                        "text": "802"
                      },
                      {
                        "id": "c3",
                        "text": "1394"
                      },
                      {
                        "id": "c4",
                        "text": "1284"
                      },
                      {
                        "id": "c5",
                        "text": "488"
                      }
                    ],
                    "info": "IEEE 754 описывает Float и Double."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-h-2",
                  "text": "Метод ___ даёт Long из Int без потерь.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Метод ___ даёт Long из Int без потерь.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "toLong()"
                      },
                      {
                        "id": "c2",
                        "text": "toInt()"
                      },
                      {
                        "id": "c3",
                        "text": "toFloat()"
                      },
                      {
                        "id": "c4",
                        "text": "toString()"
                      },
                      {
                        "id": "c5",
                        "text": "toByte()"
                      }
                    ],
                    "info": "Расширяющая конвертация безопасна."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-h-3",
                  "text": "Деление 1.0/0.0 даёт ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Деление 1.0/0.0 даёт ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Infinity"
                      },
                      {
                        "id": "c2",
                        "text": "NaN"
                      },
                      {
                        "id": "c3",
                        "text": "0"
                      },
                      {
                        "id": "c4",
                        "text": "MAX_VALUE"
                      },
                      {
                        "id": "c5",
                        "text": "Exception"
                      }
                    ],
                    "info": "Для Double это бесконечность."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-h-4",
                  "text": "Результат 0.0/0.0 — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Результат 0.0/0.0 — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "NaN"
                      },
                      {
                        "id": "c2",
                        "text": "Infinity"
                      },
                      {
                        "id": "c3",
                        "text": "0"
                      },
                      {
                        "id": "c4",
                        "text": "MAX_VALUE"
                      },
                      {
                        "id": "c5",
                        "text": "Exception"
                      }
                    ],
                    "info": "Неопределённость представляется как NaN."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-1-fb-h-5",
                  "text": "MAX_VALUE Long примерно ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "MAX_VALUE Long примерно ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "9*10^18"
                      },
                      {
                        "id": "c2",
                        "text": "2*10^9"
                      },
                      {
                        "id": "c3",
                        "text": "32767"
                      },
                      {
                        "id": "c4",
                        "text": "127"
                      },
                      {
                        "id": "c5",
                        "text": "255"
                      }
                    ],
                    "info": "Long: ~9.2 квинтиллиона."
                  }
                }
              ]
            },
            {
              "id": "lb-courses-programming-1-1-2",
              "title": "Строки и булевы значения",
              "questions": [
                {
                  "id": "qsb-courses-programming-1-1-2-sc-e-1",
                  "text": "Какой тип хранит true/false?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой тип хранит true/false?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Boolean"
                      },
                      {
                        "id": "b",
                        "text": "Int"
                      },
                      {
                        "id": "c",
                        "text": "String"
                      },
                      {
                        "id": "d",
                        "text": "Char"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Boolean — два значения: true и false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-e-2",
                  "text": "Какой тип хранит последовательность символов?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой тип хранит последовательность символов?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Char"
                      },
                      {
                        "id": "b",
                        "text": "String"
                      },
                      {
                        "id": "c",
                        "text": "Int"
                      },
                      {
                        "id": "d",
                        "text": "Float"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "String — строка из символов."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-e-3",
                  "text": "Сколько символов в \"hello\"?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Сколько символов в \"hello\"?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "4"
                      },
                      {
                        "id": "b",
                        "text": "5"
                      },
                      {
                        "id": "c",
                        "text": "6"
                      },
                      {
                        "id": "d",
                        "text": "7"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Длина строки \"hello\" равна 5."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-e-4",
                  "text": "Какой результат выражения \"abc\".length?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой результат выражения \"abc\".length?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "2"
                      },
                      {
                        "id": "b",
                        "text": "3"
                      },
                      {
                        "id": "c",
                        "text": "4"
                      },
                      {
                        "id": "d",
                        "text": "0"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Свойство length возвращает 3."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-e-5",
                  "text": "Какое значение по умолчанию у Boolean в Java?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какое значение по умолчанию у Boolean в Java?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "0"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "По умолчанию Boolean равен false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-e-1",
                  "text": "Какие из значений валидны для Boolean?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие из значений валидны для Boolean?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "1"
                      },
                      {
                        "id": "d",
                        "text": "0"
                      },
                      {
                        "id": "e",
                        "text": "\"true\""
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b"
                    ],
                    "info": "Только true и false — Boolean литералы."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-e-2",
                  "text": "Какие операции применимы к String?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операции применимы к String?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Конкатенация"
                      },
                      {
                        "id": "b",
                        "text": "Длина"
                      },
                      {
                        "id": "c",
                        "text": "Сложение чисел"
                      },
                      {
                        "id": "d",
                        "text": "Подстрока"
                      },
                      {
                        "id": "e",
                        "text": "Логическое И"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "Строки умеют склеиваться и резаться."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-e-3",
                  "text": "Какие способы создания строки в Kotlin валидны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие способы создания строки в Kotlin валидны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"hello\""
                      },
                      {
                        "id": "b",
                        "text": "'''multiline'''"
                      },
                      {
                        "id": "c",
                        "text": "\"\"\"raw\"\"\""
                      },
                      {
                        "id": "d",
                        "text": "'h'"
                      },
                      {
                        "id": "e",
                        "text": "String(charArray)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "e"
                    ],
                    "info": "Char литерал и тройные кавычки."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-e-4",
                  "text": "Какие методы возвращают строку?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие методы возвращают строку?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "toString()"
                      },
                      {
                        "id": "b",
                        "text": "substring()"
                      },
                      {
                        "id": "c",
                        "text": "trim()"
                      },
                      {
                        "id": "d",
                        "text": "length"
                      },
                      {
                        "id": "e",
                        "text": "plus()"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "length возвращает Int, остальные — String."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-e-5",
                  "text": "Какие выражения дают Boolean?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения дают Boolean?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1 == 1"
                      },
                      {
                        "id": "b",
                        "text": "\"a\" + \"b\""
                      },
                      {
                        "id": "c",
                        "text": "5 > 3"
                      },
                      {
                        "id": "d",
                        "text": "true && false"
                      },
                      {
                        "id": "e",
                        "text": "\"abc\".length"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d"
                    ],
                    "info": "Сравнения и логические операции."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-e-1",
                  "text": "Расположите шаги конкатенации строк \"Hello, \" + \"World\".",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги конкатенации строк \"Hello, \" + \"World\".",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Чтение левой строки"
                      },
                      {
                        "id": "i2",
                        "text": "Чтение правой строки"
                      },
                      {
                        "id": "i3",
                        "text": "Создание буфера"
                      },
                      {
                        "id": "i4",
                        "text": "Возврат \"Hello, World\""
                      }
                    ],
                    "info": "Стандартный путь конкатенации."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-e-2",
                  "text": "Расположите операции по результату (Boolean).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите операции по результату (Boolean).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "1 == 1 (true)"
                      },
                      {
                        "id": "i2",
                        "text": "true && true (true)"
                      },
                      {
                        "id": "i3",
                        "text": "true && false (false)"
                      },
                      {
                        "id": "i4",
                        "text": "false || false (false)"
                      }
                    ],
                    "info": "Сортировка по логическому значению."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-e-3",
                  "text": "Расположите символы строки \"abcd\" по индексу.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите символы строки \"abcd\" по индексу.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "a (0)"
                      },
                      {
                        "id": "i2",
                        "text": "b (1)"
                      },
                      {
                        "id": "i3",
                        "text": "c (2)"
                      },
                      {
                        "id": "i4",
                        "text": "d (3)"
                      }
                    ],
                    "info": "Индексация с нуля."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-e-4",
                  "text": "Расположите этапы парсинга String в Boolean.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите этапы парсинга String в Boolean.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Получить строку"
                      },
                      {
                        "id": "i2",
                        "text": "Привести к нижнему регистру"
                      },
                      {
                        "id": "i3",
                        "text": "Сравнить с \"true\""
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть Boolean"
                      }
                    ],
                    "info": "Разбор toBoolean()."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-e-5",
                  "text": "Расположите строки по длине (по возрастанию).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите строки по длине (по возрастанию).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "\"\""
                      },
                      {
                        "id": "i2",
                        "text": "\"a\""
                      },
                      {
                        "id": "i3",
                        "text": "\"ab\""
                      },
                      {
                        "id": "i4",
                        "text": "\"abc\""
                      }
                    ],
                    "info": "Сортировка по length."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-e-1",
                  "text": "Тип ___ имеет два значения.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Тип ___ имеет два значения.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Boolean"
                      },
                      {
                        "id": "c2",
                        "text": "Int"
                      },
                      {
                        "id": "c3",
                        "text": "String"
                      },
                      {
                        "id": "c4",
                        "text": "Char"
                      },
                      {
                        "id": "c5",
                        "text": "Float"
                      }
                    ],
                    "info": "Boolean — true/false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-e-2",
                  "text": "Метод ___ возвращает длину строки.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Метод ___ возвращает длину строки.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "length"
                      },
                      {
                        "id": "c2",
                        "text": "size"
                      },
                      {
                        "id": "c3",
                        "text": "count"
                      },
                      {
                        "id": "c4",
                        "text": "len"
                      },
                      {
                        "id": "c5",
                        "text": "length()"
                      }
                    ],
                    "info": "String.length — свойство."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-e-3",
                  "text": "Символ помещается в тип ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Символ помещается в тип ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Char"
                      },
                      {
                        "id": "c2",
                        "text": "String"
                      },
                      {
                        "id": "c3",
                        "text": "Byte"
                      },
                      {
                        "id": "c4",
                        "text": "Int"
                      },
                      {
                        "id": "c5",
                        "text": "Boolean"
                      }
                    ],
                    "info": "Один символ — Char."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-e-4",
                  "text": "___ кавычки создают raw string в Kotlin.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "___ кавычки создают raw string в Kotlin.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Тройные"
                      },
                      {
                        "id": "c2",
                        "text": "Двойные"
                      },
                      {
                        "id": "c3",
                        "text": "Одинарные"
                      },
                      {
                        "id": "c4",
                        "text": "Обратные"
                      },
                      {
                        "id": "c5",
                        "text": "Угловые"
                      }
                    ],
                    "info": "Raw string — \"\"\" ... \"\"\"."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-e-5",
                  "text": "___ означает отсутствие строки.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "___ означает отсутствие строки.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "null"
                      },
                      {
                        "id": "c2",
                        "text": "\"\""
                      },
                      {
                        "id": "c3",
                        "text": "\"null\""
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "false"
                      }
                    ],
                    "info": "Null = отсутствие объекта."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-h-1",
                  "text": "Что напечатает \"abc\" == \"abc\" в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает \"abc\" == \"abc\" в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "В Kotlin == сравнивает контент строк."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-h-2",
                  "text": "Что вернёт \"Hello\".substring(1, 3)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт \"Hello\".substring(1, 3)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"He\""
                      },
                      {
                        "id": "b",
                        "text": "\"el\""
                      },
                      {
                        "id": "c",
                        "text": "\"ell\""
                      },
                      {
                        "id": "d",
                        "text": "\"llo\""
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "substring(1,3) — символы 1 и 2 (без 3)."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-h-3",
                  "text": "Что напечатает \"\" + 1 + 2 в Kotlin/Java?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает \"\" + 1 + 2 в Kotlin/Java?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"3\""
                      },
                      {
                        "id": "b",
                        "text": "\"12\""
                      },
                      {
                        "id": "c",
                        "text": "\"1+2\""
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Приоритет слева → \"1\" → \"12\"."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-h-4",
                  "text": "Что результат true || (1/0 > 0)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что результат true || (1/0 > 0)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "ArithmeticException"
                      },
                      {
                        "id": "d",
                        "text": "NaN"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Short-circuit: правая часть не вычисляется."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-sc-h-5",
                  "text": "Размер пустой строки \"\" — это:",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Размер пустой строки \"\" — это:",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0"
                      },
                      {
                        "id": "b",
                        "text": "1"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "-1"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Пустая строка содержит 0 символов."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-h-1",
                  "text": "Какие утверждения верны про String в Java/Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие утверждения верны про String в Java/Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Строки иммутабельны"
                      },
                      {
                        "id": "b",
                        "text": "String == сравнивает ссылки в Java"
                      },
                      {
                        "id": "c",
                        "text": "Kotlin String.equals — это =="
                      },
                      {
                        "id": "d",
                        "text": "Kotlin == — это ==="
                      },
                      {
                        "id": "e",
                        "text": "\"a\" + 1 даёт \"a1\""
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "В Kotlin == делегирует equals; === — ссылки."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-h-2",
                  "text": "Какие выражения дают true?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения дают true?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"abc\".isNotEmpty()"
                      },
                      {
                        "id": "b",
                        "text": "\"\" .isEmpty()"
                      },
                      {
                        "id": "c",
                        "text": "\"abc\".contains(\"b\")"
                      },
                      {
                        "id": "d",
                        "text": "\"abc\".startsWith(\"z\")"
                      },
                      {
                        "id": "e",
                        "text": "\"abc\".length == 3"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "startsWith(\"z\") даёт false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-h-3",
                  "text": "Какие операторы short-circuit?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие операторы short-circuit?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "&&"
                      },
                      {
                        "id": "b",
                        "text": "||"
                      },
                      {
                        "id": "c",
                        "text": "&"
                      },
                      {
                        "id": "d",
                        "text": "|"
                      },
                      {
                        "id": "e",
                        "text": "!"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b"
                    ],
                    "info": "&& и || не вычисляют правую часть, если результат уже определён."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-h-4",
                  "text": "Какие способы безопасной работы со String?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие способы безопасной работы со String?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "?.length"
                      },
                      {
                        "id": "b",
                        "text": "!!.length"
                      },
                      {
                        "id": "c",
                        "text": ".orEmpty()"
                      },
                      {
                        "id": "d",
                        "text": ".length ?: 0"
                      },
                      {
                        "id": "e",
                        "text": "String(null)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d"
                    ],
                    "info": "Безопасные паттерны Kotlin."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-mc-h-5",
                  "text": "Какие утверждения верны про immutable String?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие утверждения верны про immutable String?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Не меняется после создания"
                      },
                      {
                        "id": "b",
                        "text": "replace возвращает новую строку"
                      },
                      {
                        "id": "c",
                        "text": "toUpperCase меняет исходную"
                      },
                      {
                        "id": "d",
                        "text": "trim возвращает новую строку"
                      },
                      {
                        "id": "e",
                        "text": "concat создаёт новый объект"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d",
                      "e"
                    ],
                    "info": "Строки иммутабельны — все методы возвращают новые объекты."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-h-1",
                  "text": "Расположите этапы вычисления \"abc\".compareTo(\"abd\").",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите этапы вычисления \"abc\".compareTo(\"abd\").",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Сравнить a и a"
                      },
                      {
                        "id": "i2",
                        "text": "Сравнить b и b"
                      },
                      {
                        "id": "i3",
                        "text": "Сравнить c и d"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть -1"
                      }
                    ],
                    "info": "Лексикографическое сравнение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-h-2",
                  "text": "Расположите операции по приоритету (от высшего к низшему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите операции по приоритету (от высшего к низшему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "!"
                      },
                      {
                        "id": "i2",
                        "text": "&&"
                      },
                      {
                        "id": "i3",
                        "text": "||"
                      },
                      {
                        "id": "i4",
                        "text": "?:"
                      }
                    ],
                    "info": "NOT > AND > OR > Elvis."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-h-3",
                  "text": "Расположите шаги конкатенации цикла \"a\" + \"b\" + \"c\".",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги конкатенации цикла \"a\" + \"b\" + \"c\".",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "\"a\" + \"b\" → \"ab\""
                      },
                      {
                        "id": "i2",
                        "text": "выделение нового буфера"
                      },
                      {
                        "id": "i3",
                        "text": "\"ab\" + \"c\" → \"abc\""
                      },
                      {
                        "id": "i4",
                        "text": "возврат \"abc\""
                      }
                    ],
                    "info": "Каждая + создаёт новую строку."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-h-4",
                  "text": "Расположите методы по результату.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите методы по результату.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "\"abc\".length (3)"
                      },
                      {
                        "id": "i2",
                        "text": "\"abcd\".length (4)"
                      },
                      {
                        "id": "i3",
                        "text": "\"abcde\".length (5)"
                      },
                      {
                        "id": "i4",
                        "text": "\"abcdef\".length (6)"
                      }
                    ],
                    "info": "Возрастание длины."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-ord-h-5",
                  "text": "Расположите шаги interpolation \"$x is $y\".",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги interpolation \"$x is $y\".",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Парсинг шаблона"
                      },
                      {
                        "id": "i2",
                        "text": "Подстановка x"
                      },
                      {
                        "id": "i3",
                        "text": "Подстановка y"
                      },
                      {
                        "id": "i4",
                        "text": "Возврат результата"
                      }
                    ],
                    "info": "Stringinterpolation в Kotlin."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-h-1",
                  "text": "В Java оператор == для String сравнивает ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "В Java оператор == для String сравнивает ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "ссылки"
                      },
                      {
                        "id": "c2",
                        "text": "содержимое"
                      },
                      {
                        "id": "c3",
                        "text": "длину"
                      },
                      {
                        "id": "c4",
                        "text": "хеши"
                      },
                      {
                        "id": "c5",
                        "text": "байты"
                      }
                    ],
                    "info": "Используй equals() для контента."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-h-2",
                  "text": "В Kotlin == делегирует методу ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "В Kotlin == делегирует методу ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "equals"
                      },
                      {
                        "id": "c2",
                        "text": "compareTo"
                      },
                      {
                        "id": "c3",
                        "text": "hashCode"
                      },
                      {
                        "id": "c4",
                        "text": "toString"
                      },
                      {
                        "id": "c5",
                        "text": "identity"
                      }
                    ],
                    "info": "Для Int identity, для String equals."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-h-3",
                  "text": "Метод ___ возвращает true для пустой строки.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Метод ___ возвращает true для пустой строки.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "isEmpty()"
                      },
                      {
                        "id": "c2",
                        "text": "isBlank()"
                      },
                      {
                        "id": "c3",
                        "text": "length"
                      },
                      {
                        "id": "c4",
                        "text": "size()"
                      },
                      {
                        "id": "c5",
                        "text": "isNull()"
                      }
                    ],
                    "info": "isEmpty проверяет length==0."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-h-4",
                  "text": "Логическое НЕ обозначается ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Логическое НЕ обозначается ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "!"
                      },
                      {
                        "id": "c2",
                        "text": "~"
                      },
                      {
                        "id": "c3",
                        "text": "&&"
                      },
                      {
                        "id": "c4",
                        "text": "||"
                      },
                      {
                        "id": "c5",
                        "text": "not"
                      }
                    ],
                    "info": "! — унарный логический оператор."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-1-2-fb-h-5",
                  "text": "Тройные кавычки в Kotlin создают ___ строку.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Тройные кавычки в Kotlin создают ___ строку.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "raw"
                      },
                      {
                        "id": "c2",
                        "text": "компилируемую"
                      },
                      {
                        "id": "c3",
                        "text": "байтовую"
                      },
                      {
                        "id": "c4",
                        "text": "mutable"
                      },
                      {
                        "id": "c5",
                        "text": "StringBuilder"
                      }
                    ],
                    "info": "Raw string без обработки escape-последовательностей."
                  }
                }
              ]
            }
          ]
        },
        {
          "id": "tb-courses-programming-1-2",
          "title": "Операторы и выражения",
          "lessons": [
            {
              "id": "lb-courses-programming-1-2-1",
              "title": "Арифметические операторы",
              "questions": [
                {
                  "id": "qsb-courses-programming-1-2-1-sc-e-1",
                  "text": "Какой оператор обозначает сложение?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор обозначает сложение?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "+"
                      },
                      {
                        "id": "b",
                        "text": "-"
                      },
                      {
                        "id": "c",
                        "text": "*"
                      },
                      {
                        "id": "d",
                        "text": "/"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "+ складывает числа."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-e-2",
                  "text": "Какой оператор делит числа?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор делит числа?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "+"
                      },
                      {
                        "id": "b",
                        "text": "/"
                      },
                      {
                        "id": "c",
                        "text": "%"
                      },
                      {
                        "id": "d",
                        "text": "*"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "/ — деление."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-e-3",
                  "text": "Что возвращает 10 % 3?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что возвращает 10 % 3?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "3"
                      },
                      {
                        "id": "b",
                        "text": "1"
                      },
                      {
                        "id": "c",
                        "text": "0"
                      },
                      {
                        "id": "d",
                        "text": "10"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "% — остаток от деления, 10/3=3 ост 1."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-e-4",
                  "text": "Чему равно 5 * 4?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Чему равно 5 * 4?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "9"
                      },
                      {
                        "id": "b",
                        "text": "1"
                      },
                      {
                        "id": "c",
                        "text": "20"
                      },
                      {
                        "id": "d",
                        "text": "54"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Умножение чисел."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-e-5",
                  "text": "Чему равно 10 / 2?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Чему равно 10 / 2?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "5"
                      },
                      {
                        "id": "b",
                        "text": "20"
                      },
                      {
                        "id": "c",
                        "text": "0"
                      },
                      {
                        "id": "d",
                        "text": "12"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Целочисленное деление."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-e-1",
                  "text": "Какие операторы — арифметические?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операторы — арифметические?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "+"
                      },
                      {
                        "id": "b",
                        "text": "-"
                      },
                      {
                        "id": "c",
                        "text": "&&"
                      },
                      {
                        "id": "d",
                        "text": "*"
                      },
                      {
                        "id": "e",
                        "text": "/"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d",
                      "e"
                    ],
                    "info": "&& — логический."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-e-2",
                  "text": "Какие выражения дают 10?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения дают 10?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "5+5"
                      },
                      {
                        "id": "b",
                        "text": "20/2"
                      },
                      {
                        "id": "c",
                        "text": "2*5"
                      },
                      {
                        "id": "d",
                        "text": "13-3"
                      },
                      {
                        "id": "e",
                        "text": "11+0"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "11+0=11, остальные =10."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-e-3",
                  "text": "Какие выражения возвращают 0?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения возвращают 0?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "5-5"
                      },
                      {
                        "id": "b",
                        "text": "0*100"
                      },
                      {
                        "id": "c",
                        "text": "10%5"
                      },
                      {
                        "id": "d",
                        "text": "0+0"
                      },
                      {
                        "id": "e",
                        "text": "1/0.5"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "1/0.5=2."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-e-4",
                  "text": "Какие операции коммутативны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операции коммутативны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Сложение"
                      },
                      {
                        "id": "b",
                        "text": "Умножение"
                      },
                      {
                        "id": "c",
                        "text": "Вычитание"
                      },
                      {
                        "id": "d",
                        "text": "Деление"
                      },
                      {
                        "id": "e",
                        "text": "Остаток"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b"
                    ],
                    "info": "a-b≠b-a, a/b≠b/a."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-e-5",
                  "text": "Какие операторы могут переполнить Int?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операторы могут переполнить Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "+"
                      },
                      {
                        "id": "b",
                        "text": "*"
                      },
                      {
                        "id": "c",
                        "text": "-"
                      },
                      {
                        "id": "d",
                        "text": "%"
                      },
                      {
                        "id": "e",
                        "text": "/"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "/ и % обычно не переполняют (кроме MIN/-1)."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-e-1",
                  "text": "Расположите операторы по приоритету (от высшего к низшему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите операторы по приоритету (от высшего к низшему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Унарный минус"
                      },
                      {
                        "id": "i2",
                        "text": "Умножение"
                      },
                      {
                        "id": "i3",
                        "text": "Сложение"
                      },
                      {
                        "id": "i4",
                        "text": "Присвоение"
                      }
                    ],
                    "info": "Стандартный порядок арифметики."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-e-2",
                  "text": "Расположите шаги вычисления 2 + 3 * 4.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги вычисления 2 + 3 * 4.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Прочитать 2, 3, 4"
                      },
                      {
                        "id": "i2",
                        "text": "Вычислить 3*4=12"
                      },
                      {
                        "id": "i3",
                        "text": "Вычислить 2+12=14"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть 14"
                      }
                    ],
                    "info": "Умножение раньше сложения."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-e-3",
                  "text": "Расположите выражения по результату.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите выражения по результату.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "2+0 (2)"
                      },
                      {
                        "id": "i2",
                        "text": "2+1 (3)"
                      },
                      {
                        "id": "i3",
                        "text": "2+2 (4)"
                      },
                      {
                        "id": "i4",
                        "text": "2+3 (5)"
                      }
                    ],
                    "info": "Возрастание."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-e-4",
                  "text": "Расположите по приоритету.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите по приоритету.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "скобки ()"
                      },
                      {
                        "id": "i2",
                        "text": "умножение/деление"
                      },
                      {
                        "id": "i3",
                        "text": "сложение/вычитание"
                      },
                      {
                        "id": "i4",
                        "text": "сравнение"
                      }
                    ],
                    "info": "Скобки максимально приоритетны."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-e-5",
                  "text": "Расположите шаги вычисления (2 + 3) * 4.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги вычисления (2 + 3) * 4.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Парсинг скобок"
                      },
                      {
                        "id": "i2",
                        "text": "Сложение 2+3=5"
                      },
                      {
                        "id": "i3",
                        "text": "Умножение 5*4=20"
                      },
                      {
                        "id": "i4",
                        "text": "Возврат 20"
                      }
                    ],
                    "info": "Скобки меняют порядок."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-e-1",
                  "text": "Оператор ___ возвращает остаток от деления.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Оператор ___ возвращает остаток от деления.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "%"
                      },
                      {
                        "id": "c2",
                        "text": "/"
                      },
                      {
                        "id": "c3",
                        "text": "*"
                      },
                      {
                        "id": "c4",
                        "text": "+"
                      },
                      {
                        "id": "c5",
                        "text": "&"
                      }
                    ],
                    "info": "% — модуль/остаток."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-e-2",
                  "text": "Сложение обозначается ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Сложение обозначается ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "+"
                      },
                      {
                        "id": "c2",
                        "text": "-"
                      },
                      {
                        "id": "c3",
                        "text": "*"
                      },
                      {
                        "id": "c4",
                        "text": "/"
                      },
                      {
                        "id": "c5",
                        "text": "%"
                      }
                    ],
                    "info": "+ — сложение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-e-3",
                  "text": "Умножение в коде — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Умножение в коде — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "*"
                      },
                      {
                        "id": "c2",
                        "text": "x"
                      },
                      {
                        "id": "c3",
                        "text": "×"
                      },
                      {
                        "id": "c4",
                        "text": "·"
                      },
                      {
                        "id": "c5",
                        "text": "^"
                      }
                    ],
                    "info": "В большинстве языков умножение — *."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-e-4",
                  "text": "Унарный минус обозначается ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Унарный минус обозначается ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "-"
                      },
                      {
                        "id": "c2",
                        "text": "+"
                      },
                      {
                        "id": "c3",
                        "text": "!"
                      },
                      {
                        "id": "c4",
                        "text": "~"
                      },
                      {
                        "id": "c5",
                        "text": "*"
                      }
                    ],
                    "info": "Префиксный -x."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-e-5",
                  "text": "10 / 3 в Int даёт ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "10 / 3 в Int даёт ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "3"
                      },
                      {
                        "id": "c2",
                        "text": "3.33"
                      },
                      {
                        "id": "c3",
                        "text": "4"
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "10"
                      }
                    ],
                    "info": "Целочисленное деление отбрасывает дробь."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-h-1",
                  "text": "Что вернёт -7 % 3 в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт -7 % 3 в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1"
                      },
                      {
                        "id": "b",
                        "text": "-1"
                      },
                      {
                        "id": "c",
                        "text": "2"
                      },
                      {
                        "id": "d",
                        "text": "-2"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "В Kotlin/Java знак результата % совпадает со знаком делимого."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-h-2",
                  "text": "Что вернёт 7 / 2 для Int?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт 7 / 2 для Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "3.5"
                      },
                      {
                        "id": "b",
                        "text": "3"
                      },
                      {
                        "id": "c",
                        "text": "4"
                      },
                      {
                        "id": "d",
                        "text": "0"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Целочисленное деление обрезает."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-h-3",
                  "text": "Что вернёт 7.0 / 2 в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт 7.0 / 2 в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "3"
                      },
                      {
                        "id": "b",
                        "text": "3.5"
                      },
                      {
                        "id": "c",
                        "text": "4"
                      },
                      {
                        "id": "d",
                        "text": "0.5"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Если хотя бы один Double — деление вещественное."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-h-4",
                  "text": "Что напечатает Int.MIN_VALUE / -1?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает Int.MIN_VALUE / -1?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int.MAX_VALUE"
                      },
                      {
                        "id": "b",
                        "text": "0"
                      },
                      {
                        "id": "c",
                        "text": "Int.MIN_VALUE"
                      },
                      {
                        "id": "d",
                        "text": "ArithmeticException"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "|MIN_VALUE| не помещается в Int — переполнение возвращает MIN_VALUE."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-sc-h-5",
                  "text": "Что вернёт 10 % 0 для Int?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт 10 % 0 для Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0"
                      },
                      {
                        "id": "b",
                        "text": "10"
                      },
                      {
                        "id": "c",
                        "text": "Infinity"
                      },
                      {
                        "id": "d",
                        "text": "ArithmeticException"
                      }
                    ],
                    "correctOptionId": "d",
                    "info": "Целочисленный mod 0 — исключение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-h-1",
                  "text": "Какие выражения вычисляются как 6 (Int)?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения вычисляются как 6 (Int)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "2*3"
                      },
                      {
                        "id": "b",
                        "text": "12/2"
                      },
                      {
                        "id": "c",
                        "text": "7%6+5"
                      },
                      {
                        "id": "d",
                        "text": "8-2"
                      },
                      {
                        "id": "e",
                        "text": "3+3"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d",
                      "e"
                    ],
                    "info": "7%6=1, +5=6 — тоже 6."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-h-2",
                  "text": "Какие свойства верны для арифметики Int?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие свойства верны для арифметики Int?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Ассоциативность сложения"
                      },
                      {
                        "id": "b",
                        "text": "Коммутативность вычитания"
                      },
                      {
                        "id": "c",
                        "text": "Возможность переполнения"
                      },
                      {
                        "id": "d",
                        "text": "Коммутативность умножения"
                      },
                      {
                        "id": "e",
                        "text": "Деление точное"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d"
                    ],
                    "info": "Вычитание не коммутативно; целочисленное деление обрезает."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-h-3",
                  "text": "Какие выражения корректны и не вызовут ошибку?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения корректны и не вызовут ошибку?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1+1"
                      },
                      {
                        "id": "b",
                        "text": "1/0.0"
                      },
                      {
                        "id": "c",
                        "text": "1.0/0.0"
                      },
                      {
                        "id": "d",
                        "text": "1/0"
                      },
                      {
                        "id": "e",
                        "text": "Int.MAX_VALUE+1"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "1/0 (Int) — ArithmeticException."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-h-4",
                  "text": "Какие выражения дают отрицательный остаток в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения дают отрицательный остаток в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "-5%3"
                      },
                      {
                        "id": "b",
                        "text": "5%-3"
                      },
                      {
                        "id": "c",
                        "text": "-5%-3"
                      },
                      {
                        "id": "d",
                        "text": "5%3"
                      },
                      {
                        "id": "e",
                        "text": "0%5"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c"
                    ],
                    "info": "Знак mod в Kotlin = знак делимого."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-mc-h-5",
                  "text": "Какие особенности у деления чисел с плавающей точкой?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие особенности у деления чисел с плавающей точкой?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1.0/0.0=Infinity"
                      },
                      {
                        "id": "b",
                        "text": "0.0/0.0=NaN"
                      },
                      {
                        "id": "c",
                        "text": "1.0/0.0=Exception"
                      },
                      {
                        "id": "d",
                        "text": "-1.0/0.0=-Infinity"
                      },
                      {
                        "id": "e",
                        "text": "0/0.0=NaN"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d",
                      "e"
                    ],
                    "info": "IEEE 754 определяет специальные значения."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-h-1",
                  "text": "Расположите операторы по приоритету (от высшего к низшему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите операторы по приоритету (от высшего к низшему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Унарные (-x, !x)"
                      },
                      {
                        "id": "i2",
                        "text": "Умножение/Деление/Mod"
                      },
                      {
                        "id": "i3",
                        "text": "Сложение/Вычитание"
                      },
                      {
                        "id": "i4",
                        "text": "Сравнение"
                      }
                    ],
                    "info": "Стандартная иерархия."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-h-2",
                  "text": "Расположите шаги вычисления 2 + 3 * 4 - 1.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги вычисления 2 + 3 * 4 - 1.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "3*4 = 12"
                      },
                      {
                        "id": "i2",
                        "text": "2+12 = 14"
                      },
                      {
                        "id": "i3",
                        "text": "14-1 = 13"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть 13"
                      }
                    ],
                    "info": "Сначала умножение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-h-3",
                  "text": "Расположите выражения по возрастанию результата.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите выражения по возрастанию результата.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "10 % 7 (3)"
                      },
                      {
                        "id": "i2",
                        "text": "10 / 2 (5)"
                      },
                      {
                        "id": "i3",
                        "text": "10 - 3 (7)"
                      },
                      {
                        "id": "i4",
                        "text": "10 + 0 (10)"
                      }
                    ],
                    "info": "Сортировка результатов."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-h-4",
                  "text": "Расположите шаги переполнения Int.MAX_VALUE+1.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги переполнения Int.MAX_VALUE+1.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "MAX_VALUE = 2^31-1"
                      },
                      {
                        "id": "i2",
                        "text": "Прибавляем 1"
                      },
                      {
                        "id": "i3",
                        "text": "2^31 не помещается в 32 бит"
                      },
                      {
                        "id": "i4",
                        "text": "Результат = -2^31 (MIN_VALUE)"
                      }
                    ],
                    "info": "Two-complement."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-ord-h-5",
                  "text": "Расположите шаги вычисления 7.0/2 - 1.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги вычисления 7.0/2 - 1.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "7.0/2 = 3.5"
                      },
                      {
                        "id": "i2",
                        "text": "3.5 - 1 = 2.5"
                      },
                      {
                        "id": "i3",
                        "text": "Возврат 2.5"
                      },
                      {
                        "id": "i4",
                        "text": "Тип результата Double"
                      }
                    ],
                    "info": "Float arithmetic."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-h-1",
                  "text": "Знак результата % в Kotlin совпадает со знаком ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Знак результата % в Kotlin совпадает со знаком ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "делимого"
                      },
                      {
                        "id": "c2",
                        "text": "делителя"
                      },
                      {
                        "id": "c3",
                        "text": "единицы"
                      },
                      {
                        "id": "c4",
                        "text": "нуля"
                      },
                      {
                        "id": "c5",
                        "text": "абсолюта"
                      }
                    ],
                    "info": "Особенность Kotlin/Java."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-h-2",
                  "text": "Деление 1.0/0.0 даёт ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Деление 1.0/0.0 даёт ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Infinity"
                      },
                      {
                        "id": "c2",
                        "text": "NaN"
                      },
                      {
                        "id": "c3",
                        "text": "0"
                      },
                      {
                        "id": "c4",
                        "text": "Exception"
                      },
                      {
                        "id": "c5",
                        "text": "MAX_VALUE"
                      }
                    ],
                    "info": "IEEE 754."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-h-3",
                  "text": "Целочисленное деление 10/3 даёт ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Целочисленное деление 10/3 даёт ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "3"
                      },
                      {
                        "id": "c2",
                        "text": "3.33"
                      },
                      {
                        "id": "c3",
                        "text": "4"
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "10"
                      }
                    ],
                    "info": "Дробь отбрасывается."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-h-4",
                  "text": "Int.MAX_VALUE+1 даёт ___ из-за переполнения.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Int.MAX_VALUE+1 даёт ___ из-за переполнения.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Int.MIN_VALUE"
                      },
                      {
                        "id": "c2",
                        "text": "0"
                      },
                      {
                        "id": "c3",
                        "text": "Long.MAX_VALUE"
                      },
                      {
                        "id": "c4",
                        "text": "Infinity"
                      },
                      {
                        "id": "c5",
                        "text": "NaN"
                      }
                    ],
                    "info": "Two-complement wrap."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-1-fb-h-5",
                  "text": "Int / 0 бросает ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Int / 0 бросает ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "ArithmeticException"
                      },
                      {
                        "id": "c2",
                        "text": "NullPointerException"
                      },
                      {
                        "id": "c3",
                        "text": "IllegalArgumentException"
                      },
                      {
                        "id": "c4",
                        "text": "NaN"
                      },
                      {
                        "id": "c5",
                        "text": "Infinity"
                      }
                    ],
                    "info": "Только для Int. Для Double — Infinity."
                  }
                }
              ]
            },
            {
              "id": "lb-courses-programming-1-2-2",
              "title": "Логические операторы",
              "questions": [
                {
                  "id": "qsb-courses-programming-1-2-2-sc-e-1",
                  "text": "Какой оператор обозначает логическое И?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор обозначает логическое И?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "&&"
                      },
                      {
                        "id": "b",
                        "text": "||"
                      },
                      {
                        "id": "c",
                        "text": "!"
                      },
                      {
                        "id": "d",
                        "text": "=="
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "&& — конъюнкция."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-e-2",
                  "text": "Какой оператор обозначает логическое ИЛИ?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор обозначает логическое ИЛИ?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "||"
                      },
                      {
                        "id": "b",
                        "text": "&&"
                      },
                      {
                        "id": "c",
                        "text": "!"
                      },
                      {
                        "id": "d",
                        "text": "!="
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "|| — дизъюнкция."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-e-3",
                  "text": "Что вернёт !true?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что вернёт !true?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "! инвертирует."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-e-4",
                  "text": "Что вернёт true && false?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что вернёт true && false?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Любое false делает && false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-e-5",
                  "text": "Что вернёт true || false?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что вернёт true || false?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Любое true делает || true."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-e-1",
                  "text": "Какие операторы логические?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операторы логические?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "&&"
                      },
                      {
                        "id": "b",
                        "text": "||"
                      },
                      {
                        "id": "c",
                        "text": "!"
                      },
                      {
                        "id": "d",
                        "text": "+"
                      },
                      {
                        "id": "e",
                        "text": "%"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "+ и % — арифметические."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-e-2",
                  "text": "Какие выражения дают true?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения дают true?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true && true"
                      },
                      {
                        "id": "b",
                        "text": "false || true"
                      },
                      {
                        "id": "c",
                        "text": "!false"
                      },
                      {
                        "id": "d",
                        "text": "false && true"
                      },
                      {
                        "id": "e",
                        "text": "!true"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "Остальные — false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-e-3",
                  "text": "Какие сравнения дают true для x=5?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие сравнения дают true для x=5?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "x > 0"
                      },
                      {
                        "id": "b",
                        "text": "x == 5"
                      },
                      {
                        "id": "c",
                        "text": "x < 10"
                      },
                      {
                        "id": "d",
                        "text": "x != 5"
                      },
                      {
                        "id": "e",
                        "text": "x > 100"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "x≠5 и x>100 — false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-e-4",
                  "text": "Какие операторы short-circuit?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операторы short-circuit?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "&&"
                      },
                      {
                        "id": "b",
                        "text": "||"
                      },
                      {
                        "id": "c",
                        "text": "&"
                      },
                      {
                        "id": "d",
                        "text": "|"
                      },
                      {
                        "id": "e",
                        "text": "!"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b"
                    ],
                    "info": "& и | — bitwise/non-shortcircuit."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-e-5",
                  "text": "Какие выражения эквивалентны !(a && b)?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения эквивалентны !(a && b)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "!a || !b"
                      },
                      {
                        "id": "b",
                        "text": "!a && !b"
                      },
                      {
                        "id": "c",
                        "text": "(!a) || (!b)"
                      },
                      {
                        "id": "d",
                        "text": "!a + !b"
                      },
                      {
                        "id": "e",
                        "text": "a || b"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c"
                    ],
                    "info": "Закон де Моргана."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-e-1",
                  "text": "Расположите операторы по приоритету (от высшего к низшему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите операторы по приоритету (от высшего к низшему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "!"
                      },
                      {
                        "id": "i2",
                        "text": "&&"
                      },
                      {
                        "id": "i3",
                        "text": "||"
                      },
                      {
                        "id": "i4",
                        "text": "?:"
                      }
                    ],
                    "info": "NOT > AND > OR > Elvis."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-e-2",
                  "text": "Расположите выражения по результату (false → true).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите выражения по результату (false → true).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "false && true (false)"
                      },
                      {
                        "id": "i2",
                        "text": "true && false (false)"
                      },
                      {
                        "id": "i3",
                        "text": "true && true (true)"
                      },
                      {
                        "id": "i4",
                        "text": "!false (true)"
                      }
                    ],
                    "info": "Сначала false, потом true."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-e-3",
                  "text": "Расположите этапы вычисления a && b.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите этапы вычисления a && b.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить a"
                      },
                      {
                        "id": "i2",
                        "text": "Если a=false → вернуть false"
                      },
                      {
                        "id": "i3",
                        "text": "Если a=true → вычислить b"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть b"
                      }
                    ],
                    "info": "Short-circuit AND."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-e-4",
                  "text": "Расположите этапы вычисления a || b.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите этапы вычисления a || b.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить a"
                      },
                      {
                        "id": "i2",
                        "text": "Если a=true → вернуть true"
                      },
                      {
                        "id": "i3",
                        "text": "Если a=false → вычислить b"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть b"
                      }
                    ],
                    "info": "Short-circuit OR."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-e-5",
                  "text": "Расположите шаги проверки x>0 && x<10 при x=5.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги проверки x>0 && x<10 при x=5.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить x>0 (true)"
                      },
                      {
                        "id": "i2",
                        "text": "Вычислить x<10 (true)"
                      },
                      {
                        "id": "i3",
                        "text": "Логическое AND"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть true"
                      }
                    ],
                    "info": "Оба условия true."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-e-1",
                  "text": "Логическое И — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Логическое И — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "&&"
                      },
                      {
                        "id": "c2",
                        "text": "||"
                      },
                      {
                        "id": "c3",
                        "text": "!"
                      },
                      {
                        "id": "c4",
                        "text": "&"
                      },
                      {
                        "id": "c5",
                        "text": "|"
                      }
                    ],
                    "info": "&& — короткое замыкание AND."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-e-2",
                  "text": "Логическое ИЛИ — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Логическое ИЛИ — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "||"
                      },
                      {
                        "id": "c2",
                        "text": "&&"
                      },
                      {
                        "id": "c3",
                        "text": "!"
                      },
                      {
                        "id": "c4",
                        "text": "|"
                      },
                      {
                        "id": "c5",
                        "text": "&"
                      }
                    ],
                    "info": "Короткое замыкание OR."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-e-3",
                  "text": "Логическое НЕ — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Логическое НЕ — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "!"
                      },
                      {
                        "id": "c2",
                        "text": "~"
                      },
                      {
                        "id": "c3",
                        "text": "-"
                      },
                      {
                        "id": "c4",
                        "text": "not"
                      },
                      {
                        "id": "c5",
                        "text": "no"
                      }
                    ],
                    "info": "Унарное отрицание."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-e-4",
                  "text": "true && false = ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "true && false = ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "false"
                      },
                      {
                        "id": "c2",
                        "text": "true"
                      },
                      {
                        "id": "c3",
                        "text": "null"
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "1"
                      }
                    ],
                    "info": "AND требует обоих true."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-e-5",
                  "text": "true || false = ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "true || false = ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "true"
                      },
                      {
                        "id": "c2",
                        "text": "false"
                      },
                      {
                        "id": "c3",
                        "text": "null"
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "1"
                      }
                    ],
                    "info": "OR достаточно одного true."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-h-1",
                  "text": "Что вернёт true || (1/0 == 0) при short-circuit?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт true || (1/0 == 0) при short-circuit?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "ArithmeticException"
                      },
                      {
                        "id": "d",
                        "text": "NaN"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Правая часть не вычисляется."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-h-2",
                  "text": "Что результат !(false || true)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что результат !(false || true)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "!(true) = false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-h-3",
                  "text": "Что вернёт true ^ true (XOR)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт true ^ true (XOR)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "null"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "XOR одинаковых = false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-h-4",
                  "text": "Закон де Моргана: !(a && b) = ?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Закон де Моргана: !(a && b) = ?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "!a && !b"
                      },
                      {
                        "id": "b",
                        "text": "!a || !b"
                      },
                      {
                        "id": "c",
                        "text": "a && b"
                      },
                      {
                        "id": "d",
                        "text": "a || b"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "!(AND) = !a OR !b."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-sc-h-5",
                  "text": "Что вернёт false && (1/0 > 0)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт false && (1/0 > 0)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "true"
                      },
                      {
                        "id": "b",
                        "text": "false"
                      },
                      {
                        "id": "c",
                        "text": "ArithmeticException"
                      },
                      {
                        "id": "d",
                        "text": "NaN"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Short-circuit: правая часть не выполняется."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-h-1",
                  "text": "Какие пары выражений эквивалентны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие пары выражений эквивалентны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "!(a&&b) ≡ !a||!b"
                      },
                      {
                        "id": "b",
                        "text": "!(a||b) ≡ !a&&!b"
                      },
                      {
                        "id": "c",
                        "text": "a&&b ≡ b&&a"
                      },
                      {
                        "id": "d",
                        "text": "a||b ≡ !(!a&&!b)"
                      },
                      {
                        "id": "e",
                        "text": "a&&b ≡ !(!a||!b)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d",
                      "e"
                    ],
                    "info": "Все верны (де Морган + коммутативность)."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-h-2",
                  "text": "Какие свойства верны для && и ||?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие свойства верны для && и ||?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Ассоциативность"
                      },
                      {
                        "id": "b",
                        "text": "Коммутативность"
                      },
                      {
                        "id": "c",
                        "text": "Дистрибутивность"
                      },
                      {
                        "id": "d",
                        "text": "Short-circuit"
                      },
                      {
                        "id": "e",
                        "text": "Side-effect-free всегда"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "Side-effects могут быть в правой части."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-h-3",
                  "text": "Какие выражения дают true при a=true, b=false?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения дают true при a=true, b=false?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "a||b"
                      },
                      {
                        "id": "b",
                        "text": "!b"
                      },
                      {
                        "id": "c",
                        "text": "a&&!b"
                      },
                      {
                        "id": "d",
                        "text": "!a&&b"
                      },
                      {
                        "id": "e",
                        "text": "a^b"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "!a&&b = false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-h-4",
                  "text": "Какие операторы могут не вычислять второй операнд?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие операторы могут не вычислять второй операнд?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "&&"
                      },
                      {
                        "id": "b",
                        "text": "||"
                      },
                      {
                        "id": "c",
                        "text": "?:"
                      },
                      {
                        "id": "d",
                        "text": "&"
                      },
                      {
                        "id": "e",
                        "text": "|"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "Elvis ?: — тоже short-circuit."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-mc-h-5",
                  "text": "Какие выражения true при x=5?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения true при x=5?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "x>0&&x<10"
                      },
                      {
                        "id": "b",
                        "text": "x==5||x==10"
                      },
                      {
                        "id": "c",
                        "text": "!(x<0)"
                      },
                      {
                        "id": "d",
                        "text": "x>10||x<0"
                      },
                      {
                        "id": "e",
                        "text": "!(x==5)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "x>10||x<0 и !(x==5) — false."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-h-1",
                  "text": "Расположите операторы по приоритету (от высшего к низшему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите операторы по приоритету (от высшего к низшему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "!"
                      },
                      {
                        "id": "i2",
                        "text": "&&"
                      },
                      {
                        "id": "i3",
                        "text": "||"
                      },
                      {
                        "id": "i4",
                        "text": "?:"
                      }
                    ],
                    "info": "NOT > AND > OR > Elvis."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-h-2",
                  "text": "Расположите шаги вычисления !(a||b)&&c при a=true.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги вычисления !(a||b)&&c при a=true.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить a||b (true)"
                      },
                      {
                        "id": "i2",
                        "text": "Применить ! → false"
                      },
                      {
                        "id": "i3",
                        "text": "false && c → false"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть false"
                      }
                    ],
                    "info": "Short-circuit at &&."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-h-3",
                  "text": "Расположите по де Моргану: !(a&&b&&c) =",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите по де Моргану: !(a&&b&&c) =",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Применить !"
                      },
                      {
                        "id": "i2",
                        "text": "!a||!(b&&c)"
                      },
                      {
                        "id": "i3",
                        "text": "!a||!b||!c"
                      },
                      {
                        "id": "i4",
                        "text": "Раскрытие OR"
                      }
                    ],
                    "info": "Поэтапное применение."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-h-4",
                  "text": "Расположите шаги вычисления (a&&b)||(c&&d) при a=false, c=true, d=true.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги вычисления (a&&b)||(c&&d) при a=false, c=true, d=true.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "a=false → пропустить b"
                      },
                      {
                        "id": "i2",
                        "text": "a&&b = false"
                      },
                      {
                        "id": "i3",
                        "text": "c&&d = true"
                      },
                      {
                        "id": "i4",
                        "text": "false||true = true"
                      }
                    ],
                    "info": "Short-circuit dual."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-ord-h-5",
                  "text": "Расположите выражения по логической силе (от слабого к сильному).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите выражения по логической силе (от слабого к сильному).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "false"
                      },
                      {
                        "id": "i2",
                        "text": "a && false"
                      },
                      {
                        "id": "i3",
                        "text": "a || false"
                      },
                      {
                        "id": "i4",
                        "text": "true"
                      }
                    ],
                    "info": "Сила — частота true."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-h-1",
                  "text": "Закон де Моргана: !(a&&b) = !a ___ !b.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Закон де Моргана: !(a&&b) = !a ___ !b.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "||"
                      },
                      {
                        "id": "c2",
                        "text": "&&"
                      },
                      {
                        "id": "c3",
                        "text": "!"
                      },
                      {
                        "id": "c4",
                        "text": "+"
                      },
                      {
                        "id": "c5",
                        "text": "XOR"
                      }
                    ],
                    "info": "NOT(AND) = OR(NOT,NOT)."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-h-2",
                  "text": "Закон де Моргана: !(a||b) = !a ___ !b.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Закон де Моргана: !(a||b) = !a ___ !b.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "&&"
                      },
                      {
                        "id": "c2",
                        "text": "||"
                      },
                      {
                        "id": "c3",
                        "text": "!"
                      },
                      {
                        "id": "c4",
                        "text": "+"
                      },
                      {
                        "id": "c5",
                        "text": "XOR"
                      }
                    ],
                    "info": "NOT(OR) = AND(NOT,NOT)."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-h-3",
                  "text": "Оператор ___ — short-circuit AND.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Оператор ___ — short-circuit AND.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "&&"
                      },
                      {
                        "id": "c2",
                        "text": "&"
                      },
                      {
                        "id": "c3",
                        "text": "||"
                      },
                      {
                        "id": "c4",
                        "text": "!"
                      },
                      {
                        "id": "c5",
                        "text": "^"
                      }
                    ],
                    "info": "& — non-short-circuit/bitwise."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-h-4",
                  "text": "Оператор ___ — short-circuit OR.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Оператор ___ — short-circuit OR.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "||"
                      },
                      {
                        "id": "c2",
                        "text": "|"
                      },
                      {
                        "id": "c3",
                        "text": "&&"
                      },
                      {
                        "id": "c4",
                        "text": "!"
                      },
                      {
                        "id": "c5",
                        "text": "^"
                      }
                    ],
                    "info": "| — bitwise OR."
                  }
                },
                {
                  "id": "qsb-courses-programming-1-2-2-fb-h-5",
                  "text": "XOR одинаковых аргументов даёт ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "XOR одинаковых аргументов даёт ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "false"
                      },
                      {
                        "id": "c2",
                        "text": "true"
                      },
                      {
                        "id": "c3",
                        "text": "null"
                      },
                      {
                        "id": "c4",
                        "text": "0"
                      },
                      {
                        "id": "c5",
                        "text": "1"
                      }
                    ],
                    "info": "true XOR true = false."
                  }
                }
              ]
            }
          ]
        }
      ]
    },
    {
      "id": "sb-courses-programming-2",
      "title": "Управление потоком",
      "themes": [
        {
          "id": "tb-courses-programming-2-1",
          "title": "Условные конструкции",
          "lessons": [
            {
              "id": "lb-courses-programming-2-1-1",
              "title": "if-else",
              "questions": [
                {
                  "id": "qsb-courses-programming-2-1-1-sc-e-1",
                  "text": "Какое ключевое слово начинает условную конструкцию?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какое ключевое слово начинает условную конструкцию?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "if"
                      },
                      {
                        "id": "b",
                        "text": "when"
                      },
                      {
                        "id": "c",
                        "text": "for"
                      },
                      {
                        "id": "d",
                        "text": "do"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "if — основа условий."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-e-2",
                  "text": "Какое слово используется для альтернативной ветки?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какое слово используется для альтернативной ветки?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "then"
                      },
                      {
                        "id": "b",
                        "text": "else"
                      },
                      {
                        "id": "c",
                        "text": "or"
                      },
                      {
                        "id": "d",
                        "text": "case"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "else — иначе."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-e-3",
                  "text": "Что выполнится при if(true){A}else{B}?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что выполнится при if(true){A}else{B}?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "A"
                      },
                      {
                        "id": "b",
                        "text": "B"
                      },
                      {
                        "id": "c",
                        "text": "Оба"
                      },
                      {
                        "id": "d",
                        "text": "Ничего"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Истина → ветка A."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-e-4",
                  "text": "Что выполнится при if(false){A}else{B}?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что выполнится при if(false){A}else{B}?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "A"
                      },
                      {
                        "id": "b",
                        "text": "B"
                      },
                      {
                        "id": "c",
                        "text": "Оба"
                      },
                      {
                        "id": "d",
                        "text": "Ничего"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Ложь → ветка else."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-e-5",
                  "text": "Можно ли в Kotlin использовать if как выражение?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Можно ли в Kotlin использовать if как выражение?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Да, возвращает значение"
                      },
                      {
                        "id": "b",
                        "text": "Нет, только statement"
                      },
                      {
                        "id": "c",
                        "text": "Только в when"
                      },
                      {
                        "id": "d",
                        "text": "Только в loop"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "val x = if(...) a else b."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-e-1",
                  "text": "Какие конструкции корректны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие конструкции корректны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "if(x>0){...}"
                      },
                      {
                        "id": "b",
                        "text": "if x>0 then ..."
                      },
                      {
                        "id": "c",
                        "text": "if(x>0){...}else{...}"
                      },
                      {
                        "id": "d",
                        "text": "if(x>0){...}else if(x<0){...}else{...}"
                      },
                      {
                        "id": "e",
                        "text": "if then x>0 do ..."
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d"
                    ],
                    "info": "Синтаксис Kotlin/Java."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-e-2",
                  "text": "Какие условия дают true для x=5?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие условия дают true для x=5?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "x>0"
                      },
                      {
                        "id": "b",
                        "text": "x==5"
                      },
                      {
                        "id": "c",
                        "text": "x<10"
                      },
                      {
                        "id": "d",
                        "text": "x!=5"
                      },
                      {
                        "id": "e",
                        "text": "x>=10"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "x≠5 и x≥10 — false."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-e-3",
                  "text": "Какие ветви могут быть в if-else?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие ветви могут быть в if-else?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "then-ветвь"
                      },
                      {
                        "id": "b",
                        "text": "else-ветвь"
                      },
                      {
                        "id": "c",
                        "text": "else-if цепочка"
                      },
                      {
                        "id": "d",
                        "text": "finally"
                      },
                      {
                        "id": "e",
                        "text": "catch"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "finally/catch — try-catch."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-e-4",
                  "text": "Какие типы можно использовать в условии if?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие типы можно использовать в условии if?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Boolean"
                      },
                      {
                        "id": "b",
                        "text": "Boolean expression"
                      },
                      {
                        "id": "c",
                        "text": "Int (только Java truthy?)"
                      },
                      {
                        "id": "d",
                        "text": "String"
                      },
                      {
                        "id": "e",
                        "text": "null"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b"
                    ],
                    "info": "В Kotlin/Java только Boolean."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-e-5",
                  "text": "Какие случаи использования if?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие случаи использования if?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Проверка ввода"
                      },
                      {
                        "id": "b",
                        "text": "Ветвление логики"
                      },
                      {
                        "id": "c",
                        "text": "Замена цикла"
                      },
                      {
                        "id": "d",
                        "text": "Возврат разных значений"
                      },
                      {
                        "id": "e",
                        "text": "Объявление класса"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d"
                    ],
                    "info": "Цикл и класс — другие конструкции."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-e-1",
                  "text": "Расположите шаги выполнения if(x>0){A}else{B}.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги выполнения if(x>0){A}else{B}.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить x>0"
                      },
                      {
                        "id": "i2",
                        "text": "Если true → A"
                      },
                      {
                        "id": "i3",
                        "text": "Если false → B"
                      },
                      {
                        "id": "i4",
                        "text": "Продолжить выполнение"
                      }
                    ],
                    "info": "Стандартный поток."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-e-2",
                  "text": "Расположите if-else цепочку (от первого к последнему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите if-else цепочку (от первого к последнему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "if"
                      },
                      {
                        "id": "i2",
                        "text": "else if"
                      },
                      {
                        "id": "i3",
                        "text": "else if (другое условие)"
                      },
                      {
                        "id": "i4",
                        "text": "else"
                      }
                    ],
                    "info": "Цепочка проверок."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-e-3",
                  "text": "Расположите выражения по сложности.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите выражения по сложности.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "if(a)"
                      },
                      {
                        "id": "i2",
                        "text": "if(a&&b)"
                      },
                      {
                        "id": "i3",
                        "text": "if(a&&b||c)"
                      },
                      {
                        "id": "i4",
                        "text": "if((a||b)&&(c||d))"
                      }
                    ],
                    "info": "Возрастание сложности."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-e-4",
                  "text": "Расположите шаги отладки if.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги отладки if.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Поставить breakpoint"
                      },
                      {
                        "id": "i2",
                        "text": "Запустить"
                      },
                      {
                        "id": "i3",
                        "text": "Проверить значение условия"
                      },
                      {
                        "id": "i4",
                        "text": "Зайти в нужную ветку"
                      }
                    ],
                    "info": "Алгоритм отладки."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-e-5",
                  "text": "Расположите шаги преобразования if в expression.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги преобразования if в expression.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Объявить val"
                      },
                      {
                        "id": "i2",
                        "text": "= if(...)"
                      },
                      {
                        "id": "i3",
                        "text": "then-value"
                      },
                      {
                        "id": "i4",
                        "text": "else-value"
                      }
                    ],
                    "info": "val x = if(c) a else b."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-e-1",
                  "text": "Альтернативная ветка — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Альтернативная ветка — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "else"
                      },
                      {
                        "id": "c2",
                        "text": "then"
                      },
                      {
                        "id": "c3",
                        "text": "or"
                      },
                      {
                        "id": "c4",
                        "text": "case"
                      },
                      {
                        "id": "c5",
                        "text": "when"
                      }
                    ],
                    "info": "else — иначе."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-e-2",
                  "text": "Условный оператор начинается с ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Условный оператор начинается с ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "if"
                      },
                      {
                        "id": "c2",
                        "text": "when"
                      },
                      {
                        "id": "c3",
                        "text": "for"
                      },
                      {
                        "id": "c4",
                        "text": "do"
                      },
                      {
                        "id": "c5",
                        "text": "switch"
                      }
                    ],
                    "info": "if — главный оператор."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-e-3",
                  "text": "В Kotlin if может быть ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "В Kotlin if может быть ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "выражением"
                      },
                      {
                        "id": "c2",
                        "text": "циклом"
                      },
                      {
                        "id": "c3",
                        "text": "функцией"
                      },
                      {
                        "id": "c4",
                        "text": "классом"
                      },
                      {
                        "id": "c5",
                        "text": "объектом"
                      }
                    ],
                    "info": "val x = if(c) a else b."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-e-4",
                  "text": "Цепочка else ___ позволяет много условий.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Цепочка else ___ позволяет много условий.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "if"
                      },
                      {
                        "id": "c2",
                        "text": "when"
                      },
                      {
                        "id": "c3",
                        "text": "do"
                      },
                      {
                        "id": "c4",
                        "text": "for"
                      },
                      {
                        "id": "c5",
                        "text": "case"
                      }
                    ],
                    "info": "else if — стандартный pattern."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-e-5",
                  "text": "Условие в if должно быть типа ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Условие в if должно быть типа ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Boolean"
                      },
                      {
                        "id": "c2",
                        "text": "Int"
                      },
                      {
                        "id": "c3",
                        "text": "String"
                      },
                      {
                        "id": "c4",
                        "text": "Any"
                      },
                      {
                        "id": "c5",
                        "text": "Nothing"
                      }
                    ],
                    "info": "Только Boolean."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-h-1",
                  "text": "Что вернёт val r = if(true) 1 else 2?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт val r = if(true) 1 else 2?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1"
                      },
                      {
                        "id": "b",
                        "text": "2"
                      },
                      {
                        "id": "c",
                        "text": "true"
                      },
                      {
                        "id": "d",
                        "text": "Unit"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "if-выражение возвращает 1."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-h-2",
                  "text": "Что вернёт if(false) 1 else if(true) 2 else 3?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт if(false) 1 else if(true) 2 else 3?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1"
                      },
                      {
                        "id": "b",
                        "text": "2"
                      },
                      {
                        "id": "c",
                        "text": "3"
                      },
                      {
                        "id": "d",
                        "text": "null"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Первая истинная ветвь — 2."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-h-3",
                  "text": "В Kotlin тип val r = if(c) 1 else \"x\" будет:",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "В Kotlin тип val r = if(c) 1 else \"x\" будет:",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "String"
                      },
                      {
                        "id": "c",
                        "text": "Any"
                      },
                      {
                        "id": "d",
                        "text": "Nothing"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Общий супертип Any."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-h-4",
                  "text": "Какой результат if(x>0)0 else if(x<0)1 else 2 при x=0?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Какой результат if(x>0)0 else if(x<0)1 else 2 при x=0?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "0"
                      },
                      {
                        "id": "b",
                        "text": "1"
                      },
                      {
                        "id": "c",
                        "text": "2"
                      },
                      {
                        "id": "d",
                        "text": "null"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "x=0 не >0 и не <0 → ветка else=2."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-sc-h-5",
                  "text": "Что выведет if(true){println(1);2}else{0} как expression?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что выведет if(true){println(1);2}else{0} как expression?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1, потом 2"
                      },
                      {
                        "id": "b",
                        "text": "2 (значение)"
                      },
                      {
                        "id": "c",
                        "text": "0"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Выводит 1, возвращает 2."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-h-1",
                  "text": "Какие утверждения верны про if в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие утверждения верны про if в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Это выражение"
                      },
                      {
                        "id": "b",
                        "text": "Возвращает значение последней строки блока"
                      },
                      {
                        "id": "c",
                        "text": "Имеет тип"
                      },
                      {
                        "id": "d",
                        "text": "Может заменить тернарный оператор"
                      },
                      {
                        "id": "e",
                        "text": "Должно иметь else для expression"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d",
                      "e"
                    ],
                    "info": "Все верны для Kotlin."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-h-2",
                  "text": "Какие проблемы плохого if?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие проблемы плохого if?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Глубокая вложенность"
                      },
                      {
                        "id": "b",
                        "text": "Дублирование кода в ветках"
                      },
                      {
                        "id": "c",
                        "text": "Неполное покрытие условий"
                      },
                      {
                        "id": "d",
                        "text": "Использование как expression"
                      },
                      {
                        "id": "e",
                        "text": "Слишком сложное условие"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "Использование как expression — норма."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-h-3",
                  "text": "Какие альтернативы длинной if-else цепи?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие альтернативы длинной if-else цепи?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "when"
                      },
                      {
                        "id": "b",
                        "text": "switch (Java)"
                      },
                      {
                        "id": "c",
                        "text": "Map<Key,Action>"
                      },
                      {
                        "id": "d",
                        "text": "Polymorphism"
                      },
                      {
                        "id": "e",
                        "text": "goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto в Kotlin нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-h-4",
                  "text": "Какие способы упрощения if-else, возвращающего одно из двух значений?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие способы упрощения if-else, возвращающего одно из двух значений?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Тернарный оператор (Java)"
                      },
                      {
                        "id": "b",
                        "text": "val x = if(c) a else b (Kotlin)"
                      },
                      {
                        "id": "c",
                        "text": "Boolean.let"
                      },
                      {
                        "id": "d",
                        "text": "takeIf"
                      },
                      {
                        "id": "e",
                        "text": "when(c){true->a; false->b}"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d",
                      "e"
                    ],
                    "info": "Boolean.let — не идиоматично."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-mc-h-5",
                  "text": "Какие выражения корректны как expression?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие выражения корректны как expression?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "if(c) 1 else 2"
                      },
                      {
                        "id": "b",
                        "text": "if(c) 1"
                      },
                      {
                        "id": "c",
                        "text": "if(c){1} (без else)"
                      },
                      {
                        "id": "d",
                        "text": "if(c){println();1}else{2}"
                      },
                      {
                        "id": "e",
                        "text": "val x = if(c) 1 else 2"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "d",
                      "e"
                    ],
                    "info": "Без else — Unit."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-h-1",
                  "text": "Расположите if-else по охвату условий (от частного к общему).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите if-else по охвату условий (от частного к общему).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "if(x==5)"
                      },
                      {
                        "id": "i2",
                        "text": "if(x>0)"
                      },
                      {
                        "id": "i3",
                        "text": "if(x>=0)"
                      },
                      {
                        "id": "i4",
                        "text": "else"
                      }
                    ],
                    "info": "От строгого к универсальному."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-h-2",
                  "text": "Расположите шаги else-if(x>0){A}else if(x<0){B}else{C} при x=0.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги else-if(x>0){A}else if(x<0){B}else{C} при x=0.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Проверить x>0 (false)"
                      },
                      {
                        "id": "i2",
                        "text": "Проверить x<0 (false)"
                      },
                      {
                        "id": "i3",
                        "text": "Выполнить else-ветку"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть C"
                      }
                    ],
                    "info": "Каскадная проверка."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-h-3",
                  "text": "Расположите шаги преобразования if-цепи в when.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги преобразования if-цепи в when.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Идентификация условий"
                      },
                      {
                        "id": "i2",
                        "text": "Извлечение subject"
                      },
                      {
                        "id": "i3",
                        "text": "Замена if на when(subject)"
                      },
                      {
                        "id": "i4",
                        "text": "Замена case на ->"
                      }
                    ],
                    "info": "Refactoring к when."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-h-4",
                  "text": "Расположите if-блоки по приоритету выполнения.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите if-блоки по приоритету выполнения.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Самое специфичное (==5)"
                      },
                      {
                        "id": "i2",
                        "text": "Общее (>0)"
                      },
                      {
                        "id": "i3",
                        "text": "Универсальное (else if x≥0)"
                      },
                      {
                        "id": "i4",
                        "text": "Default else"
                      }
                    ],
                    "info": "От узкого к широкому."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-ord-h-5",
                  "text": "Расположите шаги отладки неверного if.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги отладки неверного if.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Reproduce баг"
                      },
                      {
                        "id": "i2",
                        "text": "Поставить breakpoint в if"
                      },
                      {
                        "id": "i3",
                        "text": "Inspect значение условия"
                      },
                      {
                        "id": "i4",
                        "text": "Исправить или добавить ветку"
                      }
                    ],
                    "info": "Стандарт."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-h-1",
                  "text": "В Kotlin if без ___ не может быть expression.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "В Kotlin if без ___ не может быть expression.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "else"
                      },
                      {
                        "id": "c2",
                        "text": "then"
                      },
                      {
                        "id": "c3",
                        "text": "when"
                      },
                      {
                        "id": "c4",
                        "text": "try"
                      },
                      {
                        "id": "c5",
                        "text": "do"
                      }
                    ],
                    "info": "Без else тип Unit."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-h-2",
                  "text": "Тернарный оператор a ? b : c заменяется в Kotlin на ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Тернарный оператор a ? b : c заменяется в Kotlin на ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "if(a) b else c"
                      },
                      {
                        "id": "c2",
                        "text": "when(a){->b}"
                      },
                      {
                        "id": "c3",
                        "text": "a.let{b}"
                      },
                      {
                        "id": "c4",
                        "text": "try{a}catch{c}"
                      },
                      {
                        "id": "c5",
                        "text": "a&&b||c"
                      }
                    ],
                    "info": "Kotlin не имеет ?:."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-h-3",
                  "text": "Тип val x = if(c) 1 else \"x\" — это ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Тип val x = if(c) 1 else \"x\" — это ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "Any"
                      },
                      {
                        "id": "c2",
                        "text": "Int"
                      },
                      {
                        "id": "c3",
                        "text": "String"
                      },
                      {
                        "id": "c4",
                        "text": "Nothing"
                      },
                      {
                        "id": "c5",
                        "text": "Unit"
                      }
                    ],
                    "info": "Общий супертип."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-h-4",
                  "text": "Глубокая вложенность if называется ___ кодом.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Глубокая вложенность if называется ___ кодом.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "arrow"
                      },
                      {
                        "id": "c2",
                        "text": "spaghetti"
                      },
                      {
                        "id": "c3",
                        "text": "clean"
                      },
                      {
                        "id": "c4",
                        "text": "flat"
                      },
                      {
                        "id": "c5",
                        "text": "modular"
                      }
                    ],
                    "info": "Arrow code — много вложенных уровней."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-1-fb-h-5",
                  "text": "Альтернатива длинной if-цепи — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Альтернатива длинной if-цепи — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "when"
                      },
                      {
                        "id": "c2",
                        "text": "for"
                      },
                      {
                        "id": "c3",
                        "text": "while"
                      },
                      {
                        "id": "c4",
                        "text": "do"
                      },
                      {
                        "id": "c5",
                        "text": "try"
                      }
                    ],
                    "info": "when — в Kotlin аналог switch."
                  }
                }
              ]
            },
            {
              "id": "lb-courses-programming-2-1-2",
              "title": "switch / when",
              "questions": [
                {
                  "id": "qsb-courses-programming-2-1-2-sc-e-1",
                  "text": "В Kotlin аналог switch — это:",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "В Kotlin аналог switch — это:",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "if"
                      },
                      {
                        "id": "b",
                        "text": "when"
                      },
                      {
                        "id": "c",
                        "text": "select"
                      },
                      {
                        "id": "d",
                        "text": "case"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "when — Kotlin switch."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-e-2",
                  "text": "Какое ключевое слово в Java означает \"иначе\" в switch?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какое ключевое слово в Java означает \"иначе\" в switch?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "default"
                      },
                      {
                        "id": "b",
                        "text": "else"
                      },
                      {
                        "id": "c",
                        "text": "otherwise"
                      },
                      {
                        "id": "d",
                        "text": "fallback"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "default — ветка по умолчанию."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-e-3",
                  "text": "Что отделяет case от его кода в Java switch?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что отделяет case от его кода в Java switch?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": ":"
                      },
                      {
                        "id": "b",
                        "text": "->"
                      },
                      {
                        "id": "c",
                        "text": "="
                      },
                      {
                        "id": "d",
                        "text": "=>"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "case x: code..."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-e-4",
                  "text": "Что в Kotlin when отделяет условие от блока?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что в Kotlin when отделяет условие от блока?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "->"
                      },
                      {
                        "id": "b",
                        "text": ":"
                      },
                      {
                        "id": "c",
                        "text": "="
                      },
                      {
                        "id": "d",
                        "text": "=>"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "when(x){0->...}"
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-e-5",
                  "text": "Что использует Java switch для перехода к следующему case?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что использует Java switch для перехода к следующему case?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "fall-through"
                      },
                      {
                        "id": "b",
                        "text": "jump"
                      },
                      {
                        "id": "c",
                        "text": "return"
                      },
                      {
                        "id": "d",
                        "text": "continue"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Без break Java падает в следующий case."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-e-1",
                  "text": "Какие выражения корректны для when в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения корректны для when в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "when(x){1->...}"
                      },
                      {
                        "id": "b",
                        "text": "when{x==1->...}"
                      },
                      {
                        "id": "c",
                        "text": "when(x){1,2->...}"
                      },
                      {
                        "id": "d",
                        "text": "when(x){is Int->...}"
                      },
                      {
                        "id": "e",
                        "text": "when(x):case 1:..."
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "Все варианты Kotlin."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-e-2",
                  "text": "Какие конструкции — switch-аналог?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие конструкции — switch-аналог?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Java switch"
                      },
                      {
                        "id": "b",
                        "text": "Kotlin when"
                      },
                      {
                        "id": "c",
                        "text": "Python match (3.10+)"
                      },
                      {
                        "id": "d",
                        "text": "JS switch"
                      },
                      {
                        "id": "e",
                        "text": "C++ goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto не switch."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-e-3",
                  "text": "Какие типы можно использовать в Kotlin when (subject)?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие типы можно использовать в Kotlin when (subject)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "String"
                      },
                      {
                        "id": "c",
                        "text": "enum"
                      },
                      {
                        "id": "d",
                        "text": "sealed class"
                      },
                      {
                        "id": "e",
                        "text": "List"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "List тоже можно как объект, но обычно match по типу."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-e-4",
                  "text": "Какие проблемы Java switch без break?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие проблемы Java switch без break?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Fall-through к следующему case"
                      },
                      {
                        "id": "b",
                        "text": "Сложный отладочный путь"
                      },
                      {
                        "id": "c",
                        "text": "Неожиданное поведение"
                      },
                      {
                        "id": "d",
                        "text": "Скомпилируется без ошибки"
                      },
                      {
                        "id": "e",
                        "text": "Throw NullPointerException"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "NPE не от отсутствия break."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-e-5",
                  "text": "Какие ветки in/!in/is доступны в Kotlin when?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие ветки in/!in/is доступны в Kotlin when?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "in 1..10"
                      },
                      {
                        "id": "b",
                        "text": "!in 1..10"
                      },
                      {
                        "id": "c",
                        "text": "is String"
                      },
                      {
                        "id": "d",
                        "text": "!is String"
                      },
                      {
                        "id": "e",
                        "text": "as Int"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "as — каст, не condition."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-e-1",
                  "text": "Расположите шаги Kotlin when(x).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги Kotlin when(x).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить x"
                      },
                      {
                        "id": "i2",
                        "text": "Проверить ветки сверху вниз"
                      },
                      {
                        "id": "i3",
                        "text": "Найти первый match"
                      },
                      {
                        "id": "i4",
                        "text": "Выполнить блок"
                      }
                    ],
                    "info": "Поток when."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-e-2",
                  "text": "Расположите ветки when от специфичной к общей.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите ветки when от специфичной к общей.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "x == 0"
                      },
                      {
                        "id": "i2",
                        "text": "x in 1..10"
                      },
                      {
                        "id": "i3",
                        "text": "x is Int"
                      },
                      {
                        "id": "i4",
                        "text": "else"
                      }
                    ],
                    "info": "От узкой к широкой."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-e-3",
                  "text": "Расположите шаги Java switch без default.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги Java switch без default.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить выражение"
                      },
                      {
                        "id": "i2",
                        "text": "Сравнить с case"
                      },
                      {
                        "id": "i3",
                        "text": "Если match → выполнить"
                      },
                      {
                        "id": "i4",
                        "text": "Если нет — пропустить switch"
                      }
                    ],
                    "info": "Без default — пропуск."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-e-4",
                  "text": "Расположите шаги преобразования if-else в when.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги преобразования if-else в when.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Найти общий subject"
                      },
                      {
                        "id": "i2",
                        "text": "Создать when(subject)"
                      },
                      {
                        "id": "i3",
                        "text": "Перенести условия в ->"
                      },
                      {
                        "id": "i4",
                        "text": "Добавить else"
                      }
                    ],
                    "info": "Refactoring."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-e-5",
                  "text": "Расположите case по приоритету (Java).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите case по приоритету (Java).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "case 1"
                      },
                      {
                        "id": "i2",
                        "text": "case 2"
                      },
                      {
                        "id": "i3",
                        "text": "case 3"
                      },
                      {
                        "id": "i4",
                        "text": "default"
                      }
                    ],
                    "info": "default обычно последний."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-e-1",
                  "text": "В Kotlin аналог switch — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "В Kotlin аналог switch — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "when"
                      },
                      {
                        "id": "c2",
                        "text": "if"
                      },
                      {
                        "id": "c3",
                        "text": "select"
                      },
                      {
                        "id": "c4",
                        "text": "case"
                      },
                      {
                        "id": "c5",
                        "text": "match"
                      }
                    ],
                    "info": "when — pattern matching."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-e-2",
                  "text": "Java case без break вызывает ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Java case без break вызывает ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "fall-through"
                      },
                      {
                        "id": "c2",
                        "text": "compile error"
                      },
                      {
                        "id": "c3",
                        "text": "NullPointerException"
                      },
                      {
                        "id": "c4",
                        "text": "return"
                      },
                      {
                        "id": "c5",
                        "text": "continue"
                      }
                    ],
                    "info": "Падение в следующий case."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-e-3",
                  "text": "Default ветка в Java switch — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Default ветка в Java switch — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "default"
                      },
                      {
                        "id": "c2",
                        "text": "else"
                      },
                      {
                        "id": "c3",
                        "text": "otherwise"
                      },
                      {
                        "id": "c4",
                        "text": "fallback"
                      },
                      {
                        "id": "c5",
                        "text": "last"
                      }
                    ],
                    "info": "default — иначе."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-e-4",
                  "text": "Else ветка в Kotlin when — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Else ветка в Kotlin when — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "else"
                      },
                      {
                        "id": "c2",
                        "text": "default"
                      },
                      {
                        "id": "c3",
                        "text": "otherwise"
                      },
                      {
                        "id": "c4",
                        "text": "fallback"
                      },
                      {
                        "id": "c5",
                        "text": "end"
                      }
                    ],
                    "info": "Kotlin использует else."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-e-5",
                  "text": "Стрелка в Kotlin when — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Стрелка в Kotlin when — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "->"
                      },
                      {
                        "id": "c2",
                        "text": ":"
                      },
                      {
                        "id": "c3",
                        "text": "="
                      },
                      {
                        "id": "c4",
                        "text": "=>"
                      },
                      {
                        "id": "c5",
                        "text": "|"
                      }
                    ],
                    "info": "Синтаксис Kotlin."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-h-1",
                  "text": "Что вернёт when(x){0->\"zero\"; in 1..5->\"small\"; else->\"other\"} при x=3?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт when(x){0->\"zero\"; in 1..5->\"small\"; else->\"other\"} при x=3?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"zero\""
                      },
                      {
                        "id": "b",
                        "text": "\"small\""
                      },
                      {
                        "id": "c",
                        "text": "\"other\""
                      },
                      {
                        "id": "d",
                        "text": "null"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "in 1..5 включает 3."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-h-2",
                  "text": "Что вернёт when(x){is Int->\"i\"; is String->\"s\"; else->\"o\"} при x=1.0?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт when(x){is Int->\"i\"; is String->\"s\"; else->\"o\"} при x=1.0?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"i\""
                      },
                      {
                        "id": "b",
                        "text": "\"s\""
                      },
                      {
                        "id": "c",
                        "text": "\"o\""
                      },
                      {
                        "id": "d",
                        "text": "null"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "1.0 — Double, не Int/String."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-h-3",
                  "text": "Что вернёт when {x>0->\"pos\"; x<0->\"neg\"; else->\"zero\"} при x=0?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт when {x>0->\"pos\"; x<0->\"neg\"; else->\"zero\"} при x=0?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "\"pos\""
                      },
                      {
                        "id": "b",
                        "text": "\"neg\""
                      },
                      {
                        "id": "c",
                        "text": "\"zero\""
                      },
                      {
                        "id": "d",
                        "text": "null"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "when без аргумента проверяет boolean условия."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-h-4",
                  "text": "Можно ли в Kotlin when возвращать значение?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Можно ли в Kotlin when возвращать значение?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Да, всегда"
                      },
                      {
                        "id": "b",
                        "text": "Только при exhaustive"
                      },
                      {
                        "id": "c",
                        "text": "Нет"
                      },
                      {
                        "id": "d",
                        "text": "Только в companion"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Exhaustive when — expression."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-sc-h-5",
                  "text": "Что произойдёт в Java switch если case 1: code; case 2: код, без break?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что произойдёт в Java switch если case 1: code; case 2: код, без break?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Только case 1"
                      },
                      {
                        "id": "b",
                        "text": "Только case 2"
                      },
                      {
                        "id": "c",
                        "text": "Оба"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка компиляции"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Fall-through выполняет оба."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-h-1",
                  "text": "Какие особенности Kotlin when vs Java switch?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие особенности Kotlin when vs Java switch?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Нет fall-through по умолчанию"
                      },
                      {
                        "id": "b",
                        "text": "Может быть expression"
                      },
                      {
                        "id": "c",
                        "text": "Поддержка is, in"
                      },
                      {
                        "id": "d",
                        "text": "Поддержка нескольких значений в ветке (a,b->...)"
                      },
                      {
                        "id": "e",
                        "text": "Поддержка goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-h-2",
                  "text": "Какие типы корректны как subject when?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие типы корректны как subject when?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Int"
                      },
                      {
                        "id": "b",
                        "text": "enum"
                      },
                      {
                        "id": "c",
                        "text": "sealed class"
                      },
                      {
                        "id": "d",
                        "text": "String"
                      },
                      {
                        "id": "e",
                        "text": "Function"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "Function редко."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-h-3",
                  "text": "Какие ветки when корректны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие ветки when корректны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1->"
                      },
                      {
                        "id": "b",
                        "text": "in 1..10->"
                      },
                      {
                        "id": "c",
                        "text": "is String->"
                      },
                      {
                        "id": "d",
                        "text": "!in list->"
                      },
                      {
                        "id": "e",
                        "text": "else->"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d",
                      "e"
                    ],
                    "info": "Все валидны."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-h-4",
                  "text": "Какие проблемы Java switch?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие проблемы Java switch?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Fall-through"
                      },
                      {
                        "id": "b",
                        "text": "Не поддерживает Object до Java 7"
                      },
                      {
                        "id": "c",
                        "text": "Не expression до Java 14"
                      },
                      {
                        "id": "d",
                        "text": "Невозможно в when пакетах"
                      },
                      {
                        "id": "e",
                        "text": "Только Int/enum/String"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "Java 14+ имеет switch expression."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-mc-h-5",
                  "text": "Какие способы заменить when?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие способы заменить when?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "if-else"
                      },
                      {
                        "id": "b",
                        "text": "Map<Key,Action>"
                      },
                      {
                        "id": "c",
                        "text": "Polymorphism"
                      },
                      {
                        "id": "d",
                        "text": "Strategy pattern"
                      },
                      {
                        "id": "e",
                        "text": "goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto не доступно."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-h-1",
                  "text": "Расположите ветки Kotlin when по специфичности.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите ветки Kotlin when по специфичности.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "x == 5"
                      },
                      {
                        "id": "i2",
                        "text": "x in 1..10"
                      },
                      {
                        "id": "i3",
                        "text": "x is Int"
                      },
                      {
                        "id": "i4",
                        "text": "else"
                      }
                    ],
                    "info": "От узкой к широкой."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-h-2",
                  "text": "Расположите шаги exhaustive when для sealed.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги exhaustive when для sealed.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Объявить sealed class"
                      },
                      {
                        "id": "i2",
                        "text": "Перечислить все варианты"
                      },
                      {
                        "id": "i3",
                        "text": "when(obj){всех вариантов}"
                      },
                      {
                        "id": "i4",
                        "text": "Компилятор проверяет полноту"
                      }
                    ],
                    "info": "Exhaustive check."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-h-3",
                  "text": "Расположите эволюцию switch в Java.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите эволюцию switch в Java.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Java 1: int/enum"
                      },
                      {
                        "id": "i2",
                        "text": "Java 7: String"
                      },
                      {
                        "id": "i3",
                        "text": "Java 14: switch expression"
                      },
                      {
                        "id": "i4",
                        "text": "Java 17: pattern matching preview"
                      }
                    ],
                    "info": "История Java."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-h-4",
                  "text": "Расположите шаги Java switch с break.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги Java switch с break.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить x"
                      },
                      {
                        "id": "i2",
                        "text": "Сравнить с case"
                      },
                      {
                        "id": "i3",
                        "text": "Выполнить блок"
                      },
                      {
                        "id": "i4",
                        "text": "break — выход"
                      }
                    ],
                    "info": "Стандартный поток."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-ord-h-5",
                  "text": "Расположите шаги when expression.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги when expression.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Вычислить subject"
                      },
                      {
                        "id": "i2",
                        "text": "Найти первый match"
                      },
                      {
                        "id": "i3",
                        "text": "Вычислить ветку"
                      },
                      {
                        "id": "i4",
                        "text": "Вернуть значение"
                      }
                    ],
                    "info": "When как expression."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-h-1",
                  "text": "Exhaustive when для sealed class гарантирует ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Exhaustive when для sealed class гарантирует ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "полноту"
                      },
                      {
                        "id": "c2",
                        "text": "скорость"
                      },
                      {
                        "id": "c3",
                        "text": "безопасность типов"
                      },
                      {
                        "id": "c4",
                        "text": "короткое замыкание"
                      },
                      {
                        "id": "c5",
                        "text": "fall-through"
                      }
                    ],
                    "info": "Компилятор проверяет покрытие."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-h-2",
                  "text": "Java case без break вызывает ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Java case без break вызывает ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "fall-through"
                      },
                      {
                        "id": "c2",
                        "text": "compile error"
                      },
                      {
                        "id": "c3",
                        "text": "exception"
                      },
                      {
                        "id": "c4",
                        "text": "return"
                      },
                      {
                        "id": "c5",
                        "text": "continue"
                      }
                    ],
                    "info": "Каскад выполнения."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-h-3",
                  "text": "Kotlin when без аргумента — это набор ___ условий.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Kotlin when без аргумента — это набор ___ условий.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "boolean"
                      },
                      {
                        "id": "c2",
                        "text": "integer"
                      },
                      {
                        "id": "c3",
                        "text": "string"
                      },
                      {
                        "id": "c4",
                        "text": "enum"
                      },
                      {
                        "id": "c5",
                        "text": "class"
                      }
                    ],
                    "info": "Каждая ветка — boolean."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-h-4",
                  "text": "Java 14 ввёл switch ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Java 14 ввёл switch ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "expression"
                      },
                      {
                        "id": "c2",
                        "text": "statement"
                      },
                      {
                        "id": "c3",
                        "text": "pattern"
                      },
                      {
                        "id": "c4",
                        "text": "guard"
                      },
                      {
                        "id": "c5",
                        "text": "lambda"
                      }
                    ],
                    "info": "switch как выражение."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-1-2-fb-h-5",
                  "text": "Стрелка -> в Kotlin when отделяет ___ от блока.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Стрелка -> в Kotlin when отделяет ___ от блока.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "условие"
                      },
                      {
                        "id": "c2",
                        "text": "тип"
                      },
                      {
                        "id": "c3",
                        "text": "имя"
                      },
                      {
                        "id": "c4",
                        "text": "значение"
                      },
                      {
                        "id": "c5",
                        "text": "аргумент"
                      }
                    ],
                    "info": "Условие -> блок."
                  }
                }
              ]
            }
          ]
        },
        {
          "id": "tb-courses-programming-2-2",
          "title": "Циклы",
          "lessons": [
            {
              "id": "lb-courses-programming-2-2-1",
              "title": "for и while",
              "questions": [
                {
                  "id": "qsb-courses-programming-2-2-1-sc-e-1",
                  "text": "Какой цикл проходит коллекцию в Kotlin?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой цикл проходит коллекцию в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "for"
                      },
                      {
                        "id": "b",
                        "text": "while"
                      },
                      {
                        "id": "c",
                        "text": "do-while"
                      },
                      {
                        "id": "d",
                        "text": "loop"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "for(x in list)."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-e-2",
                  "text": "Какой цикл выполняется ПОКА условие истинно?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой цикл выполняется ПОКА условие истинно?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "for"
                      },
                      {
                        "id": "b",
                        "text": "while"
                      },
                      {
                        "id": "c",
                        "text": "switch"
                      },
                      {
                        "id": "d",
                        "text": "if"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "while — pre-condition loop."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-e-3",
                  "text": "Какой цикл всегда выполняет тело хотя бы раз?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой цикл всегда выполняет тело хотя бы раз?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "while"
                      },
                      {
                        "id": "b",
                        "text": "do-while"
                      },
                      {
                        "id": "c",
                        "text": "for"
                      },
                      {
                        "id": "d",
                        "text": "foreach"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "do-while проверяет после."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-e-4",
                  "text": "В Kotlin for(i in 1..5) выполнится:",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "В Kotlin for(i in 1..5) выполнится:",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "4 раза"
                      },
                      {
                        "id": "b",
                        "text": "5 раз"
                      },
                      {
                        "id": "c",
                        "text": "6 раз"
                      },
                      {
                        "id": "d",
                        "text": "0 раз"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Диапазон включает обе границы."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-e-5",
                  "text": "В Kotlin for(i in 1 until 5) выполнится:",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "В Kotlin for(i in 1 until 5) выполнится:",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "4 раза"
                      },
                      {
                        "id": "b",
                        "text": "5 раз"
                      },
                      {
                        "id": "c",
                        "text": "6 раз"
                      },
                      {
                        "id": "d",
                        "text": "0 раз"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "until исключает верхнюю границу."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-e-1",
                  "text": "Какие циклы есть в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие циклы есть в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "for"
                      },
                      {
                        "id": "b",
                        "text": "while"
                      },
                      {
                        "id": "c",
                        "text": "do-while"
                      },
                      {
                        "id": "d",
                        "text": "foreach (метод)"
                      },
                      {
                        "id": "e",
                        "text": "goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-e-2",
                  "text": "Какие способы итерации по списку?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие способы итерации по списку?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "for(x in list)"
                      },
                      {
                        "id": "b",
                        "text": "list.forEach{}"
                      },
                      {
                        "id": "c",
                        "text": "while(it.hasNext())"
                      },
                      {
                        "id": "d",
                        "text": "list.indices"
                      },
                      {
                        "id": "e",
                        "text": "list.goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-e-3",
                  "text": "Какие выражения создают range?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения создают range?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1..10"
                      },
                      {
                        "id": "b",
                        "text": "1 until 10"
                      },
                      {
                        "id": "c",
                        "text": "10 downTo 1"
                      },
                      {
                        "id": "d",
                        "text": "1..10 step 2"
                      },
                      {
                        "id": "e",
                        "text": "1+10"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "+ — арифметика."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-e-4",
                  "text": "Какие типы можно итерировать в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие типы можно итерировать в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "List"
                      },
                      {
                        "id": "b",
                        "text": "Array"
                      },
                      {
                        "id": "c",
                        "text": "String"
                      },
                      {
                        "id": "d",
                        "text": "Map"
                      },
                      {
                        "id": "e",
                        "text": "Int (через range)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d",
                      "e"
                    ],
                    "info": "Все Iterable + range."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-e-5",
                  "text": "Какие конструкции корректны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие конструкции корректны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "while(true){...}"
                      },
                      {
                        "id": "b",
                        "text": "do{...}while(cond)"
                      },
                      {
                        "id": "c",
                        "text": "for(i in 0..9){...}"
                      },
                      {
                        "id": "d",
                        "text": "for(;;){...} (Kotlin)"
                      },
                      {
                        "id": "e",
                        "text": "for(int i=0;i<10;i++){...} (Java)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "C-стиль for в Kotlin нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-e-1",
                  "text": "Расположите шаги while(c){body}.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги while(c){body}.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Проверить c"
                      },
                      {
                        "id": "i2",
                        "text": "Если true → body"
                      },
                      {
                        "id": "i3",
                        "text": "Перейти к проверке c"
                      },
                      {
                        "id": "i4",
                        "text": "Если false → выйти"
                      }
                    ],
                    "info": "Pre-condition loop."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-e-2",
                  "text": "Расположите шаги do{body}while(c).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги do{body}while(c).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Выполнить body"
                      },
                      {
                        "id": "i2",
                        "text": "Проверить c"
                      },
                      {
                        "id": "i3",
                        "text": "Если true → body снова"
                      },
                      {
                        "id": "i4",
                        "text": "Если false → выйти"
                      }
                    ],
                    "info": "Post-condition loop."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-e-3",
                  "text": "Расположите числа цикла for(i in 1..4).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите числа цикла for(i in 1..4).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "1"
                      },
                      {
                        "id": "i2",
                        "text": "2"
                      },
                      {
                        "id": "i3",
                        "text": "3"
                      },
                      {
                        "id": "i4",
                        "text": "4"
                      }
                    ],
                    "info": "Включает обе границы."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-e-4",
                  "text": "Расположите шаги for(x in list).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги for(x in list).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Получить итератор"
                      },
                      {
                        "id": "i2",
                        "text": "hasNext?"
                      },
                      {
                        "id": "i3",
                        "text": "next() → x"
                      },
                      {
                        "id": "i4",
                        "text": "Выполнить блок"
                      }
                    ],
                    "info": "Iterator pattern."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-e-5",
                  "text": "Расположите типы циклов по гарантии выполнения тела.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите типы циклов по гарантии выполнения тела.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "for(x in emptyList)"
                      },
                      {
                        "id": "i2",
                        "text": "while(false)"
                      },
                      {
                        "id": "i3",
                        "text": "do{}while(false) (1 раз)"
                      },
                      {
                        "id": "i4",
                        "text": "while(true) (бесконечно)"
                      }
                    ],
                    "info": "От 0 раз к бесконечности."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-e-1",
                  "text": "Цикл с pre-condition — это ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Цикл с pre-condition — это ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "while"
                      },
                      {
                        "id": "c2",
                        "text": "do-while"
                      },
                      {
                        "id": "c3",
                        "text": "for"
                      },
                      {
                        "id": "c4",
                        "text": "switch"
                      },
                      {
                        "id": "c5",
                        "text": "if"
                      }
                    ],
                    "info": "while проверяет до."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-e-2",
                  "text": "Цикл с post-condition — это ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Цикл с post-condition — это ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "do-while"
                      },
                      {
                        "id": "c2",
                        "text": "while"
                      },
                      {
                        "id": "c3",
                        "text": "for"
                      },
                      {
                        "id": "c4",
                        "text": "switch"
                      },
                      {
                        "id": "c5",
                        "text": "if"
                      }
                    ],
                    "info": "do-while проверяет после."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-e-3",
                  "text": "Range от 1 до 5 включительно: 1 ___ 5.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Range от 1 до 5 включительно: 1 ___ 5.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": ".."
                      },
                      {
                        "id": "c2",
                        "text": "until"
                      },
                      {
                        "id": "c3",
                        "text": "downTo"
                      },
                      {
                        "id": "c4",
                        "text": "step"
                      },
                      {
                        "id": "c5",
                        "text": "in"
                      }
                    ],
                    "info": ".. — closed range."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-e-4",
                  "text": "Range от 1 до 5 исключая 5: 1 ___ 5.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Range от 1 до 5 исключая 5: 1 ___ 5.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "until"
                      },
                      {
                        "id": "c2",
                        "text": ".."
                      },
                      {
                        "id": "c3",
                        "text": "downTo"
                      },
                      {
                        "id": "c4",
                        "text": "step"
                      },
                      {
                        "id": "c5",
                        "text": "in"
                      }
                    ],
                    "info": "until — half-open."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-e-5",
                  "text": "Цикл по коллекции: for(x ___ list).",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Цикл по коллекции: for(x ___ list).",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "in"
                      },
                      {
                        "id": "c2",
                        "text": "of"
                      },
                      {
                        "id": "c3",
                        "text": "from"
                      },
                      {
                        "id": "c4",
                        "text": "to"
                      },
                      {
                        "id": "c5",
                        "text": "as"
                      }
                    ],
                    "info": "Kotlin использует in."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-h-1",
                  "text": "Сколько раз выполнится цикл for(i in 10 downTo 1 step 3)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Сколько раз выполнится цикл for(i in 10 downTo 1 step 3)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "3"
                      },
                      {
                        "id": "b",
                        "text": "4"
                      },
                      {
                        "id": "c",
                        "text": "10"
                      },
                      {
                        "id": "d",
                        "text": "0"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "i = 10, 7, 4, 1 → 4 итерации."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-h-2",
                  "text": "Что произойдёт при while(true) без break?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что произойдёт при while(true) без break?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Бесконечный цикл"
                      },
                      {
                        "id": "b",
                        "text": "Завершится через 1 минуту"
                      },
                      {
                        "id": "c",
                        "text": "Compile error"
                      },
                      {
                        "id": "d",
                        "text": "Runtime error"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Infinite loop без выхода."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-h-3",
                  "text": "Что вернёт val sum = (1..5).sum()?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что вернёт val sum = (1..5).sum()?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "10"
                      },
                      {
                        "id": "b",
                        "text": "15"
                      },
                      {
                        "id": "c",
                        "text": "20"
                      },
                      {
                        "id": "d",
                        "text": "5"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "1+2+3+4+5=15."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-h-4",
                  "text": "Можно ли в Kotlin изменять loop variable for?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Можно ли в Kotlin изменять loop variable for?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Да"
                      },
                      {
                        "id": "b",
                        "text": "Нет, val"
                      },
                      {
                        "id": "c",
                        "text": "Только в while"
                      },
                      {
                        "id": "d",
                        "text": "Только в Java"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Loop var в for — val."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-sc-h-5",
                  "text": "Что напечатает for(i in 5 until 5) println(i)?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает for(i in 5 until 5) println(i)?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Ничего"
                      },
                      {
                        "id": "b",
                        "text": "5"
                      },
                      {
                        "id": "c",
                        "text": "Ошибка"
                      },
                      {
                        "id": "d",
                        "text": "null"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "Пустой range."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-h-1",
                  "text": "Какие способы перебора с индексом в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие способы перебора с индексом в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "list.forEachIndexed{i,v->}"
                      },
                      {
                        "id": "b",
                        "text": "for((i,v) in list.withIndex())"
                      },
                      {
                        "id": "c",
                        "text": "for(i in list.indices)"
                      },
                      {
                        "id": "d",
                        "text": "for(i in 0 until list.size)"
                      },
                      {
                        "id": "e",
                        "text": "list.map{it.index}"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "map.it.index — нет такого свойства."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-h-2",
                  "text": "Какие операции изменяют поведение цикла?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие операции изменяют поведение цикла?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break"
                      },
                      {
                        "id": "b",
                        "text": "continue"
                      },
                      {
                        "id": "c",
                        "text": "return"
                      },
                      {
                        "id": "d",
                        "text": "goto"
                      },
                      {
                        "id": "e",
                        "text": "throw"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "goto не доступно."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-h-3",
                  "text": "Какие правила for-each в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие правила for-each в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Не модифицируй коллекцию во время итерации"
                      },
                      {
                        "id": "b",
                        "text": "Используй iterator.remove()"
                      },
                      {
                        "id": "c",
                        "text": "Используй copy для безопасности"
                      },
                      {
                        "id": "d",
                        "text": "Используй mutator вне цикла"
                      },
                      {
                        "id": "e",
                        "text": "Можно добавлять в любой момент"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "Concurrent modification."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-h-4",
                  "text": "Какие способы создания infinite loop?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие способы создания infinite loop?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "while(true)"
                      },
                      {
                        "id": "b",
                        "text": "for(;;)"
                      },
                      {
                        "id": "c",
                        "text": "do{}while(true)"
                      },
                      {
                        "id": "d",
                        "text": "repeat(Int.MAX_VALUE)"
                      },
                      {
                        "id": "e",
                        "text": "while(1==1)"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "c",
                      "d",
                      "e"
                    ],
                    "info": "for(;;) — C-стиль, в Kotlin нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-mc-h-5",
                  "text": "Какие альтернативы for-цикла в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие альтернативы for-цикла в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "repeat(n){}"
                      },
                      {
                        "id": "b",
                        "text": "forEach{}"
                      },
                      {
                        "id": "c",
                        "text": "map{}"
                      },
                      {
                        "id": "d",
                        "text": "filter{}"
                      },
                      {
                        "id": "e",
                        "text": "fold{}"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d",
                      "e"
                    ],
                    "info": "Все functional способы."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-h-1",
                  "text": "Расположите циклы по эффективности (для прохода по array).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите циклы по эффективности (для прохода по array).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "for(i in array.indices) array[i]"
                      },
                      {
                        "id": "i2",
                        "text": "for(x in array)"
                      },
                      {
                        "id": "i3",
                        "text": "array.forEach{}"
                      },
                      {
                        "id": "i4",
                        "text": "array.iterator()"
                      }
                    ],
                    "info": "От низкоуровневого к высокоуровневому."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-h-2",
                  "text": "Расположите шаги while(i<n){...; i++}.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги while(i<n){...; i++}.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Проверить i<n"
                      },
                      {
                        "id": "i2",
                        "text": "Выполнить тело"
                      },
                      {
                        "id": "i3",
                        "text": "Инкремент i++"
                      },
                      {
                        "id": "i4",
                        "text": "Возврат к проверке"
                      }
                    ],
                    "info": "Стандартный цикл."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-h-3",
                  "text": "Расположите варианты по гарантии итераций.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите варианты по гарантии итераций.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "for(x in emptyList) (0)"
                      },
                      {
                        "id": "i2",
                        "text": "do{}while(false) (1)"
                      },
                      {
                        "id": "i3",
                        "text": "for(i in 1..5) (5)"
                      },
                      {
                        "id": "i4",
                        "text": "while(true) (∞)"
                      }
                    ],
                    "info": "От нуля к бесконечности."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-h-4",
                  "text": "Расположите шаги break.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги break.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Достичь break"
                      },
                      {
                        "id": "i2",
                        "text": "Выйти из текущего цикла"
                      },
                      {
                        "id": "i3",
                        "text": "Передать управление после цикла"
                      },
                      {
                        "id": "i4",
                        "text": "Продолжить выполнение"
                      }
                    ],
                    "info": "break exit."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-ord-h-5",
                  "text": "Расположите циклы по сложности O.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите циклы по сложности O.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Один цикл O(n)"
                      },
                      {
                        "id": "i2",
                        "text": "Два вложенных O(n²)"
                      },
                      {
                        "id": "i3",
                        "text": "Три вложенных O(n³)"
                      },
                      {
                        "id": "i4",
                        "text": "Рекурсивный O(2^n)"
                      }
                    ],
                    "info": "От линейного к экспоненциальному."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-h-1",
                  "text": "Шаг range в Kotlin задаётся ключевым словом ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Шаг range в Kotlin задаётся ключевым словом ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "step"
                      },
                      {
                        "id": "c2",
                        "text": "by"
                      },
                      {
                        "id": "c3",
                        "text": "each"
                      },
                      {
                        "id": "c4",
                        "text": "with"
                      },
                      {
                        "id": "c5",
                        "text": "span"
                      }
                    ],
                    "info": "1..10 step 2."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-h-2",
                  "text": "Обратный диапазон в Kotlin — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Обратный диапазон в Kotlin — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "downTo"
                      },
                      {
                        "id": "c2",
                        "text": "reverse"
                      },
                      {
                        "id": "c3",
                        "text": "until"
                      },
                      {
                        "id": "c4",
                        "text": "back"
                      },
                      {
                        "id": "c5",
                        "text": "from"
                      }
                    ],
                    "info": "10 downTo 1."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-h-3",
                  "text": "Цикл, гарантирующий хотя бы 1 итерацию — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Цикл, гарантирующий хотя бы 1 итерацию — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "do-while"
                      },
                      {
                        "id": "c2",
                        "text": "while"
                      },
                      {
                        "id": "c3",
                        "text": "for"
                      },
                      {
                        "id": "c4",
                        "text": "switch"
                      },
                      {
                        "id": "c5",
                        "text": "if"
                      }
                    ],
                    "info": "Post-condition."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-h-4",
                  "text": "Functional аналог for в Kotlin — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Functional аналог for в Kotlin — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "forEach"
                      },
                      {
                        "id": "c2",
                        "text": "foreach"
                      },
                      {
                        "id": "c3",
                        "text": "map"
                      },
                      {
                        "id": "c4",
                        "text": "reduce"
                      },
                      {
                        "id": "c5",
                        "text": "fold"
                      }
                    ],
                    "info": "forEach — стандартное расширение."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-1-fb-h-5",
                  "text": "while(true) без break — это ___ цикл.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "while(true) без break — это ___ цикл.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "бесконечный"
                      },
                      {
                        "id": "c2",
                        "text": "конечный"
                      },
                      {
                        "id": "c3",
                        "text": "нулевой"
                      },
                      {
                        "id": "c4",
                        "text": "арифметический"
                      },
                      {
                        "id": "c5",
                        "text": "геометрический"
                      }
                    ],
                    "info": "Infinite loop."
                  }
                }
              ]
            },
            {
              "id": "lb-courses-programming-2-2-2",
              "title": "Прерывания циклов (break, continue)",
              "questions": [
                {
                  "id": "qsb-courses-programming-2-2-2-sc-e-1",
                  "text": "Какой оператор выходит из цикла?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор выходит из цикла?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break"
                      },
                      {
                        "id": "b",
                        "text": "continue"
                      },
                      {
                        "id": "c",
                        "text": "return"
                      },
                      {
                        "id": "d",
                        "text": "exit"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "break — выход из цикла."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-e-2",
                  "text": "Какой оператор пропускает текущую итерацию?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор пропускает текущую итерацию?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break"
                      },
                      {
                        "id": "b",
                        "text": "continue"
                      },
                      {
                        "id": "c",
                        "text": "return"
                      },
                      {
                        "id": "d",
                        "text": "skip"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "continue — следующая итерация."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-e-3",
                  "text": "Какой оператор завершает функцию?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Какой оператор завершает функцию?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break"
                      },
                      {
                        "id": "b",
                        "text": "continue"
                      },
                      {
                        "id": "c",
                        "text": "return"
                      },
                      {
                        "id": "d",
                        "text": "exit"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "return — выход из метода."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-e-4",
                  "text": "Что делает break во вложенном цикле?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что делает break во вложенном цикле?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Выходит из всех"
                      },
                      {
                        "id": "b",
                        "text": "Выходит из ближайшего"
                      },
                      {
                        "id": "c",
                        "text": "Ничего"
                      },
                      {
                        "id": "d",
                        "text": "Выходит из метода"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "break — только из ближайшего."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-e-5",
                  "text": "Что делает continue в for?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "EASY",
                    "text": "Что делает continue в for?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Прерывает цикл"
                      },
                      {
                        "id": "b",
                        "text": "Переходит к след. итерации"
                      },
                      {
                        "id": "c",
                        "text": "Возвращает значение"
                      },
                      {
                        "id": "d",
                        "text": "Бросает исключение"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Skip оставшегося тела."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-e-1",
                  "text": "Какие операторы прерывают/изменяют поток цикла?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие операторы прерывают/изменяют поток цикла?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break"
                      },
                      {
                        "id": "b",
                        "text": "continue"
                      },
                      {
                        "id": "c",
                        "text": "return"
                      },
                      {
                        "id": "d",
                        "text": "throw"
                      },
                      {
                        "id": "e",
                        "text": "goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto не доступно."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-e-2",
                  "text": "Какие выражения корректны?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие выражения корректны?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break"
                      },
                      {
                        "id": "b",
                        "text": "continue"
                      },
                      {
                        "id": "c",
                        "text": "break@label"
                      },
                      {
                        "id": "d",
                        "text": "continue@label"
                      },
                      {
                        "id": "e",
                        "text": "next"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "next — Python."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-e-3",
                  "text": "Какие циклы поддерживают break?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие циклы поддерживают break?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "for"
                      },
                      {
                        "id": "b",
                        "text": "while"
                      },
                      {
                        "id": "c",
                        "text": "do-while"
                      },
                      {
                        "id": "d",
                        "text": "forEach (lambda)"
                      },
                      {
                        "id": "e",
                        "text": "repeat"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c"
                    ],
                    "info": "forEach lambda — нет break."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-e-4",
                  "text": "Какие способы выйти из вложенных циклов?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие способы выйти из вложенных циклов?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "break@outer"
                      },
                      {
                        "id": "b",
                        "text": "return"
                      },
                      {
                        "id": "c",
                        "text": "throw"
                      },
                      {
                        "id": "d",
                        "text": "flag-переменная"
                      },
                      {
                        "id": "e",
                        "text": "goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-e-5",
                  "text": "Какие типы меток в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "EASY",
                    "text": "Какие типы меток в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "outer@"
                      },
                      {
                        "id": "b",
                        "text": "loop@"
                      },
                      {
                        "id": "c",
                        "text": "@label"
                      },
                      {
                        "id": "d",
                        "text": "tag@"
                      },
                      {
                        "id": "e",
                        "text": "lbl@"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "d",
                      "e"
                    ],
                    "info": "@label — другой формат."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-e-1",
                  "text": "Расположите ключевые слова по силе прерывания.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите ключевые слова по силе прерывания.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "continue"
                      },
                      {
                        "id": "i2",
                        "text": "break"
                      },
                      {
                        "id": "i3",
                        "text": "return"
                      },
                      {
                        "id": "i4",
                        "text": "throw"
                      }
                    ],
                    "info": "От skip итерации к выходу из всего."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-e-2",
                  "text": "Расположите шаги break в for-цикле.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги break в for-цикле.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Выполнить тело"
                      },
                      {
                        "id": "i2",
                        "text": "Достичь break"
                      },
                      {
                        "id": "i3",
                        "text": "Выйти из цикла"
                      },
                      {
                        "id": "i4",
                        "text": "Продолжить после цикла"
                      }
                    ],
                    "info": "Поток с break."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-e-3",
                  "text": "Расположите шаги continue.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите шаги continue.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Выполнить часть тела"
                      },
                      {
                        "id": "i2",
                        "text": "Достичь continue"
                      },
                      {
                        "id": "i3",
                        "text": "Перейти к проверке условия"
                      },
                      {
                        "id": "i4",
                        "text": "След. итерация (если condition true)"
                      }
                    ],
                    "info": "Continue skip."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-e-4",
                  "text": "Расположите циклы по доступности break.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите циклы по доступности break.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "for"
                      },
                      {
                        "id": "i2",
                        "text": "while"
                      },
                      {
                        "id": "i3",
                        "text": "do-while"
                      },
                      {
                        "id": "i4",
                        "text": "forEach lambda (нет)"
                      }
                    ],
                    "info": "Lambda не имеет break."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-e-5",
                  "text": "Расположите способы выхода из вложенного цикла.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "EASY",
                    "text": "Расположите способы выхода из вложенного цикла.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "break@outer"
                      },
                      {
                        "id": "i2",
                        "text": "flag-variable"
                      },
                      {
                        "id": "i3",
                        "text": "throw exception"
                      },
                      {
                        "id": "i4",
                        "text": "return из функции"
                      }
                    ],
                    "info": "От локального к глобальному."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-e-1",
                  "text": "Выход из цикла — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Выход из цикла — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "break"
                      },
                      {
                        "id": "c2",
                        "text": "continue"
                      },
                      {
                        "id": "c3",
                        "text": "return"
                      },
                      {
                        "id": "c4",
                        "text": "exit"
                      },
                      {
                        "id": "c5",
                        "text": "next"
                      }
                    ],
                    "info": "break — конец цикла."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-e-2",
                  "text": "Пропуск итерации — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Пропуск итерации — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "continue"
                      },
                      {
                        "id": "c2",
                        "text": "break"
                      },
                      {
                        "id": "c3",
                        "text": "skip"
                      },
                      {
                        "id": "c4",
                        "text": "pass"
                      },
                      {
                        "id": "c5",
                        "text": "next"
                      }
                    ],
                    "info": "continue — к след. итерации."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-e-3",
                  "text": "Выход из метода — ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Выход из метода — ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "return"
                      },
                      {
                        "id": "c2",
                        "text": "break"
                      },
                      {
                        "id": "c3",
                        "text": "exit"
                      },
                      {
                        "id": "c4",
                        "text": "continue"
                      },
                      {
                        "id": "c5",
                        "text": "throw"
                      }
                    ],
                    "info": "return — завершение функции."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-e-4",
                  "text": "Метка цикла оканчивается на ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "Метка цикла оканчивается на ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "@"
                      },
                      {
                        "id": "c2",
                        "text": ":"
                      },
                      {
                        "id": "c3",
                        "text": "#"
                      },
                      {
                        "id": "c4",
                        "text": "!"
                      },
                      {
                        "id": "c5",
                        "text": "*"
                      }
                    ],
                    "info": "outer@."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-e-5",
                  "text": "break@outer выходит из ___ цикла.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "EASY",
                    "text": "break@outer выходит из ___ цикла.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "внешнего"
                      },
                      {
                        "id": "c2",
                        "text": "внутреннего"
                      },
                      {
                        "id": "c3",
                        "text": "текущего"
                      },
                      {
                        "id": "c4",
                        "text": "последнего"
                      },
                      {
                        "id": "c5",
                        "text": "первого"
                      }
                    ],
                    "info": "Метка указывает уровень."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-h-1",
                  "text": "Что напечатает for(i in 1..3){if(i==2)break; print(i)}?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает for(i in 1..3){if(i==2)break; print(i)}?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "1"
                      },
                      {
                        "id": "b",
                        "text": "12"
                      },
                      {
                        "id": "c",
                        "text": "123"
                      },
                      {
                        "id": "d",
                        "text": "23"
                      }
                    ],
                    "correctOptionId": "a",
                    "info": "break при i=2 → выводит только 1."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-h-2",
                  "text": "Что напечатает for(i in 1..3){if(i==2)continue; print(i)}?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Что напечатает for(i in 1..3){if(i==2)continue; print(i)}?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "12"
                      },
                      {
                        "id": "b",
                        "text": "13"
                      },
                      {
                        "id": "c",
                        "text": "23"
                      },
                      {
                        "id": "d",
                        "text": "123"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Пропускает i=2 → 1, 3."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-h-3",
                  "text": "В forEach{} return завершает ___?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "В forEach{} return завершает ___?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Только лямбду"
                      },
                      {
                        "id": "b",
                        "text": "Внешнюю функцию"
                      },
                      {
                        "id": "c",
                        "text": "Цикл"
                      },
                      {
                        "id": "d",
                        "text": "Программу"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Non-local return из лямбды."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-h-4",
                  "text": "В forEach{} return@forEach делает ___?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "В forEach{} return@forEach делает ___?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Выходит из forEach"
                      },
                      {
                        "id": "b",
                        "text": "Выходит из метода"
                      },
                      {
                        "id": "c",
                        "text": "Skip итерации"
                      },
                      {
                        "id": "d",
                        "text": "Ошибка"
                      }
                    ],
                    "correctOptionId": "c",
                    "info": "Это аналог continue."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-sc-h-5",
                  "text": "Можно ли использовать break в forEach{}?",
                  "payload": {
                    "type": "SingleChoice",
                    "difficulty": "HARD",
                    "text": "Можно ли использовать break в forEach{}?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Да, всегда"
                      },
                      {
                        "id": "b",
                        "text": "Нет, lambda"
                      },
                      {
                        "id": "c",
                        "text": "Только в Kotlin 2.0"
                      },
                      {
                        "id": "d",
                        "text": "Только с label"
                      }
                    ],
                    "correctOptionId": "b",
                    "info": "Break не работает в lambda."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-h-1",
                  "text": "Какие способы досрочного выхода из forEach{}?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие способы досрочного выхода из forEach{}?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "return@forEach (skip)"
                      },
                      {
                        "id": "b",
                        "text": "throw exception"
                      },
                      {
                        "id": "c",
                        "text": "flag + ifEarly"
                      },
                      {
                        "id": "d",
                        "text": "Использовать обычный for"
                      },
                      {
                        "id": "e",
                        "text": "break"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "break не работает в lambda."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-h-2",
                  "text": "Какие особенности break/continue в Kotlin?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие особенности break/continue в Kotlin?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Поддержка меток"
                      },
                      {
                        "id": "b",
                        "text": "break@outer"
                      },
                      {
                        "id": "c",
                        "text": "continue@outer"
                      },
                      {
                        "id": "d",
                        "text": "Не работает в forEach lambda"
                      },
                      {
                        "id": "e",
                        "text": "Может пропустить finally"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "finally всегда выполняется."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-h-3",
                  "text": "Какие проблемы с прерываниями?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие проблемы с прерываниями?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Глубокая вложенность"
                      },
                      {
                        "id": "b",
                        "text": "Spaghetti code"
                      },
                      {
                        "id": "c",
                        "text": "Сложная отладка"
                      },
                      {
                        "id": "d",
                        "text": "Замена if"
                      },
                      {
                        "id": "e",
                        "text": "Неочевидный поток"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "e"
                    ],
                    "info": "Замена if — другая проблема."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-h-4",
                  "text": "Какие альтернативы break?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие альтернативы break?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "Условие в while"
                      },
                      {
                        "id": "b",
                        "text": "flag-переменная"
                      },
                      {
                        "id": "c",
                        "text": "Functional методы (takeWhile)"
                      },
                      {
                        "id": "d",
                        "text": "return из функции"
                      },
                      {
                        "id": "e",
                        "text": "goto"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "goto нет."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-mc-h-5",
                  "text": "Какие альтернативы continue?",
                  "payload": {
                    "type": "MultipleChoice",
                    "difficulty": "HARD",
                    "text": "Какие альтернативы continue?",
                    "imageUrl": null,
                    "options": [
                      {
                        "id": "a",
                        "text": "if + skip body"
                      },
                      {
                        "id": "b",
                        "text": "Functional filter"
                      },
                      {
                        "id": "c",
                        "text": "Inverted condition"
                      },
                      {
                        "id": "d",
                        "text": "return@label"
                      },
                      {
                        "id": "e",
                        "text": "next"
                      }
                    ],
                    "correctOptionIds": [
                      "a",
                      "b",
                      "c",
                      "d"
                    ],
                    "info": "next — Python."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-h-1",
                  "text": "Расположите по силе прерывания (от слабого к сильному).",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите по силе прерывания (от слабого к сильному).",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "continue"
                      },
                      {
                        "id": "i2",
                        "text": "break"
                      },
                      {
                        "id": "i3",
                        "text": "break@outer"
                      },
                      {
                        "id": "i4",
                        "text": "return из функции"
                      }
                    ],
                    "info": "От локального к глобальному."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-h-2",
                  "text": "Расположите шаги break@outer во вложенном цикле.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги break@outer во вложенном цикле.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Выполнить inner"
                      },
                      {
                        "id": "i2",
                        "text": "Достичь break@outer"
                      },
                      {
                        "id": "i3",
                        "text": "Выйти из inner"
                      },
                      {
                        "id": "i4",
                        "text": "Выйти из outer"
                      }
                    ],
                    "info": "Метка указывает цикл."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-h-3",
                  "text": "Расположите шаги replace continue with filter.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги replace continue with filter.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Найти условие skip"
                      },
                      {
                        "id": "i2",
                        "text": "Инвертировать его"
                      },
                      {
                        "id": "i3",
                        "text": "Применить .filter{}"
                      },
                      {
                        "id": "i4",
                        "text": "Удалить continue"
                      }
                    ],
                    "info": "Functional refactor."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-h-4",
                  "text": "Расположите шаги выхода из forEach с flag.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите шаги выхода из forEach с flag.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "Объявить var stop=false"
                      },
                      {
                        "id": "i2",
                        "text": "forEach{if(c)stop=true; if(stop)return@forEach}"
                      },
                      {
                        "id": "i3",
                        "text": "Проверить flag после цикла"
                      },
                      {
                        "id": "i4",
                        "text": "Использовать результат"
                      }
                    ],
                    "info": "Flag-pattern."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-ord-h-5",
                  "text": "Расположите по сложности отладки.",
                  "payload": {
                    "type": "Ordering",
                    "difficulty": "HARD",
                    "text": "Расположите по сложности отладки.",
                    "imageUrl": null,
                    "items": [
                      {
                        "id": "i1",
                        "text": "if-skip"
                      },
                      {
                        "id": "i2",
                        "text": "continue"
                      },
                      {
                        "id": "i3",
                        "text": "break"
                      },
                      {
                        "id": "i4",
                        "text": "goto-like"
                      }
                    ],
                    "info": "Чем сложнее — тем труднее отладка."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-h-1",
                  "text": "Метка для break — пишется как ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Метка для break — пишется как ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "name@"
                      },
                      {
                        "id": "c2",
                        "text": "@name"
                      },
                      {
                        "id": "c3",
                        "text": "name:"
                      },
                      {
                        "id": "c4",
                        "text": "#name"
                      },
                      {
                        "id": "c5",
                        "text": "!name"
                      }
                    ],
                    "info": "Kotlin синтаксис."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-h-2",
                  "text": "В forEach lambda аналог continue — return___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "В forEach lambda аналог continue — return___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "@forEach"
                      },
                      {
                        "id": "c2",
                        "text": "@continue"
                      },
                      {
                        "id": "c3",
                        "text": "@skip"
                      },
                      {
                        "id": "c4",
                        "text": "@next"
                      },
                      {
                        "id": "c5",
                        "text": "@label"
                      }
                    ],
                    "info": "Non-local return с label."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-h-3",
                  "text": "finally блок выполнится ___ после break.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "finally блок выполнится ___ после break.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "всегда"
                      },
                      {
                        "id": "c2",
                        "text": "никогда"
                      },
                      {
                        "id": "c3",
                        "text": "иногда"
                      },
                      {
                        "id": "c4",
                        "text": "только при ошибке"
                      },
                      {
                        "id": "c5",
                        "text": "только в catch"
                      }
                    ],
                    "info": "finally гарантирован."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-h-4",
                  "text": "break без label выходит из ___ цикла.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "break без label выходит из ___ цикла.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "ближайшего"
                      },
                      {
                        "id": "c2",
                        "text": "внешнего"
                      },
                      {
                        "id": "c3",
                        "text": "верхнего"
                      },
                      {
                        "id": "c4",
                        "text": "функционального"
                      },
                      {
                        "id": "c5",
                        "text": "последнего"
                      }
                    ],
                    "info": "Только текущий уровень."
                  }
                },
                {
                  "id": "qsb-courses-programming-2-2-2-fb-h-5",
                  "text": "Functional аналог break — это ___.",
                  "payload": {
                    "type": "FillBlank",
                    "difficulty": "HARD",
                    "text": "Functional аналог break — это ___.",
                    "imageUrl": null,
                    "blanks": [
                      {
                        "id": "b1",
                        "correctCandidateId": "c1"
                      }
                    ],
                    "candidates": [
                      {
                        "id": "c1",
                        "text": "takeWhile"
                      },
                      {
                        "id": "c2",
                        "text": "forEach"
                      },
                      {
                        "id": "c3",
                        "text": "map"
                      },
                      {
                        "id": "c4",
                        "text": "reduce"
                      },
                      {
                        "id": "c5",
                        "text": "filter"
                      }
                    ],
                    "info": "takeWhile останавливает на первом false."
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
