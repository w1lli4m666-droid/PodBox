# PodBox

<a href="https://github.com/w1lli4m666-droid/PodBox/blob/main/README.md">English <img src="https://flagcdn.com/w20/gb.png" alt="United Kingdom flag" width="20"></a> |
<a href="https://github.com/w1lli4m666-droid/PodBox/blob/main/README.zh-CN.md">简体中文 <img src="https://flagcdn.com/w20/cn.png" alt="China flag" width="20"></a>

PodBox is a lightweight Android podcast player for low-end TV boxes and small touchscreen speakers. It is designed for Xiaomi TV boxes, XiaoAi touchscreen speakers, and older Android devices starting from Android 4.1.

## Features

- Search podcasts through the Apple iTunes Search API.
- Subscribe and unsubscribe podcasts.
- Recent episodes page with automatic refresh on app start.
- Stream podcast audio online without downloading episodes.
- Playback speed adjustment from 0.5x to 2.0x.
- 15-second rewind and forward, with accelerated long-press seeking.
- Play next, play all, and editable playback queue.
- Queue playback modes: sequential, repeat all, repeat one.
- Remote-control and touchscreen friendly UI.
- Automatic cache cleanup for limited-storage devices.

## Remote Control Guide

- `Up / Down / Left / Right`: move focus between tabs, episode buttons, player controls, and queue controls.
- `OK / Enter`: activate the focused button.
- `Settings / Menu`: jump focus to the bottom player bar.
- `Back` from an episode list item: return focus to the current top tab.
- `Back` from a podcast detail page: return to the subscription list.
- `Back` from the expanded playback queue: collapse the queue popup.
- `Back` from a focused top tab: move the app to the background while playback continues.
- Click the playback queue icon once to expand the queue, and click it again to collapse it.
- Long-press rewind or forward:
  - short press: seek 15 seconds
  - hold after 0.6 seconds: seek 30 seconds per step
  - hold longer than 5 seconds: seek 60 seconds per step

## Touchscreen Guide

- Tap tabs to switch pages.
- Tap the search input to use the system Chinese input method when available.
- Pull down on Recent or Subscriptions to refresh.
- Tap playback controls directly in the bottom player bar.
- Tap the queue icon to edit the current playback queue.

## Page Behavior

- Recent is the default page on app start and refreshes once automatically.
- The first visit to Subscriptions in an app session refreshes subscriptions automatically.
- Clicking the active Recent tab manually refreshes Recent.
- Clicking the active Subscriptions tab manually refreshes Subscriptions.
- Search supports Chinese, English, and pinyin candidate input.

## Playback Queue

Open the queue from the icon to the left of the rewind button.

- Top-right playback mode icon cycles through sequential, repeat all, and repeat one.
- The clear-queue icon removes all queued items.
- Each queue item has up, down, and delete controls.
- Selecting a queue item starts playback from that item.

## Build

Requirements:

- Android Studio or Android SDK
- JDK 17 or newer

Debug build:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Release APKs:

```powershell
.\gradlew.bat :app:assembleRelease
```

The release build creates:

- `armeabi-v7a`
- `arm64-v8a`
- `universal`

Current release builds are signed with the Android debug signing config so they are installable for testing. Replace this with a private release keystore before production distribution.

## Notes

PodBox uses ExoPlayer 2.x core for streaming playback and speed control while keeping the APK small enough for older devices. Podcast search comes from Apple's public iTunes Search API; episode playback uses the RSS feed URL returned by the search result.
