# SuperFloat - Floating Notepad App

A persistent floating notepad widget for Android—type notes over any app, drag it around, stays open even when you switch screens or leave the app. No digging for notes, just quick jots.

## Features

- **Draggable bubble**: 56dp circle with badge for word count
- **Tap to expand**: Small notepad window (~300×320dp) with text editor, live word/char count, clear button
- **Minimize back to bubble**
- **Themes**: Light/Dark/System
- **Stats**: Total notes/words/chars
- **Local storage**: SharedPreferences for auto-save
- **Auto-start**: Service runs in foreground
- **Share/copy notes**: Easy note management

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + XML layouts
- **Overlay**: Foreground Service + WindowManager (TYPE_APPLICATION_OVERLAY)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Permissions Needed

- `android.permission.SYSTEM_ALERT_WINDOW` — user grants in Settings > Apps > Special access > Display over other apps
- `android.permission.FOREGROUND_SERVICE` — for persistent service
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` — for Android 14+
- `android.permission.POST_NOTIFICATIONS` — for Android 13+

## Built From

This app combines features from three open-source floating widget libraries:
- [andcoe/android-floating-widget](https://github.com/andcoe/android-floating-widget)
- [dofire/Floating-Bubble-View](https://github.com/dofire/Floating-Bubble-View)
- [luiisca/floating-views](https://github.com/luiisca/floating-views)

## Setup

1. Clone this repo
2. Open in Android Studio
3. Grant overlay permission on first run
4. Build/run—test bubble over home screen

## License

MIT License
