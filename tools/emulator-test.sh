#!/usr/bin/env bash
# Installs Reelio on an emulator, plays test files and saves screenshots + logs to /tmp/report/qa
set -x
trap 'timeout 30 adb logcat -d > /tmp/report/qa/logcat-on-exit.txt 2>/dev/null; grep -E "FATAL EXCEPTION|AndroidRuntime" -A30 /tmp/report/qa/logcat-on-exit.txt > /tmp/report/qa/crashes.txt' EXIT
R=/tmp/report/qa
mkdir -p $R
P=com.rady.reelio
shot() { sleep ${2:-1}; timeout 20 adb exec-out screencap -p > "$R/$1.png"; echo "shot $1" >> $R/notes.txt; }
tap() {
  rm -f /tmp/ui.xml; timeout 25 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; timeout 10 adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  xy=$(python3 tools/ui.py /tmp/ui.xml "$1") && adb shell input tap $xy || echo "could not find '$1'" >> $R/notes.txt
}
state() { echo "== $1" >> $R/sessions.txt; timeout 15 adb shell dumpsys media_session | grep -E "state=PlaybackState|description=|package=$P" | head -6 >> $R/sessions.txt; }

adb install -r -g app/build/outputs/apk/release/app-x86_64-release.apk
adb shell appops set $P MANAGE_EXTERNAL_STORAGE allow
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb shell mkdir -p /sdcard/Music/ReelioTest /sdcard/Movies/ReelioTest
adb push /tmp/media/music/. /sdcard/Music/ReelioTest/
adb push /tmp/media/movies/. /sdcard/Movies/ReelioTest/
adb shell content call --method scan_volume --uri content://media --arg external_primary || true
sleep 8
adb logcat -c

adb shell am start -n $P/.MainActivity; shot 01-home 8
tap "Music"; shot 02-music 3
tap "Videos"; shot 03-videos 3
tap "Playlists"; shot 04-playlists 2
tap "Browse"; shot 05-browse 2
tap "Settings"; shot 06-settings 2
adb shell input keyevent KEYCODE_BACK
tap "Music"; sleep 2
tap "Test Song MP3"; shot 07-song-playing 5
state "mp3 from list"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Music/ReelioTest/song6.wma" -t audio/x-ms-wma -n $P/.MainActivity; shot 09-wma-nowplaying 6
state "wma via VLC"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Music/ReelioTest/song2.ogg" -t audio/ogg -n $P/.MainActivity; sleep 4; state "ogg"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Music/ReelioTest/song5.flac" -t audio/flac -n $P/.MainActivity; sleep 4; state "flac"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Music/ReelioTest/song7.opus" -t audio/ogg -n $P/.MainActivity; sleep 4; state "opus"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Movies/ReelioTest/clip2%20subtitles.mkv" -t video/x-matroska -n $P/.video.VideoActivity; shot 10-mkv-ac3-subtitles 9
state "mkv ac3 + srt"
adb shell input tap 540 1200; shot 11-video-controls 1
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Movies/ReelioTest/clip5.wmv" -t video/x-ms-wmv -n $P/.video.VideoActivity; shot 12-wmv-vlc 9
state "wmv via VLC"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Movies/ReelioTest/clip3.webm" -t video/webm -n $P/.video.VideoActivity; shot 13-webm 8
state "webm"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Movies/ReelioTest/clip6.avi" -t video/x-msvideo -n $P/.video.VideoActivity; shot 14-avi 8
state "avi"
adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Movies/ReelioTest/clip4%20square.mov" -t video/quicktime -n $P/.video.VideoActivity; shot 15-mov 8
state "mov"
adb shell input keyevent KEYCODE_HOME; sleep 5
state "after HOME (should still be playing)"
adb shell input keyevent KEYCODE_SLEEP; sleep 5
state "screen off (should still be playing)"
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard || true
adb shell am start -n $P/.MainActivity; sleep 3
adb shell cmd statusbar expand-notifications; shot 16-notification 2
adb shell cmd statusbar collapse
timeout 30 adb logcat -d > $R/logcat-full.txt
grep -E "FATAL EXCEPTION|AndroidRuntime|E ExoPlayer|E VLC|Reelio|ANR in|libvlc" $R/logcat-full.txt | grep -v "^--" | head -300 > $R/logcat-important.txt
gzip -f $R/logcat-full.txt
echo done >> $R/notes.txt
