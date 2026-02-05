package com.anymind.anymind.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.anymind.anymind.data.GroupSummary
import com.anymind.anymind.data.RecordRepository
import com.anymind.anymind.data.RecordSummary
import com.anymind.anymind.data.TagSummary
import com.anymind.anymind.util.GroupingMode
import com.anymind.anymind.util.TagFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecordRepository(application)

    val records = MutableLiveData<List<RecordSummary>>()
    val tagSummaries = MutableLiveData<List<TagSummary>>()
    val groups = MutableLiveData<List<GroupSummary>>()
    val syncStatus = MutableLiveData<String>()

    var groupingMode: GroupingMode = GroupingMode.WEEK
    var selectedGroupKey: String? = null
    var selectedTags: MutableList<String> = mutableListOf()
    var tagFilterMode: TagFilterMode = TagFilterMode.AND
    var searchText: String = ""
    var tagSearchText: String = ""

    private var allTags: List<TagSummary> = emptyList()

    fun reloadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTags = selectedTags.toList()
            val currentSearch = searchText
            val currentMode = tagFilterMode
            val currentGroupKey = selectedGroupKey
            val currentGrouping = groupingMode
            val groupData = repository.fetchGroupSummaries(groupingMode)
            val tagData = repository.fetchTagSummaries()
            val recordData = repository.fetchRecordSummaries(
                currentGroupKey,
                currentGrouping,
                currentSearch,
                currentTags,
                currentMode
            )
            allTags = tagData
            groups.postValue(groupData)
            tagSummaries.postValue(filterTags(tagSearchText, tagData))
            records.postValue(recordData)
        }
    }

    fun reloadRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTags = selectedTags.toList()
            val currentSearch = searchText
            val currentMode = tagFilterMode
            val currentGroupKey = selectedGroupKey
            val currentGrouping = groupingMode
            val recordData = repository.fetchRecordSummaries(
                currentGroupKey,
                currentGrouping,
                currentSearch,
                currentTags,
                currentMode
            )
            records.postValue(recordData)
        }
    }

    fun updateTagSearchText(text: String) {
        tagSearchText = text
        tagSummaries.postValue(filterTags(text, allTags))
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.syncNow()
            syncStatus.postValue(result.message)
            reloadAll()
        }
    }

    suspend fun createRecord(): String = withContext(Dispatchers.IO) {
        repository.createRecord("").id
    }

    suspend fun deleteRecord(id: String) = withContext(Dispatchers.IO) {
        repository.deleteRecord(id)
    }

    private fun filterTags(input: String, source: List<TagSummary>): List<TagSummary> {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) {
            return source
        }
        return source.filter { it.name.lowercase().contains(trimmed) }
    }
}
