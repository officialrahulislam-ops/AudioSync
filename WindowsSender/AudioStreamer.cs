using System;
using System.Net;
using System.Net.Sockets;
using NAudio.Wave;
using NAudio.CoreAudioApi;

namespace AudioSync;

/// <summary>
/// Captures system audio, encodes to Opus, and streams via UDP
/// </summary>
public class AudioStreamer : IDisposable
{
    private WasapiLoopbackCapture? capture;
    private OpusEncoderWrapper? encoder;
    private UdpClient? udpClient;
    private IPEndPoint? remoteEndPoint;

    private readonly int sampleRate = 48000;
    private readonly int channels = 2;
    private readonly int frameSizeMs = 20;
    private readonly int frameSize; // samples per frame per channel
    private readonly int frameSizeBytes; // bytes per frame (16-bit stereo)

    private ushort sequenceNumber = 0;
    private uint timestamp = 0;
    private readonly uint ssrc;
    private bool isStreaming = false;

    // Ring buffer for accumulating audio samples
    private float[] ringBuffer;
    private int ringWritePos = 0;
    private int ringReadPos = 0;
    private int ringAvailable = 0;
    private readonly object ringLock = new();

    // Buffers (reuse to avoid allocations)
    private short[] pcmBuffer;     // For encoding
    private byte[] encodedBuffer;  // Encoded Opus
    private byte[] packetBuffer;   // Full RTP packet

    // Stats
    public int FramesSent { get; private set; }
    public int BytesSent { get; private set; }
    public int CaptureCallbacks { get; private set; }
    public int EncodeErrors { get; private set; }
    public int SendErrors { get; private set; }

    public AudioStreamer(string remoteIp, int remotePort = 19827)
    {
        frameSize = sampleRate * frameSizeMs / 1000; // 960
        frameSizeBytes = frameSize * channels * 2; // 16-bit stereo
        ssrc = (uint)new Random().Next();

        remoteEndPoint = new IPEndPoint(IPAddress.Parse(remoteIp), remotePort);
        udpClient = new UdpClient();
        udpClient.Client.SendTimeout = 1000;

        encoder = new OpusEncoderWrapper(sampleRate, channels, frameSizeMs);

        // Ring buffer: hold 2 seconds of audio (100 frames)
        ringBuffer = new float[sampleRate * channels * 2];

        // Pre-allocate buffers
        pcmBuffer = new short[frameSize * channels];
        encodedBuffer = new byte[4000];
        packetBuffer = new byte[RtpPacket.HeaderSize + 4000];
    }

    public void Start()
    {
        if (isStreaming) return;

        Console.WriteLine($"Starting audio stream to {remoteEndPoint}...");
        Console.WriteLine($"Format: {sampleRate}Hz {channels}ch, {frameSizeMs}ms frames ({frameSize} samples/frame)");
        Console.WriteLine($"SSRC: 0x{ssrc:X8}");
        Console.WriteLine("NOTE: Make sure audio is playing on the system!");

        // Create WASAPI loopback capture
        var enumerator = new MMDeviceEnumerator();
        var defaultDevice = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
        Console.WriteLine($"Capture device: {defaultDevice.FriendlyName}");

        capture = new WasapiLoopbackCapture(defaultDevice);
        capture.WaveFormat = new WaveFormat(sampleRate, 32, channels); // IEEE float
        capture.DataAvailable += OnDataAvailable;
        capture.RecordingStopped += OnRecordingStopped;

        isStreaming = true;
        capture.StartRecording();

        Console.WriteLine("Streaming started. Press Enter to stop...");
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (!isStreaming) return;

        CaptureCallbacks++;

        // WASAPI provides IEEE float data in e.Buffer
        int floatBytesPerSample = 4;
        int samplesInBuffer = e.BytesRecorded / floatBytesPerSample;

        if (samplesInBuffer <= 0) return;

        // Convert float bytes to float array and add to ring buffer
        float[] floatSamples = new float[samplesInBuffer];
        for (int i = 0; i < samplesInBuffer; i++)
        {
            int byteIndex = i * 4;
            if (byteIndex + 3 < e.Buffer.Length)
            {
                floatSamples[i] = BitConverter.ToSingle(e.Buffer, byteIndex);
            }
        }

        // Add to ring buffer
        lock (ringLock)
        {
            for (int i = 0; i < samplesInBuffer; i++)
            {
                ringBuffer[ringWritePos] = floatSamples[i];
                ringWritePos = (ringWritePos + 1) % ringBuffer.Length;
                if (ringAvailable < ringBuffer.Length)
                    ringAvailable++;
                else
                    ringReadPos = (ringReadPos + 1) % ringBuffer.Length; // Overrun - drop oldest
            }
        }

        // Process as many complete frames as we have
        ProcessAvailableFrames();
    }

    private void ProcessAvailableFrames()
    {
        while (true)
        {
            int available;
            lock (ringLock)
            {
                available = ringAvailable;
            }

            int samplesPerFrame = frameSize * channels;
            if (available < samplesPerFrame)
                break; // Not enough for a full frame

            // Read one frame from ring buffer
            lock (ringLock)
            {
                for (int i = 0; i < samplesPerFrame; i++)
                {
                    // Convert float to 16-bit PCM
                    float sample = ringBuffer[ringReadPos];
                    if (sample > 1.0f) sample = 1.0f;
                    if (sample < -1.0f) sample = -1.0f;
                    pcmBuffer[i] = (short)(sample * 32767.0f);

                    ringReadPos = (ringReadPos + 1) % ringBuffer.Length;
                }
                ringAvailable -= samplesPerFrame;
            }

            // Encode to Opus
            int encodedLen = encoder!.Encode(pcmBuffer, encodedBuffer);
            if (encodedLen <= 0)
            {
                EncodeErrors++;
                continue;
            }

            // Build RTP packet
            RtpPacket packet = new RtpPacket(
                sequenceNumber++,
                timestamp,
                ssrc,
                encodedBuffer,
                encodedLen
            );

            // Increment timestamp by frame duration in samples (per channel)
            timestamp += (uint)frameSize;

            // Send
            try
            {
                byte[] packetBytes = packet.ToBytes();
                int sent = udpClient!.Send(packetBytes, packetBytes.Length, remoteEndPoint!);

                if (sent > 0)
                {
                    FramesSent++;
                    BytesSent += sent;
                }
                else
                {
                    SendErrors++;
                }
            }
            catch (Exception)
            {
                SendErrors++;
            }
        }
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        Console.WriteLine("\nCapture stopped.");
        if (e.Exception != null)
        {
            Console.WriteLine($"Capture error: {e.Exception.Message}");
        }
        isStreaming = false;
    }

    public void Stop()
    {
        if (!isStreaming) return;

        Console.WriteLine("Stopping stream...");
        isStreaming = false;

        capture?.StopRecording();
        capture?.Dispose();
        capture = null;

        udpClient?.Close();
        udpClient = null;

        encoder?.Dispose();
        encoder = null;

        Console.WriteLine($"Stats: {FramesSent} frames sent, {BytesSent} bytes, {CaptureCallbacks} capture callbacks, {EncodeErrors} encode errors, {SendErrors} send errors");
    }

    public void Dispose()
    {
        Stop();
    }
}