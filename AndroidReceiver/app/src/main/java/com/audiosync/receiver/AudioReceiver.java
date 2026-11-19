package com.audiosync.receiver;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class AudioReceiver {
    private static final String TAG = "AudioReceiver";
    private static final int STREAM_PORT = 19827;
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2;
    private static final int FRAME_SIZE_MS = 20;
    private static final int FRAME_SIZE = SAMPLE_RATE * FRAME_SIZE_MS / 1000; // 960

    private DatagramSocket socket;
    private AudioTrack audioTrack;
    private OpusDecoderWrapper opusDecoder;
    private JitterBuffer jitterBuffer;
    private Thread receiveThread;
    private Thread playbackThread;
    private boolean isRunning = false;

    // Stats
    public int packetsReceived = 0;
    public int packetsDecoded = 0;
    public int framesPlayed = 0;
    public int decodeErrors = 0;
    public int playbackUnderruns = 0;

    public interface StatusListener {
        void onStatusChanged(String status);
        void onStatsUpdated(int packetsReceived, int framesPlayed, int bufferMs);
    }

    private StatusListener statusListener;

    public AudioReceiver(StatusListener listener) {
        this.statusListener = listener;
        this.opusDecoder = new OpusDecoderWrapper(SAMPLE_RATE, CHANNELS, FRAME_SIZE_MS);
        this.jitterBuffer = new JitterBuffer();
    }

    public void start() {
        if (isRunning) return;

        if (!opusDecoder.isInitialized()) {
            notifyStatus("Opus decoder failed to initialize");
            return;
        }

        try {
            // Create UDP socket
            socket = new DatagramSocket(STREAM_PORT);
            socket.setSoTimeout(100); // Short timeout for responsive stopping

            // Create AudioTrack
            int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
                notifyStatus("AudioTrack buffer size error");
                return;
            }

            // Use larger buffer for smoother playback
            bufferSize = Math.max(bufferSize, FRAME_SIZE * CHANNELS * 4 * 4); // ~4 frames

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
            );

            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                notifyStatus("AudioTrack initialization failed");
                return;
            }

            isRunning = true;

            // Start receive thread
            receiveThread = new Thread(this::receiveLoop, "AudioReceiver-Receive");
            receiveThread.start();

            // Start playback thread
            playbackThread = new Thread(this::playbackLoop, "AudioReceiver-Playback");
            playbackThread.start();

            audioTrack.play();
            notifyStatus("Receiving on port " + STREAM_PORT);

        } catch (SocketException e) {
            Log.e(TAG, "Socket error", e);
            notifyStatus("Socket error: " + e.getMessage());
            stop();
        } catch (Exception e) {
            Log.e(TAG, "Start error", e);
            notifyStatus("Error: " + e.getMessage());
            stop();
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (isRunning) {
            try {
                socket.receive(packet);
                packetsReceived++;

                // Parse RTP packet
                RtpPacket rtpPacket = RtpPacket.parse(packet.getData(), packet.getOffset(), packet.getLength());
                if (rtpPacket == null) {
                    continue;
                }

                // Decode Opus
                short[] pcmData = opusDecoder.decode(rtpPacket.payload);
                if (pcmData != null && pcmData.length > 0) {
                    packetsDecoded++;
                    // Add to jitter buffer with timestamp from RTP packet
                    jitterBuffer.addPacket(rtpPacket.timestamp, pcmData, rtpPacket.sequenceNumber);
                } else {
                    decodeErrors++;
                }

                // Update stats periodically
                if (packetsReceived % 50 == 0) {
                    notifyStats();
                }

            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Receive error", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected receive error", e);
            }
        }
    }

    private void playbackLoop() {
        while (isRunning) {
            try {
                short[] frame = jitterBuffer.getNextFrame();
                if (frame != null && frame.length > 0) {
                    int written = audioTrack.write(frame, 0, frame.length, AudioTrack.WRITE_BLOCKING);
                    if (written > 0) {
                        framesPlayed += written / (CHANNELS * 2); // 2 bytes per sample
                    } else if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: " + written);
                    }
                } else {
                    playbackUnderruns++;
                }

                // Update stats periodically
                if (framesPlayed % 200 == 0) {
                    notifyStats();
                }

            } catch (Exception e) {
                Log.e(TAG, "Playback error", e);
            }
        }
    }

    public void stop() {
        isRunning = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
            try { receiveThread.join(1000); } catch (InterruptedException ignored) {}
            receiveThread = null;
        }

        if (playbackThread != null) {
            playbackThread.interrupt();
            try { playbackThread.join(1000); } catch (InterruptedException ignored) {}
            playbackThread = null;
        }

        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }

        if (socket != null) {
            socket.close();
            socket = null;
        }

        if (opusDecoder != null) {
            opusDecoder.release();
            opusDecoder = null;
        }

        if (jitterBuffer != null) {
            jitterBuffer.reset();
        }

        notifyStatus("Stopped");
    }

    private void notifyStatus(String status) {
        if (statusListener != null) {
            statusListener.onStatusChanged(status);
        }
    }

    private void notifyStats() {
        if (statusListener != null) {
            statusListener.onStatsUpdated(packetsReceived, framesPlayed, jitterBuffer.getBufferDurationMs());
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}