# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Android app that intercepts KakaoTalk notifications and posts AI replies back into the conversation by hijacking the notification's `RemoteInput`. The server that generates those replies lives in a sibling repository; the **cross-repo API contracts are documented in the parent directory's `CLAUDE.md`** and are not repeated here.

**This app makes no decisions.** It has no model, no trigger detection, and no provider credentials — the one key it carries authenticates it to our own server and unlocks nothing else. It forwards messages and injects whatever the server returns. Any request to "make the bot smarter" belongs in the server repo.

## Commands

```bash
./gradlew assembleDebug          # debug APK — applicationId suffix .debug, installs alongside release
./gradlew installDebug
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

Nothing works until **notification access** is granted manually in Android settings (the app's Messages tab has a button that opens the right screen). This cannot be granted programmatically, and a fresh install or an `applicationId` change resets it.

## Message capture pipeline

`KakaoNotificationListenerService` filters for `com.kakao.talk` and classifies each notification:

| Type | `text` | `subText` | `isGroupConversation` | `title` |
|---|---|---|---|---|
| `PersonalMessage` | message | empty | false | sender |
| `GroupMessage` | message | group name | true | sender |
| `UnreadSummary` | empty | unread info | false | empty |

KakaoTalk posts **multiple notifications per message** (a summary plus the actual message), which is why `UnreadSummary` must be recognized and discarded rather than treated as a message.

For every message it keeps, the service:

1. stores the `StatusBarNotification` in `NotificationStorage` (in-memory, keyed by chat ID) so a reply can be injected later,
2. checks the send gate, and
3. hands off to `ServerRequestQueue` (up to 100 concurrent requests).

### The send gate

```kotlin
allowlistManager.isTurboModeEnabled() || allowlistManager.isPersonalAllowed(sender)   // or isGroupAllowed(groupName)
```

**Turbo Mode short-circuits the allowlist**, forwarding every message from every chat to the server. It is a toggle in the Allowlist tab. When editing anything in this path, keep in mind that the allowlist is only a privacy boundary while Turbo Mode is off.

`AllowlistManager` is a SharedPreferences-backed singleton exposing `StateFlow`s, with a change listener so edits in the UI take effect in the service immediately. It must be obtained via `getInstance()` — a second instance will silently serve stale data to the notification service.

## Reply injection

`RemoteInputHijacker` pulls the reply action's `RemoteInput` off a `StatusBarNotification`, packs the response into a `Bundle` under `remoteInput.resultKey`, and fires `action.actionIntent`. The reply appears in KakaoTalk with no UI, no app switch, and works with the screen off.

Finding *which* notification to hijack is the fragile part, so `ReplyManager` resolves it in two steps: the `NotificationStorage` memory cache first, then `ActiveNotificationFinder` over `getActiveNotifications()`, caching whatever it finds. `ChatRepository` (SharedPreferences) persists chat metadata across restarts, but a `StatusBarNotification` itself cannot be persisted — **after a reboot, a chat cannot be replied to until it produces a new notification.**

Chat IDs are `ChatContext.generateChatId`: `personal_<name>` or `group_<name>`. This string is the join key against the server's scheduled-message payload, so the two must stay identical.

This whole mechanism depends on KakaoTalk's notification structure. A KakaoTalk update can break it without any change on our side; `canHijackNotification` and `getHijackingDebugInfo` exist to diagnose that.

## Scheduled messages

`AlarmReceiver` uses `AlarmManager` to fire on 10-minute boundaries (`:00 :10 :20 :30 :40 :50`), calls `CronApiService`, and injects each returned message via `ReplyManager`. It reschedules itself on every fire — a missed alarm ends the chain, so failures must not escape the receiver.

The poll contains both recurring scheduled messages and one-shot outbound messages. `CronApiService` distinguishes them with `messageSource`; after each outbound injection, the app posts the `scheduledMessageId`, `deliveryAttempt`, and boolean result to `/api/v1/outbound-messages/ack`. Scheduled messages do not send ACKs.

`USE_EXACT_ALARM` is declared, but OEM battery optimization still delays alarms in practice. The Alarm tab exposes status and a manual trigger for testing this.

## Server configuration

`ServerConfigManager` (SharedPreferences + `StateFlow`) overrides the endpoint at runtime; `BuildConfig.API_ENDPOINT` is only the fallback. To point a debug build at a local server, set the URL in the **Settings** tab — cleartext HTTP to localhost, `10.0.2.2`, and all private IP ranges is already permitted by `res/xml/network_security_config.xml`.

### The server API key

Every request adds `Authorization: Bearer <key>` through `Request.Builder.addApiKeyHeader()`. The key comes from `SERVER_API_KEY` in `local.properties` — gitignored, so it stays out of commits — and reaches the code as `BuildConfig.API_KEY`.

A build without the property compiles fine and sends no header at all, which the server accepts only while its `AUTH_ENFORCE` is off. **A clone with no `local.properties` entry therefore builds an app that will stop working the moment enforcement is turned on**, and the failure looks like a 401 on every request, not a build error.

The endpoint override and the key are independent: pointing a debug build at a local server still sends the production key, so that server needs the same secret listed in its `API_KEYS` or it will reject the app.

**This build has not been installed on the device yet**, so the server still runs with enforcement off. The install-then-enforce sequence — and what breaks if you reverse it — is written up in the server repo at `docs/REMAINING_WORK.md`.

## UI

`MainActivity.kt` is a single large Compose file with five tabs: **Messages** (live log), **Allowlist** (contacts, groups, Turbo Mode), **Chat** (browse stored chats, send manual messages), **Settings** (server config), **Alarm** (schedule status and test).

## Debugging

Logcat tags, in pipeline order:

```
KakaoNotificationListener → AllowlistManager → ServerRequestQueue → ServerApiService
AlarmReceiver → CronApiService → ReplyManager
```

`ServerApiService` logs the resolved endpoint and labels it `🏠 LOCAL SERVER` or `☁️ PRODUCTION SERVER` on every request — check this first when responses are not what you expect.

| Symptom | Likely cause |
|---|---|
| No notifications captured | Notification access not granted, or revoked by reinstall |
| Messages captured but no server request | Sender/group not allowlisted and Turbo Mode off |
| Server returns a reply but nothing appears | No cached notification for that chat (e.g. since reboot), or KakaoTalk changed its notification layout |
| Scheduled messages never arrive | Alarm chain broken, or `chatRoomName` from the server does not match a local chat ID |

## Note on naming

The class `ServerRequestQueue` lives in a file named `GptRequestQueue.kt`. The file was never renamed; do not go looking for `ServerRequestQueue.kt`.
