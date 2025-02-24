package org.example;

import org.java_websocket.WebSocket;

public class Player {
    private WebSocket ws;
    private String username;

    public Player(WebSocket ws, String username) {
        this.ws = ws;
        this.username = username;
    }

    public WebSocket getWebSocket() {
        return ws;
    }

    public String getUsername() {
        return username;
    }
}
