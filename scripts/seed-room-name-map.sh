#!/usr/bin/env bash
#
# Seeds KakaoRoomNameResolver's tag → room-name map from the device's own
# shortcut registry.
#
# Why this exists: KakaoTalk 26.7.0 removed the room name from its
# notifications, and on frameworks without Ranking.getConversationShortcutInfo
# (e.g. Samsung Android 11) the app has no way to learn a group's name by
# itself. The adb shell *can* read the system shortcut registry, where KakaoTalk
# registers every conversation as `id=<chat tag>, shortLabel=<room name>` — so
# this script copies that mapping into the app's SharedPreferences.
#
# Debug builds only (`run-as` needs a debuggable package). Safe to re-run any
# time; re-run it when a brand-new group room shows up in logcat as
# "Group text with no resolvable room name - dropping (tag=...)".
#
# Usage: ./scripts/seed-room-name-map.sh
set -euo pipefail

PKG="com.example.kakaomiddleware.debug"
PREFS_FILE="kakao_room_name_map.xml"

command -v adb >/dev/null || { echo "adb not found"; exit 1; }
adb get-state >/dev/null 2>&1 || adb reconnect offline >/dev/null 2>&1 || true
adb get-state >/dev/null || { echo "no device connected"; exit 1; }

TMP_XML="$(mktemp)"
trap 'rm -f "$TMP_XML"' EXIT

# Extract KakaoTalk's shortcut section and turn "id=..., shortLabel=..." pairs
# into a SharedPreferences XML. Labels may contain commas ("정연, 성재"), so the
# label is cut at the trailing ", resId=..." instead of at the first comma.
adb shell dumpsys shortcut | awk '
  /^      Package: /       { in_kakao = ($2 == "com.kakao.talk") }
  in_kakao && /ShortcutInfo \{id=/ {
    id = $0
    sub(/.*\{id=/, "", id); sub(/,.*/, "", id)
  }
  in_kakao && /^ *shortLabel=/ && id != "" {
    label = $0
    sub(/^ *shortLabel=/, "", label); sub(/, resId=.*$/, "", label)
    gsub(/&/, "\\&amp;", label); gsub(/</, "\\&lt;", label); gsub(/>/, "\\&gt;", label)
    printf "    <string name=\"%s\">%s</string>\n", id, label
    id = ""
  }
' > "$TMP_XML.entries"

ENTRY_COUNT=$(wc -l < "$TMP_XML.entries" | tr -d ' ')
[ "$ENTRY_COUNT" -gt 0 ] || { echo "no KakaoTalk shortcuts found in dumpsys — nothing to seed"; exit 1; }

{
  echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
  echo "<map>"
  cat "$TMP_XML.entries"
  echo "</map>"
} > "$TMP_XML"
rm -f "$TMP_XML.entries"

echo "Seeding $ENTRY_COUNT room names into $PKG..."

# SharedPreferences are cached per-process forever, so the app must be stopped
# while the file is replaced or the seed would be invisible (and could be
# overwritten by a later in-app apply()). The notification listener is rebound
# by the system after the next install/toggle; reinstalling via
# `./gradlew installDebug` right after seeding is the reliable way back up.
adb shell am force-stop "$PKG"
adb shell run-as "$PKG" mkdir -p shared_prefs
adb push "$TMP_XML" /data/local/tmp/seed_room_map.xml >/dev/null
adb shell "run-as $PKG sh -c 'cp /data/local/tmp/seed_room_map.xml shared_prefs/$PREFS_FILE'"
adb shell rm /data/local/tmp/seed_room_map.xml

echo "Done. Now rebind the listener (e.g. ./gradlew installDebug) and check:"
echo "  adb logcat -s KakaoNotificationListener KakaoRoomNameResolver"
