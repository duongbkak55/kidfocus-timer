package com.kidfocus.timer.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.data.cloud.CloudSyncStatus
import com.kidfocus.timer.ui.components.AccountAvatar
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.CloudSyncViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudSyncScreen(
    viewModel: CloudSyncViewModel,
    onBack: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val context = LocalContext.current
    val account by viewModel.account.collectAsState()
    val status by viewModel.syncStatus.collectAsState()
    val message by viewModel.formMessage.collectAsState()
    val working by viewModel.working.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!working) confirmDelete = false },
            title = { Text("Xóa tài khoản?") },
            text = {
                Text("Tài khoản, bản sao cloud và tiến độ đã đồng bộ sẽ bị xóa vĩnh viễn. Gói mua qua cửa hàng cần được hủy riêng trong Google Play hoặc App Store.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteAccount()
                    },
                    enabled = !working,
                ) { Text("Xóa vĩnh viễn", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !working) {
                    Text("Hủy")
                }
            },
        )
    }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = colors.onBackground)
            }
            Text(
                "Đồng bộ dữ liệu",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "☁️ Dùng cùng dữ liệu trên nhiều thiết bị",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Lịch học, lịch sinh hoạt, lịch sử tập trung, tiến độ trò chơi và tùy chọn sẽ tự đồng bộ. PIN phụ huynh và khóa dịch vụ AI không được đưa lên cloud.",
            color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Không đăng nhập: dữ liệu chỉ được lưu offline trên thiết bị này.",
            color = colors.onBackground,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(24.dp))

        when {
            !account.configured -> {
                Text(
                    "Firebase chưa được cấu hình cho bản cài này. Thêm 3 giá trị Firebase vào local.properties rồi cài lại ứng dụng.",
                    color = colors.onBackground,
                )
            }
            account.isSignedIn -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountAvatar(
                                account = account,
                                size = 72.dp,
                                showOnlineDot = true,
                            )
                            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(
                                    account.displayName ?: account.email?.substringBefore('@') ?: "Tài khoản phụ huynh",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                account.email?.let {
                                    Text(it, color = colors.onBackground.copy(alpha = 0.7f))
                                }
                                Text(
                                    "Đăng nhập bằng ${account.providerLabel ?: "Firebase"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF22C55E).copy(alpha = 0.12f),
                        ) {
                            Text(
                                "●  Đã đăng nhập • Đồng bộ cloud đang bật",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = androidx.compose.ui.graphics.Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SyncStatusText(status)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::syncNow,
                    enabled = status !is CloudSyncStatus.Syncing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (status is CloudSyncStatus.Syncing) {
                        CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                    } else Text("Đồng bộ ngay")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::signOut,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Đăng xuất") }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Xóa tài khoản và dữ liệu cloud", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                Button(
                    onClick = { viewModel.signInWithGoogle(context) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("G  Đăng nhập bằng Google", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Hoặc đăng nhập bằng email",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = colors.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email phụ huynh") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.signIn(email, password) },
                        enabled = !working,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Đăng nhập") }
                    OutlinedButton(
                        onClick = { viewModel.createAccount(email, password) },
                        enabled = !working,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Tạo tài khoản") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetPassword(email) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quên mật khẩu") }
                if (working) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = colors.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SyncStatusText(status: CloudSyncStatus) {
    val colors = KidFocusTheme.colors
    val text = when (status) {
        CloudSyncStatus.LocalOnly -> "Chỉ lưu trên máy"
        CloudSyncStatus.SignedOut -> "Chưa đăng nhập"
        CloudSyncStatus.Syncing -> "Đang đồng bộ…"
        is CloudSyncStatus.Synced -> "Đã đồng bộ lúc ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.atMillis))}"
        is CloudSyncStatus.Error -> "Chưa đồng bộ được: ${status.message}"
    }
    Text(text, color = colors.onBackground, fontWeight = FontWeight.Medium)
}
