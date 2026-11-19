using System;

namespace AudioSync;

/// <summary>
/// RTP-like packet structure for AudioSync protocol
/// </summary>
public struct RtpPacket
{
    public const int HeaderSize = 12; // seq(2) + timestamp(4) + ssrc(4)
    public const int MaxPayloadSize = 1400; // Safe UDP payload size

    public ushort SequenceNumber;
    public uint Timestamp;
    public uint SSRC;
    public byte[] Payload;
    public int PayloadLength;

    public RtpPacket(ushort seq, uint timestamp, uint ssrc, byte[] payload, int length)
    {
        SequenceNumber = seq;
        Timestamp = timestamp;
        SSRC = ssrc;
        Payload = payload;
        PayloadLength = length;
    }

    /// <summary>
    /// Serialize packet to byte array
    /// </summary>
    public byte[] ToBytes()
    {
        byte[] buffer = new byte[HeaderSize + PayloadLength];

        // Sequence number (big endian)
        buffer[0] = (byte)(SequenceNumber >> 8);
        buffer[1] = (byte)(SequenceNumber & 0xFF);

        // Timestamp (big endian)
        buffer[2] = (byte)(Timestamp >> 24);
        buffer[3] = (byte)(Timestamp >> 16);
        buffer[4] = (byte)(Timestamp >> 8);
        buffer[5] = (byte)(Timestamp & 0xFF);

        // SSRC (big endian)
        buffer[6] = (byte)(SSRC >> 24);
        buffer[7] = (byte)(SSRC >> 16);
        buffer[8] = (byte)(SSRC >> 8);
        buffer[9] = (byte)(SSRC & 0xFF);

        // Payload
        if (Payload != null && PayloadLength > 0)
        {
            Array.Copy(Payload, 0, buffer, HeaderSize, PayloadLength);
        }

        return buffer;
    }

    /// <summary>
    /// Parse packet from byte array
    /// </summary>
    public static bool TryParse(byte[] data, int offset, int length, out RtpPacket packet)
    {
        packet = default;

        if (length < HeaderSize)
            return false;

        try
        {
            packet.SequenceNumber = (ushort)((data[offset] << 8) | data[offset + 1]);
            packet.Timestamp = (uint)(
                (data[offset + 2] << 24) |
                (data[offset + 3] << 16) |
                (data[offset + 4] << 8) |
                data[offset + 5]
            );
            packet.SSRC = (uint)(
                (data[offset + 6] << 24) |
                (data[offset + 7] << 16) |
                (data[offset + 8] << 8) |
                data[offset + 9]
            );

            packet.PayloadLength = length - HeaderSize;
            packet.Payload = new byte[packet.PayloadLength];
            Array.Copy(data, offset + HeaderSize, packet.Payload, 0, packet.PayloadLength);

            return true;
        }
        catch
        {
            return false;
        }
    }
}