package com.example.kakaomiddleware

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.app.AlarmManager
import android.os.Build
import android.net.Uri
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import com.example.kakaomiddleware.ui.theme.KakaoMiddlewareTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var allowlistManager: AllowlistManager
    private lateinit var serverConfigManager: ServerConfigManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowlistManager = AllowlistManager.getInstance(this)
        serverConfigManager = ServerConfigManager.getInstance(this)
        enableEdgeToEdge()
        setContent {
            KakaoMiddlewareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        allowlistManager = allowlistManager,
                        serverConfigManager = serverConfigManager,
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    allowlistManager: AllowlistManager,
    serverConfigManager: ServerConfigManager,
    onOpenSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Messages", "Allowlist", "Chat", "Settings", "Alarm")
    
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        when (selectedTab) {
            0 -> KakaoMessageLogger(
                modifier = Modifier.fillMaxSize(),
                onOpenSettings = onOpenSettings
            )
            1 -> AllowlistScreen(
                modifier = Modifier.fillMaxSize(),
                allowlistManager = allowlistManager
            )
            2 -> ChatManagementScreen(
                modifier = Modifier.fillMaxSize()
            )
            3 -> ServerSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                serverConfigManager = serverConfigManager
            )
            4 -> AlarmTestScreen(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun KakaoMessageLogger(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    var notifications by remember { mutableStateOf(emptyList<KakaoNotification>()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            notifications = KakaoNotificationListenerService.notificationLog.toList()
            delay(1000)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "KakaoTalk Message Logger",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Enable Notification Access")
        }
        
        if (notifications.isEmpty()) {
            Text(
                text = "No notifications logged yet. Make sure notification access is enabled and use KakaoTalk.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Text(
                text = "Notifications (${notifications.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn {
                items(notifications.reversed()) { notification ->
                    NotificationItem(notification = notification)
                }
            }
        }
    }
}


@Composable
fun NotificationItem(notification: KakaoNotification) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    when (notification) {
                        is ImageMessage -> {
                            if (notification.groupName != null) {
                                Text(
                                    text = notification.groupName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = notification.sender,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = notification.sender,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is GroupMessage -> {
                            Text(
                                text = notification.groupName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = notification.sender,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is PersonalMessage -> {
                            Text(
                                text = notification.sender,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is UnreadSummary -> {
                            Text(
                                text = "📱 Unread Summary",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Text(
                    text = notification.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (notification) {
                is ImageMessage -> {
                    notification.imageBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Shared image",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(200.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } ?: Text(
                        text = "📷 Image (failed to load)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is GroupMessage -> {
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is PersonalMessage -> {
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is UnreadSummary -> {
                    Text(
                        text = notification.unreadInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AllowlistScreen(
    modifier: Modifier = Modifier,
    allowlistManager: AllowlistManager
) {
    val personalAllowlist by allowlistManager.personalAllowlist.collectAsState()
    val groupAllowlist by allowlistManager.groupAllowlist.collectAsState()
    val turboMode by allowlistManager.turboMode.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Reply Allowlist",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = if (turboMode) {
                "🚀 TURBO MODE: All messages are sent to server regardless of allowlist."
            } else {
                "Only messages from these contacts/groups will be sent to the server for AI replies."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (turboMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (turboMode) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (turboMode) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turbo Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (turboMode) {
                            "All messages are processed by the server"
                        } else {
                            "Only allowlisted messages are processed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = turboMode,
                    onCheckedChange = { allowlistManager.setTurboMode(it) }
                )
            }
        }
        
        AllowlistSection(
            title = "Personal Contacts",
            items = personalAllowlist,
            onAddItem = { allowlistManager.addPersonalContact(it) },
            onRemoveItem = { allowlistManager.removePersonalContact(it) },
            placeholder = "Enter contact name"
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        AllowlistSection(
            title = "Group Chats",
            items = groupAllowlist,
            onAddItem = { allowlistManager.addGroupName(it) },
            onRemoveItem = { allowlistManager.removeGroupName(it) },
            placeholder = "Enter group name"
        )
    }
}

@Composable
fun AllowlistSection(
    title: String,
    items: Set<String>,
    onAddItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    placeholder: String
) {
    var newItemText by remember { mutableStateOf("") }
    
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newItemText.isNotBlank()) {
                            onAddItem(newItemText)
                            newItemText = ""
                        }
                    }
                ),
                singleLine = true
            )
            
            IconButton(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        onAddItem(newItemText)
                        newItemText = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (items.isEmpty()) {
            Text(
                text = "No items added yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn {
                items(items.toList()) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveItem(item) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerSettingsScreen(
    modifier: Modifier = Modifier,
    serverConfigManager: ServerConfigManager
) {
    val customServerUrl by serverConfigManager.customServerUrl.collectAsState()
    val useCustomServer by serverConfigManager.useCustomServer.collectAsState()
    val currentEndpoint by serverConfigManager.currentEndpoint.collectAsState()
    
    // serverType도 반응형으로 계산
    val serverType = remember(currentEndpoint) {
        when {
            currentEndpoint.contains("localhost") || currentEndpoint.contains("192.168") || currentEndpoint.contains("10.0.2.2") -> "🏠 LOCAL SERVER"
            currentEndpoint.contains("vercel.app") -> "☁️ PRODUCTION SERVER"
            else -> "🔧 CUSTOM SERVER"
        }
    }
    
    var editingUrl by remember { mutableStateOf("") }
    var showUrlEditor by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf("") }
    
    // 에디팅 모드가 시작될 때 현재 URL로 초기화
    LaunchedEffect(showUrlEditor) {
        if (showUrlEditor) {
            editingUrl = customServerUrl
            urlError = ""
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Server Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 현재 서버 상태
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Server Status",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Current Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = serverType,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = currentEndpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 커스텀 서버 사용 토글
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Custom Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (useCustomServer) {
                            "Using custom server configuration"
                        } else {
                            "Using default build configuration"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useCustomServer,
                    onCheckedChange = { newValue ->
                        println("Switch onCheckedChange: $useCustomServer -> $newValue")
                        serverConfigManager.setUseCustomServer(newValue)
                    }
                )
            }
        }
        
        // 커스텀 서버 설정 (useCustomServer가 true일 때만 표시)
        if (useCustomServer) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Custom Server Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    if (!showUrlEditor) {
                        // URL 표시 모드
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Server URL",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = customServerUrl,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Button(
                                onClick = { showUrlEditor = true }
                            ) {
                                Text("Edit")
                            }
                        }
                    } else {
                        // URL 편집 모드
                        OutlinedTextField(
                            value = editingUrl,
                            onValueChange = { 
                                editingUrl = it
                                urlError = if (serverConfigManager.isValidUrl(it.trim())) "" else "Invalid URL format"
                            },
                            label = { Text("Server URL") },
                            placeholder = { Text("http://192.168.1.100:3000") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            singleLine = true,
                            isError = urlError.isNotEmpty(),
                            supportingText = if (urlError.isNotEmpty()) {
                                { Text(urlError, color = MaterialTheme.colorScheme.error) }
                            } else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (urlError.isEmpty() && editingUrl.trim().isNotEmpty()) {
                                        serverConfigManager.setCustomServerUrl(editingUrl.trim())
                                        showUrlEditor = false
                                    }
                                }
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (urlError.isEmpty() && editingUrl.trim().isNotEmpty()) {
                                        serverConfigManager.setCustomServerUrl(editingUrl.trim())
                                        showUrlEditor = false
                                    }
                                },
                                enabled = urlError.isEmpty() && editingUrl.trim().isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                            
                            OutlinedButton(
                                onClick = { showUrlEditor = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                    
                    // 프리셋 URL 버튼들
                    if (!showUrlEditor) {
                        Text(
                            text = "Quick Presets",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        
                        serverConfigManager.getPresetUrls().forEach { presetUrl ->
                            val isCurrentUrl = presetUrl == customServerUrl
                            FilledTonalButton(
                                onClick = { 
                                    println("🔘 Preset button clicked: $presetUrl")
                                    println("   Current URL: $customServerUrl")
                                    println("   Is current: $isCurrentUrl")
                                    if (!isCurrentUrl) {
                                        println("   Calling setCustomServerUrl...")
                                        serverConfigManager.setCustomServerUrl(presetUrl)
                                    } else {
                                        println("   Skipped (same URL)")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = if (isCurrentUrl) {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                }
                            ) {
                                Text(
                                    text = presetUrl,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (isCurrentUrl) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Current",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmTestScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAlarmActive by remember { mutableStateOf(AlarmReceiver.isAlarmActive(context)) }
    
    // 정확한 알람 권한 확인 (Android 12+)
    val canScheduleExact = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Alarm Test",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "10분 간격 알람 테스트 (00분, 10분, 20분, 30분, 40분, 50분)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 알람 상태 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAlarmActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Alarm Status",
                            modifier = Modifier.padding(end = 8.dp),
                            tint = if (isAlarmActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "Alarm Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isAlarmActive) {
                            "활성화됨 - 10분마다 로그가 출력됩니다"
                        } else {
                            "비활성화됨"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // 알람 시작 버튼
        Button(
            onClick = {
                AlarmReceiver.startPeriodicAlarm(context)
                isAlarmActive = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            enabled = !isAlarmActive && canScheduleExact
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start 10-Minute Alarm")
        }
        
        // 알람 정지 버튼
        OutlinedButton(
            onClick = {
                AlarmReceiver.cancelAlarm(context)
                isAlarmActive = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isAlarmActive
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop Alarm")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 권한 및 최적화 안내 (Android 12+ 또는 권한 없을 때만 표시)
        if (!canScheduleExact || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (canScheduleExact) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (canScheduleExact) "⚡ 배터리 최적화 설정" else "⚠️ 권한 필요",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canScheduleExact) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (!canScheduleExact) {
                        Text(
                            text = "정확한 알람 권한이 필요합니다. 설정으로 이동하여 권한을 허용하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("정확한 알람 권한 설정")
                        }
                    } else {
                        Text(
                            text = "알람 지연을 최소화하려면:\n" +
                                  "• 설정 > 앱 > KakaoMiddleware > 배터리 > 제한 없음\n" +
                                  "• 설정 > 배터리 > 배터리 최적화 > KakaoMiddleware 제외",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("배터리 최적화 설정")
                        }
                    }
                }
            }
        }
        
        // 사용법 안내
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "사용법",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "1. 'Start 10-Minute Alarm' 버튼을 눌러 알람을 시작합니다.\n" +
                          "2. 매시 00, 10, 20, 30, 40, 50분에 정확히 로그가 출력됩니다.\n" +
                          "3. Android Studio의 Logcat에서 'AlarmReceiver' 태그로 확인할 수 있습니다.\n" +
                          "   - adb logcat -s AlarmReceiver\n" +
                          "4. 앱이 종료되어도 알람은 백그라운드에서 계속 작동합니다.\n" +
                          "5. 로그에서 지연 시간을 확인할 수 있습니다.\n" +
                          "6. 'Stop Alarm' 버튼으로 알람을 중지할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatManagementScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val replyManager = remember { ReplyManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var availableChats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    
    // 채팅방 목록 로드
    LaunchedEffect(Unit) {
        while (true) {
            availableChats = replyManager.getAvailableChats()
            delay(60000) // 60초마다 새로고침
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 헤더
        Text(
            text = "💬 Chat Management",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 통계 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📊 저장된 채팅방 컨텍스트",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val groupChats = availableChats.filter { it.chatType == ChatContext.ChatType.GROUP }
                val personalChats = availableChats.filter { it.chatType == ChatContext.ChatType.PERSONAL }
                
                Text(
                    text = "전체: ${availableChats.size}개\n" +
                          "👥 그룹 채팅: ${groupChats.size}개\n" +
                          "👤 개인 채팅: ${personalChats.size}개",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // 필터 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filterOptions = listOf("All", "Personal", "Group")
            filterOptions.forEach { option ->
                FilterChip(
                    onClick = { filterType = option },
                    label = { Text(option) },
                    selected = filterType == option,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 채팅방 목록
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filteredChats = when (filterType) {
                "Personal" -> availableChats.filter { it.chatType == ChatContext.ChatType.PERSONAL }
                "Group" -> availableChats.filter { it.chatType == ChatContext.ChatType.GROUP }
                else -> availableChats
            }
            
            if (filteredChats.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📭",
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Text(
                                text = "저장된 채팅방 컨텍스트가 없습니다",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = "KakaoTalk에서 메시지를 주고받으면 채팅방 컨텍스트가 자동으로 저장됩니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(filteredChats) { chat ->
                    ChatContextItem(
                        chat = chat,
                        isSelected = selectedChatId == chat.chatId,
                        onClick = { selectedChatId = chat.chatId }
                    )
                }
            }
        }
        
        // 메시지 입력 및 전송 영역
        if (selectedChatId != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    val selectedChat = availableChats.find { it.chatId == selectedChatId }
                    
                    Text(
                        text = "📤 ${selectedChat?.displayName}에게 메시지 보내기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("메시지 입력") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank() && !isLoading && selectedChatId != null) {
                                    // 키보드 전송과 동일한 로직
                                    isLoading = true
                                    statusMessage = "📤 메시지 전송 중..."
                                    
                                    coroutineScope.launch {
                                        try {
                                            val currentMessage = messageText
                                            val success = replyManager.sendMessageToChat(selectedChatId!!, currentMessage)
                                            
                                            isLoading = false
                                            if (success) {
                                                statusMessage = "✅ 메시지가 성공적으로 전송되었습니다!"
                                                messageText = ""
                                            } else {
                                                statusMessage = "❌ 메시지 전송에 실패했습니다. 해당 채팅방에서 최근 메시지를 받은 후 다시 시도해주세요."
                                            }
                                            
                                            delay(5000)
                                            statusMessage = ""
                                            
                                        } catch (e: Exception) {
                                            isLoading = false
                                            statusMessage = "❌ 오류가 발생했습니다: ${e.message}"
                                            
                                            delay(5000)
                                            statusMessage = ""
                                        }
                                    }
                                }
                            }
                        ),
                        maxLines = 3
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                selectedChatId = null
                                messageText = ""
                                statusMessage = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("취소")
                        }
                        
                        Button(
                            onClick = {
                                if (messageText.isNotBlank() && !isLoading && selectedChatId != null) {
                                    // 실제 메시지 전송 구현
                                    isLoading = true
                                    statusMessage = "📤 메시지 전송 중..."
                                    
                                    // 코루틴에서 비동기 메시지 전송
                                    coroutineScope.launch {
                                        try {
                                            val currentMessage = messageText
                                            val success = replyManager.sendMessageToChat(selectedChatId!!, currentMessage)
                                            
                                            isLoading = false
                                            if (success) {
                                                statusMessage = "✅ 메시지가 성공적으로 전송되었습니다!"
                                                messageText = "" // 전송 성공 시 입력 필드 초기화
                                            } else {
                                                statusMessage = "❌ 메시지 전송에 실패했습니다. 해당 채팅방에서 최근 메시지를 받은 후 다시 시도해주세요."
                                            }
                                            
                                            // 5초 후 상태 메시지 초기화
                                            delay(5000)
                                            statusMessage = ""
                                            
                                        } catch (e: Exception) {
                                            isLoading = false
                                            statusMessage = "❌ 오류가 발생했습니다: ${e.message}"
                                            
                                            // 5초 후 상태 메시지 초기화
                                            delay(5000)
                                            statusMessage = ""
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = messageText.isNotBlank() && !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("전송")
                            }
                        }
                    }
                    
                    if (statusMessage.isNotEmpty()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatContextItem(
    chat: ChatSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                } else {
                    Modifier
                }
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            ButtonDefaults.outlinedButtonBorder
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "마지막 발신: ${chat.lastSender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                Text(
                    text = chat.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Text(
                    text = "💡 메시지를 입력하고 전송 버튼을 눌러주세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KakaoMessageLoggerPreview() {
    KakaoMiddlewareTheme {
        KakaoMessageLogger(onOpenSettings = {})
    }
}