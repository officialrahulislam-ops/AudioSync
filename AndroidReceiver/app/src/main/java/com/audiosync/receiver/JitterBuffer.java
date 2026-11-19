package com.audiosync.receiver;

import android.util.Log;
import java.util.PriorityQueue;
import java.util.Comparator;

public class JitterBuffer {
    private static final String TAG = "JitterBuffer";

    // Buffer configuration
    private final int targetBufferMs = 60; // Target jitter buffer size in ms
    private final int maxBufferMs = 200;   // Maximum buffer size
    private final int sampleRate = 48000;
    private final int channels = 2;
    private final int frameSizeMs = 20;
    private final int frameSize = sampleRate * frameSizeMs / 1000; // 960 samples per frame per channel

    // Packet queue sorted by timestamp
    private final PriorityQueue<JitterPacket> packetQueue;
    private long nextPlayTimestamp = -1;
    private boolean initialized = false;

    // Stats
    public int packetsReceived = 0;
    public int packetsPlayed = 0;
    public int packetsDropped = 0;
    public int bufferUnderruns = 0;
    public int bufferOverruns = 0;

    public JitterBuffer() {
        packetQueue = new PriorityQueue<>(Comparator.comparingLong(p -> p.timestamp));
    }

    public static class JitterPacket {
        public long timestamp;
        public short[] pcmData;
        public int sequenceNumber;

        public JitterPacket(long timestamp, short[] pcmData, int sequenceNumber) {
            this.timestamp = timestamp;
            this.pcmData = pcmData;
            this.sequenceNumber = sequenceNumber;
        }
    }

    /**
     * Add a decoded audio packet to the jitter buffer
     */
    public void addPacket(long timestamp, short[] pcmData, int sequenceNumber) {
        if (pcmData == null || pcmData.length == 0) return;

        packetsReceived++;

        // Initialize on first packet
        if (!initialized) {
            nextPlayTimestamp = timestamp;
            initialized = true;
        }

        // Discard packets that are too old (more than maxBufferMs behind)
        long maxAge = maxBufferMs * sampleRate / 1000;
        if (timestamp < nextPlayTimestamp - maxAge) {
            packetsDropped++;
            return;
        }

        // Add to queue
        packetQueue.offer(new JitterPacket(timestamp, pcmData, sequenceNumber));

        // Limit queue size
        while (getBufferDurationMs() > maxBufferMs) {
            packetQueue.poll();
            bufferOverruns++;
        }
    }

    /**
     * Get the next frame of audio for playback
     * Returns null if buffer underrun (not enough data)
     */
    public short[] getNextFrame() {
        if (!initialized) return null;

        // Calculate expected timestamp for this frame
        long expectedTimestamp = nextPlayTimestamp;

        // Look for packet with matching or close timestamp
        JitterPacket packet = null;

        // Try exact match first
        for (JitterPacket p : packetQueue) {
            if (p.timestamp == expectedTimestamp) {
                packet = p;
                break;
            }
        }

        // If no exact match, find closest packet
        if (packet == null && !packetQueue.isEmpty()) {
            JitterPacket closest = null;
            long minDiff = Long.MAX_VALUE;

            for (JitterPacket p : packetQueue) {
                long diff = Math.abs(p.timestamp - expectedTimestamp);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = p;
                }
            }

            // If closest is within reasonable range, use it
            if (closest != null && minDiff < frameSize * 2) {
                packet = closest;
            }
        }

        if (packet != null) {
            // Remove this packet from queue
            packetQueue.remove(packet);
            packetsPlayed++;
            nextPlayTimestamp += frameSize;
            return packet.pcmData;
        } else {
            // Buffer underrun - return silence
            bufferUnderruns++;
            return new short[frameSize * channels];
        }
    }

    /**
     * Get current buffer duration in milliseconds
     */
    public int getBufferDurationMs() {
        if (packetQueue.isEmpty()) return 0;

        long minTs = packetQueue.peek().timestamp;
        long maxTs = minTs;
        for (JitterPacket p : packetQueue) {
            if (p.timestamp > maxTs) maxTs = p.timestamp;
        }
        return (int)((maxTs - minTs) * 1000 / sampleRate);
    }

    public void reset() {
        packetQueue.clear();
        nextPlayTimestamp = -1;
        initialized = false;
        packetsReceived = 0;
        packetsPlayed = 0;
        packetsDropped = 0;
        bufferUnderruns = 0;
        bufferOverruns = 0;
    }

    public boolean isReady() {
        return initialized && getBufferDurationMs() >= targetBufferMs / 2;
    }
}