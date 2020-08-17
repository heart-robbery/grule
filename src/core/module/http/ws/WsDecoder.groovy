package core.module.http.ws


import java.nio.ByteBuffer

/**
 * web socket 解码器
 */
class WsDecoder {
    final WebSocket ws
    // 当前正在被解码的消息
    protected WsMsg curMsg

    WsDecoder(WebSocket ws) { this.ws = ws }



    /**
     * 接收消息
     * 解码参考自: tio WsServerDecoder
     * @param buf
     */
    void decode(ByteBuffer buf) {
        if (curMsg == null) curMsg = new WsMsg()
        if (!curMsg.headComplete) curMsg.headComplete = head(buf)
        if (!curMsg.headComplete) return

        if (buf.remaining() < curMsg.payloadLength) return

        byte[] msgBs = new byte[curMsg.payloadLength]
        buf.get(msgBs)
        if (curMsg.hasMask) {
            for (int i = 0; i < msgBs.length; i++) {
                msgBs[i] = (byte) (msgBs[i] ^ curMsg.mask[i % 4])
            }
        }

        WsMsg msg = curMsg; curMsg = null
        if (msg.opCode == (byte) 1) {
            ws.listener?.onText(new String(msgBs, 'utf-8'))
        } else if (msg.opCode == (byte) 2) {
            ws.listener?.onBinary(msgBs)
        }

        if (buf.hasRemaining()) { // 如果还有剩余, 则继续解码
            decode(buf)
        }
    }


    /**
     * 解析消息头
     * @param buf
     * @return true: 消息头解析完成
     */
    protected boolean head(ByteBuffer buf) {
        // 第一阶段解析
        int initPosition = buf.position()
        curMsg.readableLength = buf.limit() - initPosition

        curMsg.headLength = 2

        if (curMsg.readableLength < curMsg.headLength + 12) return false

        byte first = buf.get()
        //	int b = first & 0xFF; //转换成32位
        boolean fin = (first & 0x80) > 0; // 得到第8位 10000000>0
        int rsv = (first & 0x70) >>> 4; // 得到5、6、7 为01110000 然后右移四位为00000111
        curMsg.opCode = (byte) (first & 0x0F); // 后四位为opCode 00001111
        // NOT_FIN((byte) 0), TEXT((byte) 1), BINARY((byte) 2), CLOSE((byte) 8), PING((byte) 9), PONG((byte) 10);
        if (curMsg.opCode == (byte) 0) {

        } else if (curMsg.opCode == (byte) 1) {

        } else if (curMsg.opCode == (byte) 2) {

        } else if (curMsg.opCode == (byte) 8) {
            ws.close()
            ws.listener?.onClose(ws)
            return
        } else if (curMsg.opCode == (byte) 9) {

        } else if (curMsg.opCode == (byte) 10) {

        }
        curMsg.second = buf.get(); // 向后读取一个字节
        curMsg.hasMask = (curMsg.second & 0xFF) >> 7 == 1; // 用于标识PayloadData是否经过掩码处理。如果是1，Masking-key域的数据即是掩码密钥，用于解码PayloadData。客户端发出的数据帧需要进行掩码处理，所以此位是1。

        // Client data must be masked
        if (!curMsg.hasMask) { // 第9为为mask,必须为1
            // throw new AioDecodeException("websocket client data must be masked");
        } else {
            curMsg.headLength += 4
        }

        curMsg.payloadLength = curMsg.second & 0x7F // 读取后7位  Payload legth，如果<126则payloadLength

        if (curMsg.payloadLength == 126) { // 为126读2个字节，后两个字节为payloadLength
            curMsg.headLength += 2;
            if (curMsg.readableLength < curMsg.headLength) {return}
            // payloadLength = ByteBufferUtils.readUB2WithBigEdian(buf);
            curMsg.payloadLength = (buf.get() & 0xff) << 8
            curMsg.payloadLength |= buf.get() & 0xff;
            // log.info("{} payloadLengthFlag: 126，payloadLength {}", channelContext, payloadLength);

        } else if (curMsg.payloadLength == 127) { // 127读8个字节,后8个字节为payloadLength
            curMsg.headLength += 8
            if (curMsg.readableLength < curMsg.headLength) {return}

            curMsg.payloadLength = (int) buf.getLong()
            // log.info("{} payloadLengthFlag: 127，payloadLength {}", channelContext, payloadLength);
        }

        if (curMsg.payloadLength < 0 || curMsg.payloadLength > 1024 * 512) {
            throw new RuntimeException("body length(" + curMsg.payloadLength + ") is not right");
        }
        if (curMsg.hasMask) {
            curMsg.mask = new byte[4]
            buf.get(curMsg.mask)
        }
        curMsg.headComplete = true
        return true
    }


    class WsMsg {
        byte first
        byte second
        int headLength
        int readableLength
        byte opCode
        boolean hasMask
        byte[] mask
        int payloadLength
        boolean headComplete
    }
}
