using System;
using Concentus;
using Concentus.Enums;
using NAudio.Wave;

namespace AudioSync;

class OpusTest
{
    public static void RunTest()
    {
        Console.WriteLine("Testing Opus encode/decode with Concentus...");

        // Create Opus encoder using factory
        using var encoder = OpusCodecFactory.CreateEncoder(48000, 2, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.Bitrate = 64000; // 64 kbps

        // Create Opus decoder using factory
        using var decoder = OpusCodecFactory.CreateDecoder(48000, 2);

        // Generate test audio: 1 second of 440Hz sine wave at 48kHz stereo
        int sampleRate = 48000;
        int channels = 2;
        int durationMs = 1000;
        int frameSizeMs = 20;
        int frameSize = sampleRate * frameSizeMs / 1000; // 960 samples per frame
        int totalFrames = durationMs / frameSizeMs;

        Console.WriteLine($"Frame size: {frameSize} samples ({frameSizeMs}ms)");
        Console.WriteLine($"Total frames: {totalFrames}");

        short[] pcmBuffer = new short[frameSize * channels];
        short[] decodedBuffer = new short[frameSize * channels];
        byte[] encoded = new byte[4096];

        for (int frame = 0; frame < totalFrames; frame++)
        {
            // Generate sine wave
            for (int i = 0; i < frameSize; i++)
            {
                double t = (frame * frameSize + i) / (double)sampleRate;
                short sample = (short)(Math.Sin(2 * Math.PI * 440 * t) * 16000);
                pcmBuffer[i * channels] = sample;     // Left
                pcmBuffer[i * channels + 1] = sample; // Right
            }

            // Encode
            int encodedLen = encoder.Encode(pcmBuffer, frameSize, encoded, encoded.Length);
            Console.WriteLine($"Frame {frame}: encoded {encodedLen} bytes");

            // Decode
            int decodedSamples = decoder.Decode(encoded.AsSpan(0, encodedLen), decodedBuffer, frameSize, false);
            Console.WriteLine($"Frame {frame}: decoded {decodedSamples} samples");

            if (decodedSamples != frameSize)
            {
                Console.WriteLine($"WARNING: Decoded {decodedSamples} != expected {frameSize}");
            }
        }

        Console.WriteLine("Opus test completed successfully!");
    }
}