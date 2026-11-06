# Bloom by Reviiser on your TV

A living pixel-art forest as a screensaver, for Fire TV and Android TV. Free, about 200 KB, no account, no network use. Each TV grows its own forest.

You need the file `bloom-by-reviiser.apk`.

## Fire TV

Fire TV has no menu for choosing a screensaver, so this takes a one-time setup from a computer on the same network.

1. On the TV, open **Settings > My Fire TV > About** and click the device name seven times. This unlocks developer options.
2. In **Settings > My Fire TV > Developer Options**, turn on **ADB Debugging**.
3. Find the TV's address in **Settings > My Fire TV > About > Network**.
4. On the computer, with [Android's platform tools](https://developer.android.com/tools/releases/platform-tools) installed:

```bash
adb connect TV_ADDRESS:5555
```

Choose **Allow** when the TV asks.

```bash
adb install bloom-by-reviiser.apk
```

```bash
adb shell settings put secure screensaver_components com.reviiser.bloom/.BloomDream
```

The setting survives restarts. A Fire OS update could reset it, and then the last command puts it back.

To go back to Amazon's screensaver:

```bash
adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService
```

## Android TV and Google TV

1. Install the APK with any file manager or sideloading app, or with `adb install` as above.
2. Open **Settings > Device Preferences > Screen saver** and choose **Bloom by Reviiser**.

Some newer Google TV models hide this choice. On those, the `adb shell settings put` command above does the same job.

## Using it

- **As the screensaver:** it starts when the TV has been idle, and any button returns you to what you were doing.
- **As an app:** open Bloom by Reviiser from your apps to see the forest full screen. Press **Select** for the menu.

In the menu:

| Button | What it does |
|---|---|
| Arrows | Move and adjust |
| Up, from the top of a page | Reach the page tabs, then Left and Right change page |
| Rewind and Fast Forward | Change page directly |
| Select | Choose. On the Animals page, it lets you step through each individual |
| Back | Close the menu, then leave the app |

**Forest seed:** press Select, then Up and Down dial each digit and Left and Right move between digits. Select grows that forest. The forest you leave is kept.

**Your place:** the stars use your TV's timezone. To be more exact, press Select on this row and dial in your latitude and longitude, with north and east as plus.

How long the forest shows depends on your TV's own sleep setting, since a TV that goes to sleep turns the screensaver off too. Opened as an app, Bloom keeps the screen on until you leave it, whatever that setting says.
