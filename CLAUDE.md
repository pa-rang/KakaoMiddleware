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

**Group room names come from `KakaoRoomNameResolver`, nowhere else.** KakaoTalk 26.7.0 (2026-08) removed the room name from the notification body — `subText` is null and `conversationTitle` absent — so the resolver walks subText → conversationTitle → the conversation-shortcut label → a persisted `tag → name` map. Both the capture path and `ActiveNotificationFinder` (reply injection) must use it; if they ever disagree about a room's name, outbound injection fails silently.

`sbn.tag` is KakaoTalk's own chat-room id and the shortcut id, so it identifies a room exactly — but reading that shortcut's label needs `Ranking.getConversationShortcutInfo`, which is **API 31**. The test device is Android 11, one level short, so the tag map is the only live source there: seed it with `scripts/seed-room-name-map.sh` (copies the system shortcut registry over adb) and re-run it after joining a new group room, which logcat announces as `Group ... with no resolvable room name - dropping (tag=...)`. **On Android 12+ the shortcut step names a new room on its first message and the script is unnecessary.** The `LinkageError` catch stays as a guard for OEM frameworks that ship without the API regardless — a missing method throws an `Error`, which a `catch (Exception)` would miss and which killed the listener service until the guard was corrected.

The name only *names* a group — whether a chat **is** one stays `isGroupConversation` alone, so a personal chat carrying a title can never silently become a group. **When `isGroupConversation` is true and no name resolves, the notification is dropped loudly rather than downgraded to a personal message.** Downgrading is how a group photo reached the server as a 1:1 message and got answered without anyone mentioning the assistant.

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

Chat IDs are `ChatContext.generateChatId`: `personal_<name>` or `group_<name>`. This string is the join key against the server's outbound claim payload, so the two must stay identical.

This whole mechanism depends on KakaoTalk's notification structure. A KakaoTalk update can break it without any change on our side; `canHijackNotification` and `getHijackingDebugInfo` exist to diagnose that.

## Outbound and scheduled messages

FCM is the primary trigger and carries no message body. `PushRegistrationManager` opts into Firebase installation-ID mode; `KakaoFirebaseMessagingService.onRegistered()` uploads the FID through `PushRegistrationWorker`. A supported `outbound_available` data payload enqueues an expedited `OutboundDrainWorker`.

The Worker is the only modern delivery implementation:

1. claim device-scoped rows from `/api/v1/device/outbound-messages/claim`,
2. inject each through `ReplyManager`,
3. persist its id in `DeliveryJournal` immediately after injection,
4. ACK the matching `attemptCount`, then remove the journal entry.

The journal holds at most 200 entries for 48 hours. If ACK was lost, a later claim sees the id and repairs ACK without injecting again. HTTP/network failures retry; 401/403-class configuration failures stop rather than loop forever.

`AlarmReceiver` remains a ten-minute FCM-loss fallback, but it only enqueues the same non-expedited Worker and schedules its successor. It performs no HTTP, KakaoTalk injection, or ACK itself. `AlarmRescheduleReceiver` restores the chain on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Exact-alarm permission or OEM delay affects only this fallback, not FCM.

`CronApiService` and its legacy payload model remain temporarily so an older APK can talk to the server compatibility route; new runtime code does not call them.

### Firebase project file

`google-services.json` is environment-owned and gitignored. The Google Services plugin is applied only when `app/google-services.json`, `app/src/debug/google-services.json`, or `app/src/release/google-services.json` exists, so a fresh clone can still run tests. A build without one starts normally but logs that FCM is unavailable. Because debug has an `.debug` application-id suffix, its Firebase Android app registration must match that package if debug push testing is required.

The provisioned Firebase project is `kakao-gpt-coby-20260802`. It contains Android registrations for both `com.example.kakaomiddleware` and `com.example.kakaomiddleware.debug`; the environment-owned debug/release config files each contain both clients and the Google Services plugin selects the matching package at build time.

## Server configuration

`ServerConfigManager` (SharedPreferences + `StateFlow`) overrides the endpoint at runtime; `BuildConfig.API_ENDPOINT` is only the fallback. To point a debug build at a local server, set the URL in the **Settings** tab — cleartext HTTP to localhost, `10.0.2.2`, and all private IP ranges is already permitted by `res/xml/network_security_config.xml`.

### The server API key

Every request adds `Authorization: Bearer <key>` through `Request.Builder.addApiKeyHeader()`. The key comes from `SERVER_API_KEY` in `local.properties` — gitignored, so it stays out of commits — and reaches the code as `BuildConfig.API_KEY`.

A build without the property compiles fine and sends no header at all. The inbound and legacy compatibility routes may accept that only while `AUTH_ENFORCE` is off; push registration, device claim, and ACK always reject it. **A clone with no `local.properties` entry therefore cannot use FCM delivery**, and the failure appears as a permanent worker authentication error rather than a build error.

The endpoint override and the key are independent: pointing a debug build at a local server still sends the production key, so that server needs the same secret listed in its `API_KEYS` or it will reject the app.

The install-then-enforce and FCM rollout sequence — and what breaks if you reverse it — is written up in the server repo at `docs/REMAINING_WORK.md`.

## UI

`MainActivity.kt` is a single large Compose file with five tabs: **Messages** (live log), **Allowlist** (contacts, groups, Turbo Mode), **Chat** (browse stored chats, send manual messages), **Settings** (server config), **Alarm** (schedule status and test).

## Debugging

Logcat tags, in pipeline order:

```
KakaoNotificationListener → AllowlistManager → ServerRequestQueue → ServerApiService
KakaoFirebaseService → OutboundDrainWorker → DeviceApiService → ReplyManager
AlarmReceiver → OutboundDrainWorker  (fallback)
```

`ServerApiService` logs the resolved endpoint and labels it `🏠 LOCAL SERVER` or `☁️ PRODUCTION SERVER` on every request — check this first when responses are not what you expect.

| Symptom | Likely cause |
|---|---|
| No notifications captured | Notification access not granted, or revoked by reinstall |
| Messages captured but no server request | Sender/group not allowlisted and Turbo Mode off |
| Server returns a reply but nothing appears | No cached notification for that chat (e.g. since reboot), or KakaoTalk changed its notification layout |
| Outbound messages never arrive | FID not registered / FCM disabled, API key rejected, or `chatRoomName` does not match a local chat ID |
| FCM log appears but no delivery | WorkManager/network constraint, no active KakaoTalk notification, or server claim returned empty |

## Note on naming

The class `ServerRequestQueue` lives in a file named `GptRequestQueue.kt`. The file was never renamed; do not go looking for `ServerRequestQueue.kt`.
