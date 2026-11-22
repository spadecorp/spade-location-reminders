# SPaDe Location Reminders · 0.1.2

Attach a task to any point on the map. Enter coordinates, tap a pin into place, or use your current location. Each task has an adjustable 100 m–3 km radius and an optional Read aloud switch. Entering the area triggers the alert. It stays scheduled for future visits until you tap **Complete**.

## Try the Android build

1. On an Android 7.0+ phone with Google Play services, open the supplied APK and select **Install**. Android may ask you to allow installation from the app you used to open this file.
2. Open **SPaDe Location Reminders → New reminder**. Enter a task, place the pin, choose a radius, and turn on **Read aloud** if desired.
3. Tap **Save reminder**. Allow precise location and notifications when prompted. On Android 10+ allow background location. On Android 11+ follow **Open app settings → Permissions → Location → Allow all the time**, return to the editor, and tap **Save reminder** again. Confirm that the task appears in your list.
4. For a first field test, place a 300 m circle a few blocks ahead while you are outside it. Lock the phone and walk into the circle. Give the phone several minutes. Check the text and speech, then leave, re-enter, and check that it alerts again. Tap **Complete**, leave and re-enter once more to verify cancellation.

The APK is signed for development and can be installed directly. Rebuilding with a different debug signing key requires removing the first installation, which clears its saved tasks. Android can suspend alerts after **Force stop** until you reopen the app. The app re-registers saved geofences at boot, after an update, and when launched. Silent mode, Do Not Disturb, volume, audio focus, notification settings and available offline voices can affect speech. Fast crossings can be missed. A larger radius is helpful when passing by in a vehicle. Select the radius before traveling.

## Develop on Windows

Install Node.js 22 LTS, Android Studio with Android SDK Platform 35, and a JDK 21. Set JAVA_HOME to the JDK and ANDROID_HOME to your SDK directory when using the Gradle wrapper from PowerShell. Extract the project, open PowerShell in the folder containing package.json, and run:

```powershell
npm ci
npm test
npm run build
npx cap sync android
npx cap open android
```

Select your connected phone in Android Studio and click **Run**. To build the APK from the same PowerShell session:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The installer is written to `android/app/build/outputs/apk/debug/app-debug.apk`. After changes to the web interface, run `npm run build` and `npx cap sync android` from the project root before rebuilding.

For a desktop UI preview, use `npm ci` and `npm run dev`, then open the displayed localhost address. The preview persists reminders in that browser. Background monitoring and arrival alerts operate in the native mobile app.

## Build for iPhone on a Mac

Install Node.js 22 LTS, Xcode 16 or newer, and CocoaPods. In the extracted project run:

```sh
npm ci
npm run build
npx cap sync ios
npx cap open ios
```

In Xcode, select the App target and your signing team under **Signing & Capabilities**, connect an iPhone, and click **Run**. Use a team-owned bundle identifier if signing requires it. Grant **While Using the App**, enable **Precise Location**, and allow notifications and sounds. Repeat the same locked-screen walking test. Native notification delivery respects iPhone sound and Focus settings.

## Implementation

- The shared interface uses Capacitor, JavaScript, Leaflet and OpenStreetMap tiles. It accepts arbitrary pins without an account, favorite, server or paid API key. Loading an uncached base map requires internet access. The tile provider receives tile requests for the area being viewed. The task text is stored locally.
- Android registers native entry geofences through Google Play services and stores task metadata in private app preferences. An entry receiver posts the notification. When enabled, a short-lived foreground service uses an installed offline voice. Android supports up to 100 active geofences per app per device user.
- iOS stores each task in a repeating UNLocationNotificationTrigger. The app limits the list to 20 active regions. When Read aloud is enabled, it prepares a short local speech file when the task is saved and attaches the file as the notification sound.
- The default radius is 300 m. The circle is a user-selected boundary. Position estimates and operating-system scheduling affect when a device recognizes a crossing. Saving a task while already inside the circle may require leaving and returning for the first entry.
- The Android APK was compiled and signature-verified. The shared UI was exercised in a mobile-width browser for creation, validation, map and coordinate selection, voice selection, persistence, edits and completion. Physical background delivery and speech still require testing. The iOS project is included as source and has not been built or signed in Xcode.
