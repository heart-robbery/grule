package core.http.ws

/**
 * web socket 监听器
 */
class Listener {
    void onClose(WebSocket ws) {}
    void onText(String msg) {}
    void onBinary(byte[] msg) {}
}
