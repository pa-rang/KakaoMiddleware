# KakaoMiddleware - KakaoTalk AI Reply System

## Project Overview
KakaoMiddleware is an Android application that intercepts KakaoTalk notifications and provides server-mediated automatic replies. The system sends ALL KakaoTalk messages to a custom server API and handles responses through background RemoteInput hijacking.

## Project Status: Phase 2.2 Complete ✅
**Current State**: Production-ready server-based middleware with allowlist filtering  
**Architecture**: Selective message processing with allowlist verification

## Architecture

### Core Components

#### 1. KakaoNotificationListenerService
- **Location**: `app/src/main/java/com/example/kakaomiddleware/KakaoNotificationListenerService.kt`
- **Purpose**: Android NotificationListenerService that intercepts KakaoTalk notifications
- **Key Features**:
  - Filters notifications from `com.kakao.talk` package
  - Analyzes notification extras to determine message type
  - Sends ALL messages to server via ServerRequestQueue
  - Integrated RemoteInput hijacking for invisible replies

#### 2. ServerRequestQueue
- **Location**: `app/src/main/java/com/example/kakaomiddleware/GptRequestQueue.kt`
- **Purpose**: Asynchronous server request processing with high concurrency
- **Key Features**:
  - 100 concurrent request limit for optimal performance
  - Queue-based processing system
  - Processes ALL messages (no trigger detection)
  - RemoteInput hijacking for server responses

#### 3. ServerApiService
- **Location**: `app/src/main/java/com/example/kakaomiddleware/ServerApiService.kt`
- **Purpose**: HTTP client for custom server API communication
- **Key Features**:
  - OkHttp-based REST client
  - Endpoint: https://kakaobot-server.vercel.app/api/v1/process-message
  - Robust error handling and timeout management
  - JSON payload creation and response parsing

#### 4. MainActivity
- **Location**: `app/src/main/java/com/example/kakaomiddleware/MainActivity.kt`
- **Purpose**: Tabbed UI for message logging and allowlist management
- **Key Features**:
  - Real-time notification display with 1-second refresh
  - Allowlist management for personal contacts and group chats
  - Button to open notification access settings
  - Jetpack Compose UI with Material Design 3

#### 5. AllowlistManager
- **Location**: `app/src/main/java/com/example/kakaomiddleware/AllowlistManager.kt`
- **Purpose**: Singleton manager for allowlist storage and verification
- **Key Features**:
  - SharedPreferences-based persistent storage
  - Separate allowlists for personal contacts and group chats
  - Real-time updates via SharedPreferences change listeners
  - Thread-safe singleton pattern for cross-component access

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
MainScreen
├── TabRow: "Messages" | "Allowlist"
├── Messages Tab:
│   ├── Title: "KakaoTalk Message Logger"
│   ├── Button: "Enable Notification Access"
│   └── LazyColumn: List of notifications (newest first)
│       └── NotificationItem: Card with timestamp and content
└── Allowlist Tab:
    ├── Title: "Reply Allowlist"
    ├── Description: Filtering information
    ├── Personal Contacts Section:
    │   ├── Input field + Add button
    │   └── List of contacts with delete buttons
    └── Group Chats Section:
        ├── Input field + Add button
        └── List of groups with delete buttons
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

#### **Milestone 1**: Basic Hijacking ✅ COMPLETE
- [x] Extract RemoteInput from notifications
- [x] Successfully inject test responses  
- [x] Verify KakaoTalk receives hijacked replies

#### **Milestone 2**: AI Integration ✅ COMPLETE
- [x] Re-implement Gemini API service
- [x] Add "@GPT_call_it" trigger detection
- [x] Test end-to-end AI response flow

#### **Milestone 3**: Production Ready ✅ COMPLETE
- [x] Async queue system implementation
- [x] Error handling and retry logic
- [x] Performance optimization and testing

### ✅ **PHASE 2.1 COMPLETE - SERVER INTEGRATION READY**

---

## 🎉 Phase 2.1 Final Implementation

### **Current Architecture: Server-Based Middleware**

#### **Core Philosophy**
- **Selective Processing**: Only allowlisted contacts/groups are sent to server for processing
- **Client-side Filtering**: AllowlistManager provides pre-filtering before server requests
- **Server Intelligence**: Custom server decides when to respond based on message content
- **Conditional Injection**: Android app only injects replies when server provides non-null response
- **Invisible Operation**: Background RemoteInput hijacking maintains seamless UX

### **System Components**

#### **1. ServerRequestQueue.kt** (formerly GptRequestQueue.kt)
- **Purpose**: High-throughput asynchronous server request processing
- **Key Features**:
  - 100 concurrent request limit for enterprise-scale processing
  - Universal message processing (no trigger detection)
  - Conditional reply injection based on server response
  - Robust error handling and connection management

#### **2. ServerApiService.kt** (replaces GeminiApiService.kt)
- **Purpose**: OkHttp-based HTTP client for custom server communication
- **Key Features**:
  - RESTful API client with proper timeout configuration
  - JSON payload creation and response parsing
  - Comprehensive error handling for network failures
  - Structured data classes matching server API schema

#### **3. RemoteInputHijacker.kt**
- **Purpose**: Core notification hijacking functionality
- **Key Features**:
  - Extracts RemoteInput from original KakaoTalk notifications
  - Injects server responses back to KakaoTalk invisibly
  - Validates hijacking capability before attempting injection
  - Maintains notification context for seamless operation

#### **4. KakaoNotificationListenerService.kt**
- **Enhanced Features**:
  - Universal message capture and classification
  - Automatic server request queuing for all message types
  - Simplified logging and error handling
  - Integration with ServerRequestQueue for background processing

### **Current System Flow**

```
KakaoTalk Notification
    ↓
KakaoNotificationListenerService (captures ALL messages)
    ↓
AllowlistManager (verifies sender/group is allowlisted)
    ↓ (only if allowlisted)
ServerRequestQueue (queues for processing)
    ↓
ServerApiService (HTTP POST to custom server)
    ↓
Custom Server (https://kakaobot-server.vercel.app/api/v1/process-message)
    ↓
Server Response (reply: "Hello, I'm GPT!" or reply: null)
    ↓
Conditional RemoteInput Hijacking (only if reply ≠ null)
    ↓
KakaoTalk receives response (invisible to user)
```

### **Server Integration Details**

#### **API Endpoint**
- **URL**: `https://kakaobot-server.vercel.app/api/v1/process-message`
- **Method**: POST
- **Content-Type**: application/json

#### **Request Schema**
```json
{
  "id": "msg_android_[timestamp]_[uuid]",
  "isGroup": true,
  "groupName": "My Friends",
  "sender": "John",
  "message": "Hello @GPT how are you?",
  "timestamp": 1642425600000,
  "deviceId": "android_kakaomiddleware"
}
```

#### **Server Logic**
- **Trigger Detection**: Server checks for "@GPT" in message content
- **Response Logic**:
  - Contains "@GPT" → Returns `{"reply": "Hello, I'm GPT!"}`
  - No "@GPT" → Returns `{"reply": null}`
- **Android Behavior**:
  - `reply ≠ null` → Inject response to KakaoTalk
  - `reply = null` → Do nothing (silent processing)

### **Production Ready Features**

#### **Scalability**
- 100 concurrent server requests for high-throughput processing
- Asynchronous queue system prevents blocking
- Memory-efficient request handling with automatic cleanup

#### **Reliability**
- Comprehensive error handling for network failures
- Graceful degradation when server is unavailable
- Automatic retry logic and timeout management

#### **Security**
- No client-side API keys or sensitive data storage
- Server-side intelligence and processing control
- Minimal logging to prevent data leakage

---

## File Structure
```
app/src/main/
├── AndroidManifest.xml (permissions & service declarations)
├── java/com/example/kakaomiddleware/
│   ├── MainActivity.kt (tabbed UI with allowlist management)
│   ├── KakaoNotificationListenerService.kt (message capture with allowlist filtering)
│   ├── AllowlistManager.kt (singleton allowlist storage and verification)
│   ├── RemoteInputHijacker.kt (core hijacking functionality)
│   ├── GptRequestQueue.kt → ServerRequestQueue.kt (server request processing)
│   ├── ServerApiService.kt (OkHttp-based server client)
│   └── ui/theme/ (Material Design theme)
└── res/ (app resources)
```

## Dependencies
- Jetpack Compose for UI
- Material Design 3
- Android NotificationListenerService API
- Kotlin Coroutines for async processing
- OkHttp for HTTP client (4.12.0)
- Custom Server API (Next.js 15)

## 🧪 Testing Guide

### **Setup Instructions**
1. **Install APK** on Android device (build with `./gradlew assembleDebug`)
2. **Enable notification access**:
   - Open KakaoMiddleware app
   - Tap "Enable Notification Access" 
   - Find "KakaoMiddleware" in Android settings
   - Enable notification access permission
3. **Configure allowlist** (Messages Tab → Allowlist Tab):
   - Add personal contact names to Personal Contacts
   - Add group chat names to Group Chats
   - Only messages from allowlisted contacts/groups will trigger AI responses
4. **Verify server connectivity** (server should be running at https://kakaobot-server.vercel.app)

### **Test Scenarios**

#### **Scenario 1: Non-Allowlisted Contact (No Server Request)**
```
Send from non-allowlisted contact: "Hey @GPT how are you?"
Expected Result: ❌ No server request, ❌ No reply injected
Log: "Sender 'ContactName' not in allowlist - skipping server request"
```

#### **Scenario 2: Allowlisted Contact, Regular Message (No Reply)**
```
Send from allowlisted contact: "Hey how are you?"
Expected Result: ✅ Message sent to server, ❌ No reply injected
Server Response: {"success": true, "reply": null}
```

#### **Scenario 3: Allowlisted Contact, @GPT Trigger (Reply Injection)**
```
Send from allowlisted contact: "Hey @GPT how are you?"
Expected Result: ✅ Message sent to server, ✅ "Hello, I'm GPT!" appears in chat
Server Response: {"success": true, "reply": "Hello, I'm GPT!"}
```

### **Logging & Debug**
Use Android Studio Logcat with these tags:
- `KakaoNotificationListener` - Message detection and allowlist verification
- `AllowlistManager` - Allowlist storage and verification results
- `ServerRequestQueue` - Request processing and injection results  
- `ServerApiService` - HTTP communication with server

### **Troubleshooting**
- **No notifications captured**: Check notification access permissions
- **Messages not being sent to server**: Verify contacts/groups are in allowlist
- **Allowlist changes not taking effect**: Check that singleton AllowlistManager is being used
- **Server errors**: Verify server is running and network connectivity
- **Reply injection fails**: Check KakaoTalk version compatibility

---

## 🎯 Phase 2.2 Complete: Allowlist Feature

### **New Feature: Selective Message Processing**

#### **Allowlist Management**
- **Personal Contacts**: Only messages from specified personal contacts are processed
- **Group Chats**: Only messages from specified group chats are processed
- **UI Management**: Tabbed interface for easy allowlist configuration
- **Persistent Storage**: SharedPreferences-based storage survives app restarts

#### **Technical Implementation**
- **Singleton Pattern**: Single AllowlistManager instance shared across components
- **Real-time Updates**: SharedPreferences change listeners for immediate sync
- **Pre-filtering**: Client-side filtering before server requests to save bandwidth
- **Thread Safety**: Concurrent access protection for notification service

#### **User Experience**
- **Privacy Control**: Users control exactly which contacts can trigger AI responses
- **Bandwidth Optimization**: Reduced server requests and data usage
- **Easy Management**: Add/remove contacts and groups with simple UI
- **Visual Feedback**: Real-time allowlist updates in the interface

### **Benefits**
- ✅ **Privacy**: Only trusted contacts can trigger AI responses
- ✅ **Performance**: Reduced server load and faster processing
- ✅ **Control**: Fine-grained control over AI reply behavior
- ✅ **Scalability**: Client-side filtering reduces server bandwidth usage