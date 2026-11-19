# AudioSync Architecture Plan

## Overview

A lightweight, local-only one-way audio streaming system from Windows PC to Android phone over Wi-Fi.

## Technology Stack

### Windows Sender
- **Language**: C# .NET 8 (Windows Forms)
- **Audio capture**: NAudio library with WASAPI loopback
- **Codec**: Opus via .NET wrapper (Concentus or OpusSharp)
- **Network**: UDP with RTP-like headers (seq num, timestamp)
- **Discovery**: Simple UDP broadcast/packet echo or manual IP; avoid mDNS initially due to Windows complexity
- **Build**: Windows EXE via .NET publish (single-file or default)

### Android Receiver
- **Language**: Kotlin (no Jetpack Compose initially, use standard UI)
- **Minimum SDK**: 一路 = 21 (Android 5.0)
- **Audio reception**: Java UDP + AudioTrack, with Opus decoding via `org.restcomm.media.core.codec.opus:opus-java` (Maven Central)
- **Codec**: Opus decoding — using Java wrapper, fallback to system codec
- **Network**: UDP with same protocol as Windows sender
- **Build**: Android APK via Gradle (expect Gradle/Android SDK may need installation)

### Shared Protocol
- Lightweight UDP-based protocol
- RTP-like: sequence number, timestamp, SSRC
- Opus frames per packet (typically 20ms)
- No cloud, no accounts, local Wi-Fi only

## Audio Pipeline (Windows → Network → Android)

1. **Capture**: WASAPI loopback on Windows stereo mix → 16-bit PCM, 48 kHz, stereo
2. **Encode**: Opus encoding (20ms frames = 960 samples at 48kHz)
3. **Package**: RTP packet with seq/ts/SSRC headers
4. **Transport**: UDP to Android IP:port
5. **Receive**: UDP decode, buffer into jitter buffer
6. **Decode**: Opus decode to 16-bit PCM
7. **Play**: AudioTrack at 48kHz stereo

## Communication Protocol

### Device Discovery
- mDNS/AVAHI/Bonjour to discover "audiosync-receiver._audiocast.local"
- Fallback: manual IP entry dialog

### Connection Establishment
1. Sender sends UDP "hello" packet to discovery port (19826)
2. Receiver responds with "handshake" UDP packet containing its IP/port/capabilities
3. Sender confirms connection
4. Both sides switch to streaming mode

### Audio Packet Format (UDP/RTP)
```
+---------------+---------------+---------------+---------------+
|   Seq Num (2) |   Timestamp (4)  |   SSRC (4)   |   Opus data (variable)
+---------------+---------------+---------------+---------------+
```

- Seq Num: increments per packet
- Timestamp: in ms, increments by frame duration (20ms)
- SSRC: random 32-bit identifier for the stream

### Packet Loss Handling
- No NACKs or retransmissions (keeps latency low)
- Jitter buffer at receiver: 100-200ms target
- Packets older than buffer window are discarded
- If buffer underrun → last audio sample held (click-free)

### Jitter Buffer
- Adjustable target: 40–80ms base, expandable if network quality is poor
- Fixed-size incoming packet buffer sorted by timestamp
- Playback pointer advances at constant rate (48kHz)
- If gap > threshold → mute or interpolate
- If buffer draining → wait for next packet

## Performance Goals

1. **Latency**: 150-250ms end-to-end (capture → encode → network → decode → play)
2. **Crackling**: Jitter buffer prevents >5ms gaps
3. **CPU**: <5% on each side for encoding/decoding/playback
4. **Bandwidth**: ~64-128 kbps for stereo Opus at 48kHz
5. **Reliability**: Works on typical home Wi-Fi; graceful degradation if Wi-Fi drops

## Repository Structure

```
AudioSync/
├── WindowsSender/          # C# Windows Forms app
│   ├── AudioSync.Win.csproj
│   ├── Program.cs
│   ├── MainForm.cs / .cs
│   ├── Properties/
│   └── bin/ (output)
├── AndroidReceiver/        # Kotlin Android app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/audiosync/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── discovery/
│   │   │   │   ├── receiver/
│   │   │   │   └── audio/
│   │   │   │   ├── Adapter.kt (UI adapters)
│   │   │   │   └── constants.kt
│   │   │   ├── res/
│   │   │   │   ├── layouts/
│   │   │   │   └── drawable/
│   │   │   └── java/com/audiosync/
│   │   └── build.gradle
│   ├── gradle/
│   └── settings.gradle
├── protocol/               # Shared protocol docs
│   └── protocol-spec.md
├── README.md
├── .github/ (CI if needed)
├── .gitignore
├── LICENSE
└── TODO.txt
```

## Implementation Plan

### Phase 1: Foundations (Week 1)
- [x] Init repo, MEMORY.md, MEMORY.md index
- [ ] Set up WindowsSender C# project with NAudio
- [ ] Set up AndroidReceiver Kotlin/Gradle project
- [ ] Define shared protocol spec
- [ ] Verify dependencies can be installed and built

### Phase 2: Windows Sender Core (Week 2)
- [ ] WASAPI loopback capture (NAudio)
- [ ] Opus encoding (find .NET Opus library or implement basic PCM→Opus)
- [ ] UDP packet sending with RTP headers
- [ ] mDNS discovery implementation
- [ ] UI: discovery list, connect, start/stop streaming

### Phase 3: Android Receiver Core (Week 3)
- [ ] UDP packet receiving
- [ ] Opus decoding (find Android-compatible library)
- [ ] AudioTrack playback at 48kHz stereo
- [ ] Jitter buffer implementation
- [ ] UI: connection status, playback controls

### Phase 4: Integration Testing (Week 4)
- [ ] Connect Windows → Android on same Wi-Fi
- [ ] Test stable playback without crackling
- [ ] Test Wi-Fi interruption handling
- [ ] Refine latency and bandwidth

### Phase 5: Polish (Week 5)
- [ ] Build standalone Windows EXE (.NET publish)
- [ ] Build release APK
- [ ] Documentation: protocol, setup instructions
- [ ] Final testing and bug fixes

## Key Decisions & Rationale

1. **Opus codec**: Best quality/bandwidth/latency tradeoff for real-time streaming. More reliable than Speex, more efficient than MP3/AAC at low bitrates.

2. **UDP over TCP**: Real-time audio hates reordering/head-of-line blocking. UDP with jitter buffer is the right tradeoff.

3. **WASAPI loopback**: Captures system audio output without needing microphone setup. This is the core requirement.

4. **48 kHz / stereo**: Standard high-quality streaming sample rate. Matches Android AudioTrack configuration.

5. **mDNS discovery**: Zero-config experience when both devices are on same network. Fallback manual IP for guest networks or complex Wi-Fi setups.

6. **No internet, no cloud**: Pure local network — matches the project requirements exactly.

## Risks & Mitigations

- **Opus availability on .NET**: May need P/Invoke or find a managed wrapper. Mitigate by evaluating early.
- **Opus availability on Android**: Use `org.opus-codec:opusandroid` or build native wrapper. Mitigate by checking Gradle availability.
- **WASAPI loopback permissions**: Windows may prompt for audio capture permission. Mitigate by clear UI explaining why.
- **Wi-Fi network variability**: Jitter buffer handles this; fallback to manual IP if mDNS fails.
- **Latency accumulation**: Keep fixed frame sizes, no buffering on sender side beyond capture ring.

Next: Present this plan to the user for approval before coding begins.