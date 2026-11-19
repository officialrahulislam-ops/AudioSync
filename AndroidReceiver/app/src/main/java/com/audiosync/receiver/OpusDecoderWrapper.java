package com.audiosync.receiver;

import android.util.Log;
import org.restcomm.media.core.codec.opus.OpusDecoder;

public class OpusDecoderWrapper {
    private static final String TAG = "OpusDecoderWrapper";

    private OpusDecoder decoder;
    private final int sampleRate;
    private final int channels;
    private final int frameSize;
    private boolean initialized = false;

    public OpusDecoderWrapper(int sampleRate, int channels, int frameSizeMs) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.frameSize = sampleRate * frameSizeMs / 1000; // samples per frame per channel

        try {
            decoder = new OpusDecoder();
            decoder.init(sampleRate, channels);
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
            // opus-java decode signature: decode(byte[] data, int offset, int len, short[] output, int outputOffset, boolean decodeFec)
            // Returns number of decoded samples per channel
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
            Log.e(TAG, "Opus decode error: " + e.getMessage(), e);
            return null;
        }
    }

    public void reset() {
        if (decoder != null && initialized) {
            try {
                decoder.init(sampleRate, channels);
            } catch (Exception e) {
                Log.e(TAG, "Opus decoder reset error", e);
            }
        }
    }

    public void release() {
        decoder = null;
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getFrameSize() {
        return frameSize;
    }
}