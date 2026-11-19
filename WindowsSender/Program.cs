using System;
using System.Threading;

namespace AudioSync;

class Program
{
    static void Main(string[] args)
    {
        Console.WriteLine("AudioSync Windows Sender - Starting...");
        Console.WriteLine("======================================");

        // Test Opus encode/decode
        OpusTest.RunTest();

        // Test WASAPI loopback capture
        try
        {
            var capture = new WasapiCaptureTest();
            capture.Start();

            Console.WriteLine("\nWaiting 15 seconds for recording (make sure audio is playing)...");
            Thread.Sleep(16000);

            capture.Stop();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
        }

        Console.WriteLine("Done.");
    }
}