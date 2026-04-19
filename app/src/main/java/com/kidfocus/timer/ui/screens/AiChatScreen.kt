package com.kidfocus.timer.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kidfocus.timer.domain.model.ChatMessage
import com.kidfocus.timer.ui.theme.KidFocusColors
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.AiChatViewModel

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val colors = KidFocusTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isLoading) {
        val targetIndex = if (isLoading) messages.size else (messages.size - 1).coerceAtLeast(0)
        if (messages.isNotEmpty()) listState.animateScrollToItem(targetIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = colors.primary)
            }
            Text(
                text = "🦉 Cú học",
                style = MaterialTheme.typography.titleLarge,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(messages) { msg -> ChatBubble(msg = msg, colors = colors) }
            if (isLoading) {
                item { TypingIndicator(colors = colors) }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        ChatInput(
            isLoading = isLoading,
            colors = colors,
            onSend = { viewModel.sendMessage(it) },
        )
    }
}

@Composable
private fun NoApiKeyPrompt(colors: KidFocusColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(32.dp),
            elevation = CardDefaults.cardElevation(4.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🦉", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Cú học chưa sẵn sàng",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Phụ huynh cần cài đặt Gemini API Key trước.\n\nVào: ⚙️ Cài đặt → Gemini AI → nhập API key",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Lấy API key miễn phí tại aistudio.google.com",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = colors.primary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, colors: KidFocusColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!msg.isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🦉", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = if (msg.isUser) 16.dp else 4.dp,
                topEnd = if (msg.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = if (msg.isUser) colors.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (msg.isUser) Color.White else colors.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun TypingIndicator(colors: KidFocusColors) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "typing_alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🦉", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "Đang suy nghĩ...",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = colors.onBackground.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChatInput(isLoading: Boolean, colors: KidFocusColors, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Hỏi Cú học bất kỳ điều gì...") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend(text); text = "" }),
            enabled = !isLoading,
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = { onSend(text); text = "" },
            enabled = text.isNotBlank() && !isLoading,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (text.isNotBlank() && !isLoading) colors.primary else colors.primary.copy(alpha = 0.3f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = Color.White)
        }
    }
}
