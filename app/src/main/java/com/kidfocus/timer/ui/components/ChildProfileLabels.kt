package com.kidfocus.timer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kidfocus.timer.R
import com.kidfocus.timer.data.database.ChildProfileEntity

@Composable
fun childProfileName(profile: ChildProfileEntity): String =
    if (profile.id == ChildProfileEntity.DEFAULT_ID && profile.name == "Bé") {
        stringResource(R.string.profile_default_name)
    } else {
        profile.name
    }
