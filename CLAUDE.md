# KakaoMiddleware - KakaoTalk AI Reply System

## Project Overview
KakaoMiddleware is an Android application that intercepts KakaoTalk notifications and provides AI-powered automatic replies. The system detects "@GPT_call_it" triggers in messages and responds using Google's Gemini API through background RemoteInput hijacking.

## Project Status: Phase 1 Complete ✅
**Current State**: Basic message logging and classification  
**Next Phase**: RemoteInput hijacking for AI reply functionality

## Architecture

### Core Components

#### 1. KakaoNotificationListenerService
- **Location**: `app/src/main/java/com/example/kakaomiddleware/KakaoNotificationListenerService.kt`
- **Purpose**: Android NotificationListenerService that intercepts KakaoTalk notifications
- **Key Features**:
  - Filters notifications from `com.kakao.talk` package
  - Analyzes notification extras to determine message type
  - Stores notifications in static list for UI access

#### 2. MainActivity
- **Location**: `app/src/main/java/com/example/kakaomiddleware/MainActivity.kt`
- **Purpose**: Main UI displaying logged notifications
- **Key Features**:
  - Real-time notification display with 1-second refresh
  - Button to open notification access settings
  - Jetpack Compose UI with Material Design 3

## Data Classes

### KakaoNotification (Sealed Class)
Base class for all notification types with common properties:
```kotlin
sealed class KakaoNotification {
    abstract val timestamp: Long
    abstract val formattedTime: String
}
```

### PersonalMessage
For 1:1 personal chat messages:
```kotlin
data class PersonalMessage(
    val sender: String,
    val message: String,
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()
```

### GroupMessage
For group chat messages:
```kotlin
data class GroupMessage(
    val groupName: String,
    val sender: String,
    val message: String,
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()
```

### UnreadSummary
For summary notifications (e.g., "40개의 안 읽은 메시지"):
```kotlin
data class UnreadSummary(
    val unreadInfo: String,
    override val timestamp: Long,
    override val formattedTime: String
) : KakaoNotification()
```

## Notification Classification Logic

### Detection Patterns
Based on analysis of KakaoTalk notification structure:

| Notification Type | `text` | `subText` | `isGroupConversation` | `title` |
|-------------------|--------|-----------|----------------------|---------|
| PersonalMessage   | ✓ (message) | ❌ (empty) | false | sender name |
| GroupMessage      | ✓ (message) | ✓ (group name) | true | sender name |
| UnreadSummary     | ❌ (empty) | ✓ (unread info) | false | ❌ (empty) |

### Classification Code
```kotlin
val notification = when {
    text.isNotEmpty() && isGroupConversation && subText.isNotEmpty() -> {
        GroupMessage(groupName = subText, sender = title, message = text, ...)
    }
    text.isNotEmpty() && !isGroupConversation -> {
        PersonalMessage(sender = title, message = text, ...)
    }
    text.isEmpty() && subText.isNotEmpty() -> {
        UnreadSummary(unreadInfo = subText, ...)
    }
    else -> null
}
```

## UI Design

### Visual Distinction
- **Personal Messages**: Default styling with sender name
- **Group Messages**: Blue group name + sender name below
- **Unread Summaries**: Secondary color with 📱 icon

### Layout Structure
```
KakaoMessageLogger
├── Title: "KakaoTalk Message Logger"
├── Button: "Enable Notification Access"
└── LazyColumn: List of notifications (newest first)
    └── NotificationItem: Card with timestamp and content
```

## Permissions & Manifest

### Required Permissions
```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
```

### Service Declaration
```xml
<service
    android:name=".KakaoNotificationListenerService"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

## Setup Instructions

1. **Install the app** on Android device
2. **Enable notification access**:
   - Tap "Enable Notification Access" button
   - Find "KakaoMiddleware" in settings
   - Enable notification access
3. **Use KakaoTalk** - messages will appear in the app

## Current Implementation Status

### ✅ Phase 1 Complete: Message Logging System
- **Notification Interception**: Successfully captures KakaoTalk notifications
- **Message Classification**: Distinguishes Personal/Group/Summary messages  
- **UI Display**: Real-time notification display with Material Design 3
- **Data Structure**: Robust sealed class hierarchy for different message types

### Key Technical Discoveries
- KakaoTalk uses `android.subText` field for group names
- `android.isGroupConversation` flag distinguishes group vs personal chats
- Empty text with non-empty subText indicates summary notifications
- Multiple notifications are sent per message (summary + actual message)

### Removed Components (Cleaned Up)
- Previous reply system implementation
- Gemini API integration (temporary removal)
- Notification reply manager
- API key management UI

---

## 🎯 Next Phase: RemoteInput Hijacking Implementation

### Objective
Implement **invisible background AI replies** to KakaoTalk messages containing "@GPT_call_it" trigger without any user interface changes or app switching.

### Technical Approach: Enhanced RemoteInput Hijacking

#### **Core Concept**
Instead of creating duplicate notifications, **hijack the original KakaoTalk notification's RemoteInput** to inject AI-generated responses directly back to KakaoTalk.

#### **Key Advantages**
- ✅ **Completely invisible** - No screen changes or app switching
- ✅ **True background operation** - Works while using other apps
- ✅ **Screen-off compatible** - Functions even when screen is off
- ✅ **Asynchronous processing** - Handle multiple "@GPT_call_it" requests concurrently

### Implementation Plan

#### **Phase 2.1: Core Hijacking System**
1. **RemoteInput Extractor**
   ```kotlin
   class RemoteInputHijacker {
       fun extractOriginalRemoteInput(sbn: StatusBarNotification): RemoteInput?
       fun injectResponse(sbn: StatusBarNotification, aiResponse: String): Boolean
   }
   ```

2. **Enhanced Notification Listener**
   - Capture original KakaoTalk StatusBarNotification objects
   - Store notification context for later hijacking
   - Detect "@GPT_call_it" triggers in real-time

#### **Phase 2.2: AI Integration & Queue System**
3. **Gemini API Service** (Re-implementation)
   ```kotlin
   class GeminiApiService {
       suspend fun generateResponse(prompt: String): Result<String>
   }
   ```

4. **Asynchronous Request Queue**
   ```kotlin
   class GptRequestQueue {
       fun addRequest(originalSbn: StatusBarNotification, prompt: String)
       private fun processRequestAsync(request: GptRequest)
   }
   ```

#### **Phase 2.3: Background Processing Pipeline**
5. **Request Flow Design**
   ```
   KakaoTalk Notification → Trigger Detection → Queue Request → 
   Generate AI Response → Hijack RemoteInput → Send to KakaoTalk
   ```

6. **Concurrent Processing**
   - Multiple Gemini API calls in parallel
   - Independent processing threads for each conversation
   - Request timeout and error handling

### Technical Specifications

#### **Hijacking Method**
```kotlin
// Extract RemoteInput from original notification
val actions = originalSbn.notification.actions
val replyAction = actions?.find { it.remoteInputs?.isNotEmpty() == true }

replyAction?.let { action ->
    val remoteInput = action.remoteInputs[0]
    val intent = Intent()
    val bundle = Bundle()
    
    // Inject AI response
    bundle.putCharSequence(remoteInput.resultKey, aiResponse)
    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
    
    // Send back to KakaoTalk
    action.actionIntent.send(context, 0, intent)
}
```

#### **Async Queue Architecture**
```kotlin
class GptRequestQueue {
    private val processingQueue = ConcurrentLinkedQueue<GptRequest>()
    private val executorService = Executors.newFixedThreadPool(3)
    
    fun addRequest(request: GptRequest) {
        processingQueue.offer(request)
        executorService.submit { processRequest(request) }
    }
}
```

### Expected User Experience

#### **Invisible Operation**
1. User receives KakaoTalk message: `"Hey @GPT_call_it what's the weather?"`
2. **Background processing** (user sees nothing)
3. AI response appears in KakaoTalk conversation: `"Today's weather is sunny, 22°C"`
4. User continues using other apps uninterrupted

#### **Concurrent Handling**
- Multiple "@GPT_call_it" messages processed simultaneously
- Each conversation thread handled independently
- No interference with normal KakaoTalk usage

### Security & Compatibility

#### **Considerations**
- **KakaoTalk Updates**: Monitor for RemoteInput structure changes
- **Android Security**: Handle potential security restrictions
- **Rate Limiting**: Implement Gemini API usage controls
- **Error Handling**: Graceful failure without breaking KakaoTalk

#### **Fallback Strategies**
- Retry mechanisms for failed hijacking attempts
- Alternative reply methods if RemoteInput changes
- User notification for system failures

### Development Milestones

#### **Milestone 1**: Basic Hijacking
- [ ] Extract RemoteInput from notifications
- [ ] Successfully inject test responses
- [ ] Verify KakaoTalk receives hijacked replies

#### **Milestone 2**: AI Integration  
- [ ] Re-implement Gemini API service
- [ ] Add "@GPT_call_it" trigger detection
- [ ] Test end-to-end AI response flow

#### **Milestone 3**: Production Ready
- [ ] Async queue system implementation
- [ ] Error handling and retry logic
- [ ] Performance optimization and testing

---

## File Structure
```
app/src/main/
├── AndroidManifest.xml (permissions & service)
├── java/com/example/kakaomiddleware/
│   ├── MainActivity.kt (UI)
│   ├── KakaoNotificationListenerService.kt (core logic)
│   └── ui/theme/ (Material Design theme)
└── res/ (app resources)
```

## Dependencies
- Jetpack Compose for UI
- Material Design 3
- Android NotificationListenerService API