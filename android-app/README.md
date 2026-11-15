# ClasscardHelper Android app

A minimal Kotlin Android starter. The app asks the user to enable its AccessibilityService from Android Settings. Once enabled, it shows a visible **CH** overlay. Tapping CH opens user-triggered **Memorize**, **Recall**, and **Spell** choices.

The service does not retrieve screen content, hide itself, or automate actions in other apps. Each menu choice only updates the visible CH label and shows a confirmation toast.

## Build

From this directory, run `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
