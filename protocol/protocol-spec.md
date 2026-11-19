# AudioSync Protocol Specification

## Overview

AudioSync uses a lightweight UDP-based protocol for one-way audio streaming from Windows sender to Android receiver over local Wi-Fi. No cloud servers or internet connectivity required.

## Protocol Overview

- **Transport**: UDP (no TCP for low latency)
- **Codec**: Opus (audio encoding/decoding)
- **Direction**: Windows PC → Android phone only
- **Discovery**: Simple UDP echo + manual IP
- **No reliability features**: No retransmissions, ACKs, or connection setup — keep it simple

## Device Discovery

### Basic Discovery Flow

1. **Sender discovery**:
   - Windows tries to send "HELLO" packet to common discovery port (19826)
   - Windows also supports manual IP entry via UI

2. **Receiver response**:
   - If receiver hears "HELLO" on discovery port, it responds with "HANDSHAKE" packet
   - Contains: receiver IP, UDP port, audio capabilities (Opus, 48kHz stereo)

3. **Sender confirmation**:
   - On "HANDSHAKE", sender switches to streaming mode
   - No persistent connection state

### Discovery Packet Formats

**HELLO (Sender → Receiver)**
```
+-------------+----------+
| Type (1 byte)| Payload |
+-------------+----------+
|   0x01      |  Optional device name
```

**HANDSHAKE (Receiver → Sender)**
```
+-------------+---------------+--------------------+---------------+---------------+
| Type (1 byte)| UDP Port (2)   | Audio Config (3)   | IP (4 bytes)  | Timestamp (4)
+-------------+---------------+--------------------+---------------+---------------+
|   0x02      |  19827        | Opus 48000Hz stereo | 192.168.1.100 | Unix epoch ms
```

## Connection Establishment

After discovery:

1. **Sender → Receiver** (streaming mode)
   - Audio packets begin flowing immediately
   - No formal "connect" signal needed

2. **No connection cleanup**
   - Streams end when sender stops sending or program closes
   - Receiver stops playback automatically

## Audio Packet Format

**RTP-like audio packet**
```
+---------------+---------------+---------------+---------------+---------------+---------------+
| Seq Num (2)   | Timestamp (4) | SSRC (4)      | Opus Data...  | CRC (4)       |
+---------------+---------------+---------------+---------------+---------------+---------------+
```

### Packet Fields

- **Seq Num**: 16-bit sequence number (increments per packet, wraps at 65535)
- **Timestamp**: 32-bit audio playback timestamp in milliseconds
  - Starts at 0, increments by frame duration (20ms per Opus 20ms frame)
  - Not sample-accurate; prevents decoding skew
- **SSRC**: 32-bit stream identifier
  - Random value chosen at stream start
  - Helps receiver distinguish multiple streams
- **Opus Data**: Opus-encoded audio frame(s)
  - One Opus packet (typically 20ms of audio)
  - May contain multiple Opus frames if sender wants to reduce UDP overhead
- **CRC**: 32-bit Adler-32 checksum (optional; helps debug)

## Audio Configuration

### Opus Settings (Shared between sender/receiver)

```
Application: voip (for real-time conversational audio)
Sample Rate: 48000 Hz
Channels: Stereo
Frame Duration: 20 ms (0x0fa0)
Bitrate: ~64 kbps (adjustable)
Complexity: 0 (fastest)
```

### Negotiated During Discovery

Both sender and receiver use same Opus configuration. Could be extended to:
- Frame duration negotiation (10ms, 20ms, 40ms)
- Bitrate limits
- Sample rate fallback

## Packet Loss Handling

### No Retransmissions

- No NACK (Negative ACKnowledgement)
- No ACK (ACKnowledgement)
- Discard lost packets silently

### Jitter Buffer

- Target buffer size: 40-80ms (adjustable based on network quality)
- Receive packets out of order, reorder by timestamp
- Discard packets older than buffer size + tolerance
- If buffer underruns → hold last audio sample (click-free fallback)

### Timestamp Used for Loss Detection

- Receiver monitors timestamp continuity
- If timestamp jumps > 100ms → likely packet loss
- If gap > 200ms → consider increasing buffer size

## Performance Targets

| Metric | Target |
|--------|--------|
| Latency | 150-250ms end-to-end |
| Jitter Buffer | 40-80ms base, expandable |
| Bandwidth | ~64kbps |
| CPU Usage | <5% on each platform |
| Crackling | Prevent with jitter buffer |

## Implementation Details

### Windows Sender

- Audio capture: WASAPI loopback, 48kHz stereo, 16-bit PCM
- Opus encoding: .NET wrapper
- UDP sending: raw bytes, no sockets keepalive
- Discovery: UDP broadcast to 255.255.255.255:19826

### Android Receiver

- UDP receiving: Java socket, non-blocking
- Opus decoding: Java Opus library
- Audio playback: AudioTrack 48kHz stereo
- Jitter buffer: Packet queue sorted by timestamp

## Troubleshooting

### Common Issues and Symptoms

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| Crackling audio | Jitter buffer too small | Increase buffer size (40-80ms → 80-120ms) |
| Glitches every few seconds | UDP packet loss on Wi-Fi | Reduce frame size to 10ms, increase buffer |
| No audio at all | Discovery failure | Check IP addresses, manual connection |
| High CPU | Opus encoding/decoding | Lower bitrate, reduce frame rate |

### Manual Testing

1. **Sender**: `cd WindowsSender && dotnet run` (or build/publish)
2. **Receiver**: Use Android Studio, run "HelloWorld" app
3. **Connection**: Both devices on same Wi-Fi
4. **Playback**: Audio should flow from Windows speakers to Android output

## Future Extensions (Not in current scope)

- mDNS/Bonjour discovery (planned for Phase 2)
- Bidirectional communication (planned for Phase 3)
- Audio quality adaptation (planned for Phase 4)
- Cloud backup (planned for Phase 5)

## Copyright and Usage

AudioSync protocol is for internal use only. Released under MIT License with AudioSync branding required.