package com.tpov.schoolquiz

import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.data.model.local.EventData
import com.tpov.common.data.model.local.QuizData
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.data.model.local.SubCategoryData
import com.tpov.common.data.model.local.SubsubCategoryData

object StructureData1 {
    val structureData = StructureData(
        listOf(
            EventData(1, listOf(
                    CategoryData(1,listOf(
                            SubCategoryData(1,listOf(
                                    SubsubCategoryData(1,listOf(
                                            QuizData(101, "Столицы Европы")
                                        ), "Столицы")
                                ), "Европа"),
                        SubCategoryData(2,listOf(
                                    SubsubCategoryData(1,listOf(
                                            QuizData(102, "Столицы Африки")
                                        ), "Столицы")
                                ), "Африка"),
                        SubCategoryData(3,listOf(
                                    SubsubCategoryData(1,listOf(
                                            QuizData(103, "Столицы Северной Америки")
                                        ), "Столицы")
                                ), "Северная Америка")
                        ),"География",)
                ))
        )
    )

}