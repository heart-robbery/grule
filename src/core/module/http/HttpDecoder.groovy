package core.module.http

import core.module.http.mvc.FileData
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer

class HttpDecoder {

    static final Logger log = LoggerFactory.getLogger(HttpDecoder)

    /**
     * 解析http请求
     * @param buf
     * @return
     */
    static HttpRequest decode(ByteBuffer buf) {
        // HttpMethodDecoder
        HttpRequest req = new HttpRequest()
        startLine(req, buf)
        header(req, buf)
        body(req, buf)
        return req
    }


    /**
     * 解析: 请求起始行
     * @param req
     * @param buf
     */
    static protected void startLine(HttpRequest req, ByteBuffer buf) {
        String firstLine = readLine(buf)
        def arr = firstLine.split(" ")
        req.method = arr[0]
        req.rowUrl = arr[1]
        req.protocol = arr[2].split("/")[0]
        req.version = arr[2].split("/")[1]
    }


    /**
     * 解析: 请求头
     * @param req
     * @param buf
     */
    static protected void header(HttpRequest req, ByteBuffer buf) {
        do {
            String headerLine = readLine(buf)
            if (!headerLine || '\r' == headerLine) break
            int index = headerLine.indexOf(":")
            req.headers.put(headerLine.substring(0, index).toString(), headerLine.substring(index + 1)?.trim())
        } while (true)
        // [Cookie:USER_ID_ANONYMOUS=28f34d1a1137476994fda617d2777b7c; DETECTED_VERSION=1.8.1; MAIN_NAV_ACTIVE_TAB_INDEX=1; PAGINATION_PAGE_SIZE=10; mntLogin=true; name=admin; roles=admin; Cache-Control=max-age=120; uId=4028b88173142d470173142d52700000; JSESSIONID=xPzSn1KxdBnxGU2fp_jYI4vWtdSduKAB2A64Q3R4, Accept:*/*, Connection:keep-alive, User-Agent:Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.25 Safari/537.36 Core/1.70.3766.400 QQBrowser/10.6.4163.400, Host:localhost:9090, Accept-Encoding:gzip, deflate, br, Accept-Language:zh-CN,zh;q=0.9]
        // log.info("headers: " + req.headers)
    }


    /**
     * 解析: 请求体
     * @param request
     * @param buf
     */
    static protected void body(HttpRequest request, ByteBuffer buf) {
        if (request.contentType.containsIgnoreCase('application/json') ||
            request.contentType.containsIgnoreCase('application/x-www-form-urlencoded') ||
            request.contentType.containsIgnoreCase('text/plain')
        ) {
            byte[] bs = new byte[Integer.valueOf(request.getHeader('content-length'))]
            buf.get(bs)
            request.bodyStr = new String(bs, 'utf-8')
        } else if (request.contentType.containsIgnoreCase('multipart/form-data')) {
            // HttpMultiBodyDecoder
            String boundary = '--' + request.boundary
            String endLine = boundary + '--'

            part: do {
                String line = readLine(buf)
                if (line == endLine) break part
                if (line == boundary) {
                    // 读参数名: 从header Content-Disposition 中读取参数名 和文件名
                    String name
                    String filename
                    header: do {
                        line = readLine(buf)
                        if ('' == line) break header
                        else if (line.containsIgnoreCase('Content-Disposition')) {
                            line.split(': ')[1].split(';').each {entry ->
                                def arr = entry.split('=')
                                if (arr.length > 1) {
                                    if (arr[0] == 'name') name = arr[1]
                                    else if (arr[0] == 'filename') filename = arr[1]
                                }
                            }
                        }
                    } while (true)

                    // 读参数值
                    if (filename) {
                        int index = getDelimIndex(buf, boundary.getBytes('utf-8'))
                        request.formParams.put(name, new FileData(originName: filename))
                    } else {
                        String value = readLine(buf)
                        request.formParams.put(name, URLDecoder.decode(value, 'utf-8'))
                    }
                }
            } while (true)
        }
    }

    // 查找分割符所匹配下标
    protected static int getDelimIndex(ByteBuffer buffer, byte[] delim) {
        byte[] hb = buffer.array()
        int delimIndex = -1 // 分割符所在的下标
        for (int i = buffer.position(), size = buffer.limit(); i < size; i++) {
            boolean match = true // 是否找到和 delim 相同的字节串
            for (int j = 0; j < delim.length; j++) {
                match = match && (i + j < size) && delim[j] == hb[i + j]
            }
            if (match) {
                delimIndex = i
                break
            }
        }
        return delimIndex
    }


    /**
     * 读行
     * @param buf
     * @return
     */
    static final byte[] lienDelim = '\n'.getBytes('utf-8')
    static protected String readLine(ByteBuffer buf) {
        int index = getLineIndex(buf)
        int readableLength = index - buf.position()
        byte[] bs = new byte[readableLength]
        buf.get(bs)
        // 跳过 分割符的长度
        for (int i = 0; i < lienDelim.length; i++) {buf.get()}
        new String(bs, 'utf-8')
    }


    // 查找行分割符所匹配下标
    static protected int getLineIndex(ByteBuffer buf) {
        byte[] hb = buf.array()
        int delimIndex = -1 // 分割符所在的下标
        for (int i = buf.position(), size = buf.limit(); i < size; i++) {
            boolean match = true // 是否找到和 delim 相同的字节串
            for (int j = 0; j < lienDelim.length; j++) {
                match = match && (i + j < size) && lienDelim[j] == hb[i + j]
            }
            if (match) {
                delimIndex = i
                break
            }
        }
        return delimIndex
    }
}
