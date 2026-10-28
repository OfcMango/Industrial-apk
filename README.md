# TouchTalk 2.0 — Android + ESP32

TouchTalk is a native Android app for two-way communication with an original ESP32 / ESP32-WROOM over Bluetooth Classic SPP. This project uses native Android views (no Jetpack Compose), Kotlin 1.9.24, Android Gradle Plugin 8.6.1, Java 17, and Gradle 8.7 in GitHub Actions.

## Features
- Premium dark interface with Home, Communicate, and Device screens.
- Pair and connect to a Bluetooth Classic ESP32 named `TouchTalk`.
- Incoming `TEXT:` messages are displayed and spoken automatically by Android TextToSpeech by default.
- Incoming `DRAFT:` updates refresh the live keypad draft.
- Speech recognition uses the Android speech-recognition service. When the user taps **Start speech recognition** and the recognition service returns a result, TouchTalk converts the recognized text to Braille masks and sends each cell automatically when Automatic Braille transmission is enabled.
- Typed text can be previewed and transmitted with an explicit control.
- Device screen includes automatic voice/transmission preferences and LED output diagnostics.
- Companion ESP32 firmware is included under `esp32_firmware/`.
- GitHub Actions builds a debug APK and uploads it as `TouchTalk-apk`.

## Build the APK with GitHub
1. Create a clean GitHub repository.
2. Extract the ZIP and upload **all contents** of `TouchTalk_Industrial`, including the hidden `.github` folder.
3. Commit to `main` or `master`, or open Actions and run **Build TouchTalk APK** manually.
4. When the workflow succeeds, open the run and download the `TouchTalk-apk` artifact.

## Pair and use
1. Flash `esp32_firmware/TouchTalk_ESP32.ino` to an original ESP32/ESP32-WROOM board.
2. Pair the Bluetooth device named `TouchTalk` in Android Bluetooth settings.
3. Open the app, choose **Connect TouchTalk device**, and select the paired ESP32.
4. On the Communicate screen, incoming `TEXT:` messages are read aloud automatically by default.
5. Tap **Start speech recognition** to speak a message. When the phone's speech service returns a recognized result, it is automatically sent as `CELL:<mask>` commands if automatic transmission is enabled.

## Bluetooth protocol
- ESP32 → app: `DRAFT:<text>`, `TEXT:<completed text>`, `STATUS:<message>` each terminated with newline.
- App → ESP32: `CELL:<0..63>` to set six output pins, `DONE` at the end of a sequence, `CLEAR` to turn all outputs off.
- Braille dot bit positions: dot 1 is bit 0 through dot 6 bit 5.

## Pin mapping
- Key inputs: dots 1–6 = GPIO 16, 17, 18, 19, 21, 22.
- Space = GPIO 23; Backspace = GPIO 25.
- LED output test pins = GPIO 4, 5, 26, 27, 32, 33.

**Electrical safety:** Never connect an electromagnet or solenoid directly to ESP32 GPIO. Use suitable transistor/MOSFET drivers, flyback protection, and a separate correctly rated supply. The included output pins are for LED tests; confirm your exact board and wiring before connecting hardware.

## Important limitations
- Speech recognition is initiated by a user tap and uses the phone's installed recognition service; it is not always-listening in the background. After recognition completes, sending is automatic. Android/device settings can affect offline speech availability.
- This source project has not been compiled or tested on a physical Android phone in this environment. GitHub Actions must succeed before considering the APK build verified; physical Bluetooth and hardware testing are still required.
- This is a development/debug APK workflow, not a signed production release pipeline.
