using System;
using System.Net;
using System.Threading;

namespace AudioSync;

class Program
{
    static void Main(string[] args)
    {
        Console.WriteLine("AudioSync Windows Sender - Starting...");
        Console.WriteLine("======================================");

        // Get target IP from command line or prompt
        string remoteIp = "127.0.0.1"; // Default to localhost for loopback testing
        int remotePort = 19827;

        if (args.Length > 0)
        {
            remoteIp = args[0];
        }
        else
        {
            Console.Write("Enter receiver IP address (default 127.0.0.1 for loopback): ");
            string? input = Console.ReadLine();
            if (!string.IsNullOrWhiteSpace(input))
            {
                if (IPAddress.TryParse(input, out _))
                {
                    remoteIp = input;
                }
                else
                {
                    Console.WriteLine("Invalid IP, using 127.0.0.1");
                }
            }
        }

        Console.WriteLine($"Target: {remoteIp}:{remotePort}");

        // Start debug listener on the same port to see what's coming in
        DebugListener listener = new DebugListener(remotePort);
        listener.Start();

        // Test Opus encode/decode first
        Console.WriteLine("\n--- Opus Codec Test ---");
        OpusTest.RunTest();

        // Start audio streaming
        Console.WriteLine("\n--- Audio Streaming ---");
        try
        {
            var streamer = new AudioStreamer(remoteIp, remotePort);
            streamer.Start();

            // Wait for user to stop
            Console.WriteLine("\nStreaming... Press Enter to stop.");
            Console.ReadLine();

            streamer.Stop();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
        }
        finally
        {
            listener.Stop();
        }

        Console.WriteLine("Done.");
    }
}