package com.audiosync.receiver;

public class RtpPacket {
    public static final int HEADER_SIZE = 12; // seq(2) + timestamp(4) + ssrc(4)

    public int sequenceNumber;
    public long timestamp;
    public long ssrc;
    public byte[] payload;
    public int payloadLength;

    public RtpPacket(int sequenceNumber, long timestamp, long ssrc, byte[] payload, int payloadLength) {
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.payload = payload;
        this.payloadLength = payloadLength;
    }

    /**
     * Parse RTP packet from byte array
     */
    public static RtpPacket parse(byte[] data, int offset, int length) {
        if (length < HEADER_SIZE) {
            return null;
        }

        try {
            int seq = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            long ts = (((long) data[offset + 2] & 0xFF) << 24) |
                      (((long) data[offset + 3] & 0xFF) << 16) |
                      (((long) data[offset + 4] & 0xFF) << 8) |
                      ((long) data[offset + 5] & 0xFF);
            long ssrcVal = (((long) data[offset + 6] & 0xFF) << 24) |
                           (((long) data[offset + 7] & 0xFF) << 16) |
                           (((long) data[offset + 8] & 0xFF) << 8) |
                           ((long) data[offset + 9] & 0xFF);

            int payloadLen = length - HEADER_SIZE;
            byte[] payload = new byte[payloadLen];
            System.arraycopy(data, offset + HEADER_SIZE, payload, 0, payloadLen);

            return new RtpPacket(seq, ts, ssrcVal, payload, payloadLen);
        } catch (Exception e) {
            return null;
        }
    }
}