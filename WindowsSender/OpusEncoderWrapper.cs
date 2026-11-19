using System;
using Concentus;
using Concentus.Enums;
using NAudio.Wave;

namespace AudioSync;

/// <summary>
/// Wrapper around Concentus Opus encoder for AudioSync streaming
/// Handles PCM float -> short conversion and encoding
/// </summary>
public class OpusEncoderWrapper : IDisposable
{
    private readonly IOpusEncoder encoder;
    private readonly int sampleRate;
    private readonly int channels;
    private readonly int frameSize; // samples per frame per channel
    private bool disposed = false;

    public OpusEncoderWrapper(int sampleRate = 48000, int channels = 2, int frameSizeMs = 20)
    {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.frameSize = sampleRate * frameSizeMs / 1000; // 960 at 48kHz/20ms

        // Create encoder using factory
        encoder = OpusCodecFactory.CreateEncoder(sampleRate, channels, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.Bitrate = 64000; // 64 kbps for stereo
    }

    /// <summary>
    /// Encode 16-bit PCM samples to Opus
    /// </summary>
    /// <param name="pcmData">Interleaved 16-bit PCM (L, R, L, R...)</param>
    /// <param name="output">Output buffer for encoded data</param>
    /// <returns>Number of bytes encoded, or -1 on error</returns>
    public int Encode(short[] pcmData, byte[] output)
    {
        if (disposed) return -1;
        if (pcmData == null || pcmData.Length < frameSize * channels) return -1;
        if (output == null || output.Length < 4000) return -1;

        try
        {
            // Concentus expects frame_size as samples PER CHANNEL
            return encoder.Encode(pcmData, frameSize, output, output.Length);
        }
        catch (Exception)
        {
            return -1;
        }
    }

    /// <summary>
    /// Encode 32-bit float PCM samples to Opus (convert to short first)
    /// </summary>
    /// <param name="pcmData">Interleaved float PCM (-1.0 to 1.0)</param>
    /// <param name="output">Output buffer for encoded data</param>
    /// <returns>Number of bytes encoded, or -1 on error</returns>
    public int Encode(float[] pcmData, byte[] output)
    {
        if (disposed) return -1;
        if (pcmData == null || pcmData.Length < frameSize * channels) return -1;

        // Convert float to short
        short[] shortBuffer = new short[frameSize * channels];
        for (int i = 0; i < frameSize * channels; i++)
        {
            float sample = pcmData[i];
            if (sample > 1.0f) sample = 1.0f;
            if (sample < -1.0f) sample = -1.0f;
            shortBuffer[i] = (short)(sample * 32767.0f);
        }

        return Encode(shortBuffer, output);
    }

    public void Reset() => encoder?.ResetState();
    public void Dispose()
    {
        if (!disposed)
        {
            encoder?.Dispose();
            disposed = true;
        }
    }

    public int FrameSize => frameSize;
    public int Channels => channels;
    public int SampleRate => sampleRate;
}