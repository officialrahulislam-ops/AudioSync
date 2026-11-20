# AudioSync Phase 3 — Plan and Execution

## Overview

Phase 3 executes the critical tasks:
1. Fix Android Gradle build
2. Test Windows audio capture with real audio playing
3. Build and install Android APK on phone
4. End-to-end AudioSync streaming test

## 1. Fix Android Gradle Build

### Current Problem
`gradlew` fails: "Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain"

### Investigation
Check gradle/wrapper directory and gradle.properties:

```bash
cd AudioSync/AndroidReceiver
ls -la gradle/wrapper/
ls -la gradle.properties
grep "distributionUrl" gradle-wrapper.properties
cat gradle/wrapper/gradle-wrapper.properties
```

### Root Cause Analysis
1. **Missing Gradle binary**: gradle/wrapper/ contains only properties file, no gradle executable
2. **Wrong distributionUrl format**: Should be `file:/tmp/gradle-8.5-bin.zip`
3. **Missing gradle wrapper script**: gradle/wrapper/gradle not present

### Solution
The gradle/wrapper/gradle file exists but doesn't execute. Problem:
- gradle/wrapper/gradle file size is 8757 bytes - too large for a shell script
- Should be a shell script (executable) to run Gradle

### Fix
1. **Check current gradle/wrapper/gradle file content**:
```bash
cd AudioSync/AndroidReceiver
cat gradle/wrapper/gradle
```

2. **If it's not a shell script**, replace with proper wrapper:
   - Use gradle-8.5-bin distribution from /tmp
   - Create proper gradle wrapper script
   - Ensure it loads org.gradle.wrapper.GradleWrapperMain

3. **Verify gradle.properties**:
```bash
cat gradle/wrapper/gradle-wrapper.properties
```

4. **Test gradle wrapper**:
```bash
cd AudioSync/AndroidReceiver
./gradlew wrapper --gradle-version 8.5
```

### Expected Result
- `gradle/wrapper/gradle` should be executable shell script
- `gradle/wrapper/gradle-wrapper.properties` should point to correct Gradle distribution
- `gradlew` should run and generate gradle/wrapper/*.jar files

## 2. Verify Android Project Structure

### Check MainActivity Conflicts
```bash
cd AudioSync/AndroidReceiver
ls -la app/src/main/java/com/audiosync/receiver/
```

### Current State
- `MainActivity.java` - implements StatusListener and UDP receiver logic
- `MainActivity.kt` - contains UI setup and discovery listener
- Both present but likely both needed: Java for audio processing, Kotlin for UI

### Verify Dependencies
Check `build.gradle`:
```bash
cat AndroidReceiver/build.gradle
grep "implementation" AndroidReceiver/build.gradle
grep "compileOnly" AndroidReceiver/build.gradle
```

### Verify Opus Decoder
- Dependency: `org.restcomm.media.core.codec.opus:opus-java:8.0.0-20`
- Check if opus-java.jar is available in Maven Central
- Ensure AudioTrack initialization works

### Verify Permissions
Check AndroidManifest.xml:
```bash
cat AndroidReceiver/app/src/main/AndroidManifest.xml
grep "uses-permission" AndroidReceiver/app/src/main/AndroidManifest.xml
```

### Cleanup Redundant Files
Only remove files if their purpose is verified:
- `MainActivity.kt` is needed for UI setup
- Keep both .java and .kt files if they serve different purposes

## 3. Test Windows Audio Capture

### Current Issue
Windows captures 0 bytes when no system audio is playing (WASAPI loopback behavior).

### Test Plan
1. **Start AudioStreamer with debugging**:
```bash
cd WindowsSender
"C:\Program Files\dotnet\dotnet.exe" run --project AudioSync.Win.csproj 127.0.0.1
```

2. **Generate system audio on Windows**:
   - Play music or video through speakers
   - Verify Windows audio is actually playing

3. **Monitor capture callbacks**:
```bash
cd WindowsSender
cat AudioStreamer.cs | grep -n "OnDataAvailable\|CaptureCallbacks"
```

4. **Verify captured format**:
```csharp
// In WasapiCaptureTest.cs, check WaveFormat:
if (capture != null)
{
    Console.WriteLine($"Format: {capture.WaveFormat.SampleRate}Hz, {capture.WaveFormat.Channels}ch, {capture.WaveFormat.BitsPerSample} bits ({capture.WaveFormat.Encoding})");
}
```

### Expected Behavior
- AudioStreamer should show: "Format: 48000Hz 2ch, 32 bits (IeeeFloat)"
- CaptureCallbacks should increment when audio is playing
- captured data should be non-zero when system audio is playing

### Troubleshooting
If still no capture:
1. Verify WASAPI loopback device is correct
2. Check microphone/speaker permissions
3. Test with different audio sources
4. Verify AudioCapture device is not muted

## 4. Build Android APK

### After Gradle Fix
```bash
cd AudioSync/AndroidReceiver
./gradlew assembleDebug
```

### Expected Output
- `app/build/outputs/apk/debug/app-debug.apk`
- No Gradle wrapper errors
- Compile success with warnings only

### Install on Physical Device
1. Enable USB debugging on Android phone
2. Connect phone via USB to computer
3. Use Android Debug Bridge (ADB):
```bash
adb devices
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.audiosync.receiver/com.audiosync.receiver.MainActivity
```

## 5. Test AudioSync Pipeline

### Start Windows Sender
1. **Configure Windows IP**:
   - Use loopback: `127.0.0.1` (test on same machine)
   - Or phone IP: `192.168.x.x` (test on same Wi-Fi)

2. **Start Windows AudioStreamer**:
```bash
cd WindowsSender
"C:\Program Files\dotnet\dotnet.exe" run --project AudioSync.Win.csproj "192.168.1.100"
```

3. **Monitor Windows output**:
   - Check "FramesSent", "BytesSent" in AudioStreamer stats
   - Verify RTP packets are being transmitted

### Start Android Receiver
1. **Launch Android app** after installation
2. **Verify UI** shows "Ready. Enter sender IP and press Connect."
3. **Enter Windows IP** and press "Connect"
4. **Check Android output**:
   - Verify "Receiving on port 19827"
   - Monitor packet reception stats
   - Verify AudioTrack is playing

### Expected Results
- Audio captured on Windows should appear on Android
- No crashes or null pointer exceptions
- Audio should play through Android speakers
- Minimal latency with stable playback

## 6. Debugging Pipeline

### Windows Side Debug
```csharp
// In AudioStreamer.cs, add detailed logging:
if (encodeLen <= 0)
{
    EncodeErrors++;
    Console.WriteLine($"[ERROR] Encoding failed at {DateTime.Now:HH:mm:ss.fff}");
}
else
{
    Console.WriteLine($"[INFO] Frame {FramesSent}: encoded {encodeLen} bytes, sent {sent} bytes");
}
```

### Android Side Debug
```java
// In AudioReceiver.java, add logging:
if (pcmData != null && pcmData.length > 0) {
    packetsDecoded++;
    Log.d(TAG, "Decoded packet: " + pcmData.length + " samples");
} else {
    decodeErrors++;
    Log.e(TAG, "Decode failed for packet " + packetsReceived);
}
```

### Collect Actual Metrics
For verification and reporting:
1. **Captured audio bytes**: In WasapiCaptureTest
2. **Encoded packet sizes**: In AudioStreamer
3. **UDP transmission**: In AudioStreamer
4. **UDP reception**: In AudioReceiver
5. **Decode success rate**: In AudioReceiver
6. **Playback underruns**: In JitterBuffer

## Completion Criteria

Phase 3 is complete when ALL of the following are true:

✅ **Android Build**: Gradle wrapper works and produces debug APK

✅ **APK Installation**: APK installs successfully on my Android phone

✅ **App Launch**: App launches and displays "Ready. Enter sender IP and press Connect."

✅ **Audio Capture**: Windows successfully captures system audio when audio is playing

✅ **Network Transmission**: Windows successfully transmits audio via UDP

✅ **Android Reception**: Android successfully receives UDP packets

✅ **Audio Playback**: Android successfully decodes and plays audio through speakers

✅ **Real-world Test**: I can hear Windows audio through my Android phone

## Phase 3 Execution Priority

1. **Fix Gradle wrapper** (30-60 minutes)
2. **Build and install Android APK** (15-30 minutes)
3. **Test Windows audio capture with real audio** (15-30 minutes)
4. **Run end-to-end test** (30-60 minutes)
5. **Debug and refine** (20-45 minutes)

## Final Report

After Phase 3, I will provide:

1. **Gradle fix**: What was wrong and how it was fixed
2. **APK build**: Screenshot and file size
3. **Installation**: Confirmation APK installed on phone
4. **App verification**: UI and functionality check
5. **Windows audio**: Capture callback counts and byte counts
6. **Network metrics**: Frames sent, bytes transmitted, packets received
7. **Playback results**: Audio continuity, underruns, quality
8. **Known issues**: What's not working and next steps

This will be an honest assessment of the actual state, not a fabricated success claim. Phase 4 will follow based on actual results.

Note: Phase 3 is critical for validation, so careful investigation and honest reporting of issues are essential.