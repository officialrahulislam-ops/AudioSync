using System;
using NAudio.Wave;
using NAudio.CoreAudioApi;

namespace AudioSync;

class WasapiCaptureTest
{
    private WasapiLoopbackCapture? capture;
    private WaveFileWriter? writer;
    private int sampleCount = 0;
    private int callbackCount = 0;
    private int zeroByteCount = 0;
    private object writeLock = new();

    public void Start()
    {
        Console.WriteLine("Enumerating audio endpoints...");
        var enumerator = new MMDeviceEnumerator();
        var defaultDevice = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
        Console.WriteLine($"Default playback device: {defaultDevice.FriendlyName}");

        // Create WASAPI loopback capture - DON'T set WaveFormat, let it use native format
        capture = new WasapiLoopbackCapture(defaultDevice);
        capture.DataAvailable += OnDataAvailable;
        capture.RecordingStopped += OnRecordingStopped;

        // Write to WAV file for verification - use the device's native format
        writer = new WaveFileWriter("test_capture.wav", capture.WaveFormat);

        Console.WriteLine($"Starting capture at {capture.WaveFormat.SampleRate}Hz, {capture.WaveFormat.Channels}ch, {capture.WaveFormat.BitsPerSample} bits ({capture.WaveFormat.Encoding})");
        Console.WriteLine("NOTE: Make sure audio is playing on the system (music, video, etc.)");
        capture.StartRecording();
        Console.WriteLine("Recording... (will run for 10 seconds)");
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        callbackCount++;
        if (writer != null)
        {
            lock (writeLock)
            {
                writer.Write(e.Buffer, 0, e.BytesRecorded);
                sampleCount += e.BytesRecorded / 4; // Approximate for stereo
            }

            if (sampleCount % 48000 == 0 && sampleCount > 0)
            {
                Console.Write(".");
            }
        }

        if (e.BytesRecorded == 0)
        {
            zeroByteCount++;
        }

        if (callbackCount % 50 == 0)
        {
            Console.WriteLine($"[DEBUG] Callback #{callbackCount}, bytes={e.BytesRecorded}, total samples={sampleCount}, zero-byte callbacks={zeroByteCount}, format={capture?.WaveFormat}");
        }
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        Console.WriteLine("\nRecording stopped.");
        if (e.Exception != null)
        {
            Console.WriteLine($"Error: {e.Exception.Message}");
        }
        writer?.Dispose();
        writer = null;
        capture?.Dispose();
        capture = null;
        Console.WriteLine($"Captured {sampleCount} samples ({sampleCount / 48000.0:F1} seconds) in {callbackCount} callbacks, {zeroByteCount} zero-byte callbacks");
    }

    public void Stop()
    {
        if (capture != null)
        {
            capture.StopRecording();
        }
    }
}