package com.tpov.schoolquiz.presentation.edit.strategy

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.schoolquiz.presentation.edit.manager.QuestionStateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
class CreateQuizRegimeStrategyTest {

    @Mock
    lateinit var structureUseCase: StructureUseCase

    @Mock
    lateinit var questionUseCase: QuestionUseCase

    @Mock
    lateinit var questionStateManager: QuestionStateManager

    @Mock
    lateinit var defaultImage: BitmapDrawable

    @Captor
    lateinit var structureDataCaptor: ArgumentCaptor<List<StructureDataLocal>>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testSaveData_sequentialCalls() = runBlocking {
        // Create instance of the strategy
        val strategy = CreateQuizRegimeStrategy(structureUseCase, questionUseCase, questionStateManager)

        // Initialize the user's structure data with inputStructureData for sequential tests.
        // We use a mutable list to simulate the state being updated by updateStructureData.
        val initialStructures = deepCopyStructureList(inputStructureData).toMutableList()

        // Print initial structure
        println("\n--- Initial Structure ---")
        initialStructures.forEach { it.printFullStructure("") }
        println("---\n")

        // Mock getStructureEventData to return a deep copy of the current state of initialStructures
        whenever(structureUseCase.getStructureEventData(EventQuiz.QUIZ_BY_USER)).thenAnswer { deepCopyStructureList(initialStructures) }

        // Mock updateStructureData to simply do nothing, as merging is now done in saveData
        whenever(structureUseCase.updateStructureDataList(any(), eq(EventQuiz.QUIZ_BY_USER) as EventQuiz)).thenAnswer { invocation ->
            val updatedList = invocation.getArgument<List<StructureDataLocal>>(0)
            initialStructures.clear()
            initialStructures.addAll(updatedList)
            updatedList // Return the updated list
        }

        // Perform the three sequential calls to saveData with the provided input data
        // Convert structure data to List<Pair<String, BitmapDrawable>> format
        val structurePairs1 = inputData1.map { it.nameItem to defaultImage }
        val structurePairs2 = inputData2.map { it.nameItem to defaultImage }
        val structurePairs3 = inputData3.map { it.nameItem to defaultImage }
        
        strategy.saveData(emptyList(), emptyMap(), structurePairs1, defaultImage)
        // After this call, saveData will have merged inputData1 into the structure and called updateStructureDataList

        strategy.saveData(emptyList(), emptyMap(), structurePairs2, defaultImage)
        // After this call, saveData will have merged inputData2 into the structure and called updateStructureDataList

        strategy.saveData(emptyList(), emptyMap(), structurePairs3, defaultImage)
        // After this call, saveData will have merged inputData3 into the structure and called updateStructureDataList

        // Now, initialStructures holds the state after all the merges simulated by calling saveData.

        // Print final structure after sequential saves (updated by saveData calls)
        println("\n--- Final Structure after sequential saves ---")
        initialStructures.forEach { it.printFullStructure("") }
        println("---\n")

        // Expected final structure after sequential saves - based on outputData
        val expectedFinalStructures = deepCopyStructureList(outputData)

        // Print expected final structure
        println("\n--- Expected Final Structure (outputData) ---")
        expectedFinalStructures.forEach { it.printFullStructure("") }
        println("---\n")

        // Assert that the final state of initialStructures matches the expected outputData
        // based on names and hierarchy, using the existing helper function.
        assertTrue(
            "Expected and actual structures do not match based on names after sequential saves",
            compareStructureLists(expectedFinalStructures, initialStructures)
        )

        // Verify that updateStructureDataList was called the correct number of times (once for each saveData call that updates structure)
        // Note: saveData only calls updateStructureDataList if structureList is not empty.
        // inputData1, inputData2, and inputData3 are structure lists, so updateStructureDataList should be called 3 times.
        verify(structureUseCase, times(3)).updateStructureDataList(any(), eq(EventQuiz.QUIZ_BY_USER))

        // Verify that questionUseCase.pushQuestion was not called (since questionList was empty)
        verify(questionUseCase, never()).pushQuestion(any())
    }

    // Helper function to compare two StructureDataLocal lists based on nameItem and children structure
    private fun compareStructureLists(list1: List<StructureDataLocal>, list2: List<StructureDataLocal>): Boolean {
        if (list1.size != list2.size) {
            return false
        }

        // Sort lists by nameItem for consistent comparison
        val sortedList1 = list1.sortedBy { it.nameItem }
        val sortedList2 = list2.sortedBy { it.nameItem }

        for (i in sortedList1.indices) {
            if (!compareStructures(sortedList1[i], sortedList2[i])) {
                return false
            }
        }

        return true
    }

    // Helper function to compare two StructureDataLocal objects based on nameItem and children structure
    private fun compareStructures(s1: StructureDataLocal, s2: StructureDataLocal): Boolean {
        if (s1.nameItem != s2.nameItem) {
            return false
        }

        val children1 = s1.children ?: mutableListOf()
        val children2 = s2.children ?: mutableListOf()

        return compareStructureLists(children1, children2)
    }

    // Helper function for deep copying StructureDataLocal lists and their children
    private fun deepCopyStructureList(list: List<StructureDataLocal>?): MutableList<StructureDataLocal> {
        return list?.map { deepCopyStructure(it) }?.toMutableList() ?: mutableListOf()
    }

    // Helper function for deep copying StructureDataLocal objects
    private fun deepCopyStructure(structure: StructureDataLocal): StructureDataLocal {
        return StructureDataLocal(
            children = deepCopyStructureList(structure.children), // Recursively copy children
            nameItem = structure.nameItem,
            dataUpdateGlobal = structure.dataUpdateGlobal,
            dataUpdateLocal = structure.dataUpdateLocal,
            dataCreate = structure.dataCreate,
            version = structure.version,
            ratingGlobal = structure.ratingGlobal,
            ratingLocal = structure.ratingLocal,
            starsMaxLocal = structure.starsMaxLocal,
            starsMaxGlobal = structure.starsMaxGlobal,
            starsAverageLocal = structure.starsAverageLocal,
            starsAverageGlobal = structure.starsAverageGlobal,
            numHQ = structure.numHQ,
            numQ = structure.numQ,
            tpovIdCreator = structure.tpovIdCreator,
            nameCreator = structure.nameCreator,
            tpovIdMaxStarsGlobal = structure.tpovIdMaxStarsGlobal,
            languages = structure.languages,
            picture = structure.picture,
            isShowDownload = structure.isShowDownload,
            isShowArchive = structure.isShowArchive,
            hasGeneratedQuiz = structure.hasGeneratedQuiz,
            quizId = structure.quizId,
            isPurchased = structure.isPurchased,
            isBought = structure.isBought,
            isBoughtTime = structure.isBoughtTime,
            isDownload = structure.isDownload,
            show = structure.show
        )
    }


}
