package com.audiosync.receiver;

import org.restcomm.media.core.codec.opus.Opus;
import org.restcomm.media.core.codec.opus.OpusDecoder;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class OpusDecoderWrapper {
    private static final String TAG = "OpusDecoderWrapper";
    private OpusDecoder decoder;
    private final int sampleRate;
    private final int channels;
    private final int frameSize;

    public OpusDecoderWrapper(int sampleRate, int channels, int frameSizeMs) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.frameSize = sampleRate * frameSizeMs / 1000; // samples per frame per channel

        // Initialize Opus decoder
        // Note: opus-java might use native library - handle gracefully
        try {
            decoder = new OpusDecoder();
            decoder.init(sampleRate, channels);
            System.loadLibrary("opus");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Opus decoder: " + e.getMessage(), e);
        }
    }

    /**
     * Decode an Opus packet to 16-bit PCM
     * @param opusData Opus encoded data
     * @return Decoded PCM samples (interleaved stereo), or null on error
     */
    public short[] decode(byte[] opusData) {
        if (opusData == null || opusData.length == 0) {
            return null;
        }

        try {
            // opus-java decode method signature varies by version
            // Try to decode with the frame size we expect
            short[] output = new short[frameSize * channels];
            int decodedSamples = decoder.decode(opusData, 0, opusData.length, output, 0, false);

            if (decodedSamples <= 0) {
                return null;
            }

            // Return only the actual decoded samples
            short[] result = new short[decodedSamples * channels];
            System.arraycopy(output, 0, result, 0, decodedSamples * channels);
            return result;
        } catch (Exception e) {
            android.util.Log.e(TAG, "Opus decode error", e);
            return null;
        }
    }

    public void reset() {
        if (decoder != null) {
            decoder.init(sampleRate, channels);
        }
    }

    public void release() {
        decoder = null;
    }
}