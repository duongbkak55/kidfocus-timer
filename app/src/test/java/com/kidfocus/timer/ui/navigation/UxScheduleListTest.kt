package com.kidfocus.timer.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.*
import com.kidfocus.timer.ui.screens.ScheduleScreen
import com.kidfocus.timer.ui.screens.TaskEditScreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ParentNavigationTestApplication::class, qualifiers = "en-w411dp-h891dp")
class UxScheduleListTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val first = ScheduledTask(11, TaskType.HOMEWORK, "Math practice", "📝", 7, 0, setOf(2), 20, 5)
    private val second = first.copy(id = 12, name = "Writing practice", hour = 16, daysOfWeek = setOf(4), enabled = false)
    private val viewModel = mockk<ScheduleViewModel>(relaxed = true)
    private fun show() {
        every { viewModel.tasks } returns MutableStateFlow(listOf(first, second))
        compose.setContent {
            var editing by remember { mutableStateOf<ScheduledTask?>(null) }
            KidFocusTheme {
                if (editing == null) ScheduleScreen(viewModel, {}, { editing = it }, {})
                else TaskEditScreen(editing!!, viewModel, onBack = { editing = null })
            }
        }
    }
    @Test fun everySameTypeActivityHasItsOwnEditAndToggleAndSaveTargetsExactId() {
        show()
        compose.onNodeWithTag("toggle_task_12").performScrollTo().performClick()
        verify(exactly = 1) { viewModel.toggleEnabled(second) }
        compose.onNodeWithTag("edit_task_11").performScrollTo().assertExists()
        compose.onNodeWithTag("edit_task_12").performScrollTo().performClick()
        compose.onNodeWithTag("task_edit_name").performTextReplacement("Writing revised")
        compose.onNodeWithText(compose.activity.getString(R.string.schedule_save)).performScrollTo().performClick()
        verify(exactly = 1) { viewModel.saveTask(match { it.id == 12L && it.name == "Writing revised" && !it.enabled }) }
        verify(exactly = 0) { viewModel.saveTask(match { it.id == 11L }) }
    }
    @Test fun secondSameTypeActivityCanBeDeletedWithoutDeletingFirst() {
        show()
        compose.onNodeWithTag("edit_task_12").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.schedule_delete)).performScrollTo().performClick()
        verify(exactly = 1) { viewModel.deleteTask(second) }
        verify(exactly = 0) { viewModel.deleteTask(first) }
    }
}
