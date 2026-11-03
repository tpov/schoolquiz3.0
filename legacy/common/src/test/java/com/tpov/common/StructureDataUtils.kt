package StructureDataUtils

import com.tpov.common.data.model.local.StructureDataLocal

object StructureDataUtils {
    val structureData = StructureDataLocal(
        nameItem = "root_0",
        children = mutableListOf(
            StructureDataLocal(
                nameItem = "item_1",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "item_1_1"),
                    StructureDataLocal(nameItem = "item_1_2"),
                    StructureDataLocal(nameItem = "item_1_3")
                )
            ),
            StructureDataLocal(
                nameItem = "item_2",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "item_2_1"),
                    StructureDataLocal(nameItem = "item_2_2"),
                    StructureDataLocal(nameItem = "item_2_3")
                )
            ),
            StructureDataLocal(
                nameItem = "item_4",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "item_4_1"),
                    StructureDataLocal(nameItem = "item_4_2"),
                    StructureDataLocal(nameItem = "item_4_3")
                )
            ),
            StructureDataLocal(
                nameItem = "item_6",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "item_6_1"),
                    StructureDataLocal(nameItem = "item_6_2"),
                    StructureDataLocal(
                        nameItem = "item_6_3",
                        children = mutableListOf(
                            StructureDataLocal(nameItem = "item_6_3_1"),
                            StructureDataLocal(nameItem = "item_6_3_2")
                        )
                    )
                )
            )
        )
    )
    val structureDataNew = StructureDataLocal(
        nameItem = "root_new",
        children = mutableListOf(
            StructureDataLocal(
                nameItem = "new_item_1",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "new_item_1_1"),
                    StructureDataLocal(nameItem = "new_item_1_2"),
                    StructureDataLocal(nameItem = "new_item_1_3")
                )
            ),
            StructureDataLocal(
                nameItem = "new_item_2",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "new_item_2_1"),
                    StructureDataLocal(nameItem = "new_item_2_2"),
                    StructureDataLocal(nameItem = "new_item_2_3")
                )
            ),
            StructureDataLocal(
                nameItem = "new_item_3",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "new_item_3_1"),
                    StructureDataLocal(nameItem = "new_item_3_2"),
                    StructureDataLocal(nameItem = "new_item_3_3")
                )
            ),
            StructureDataLocal(
                nameItem = "new_item_4",
                children = mutableListOf(
                    StructureDataLocal(nameItem = "new_item_4_1"),
                    StructureDataLocal(nameItem = "new_item_4_2"),
                    StructureDataLocal(
                        nameItem = "new_item_4_3",
                        children = mutableListOf(
                            StructureDataLocal(nameItem = "new_item_4_3_1"),
                            StructureDataLocal(nameItem = "new_item_4_3_2")
                        )
                    )
                )
            )
        )
    )
}
