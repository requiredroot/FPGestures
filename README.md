# FPGestures — root-only fingerprint gestures for begonia

Maps the Goodix fingerprint sensor gestures (swipe / tap / double-tap /
long-press / heavy-press) to system actions. **Requires root, nothing else:**
no AccessibilityService, no device admin, no Shizuku.

## How it works

The begonia Goodix driver (`goodix_5658`, `gf_spi_tee.c`) already emits each
gesture as a Linux key event on its own input device, `uinput-goodix`:

| Gesture      | Key event       |
|--------------|-----------------|
| Swipe down   | `KEY_DOWN`      |
| Swipe up     | `KEY_UP`        |
| Swipe left   | `KEY_LEFT`      |
| Swipe right  | `KEY_RIGHT`     |
| Single tap   | `KEY_VOLUMEDOWN`|
| Double tap   | `KEY_VOLUMEUP`  |
| Long press   | `KEY_SEARCH`    |
| Heavy press  | `KEY_CHAT`      |

A foreground service streams `su -c getevent -lt <device>`, maps DOWN
transitions to gestures, and executes the configured action as root
(`input keyevent …`, `cmd statusbar …`, begonia torch sysfs). No kernel
changes needed.

> Note: single tap arrives as `KEY_VOLUMEDOWN` and double tap as
> `KEY_VOLUMEUP`, so the system volume UI may flash when those gestures
> fire. This is how the stock driver maps them; a future update may add an
> optional evdev grabber to swallow the originals.

## Build

APKs are built by CI (`.github/workflows/build.yml`): every push to `main`
produces an `fpgestures-release-apk` artifact. To build locally:

```sh
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

## Use

1. Install the APK, grant root when prompted.
2. Open FPGestures, flip the toggle, pick an action per gesture.
3. Verify the pipe is live (optional, needs root):
   `su -c 'getevent -l /dev/input/eventX'` on the `uinput-goodix` device,
   then swipe/tap the sensor.
