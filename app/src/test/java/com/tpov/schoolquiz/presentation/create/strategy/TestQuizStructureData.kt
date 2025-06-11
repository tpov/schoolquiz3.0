package com.tpov.schoolquiz.presentation.create.strategy

import com.tpov.common.data.model.local.StructureDataLocal


val inputStructureData = mutableListOf(
    StructureDataLocal(
        nameItem = "Тест Категория",
        children = mutableListOf(
            StructureDataLocal(
                nameItem = "Тест Субкатегория",
                children = mutableListOf(
                    StructureDataLocal(
                        nameItem = "Тест СубСубкатегория",
                        children = mutableListOf(StructureDataLocal(nameItem = "Тест Квиз"))
                    )
                )
            )
        )
    )
)

//сценарии последовательного запуска
val inputData1 = mutableListOf(
    StructureDataLocal(nameItem = "Тест Категория"), // Добавляемые дети
    StructureDataLocal(nameItem = "Тест Субкатегория"), // Пример другого нового ребенка
    StructureDataLocal(nameItem = "Новая СубСубкатегория"), // Пример другого нового ребенка
    StructureDataLocal(nameItem = "Тест Квиз") // Пример другого нового ребенка
)
val inputData2 = mutableListOf(
    StructureDataLocal(nameItem = "Новая Категория"), // Добавляемые дети
    StructureDataLocal(nameItem = "Новая Субкатегория"), // Пример другого нового ребенка
    StructureDataLocal(nameItem = "Новая СубСубкатегория"), // Пример другого нового ребенка
    StructureDataLocal(nameItem = "Тест Квиз") // Пример другого нового ребенка
)
val inputData3 = mutableListOf(
    StructureDataLocal(nameItem = "Новая Категория"), // Добавляемые дети
    StructureDataLocal(nameItem = "Новая Субкатегория"), // Пример другого нового ребенка
    StructureDataLocal(nameItem = "Новая СубСубкатегория"), // Пример другого нового ребенка
    StructureDataLocal(nameItem = "Тест Квиз2") // Пример другого нового ребенка
)

val outputData = mutableListOf(
    StructureDataLocal(
        nameItem = "Тест Категория",
        children = mutableListOf(
            StructureDataLocal(
                nameItem = "Тест Субкатегория",
                children = mutableListOf(
                    StructureDataLocal(
                        nameItem = "Тест СубСубкатегория",
                        children = mutableListOf(StructureDataLocal(nameItem = "Тест Квиз"))
                    ),
                    StructureDataLocal(
                        nameItem = "Новая СубСубкатегория",
                        children = mutableListOf(StructureDataLocal(nameItem = "Тест Квиз"))
                    ),
                )
            )
        )
    ), StructureDataLocal(
        nameItem = "Новая Категория",
        children = mutableListOf(
            StructureDataLocal(
                nameItem = "Новая Субкатегория",
                children = mutableListOf(
                    StructureDataLocal(
                        nameItem = "Новая СубСубкатегория",
                        children = mutableListOf(
                            StructureDataLocal(nameItem = "Тест Квиз"),
                            StructureDataLocal(nameItem = "Тест Квиз2")
                        )
                    ),
                )
            )
        )
    )
)

