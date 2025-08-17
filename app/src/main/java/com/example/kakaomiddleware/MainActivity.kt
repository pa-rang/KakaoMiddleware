package com.example.kakaomiddleware

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kakaomiddleware.ui.theme.KakaoMiddlewareTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KakaoMiddlewareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    KakaoMessageLogger(
                        modifier = Modifier.padding(innerPadding),
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
            Text(
                text = when (notification) {
                    is GroupMessage -> notification.message
                    is PersonalMessage -> notification.message
                    is UnreadSummary -> notification.unreadInfo
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
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