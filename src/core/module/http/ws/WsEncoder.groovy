package core.module.http.ws

import groovy.transform.PackageScope

import java.nio.ByteBuffer

/**
 * web socket 编码器
 */
@PackageScope
class WsEncoder {


    /**
     * 编码 响应 文档消息
     * 编码参考: tio WsServerEncoder
     * @param msg
     * @return
     */
    static ByteBuffer encode(String msg) {
        encode(msg.getBytes('utf-8'), (byte) 1)
    }


    static ByteBuffer encode(byte[] body, byte opCode) {
        ByteBuffer buf
        byte header0 = (byte) (0x8f & (opCode | 0xf0))
        if (body.length < 126) {
            buf = ByteBuffer.allocate(2 + body.length)
            buf.put(header0)
            buf.put((byte) body.length)
        } else if (body.length < (1 << 16) - 1) {
            buf = ByteBuffer.allocate(4 + body.length)
            buf.put(header0)
            buf.put((byte) 126)
            buf.put((byte) (body.length >>> 8))
            buf.put((byte) (body.length & 0xff))
        } else {
            buf = ByteBuffer.allocate(10 + body.length)
            buf.put(header0)
            buf.put((byte) 127)
            //			buf.put(new byte[] { 0, 0, 0, 0 });
            buf.position(buf.position() + 4)
            buf.put((byte) (body.length >>> 24))
            buf.put((byte) (body.length >>> 16))
            buf.put((byte) (body.length >>> 8))
            buf.put((byte) (body.length & 0xff))
        }
        buf.put(body)
        buf.flip()
        buf
    }
}
