#!/bin/bash
# Installs the release APK, opens each main screen, saves screenshots and any crash log.
set -x
mkdir -p shots
APK=app/build/outputs/apk/release/app-release.apk
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.parag.apkslim/.MainActivity
sleep 8
adb exec-out screencap -p > shots/1-home.png
adb shell input swipe 540 1800 540 600 400; sleep 2
adb exec-out screencap -p > shots/2-home-scrolled.png
# Bottom nav: Apps tab (second of four)
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); WW=${W%x*}; HH=${W#*x}
adb shell input tap $((WW*3/8)) $((HH-90)); sleep 4
adb exec-out screencap -p > shots/3-apps.png
# Open the first app in the list
adb shell input tap $((WW/2)) $((HH*35/100)); sleep 6
adb exec-out screencap -p > shots/4-analysis.png
adb shell input swipe 540 1800 540 700 400; sleep 2
adb exec-out screencap -p > shots/5-analysis-scrolled.png
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap $((WW*5/8)) $((HH-90)); sleep 3
adb exec-out screencap -p > shots/6-history.png
adb shell input tap $((WW*7/8)) $((HH-90)); sleep 3
adb exec-out screencap -p > shots/7-settings.png
adb logcat -d -b crash > shots/crash.txt
adb logcat -d | grep -E "AndroidRuntime|FATAL|apkslim" | tail -200 > shots/log.txt
echo "===== CRASH LOG ====="; cat shots/crash.txt
echo "===== APP LOG ====="; tail -80 shots/log.txt
adb shell pidof com.parag.apkslim && echo "APP_ALIVE=yes" || echo "APP_ALIVE=no"
exit 0
