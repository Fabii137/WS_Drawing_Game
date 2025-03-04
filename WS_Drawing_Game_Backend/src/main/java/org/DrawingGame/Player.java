package org.DrawingGame;

import org.java_websocket.WebSocket;

public class Player {
    private final WebSocket ws;
    private final String username;
    private int points;

    public Player(WebSocket ws, String username) {
        this.ws = ws;
        this.username = username;
        points = 0;
    }

    public WebSocket getWebSocket() {
        return ws;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(int amount) {
        points += amount;
    }

    public void resetPoints() {
        points = 0;
    }
}
