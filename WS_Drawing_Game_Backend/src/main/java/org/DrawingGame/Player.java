package org.DrawingGame;

import org.java_websocket.WebSocket;

public class Player {
    private WebSocket ws;
    private String username;
    private boolean isDone;

    public Player(WebSocket ws, String username) {
        this.ws = ws;
        this.username = username;
        isDone = false;
    }

    public WebSocket getWebSocket() {
        return ws;
    }

    public String getUsername() {
        return username;
    }
    public void setDone(boolean isDone) {
        this.isDone = isDone;
    }

    public boolean isDone() {
        return isDone;
    }
}
