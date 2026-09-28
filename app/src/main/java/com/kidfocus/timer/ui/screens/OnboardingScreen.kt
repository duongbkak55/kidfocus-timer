package com.kidfocus.timer.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.R
import com.kidfocus.timer.ui.theme.KidFocusTheme

private data class OnboardingPage(
    val emoji: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "🦉",
        title = R.string.onboarding_welcome_title,
        description = R.string.onboarding_welcome_desc,
    ),
    OnboardingPage(
        emoji = "⏱️",
        title = R.string.onboarding_pomodoro_title,
        description = R.string.onboarding_pomodoro_desc,
    ),
    OnboardingPage(
        emoji = "🎉",
        title = R.string.onboarding_reward_title,
        description = R.string.onboarding_reward_desc,
    ),
    OnboardingPage(
        emoji = "🔒",
        title = R.string.onboarding_parent_title,
        description = R.string.onboarding_parent_desc,
    ),
)

/**
 * Four-page onboarding flow shown to first-time users.
 * Calls [onFinished] when the user reaches the last page and taps "Bắt đầu".
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val colors = KidFocusTheme.colors
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    val isLast = pageIndex == onboardingPages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                label = "onboarding_page",
            ) { index ->
                val current = onboardingPages[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = current.emoji, fontSize = 88.sp)

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = stringResource(current.title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(current.description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp),
                ) {
                    onboardingPages.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == pageIndex) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (i == pageIndex) colors.primary
                                    else colors.primary.copy(alpha = 0.25f)
                                ),
                        )
                    }
                }

                // Next / Start button
                Button(
                    onClick = {
                        if (isLast) onFinished()
                        else pageIndex++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = stringResource(if (isLast) R.string.onboarding_get_started else R.string.next),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (!isLast) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onFinished,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.skip),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onBackground.copy(alpha = 0.5f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
