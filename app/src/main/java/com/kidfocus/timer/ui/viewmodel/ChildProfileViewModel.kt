package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.repository.ChildProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChildProfileViewModel @Inject constructor(
    private val repository: ChildProfileRepository,
) : ViewModel() {
    val profiles: StateFlow<List<ChildProfileEntity>> = repository.profiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val activeProfile: StateFlow<ChildProfileEntity> = repository.activeProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChildProfileEntity.default(),
    )

    init {
        viewModelScope.launch { repository.ensureDefaultProfile() }
    }

    fun select(profileId: String, onSelected: () -> Unit = {}) {
        viewModelScope.launch {
            repository.select(profileId)
            onSelected()
        }
    }

    fun save(
        id: String?,
        name: String,
        avatarEmoji: String,
        ageBand: String,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val saved = repository.save(id, name, avatarEmoji, ageBand)
            repository.select(saved.id)
            onSaved()
        }
    }

    fun archive(profile: ChildProfileEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.archive(profile)) }
    }
}
