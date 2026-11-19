using System;
using System.Net;
using System.Net.Sockets;

namespace AudioSync;

/// <summary>
/// Simple UDP listener for debugging packet reception
/// </summary>
public class DebugListener : IDisposable
{
    private UdpClient udpClient;
    private IPEndPoint localEndPoint;
    private bool isRunning = false;
    private Thread? listenerThread;

    public event Action<byte[], int, IPEndPoint>? OnPacketReceived;

    public DebugListener(int port = 19827)
    {
        localEndPoint = new IPEndPoint(IPAddress.Any, port);
        udpClient = new UdpClient(localEndPoint);
        udpClient.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, 1);
    }

    public void Start()
    {
        if (isRunning) return;

        isRunning = true;
        listenerThread = new Thread(ListenLoop);
        listenerThread.Start();
        Console.WriteLine($"Debug listener started on port {localEndPoint.Port}");
    }

    private void ListenLoop()
    {
        while (isRunning)
        {
            try
            {
                IPEndPoint remoteEndPoint = new IPEndPoint(IPAddress.Any, 0);
                byte[] data = udpClient.Receive(ref remoteEndPoint);

                // Parse RTP packet
                if (RtpPacket.TryParse(data, 0, data.Length, out RtpPacket packet))
                {
                    Console.WriteLine($"[{DateTime.Now:HH:mm:ss.fff}] Received packet - Seq: {packet.SequenceNumber}, TS: {packet.Timestamp}, " +
                        $"SSRC: 0x{packet.SSRC:X8}, Payload: {packet.PayloadLength} bytes, Audio: {packet.PayloadLength > 0}");
                }
                else
                {
                    Console.WriteLine($"[{DateTime.Now:HH:mm:ss.fff}] Received invalid packet - Length: {data.Length}");
                }

                OnPacketReceived?.Invoke(data, data.Length, remoteEndPoint);
            }
            catch (Exception ex)
            {
                if (isRunning)
                {
                    Console.WriteLine($"Listener error: {ex.Message}");
                }
            }
        }
    }

    public void Stop()
    {
        isRunning = false;
        udpClient?.Close();
        listenerThread?.Join(1000);
        Console.WriteLine("Debug listener stopped.");
    }

    public void Dispose()
    {
        Stop();
        udpClient?.Dispose();
    }
}