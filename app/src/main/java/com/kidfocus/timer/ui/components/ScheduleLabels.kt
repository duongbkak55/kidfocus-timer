package com.kidfocus.timer.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.TaskCategory
import com.kidfocus.timer.domain.model.TaskType
import java.util.Calendar

@Composable
fun taskTypeLabel(type: TaskType): String = stringResource(type.labelResource())

@Composable
fun taskCategoryLabel(category: TaskCategory): String = stringResource(category.labelResource())

@Composable
fun scheduleDaysLabel(days: Set<Int>): String = when (days) {
    TaskType.ALL_DAYS -> stringResource(R.string.repeat_every_day)
    TaskType.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    TaskType.WEEKEND -> stringResource(R.string.repeat_weekend)
    else -> {
        val labels = mapOf(
            Calendar.MONDAY to stringResource(R.string.weekday_monday_short),
            Calendar.TUESDAY to stringResource(R.string.weekday_tuesday_short),
            Calendar.WEDNESDAY to stringResource(R.string.weekday_wednesday_short),
            Calendar.THURSDAY to stringResource(R.string.weekday_thursday_short),
            Calendar.FRIDAY to stringResource(R.string.weekday_friday_short),
            Calendar.SATURDAY to stringResource(R.string.weekday_saturday_short),
            Calendar.SUNDAY to stringResource(R.string.weekday_sunday_short),
        )
        listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY,
        ).filter(days::contains).joinToString(", ") { labels.getValue(it) }
    }
}

@StringRes
private fun TaskCategory.labelResource(): Int = when (this) {
    TaskCategory.STUDY -> R.string.category_study
    TaskCategory.HYGIENE -> R.string.category_hygiene
    TaskCategory.CHORES -> R.string.category_chores
    TaskCategory.ENTERTAINMENT -> R.string.category_entertainment
}

@StringRes
private fun TaskType.labelResource(): Int = when (this) {
    TaskType.MORNING_STUDY -> R.string.task_morning_study
    TaskType.AFTERNOON_STUDY -> R.string.task_afternoon_study
    TaskType.HOMEWORK -> R.string.task_homework
    TaskType.READING -> R.string.task_reading
    TaskType.WEEKEND_STUDY -> R.string.task_weekend_study
    TaskType.MUSIC_PRACTICE -> R.string.task_music_practice
    TaskType.LEARNING_GAMES -> R.string.task_learning_games
    TaskType.BATH -> R.string.task_bath
    TaskType.BRUSH_TEETH -> R.string.task_brush_teeth
    TaskType.EXERCISE -> R.string.task_exercise
    TaskType.SLEEP -> R.string.task_sleep
    TaskType.MAKE_BED -> R.string.task_make_bed
    TaskType.CLEAN_ROOM -> R.string.task_clean_room
    TaskType.WASH_DISHES -> R.string.task_wash_dishes
    TaskType.BREAKFAST -> R.string.task_breakfast
    TaskType.LUNCH -> R.string.task_lunch
    TaskType.DINNER -> R.string.task_dinner
    TaskType.GAME_TIME -> R.string.task_game_time
    TaskType.TV_TIME -> R.string.task_tv_time
    TaskType.OUTDOOR_PLAY -> R.string.task_outdoor_play
    TaskType.ART -> R.string.task_art
    TaskType.CUSTOM -> R.string.task_custom
}
