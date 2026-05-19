package com.pisces312.milocal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pisces312.milocal.data.db.AppDatabase
import com.pisces312.milocal.data.db.GroupEntity
import com.pisces312.milocal.data.repository.GroupRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GroupUiState(
    val groups: List<GroupEntity> = emptyList(),
    val editing: GroupEntity? = null,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false
)

class GroupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupRepository(AppDatabase.getInstance(app).groupDao())

    private val _state = MutableStateFlow(GroupUiState())
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.allGroups().collect { groups ->
                _state.update { it.copy(groups = groups) }
            }
        }
    }

    fun showAddDialog() = _state.update { it.copy(showAddDialog = true, showEditDialog = false, editing = null) }
    fun showEditDialog(group: GroupEntity) = _state.update { it.copy(showEditDialog = true, showAddDialog = false, editing = group) }
    fun dismissDialog() = _state.update { it.copy(showAddDialog = false, showEditDialog = false, editing = null) }

    fun addGroup(name: String, color: String) {
        val order = (_state.value.groups.maxOfOrNull { it.order } ?: 0) + 1
        viewModelScope.launch {
            repo.insert(GroupEntity(name = name, order = order, color = color))
            dismissDialog()
        }
    }

    fun updateGroup(group: GroupEntity, name: String, color: String) {
        viewModelScope.launch {
            repo.update(group.copy(name = name, color = color))
            dismissDialog()
        }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch { repo.delete(group) }
    }
}
