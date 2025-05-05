package StructureDataUtils

import com.tpov.common.domain.model.StructureDataLocal

object StructureDataUtils {
    val structureData = StructureDataLocal(
        id = 0,
        children = mutableListOf(
            StructureDataLocal(
                id = 1,
                children = mutableListOf(
                    StructureDataLocal(id = 1),
                    StructureDataLocal(id = 2),
                    StructureDataLocal(id = 3)
                )
            ),
            StructureDataLocal(
                id = 2,
                children = mutableListOf(
                    StructureDataLocal(id = 2),
                    StructureDataLocal(id = 3),
                    StructureDataLocal(id = 4)
                )
            ),
            StructureDataLocal(
                id = 4,
                children = mutableListOf(
                    StructureDataLocal(id = 2),
                    StructureDataLocal(id = 4),
                    StructureDataLocal(id = 6)
                )
            ),
            StructureDataLocal(
                id = 6,
                children = mutableListOf(
                    StructureDataLocal(id = 3),
                    StructureDataLocal(id = 4),
                    StructureDataLocal(
                        id = 6,
                        children = mutableListOf(
                            StructureDataLocal(id = 3),
                            StructureDataLocal(id = 8)
                        )
                    )
                )
            )
        )
    )
    val structureDataNew = StructureDataLocal(
        id = 0,
        children = mutableListOf(
            StructureDataLocal(
                id = 1,
                children = mutableListOf(
                    StructureDataLocal(id = 1),
                    StructureDataLocal(id = 2),
                    StructureDataLocal(id = 3)
                )
            ),
            StructureDataLocal(
                id = 2,
                children = mutableListOf(
                    StructureDataLocal(id = 1),
                    StructureDataLocal(id = 2),
                    StructureDataLocal(id = 3)
                )
            ),
            StructureDataLocal(
                id = 3,
                children = mutableListOf(
                    StructureDataLocal(id = 1),
                    StructureDataLocal(id = 2),
                    StructureDataLocal(id = 3)
                )
            ),
            StructureDataLocal(
                id = 4,
                children = mutableListOf(
                    StructureDataLocal(id = 1),
                    StructureDataLocal(id = 2),
                    StructureDataLocal(
                        id = 3,
                        children = mutableListOf(
                            StructureDataLocal(id = 1),
                            StructureDataLocal(id = 2)
                        )
                    )
                )
            )
        )
    )
}