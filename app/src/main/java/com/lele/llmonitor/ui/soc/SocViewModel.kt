package com.lele.llmonitor.ui.soc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lele.llmonitor.data.soc.SocRepository
import com.lele.llmonitor.data.soc.SocSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocUiState(
    val isLoading: Boolean = true,
    val snapshot: SocSnapshot? = null,
    val error: String? = null
)

class SocViewModel(
    private val repository: SocRepository = SocRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SocUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.stream()
                .conflate()
                .catch { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message ?: com.lele.llmonitor.i18n.l10n("采集失败"))
                    }
                }
                .collect { snapshot ->
                    _uiState.value = SocUiState(
                        isLoading = false,
                        snapshot = snapshot,
                        error = null
                    )
                }
        }
    }
}
