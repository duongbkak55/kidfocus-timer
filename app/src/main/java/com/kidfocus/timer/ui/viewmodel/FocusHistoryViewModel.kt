package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.RoutineRepository
import com.kidfocus.timer.data.repository.SessionRepository
import com.kidfocus.timer.domain.model.FocusHistory
import com.kidfocus.timer.domain.model.buildFocusHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FocusHistoryViewModel @Inject constructor(
    private val sessions: SessionRepository,
    routines: RoutineRepository,
    private val profiles: ChildProfileRepository,
) : ViewModel() {
    val history: StateFlow<FocusHistory?> = combine(
        sessions.getSessionsSince(0),
        routines.observeAll(),
        routines.observeAllCompletions(),
        profiles.activeProfileId,
    ) { sessionRows, routineRows, completions, profileId ->
        buildFocusHistory(sessionRows, routineRows, completions, profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteOne(id: Long) = viewModelScope.launch {
        sessions.deleteForProfile(id, profiles.currentProfileId())
    }

    fun deleteAll() = viewModelScope.launch {
        sessions.deleteAllForProfile(profiles.currentProfileId())
    }
}
