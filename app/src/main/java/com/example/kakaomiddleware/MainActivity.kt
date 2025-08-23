package com.example.kakaomiddleware

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : ComponentActivity() {
    private lateinit var allowlistManager: AllowlistManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowlistManager = AllowlistManager.getInstance(this)
        enableEdgeToEdge()
        setContent {
            KakaoMiddlewareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        allowlistManager = allowlistManager,
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
    onOpenSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Messages", "Allowlist")
    
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

@Preview(showBackground = true)
@Composable
fun KakaoMessageLoggerPreview() {
    KakaoMiddlewareTheme {
        KakaoMessageLogger(onOpenSettings = {})
    }
}