package com.kidfocus.timer.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kidfocus.timer.R
import com.kidfocus.timer.data.billing.StorePlan
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SubscriptionViewModel

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val colors = KidFocusTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val account by viewModel.account.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.premium_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("🌟", style = MaterialTheme.typography.displayMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            if (state.premium) stringResource(R.string.premium_active) else stringResource(R.string.premium_headline),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        listOf(
            R.string.premium_benefit_models,
            R.string.premium_benefit_limit,
            R.string.premium_benefit_sync,
        ).forEach {
            Text("✓  ${stringResource(it)}", color = colors.onBackground, modifier = Modifier.padding(vertical = 5.dp))
        }
        Spacer(Modifier.height(20.dp))

        if (!account.isSignedIn) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.primary.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.premium_login_required), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.premium_login_button))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.premium) {
            Text(
                stringResource(R.string.premium_active_description),
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.plans.isNotEmpty()) {
            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    enabled = account.isSignedIn && !state.loading,
                    onPurchase = {
                        val activity = context as? Activity ?: return@PlanCard
                        viewModel.purchase(activity, plan.identifier)
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        } else if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text(
                state.message ?: stringResource(R.string.premium_store_unavailable),
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.premium_retry))
            }
        }

        state.message?.takeIf { state.plans.isNotEmpty() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = viewModel::restore,
            enabled = account.isSignedIn && !state.loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(R.string.premium_restore)) }
        Text(
            stringResource(R.string.premium_renewal_notice),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { context.openUrl(PRIVACY_URL) }) { Text(stringResource(R.string.premium_privacy)) }
            TextButton(onClick = { context.openUrl(TERMS_URL) }) { Text(stringResource(R.string.premium_terms)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(plan: StorePlan, enabled: Boolean, onPurchase: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (plan.isAnnual) 4.dp else 1.dp,
        color = if (plan.isAnnual) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(18.dp)) {
            if (plan.isAnnual) {
                Text(stringResource(R.string.premium_best_value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(plan.price, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onPurchase, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.premium_subscribe))
            }
        }
    }
}

private fun android.content.Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private const val PRIVACY_URL = "https://duongbkak55.github.io/kidfocus-timer/privacy-policy.html"
private const val TERMS_URL = "https://duongbkak55.github.io/kidfocus-timer/terms.html"
