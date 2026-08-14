package com.kidfocus.timer.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kidfocus.timer.BuildConfig
import com.kidfocus.timer.R
import com.kidfocus.timer.data.repository.LearningProgress
import com.kidfocus.timer.data.repository.LearningResult
import com.kidfocus.timer.ui.learning.LearningTtsController
import com.kidfocus.timer.ui.viewmodel.LearningNativeConfig
import com.kidfocus.timer.ui.viewmodel.LearningViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private const val LEARNING_ORIGIN = "https://appassets.androidplatform.net"
private const val LEARNING_URL = "$LEARNING_ORIGIN/assets/index.html?embedded=1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningHubScreen(
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val completed by viewModel.totalCompleted.collectAsState()
    var currentScreen by remember { mutableStateOf("home") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val tts = remember { LearningTtsController(context.applicationContext) }

    fun handleBack() {
        if (currentScreen == "home") {
            onBack()
        } else {
            tts.stop()
            webView?.evaluateJavascript(
                "if(window.KidFocusHost){window.KidFocusHost.handleBack();}",
                null,
            )
            currentScreen = "home"
        }
    }

    BackHandler(onBack = ::handleBack)
    DisposableEffect(Unit) {
        onDispose {
            tts.close()
            webView?.stopLoading()
            webView?.destroy()
        }
    }
    LaunchedEffect(webView, config) {
        webView?.applyLearningConfig(config)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.learning_title))
                        if (completed > 0) {
                            Text(
                                stringResource(R.string.learning_completed_count, completed),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProgress) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = stringResource(R.string.learning_open_progress),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (LocalInspectionMode.current) return@Scaffold
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = {
                createLearningWebView(
                    context = context,
                    tts = tts,
                    onScreenChanged = { currentScreen = it },
                    onResult = viewModel::recordResult,
                    onAgeBandChanged = viewModel::setAgeBand,
                    onReady = { view -> view.applyLearningConfig(config) },
                ).also { webView = it }
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createLearningWebView(
    context: android.content.Context,
    tts: LearningTtsController,
    onScreenChanged: (String) -> Unit,
    onResult: (LearningResult) -> Unit,
    onAgeBandChanged: (String) -> Unit,
    onReady: (WebView) -> Unit,
): WebView {
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    val webView = WebView(context)
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    CookieManager.getInstance().setAcceptCookie(false)
    webView.setBackgroundColor(Color.TRANSPARENT)
    webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    webView.contentDescription = context.getString(R.string.learning_web_content_description)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        builtInZoomControls = false
        displayZoomControls = false
    }
    webView.webViewClient = object : WebViewClientCompat() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !request.url.isTrustedLearningUrl()

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            onReady(view)
        }
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            webView,
            "KidFocusNative",
            setOf(LEARNING_ORIGIN),
        ) { view, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame || !sourceOrigin.isTrustedLearningUrl()) return@addWebMessageListener
            handleLearningMessage(
                view = view,
                message = message,
                tts = tts,
                onScreenChanged = onScreenChanged,
                onResult = onResult,
                onAgeBandChanged = onAgeBandChanged,
                onReady = onReady,
            )
        }
    }
    webView.loadUrl(LEARNING_URL)
    return webView
}

private fun handleLearningMessage(
    view: WebView,
    message: WebMessageCompat,
    tts: LearningTtsController,
    onScreenChanged: (String) -> Unit,
    onResult: (LearningResult) -> Unit,
    onAgeBandChanged: (String) -> Unit,
    onReady: (WebView) -> Unit,
) {
    val rawMessage = message.data ?: return
    val root = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return
    val payload = root.optJSONObject("payload") ?: JSONObject()
    when (root.optString("type")) {
        "ready" -> onReady(view)
        "screenChanged" -> onScreenChanged(payload.optString("screenId", "home").take(48))
        "profileChanged" -> onAgeBandChanged(payload.optString("ageBand"))
        "learningResult" -> {
            val total = payload.nullableInt("totalQuestions")
            onResult(
                LearningResult(
                    gameId = payload.optString("gameId", "unknown"),
                    ageBand = payload.optString("ageBand", "4-5"),
                    score = payload.nullableInt("score"),
                    totalQuestions = total,
                    durationMillis = payload.optLong("durationMillis", 0L),
                    completed = payload.optBoolean("completed", true),
                ),
            )
        }
        "speak" -> tts.speak(
            text = payload.optString("text"),
            languageTag = payload.optString("lang", "vi-VN"),
            fallbackText = payload.optString("fallbackText").takeIf(String::isNotBlank),
            fallbackLanguageTag = payload.optString("fallbackLang", "en-US"),
            rate = payload.optDouble("rate", 0.85).toFloat(),
            pitch = payload.optDouble("pitch", 1.2).toFloat(),
        )
        "stopSpeech" -> tts.stop()
        "installVoice" -> tts.openVoiceInstaller()
    }
}

private fun JSONObject.nullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun Uri.isTrustedLearningUrl(): Boolean =
    scheme == "https" && host == "appassets.androidplatform.net"

private fun WebView.applyLearningConfig(config: LearningNativeConfig) {
    val json = JSONObject()
        .put("ageBand", config.ageBand)
        .put("soundEnabled", config.soundEnabled)
        .put("premium", config.premium)
        .put("signedIn", config.signedIn)
        .put("locale", Locale.getDefault().toLanguageTag())
    evaluateJavascript(
        "if(window.KidFocusHost){window.KidFocusHost.applyConfig(${json});}",
        null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningProgressScreen(
    onBack: () -> Unit,
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsState()
    val config by viewModel.config.collectAsState()
    val totalAttempts = progress.sumOf { it.attempts }
    val totalCorrect = progress.sumOf { it.correctAnswers }
    val totalQuestions = progress.sumOf { it.totalQuestions }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.learning_progress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.learning_overview),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressMetric(
                        value = totalAttempts.toString(),
                        label = stringResource(R.string.learning_attempts),
                        modifier = Modifier.weight(1f),
                    )
                    ProgressMetric(
                        value = if (totalQuestions > 0) "${totalCorrect * 100 / totalQuestions}%" else "—",
                        label = stringResource(R.string.learning_accuracy),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (config.signedIn) stringResource(R.string.learning_sync_on)
                    else stringResource(R.string.learning_sync_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (progress.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.learning_progress_empty),
                        modifier = Modifier.padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                items(progress, key = LearningProgress::gameId) { item ->
                    LearningProgressCard(item)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProgressMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LearningProgressCard(progress: LearningProgress) {
    val percent = if (progress.totalQuestions > 0) {
        progress.correctAnswers.toFloat() / progress.totalQuestions
    } else null
    val label = learningGameLabel(progress.gameId)
    val spokenSummary = buildString {
        append(label)
        append(", ${progress.attempts} ")
        append(stringResource(R.string.learning_attempts).lowercase())
        progress.bestPercent?.let { append(", ${stringResource(R.string.learning_best)} $it%") }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = spokenSummary },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    progress.bestPercent?.let { stringResource(R.string.learning_best_value, it) } ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent?.coerceIn(0f, 1f) ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.learning_progress_detail,
                    progress.attempts,
                    progress.correctAnswers,
                    progress.totalQuestions,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(
                    R.string.learning_last_played,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(progress.lastPlayedAtMillis)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun learningGameLabel(gameId: String): String {
    val english = Locale.getDefault().language == "en"
    val labels = if (english) GAME_LABELS_EN else GAME_LABELS_VI
    return labels[gameId] ?: gameId.replaceFirstChar { it.uppercase() }
}

private val GAME_LABELS_VI = mapOf(
    "add" to "Phép cộng", "sub" to "Phép trừ", "beaf" to "Số liền trước và sau",
    "cmp" to "So sánh số", "cnt" to "Đếm số", "color" to "Màu sắc",
    "shape" to "Hình dạng", "size" to "Lớn và nhỏ", "place" to "Chục và đơn vị",
    "skip" to "Đếm cách đều", "groups" to "Nhóm bằng nhau", "sudoku" to "Sudoku 4×4",
    "maze" to "Mê cung", "mult" to "Phép nhân", "divi" to "Phép chia",
    "place100" to "Giá trị hàng", "pattern" to "Quy luật", "sort" to "Phân loại",
    "science" to "Khám phá khoa học", "seq" to "Số còn thiếu", "bond" to "Tách gộp số",
    "wrd" to "Từ tiếng Anh", "clock" to "Xem giờ", "money" to "Tiền Việt Nam",
    "memory" to "Ghép cặp trí nhớ", "rhythm" to "Nhịp điệu", "feelings" to "Cảm xúc",
    "exam" to "Đề luyện tập", "story" to "Đọc hiểu truyện",
)

private val GAME_LABELS_EN = mapOf(
    "add" to "Addition", "sub" to "Subtraction", "beaf" to "Before and after",
    "cmp" to "Compare numbers", "cnt" to "Counting", "color" to "Colors",
    "shape" to "Shapes", "size" to "Big and small", "place" to "Tens and ones",
    "skip" to "Skip counting", "groups" to "Equal groups", "sudoku" to "4×4 Sudoku",
    "maze" to "Maze", "mult" to "Multiplication", "divi" to "Division",
    "place100" to "Place value", "pattern" to "Patterns", "sort" to "Sorting",
    "science" to "Science", "seq" to "Missing number", "bond" to "Number bonds",
    "wrd" to "English words", "clock" to "Telling time", "money" to "Vietnamese money",
    "memory" to "Memory match", "rhythm" to "Rhythm", "feelings" to "Feelings",
    "exam" to "Practice exam", "story" to "Reading comprehension",
)
