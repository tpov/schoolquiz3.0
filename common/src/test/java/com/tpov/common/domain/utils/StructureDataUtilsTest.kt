package com.tpov.common.domain.utils

import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.presentation.model.PathStructure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class) // If using Mockito for helpers, though might not be needed for pure util tests
class StructureDataUtilsTest {

    // Helper to create test data easily
    private fun createTestData(
        name: String,
        version: Int = 0,
        dataUpdateGlobal: String = "",
        dataUpdateLocal: String = "",
        ratingGlobal: Int = 0,
        isShowArchive: Boolean = true,
        isShowDownload: Boolean = true,
        children: MutableList<StructureDataLocal>? = null
    ): StructureDataLocal {
        return StructureDataLocal(
            nameItem = name,
            version = version,
            dataUpdateGlobal = dataUpdateGlobal,
            dataUpdateLocal = dataUpdateLocal,
            ratingGlobal = ratingGlobal,
            isShowArchive = isShowArchive,
            isShowDownload = isShowDownload,
            children = children ?: mutableListOf()
        )
    }

    @BeforeEach
    fun setUp() {
        // Any common setup for util tests, if needed
    }

    // --- Tests for isUpdateStructure ---

    @Test
    fun `isUpdateStructure - new version is greater, returns false (based on current logic)`() {
        // Current logic: old.version!! > new.version. This means update if OLD is greater.
        // This test reflects the code AS IS. If logic is old.version < new.version, test changes.
        val oldData = createTestData("item", version = 1)
        val newData = createTestData("item", version = 2) // New version is greater
        assertFalse(StructureDataUtils.isUpdateStructure(oldData, newData), "Should be false if old.version (1) is NOT > new.version (2)")
    }

    @Test
    fun `isUpdateStructure - old version is greater, returns true (based on current logic)`() {
        val oldData = createTestData("item", version = 2)
        val newData = createTestData("item", version = 1) // Old version is greater
        assertTrue(StructureDataUtils.isUpdateStructure(oldData, newData), "Should be true if old.version (2) > new.version (1)")
    }

    @Test
    fun `isUpdateStructure - versions are equal, returns false (based on current logic)`() {
        val oldData = createTestData("item", version = 1)
        val newData = createTestData("item", version = 1) // Versions are equal
        assertFalse(StructureDataUtils.isUpdateStructure(oldData, newData), "Should be false if versions are equal")
    }

    @Test
    fun `isUpdateStructure - oldData is null, throws NullPointerException`() {
        val newData = createTestData("item", version = 1)
        assertThrows(NullPointerException::class.java) {
            StructureDataUtils.isUpdateStructure(null, newData)
        }
    }

    // --- Tests for findStructureByName ---
    @Test
    fun `findStructureByName - item exists in list, returns item`() {
        val item1 = createTestData("Item1")
        val item2 = createTestData("Item2")
        val list = listOf(item1, item2)
        val itemToFind = createTestData("Item1")

        val result = StructureDataUtils.findStructureByName(list, itemToFind)
        assertEquals(item1, result)
    }

    @Test
    fun `findStructureByName - item does not exist in list, returns null`() {
        val item1 = createTestData("Item1")
        val item2 = createTestData("Item2")
        val list = listOf(item1, item2)
        val itemToFind = createTestData("Item3")

        val result = StructureDataUtils.findStructureByName(list, itemToFind)
        assertNull(result)
    }

    @Test
    fun `findStructureByName - list is empty, returns null`() {
        val list = emptyList<StructureDataLocal>()
        val itemToFind = createTestData("Item1")

        val result = StructureDataUtils.findStructureByName(list, itemToFind)
        assertNull(result)
    }

    @Test
    fun `findStructureByName - list is null, returns null`() {
        val list: List<StructureDataLocal>? = null
        val itemToFind = createTestData("Item1")

        val result = StructureDataUtils.findStructureByName(list, itemToFind)
        assertNull(result)
    }

    @Test
    fun `findStructureByName - itemToFind is null, returns null (or first item if names are empty and match)`() {
        // If itemToFind is null, structureDataNew?.nameItem will be null.
        // If list contains items with empty names, it might match the first one.
        // Current implementation: it.nameItem == structureDataNew?.nameItem
        // If structureDataNew is null, structureDataNew?.nameItem is null.
        // So it will find an item where it.nameItem is also null (or if nameItem is nullable and null).
        // StructureDataLocal.nameItem is String = "", so it won't be null unless structureDataNew itself is null.
        val item1 = createTestData("") // Item with empty name
        val list = listOf(item1, createTestData("Item2"))

        val result = StructureDataUtils.findStructureByName(list, null)
        // This depends on whether find { it.nameItem == null } is true for empty string. It's not.
        // If an item in the list had a nullable name `val nameItem: String? = null`, then it would match.
        // Since nameItem is String = "", it will not match null.
        assertNull(result, "Should be null as nameItem (empty string) does not equal null")

        val itemWithNullName = StructureDataLocal(nameItem = null as String) // This is not possible with current StructureDataLocal
        // If StructureDataLocal could have a null nameItem, and structureDataNew was null, it would match.
        // But with nameItem: String = "", this specific interaction with null structureDataNew is safe.
    }

    // --- Tests for StructureDataLocal.findChildren ---
    @Test
    fun `findChildren - child exists, returns child`() {
        val child1 = createTestData("Child1")
        val parent = createTestData("Parent", children = mutableListOf(child1, createTestData("Child2")))

        val result = parent.findChildren("Child1")
        assertEquals(child1, result)
    }

    @Test
    fun `findChildren - child does not exist, returns null`() {
        val parent = createTestData("Parent", children = mutableListOf(createTestData("Child1")))

        val result = parent.findChildren("NonExistentChild")
        assertNull(result)
    }

    @Test
    fun `findChildren - children list is null, returns null`() {
        val parent = createTestData("Parent", children = null) // Explicitly set children to null

        val result = parent.findChildren("AnyChild")
        assertNull(result)
    }

    @Test
    fun `findChildren - children list is empty, returns null`() {
        val parent = createTestData("Parent", children = mutableListOf())

        val result = parent.findChildren("AnyChild")
        assertNull(result)
    }

    @Test
    fun `findChildren - nameItem is empty string, returns parent itself`() {
        val parent = createTestData("Parent", children = mutableListOf(createTestData("Child1")))

        val result = parent.findChildren("")
        assertEquals(parent, result, "Should return the parent object if nameItem is empty string")
    }

    // --- Tests for List<StructureInfoEntity>.findInfoByPath ---
    @Test
    fun `findInfoByPath - entity with matching path exists, returns entity`() {
        val path1 = PathStructure(nameEvent = "Event1", nameQuiz = "Quiz1")
        val entity1 = StructureInfoEntity(id = 1, pathStructure = path1, dateUpdate = "", idUser = 1, rating = 0, starsMax = 0, starsAverage = 0, version = 0, languages = "", isShow = false)
        val path2 = PathStructure(nameEvent = "Event1", nameQuiz = "Quiz2")
        val entity2 = StructureInfoEntity(id = 2, pathStructure = path2, dateUpdate = "", idUser = 1, rating = 0, starsMax = 0, starsAverage = 0, version = 0, languages = "", isShow = false)
        val list = listOf(entity1, entity2)

        val result = list.findInfoByPath(path1)
        assertEquals(entity1, result)
    }

    @Test
    fun `findInfoByPath - no entity with matching path, returns null`() {
        val path1 = PathStructure(nameEvent = "Event1", nameQuiz = "Quiz1")
        val entity1 = StructureInfoEntity(id = 1, pathStructure = path1, dateUpdate = "", idUser = 1, rating = 0, starsMax = 0, starsAverage = 0, version = 0, languages = "", isShow = false)
        val list = listOf(entity1)
        val pathToFind = PathStructure(nameEvent = "Event1", nameQuiz = "NonExistentQuiz")

        val result = list.findInfoByPath(pathToFind)
        assertNull(result)
    }

    @Test
    fun `findInfoByPath - list is empty, returns null`() {
        val list = emptyList<StructureInfoEntity>()
        val pathToFind = PathStructure(nameEvent = "Event1", nameQuiz = "Quiz1")

        val result = list.findInfoByPath(pathToFind)
        assertNull(result)
    }

    // Note: findChangeByPath is identical to findInfoByPath, so specific tests for it might be redundant
    // unless its intended use implies different scenarios or if it ever diverges.
    // For now, assuming they are functionally the same.

    // --- Tests for MutableList<StructureDataLocal>.addNodeByPath ---
    @Test
    fun `addNodeByPath - adds node to correct quiz level`() {
        val quizNode = createTestData("TargetQuiz")
        val subSubCategoryNode = createTestData("TargetSubSub", children = mutableListOf(quizNode))
        val subCategoryNode = createTestData("TargetSub", children = mutableListOf(subSubCategoryNode))
        val categoryNode = createTestData("TargetCat", children = mutableListOf(subCategoryNode))
        val list = mutableListOf(categoryNode)

        val newNode = createTestData("NewNodeToAdd")
        val path = PathStructure(
            nameEvent = "AnyEvent", // Event name not used by addNodeByPath directly for finding location
            nameCategory = "TargetCat",
            nameSubCategory = "TargetSub",
            nameSubsubCategory = "TargetSubSub",
            nameQuiz = "TargetQuiz" // This implies adding as a child of TargetQuiz, but current logic adds to parent of quiz.
        )
        // The current logic of addNodeByPath:
        // this.find { it.nameItem == path.nameCategory }
        //     ?.findChildren(path.nameSubCategory)
        //     ?.findChildren(path.nameSubsubCategory)?.let { quiz -> ... quiz.children?.add(node) }
        // This means it finds the SubSubCategory and adds the new node as a child to IT, if nameQuiz is specified in path.
        // If nameQuiz was empty, it would try to add to the parent of SubSubCategory (SubCategory).
        // Let's test adding as a child of a SubSubCategory (acting as a quiz container)

        val pathToSubSub = PathStructure(
            nameCategory = "TargetCat",
            nameSubCategory = "TargetSub",
            nameSubsubCategory = "TargetSubSub",
            nameQuiz = "" // To add to children of TargetSubSub
        )
        list.addNodeByPath(newNode, pathToSubSub)

        val targetSubSub = list.find { it.nameItem == "TargetCat" }
            ?.findChildren("TargetSub")
            ?.findChildren("TargetSubSub")

        assertNotNull(targetSubSub)
        assertTrue(targetSubSub!!.children!!.any { it.nameItem == "NewNodeToAdd" })
    }

    @Test
    fun `addNodeByPath - adds to category if path is only category deep (and nameQuiz is empty)`() {
        val categoryNode = createTestData("TargetCat")
        val list = mutableListOf(categoryNode)
        val newNode = createTestData("NewNodeForCat")
        val path = PathStructure(nameCategory = "TargetCat", nameQuiz = "") // Quiz name empty means add to category's children

        list.addNodeByPath(newNode, path)

        assertNotNull(list[0].children)
        assertTrue(list[0].children!!.any { it.nameItem == "NewNodeForCat" })
    }


    @Test
    fun `addNodeByPath - path to parent does not fully exist, node not added`() {
        // Current implementation does not create intermediate path elements.
        val categoryNode = createTestData("TargetCat")
        val list = mutableListOf(categoryNode)
        val newNode = createTestData("NewNodeOrphan")
        val path = PathStructure(
            nameCategory = "TargetCat",
            nameSubCategory = "NonExistentSub", // This sub-category doesn't exist
            nameSubsubCategory = "AnySubSub",
            nameQuiz = ""
        )

        list.addNodeByPath(newNode, path)

        // Check that the original structure is unchanged or newNode is not found where expected
        val targetCat = list.find { it.nameItem == "TargetCat" }
        assertNotNull(targetCat)
        assertTrue(targetCat!!.children!!.none { it.nameItem == "NewNodeOrphan" }) // Assuming children was initialized
        val nonExistentSub = targetCat.children!!.find { it.nameItem == "NonExistentSub" }
        assertNull(nonExistentSub)
    }

    @Test
    fun `addNodeByPath - parent node children list is null, it gets initialized and node added`() {
        val subSubCategoryNode = createTestData("TargetSubSub", children = null) // Children is null
        val subCategoryNode = createTestData("TargetSub", children = mutableListOf(subSubCategoryNode))
        val categoryNode = createTestData("TargetCat", children = mutableListOf(subCategoryNode))
        val list = mutableListOf(categoryNode)

        val newNode = createTestData("NewChildForInitializedList")
        val path = PathStructure(
            nameCategory = "TargetCat",
            nameSubCategory = "TargetSub",
            nameSubsubCategory = "TargetSubSub",
            nameQuiz = "" // Add to children of TargetSubSub
        )

        list.addNodeByPath(newNode, path)

        val targetParent = list.find { it.nameItem == "TargetCat" }
            ?.findChildren("TargetSub")
            ?.findChildren("TargetSubSub")

        assertNotNull(targetParent)
        assertNotNull(targetParent!!.children, "Children list should have been initialized.")
        assertTrue(targetParent.children!!.any { it.nameItem == "NewChildForInitializedList" })
    }


    // --- Tests for MutableList<StructureDataLocal>.removeNodeByPath ---
    @Test
    fun `removeNodeByPath - removes quiz node`() {
        val quizToRemove = createTestData("QuizToRemove")
        val subSub = createTestData("SubSub", children = mutableListOf(quizToRemove, createTestData("OtherQuiz")))
        val sub = createTestData("Sub", children = mutableListOf(subSub))
        val cat = createTestData("Cat", children = mutableListOf(sub))
        val list = mutableListOf(cat)
        val path = PathStructure(nameCategory = "Cat", nameSubCategory = "Sub", nameSubsubCategory = "SubSub", nameQuiz = "QuizToRemove")

        val result = list.removeNodeByPath(path)
        assertTrue(result)
        val parent = list[0].children!![0].children!![0] // SubSub
        assertFalse(parent.children!!.any { it.nameItem == "QuizToRemove" })
        assertTrue(parent.children!!.any { it.nameItem == "OtherQuiz" })
    }

    @Test
    fun `removeNodeByPath - removes subSubCategory node`() {
        val subSubToRemove = createTestData("SubSubToRemove")
        val sub = createTestData("Sub", children = mutableListOf(subSubToRemove, createTestData("OtherSubSub")))
        val cat = createTestData("Cat", children = mutableListOf(sub))
        val list = mutableListOf(cat)
        val path = PathStructure(nameCategory = "Cat", nameSubCategory = "Sub", nameSubsubCategory = "SubSubToRemove")

        val result = list.removeNodeByPath(path)
        assertTrue(result)
        val parent = list[0].children!![0] // Sub
        assertFalse(parent.children!!.any { it.nameItem == "SubSubToRemove" })
        assertTrue(parent.children!!.any { it.nameItem == "OtherSubSub" })
    }

    @Test
    fun `removeNodeByPath - removes subCategory node`() {
        val subToRemove = createTestData("SubToRemove")
        val cat = createTestData("Cat", children = mutableListOf(subToRemove, createTestData("OtherSub")))
        val list = mutableListOf(cat)
        val path = PathStructure(nameCategory = "Cat", nameSubCategory = "SubToRemove")

        val result = list.removeNodeByPath(path)
        assertTrue(result)
        val parent = list[0] // Cat
        assertFalse(parent.children!!.any { it.nameItem == "SubToRemove" })
        assertTrue(parent.children!!.any { it.nameItem == "OtherSub" })
    }

    @Test
    fun `removeNodeByPath - removes category node`() {
        val catToRemove = createTestData("CatToRemove")
        val list = mutableListOf(catToRemove, createTestData("OtherCat"))
        val path = PathStructure(nameCategory = "CatToRemove")

        val result = list.removeNodeByPath(path)
        assertTrue(result)
        assertFalse(list.any { it.nameItem == "CatToRemove" })
        assertTrue(list.any { it.nameItem == "OtherCat" })
    }

    @Test
    fun `removeNodeByPath - path does not exist, returns false and list unchanged`() {
        val cat = createTestData("Cat")
        val list = mutableListOf(cat)
        val originalListJson = Json.encodeToString(list)
        val path = PathStructure(nameCategory = "Cat", nameSubCategory = "NonExistentSub")

        val result = list.removeNodeByPath(path)
        assertFalse(result)
        assertEquals(originalListJson, Json.encodeToString(list))
    }

    @Test
    fun `removeNodeByPath - empty path, returns false and list unchanged`() {
        val cat = createTestData("Cat")
        val list = mutableListOf(cat)
        val originalListJson = Json.encodeToString(list)
        val path = PathStructure() // Empty path

        val result = list.removeNodeByPath(path)
        assertFalse(result)
        assertEquals(originalListJson, Json.encodeToString(list))
    }


    // --- Tests for MutableList<StructureDataLocal>.updateLocalInfoData ---
    // WARNING: This function's name is misleading. It appears to FIND a child list, not UPDATE in place.
    // Tests will verify its current FIND behavior.

    @Test
    fun `updateLocalInfoData - finds and returns correct child list from 'this' (old list)`() {
        val childQuiz1 = createTestData("ChildQuiz1")
        val childQuiz2 = createTestData("ChildQuiz2")
        val targetSubSubChildren = mutableListOf(childQuiz1, childQuiz2)
        val targetSubSub = createTestData("TargetSubSub", children = targetSubSubChildren)
        val targetSub = createTestData("TargetSub", children = mutableListOf(targetSubSub))
        val targetCat = createTestData("TargetCat", children = mutableListOf(targetSub))
        val oldList = mutableListOf(targetCat, createTestData("OtherCat"))

        // 'structureDataNew' (remote list) is used to guide path construction to find item in 'oldList'
        val newSubSubEquivalent = createTestData("TargetSubSub") // Name matches
        val newSubEquivalent = createTestData("TargetSub", children = mutableListOf(newSubSubEquivalent))
        val newCatEquivalent = createTestData("TargetCat", children = mutableListOf(newSubEquivalent))
        val newListParameters = listOf(newCatEquivalent) // Parameter for the function call

        val path = PathStructure(
            nameCategory = "TargetCat",
            nameSubCategory = "TargetSub",
            nameSubsubCategory = "TargetSubSub"
            // nameQuiz is not used by this function to select the final list, it stops at subsub's children
        )

        // Act: The function is an extension on 'oldList' (this)
        val result = oldList.updateLocalInfoData(newListParameters, path)

        // Assert: result should be the children of the 'TargetSubSub' node in 'oldList'
        assertNotNull(result)
        assertEquals(targetSubSubChildren, result, "Should return the children list of the specified node in the 'old' (this) list.")
        assertEquals(2, result?.size)
        assertTrue(result!!.any { it.nameItem == "ChildQuiz1" })
    }

    @Test
    fun `updateLocalInfoData - path does not fully exist in 'this' (old list), returns null`() {
        val targetCat = createTestData("TargetCat")
        val oldList = mutableListOf(targetCat)

        val newListParameters = listOf(createTestData("TargetCat", children = mutableListOf(createTestData("TargetSub"))))
        val path = PathStructure(nameCategory = "TargetCat", nameSubCategory = "TargetSub", nameSubsubCategory = "NonExistentSubSub")

        val result = oldList.updateLocalInfoData(newListParameters, path)
        assertNull(result, "Should return null if path does not fully resolve in the 'old' (this) list.")
    }

    @Test
    fun `updateLocalInfoData - path stops at category, returns category's children from 'this'`() {
        val childCat1 = createTestData("ChildCat1")
        val targetCatChildren = mutableListOf(childCat1)
        val targetCat = createTestData("TargetCat", children = targetCatChildren)
        val oldList = mutableListOf(targetCat)

        val newListParameters = listOf(createTestData("TargetCat"))
        val path = PathStructure(nameCategory = "TargetCat") // Path only to category

        val result = oldList.updateLocalInfoData(newListParameters, path)
        assertEquals(targetCatChildren, result)
    }


    // --- Tests for findStructureDataOld ---
    @Test
    fun `findStructureDataOld - path exists, returns correct OldStructureResult`() {
        val oldQuiz = createTestData("Quiz1Old")
        val oldSubSub = createTestData("SubSub1Old", children = mutableListOf(oldQuiz))
        val oldSub = createTestData("Sub1Old", children = mutableListOf(oldSubSub))
        val oldCat = createTestData("Cat1Old", children = mutableListOf(oldSub))
        val oldList = mutableListOf(oldCat)

        // New list is used to provide names for path traversal in oldList
        val newQuiz = createTestData("Quiz1NewName") // Name used in path
        val newSubSub = createTestData("SubSub1NewName", children = mutableListOf(newQuiz))
        val newSub = createTestData("Sub1NewName", children = mutableListOf(newSubSub))
        val newCat = createTestData("Cat1NewName", children = mutableListOf(newSub))
        val newList = mutableListOf(newCat)

        val path = PathStructure(
            nameEvent = "EventName",
            nameCategory = "Cat1NewName",    // Corresponds to oldCat via name matching logic if names were same,
            nameSubCategory = "Sub1NewName", // or just provides the name for lookup in oldList.
            nameSubsubCategory = "SubSub1NewName",
            nameQuiz = "Quiz1NewName"
        )
        // For this function, the names in `path` are used to traverse `newList` first to get equivalent nodes,
        // then those equivalent nodes' names are used to traverse `oldList`.
        // So, if newList has Cat1NewName, and oldList has Cat1Old, it will try to match "Cat1NewName" in oldList.
        // Let's adjust test data so names match for simpler path finding for this test's purpose.

        val pathMatchedNames = PathStructure(
            nameEvent = "EventName",
            nameCategory = "Cat1Old",
            nameSubCategory = "Sub1Old",
            nameSubsubCategory = "SubSub1Old",
            nameQuiz = "Quiz1Old"
        )
        // newList needs to reflect these names for the internal find logic to work as intended by the function
        val newQuizNameMatch = createTestData("Quiz1Old")
        val newSubSubNameMatch = createTestData("SubSub1Old", children = mutableListOf(newQuizNameMatch))
        val newSubNameMatch = createTestData("Sub1Old", children = mutableListOf(newSubSubNameMatch))
        val newCatNameMatch = createTestData("Cat1Old", children = mutableListOf(newSubNameMatch))
        val newListMatched = mutableListOf(newCatNameMatch)


        val result = StructureDataUtils.findStructureDataOld(oldList, newListMatched, pathMatchedNames)

        assertEquals(oldQuiz, result.structureData, "Should find the specific old quiz")
        assertEquals(pathMatchedNames.nameEvent, result.pathOld.nameEvent)
        assertEquals(oldCat.nameItem, result.pathOld.nameCategory)
        assertEquals(oldSub.nameItem, result.pathOld.nameSubCategory)
        assertEquals(oldSubSub.nameItem, result.pathOld.nameSubsubCategory)
        assertEquals(oldQuiz.nameItem, result.pathOld.nameQuiz)
    }

    @Test
    fun `findStructureDataOld - path does not fully exist in oldList, returns null structureData and partial path`() {
        val oldCat = createTestData("Cat1Old")
        val oldList = mutableListOf(oldCat)

        val newSub = createTestData("Sub1New") // This subcategory does not exist in oldList under Cat1Old
        val newCat = createTestData("Cat1Old", children = mutableListOf(newSub)) // newCat name matches oldCat
        val newList = mutableListOf(newCat)

        val path = PathStructure(
            nameEvent = "EventName",
            nameCategory = "Cat1Old",
            nameSubCategory = "Sub1New", // This won't be found in oldList's Cat1Old
            nameSubsubCategory = "",
            nameQuiz = ""
        )

        val result = StructureDataUtils.findStructureDataOld(oldList, newList, path)

        assertNull(result.structureData, "structureData should be null as full path not in oldList")
        assertEquals(path.nameEvent, result.pathOld.nameEvent)
        assertEquals(oldCat.nameItem, result.pathOld.nameCategory) // Category found
        assertEquals("", result.pathOld.nameSubCategory, "SubCategory part of old path should be empty as not found")
        assertEquals("", result.pathOld.nameSubsubCategory)
        assertEquals("", result.pathOld.nameQuiz)
    }

    @Test
    fun `findStructureDataOld - path only to category, returns category and its path`() {
        val oldCat = createTestData("Cat1Old", children = mutableListOf(createTestData("ChildOfCat")))
        val oldList = mutableListOf(oldCat)

        val newCat = createTestData("Cat1Old")
        val newList = mutableListOf(newCat)

        val path = PathStructure(nameEvent = "EventName", nameCategory = "Cat1Old")
        val result = StructureDataUtils.findStructureDataOld(oldList, newList, path)

        assertEquals(oldCat, result.structureData)
        assertEquals(oldCat.nameItem, result.pathOld.nameCategory)
        assertEquals("", result.pathOld.nameSubCategory)
    }


import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.argumentCaptor

    // --- Tests for processStructureDataDifferences ---

    // Mockable callback handler class
    class MockableCallbacks {
        fun onMissingOldStructure(oldList: MutableList<StructureDataLocal>?, newNode: StructureDataLocal, path: PathStructure) {}
        fun onHasChildren(oldNodeSimilar: MutableList<StructureDataLocal>?, newNodeWithChildren: StructureDataLocal, path: PathStructure) {}
        fun onNoChildren(oldNodeSimilar: MutableList<StructureDataLocal>?, newNodeLeaf: StructureDataLocal, path: PathStructure) {}
    }

    @Test
    fun `processStructureDataDifferences - new item in newList, calls onMissingOldStructure`() {
        val mockCallbacks = mock<MockableCallbacks>()
        val callbacks = CallbackDifferences(
            onMissingOldStructure = mockCallbacks::onMissingOldStructure,
            onHasChildren = mockCallbacks::onHasChildren,
            onNoChildren = mockCallbacks::onNoChildren
        )

        val newItem = createTestData("NewItem1")
        val newList = mutableListOf(newItem)
        val oldList = mutableListOf<StructureDataLocal>()
        val event = EventQuiz.QUIZ_HOME // Example event

        StructureDataUtils.processStructureDataDifferences(newList, oldList, event, callbacks)

        val pathCaptor = argumentCaptor<PathStructure>()
        verify(mockCallbacks).onMissingOldStructure(eq(oldList), eq(newItem), capture(pathCaptor))
        assertEquals(PathStructure(nameEvent = event.name, nameCategory = "NewItem1"), pathCaptor.firstValue)
        verify(mockCallbacks, times(0)).onHasChildren(any(), any(), any())
        verify(mockCallbacks, times(0)).onNoChildren(any(), any(), any())
    }

    @Test
    fun `processStructureDataDifferences - item in both, newList item has children, calls onHasChildren and recurses`() {
        val mockCallbacks = mock<MockableCallbacks>()
        val callbacks = CallbackDifferences(
            onMissingOldStructure = mockCallbacks::onMissingOldStructure,
            onHasChildren = mockCallbacks::onHasChildren,
            onNoChildren = mockCallbacks::onNoChildren
        )

        val newChild = createTestData("NewChild1")
        val newItemWithChild = createTestData("Item1", children = mutableListOf(newChild))
        val oldItemEquivalent = createTestData("Item1", children = mutableListOf()) // Old one has no child for simplicity here

        val newList = mutableListOf(newItemWithChild)
        val oldList = mutableListOf(oldItemEquivalent)
        val event = EventQuiz.QUIZ_HOME

        StructureDataUtils.processStructureDataDifferences(newList, oldList, event, callbacks)

        // Verify onHasChildren for "Item1"
        val pathCaptorParent = argumentCaptor<PathStructure>()
        verify(mockCallbacks).onHasChildren(eq(mutableListOf(oldItemEquivalent)), eq(newItemWithChild), capture(pathCaptorParent))
        assertEquals(PathStructure(nameEvent = event.name, nameCategory = "Item1"), pathCaptorParent.firstValue)

        // Verify onMissingOldStructure for "NewChild1" (because oldItemEquivalent had no children)
        val pathCaptorChild = argumentCaptor<PathStructure>()
        verify(mockCallbacks).onMissingOldStructure(eq(mutableListOf<StructureDataLocal>()), eq(newChild), capture(pathCaptorChild))
        assertEquals(PathStructure(nameEvent = event.name, nameCategory = "Item1", nameSubCategory = "NewChild1"), pathCaptorChild.firstValue)

        verify(mockCallbacks, times(0)).onNoChildren(any(), any(), any())
    }

    @Test
    fun `processStructureDataDifferences - item in both, newList item is leaf, calls onNoChildren`() {
        val mockCallbacks = mock<MockableCallbacks>()
        val callbacks = CallbackDifferences(
            onMissingOldStructure = mockCallbacks::onMissingOldStructure,
            onHasChildren = mockCallbacks::onHasChildren,
            onNoChildren = mockCallbacks::onNoChildren
        )

        val newItemLeaf = createTestData("ItemLeaf1") // No children
        val oldItemEquivalent = createTestData("ItemLeaf1")

        val newList = mutableListOf(newItemLeaf)
        val oldList = mutableListOf(oldItemEquivalent)
        val event = EventQuiz.QUIZ_HOME

        StructureDataUtils.processStructureDataDifferences(newList, oldList, event, callbacks)

        val pathCaptor = argumentCaptor<PathStructure>()
        verify(mockCallbacks).onNoChildren(eq(mutableListOf(oldItemEquivalent)), eq(newItemLeaf), capture(pathCaptor))
        assertEquals(PathStructure(nameEvent = event.name, nameCategory = "ItemLeaf1"), pathCaptor.firstValue)
        verify(mockCallbacks, times(0)).onMissingOldStructure(any(), any(), any())
        verify(mockCallbacks, times(0)).onHasChildren(any(), any(), any())
    }

    @Test
    fun `processStructureDataDifferences - complex nested structure, correct paths and callbacks`() {
        val mockCallbacks = mock<MockableCallbacks>()
        val callbacks = CallbackDifferences(
            onMissingOldStructure = mockCallbacks::onMissingOldStructure,
            onHasChildren = mockCallbacks::onHasChildren,
            onNoChildren = mockCallbacks::onNoChildren
        )

        // New List Structure
        val newQuizL2 = createTestData("NewQuizL2")
        val newSubSubL1 = createTestData("NewSubSubL1", children = mutableListOf(newQuizL2))
        val newSubL1 = createTestData("NewSubL1", children = mutableListOf(newSubSubL1))
        val newCatL1 = createTestData("NewCatL1", children = mutableListOf(newSubL1))

        val newLeafCatL2 = createTestData("NewLeafCatL2")

        val newList = mutableListOf(newCatL1, newLeafCatL2)

        // Old List Structure (missing NewSubSubL1 and NewLeafCatL2)
        val oldSubL1 = createTestData("NewSubL1") // No children initially
        val oldCatL1 = createTestData("NewCatL1", children = mutableListOf(oldSubL1))
        val oldList = mutableListOf(oldCatL1)
        val event = EventQuiz.QUIZ_BY_USER

        StructureDataUtils.processStructureDataDifferences(newList, oldList, event, callbacks)

        // Order of calls might be tricky to assert strictly without more complex captors / inOrder
        // Let's verify key calls and paths

        // For NewCatL1 (has children, exists in old)
        val pathCatL1 = PathStructure(nameEvent = event.name, nameCategory = "NewCatL1")
        verify(mockCallbacks).onHasChildren(eq(mutableListOf(oldCatL1)), eq(newCatL1), eq(pathCatL1))

        // For NewSubL1 (has children, exists in old under NewCatL1)
        val pathSubL1 = PathStructure(nameEvent = event.name, nameCategory = "NewCatL1", nameSubCategory = "NewSubL1")
        verify(mockCallbacks).onHasChildren(eq(mutableListOf(oldSubL1)), eq(newSubL1), eq(pathSubL1))

        // For NewSubSubL1 (has children, MISSING in old under NewSubL1)
        val pathSubSubL1 = PathStructure(nameEvent = event.name, nameCategory = "NewCatL1", nameSubCategory = "NewSubL1", nameSubsubCategory = "NewSubSubL1")
        verify(mockCallbacks).onMissingOldStructure(eq(mutableListOf()), eq(newSubSubL1), eq(pathSubSubL1))

        // For NewQuizL2 (leaf, MISSING in old under NewSubSubL1 - because NewSubSubL1 was missing)
        // This implies onMissingOldStructure for NewSubSubL1 will be called, then recursion on its children (NewQuizL2) against an empty old child list.
        val pathQuizL2 = PathStructure(nameEvent = event.name, nameCategory = "NewCatL1", nameSubCategory = "NewSubL1", nameSubsubCategory = "NewSubSubL1", nameQuiz = "NewQuizL2")
        verify(mockCallbacks).onMissingOldStructure(eq(mutableListOf()), eq(newQuizL2), eq(pathQuizL2))

        // For NewLeafCatL2 (leaf, MISSING in old)
        val pathLeafCatL2 = PathStructure(nameEvent = event.name, nameCategory = "NewLeafCatL2")
        verify(mockCallbacks).onMissingOldStructure(eq(oldList), eq(newLeafCatL2), eq(pathLeafCatL2))

        // Ensure onNoChildren was not called for the recursive parts where children existed or were missing
        // It might be called if an item existed in both and was a leaf.
    }
}
