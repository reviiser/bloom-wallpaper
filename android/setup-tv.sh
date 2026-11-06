#!/bin/bash
# One-time setup from a computer on the same network as the TV: installs Bloom and makes it the screensaver.
#   ./setup-tv.sh 192.168.1.50                install and set as the screensaver
#   ./setup-tv.sh 192.168.1.50 --undo-amazon  put a Fire TV back on Amazon's own screensaver
set -e
here="$(dirname "$(readlink -f "$0")")"
adb_tool="$HOME/.local/share/bloom-android/sdk/platform-tools/adb"
[ -x "$adb_tool" ] || adb_tool=adb
tv="${1:?usage: setup-tv.sh TV_IP_ADDRESS}"
"$adb_tool" connect "$tv:5555"
echo "If the TV asks whether to allow this computer, choose Allow, then press Enter here."
read -r _
if [ "$2" = "--undo-amazon" ]; then
    "$adb_tool" -s "$tv:5555" shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService
    echo "Back on Amazon's screensaver. Bloom is still installed as an app."
    exit 0
fi
"$adb_tool" -s "$tv:5555" install -r "$here/dist/bloom-by-reviiser.apk"
"$adb_tool" -s "$tv:5555" shell settings put secure screensaver_components com.reviiser.bloom/.BloomDream
echo "Done. Bloom is now the screensaver: $("$adb_tool" -s "$tv:5555" shell settings get secure screensaver_components)"
