package com.audiosync.receiver;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import java.nio.ByteBuffer;

/**
 * Opus decoder wrapper using Android's built-in MediaCodec.
 * Replaces the RestComm opus-java dependency which had unresolved
 * transitive dependencies on Android.
 */
public class OpusDecoderWrapper {
    private static final String TAG = "OpusDecoderWrapper";

    private MediaCodec decoder;
    private final int sampleRate;
    private final int channels;
    private final int frameSize;
    private boolean initialized = false;

    public OpusDecoderWrapper(int sampleRate, int channels, int frameSizeMs) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.frameSize = sampleRate * frameSizeMs / 1000; // samples per frame per channel

        try {
            decoder = MediaCodec.createDecoderByType("audio/opus");
            MediaFormat format = MediaFormat.createAudioFormat("audio/opus", sampleRate, channels);
            decoder.configure(format, null, null, 0);
            decoder.start();
            initialized = true;
            Log.d(TAG, "Opus decoder initialized: " + sampleRate + "Hz " + channels + "ch");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Opus decoder", e);
            initialized = false;
        }
    }

    /**
     * Decode an Opus packet to 16-bit PCM
     * @param opusData Opus encoded data
     * @return Decoded PCM samples (interleaved stereo), or null on error
     */
    public short[] decode(byte[] opusData) {
        if (!initialized || decoder == null || opusData == null || opusData.length == 0) {
            return null;
        }

        try {
            // Queue input buffer
            int inputIndex = decoder.dequeueInputBuffer(5000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                inputBuffer.clear();
                inputBuffer.put(opusData);
                decoder.queueInputBuffer(inputIndex, 0, opusData.length, 0, 0);
            }

            // Dequeue output buffer
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outputIndex = decoder.dequeueOutputBuffer(info, 5000);
            if (outputIndex >= 0 && info.size > 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                byte[] pcmBytes = new byte[info.size];
                outputBuffer.get(pcmBytes);
                decoder.releaseOutputBuffer(outputIndex, false);

                // Convert bytes to short array (16-bit PCM, little-endian)
                short[] result = new short[pcmBytes.length / 2];
                for (int i = 0; i < result.length; i++) {
                    result[i] = (short)((pcmBytes[i * 2] & 0xFF) | ((pcmBytes[i * 2 + 1] & 0xFF) << 8));
                }
                return result;
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Opus decode error: " + e.getMessage(), e);
            return null;
        }
    }

    public void reset() {
        if (decoder != null && initialized) {
            try {
                decoder.flush();
            } catch (Exception e) {
                Log.e(TAG, "Opus decoder reset error", e);
            }
        }
    }

    public void release() {
        if (decoder != null) {
            try {
                decoder.stop();
                decoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Opus decoder release error", e);
            }
            decoder = null;
        }
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getFrameSize() {
        return frameSize;
    }
}