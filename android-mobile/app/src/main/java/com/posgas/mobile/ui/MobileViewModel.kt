package com.posgas.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posgas.mobile.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileViewModel(private val api: ApiClient) : ViewModel() {
    var selectedTab by mutableStateOf(0)
    var rows by mutableStateOf<List<Map<String, Any?>>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val currentEntity: String
        get() = when (selectedTab) {
            0 -> "inventario"
            1 -> "compras"
            else -> "facturas"
        }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) { api.list(currentEntity) }
            }.onSuccess {
                rows = it
            }.onFailure {
                error = it.message
            }
            loading = false
        }
    }

    fun create(data: Map<String, Any?>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.create(currentEntity, data) }
            load()
        }
    }

    fun update(id: String, data: Map<String, Any?>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.update(currentEntity, id, data) }
            load()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.delete(currentEntity, id) }
            load()
        }
    }
}
