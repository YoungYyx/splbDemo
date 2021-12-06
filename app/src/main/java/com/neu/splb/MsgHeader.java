package com.neu.splb;

/**
 * MsgHeader
 * Socket消息处理
 */
public class MsgHeader {
    private byte[] hdrBegin;
    private byte[] version;
    private byte[] msgType;
    private byte[] funcId;
    private byte[] bodyLen;
    private byte[] reqId;
    private byte[] timeStamp;
    private byte[] bodyFmt;
    private byte[] reserve1;
    private byte[] reserve2;
    private byte[] reserve3;
    private long autoReqId;

    public MsgHeader() {
        this.hdrBegin = new byte[]{0x50, 0x43, 0x44, 0x48};
        this.version = new byte[]{0x10, 0x00};
        this.msgType = new byte[]{0x01, 0x00};
        this.funcId = new byte[]{0x01, 0x00};
        this.bodyLen = new byte[]{0x00, 0x00, 0x00, 0x00};
        this.reqId = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        this.timeStamp = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        this.bodyFmt = new byte[]{0x03, 0x00, 0x00, 0x00};
        this.reserve1 = new byte[]{0x00, 0x00, 0x00, 0x00};
        this.reserve2 = new byte[]{0x00, 0x00, 0x00, 0x00};
        this.reserve3 = new byte[]{0x00, 0x00, 0x00, 0x00};
    }

    public byte[] getNewHeaderMsg(int bodyLen) {
        updateBodyLen(bodyLen);
        updateReqId();
        return byteMergerAll(this.hdrBegin, this.version,
                this.msgType, this.funcId, this.bodyLen,
                this.reqId, this.timeStamp, this.bodyFmt,
                this.reserve1, this.reserve2, this.reserve3);
    }

    private byte[] byteMergerAll(byte[]... values) {
        int length_byte = 0;
        for (int i = 0; i < values.length; i++) {
            length_byte += values[i].length;
        }
        byte[] all_byte = new byte[length_byte];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }

    private void updateBodyLen(int length) {
        byte[] tmp = intToByteArray(length);
        for (int i = 0; i < 4; i++) {
            this.bodyLen[i] = tmp[3 - i];
        }
    }

    private byte[] intToByteArray(int srcInt) {
        return new byte[]{
                (byte) ((srcInt >> 24) & 0xFF),
                (byte) ((srcInt >> 16) & 0xFF),
                (byte) ((srcInt >> 8) & 0xFF),
                (byte) (srcInt & 0xFF)
        };
    }

    private void updateReqId() {
        this.autoReqId++;
        byte[] tmp = longToByteArray(this.autoReqId);
        for (int i = 0; i < 8; i++) {
            this.reqId[i] = tmp[7 - i];
        }
    }

    private byte[] longToByteArray(long srcLong) {
        return new byte[]{
                (byte) ((srcLong >> 56) & 0xFF),
                (byte) ((srcLong >> 48) & 0xFF),
                (byte) ((srcLong >> 40) & 0xFF),
                (byte) ((srcLong >> 32) & 0xFF),
                (byte) ((srcLong >> 24) & 0xFF),
                (byte) ((srcLong >> 16) & 0xFF),
                (byte) ((srcLong >> 8) & 0xFF),
                (byte) (srcLong & 0xFF)
        };
    }
}
