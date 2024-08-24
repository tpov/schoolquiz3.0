package com.tpov.schoolquiz.presentation.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.domain.StructureUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class MainViewModel @Inject constructor(
    private val structureUseCase: StructureUseCase
) : ViewModel() {

    private val _categoryData = MutableLiveData<List<CategoryData>>()
    val categoryData: LiveData<List<CategoryData>> get() = _categoryData

    suspend fun loadHomeCategory() {
        var structureDataList = structureUseCase.getStructureData()
        if (structureDataList == null) {
            structureUseCase.syncData()
            structureDataList = structureUseCase.getStructureData()
        }
        withContext(Dispatchers.Main) {
            _categoryData.value = prepareData(structureDataList ?: retryFromDelay())
        }
    }

    private suspend fun retryFromDelay(maxAttempts: Int = 100): StructureData {
        var attempt = 0
        while (attempt < maxAttempts) {
            delay(1000L * (attempt + 1))
            val data = structureUseCase.getStructureData()
            if (data != null) {
                return data
            }
            attempt++
        }
        return null!!                   //Best practices
    }


    private fun prepareData(structureDataList: StructureData): List<CategoryData> {
        return structureDataList.event.filter { it.id == 8 }.flatMap { eventData ->
                eventData.category.filter { it.isShowDownload }
            }
        }
}

class ViewModelFactory @Inject constructor(
    private val creators: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass] ?: creators.entries.first { modelClass.isAssignableFrom(it.key) }.value
        return creator.get() as T
    }
}
