package com.pisces312.milocal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pisces312.milocal.data.db.AppDatabase
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.data.repository.DeviceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AddDeviceState(
    val name: String = "",
    val ip: String = "",
    val token: String = "",
    val model: String = "",
    val type: String = "generic",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class AddDeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DeviceRepository(AppDatabase.getInstance(app).deviceDao())

    private val _state = MutableStateFlow(AddDeviceState())
    val state: StateFlow<AddDeviceState> = _state.asStateFlow()

    fun updateName(name: String) = _state.update { it.copy(name = name) }
    fun updateIp(ip: String) = _state.update { it.copy(ip = ip) }
    fun updateToken(token: String) = _state.update { it.copy(token = token) }
    fun updateModel(model: String) = _state.update { it.copy(model = model) }
    fun updateType(type: String) = _state.update { it.copy(type = type) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank() || s.ip.isBlank() || s.token.isBlank()) {
            _state.update { it.copy(error = "名称、IP、Token 不能为空") }
            return
        }
        if (s.token.length != 32) {
            _state.update { it.copy(error = "Token 必须是32位十六进制") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                repo.insert(
                    DeviceEntity(
                        name = s.name,
                        ip = s.ip,
                        token = s.token,
                        model = s.model,
                        type = s.type
                    )
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
