# AudioSync

**Windows PC → Android Phone** audio streaming over local Wi-Fi.

## Overview

AudioSync is a lightweight, local-only one-way audio streaming system that captures system audio from a Windows PC and plays it on an Android phone over the local network.

- **No cloud, no accounts, no internet required**
- **Low latency** (~150-250ms end-to-end)
- **Good quality** (Opus 48kHz stereo)
- **Simple setup** (manual IP or discovery)

## Architecture

```
Windows PC                          Android Phone
┌─────────────────┐    UDP/Wi-Fi    ┌─────────────────┐
│ WASAPI Loopback │ ─────────────▶ │  AudioTrack     │
│ 48kHz stereo    │   Opus packets  │  48kHz stereo   │
│    PCM          │ ◀─────────────  │   Jitter Buffer │
└─────────────────┘                 └─────────────────┘
```

### Components

| Component | Technology | Status |
|-----------|------------|--------|
| **Windows Sender** | C# .NET 8, NAudio, Concentus (Opus) | ✅ Phase 1 complete |
| **Android Receiver** | Kotlin, AudioTrack, opus-java | 🟡 Phase 1 scaffolding |
| **Protocol** | UDP, RTP-like headers | 📄 Documented |

## Quick Start

### Windows Sender

```bash
cd WindowsSender
dotnet run
```

### Android Receiver

Open `AndroidReceiver` in Android Studio, build and run on device/emulator.

## Protocol

See [protocol/protocol-spec.md](protocol/protocol-spec.md) for the complete specification.

### Key Details

- **Transport**: UDP (ports 19826 discovery, 19827 streaming)
- **Codec**: Opus 48kHz stereo, 20ms frames, ~64 kbps
- **Packet Format**: RTP-like (Seq Num, Timestamp, SSRC, Opus Data)
- **Discovery**: Simple UDP echo + manual IP entry
- **Jitter Buffer**: 40-80ms adjustable target

## Build Requirements

### Windows
- .NET 8 SDK
- Visual Studio 2022 or VS Code

### Android
- Android Studio (latest)
- Android SDK 34
- Gradle 8.5+

## Development Phases

- **Phase 1** ✅: Repository setup, Windows audio capture + Opus encode/decode, Android project scaffolding, protocol spec
- **Phase 2**: UDP networking, Windows sender streaming, Android receiver playback
- **Phase 3**: Jitter buffer, device discovery, connection management
- **Phase 4**: Integration testing, latency optimization, Wi-Fi resilience
- **Phase 5**: Polish, EXE/APK builds, documentation

## License

MIT License - see LICENSE file.