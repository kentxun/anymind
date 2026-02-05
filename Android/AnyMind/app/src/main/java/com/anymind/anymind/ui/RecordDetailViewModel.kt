package com.anymind.anymind.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.anymind.anymind.data.Record
import com.anymind.anymind.data.RecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecordRepository(application)

    val record = MutableLiveData<Record?>()
    val saveStatus = MutableLiveData<String>()

    fun load(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            record.postValue(repository.fetchRecord(id))
        }
    }

    fun save(id: String, content: String, notify: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.updateRecord(id, content)
            record.postValue(updated)
            if (notify) {
                saveStatus.postValue("Saved")
            }
        }
    }

    fun toggleSync(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.setSyncEnabled(id, enabled)
            record.postValue(updated)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRecord(id)
            record.postValue(null)
        }
    }

    suspend fun fetchCurrent(id: String): Record? = withContext(Dispatchers.IO) {
        repository.fetchRecord(id)
    }
}
