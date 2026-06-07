## OverlayRec

<p align="center">
  <img src="OverlayRec_logo.png" alt="OverlayRec logo" width="220" />
</p>

<p align="center">
  Trigger AR screenshots, AR recording, HUD-only screen recording, and Bluetooth video export directly from Rokid glasses.
</p>

<p align="center">
  <a href="https://ko-fi.com/M8R61ZTXMI" target="_blank">
    <img height="36" style="border:0px;height:36px;" src="https://storage.ko-fi.com/cdn/kofi4.png?v=6" border="0" alt="Buy Me a Coffee at ko-fi.com" />
  </a>
</p>

---

It runs directly on the glasses as an Android Accessibility Service and listens for a gesture combo:

```text
two finger slide left left right right
```

When the combo is detected, OverlayRec shows a small on-glass overlay menu with three actions:

- AR Screenshot
- AR Record
- HUD Record

Press OK to confirm immediately, or wait 5 seconds for auto-confirm.

## Screenshots

<p align="center">
  <img src="OverlayRec-screenshot1.jpg" alt="OverlayRec main screen" width="280" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="OverlayRec-screenshot2.jpg" alt="OverlayRec HUD video library" width="280" />
</p>
<p align="center">
  <em>Main app &middot; HUD video library</em>
</p>

---

## Features

- Launch AR Screenshot from the glasses.
- Launch AR Record from the glasses.
- Launch HUD-only screen recording from the glasses, without opening the AR camera recorder.
- Browse HUD recordings saved on the glasses.
- Select, send, and delete one or more HUD recordings.
- Send recordings through the glasses' built-in Android Bluetooth file transfer.
- Queue auto-delete after Bluetooth sharing, with Android confirmation when storage rules require it.
- Keep the current app open underneath the overlay.
- Auto-confirm the selected action after 5 seconds.
- Stop AR recording with the physical button on the glasses.
- Stop HUD recording by selecting HUD Record again.
- Restore volume after the two-finger gesture combo.
- Debounce Rokid paired swipe key events so one physical swipe moves one item.

## How It Works

OverlayRec listens for Rokid's two-finger swipe broadcasts:

```text
com.android.action.ACTION_TWO_FINGER_SWIPE_BACK
com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD
```

After the gesture combo, it sends Rokid's internal scene command:

```text
com.rokid.os.master.assist.server.cmd
cmd_type=control_scene
scene=ar_picture | mix_record
open=true
```

HUD Record uses Rokid's screen recorder receiver instead of the AR camera recorder:

```text
com.rokid.yodaos.action.SCREENRECORD_ON
com.rokid.yodaos.action.SCREENRECORD_OFF
target package=com.rokid.os.master.screenstream
foreground receiver flag=true
```

Hi Rokid can still be used afterward to import/process the generated media.

## Controls

```text
Open menu: two finger slide left left right right
Select action: two finger slide left/right
Confirm: OK
Auto-confirm: wait 5 seconds
Stop AR Record: physical glasses button
Stop HUD Record: open menu and select HUD Record again
Foreground screens: simple left/right swipe moves focus, OK activates
```

Simple left/right key events do not open the global menu; they are reserved for foreground app navigation.

## Install

Build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Install it on the glasses:

```powershell
adb -s <glasses_serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

Enable the Accessibility Service on the glasses:

```powershell
adb -s <glasses_serial> shell settings put secure enabled_accessibility_services com.rokid.overlayrec/com.rokid.overlayrec.OverlayRecService
adb -s <glasses_serial> shell settings put secure accessibility_enabled 1
```

You can also open the app and choose `Accessibility service`.

## Notes

OverlayRec is experimental and built specifically for Rokid Glasses firmware that exposes the two-finger swipe actions above.

The app does not merge camera and HUD videos itself. Rokid's own AR Record flow creates the media, and Hi Rokid handles import/processing.
HUD Record is separate: it records the glasses display to `/sdcard/ScreenRecorder/` and does not start the AR camera recording scene.

## HUD Video Library

Open the main OverlayRec app and choose `HUD Videos`.

- Select one or more recordings in the list.
- Navigate with simple left/right swipes; press OK to select a video or activate the focused action.
- `Send` opens Android Bluetooth file transfer for the selected videos.
- `Delete` removes the selected videos from the glasses. Android may show a system confirmation prompt depending on firmware storage rules.
- `Auto DELETE : ON/OFF` schedules selected videos for deletion 10 minutes after Bluetooth sharing is opened. It deletes directly when Android allows it; if the system recorder owns the file, Android shows a delete confirmation prompt instead of failing silently. It is off by default because Bluetooth transfers can be slow.

This uses the glasses' built-in Bluetooth OPP sender, so it does not require a phone companion app. The receiving phone must support normal Bluetooth file receive; Android phones usually do, iPhones generally do not.

## License

OverlayRec is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
