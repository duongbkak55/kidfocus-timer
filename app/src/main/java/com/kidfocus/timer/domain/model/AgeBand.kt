package com.kidfocus.timer.domain.model

import androidx.annotation.StringRes
import com.kidfocus.timer.R

/** Shared wire IDs and display order for child profiles, learning, and schedule requests. */
enum class AgeBand(
    val id: String,
    @StringRes val labelRes: Int,
    val minimumSleepMinutes: Int,
    val maximumFocusMinutes: Int,
) {
    AGE_2_3("2-3", R.string.profile_age_2_3, 660, 15),
    AGE_4_5("4-5", R.string.profile_age_4_5, 600, 20),
    AGE_5_6("5-6", R.string.profile_age_5_6, 600, 20),
    GRADE_1("l1", R.string.profile_grade_1, 540, 30),
    GRADE_2("l2", R.string.profile_grade_2, 540, 40),
    GRADE_3("l3", R.string.profile_grade_3, 540, 40);

    companion object {
        const val DEFAULT_ID = "4-5"
        val orderedIds: List<String> = entries.map { it.id }
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String?): AgeBand? = byId[id]
        fun isValid(id: String?): Boolean = id in byId
        fun normalize(id: String?): String = fromId(id)?.id ?: DEFAULT_ID
    }
}
